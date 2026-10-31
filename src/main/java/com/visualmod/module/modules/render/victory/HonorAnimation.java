package com.visualmod.module.modules.render.victory;

import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class HonorAnimation extends VictoryAnimation {

    private float headVx = 8f, headVy = -20f, headX, headY;
    private boolean headCut = false;

    private double hx, hy, hz, hvx, hvy, hvz;
    private float headSpin;
    private boolean cut3d;
    private double usX, usZ;

    public HonorAnimation(Identifier skin, int swordStyle) {
        super(skin, swordStyle);
        phaseDurations = new float[]{2f, 3f, 4f, 2.5f, 2f, 1.5f};
    }

    @Override
    public void start() {
        super.start();
        headX = W() * 0.55f;
        headY = H() * 0.32f;
        headCut = false;
        cut3d = false;
        headSpin = 0f;
    }

    @Override
    protected void onTick(float dt) {
        if (phase == 3 && phaseTime > 0.6f && !headCut) {
            headCut = true;
            headX = W() * 0.55f;
            headY = H() * 0.32f;
        }
        if (headCut) {
            headVy += 60f * dt;
            headX  += headVx * dt * W() * 0.01f;
            headY  += headVy * dt;
        }

        if (!scene3D()) return;
        net.minecraft.util.math.Vec3d v = VictoryStage.victimPos;
        if (!cut3d) {
            hx = 0; hy = 1.55; hz = 0;
            if (phase == 3 && phaseTime > 0.42f) {
                cut3d = true;
                double dx = v.x - usX, dz = v.z - usZ;
                double len = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                hvx = dx / len * 2.4 + dz / len * 1.1;
                hvz = dz / len * 2.4 - dx / len * 1.1;
                hvy = 2.6;
            }
        } else {
            hvy -= 12f * dt;
            hx += hvx * dt; hy += hvy * dt; hz += hvz * dt;
            if (hy < 0.24) {
                hy = 0.24;
                hvy *= -0.25f;
                hvx *= 0.4f; hvz *= 0.4f;
            }
            headSpin += dt * (hy > 0.25f ? 7f : 0.6f);
        }
    }

    @Override
    public void buildStage() {
        net.minecraft.util.math.Vec3d v = VictoryStage.victimPos;
        net.minecraft.util.math.Vec3d k = VictoryStage.killerPos;
        double dist = Math.min(3.0, Math.max(1.35, VictoryStage.gap * 0.8));
        net.minecraft.util.math.Vec3d us = VictoryStage.ahead(v, k, dist);
        usX = us.x; usZ = us.z;
        float victimYaw = VictoryStage.faceTo(us.x - v.x, us.z - v.z);
        float appear = phase == 0 ? easeOut(phaseTime) : 1f;

        VictoryStage.Actor foe = stageAt(v.x, v.y, v.z, victimYaw, 0xFF9B5555);
        foe.alpha = appear;
        foe.hipsDrop = 0.34f;
        foe.leftLeg = foe.rightLeg = -1.45f;
        foe.bodyLean = 0.12f;
        foe.headTilt = 0.45f;

        VictoryStage.Actor me = stageAt(us.x, us.y, us.z, VictoryStage.faceToVictim, 0xFF7B5CF0);
        me.alpha = appear;
        me.held = "sword";
        me.rightArmOut = 0.2f;

        switch (phase) {
            case 0 -> {
                me.rightArm = 0.05f;
                me.bladePitch = -0.15f;
            }
            case 1 -> {
                float t = phaseTime;
                me.leftArm = lerp(0f, 1.0f, clamp01(t / 0.45f));
                me.rightArm = -0.2f;
                if (t < 0.5f) {
                    float p = t / 0.5f;
                    VictoryStage.Prop knife = VictoryStage.prop();
                    knife.kind = "knife";
                    knife.x = lerp((float) us.x, (float) v.x, p);
                    knife.z = lerp((float) us.z, (float) v.z, p);
                    knife.y = lerp((float) (us.y + 1.25), (float) (v.y + 1.05), p) + Math.sin(p * Math.PI) * 0.6;
                    knife.roll = p * 9f;
                    knife.alpha = appear;
                } else {
                    float p = easeInOut((t - 0.5f) / 0.5f);
                    foe.held = "knife";
                    foe.rightArm = lerp(-0.3f, 2.1f, p);
                    foe.rightArmOut = lerp(0.12f, 0.55f, p);
                    foe.bodyLean = lerp(0.12f, 0.4f, p);
                    foe.wound = p > 0.75f;
                }
            }
            case 2 -> {
                float t = easeInOut(phaseTime);
                foe.held = "knife";
                foe.rightArm = 2.1f;
                foe.rightArmOut = 0.55f;
                foe.wound = true;
                me.leftArm = 0.9f;
                me.rightArm = lerp(-0.2f, -2.65f, t);
                me.bladePitch = lerp(-0.15f, -0.45f, t);
                me.bodyLean = lerp(0f, -0.22f, t);
            }
            case 3 -> {
                float t = phaseTime;
                float swing = t < 0.25f ? -2.65f
                        : t < 0.45f ? lerp(-2.65f, 1.0f, (t - 0.25f) / 0.2f)
                        : 1.0f - Math.min(1f, (t - 0.45f) * 0.6f);
                me.rightArm = swing;
                me.rightArmOut = 0.25f;
                me.bodyLean = lerp(-0.22f, 0.3f, clamp01((t - 0.2f) / 0.25f));
                me.bladePitch = lerp(-0.45f, 0.5f, clamp01((t - 0.2f) / 0.25f));
                foe.held = "knife";
                foe.rightArm = 2.1f;
                foe.wound = true;
                if (cut3d) {
                    foe.hasHead = false;
                    foe.neckBlood = true;
                    VictoryStage.Actor head = stageAt(v.x + hx, v.y + hy, v.z + hz,
                            VictoryStage.faceTo(hx, hz), 0xFFA07070);
                    head.headOnly = true;
                    head.bodyLean = headSpin;
                    head.alpha = 1f;
                    VictoryStage.Arc slash = VictoryStage.arc();
                    slash.x = v.x; slash.y = v.y + 1.5; slash.z = v.z;
                    slash.radius = 1.25f;
                    slash.halfHeight = 0.35f;
                    slash.from = victimYaw - 1.1f;
                    slash.to = victimYaw + lerp(-0.2f, 1.1f, clamp01((t - 0.3f) / 0.4f));
                    slash.alpha = t < 0.3f ? 0f : Math.max(0f, 0.85f - (t - 0.42f) * 1.6f);
                }
            }
            default -> {
                float fall = easeIn(Math.min(1f, phaseTime * 2.2f));
                foe.hasHead = false;
                foe.neckBlood = true;
                foe.bodyLean = lerp(0.4f, 1.55f, fall);
                foe.hipsDrop = lerp(0.34f, 0.08f, fall);
                foe.leftLeg = foe.rightLeg = lerp(-1.45f, -0.2f, fall);
                foe.held = null;
                stageAt(v.x + hx, v.y + hy, v.z + hz, VictoryStage.faceTo(hx, hz), 0xFFA07070)
                        .headOnly = true;
                me.rightArm = 0.35f;
                me.rightArmOut = 0.15f;
                me.bladePitch = -0.05f;
                me.headTilt = 0.1f;
                me.yaw = VictoryStage.faceToVictim + lerp(0f, 1.1f, clamp01(phaseTime * 1.5f));
            }
        }
    }

    @Override
    public void render(DrawContext ctx) {
        if (scene3D()) { render3DOverlay(ctx); return; }

        int w = W(), h = H();
        float cx = w * 0.5f, cy = h * 0.6f;
        float scale = h / 120f;

        drawBackground(ctx,
            0xFF1A1008,
            0xFF080510,
            0.55f);

        for (int i = 0; i < 30; i++) {
            float t = i / 30f;
            ctx.fill(0, (int)(h*0.53f) + i, w, (int)(h*0.53f) + i + 1,
                argb(0.05f+t*0.05f, 0.03f, 0.08f+t*0.1f, 0.15f - t*0.15f));
        }

        drawLetterbox(ctx);

        switch (phase) {
            case 0 -> renderPhase0(ctx, cx, cy, scale);
            case 1 -> renderPhase1(ctx, cx, cy, scale);
            case 2 -> renderPhase2(ctx, cx, cy, scale);
            case 3 -> renderPhase3(ctx, cx, cy, scale);
            case 4 -> renderPhase4(ctx, cx, cy, scale);
            case 5 -> { renderPhase4(ctx, cx, cy, scale); drawFade(ctx, phaseTime); }
        }

        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
    }

    private void renderPhase0(DrawContext ctx, float cx, float cy, float s) {
        float t = easeOut(phaseTime);
        drawEnemy(ctx, cx + 60*s, cy, s * 0.85f, t, false, false);
        drawUs(ctx, cx - 60*s, cy, s, -0.3f, 0f, -0.5f, 0f, 0f, true);
    }

    private void renderPhase1(DrawContext ctx, float cx, float cy, float s) {
        float t = easeInOut(phaseTime);
        drawEnemy(ctx, cx + 60*s, cy, s * 0.85f, 1f, t > 0.5f, false);
        float armAng = lerp(0f, 0.8f, t);
        drawUs(ctx, cx - 60*s, cy, s, -0.3f, armAng, -0.3f, 0f, 0f, true);
        if (t < 0.5f) {
            float kx = lerp(cx - 40*s, cx + 20*s, t*2);
            float ky = lerp(cy - 20*s, cy - 15*s, t*2);
            drawKnife(ctx, kx, ky, s, t*2*3.14f);
        }
    }

    private void renderPhase2(DrawContext ctx, float cx, float cy, float s) {
        float t = easeInOut(phaseTime);
        drawEnemy(ctx, cx + 60*s, cy, s * 0.85f, 1f, true, false);
        float armUp = lerp(0f, -2.0f, t);
        drawUs(ctx, cx - 60*s, cy, s, lerp(-0.3f, 0.2f, t), 0f, armUp, 0f, 0f, true);

        if (t > 0.6f) {
            float flash = (t - 0.6f) / 0.4f;
            ctx.fill(0, 0, W(), 4, argb(1f, 0f, 0f, flash * 0.7f));
            ctx.fill(0, H()-4, W(), H(), argb(1f, 0f, 0f, flash * 0.7f));
        }
    }

    private void renderPhase3(DrawContext ctx, float cx, float cy, float s) {
        float t = phaseTime;
        boolean struck = t > 0.4f;

        if (t > 0.35f && t < 0.55f) {
            float flash = 1f - Math.abs(t - 0.45f) / 0.1f;
            ctx.fill(0, 0, W(), H(), argb(1f, 1f, 1f, flash * 0.6f));
        }

        drawEnemyHeadless(ctx, cx + 60*s, cy, s * 0.85f, struck ? 1f : easeOut(t/0.4f));

        if (headCut) {
            float hRot = (time - sumPhaseStart(3) - 0.6f) * 5f;
            drawHead(ctx, headX, headY, s, hRot);
        } else {
            drawHead(ctx, cx + 60*s, cy - s*20f, s, 0f);
        }

        if (t > 0.25f && t < 0.65f) {
            float slash = (t - 0.25f) / 0.4f;
            float sx1 = cx - 20*s, sy1 = cy - 25*s;
            float sx2 = cx + 80*s, sy2 = cy - 10*s;
            drawBleachSlash(ctx,
                (int)(sx1 + (sx2-sx1)*Math.max(0,slash-0.3f)),
                (int)(sy1 + (sy2-sy1)*Math.max(0,slash-0.3f)),
                (int)(sx1 + (sx2-sx1)*Math.min(1,slash+0.3f)),
                (int)(sy1 + (sy2-sy1)*Math.min(1,slash+0.3f)),
                (1f - Math.abs(slash-0.5f)*2) * 0.9f);
        }

        drawUs(ctx, cx - 60*s, cy, s, 0.2f, 0f, struck ? 0.5f : -1.5f, 0f, 0f, true);
    }

    private void renderPhase4(DrawContext ctx, float cx, float cy, float s) {
        float fallY = lerp(0, s*15f, easeIn(Math.min(1f, phaseTime * 3)));
        drawEnemyHeadless(ctx, cx + 60*s, cy + fallY, s * 0.85f, 1f);
        drawHead(ctx, headX, Math.min(H() * 0.58f, headY), s, headCut ? (time*3f) : 0f);
        drawUs(ctx, cx - 60*s, cy, s, -0.1f, 0f, 0.3f, 0.1f, -0.1f, true);
        if (phaseTime > 0.5f) {
            float ta = easeOut((phaseTime - 0.5f) * 2);
            drawCenteredText(ctx, "§6§lHONOR KILLS", H()/2 + (int)(H()*0.1f),
                argb(1f, 0.8f, 0.2f, ta));
        }
    }

    private void render3DOverlay(DrawContext ctx) {
        drawLetterbox(ctx);
        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        if (phase == 5) drawFade(ctx, easeIn(phaseTime));

        if (phase == 2 && phaseTime > 0.6f) {
            float flash = (phaseTime - 0.6f) / 0.4f;
            ctx.fill(0, 0, W(), 4, argb(1f, 0f, 0f, flash * 0.7f));
            ctx.fill(0, H() - 4, W(), H(), argb(1f, 0f, 0f, flash * 0.7f));
        }
        if (phase == 3 && phaseTime > 0.34f && phaseTime < 0.54f) {
            float flash = 1f - Math.abs(phaseTime - 0.44f) / 0.1f;
            ctx.fill(0, 0, W(), H(), argb(1f, 0.95f, 0.9f, flash * 0.5f));
        }
        if (phase >= 4 && phaseTime > 0.5f) {
            float ta = easeOut((phaseTime - 0.5f) * 2);
            drawCenteredText(ctx, "§6§lHONOR KILLS", H() / 2 + (int) (H() * 0.1f),
                    argb(1f, 0.8f, 0.2f, ta));
        }
    }

    private void drawEnemy(DrawContext ctx, float x, float y, float s,
                           float appear, boolean stabbed, boolean headless) {
        float kneelY = y + s * 8f;
        if (!headless) drawPlayerFigure(ctx, x, kneelY, s * appear, 0.4f,
            0xFF9B5555, appear, 0.3f, 0.4f, stabbed ? 1.2f : 0.2f,
            0.8f, 0.4f, false);
    }

    private void drawEnemyHeadless(DrawContext ctx, float x, float y, float s, float alpha) {
        float kneelY = y + s * 8f;
        HudStyle.fillRounded(ctx, x - 4*s, kneelY - 12*s, 8*s, 12*s, 2*s,
            argb(0.61f, 0.33f, 0.33f, alpha));
        HudStyle.fillRounded(ctx, x + 4*s, kneelY - 11*s, 4*s, 8*s, 1*s,
            argb(0.48f, 0.3f, 0.3f, alpha));
        HudStyle.fillRounded(ctx, x - 8*s, kneelY - 11*s, 4*s, 8*s, 1*s,
            argb(0.48f, 0.3f, 0.3f, alpha));
        HudStyle.fillRounded(ctx, x - 4*s, kneelY + 2*s, 4*s, 8*s, 1*s,
            argb(0.4f, 0.27f, 0.27f, alpha));
        HudStyle.fillRounded(ctx, x - 2*s, kneelY - 12*s, 4*s, 3*s, 1*s,
            argb(0.8f, 0.05f, 0.05f, alpha));
    }

    private void drawHead(DrawContext ctx, float x, float y, float s, float rot) {
        float skew = (float)Math.sin(rot) * s * 2f;
        HudStyle.fillRounded(ctx, x - 4*s + skew, y - 8*s, 8*s, 8*s, 1*s,
            0xFFA07070);
        ctx.fill((int)(x - 2*s + skew), (int)(y - 6*s),
                 (int)(x - 1*s + skew), (int)(y - 5*s), 0xFF111111);
        ctx.fill((int)(x + 1*s + skew), (int)(y - 6*s),
                 (int)(x + 2*s + skew), (int)(y - 5*s), 0xFF111111);
    }

    private void drawUs(DrawContext ctx, float x, float y, float s,
                        float rotY, float leftArm, float rightArm,
                        float leftLeg, float rightLeg, boolean sword) {
        drawPlayerFigure(ctx, x, y, s, rotY, 0xFF7B5CF0, 1f,
            0.0f, leftArm, rightArm, leftLeg, rightLeg, sword);
    }

    private void drawKnife(DrawContext ctx, float x, float y, float s, float angle) {
        float sin = (float)Math.sin(angle), cos = (float)Math.cos(angle);
        for (int i = 0; i < 8; i++) {
            ctx.fill((int)(x + sin*i*s), (int)(y + cos*i*s),
                     (int)(x + sin*i*s + (int)Math.max(1, 2*s)),
                     (int)(y + cos*i*s + (int)Math.max(1, 2*s)),
                     argb(0.85f, 0.85f, 0.9f, 0.9f));
        }
    }

    private float sumPhaseStart(int p) {
        float s = 0;
        for (int i = 0; i < p; i++) s += phaseDurations[i];
        return s;
    }
}
