package com.visualmod.cosmetics;

import java.util.ArrayList;
import java.util.List;

public final class CosmeticModel {

    public enum Bone {
        HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, ROOT
    }

    public final List<Part> parts = new ArrayList<>();

    public static final class Part {
        public Bone bone = Bone.HEAD;
        public final List<Face> faces = new ArrayList<>();
        public String anim;
        public float animAmp = 0.5f;
        public float animSpeed = 2f;
        public float animPhase = 0f;
        public char animAxis = 'z';
        public final float[] animPivot = new float[3];
    }

    public static final class Face {
        public final float[][] v = new float[4][3];
        public float nx, ny, nz;
        public final float[] top = new float[4];
        public final float[] bottom = new float[4];
        public boolean gradient;
        public boolean emissive;
        public boolean doubleSided;

        public Face copy() {
            Face f = new Face();
            for (int i = 0; i < 4; i++) System.arraycopy(this.v[i], 0, f.v[i], 0, 3);
            f.nx = nx; f.ny = ny; f.nz = nz;
            System.arraycopy(top, 0, f.top, 0, 4);
            System.arraycopy(bottom, 0, f.bottom, 0, 4);
            f.gradient = gradient; f.emissive = emissive; f.doubleSided = doubleSided;
            return f;
        }
    }

    public Part part(Bone bone) {
        Part p = new Part();
        p.bone = bone;
        parts.add(p);
        return p;
    }

    public static void box(Part p, double fx, double fy, double fz,
                           double sx, double sy, double sz,
                           int color, Integer colorBottom,
                           float alpha, boolean emissive, boolean doubleSided) {
        double x0 = fx, y0 = fy, z0 = fz, x1 = fx + sx, y1 = fy + sy, z1 = fz + sz;
        float[] cTop = rgba(color, alpha);
        float[] cBot = colorBottom != null ? rgba(colorBottom, alpha) : cTop;
        boolean grad = colorBottom != null;

        face(p, x0, y1, z0,  x1, y1, z0,  x1, y1, z1,  x0, y1, z1,  0, 1, 0, cBot, cBot, grad, emissive, doubleSided);
        face(p, x0, y0, z1,  x1, y0, z1,  x1, y0, z0,  x0, y0, z0,  0, -1, 0, cTop, cTop, grad, emissive, doubleSided);
        face(p, x0, y0, z0,  x1, y0, z0,  x1, y1, z0,  x0, y1, z0,  0, 0, -1, cTop, cBot, grad, emissive, doubleSided);
        face(p, x1, y0, z1,  x0, y0, z1,  x0, y1, z1,  x1, y1, z1,  0, 0, 1, cTop, cBot, grad, emissive, doubleSided);
        face(p, x1, y0, z0,  x1, y0, z1,  x1, y1, z1,  x1, y1, z0,  1, 0, 0, cTop, cBot, grad, emissive, doubleSided);
        face(p, x0, y0, z1,  x0, y0, z0,  x0, y1, z0,  x0, y1, z1,  -1, 0, 0, cTop, cBot, grad, emissive, doubleSided);
    }

    public static void pyramid(Part p,
                               double ax, double ay, double az,
                               double[][] base,
                               int color, float alpha, boolean emissive) {
        float[] c = rgba(color, alpha);
        float[] cb = new float[]{c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f, c[3]};
        for (int i = 0; i < 4; i++) {
            double[] b0 = base[i], b1 = base[(i + 1) % 4];
            Face f = new Face();
            f.v[0][0] = (float) ax; f.v[0][1] = (float) ay; f.v[0][2] = (float) az;
            f.v[1][0] = (float) b0[0]; f.v[1][1] = (float) b0[1]; f.v[1][2] = (float) b0[2];
            f.v[2][0] = (float) b1[0]; f.v[2][1] = (float) b1[1]; f.v[2][2] = (float) b1[2];
            f.v[3] = f.v[2];
            normal(f);
            System.arraycopy(c, 0, f.top, 0, 4);
            System.arraycopy(c, 0, f.bottom, 0, 4);
            f.emissive = emissive;
            f.doubleSided = true;
            p.faces.add(f);
        }
        Face f = new Face();
        for (int i = 0; i < 4; i++) {
            f.v[i][0] = (float) base[i][0]; f.v[i][1] = (float) base[i][1]; f.v[i][2] = (float) base[i][2];
        }
        f.nx = 0; f.ny = (float) Math.signum(base[0][1] - ay); f.nz = 0;
        if (f.ny == 0) f.ny = -1;
        System.arraycopy(cb, 0, f.top, 0, 4);
        System.arraycopy(cb, 0, f.bottom, 0, 4);
        p.faces.add(f);
    }

    public static void quad(Part p, double[][] verts, int color, float alpha,
                            boolean emissive, boolean doubleSided) {
        Face f = new Face();
        for (int i = 0; i < 4; i++) {
            f.v[i][0] = (float) verts[i][0];
            f.v[i][1] = (float) verts[i][1];
            f.v[i][2] = (float) verts[i][2];
        }
        normal(f);
        float[] c = rgba(color, alpha);
        System.arraycopy(c, 0, f.top, 0, 4);
        System.arraycopy(c, 0, f.bottom, 0, 4);
        f.emissive = emissive;
        f.doubleSided = doubleSided;
        p.faces.add(f);
    }

    private static void face(Part p,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             float nx, float ny, float nz,
                             float[] cTop, float[] cBot, boolean grad,
                             boolean emissive, boolean doubleSided) {
        Face f = new Face();
        f.v[0][0] = (float) x0; f.v[0][1] = (float) y0; f.v[0][2] = (float) z0;
        f.v[1][0] = (float) x1; f.v[1][1] = (float) y1; f.v[1][2] = (float) z1;
        f.v[2][0] = (float) x2; f.v[2][1] = (float) y2; f.v[2][2] = (float) z2;
        f.v[3][0] = (float) x3; f.v[3][1] = (float) y3; f.v[3][2] = (float) z3;
        f.nx = nx; f.ny = ny; f.nz = nz;
        System.arraycopy(cTop, 0, f.top, 0, 4);
        System.arraycopy(cBot, 0, f.bottom, 0, 4);
        f.gradient = grad;
        f.emissive = emissive;
        f.doubleSided = doubleSided;
        p.faces.add(f);
    }

    static void normal(Face f) {
        float[] a = f.v[1], b = f.v[0], c = f.v[2];
        float ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
        float vx = c[0] - b[0], vy = c[1] - b[1], vz = c[2] - b[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) { f.nx = 0; f.ny = 1; f.nz = 0; return; }
        f.nx = nx / l; f.ny = ny / l; f.nz = nz / l;
    }

    public static float[] rgba(int color, float alpha) {
        return new float[]{((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f, alpha};
    }

    public static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            return (int) Long.parseLong(h, 16);
        } catch (Exception e) {
            return fallback;
        }
    }
}
