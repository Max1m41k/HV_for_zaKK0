package com.visualmod.util;

import net.minecraft.client.gl.RenderPipelines;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GameBackground {
    private GameBackground() {}

    public record Result(boolean ok, String message) {}
    private record Decoded(byte[] png) {}

    private static final Identifier ID = Identifier.of("visualmod", "game_bg/current");
    private static final int MAX_W = 2560;
    private static final Gson GSON = new Gson();

    private static int selected = -1;
    private static int loadedFor = 0;
    private static int failedFor = -1;
    private static boolean loading = false;
    private static int texW, texH;

    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("visualmod_background.json"); }

    public static int current() {
        if (selected < 0) {
            selected = 0;
            try {
                if (Files.exists(configFile())) {
                    JsonObject o = GSON.fromJson(Files.readString(configFile(), StandardCharsets.UTF_8), JsonObject.class);
                    if (o != null && o.has("current")) selected = Math.max(0, Math.min(LauncherBackground.COUNT, o.get("current").getAsInt()));
                }
            } catch (Exception ignored) {}
        }
        return selected;
    }

    private static void saveSelected() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("current", selected);
            Files.createDirectories(configFile().getParent());
            Files.writeString(configFile(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static void select(int n, Consumer<Result> callback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (n == 0) {
            unload(mc);
            selected = 0; failedFor = -1; saveSelected();
            callback.accept(new Result(true, "Возвращён стандартный фон"));
            return;
        }
        Path src = LauncherBackground.imageFor(n);
        if (src == null) { callback.accept(new Result(false, "Файл textures/" + n + ".png не найден")); return; }
        if (loading) { callback.accept(new Result(false, "Подождите, предыдущий фон ещё загружается")); return; }
        loading = true;
        CompletableFuture.supplyAsync(() -> decode(src)).thenAccept(d -> mc.execute(() -> {
            loading = false;
            if (d == null || !register(mc, d, n)) { callback.accept(new Result(false, "Не удалось прочитать картинку №" + n)); return; }
            selected = n; failedFor = -1; saveSelected();
            callback.accept(new Result(true, "Фон игры изменён на №" + n));
        }));
    }

    private static void ensureLoaded() {
        int want = current();
        if (want <= 0 || loadedFor == want || loading || failedFor == want) return;
        Path src = LauncherBackground.imageFor(want);
        if (src == null) { failedFor = want; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        loading = true;
        CompletableFuture.supplyAsync(() -> decode(src)).thenAccept(d -> mc.execute(() -> {
            loading = false;
            if (d == null || !register(mc, d, want)) failedFor = want;
        }));
    }

    public static void draw(DrawContext ctx, Identifier fallback, int w, int h) {
        ensureLoaded();
        if (loadedFor > 0 && loadedFor == current() && texW > 0 && texH > 0) {
            double scale = Math.max(w / (double) texW, h / (double) texH);
            int rw = Math.min(texW, (int) Math.round(w / scale));
            int rh = Math.min(texH, (int) Math.round(h / scale));
            float u = (texW - rw) / 2f, v = (texH - rh) / 2f;
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ID, 0, 0, u, v, w, h, rw, rh, texW, texH);
        } else {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, fallback, 0, 0, 0.0F, 0.0F, w, h, w, h);
        }
    }

    private static Decoded decode(Path src) {
        try {
            BufferedImage img = ImageIO.read(src.toFile());
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int nw = w, nh = h;
            if (w > MAX_W) { nw = MAX_W; nh = Math.max(1, (int) Math.round(h * (MAX_W / (double) w))); }
            BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return new Decoded(bos.toByteArray());
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static boolean register(MinecraftClient mc, Decoded d, int n) {
        try {
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(d.png()));
            TextureManager tm = mc.getTextureManager();
            if (loadedFor > 0) tm.destroyTexture(ID);
            texW = ni.getWidth(); texH = ni.getHeight();
            tm.registerTexture(ID, new NativeImageBackedTexture(ID::toString, ni));
            loadedFor = n;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void unload(MinecraftClient mc) {
        if (loadedFor > 0) mc.getTextureManager().destroyTexture(ID);
        loadedFor = 0; texW = 0; texH = 0;
    }
}
