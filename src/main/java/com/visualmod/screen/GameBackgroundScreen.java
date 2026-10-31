package com.visualmod.screen;

import com.visualmod.util.ButtonStyle;
import com.visualmod.util.HudStyle;
import com.visualmod.util.GameBackground;
import com.visualmod.util.LauncherBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class GameBackgroundScreen extends Screen {

    private static final int CARD_W = 128, CARD_H = 80, GAP = 14;
    private static final int THUMB_W = CARD_W * 2, THUMB_H = CARD_H * 2;

    private final Screen parent;
    private final Identifier[] thumbIds = new Identifier[LauncherBackground.COUNT + 1];
    private final boolean[] hasImage = new boolean[LauncherBackground.COUNT + 1];
    private int gridX, gridY;
    private boolean busy = false;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;
    private boolean closed = false;

    public GameBackgroundScreen(Screen parent) {
        super(Text.literal("Смена фона игры"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        gridX = (width - (3 * CARD_W + 2 * GAP)) / 2;
        gridY = Math.max(44, height / 2 - CARD_H - GAP / 2 - 10);

        int by = gridY + 2 * CARD_H + GAP + 44;
        addDrawableChild(ButtonWidget.builder(Text.literal("Стандартный фон"), b -> choose(0))
                .dimensions(width / 2 - 146, by, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
                .dimensions(width / 2 + 6, by, 140, 20).build());

        for (int n = 1; n <= LauncherBackground.COUNT; n++) hasImage[n] = LauncherBackground.imageFor(n) != null;
        loadThumbnailsAsync();
    }

    private void loadThumbnailsAsync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int n = 1; n <= LauncherBackground.COUNT; n++) {
            final int num = n;
            Path src = LauncherBackground.imageFor(n);
            if (src == null || thumbIds[n] != null) continue;
            CompletableFuture.supplyAsync(() -> makeThumbnailPng(src)).thenAccept(png -> {
                if (png == null) return;
                mc.execute(() -> {
                    if (closed) return;
                    try {
                        NativeImage ni = NativeImage.read(new ByteArrayInputStream(png));
                        Identifier id = Identifier.of("visualmod", "game_bg_thumb/" + num);
                        mc.getTextureManager().registerTexture(id,
                                new NativeImageBackedTexture(() -> "visualmod:game_bg_thumb/" + num, ni));
                        thumbIds[num] = id;
                    } catch (Exception ignored) {}
                });
            });
        }
    }

    private static byte[] makeThumbnailPng(Path src) {
        try {
            BufferedImage img = ImageIO.read(src.toFile());
            if (img == null) return null;
            double sx = THUMB_W / (double) img.getWidth(), sy = THUMB_H / (double) img.getHeight();
            double s = Math.max(sx, sy);
            int w = (int) Math.ceil(img.getWidth() * s), h = (int) Math.ceil(img.getHeight() * s);
            BufferedImage out = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (THUMB_W - w) / 2, (THUMB_H - h) / 2, w, h, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        TextRenderer tr = textRenderer;
        ctx.drawCenteredTextWithShadow(tr, "Смена фона игры", width / 2, 16, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, "Выберите картинку из папки textures (1-6) — фон сменится сразу", width / 2, 29, 0xFFAAAAAA);

        int current = GameBackground.current();
        for (int n = 1; n <= LauncherBackground.COUNT; n++) {
            int[] pos = cardPos(n);
            int x = pos[0], y = pos[1];
            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            boolean selected = n == current;
            int border = selected ? 0xFF55FF55 : hover ? ButtonStyle.ORANGE_LIGHT : hasImage[n] ? ButtonStyle.ORANGE : 0xFF4A4A4A;

            HudStyle.fillRounded(ctx, x, y, CARD_W, CARD_H, 8, border);
            HudStyle.fillRounded(ctx, x + 1, y + 1, CARD_W - 2, CARD_H - 2, 7, 0xF0101010);

            if (thumbIds[n] != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, thumbIds[n], x + 4, y + 4, 0f, 0f,
                        CARD_W - 8, CARD_H - 8, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
                ctx.fill(x + 4, y + 4, x + CARD_W - 4, y + CARD_H - 4, hover ? 0x22000000 : 0x55000000);
            }

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + CARD_W / 2f, y + CARD_H / 2f - 10);
            ctx.getMatrices().scale(3f, 3f);
            String num = String.valueOf(n);
            ctx.drawText(tr, num, -tr.getWidth(num) / 2, -4, hasImage[n] ? 0xFFFFFFFF : 0xFF777777, true);
            ctx.getMatrices().popMatrix();

            if (!hasImage[n]) ctx.drawCenteredTextWithShadow(tr, "нет файла", x + CARD_W / 2, y + CARD_H - 22, 0xFF888888);
            else if (selected) ctx.drawCenteredTextWithShadow(tr, "\u2713 текущий", x + CARD_W / 2, y + CARD_H - 22, 0xFF55FF55);
        }

        if (!status.isEmpty()) ctx.drawCenteredTextWithShadow(tr, status, width / 2, gridY + 2 * CARD_H + GAP + 14, statusColor);
        if (busy) ctx.drawCenteredTextWithShadow(tr, "Загрузка картинки...", width / 2, gridY + 2 * CARD_H + GAP + 26, 0xFFFFAA00);
    }

    private int[] cardPos(int n) {
        int i = n - 1;
        return new int[]{gridX + (i % 3) * (CARD_W + GAP), gridY + (i / 3) * (CARD_H + GAP)};
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        if (click.button() == 0 && !busy) {
            for (int n = 1; n <= LauncherBackground.COUNT; n++) {
                int[] p = cardPos(n);
                if (mouseX >= p[0] && mouseX < p[0] + CARD_W && mouseY >= p[1] && mouseY < p[1] + CARD_H) {
                    choose(n);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void choose(int n) {
        if (busy) return;
        if (n > 0 && !hasImage[n]) {
            setStatus("Файл textures/" + n + ".png не найден", 0xFFFF5555);
            return;
        }
        busy = true;
        setStatus("", 0xFFAAAAAA);
        GameBackground.select(n, r -> {
            busy = false;
            setStatus(r.message(), r.ok() ? 0xFF55FF55 : 0xFFFF5555);
        });
    }

    private void setStatus(String s, int color) { status = s; statusColor = color; }

    @Override
    public void removed() {
        closed = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int n = 1; n <= LauncherBackground.COUNT; n++) {
            if (thumbIds[n] != null) mc.getTextureManager().destroyTexture(thumbIds[n]);
        }
        super.removed();
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
