package com.visualmod.module.modules.render.victory;

import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class TheWinAnimation extends VictoryAnimation {

    private float topY = 0, topAlpha = 1f;
    private float botY = 0, botAlpha = 1f;
    private boolean splitDone = false;

    public TheWinAnimation(Identifier skin, int swordStyle) {
        super(skin, swordStyle);
        phaseDurations = new float[]{2f, 3.5f, 1.5f, 3.5f, 3.5f, 2f};
    }

    @Override
    public void start() {
        super.start();
        topY = 0; botY = 0; topAlpha = 1f; botAlpha = 1f; splitDone = false;
    }

    @Override
    protected void onTick(float dt) {
        if (phase == 3 && phaseTime > 0.3f) {
            splitDone = true;
            topY -= 70f * dt;
            botY += 50f * dt;
            topAlpha = Math.max(0f, topAlpha - dt * 0.8f);
            botAlpha = Math.max(0f, botAlpha - dt * 0.5f);
        }
    }

    @Override
    public void render(DrawContext ctx) {
        if (scene3D()) { render3DOverlay(ctx); return; }
        int w = W(), h = H();
        float cx = w * 0.5f, cy = h * 0.6f;
        float scale = h / 120f;

        switch (phase) {
            case 0 -> renderFirstPerson(ctx, cx, cy, scale, false);
            case 1 -> renderFirstPerson(ctx, cx, cy, scale, true);
            case 2 -> renderHitFlash(ctx, cx, cy, scale);
            case 3 -> renderThirdPerson(ctx, cx, cy, scale);
            case 4 -> renderEpilogue(ctx, cx, cy, scale);
            case 5 -> { renderEpilogue(ctx, cx, cy, scale); drawFade(ctx, phaseTime); }
        }

        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        drawLetterbox(ctx);
    }

    private void renderFirstPerson(DrawContext ctx, float cx, float cy, float s, boolean raising) {
        float t = phaseTime;

        drawBackground(ctx, 0xFF2C3E1A, 0xFF4A6B8C, 0.5f);

        float armRaise = raising ? easeInOut(t) * 0.5f : 0f;
        HudStyle.fillRounded(ctx, 20, (int)(cy + 30*s - armRaise*10*s), 20*s, 30*s, 3*s,
            0xFF8B6B6B);
        HudStyle.fillRounded(ctx, W()-20-20*s, (int)(cy + 30*s - armRaise*10*s), 20*s, 30*s, 3*s,
            0xFF8B6B6B);

        float dist  = lerp(0.4f, raising ? 1.6f : 0.8f, easeOut(t));
        float usS   = s * dist * 0.9f;
        float usY   = lerp(cy + 20*s, cy - 10*s, raising ? easeOut(t) : 0f);

        drawPlayerFigure(ctx, cx, usY, usS, 0f, 0xFF7B5CF0, 1f,
            lerp(0f, -0.3f, raising ? t : 0f),
            0f,
            raising ? lerp(0f, -2.5f, easeIn(t)) : 0f,
            0.1f, -0.1f, true);

        float breathX = (float)(Math.sin(time * 1.5f) * 3);
        float breathY = (float)(Math.cos(time * 1.2f) * 2);
        ctx.fill(0, 0, W(), H(), 0x00000000);
    }

    private void renderHitFlash(DrawContext ctx, float cx, float cy, float s) {
        float t = phaseTime;
        float flash = t < 0.3f ? 1f : Math.max(0f, 1f - (t - 0.3f) / 0.7f);
        drawBackground(ctx, 0xFF2C3E1A, 0xFF4A6B8C, 0.5f);
        ctx.fill(0, 0, W(), H(), argb(1f, 1f, 1f, flash));

        if (t > 0.1f) {
            float wave = (t - 0.1f) / 0.9f;
            for (int ring = 0; ring < 4; ring++) {
                float r = (wave + ring * 0.15f) % 1f;
                int rc = argb(1f, 1f, 1f, (1f-r) * 0.5f * flash);
                int rx = (int)(cx - r * W() * 0.8f);
                int ry = (int)(cy - r * H() * 0.8f);
                int rw = (int)(r * W() * 1.6f);
                int rh = (int)(r * H() * 1.6f);
                ctx.fill(rx, ry, rx + rw, ry + 3, rc);
                ctx.fill(rx, ry + rh, rx + rw, ry + rh + 3, rc);
                ctx.fill(rx, ry, rx + 3, ry + rh, rc);
                ctx.fill(rx + rw, ry, rx + rw + 3, ry + rh, rc);
            }
        }
    }

    private void renderThirdPerson(DrawContext ctx, float cx, float cy, float s) {
        float skyShift = easeOut(phaseTime);
        drawBackground(ctx,
            0xFF2C3E1A,
            argbInt(lerp(0.29f, 0.7f, skyShift), lerp(0.42f, 0.5f, skyShift), lerp(0.55f, 0.2f, skyShift)),
            0.5f);

        drawPlayerFigure(ctx, cx - 40*s, cy, s * 0.9f, 0f, 0xFF7B5CF0, 1f,
            0f, 0f, 0.2f, 0.1f, -0.1f, false);

        float enemyX = cx + 40*s;

        if (!splitDone) {
            float stillAlpha = 1f - easeIn(Math.min(1f, phaseTime / 0.3f));
            drawPlayerFigure(ctx, enemyX, cy, s, 0.2f, 0xFF884444, stillAlpha,
                0f, 0f, 0f, 0f, 0f, false);
            float cutProgress = easeOut(Math.max(0, (phaseTime - 0.1f) / 0.2f));
            if (cutProgress > 0) {
                ctx.fill((int)(enemyX - 8*s), (int)(cy - 24*s * cutProgress),
                         (int)(enemyX + 8*s), (int)(cy - 22*s * cutProgress),
                         argb(1f, 1f, 1f, cutProgress * 0.9f));
            }
        } else {
            if (topAlpha > 0.01f) {
                HudStyle.fillRounded(ctx,
                    enemyX - 8*s, cy - 24*s + topY,
                    16*s, 12*s, 2*s,
                    argb(0.53f, 0.27f, 0.27f, topAlpha));
                HudStyle.fillRounded(ctx,
                    enemyX - 4*s, cy - 32*s + topY,
                    8*s, 8*s, 1*s,
                    argb(0.63f, 0.33f, 0.33f, topAlpha));
            }
            if (botAlpha > 0.01f) {
                HudStyle.fillRounded(ctx,
                    enemyX - 8*s, cy - 12*s + botY,
                    16*s, 24*s, 2*s,
                    argb(0.4f, 0.22f, 0.22f, botAlpha));
            }
            for (int i = 0; i < 10; i++) {
                float ang = (float)(i * Math.PI / 5);
                float r   = (float)(Math.sin(time * 3 + i) * 20 * s);
                HudStyle.fillRounded(ctx,
                    enemyX + (float)Math.cos(ang) * r,
                    cy - 12*s + (float)Math.sin(ang) * r,
                    2*s, 2*s, 1*s,
                    argb(0.8f, 0.05f, 0.05f, botAlpha * 0.6f));
            }
        }
    }

    private void renderEpilogue(DrawContext ctx, float cx, float cy, float s) {
        ctx.fill(0, 0, W(), H(), argbInt(0.05f, 0.02f, 0.08f));
        for (int i = 0; i < 60; i++) {
            float t = i / 60f;
            int y = (int)(H() * (0.3f + t * 0.3f));
            float r = lerp(0.1f, 0.9f, 1f-t);
            float g = lerp(0.05f, 0.5f, 1f-t);
            float b = lerp(0.15f, 0.1f, 1f-t);
            ctx.fill(0, y, W(), y+1, argb(r, g, b, 0.9f));
        }

        ctx.fill(0, (int)(H()*0.6f), W(), H(), 0xFF0D0D0D);

        drawPlayerFigure(ctx, cx, cy, s, 0f, 0xFF111111, 1f,
            0f, 0.1f, 0.2f, 0.1f, -0.1f, false);

        if (phaseTime > 0.4f) {
            float ta = easeOut((phaseTime - 0.4f) / 0.6f);
            drawCenteredText(ctx, "§6§lTHE WIN",
                H()/2 + (int)(H()*0.1f), argb(1f, 0.8f, 0.1f, ta));
            if (phaseTime > 0.7f) {
                float ta2 = easeOut((phaseTime - 0.7f) * 3f);
                drawCenteredText(ctx, "§8Victory is yours.",
                    H()/2 + (int)(H()*0.17f), argb(0.4f, 0.35f, 0.3f, ta2));
            }
        }
    }

    private int argbInt(float r, float g, float b) { return argb(r, g, b, 1f); }

    @Override
    public void buildStage() {
        net.minecraft.util.math.Vec3d v = VictoryStage.victimPos;
        net.minecraft.util.math.Vec3d k = VictoryStage.killerPos;
        net.minecraft.util.math.Vec3d near = VictoryStage.ahead(v, k, 1.1);
        float appear = phase == 0 ? easeOut(phaseTime) : 1f;

        float approach = phase == 0 ? 0.25f
                : phase == 1 ? lerp(0.25f, 1f, easeInOut(phaseTime))
                : 1f;
        double ux = lerp((float) k.x, (float) near.x, approach);
        double uz = lerp((float) k.z, (float) near.z, approach);
        double uy = lerp((float) k.y, (float) near.y, approach);

        VictoryStage.Actor me = stageAt(ux, uy, uz,
                VictoryStage.faceTo(v.x - ux, v.z - uz), 0xFF7B5CF0);
        me.alpha = appear;
        me.held = "sword";
        me.rightArmOut = 0.25f;

        boolean foeVisible = phase >= 3;
        if (foeVisible) {
            VictoryStage.Actor foe = stageAt(v.x, v.y, v.z,
                    VictoryStage.faceTo(ux - v.x, uz - v.z), 0xFF884444);
            foe.alpha = appear;
            if (phase >= 3) {
                float t = phase == 3 ? phaseTime : 1f;
                foe.upperShiftX = 0.30f * easeOut(t);
                foe.upperShiftY = -0.55f * easeIn(t);
                foe.upperTilt = 0.9f * easeOut(t);
                foe.alpha = Math.max(0f, 1f - t * 0.75f);
                foe.neckBlood = t > 0.1f;
            }
        }

        switch (phase) {
            case 0 -> {
                me.rightArm = -0.2f;
                me.leftLeg = 0.35f; me.rightLeg = -0.35f;
            }
            case 1 -> {
                float t = easeInOut(phaseTime);
                me.rightArm = lerp(-0.2f, -2.5f, t);
                me.bladePitch = lerp(-0.1f, -0.55f, t);
                me.bodyLean = lerp(0.1f, -0.2f, t);
                me.leftLeg = me.rightLeg = 0f;
            }
            case 2 -> {
                float t = phaseTime;
                me.rightArm = t < 0.4f ? lerp(-2.5f, 1.3f, t / 0.4f) : 1.3f;
                me.bladePitch = lerp(-0.55f, 0.7f, Math.min(1f, t / 0.4f));
                me.bodyLean = 0.3f;
            }
            default -> {
                me.rightArm = 0.5f;
                me.bladePitch = -0.05f;
                me.bodyLean = 0f;
                me.headTilt = 0.15f;
            }
        }
    }

    private void render3DOverlay(DrawContext ctx) {
        drawLetterbox(ctx);
        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        if (phase == 5) drawFade(ctx, easeIn(phaseTime));

        if (phase == 2 && phaseTime > 0.25f && phaseTime < 0.6f) {
            float flash = 1f - Math.abs(phaseTime - 0.42f) / 0.18f;
            ctx.fill(0, 0, W(), H(), argb(1f, 1f, 1f, Math.max(0f, flash) * 0.85f));
        }
        if (phase == 3) {
            float p = easeOut(Math.min(1f, phaseTime * 1.6f));
            int x = (int) (W() * 0.5f);
            ctx.fill(x - 2, 0, x + 2, (int) (H() * p), argb(1f, 1f, 1f, 0.5f * (1f - p)));
        }
        if (phase >= 4 && phaseTime > 0.45f) {
            float ta = easeOut((phaseTime - 0.45f) * 2);
            drawCenteredText(ctx, "§6§lTHE WIN", H() / 2 + (int) (H() * 0.1f),
                    argb(1f, 0.8f, 0.1f, ta));
            if (phaseTime > 0.7f) {
                float ta2 = easeOut((phaseTime - 0.7f) * 3f);
                drawCenteredText(ctx, "§8Victory is yours.",
                        H() / 2 + (int) (H() * 0.17f), argb(0.75f, 0.7f, 0.65f, ta2));
            }
        }
    }
}
