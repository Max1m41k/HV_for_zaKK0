package com.visualmod.totem;

import com.visualmod.util.Diag;
import com.visualmod.util.RuntimePacks;
import net.minecraft.util.Identifier;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сгенерированный ресурспак кастомного тотема — тот же механизм, что у ShieldPack.
 *
 * Папка лежит в run/textures, её сканирует ResourcePackManager (см. ResourcePackManagerMixin),
 * поэтому свой провайдер регистрировать не нужно. В пак уходят COLOR-перекраска и модель редактора;
 * 1.12 и JUNGLE живут статикой в jar, но геометрию все четыре варианта считают из {@link TotemLayers}.
 *
 * Готовность варианта смотрим по текстуре пака, а не по items-обёртке: обёртка без пака ничего не
 * значит, а текстуру пишет только наш pack, поэтому её наличие — честный признак «пак дописан».
 */
public final class TotemPack {

    public static final String FOLDER = "HolyVisualsTotem";
    public static final String PROFILE_ID = "file/" + FOLDER;

    private TotemPack() {}

    public static Path root() {
        return RuntimePacks.root(FOLDER);
    }

    public static boolean write() {
        try {
            Path assets = prepare();
            Files.write(assets.resolve("models/item/totem_custom.json"),
                    TotemSpec.modelJson().getBytes(StandardCharsets.UTF_8));
            Files.write(assets.resolve("items/totem_custom.json"),
                    ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"visualmod:item/totem_custom\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            File png = assets.resolve("textures/item/totem_custom.png").toFile();
            return TotemSpec.writeTexture(png);
        } catch (Throwable t) {
            Diag.once("TotemPack.write", t);
            return false;
        }
    }

    /** Тот же пак, но модель-перекраска: геометрия фиксированная, цвет берётся из настройки. */
    public static boolean writeTint(int argb) {
        try {
            Path assets = prepare();
            Files.write(assets.resolve("models/item/totem_tint.json"),
                    TotemTint.modelJson().getBytes(StandardCharsets.UTF_8));
            Files.write(assets.resolve("items/totem_tint.json"),
                    ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"visualmod:item/totem_tint\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            File png = assets.resolve("textures/item/totem_tint.png").toFile();
            return TotemSpec.writePng(png, TotemTint.texturePixels(argb), TotemTint.ATLAS);
        } catch (Throwable t) {
            Diag.once("TotemPack.writeTint", t);
            return false;
        }
    }

    private static Path prepare() throws Exception {
        Path base = root();
        Path assets = base.resolve("assets/visualmod");
        Files.createDirectories(assets.resolve("models/item"));
        Files.createDirectories(assets.resolve("items"));
        Files.createDirectories(assets.resolve("textures/item"));
        RuntimePacks.writeMcmeta(base, "Holy Visuals - custom totem");
        return assets;
    }

    public static boolean ready(String id) {
        return RuntimePacks.has(Identifier.of("visualmod", "textures/item/" + id + ".png"));
    }

    public static boolean enableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, true);
    }

    public static boolean disableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, false);
    }

    /**
     * Что реально лежит на диске. Маркер нужен, чтобы после перезапуска игры не устраивать
     * лишнюю перезагрузку ресурсов только потому, что в памяти ещё ничего не применялось.
     */
    public static String applied() {
        return RuntimePacks.applied(root());
    }

    public static void markApplied(String signature) {
        RuntimePacks.markApplied(root(), signature);
    }
}
