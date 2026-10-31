package com.visualmod.record;

import com.visualmod.module.modules.util.HighlightReel;
import com.visualmod.util.HudStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class ReplayScreen extends Screen {

    private static final int BAR_H = 44;
    private static final int[] SPEEDS = {1, 2, 4, 8};

    private static volatile ReplayScreen instance;

    private final Recording rec;
    private final double[] pos = new double[3];
    private final float[] rot = new float[2];

    private float time;
    private boolean playing = true;
    private int speedIndex = 0;
    private int mode = 0;
    private boolean freeReady = false;
    private boolean confirmClose;
    private long lastFrameNs;

    private double fx, fy, fz;
    private float fyaw, fpitch;
    private boolean trackDrag;

    private final List<HighlightReel.Cut> cuts = new ArrayList<>();
    private final double[] blendPos = new double[3];
    private final float[] blendRot = new float[2];
    private int cutIndex;
    private boolean reel;
    private boolean blending;
    private float blendElapsed;
    private String cutLabel = "";

    private record Zone(int x1, int y1, int x2, int y2, Runnable action) {}
    private final List<Zone> zones = new ArrayList<>();

    public ReplayScreen(Recording rec) {
        super(Text.literal("Replay"));
        this.rec = rec;
    }

    public static boolean isDrivingCamera() { return instance != null && ReplayCamera.active; }

    @Override
    protected void init() {
        instance = this;
        lastFrameNs = 0L;
        time = 0f;
        playing = true;
        cuts.clear();
        cuts.addAll(HighlightReel.cuts(rec));
        cutIndex = 0;
        blending = false;
        blendElapsed = 0f;
        cutLabel = cuts.isEmpty() ? "" : cuts.get(0).label();
        reel = !cuts.isEmpty() && HighlightReel.autoReel;
        if (reel) time = cuts.get(0).a();
        applyPath(time);
    }

    @Override
    public void removed() {
        super.removed();
        if (instance == this) instance = null;
        ReplayCamera.release();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0f : Math.min(0.1f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;

        if (playing) {
            if (blending) {
                blendElapsed += dt;
                if (blendElapsed >= HighlightReel.blendSeconds()) blending = false;
            } else {
                time += dt * SPEEDS[speedIndex];
                if (reel && !cuts.isEmpty()) {
                    float end = cuts.get(Math.min(cutIndex, cuts.size() - 1)).b();
                    if (time >= end) { time = end; nextCut(); }
                } else if (time >= rec.duration()) {
                    time = rec.duration();
                    playing = false;
                }
            }
        }

        zones.clear();
        driveCamera(dt);
        renderBar(ctx, mx, my);
        ctx.drawCenteredTextWithShadow(textRenderer, rec.title + "  ·  " + rec.world,
                width / 2, BAR_H + 6, 0xB0FFFFFF);
        if (reel && HighlightReel.banner && !cuts.isEmpty()) {
            String who = cutLabel.isEmpty() ? "" : "  ·  " + cutLabel;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Кусок " + (cutIndex + 1) + "/" + cuts.size() + who + " §7(R — нарезка)",
                    width / 2, BAR_H + 18, 0xFFFFF0C8);
        }
        if (confirmClose) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§eESC ещё раз — выйти из просмотра",
                    width / 2, BAR_H + 30, 0xFFFFFFFF);
        }
        if (mode == 1) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7WASD — лететь · E/Q — вверх/вниз · стрелки — взгляд · Shift быстрее, Ctrl медленнее",
                    width / 2, height - 12, 0xB0FFFFFF);
        } else if (!cuts.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7R — нарезка · N/B — кусок · ⏮⏭ прыгают по кускам",
                    width / 2, height - 12, 0x80FFFFFF);
        }
        super.render(ctx, mx, my, delta);
    }

    private void applyPath(float t) {
        rec.sample(t, pos, rot);
    }

    private void driveCamera(float dt) {
        if (rec.frames.isEmpty()) { ReplayCamera.release(); return; }

        if (mode == 0) {
            applyPath(time);
            if (blending) {
                float u = Math.min(1f, blendElapsed / HighlightReel.blendSeconds());
                float k = u * u * (3f - 2f * u);
                pos[0] += (blendPos[0] - pos[0]) * (1f - k);
                pos[1] += (blendPos[1] - pos[1]) * (1f - k);
                pos[2] += (blendPos[2] - pos[2]) * (1f - k);
                rot[0] = Recording.lerpAngle(blendRot[0], rot[0], k);
                rot[1] += (blendRot[1] - rot[1]) * (1f - k);
            }
            ReplayCamera.set(pos[0], pos[1], pos[2], rot[0], rot[1]);
            freeReady = false;
            return;
        }

        if (!freeReady) {
            applyPath(time);
            fx = pos[0]; fy = pos[1]; fz = pos[2];
            fyaw = rot[0]; fpitch = rot[1];
            freeReady = true;
        }

        fly(dt);
        ReplayCamera.set(fx, fy, fz, fyaw, fpitch);
    }

    private void toggleReel() {
        if (cuts.isEmpty()) return;
        reel = !reel;
        blending = false;
        if (!reel) return;
        syncCut();
        jumpToCut(cutIndex);
    }

    private void nextCut() {
        if (cuts.isEmpty()) return;
        int n = cutIndex + 1;
        if (n >= cuts.size()) {
            if (!HighlightReel.loop) {
                reel = false;
                blending = false;
                playing = false;
                return;
            }
            n = 0;
        }
        jumpToCut(n);
    }

    private void jumpToCut(int index) {
        if (cuts.isEmpty()) return;
        int i = Math.max(0, Math.min(cuts.size() - 1, index));
        if (reel) {
            blendPos[0] = pos[0]; blendPos[1] = pos[1]; blendPos[2] = pos[2];
            blendRot[0] = rot[0]; blendRot[1] = rot[1];
            blending = true;
            blendElapsed = 0f;
            mode = 0;
            freeReady = false;
        }
        cutIndex = i;
        cutLabel = cuts.get(i).label();
        time = cuts.get(i).a();
        applyPath(time);
        playing = true;
    }

    /** Кусок, которому принадлежит текущий момент — чтобы скачок по таймлайну не терял счётчик. */
    private void syncCut() {
        if (cuts.isEmpty()) return;
        for (int i = 0; i < cuts.size(); i++) {
            HighlightReel.Cut c = cuts.get(i);
            if (time >= c.a() && time <= c.b()) {
                cutIndex = i;
                cutLabel = c.label();
                return;
            }
        }
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < cuts.size(); i++) {
            float d = Math.abs(time - cuts.get(i).a());
            if (d < bd) { bd = d; best = i; }
        }
        cutIndex = best;
        cutLabel = cuts.get(best).label();
    }

    private void fly(float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long win = mc.getWindow().getHandle();
        if (dt <= 0f) return;

        if (key(win, GLFW.GLFW_KEY_LEFT))  fyaw -= 90f * dt;
        if (key(win, GLFW.GLFW_KEY_RIGHT)) fyaw += 90f * dt;
        if (key(win, GLFW.GLFW_KEY_UP))    fpitch -= 90f * dt;
        if (key(win, GLFW.GLFW_KEY_DOWN))  fpitch += 90f * dt;
        fpitch = Math.max(-89f, Math.min(89f, fpitch));

        double speed = 6.0;
        if (key(win, GLFW.GLFW_KEY_LEFT_SHIFT)) speed *= 3;
        if (key(win, GLFW.GLFW_KEY_LEFT_CONTROL)) speed *= 0.3;

        double yaw = Math.toRadians(fyaw), pitch = Math.toRadians(fpitch);
        double fwdX = -Math.sin(yaw) * Math.cos(pitch), fwdY = -Math.sin(pitch), fwdZ = Math.cos(yaw) * Math.cos(pitch);
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);

        double mx = 0, my = 0, mz = 0;
        if (key(win, GLFW.GLFW_KEY_W)) { mx += fwdX; my += fwdY; mz += fwdZ; }
        if (key(win, GLFW.GLFW_KEY_S)) { mx -= fwdX; my -= fwdY; mz -= fwdZ; }
        if (key(win, GLFW.GLFW_KEY_D)) { mx += rightX; mz += rightZ; }
        if (key(win, GLFW.GLFW_KEY_A)) { mx -= rightX; mz -= rightZ; }
        if (key(win, GLFW.GLFW_KEY_E)) { my += 1; }
        if (key(win, GLFW.GLFW_KEY_Q)) { my -= 1; }

        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 0.0001) {
            double k = speed * dt / len;
            fx += mx * k; fy += my * k; fz += mz * k;
        }
    }

    private static boolean key(long win, int code) {
        return GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS;
    }

    private void renderBar(DrawContext ctx, int mx, int my) {
        int x = 8, w = Math.max(200, width - 16);
        HudStyle.fillRounded(ctx, x, 6, w, BAR_H, 8, 0xE60E0E14);

        int bx = x + 6, by = 12;
        bx = chip(ctx, bx, by, "⏮", mx, my, this::stepBack);
        bx = chip(ctx, bx, by, playing ? "⏸" : "▶", mx, my, () -> playing = !playing);
        bx = chip(ctx, bx, by, "⏭", mx, my, this::stepForward);
        bx = chip(ctx, bx, by, mode == 0 ? "От игрока" : "Свободная", mx, my, () -> {
            mode = mode == 0 ? 1 : 0;
            freeReady = false;
            if (mode == 1) { reel = false; blending = false; }
        });
        if (!cuts.isEmpty()) {
            bx = chip(ctx, bx, by, reel ? "Рил " + (cutIndex + 1) + "/" + cuts.size() : "Рил",
                    mx, my, this::toggleReel);
        }
        bx = chip(ctx, bx, by, "x" + SPEEDS[speedIndex], mx, my,
                () -> speedIndex = (speedIndex + 1) % SPEEDS.length);
        chip(ctx, x + w - 46, by, "Выход", mx, my, () -> confirmClose = true);

        String clock = String.format("%.1f / %.1f с", time, rec.duration());
        if (bx + 8 + textRenderer.getWidth(clock) <= x + w - 52) {
            ctx.drawTextWithShadow(textRenderer, clock, bx + 8, by + 4, HudStyle.TEXT_DIM);
        }

        int tx = x + 8, tw = w - 16, ty = 34;
        ctx.fill(tx, ty, tx + tw, ty + 3, 0xFF2A2A36);
        float dur = rec.duration();
        if (dur > 0f) {
            for (int i = 0; i < cuts.size(); i++) {
                HighlightReel.Cut c = cuts.get(i);
                int c1 = tx + (int) (tw * (c.a() / dur));
                int c2 = tx + (int) (tw * (c.b() / dur));
                ctx.fill(c1, ty - 2, Math.max(c1 + 1, c2), ty + 5,
                        reel && i == cutIndex ? 0x99FF6600 : 0x4DFFFFFF);
            }
        }
        float frac = dur > 0 ? Math.min(1f, time / dur) : 0f;
        ctx.fill(tx, ty, tx + (int) (tw * frac), ty + 3, HudStyle.ACCENT);
        ctx.fill(tx + (int) (tw * frac) - 1, ty - 3, tx + (int) (tw * frac) + 2, ty + 6, 0xFFFFFFFF);
    }

    private int chip(DrawContext ctx, int x, int y, String label, int mx, int my, Runnable action) {
        int w = textRenderer.getWidth(label) + 12, h = 16;
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + h;
        HudStyle.fillRounded(ctx, x, y, w, h, 5, hov ? 0xE6FF6600 : 0xFF22222E);
        ctx.drawTextWithShadow(textRenderer, label, x + 6, y + 4, hov ? 0xFF14141C : HudStyle.TEXT);
        zones.add(new Zone(x, y, x + w, y + h, action));
        return x + w + 4;
    }

    private void stepBack() {
        if (reel && !cuts.isEmpty()) jumpToCut(cutIndex - 1);
        else seek(-5f);
    }

    private void stepForward() {
        if (reel && !cuts.isEmpty()) jumpToCut(cutIndex + 1);
        else seek(5f);
    }

    private void seek(float seconds) {
        time = Math.max(0f, Math.min(rec.duration(), time + seconds));
        if (mode == 1) freeReady = false;
        if (reel) syncCut();
    }

    private void seekTo(double mx, int tx, int tw) {
        if (tw <= 0) return;
        float f = (float) Math.max(0f, Math.min(1f, (mx - tx) / tw));
        time = f * rec.duration();
        if (mode == 1) freeReady = false;
        if (reel) syncCut();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        for (Zone z : zones) {
            if (mx >= z.x1() && mx <= z.x2() && my >= z.y1() && my <= z.y2()) { z.action().run(); return true; }
        }
        int x = 8, w = Math.max(200, width - 16), tx = x + 8, tw = w - 16;
        if (my >= 30 && my <= 44 && mx >= tx && mx <= tx + tw) {
            trackDrag = true;
            seekTo(mx, tx, tw);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        trackDrag = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        int x = 8, w = Math.max(200, width - 16);
        if (trackDrag && my <= BAR_H) { seekTo(mx, x + 8, w - 16); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hA, double vA) {
        if (my <= BAR_H) { seek(vA > 0 ? 1f : -1f); return true; }
        return super.mouseScrolled(mx, my, hA, vA);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmClose) { close(); return true; }
            confirmClose = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) { playing = !playing; return true; }
        if (keyCode == GLFW.GLFW_KEY_M) {
            mode = mode == 0 ? 1 : 0;
            freeReady = false;
            if (mode == 1) { reel = false; blending = false; }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) { toggleReel(); return true; }
        if (keyCode == GLFW.GLFW_KEY_N) { jumpToCut(cutIndex + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_B) { jumpToCut(cutIndex - 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { stepBack(); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { stepForward(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
