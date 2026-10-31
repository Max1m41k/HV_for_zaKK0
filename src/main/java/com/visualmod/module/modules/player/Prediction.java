package com.visualmod.module.modules.player;

import com.visualmod.util.McCompat;
import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Prediction extends Module implements Configurable {

    public static boolean plus = true;
    public static boolean enemyPearls = true;
    public static boolean trail = true;
    public static boolean heldPreview = true;
    public static double maxTicks = 200;
    public static double snowballGravity = 0.03;
    public static int ownColor = 0xFF7A5CFF;
    public static int enemyColor = 0xFFFF5C5C;
    public static int hitColor = 0xFFFFD84A;

    private static final class Sim {
        final List<Vec3d> pts = new ArrayList<>();
        Vec3d hit; int hitKind;
        BlockPos blockPos; Entity hitEntity;
        double launchDeg, impactDeg; boolean held;
        boolean enemy; String owner = ""; String kind = ""; String stackName = "";
        final ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    }
    private static final Map<Integer, Sim> SIMS = new HashMap<>();

    static { WorldRenderEvents.AFTER_ENTITIES.register(Prediction::render); }

    public Prediction() { super("Prediction", "Where thrown projectiles will land", Category.PLAYER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Prediction+", new String[]{"OFF", "ON"}, () -> plus ? 1 : 0, i -> plus = (i == 1)));
        l.add(new ModeSetting("Enemy Pearls", new String[]{"OFF", "ON"}, () -> enemyPearls ? 1 : 0, i -> enemyPearls = (i == 1)));
        l.add(new ModeSetting("Held Preview", new String[]{"OFF", "ON"}, () -> heldPreview ? 1 : 0, i -> heldPreview = (i == 1)));
        l.add(new ModeSetting("Trail", new String[]{"OFF", "ON"}, () -> trail ? 1 : 0, i -> trail = (i == 1)));
        l.add(new SliderSetting("Max Time", 40, 300, 20, () -> maxTicks, v -> maxTicks = v, "t"));
        l.add(new SliderSetting("Snowball Gravity", 0.0, 0.1, 0.005, () -> snowballGravity, v -> snowballGravity = v));
        l.add(new ColorSetting("Own Color", () -> ownColor, c -> ownColor = c));
        l.add(new ColorSetting("Enemy Color", () -> enemyColor, c -> enemyColor = c));
        l.add(new ColorSetting("Hit Color", () -> hitColor, c -> hitColor = c));
        return l;
    }

    @Override public void onDisable() { SIMS.clear(); }

    private static String kindOf(Entity e) { return Registries.ENTITY_TYPE.getId(e.getType()).getPath(); }

    private static double gravity(String k) {
        return switch (k) {
            case "arrow", "spectral_arrow", "trident", "splash_potion", "lingering_potion" -> 0.05;
            case "experience_bottle" -> 0.07;
            case "snowball" -> snowballGravity;
            default -> 0.03;
        };
    }
    private static double waterDrag(String k) {
        return switch (k) { case "arrow", "spectral_arrow" -> 0.6; case "trident" -> 0.99; default -> 0.8; };
    }
    private static boolean tracked(String k) {
        return switch (k) { case "arrow", "spectral_arrow", "trident", "snowball", "egg", "ender_pearl",
                "experience_bottle", "splash_potion", "lingering_potion" -> true; default -> false; };
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) { SIMS.clear(); return; }
        Set<Integer> alive = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ProjectileEntity pe)) continue;
            String k = kindOf(e);
            if (!tracked(k) || e.getVelocity().lengthSquared() < 0.0025 || !e.isAlive()) continue;
            Entity owner = pe.getOwner();
            boolean own = owner == mc.player;
            if (!own && !(enemyPearls && k.equals("ender_pearl"))) continue;
            alive.add(e.getId());
            Sim s = SIMS.computeIfAbsent(e.getId(), id -> new Sim());
            if (trail) { s.trail.addLast(McCompat.pos(e)); while (s.trail.size() > 26) s.trail.removeFirst(); } else s.trail.clear();
            simulate(mc, e, k, owner, own, s);
        }
        SIMS.keySet().removeIf(id -> !alive.contains(id));
    }

    private static void simulate(MinecraftClient mc, Entity e, String k, Entity owner, boolean own, Sim s) {
        s.enemy = !own; s.kind = k; s.held = false;
        s.owner = owner != null ? owner.getName().getString() : "";
        s.stackName = (e instanceof ThrownItemEntity t) ? t.getStack().getName().getString() : "";
        List<LivingEntity> cands = mc.world.getEntitiesByClass(LivingEntity.class, e.getBoundingBox().expand(96.0), c -> c.isAlive() && !c.isSpectator());
        run(mc, e, McCompat.pos(e), e.getVelocity(), k, owner, cands, s);
    }

    private static void run(MinecraftClient mc, Entity ctxE, Vec3d pos, Vec3d vel, String k, Entity owner, List<LivingEntity> cands, Sim s) {
        World w = mc.world;
        s.pts.clear(); s.hit = null; s.hitKind = 0; s.blockPos = null; s.hitEntity = null;
        s.pts.add(pos);
        s.launchDeg = Math.toDegrees(Math.atan2(vel.y, Math.hypot(vel.x, vel.z)));
        double g = gravity(k), wd = waterDrag(k);
        int steps = (int) maxTicks;
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult bhr = w.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ctxE));
            Vec3d end = bhr.getType() != HitResult.Type.MISS ? bhr.getPos() : next;

            LivingEntity hitE = null; Vec3d hitEPos = null; double bestD = Double.MAX_VALUE;
            for (LivingEntity c : cands) {
                if (c == owner && i < 6) continue;
                Optional<Vec3d> r = c.getBoundingBox().expand(0.3).raycast(pos, end);
                if (r.isPresent()) { double d = r.get().squaredDistanceTo(pos); if (d < bestD) { bestD = d; hitE = c; hitEPos = r.get(); } }
            }
            double impact = Math.toDegrees(Math.atan2(-vel.y, Math.hypot(vel.x, vel.z)));
            if (hitE != null) { s.pts.add(hitEPos); s.hit = hitEPos; s.hitKind = 2; s.hitEntity = hitE; s.impactDeg = impact; return; }
            if (bhr.getType() != HitResult.Type.MISS) { s.pts.add(end); s.hit = end; s.hitKind = 1; s.blockPos = bhr.getBlockPos(); s.impactDeg = impact; return; }

            pos = next;
            s.pts.add(pos);
            boolean water = w.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
            vel = vel.multiply(water ? wd : 0.99).subtract(0, g, 0);
            if (pos.y < w.getBottomY() - 8) break;
        }
        s.impactDeg = Math.toDegrees(Math.atan2(-vel.y, Math.hypot(vel.x, vel.z)));
        s.hit = pos;
    }

    private static final Sim HELD = new Sim();
    private static List<LivingEntity> heldCands = new ArrayList<>();
    private static long heldCandsTick = -1;

    private static final class Shot { String kind; double speed, pitchOffset; String stackName = ""; }

    private static Shot shotFor(MinecraftClient mc, ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        Shot s = new Shot(); s.stackName = st.getName().getString();
        if (st.isOf(Items.ENDER_PEARL)) { s.kind = "ender_pearl"; s.speed = 1.5; return s; }
        if (st.isOf(Items.SNOWBALL)) { s.kind = "snowball"; s.speed = 1.5; return s; }
        if (st.isOf(Items.EGG)) { s.kind = "egg"; s.speed = 1.5; return s; }
        if (st.isOf(Items.SPLASH_POTION)) { s.kind = "splash_potion"; s.speed = 0.5; s.pitchOffset = -20; return s; }
        if (st.isOf(Items.LINGERING_POTION)) { s.kind = "lingering_potion"; s.speed = 0.5; s.pitchOffset = -20; return s; }
        if (st.isOf(Items.EXPERIENCE_BOTTLE)) { s.kind = "experience_bottle"; s.speed = 0.7; s.pitchOffset = -20; return s; }
        if (st.isOf(Items.TRIDENT) && mc.player.isUsingItem() && mc.player.getActiveItem() == st) { s.kind = "trident"; s.speed = 2.5; return s; }
        if (st.isOf(Items.BOW) && mc.player.isUsingItem() && mc.player.getActiveItem() == st) {
            float pull = BowItem.getPullProgress(mc.player.getItemUseTime());
            if (pull < 0.1f) return null;
            s.kind = "arrow"; s.speed = pull * 3.0; return s;
        }
        if (st.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(st)) { s.kind = "arrow"; s.speed = 3.15; return s; }
        return null;
    }

    private static Sim computeHeld(MinecraftClient mc, float td) {
        var p = mc.player;
        Shot shot = shotFor(mc, p.getMainHandStack());
        if (shot == null) shot = shotFor(mc, p.getOffHandStack());
        if (shot == null) return null;

        long tick = mc.world.getTime();
        if (tick != heldCandsTick) {
            heldCandsTick = tick;
            heldCands = mc.world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(96.0), c -> c.isAlive() && !c.isSpectator() && c != p);
        }
        double yaw = Math.toRadians(p.getYaw(td)), pitch = Math.toRadians(p.getPitch(td) + shot.pitchOffset);
        double rp = Math.toRadians(p.getPitch(td));
        Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3d mv = p.getVelocity();
        Vec3d vel = dir.multiply(shot.speed).add(mv.x, p.isOnGround() ? 0.0 : mv.y, mv.z);
        Vec3d pos = p.getLerpedPos(td).add(0, p.getEyeHeight(p.getPose()) - 0.1, 0);

        HELD.held = true; HELD.enemy = false; HELD.kind = shot.kind; HELD.owner = ""; HELD.stackName = shot.stackName;
        HELD.trail.clear();
        run(mc, p, pos, vel, shot.kind, p, heldCands, HELD);
        HELD.launchDeg = -Math.toDegrees(rp);
        return HELD;
    }

    private static final class Item { Sim s; Entity e; Vec3d head; float r, g, b; }

    private static void render(WorldRenderContext ctx) {
        try {
            if (VisualsMod.moduleManager == null) return;
            Prediction m = VisualsMod.moduleManager.getModule(Prediction.class);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (m == null || !m.isEnabled() || mc.world == null || mc.player == null || (SIMS.isEmpty() && !heldPreview)
                    || ctx.matrices() == null || ctx.consumers() == null) return;

            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();

            List<Item> items = new ArrayList<>();
            List<Box> areas = new ArrayList<>();
            for (Map.Entry<Integer, Sim> en : SIMS.entrySet()) {
                Entity e = mc.world.getEntityById(en.getKey());
                Sim s = en.getValue();
                if (e == null || s.pts.size() < 2 || s.hit == null) continue;
                Item it = new Item();
                it.s = s; it.e = e; it.head = e.getLerpedPos(td);
                int col = s.enemy ? enemyColor : ownColor;
                it.r = WorldRenderUtil.colorR(col); it.g = WorldRenderUtil.colorG(col); it.b = WorldRenderUtil.colorB(col);
                items.add(it);
                HolyItems.Entry he = HolyItems.matchName(s.stackName);
                if (he != null && !"SELF".equalsIgnoreCase(he.mode) && HolyItems.usable(he)) {
                    Vec3d h = s.hit;
                    areas.add(new Box(h.x - he.sizeX / 2, h.y + he.offsetY, h.z - he.sizeZ / 2, h.x + he.sizeX / 2, h.y + he.offsetY + he.sizeY, h.z + he.sizeZ / 2));
                }
            }
            if (heldPreview) {
                Sim hs = computeHeld(mc, td);
                if (hs != null && hs.pts.size() >= 2 && hs.hit != null) {
                    Item it = new Item();
                    it.s = hs; it.e = null; it.head = hs.pts.get(0);
                    it.r = WorldRenderUtil.colorR(ownColor); it.g = WorldRenderUtil.colorG(ownColor); it.b = WorldRenderUtil.colorB(ownColor);
                    items.add(it);
                    HolyItems.Entry he = HolyItems.matchName(hs.stackName);
                    if (he != null && !"SELF".equalsIgnoreCase(he.mode) && HolyItems.usable(he)) {
                        Vec3d h = hs.hit;
                        areas.add(new Box(h.x - he.sizeX / 2, h.y + he.offsetY, h.z - he.sizeZ / 2, h.x + he.sizeX / 2, h.y + he.offsetY + he.sizeY, h.z + he.sizeZ / 2));
                    }
                }
            }
            if (items.isEmpty()) return;

            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();

            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayers.lines());
            float hr = WorldRenderUtil.colorR(hitColor), hg = WorldRenderUtil.colorG(hitColor), hb = WorldRenderUtil.colorB(hitColor);
            for (Item it : items) {
                Sim s = it.s; Vec3d head = it.head, h = s.hit;
                int n = s.pts.size();
                Vec3d prev = head;
                for (int i = 1; i < n; i++) {
                    Vec3d p = s.pts.get(i);
                    float a = plus ? (float) (0.95 - 0.8 * (i / (double) n)) : 0.85f;
                    WorldRenderUtil.drawLine(lines, mat, prev.x, prev.y, prev.z, p.x, p.y, p.z, it.r, it.g, it.b, a);
                    prev = p;
                }
                if (plus) {
                    if (trail && s.trail.size() > 1) {
                        Vec3d[] t = s.trail.toArray(new Vec3d[0]);
                        for (int i = 1; i < t.length; i++) {
                            float a = 0.7f * i / t.length;
                            WorldRenderUtil.drawLine(lines, mat, t[i - 1].x, t[i - 1].y, t[i - 1].z, t[i].x, t[i].y, t[i].z, it.r, it.g, it.b, a);
                        }
                    }
                    double len = head.distanceTo(h);
                    int dashes = Math.max(2, (int) (len / 0.6));
                    for (int i = 0; i < dashes; i += 2) {
                        double t0 = i / (double) dashes, t1 = Math.min(1.0, (i + 1) / (double) dashes);
                        WorldRenderUtil.drawLine(lines, mat,
                                head.x + (h.x - head.x) * t0, head.y + (h.y - head.y) * t0, head.z + (h.z - head.z) * t0,
                                head.x + (h.x - head.x) * t1, head.y + (h.y - head.y) * t1, head.z + (h.z - head.z) * t1, 1f, 1f, 1f, 0.35f);
                    }
                }
                if (s.hitKind == 1 && s.blockPos != null) WorldRenderUtil.drawOutlinedBox(lines, mat, new Box(s.blockPos).expand(0.002), hr, hg, hb, 0.9f);
                else if (s.hitKind == 2 && s.hitEntity != null) WorldRenderUtil.drawOutlinedBox(lines, mat, s.hitEntity.getBoundingBox().expand(0.002), 1f, 0.25f, 0.25f, 0.95f);
                double c = 0.22;
                WorldRenderUtil.drawLine(lines, mat, h.x - c, h.y, h.z, h.x + c, h.y, h.z, hr, hg, hb, 1f);
                WorldRenderUtil.drawLine(lines, mat, h.x, h.y - c, h.z, h.x, h.y + c, h.z, hr, hg, hb, 1f);
                WorldRenderUtil.drawLine(lines, mat, h.x, h.y, h.z - c, h.x, h.y, h.z + c, hr, hg, hb, 1f);
            }

            VertexConsumer quads = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            for (Item it : items) {
                Sim s = it.s;
                if (s.hitKind == 1 && s.blockPos != null) WorldRenderUtil.drawFilledBox(quads, mat, new Box(s.blockPos).expand(0.002), hr, hg, hb, 0.07f);
                else if (s.hitKind == 2 && s.hitEntity != null) WorldRenderUtil.drawFilledBox(quads, mat, s.hitEntity.getBoundingBox().expand(0.002), 1f, 0.25f, 0.25f, 0.10f);
            }
            ctx.matrices().pop();

            if (plus) for (Item it : items) label(ctx, mc, it.s, it.s.hit);

            for (Box a : areas) HolyItems.drawArea(ctx, mc, a);
        } catch (Throwable t) { com.visualmod.util.Diag.once("Prediction", t); }
    }

    private static void label(WorldRenderContext ctx, MinecraftClient mc, Sim s, Vec3d h) {
        double dist = mc.player.getEyePos().distanceTo(h);
        String what = switch (s.hitKind) {
            case 1 -> mc.world.getBlockState(s.blockPos).getBlock().getName().getString();
            case 2 -> s.hitEntity.getName().getString();
            default -> "—";
        };
        String text = String.format("%.1f м · %s · падение %d°", dist, what, Math.round(s.impactDeg));
        if (s.held) text = String.format("%.1f м · %s · угол %d° · падение %d°", dist, what, Math.round(s.launchDeg), Math.round(s.impactDeg));
        if (s.enemy && !s.owner.isEmpty()) text = "Перл " + s.owner + " · " + text;

        var ms = ctx.matrices();
        ms.push();
        ms.translate(h.x, h.y + 0.6, h.z);
        ms.multiply(ctx.gameRenderer().getCamera().getRotation());
        float scale = 0.025f;
        ms.scale(-scale, -scale, scale);
        Matrix4f tm = ms.peek().getPositionMatrix();
        float x = -mc.textRenderer.getWidth(text) / 2f;
        mc.textRenderer.draw(text, x, 0, 0xFFFFFFFF, true, tm, ctx.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x55000000, 15728880);
        ms.pop();
    }
}
