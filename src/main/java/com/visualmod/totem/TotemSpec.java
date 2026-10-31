package com.visualmod.totem;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.visualmod.util.Diag;
import com.visualmod.util.PixelPng;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Пользовательский тотем: набор кубиков без поворотов + пиксельная раскраска каждой грани.
 *
 * Каждый куб берёт себе шесть подряд идущих слотов 4x4 в атласе 64x64, поэтому UV считаются
 * из порядка кубов и никогда не пересекаются. Отсюда же потолок в MAX_CUBES.
 */
public final class TotemSpec {

    public static final int ATLAS = 64;
    public static final int FACE_PX = 4;
    public static final int SLOT_COLS = ATLAS / FACE_PX;
    public static final int MAX_CUBES = SLOT_COLS * SLOT_COLS / 6;

    public static final int F_DOWN = 0, F_UP = 1, F_NORTH = 2, F_SOUTH = 3, F_WEST = 4, F_EAST = 5;
    public static final String[] FACE_NAMES = {"низ", "верх", "перед", "зад", "лево", "право"};

    /** Порядок обхода 4 углов грани — индексы вершин куба. */
    public static final int[][] FACE_VERTS = {
            {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}
    };
    /** Боковая подсветка граней — тот же, что у офлайн-превью, чтобы картинка не расходилась. */
    public static final float[] FACE_SHADE = {0.60f, 1.12f, 1.00f, 0.80f, 0.72f, 0.92f};

    public static final class Cube {
        public double x, y, z, w, h, d;
        public final int[][] px = new int[6][FACE_PX * FACE_PX];

        public Cube(double x, double y, double z, double w, double h, double d, int rgb) {
            this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.d = d;
            paint(rgb);
        }

        public final int color() { return px[F_NORTH][0]; }

        public final void paint(int rgb) {
            for (int[] face : px) Arrays.fill(face, rgb);
        }

        public final double[] bounds() { return new double[]{x, y, z, x + w, y + h, z + d}; }
    }

    public static final List<Cube> cubes = new ArrayList<>();
    public static final int[] PALETTE = {
            0xFFB5824A, 0xFF8A5F32, 0xFF5C3D20, 0xFFE8B23C,
            0xFFB8862A, 0xFF4E9A3C, 0xFF2F6B26, 0xFF7FA84A,
            0xFFE7DCC0, 0xFF1A1414, 0xFF2A2118, 0xFF8C8C96,
            0xFFC6423A, 0xFF3FA9A0, 0xFFD9682F, 0xFFFFFFFF
    };

    private TotemSpec() {}

    public static void seedDefault() {
        cubes.clear();
        cubes.add(new Cube(5.5, 0.5, 6.5, 5.0, 1.5, 3.0, 0xFF5C3D20));
        cubes.add(new Cube(6.0, 2.0, 6.8, 4.0, 5.5, 2.4, 0xFFB5824A));
        cubes.add(new Cube(5.5, 7.5, 6.3, 5.0, 4.5, 3.4, 0xFFB5824A));
        cubes.add(new Cube(6.4, 9.6, 6.1, 1.2, 1.2, 0.4, 0xFF1A1414));
        cubes.add(new Cube(8.4, 9.6, 6.1, 1.2, 1.2, 0.4, 0xFF1A1414));
        cubes.add(new Cube(6.6, 8.2, 6.1, 2.8, 0.8, 0.4, 0xFF2A2118));
        cubes.add(new Cube(7.2, 12.0, 7.3, 1.6, 3.0, 1.4, 0xFF3FA9A0));
    }

    private static File configFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "visualmod_configs/totem_custom.json");
    }

    public static void load() {
        try {
            File f = configFile();
            if (!f.isFile()) { seedDefault(); save(); return; }
            JsonArray arr = new Gson().fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8),
                    JsonObject.class).getAsJsonArray("cubes");
            List<Cube> next = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                JsonArray p = o.getAsJsonArray("p"), s = o.getAsJsonArray("s");
                Cube c = new Cube(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble(),
                        s.get(0).getAsDouble(), s.get(1).getAsDouble(), s.get(2).getAsDouble(), 0xFFFFFFFF);
                JsonArray faces = o.getAsJsonArray("f");
                for (int fi = 0; fi < 6 && fi < faces.size(); fi++) {
                    JsonArray px = faces.get(fi).getAsJsonArray();
                    for (int i = 0; i < c.px[fi].length && i < px.size(); i++) c.px[fi][i] = px.get(i).getAsInt();
                }
                next.add(c);
            }
            cubes.clear();
            cubes.addAll(next);
        } catch (Throwable t) {
            Diag.once("TotemSpec.load", t);
            seedDefault();
        }
    }

    public static void save() {
        try {
            File f = configFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), toJson().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Diag.once("TotemSpec.save", t);
        }
    }

    public static String toJson() {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"cubes\":[");
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"p\":[").append(c.x).append(',').append(c.y).append(',').append(c.z)
              .append("],\"s\":[").append(c.w).append(',').append(c.h).append(',').append(c.d).append("],\"f\":[");
            for (int f = 0; f < 6; f++) {
                if (f > 0) sb.append(',');
                sb.append('[');
                for (int k = 0; k < c.px[f].length; k++) sb.append(k > 0 ? "," : "").append(c.px[f][k]);
                sb.append(']');
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    public static int slotOf(int cubeIndex, int face) { return cubeIndex * 6 + face; }

    public static int[] uvOf(int slot) {
        int u = (slot % SLOT_COLS) * FACE_PX, v = (slot / SLOT_COLS) * FACE_PX;
        return new int[]{u, v, u + FACE_PX, v + FACE_PX};
    }

    public static String modelJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"parent\":\"minecraft:item/generated\",\"gui_light\":\"front\",")
          .append("\"textures\":{\"0\":\"visualmod:item/totem_custom\",\"particle\":\"visualmod:item/totem_custom\"},")
          .append("\"elements\":[");
        List<String> els = new ArrayList<>();
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            double[][] uv6 = new double[6][];
            for (int f = 0; f < 6; f++) {
                int[] r = uvOf(slotOf(i, f));
                uv6[f] = new double[]{r[0], r[1], r[2], r[3]};
            }
            els.addAll(TotemLayers.lamellas(new double[]{c.x, c.y, c.z, c.x + c.w, c.y + c.h, c.z + c.d}, uv6));
        }
        for (int i = 0; i < els.size(); i++) sb.append(i > 0 ? "," : "").append(els.get(i));
        sb.append("],\"display\":{")
          .append("\"thirdperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[0,2.4,0],\"scale\":[0.9,0.9,0.9]},")
          .append("\"thirdperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[0,2.4,0],\"scale\":[0.9,0.9,0.9]},")
          .append("\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.6,1.2,1.0],\"scale\":[0.85,0.85,0.85]},")
          .append("\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.6,1.2,1.0],\"scale\":[0.85,0.85,0.85]},")
          .append("\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,2,0],\"scale\":[0.6,0.6,0.6]},")
          .append("\"head\":{\"rotation\":[0,0,0],\"translation\":[0,12,0],\"scale\":[0.9,0.9,0.9]},")
          .append("\"gui\":{\"rotation\":[15,-135,0],\"translation\":[0,0,0],\"scale\":[0.85,0.85,0.85]},")
          .append("\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.85,0.85,0.85]}}}");
        return sb.toString();
    }

    public static int[] texturePixels() {
        int[] img = new int[ATLAS * ATLAS];
        for (int i = 0; i < cubes.size(); i++) {
            Cube c = cubes.get(i);
            for (int f = 0; f < 6; f++) {
                int[] uv = uvOf(slotOf(i, f));
                for (int y = 0; y < FACE_PX; y++)
                    for (int x = 0; x < FACE_PX; x++)
                        img[(uv[1] + y) * ATLAS + uv[0] + x] = c.px[f][y * FACE_PX + x];
            }
        }
        return img;
    }

    public static boolean writeTexture(File out) {
        return writePng(out, texturePixels(), ATLAS);
    }

    /** ARGB-буфер size*size → PNG. Кодер общий для всей генерируемой косметики. */
    public static boolean writePng(File out, int[] px, int size) {
        return PixelPng.write(out, px, size);
    }
}
