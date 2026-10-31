package com.visualmod.module.modules.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class HolyAnarchyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File DATA_DIR = new File(MinecraftClient.getInstance().runDirectory, "Holy-visual Info");
    private static final File DATA_FILE = new File(DATA_DIR, "anarchy.json");

    public static class AnarchyData {
        public int solo = 0;
        public int duo = 0;
        public int trio = 0;
        public int clan = 0;
    }

    public static AnarchyData data = new AnarchyData();

    public static void load() {
        if (!DATA_DIR.exists()) {
            DATA_DIR.mkdirs();
        }
        if (!DATA_FILE.exists()) {
            data = new AnarchyData();
            save();
            return;
        }
        try (FileReader reader = new FileReader(DATA_FILE)) {
            AnarchyData loaded = GSON.fromJson(reader, AnarchyData.class);
            if (loaded != null) {
                data = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
            try (FileWriter writer = new FileWriter(DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getGlobalId(String category, int indexInsideCategory) {
        int id = 0;
        if (category.equals("SOLO")) {
            return indexInsideCategory;
        }
        id += data.solo;
        if (category.equals("DUO")) {
            return id + indexInsideCategory;
        }
        id += data.duo;
        if (category.equals("TRIO")) {
            return id + indexInsideCategory;
        }
        id += data.clan;
        return id + indexInsideCategory;
    }
}
