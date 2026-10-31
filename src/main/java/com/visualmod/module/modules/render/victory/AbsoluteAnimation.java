package com.visualmod.module.modules.render.victory;

import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class AbsoluteAnimation extends VictoryAnimation {

    private float topHalfY = 0, topHalfX = 0;
    private float botHalfY = 0;
    private boolean split = false;
    private float dashX = 0;

    public AbsoluteAnimation(Identifier skin, int swordStyle) {
        super(skin, swordStyle);
        phaseDurations = new float[]{1.5f, 2.5f, 2f, 3f, 4f, 2f};
    }

    @Override
    public void start() {
        super.start();
        split = false;
        dashX = 0;
        topHalfY = 0; topHalfX = 0; botHalfY = 0;
    }

    @Override
    protected void onTick(float dt) {
        if (phase >= 3 && split) {
            topHalfY += 60 * dt;
            topHalfX += 80 * dt;
            botHalfY += 40 * dt;
        }
        if (phase == 2 && phaseTime > 0.5f) split = true;
    }

    @Override
    public void render(DrawContext ctx) {
        if (scene3D()) { render3DOverlay(ctx); return; }

        int w = W(), h = H();
        float cx = w * 0.5f, cy = h * 0.6f;
        float scale = h / 120f;

        drawBackground(ctx, 0xFF0D0D0D, 0xFF050508, 0.55f);
        drawLetterbox(ctx);

        switch (phase) {
            case 0 -> {
                drawUs   (ctx, cx - 80*scale, cy, scale, -0.3f, true);
                drawEnemy(ctx, cx + 80*scale, cy, scale, 1f);
                drawFade(ctx, 1f - easeOut(phaseTime));
            }
            case 1 -> renderDash(ctx, cx, cy, scale);
            case 2 -> renderSplit(ctx, cx, cy, scale);
            case 3 -> renderSword(ctx, cx, cy, scale);
            case 4 -> renderFinal(ctx, cx, cy, scale);
            case 5 -> { renderFinal(ctx, cx, cy, scale); drawFade(ctx, phaseTime); }
        }
    }

    private void renderDash(DrawContext ctx, float cx, float cy, float s) {
        float t = easeIn(phaseTime);
        drawEnemy(ctx, cx + 80*s, cy, s, 1f);
        for (int i = 4; i >= 0; i--) {
            float ghostT = Math.max(0, t - i * 0.05f);
            float ghostX = lerp(cx - 80*s, cx + 40*s, ghostT);
            float alpha  = (i == 0) ? 1f : (0.2f - i*0.04f);
            drawUs(ctx, ghostX, cy, s, lerp(-0.3f, 0.3f, ghostT), true);
            if (i > 0) {
                ctx.fill((int)ghostX - 2, (int)(cy - 30*s),
                         (int)(cx - 80*s - i*30), (int)(cy - 28*s),
                         argb(0.6f, 0.5f, 1f, alpha * 0.5f));
            }
        }
        for (int i = 0; i < 20; i++) {
            float lineY = (float)(Math.random() * H());
            float len   = lerp(0, 200, t);
            ctx.fill(0, (int)lineY, (int)(cx * t - 20), (int)lineY + 1,
                argb(0.5f, 0.4f, 1f, 0.15f));
        }
    }

    private void renderSplit(DrawContext ctx, float cx, float cy, float s) {
        float t = phaseTime;

        if (t < 0.3f) {
            drawUs   (ctx, cx + 20*s, cy, s, 0.2f, true);
            drawEnemy(ctx, cx + 80*s, cy, s, 1f);
        } else {
            float flash = Math.max(0, 1f - Math.abs(t - 0.35f) * 8f);
            if (flash > 0) ctx.fill(0, 0, W(), H(), argb(1f, 1f, 1f, flash * 0.8f));

            float slashProgress = easeOut((t - 0.3f) / 0.3f);
            float slashX = cx + 80*s;
            ctx.fill((int)slashX - 1, 0, (int)slashX + 1,
                (int)(H() * slashProgress), argb(1f, 1f, 1f, 0.95f));

            if (split) {
                drawEnemyHalf(ctx, cx + 80*s + topHalfX, cy + topHalfY, s, true,  1f);
                drawEnemyHalf(ctx, cx + 80*s - topHalfX*0.3f, cy + botHalfY, s, false, 1f);
            } else {
                drawEnemy(ctx, cx + 80*s, cy, s, 1f);
            }
            drawUs(ctx, cx + 20*s, cy, s, 0.2f, true);
        }
    }

    private void renderSword(DrawContext ctx, float cx, float cy, float s) {
        float t = easeOut(phaseTime);

        drawEnemyHalf(ctx, cx + 80*s + topHalfX, Math.min(cy + s*15f, cy + topHalfY), s, true, 1f - easeIn(phaseTime)*0.3f);
        drawEnemyHalf(ctx, cx + 80*s - topHalfX*0.3f, Math.min(cy + s*20f, cy + botHalfY), s, false, 1f - easeIn(phaseTime)*0.3f);

        float swordY = lerp(cy - 40*s, cy + 5*s, t);
        float swordBobble = (float)(Math.sin(phaseTime * 20f) * 2 * Math.max(0, 1f - phaseTime*2));
        drawUs(ctx, cx + 20*s, cy, s, 0.1f, false);
        drawSword(ctx, cx + 15*s, swordY + swordBobble, s * 1.4f, 0f, -1.57f + t*0.5f, 0xFF7B5CF0, 1f);

        if (phaseTime > 0.7f && phaseTime < 0.9f) {
            float dust = (phaseTime - 0.7f) / 0.2f;
            for (int i = 0; i < 8; i++) {
                float ang = (float)(i * Math.PI / 4 + dust);
                float dx  = (float)(Math.cos(ang) * dust * 40 * s);
                float dy  = (float)(Math.sin(ang) * dust * 20 * s);
                HudStyle.fillRounded(ctx, cx + 15*s + dx, cy + dy, 3*s, 3*s, 1*s,
                    argb(0.8f, 0.6f, 0.2f, (1f-dust)*0.7f));
            }
        }
    }

    private void renderFinal(DrawContext ctx, float cx, float cy, float s) {
        float zoom = lerp(1f, 0.7f, easeOut(phaseTime));

        drawEnemyHalf(ctx, cx + 80*s*zoom + topHalfX, cy + topHalfY*zoom + s*15f, s*zoom, true, 0.7f);
        drawEnemyHalf(ctx, cx + 80*s*zoom, cy + botHalfY*zoom + s*20f, s*zoom, false, 0.7f);
        drawUs(ctx, cx*zoom, cy, s*zoom, 0.1f, false);
        drawSword(ctx, cx*zoom + 15*s*zoom, cy + 5*s*zoom, s*1.4f*zoom, 0f, -1.1f, 0xFF7B5CF0, 1f);

        if (phaseTime > 0.5f) {
            float ta = easeOut((phaseTime - 0.5f) * 2);
            drawCenteredText(ctx, "§5§lABSOLUTE", H()/2 + (int)(H()*0.1f),
                argb(0.8f, 0.3f, 1f, ta));
        }
    }

    @Override
    public void buildStage() {
        net.minecraft.util.math.Vec3d v = VictoryStage.victimPos;
        net.minecraft.util.math.Vec3d k = VictoryStage.killerPos;
        net.minecraft.util.math.Vec3d near = VictoryStage.ahead(v, k, 1.15);
        float appear = phase == 0 ? easeOut(phaseTime) : 1f;

        net.minecraft.util.math.Vec3d us = switch (phase) {
            case 0 -> k;
            case 1 -> slide(k, near, easeIn(phaseTime));
            default -> near;
        };
        float myYaw = VictoryStage.faceTo(v.x - us.x, v.z - us.z);
        float foeYaw = VictoryStage.faceTo(us.x - v.x, us.z - v.z);

        VictoryStage.Actor foe = stageAt(v.x, v.y, v.z, foeYaw, 0xFF884444);
        foe.alpha = appear;
        VictoryStage.Actor me = stageAt(us.x, us.y, us.z, myYaw, 0xFF7B5CF0);
        me.alpha = appear;
        me.held = "sword";

        switch (phase) {
            case 0 -> {
                me.rightArm = -0.4f;
                me.bodyLean = 0.05f;
            }
            case 1 -> {
                float t = easeIn(phaseTime);
                me.bodyLean = 0.55f;
                me.rightArm = -0.9f;
                me.leftArm = 0.9f;
                for (int i = 1; i <= 3; i++) {
                    float gt = Math.max(0f, t - i * 0.11f);
                    net.minecraft.util.math.Vec3d g = slide(k, near, gt);
                    VictoryStage.Actor ghost = stageAt(g.x, g.y, g.z, myYaw, 0xFF5A3FB0);
                    ghost.alpha = (0.28f - i * 0.07f) * appear;
                    ghost.bodyLean = 0.55f;
                    ghost.held = null;
                    ghost.leftLeg = 0.5f; ghost.rightLeg = -0.5f;
                }
            }
            case 2 -> {
                float t = phaseTime;
                float swing = t < 0.5f ? lerp(-0.9f, -2.4f, t / 0.5f)
                                       : lerp(-2.4f, 1.2f, Math.min(1f, (t - 0.5f) / 0.18f));
                me.rightArm = swing;
                me.bladePitch = lerp(-0.2f, 0.6f, clamp01((t - 0.4f) / 0.3f));
                me.bodyLean = lerp(0.55f, 0.2f, clamp01(t / 0.6f));
                if (t > 0.5f) {
                    float p = easeOut((t - 0.5f) / 0.5f);
                    foe.upperShiftX = 0.42f * p;
                    foe.upperShiftY = -0.12f * p;
                    foe.upperTilt = 0.55f * p;
                    foe.alpha = appear * (1f - 0.25f * p);
                    VictoryStage.Arc cut = VictoryStage.arc();
                    cut.x = v.x; cut.y = v.y + 1.0; cut.z = v.z;
                    cut.radius = 1.5f;
                    cut.halfHeight = 1.15f;
                    cut.from = foeYaw - 0.35f;
                    cut.to = foeYaw + 0.35f;
                    cut.alpha = Math.max(0f, 0.9f - p * 0.9f);
                }
            }
            case 3 -> {
                float t = easeOut(phaseTime);
                me.rightArm = lerp(0.6f, 1.5f, t);
                me.held = t > 0.55f ? null : "sword";
                foe.upperShiftX = 0.42f + 0.3f * t;
                foe.upperShiftY = -0.12f - 0.5f * t;
                foe.upperTilt = 0.55f + 0.5f * t;
                foe.alpha = appear * (1f - 0.25f * t);
                net.minecraft.util.math.Vec3d spot = VictoryStage.ahead(us, v, 0.7);
                VictoryStage.Prop sword = VictoryStage.prop();
                sword.kind = "sword";
                sword.x = spot.x; sword.z = spot.z;
                sword.y = spot.y + 0.02;
                sword.yaw = myYaw;
                sword.roll = lerp(1.4f, 0.06f, t);
                sword.alpha = appear;
                if (phaseTime > 0.62f && phaseTime < 0.78f) {
                    VictoryStage.Arc ring = VictoryStage.arc();
                    ring.x = spot.x; ring.y = spot.y; ring.z = spot.z;
                    ring.radius = 0.5f + (phaseTime - 0.62f) * 4f;
                    ring.halfHeight = 0.12f;
                    ring.from = 0f; ring.to = (float) (Math.PI * 2);
                    ring.color = 0xFFD8A24A;
                    ring.alpha = 0.7f - (phaseTime - 0.62f) * 3.5f;
                }
            }
            default -> {
                float p = Math.min(1f, phaseTime + (phase == 5 ? 1f : 0f));
                me.rightArm = 1.5f;
                me.held = null;
                me.leftArm = 0.1f;
                foe.upperShiftX = 0.72f + 0.1f * p;
                foe.upperShiftY = -0.62f;
                foe.upperTilt = 1.05f;
                foe.alpha = appear * 0.7f;
                net.minecraft.util.math.Vec3d spot = VictoryStage.ahead(us, v, 0.7);
                VictoryStage.Prop sword = VictoryStage.prop();
                sword.kind = "sword";
                sword.x = spot.x; sword.z = spot.z; sword.y = spot.y + 0.02;
                sword.yaw = myYaw;
                sword.roll = 0.06f;
                sword.alpha = appear;
            }
        }
    }

    private static net.minecraft.util.math.Vec3d slide(net.minecraft.util.math.Vec3d from,
                                                       net.minecraft.util.math.Vec3d to, float t) {
        return new net.minecraft.util.math.Vec3d(
            lerp((float) from.x, (float) to.x, t),
            lerp((float) from.y, (float) to.y, t),
            lerp((float) from.z, (float) to.z, t));
    }

    private void render3DOverlay(DrawContext ctx) {
        drawLetterbox(ctx);
        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        if (phase == 5) drawFade(ctx, easeIn(phaseTime));

        if (phase == 1) {
            float t = easeIn(phaseTime);
            for (int i = 0; i < 18; i++) {
                float y = ((i * 617) % H());
                ctx.fill(0, (int) y, (int) (W() * 0.5f * t), (int) y + 1,
                    argb(0.55f, 0.45f, 1f, 0.16f * (1f - t * 0.4f)));
            }
        }
        if (phase == 2 && phaseTime > 0.42f && phaseTime < 0.62f) {
            float flash = 1f - Math.abs(phaseTime - 0.52f) / 0.1f;
            ctx.fill(0, 0, W(), H(), argb(1f, 1f, 1f, Math.max(0f, flash) * 0.55f));
        }
        if (phase >= 4 && phaseTime > 0.5f) {
            float ta = easeOut((phaseTime - 0.5f) * 2);
            drawCenteredText(ctx, "§5§lABSOLUTE", H() / 2 + (int) (H() * 0.1f),
                argb(0.8f, 0.3f, 1f, ta));
        }
    }

    private void drawEnemy(DrawContext ctx, float x, float y, float s, float alpha) {
        drawPlayerFigure(ctx, x, y, s, 0.3f, 0xFF884444, alpha,
            0f, 0.2f, 0.2f, 0.1f, -0.1f, false);
    }

    private void drawEnemyHalf(DrawContext ctx, float x, float y, float s,
                                boolean rightHalf, float alpha) {
        float offX = rightHalf ? 2*s : -6*s;
        HudStyle.fillRounded(ctx, x + offX, y - 12*s, 4*s, 24*s, 1*s,
            argb(0.53f, 0.27f, 0.27f, alpha));
    }

    private void drawUs(DrawContext ctx, float x, float y, float s, float rotY, boolean sword) {
        drawPlayerFigure(ctx, x, y, s, rotY, 0xFF7B5CF0, 1f,
            0f, -0.2f, sword ? -1.8f : 0.3f, 0.1f, -0.1f, sword);
    }
}
