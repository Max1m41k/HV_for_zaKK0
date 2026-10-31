package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.Diag;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Кристаллизация урона: вместо ванильных крит-частиц из хитбокса побитого игрока
 * вылетают объёмные осколки-октаэдры, цвет зависит от его нагрудника.
 */
public class ImpactShards extends Module implements Configurable {

    private static final int MAX_SHARDS = 40;
    private static final int FADE_TICKS = 10;          // 0.5 с затухания на земле
    private static final int MAX_AGE = 200;            // страховка от зависших в невыгруженных чанках
    private static final long IMPACT_TTL_MS = 600;     // окно, в котором крит-частица считается «нашей»
    private static final double CANCEL_DIST_SQ = 16.0; // ~4 блока
    private static final double GRAVITY = 0.028;
    private static final float BOUNCE_KEEP = 0.45f;

    // Цвет по умолчанию для случая «без брони» — единственная настройка цвета,
    // палитру брони переопределять нельзя, в этом суть фичи.
    private int color = 0xFFFFFF;
    private double shardCount = 6;
    private double size = 1.0;
    private boolean bounce = true;

    private final List<Setting> settings = List.of(
            new ColorSetting("Color", () -> color, c -> color = c),
            new SliderSetting("Shards", 5, 8, 1, () -> shardCount, v -> shardCount = v),
            new SliderSetting("Size", 0.5, 2.0, 0.1, () -> size, v -> size = v),
            new ToggleSetting("Bounce", () -> bounce, v -> bounce = v)
    );

    private static final class Shard {
        double x, y, z, vx, vy, vz;
        float ax, ay, az;      // ось кристалла — вдоль вектора скорости
        float spin, spinSpeed;
        float scale;
        int coreColor;
        int haloColor;         // 0 — без ореола
        int age;
        int fade = -1;         // >= 0 — отсчёт затухания после приземления
    }

    private static final class Impact {
        double x, y, z;
        long until;
    }

    private static final ArrayList<Shard> shards = new ArrayList<>();
    private static final ArrayList<Impact> impacts = new ArrayList<>();
    private final Map<Integer, Integer> lastHurt = new HashMap<>();

    // Скретч-векторы: в render() не должно быть аллокаций.
    private static final Vector3f SA = new Vector3f();
    private static final Vector3f SB = new Vector3f();
    private static final Vector3f SC = new Vector3f();
    private static final float[] COS4 = {1f, 0f, -1f, 0f};
    private static final float[] SIN4 = {0f, 1f, 0f, -1f};
    private static final float LX = 0.371f, LY = 0.877f, LZ = 0.392f; // normalize(0.36, 0.85, 0.38)

    public ImpactShards() {
        super("ImpactShards", "Replaces vanilla crit particles with 3D crystal shards bursting out of damaged players", Category.RENDER);
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public void onDisable() {
        shards.clear();
        impacts.clear();
        lastHurt.clear();
    }

    @Override
    public void onTick() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) {
                lastHurt.clear();
                return;
            }
            tickShards(mc);

            // Триггер урона: hurtTime переходит из 0 в > 0 — работает и для локального,
            // и для чужих игроков, не зависит от порядка пакетов анимаций.
            List<? extends PlayerEntity> players = mc.world.getPlayers();
            for (int i = 0; i < players.size(); i++) {
                PlayerEntity p = players.get(i);
                int cur = p.hurtTime;
                Integer prev = lastHurt.put(p.getId(), cur);
                if (prev != null && prev == 0 && cur > 0) {
                    spawnShards(p);
                }
            }
            if (lastHurt.size() > players.size() + 8) {
                Set<Integer> alive = new HashSet<>();
                for (int i = 0; i < players.size(); i++) alive.add(players.get(i).getId());
                lastHurt.keySet().retainAll(alive);
            }
        } catch (Throwable t) {
            Diag.once("ImpactShards.onTick", t);
        }
    }

    private void spawnShards(PlayerEntity target) {
        Vec3d c = target.getBoundingBox().getCenter();
        double px = c.x, py = c.y, pz = c.z;

        // Точка удара — центр хитбокса, чуть смещённый к атакующему.
        double dx = 0, dz = 0;
        LivingEntity attacker = target.getAttacker();
        if (attacker != null && attacker != target) {
            dx = px - attacker.getX();
            dz = pz - attacker.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1e-4) {
                dx /= len; dz /= len;
                px += dx * 0.2; pz += dz * 0.2;
            } else {
                dx = 0; dz = 0;
            }
        }
        if (dx == 0 && dz == 0) {
            double a = Math.random() * Math.PI * 2;
            dx = Math.cos(a); dz = Math.sin(a);
        }

        int core = color & 0xFFFFFF, halo = 0;
        int[] palette = chestPalette(target, core);
        core = palette[0];
        halo = palette[1];

        int count = Math.max(5, Math.min(8, (int) shardCount));
        for (int i = 0; i < count; i++) {
            if (shards.size() >= MAX_SHARDS) shards.remove(0); // самый старый уступает место свежему удару
            Shard s = new Shard();

            // Случайный конус вокруг направления «от атакующего», с уклоном вверх.
            double sx = dx + (Math.random() - 0.5) * 1.5;
            double sz = dz + (Math.random() - 0.5) * 1.5;
            double sy = 0.35 + Math.random() * 0.65;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            double speed = 0.12 + Math.random() * 0.14;
            s.x = px + (Math.random() - 0.5) * 0.1;
            s.y = py + (Math.random() - 0.5) * 0.1;
            s.z = pz + (Math.random() - 0.5) * 0.1;
            s.vx = sx / len * speed;
            s.vy = sy / len * speed;
            s.vz = sz / len * speed;
            s.ax = (float) (s.vx / speed);
            s.ay = (float) (s.vy / speed);
            s.az = (float) (s.vz / speed);
            s.spin = (float) (Math.random() * Math.PI * 2);
            s.spinSpeed = (float) ((Math.random() - 0.5) * 0.5);
            s.scale = (float) size * (0.8f + (float) (Math.random() * 0.4));
            s.coreColor = core;
            s.haloColor = halo;
            shards.add(s);
        }

        Impact imp = new Impact();
        imp.x = px; imp.y = py; imp.z = pz;
        imp.until = System.currentTimeMillis() + IMPACT_TTL_MS;
        impacts.removeIf(it -> it.until < imp.until);
        if (impacts.size() >= 32) impacts.remove(0);
        impacts.add(imp);
    }

    /** [core, halo]; halo == 0 значит без ореола. */
    private static int[] chestPalette(PlayerEntity p, int fallback) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        Item item = chest.isEmpty() ? null : chest.getItem();
        if (item == Items.DIAMOND_CHESTPLATE) return new int[]{0x22F5FF, 0};
        if (item == Items.NETHERITE_CHESTPLATE) return new int[]{0x4A0E22, 0x9A2BFF};
        if (item == Items.GOLDEN_CHESTPLATE) return new int[]{0xFFE033, 0};
        if (item == Items.IRON_CHESTPLATE) return new int[]{0xCBD8E4, 0};
        if (item == Items.CHAINMAIL_CHESTPLATE) return new int[]{0x8E97A3, 0};
        if (item == Items.LEATHER_CHESTPLATE) return new int[]{0xA9713D, 0};
        return new int[]{fallback & 0xFFFFFF, 0};
    }

    private void tickShards(MinecraftClient mc) {
        boolean doBounce = bounce;
        shards.removeIf(s -> {
            s.age++;
            if (s.fade >= 0) {
                s.fade--;
                return s.fade < 0;
            }
            if (s.age > MAX_AGE) return true;

            s.vy -= GRAVITY;
            double sp = s.vx * s.vx + s.vy * s.vy + s.vz * s.vz;
            if (sp > 1e-6) {
                double l = Math.sqrt(sp);
                s.ax = (float) (s.vx / l);
                s.ay = (float) (s.vy / l);
                s.az = (float) (s.vz / l);
            }
            s.spin += s.spinSpeed;

            if (doBounce) {
                // Раздельно по осям: иначе осколок «залипает» в углах.
                double nx = s.x + s.vx;
                if (blockCollides(mc, nx, s.y, s.z)) s.vx = -s.vx * BOUNCE_KEEP; else s.x = nx;
                double ny = s.y + s.vy;
                if (blockCollides(mc, s.x, ny, s.z)) {
                    s.vy = -s.vy * BOUNCE_KEEP;
                    s.vx *= 0.8; s.vz *= 0.8; // трение о поверхность
                } else s.y = ny;
                double nz = s.z + s.vz;
                if (blockCollides(mc, s.x, s.y, nz)) s.vz = -s.vz * BOUNCE_KEEP; else s.z = nz;
            } else {
                double nx = s.x + s.vx, ny = s.y + s.vy, nz = s.z + s.vz;
                if (blockCollides(mc, nx, ny, nz)) {
                    s.vx = 0; s.vy = 0; s.vz = 0;
                } else {
                    s.x = nx; s.y = ny; s.z = nz;
                }
            }

            boolean slow = s.vx * s.vx + s.vz * s.vz < 4e-4 && Math.abs(s.vy) < 0.05;
            if (slow && blockCollides(mc, s.x, s.y - 0.06, s.z)) {
                s.vx = 0; s.vy = 0; s.vz = 0;
                s.fade = FADE_TICKS;
            }
            return false;
        });
    }

    private static boolean blockCollides(MinecraftClient mc, double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    /**
     * Вызывается из миксина ParticleManager. Отменяет ванильный крит, только если
     * рядом и совсем недавно реально родились осколки — иначе не трогаем чужие эффекты.
     */
    public static boolean cancelVanillaCrit(ParticleEffect effect, double x, double y, double z) {
        try {
            if (effect != ParticleTypes.CRIT && effect != ParticleTypes.ENCHANTED_HIT) return false;
            ImpactShards mod = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(ImpactShards.class);
            if (mod == null || !mod.isEnabled()) return false;
            long now = System.currentTimeMillis();
            for (int i = 0; i < impacts.size(); i++) {
                Impact it = impacts.get(i);
                if (it.until < now) continue;
                double dx = it.x - x, dy = it.y - y, dz = it.z - z;
                if (dx * dx + dy * dy + dz * dz <= CANCEL_DIST_SQ) return true;
            }
            // На клиенте атакующего крит-частицы появляются раньше, чем придёт пакет урона,
            // поэтому дополнительно смотрим на свежий hurtTime nearby стоящих игроков:
            // осколки на этот удар уже родились (или родятся в этом же тике).
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p.hurtTime > 0 && p.hurtTime >= p.maxHurtTime - FADE_TICKS
                            && p.squaredDistanceTo(x, y, z) <= CANCEL_DIST_SQ) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Diag.once("ImpactShards.cancelCrit", t);
        }
        return false;
    }

    public static void render(WorldRenderContext ctx) {
        try {
            ImpactShards mod = VisualsMod.moduleManager == null ? null : VisualsMod.moduleManager.getModule(ImpactShards.class);
            if (mod == null || !mod.isEnabled() || shards.isEmpty()) return;
            MatrixStack ms = ctx.matrices();
            VertexConsumerProvider consumers = ctx.consumers();
            if (ms == null || consumers == null) return;
            // Диспетчер уже сдвинул стек на -camera: рисуем в абсолютных мировых координатах.
            Matrix4f mat = ms.peek().getPositionMatrix();
            // Один слой от начала до конца — Immediate-буфер не терпит чередования getBuffer.
            VertexConsumer buf = consumers.getBuffer(RenderLayers.debugQuads());
            for (int i = 0; i < shards.size(); i++) {
                drawShard(buf, mat, shards.get(i));
            }
        } catch (Throwable t) {
            Diag.once("ImpactShards.render", t);
        }
    }

    private static void drawShard(VertexConsumer buf, Matrix4f mat, Shard s) {
        float fm = s.fade >= 0 ? s.fade / (float) FADE_TICKS : 1f;
        if (fm <= 0.02f) return;
        float px = (float) s.x, py = (float) s.y, pz = (float) s.z;

        SA.set(s.ax, s.ay, s.az);
        if (SA.lengthSquared() < 1e-6f) SA.set(0f, 1f, 0f); else SA.normalize();
        // Два перпендикуляра к оси, повёрнутые на spin — вращение кристалла вокруг оси полёта.
        if (Math.abs(SA.y) > 0.9f) SB.set(1f, 0f, 0f); else SB.set(0f, 1f, 0f);
        SB.cross(SA, SB).normalize();
        SC.cross(SA, SB);
        float cs = (float) Math.cos(s.spin), sn = (float) Math.sin(s.spin);
        float bx = SB.x * cs + SC.x * sn, by = SB.y * cs + SC.y * sn, bz = SB.z * cs + SC.z * sn;
        float cx = SC.x * cs - SB.x * sn, cy = SC.y * cs - SB.y * sn, cz = SC.z * cs - SB.z * sn;
        SB.set(bx, by, bz);
        SC.set(cx, cy, cz);

        float hl = 0.075f * s.scale * fm;
        float w = 0.030f * s.scale * fm;

        if (s.haloColor != 0) {
            // Фиолетовое свечение незерита: тот же октаэдр крупнее и почти прозрачный.
            emitCrystal(buf, mat, px, py, pz, hl * 1.9f, w * 1.9f,
                    WorldRenderUtil.colorR(s.haloColor), WorldRenderUtil.colorG(s.haloColor), WorldRenderUtil.colorB(s.haloColor),
                    0.16f * fm, false);
        }
        emitCrystal(buf, mat, px, py, pz, hl, w,
                WorldRenderUtil.colorR(s.coreColor), WorldRenderUtil.colorG(s.coreColor), WorldRenderUtil.colorB(s.coreColor),
                0.92f * fm, true);
    }

    /** Удлинённый октаэдр вдоль оси SA; треугольные грани — вырожденными квадами (v1,v2,v3,v3). */
    private static void emitCrystal(VertexConsumer buf, Matrix4f mat,
                                    float px, float py, float pz,
                                    float hl, float w,
                                    float r, float g, float b, float alpha, boolean shaded) {
        float t1x = px + SA.x * hl, t1y = py + SA.y * hl, t1z = pz + SA.z * hl;
        float t2x = px - SA.x * hl, t2y = py - SA.y * hl, t2z = pz - SA.z * hl;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            float b0x = px + (SB.x * COS4[i] + SC.x * SIN4[i]) * w;
            float b0y = py + (SB.y * COS4[i] + SC.y * SIN4[i]) * w;
            float b0z = pz + (SB.z * COS4[i] + SC.z * SIN4[i]) * w;
            float b1x = px + (SB.x * COS4[j] + SC.x * SIN4[j]) * w;
            float b1y = py + (SB.y * COS4[j] + SC.y * SIN4[j]) * w;
            float b1z = pz + (SB.z * COS4[j] + SC.z * SIN4[j]) * w;

            float fr = r, fg = g, fb = b;
            if (shaded) {
                // Псевдосвет по нормали грани: «ядро» (грани к источнику) ярче, боковые темнее.
                float e1x = b0x - t1x, e1y = b0y - t1y, e1z = b0z - t1z;
                float e2x = b1x - t1x, e2y = b1y - t1y, e2z = b1z - t1z;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float d = nl < 1e-6f ? 0f : Math.abs((nx * LX + ny * LY + nz * LZ) / nl);
                float sh = 0.5f + 0.5f * d;
                fr *= sh; fg *= sh; fb *= sh;
            }
            WorldRenderUtil.quad(buf, mat, t1x, t1y, t1z, b0x, b0y, b0z, b1x, b1y, b1z, b1x, b1y, b1z, fr, fg, fb, alpha);
            WorldRenderUtil.quad(buf, mat, t2x, t2y, t2z, b1x, b1y, b1z, b0x, b0y, b0z, b0x, b0y, b0z, fr, fg, fb, alpha * 0.85f);
        }
    }
}
