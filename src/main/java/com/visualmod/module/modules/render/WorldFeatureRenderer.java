package com.visualmod.module.modules.render;

import com.visualmod.module.modules.combat.HitColor;
import com.visualmod.VisualsMod;
import com.visualmod.module.modules.hud.TargetHUD;
import com.visualmod.util.WorldRenderUtil;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class WorldFeatureRenderer {
    private WorldFeatureRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
            if (VisualsMod.moduleManager == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || ctx.matrices() == null || ctx.consumers() == null) return;

            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f mat = ctx.matrices().peek().getPositionMatrix();

            renderCustomHitboxes(ctx, mc, mat);
            renderBlockOverlay(ctx, mc, mat);
            renderHitColor(ctx, mc, mat);
            renderChinaHat(ctx, mc, mat);
            renderTargetMarker(ctx, mc, mat);
            renderTntTimer(ctx, mc, mat);
            renderFlatTrails(ctx, mc, mat);
            com.visualmod.util.CombatPulse.render(ctx, mc, mat);
            // У осколков нет своего сдвига на камеру — им нужен уже сдвинутый стек.
            ImpactShards.render(ctx);
            com.visualmod.util.WaypointManager.render(ctx, mc, mat);

            ctx.matrices().pop();
            com.visualmod.particle.CustomParticleManager.render(ctx);
        });
    }

    private static void renderTntTimer(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        TntTimer mod = VisualsMod.moduleManager.getModule(TntTimer.class);
        if (mod == null || !mod.isEnabled()) return;

        float td = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        for (com.visualmod.util.TntWatch.Bomb b : com.visualmod.util.TntWatch.live(mc, td)) {
            if (b.pos().squaredDistanceTo(cam) > TntTimer.MAX_DISTANCE * TntTimer.MAX_DISTANCE) continue;
            String text = TntTimer.label(b);
            int textColor = TntTimer.colorFor(b);

            var matrixStack = ctx.matrices();
            matrixStack.push();
            matrixStack.translate(b.pos().x, b.pos().y + 0.62, b.pos().z);
            matrixStack.multiply(mc.gameRenderer.getCamera().getRotation());
            float scale = 0.032f;
            matrixStack.scale(-scale, -scale, scale);

            Matrix4f textMat = matrixStack.peek().getPositionMatrix();
            float xOffset = -mc.textRenderer.getWidth(text) / 2f;

            mc.textRenderer.draw(
                    text, xOffset, 0, textColor, true, textMat, ctx.consumers(),
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                    15728880, 0xB0080810);
            matrixStack.pop();
        }
    }

    private static void renderFlatTrails(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        Trails mod = VisualsMod.moduleManager.getModule(Trails.class);
        if (mod == null || !mod.isEnabled()) return;

        float r = WorldRenderUtil.colorR(Trails.color);
        float g = WorldRenderUtil.colorG(Trails.color);
        float b = WorldRenderUtil.colorB(Trails.color);

        if (Trails.mode == 1) {
            renderBehindTrail(ctx, mc, mat, r, g, b);
            return;
        }

        synchronized (Trails.trailBoxes) {
            int size = Trails.trailBoxes.size();
            if (size == 0) return;

            VertexConsumer fillBuf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            for (int i = 0; i < size; i++) {
                Box originalBox = Trails.trailBoxes.get(i);
                float progress = (float) (i + 1) / size;
                float fillAlpha = 0.15f * progress;

                Box flatBox = new Box(
                        originalBox.minX - 0.05, originalBox.minY, originalBox.minZ - 0.05,
                        originalBox.maxX + 0.04, originalBox.minY + 0.015, originalBox.maxZ + 0.04
                );
                WorldRenderUtil.drawFilledBox(fillBuf, mat, flatBox, r, g, b, fillAlpha);
            }
        }
    }

    /**
     * Линия за спиной: путь предварительно режется по длине дуги, поэтому он остаётся линией и
     * на медленном ходу, когда сырые тиковые точки слипаются в пятно. Ширина постоянная,
     * яркость растёт к ногам. От первого лица не рисуется.
     */
    private static void renderBehindTrail(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc,
                                          Matrix4f mat, float r, float g, float b) {
        if (mc.options.getPerspective().isFirstPerson()) return;
        int n = copySmoothedTrail();
        if (n < 2) return;
        int m = resampleTrail(n);
        if (m < 2) return;

        VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
        final float HALF = 0.11f;
        final float LIFT = 0.04f;
        // RX[0] — дальний хвост, RX[m-1] у ног: свечение усиливается к игроку.
        for (int i = 0; i + 1 < m; i++) {
            float dx = (float) (RX[i + 1] - RX[i]), dz = (float) (RZ[i + 1] - RZ[i]);
            float len = (float) Math.hypot(dx, dz);
            if (len < 1e-5f) continue;
            float px = -dz / len, pz = dx / len;
            float t = i / (float) (m - 1);
            WorldRenderUtil.quad(buf, mat,
                    (float) (RX[i] - px * HALF), (float) (RY[i] + LIFT), (float) (RZ[i] - pz * HALF),
                    (float) (RX[i] + px * HALF), (float) (RY[i] + LIFT), (float) (RZ[i] + pz * HALF),
                    (float) (RX[i + 1] + px * HALF), (float) (RY[i + 1] + LIFT), (float) (RZ[i + 1] + pz * HALF),
                    (float) (RX[i + 1] - px * HALF), (float) (RY[i + 1] + LIFT), (float) (RZ[i + 1] - pz * HALF),
                    r, g, b, 0.10f + 0.55f * t * t);
        }
    }

    private static final int TRAIL_MAX = 128;
    private static final double[] TX = new double[TRAIL_MAX];
    private static final double[] TY = new double[TRAIL_MAX];
    private static final double[] TZ = new double[TRAIL_MAX];
    /** Шаг ресемплинга: короче шага движения лента не станет «точкой», длиннее — начнёт ломаться. */
    private static final float TRAIL_STEP = 0.4f;
    private static final double[] RX = new double[TRAIL_MAX];
    private static final double[] RY = new double[TRAIL_MAX];
    private static final double[] RZ = new double[TRAIL_MAX];

    /** Полилиния (от хвоста к ногам) с фиксированным шагом по длине дуги. Рывок/телепорт = обрыв линии. */
    private static int resampleTrail(int n) {
        RX[0] = TX[0]; RY[0] = TY[0]; RZ[0] = TZ[0];
        int m = 1;
        double acc = 0;
        for (int i = 0; i + 1 < n && m < TRAIL_MAX; i++) {
            double dx = TX[i + 1] - TX[i], dy = TY[i + 1] - TY[i], dz = TZ[i + 1] - TZ[i];
            double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (seg < 1e-4) continue;
            if (seg > 6) {
                RX[m] = TX[i + 1]; RY[m] = TY[i + 1]; RZ[m] = TZ[i + 1];
                m++;
                acc = 0;
                continue;
            }
            double from = acc;
            while (acc + TRAIL_STEP <= from + seg && m < TRAIL_MAX) {
                acc += TRAIL_STEP;
                double f = (acc - from) / seg;
                RX[m] = TX[i] + dx * f;
                RY[m] = TY[i] + dy * f;
                RZ[m] = TZ[i] + dz * f;
                m++;
            }
        }
        return m;
    }

    /** Копируем ленту в свои массивы, сглаживая каждую точку соседями: сырые тиковые позиции рваные. */
    private static int copySmoothedTrail() {
        synchronized (Trails.behindPoints) {
            int size = Trails.behindPoints.size();
            if (size < 3) return 0;
            int n = Math.min(size, TRAIL_MAX);
            int off = size - n;
            for (int i = 0; i < n; i++) {
                int at = off + i;
                Trails.TrailPoint p = Trails.behindPoints.get(at);
                Trails.TrailPoint a = Trails.behindPoints.get(Math.max(off, at - 1));
                Trails.TrailPoint c = Trails.behindPoints.get(Math.min(off + n - 1, at + 1));
                TX[i] = (a.x + p.x * 2 + c.x) * 0.25;
                TY[i] = (a.y + p.y * 2 + c.y) * 0.25;
                TZ[i] = (a.z + p.z * 2 + c.z) * 0.25;
            }
            return n;
        }
    }

    private static void renderCustomHitboxes(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        com.visualmod.module.modules.player.CustomHitbox mod = VisualsMod.moduleManager.getModule(com.visualmod.module.modules.player.CustomHitbox.class);
        if (mod == null || !mod.isEnabled()) return;

        float r = WorldRenderUtil.colorR(mod.getColor());
        float g = WorldRenderUtil.colorG(mod.getColor());
        float b = WorldRenderUtil.colorB(mod.getColor());

        if (com.visualmod.module.modules.player.CustomHitbox.style
                == com.visualmod.module.modules.player.CustomHitbox.Style.FILLED) {
            // Отдельного слоя «квады без записи глубины» (бывший structure quads) в 1.21.11 нет —
            // остаётся обычный debugQuads: заполнение может чуть притенять модель за собой.
            VertexConsumer fillBuf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            float a = com.visualmod.module.modules.player.CustomHitbox.getFillAlpha(0.45f);
            for (Entity e : mc.world.getEntities()) {
                if (!hitboxTarget(e, mc)) continue;
                WorldRenderUtil.drawFilledBox(fillBuf, mat, ((LivingEntity) e).getBoundingBox().expand(0.04), r, g, b, a);
            }
        }

        // Второй буфер — только после того, как первый полностью записан.
        VertexConsumer lineBuf = ctx.consumers().getBuffer(RenderLayers.lines());
        for (Entity e : mc.world.getEntities()) {
            if (!hitboxTarget(e, mc)) continue;
            WorldRenderUtil.drawOutlinedBox(lineBuf, mat, ((LivingEntity) e).getBoundingBox().expand(0.04), r, g, b, 0.9f);
        }
    }

    private static boolean hitboxTarget(Entity e, MinecraftClient mc) {
        return e != mc.player && e instanceof LivingEntity living && !living.isInvisible();
    }

    private static void renderBlockOverlay(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        BlockOverlay mod = VisualsMod.moduleManager.getModule(BlockOverlay.class);
        if (mod == null || !mod.isEnabled()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;

        BlockPos pos = hit.getBlockPos();
        Box box = new Box(pos).expand(0.002);
        List<BlockPos> area = BlockOverlay.area(pos, hit.getSide(), miningHand(mc), mc.world);

        if (BlockOverlay.getStyle() == BlockOverlay.Style.FILLED) {
            int fillInt = mod.getFillColor();
            float fr = WorldRenderUtil.colorR(fillInt);
            float fg = WorldRenderUtil.colorG(fillInt);
            float fb = WorldRenderUtil.colorB(fillInt);
            float fa = alphaOf(fillInt, 0.42f);
            // «Заливка без записи глубины» в 1.21.11 недоступна — тот же debugQuads.
            VertexConsumer fillBuf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            WorldRenderUtil.drawFilledBox(fillBuf, mat, box, fr, fg, fb, fa);
            for (BlockPos p : area) {
                if (p.equals(pos)) continue;
                WorldRenderUtil.drawFilledBox(fillBuf, mat, new Box(p).expand(0.002), fr, fg, fb, fa * 0.6f);
            }
            return;
        }

        int colorInt = mod.getColor();
        float r = WorldRenderUtil.colorR(colorInt);
        float g = WorldRenderUtil.colorG(colorInt);
        float b = WorldRenderUtil.colorB(colorInt);
        float alpha = alphaOf(colorInt, 0.95f);

        VertexConsumer lineBuf = ctx.consumers().getBuffer(RenderLayers.lines());
        WorldRenderUtil.drawOutlinedBox(lineBuf, mat, box, r, g, b, alpha);
        for (BlockPos p : area) {
            if (p.equals(pos)) continue;
            WorldRenderUtil.drawOutlinedBox(lineBuf, mat, new Box(p).expand(0.002), r, g, b, alpha * 0.55f);
        }
    }

    private static float alphaOf(int argb, float def) {
        int a = (argb >> 24) & 0xFF;
        return a == 0 ? def : a / 255.0f;
    }

    private static net.minecraft.item.ItemStack miningHand(MinecraftClient mc) {
        net.minecraft.item.ItemStack main = mc.player.getMainHandStack();
        return main.isEmpty() ? mc.player.getOffHandStack() : main;
    }

    private static void renderHitColor(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        if (!HitColor.isFeatureEnabled) return;
        HitColor mod = VisualsMod.moduleManager.getModule(HitColor.class);
        if (mod == null || !mod.isEnabled()) return;

        float r = WorldRenderUtil.colorR(mod.getColor());
        float g = WorldRenderUtil.colorG(mod.getColor());
        float b = WorldRenderUtil.colorB(mod.getColor());
        float baseAlpha = (float) mod.getIntensity();

        VertexConsumer fillBuf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !HitColor.shouldRender(p)) continue;
            float fade = HitColor.getFadeProgress(p);
            if (fade <= 0f) continue;

            Box box = p.getBoundingBox().expand(0.08);
            WorldRenderUtil.drawFilledBox(fillBuf, mat, box, r, g, b, baseAlpha * fade);
        }
    }

    private static void renderChinaHat(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        ChinaHat mod = VisualsMod.moduleManager.getModule(ChinaHat.class);
        if (mod == null || !mod.isEnabled() || mc.player == null) return;

        // Шляпа рисуется ровно одним путём: от 3-го лица — из ChinaHatMixin (в матрицах модели),
        // а здесь только от 1-го лица. Раньше оба пути рисовали её одновременно, а мировой
        // использовал неинтерполированные координаты — отсюда дрожание и «двойная» шляпа.
        if (!mc.options.getPerspective().isFirstPerson()) return;

        PlayerEntity p = mc.player;
        float td = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d pos = p.getLerpedPos(td);

        // Камера сидит на уровне головы, поэтому при взгляде вверх конус закрывает весь экран:
        // гасим шляпу по наклону камеры, она остаётся над головой и не мешает.
        float lookingUp = Math.max(0f, -mc.gameRenderer.getCamera().getPitch());
        float fade = Math.max(0f, 1f - lookingUp / 26f);
        if (fade <= 0.02f) return;

        float r = WorldRenderUtil.colorR(mod.getColor());
        float g = WorldRenderUtil.colorG(mod.getColor());
        float b = WorldRenderUtil.colorB(mod.getColor());
        VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

        double cx = pos.x, cz = pos.z;
        double brimY = pos.y + p.getHeight() + 0.06;
        double radius = 0.74;
        double tipY = brimY + 0.60;
        int seg = 32;
        float pulse = ChinaHat.getPulseAlpha(1f) * fade;
        // Интерполирования тела больше нет (prevBodyYaw удалён) — берём текуный yaw, он и так
        // интерполируется на стороне сущности.
        double yaw = Math.toRadians(p.getBodyYaw());

        // Сплошные поверхности вместо каркаса из линий: раньше сквозь конус было видно
        // мир («дырявая»). Каждая грань перекрывается противоположной стороной конуса,
        // поэтому альфа держим низкой — на глаз она складывается в плотный цвет.
        fillCone(buf, mat, cx, brimY, cz, radius, tipY, seg, r, g, b, 0.26f * pulse);
        fillDisc(buf, mat, cx, brimY, cz, radius, seg, r, g, b, 0.32f * pulse);
        fillRing(buf, mat, cx, brimY + 0.012, cz, radius * 0.84, radius, seg, r, g, b, 0.85f * pulse);
        fillRing(buf, mat, cx, brimY + 0.024, cz, radius * 0.06, radius * 0.20, seg, r, g, b, 0.55f * pulse);
        for (int i = 0; i < 8; i++) {
            double ang = yaw + (Math.PI * 2 * i) / 8;
            fillSpoke(buf, mat, cx, brimY, cz, radius, tipY, ang, r, g, b, 0.45f * pulse);
        }
        fillDisc(buf, mat, cx, tipY - 0.01, cz, 0.055, 12, 1f, 1f, 1f, 0.9f * pulse);
    }

    private static void fillCone(VertexConsumer buf, Matrix4f mat, double cx, double baseY, double cz,
                                 double radius, double tipY, int seg,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            double a0 = (Math.PI * 2 * i) / seg;
            double a1 = (Math.PI * 2 * (i + 1)) / seg;
            WorldRenderUtil.quad(buf, mat,
                    (float) (cx + Math.cos(a0) * radius), (float) baseY, (float) (cz + Math.sin(a0) * radius),
                    (float) (cx + Math.cos(a1) * radius), (float) baseY, (float) (cz + Math.sin(a1) * radius),
                    (float) cx, (float) tipY, (float) cz,
                    (float) cx, (float) tipY, (float) cz,
                    r, g, b, a);
        }
    }

    /** Узкая светлая полоса вдоль тульи — даёт объём без ощущимых зазоров между гранями. */
    private static void fillSpoke(VertexConsumer buf, Matrix4f mat, double cx, double baseY, double cz,
                                  double radius, double tipY, double ang,
                                  float r, float g, float b, float a) {
        double d = 0.055;
        WorldRenderUtil.quad(buf, mat,
                (float) (cx + Math.cos(ang - d) * radius), (float) baseY, (float) (cz + Math.sin(ang - d) * radius),
                (float) (cx + Math.cos(ang + d) * radius), (float) baseY, (float) (cz + Math.sin(ang + d) * radius),
                (float) cx, (float) tipY, (float) cz,
                (float) cx, (float) tipY, (float) cz,
                r, g, b, a);
    }

    private static void fillDisc(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz,
                                 double radius, int seg, float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            double a0 = (Math.PI * 2 * i) / seg;
            double a1 = (Math.PI * 2 * (i + 1)) / seg;
            WorldRenderUtil.quad(buf, mat,
                    (float) cx, (float) y, (float) cz,
                    (float) (cx + Math.cos(a0) * radius), (float) y, (float) (cz + Math.sin(a0) * radius),
                    (float) (cx + Math.cos(a1) * radius), (float) y, (float) (cz + Math.sin(a1) * radius),
                    (float) (cx + Math.cos(a1) * radius), (float) y, (float) (cz + Math.sin(a1) * radius),
                    r, g, b, a);
        }
    }

    private static void fillRing(VertexConsumer buf, Matrix4f mat, double cx, double y, double cz,
                                 double rIn, double rOut, int seg, float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            double a0 = (Math.PI * 2 * i) / seg;
            double a1 = (Math.PI * 2 * (i + 1)) / seg;
            WorldRenderUtil.quad(buf, mat,
                    (float) (cx + Math.cos(a0) * rIn), (float) y, (float) (cz + Math.sin(a0) * rIn),
                    (float) (cx + Math.cos(a1) * rIn), (float) y, (float) (cz + Math.sin(a1) * rIn),
                    (float) (cx + Math.cos(a1) * rOut), (float) y, (float) (cz + Math.sin(a1) * rOut),
                    (float) (cx + Math.cos(a0) * rOut), (float) y, (float) (cz + Math.sin(a0) * rOut),
                    r, g, b, a);
        }
    }

    private static void renderTargetMarker(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, MinecraftClient mc, Matrix4f mat) {
        TargetHUD hud = VisualsMod.moduleManager.getModule(TargetHUD.class);
        if (hud == null || !hud.isEnabled()) return;

        LivingEntity target = TargetHUD.getTarget();
        if (target == null || target.isDead() || target == mc.player || target.isInvisible()) return;

        int color = hud.getAccentColor();
        float r = WorldRenderUtil.colorR(color);
        float g = WorldRenderUtil.colorG(color);
        float b = WorldRenderUtil.colorB(color);
        int col2 = hud.getSecondColor();
        float r2 = WorldRenderUtil.colorR(col2), g2 = WorldRenderUtil.colorG(col2), b2 = WorldRenderUtil.colorB(col2);

        TargetHUD.TargetShape shape = hud.getTargetShape();
        VertexConsumer q = ctx.consumers().getBuffer(RenderLayers.debugQuads());

        float flash = TargetHUD.hitFlash(target);
        float cr = lerp(r, 1f, flash * 0.85f);
        float cg = lerp(g, 0.10f, flash * 0.85f);
        float cb = lerp(b, 0.10f, flash * 0.85f);
        float cr2 = lerp(r2, 1f, flash * 0.7f);
        float cg2 = lerp(g2, 0.16f, flash * 0.7f);
        float cb2 = lerp(b2, 0.16f, flash * 0.7f);

        var ms = ctx.matrices();
        ms.push();
        ms.translate(target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ());
        ms.multiply(mc.gameRenderer.getCamera().getRotation());
        ms.scale(-1f, -1f, 1f);

        Matrix4f m = ms.peek().getPositionMatrix();
        LOCAL_ROT.set(mc.gameRenderer.getCamera().getRotation()).conjugate();
        double sec = System.currentTimeMillis() / 1000.0;
        float s = Math.max(0.3f, hud.getMarkerScale());
        float sp = Math.max(0.1f, hud.getMarkerSpeed()) * (1f + flash * 1.3f);
        float hw = (float) Math.max(0.35, target.getWidth() * 0.85) * s;
        float hh = (float) Math.max(0.5, target.getHeight() / 2.0) * s;
        float w = 0.032f * s * Math.max(0.2f, hud.getMarkerThickness());

        switch (shape) {
            case CORNERS -> {
                float len = Math.min(hw, hh) * 0.62f;
                float pulse = 1f + 0.05f * (float) Math.sin(sec * 2.4 * sp);
                float bx = hw * pulse, by = hh * pulse;
                for (int qx = -1; qx <= 1; qx += 2) {
                    for (int qy = -1; qy <= 1; qy += 2) {
                        float x = qx * bx, y = qy * by;
                        tLine(q, m, x, y, 0, x - qx * len, y, 0, w * 1.3f, cr, cg, cb, 0.95f);
                        tLine(q, m, x, y, 0, x, y - qy * len, 0, w * 1.3f, cr, cg, cb, 0.95f);
                        dot(q, m, x, y, 0, w * 1.15f, cr, cg, cb, 0.9f);
                    }
                }
                tEllipse(q, m, 0, 0, bx * 0.2f, by * 0.2f, 16, 0, w * 0.8f, cr2, cg2, cb2, 0.55f);
            }
            case HEXAGON -> {
                float rot = (float) (sec * 0.9 * sp);
                tPoly(q, m, 6, 0.7f * s, 0.7f * s, rot, w * 1.2f, cr, cg, cb, 0.95f);
                tPoly(q, m, 6, 0.46f * s, 0.46f * s, -rot * 1.6f, w, cr2, cg2, cb2, 0.7f);
                for (int i = 0; i < 6; i++) {
                    float a = rot + i * (float) (Math.PI / 3f);
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    tLine(q, m, ca * 0.7f * s, sa * 0.7f * s, 0,
                            ca * 0.9f * s, sa * 0.9f * s, 0, w * 0.9f, cr, cg, cb, 0.5f);
                }
            }
            case CHEVRONS -> {
                // Вся геометрия уходит в local +Z, то есть к камере, поэтому шевроны
                // ложатся поверх модели цели и не прячутся за ней.
                float z = 0.55f * s + flash * 0.25f * s;
                float size = Math.max(0.28f, hw * 0.72f);
                float arm = size * 0.55f;
                float thickness = w * 1.55f;
                float rot = (float) (sec * 0.9 * sp) + flash * 3.4f;
                float bob = 0.05f * s * (float) Math.sin(sec * 2.2 * sp);

                // Верхняя и нижняя стрелки-шеврона: двойные, внешний — вторым цветом.
                float cy = hh + size * 0.6f + bob;
                chevron(q, m, 0f, cy, z, size, arm, thickness, -1f, cr, cg, cb, 0.95f);
                chevron(q, m, 0f, cy - arm * 0.85f, z, size * 0.7f, arm * 0.65f, thickness * 0.75f, -1f, cr2, cg2, cb2, 0.75f);
                chevron(q, m, 0f, -cy, z, size, arm, thickness, 1f, cr, cg, cb, 0.95f);
                chevron(q, m, 0f, -cy + arm * 0.85f, z, size * 0.7f, arm * 0.65f, thickness * 0.75f, 1f, cr2, cg2, cb2, 0.75f);

                // Маячки по бокам, чтобы маркер читался даже когда цель боком.
                float side = hw * 1.06f;
                dot(q, m, side, 0, z, thickness * 0.75f, cr, cg, cb, 0.9f);
                dot(q, m, -side, 0, z, thickness * 0.75f, cr, cg, cb, 0.9f);
                tLine(q, m, side * 0.55f, 0, z, side, 0, z, thickness * 0.55f, cr2, cg2, cb2, 0.55f);
                tLine(q, m, -side * 0.55f, 0, z, -side, 0, z, thickness * 0.55f, cr2, cg2, cb2, 0.55f);

                // Вращающийся ромб-пояс вокруг торса цели.
                tPoly(q, m, 4, hw * 1.05f, hh * 0.42f, rot, thickness, cr, cg, cb, 0.85f);
                tPoly(q, m, 4, hw * 0.72f, hh * 0.28f, -rot * 1.4f, thickness * 0.7f, cr2, cg2, cb2, 0.6f);

                // Центральная точка-вспышка.
                dot(q, m, 0, 0, z, thickness * 0.9f, cr, cg, cb, 0.55f + 0.35f * flash);
            }
            case CROSSHAIR -> {
                float rad = Math.max(hw, hh) * 0.78f;
                float rot = (float) (sec * 0.35 * sp);
                gaps(q, m, rad, rot, w * 1.4f, cr, cg, cb, 0.95f);
                tPoly(q, m, 4, rad * 0.42f, rad * 0.42f, -rot * 2f, w, cr2, cg2, cb2, 0.6f);
                float t = rad * 0.24f, o = rad * 1.2f;
                tLine(q, m, o, 0, 0, o - t, 0, 0, w, cr, cg, cb, 0.85f);
                tLine(q, m, -o, 0, 0, -o + t, 0, 0, w, cr, cg, cb, 0.85f);
                tLine(q, m, 0, o, 0, 0, o - t, 0, w, cr, cg, cb, 0.85f);
                tLine(q, m, 0, -o, 0, 0, -o + t, 0, w, cr, cg, cb, 0.85f);
                dot(q, m, 0, 0, 0, w * 1.3f, cr, cg, cb, 0.9f);
            }
            case CHAINS -> {
                int levels = 3;
                int per = 11;
                float phase = (float) (sec * 0.45 * sp);
                for (int k = 0; k < levels; k++) {
                    float f = k / (float) (levels - 1);
                    float ly = (0.78f - 1.56f * f) * hh + 0.06f * hh * (float) Math.sin(sec * 1.1f * sp + k);
                    float lr = hw * 1.12f * (0.86f + 0.2f * (float) Math.cos((f - 0.5f) * Math.PI));
                    hRing(q, m, ly, lr, w * 0.4f, cr2, cg2, cb2, 0.16f);
                    int dir = (k & 1) == 0 ? 1 : -1;
                    for (int i = 0; i < per; i++) {
                        double ang = phase * dir + (Math.PI * 2 * i) / per;
                        boolean alt = (i & 1) == 1;
                        chainLink(q, m, (float) Math.cos(ang) * lr, ly, (float) Math.sin(ang) * lr,
                                (float) -Math.sin(ang), (float) Math.cos(ang), s, w, alt,
                                alt ? cr2 : cr, alt ? cg2 : cg, alt ? cb2 : cb, alt ? 0.55f : 0.92f);
                    }
                }
                dot(q, m, 0, 0, 0, w * 1.2f, cr, cg, cb, 0.85f);
            }
            case SOULS -> {
                int n = 6;
                float orbit = hw * 1.35f;
                float rot = (float) (sec * 0.55 * sp);
                for (int i = 0; i < n; i++) {
                    double ang = rot + (Math.PI * 2 * i) / n;
                    float ox = (float) Math.cos(ang) * orbit;
                    float oz = (float) Math.sin(ang) * orbit;
                    float oy = (float) Math.sin(sec * 1.25 * sp + i * 1.05) * hh * 0.8f;
                    org.joml.Vector3f L = toLocal(ox, oy, oz);
                    float lx = L.x, ly = L.y, lz = L.z;
                    soul(q, m, lx, ly, lz, s, w,
                            i % 2 == 0 ? cr : cr2, i % 2 == 0 ? cg : cg2, i % 2 == 0 ? cb : cb2, 0.9f);
                    org.joml.Vector3f d = toLocal(ox * 0.45f, oy * 0.45f, oz * 0.45f);
                    tLine(q, m, lx, ly, lz, d.x, d.y, d.z, w * 0.35f, cr2, cg2, cb2, 0.22f);
                }
                hRing(q, m, 0, orbit, w * 0.35f, cr2, cg2, cb2, 0.2f);
                tEllipse(q, m, 0, 0, hw * 0.9f, hh * 0.9f, 26, 0, w * 0.7f, cr, cg, cb, 0.35f);
                dot(q, m, 0, 0, 0, w * 1.4f, cr, cg, cb, 0.8f);
            }
            default -> {
                float range = hh * 1.08f;
                double prog = (sec * 0.5 * sp) % 2.0;
                float tri = prog < 1.0 ? (float) prog : (float) (2.0 - prog);
                float scanY = range * (1f - 2f * tri);
                float move = prog < 1.0 ? -1f : 1f;
                tLine(q, m, 0, -range, 0, 0, range, 0, w * 0.35f, cr2, cg2, cb2, 0.22f);
                for (int i = 3; i >= 1; i--) {
                    float gy = scanY - move * (range * 0.09f * i);
                    scanRing(q, m, gy, hw, range, w * 0.75f, cr, cg, cb, 0.9f - i * 0.24f);
                }
                scanRing(q, m, scanY, hw, range, w * 1.25f, cr, cg, cb, 0.95f);
                dot(q, m, 0, range, 0, w * 1.1f, cr2, cg2, cb2, 0.8f);
                dot(q, m, 0, -range, 0, w * 1.1f, cr2, cg2, cb2, 0.8f);
            }
        }
        ms.pop();
    }

    private static final org.joml.Quaternionf LOCAL_ROT = new org.joml.Quaternionf();
    private static final org.joml.Vector3f LOCAL_POS = new org.joml.Vector3f();

    /** Offset around the target body (world axes, metres) -> billboard-space coordinates. */
    private static org.joml.Vector3f toLocal(float x, float y, float z) {
        LOCAL_POS.set(x, y, z).rotate(LOCAL_ROT);
        LOCAL_POS.x = -LOCAL_POS.x;
        LOCAL_POS.y = -LOCAL_POS.y;
        return LOCAL_POS;
    }

    private static float lerp(float a, float b, float t) {
        float k = Math.min(1f, Math.max(0f, t));
        return a + (b - a) * k;
    }

    private static void scanRing(VertexConsumer q, Matrix4f m, float y, float bodyHalf, float range,
                                 float w, float r, float g, float b, float a) {
        float f = Math.min(1f, Math.max(0f, (y / range + 1f) * 0.5f));
        float profile = 0.5f + 0.5f * (float) Math.sin(Math.PI * (0.14f + 0.76f * f));
        float rx = bodyHalf * profile;
        tEllipse(q, m, 0, y, rx, rx * 0.26f, 24, 0, w, r, g, b, a);
        dot(q, m, -rx, y, 0, w * 1.1f, r, g, b, a);
        dot(q, m, rx, y, 0, w * 1.1f, r, g, b, a);
    }

    private static void chainLink(VertexConsumer q, Matrix4f m, float cx, float cy, float cz,
                                  float tx, float tz, float s, float w, boolean alt,
                                  float r, float g, float b, float a) {
        float ra = 0.115f * s, rb = 0.072f * s;
        float px = 0, py = 0, pz = 0;
        int segs = 12;
        for (int i = 0; i <= segs; i++) {
            double t = (Math.PI * 2 * i) / segs;
            float ca = (float) Math.cos(t), sa = (float) Math.sin(t);
            float ax, ay, az, bx, by, bz;
            if (alt) {
                ax = tx; ay = 0f; az = tz;
                bx = 0f; by = 1f; bz = 0f;
            } else {
                ax = 0f; ay = 1f; az = 0f;
                bx = tx; by = 0f; bz = tz;
            }
            org.joml.Vector3f L = toLocal(
                    cx + ca * ra * ax + sa * rb * bx,
                    cy + ca * ra * ay + sa * rb * by,
                    cz + ca * ra * az + sa * rb * bz);
            if (i > 0) tLine(q, m, px, py, pz, L.x, L.y, L.z, w, r, g, b, a);
            px = L.x; py = L.y; pz = L.z;
        }
    }

    private static void hRing(VertexConsumer q, Matrix4f m, float y, float radius,
                              float w, float r, float g, float b, float a) {
        int segs = 26;
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= segs; i++) {
            double t = (Math.PI * 2 * i) / segs;
            org.joml.Vector3f L = toLocal((float) Math.cos(t) * radius, y, (float) Math.sin(t) * radius);
            if (i > 0) tLine(q, m, px, py, pz, L.x, L.y, L.z, w, r, g, b, a);
            px = L.x; py = L.y; pz = L.z;
        }
    }

    private static void soul(VertexConsumer q, Matrix4f m, float x, float y, float z,
                             float s, float w, float r, float g, float b, float a) {
        float rr = 0.09f * s;
        dot(q, m, x, y, z, rr * 2.1f, r, g, b, a * 0.2f);
        dot(q, m, x, y, z, rr * 1.15f, r, g, b, a * 0.5f);
        dot(q, m, x, y, z, rr * 0.55f, 1f, 1f, 1f, a);
        tLine(q, m, x, y, z, x, y + rr * 2.6f, z, rr * 0.4f, r, g, b, a * 0.35f);
    }

    private static void tLine(VertexConsumer q, Matrix4f m,
                              float x0, float y0, float z0, float x1, float y1, float z1,
                              float w, float r, float g, float b, float a) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-5f) return;
        float nx = -dy / len * w * 0.5f;
        float ny = dx / len * w * 0.5f;
        quad(q, m,
                x0 + nx, y0 + ny, z0, x1 + nx, y1 + ny, z1,
                x1 - nx, y1 - ny, z1, x0 - nx, y0 - ny, z0, r, g, b, a);
    }

    /** Шеврон: вершина в (cx,cy), плечи уходят в endY = cy - dir*arm. dir<0 → «Λ», dir>0 → «V». */
    private static void chevron(VertexConsumer q, Matrix4f m, float cx, float cy, float z,
                                float size, float arm, float w, float dir,
                                float r, float g, float b, float a) {
        float endY = cy - dir * arm;
        tLine(q, m, cx - size, endY, z, cx, cy, z, w, r, g, b, a);
        tLine(q, m, cx, cy, z, cx + size, endY, z, w, r, g, b, a);
        dot(q, m, cx, cy, z, w * 0.62f, r, g, b, a);
        dot(q, m, cx - size, endY, z, w * 0.5f, r, g, b, a * 0.85f);
        dot(q, m, cx + size, endY, z, w * 0.5f, r, g, b, a * 0.85f);
    }

    private static void tEllipse(VertexConsumer q, Matrix4f m, float cx, float cy,
                                 float rx, float ry, int segs, float rot, float w,
                                 float r, float g, float b, float a) {
        float ca = (float) Math.cos(rot), sa = (float) Math.sin(rot);
        float px = 0, py = 0;
        for (int i = 0; i <= segs; i++) {
            double t = (Math.PI * 2 * i) / segs;
            double ex = Math.cos(t) * rx, ey = Math.sin(t) * ry;
            float x = cx + (float) (ex * ca - ey * sa);
            float y = cy + (float) (ex * sa + ey * ca);
            if (i > 0) tLine(q, m, px, py, 0, x, y, 0, w, r, g, b, a);
            px = x; py = y;
        }
    }

    private static void tPoly(VertexConsumer q, Matrix4f m, int n, float rx, float ry, float rot,
                              float w, float r, float g, float b, float a) {
        float px = 0, py = 0;
        for (int i = 0; i <= n; i++) {
            double t = rot + (Math.PI * 2 * i) / n;
            float x = (float) (Math.cos(t) * rx), y = (float) (Math.sin(t) * ry);
            if (i > 0) tLine(q, m, px, py, 0, x, y, 0, w, r, g, b, a);
            px = x; py = y;
        }
    }

    private static void gaps(VertexConsumer q, Matrix4f m, float rad, float rot, float w,
                             float r, float g, float b, float a) {
        int segs = 44;
        for (int i = 0; i < segs; i++) {
            if (i % 11 < 2) continue;
            double t0 = rot + (Math.PI * 2 * i) / segs;
            double t1 = rot + (Math.PI * 2 * (i + 1)) / segs;
            tLine(q, m,
                    (float) (Math.cos(t0) * rad), (float) (Math.sin(t0) * rad), 0,
                    (float) (Math.cos(t1) * rad), (float) (Math.sin(t1) * rad), 0, w, r, g, b, a);
        }
    }

    private static void dot(VertexConsumer q, Matrix4f m, float cx, float cy, float cz, float radius,
                            float r, float g, float b, float a) {
        float rr = Math.max(0.002f, radius);
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            double t0 = (Math.PI * 2 * i) / segs;
            double t1 = (Math.PI * 2 * (i + 1)) / segs;
            quad(q, m,
                    cx, cy, cz,
                    cx + (float) Math.cos(t0) * rr, cy + (float) Math.sin(t0) * rr, cz,
                    cx + (float) Math.cos(t1) * rr, cy + (float) Math.sin(t1) * rr, cz,
                    cx + (float) Math.cos(t1) * rr, cy + (float) Math.sin(t1) * rr, cz, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer q, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        q.vertex(m, x0, y0, z0).color(r, g, b, a);
        q.vertex(m, x1, y1, z1).color(r, g, b, a);
        q.vertex(m, x2, y2, z2).color(r, g, b, a);
        q.vertex(m, x3, y3, z3).color(r, g, b, a);
        q.vertex(m, x3, y3, z3).color(r, g, b, a);
        q.vertex(m, x2, y2, z2).color(r, g, b, a);
        q.vertex(m, x1, y1, z1).color(r, g, b, a);
        q.vertex(m, x0, y0, z0).color(r, g, b, a);
    }
}
