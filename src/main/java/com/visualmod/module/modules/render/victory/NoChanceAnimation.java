package com.visualmod.module.modules.render.victory;

import net.minecraft.client.MinecraftClient;
import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NoChanceAnimation extends VictoryAnimation {

    private final List<float[]> slashLines = new ArrayList<>();
    private final List<float[]> storm3d = new ArrayList<>();
    private final Random rng = new Random(42);

    private float enemyPanicX = 0;
    private boolean swordSheathed = false;

    public NoChanceAnimation(Identifier skin, int swordStyle) {
        super(skin, swordStyle);
        phaseDurations = new float[]{2.5f, 3.5f, 5f, 2.5f, 4f, 2.5f};
    }

    @Override
    public void start() {
        super.start();
        slashLines.clear();
        storm3d.clear();
        swordSheathed = false;
    }

    @Override
    protected void onTick(float dt) {
        enemyPanicX = (float)(Math.sin(time * 8f) * 6f);

        if (phase == 2 && slashLines.size() < 30) {
            float phaseStart = phaseDurations[0]+phaseDurations[1];
            float localTime = time - phaseStart;
            int expected = (int)(localTime / 0.13f);
            while (slashLines.size() < expected && slashLines.size() < 30) {
                int h = H(), w = W();
                boolean horiz = rng.nextBoolean();
                float[] line;
                if (horiz) {
                    int y = rng.nextInt(h);
                    line = new float[]{0, y, w, y + rng.nextInt(10)-5,
                                       localTime, 1f};
                } else {
                    int x = rng.nextInt(w);
                    line = new float[]{x, 0, x + rng.nextInt(20)-10, h,
                                       localTime, 1f};
                }
                slashLines.add(line);
            }
        }

        if (phase == 2 && scene3D() && storm3d.size() < 26) {
            float localTime = time - (phaseDurations[0] + phaseDurations[1]);
            int expected = (int) (localTime / 0.18f);
            while (storm3d.size() < expected && storm3d.size() < 26) {
                storm3d.add(new float[]{
                        (float) (rng.nextDouble() * Math.PI * 2),
                        0.35f + (float) rng.nextDouble() * 1.5f,
                        0.85f + (float) rng.nextDouble() * 0.6f,
                        localTime });
            }
        }

        if (phase == 3 && phaseTime > 0.8f) swordSheathed = true;
    }

    @Override
    public void render(DrawContext ctx) {
        if (scene3D()) { render3DOverlay(ctx); return; }
        int w = W(), h = H();
        float cx = w * 0.5f, cy = h * 0.6f;
        float scale = h / 120f;

        switch (phase) {
            case 0 -> renderDisappear(ctx, cx, cy, scale);
            case 1 -> renderAppearBehind(ctx, cx, cy, scale);
            case 2 -> renderBleach(ctx, cx, cy, scale);
            case 3 -> renderFall(ctx, cx, cy, scale);
            case 4 -> renderEnd(ctx, cx, cy, scale);
            case 5 -> { renderEnd(ctx, cx, cy, scale); drawFade(ctx, phaseTime); }
        }

        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        drawLetterbox(ctx);
    }

    private void renderDisappear(DrawContext ctx, float cx, float cy, float s) {
        drawBackground(ctx, 0xFF1A1A1A, 0xFF0A0A0A, 0.55f);
        drawEnemy(ctx, cx + 60*s, cy, s, 1f, 0xFF884444);
        float ourAlpha = 1f - easeIn(phaseTime);
        if (ourAlpha > 0.01f)
            drawPlayerFigure(ctx, cx - 60*s, cy, s, -0.3f, 0xFF7B5CF0, ourAlpha,
                0f, 0f, 0.3f, 0.1f, -0.1f, true);
        if (phaseTime > 0.5f) {
            float t = (phaseTime - 0.5f) * 2;
            for (int i = 0; i < 20; i++) {
                float ang = (float)(i * Math.PI * 2 / 20 + time * 3);
                float r   = t * 60 * s;
                float px  = cx - 60*s + (float)Math.cos(ang) * r;
                float py  = cy - 20*s + (float)Math.sin(ang) * r;
                HudStyle.fillRounded(ctx, px, py, 2*s, 2*s, 1*s,
                    argb(0.6f, 0.4f, 1f, (1f-t)*0.8f));
            }
        }
    }

    private void renderAppearBehind(DrawContext ctx, float cx, float cy, float s) {
        float t = phaseTime;
        float bgWhiten = easeIn(Math.max(0, t - 0.5f) / 0.5f);
        drawBackground(ctx,
            blendColor(0xFF1A1A1A, 0xFFFFFFFF, bgWhiten),
            blendColor(0xFF0A0A0A, 0xFFFFFFFF, bgWhiten),
            0.55f);

        int enemyColor = blendColor(0xFF884444, 0xFF111111, bgWhiten);
        drawEnemy(ctx, cx + 60*s + enemyPanicX, cy, s, 1f, enemyColor);

        if (t > 0.3f) {
            float qAlpha = Math.min(1f, (t - 0.3f) * 3f);
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            ctx.drawTextWithShadow(tr, "§c?!", (int)(cx + 50*s + enemyPanicX), (int)(cy - 35*s),
                argb(1f, 0.3f, 0.3f, qAlpha));
        }

        if (t > 0.6f) {
            float usAlpha = easeOut((t - 0.6f) / 0.4f);
            int usColor = blendColor(0xFF7B5CF0, 0xFF000000, bgWhiten);
            drawPlayerFigure(ctx, cx + 100*s, cy, s, -0.5f, usColor, usAlpha,
                0f, 0f, -0.5f, 0.1f, -0.1f, true);
        }
    }

    private void renderBleach(DrawContext ctx, float cx, float cy, float s) {
        ctx.fill(0, 0, W(), H(), 0xFFFFFFFF);

        float fallProgress = easeIn(Math.max(0, phaseTime - 0.7f) / 0.3f);
        float enemyY = cy + fallProgress * s * 30f;

        for (int i = 0; i < 5; i++) {
            float pieceX = cx + 60*s + (phaseTime > 0.7f ? (rng.nextFloat()-0.5f)*i*40 : 0);
            float pieceY = enemyY + (phaseTime > 0.7f ? rng.nextFloat()*i*20 : 0);
            HudStyle.fillRounded(ctx, pieceX - 4*s + i*s, pieceY - (12-i*2)*s,
                8*s - i*s, (12-i*2)*s, 1*s, 0xFF111111);
        }

        drawPlayerFigure(ctx, cx + 100*s, cy, s, -0.4f, 0xFF000000, 1f,
            0f, 0f, -1.5f, 0.1f, -0.1f, true);

        float phaseStart = phaseDurations[0]+phaseDurations[1];
        float localTime = time - phaseStart;
        for (float[] line : slashLines) {
            float lineAge = localTime - line[4];
            float lineAlpha = Math.max(0, 1f - lineAge * 1.5f);
            if (lineAlpha > 0.02f) {
                drawBleachSlash(ctx, (int)line[0], (int)line[1],
                    (int)line[2], (int)line[3], lineAlpha);
                int midX = (int)((line[0]+line[2])/2);
                int midY = (int)((line[1]+line[3])/2);
                HudStyle.fillRounded(ctx, midX-3, midY-3, 6, 6, 3,
                    argb(1f, 1f, 0.9f, lineAlpha * 0.8f));
            }
        }
    }

    private void renderFall(DrawContext ctx, float cx, float cy, float s) {
        float t = phaseTime;
        ctx.fill(0, 0, W(), H(), blendColor(0xFFFFFFFF, 0xFF0A0A0A, easeOut(t)));
        drawBackground(ctx, 0xFF1A1A1A, 0xFF0A0A0A, 0.55f);

        float fallY = lerp(0, s * 40f, easeIn(t));
        float fallRot = lerp(0f, 1.2f, easeIn(t));
        drawEnemy(ctx, cx + 60*s, cy + fallY, s * (1f - t*0.3f), 1f - t*0.3f, 0xFF884444);

        float sheatheProgress = easeInOut(Math.min(1f, t * 1.5f));
        drawPlayerFigure(ctx, cx + 100*s, cy, s, -0.3f, 0xFF7B5CF0, 1f,
            0f, 0f, lerp(-1.5f, 0.3f, sheatheProgress),
            0.1f, -0.1f, !swordSheathed);
    }

    private void renderEnd(DrawContext ctx, float cx, float cy, float s) {
        drawBackground(ctx, 0xFF1A1A1A, 0xFF080810, 0.55f);
        drawEnemy(ctx, cx + 60*s, cy + s * 40f, s * 0.7f, 0.4f, 0xFF553333);
        drawPlayerFigure(ctx, cx + 100*s, cy, s, -0.3f, 0xFF7B5CF0, 1f,
            0f, 0f, 0.3f, 0.1f, -0.1f, false);

        if (phaseTime > 0.3f) {
            float ta = easeOut((phaseTime - 0.3f) * 1.4f);
            drawCenteredText(ctx, "§f§lNO CHANCE", H()/2 + (int)(H()*0.12f),
                argb(1f, 1f, 1f, ta));
            if (phaseTime > 0.6f) {
                float ta2 = easeOut((phaseTime - 0.6f) * 2.5f);
                drawCenteredText(ctx, "§8— you never stood a chance —",
                    H()/2 + (int)(H()*0.18f), argb(0.5f, 0.5f, 0.5f, ta2));
            }
        }
    }

    private void drawEnemy(DrawContext ctx, float x, float y, float s, float alpha, int color) {
        drawPlayerFigure(ctx, x, y, s, 0.3f, color, alpha,
            0.2f, 0.6f, 0.4f, 0.3f, 0.1f, false);
    }

    private int blendColor(int c1, int c2, float t) {
        t = clamp01(t);
        float r = lerp(((c1>>16)&0xFF)/255f, ((c2>>16)&0xFF)/255f, t);
        float g = lerp(((c1>> 8)&0xFF)/255f, ((c2>> 8)&0xFF)/255f, t);
        float b = lerp(( c1     &0xFF)/255f, ( c2     &0xFF)/255f, t);
        return argb(r, g, b, 1f);
    }

    @Override
    public void buildStage() {
        net.minecraft.util.math.Vec3d v = VictoryStage.victimPos;
        net.minecraft.util.math.Vec3d k = VictoryStage.killerPos;
        float panic = (float) Math.sin(time * 7f) * 0.5f;
        float sway  = (float) Math.sin(time * 2.4f) * 0.7f;

        VictoryStage.Actor foe = stageAt(v.x, v.y, v.z,
                VictoryStage.faceTo(k.x - v.x, k.z - v.z) + sway, 0xFF000000);
        foe.silhouette = true;
        foe.leftArm = foe.rightArm = 1.1f + panic * 0.3f;
        foe.leftArmOut = foe.rightArmOut = 0.7f;
        foe.bodyLean = 0.1f + panic * 0.08f;
        foe.hipsDrop = 0.1f;
        foe.leftLeg = panic * 0.3f; foe.rightLeg = -panic * 0.3f;

        boolean visible = phase >= 1;
        net.minecraft.util.math.Vec3d behind = VictoryStage.ahead(v, k, -1.15);
        if (visible) {
            float appear = phase == 1 ? easeOut(phaseTime) : 1f;
            VictoryStage.Actor me = stageAt(behind.x, behind.y, behind.z,
                    VictoryStage.faceTo(v.x - behind.x, v.z - behind.z), 0xFF000000);
            me.silhouette = true;
            me.alpha = appear;
            me.held = "sword";
            if (phase == 2) {
                float swing = (float) Math.sin(time * 9f);
                me.rightArm = 0.4f + swing * 1.5f;
                me.bladePitch = swing * 0.9f;
                me.bodyLean = 0.25f;
                for (float[] s : storm3d) {
                    float age = (float) (time - (phaseDurations[0] + phaseDurations[1])) - s[3];
                    if (age < 0f || age > 0.55f) continue;
                    VictoryStage.Arc a = VictoryStage.arc();
                    a.x = v.x; a.z = v.z; a.y = v.y + s[1];
                    a.radius = s[2];
                    a.halfHeight = 0.1f + age * 0.4f;
                    a.from = s[0] - 0.25f; a.to = s[0] + 0.25f;
                    a.alpha = (1f - age / 0.55f) * 0.95f * appear;
                }
            } else if (phase >= 3) {
                float fall = easeIn(clamp01(phaseTime * (phase == 3 ? 1.4f : 0.2f)));
                foe.bodyLean = lerp(0.1f, 1.5f, fall);
                foe.hipsDrop = lerp(0.1f, 0.02f, fall);
                foe.leftArm = foe.rightArm = lerp(1.1f, 0.2f, fall);
                foe.alpha = 1f - 0.25f * fall;
                me.rightArm = 0.35f;
                me.bladePitch = -0.1f;
                if (phase == 3 && phaseTime > 0.55f) {
                    me.held = null;
                    VictoryStage.Prop sc = VictoryStage.prop();
                    sc.kind = "sword";
                    sc.x = behind.x; sc.z = behind.z;
                    sc.y = behind.y + 1.05;
                    sc.yaw = VictoryStage.faceTo(v.x - behind.x, v.z - behind.z);
                    sc.roll = 2.5f;
                    sc.alpha = 1f;
                } else if (phase >= 4) {
                    me.held = null;
                    VictoryStage.Prop sc = VictoryStage.prop();
                    sc.kind = "sword";
                    sc.x = behind.x; sc.z = behind.z; sc.y = behind.y + 1.05;
                    sc.yaw = VictoryStage.faceTo(v.x - behind.x, v.z - behind.z);
                    sc.roll = 2.5f;
                }
            }
        }
    }

    private void render3DOverlay(DrawContext ctx) {
        drawLetterbox(ctx);
        if (phase == 0) drawFade(ctx, 1f - easeOut(phaseTime));
        if (phase == 5) drawFade(ctx, easeIn(phaseTime));

        if (phase >= 1 && phase <= 3) {
            float w = phase == 1 ? easeOut(phaseTime) * 0.5f
                    : phase == 2 ? 0.5f + 0.12f * (float) Math.sin(time * 6f)
                    : lerp(0.5f, 0.15f, clamp01(phaseTime));
            drawWhite(ctx, w);
        }
        if (phase == 2) {
            for (float[] l : slashLines) {
                float age = time - (l[4] + phaseDurations[0] + phaseDurations[1]);
                if (age < 0f || age > 0.35f) continue;
                ctx.fill((int) l[0], (int) l[1], (int) l[2], (int) l[3] + 2,
                        argb(1f, 1f, 1f, (1f - age / 0.35f) * 0.5f));
            }
        }
        if (phase >= 4 && phaseTime > 0.4f) {
            float ta = easeOut((phaseTime - 0.4f) * 2);
            drawCenteredText(ctx, "§f§lNO CHANCE", H() / 2 + (int) (H() * 0.1f),
                    argb(1f, 1f, 1f, ta));
        }
    }
}
