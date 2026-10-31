package com.visualmod.util;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.opengl.GL11;

/**
 * Настоящее «мыльное стекло» для HUD-панелей: кадр мира ужимается в две маленькие
 * промежуточные framebuffer-картинки (линейная выборка = размытие), а затем размытая
 * картинка растягивается обратно только внутри прямоугольника панели (ножницы).
 *
 * С 1.21.11 прямых FBO-ручек (Framebuffer.fbo, glBlitFramebuffer) больше нет, поэтому
 * каждый перенос делается полноценным render-проходом blaze3d: пайплайн TRACY_BLIT
 * (тот же screenquad + blit_screen, что у ванильного Framebuffer#drawBlit, но без
 * смешивания) рисует fullscreen-треугольник из исходной текстуры в целевую.
 *
 * Всё намеренно обёрнуто в try/catch с флагом {@link #broken}: если драйвер или
 * другой мод испортят состояние GL, стекло просто перестаёт размывать и остаётся
 * обычная полупрозрачная заливка, как было раньше.
 */
public final class GlassBlur {

    /** Можно выключить совсем (тогда GLASS = прежняя плоская заливка). */
    public static boolean enabled = true;

    private static final int FIRST_STEP = 6;
    private static final int SECOND_STEP = 3;
    private static final long REFRESH_NANOS = 20_000_000L;

    private static SimpleFramebuffer first;
    private static SimpleFramebuffer second;
    private static int madeForW;
    private static int madeForH;
    private static long capturedAt;
    private static boolean broken;
    /** В прошлом кадре стекло рисовали — в этом снимаем мир сразу, до всей остальной HUD-отрисовки. */
    private static boolean usedLastFrame;

    private GlassBlur() {}

    /** Вызывается из миксина в начале кадра HUD: чтобы размытие брало мир, а не уже нарисованный интерфейс. */
    public static void newFrame() {
        boolean wanted = enabled && !broken && usedLastFrame && !IrisCompat.shaderActive();
        usedLastFrame = false;
        capturedAt = 0L;
        if (!wanted) return;
        try {
            refresh();
        } catch (Throwable t) {
            broken = true;
            release();
            Diag.once("GlassBlur.frame", t);
        }
    }

    /**
     * Размыть картинку мира внутри прямоугольника панели.
     *
     * @param inset на сколько экранных пикселей отступить от краёв: угол панели скруглён,
     *              а прямоугольный проход не знает про скругление, поэтому мыло не должно
     *              вылезать за угол
     * @return true, если размытие действительно нарисовано (тогда заливку делают темнее)
     */
    public static boolean drawRegion(DrawContext ctx, float x, float y, float w, float h, float inset) {
        // Под шейдер-паком кадр мира лежит не в том буфере, куда мы пишем, —
        // вместо стекла остаёмся плоской полупрозрачной заливкой.
        if (!enabled || broken || ctx == null || IrisCompat.shaderActive()) return false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null || mc.getFramebuffer() == null) return false;
            if (!refresh()) return false;
            if (second == null) return false;

            int gx = (int) Math.floor(x + inset);
            int gy = (int) Math.floor(y + inset);
            int gx2 = (int) Math.ceil(x + w - inset);
            int gy2 = (int) Math.ceil(y + h - inset);
            if (gx2 - gx < 4 || gy2 - gy < 4) return false;

            Framebuffer main = mc.getFramebuffer();
            GpuTextureView target = main.getColorAttachmentView();
            if (target == null) return false;

            // Ножницы прохода — в пикселях целевого буфера, начало координат в НИЖНЕМ левом
            // углу (так же пересчитывает ванильный GuiRenderer#enableScissor).
            int sf = Math.max(1, mc.getWindow().getScaleFactor());
            int px = gx * sf;
            int py = main.textureHeight - gy2 * sf;
            int pw = (gx2 - gx) * sf;
            int ph = (gy2 - gy) * sf;

            blit(second.getColorAttachmentView(), target, px, py, pw, ph);
            // Чужие моды могли оставить GL-ошибку, поэтому здесь её не проверяем:
            // достаточно исключения, а ошибку снятия кадров проверяем в refresh().
            usedLastFrame = true;
            return true;
        } catch (Throwable t) {
            broken = true;
            release();
            Diag.once("GlassBlur", t);
            return false;
        }
    }

    /** Пересобрать промежуточные буферы и снять кадр, если он устарел или изменился размер окна. */
    private static boolean refresh() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (main == null) return false;
        int fw = main.textureWidth;
        int fh = main.textureHeight;
        if (fw < 32 || fh < 32) return false;

        long now = System.nanoTime();
        boolean sizeChanged = first == null || second == null || madeForW != fw || madeForH != fh;
        if (!sizeChanged && now - capturedAt < REFRESH_NANOS) return true;

        int w1 = Math.max(16, fw / FIRST_STEP);
        int h1 = Math.max(16, fh / FIRST_STEP);
        int w2 = Math.max(12, w1 / SECOND_STEP);
        int h2 = Math.max(12, h1 / SECOND_STEP);
        if (sizeChanged) {
            release();
            first = new SimpleFramebuffer("hv_glass1", w1, h1, false);
            second = new SimpleFramebuffer("hv_glass2", w2, h2, false);
            madeForW = fw;
            madeForH = fh;
        }

        GpuTextureView mainView = main.getColorAttachmentView();
        if (mainView == null || first.getColorAttachmentView() == null
                || second.getColorAttachmentView() == null) return false;
        blit(mainView, first.getColorAttachmentView(), 0, 0, 0, 0);
        blit(first.getColorAttachmentView(), second.getColorAttachmentView(), 0, 0, 0, 0);

        capturedAt = now;
        if (GL11.glGetError() != GL11.GL_NO_ERROR) {
            broken = true;
            release();
            return false;
        }
        return true;
    }

    /**
     * Перенести одну текстуру в другую целиком (если ширина или высота равны нулю)
     * либо только внутри прямоугольника ножниц.
     */
    private static void blit(GpuTextureView source, GpuTextureView target, int x, int y, int width, int height) {
        if (source == null || target == null) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "hv_glass", target, OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            if (width > 0 && height > 0) pass.enableScissor(x, y, width, height);
            pass.draw(0, 3);
        }
    }

    public static void release() {
        if (first != null) {
            try { first.delete(); } catch (Throwable ignored) {}
            first = null;
        }
        if (second != null) {
            try { second.delete(); } catch (Throwable ignored) {}
            second = null;
        }
        madeForW = 0;
        madeForH = 0;
        capturedAt = 0L;
    }
}
