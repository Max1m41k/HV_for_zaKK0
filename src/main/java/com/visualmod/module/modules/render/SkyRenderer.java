package com.visualmod.module.modules.render;

import net.minecraft.client.render.RenderLayers;
import com.visualmod.VisualsMod;
import com.visualmod.util.WorldRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Random;

public final class SkyRenderer {
    private SkyRenderer() {}

    private static float[] sx, sy, sz, sSize, sAlpha, sPhase, sSpeed, sRot;
    private static int[] sPal;
    private static boolean[] sSpark;
    private static int builtCount = -1;

    private static final int NEB = 6;
    private static final float[][] nebDir = new float[NEB][3];
    private static final float[] nebRad = new float[NEB], nebStretch = new float[NEB], nebRot = new float[NEB];
    private static boolean nebBuilt = false;

    private static final Random RNG = new Random();
    private static boolean cometActive = false;
    private static final double[] cA = new double[3], cB = new double[3];
    private static long cStart, cDur, nextComet;

    private static final double[] TR = new double[3], TU = new double[3];
    private static boolean errorLogged = false;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            try {
                render(ctx);
            } catch (Throwable t) {
                if (!errorLogged) { errorLogged = true; System.err.println("[VisualsMod] CustomSky render error: " + t); t.printStackTrace(); }
            }
        });
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx) {
        if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
        if (VisualsMod.moduleManager == null) return;
        CustomSky mod = VisualsMod.moduleManager.getModule(CustomSky.class);
        if (mod == null || !mod.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null || mc.player == null || ctx.matrices() == null || ctx.consumers() == null) return;
        if (!CustomSky.isOverworld(w)) return;
        if (!CustomSky.stars && !CustomSky.nebula && !CustomSky.comets && !CustomSky.roundMoon) return;

        float td = mc.getRenderTickCounter().getTickProgress(false);
        double time = CustomSky.effectiveTime(w, td);
        double night = CustomSky.nightFactor(time);
        double rain = w.getRainGradient(td);
        double vis = night * (1.0 - 0.85 * rain);
        double angle = CustomSky.skyAngle(time) * Math.PI * 2.0;
        double sinT = Math.sin(angle), cosT = Math.cos(angle);

        Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
        double R = Math.max(96.0, mc.options.getClampedViewDistance() * 32.0);
        long now = System.currentTimeMillis();
        double sec = now / 1000.0;

        ctx.matrices().push();
        ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ctx.matrices().peek().getPositionMatrix();
        VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());

        if (CustomSky.nebula && vis > 0.02) drawNebula(buf, mat, cam, R * 1.00, sinT, cosT, vis);
        if (CustomSky.stars && vis > 0.02) drawStars(buf, mat, cam, R * 0.990, sinT, cosT, vis, sec);
        if (CustomSky.comets) drawComet(buf, mat, cam, R * 0.985, vis, now);
        if (CustomSky.roundMoon) drawMoon(buf, mat, cam, w, R, sinT, cosT);

        ctx.matrices().pop();
    }

    private static void ensureStars() {
        int n = (int) CustomSky.starDensity;
        if (n == builtCount) return;
        builtCount = n;
        Random r = new Random(1337);
        sx = new float[n]; sy = new float[n]; sz = new float[n]; sSize = new float[n]; sAlpha = new float[n];
        sPhase = new float[n]; sSpeed = new float[n]; sRot = new float[n]; sPal = new int[n]; sSpark = new boolean[n];
        for (int i = 0; i < n; i++) {
            double y = r.nextDouble() * 2 - 1, rr = Math.sqrt(1 - y * y), ph = r.nextDouble() * Math.PI * 2;
            sx[i] = (float) (rr * Math.cos(ph)); sy[i] = (float) y; sz[i] = (float) (rr * Math.sin(ph));
            double big = Math.pow(r.nextDouble(), 4);
            sSize[i] = (float) (0.0011 + big * 0.0030);
            sAlpha[i] = (float) (0.35 + 0.65 * r.nextDouble());
            sPhase[i] = (float) (r.nextDouble() * 6.283);
            sSpeed[i] = (float) (1.2 + r.nextDouble() * 2.6);
            sRot[i] = (float) (r.nextDouble() * 6.283);
            sPal[i] = r.nextInt(5);
            sSpark[i] = big > 0.12;
        }
    }

    private static final float[][] PALETTE = {
            {1.00f, 1.00f, 1.00f}, {0.75f, 0.86f, 1.00f}, {1.00f, 0.95f, 0.75f}, {1.00f, 0.80f, 0.90f}
    };

    private static void drawStars(VertexConsumer buf, Matrix4f mat, Vec3d cam, double rs,
                                  double sinT, double cosT, double vis, double sec) {
        ensureStars();
        float ar = WorldRenderUtil.colorR(CustomSky.atmosphereColor), ag = WorldRenderUtil.colorG(CustomSky.atmosphereColor), ab = WorldRenderUtil.colorB(CustomSky.atmosphereColor);
        float mr = WorldRenderUtil.colorR(CustomSky.starColor), mg = WorldRenderUtil.colorG(CustomSky.starColor), mb = WorldRenderUtil.colorB(CustomSky.starColor);

        for (int i = 0; i < sx.length; i++) {
            double x = sx[i], y = sy[i], z = sz[i];
            double dx = -(y * sinT + z * cosT), dy = y * cosT - z * sinT, dz = x;
            if (dy < 0.02) continue;
            float horizon = (float) Math.min(1.0, dy * 5.0);
            float tw = CustomSky.twinkle ? 0.65f + 0.35f * (float) Math.sin(sec * sSpeed[i] + sPhase[i]) : 1f;
            float a = (float) (sAlpha[i] * tw * vis * horizon);
            if (a < 0.02f) continue;

            float r, g, b;
            switch (CustomSky.starPalette) {
                case 0 -> { r = mr; g = mg; b = mb; }
                case 1 -> { r = 1f + (ar - 1f) * 0.6f; g = 1f + (ag - 1f) * 0.6f; b = 1f + (ab - 1f) * 0.6f; }
                default -> {
                    int p = sPal[i];
                    if (p < 4) { r = PALETTE[p][0]; g = PALETTE[p][1]; b = PALETTE[p][2]; }
                    else { r = 1f + (ar - 1f) * 0.5f; g = 1f + (ag - 1f) * 0.5f; b = 1f + (ab - 1f) * 0.5f; }
                }
            }

            double s = rs * sSize[i];
            basis(dx, dy, dz);
            double cs = Math.cos(sRot[i]), sn = Math.sin(sRot[i]);
            double rx = TR[0] * cs + TU[0] * sn, ry = TR[1] * cs + TU[1] * sn, rz = TR[2] * cs + TU[2] * sn;
            double ux = -TR[0] * sn + TU[0] * cs, uy = -TR[1] * sn + TU[1] * cs, uz = -TR[2] * sn + TU[2] * cs;
            double cx = cam.x + dx * rs, cy = cam.y + dy * rs, cz = cam.z + dz * rs;

            boolean spark = CustomSky.starShape == 1 || (CustomSky.starShape == 2 && sSpark[i]);
            if (spark) {
                double L = s * 2.4, S = s * 0.45;
                rhombus(buf, mat, cx, cy, cz, ux * L, uy * L, uz * L, rx * S, ry * S, rz * S, r, g, b, a);
                rhombus(buf, mat, cx, cy, cz, rx * L, ry * L, rz * L, ux * S, uy * S, uz * S, r, g, b, a);
            } else {
                rhombus(buf, mat, cx, cy, cz, rx * s, ry * s, rz * s, ux * s, uy * s, uz * s, r, g, b, a);
            }
        }
    }

    private static void buildNebula() {
        if (nebBuilt) return;
        nebBuilt = true;
        Random r = new Random(4242);
        for (int i = 0; i < NEB; i++) {
            double y = r.nextDouble() * 2 - 1, rr = Math.sqrt(1 - y * y), ph = r.nextDouble() * Math.PI * 2;
            nebDir[i][0] = (float) (rr * Math.cos(ph)); nebDir[i][1] = (float) y; nebDir[i][2] = (float) (rr * Math.sin(ph));
            nebRad[i] = (float) (0.25 + r.nextDouble() * 0.25);
            nebStretch[i] = (float) (1.0 + r.nextDouble() * 0.8);
            nebRot[i] = (float) (r.nextDouble() * Math.PI);
        }
    }

    private static void drawNebula(VertexConsumer buf, Matrix4f mat, Vec3d cam, double rn, double sinT, double cosT, double vis) {
        buildNebula();
        float ar = WorldRenderUtil.colorR(CustomSky.atmosphereColor), ag = WorldRenderUtil.colorG(CustomSky.atmosphereColor), ab = WorldRenderUtil.colorB(CustomSky.atmosphereColor);
        float[][] cols = {{ar, ag, ab}, {ag, ab, ar}, {ab, ar, ag}};
        double aC = 0.17 * CustomSky.nebulaStrength * vis;
        if (aC < 0.004) return;
        final int N = 22;

        for (int i = 0; i < NEB; i++) {
            double x = nebDir[i][0], y = nebDir[i][1], z = nebDir[i][2];
            double dx = -(y * sinT + z * cosT), dy = y * cosT - z * sinT, dz = x;
            if (dy < -0.25) continue;
            basis(dx, dy, dz);
            double cs = Math.cos(nebRot[i]), sn = Math.sin(nebRot[i]);
            double rad = rn * nebRad[i];
            double ax = (TR[0] * cs + TU[0] * sn) * rad * nebStretch[i], ay = (TR[1] * cs + TU[1] * sn) * rad * nebStretch[i], az = (TR[2] * cs + TU[2] * sn) * rad * nebStretch[i];
            double bx = (-TR[0] * sn + TU[0] * cs) * rad, by = (-TR[1] * sn + TU[1] * cs) * rad, bz = (-TR[2] * sn + TU[2] * cs) * rad;
            double cx = cam.x + dx * rn, cy = cam.y + dy * rn, cz = cam.z + dz * rn;
            float[] c = cols[i % 3];
            float r = c[0] * 0.8f + 0.2f, g = c[1] * 0.8f + 0.2f, b = c[2] * 0.8f + 0.2f;
            float aMid = (float) (aC * 0.55);

            for (int k = 0; k < N; k++) {
                double p0 = Math.PI * 2 * k / N, p1 = Math.PI * 2 * (k + 1) / N;
                double c0 = Math.cos(p0), s0 = Math.sin(p0), c1 = Math.cos(p1), s1 = Math.sin(p1);
                v(buf, mat, cx, cy, cz, r, g, b, (float) aC);
                v(buf, mat, cx + (ax * c0 + bx * s0) * 0.5, cy + (ay * c0 + by * s0) * 0.5, cz + (az * c0 + bz * s0) * 0.5, r, g, b, aMid);
                v(buf, mat, cx + (ax * c1 + bx * s1) * 0.5, cy + (ay * c1 + by * s1) * 0.5, cz + (az * c1 + bz * s1) * 0.5, r, g, b, aMid);
                v(buf, mat, cx + (ax * c1 + bx * s1) * 0.5, cy + (ay * c1 + by * s1) * 0.5, cz + (az * c1 + bz * s1) * 0.5, r, g, b, aMid);
                v(buf, mat, cx + (ax * c0 + bx * s0) * 0.5, cy + (ay * c0 + by * s0) * 0.5, cz + (az * c0 + bz * s0) * 0.5, r, g, b, aMid);
                v(buf, mat, cx + (ax * c0 + bx * s0), cy + (ay * c0 + by * s0), cz + (az * c0 + bz * s0), r, g, b, 0f);
                v(buf, mat, cx + (ax * c1 + bx * s1), cy + (ay * c1 + by * s1), cz + (az * c1 + bz * s1), r, g, b, 0f);
                v(buf, mat, cx + (ax * c1 + bx * s1) * 0.5, cy + (ay * c1 + by * s1) * 0.5, cz + (az * c1 + bz * s1) * 0.5, r, g, b, aMid);
            }
        }
    }

    private static void spawnComet(long now) {
        double el = Math.toRadians(25 + RNG.nextDouble() * 45), az = RNG.nextDouble() * Math.PI * 2;
        cA[0] = Math.cos(el) * Math.cos(az); cA[1] = Math.sin(el); cA[2] = Math.cos(el) * Math.sin(az);
        basis(cA[0], cA[1], cA[2]);
        double ang = RNG.nextDouble() * Math.PI * 2, span = 0.7 + RNG.nextDouble() * 0.5;
        for (int attempt = 0; attempt < 2; attempt++) {
            double tx = TR[0] * Math.cos(ang) + TU[0] * Math.sin(ang), ty = TR[1] * Math.cos(ang) + TU[1] * Math.sin(ang), tz = TR[2] * Math.cos(ang) + TU[2] * Math.sin(ang);
            double bx = cA[0] + tx * span, by = cA[1] + ty * span, bz = cA[2] + tz * span;
            double l = Math.sqrt(bx * bx + by * by + bz * bz);
            cB[0] = bx / l; cB[1] = by / l; cB[2] = bz / l;
            if (cB[1] > 0.08) break;
            ang += Math.PI;
        }
        cStart = now;
        cDur = 1100 + RNG.nextInt(900);
        cometActive = true;
    }

    private static void drawComet(VertexConsumer buf, Matrix4f mat, Vec3d cam, double rc, double vis, long now) {
        if (CustomSky.comets && vis > 0.4 && !cometActive) {
            if (nextComet == 0) nextComet = now + (long) (CustomSky.cometInterval * 1000 * (0.4 + 0.8 * RNG.nextDouble()));
            if (now >= nextComet) spawnComet(now);
        }
        if (!cometActive) return;

        double p = (now - cStart) / (double) cDur;
        if (p >= 1.25) {
            cometActive = false;
            nextComet = now + (long) (CustomSky.cometInterval * 1000 * (0.6 + 0.8 * RNG.nextDouble()));
            return;
        }
        double head = Math.min(p, 1.0);
        double fade = p <= 1.0 ? 1.0 : Math.max(0.0, 1.0 - (p - 1.0) / 0.25);
        float cr = WorldRenderUtil.colorR(CustomSky.cometColor), cg = WorldRenderUtil.colorG(CustomSky.cometColor), cb = WorldRenderUtil.colorB(CustomSky.cometColor);
        double aMax = 0.45 * Math.min(1.0, vis) * fade;
        if (aMax < 0.02) return;

        final int K = 10;
        double L = 0.35, wMax = rc * 0.0035;
        double[] px = new double[K + 1], py = new double[K + 1], pz = new double[K + 1];
        for (int k = 0; k <= K; k++) {
            double s = Math.max(0.0, Math.min(1.0, head - L * (1.0 - k / (double) K)));
            double x = cA[0] * (1 - s) + cB[0] * s, y = cA[1] * (1 - s) + cB[1] * s, z = cA[2] * (1 - s) + cB[2] * s;
            double l = Math.sqrt(x * x + y * y + z * z);
            px[k] = x / l; py[k] = y / l; pz[k] = z / l;
        }
        double[] lx = new double[K + 1], ly = new double[K + 1], lz = new double[K + 1], rx = new double[K + 1], ry = new double[K + 1], rz = new double[K + 1];
        float[] al = new float[K + 1];
        for (int k = 0; k <= K; k++) {
            int k0 = Math.max(0, k - 1), k1 = Math.min(K, k + 1);
            double tx = px[k1] - px[k0], ty = py[k1] - py[k0], tz = pz[k1] - pz[k0];
            double nx = py[k] * tz - pz[k] * ty, ny = pz[k] * tx - px[k] * tz, nz = px[k] * ty - py[k] * tx;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-9) nl = 1e-9;
            nx /= nl; ny /= nl; nz /= nl;
            double f = k / (double) K, w = wMax * f;
            double cx = cam.x + px[k] * rc, cy = cam.y + py[k] * rc, cz = cam.z + pz[k] * rc;
            lx[k] = cx + nx * w; ly[k] = cy + ny * w; lz[k] = cz + nz * w;
            rx[k] = cx - nx * w; ry[k] = cy - ny * w; rz[k] = cz - nz * w;
            al[k] = (float) (aMax * Math.pow(f, 1.5));
        }
        for (int k = 0; k < K; k++) {
            v(buf, mat, lx[k], ly[k], lz[k], cr, cg, cb, al[k]);
            v(buf, mat, rx[k], ry[k], rz[k], cr, cg, cb, al[k]);
            v(buf, mat, rx[k + 1], ry[k + 1], rz[k + 1], cr, cg, cb, al[k + 1]);
            v(buf, mat, lx[k + 1], ly[k + 1], lz[k + 1], cr, cg, cb, al[k + 1]);
        }
        basis(px[K], py[K], pz[K]);
        double hs = wMax * 1.6, hx = cam.x + px[K] * rc, hy = cam.y + py[K] * rc, hz = cam.z + pz[K] * rc;
        rhombus(buf, mat, hx, hy, hz, TR[0] * hs, TR[1] * hs, TR[2] * hs, TU[0] * hs, TU[1] * hs, TU[2] * hs, 1f, 1f, 1f, (float) aMax);
    }

    private static final double[][] CRATERS = {
            {-0.30, 0.35, 0.16}, {0.25, 0.10, 0.12}, {-0.10, -0.30, 0.20},
            {0.40, -0.35, 0.10}, {-0.45, -0.05, 0.09}, {0.05, 0.58, 0.08}
    };

    private static void drawMoon(VertexConsumer buf, Matrix4f mat, Vec3d cam, ClientWorld w, double R, double sinT, double cosT) {
        double mx = sinT, my = -cosT, mz = 0.0;
        if (my < -0.12) return;
        float fade = (float) Math.max(0.0, Math.min(1.0, (my + 0.12) / 0.2));

        int phase = CustomSky.moonPhases ? com.visualmod.util.McCompat.moonPhase(w, cam) : 0;
        double cph = Math.cos(phase / 8.0 * Math.PI * 2.0);
        double lit = (1.0 + cph) / 2.0;
        int side = phase >= 5 ? 1 : -1;
        float mr = WorldRenderUtil.colorR(CustomSky.moonColor), mg = WorldRenderUtil.colorG(CustomSky.moonColor), mb = WorldRenderUtil.colorB(CustomSky.moonColor);
        double tanA = Math.tan(Math.toRadians(CustomSky.moonSize));

        basis(mx, my, mz);
        final double rx = TR[0], ry = TR[1], rz = TR[2], ux = TU[0], uy = TU[1], uz = TU[2];

        if (CustomSky.moonGlow && lit > 0.02) {
            double rg = R * 0.980, rad = rg * tanA;
            double cx = cam.x + mx * rg, cy = cam.y + my * rg, cz = cam.z + mz * rg;
            float gr = mr * 0.8f + WorldRenderUtil.colorR(CustomSky.atmosphereColor) * 0.2f;
            float gg = mg * 0.8f + WorldRenderUtil.colorG(CustomSky.atmosphereColor) * 0.2f;
            float gb = mb * 0.8f + WorldRenderUtil.colorB(CustomSky.atmosphereColor) * 0.2f;
            ring(buf, mat, cx, cy, cz, rx, ry, rz, ux, uy, uz, rad * 0.9, rad * 2.2, 40, gr, gg, gb, (float) (0.30 * lit) * fade, 0f);
            ring(buf, mat, cx, cy, cz, rx, ry, rz, ux, uy, uz, rad * 2.2, rad * 4.2, 40, gr, gg, gb, (float) (0.07 * lit) * fade, 0f);
        }

        double rd = R * 0.975, rm = rd * tanA;
        double cx = cam.x + mx * rd, cy = cam.y + my * rd, cz = cam.z + mz * rd;
        fan(buf, mat, cx, cy, cz, rx, ry, rz, ux, uy, uz, 0, 0, rm, 40, mr * 0.35f, mg * 0.35f, mb * 0.40f, 0.16f * fade);

        double rl = R * 0.972, rml = rl * tanA;
        double lx = cam.x + mx * rl, ly = cam.y + my * rl, lz = cam.z + mz * rl;
        if (lit > 0.01) {
            final int N = 28;
            for (int k = 0; k < N; k++) {
                double y0 = -rml + 2 * rml * k / N, y1 = -rml + 2 * rml * (k + 1) / N;
                double e0 = Math.sqrt(Math.max(0, rml * rml - y0 * y0)), e1 = Math.sqrt(Math.max(0, rml * rml - y1 * y1));
                double t0 = -cph * e0, t1 = -cph * e1;
                double xa0, xb0, xa1, xb1;
                if (side > 0) { xa0 = t0; xb0 = e0; xa1 = t1; xb1 = e1; }
                else          { xa0 = -e0; xb0 = -t0; xa1 = -e1; xb1 = -t1; }
                moonVert(buf, mat, lx, ly, lz, rx, ry, rz, ux, uy, uz, xa0, y0, rml, mr, mg, mb, fade);
                moonVert(buf, mat, lx, ly, lz, rx, ry, rz, ux, uy, uz, xb0, y0, rml, mr, mg, mb, fade);
                moonVert(buf, mat, lx, ly, lz, rx, ry, rz, ux, uy, uz, xb1, y1, rml, mr, mg, mb, fade);
                moonVert(buf, mat, lx, ly, lz, rx, ry, rz, ux, uy, uz, xa1, y1, rml, mr, mg, mb, fade);
            }
        }

        double rc = R * 0.970, rmc = rc * tanA;
        double kx = cam.x + mx * rc, ky = cam.y + my * rc, kz = cam.z + mz * rc;
        for (double[] c : CRATERS) {
            double px = c[0] * rmc, py = c[1] * rmc, cr = c[2] * rmc;
            double e = Math.sqrt(Math.max(0, rmc * rmc - py * py)), t = -cph * e;
            double xa = side > 0 ? t : -e, xb = side > 0 ? e : -t;
            if (px - cr < xa || px + cr > xb) continue;
            fan(buf, mat, kx, ky, kz, rx, ry, rz, ux, uy, uz, px, py, cr, 16, mr * 0.62f, mg * 0.64f, mb * 0.72f, 0.55f * fade);
        }
    }

    private static void moonVert(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                                 double rx, double ry, double rz, double ux, double uy, double uz,
                                 double x, double y, double rm, float r, float g, float b, float fade) {
        double shade = 0.86 + 0.14 * Math.max(-1.0, Math.min(1.0, (-x * 0.55 + y * 0.80) / rm));
        float s = (float) shade;
        v(buf, mat, cx + rx * x + ux * y, cy + ry * x + uy * y, cz + rz * x + uz * y, r * s, g * s, b * s, 0.97f * fade);
    }

    private static void basis(double dx, double dy, double dz) {
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.98) { ux = 1; uy = 0; uz = 0; }
        double rx = dy * uz - dz * uy, ry = dz * ux - dx * uz, rz = dx * uy - dy * ux;
        double l = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (l < 1e-9) l = 1e-9;
        rx /= l; ry /= l; rz /= l;
        TR[0] = rx; TR[1] = ry; TR[2] = rz;
        TU[0] = ry * dz - rz * dy; TU[1] = rz * dx - rx * dz; TU[2] = rx * dy - ry * dx;
    }

    private static void v(VertexConsumer buf, Matrix4f mat, double x, double y, double z, float r, float g, float b, float a) {
        buf.vertex(mat, (float) x, (float) y, (float) z).color(r, g, b, Math.max(0f, Math.min(1f, a)));
    }

    private static void rhombus(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                                double ax, double ay, double az, double bx, double by, double bz,
                                float r, float g, float b, float a) {
        v(buf, mat, cx + ax, cy + ay, cz + az, r, g, b, a);
        v(buf, mat, cx + bx, cy + by, cz + bz, r, g, b, a);
        v(buf, mat, cx - ax, cy - ay, cz - az, r, g, b, a);
        v(buf, mat, cx - bx, cy - by, cz - bz, r, g, b, a);
    }

    private static void fan(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                            double rx, double ry, double rz, double ux, double uy, double uz,
                            double ox, double oy, double rad, int n, float r, float g, float b, float a) {
        double bx = cx + rx * ox + ux * oy, by = cy + ry * ox + uy * oy, bz = cz + rz * ox + uz * oy;
        for (int k = 0; k < n; k++) {
            double p0 = Math.PI * 2 * k / n, p1 = Math.PI * 2 * (k + 1) / n;
            double c0 = Math.cos(p0) * rad, s0 = Math.sin(p0) * rad, c1 = Math.cos(p1) * rad, s1 = Math.sin(p1) * rad;
            v(buf, mat, bx, by, bz, r, g, b, a);
            v(buf, mat, bx + rx * c0 + ux * s0, by + ry * c0 + uy * s0, bz + rz * c0 + uz * s0, r, g, b, a);
            v(buf, mat, bx + rx * c1 + ux * s1, by + ry * c1 + uy * s1, bz + rz * c1 + uz * s1, r, g, b, a);
            v(buf, mat, bx + rx * c1 + ux * s1, by + ry * c1 + uy * s1, bz + rz * c1 + uz * s1, r, g, b, a);
        }
    }

    private static void ring(VertexConsumer buf, Matrix4f mat, double cx, double cy, double cz,
                             double rx, double ry, double rz, double ux, double uy, double uz,
                             double r0, double r1, int n, float cr, float cg, float cb, float a0, float a1) {
        for (int k = 0; k < n; k++) {
            double p0 = Math.PI * 2 * k / n, p1 = Math.PI * 2 * (k + 1) / n;
            double c0 = Math.cos(p0), s0 = Math.sin(p0), c1 = Math.cos(p1), s1 = Math.sin(p1);
            v(buf, mat, cx + (rx * c0 + ux * s0) * r0, cy + (ry * c0 + uy * s0) * r0, cz + (rz * c0 + uz * s0) * r0, cr, cg, cb, a0);
            v(buf, mat, cx + (rx * c0 + ux * s0) * r1, cy + (ry * c0 + uy * s0) * r1, cz + (rz * c0 + uz * s0) * r1, cr, cg, cb, a1);
            v(buf, mat, cx + (rx * c1 + ux * s1) * r1, cy + (ry * c1 + uy * s1) * r1, cz + (rz * c1 + uz * s1) * r1, cr, cg, cb, a1);
            v(buf, mat, cx + (rx * c1 + ux * s1) * r0, cy + (ry * c1 + uy * s1) * r0, cz + (rz * c1 + uz * s1) * r0, cr, cg, cb, a0);
        }
    }
}
