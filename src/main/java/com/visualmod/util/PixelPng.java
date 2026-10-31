package com.visualmod.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * PNG пишется вручную: у Minecraft-рантайма нет гарантии, что модуль java.desktop
 * вообще доступен, а ImageIO тянул бы его в зависимость.
 */
public final class PixelPng {

    private PixelPng() {}

    /** ARGB-буфер size*size → PNG RGBA (строки без фильтра, тип 0). */
    public static boolean write(File out, int[] px, int size) {
        return write(out, px, size, size);
    }

    /**
     * То же для неквадратного буфера: стрип анимированной текстуры — это w на h*кадров,
     * а кадры ваниль читает сверху вниз.
     */
    public static boolean write(File out, int[] px, int w, int h) {
        try {
            byte[] raw = new byte[(w * 4 + 1) * h];
            for (int y = 0; y < h; y++) {
                int row = y * (w * 4 + 1);
                raw[row] = 0;
                for (int x = 0; x < w; x++) {
                    int c = px[y * w + x], o = row + 1 + x * 4;
                    raw[o] = (byte) (c >> 16);
                    raw[o + 1] = (byte) (c >> 8);
                    raw[o + 2] = (byte) c;
                    raw[o + 3] = (byte) (c >>> 24);
                }
            }
            Deflater deflater = new Deflater();
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            while (!deflater.finished()) bytes.write(tmp, 0, deflater.deflate(tmp));
            deflater.end();

            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                ByteBuffer ihdr = ByteBuffer.allocate(13);
                ihdr.putInt(w).putInt(h).put((byte) 8).put((byte) 6)
                    .put((byte) 0).put((byte) 0).put((byte) 0);
                chunk(os, "IHDR", ihdr.array());
                chunk(os, "IDAT", bytes.toByteArray());
                chunk(os, "IEND", new byte[0]);
            }
            return out.length() > 8;
        } catch (Throwable t) {
            Diag.once("PixelPng.write", t);
            return false;
        }
    }

    private static void chunk(OutputStream os, String type, byte[] data) throws IOException {
        ByteBuffer len = ByteBuffer.allocate(4).putInt(data.length);
        byte[] body = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(body);
        crc.update(data);
        ByteBuffer tail = ByteBuffer.allocate(4).putInt((int) crc.getValue());
        os.write(len.array());
        os.write(body);
        os.write(data);
        os.write(tail.array());
    }
}
