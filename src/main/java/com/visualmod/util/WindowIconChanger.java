package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Значок окна и кнопки на панели задач.
 *
 * Ваниль ставит свою иконку внутри конструктора MinecraftClient, а client.execute() на этом же
 * треде выполняется сразу, то есть ещё до ванили — её вызов перезаписывал наш. Поэтому иконку
 * навешиваем с тика клиента: к первому тику конструктор уже отработал. Картинки отдаём списком
 * размеров, чтобы Windows брала честные 16 px в заголовке окна и в диспетчере задач, а не
 * растянутый 256.
 */
public final class WindowIconChanger {

    private static final String[] HV_ICONS = {
            "/assets/visualmod/icons/hv_icon_16.png",
            "/assets/visualmod/icons/hv_icon_32.png",
            "/assets/visualmod/icons/hv_icon_48.png",
            "/assets/visualmod/icons/hv_icon.png",
    };

    /** Сколько раз навесить иконку просто так: первый тик и одна перестановка через 3 секунды. */
    private static final long REASSERT_MS = 3000L;

    private static GLFWImage.Buffer images;
    private static List<ByteBuffer> pixelKeepAlive;
    private static boolean broken;

    private static long appliedHandle;
    private static int applied;
    private static long appliedAtMs;

    private WindowIconChanger() {}

    public static void tick(MinecraftClient client) {
        Window window = client.getWindow();
        long handle = window.getHandle();
        if (handle == 0L) return;

        if (handle == appliedHandle) {
            if (applied >= 2) return;
            if (applied == 1 && System.currentTimeMillis() - appliedAtMs < REASSERT_MS) return;
        }

        GLFWImage.Buffer buffer = images();
        if (buffer == null) return;
        try {
            GLFW.glfwSetWindowIcon(handle, buffer);
            appliedHandle = handle;
            applied++;
            appliedAtMs = System.currentTimeMillis();
        } catch (Throwable t) {
            Diag.once("WindowIconChanger", t);
        }
    }

    private static GLFWImage.Buffer images() {
        if (images != null || broken) return images;

        List<ByteBuffer> decoded = new ArrayList<>(HV_ICONS.length);
        List<int[]> dims = new ArrayList<>(HV_ICONS.length);
        for (String path : HV_ICONS) {
            ByteBuffer packed = null;
            try (InputStream is = WindowIconChanger.class.getResourceAsStream(path)) {
                if (is == null) continue;
                byte[] bytes = is.readAllBytes();
                packed = MemoryUtil.memAlloc(bytes.length);
                packed.put(bytes).flip();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer comp = stack.mallocInt(1);
                    ByteBuffer pixels = STBImage.stbi_load_from_memory(packed, w, h, comp, 4);
                    if (pixels == null) continue;
                    decoded.add(pixels);
                    dims.add(new int[]{w.get(0), h.get(0)});
                }
            } catch (Throwable t) {
                Diag.once("WindowIconChanger.load", t);
            } finally {
                if (packed != null) MemoryUtil.memFree(packed);
            }
        }

        if (decoded.isEmpty()) {
            broken = true;
            return null;
        }
        GLFWImage.Buffer buffer = GLFWImage.malloc(decoded.size());
        for (int i = 0; i < decoded.size(); i++) {
            buffer.get(i).set(dims.get(i)[0], dims.get(i)[1], decoded.get(i));
        }
        pixelKeepAlive = decoded;
        images = buffer;
        return buffer;
    }
}
