package com.visualmod.util;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public final class WorldRenderUtil {
    private WorldRenderUtil() {}

    public static void drawOutlinedBox(VertexConsumer buf, Matrix4f mat, Box box, float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        line(buf, mat, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, mat, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(buf, mat, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, mat, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, mat, x1, y2, z2, x1, y2, z1, r, g, b, a);
        line(buf, mat, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    public static void drawFilledBox(VertexConsumer buf, Matrix4f mat, Box box, float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        quad(buf, mat, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(buf, mat, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(buf, mat, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(buf, mat, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, a);
        quad(buf, mat, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        quad(buf, mat, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    public static void drawCircle(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz, double radius, int segments, float r, float g, float b, float a) {
        double prevX = cx + radius, prevZ = cz;
        for (int i = 1; i <= segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            double nx = cx + Math.cos(angle) * radius;
            double nz = cz + Math.sin(angle) * radius;
            line(buf, mat, (float) prevX, (float) cy, (float) prevZ, (float) nx, (float) cy, (float) nz, r, g, b, a);
            prevX = nx;
            prevZ = nz;
        }
    }

    public static void drawLine(VertexConsumer buf, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        line(buf, mat, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, r, g, b, a);
    }

    public static int colorToRgb(int argb) {
        return argb & 0x00FFFFFF;
    }

    public static float colorR(int argb) { return ((argb >> 16) & 0xFF) / 255f; }
    public static float colorG(int argb) { return ((argb >> 8) & 0xFF) / 255f; }
    public static float colorB(int argb) { return (argb & 0xFF) / 255f; }

    private static void line(VertexConsumer buf, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        org.joml.Vector3f s = mat.transformPosition(x1, y1, z1, new org.joml.Vector3f());
        org.joml.Vector3f e = mat.transformPosition(x2, y2, z2, new org.joml.Vector3f());
        float dx = e.x - s.x, dy = e.y - s.y, dz = e.z - s.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6f) { dx = 0f; dy = 1f; dz = 0f; } else { dx /= len; dy /= len; dz /= len; }
        // 1.21.11: у RenderLayers.lines() формат POSITION_COLOR_NORMAL_LINE_WIDTH,
        // без lineWidth BufferBuilder кидает "Missing elements in vertex: LineWidth".
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(dx, dy, dz).lineWidth(1.0f);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(dx, dy, dz).lineWidth(1.0f);
    }

    public static void quad(VertexConsumer buf, Matrix4f mat,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float x4, float y4, float z4,
                            float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buf.vertex(mat, x3, y3, z3).color(r, g, b, a);
        buf.vertex(mat, x4, y4, z4).color(r, g, b, a);
    }
}
