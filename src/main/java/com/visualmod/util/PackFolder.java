package com.visualmod.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Каталог <gamedir>/textures — он же ресурспак-провайдер: сюда кладутся паки мода и свои текстуры пользователя. */
public final class PackFolder {
    private PackFolder() {}

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("textures");
    }

    public static void ensureDir() {
        try { Files.createDirectories(dir()); } catch (IOException ignored) {}
    }
}
