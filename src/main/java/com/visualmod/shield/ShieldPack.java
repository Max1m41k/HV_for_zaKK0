package com.visualmod.shield;

import com.visualmod.util.Diag;
import com.visualmod.util.PixelPng;
import com.visualmod.util.RuntimePacks;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сгенерированный ресурспак кастомных щитов: геометрия, items-обёртки и атласы всех режимов.
 *
 * Всё пишет один класс ({@link ShieldSpec} считает и модель, и картинку), поэтому узор не
 * разъезжается с ламелями, а статика в jar не нужна вовсе — раньше рыцарский и легендарный щиты
 * лежали там отдельными png, нарисованными внешним скриптом по копии силуэта.
 *
 * На каждый режим две модели (покой и «поднят»): положение перебирает vanilla-условие using_item
 * в items-обёртке, ровно как это делает assets/minecraft/items/shield.json.
 */
public final class ShieldPack {

    public static final String FOLDER = "HolyVisualsShield";
    public static final String PROFILE_ID = "file/" + FOLDER;

    private ShieldPack() {}

    public static Path root() {
        return RuntimePacks.root(FOLDER);
    }

    /** Пишет все четыре раскраски сразу: режим можно переключить в бою, и пак должен быть готов. */
    public static boolean write(int mainArgb, int cdArgb) {
        int done = 0;
        try {
            Path base = root();
            Path assets = base.resolve("assets/visualmod");
            Files.createDirectories(assets.resolve("models/item"));
            Files.createDirectories(assets.resolve("items"));
            Files.createDirectories(assets.resolve("textures/item"));
            RuntimePacks.writeMcmeta(base, "Holy Visuals - custom shields");
            if (variant(assets, "shield_custom", ShieldSpec.CUSTOM, mainArgb, false, false)) done++;
            if (variant(assets, "shield_custom_cd", ShieldSpec.CUSTOM, cdArgb, true, false)) done++;
            if (variant(assets, "shield_knight", ShieldSpec.KNIGHT, 0, false, false)) done++;
            if (variant(assets, "shield_legendary", ShieldSpec.LEGENDARY, 0, false, true)) done++;
        } catch (Throwable t) {
            Diag.once("ShieldPack.write", t);
        }
        return done > 0;
    }

    /** Одна раскраска: две модели, обёртка с условием и атлас. cd — трещины и приглушение. */
    private static boolean variant(Path assets, String name, int variant, int argb, boolean cd, boolean wings) {
        try {
            writeModel(assets, name, name, false, wings);
            writeModel(assets, name, name + "_block", true, wings);
            Files.write(assets.resolve("items/" + name + ".json"),
                    condition(name, name + "_block").getBytes(StandardCharsets.UTF_8));
            return PixelPng.write(assets.resolve("textures/item/" + name + ".png").toFile(),
                    ShieldSpec.pixels(argb, cd, variant), ShieldSpec.TEX);
        } catch (Throwable t) {
            Diag.once("ShieldPack.variant", t);
            return false;
        }
    }

    private static void writeModel(Path assets, String tex, String name, boolean block, boolean wings) throws Exception {
        Files.write(assets.resolve("models/item/" + name + ".json"),
                ShieldSpec.modelJson(tex, block, wings).getBytes(StandardCharsets.UTF_8));
    }

    /** Позицию «поднят/не поднят» отдаём vanilla-условию, как это делает assets/minecraft/items/shield.json. */
    private static String condition(String idle, String raised) {
        return "{\"model\":{\"type\":\"minecraft:condition\",\"property\":\"minecraft:using_item\","
                + "\"on_false\":{\"type\":\"minecraft:model\",\"model\":\"visualmod:item/" + idle + "\"},"
                + "\"on_true\":{\"type\":\"minecraft:model\",\"model\":\"visualmod:item/" + raised + "\"}}}";
    }

    /** Подменять модель можно только если её атлас действительно лежит в паке. */
    public static boolean ready(String id) {
        return RuntimePacks.has(Identifier.of("visualmod", "textures/item/" + id + ".png"));
    }

    public static boolean enableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, true);
    }

    public static boolean disableAndReload() {
        return RuntimePacks.profileAndReload(PROFILE_ID, false);
    }

    public static String applied() {
        return RuntimePacks.applied(root());
    }

    public static void markApplied(String signature) {
        RuntimePacks.markApplied(root(), signature);
    }
}
