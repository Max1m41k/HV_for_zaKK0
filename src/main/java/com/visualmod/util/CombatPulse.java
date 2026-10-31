package com.visualmod.util;

import net.minecraft.client.render.RenderLayers;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Состояние и логика «Пульса Уязвимости»: неоновый контур вокруг взмахнувшего противника,
 * сжимающийся внутрь ровно за длительность кулдауна его атаки, и сетка поднятого щита
 * с трещинами от ударов. Ничего не отправляется на сервер — только чтение пакетов.
 *
 * Миксин имеет право вызывать ТОЛЬКО этот класс: код, к которому обращается mixin,
 * не должен лежать в com.visualmod.mixin (иначе IllegalClassLoadError = чёрный экран).
 */
public final class CombatPulse {

    // ===================== настройки (пишет их модуль VulnerabilityPulse) =====================

    /** Цвет контура в момент взмаха (неон). */
    public static int swingColor = 0x00FFE0;
    /** Цвет контура в конце кулдауна (ярко-красный). */
    public static int dangerColor = 0xFF2030;
    /** Цвет сетки щита. */
    public static int shieldColor = 0x4FC3FF;
    /** Цвет трещин на щите. */
    public static int crackColor = 0xFF3BD6;
    /** Дальность, дальше которой противников не обрабатываем. */
    public static double range = 15;
    /** Рисовать ли сетку щита и трещины. */
    public static boolean shieldFx = true;

    /** Через столько времени трещины гаснут, если по щиту больше не бьют. */
    private static final long CRACK_LIFE_MS = 1200;
    private static final long BREAK_LIFE_MS = 600;
    /** Сколько держится «угроза топором» после взмаха топором рядом с защищающимся. */
    private static final long AXE_THREAT_MS = 900;
    private static final double AXE_THREAT_RANGE = 6.0;
    private static final int MAX_CRACKS = 7;
    /** Доля потерянной прочности, с которой щит считается критическим. */
    private static final float CRITICAL_WEAR = 0.75f;

    private static final class Pulse {
        final long start;
        final int durationMs;

        Pulse(long start, int durationMs) {
            this.start = start;
            this.durationMs = durationMs;
        }
    }

    private static final class Shield {
        int cracks;
        long lastBlockMs;
        long breakMs;
        long axeUntil;
    }

    /** Ключ — network id сущности: он стабилен, пока сущность жива, и не требует держать Entity. */
    private static final Map<Integer, Pulse> PULSES = new HashMap<>();
    private static final Map<Integer, Shield> SHIELDS = new HashMap<>();

    private CombatPulse() {
    }

    // ===================== точки входа для миксина =====================

    public static boolean active() {
        try {
            com.visualmod.module.modules.combat.VulnerabilityPulse m =
                    com.visualmod.VisualsMod.moduleManager == null ? null
                            : com.visualmod.VisualsMod.moduleManager
                            .getModule(com.visualmod.module.modules.combat.VulnerabilityPulse.class);
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            Diag.once("CombatPulse.active", t);
            return false;
        }
    }

    /** Пакет EntityAnimation: противник взмахнул рукой — запускаем сжимающийся контур. */
    public static void onSwing(MinecraftClient mc, int entityId) {
        if (!active() || mc == null || mc.world == null || mc.player == null) return;
        try {
            Entity e = mc.world.getEntityById(entityId);
            if (!(e instanceof LivingEntity le) || le == mc.player || le.isRemoved()) return;
            if (le.distanceTo(mc.player) > Math.max(4.0, range)) return;

            long now = System.currentTimeMillis();
            PULSES.put(entityId, new Pulse(now, cooldownMs(le)));

            if (shieldFx && holdsAxe(le)) threatenBlocking(mc, le, now);
        } catch (Throwable t) {
            Diag.once("CombatPulse.onSwing", t);
        }
    }

    /**
     * Пакет EntityStatus: 29 — щит отбил удар (добавляем трещину), 30 — щит сломан
     * (вспышка трещин и мерцание).
     */
    public static void onStatus(MinecraftClient mc, Entity entity, byte status) {
        if (!active() || !shieldFx || mc == null || mc.player == null) return;
        if (status != 29 && status != 30) return;
        if (!(entity instanceof LivingEntity le) || le.isRemoved()) return;
        try {
            if (le.distanceTo(mc.player) > Math.max(4.0, range)) return;
            Shield s = SHIELDS.computeIfAbsent(le.getId(), k -> new Shield());
            long now = System.currentTimeMillis();
            if (status == 29) {
                if (now - s.lastBlockMs > CRACK_LIFE_MS) s.cracks = 0;
                if (s.cracks < MAX_CRACKS) s.cracks++;
                s.lastBlockMs = now;
            } else {
                s.cracks = MAX_CRACKS;
                s.lastBlockMs = now;
                s.breakMs = now;
            }
        } catch (Throwable t) {
            Diag.once("CombatPulse.onStatus", t);
        }
    }

    /** Все, кто держит щит рядом с взмахнувшим топором, начинают интенсивно мерцать. */
    private static void threatenBlocking(MinecraftClient mc, LivingEntity attacker, long now) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == attacker || p == mc.player || !p.isBlocking()) continue;
            if (p.distanceTo(attacker) > AXE_THREAT_RANGE) continue;
            SHIELDS.computeIfAbsent(p.getId(), k -> new Shield()).axeUntil = now + AXE_THREAT_MS;
        }
    }

    /**
     * Кулдаун атаки в миллисекундах по ATTACK_SPEED оружия в руке. Базовая скорость игрока 4.0,
     * предмет добавляет модификатор (например, алмазный меч -2.4 → 1.6 → 12.5 тика), поэтому
     * контур сжимается ровно столько, сколько противник реально не может ударить снова.
     */
    private static int cooldownMs(LivingEntity le) {
        double speed = 4.0;
        try {
            ItemStack stack = le.getMainHandStack();
            if (!stack.isEmpty()) {
                double[] sum = new double[1];
                stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attr, mod) -> {
                    if (attr.value() == EntityAttributes.ATTACK_SPEED.value()
                            && mod.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                        sum[0] += mod.value();
                    }
                });
                speed = Math.max(0.2, 4.0 + sum[0]);
            }
        } catch (Throwable t) {
            Diag.once("CombatPulse.cooldown", t);
        }
        return (int) Math.round(20.0 / speed * 50.0);
    }

    private static boolean holdsAxe(LivingEntity le) {
        try {
            return le.getMainHandStack().getItem() instanceof AxeItem;
        } catch (Throwable t) {
            return false;
        }
    }

    // ===================== рендер =====================

    public static void render(WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        if (!active() || ctx == null || mc.player == null || mc.world == null || ctx.consumers() == null) return;
        try {
            long now = System.currentTimeMillis();
            float td = mc.getRenderTickCounter().getTickProgress(false);
            double maxDist = Math.max(4.0, range);
            // Один буфер на весь метод: все примитивы здесь — линии одного слоя.
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.lines());

            for (Iterator<Map.Entry<Integer, Pulse>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Pulse> entry = it.next();
                Pulse p = entry.getValue();
                if (now - p.start >= p.durationMs) {
                    it.remove();
                    continue;
                }
                Entity e = mc.world.getEntityById(entry.getKey());
                if (!(e instanceof LivingEntity le) || le.isRemoved() || le == mc.player) {
                    it.remove();
                    continue;
                }
                if (le.distanceTo(mc.player) > maxDist) continue;
                // Невидимка (зелье) — контур по хитбоксу выдал бы его положение, поэтому не рисуем.
                if (le.isInvisible()) continue;
                drawPulse(buf, mat, le, td, (now - p.start) / (float) p.durationMs);
            }

            if (shieldFx) {
                for (Iterator<Map.Entry<Integer, Shield>> it = SHIELDS.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, Shield> entry = it.next();
                    Shield s = entry.getValue();
                    Entity e = mc.world.getEntityById(entry.getKey());
                    boolean blocking = e instanceof PlayerEntity pe && !pe.isRemoved() && pe.isBlocking();
                    boolean freshBreak = now - s.breakMs < BREAK_LIFE_MS;
                    if (!blocking && !freshBreak && now - s.lastBlockMs > CRACK_LIFE_MS && now > s.axeUntil) {
                        it.remove();
                        continue;
                    }
                    if (!(e instanceof PlayerEntity pe) || e == mc.player
                            || pe.isInvisible() || pe.distanceTo(mc.player) > maxDist) continue;
                    drawShield(buf, mat, pe, td, s, now);
                }
            }
        } catch (Throwable t) {
            Diag.once("CombatPulse.render", t);
        }
    }

    private static void drawPulse(VertexConsumer buf, Matrix4f mat, LivingEntity le, float td, float t) {
        Vec3d pos = le.getLerpedPos(td);
        Box bb = le.getBoundingBox();
        double half = (bb.maxX - bb.minX) / 2.0;
        double height = bb.maxY - bb.minY;

        // Контур ползёт внутрь модели: от +0.22 снаружи до -0.06 (чуть врезается в тело).
        double pad = 0.22 - 0.28 * t;
        int c = lerpColor(swingColor, dangerColor, t);
        float r = red(c), g = green(c), b = blue(c);
        float a = 1.0f - 0.6f * t;

        WorldRenderUtil.drawOutlinedBox(buf, mat,
                new Box(pos.x - half - pad, pos.y - pad * 0.4, pos.z - half - pad,
                        pos.x + half + pad, pos.y + height + pad, pos.z + half + pad),
                r, g, b, a);

        // Пульс: горизонтальное кольцо на высоте груди сходится быстрее общего контура.
        double ring = half + pad * (1.0f - t);
        double y = pos.y + height * 0.62;
        square(buf, mat, pos.x, y, pos.z, ring, r, g, b, Math.min(1f, a + 0.15f));
    }

    private static void drawShield(VertexConsumer buf, Matrix4f mat, PlayerEntity p, float td, Shield s, long now) {
        Vec3d pos = p.getLerpedPos(td);
        Box bb = p.getBoundingBox();
        double half = (bb.maxX - bb.minX) / 2.0 + 0.14;
        double height = bb.maxY - bb.minY;

        float yaw = MathHelper.lerpAngleDegrees(td, p.lastBodyYaw, p.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;

        boolean stressed = shieldCritical(p) || now < s.axeUntil || now - s.breakMs < BREAK_LIFE_MS;
        float flick = stressed ? (((now % 110) < 55) ? 1.0f : 0.2f) : 1.0f;

        float r = red(shieldColor), g = green(shieldColor), b = blue(shieldColor);
        Box cage = new Box(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half);
        WorldRenderUtil.drawOutlinedBox(buf, mat, cage, r, g, b, 0.30f * flick);
        // Раскосы на четырёх вертикальных гранях + средний пояс — это и есть «геометрическая сетка».
        brace(buf, mat, cage, r, g, b, 0.22f * flick);
        square(buf, mat, pos.x, pos.y + height * 0.5, pos.z, half, r, g, b, 0.22f * flick);

        int cracks = s.cracks;
        if (now - s.lastBlockMs > CRACK_LIFE_MS && now - s.breakMs > BREAK_LIFE_MS) cracks = 0;
        if (cracks <= 0) return;

        float fade = 1.0f;
        long since = now - s.lastBlockMs;
        if (since > CRACK_LIFE_MS * 0.6) {
            fade = 1.0f - (since - CRACK_LIFE_MS * 0.6f) / (float) (CRACK_LIFE_MS * 0.4);
        }
        if (fade <= 0f) return;

        float cr = red(crackColor), cg = green(crackColor), cb = blue(crackColor);
        double sx = pos.x + fx * 0.42, sy = pos.y + height * 0.62, sz = pos.z + fz * 0.42;
        for (int i = 0; i < cracks; i++) {
            crack(buf, mat, sx, sy, sz, rx, rz, p.getId() * 31 + i * 7 + 1, cr, cg, cb,
                    Math.min(1f, 0.95f * fade * (stressed ? flick + 0.3f : 1f)));
        }
    }

    private static void brace(VertexConsumer buf, Matrix4f mat, Box c, float r, float g, float b, float a) {
        WorldRenderUtil.drawLine(buf, mat, c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.minZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.maxX, c.minY, c.minZ, c.minX, c.maxY, c.minZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.minX, c.minY, c.maxZ, c.maxX, c.maxY, c.maxZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.maxX, c.minY, c.maxZ, c.minX, c.maxY, c.maxZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.minX, c.minY, c.minZ, c.minX, c.maxY, c.maxZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.minX, c.minY, c.maxZ, c.minX, c.maxY, c.minZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.maxX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, c.maxX, c.minY, c.maxZ, c.maxX, c.maxY, c.minZ, r, g, b, a);
    }

    /**
     * Ломаная трещина на плоскости щита. Направление и длина сегментов детерминированы
     * от сида, иначе трещина дёргалась бы каждый кадр.
     */
    private static void crack(VertexConsumer buf, Matrix4f mat, double ox, double oy, double oz,
                              double rx, double rz, int seed, float r, float g, float b, float a) {
        double px = ox, py = oy, pz = oz;
        long s = seed * 2654435761L + 12345L;
        for (int i = 0; i < 3; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            double ang = ((s >>> 11) % 6283) / 1000.0;
            double len = 0.09 + ((s >>> 33) % 100) / 100.0 * 0.13;
            double nx = px + rx * Math.cos(ang) * len;
            double ny = py + Math.sin(ang) * len;
            double nz = pz + rz * Math.cos(ang) * len;
            WorldRenderUtil.drawLine(buf, mat, px, py, pz, nx, ny, nz, r, g, b, a);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static void square(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz,
                               double half, float r, float g, float b, float a) {
        WorldRenderUtil.drawLine(buf, mat, cx - half, y, cz - half, cx + half, y, cz - half, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, cx + half, y, cz - half, cx + half, y, cz + half, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, cx + half, y, cz + half, cx - half, y, cz + half, r, g, b, a);
        WorldRenderUtil.drawLine(buf, mat, cx - half, y, cz + half, cx - half, y, cz - half, r, g, b, a);
    }

    private static boolean shieldCritical(PlayerEntity p) {
        ItemStack shield = shieldStack(p);
        if (shield == null) return false;
        int max = shield.getMaxDamage();
        return max > 0 && (float) shield.getDamage() / max >= CRITICAL_WEAR;
    }

    private static ItemStack shieldStack(PlayerEntity p) {
        try {
            if (p.getOffHandStack().isOf(Items.SHIELD)) return p.getOffHandStack();
            if (p.getMainHandStack().isOf(Items.SHIELD)) return p.getMainHandStack();
        } catch (Throwable t) {
            Diag.once("CombatPulse.shieldStack", t);
        }
        return null;
    }

    private static int lerpColor(int from, int to, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int r = Math.round(red(from) + (red(to) - red(from)) * t * 255f);
        int g = Math.round(green(from) + (green(to) - green(from)) * t * 255f);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * t * 255f);
        return (r << 16) | (g << 8) | b;
    }

    private static float red(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }
    private static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255f; }
    private static float blue(int rgb) { return (rgb & 0xFF) / 255f; }
}
