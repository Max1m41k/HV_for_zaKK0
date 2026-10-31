package com.visualmod.util;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkCache;
import org.joml.Matrix4f;

/**
 * «Максимальная концентрация». Один проход на BEFORE_ENTITIES: белый экран-пелена, затем
 * чёрные кубы по всем видимым блокам вокруг (обходим столбцы от поверхности вниз) и белая
 * окантовка поверх ближней зоны. Сущности рисуются после, поэтому игроки остаются поверх пелены.
 * За радиусом мир растворяется в белом — это делает AnimeFocusFogMixin через туман.
 *
 * Глубина настоящая: кубы проходят обычный depth-test, сквозь блоки ничего не видно — это не X-Ray.
 */
public final class FocusVeil {
    private FocusVeil() {}

    /** Чистый белый квадрат ванили — текстура для «белой фигурки» игрока. */
    public static final Identifier FIGURE_TEXTURE = Identifier.ofVanilla("textures/misc/white.png");

    // Настройки, к которым привязан модуль AnimeFocus.
    public static int skyColor = 0xFFFFFF;
    public static int blockColor = 0x07070B;
    public static int outlineColor = 0xFFFFFF;
    public static double range = 20.0;
    public static boolean outlines = true;
    public static boolean whiteFigures = true;

    private static final long FADE_IN_MS = 120;
    private static final long FADE_OUT_MS = 260;
    private static final float SKY_DIST = 0.20f;   // пелена за ближней плоскостью (0.05), но перед миром
    private static final float SKY_HALF = 1.70f;   // покрывает до ~83° полуугла — любой FOV
    private static final int MAX_BOXES = 5000;
    /** Сколько блоков столбца проверяем вниз от поверхности: дальше блоки уже не видны. */
    private static final int SHELL = 10;

    private static boolean fadeTarget;
    private static long fadeStart;

    /** Флаг для рендера сущностей: текущая модель рисуется белой. Ставится из миксина. */
    private static boolean figureWhite;

    private static final BlockPos.Mutable MPOS = new BlockPos.Mutable();
    private static final int[] BX = new int[MAX_BOXES];
    private static final int[] BY = new int[MAX_BOXES];
    private static final int[] BZ = new int[MAX_BOXES];

    private static boolean moduleOn() {
        try {
            if (VisualsMod.moduleManager == null) return false;
            com.visualmod.module.modules.render.AnimeFocus m =
                    VisualsMod.moduleManager.getModule(com.visualmod.module.modules.render.AnimeFocus.class);
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            Diag.once("FocusVeil.moduleOn", t);
            return false;
        }
    }

    /** Текущая сила эффекта 0..1; плавно нарастает при включении и гаснет при выключении. */
    public static float strength() {
        boolean on = moduleOn();
        long now = System.currentTimeMillis();
        if (on != fadeTarget) {
            fadeTarget = on;
            fadeStart = now;
        }
        if (on) return ramp(now - fadeStart, FADE_IN_MS);
        return 1f - ramp(now - fadeStart, FADE_OUT_MS);
    }

    private static float ramp(long dt, long span) {
        if (dt <= 0L) return 0f;
        return dt >= span ? 1f : (float) dt / (float) span;
    }

    // ------------------------------------------------- белая фигурка игрока

    /** Миксин спрашивает перед каждой живой сущностью: красить ли её модель в белое. */
    public static boolean whiteFigureRequested() {
        return whiteFigures && strength() > 0.05f;
    }

    public static void markFigure(boolean white) {
        figureWhite = white;
    }

    public static boolean figureWhite() {
        return figureWhite;
    }

    /** Цвет тумана за радиусом — тот же, что и пелена, поэтому мир плавно растворяется в белом. */
    public static float[] fogTint() {
        return new float[]{WorldRenderUtil.colorR(skyColor),
                WorldRenderUtil.colorG(skyColor), WorldRenderUtil.colorB(skyColor)};
    }

    // ------------------------------------------------------------- рендер

    public static void render(WorldRenderContext ctx) {
        try {
            if (IrisCompat.skipWorldPass()) return;
            float k = strength();
            if (k <= 0.01f) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            Camera cam = ctx.gameRenderer().getCamera();
            MatrixStack ms = ctx.matrices();
            VertexConsumerProvider consumers = ctx.consumers();
            if (mc.world == null || cam == null || ms == null || consumers == null) return;

            Vec3d c = cam.getCameraPos();
            int count = collect(mc, c);

            ms.push();
            ms.translate(-c.x, -c.y, -c.z);
            Matrix4f mat = ms.peek().getPositionMatrix();

            VertexConsumer quads = consumers.getBuffer(RenderLayers.debugQuads());
            drawSky(quads, mat, cam, c, k);

            float br = WorldRenderUtil.colorR(blockColor), bg = WorldRenderUtil.colorG(blockColor),
                    bb = WorldRenderUtil.colorB(blockColor);
            double reach = Math.max(1.0, range);
            double fadeFrom = reach * 0.85;
            for (int i = 0; i < count; i++) {
                double dx = BX[i] + 0.5 - c.x, dy = BY[i] + 0.5 - c.y, dz = BZ[i] + 0.5 - c.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float a = (float) MathHelper.clamp((reach - d) / (reach - fadeFrom), 0.0, 1.0) * k;
                if (a <= 0.01f) continue;
                WorldRenderUtil.drawFilledBox(quads, mat, blockBox(i, 0.004), br, bg, bb, a);
            }

            if (!outlines || count == 0) {
                ms.pop();
                return;
            }
            float or = WorldRenderUtil.colorR(outlineColor), og = WorldRenderUtil.colorG(outlineColor),
                    ob = WorldRenderUtil.colorB(outlineColor);
            // Второй слой — только после того, как первый полностью отрисован в буфер.
            VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());
            for (int i = 0; i < count; i++) {
                double dx = BX[i] + 0.5 - c.x, dy = BY[i] + 0.5 - c.y, dz = BZ[i] + 0.5 - c.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                // Окантовку рисуем только в ближней зоне: на дальней она превращается в кашу
                // и стоит десятков тысяч лишних вершин.
                if (d > reach * 0.78) continue;
                float a = (float) MathHelper.clamp((reach - d) / (reach - fadeFrom), 0.0, 1.0) * k * 0.85f;
                if (a <= 0.02f) continue;
                WorldRenderUtil.drawOutlinedBox(lines, mat, blockBox(i, 0.008), or, og, ob, a);
            }
            ms.pop();
        } catch (Throwable t) {
            Diag.once("FocusVeil.render", t);
        }
    }

    /** Белая пелена вплотную к камере: закрывает и небо, и ландшафт, не трогая буфер глубины. */
    private static void drawSky(VertexConsumer buf, Matrix4f mat, Camera cam, Vec3d c, float k) {
        double yaw = cam.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double pitch = cam.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        double fx = -sy * cp, fy = -sp, fz = cy * cp;
        double rx = -cy, rz = -sy;                                 // «вправо» от камеры
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right × forward

        double bx = c.x + fx * SKY_DIST, by = c.y + fy * SKY_DIST, bz = c.z + fz * SKY_DIST;
        double ax = rx * SKY_HALF, az = rz * SKY_HALF;
        double ex = ux * SKY_HALF, ey = uy * SKY_HALF, ez = uz * SKY_HALF;

        WorldRenderUtil.quad(buf, mat,
                (float) (bx - ax - ex), (float) (by - ey), (float) (bz - az - ez),
                (float) (bx + ax - ex), (float) (by - ey), (float) (bz + az - ez),
                (float) (bx + ax + ex), (float) (by + ey), (float) (bz + az + ez),
                (float) (bx - ax + ex), (float) (by + ey), (float) (bz - az + ez),
                WorldRenderUtil.colorR(skyColor), WorldRenderUtil.colorG(skyColor),
                WorldRenderUtil.colorB(skyColor), k);
    }

    /**
     * Собирает открытые блоки столбцами: от поверхности колонки идём вниз, пока блоки касаются
     * открытого пространства. Сплошная порода в глубине обрывается — её всё равно не видно.
     */
    private static int collect(MinecraftClient mc, Vec3d c) {
        int r = MathHelper.clamp((int) Math.round(range), 4, 32);
        int cx = MathHelper.floor(c.x), cz = MathHelper.floor(c.z);
        int top = MathHelper.floor(c.y) + r, bottom = MathHelper.floor(c.y) - r;
        ChunkCache cache;
        try {
            cache = new ChunkCache(mc.world, new BlockPos(cx - r, bottom, cz - r), new BlockPos(cx + r, top, cz + r));
        } catch (Throwable t) {
            Diag.once("FocusVeil.cache", t);
            return 0;
        }
        double r2 = (r + 0.5) * (r + 0.5);
        int n = 0;
        for (int z = cz - r; z <= cz + r && n < MAX_BOXES; z++) {
            double dz = z + 0.5 - c.z;
            for (int x = cx - r; x <= cx + r && n < MAX_BOXES; x++) {
                double dx = x + 0.5 - c.x;
                if (dx * dx + dz * dz > r2) continue;
                int y = columnTop(mc, x, z, bottom, top, MathHelper.floor(c.y) + 2);
                int buried = 0;
                for (int i = 0; i < SHELL && y >= bottom && n < MAX_BOXES; i++, y--) {
                    if (cache.getBlockState(MPOS.set(x, y, z)).isAir()) { buried = 0; continue; }
                    if (!exposed(cache, x, y, z)) {
                        if (++buried > 1) break;
                        continue;
                    }
                    buried = 0;
                    BX[n] = x; BY[n] = y; BZ[n] = z;
                    n++;
                }
            }
        }
        return n;
    }

    /** Верх колонки: карта высот, но если игрок в пещере — стартуем от его уровня. */
    private static int columnTop(MinecraftClient mc, int x, int z, int bottom, int top, int playerCeil) {
        int y = top;
        try {
            y = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        } catch (Throwable t) {
            Diag.once("FocusVeil.top", t);
        }
        return MathHelper.clamp(Math.max(y, playerCeil), bottom, top);
    }

    /** Хоть один сосед не сплошной: такие блоки действительно видно, внутренние рисовать незачем. */
    private static boolean exposed(ChunkCache cache, int x, int y, int z) {
        return !solid(cache, x + 1, y, z) || !solid(cache, x - 1, y, z)
                || !solid(cache, x, y + 1, z) || !solid(cache, x, y - 1, z)
                || !solid(cache, x, y, z + 1) || !solid(cache, x, y, z - 1);
    }

    private static boolean solid(ChunkCache cache, int x, int y, int z) {
        return cache.getBlockState(MPOS.set(x, y, z)).isOpaque();
    }

    private static Box blockBox(int i, double pad) {
        return new Box(BX[i], BY[i], BZ[i], BX[i] + 1.0, BY[i] + 1.0, BZ[i] + 1.0).expand(pad);
    }
}
