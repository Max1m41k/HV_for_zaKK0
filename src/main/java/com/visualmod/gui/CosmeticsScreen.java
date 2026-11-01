package com.visualmod.gui;

import com.visualmod.cosmetics.Nimbus;
import com.visualmod.lang.Lang;
import com.visualmod.module.modules.cosmetics.CosmeticsManager;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.util.ButtonStyle;
import com.visualmod.util.HudStyle;
import com.visualmod.util.Soap;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CosmeticsScreen extends Screen {

    private static final int CELL_W     = 70;
    private static final int CELL_H     = 80;
    private static final int CELL_GAP   = 8;
    private static final int COLS       = 2;
    private static final int SECTION_H  = 20;
    private static final int PANEL_W    = COLS * CELL_W + (COLS - 1) * CELL_GAP + 24;

    private static final Identifier LOGO = Identifier.of("visualmod", "textures/gui/hw_logo.png");

    private static final int PANEL      = 0xE60C0A12;
    private static final int ACCENT     = ButtonStyle.ORANGE;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_DIM   = 0xFFC9BCD6;
    private static final int TEXT_SEC   = 0xFFFFC78A;

    private int scrollY = 0;
    private int contentHeight = 0;
    private int panelX, panelY, panelH;
    /** Вид камеры до открытия меню: возвращаем его при закрытии, а не оставляем F5. */
    private Perspective camBefore;

    private record CellHit(String slot, int idx, int x, int y) {}
    private final List<CellHit> cellHits = new ArrayList<>();

    private ColorPicker colorPicker;
    private int chipX, chipY, chipW = -1, chipH = 20;
    private int aiChipY;

    private static final int[] SMALL_ANIM = new int[30];

    public CosmeticsScreen() {
        super(Text.literal("Cosmetics"));
    }

    @Override
    protected void init() {
        panelH = height - 60;
        panelX = width - PANEL_W - 10;
        panelY = 30;
        cellHits.clear();
        colorPicker = null;
        chipW = -1;
        // init() вызывается и при смене размера окна — исходный вид камеры запоминаем один раз.
        if (camBefore == null && client != null && client.player != null) {
            camBefore = client.options.getPerspective();
            if (camBefore != Perspective.THIRD_PERSON_BACK) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x7307060B);

        HudStyle.fillRounded(ctx, panelX + 3, panelY + 4, PANEL_W, panelH, 12, 0x66000000);
        HudStyle.fillRounded(ctx, panelX, panelY, PANEL_W, panelH, 12, PANEL);
        HudStyle.fillRounded(ctx, panelX, panelY, PANEL_W, 30, 12, 0x33FF7A00);
        if (Soap.onGui()) Soap.overlay(ctx, panelX, panelY, PANEL_W, panelH, 12);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, panelX + 9, panelY + 7,
                0f, 0f, 16, 16, 32, 32, 32, 32);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(Lang.isRu() ? "Косметика" : "Cosmetics"), panelX + 31, panelY + 11, 0xFFFFD9B0);

        boolean hovBack = mx >= panelX + PANEL_W - 30 && mx <= panelX + PANEL_W - 10
                && my >= panelY + 6 && my <= panelY + 24;
        HudStyle.fillRounded(ctx, panelX + PANEL_W - 30, panelY + 6, 20, 18, 6,
                hovBack ? ACCENT : 0x2EFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("←"),
                panelX + PANEL_W - 20, panelY + 10, hovBack ? 0xFF1A1206 : 0xFFE8DCC8);

        int contentTop = panelY + 36;
        int contentBot = panelY + panelH - 8;
        ctx.enableScissor(panelX + 4, contentTop, panelX + PANEL_W - 4, contentBot);

        cellHits.clear();
        int drawY = contentTop + scrollY;

        drawY = drawSection(ctx, mx, my, "armor",  CosmeticsManager.ARMOR_NAMES, CosmeticsManager.ARMOR_COLORS, "Броня",    drawY);
        drawY = drawSection(ctx, mx, my, "legs",   CosmeticsManager.LEG_NAMES,   CosmeticsManager.LEG_COLORS,   "Ноги",     drawY);
        drawY = drawSection(ctx, mx, my, "hat",    CosmeticsManager.HAT_NAMES,   CosmeticsManager.HAT_COLORS,   "Шапочки",  drawY);
        drawY = drawSection(ctx, mx, my, "horns",  CosmeticsManager.HORN_NAMES,  CosmeticsManager.HORN_COLORS,  "Рога",     drawY);
        drawY = drawSection(ctx, mx, my, "cape",   CosmeticsManager.CAPE_NAMES,  CosmeticsManager.CAPE_COLORS,  "Плащи",    drawY);
        drawY = drawSection(ctx, mx, my, "back",   CosmeticsManager.BACK_NAMES,  CosmeticsManager.BACK_COLORS,  "Спина",    drawY);
        drawY = drawSection(ctx, mx, my, "face",   CosmeticsManager.FACE_NAMES,  CosmeticsManager.FACE_COLORS,  "На лицо",  drawY);
        drawY = drawSection(ctx, mx, my, "mask",   CosmeticsManager.MASK_NAMES,  CosmeticsManager.MASK_COLORS,  "Маски",    drawY);
        drawY = drawSection(ctx, mx, my, "gloves", CosmeticsManager.GLOVE_NAMES, CosmeticsManager.GLOVE_COLORS, "Перчатки", drawY);
        drawY = drawSection(ctx, mx, my, "tail",   CosmeticsManager.TAIL_NAMES,  CosmeticsManager.TAIL_COLORS,  "Хвосты",   drawY);
        drawY = drawSection(ctx, mx, my, "pet",    CosmeticsManager.PET_NAMES,   CosmeticsManager.PET_COLORS,   "Питомцы",  drawY);

        int[] nimbusColors = new int[CosmeticsManager.NIMBUS_NAMES.length];
        Arrays.fill(nimbusColors, CosmeticsManager.nimbusColor);
        drawY = drawSection(ctx, mx, my, "nimbus", CosmeticsManager.NIMBUS_NAMES, nimbusColors, "Нимбы", drawY);

        chipX = panelX + 12;
        chipW = PANEL_W - 24;
        aiChipY = drawY + 1;
        drawPetAIChip(ctx, mx, my);
        drawY += chipH + 6;
        chipY = drawY + 1;
        drawColorChip(ctx, mx, my);
        drawY += chipH + 8;

        ctx.disableScissor();
        contentHeight = drawY - (contentTop + scrollY);

        int totalContent = contentHeight;
        int visH = contentBot - contentTop;
        if (totalContent > visH) {
            int sbH = (int)((float) visH / totalContent * visH);
            int sbY = contentTop + (int)((float)(-scrollY) / totalContent * visH);
            ctx.fill(panelX + PANEL_W - 5, contentTop, panelX + PANEL_W - 3, contentBot, 0x22FFFFFF);
            HudStyle.fillRounded(ctx, panelX + PANEL_W - 5, sbY, 3, sbH, 1.5f, ACCENT);
        }

        super.render(ctx, mx, my, delta);
        if (colorPicker != null) colorPicker.render(ctx, mx, my, textRenderer, width, height);
    }

    private void drawPetAIChip(DrawContext ctx, int mx, int my) {
        boolean hov = mx >= chipX && mx <= chipX + chipW && my >= aiChipY && my <= aiChipY + chipH;
        boolean ai = CosmeticsManager.petAI;
        boolean hasPet = CosmeticsManager.equippedPet >= 0;
        int base = !hasPet ? 0x14FFFFFF : hov ? 0x2EFFFFFF : 0x1AFFFFFF;
        HudStyle.fillRounded(ctx, chipX, aiChipY, chipW, chipH, 7, base);
        if (hov && hasPet) {
            HudStyle.fillRounded(ctx, chipX, aiChipY, chipW, chipH, 7, 0x26FFFFFF);
            HudStyle.fillRounded(ctx, chipX + 1, aiChipY + 1, chipW - 2, chipH - 2, 6, base);
        }
        ctx.drawTextWithShadow(textRenderer, "Поведение питомца", chipX + 9, aiChipY + 6,
                hasPet ? (hov ? TEXT_WHITE : TEXT_DIM) : 0xFF556070);
        String val = ai ? "Свободный полёт" : "Орбита";
        int vw = textRenderer.getWidth(val);
        ctx.drawTextWithShadow(textRenderer, val, chipX + chipW - vw - 9, aiChipY + 6,
                !hasPet ? 0xFF556070 : (ai ? ACCENT : TEXT_DIM));
    }

    private void drawColorChip(DrawContext ctx, int mx, int my) {
        boolean hov = mx >= chipX && mx <= chipX + chipW && my >= chipY && my <= chipY + chipH;
        int col = CosmeticsManager.nimbusColor | 0xFF000000;
        int base = hov ? 0x2EFFFFFF : 0x1AFFFFFF;
        HudStyle.fillRounded(ctx, chipX, chipY, chipW, chipH, 7, base);
        if (hov) {
            HudStyle.fillRounded(ctx, chipX, chipY, chipW, chipH, 7, 0x26FFFFFF);
            HudStyle.fillRounded(ctx, chipX + 1, chipY + 1, chipW - 2, chipH - 2, 6, base);
        }
        HudStyle.fillRounded(ctx, chipX + 5, chipY + 4, chipH - 8, chipH - 8, 4, col);
        HudStyle.fillRounded(ctx, chipX + 5, chipY + 4, chipH - 8, 3, 4, 0x4DFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Цвет нимба", chipX + chipH + 3, chipY + 6,
                hov ? TEXT_WHITE : TEXT_DIM);
        String hex = String.format("#%06X", col & 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, hex, chipX + chipW - textRenderer.getWidth(hex) - 9,
                chipY + 6, TEXT_SEC);
    }

    private int drawSection(DrawContext ctx, int mx, int my,
                            String slot, String[] names, int[] colors,
                            String title, int startY) {
        int y = startY + 3;

        HudStyle.fillRounded(ctx, panelX + 12, y + 1, 3, 10, 1.5f, ACCENT);
        ctx.drawTextWithShadow(textRenderer, title, panelX + 21, y, TEXT_SEC);
        int lineX = panelX + 27 + textRenderer.getWidth(title);
        if (lineX < panelX + PANEL_W - 14) ctx.fill(lineX, y + 5, panelX + PANEL_W - 12, y + 6, 0x26FFFFFF);
        y += SECTION_H;

        int startX = panelX + 12;
        for (int i = 0; i < names.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx  = startX + col * (CELL_W + CELL_GAP);
            int cy  = y + row * (CELL_H + CELL_GAP);

            boolean equipped = CosmeticsManager.isEquipped(slot, i);
            boolean hovered  = mx >= cx && mx <= cx + CELL_W && my >= cy && my <= cy + CELL_H;

            drawCard(ctx, slot, i, names[i], cx, cy, colors[i], equipped, hovered);
            cellHits.add(new CellHit(slot, i, cx, cy));
        }

        int rows = (int) Math.ceil(names.length / (float) COLS);
        return y + rows * (CELL_H + CELL_GAP) + CELL_GAP;
    }

    /**
     * Карточка: тень → рамка → непрозрачная база → верхний блик → плашка ника.
     * Рамка оранжевая только у экипированного предмета, остальное — нейтральные подсветки.
     */
    private void drawCard(DrawContext ctx, String slot, int idx, String title, int cx, int cy,
                          int color, boolean equipped, boolean hovered) {
        HudStyle.fillRounded(ctx, cx + 1, cy + 2, CELL_W, CELL_H, 9, 0x59000000);
        HudStyle.fillRounded(ctx, cx, cy, CELL_W, CELL_H, 9,
                equipped ? 0xE6FF8A1E : hovered ? 0x59FFFFFF : 0x2BFFFFFF);

        int base = equipped ? 0xFF221506 : 0xFF131019;
        HudStyle.fillRounded(ctx, cx + 1, cy + 1, CELL_W - 2, CELL_H - 2, 8, base);
        HudStyle.fillRounded(ctx, cx + 1, cy + 1, CELL_W - 2, 24, 8,
                equipped ? 0x26FFC080 : 0x12FFFFFF);

        drawCosmeticPreview(ctx, slot, idx, cx, cy, color, equipped);

        HudStyle.fillRounded(ctx, cx + 3, cy + CELL_H - 17, CELL_W - 6, 14, 5,
                equipped ? 0x40FF8A1E : 0x59000000);
        String name = fit(title, CELL_W - 12);
        ctx.drawTextWithShadow(textRenderer, name, cx + (CELL_W - textRenderer.getWidth(name)) / 2,
                cy + CELL_H - 15, equipped ? 0xFFFFE0B8 : hovered ? TEXT_WHITE : TEXT_DIM);

        if (equipped) {
            HudStyle.fillRounded(ctx, cx + CELL_W - 15, cy + 4, 11, 11, 3, ACCENT);
            ctx.drawText(textRenderer, "✔", cx + CELL_W - 13, cy + 5, 0xFF241403, false);
        }
    }

    private String fit(String name, int maxW) {
        if (textRenderer.getWidth(name) <= maxW) return name;
        for (int n = name.length() - 1; n > 0; n--) {
            String s = name.substring(0, n) + "…";
            if (textRenderer.getWidth(s) <= maxW) return s;
        }
        return "…";
    }

    private void drawCosmeticPreview(DrawContext ctx, String slot, int idx,
                                     int cx, int cy, int color, boolean equipped) {
        float t = (System.currentTimeMillis() % 3000) / 3000f;
        int centerX = cx + CELL_W / 2;
        int baseY   = cy + CELL_H - 22;
        float s = 1.6f;

        // «Сцена» под силуэтом: превью не висит в пустоте, а подсвечено снизу.
        HudStyle.fillRounded(ctx, cx + 6, cy + 20, CELL_W - 12, CELL_H - 40, 9,
                equipped ? 0x1FFFFFFF : 0x0DFFFFFF);

        switch (slot) {
            case "cape"   -> drawCapePreview(ctx, idx, centerX, baseY, s, color, t);
            case "hat"    -> drawHatPreview (ctx, idx, centerX, baseY, s, color, t);
            case "armor"  -> drawArmorPreview(ctx, idx, centerX, baseY, s, color);
            case "face"   -> drawFacePreview(ctx, idx, centerX, baseY, s, color);
            case "gloves" -> drawGlovesPreview(ctx, idx, centerX, baseY, s, color);
            case "legs"   -> drawLegsPreview(ctx, idx, centerX, baseY, s, color);
            case "horns"  -> drawHornsPreview(ctx, idx, centerX, baseY, s, color, t);
            case "mask"   -> drawMaskPreview(ctx, idx, centerX, baseY, s, color);
            case "tail"   -> drawTailPreview(ctx, idx, centerX, baseY, s, color, t);
            case "back"   -> drawBackPreview(ctx, idx, centerX, baseY, s, color, t);
            case "pet"    -> drawPetPreview(ctx, idx, centerX, baseY, s, color, t);
            case "nimbus" -> drawNimbusPreview(ctx, idx, centerX, baseY, color, t);
        }
    }

    private void drawNimbusPreview(DrawContext ctx, int idx, int cx, int by, int col, float t) {
        drawMiniFigure(ctx, cx, by, 1.6f, 0x8090A0);
        int cy = by - 34 + (int) (Math.sin(t * Math.PI * 2) * 1.5f);
        int rgb = col | 0xFF000000;
        switch (idx) {
            case Nimbus.TWIN -> {
                ellipse(ctx, cx, cy, 11, 3, 2, rgb);
                ellipse(ctx, cx, cy + 4, 7, 2, 1, argb(rgb & 0xFFFFFF, 0xAA));
            }
            case Nimbus.RUNES -> {
                ellipse(ctx, cx, cy, 10, 3, 1, argb(rgb & 0xFFFFFF, 0xDD));
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2 + t * Math.PI * 2;
                    int px = cx + (int) Math.round(Math.cos(a) * 10);
                    int py = cy + (int) Math.round(Math.sin(a) * 3);
                    ctx.fill(px - 1, py - 3, px + 2, py + 3, rgb);
                }
            }
            case Nimbus.HALO -> {
                ellipse(ctx, cx, cy, 13, 4, 3, argb(rgb & 0xFFFFFF, 0x33));
                ellipse(ctx, cx, cy, 8, 2, 2, rgb);
            }
            default -> {
                ellipse(ctx, cx, cy, 11, 3, 2, rgb);
                ellipse(ctx, cx, cy, 13, 4, 1, argb(rgb & 0xFFFFFF, 0x44));
            }
        }
    }

    /** Кольцо-эллипс для превью: точки по окружности, поэтому выглядит как светящийся обод. */
    private void ellipse(DrawContext ctx, int cx, int cy, int rx, int ry, int thick, int col) {
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0 * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * rx);
            int y = cy + (int) Math.round(Math.sin(a) * ry);
            ctx.fill(x - thick / 2, y - thick / 2, x + thick - thick / 2, y + thick - thick / 2, col);
        }
    }

    private void drawCapePreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        float wave = (float)Math.sin(t * Math.PI * 2) * 3;
        switch (idx) {
            case 0 -> {
                fill4(ctx, cx-3, by-22, cx+3, by+2, col);
                ctx.fill(cx-3, by-8, cx+3, by-6, 0xFF8B6914);
            }
            case 1 -> {
                int[] xs = {cx-4, cx+4, cx+6+(int)wave, cx-6-(int)wave};
                int[] ys = {by-22, by-22, by+4, by+4};
                fillQuad(ctx, xs, ys, col);
            }
            case 2 -> {
                fill4(ctx, cx-4, by-22, cx+4, by+4, col);
                ctx.fill(cx-1, by-20, cx+1, by+2, 0xFF000000);
                ctx.fill(cx-4, by-14, cx+4, by-12, 0xFF000000);
            }
            case 3 -> {
                fill4(ctx, cx-4, by-22, cx+4, by+4, col);
                ctx.fill(cx-4, by-22, cx+4, by-20, 0xFFFFD700);
                ctx.fill(cx-4, by+2, cx+4, by+4, 0xFFFFD700);
            }
            case 4 -> {
                fill4(ctx, cx-5+(int)(wave*0.5f), by-22, cx+5+(int)(wave*0.5f), by+4, col);
                ctx.fill(cx-5, by-22, cx-4, by+4, 0xFF9B6FFF);
                ctx.fill(cx+4, by-22, cx+5, by+4, 0xFF9B6FFF);
            }
        }
    }

    private void drawHatPreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int headTop = by - 28;
        switch (idx) {
            case 0 -> {
                HudStyle.fillRounded(ctx, cx-6, headTop-5, 12, 7, 3, col);
                ctx.fill(cx-7, headTop-2, cx-5, headTop+3, col);
                ctx.fill(cx+5, headTop-2, cx+7, headTop+3, col);
            }
            case 1 -> {
                ctx.fill(cx-6, headTop-4, cx+6, headTop-1, col);
                ctx.fill(cx-8, headTop-1, cx+4, headTop+1, col);
            }
            case 2 -> {
                HudStyle.fillRounded(ctx, cx-5, headTop-8, 10, 10, 2, col);
                ctx.fill(cx-2, headTop-14, cx+2, headTop-6, 0xFFFFFFFF);
                ctx.fill(cx-1, headTop-16, cx+1, headTop-14, col);
            }
            case 3 -> {
                ctx.fill(cx-9, headTop-2, cx+9, headTop+1, col);
                HudStyle.fillRounded(ctx, cx-5, headTop-7, 10, 7, 2, col);
            }
            case 4 -> {
                ctx.fill(cx-6, headTop-2, cx+6, headTop+2, col);
                for (int z = 0; z < 3; z++) {
                    int zx = cx - 4 + z * 4;
                    ctx.fill(zx, headTop-6, zx+2, headTop-2, col);
                }
                ctx.fill(cx-3, headTop-1, cx-1, headTop+1, 0xFFFF4444);
                ctx.fill(cx+1, headTop-1, cx+3, headTop+1, 0xFF4444FF);
            }
        }
    }

    private void drawArmorPreview(DrawContext ctx, int idx, int cx, int by, float s, int col) {
        switch (idx) {
            case 0 -> {
                drawMiniFigure(ctx, cx, by, s, col);
                fill4(ctx, cx-5, by-18, cx+5, by+4, argb(col, 0xCC));
                ctx.fill(cx+7, by-24, cx+8, by+2, 0xFF8B6914);
                ctx.fill(cx+6, by-25, cx+9, by-23, col);
            }
            case 1 -> {
                drawMiniFigure(ctx, cx, by, s, col);
                ctx.fill(cx-5, by-28, cx+5, by-22, 0xFF888888);
                ctx.fill(cx-3, by-26, cx+3, by-24, 0xFF333333);
                ctx.fill(cx-8, by-20, cx-5, by-16, col);
                ctx.fill(cx+5, by-20, cx+8, by-16, col);
            }
            case 2 -> {
                drawMiniFigure(ctx, cx, by, s, 0x2C2C2C);
                ctx.fill(cx-5, by-18, cx-2, by+4, col);
                ctx.fill(cx+2, by-18, cx+5, by+4, 0xFFEEEEEE);
                ctx.fill(cx-10, by-26, cx-9, by-4, 0xFFCCCCCC);
                ctx.fill(cx-11, by-10, cx-8, by-9, 0xFF8B6914);
            }
            case 3 -> {
                drawMiniFigure(ctx, cx, by, s, col);
                ctx.fill(cx-6, by-20, cx+6, by-19, 0xFFFFD700);
                ctx.fill(cx-6, by+3, cx+6, by+4, 0xFFFFD700);
                ctx.fill(cx+7, by-26, cx+8, by+2, 0xFFFFD700);
                HudStyle.fillRounded(ctx, cx+5, by-28, 6, 5, 2, 0xFFFFD700);
            }
            case 4 -> {
                drawMiniFigure(ctx, cx, by, s, col);
                ctx.fill(cx-9, by-22, cx-7, by-17, 0xFF444444);
                ctx.fill(cx+7, by-22, cx+9, by-17, 0xFF444444);
                ctx.fill(cx-4, by-27, cx-2, by-25, 0xFFFF0000);
                ctx.fill(cx+2, by-27, cx+4, by-25, 0xFFFF0000);
            }
        }
    }

    private void drawFacePreview(DrawContext ctx, int idx, int cx, int by, float s, int col) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int ey = by - 26;
        switch (idx) {
            case 0 -> {
                ctx.fill(cx-5, ey-1, cx-2, ey+2, col);
                ctx.fill(cx+2, ey-1, cx+5, ey+2, col);
                ctx.fill(cx-2, ey, cx+2, ey+1, col);
            }
            case 1 -> {
                ctx.fill(cx-5, ey-2, cx+1, ey+2, col);
                ctx.fill(cx-5, ey-1, cx-3, ey, 0xFF000000);
            }
            case 2 -> {
                HudStyle.fillRounded(ctx, cx-5, ey+1, 10, 6, 2, col);
            }
            case 3 -> {
                HudStyle.fillRounded(ctx, cx-6, ey-2, 12, 5, 2, col);
                ctx.fill(cx-4, ey-1, cx-1, ey+1, 0xFF00AA44);
                ctx.fill(cx+1, ey-1, cx+4, ey+1, 0xFF00AA44);
            }
            case 4 -> {
                HudStyle.fillRounded(ctx, cx-5, ey-2, 10, 8, 1, col);
                ctx.fill(cx-3, ey-1, cx-1, ey+1, 0xFF000000);
                ctx.fill(cx+1, ey-1, cx+3, ey+1, 0xFF000000);
                ctx.fill(cx-2, ey+2, cx+2, ey+3, 0xFF000000);
            }
        }
    }

    private void drawGlovesPreview(DrawContext ctx, int idx, int cx, int by, float s, int col) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int gy = by - 8;
        HudStyle.fillRounded(ctx, cx-9, gy, 4, 5, 1, col);
        HudStyle.fillRounded(ctx, cx+5, gy, 4, 5, 1, col);
        switch (idx) {
            case 2 -> {
                ctx.fill(cx-9, gy-1, cx-5, gy, 0xFFFF6622);
                ctx.fill(cx+5, gy-1, cx+9, gy, 0xFFFF6622);
            }
            case 3 -> {
                ctx.fill(cx-9, gy, cx-5, gy+1, 0xFF9B6FFF);
                ctx.fill(cx+5, gy, cx+9, gy+1, 0xFF9B6FFF);
            }
            case 4 -> {
                ctx.fill(cx-10, gy, cx-9, gy+5, 0xFF88EEFF);
                ctx.fill(cx+9, gy, cx+10, gy+5, 0xFF88EEFF);
            }
        }
    }

    private void drawHornsPreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int top = by - 30;
        float sway = (float) Math.sin(t * Math.PI * 2) * 0.8f;
        switch (idx) {
            case 0 -> {
                fillQuad(ctx, new int[]{cx-6, cx-3, cx-8+(int)sway, cx-7+(int)sway},
                        new int[]{top, top, top-8, top-7}, col);
                fillQuad(ctx, new int[]{cx+3, cx+6, cx+7+(int)sway, cx+8+(int)sway},
                        new int[]{top, top, top-7, top-8}, col);
                ctx.fill(cx-8, top-9, cx-6, top-7, 0xFF5E1E1E);
                ctx.fill(cx+6, top-9, cx+8, top-7, 0xFF5E1E1E);
            }
            case 1 -> {
                ctx.fill(cx-8, top-3, cx-5, top+1, col);
                ctx.fill(cx+5, top-3, cx+8, top+1, col);
                ctx.fill(cx-10, top, cx-7, top+4, 0xFFD6CAB0);
                ctx.fill(cx+7, top, cx+10, top+4, 0xFFD6CAB0);
                ctx.fill(cx-9, top+4, cx-6, top+7, 0xFFC4B79B);
                ctx.fill(cx+6, top+4, cx+9, top+7, 0xFFC4B79B);
            }
            case 2 -> {
                ctx.fill(cx-6, top-7, cx-4, top+1, col);
                ctx.fill(cx+4, top-7, cx+6, top+1, col);
                ctx.fill(cx-9, top-5, cx-4, top-4, 0xFF8A6B44);
                ctx.fill(cx+4, top-5, cx+9, top-4, 0xFF8A6B44);
                ctx.fill(cx-9, top-9, cx-8, top-4, 0xFF86663F);
                ctx.fill(cx+8, top-9, cx+9, top-4, 0xFF86663F);
                ctx.fill(cx-5, top-10, cx-4, top-6, 0xFF7A5B3C);
                ctx.fill(cx+4, top-10, cx+5, top-6, 0xFF7A5B3C);
            }
            default -> {
                fillQuad(ctx, new int[]{cx-6, cx-4, cx-9+(int)sway, cx-9+(int)sway},
                        new int[]{top, top, top-9, top-6}, col);
                fillQuad(ctx, new int[]{cx+4, cx+6, cx+9+(int)sway, cx+9+(int)sway},
                        new int[]{top, top, top-6, top-9}, 0xFF54F0CC);
                ctx.fill(cx-2, top-6, cx-1, top-2, 0xFFB48CFF);
                ctx.fill(cx+1, top-6, cx+2, top-2, 0xFFB48CFF);
            }
        }
    }

    private void drawMaskPreview(DrawContext ctx, int idx, int cx, int by, float s, int col) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int fy = by - 29;
        switch (idx) {
            case 0 -> {
                ctx.fill(cx-5, fy+3, cx+5, fy+8, col);
                fillQuad(ctx, new int[]{cx-2, cx+2, cx+1, cx-1},
                        new int[]{fy+6, fy+6, fy+14, fy+14}, 0xFF241C2C);
                ctx.fill(cx-4, fy+4, cx-2, fy+6, 0xFFC8A24A);
                ctx.fill(cx+2, fy+4, cx+4, fy+6, 0xFFC8A24A);
            }
            case 1 -> {
                HudStyle.fillRounded(ctx, cx-6, fy+2, 12, 4, 1, 0xFF0E141C);
                ctx.fill(cx-5, fy+3, cx+5, fy+5, col);
                ctx.fill(cx-1, fy, cx+1, fy+1, 0xFFFF4D6D);
            }
            case 2 -> {
                HudStyle.fillRounded(ctx, cx-5, fy, 10, 10, 2, col);
                ctx.fill(cx-4, fy+3, cx-1, fy+5, 0xFFFF2A2A);
                ctx.fill(cx+1, fy+3, cx+4, fy+5, 0xFFFF2A2A);
                ctx.fill(cx-2, fy+8, cx+2, fy+9, 0xFF4A4256);
            }
            default -> {
                ctx.fill(cx-5, fy+5, cx+5, fy+9, col);
                ctx.fill(cx-5, fy+6, cx+5, fy+7, 0xFFE8DCC0);
                fillQuad(ctx, new int[]{cx+5, cx+6, cx+9, cx+8},
                        new int[]{fy+6, fy+7, fy+10, fy+8}, 0xFF8C242E);
            }
        }
    }

    private void drawTailPreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int wave = (int) (Math.sin(t * Math.PI * 2) * 2.5f);
        int rx = cx + 6, ry = by - 8;
        switch (idx) {
            case 0 -> {
                HudStyle.fillRounded(ctx, rx, ry + wave, 5, 5, 2, col);
                HudStyle.fillRounded(ctx, rx + 4, ry - 3 + wave, 6, 6, 3, 0xFFE07B3C);
                HudStyle.fillRounded(ctx, rx + 9, ry - 8 + wave, 7, 7, 3, 0xFFE88A48);
                HudStyle.fillRounded(ctx, rx + 14, ry - 13 + wave, 7, 7, 3, 0xFFF7F1E4);
            }
            case 1 -> {
                ctx.fill(rx, ry, rx + 2, ry + 4, col);
                ctx.fill(rx + 2, ry - 3 + wave, rx + 4, ry + 1, 0xFF2F2B39);
                ctx.fill(rx + 4, ry - 7 + wave, rx + 6, ry - 3, 0xFF383245);
                ctx.fill(rx + 6, ry - 9 + wave, rx + 9, ry - 7, 0xFF443C53);
            }
            case 2 -> {
                ctx.fill(rx, ry, rx + 2, ry + 3, col);
                ctx.fill(rx + 3, ry - 2 + wave, rx + 5, ry + 1, 0xFF54232C);
                ctx.fill(rx + 6, ry - 6 + wave, rx + 8, ry - 3, 0xFF5E2833);
                fillQuad(ctx, new int[]{rx + 8, rx + 14, rx + 13, rx + 9},
                        new int[]{ry - 8, ry - 10 + wave, ry - 4 + wave, ry - 4}, 0xFFA33344);
            }
            default -> {
                fillQuad(ctx, new int[]{rx, rx + 2, rx + 9, rx + 7},
                        new int[]{ry, ry + 1, ry - 8 + wave, ry - 10 + wave}, col);
                fillQuad(ctx, new int[]{rx, rx + 2, rx + 6, rx + 4},
                        new int[]{ry + 1, ry + 2, ry - 12 + wave, ry - 13 + wave}, 0xFF2BB6D8);
                fillQuad(ctx, new int[]{rx + 1, rx + 3, rx + 11, rx + 9},
                        new int[]{ry, ry + 1, ry - 4 + wave, ry - 6 + wave}, 0xFFF6D45A);
                fillQuad(ctx, new int[]{rx + 2, rx + 4, rx + 12, rx + 10},
                        new int[]{ry + 1, ry + 2, ry + 1 + wave, ry - 1 + wave}, 0xFFFF8A3C);
            }
        }
    }

    private void drawBackPreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int sw = (int) (Math.sin(t * Math.PI * 2) * 1.5f);
        // Лямки видны спереди, сама поклажа рисуется справа от силуэта — там, где у модели спина.
        ctx.fill(cx-5, by-20, cx-4, by-11, 0xFF6B4322);
        ctx.fill(cx+4, by-20, cx+5, by-11, 0xFF6B4322);
        ctx.fill(cx-4, by-14, cx+4, by-13, 0xFF6B4322);

        int px = cx + 5;
        switch (idx) {
            case 1 -> {
                HudStyle.fillRounded(ctx, px, by-20+sw, 10, 16, 5, 0xFFEDE3CC);
                ctx.fill(px-1, by-16+sw, px+11, by-15+sw, 0xFFF7F1E4);
                ctx.fill(px-1, by-10+sw, px+11, by-9+sw, 0xFFE3D5B8);
                ctx.fill(px+2, by-18+sw, px+7, by-7+sw, argb(0xFFD766, 0x44));
                ctx.fill(px+4, by-16+sw, px+5, by-9+sw, 0xFFFFD766);
                fillQuad(ctx, new int[]{cx-1, cx+1, px+2, px}, new int[]{by-21, by-21, by-19+sw, by-20+sw}, 0xCCF7F1E4);
                fillQuad(ctx, new int[]{cx+2, cx+4, px+7, px+5}, new int[]{by-21, by-21, by-19+sw, by-20+sw}, 0xCCF7F1E4);
            }
            case 2 -> {
                for (int k = 0; k < 3; k++) {
                    int ty = by - 19 + k * 5 + sw / 2;
                    ctx.fill(px, ty, px+9, ty+4, k == 1 ? 0xFFD6C39C : 0xFFC9B48E);
                    ctx.fill(px+9, ty, px+11, ty+4, 0xFFEFE4C8);
                }
                ctx.fill(px+3, by-20+sw/2, px+6, by-7+sw/2, 0xFF6B4322);
                ctx.fill(px+2, by-14+sw/2, px+7, by-12+sw/2, 0xFFB0303C);
                ctx.fill(px+3, by-19+sw/2, px+6, by-17+sw/2, 0xFFFFD766);
            }
            case 3 -> {
                for (int k = 0; k < 4; k++) {
                    int ty = by - 20 + k * 4;
                    int w = 11 - k * 2;
                    ctx.fill(px, ty, px+w, ty+3, k % 2 == 0 ? 0xFF2E5A54 : 0xFF356A62);
                    ctx.fill(px, ty, px+w, ty+1, 0xFF7FE0D0);
                    fillQuad(ctx, new int[]{px+w-2, px+w, px+w+4, px+w+3},
                            new int[]{ty, ty+2, ty-2, ty-4}, 0xFF5FCFBE);
                }
                ctx.fill(px+1, by-24, px+4, by-19, 0xFF4FBFAF);
            }
            default -> {
                HudStyle.fillRounded(ctx, px, by-19+sw/2, 10, 12, 3, col);
                ctx.fill(px, by-19+sw/2, px+10, by-16+sw/2, 0xFFA9703A);
                HudStyle.fillRounded(ctx, px-1, by-23, 12, 4, 2, 0xFF7A6A55);
                ctx.fill(px+2, by-19+sw/2, px+3, by-7+sw/2, 0xFF4A2E15);
                ctx.fill(px+7, by-19+sw/2, px+8, by-7+sw/2, 0xFF4A2E15);
                ctx.fill(px+1, by-13+sw/2, px+4, by-12+sw/2, 0xFFFFD700);
                ctx.fill(px+6, by-13+sw/2, px+9, by-12+sw/2, 0xFFFFD700);
            }
        }
    }

    private void drawLegsPreview(DrawContext ctx, int idx, int cx, int by, float s, int col) {
        drawMiniFigure(ctx, cx, by, s, 0x8090A0);
        int ly = by - 8;
        switch (idx) {
            case 0 -> {
                ctx.fill(cx-4, ly, cx-1, ly+9, col);
                ctx.fill(cx+1, ly, cx+4, ly+9, col);
                ctx.fill(cx-4, ly, cx+4, ly+2, 0xFFC8A24A);
                ctx.fill(cx-1, ly+3, cx+1, ly+6, 0xFFDCE2EA);
            }
            case 1 -> {
                ctx.fill(cx-4, ly+3, cx-1, ly+9, col);
                ctx.fill(cx+1, ly+3, cx+4, ly+9, col);
                ctx.fill(cx-6, ly+7, cx-1, ly+10, 0xFF4C5566);
                ctx.fill(cx+1, ly+7, cx+6, ly+10, 0xFF4C5566);
            }
            case 2 -> {
                ctx.fill(cx-4, ly, cx-1, ly+9, 0xFF5B2A6E);
                ctx.fill(cx+1, ly, cx+4, ly+9, 0xFF5B2A6E);
                ctx.fill(cx-4, ly, cx+4, ly+1, col);
                fillQuad(ctx, new int[]{cx-4, cx-1, cx-6, cx-6}, new int[]{ly+2, ly+3, ly, ly-1}, 0xFFD0B0FF);
                fillQuad(ctx, new int[]{cx+1, cx+4, cx+6, cx+6}, new int[]{ly+2, ly+3, ly-1, ly}, 0xFFD0B0FF);
            }
            default -> {
                for (int k = 0; k < 4; k++) {
                    ctx.fill(cx-4, ly + 1 + k * 2, cx-1, ly + 2 + k * 2, k % 2 == 0 ? col : 0xFFD8C9A6);
                    ctx.fill(cx+1, ly + 1 + k * 2, cx+4, ly + 2 + k * 2, k % 2 == 0 ? col : 0xFFD8C9A6);
                }
                ctx.fill(cx-5, ly+9, cx-1, ly+11, 0xFF8B5A2B);
                ctx.fill(cx+1, ly+9, cx+5, ly+11, 0xFF8B5A2B);
            }
        }
    }

    private void drawPetPreview(DrawContext ctx, int idx, int cx, int by, float s, int col, float t) {
        float bob = (float)Math.sin(t * Math.PI * 2) * 3;
        int px = cx + 14, py = (int)(by - 10 + bob);
        switch (idx) {
            case 0 -> {
                HudStyle.fillRounded(ctx, px-5, py-6, 10, 8, 3, col);
                ctx.fill(px-3, py-10, px-1, py-6, col);
                ctx.fill(px+1, py-10, px+3, py-6, col);
                ctx.fill(px-1, py-8, px+1, py-7, 0xFF111111);
                ctx.fill(px+4, py-2, px+8, py-1, col);
            }
            case 1 -> {
                HudStyle.fillRounded(ctx, px-4, py-5, 8, 8, 4, col);
                ctx.fill(px-3, py-5, px+3, py-3, 0xFF111111);
                ctx.fill(px-3, py-1, px+3, py+1, 0xFF111111);
                HudStyle.fillRounded(ctx, px-7, py-7, 4, 5, 3, argb(0xAAEEFF, 0x88));
                HudStyle.fillRounded(ctx, px+3, py-7, 4, 5, 3, argb(0xAAEEFF, 0x88));
            }
            case 2 -> {
                HudStyle.fillRounded(ctx, px-4, py-5, 8, 7, 2, col);
                ctx.fill(px-4, py-8, px-2, py-5, col);
                ctx.fill(px+2, py-8, px+4, py-5, col);
                ctx.fill(px-2, py-4, px, py-3, 0xFF111111);
                ctx.fill(px, py-4, px+2, py-3, 0xFF111111);
            }
            case 3 -> {
                HudStyle.fillRounded(ctx, px-3, py-6, 6, 8, 3, col);
                fillQuad(ctx,
                    new int[]{px-3,px-8,px-9,px-4},
                    new int[]{py-5,py-8,py-3,py-1},
                    argb(0x88AAFF, 0x99));
                fillQuad(ctx,
                    new int[]{px+3,px+8,px+9,px+4},
                    new int[]{py-5,py-8,py-3,py-1},
                    argb(0x88AAFF, 0x99));
            }
            case 4 -> {
                HudStyle.fillRounded(ctx, px-3, py-5, 7, 6, 3, col);
                ctx.fill(px+4, py-3, px+7, py-2, 0xFFFFCC44);
                ctx.fill(px-5, py-2, px-3, py+1, col);
            }
        }
    }

    private void drawMiniFigure(DrawContext ctx, int cx, int by, float s, int bodyColor) {
        int bc = 0xFF000000 | bodyColor;
        HudStyle.fillRounded(ctx, cx-5, by-30, 10, 9, 2, 0xFFAA9988);
        ctx.fill(cx-4, by-21, cx+4, by-9, bc);
        ctx.fill(cx-7, by-20, cx-4, by-10, bc);
        ctx.fill(cx+4, by-20, cx+7, by-10, bc);
        ctx.fill(cx-4, by-9, cx-1, by+2, bc);
        ctx.fill(cx+1, by-9, cx+4, by+2, bc);
    }

    private void fill4(DrawContext ctx, int x1, int y1, int x2, int y2, int col) {
        ctx.fill(x1, y1, x2, y2, 0xFF000000 | col);
    }

    private void fillQuad(DrawContext ctx, int[] xs, int[] ys, int col) {
        int minY = Math.min(Math.min(ys[0],ys[1]),Math.min(ys[2],ys[3]));
        int maxY = Math.max(Math.max(ys[0],ys[1]),Math.max(ys[2],ys[3]));
        for (int y = minY; y <= maxY; y++) {
            float t = (maxY==minY)?0:(float)(y-minY)/(maxY-minY);
            int lx = (int)(xs[0]+(xs[3]-xs[0])*t);
            int rx = (int)(xs[1]+(xs[2]-xs[1])*t);
            if (lx > rx) { int tmp=lx; lx=rx; rx=tmp; }
            ctx.fill(lx, y, rx+1, y+1, col);
        }
    }

    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        if (colorPicker != null) {
            if (!colorPicker.mouseClicked(mx, my, btn)) colorPicker = null;
            return true;
        }
        if (btn == 0) {
            if (chipW > 0 && CosmeticsManager.equippedPet >= 0
                    && mx >= chipX && mx <= chipX + chipW && my >= aiChipY && my <= aiChipY + chipH) {
                CosmeticsManager.petAI = !CosmeticsManager.petAI;
                return true;
            }
            if (chipW > 0 && mx >= chipX && mx <= chipX + chipW && my >= chipY && my <= chipY + chipH) {
                colorPicker = new ColorPicker(nimbusColorSetting(), ColorPicker.Kind.WHEEL);
                return true;
            }
            int bx = panelX + PANEL_W - 30, bw = 20, bTop = panelY + 6, bH = 18;
            if (mx>=bx && mx<=bx+bw && my>=bTop && my<=bTop+bH) {
                client.setScreen(new ClickGUI());
                return true;
            }
            for (CellHit h : cellHits) {
                if (mx>=h.x() && mx<=h.x()+CELL_W && my>=h.y() && my<=h.y()+CELL_H) {
                    CosmeticsManager.toggle(h.slot(), h.idx());
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (colorPicker != null) return true;
        int visH = panelH - 44;
        int maxScroll = Math.max(0, contentHeight - visH);
        scrollY = Math.max(-maxScroll, Math.min(0, scrollY + (int)(vAmt * 18)));
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (colorPicker != null) return colorPicker.mouseDragged(click.x(), click.y());
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (colorPicker != null) colorPicker.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (colorPicker != null && colorPicker.keyPressed(input.key())) {
            colorPicker = null;
            return true;
        }
        return super.keyPressed(input);
    }

    private static ColorSetting nimbusColorSetting() {
        return new ColorSetting("Nimbus Color",
                () -> CosmeticsManager.nimbusColor,
                c -> CosmeticsManager.nimbusColor = c | 0xFF000000);
    }

    @Override
    public boolean shouldPause() { return false; }
    @Override
    public boolean shouldCloseOnEsc() { return true; }
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    public void removed() {
        super.removed();
        if (camBefore != null) {
            client.options.setPerspective(camBefore);
            camBefore = null;
        }
        com.visualmod.config.ConfigManager.autoSave();
    }
}
