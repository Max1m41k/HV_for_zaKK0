package com.visualmod.util;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Обложка текущего трека. Ищем картинку с тем же именем рядом с аудио (track.png, cover.jpg,
 * папка covers/), а если её нет — вложенное изображение внутри самого файла (ID3 APIC, RIFF-метаданные).
 * Читаем и пересобираем в PNG вне игрового потока, текстурой всё становится на клиентском потоке.
 */
public final class AlbumArt {
    private AlbumArt() {}

    private record Art(byte[] png, int avgColor) {}

    private static final Identifier ID = Identifier.of("visualmod", "album_cover");
    private static final int MAX_SIDE = 420;
    private static final String[] IMG_EXT = {".png", ".jpg", ".jpeg", ".gif", ".bmp"};
    private static final int SCAN_WINDOW = 6 * 1024 * 1024;

    private static String trackKey = "";
    private static int registeredFor = -1;
    private static boolean loading;
    private static int texW, texH;
    private static int avg;

    /** Средний тон обложки (ARGB) или 0, если обложки нет. */
    public static int avgColor() { return avg; }

    public static boolean ready() { return registeredFor >= 0 && texW > 0 && texH > 0; }

    /** Вызывается каждый кадр: задача ставится только когда трек реально сменился. */
    public static void setTrack(File audio) {
        String key = audio == null || !audio.isFile() ? "" : audio.getAbsolutePath();
        if (key.equals(trackKey)) return;
        // Пока предыдущая картинка читается, метку не трогаем — иначе результат станет неактуальным
        // и новый трек остался бы без обложки до следующей смены.
        if (loading) return;
        trackKey = key;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (key.isEmpty()) { unload(mc); return; }
        loading = true;
        int stamp = key.hashCode();
        Path src = audio.toPath();
        CompletableFuture.supplyAsync(() -> find(src)).thenAccept(a -> mc.execute(() -> {
            loading = false;
            if (!key.equals(trackKey)) return;
            if (a == null || !register(mc, a, stamp)) unload(mc);
        }));
    }

    private static void unload(MinecraftClient mc) {
        if (registeredFor >= 0 && mc != null && mc.getTextureManager() != null) {
            mc.getTextureManager().destroyTexture(ID);
        }
        registeredFor = -1; texW = 0; texH = 0; avg = 0;
    }

    private static boolean register(MinecraftClient mc, Art a, int stamp) {
        try {
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(a.png()));
            TextureManager tm = mc.getTextureManager();
            if (registeredFor >= 0) tm.destroyTexture(ID);
            texW = ni.getWidth(); texH = ni.getHeight();
            tm.registerTexture(ID, new NativeImageBackedTexture(ID::toString, ni));
            registeredFor = stamp;
            avg = a.avgColor();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Cover-fit: картинка заполняет прямоугольник целиком, лишнее обрезается с двух сторон. */
    public static void draw(DrawContext ctx, int x, int y, int w, int h) {
        if (!ready() || w <= 0 || h <= 0) return;
        double scale = Math.max(w / (double) texW, h / (double) texH);
        int rw = Math.min(texW, (int) Math.round(w / scale));
        int rh = Math.min(texH, (int) Math.round(h / scale));
        float u = (texW - rw) / 2f, v = (texH - rh) / 2f;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ID, x, y, u, v, w, h, rw, rh, texW, texH);
    }

    private static Art find(Path audio) {
        try {
            File dir = audio.getParent() == null ? new File(".") : audio.getParent().toFile();
            String name = audio.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            File[] cand = {new File(dir, base + ".png"), new File(dir, base + ".jpg"),
                    new File(dir, base + ".jpeg"), new File(dir, base + ".gif"), new File(dir, base + ".bmp"),
                    new File(new File(dir, "covers"), base + ".png"), new File(new File(dir, "covers"), base + ".jpg"),
                    new File(dir, "cover.png"), new File(dir, "cover.jpg"), new File(dir, "folder.jpg")};
            for (File f : cand) {
                if (f.isFile()) {
                    Art a = fromImage(f);
                    if (a != null) return a;
                }
            }
            byte[] emb = embedded(audio.toFile());
            return emb == null ? null : fromBytes(emb);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Art fromImage(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            return img == null ? null : encode(img);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Art fromBytes(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            return img == null ? null : encode(img);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Art encode(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        int nw = w, nh = h;
        int big = Math.max(w, h);
        if (big > MAX_SIDE) {
            double k = MAX_SIDE / (double) big;
            nw = Math.max(1, (int) Math.round(w * k));
            nh = Math.max(1, (int) Math.round(h * k));
        }
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "png", bos)) return null;
            return new Art(bos.toByteArray(), averageColor(out));
        } catch (Throwable e) {
            return null;
        }
    }

    private static int averageColor(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int sw = Math.min(32, w), sh = Math.min(32, h);
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int rgb = img.getRGB(Math.min(w - 1, x * w / sw), Math.min(h - 1, y * h / sh));
                if ((rgb >>> 24) < 32) continue;
                r += (rgb >> 16) & 255; g += (rgb >> 8) & 255; b += rgb & 255;
                n++;
            }
        }
        if (n == 0) return 0;
        int ar = (int) (r / n), ag = (int) (g / n), ab = (int) (b / n);
        double lum = (0.299 * ar + 0.587 * ag + 0.114 * ab) / 255.0;
        if (lum < 0.45) {                       // тёмная обложка: иначе акцент сольётся с панелью
            double k = 0.45 / Math.max(0.08, lum);
            ar = (int) Math.min(255, ar * k); ag = (int) Math.min(255, ag * k); ab = (int) Math.min(255, ab * k);
        }
        int mx = Math.max(ar, Math.max(ag, ab)), mn = Math.min(ar, Math.min(ag, ab));
        if (mx - mn < 26) {                     // почти серый кадр — подмешиваем фирменный оранжевый
            ar = (ar * 2 + 255) / 3; ag = (ag * 2 + 136) / 3; ab = (ab * 2 + 0) / 3;
        }
        return 0xFF000000 | (clamp(ar) << 16) | (clamp(ag) << 8) | clamp(ab);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Вложенная картинка: ищем JPEG/PNG в начале файла и в его хвосте (теги бывают с обеих сторон). */
    private static byte[] embedded(File audio) {
        long len = audio.length();
        if (len < 2048 || len > 128L * 1024 * 1024) return null;
        byte[] head = readWindow(audio, 0, (int) Math.min(SCAN_WINDOW, len));
        byte[] found = pickImage(head);
        if (found != null) return found;
        if (len > SCAN_WINDOW) {
            byte[] tail = readWindow(audio, len - SCAN_WINDOW, SCAN_WINDOW);
            found = pickImage(tail);
            if (found != null) return found;
        }
        return null;
    }

    private static byte[] readWindow(File f, long offset, int size) {
        byte[] buf = new byte[size];
        int got = 0;
        try (InputStream in = new FileInputStream(f)) {
            if (offset > 0) in.skip(offset);
            while (got < size) {
                int n = in.read(buf, got, size - got);
                if (n <= 0) break;
                got += n;
            }
        } catch (Throwable e) {
            return null;
        }
        return got == size ? buf : Arrays.copyOf(buf, got);
    }

    private static final byte[] PNG_END = {0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    private static byte[] pickImage(byte[] b) {
        if (b == null || b.length < 64) return null;
        int jpg = indexOf(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0);
        if (jpg >= 0) {
            int end = lastIndexOf(b, new byte[]{(byte) 0xFF, (byte) 0xD9}, jpg + 3);
            if (end > jpg) return slice(b, jpg, end + 2);
        }
        int png = indexOf(b, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 0);
        if (png >= 0) {
            int end = lastIndexOf(b, PNG_END, png + 8);
            if (end > png) return slice(b, png, end + PNG_END.length);
        }
        return null;
    }

    private static byte[] slice(byte[] b, int from, int to) {
        to = Math.min(to, b.length);
        if (to - from < 32) return null;
        return Arrays.copyOfRange(b, from, to);
    }

    private static int indexOf(byte[] b, byte[] pat, int from) {
        outer:
        for (int i = Math.max(0, from); i <= b.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static int lastIndexOf(byte[] b, byte[] pat, int from) {
        for (int i = b.length - pat.length; i >= Math.max(0, from); i--) {
            boolean same = true;
            for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) { same = false; break; }
            if (same) return i;
        }
        return -1;
    }
}
