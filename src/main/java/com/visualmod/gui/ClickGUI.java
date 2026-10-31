package com.visualmod.gui;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.hud.BlurGUI;
import com.visualmod.module.modules.hud.GUIAnimations;
import com.visualmod.module.modules.hud.GuiStyle;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.KeySetting;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.module.settings.ToggleSetting;
import com.visualmod.util.BindRegistry;
import com.visualmod.util.BrowserMedia;
import com.visualmod.module.modules.hud.MusicOverlay;
import com.visualmod.util.HudStyle;
import com.visualmod.util.KeyNames;
import com.visualmod.util.MusicManager;
import com.visualmod.util.Rgb;
import com.visualmod.util.Soap;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGUI extends Screen {

    @Override
    protected void applyBlur(DrawContext ctx) {
        if (GuiStyle.style == GuiStyle.Style.SOAP) {
            super.applyBlur(ctx);
        } else if (GuiStyle.style == GuiStyle.Style.COLUMN
                && com.visualmod.module.modules.util.ColumnSettings.frost()) {
            super.applyBlur(ctx);
        }
    }

    private float openFade = 1f;

    private int panelColor() {
        if (GuiStyle.style == GuiStyle.Style.COLUMN) {
            return com.visualmod.module.modules.util.ColumnSettings.panelArgb(openFade);
        }
        int argb;
        if (GuiStyle.style == GuiStyle.Style.CUSTOM) {
            int a = (int) (0xFF * Math.min(1f, Math.max(0f, (float) GuiStyle.customFill)));
            argb = (a << 24) | (GuiStyle.customColor & 0x00FFFFFF);
        } else {
            argb = 0xC812121A;
        }
        return withAlpha(argb, openFade);
    }

    private static int withAlpha(int argb, float a) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static final int C_ACCENT   = 0xFFFF6600;
    private static final int C_TEXT     = 0xFFFFFFFF;
    private static final int C_TEXT_DIM = 0xFF9A9AAE;
    /** Фиолетовый акцент настроек по референсу: чипы режимов и слайдеры. */
    private static final int C_VIOLET     = 0xFF8B5CF6;
    private static final int C_VIOLET_SOFT = 0xFFC3B2FF;
    private static final int CHIP_H = 13;
    /** Ширина чипа «rgb»: на столько же укорачивается ряд бинда, перед которым он стоит. */
    private static final int RGB_W = 30;
    /** Маркеры «не обрезать» для общих хелперов отрисовки настроек. */
    private static final int NO_CLIP_TOP = -1_000_000;
    private static final int NO_CLIP_BOTTOM = 1_000_000;

    private static final Module.Category[] CATS = Module.Category.values();

    private Module settingsModule = null;
    private final List<ClickRegion> settingsRegions = new ArrayList<>();
    private SliderSetting draggingSlider;
    private Module awaitingBindFor;
    /** Настройка-клавиша, для которой сейчас ждём нажатие (включая кнопку мыши). */
    private KeySetting awaitingKeyFor;
    private int pointerButton;
    private int dragBarX, dragBarW;
    private ColorPicker colorPicker;

    private static final int SETTINGS_PANEL_W = 180;

    @FunctionalInterface
    private interface RegionAction { void run(double mx, double my); }

    private record ClickRegion(int x1, int y1, int x2, int y2, RegionAction action) {
        boolean contains(double mx, double my) {
            return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
        }
    }

    private static final int GUI_W      = 540;
    private static final int GUI_H      = 320;
    private static final int SIDEBAR_W  = 44;
    private static final int TAB_H      = 32;
    private static final int MODULE_H   = 32;
    private static final int MODULE_W   = 220;
    private static final int COLS       = 2;

    private static final int MOD_ROW_H   = 26;
    private static final int COL_HEADER  = 28;
    private static final int COL_FOOTER  = 17;

    /** Категории + последняя колонка с конфигами. */
    private int columnCount() { return CATS.length + 1; }

    private Module.Category selectedCat = Module.Category.RENDER;
    private int scrollOffset = 0;
    private int configsScroll = 0;
    /** Список конфигов читается с диска при открытии GUI, а не каждый кадр. */
    private List<String> configsCache = List.of();
    private final Map<Module.Category, Integer> columnScrollOffsets = new HashMap<>();
    private int guiX, guiY;
    private String tipText;
    private String tipTitle = "";
    private int tipX, tipY;

    /** Внешний вызов (например, из ArrayList-чипа) подсветить модуль зелёным при открытии. */
    public static Module pendingFocus;
    private Module focusModule;
    private long focusUntil;

    private final Map<Module, Float> toggleAnim = new HashMap<>();
    /** Анимация раскрытия блока настроек модуля в колонках: доля высоты и полная высота блока. */
    private final Map<Module, Float> settingsReveal = new HashMap<>();
    private final Map<Module, Integer> settingsHeights = new HashMap<>();
    /** Высота панели настроек в стандартном виде: пересчитывается каждый кадр, панель растёт вместе с содержимым. */
    private int settingsPanelH = 200;
    private float frameDelta = 0.5f;
    private TextFieldWidget searchField;
    private String searchQuery = "";

    public ClickGUI() { super(Text.literal("HolyVisuals")); }

    @Override
    protected void init() {
        com.visualmod.util.GuiWave.trigger();
        guiX = (width  - GUI_W) / 2;
        guiY = (height - GUI_H) / 2;

        int sfX = guiX + SIDEBAR_W + 10;
        int sfY = guiY + TAB_H + 6;
        searchField = new TextFieldWidget(textRenderer, sfX, sfY, GUI_W - SIDEBAR_W - 20, 14, Text.literal(""));
        searchField.setMaxLength(32);
        searchField.setPlaceholder(Text.literal("§8Поиск модуля..."));
        searchField.setChangedListener(q -> { searchQuery = q.toLowerCase().trim(); scrollOffset = 0; });
        searchField.setDrawsBackground(false);
        addDrawableChild(searchField);
        settingsModule = null;
        awaitingBindFor = null;
        awaitingKeyFor = null;
        colorPicker = null;
        settingsReveal.clear();
        settingsHeights.clear();
        draggingSlider = null;
        configsCache = com.visualmod.config.ConfigManager.listConfigs();
        configsScroll = 0;

        Module focus = pendingFocus;
        pendingFocus = null;
        if (focus != null) {
            focusModule = focus;
            focusUntil = System.currentTimeMillis() + 3000L;
            selectedCat = focus.getCategory();
            searchQuery = focus.getName().toLowerCase(java.util.Locale.ROOT);
            if (searchField != null) searchField.setText(searchQuery);
            scrollOffset = 0;
            columnScrollOffsets.put(focus.getCategory(), 0);
            settingsModule = focus instanceof Configurable ? focus : null;
        } else {
            focusModule = null;
        }

        if (VisualsMod.moduleManager != null) {
            GUIAnimations ga = VisualsMod.moduleManager.getModule(GUIAnimations.class);
            if (ga != null) ga.triggerOpen();
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (BlurGUI.isActive()) {
            ctx.fill(0, 0, width, height, 0xAA08080C);
        }

        GUIAnimations.Transform anim = GUIAnimations.globalTransform();
        openFade = anim.alpha();
        frameDelta = Math.max(0.25f, Math.min(3f, delta));
        settingsRegions.clear();

        for (Module m : VisualsMod.moduleManager.getModules()) {
            float cur = toggleAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f);
            float target = m.isEnabled() ? 1f : 0f;
            toggleAnim.put(m, cur + (target - cur) * 0.15f);
        }

        ctx.fill(0, 0, width, height, withAlpha(0x55000000, openFade));

        boolean animActive = anim.alpha() < 1f || anim.scale() != 1f || anim.offsetY() != 0f;
        if (animActive) {
            int cx = guiX + GUI_W / 2, cy = guiY + GUI_H / 2;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, cy + anim.offsetY());
            ctx.getMatrices().scale(anim.scale(), anim.scale());
            ctx.getMatrices().translate(-cx, -cy);
        }

        if (GuiStyle.style == GuiStyle.Style.COLUMN) {
            renderMultiColumnView(ctx, mx, my);
        } else {
            renderStandardView(ctx, mx, my);
        }

        if (tipText != null) {
            drawTooltip(ctx, tipText, tipX, tipY);
            tipText = null;
        }

        if (animActive) {
            ctx.getMatrices().popMatrix();
        }

        // Модуль мог быть скрыт (лицензия или блок-лист сервера), пока его настройки открыты.
        if (settingsModule != null && (settingsModule.isHidden() || !(settingsModule instanceof Configurable))) {
            settingsModule = null;
        }
        if (settingsModule != null && GuiStyle.style != GuiStyle.Style.COLUMN
                && settingsModule instanceof Configurable cfg) {
            renderSettingsPanel(ctx, mx, my, settingsModule, cfg);
        }

        if (com.visualmod.api.LiteApi.isPending()) {
            String note = "⏳ Сервер проверяет права: часть функций скрыта до ответа";
            ctx.drawTextWithShadow(textRenderer, note, (width - textRenderer.getWidth(note)) / 2, 4, 0xFFE1A85B);
        }

        if (GuiStyle.style != GuiStyle.Style.COLUMN) {
            renderMusicOverlay(ctx, mx, my);
        }

        super.render(ctx, mx, my, delta);

        if (colorPicker != null) colorPicker.render(ctx, mx, my, textRenderer, width, height);
    }

    private void renderStandardView(DrawContext ctx, int mx, int my) {
        HudStyle.fillRounded(ctx, guiX, guiY, GUI_W, GUI_H, 12, panelColor());
        if (Soap.onGui()) Soap.overlay(ctx, guiX, guiY, GUI_W, GUI_H, 12, openFade);
        HudStyle.fillRounded(ctx, guiX, guiY, SIDEBAR_W, GUI_H, 12, 0xEE0E0E14);
        if (Soap.onGui()) Soap.overlay(ctx, guiX, guiY, SIDEBAR_W, GUI_H, 12, openFade * 0.7f);

        ctx.fill(guiX + SIDEBAR_W, guiY, guiX + SIDEBAR_W + 1, guiY + GUI_H, 0x22FFFFFF);
        HudStyle.fillRounded(ctx, guiX + 8, guiY + 10, 6, 6, 3, C_ACCENT);

        int iconY = guiY + 38;
        for (int i = 0; i < CATS.length; i++) {
            boolean sel = CATS[i] == selectedCat;
            boolean hov = mx >= guiX && mx <= guiX + SIDEBAR_W && my >= iconY && my < iconY + 28;
            if (sel) {
                ctx.fill(guiX, iconY, guiX + SIDEBAR_W, iconY + 28, 0x22FF6600);
                HudStyle.fillRounded(ctx, guiX, iconY, 3, 28, 1.5f, C_ACCENT);
            } else if (hov) {
                ctx.fill(guiX, iconY, guiX + SIDEBAR_W, iconY + 28, 0x11FFFFFF);
            }
            int ic = sel ? C_ACCENT : hov ? C_TEXT : C_TEXT_DIM;
            ctx.drawCenteredTextWithShadow(textRenderer, new String[]{ "⚔", "🏃", "👁", "👤", "⚙", "📊", "✨" }[i], guiX + SIDEBAR_W / 2, iconY + 10, ic);
            iconY += 32;
        }

        int cosY = guiY + GUI_H - 36;
        boolean cosHov = mx >= guiX && mx <= guiX + SIDEBAR_W && my >= cosY && my < cosY + 28;
        HudStyle.fillRounded(ctx, guiX + 4, cosY + 2, SIDEBAR_W - 8, 24, 6,
            cosHov ? 0xDD2A1A40 : 0xBB1A1030);
        ctx.drawCenteredTextWithShadow(textRenderer, "✨",
            guiX + SIDEBAR_W / 2, cosY + 9,
            cosHov ? 0xFFCC88FF : 0xFF9B6FFF);

        renderTabs(ctx, mx, my);

        int sfX = guiX + SIDEBAR_W + 10;
        int sfY = guiY + TAB_H + 6;
        searchField.setX(sfX);
        searchField.setY(sfY);
        searchField.setWidth(GUI_W - SIDEBAR_W - 20);

        renderModules(ctx, mx, my);
    }

    private record Columns(int screenW, int colW, int gap, int startY, int colH, int colsPerRow, int rowStep, int count) {
        int colX(int i) {
            int row = i / colsPerRow, col = i % colsPerRow;
            int inRow = Math.min(colsPerRow, count - row * colsPerRow);
            return (screenW - (inRow * colW + (inRow - 1) * gap)) / 2 + col * (colW + gap);
        }
        int colY(int i)  { return startY + (i / colsPerRow) * rowStep; }
        int listY(int i) { return colY(i) + COL_HEADER; }
        int listH(int i) { return colH - COL_HEADER - COL_FOOTER; }
        boolean contains(int i, double mx, double my) {
            double x = colX(i);
            return mx >= x && mx <= x + colW && my >= colY(i) && my <= colY(i) + colH;
        }
    }

    private Columns columns() {
        int n = columnCount(), gap = 5;
        int avail = Math.max(140, width - 16);
        // Сверху нужен запас под плашку-заголовок, снизу — под строку поиска.
        int startY = Math.max(20, Math.min(34, height / 12));
        int bottom = Math.max(startY + 90, height - 44);
        int oneRow = (avail - (n - 1) * gap) / n;
        if (oneRow >= 62) {
            return new Columns(width, Math.min(118, oneRow), gap, startY, bottom - startY, n, bottom - startY, n);
        }
        int per = (n + 1) / 2;
        int colW = Math.max(64, Math.min(118, (avail - (per - 1) * gap) / per));
        // Между рядами оставляем место под плашку-заголовок верхних колонок второго ряда.
        int rowGap = 20;
        int colH = Math.max(90, (bottom - startY - rowGap) / 2);
        return new Columns(width, colW, gap, startY, colH, per, colH + rowGap, n);
    }

    /**
     * Кольцо колонки для STEPS: 0 у центральной (у чётного числа — у обеих средних),
     * дальше по одной единице в каждую сторону, поэтому волна расходится от середины.
     */
    private static int columnRing(Columns c, int i) {
        int row = i / c.colsPerRow(), col = i % c.colsPerRow();
        int inRow = Math.min(c.colsPerRow(), c.count() - row * c.colsPerRow());
        return (int) Math.ceil(Math.abs(col - (inRow - 1) / 2.0));
    }

    private void renderMultiColumnView(DrawContext ctx, int mx, int my) {
        Columns c = columns();
        boolean steps = GUIAnimations.stepsActive();

        for (int i = 0; i < c.count(); i++) {
            int cx = c.colX(i), cy = c.colY(i), colW = c.colW();
            int listY = c.listY(i), listH = c.listH(i);
            boolean configsCol = i >= CATS.length;
            Module.Category cat = configsCol ? null : CATS[i];

            float reveal = steps ? GUIAnimations.stepsColumnAlpha(columnRing(c, i)) : 1f;
            if (steps && reveal <= 0.004f) continue;
            float panelA = steps ? Math.min(1f, reveal * 1.6f) : 1f;

            int wash = com.visualmod.module.modules.util.ColumnSettings.washArgb();
            if (wash != 0) HudStyle.fillRounded(ctx, cx - 3, cy - 3, colW + 6, c.colH() + 6, 14, withAlpha(wash, panelA));
            HudStyle.fillRounded(ctx, cx, cy, colW, c.colH(), 11, withAlpha(panelColor(), panelA));
            if (Soap.onGui()) Soap.overlay(ctx, cx, cy, colW, c.colH(), 11, panelA);
            if (!steps || reveal > 0.5f) drawColumnPill(ctx, cx, cy, colW, configsCol ? "Configs" : cat.getDisplayName());

            if (configsCol) {
                renderConfigsColumn(ctx, cx + 5, listY + configsScroll, colW - 10, listY, listH, mx, my);
                continue;
            }

            List<Module> mods = VisualsMod.moduleManager.getModules().stream()
                    .filter(m -> m.getCategory() == cat)
                    .filter(m -> !m.isHidden())
                    .filter(m -> com.visualmod.lang.Lang.matches(searchQuery, m.getName(), m.getDisplayName(), m.getDisplayDescription()))
                    .toList();

            ctx.enableScissor(cx + 4, listY, cx + colW - 4, listY + Math.max(0, listH));

            int curY = listY + columnScrollOffsets.getOrDefault(cat, 0);
            for (Module mod : mods) {
                boolean clipped = curY + MOD_ROW_H <= listY || curY >= listY + listH;
                boolean hov = !clipped && mx >= cx + 5 && mx <= cx + colW - 5 && my >= curY && my < curY + MOD_ROW_H - 3;
                float rowA = steps ? GUIAnimations.stepsRowAlpha(reveal, listY, listH, curY, MOD_ROW_H - 3) : 1f;
                if (rowA > 0.004f) {
                    HudStyle.fillRounded(ctx, cx + 5, curY, colW - 10, MOD_ROW_H - 3, 7,
                            withAlpha(hov ? 0xDD2A2A38 : 0xAA1E1E28, rowA));

                    if (mod.isEnabled()) {
                        HudStyle.fillRounded(ctx, cx + 5, curY, 3, MOD_ROW_H - 3, 1.5f, withAlpha(accentOf(mod), rowA));
                    }
                    drawFocusGlow(ctx, mod, cx + 5, curY, colW - 10, MOD_ROW_H - 3, 7);
                    ctx.drawTextWithShadow(textRenderer,
                            com.visualmod.util.ConfigMark.trim(textRenderer, mod.getDisplayName(), colW - 16),
                            cx + 10, curY + 5, withAlpha(mod.isEnabled() ? C_TEXT : C_TEXT_DIM, rowA));
                    if (com.visualmod.util.ConfigMark.configurable(mod)) {
                        com.visualmod.util.ConfigMark.draw(ctx, cx + colW - 11, curY + (MOD_ROW_H - 3) / 2,
                                withAlpha(0xFFFFFFFF, rowA));
                    }
                }
                if (hov && settingsModule != mod) updateTooltip(mod, mx, my);

                addModuleRowRegion(mod, cx + 5, curY, colW - 10, listY, listH);

                curY += MOD_ROW_H;
                float settingsOpen = revealFor(mod, settingsModule == mod);
                if (settingsOpen > 0.004f && mod instanceof Configurable cfg) {
                    int blockTop = curY - 1;
                    Integer full = settingsHeights.get(mod);
                    int animH = full == null ? 1 : Math.max(1, Math.round(full * settingsOpen));
                    ctx.enableScissor(cx + 4, blockTop, cx + colW - 4, blockTop + animH);
                    int blockBottom = renderInlineSettingsNarrow(ctx, cx + 5, blockTop, colW - 10, mod, cfg,
                            mx, my, listY, listH, blockTop + animH);
                    ctx.disableScissor();
                    // Блок расчерчен целиком, поэтому его полная высота известна только после первой отрисовки.
                    settingsHeights.put(mod, Math.max(0, blockBottom - blockTop));
                    curY = blockTop + animH + 2;
                }
            }

            // curY уже включает смещение, поэтому высоту контента считаем без него:
            // иначе clamp после каждой прокрутки возвращает offset к 0 и колесо кажется мёртвым.
            int off = columnScrollOffsets.getOrDefault(cat, 0);
            int contentH = Math.max(0, curY - listY - off);
            int minOff = Math.min(0, listH - contentH);
            if (off < minOff) columnScrollOffsets.put(cat, minOff);

            ctx.disableScissor();

            if (cat == Module.Category.COSMETICS) {
                drawCosmeticsButton(ctx, cx + 5, listY + listH + 1, colW - 10, mx, my);
            }
        }

        int barW = Math.min(270, width - 8);
        int barH = 26;
        int barX = (width - barW) / 2;
        int barY = height - 38;

        HudStyle.fillRounded(ctx, barX, barY, barW, barH, 10, panelColor());
        searchField.setX(barX + 10);
        searchField.setY(barY + 6);
        searchField.setWidth(Math.max(40, barW - 135));
        searchField.setVisible(true);

        renderMusicOverlayIntegrated(ctx, barX + barW - 115, barY + 3, mx, my);
    }

    /** Плашка с названием блока над колонкой (референс: «» Movement»). */
    private void drawColumnPill(DrawContext ctx, int cx, int cy, int colW, String title) {
        if (!com.visualmod.module.modules.util.ColumnSettings.pillEnabled()) return;
        String label = "» " + title;
        int pw = textRenderer.getWidth(label) + 12;
        int px = Math.max(2, cx + (colW - pw) / 2);
        int py = cy - 15;
        int color = com.visualmod.module.modules.util.ColumnSettings.titleArgb();
        HudStyle.fillRounded(ctx, px, py, pw, 13, 6.5f, color);
        ctx.drawTextWithShadow(textRenderer, label, px + 6, py + 3, readableOn(color));
    }

    private void renderConfigsColumn(DrawContext ctx, int x, int y, int w, int listY, int listH, int mx, int my) {
        int maxScroll = Math.max(0, configsCache.size() * MOD_ROW_H - Math.max(0, listH));
        configsScroll = Math.max(-maxScroll, Math.min(0, configsScroll));
        ctx.enableScissor(x - 5, listY, x + w + 5, listY + Math.max(0, listH));
        int curY = y;
        for (String name : configsCache) {
            boolean current = name.equals(com.visualmod.config.ConfigManager.currentConfigName);
            boolean hov = curY + 19 > listY && curY < listY + listH
                    && mx >= x && mx <= x + w && my >= curY && my < curY + 19;
            HudStyle.fillRounded(ctx, x, curY, w, 19, 7, current ? 0xE6FF6600 : hov ? 0xDD2A2A38 : 0xAA1E1E28);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, w - 12), x + 6, curY + 5,
                    current ? 0xFF14141C : hov ? C_TEXT : C_TEXT_DIM);
            if (hov) {
                final String cfg = name;
                settingsRegions.add(new ClickRegion(x, Math.max(curY, listY), x + w,
                        Math.min(curY + 19, listY + listH), (cmx, cmy) ->
                        com.visualmod.config.ConfigManager.loadConfig(cfg)));
            }
            curY += MOD_ROW_H;
        }
        ctx.disableScissor();
    }

    private void addModuleRowRegion(Module mod, int x, int y, int w, int listY, int listH) {
        addClippedRegion(x, y, w, MOD_ROW_H - 3, listY, listH, (cx, cy) -> {
            if (pointerButton == 0) mod.toggle();
            else if (pointerButton == 1) settingsModule = settingsModule == mod ? null : mod;
        });
    }

    private void addClippedRegion(int x, int y, int w, int h, int listY, int listH, RegionAction action) {
        addRegion(x, y, w, h, listY, listY + listH, action);
    }

    /** Кликабельная область, обрезанная по вертикали [clipTop, clipBottom]: за пределами видно нечего. */
    private void addRegion(int x, int y, int w, int h, int clipTop, int clipBottom, RegionAction action) {
        if (y + h <= clipTop || y >= clipBottom) return;
        settingsRegions.add(new ClickRegion(x, Math.max(y, clipTop), x + w, Math.min(y + h, clipBottom), action));
    }

    /**
     * Плавное раскрытие блока настроек в колонках: 0 — свернут, 1 — раскрыт. Двигается за кадр,
     * поэтому скорость не зависит от частоты fps в пределах разумного.
     */
    private float revealFor(Module mod, boolean open) {
        float cur = settingsReveal.getOrDefault(mod, 0f);
        if (cur == 0f && !open) return 0f;

        float target = open ? 1f : 0f;
        float k = Math.max(0.08f, Math.min(0.45f, frameDelta * 0.55f));
        float next = cur + (target - cur) * k;
        if (Math.abs(target - next) < 0.01f) next = target;

        if (next <= 0f) {
            settingsReveal.remove(mod);
            settingsHeights.remove(mod);
        } else {
            settingsReveal.put(mod, next);
        }
        return next;
    }

    private void updateTooltip(Module mod, int mx, int my) {
        String desc = mod.getDisplayDescription();
        if (desc == null || desc.isEmpty()) return;
        tipText = desc;
        tipTitle = mod.getDisplayName();
        tipX = mx;
        tipY = my;
    }

    private void drawTooltip(DrawContext ctx, String desc, int mx, int my) {
        int maxTextW = Math.min(180, width - 24);
        List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String word : desc.split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = sb.length() == 0 ? word : sb + " " + word;
            if (sb.length() > 0 && textRenderer.getWidth(candidate) > maxTextW) {
                lines.add(sb.toString());
                sb.setLength(0);
                sb.append(word);
            } else {
                sb.setLength(0);
                sb.append(candidate);
            }
        }
        if (sb.length() > 0) lines.add(sb.toString());

        int w = textRenderer.getWidth(tipTitle);
        for (String line : lines) w = Math.max(w, textRenderer.getWidth(line));
        w += 12;
        int h = 14 + lines.size() * 10;

        int x = mx + 12, y = my - h - 8;
        if (x + w > width - 4) x = Math.max(4, width - 4 - w);
        if (y < 4) y = Math.min(height - h - 4, my + 16);

        HudStyle.fillRounded(ctx, x, y, w, h, 5, 0xF20C0C12);
        ctx.fill(x + 3, y + 1, x + w - 3, y + 2, 0x66FF6600);
        ctx.drawTextWithShadow(textRenderer, tipTitle, x + 6, y + 4, C_ACCENT);
        int ly = y + 14;
        for (String line : lines) {
            ctx.drawTextWithShadow(textRenderer, line, x + 6, ly, C_TEXT_DIM);
            ly += 10;
        }
    }

    private void renderMusicOverlayIntegrated(DrawContext ctx, int x, int y, int mx, int my) {
        String[] info = musicWidgetInfo();
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info[0], 60), x, y + 3, C_TEXT);
        drawMusicButton(ctx, x + 66, y, 44);
    }

    /**
     * Косметика — одна кнопка на весь слот-список: раньше здесь были строки «Плащ», «Шапка»,
     * «Броня» размером с модуль, и они выдавались за переключатели.
     */
    private void drawCosmeticsButton(DrawContext ctx, int x, int y, int w, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + 16;
        HudStyle.fillRounded(ctx, x, y, w, 16, 6, hov ? 0xDD4A2A70 : 0xBB2A1A50);
        ctx.drawCenteredTextWithShadow(textRenderer, "✨ Косметика", x + w / 2, y + 4,
                hov ? 0xFFEEAAFF : 0xFFCC88FF);
        settingsRegions.add(new ClickRegion(x, y, x + w, y + 16, (cx, cy) ->
                client.setScreen(new CosmeticsScreen())));
    }

    private int renderInlineSettingsNarrow(DrawContext ctx, int x, int y, int w, Module mod, Configurable cfg,
                                           int mx, int my, int listY, int listH, int clipBottom) {
        int curY = y;
        int top = listY;
        int bottom = Math.min(listY + listH, clipBottom);

        boolean awaiting = awaitingBindFor == mod;
        String bindKey = BindRegistry.getKeyFor(mod.getName());
        String bindLabel = awaiting ? "Нажмите..." : bindKey != null ? KeyNames.displayLabel(bindKey) : "нет";
        drawRgbChip(ctx, x, curY, 20, mod, mx, my);
        int bindX = x + RGB_W + 4, bindW = Math.max(40, w - RGB_W - 4);
        HudStyle.fillRounded(ctx, bindX, curY, bindW, 20, 6, awaiting ? 0xDD4A3370 : 0xCC1E1E28);
        ctx.drawTextWithShadow(textRenderer, "Bind", bindX + 5, curY + 6, C_TEXT_DIM);
        String bindShown = textRenderer.trimToWidth(bindLabel, bindW - 34);
        ctx.drawTextWithShadow(textRenderer, bindShown,
                bindX + bindW - textRenderer.getWidth(bindShown) - 5, curY + 6, awaiting ? C_ACCENT : C_TEXT);
        addRegion(bindX, curY, bindW, 20, top, bottom, (cx, cy) -> awaitingBindFor = mod);
        curY += 22;

        for (Setting s : cfg.getSettings()) {
            if (s instanceof ModeSetting mode) {
                curY = renderModeChips(ctx, x + 5, curY, w - 10, mode, mx, my, top, bottom) + 2;
            } else if (s instanceof ToggleSetting tg) {
                ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(tg.getName(), w - 52),
                        x + 5, curY + 6, C_TEXT_DIM);
                int chipW = Math.min(46, w - 12);
                if (curY + 17 > top && curY < bottom) {
                    drawToggleButton(ctx, x + w - chipW - 5, curY + 3, chipW, tg);
                }
                curY += 22;
            } else if (s instanceof SliderSetting slider) {
                curY = renderSlider(ctx, x + 5, curY, w - 10, slider, top, bottom) + 4;
            } else if (s instanceof ColorSetting color) {
                HudStyle.fillRounded(ctx, x, curY, w, 20, 6, 0xCC1E1E28);
                ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(color.getName(), w - 52),
                        x + 5, curY + 6, C_TEXT_DIM);
                int chipW = Math.min(46, w - 12), chipH = 14, chipX = x + w - chipW - 5, chipY = curY + 3;
                int col = color.getColor() | 0xFF000000;
                HudStyle.fillRounded(ctx, chipX, chipY, chipW, chipH, 5, col);
                ctx.drawTextWithShadow(textRenderer, "color", chipX + 4, chipY + 3, readableOn(col));
                addRegion(x, curY, w, 20, top, bottom, (cmx, cmy) -> openColorPicker(color));
                curY += 22;
            } else if (s instanceof KeySetting ks) {
                curY = renderKeyRow(ctx, x, curY, w, ks, top, bottom) + 4;
            }
        }
        return curY;
    }

    /**
     * Режимы чипами в несколько строк вместо стрелок: активный залит фиолетовым, остальные — тёмные пилюли,
     * которые попадают в режим напрямую одним кликом.
     */
    private int renderModeChips(DrawContext ctx, int x, int y, int w, ModeSetting s, int mx, int my,
                                int clipTop, int clipBottom) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), w), x, y, C_TEXT_DIM);

        int cy = y + 11;
        int cx = x;
        int active = s.getIndex();
        String[] options = s.getOptions();
        for (int i = 0; i < options.length; i++) {
            int chipW = textRenderer.getWidth(options[i]) + 10;
            if (cx > x && cx + chipW > x + w) {
                cx = x;
                cy += CHIP_H + 3;
            }
            boolean sel = i == active;
            boolean hov = mx >= cx && mx <= cx + chipW && my >= cy && my <= cy + CHIP_H;
            int bg = sel ? (hov ? 0xFFA584FF : C_VIOLET) : (hov ? 0xFF32323F : 0xFF22222D);
            HudStyle.fillRounded(ctx, cx, cy, chipW, CHIP_H, CHIP_H / 2f, bg);
            ctx.drawTextWithShadow(textRenderer, options[i], cx + 5, cy + 2, sel ? 0xFF14121C : C_TEXT_DIM);

            final int index = i;
            addRegion(cx, cy, chipW, CHIP_H, clipTop, clipBottom, (cmx, cmy) -> s.setIndex(index));
            cx += chipW + 3;
        }
        return cy + CHIP_H + 2;
    }

    /** Слайдер по референсу: имя слева, значение справа, тонкий фиолетовый трек с круглой ручкой. */
    private int renderSlider(DrawContext ctx, int x, int y, int w, SliderSetting s, int clipTop, int clipBottom) {
        String value = s.getDisplayValue();
        int valueW = textRenderer.getWidth(value);
        int barW = Math.max(28, w - valueW - 8);

        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), barW), x, y, C_TEXT_DIM);
        ctx.drawTextWithShadow(textRenderer, value, x + w - valueW, y, C_VIOLET_SOFT);

        int barY = y + 10;
        float frac = Math.max(0f, Math.min(1f, s.getFraction()));
        int fillW = Math.round(barW * frac);
        HudStyle.fillRounded(ctx, x, barY, barW, 3, 1.5f, 0xFF262634);
        if (fillW > 0) HudStyle.fillRounded(ctx, x, barY, fillW, 3, 1.5f, C_VIOLET);
        float knobX = x + fillW;
        HudStyle.fillRounded(ctx, knobX - 4f, barY - 2.5f, 8, 8, 4f, C_VIOLET);
        HudStyle.fillRounded(ctx, knobX - 1.5f, barY + 0.5f, 3, 3, 1.5f, 0xFFF2EEFF);

        final int fBarX = x, fBarW = barW;
        addRegion(x, y - 1, barW, 17, clipTop, clipBottom, (cx, cy) -> {
            draggingSlider = s;
            dragBarX = fBarX;
            dragBarW = fBarW;
            s.setFraction((float) (cx - fBarX) / fBarW);
        });
        return barY + 9;
    }

    private void renderTabs(DrawContext ctx, int mx, int my) {
        String[] tabNames = getTabNames();
        int tabX = guiX + SIDEBAR_W + 60;
        int tabY = guiY + 8;

        for (String tab : tabNames) {
            boolean sel = tab.equals(selectedCat.getDisplayName());
            int tw = textRenderer.getWidth(tab) + 16;
            if (sel) {
                HudStyle.fillRounded(ctx, tabX, tabY, tw, 18, 5, 0xDD1E1E28);
                ctx.fill(tabX + 2, tabY + 16, tabX + tw - 2, tabY + 18, C_ACCENT);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, tab, tabX + tw / 2, tabY + 5, sel ? 0xFFFFFFFF : C_TEXT_DIM);
            tabX += tw + 4;
        }
        ctx.fill(guiX + SIDEBAR_W, guiY + TAB_H, guiX + GUI_W, guiY + TAB_H + 1, 0x22FFFFFF);
    }

    private String[] getTabNames() {
        List<String> tabs = new ArrayList<>();
        for (Module.Category c : CATS) tabs.add(c.getDisplayName());
        return tabs.toArray(new String[0]);
    }

    private void renderModules(DrawContext ctx, int mx, int my) {
        List<Module> mods = getFiltered();
        int contentX = guiX + SIDEBAR_W + 10;
        int contentY = guiY + TAB_H + 26;
        int contentH = GUI_H - TAB_H - 26 - 4;

        int rows = (mods.size() + COLS - 1) / COLS;
        int gridH = Math.max(0, rows * (MODULE_H + 4) - 4)
                + (selectedCat == Module.Category.COSMETICS ? MODULE_H + 4 : 0);
        scrollOffset = Math.max(Math.min(0, contentH - gridH), Math.min(0, scrollOffset));

        ctx.enableScissor(contentX, contentY, contentX + GUI_W - SIDEBAR_W - 10, contentY + contentH);

        int topPad = 0;
        if (selectedCat == Module.Category.COSMETICS) {
            int cardW = COLS * MODULE_W + (COLS - 1) * 8;
            int cy = contentY + scrollOffset;
            boolean hov = mx >= contentX && mx <= contentX + cardW && my >= cy && my < cy + MODULE_H;
            HudStyle.fillRounded(ctx, contentX, cy, cardW, MODULE_H, 8, hov ? 0xDD3A2A58 : 0xDD2A1A44);
            HudStyle.fillRounded(ctx, contentX, cy, 3, MODULE_H, 1.5f, 0xFF9B6FFF);
            ctx.drawTextWithShadow(textRenderer, "✨ Косметика", contentX + 14, cy + 5, 0xFFEEAAFF);
            ctx.drawTextWithShadow(textRenderer, "§7Меню косметики: плащ, шапка, броня и остальное",
                    contentX + 14, cy + 18, 0xFF9A9AAE);
            ctx.drawTextWithShadow(textRenderer, "→", contentX + cardW - 18, cy + 12, hov ? 0xFFFFFFFF : 0xFF9B6FFF);
            topPad = MODULE_H + 4;
        }

        int col = 0, row = 0;
        for (Module mod : mods) {
            int mx2 = contentX + col * (MODULE_W + 8);
            int my2 = contentY + topPad + row * (MODULE_H + 4) + scrollOffset;
            float a = GUIAnimations.staggerAlpha(row * COLS + col);
            boolean hov = mx >= mx2 && mx <= mx2 + MODULE_W && my >= my2 && my < my2 + MODULE_H;
            HudStyle.fillRounded(ctx, mx2, my2, MODULE_W, MODULE_H, 8, withAlpha(hov ? 0xDD22222E : 0xDD181820, a));

            if (mod.isEnabled()) {
                HudStyle.fillRounded(ctx, mx2, my2, 3, MODULE_H, 1.5f, withAlpha(accentOf(mod), a));
            }

            drawFocusGlow(ctx, mod, mx2, my2, MODULE_W, MODULE_H, 8);

            ctx.drawTextWithShadow(textRenderer, mod.getDisplayName(), mx2 + 14, my2 + 12,
                    withAlpha(mod.isEnabled() ? C_TEXT : C_TEXT_DIM, a));
            if (com.visualmod.util.ConfigMark.configurable(mod)) {
                com.visualmod.util.ConfigMark.draw(ctx, mx2 + MODULE_W - 46, my2 + MODULE_H / 2,
                        withAlpha(0xFFFFFFFF, a));
            }
            if (hov) updateTooltip(mod, mx, my);
            renderToggle(ctx, mx2 + MODULE_W - 32, my2 + MODULE_H / 2 - 6, toggleAnim.getOrDefault(mod, 0f), a);

            col++;
            if (col >= COLS) { col = 0; row++; }
        }
        ctx.disableScissor();
    }

    /** Пульсирующая зелёная рамка вокруг модуля, открытого через ArrayList-чип. */
    private void drawFocusGlow(DrawContext ctx, Module mod, int x, int y, int w, int h, int r) {
        if (focusModule != mod) return;
        long now = System.currentTimeMillis();
        if (now >= focusUntil) { focusModule = null; return; }
        float t = (focusUntil - now) / 3000f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 140.0);
        int a = (int) ((0.5f + 0.35f * pulse) * Math.min(1f, t * 3f) * 255f);
        int green = (a << 24) | 0x00FF88;
        int inner = (Math.min(180, a) << 24) | 0x00552F;
        HudStyle.fillRounded(ctx, x, y, w, h, r, inner);
        HudStyle.fillRounded(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, green);
        HudStyle.fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(2, r - 1), 0x00000000);
    }

    private void renderToggle(DrawContext ctx, int x, int y, float anim, float rowAlpha) {
        int tw = 24, th = 12;
        int bg = withAlpha(lerpC(0xFF2A2A36, C_ACCENT, anim), rowAlpha);
        HudStyle.fillRounded(ctx, x, y, tw, th, 6, bg);
        int ballX = (int)(x + 2 + (tw - th - 2) * anim);
        HudStyle.fillRounded(ctx, ballX, y + 2, th - 4, th - 4, 3, withAlpha(0xFFFFFFFF, rowAlpha));
    }

    private void renderMusicOverlay(DrawContext ctx, int mx, int my) {
        int startY = guiY + 235, pW = SIDEBAR_W - 6, pX = guiX + 3;
        HudStyle.fillRounded(ctx, pX, startY, pW, 80, 8, 0xEE0E0E14);
        String[] info = musicWidgetInfo();
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(info[0], pW - 6),
                pX + pW / 2, startY + 30, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, info[1], pX + pW / 2, startY + 41, C_TEXT_DIM);
        drawMusicButton(ctx, pX + (pW - 52) / 2, startY + 56, 52);
    }

    private String[] musicWidgetInfo() {
        if (MusicOverlay.source != 0) {
            BrowserMedia.Track t = BrowserMedia.track();
            return new String[]{ t == null || t.title.isEmpty() ? "нет трека" : t.title,
                    t == null ? "источник молчит" : t.playing ? "играет" : "пауза" };
        }
        String name = MusicManager.getCurrentName();
        if (name.endsWith(".wav")) name = name.substring(0, name.length() - 4);
        return new String[]{ name.isEmpty() ? "плейлист пуст" : name,
                MusicManager.isPlaying ? "играет" : "пауза" };
    }

    private void drawMusicButton(DrawContext ctx, int x, int y, int w) {
        if (MusicOverlay.source != 0) {
            BrowserMedia.Track t = BrowserMedia.track();
            drawMusicChip(ctx, x, y, w, t != null && t.playing);
        } else {
            drawMusicChip(ctx, x, y, w, MusicManager.isPlaying);
        }
    }

    private void drawMusicChip(DrawContext ctx, int x, int y, int w, boolean playing) {
        int h = 15;
        HudStyle.fillRounded(ctx, x, y, w, h, 5, playing ? 0xE6FF6600 : 0xFF2A2A36);
        ctx.drawCenteredTextWithShadow(textRenderer, playing ? "пауза" : "играть", x + w / 2, y + 4,
                playing ? 0xFF14141C : C_TEXT);
        settingsRegions.add(new ClickRegion(x, y, x + w, y + h, (cmx, cmy) -> {
            if (MusicOverlay.source != 0) BrowserMedia.togglePlayPause();
            else if (MusicManager.isPlaying) MusicManager.stop();
            else MusicManager.play();
        }));
    }

    private void renderSettingsPanel(DrawContext ctx, int mx, int my, Module mod, Configurable cfg) {
        int x = guiX + GUI_W + 10;
        if (x + SETTINGS_PANEL_W > width - 4) x = Math.max(4, guiX + GUI_W - SETTINGS_PANEL_W - 6);
        if (x < 4) x = 4;
        int top = guiY + 6;
        int maxH = Math.max(90, height - top - 8);
        int panelH = Math.min(Math.max(90, settingsPanelH), maxH);
        HudStyle.fillRounded(ctx, x - 4, top, SETTINGS_PANEL_W, panelH, 10, 0xEE16161F);
        if (Soap.onGui()) Soap.overlay(ctx, x - 4, top, SETTINGS_PANEL_W, panelH, 10, openFade);

        int y = top + 8;
        ctx.drawTextWithShadow(textRenderer, "§7" + mod.getDisplayName(), x, y, C_TEXT);
        y = renderBindRow(ctx, x, y + 12, mod, mx, my) + 8;
        int bottom = top + panelH - 16;
        for (Setting s : cfg.getSettings()) {
            if (y > bottom) break;
            if (s instanceof ModeSetting mode) {
                y = renderModeChips(ctx, x, y, SETTINGS_PANEL_W - 12, mode, mx, my, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 6;
            } else if (s instanceof ToggleSetting tg) {
                y = renderToggleRow(ctx, x, y, tg) + 7;
            } else if (s instanceof SliderSetting slider) {
                y = renderSlider(ctx, x, y, SETTINGS_PANEL_W - 12, slider, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 7;
            } else if (s instanceof ColorSetting color) {
                y = renderColorRow(ctx, x, y, color) + 7;
            } else if (s instanceof KeySetting ks) {
                y = renderKeyRow(ctx, x, y, SETTINGS_PANEL_W - 12, ks, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 7;
            }
        }
        settingsPanelH = Math.min(y + 6 - top, maxH);
    }

    /** Акцент модуля: радужный, если у модуля включён rgb, иначе обычный оранжевый. */
    private int accentOf(Module mod) {
        return Rgb.on(mod.getName()) ? Rgb.color() : C_ACCENT;
    }

    /**
     * Кнопка «rgb» перед полем бинда: включает модулю радужную подсветку — все его акценты
     * в GUI и на HUD начинают плыть по кругу.
     */
    private void drawRgbChip(DrawContext ctx, int x, int y, int h, Module mod, int mx, int my) {
        boolean on = Rgb.on(mod.getName());
        boolean hov = mx >= x && mx <= x + RGB_W && my >= y && my <= y + h;
        int col = on ? Rgb.color() : (hov ? 0xDD2A2A38 : 0xFF22222D);
        HudStyle.fillRounded(ctx, x, y, RGB_W, h, 5, col);
        ctx.drawTextWithShadow(textRenderer, "rgb", x + (RGB_W - textRenderer.getWidth("rgb")) / 2,
                y + (h - 9) / 2, on ? 0xFF14141C : (hov ? C_TEXT : C_TEXT_DIM));
        settingsRegions.add(new ClickRegion(x, y, x + RGB_W, y + h, (cmx, cmy) -> Rgb.toggle(mod.getName())));
    }

    private int renderBindRow(DrawContext ctx, int x, int y, Module mod, int mx, int my) {
        int w = SETTINGS_PANEL_W - 12;
        drawRgbChip(ctx, x, y, 18, mod, mx, my);
        int bx = x + RGB_W + 4, bw = w - RGB_W - 4;
        boolean awaiting = awaitingBindFor == mod;
        String key = BindRegistry.getKeyFor(mod.getName());
        String label = awaiting ? "Нажмите..." : key != null ? KeyNames.displayLabel(key) : "нет";
        HudStyle.fillRounded(ctx, bx, y, bw, 18, 5, awaiting ? 0xDD4A3370 : 0xFF22222D);
        ctx.drawTextWithShadow(textRenderer, "Bind", bx + 5, y + 5, C_TEXT_DIM);
        String shown = textRenderer.trimToWidth(label, bw - 42);
        ctx.drawTextWithShadow(textRenderer, shown, bx + bw - textRenderer.getWidth(shown) - 5, y + 5,
                awaiting ? C_ACCENT : C_TEXT);
        settingsRegions.add(new ClickRegion(bx, y, bx + bw, y + 18, (cmx, cmy) -> awaitingBindFor = mod));
        return y + 18;
    }

    /** Ряд настройки-клавиши: клик ждёт нажатие клавиши или кнопки мыши, так же, как ряд бинда. */
    private int renderKeyRow(DrawContext ctx, int x, int y, int w, KeySetting s, int clipTop, int clipBottom) {
        boolean awaiting = awaitingKeyFor == s;
        String label = awaiting ? "Нажмите..." : s.getDisplayValue();
        HudStyle.fillRounded(ctx, x, y, w, 18, 5, awaiting ? 0xDD4A3370 : 0xFF22222D);
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), w - 46), x + 5, y + 5, C_TEXT_DIM);
        String shown = textRenderer.trimToWidth(label, w - 46);
        ctx.drawTextWithShadow(textRenderer, shown, x + w - textRenderer.getWidth(shown) - 5, y + 5,
                awaiting ? C_ACCENT : C_TEXT);
        addRegion(x, y, w, 18, clipTop, clipBottom, (cmx, cmy) -> awaitingKeyFor = s);
        return y + 18;
    }

    private int renderToggleRow(DrawContext ctx, int x, int y, ToggleSetting s) {
        int w = SETTINGS_PANEL_W - 12;
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), w - 52), x + 2, y + 3, C_TEXT_DIM);
        drawToggleButton(ctx, x + w - 44, y, 44, s);
        return y + 14;
    }

    private void drawToggleButton(DrawContext ctx, int x, int y, int w, ToggleSetting s) {
        int h = 14;
        boolean on = s.isEnabled();
        HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, on ? 0xE6FF6600 : 0xFF26262F);
        ctx.drawCenteredTextWithShadow(textRenderer, s.getDisplayValue(), x + w / 2, y + 3,
                on ? 0xFF14141C : C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x, y, x + w, y + h, (cmx, cmy) -> s.toggle()));
    }

    private int renderColorRow(DrawContext ctx, int x, int y, ColorSetting s) {
        int w = SETTINGS_PANEL_W - 12;
        ctx.drawTextWithShadow(textRenderer, s.getName(), x + 2, y, C_TEXT_DIM);
        int chipY = y + 11, chipH = 12;
        int col = s.getColor() | 0xFF000000;
        HudStyle.fillRounded(ctx, x + 2, chipY, w - 4, chipH, 5, col);
        ctx.drawTextWithShadow(textRenderer, s.getDisplayValue(), x + 6, chipY + 2, readableOn(col));
        settingsRegions.add(new ClickRegion(x + 2, chipY, x + w - 2, chipY + chipH,
                (cmx, cmy) -> openColorPicker(s)));
        return chipY + chipH;
    }

    private static int readableOn(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        return (0.299f * r + 0.587f * g + 0.114f * b) > 0.55f ? 0xF0000000 : 0xF0FFFFFF;
    }

    private void openColorPicker(ColorSetting s) {
        ColorPicker.Kind kind = GuiStyle.style == GuiStyle.Style.COLUMN
                ? ColorPicker.Kind.WHEEL : ColorPicker.Kind.GRID;
        colorPicker = new ColorPicker(s, kind);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        if (awaitingKeyFor != null) {
            KeySetting ks = awaitingKeyFor;
            awaitingKeyFor = null;
            if (btn >= 0 && btn <= 2) {
                ks.setKey(KeyNames.fromMouseButton(btn));
                com.visualmod.config.ConfigManager.autoSave();
            }
            return true;
        }
        if (colorPicker != null) {
            if (!colorPicker.mouseClicked(mx, my, btn)) colorPicker = null;
            return true;
        }
        pointerButton = btn;
        for (ClickRegion r : settingsRegions) {
            if (r.contains(mx, my)) { r.action().run(mx, my); return true; }
        }

        if (GuiStyle.style != GuiStyle.Style.COLUMN) {
            int cosY2 = guiY + GUI_H - 36;
            if (btn == 0 && mx >= guiX && mx <= guiX + SIDEBAR_W && my >= cosY2 && my < cosY2 + 28) {
                client.setScreen(new CosmeticsScreen());
                return true;
            }

            int iconY = guiY + 38;
            for (Module.Category c : CATS) {
                if (mx >= guiX && mx <= guiX + SIDEBAR_W && my >= iconY && my < iconY + 28) {
                    selectedCat = c; scrollOffset = 0; return true;
                }
                iconY += 32;
            }

            String[] tabNames = getTabNames();
            int tabX = guiX + SIDEBAR_W + 60;
            int tabY = guiY + 8;
            for (int i = 0; i < tabNames.length; i++) {
                int tw = textRenderer.getWidth(tabNames[i]) + 16;
                if (mx >= tabX && mx <= tabX + tw && my >= tabY && my < tabY + 18) {
                    selectedCat = CATS[i]; scrollOffset = 0; return true;
                }
                tabX += tw + 4;
            }

            List<Module> mods = getFiltered();
            int contentX = guiX + SIDEBAR_W + 10, contentY = guiY + TAB_H + 26;
            if (selectedCat == Module.Category.COSMETICS) {
                int cardW = COLS * MODULE_W + (COLS - 1) * 8;
                int cy = contentY + scrollOffset;
                if (btn == 0 && mx >= contentX && mx <= contentX + cardW && my >= cy && my < cy + MODULE_H) {
                    client.setScreen(new CosmeticsScreen());
                    return true;
                }
            }
            int topPad = selectedCat == Module.Category.COSMETICS ? MODULE_H + 4 : 0;
            int col = 0, row = 0;
            for (Module mod : mods) {
                int mx2 = contentX + col * (MODULE_W + 8);
                int my2 = contentY + topPad + row * (MODULE_H + 4) + scrollOffset;
                if (mx >= mx2 && mx <= mx2 + MODULE_W && my >= my2 && my < my2 + MODULE_H) {
                    if (btn == 0) mod.toggle();
                    if (btn == 1) settingsModule = (settingsModule == mod) ? null : mod;
                    return true;
                }
                col++; if (col >= COLS) { col = 0; row++; }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hA, double vA) {
        if (GuiStyle.style == GuiStyle.Style.COLUMN) {
            Columns c = columns();
            for (int i = 0; i < c.count(); i++) {
                if (!c.contains(i, mx, my)) continue;
                if (i >= CATS.length) {
                    configsScroll = (int) Math.min(0, configsScroll + vA * MOD_ROW_H);
                } else {
                    int cur = columnScrollOffsets.getOrDefault(CATS[i], 0);
                    columnScrollOffsets.put(CATS[i], (int) Math.min(0, cur + vA * MOD_ROW_H));
                }
                return true;
            }
        }
        scrollOffset = (int) Math.min(0, scrollOffset + vA * 16);
        return super.mouseScrolled(mx, my, hA, vA);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x(), my = click.y();
        if (colorPicker != null) return colorPicker.mouseDragged(mx, my);
        if (draggingSlider != null) {
            float frac = (float) ((mx - dragBarX) / dragBarW);
            draggingSlider.setFraction(Math.max(0f, Math.min(1f, frac)));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        if (colorPicker != null) colorPicker.mouseReleased();
        return super.mouseReleased(click);
    }

    private List<Module> getFiltered() {
        List<Module> r = new ArrayList<>();
        for (Module m : VisualsMod.moduleManager.getByCategory(selectedCat)) {
            if (com.visualmod.lang.Lang.matches(searchQuery, m.getName(), m.getDisplayName(), m.getDisplayDescription())) r.add(m);
        }
        return r;
    }

    private static int lerpC(int a, int b, float t) {
        int aA=(a>>24)&0xFF,aR=(a>>16)&0xFF,aG=(a>>8)&0xFF,aB=a&0xFF;
        int bA=(b>>24)&0xFF,bR=(b>>16)&0xFF,bG=(b>>8)&0xFF,bB=b&0xFF;
        return ((int)(aA+(bA-aA)*t)<<24)|((int)(aR+(bR-aR)*t)<<16)|((int)(aG+(bG-aG)*t)<<8)|(int)(aB+(bB-aB)*t);
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return awaitingBindFor == null && awaitingKeyFor == null; }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode();
        if (colorPicker != null && colorPicker.keyPressed(keyCode)) {
            colorPicker = null;
            return true;
        }
        if (awaitingKeyFor != null) {
            KeySetting ks = awaitingKeyFor;
            awaitingKeyFor = null;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ks.setKey(null);
            } else {
                String key = KeyNames.fromKeyCode(keyCode, scanCode);
                if (key == null || KeyNames.RESERVED_INSERT.equals(key)) return true;
                ks.setKey(key);
            }
            com.visualmod.config.ConfigManager.autoSave();
            return true;
        }
        if (awaitingBindFor != null) {
            Module mod = awaitingBindFor;
            awaitingBindFor = null;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                BindRegistry.clearBind(mod.getName());
                return true;
            }
            String key = KeyNames.fromKeyCode(keyCode, scanCode);
            if (key != null && !KeyNames.RESERVED_INSERT.equals(key)) {
                BindRegistry.setBind(mod.getName(), key);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        super.removed();
        com.visualmod.config.ConfigManager.autoSave();
    }
}
