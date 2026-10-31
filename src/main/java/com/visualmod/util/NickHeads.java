package com.visualmod.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Головы скинов для AltManager: берём 32×32 с Cravatar (он же отдаёт скины офлайн-ников, которых
 * нет у Mojang), кэшируем на диске и регистрируем как динамическую текстуру. Запрос асинхронный:
 * пока голова не готова, рисует заглушку, поэтому UI не дёргается.
 */
public final class NickHeads {
    private static final int PX = 32;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final Map<String, Identifier> loaded = new HashMap<>();
    private static final Set<String> pending = new HashSet<>();

    private NickHeads() {}

    /** null, если голова ещё грузится (загрузка стартует сама). */
    public static Identifier head(String nick) {
        if (nick == null || nick.isEmpty()) return null;
        String key = nick.toLowerCase(Locale.ROOT);
        synchronized (NickHeads.class) {
            Identifier id = loaded.get(key);
            if (id != null || pending.contains(key)) return id;
            pending.add(key);
        }
        fetch(key);
        return null;
    }

    private static void fetch(String nick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture.supplyAsync(() -> bytes(nick)).thenAccept(data -> mc.execute(() -> {
            synchronized (NickHeads.class) {
                pending.remove(nick);
            }
            if (data == null) return;
            try {
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(data));
                Identifier id = Identifier.of("visualmod", "heads/" + nick);
                mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
                synchronized (NickHeads.class) {
                    loaded.put(nick, id);
                }
            } catch (Throwable t) {
                Diag.once("NickHeads." + nick, t);
            }
        }));
    }

    private static byte[] bytes(String nick) {
        try {
            Path cache = cacheFile(nick);
            if (Files.exists(cache)) {
                byte[] hit = Files.readAllBytes(cache);
                if (hit.length > 0) return hit;
            }
            HttpRequest rq = HttpRequest.newBuilder(
                    URI.create("https://cravatar.eu/helmavatar/" + nick + "/" + PX + ".png"))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<byte[]> rs = HTTP.send(rq, HttpResponse.BodyHandlers.ofByteArray());
            if (rs.statusCode() != 200 || rs.body() == null || rs.body().length == 0) return null;
            Files.createDirectories(cache.getParent());
            Files.write(cache, rs.body());
            return rs.body();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Path cacheFile(String nick) {
        return FabricLoader.getInstance().getConfigDir().resolve("visualmod_heads").resolve(nick + ".png");
    }
}
