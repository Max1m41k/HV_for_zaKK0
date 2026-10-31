package com.visualmod.util;

import com.visualmod.util.McCompat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Встроенный эффект «GUIvisualopen»: при открытии нашего GUI от игрока во все стороны расходится
 * волна подсветки верхних блоков в радиусе 30 блоков. Каждый блок вспыхивает, когда фронт доходит
 * до него, и гаснет примерно за секунду — просто визуальный след, ничего не подсвечивает навечно.
 */
public final class GuiWave {
    private GuiWave() {}

    /** Дальность волны в блоках. */
    public static double radius = 30.0;
    /** Скорость фронта, блоков в секунду. */
    public static double speed = 26.0;
    /** Сколько держится подсветка уже тронутого блока. */
    public static long lifeMs = 700L;
    public static int color = HudStyle.ACCENT;

    private static final int MAX_QUADS = 4096;
    private static final BlockPos.Mutable MPOS = new BlockPos.Mutable();

    private static double ox, oy, oz;
    private static long startMs;
    private static boolean running;
    /** Игрок под кровлей — волна идёт по его уровню, а не по карте высот. */
    private static boolean cave;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx));
    }

    /** Запускает волну из текущей позиции игрока. Вызывается при открытии ClickGUI. */
    public static void trigger() {
        try {
            // init() у экрана вызывается и на ресайз окна — не перезапускать волну, пока старая жива.
            if (running && System.currentTimeMillis() - startMs < 400L) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;
            Vec3d p = McCompat.pos(mc.player);
            ox = p.x; oy = p.y; oz = p.z;
            int surface = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                    MathHelper.floor(p.x), MathHelper.floor(p.z));
            cave = p.y < surface - 1.0;
            startMs = System.currentTimeMillis();
            running = true;
        } catch (Throwable t) {
            Diag.once("GuiWave.trigger", t);
        }
    }

    public static void render(WorldRenderContext ctx) {
        try {
            if (!running || IrisCompat.skipWorldPass()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            Camera cam = ctx.gameRenderer().getCamera();
            MatrixStack ms = ctx.matrices();
            VertexConsumerProvider consumers = ctx.consumers();
            if (mc.world == null || cam == null || ms == null || consumers == null) return;

            double reach = Math.max(4.0, radius);
            double band = Math.max(1.0, speed * lifeMs / 1000.0);
            double front = speed * (System.currentTimeMillis() - startMs) / 1000.0;
            if (front - band > reach) {
                running = false;
                return;
            }

            Vec3d c = cam.getCameraPos();
            int r = MathHelper.ceil(reach);
            int cx = MathHelper.floor(ox), cz = MathHelper.floor(oz);
            int bottom = MathHelper.floor(Math.max(oy - 4, mc.world.getBottomY()));
            int top = MathHelper.floor(Math.min(oy + r + 4, mc.world.getHeight() - 1));

            float cr = WorldRenderUtil.colorR(color), cg = WorldRenderUtil.colorG(color), cb = WorldRenderUtil.colorB(color);
            VertexConsumer buf = consumers.getBuffer(RenderLayers.debugQuads());

            ms.push();
            ms.translate(-c.x, -c.y, -c.z);
            Matrix4f mat = ms.peek().getPositionMatrix();

            int drawn = 0;
            for (int z = cz - r; z <= cz + r && drawn < MAX_QUADS; z++) {
                double dz = z + 0.5 - oz;
                for (int x = cx - r; x <= cx + r && drawn < MAX_QUADS; x++) {
                    double dx = x + 0.5 - ox;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > front || d < front - band) continue;
                    int y = surfaceY(mc, x, z, bottom, top);
                    if (y < bottom || y > top) continue;
                    // u=0 — блок только что тронут фронтом, u=1 — пора гаснуть.
                    float u = (float) ((front - d) / band);
                    float a = (1f - u) * (1f - u) * 0.55f;
                    if (a <= 0.02f) continue;
                    float wy = y + 1.002f;
                    WorldRenderUtil.quad(buf, mat,
                            x, wy, z,
                            x + 1f, wy, z,
                            x + 1f, wy, z + 1f,
                            x, wy, z + 1f,
                            cr, cg, cb, a);
                    drawn++;
                }
            }
            ms.pop();
        } catch (Throwable t) {
            Diag.once("GuiWave.render", t);
        }
    }

    /** Верх колонки: карта высот; под землёй берём уровень игрока, иначе волна уйдёт на поверхность. */
    private static int surfaceY(MinecraftClient mc, int x, int z, int bottom, int top) {
        int y;
        try {
            y = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        } catch (Throwable t) {
            Diag.once("GuiWave.top", t);
            return top;
        }
        if (cave) y = Math.min(y, MathHelper.floor(oy) + 1);
        return MathHelper.clamp(Math.min(y, top), bottom, top);
    }
}
