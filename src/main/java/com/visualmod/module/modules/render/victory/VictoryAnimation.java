package com.visualmod.module.modules.render.victory;

import com.mojang.blaze3d.systems.RenderSystem;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public abstract class VictoryAnimation {

    protected final Identifier skin;
    protected final int        swordStyle;

    protected float  time       = 0f;
    protected int    phase      = 0;
    protected float  phaseTime  = 0f;
    protected boolean finished  = false;
    private   int    skipClicks = 0;
    protected boolean skipping  = false;
    protected int    skipNeeded = 3;

    protected float[] phaseDurations;

    protected VictoryAnimation(Identifier skin, int swordStyle) {
        this.skin        = skin;
        this.swordStyle  = swordStyle;
    }

    public void start() { time = 0; phase = 0; phaseTime = 0; finished = false; }
    public boolean isFinished() { return finished; }

    public void applyScript(VictoryScripts.Script script) {
        if (script == null) return;
        skipNeeded = Math.max(1, script.skipClicks);
        if (phaseDurations == null || script.seconds.length == 0) return;
        int n = Math.min(phaseDurations.length, script.seconds.length);
        float[] merged = new float[phaseDurations.length];
        System.arraycopy(phaseDurations, 0, merged, 0, phaseDurations.length);
        System.arraycopy(script.seconds, 0, merged, 0, n);
        phaseDurations = merged;
    }

    public void skipClick() {
        skipClicks++;
        if (skipClicks >= skipNeeded) skipping = true;
    }

    public void tick(float dt) {
        if (finished) return;
        if (skipping) { dt *= 8f; }

        time += dt;

        if (phaseDurations != null && phase < phaseDurations.length) {
            float phaseStart = sumDuration(phase - 1);
            if (time - phaseStart >= phaseDurations[phase]) {
                phase++;
                if (phase >= phaseDurations.length) { finished = true; return; }
                phaseStart = sumDuration(phase - 1);
            }
            phaseTime = clamp01((time - phaseStart) / Math.max(0.05f, phaseDurations[phase]));
        }

        onTick(dt);
    }

    protected void onTick(float dt) {}

    private float sumDuration(int upToPhase) {
        if (upToPhase < 0) return 0f;
        float s = 0;
        for (int i = 0; i <= Math.min(upToPhase, phaseDurations.length - 1); i++) s += phaseDurations[i];
        return s;
    }

    public abstract void render(DrawContext ctx);

    public void buildStage() {}

    protected boolean scene3D() { return VictoryAnimationEngine.sceneMode == 1; }

    protected VictoryStage.Actor stageAt(double x, double y, double z, float yaw, int color) {
        VictoryStage.Actor a = VictoryStage.add();
        a.x = x; a.y = y; a.z = z;
        a.yaw = yaw;
        a.color = color;
        return a;
    }

    protected static int W() { return MinecraftClient.getInstance().getWindow().getScaledWidth();  }
    protected static int H() { return MinecraftClient.getInstance().getWindow().getScaledHeight(); }

    protected static float easeOut(float t) { t = clamp01(t); return 1f - (1f-t)*(1f-t)*(1f-t); }
    protected static float easeIn(float t)  { t = clamp01(t); return t*t*t; }
    protected static float easeInOut(float t) { t=clamp01(t); return t<0.5f ? 2*t*t : 1-(-2*t+2)*(-2*t+2)/2f; }
    protected static float clamp01(float t) { return Math.max(0f, Math.min(1f, t)); }
    protected static float lerp(float a, float b, float t) { return a + (b-a)*clamp01(t); }

    protected void drawFade(DrawContext ctx, float alpha) {
        ctx.fill(0, 0, W(), H(), argb(0, 0, 0, alpha));
    }

    protected void drawWhite(DrawContext ctx, float alpha) {
        ctx.fill(0, 0, W(), H(), argb(1, 1, 1, alpha));
    }

    protected void drawLetterbox(DrawContext ctx) {
        int barH = H() / 7;
        ctx.fill(0, 0, W(), barH, 0xFF000000);
        ctx.fill(0, H()-barH, W(), H(), 0xFF000000);
    }

    protected void drawCenteredText(DrawContext ctx, String text, int y, int color) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int w  = tr.getWidth(text);
        ctx.drawTextWithShadow(tr, text, (W()-w)/2, y, color);
    }

    protected void drawSlashLine(DrawContext ctx, int x, float alpha, boolean horizontal) {
        int col = argb(1f, 1f, 1f, alpha);
        if (horizontal) {
            ctx.fill(0, x, W(), x+2, col);
        } else {
            ctx.fill(x, 0, x+2, H(), col);
        }
    }

    protected int argb(float r, float g, float b, float a) {
        return ((int)(clamp01(a)*255) << 24)
             | ((int)(clamp01(r)*255) << 16)
             | ((int)(clamp01(g)*255) <<  8)
             |  (int)(clamp01(b)*255);
    }

    protected void drawPlayerFigure(DrawContext ctx,
                                    float cx, float cy, float scale,
                                    float rotY,
                                    int tint, float alpha,
                                    float headAngle,
                                    float leftArmAngle,
                                    float rightArmAngle,
                                    float leftLegAngle,
                                    float rightLegAngle,
                                    boolean showSword) {

        float s  = scale;
        float px = (float)(Math.sin(rotY) * s * 4);
        float a  = clamp01(alpha);
        int   c  = argb(
            ((tint>>16)&0xFF)/255f,
            ((tint>> 8)&0xFF)/255f,
            ( tint     &0xFF)/255f, a);

        HudStyle.fillRounded(ctx,
            cx - 4*s + px*0.3f, cy - 12*s, 8*s, 12*s, 2*s, blend(0xFF8B8B8B, tint, a));

        float hx = cx + (float)(Math.sin(headAngle)*2*s);
        float hy = cy - 12*s - 8*s + (float)((1-Math.cos(headAngle))*2*s);
        HudStyle.fillRounded(ctx, hx-4*s, hy, 8*s, 8*s, 1*s, blend(0xFFA0A0A0, tint, a));

        float lax = cx + 4*s + px*0.1f;
        float lay = cy - 11*s + (float)(Math.sin(leftArmAngle)*2*s);
        HudStyle.fillRounded(ctx, lax, lay, 4*s, 12*s, 1*s, blend(0xFF7A7A7A, tint, a));

        float rax = cx - 8*s + px*0.1f;
        float ray = cy - 11*s + (float)(Math.sin(rightArmAngle)*2*s);
        HudStyle.fillRounded(ctx, rax, ray, 4*s, 12*s, 1*s, blend(0xFF7A7A7A, tint, a));

        float llx = cx + (float)(Math.sin(leftLegAngle)*2*s);
        float lly = cy;
        HudStyle.fillRounded(ctx, llx, lly, 4*s, 12*s, 1*s, blend(0xFF666666, tint, a));

        float rlx = cx - 4*s + (float)(Math.sin(rightLegAngle)*2*s);
        float rly = cy;
        HudStyle.fillRounded(ctx, rlx, rly, 4*s, 12*s, 1*s, blend(0xFF666666, tint, a));

        if (showSword) drawSword(ctx, rax + 2*s, ray - 2*s, s, rotY, rightArmAngle, tint, a);
    }

    protected void drawSword(DrawContext ctx, float x, float y, float scale,
                             float rotY, float angle, int tint, float alpha) {
        float s   = scale;
        float sin = (float) Math.sin(angle - 0.3f);
        float cos = (float) Math.cos(angle - 0.3f);
        ctx.fill((int)(x - sin*s), (int)(y + cos*s),
                 (int)(x - sin*s + 2*s), (int)(y + cos*s + 6*s),
                 argb(0.6f, 0.4f, 0.2f, alpha));
        ctx.fill((int)(x - cos*3*s - sin*s), (int)(y - sin*3*s + cos*s),
                 (int)(x + cos*3*s - sin*s), (int)(y + sin*3*s + cos*s),
                 argb(0.8f, 0.7f, 0.3f, alpha));
        for (int i = 0; i < 12; i++) {
            float t = i / 12f;
            ctx.fill(
                (int)(x - sin*(6+i)*s),
                (int)(y + cos*(6+i)*s),
                (int)(x - sin*(6+i)*s + (int)Math.max(1, 2*s*(1-t*0.4f))),
                (int)(y + cos*(6+i)*s + (int)Math.max(1, 2*s*(1-t*0.4f))),
                argb(0.85f + t*0.1f, 0.9f + t*0.05f, 0.95f, alpha * (1-t*0.1f))
            );
        }
    }

    protected int blend(int base, int tint, float alpha) {
        float r = ((base>>16)&0xFF)/255f * ((tint>>16)&0xFF)/255f;
        float g = ((base>> 8)&0xFF)/255f * ((tint>> 8)&0xFF)/255f;
        float b = ( base     &0xFF)/255f * ( tint     &0xFF)/255f;
        return argb(r, g, b, alpha);
    }

    protected void drawBackground(DrawContext ctx, int groundColor, int skyColor, float horizonY) {
        int h = (int)(H() * horizonY);
        ctx.fill(0, 0, W(), h, skyColor);
        ctx.fill(0, h, W(), H(), groundColor);
    }

    protected void drawBleachSlash(DrawContext ctx, int x1, int y1, int x2, int y2, float alpha) {
        for (int i = -2; i <= 2; i++) {
            float a = alpha * (1f - Math.abs(i)/3f);
            int col = argb(1f, 1f, 1f, a);
            int dx = x2-x1, dy = y2-y1;
            int steps = Math.max(Math.abs(dx), Math.abs(dy));
            for (int s = 0; s < steps; s++) {
                int px = x1 + dx*s/steps + (Math.abs(dy)>Math.abs(dx)?i:0);
                int py = y1 + dy*s/steps + (Math.abs(dx)>Math.abs(dy)?i:0);
                ctx.fill(px, py, px+2, py+2, col);
            }
        }
    }
}
