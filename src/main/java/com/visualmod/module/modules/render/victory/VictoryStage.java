package com.visualmod.module.modules.render.victory;

import net.minecraft.client.render.RenderLayers;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class VictoryStage {

    public static final class Actor {
        public double x, y, z;
        public float yaw;
        public float scale = 1f;
        public float alpha = 1f;
        public int color = 0xFFB07A55;
        public boolean silhouette;

        public float headTilt;
        public float bodyLean;
        public float leftArm, rightArm;
        public float leftArmOut = 0.12f, rightArmOut = 0.12f;
        public float leftLeg, rightLeg;
        public float hipsDrop;
        public float upperShiftX;
        public float upperShiftY;
        public float upperTilt;
        public boolean hasHead = true;
        public boolean headOnly;
        public boolean neckBlood;
        public boolean wound;
        public String held;
        public float bladePitch = -0.15f;
        public float bladeYaw;
    }

    public static final class Prop {
        public double x, y, z;
        public float yaw, pitch, roll, alpha = 1f;
        public String kind = "knife";
        public float size = 1f;
    }

    public static final class Arc {
        public double x, y, z;
        public float radius, halfHeight, from, to, alpha, thickness;
        public int color = 0xFFFFFFFF;
    }

    private static final List<Actor> ACTORS = new ArrayList<>();
    private static final List<Prop> PROPS = new ArrayList<>();
    private static final List<Arc> ARCS = new ArrayList<>();
    private static volatile boolean active;
    private static volatile int swordStyle;

    public static volatile Vec3d victimPos = Vec3d.ZERO;
    public static volatile Vec3d killerPos = Vec3d.ZERO;
    public static volatile float faceToVictim;
    public static volatile double gap = 2.0;
    public static volatile double orbitStartDeg;

    private VictoryStage() {}

    static { WorldRenderEvents.AFTER_ENTITIES.register(VictoryStage::render); }

    public static void begin(int style) {
        swordStyle = style;
        active = true;
        ACTORS.clear();
        PROPS.clear();
        ARCS.clear();
    }

    public static Actor add() {
        Actor a = new Actor();
        ACTORS.add(a);
        return a;
    }

    public static Prop prop() {
        Prop p = new Prop();
        PROPS.add(p);
        return p;
    }

    public static Arc arc() {
        Arc a = new Arc();
        ARCS.add(a);
        return a;
    }

    public static void end() {  }

    public static void release() {
        active = false;
        ACTORS.clear();
        PROPS.clear();
        ARCS.clear();
    }

    public static float faceTo(double dx, double dz) { return (float) Math.atan2(-dx, -dz); }

    public static Vec3d ahead(Vec3d from, Vec3d to, double dist) {
        Vec3d d = to.subtract(from);
        double len = Math.max(0.001, Math.sqrt(d.x * d.x + d.z * d.z));
        return from.add(d.x / len * dist, 0, d.z / len * dist);
    }

    public static void setScene(Vec3d victim, Vec3d killer) {
        victimPos = victim;
        killerPos = killer;
        faceToVictim = faceTo(victim.x - killer.x, victim.z - killer.z);
        Vec3d d = victim.subtract(killer);
        gap = Math.min(6.0, Math.max(1.6, Math.sqrt(d.x * d.x + d.z * d.z)));
        orbitStartDeg = Math.toDegrees(Math.atan2(-(killer.x - victim.x), killer.z - victim.z));
    }

    private static void render(WorldRenderContext ctx) {
        if (!active || ACTORS.isEmpty() && PROPS.isEmpty() && ARCS.isEmpty()) return;
        if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { release(); return; }
        if (ctx.matrices() == null || ctx.consumers() == null || ctx.gameRenderer().getCamera() == null) return;

        try {
            Vec3d cam = ctx.gameRenderer().getCamera().getCameraPos();
            VertexConsumer buf = ctx.consumers().getBuffer(RenderLayers.debugQuads());
            ctx.matrices().push();
            ctx.matrices().translate(-cam.x, -cam.y, -cam.z);
            Matrix4f view = new Matrix4f(ctx.matrices().peek().getPositionMatrix());
            for (Actor a : ACTORS) drawActor(buf, view, a);
            for (Prop p : PROPS) drawProp(buf, view, p);
            for (Arc k : ARCS) drawArc(buf, view, k);
            ctx.matrices().pop();
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("VictoryStage", t);
        }
    }

    private static final float HIP_Y = 0.72f, BODY_H = 0.7f, BODY_W = 0.5f;
    private static final float LIMB_W = 0.24f, LIMB_L = 0.7f;
    private static final float SHO_Y = HIP_Y + BODY_H - 0.06f;

    private static void drawActor(VertexConsumer buf, Matrix4f view, Actor a) {
        if (a.alpha <= 0.01f) return;
        Matrix4f m = new Matrix4f(view);
        m.translate((float) a.x, (float) a.y, (float) a.z);
        m.rotateY(a.yaw);
        m.scale(a.scale);

        float r, g, b;
        if (a.silhouette) { r = 0.02f; g = 0.02f; b = 0.03f; }
        else { r = ((a.color >> 16) & 0xFF) / 255f; g = ((a.color >> 8) & 0xFF) / 255f; b = (a.color & 0xFF) / 255f; }

        float hip = HIP_Y - a.hipsDrop;
        Matrix4f torso = new Matrix4f(m);
        torso.translate(a.upperShiftX, hip + a.upperShiftY, 0);
        torso.rotateZ(a.upperTilt);
        torso.rotateX(a.bodyLean);
        if (a.headOnly) {
            Matrix4f fly = new Matrix4f(m);
            fly.rotateX(a.bodyLean);
            limb(buf, fly, 0, 0.23f, 0, 0.46f, 0.46f, 0.46f, r * 1.1f, g * 1.05f, b, a.alpha);
            limb(buf, fly, 0, 0.26f, -0.24f, 0.34f, 0.07f, 0.02f, 0.08f, 0.08f, 0.1f, a.alpha);
            return;
        }
        limb(buf, torso, 0, BODY_H * 0.5f, 0, BODY_W, BODY_H, 0.28f, r, g, b, a.alpha);
        if (a.neckBlood) {
            limb(buf, torso, 0, BODY_H - 0.04f, 0, BODY_W * 0.7f, 0.12f, 0.3f, 0.75f, 0.05f, 0.05f, a.alpha);
        }
        if (a.wound) {
            limb(buf, torso, 0, BODY_H * 0.55f, -0.16f, 0.16f, 0.2f, 0.04f, 0.72f, 0.04f, 0.04f, a.alpha);
        }

        Matrix4f head = new Matrix4f(torso);
        head.translate(0, BODY_H + 0.02f, 0);
        head.rotateX(a.headTilt - 0.12f);
        if (a.hasHead) {
            limb(buf, head, 0, 0.24f, 0, 0.46f, 0.46f, 0.46f,
                a.silhouette ? r : r * 1.15f, a.silhouette ? g : g * 1.1f, a.silhouette ? b : b * 1.05f, a.alpha);
            if (!a.silhouette) {
                limb(buf, head, 0, 0.27f, -0.24f, 0.34f, 0.07f, 0.02f, 0.08f, 0.08f, 0.1f, a.alpha);
            }
        }

        drawArm(buf, torso, +1, a.leftArm, a.leftArmOut, r * 0.9f, g * 0.9f, b * 0.9f, a.alpha, null);
        Matrix4f hand = drawArm(buf, torso, -1, a.rightArm, a.rightArmOut,
            r * 0.9f, g * 0.9f, b * 0.9f, a.alpha, a.held == null ? null : a);

        leg(buf, m, +1, hip, a.leftLeg, r * 0.55f, g * 0.55f, b * 0.6f, a.alpha);
        leg(buf, m, -1, hip, a.rightLeg, r * 0.55f, g * 0.55f, b * 0.6f, a.alpha);

        if (hand != null) bladeAt(buf, hand, a.held, a.bladePitch, a.bladeYaw, a.alpha);
    }

    private static void drawProp(VertexConsumer buf, Matrix4f view, Prop p) {
        if (p.alpha <= 0.01f) return;
        Matrix4f m = new Matrix4f(view);
        m.translate((float) p.x, (float) p.y, (float) p.z);
        m.rotateY(p.yaw);
        m.rotateZ(p.roll);
        m.rotateX(p.pitch);
        m.scale(p.size);
        bladeAt(buf, m, p.kind, 0f, 0f, p.alpha);
    }

    private static Matrix4f drawArm(VertexConsumer buf, Matrix4f torso, int side,
                                    float swing, float out, float r, float g, float b, float a, Actor heldBy) {
        Matrix4f arm = new Matrix4f(torso);
        arm.translate(side * (BODY_W * 0.5f + LIMB_W * 0.42f), SHO_Y - HIP_Y, 0);
        arm.rotateZ(-side * out);
        arm.rotateX(swing);
        limb(buf, arm, 0, -LIMB_L * 0.5f, 0, LIMB_W, LIMB_L, LIMB_W, r, g, b, a);
        if (heldBy == null) return null;
        arm.translate(0, -LIMB_L, 0);
        return arm;
    }

    private static void leg(VertexConsumer buf, Matrix4f m, int side, float hip, float swing,
                            float r, float g, float b, float a) {
        Matrix4f leg = new Matrix4f(m);
        leg.translate(side * BODY_W * 0.26f, hip, 0);
        leg.rotateX(swing);
        limb(buf, leg, 0, -LIMB_L * 0.5f, 0, LIMB_W, LIMB_L, LIMB_W, r, g, b, a);
    }

    private static void limb(VertexConsumer buf, Matrix4f m, float cx, float cy, float cz,
                             float w, float h, float d, float r, float g, float b, float a) {
        float x0 = cx - w / 2, x1 = cx + w / 2;
        float y0 = cy - h / 2, y1 = cy + h / 2;
        float z0 = cz - d / 2, z1 = cz + d / 2;
        face(buf, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r * 1.12f, g * 1.12f, b * 1.12f, a);
        face(buf, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * 0.5f, g * 0.5f, b * 0.5f, a);
        face(buf, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        face(buf, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r * 0.7f, g * 0.7f, b * 0.7f, a);
        face(buf, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r * 0.85f, g * 0.85f, b * 0.85f, a);
        face(buf, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * 0.85f, g * 0.85f, b * 0.85f, a);
    }

    private static void face(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
        buf.vertex(m, x4, y4, z4).color(r, g, b, a);
    }

    private static void bladeAt(VertexConsumer buf, Matrix4f hand, String kind,
                               float pitch, float yaw, float alpha) {
        if (kind == null) return;
        Matrix4f bl = new Matrix4f(hand);
        bl.rotateZ(yaw);
        bl.rotateX(pitch);
        if ("knife".equals(kind)) {
            limb(buf, bl, 0, 0.16f, 0, 0.05f, 0.32f, 0.05f, 0.86f, 0.88f, 0.92f, alpha);
            limb(buf, bl, 0, -0.02f, 0, 0.08f, 0.1f, 0.08f, 0.35f, 0.22f, 0.12f, alpha);
            return;
        }
        switch (swordStyle) {
            case 1 -> {
                limb(buf, bl, 0, 0.62f, 0, 0.11f, 1.05f, 0.04f, 0.8f, 0.84f, 0.9f, alpha);
                limb(buf, bl, 0, 0.05f, 0, 0.34f, 0.07f, 0.07f, 0.75f, 0.62f, 0.2f, alpha);
                limb(buf, bl, 0, -0.12f, 0, 0.06f, 0.28f, 0.06f, 0.3f, 0.2f, 0.1f, alpha);
            }
            case 2 -> {
                limb(buf, bl, 0, 0.2f, 0, 0.06f, 0.4f, 0.05f, 0.72f, 0.76f, 0.82f, alpha);
                limb(buf, bl, 0.03f, 0.02f, 0, 0.12f, 0.05f, 0.05f, 0.3f, 0.24f, 0.12f, alpha);
            }
            case 3 -> {
                limb(buf, bl, 0, 0.55f, 0, 0.06f, 1.25f, 0.06f, 0.32f, 0.24f, 0.16f, alpha);
                for (int i = 0; i < 6; i++) {
                    float t = i / 6f;
                    limb(buf, bl, 0.16f * t, 1.2f - 0.03f * i, 0, 0.16f, 0.05f, 0.04f,
                        0.85f, 0.88f, 0.94f, alpha);
                }
            }
            default -> {
                limb(buf, bl, 0, 0.6f, 0, 0.05f, 1.15f, 0.035f, 0.88f, 0.9f, 0.96f, alpha);
                limb(buf, bl, 0.02f, 1.18f, 0, 0.05f, 0.06f, 0.03f, 0.95f, 0.96f, 1f, alpha);
                limb(buf, bl, 0, 0.02f, 0, 0.26f, 0.05f, 0.05f, 0.1f, 0.1f, 0.12f, alpha);
                limb(buf, bl, 0, -0.16f, 0, 0.07f, 0.32f, 0.07f, 0.25f, 0.16f, 0.08f, alpha);
            }
        }
    }

    private static void drawArc(VertexConsumer buf, Matrix4f view, Arc k) {
        if (k.alpha <= 0.01f) return;
        float r = ((k.color >> 16) & 0xFF) / 255f;
        float g = ((k.color >> 8) & 0xFF) / 255f;
        float b = (k.color & 0xFF) / 255f;
        final int SEG = 16;
        for (int i = 0; i < SEG; i++) {
            float a0 = k.from + (k.to - k.from) * i / SEG;
            float a1 = k.from + (k.to - k.from) * (i + 1) / SEG;
            float t0 = (float) (k.x + Math.cos(a0) * k.radius);
            float u0 = (float) (k.z + Math.sin(a0) * k.radius);
            float t1 = (float) (k.x + Math.cos(a1) * k.radius);
            float u1 = (float) (k.z + Math.sin(a1) * k.radius);
            float fy = (float) k.y, hy = fy + k.halfHeight, ly = fy - k.halfHeight;
            float fade = k.alpha * (1f - Math.abs(i - SEG / 2f) / (SEG / 2f) * 0.65f);
            buf.vertex(view, t0, ly, u0).color(r, g, b, fade);
            buf.vertex(view, t1, ly, u1).color(r, g, b, fade * 0.8f);
            buf.vertex(view, t1, hy, u1).color(r, g, b, fade * 0.8f);
            buf.vertex(view, t0, hy, u0).color(r, g, b, fade);
        }
    }
}
