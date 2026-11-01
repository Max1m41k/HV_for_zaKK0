package com.visualmod.util;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Встроенная функция «посмотреть инвентарь в упор»: пока модуль включён, на игрока под прицелом
 * уходит /invsee, открывшееся серверное окно снимается в снимок и сразу закрывается, а содержимое
 * рисуется панелью над головой цели.
 *
 * /invsee есть только у доната «драгон»: если окно за отведённое время не открылось, в чат уходит
 * пояснение и запросы прекращаются до повторного включения. В режиме PVP (тот же флаг, что у
 * PvpSafe) модуль выключает сам модуль.
 */
public final class InventoryCheck {

    private static final long SNAPSHOT_TTL = 5000;
    private static final long MIN_INTERVAL = 1500;
    private static final long OPEN_TIMEOUT = 2500;
    private static final double LOOK_RANGE = 8.0;

    private static final int COLS = 9, SLOT = 18, GAP = 1, PAD = 4, HEAD = 13, MAX_ROWS = 6;

    private static boolean registered;
    private static boolean active;
    private static boolean awaiting;
    private static boolean blocked;
    private static long askedAt;
    private static long lastRequest;
    private static int targetId = -1;
    private static List<ItemStack> items = Collections.emptyList();

    private InventoryCheck() {}

    public static void init() {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register(InventoryCheck::render);
    }

    public static void setActive(boolean on) {
        active = on;
        clear();
    }

    private static void clear() {
        awaiting = false;
        blocked = false;
        targetId = -1;
        askedAt = 0L;
        lastRequest = 0L;
        items = Collections.emptyList();
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        try {
            if (mc == null || mc.player == null || mc.world == null) {
                clear();
                return;
            }
            long now = System.currentTimeMillis();

            if (awaiting) {
                if (mc.currentScreen instanceof HandledScreen<?> screen) {
                    capture(mc, screen);
                    return;
                }
                if (now - askedAt > OPEN_TIMEOUT) {
                    awaiting = false;
                    blocked = true;
                    notify(mc, "§cДанная функция работает только от доната драгон");
                }
                return;
            }

            if (blocked || mc.currentScreen != null) return;
            if (targetId != -1 && now - lastRequest < SNAPSHOT_TTL) return;
            if (now - lastRequest < MIN_INTERVAL) return;

            PlayerEntity target = lookingAt(mc);
            if (target == null) return;
            ask(mc, target);
        } catch (Throwable t) {
            Diag.once("InventoryCheck.tick", t);
        }
    }

    private static void ask(MinecraftClient mc, PlayerEntity target) {
        String nick = target.getGameProfile().name();
        if (nick == null || nick.isEmpty() || mc.player.networkHandler == null) return;
        awaiting = true;
        askedAt = lastRequest = System.currentTimeMillis();
        targetId = target.getId();
        mc.player.networkHandler.sendChatCommand("invsee " + nick);
    }

    /** Слоты чужого инвентаря лежат в открытом handler'е; свои отсекаем по типу inventory. */
    private static void capture(MinecraftClient mc, HandledScreen<?> screen) {
        awaiting = false;
        ScreenHandler handler = screen.getScreenHandler();
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            out.add(slot.getStack().copy());
        }
        items = out;
        lastRequest = System.currentTimeMillis();
        mc.player.closeHandledScreen();
    }

    private static PlayerEntity lookingAt(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity p && p != mc.player) return p;

        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1f).multiply(LOOK_RANGE));
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            java.util.Optional<Vec3d> h = p.getBoundingBox().expand(0.12).raycast(eye, end);
            if (h.isEmpty()) continue;
            double d = h.get().squaredDistanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static void render(DrawContext ctx, RenderTickCounter tc) {
        if (!active || items.isEmpty() || ctx == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || mc.options.hudHidden) return;
        try {
            Entity e = mc.world.getEntityById(targetId);
            if (!(e instanceof PlayerEntity p)) return;

            int rows = Math.min(MAX_ROWS, (items.size() + COLS - 1) / COLS);
            int pw = PAD * 2 + COLS * (SLOT + GAP) - GAP;
            int ph = PAD * 2 + HEAD + rows * (SLOT + GAP) - GAP;

            Vec3d pos = new Vec3d(p.getX(), p.getY() + p.getHeight() + 0.55, p.getZ());
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d camPos = cam.getCameraPos();
            Matrix4f view = new Matrix4f();
            view.rotate(cam.getRotation());
            view.invert();
            view.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
            Matrix4f mvp = new Matrix4f(mc.gameRenderer.getBasicProjectionMatrix(tc.getTickProgress(true))).mul(view);

            Vector4f v = new Vector4f((float) pos.x, (float) pos.y, (float) pos.z, 1f).mul(mvp);
            if (v.w <= 1e-4f) return;
            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
            float sx = (v.x / v.w * 0.5f + 0.5f) * sw;
            float sy = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
            float x = sx - pw / 2f, y = sy - ph;
            if (x + pw < 0 || x > sw || y + ph < 0 || y > sh) return;

            HudStyle.fillRounded(ctx, x, y, pw, ph, 6f, 0xF0121218);
            HudStyle.fillRounded(ctx, x, y, pw, 1.5f, 0.75f, 0xFFFF8A2A);

            TextRenderer tr = mc.textRenderer;
            String title = p.getGameProfile().name();
            ctx.drawTextWithShadow(tr, title, (int) x + PAD, (int) y + 3, 0xFFFFFFFF);

            for (int i = 0; i < items.size() && i < rows * COLS; i++) {
                ItemStack stack = items.get(i);
                int cx = (int) x + PAD + (i % COLS) * (SLOT + GAP);
                int cy = (int) y + PAD + HEAD + (i / COLS) * (SLOT + GAP);
                HudStyle.fillRounded(ctx, cx, cy, SLOT, SLOT, 3f, 0xFF2A2C33);
                if (stack.isEmpty()) continue;
                ctx.drawItem(stack, cx + 1, cy + 1);
                ctx.drawStackOverlay(tr, stack, cx + 1, cy + 1);
            }
        } catch (Throwable t) {
            Diag.once("InventoryCheck.render", t);
        }
    }

    private static void notify(MinecraftClient mc, String message) {
        if (mc == null || mc.player == null) return;
        mc.player.sendMessage(Text.of("§d[HolyVisuals] " + message), false);
    }
}
