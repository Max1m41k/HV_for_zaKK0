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
import com.visualmod.util.HudIcons;
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

    /** Геометрия классического вида (стиль Phaze): шапка, левая колонка конфигов, сетка карточек. */
    private static final int HEADER_H  = 38;
    private static final int SIDE_W    = 150;
    private static final int CFG_ROW_H = 22;
    private static final int CARD_H    = 82;
    private static final int CARD_GAP  = 10;
    private static final int CAT_CHIP_H  = 18;
    private static final int SEARCH_W  = 132;
    private static final int STRIP_H   = 15;

    private static final int PAGE_MODS = 0, PAGE_SETTINGS = 1, PAGE_CONFIGS = 2;
    private static final String[] PAGE_NAMES = {"MODS", "SETTINGS", "CONFIGS"};
    /** Модули, чьи настройки собраны на странице SETTINGS: всё, что касается вида интерфейса. */
    private static final String[] SETTINGS_MODULES = {
            "GuiStyle", "GUIAnimations", "ColumnSettings", "InterfaceHUD", "MusicOverlay", "CustomCursor", "Language"
    };

    private static final int C_BLUE      = 0xFF5C7CFF;
    private static final int C_BLUE_SOFT = 0xFF9DB2FF;
    private static final int C_ON        = 0xFF1FA864;
    private static final int C_OFF       = 0xFFB02048;
    private static final int C_CARD      = 0xB3151822;
    private static final int C_CARD_HOV  = 0xD91D2230;
    private static final int C_LINE      = 0x24FFFFFF;

    private static final int MOD_ROW_H   = 26;
    private static final int COL_HEADER  = 28;
    private static final int COL_FOOTER  = 17;

    /** Категории + последняя колонка с конфигами. */
    private int columnCount() { return CATS.length + 1; }

    /** null — вкладка «ALL»: показывать модули всех категорий. */
    private Module.Category selectedCat = null;
    private int page = PAGE_MODS;
    private int scrollOffset = 0;
    private int configsScroll = 0;
    private int settingsScroll = 0;
    /** Полная высота страницы настроек: известна только после прошлой отрисовки. */
    private int settingsContentH = 0;
    /** Конфиг, помеченный к удалению первым кликом по «×»: второй клик в течение 3 с удаляет файл. */
    private String pendingDelete;
    private long pendingDeleteAt;
    /** Список конфигов читается с диска при открытии GUI, а не каждый кадр. */
    private List<String> configsCache = List.of();
    private final Map<Module.Category, Integer> columnScrollOffsets = new HashMap<>();
    private int guiX, guiY, guiW, guiH;
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

    public ClickGUI() { super(Text.literal("TriadVisual")); }

    /** Панель классического вида подгоняется под экран: на маленьких окнах она не должна вылезать за края. */
    private void layout() {
        guiW = Math.min(width - 16, Math.max(420, Math.min(width - 60, 760)));
        guiH = Math.min(height - 16, Math.max(280, Math.min(height - 44, 440)));
        guiX = (width - guiW) / 2;
        guiY = (height - guiH) / 2;
    }

    @Override
    protected void init() {
        com.visualmod.util.GuiWave.trigger();
        layout();
        searchField = new TextFieldWidget(textRenderer, guiX + SIDE_W + 12, guiY + HEADER_H + 10,
                SEARCH_W, 14, Text.literal(""));
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
        page = PAGE_MODS;
        settingsScroll = 0;
        pendingDelete = null;

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
        layout();
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
            int cx = guiX + guiW / 2, cy = guiY + guiH / 2;
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

        super.render(ctx, mx, my, delta);

        if (colorPicker != null) colorPicker.render(ctx, mx, my, textRenderer, width, height);
    }

    /**
     * Классический вид по референсу Phaze: шапка с вкладками, левая колонка конфигов
     * и сетка карточек модулей. Все клики вешаются на settingsRegions — отдельной ветки
     * hit-тестов в mouseClicked, как раньше, больше нет.
     */
    private void renderStandardView(DrawContext ctx, int mx, int my) {
        HudStyle.fillRounded(ctx, guiX - 1, guiY - 1, guiW + 2, guiH + 2, 15, withAlpha(C_BLUE, 0.5f * openFade));
        HudStyle.fillRounded(ctx, guiX, guiY, guiW, guiH, 14, panelColor());
        if (Soap.onGui()) Soap.overlay(ctx, guiX, guiY, guiW, guiH, 14, openFade);

        renderHeader(ctx, mx, my);
        renderSideColumn(ctx, mx, my);
        searchField.setVisible(page == PAGE_MODS);

        int contentX = guiX + SIDE_W + 12;
        int contentY = guiY + HEADER_H + 12;
        int contentW = guiW - SIDE_W - 24;
        int contentH = guiH - HEADER_H - 24;

        ctx.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        switch (page) {
            case PAGE_SETTINGS -> renderSettingsPage(ctx, contentX, contentY, contentW, contentH, mx, my);
            case PAGE_CONFIGS -> renderConfigsPage(ctx, contentX, contentY, contentW, contentH, mx, my);
            default -> renderModsPage(ctx, contentX, contentY, contentW, contentH, mx, my);
        }
        ctx.disableScissor();
    }

    private void renderHeader(DrawContext ctx, int mx, int my) {
        int lx = guiX + 12, ly = guiY + 10;
        HudStyle.fillRounded(ctx, lx, ly, 18, 18, 6, C_BLUE);
        HudIcons.draw(ctx, "star", lx + 4.5f, ly + 4.5f, 0xFF0B0E18);
        String brand = "Triad", tail = "Visual";
        ctx.drawTextWithShadow(textRenderer, brand, lx + 24, ly + 5, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, tail, lx + 24 + textRenderer.getWidth(brand), ly + 5, C_TEXT_DIM);

        int tabY = guiY + 9, tabH = 20, gap = 8;
        int total = 0;
        for (String p : PAGE_NAMES) total += textRenderer.getWidth(p) + 22;
        total += gap * (PAGE_NAMES.length - 1);
        // Вкладки центрируем над областью контента: на узком окне они не налезают на логотип.
        int tx = guiX + SIDE_W + 12 + Math.max(0, (guiW - SIDE_W - 24 - total) / 2);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            String label = PAGE_NAMES[i];
            int tw = textRenderer.getWidth(label) + 22;
            boolean sel = page == i;
            boolean hov = mx >= tx && mx <= tx + tw && my >= tabY && my <= tabY + tabH;
            HudStyle.fillRounded(ctx, tx, tabY, tw, tabH, 6, sel ? 0xE6F2F4FF : hov ? 0x26FFFFFF : 0x14FFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, label, tx + tw / 2, tabY + 6,
                    sel ? 0xFF12141C : hov ? C_TEXT : C_TEXT_DIM);
            final int p = i;
            settingsRegions.add(new ClickRegion(tx, tabY, tx + tw, tabY + tabH, (cmx, cmy) -> {
                page = p;
                scrollOffset = settingsScroll = configsScroll = 0;
            }));
            tx += tw + gap;
        }
        ctx.fill(guiX + 10, guiY + HEADER_H, guiX + guiW - 10, guiY + HEADER_H + 1, C_LINE);
    }

    /** Левая колонка: быстрые конфиги, виджет плеера и кнопки NEW CONFIG / EDIT HUD LAYOUT. */
    private void renderSideColumn(DrawContext ctx, int mx, int my) {
        int x = guiX + 10, w = SIDE_W - 10;
        int bottom = guiY + guiH - 10;
        int editY = bottom - 18, newY = editY - 4 - 18;
        int musicY = newY - 6 - 30;
        int listTop = guiY + HEADER_H + 26;
        int listBottom = musicY - 6;

        ctx.drawTextWithShadow(textRenderer, "CONFIGS", x + 2, guiY + HEADER_H + 12, C_TEXT_DIM);

        int rowH = CFG_ROW_H - 2;
        int contentH = configsCache.size() * CFG_ROW_H;
        configsScroll = Math.max(Math.min(0, listBottom - listTop - contentH), Math.min(0, configsScroll));
        int curY = listTop + configsScroll;
        for (String name : configsCache) {
            if (curY + rowH > listTop && curY < listBottom) drawConfigRow(ctx, name, x, curY, w, rowH, mx, my);
            curY += CFG_ROW_H;
        }
        ctx.fill(x, listBottom + 2, x + w, listBottom + 3, C_LINE);

        renderMusicWidget(ctx, x, musicY, w, mx, my);
        drawSideButton(ctx, "NEW CONFIG", x, newY, w, 18, false, mx, my, (a, b) -> createConfig());
        drawSideButton(ctx, "EDIT HUD LAYOUT", x, editY, w, 18, true, mx, my, (a, b) -> editHudLayout());
    }

    private void drawConfigRow(DrawContext ctx, String name, int x, int y, int w, int h, int mx, int my) {
        boolean current = name.equals(com.visualmod.config.ConfigManager.currentConfigName);
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + h;
        HudStyle.fillRounded(ctx, x, y, w, h, 6,
                current ? withAlpha(C_BLUE, 0.28f) : hov ? 0xCC222836 : 0xAA1A1E28);
        if (current) HudStyle.fillRounded(ctx, x, y, 3, h, 1.5f, C_BLUE);
        boolean armed = name.equals(pendingDelete) && System.currentTimeMillis() < pendingDeleteAt;
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, w - 22), x + 8, y + 6,
                armed ? 0xFFFF8080 : current ? C_TEXT : hov ? C_TEXT : C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x, y, x + w - 16, y + h, (cmx, cmy) -> loadConfigAndRefresh(name)));

        if (deletable(name) && (hov || armed)) {
            int bx = x + w - 16;
            boolean bxHov = mx >= bx && mx <= bx + 16 && my >= y && my < y + h;
            HudStyle.fillRounded(ctx, bx, y + 3, 13, 13, 4, bxHov ? 0x66FF4D4D : 0x22FFFFFF);
            drawCross(ctx, bx + 2, y + 5, bxHov ? C_TEXT : C_TEXT_DIM);
            settingsRegions.add(new ClickRegion(bx, y, bx + 16, y + h, (cmx, cmy) -> requestDelete(name)));
        }
    }

    /** Текущий конфиг и default не удаляем — иначе можно остаться вообще без настроек. */
    private static boolean deletable(String name) {
        return !name.equals(com.visualmod.config.ConfigManager.currentConfigName) && !name.equals("default");
    }

    /** Крестик «×» из заливок: глиф в дефолтном шрифте передаётся нестабильно. */
    private static void drawCross(DrawContext ctx, int x, int y, int c) {
        for (int i = 0; i < 5; i++) {
            ctx.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            ctx.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, c);
        }
    }

    private void drawSideButton(DrawContext ctx, String label, int x, int y, int w, int h, boolean accent,
                                int mx, int my, RegionAction action) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + h;
        int bg = accent ? (hov ? 0xFF7590FF : C_BLUE) : (hov ? 0xCC262C3C : 0xAA1E222E);
        HudStyle.fillRounded(ctx, x, y, w, h, 6, withAlpha(bg, openFade));
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 9) / 2,
                accent ? 0xFF0B0E18 : hov ? C_TEXT : C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x, y, x + w, y + h, action));
    }

    private void renderMusicWidget(DrawContext ctx, int x, int y, int w, int mx, int my) {
        HudStyle.fillRounded(ctx, x, y, w, 30, 7, 0xAA12161F);
        HudIcons.draw(ctx, "note", x + 5, y + 4, C_BLUE_SOFT);
        String[] info = musicWidgetInfo();
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info[0], w - 22), x + 17, y + 4, C_TEXT);
        int chipY = y + 16, chipH = 11;
        BrowserMedia.Track bt = MusicOverlay.source != 0 ? BrowserMedia.track() : null;
        boolean playing = MusicOverlay.source != 0 ? bt != null && bt.playing : MusicManager.isPlaying;
        boolean hov = mx >= x + 4 && mx <= x + w - 4 && my >= chipY && my <= chipY + chipH;
        HudStyle.fillRounded(ctx, x + 4, chipY, w - 8, chipH, 5,
                playing ? withAlpha(C_BLUE, 0.9f) : hov ? 0xFF2A3040 : 0xFF20242E);
        ctx.drawCenteredTextWithShadow(textRenderer, playing ? "пауза" : "играть", x + w / 2, chipY + 1,
                playing ? 0xFF0B0E18 : C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x + 4, chipY, x + w - 4, chipY + chipH, (cmx, cmy) -> toggleMusic()));
    }

    private void toggleMusic() {
        if (MusicOverlay.source != 0) BrowserMedia.togglePlayPause();
        else if (MusicManager.isPlaying) MusicManager.stop();
        else MusicManager.play();
    }

    private void createConfig() {
        com.visualmod.config.ConfigManager.saveConfig(com.visualmod.config.ConfigManager.freshConfigName());
        configsCache = com.visualmod.config.ConfigManager.listConfigs();
        configsScroll = 0;
    }

    private void loadConfigAndRefresh(String name) {
        com.visualmod.config.ConfigManager.loadConfig(name);
        configsCache = com.visualmod.config.ConfigManager.listConfigs();
    }

    /** «×» работает в два клика: первый помечает конфиг, второй в течение 3 с удаляет файл. */
    private void requestDelete(String name) {
        if (name.equals(pendingDelete) && System.currentTimeMillis() < pendingDeleteAt) {
            com.visualmod.config.ConfigManager.deleteConfig(name);
            configsCache = com.visualmod.config.ConfigManager.listConfigs();
            pendingDelete = null;
        } else {
            pendingDelete = name;
            pendingDeleteAt = System.currentTimeMillis() + 3000L;
        }
    }

    /** Планировка HUD — тот же режим, что в открытом чате: там рисуются плашки и идёт перетаскивание. */
    private void editHudLayout() {
        client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("", false));
    }

    private void renderModsPage(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int chipsBottom = renderCategoryChips(ctx, x, y, w, mx, my);
        int gridY = chipsBottom + 8;
        int gridH = y + h - gridY;
        int clipBottom = y + h;

        List<Module> mods = getFiltered();
        int cols = Math.max(1, Math.min(4, (w + CARD_GAP) / (168 + CARD_GAP)));
        int cardW = (w - (cols - 1) * CARD_GAP) / cols;

        int topPad = 0;
        if (selectedCat == null || selectedCat == Module.Category.COSMETICS) {
            drawCosmeticsCard(ctx, x, gridY, w, mx, my);
            topPad = 26 + CARD_GAP;
        }

        int rows = (mods.size() + cols - 1) / cols;
        int contentH = topPad + Math.max(0, rows * (CARD_H + CARD_GAP) - CARD_GAP);
        scrollOffset = Math.max(Math.min(0, gridH - contentH), Math.min(0, scrollOffset));

        int col = 0, row = 0;
        for (Module mod : mods) {
            int cx = x + col * (cardW + CARD_GAP);
            int cy = gridY + topPad + row * (CARD_H + CARD_GAP) + scrollOffset;
            if (cy + CARD_H > gridY && cy < clipBottom) {
                drawModuleCard(ctx, mod, cx, cy, cardW, mx, my, row * cols + col, gridY, clipBottom);
            }
            col++;
            if (col >= cols) { col = 0; row++; }
        }
    }

    /** @return нижний край ряда чипов — по нему начинается сетка карточек. */
    private int renderCategoryChips(DrawContext ctx, int x, int y, int w, int mx, int my) {
        int chipsW = w - SEARCH_W - 12;
        int cx = x, cy = y;
        cx += drawCatChip(ctx, "ALL", cx, cy, chipsW, selectedCat == null, mx, my, null) + 6;
        for (Module.Category cat : CATS) {
            String label = cat.getDisplayName();
            if (cx + textRenderer.getWidth(label) + 18 > x + chipsW) {
                cx = x;
                cy += CAT_CHIP_H + 5;
            }
            cx += drawCatChip(ctx, label, cx, cy, chipsW, selectedCat == cat, mx, my, cat) + 6;
        }

        int sx = x + w - SEARCH_W;
        HudStyle.fillRounded(ctx, sx, y, SEARCH_W, CAT_CHIP_H, 6, 0xAA12161F);
        drawSearchIcon(ctx, sx + 5, y + 4);
        searchField.setX(sx + 17);
        searchField.setY(y + 4);
        searchField.setWidth(SEARCH_W - 22);
        searchField.setVisible(true);
        return cy + CAT_CHIP_H;
    }

    /** @return ширина чипа — по ней идёт раскладка следующего. */
    private int drawCatChip(DrawContext ctx, String label, int x, int y, int maxW, boolean sel,
                            int mx, int my, Module.Category cat) {
        int cw = Math.min(maxW, textRenderer.getWidth(label) + 18);
        boolean hov = mx >= x && mx <= x + cw && my >= y && my < y + CAT_CHIP_H;
        HudStyle.fillRounded(ctx, x, y, cw, CAT_CHIP_H, 6, sel ? C_BLUE : hov ? 0xCC262C3C : 0xAA1E222E);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + cw / 2, y + 5,
                sel ? 0xFF0B0E18 : hov ? C_TEXT : C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x, y, x + cw, y + CAT_CHIP_H, (cmx, cmy) -> {
            selectedCat = cat;
            scrollOffset = 0;
        }));
        return cw;
    }

    private static void drawSearchIcon(DrawContext ctx, int x, int y) {
        HudStyle.ring(ctx, x + 3.5f, y + 3.5f, 2.2f, 3f, 0f, 360f, C_TEXT_DIM);
        ctx.fill(x + 6, y + 6, x + 7, y + 7, C_TEXT_DIM);
        ctx.fill(x + 7, y + 7, x + 8, y + 8, C_TEXT_DIM);
    }

    private void drawCosmeticsCard(DrawContext ctx, int x, int y, int w, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + 26;
        HudStyle.fillRounded(ctx, x, y, w, 26, 8, hov ? 0xD92A2140 : 0xB3191526);
        HudStyle.fillRounded(ctx, x + 6, y + 6, 14, 14, 5, 0xFF9B6FFF);
        HudIcons.draw(ctx, "star", x + 8.5f, y + 8.5f, 0xFF12141C);
        ctx.drawTextWithShadow(textRenderer, "Косметика", x + 26, y + 9, hov ? C_TEXT : 0xFFCC88FF);
        ctx.drawTextWithShadow(textRenderer, "плащ, шапка, крылья, оружие", x + 96, y + 9, C_TEXT_DIM);
        settingsRegions.add(new ClickRegion(x, y, x + w, y + 26,
                (cmx, cmy) -> client.setScreen(new CosmeticsScreen())));
    }

    private void drawModuleCard(DrawContext ctx, Module mod, int x, int y, int w, int mx, int my,
                                int index, int clipTop, int clipBottom) {
        float a = GUIAnimations.staggerAlpha(index);
        boolean on = mod.isEnabled();
        boolean hov = mx >= x && mx <= x + w && my >= y && my < y + CARD_H;
        int accent = on ? accentOf(mod) : 0xFF3A4152;

        HudStyle.fillRounded(ctx, x - 1, y - 1, w + 2, CARD_H + 2, 11, withAlpha(accent, (hov ? 1f : 0.7f) * a));
        HudStyle.fillRounded(ctx, x, y, w, CARD_H, 10, withAlpha(hov ? C_CARD_HOV : C_CARD, a));
        drawFocusGlow(ctx, mod, x, y, w, CARD_H, 10);

        drawCardIcon(ctx, mod, x + w / 2f, y + 20f, on ? C_TEXT : C_TEXT_DIM);
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(mod.getDisplayName(), w - 12),
                x + w / 2, y + 34, withAlpha(on ? C_TEXT : C_TEXT_DIM, a));

        ctx.fill(x + 1, y + 47, x + w - 1, y + 48, withAlpha(C_LINE, a));
        if (com.visualmod.util.ConfigMark.configurable(mod)) {
            int gx = x + w - 25;
            boolean gearHov = mx >= gx && mx <= gx + 21 && my >= y + 49 && my < y + 64;
            ctx.drawTextWithShadow(textRenderer, "OPTIONS", x + 8, y + 52, withAlpha(on ? C_BLUE_SOFT : C_TEXT_DIM, a));
            ctx.fill(gx - 5, y + 49, gx - 4, y + 64, withAlpha(C_LINE, a));
            HudStyle.fillRounded(ctx, gx, y + 49, 21, 15, 5, withAlpha(gearHov ? 0x3DFFFFFF : 0x14FFFFFF, a));
            HudIcons.draw(ctx, "gear", gx + 6f, y + 52, withAlpha(C_TEXT, a));
            settingsRegions.add(new ClickRegion(gx, y + 49, gx + 21, y + 64,
                    (cmx, cmy) -> settingsModule = settingsModule == mod ? null : mod));
        } else {
            ctx.drawTextWithShadow(textRenderer, "БЕЗ НАСТРОЕК", x + 8, y + 52, withAlpha(C_TEXT_DIM, a));
        }

        int stripY = y + CARD_H - STRIP_H - 3;
        HudStyle.fillRounded(ctx, x + 3, stripY, w - 6, STRIP_H, 6, withAlpha(on ? C_ON : C_OFF, a));
        ctx.drawCenteredTextWithShadow(textRenderer, on ? "ENABLED" : "DISABLED", x + w / 2, stripY + 4,
                withAlpha(0xFFFFFFFF, a));

        if (hov && settingsModule != mod) updateTooltip(mod, mx, my);
        addRegion(x, y, w, CARD_H, clipTop, clipBottom, (cmx, cmy) -> {
            if (pointerButton == 0) mod.toggle();
            else if (pointerButton == 1) settingsModule = settingsModule == mod ? null : mod;
        });
    }

    /** Иконка карточки: глиф HudIcons в 2.1×, а для модулей без глифа — первая буква названия. */
    private void drawCardIcon(DrawContext ctx, Module mod, float cx, float cy, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(2.1f, 2.1f);
        ctx.getMatrices().translate(-cx, -cy);
        String id = HudIcons.forModule(mod.getName());
        if (id != null) {
            HudIcons.draw(ctx, id, cx - HudIcons.SIZE / 2f, cy - HudIcons.SIZE / 2f, color);
        } else {
            String name = mod.getDisplayName();
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            ctx.drawCenteredTextWithShadow(textRenderer, letter, (int) cx, (int) (cy - 4), color);
        }
        ctx.getMatrices().popMatrix();
    }

    /** Страница SETTINGS: настройки интерфейса собраны с нескольких модулей в один список. */
    private void renderSettingsPage(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int bottom = y + h;
        int curY = y + settingsScroll;
        for (String name : SETTINGS_MODULES) {
            Module m = VisualsMod.moduleManager.getModuleByName(name);
            if (!(m instanceof Configurable cfg) || cfg.getSettings().isEmpty()) continue;
            if (curY + 16 > y && curY < bottom) {
                HudStyle.fillRounded(ctx, x, curY, w, 16, 6, 0xAA1E222E);
                drawCardIcon(ctx, m, x + 12, curY + 8, C_BLUE_SOFT);
                ctx.drawTextWithShadow(textRenderer, m.getDisplayName(), x + 24, curY + 4, C_TEXT);
                drawModuleToggleMini(ctx, x + w - 38, curY + 3, m, y, bottom);
            }
            curY += 20;
            for (Setting s : cfg.getSettings()) {
                int rowBottom = renderSettingRow(ctx, x + 6, curY, w - 12, s, mx, my, y, bottom);
                if (rowBottom == curY) continue;
                curY = rowBottom + 5;
            }
            curY += 6;
        }
        settingsContentH = curY - y - settingsScroll;
        settingsScroll = Math.max(Math.min(0, h - settingsContentH), Math.min(0, settingsScroll));
    }

    /** @return нижний край ряда, или переданный y, если для этого типа настройки ряда нет. */
    private int renderSettingRow(DrawContext ctx, int x, int y, int w, Setting s, int mx, int my,
                                 int clipTop, int clipBottom) {
        if (s instanceof ModeSetting mode) return renderModeChips(ctx, x, y, w, mode, mx, my, clipTop, clipBottom);
        if (s instanceof SliderSetting slider) return renderSlider(ctx, x, y, w, slider, clipTop, clipBottom) + 3;
        if (s instanceof ToggleSetting tg) return renderToggleRow(ctx, x, y, w, tg, clipTop, clipBottom);
        if (s instanceof ColorSetting color) return renderColorRow(ctx, x, y, w, color, clipTop, clipBottom);
        if (s instanceof KeySetting ks) return renderKeyRow(ctx, x, y, w, ks, clipTop, clipBottom);
        return y;
    }

    private void drawModuleToggleMini(DrawContext ctx, int x, int y, Module m, int clipTop, int clipBottom) {
        boolean on = m.isEnabled();
        String label = on ? "вкл" : "выкл";
        HudStyle.fillRounded(ctx, x, y, 34, 10, 5, on ? C_ON : 0xFF3A4152);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + 17, y + 1, 0xFFFFFFFF);
        addRegion(x, y, 34, 10, clipTop, clipBottom, (cmx, cmy) -> m.toggle());
    }

    /** Страница CONFIGS: тот же список, что и в левой колонке, но крупнее и с подписями. */
    private void renderConfigsPage(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int rowH = 30, gap = 5;
        int contentH = configsCache.size() * (rowH + gap);
        configsScroll = Math.max(Math.min(0, h - contentH), Math.min(0, configsScroll));
        int curY = y + configsScroll;
        for (String name : configsCache) {
            if (curY + rowH > y && curY < y + h) {
                boolean current = name.equals(com.visualmod.config.ConfigManager.currentConfigName);
                boolean hov = mx >= x && mx <= x + w && my >= curY && my < curY + rowH;
                HudStyle.fillRounded(ctx, x, curY, w, rowH, 8,
                        current ? withAlpha(C_BLUE, 0.24f) : hov ? 0xCC222836 : 0xAA1A1E28);
                if (current) HudStyle.fillRounded(ctx, x, curY, 3, rowH, 1.5f, C_BLUE);
                ctx.drawTextWithShadow(textRenderer, name, x + 12, curY + 5, current ? C_TEXT : C_TEXT_DIM);
                ctx.drawTextWithShadow(textRenderer, current ? "загружен" : "клик — загрузить",
                        x + 12, curY + 17, current ? C_BLUE_SOFT : 0xFF6E7691);
                settingsRegions.add(new ClickRegion(x, curY, x + w - 26, curY + rowH,
                        (cmx, cmy) -> loadConfigAndRefresh(name)));
                if (deletable(name)) {
                    int bx = x + w - 24;
                    boolean bxHov = mx >= bx && mx <= bx + 20 && my >= curY && my < curY + rowH;
                    HudStyle.fillRounded(ctx, bx, curY + 5, 20, 20, 6, bxHov ? 0x66FF4D4D : 0x22FFFFFF);
                    drawCross(ctx, bx + 6, curY + 10, bxHov ? C_TEXT : C_TEXT_DIM);
                    settingsRegions.add(new ClickRegion(bx, curY, bx + 24, curY + rowH,
                            (cmx, cmy) -> requestDelete(name)));
                }
            }
            curY += rowH + gap;
        }
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
                    drawToggleButton(ctx, x + w - chipW - 5, curY + 3, chipW, tg, top, bottom);
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
        int x = guiX + guiW + 10;
        if (x + SETTINGS_PANEL_W > width - 4) x = Math.max(4, guiX + guiW - SETTINGS_PANEL_W - 6);
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
                y = renderToggleRow(ctx, x, y, SETTINGS_PANEL_W - 12, tg, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 7;
            } else if (s instanceof SliderSetting slider) {
                y = renderSlider(ctx, x, y, SETTINGS_PANEL_W - 12, slider, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 7;
            } else if (s instanceof ColorSetting color) {
                y = renderColorRow(ctx, x, y, SETTINGS_PANEL_W - 12, color, NO_CLIP_TOP, NO_CLIP_BOTTOM) + 7;
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

    private int renderToggleRow(DrawContext ctx, int x, int y, int w, ToggleSetting s, int clipTop, int clipBottom) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), w - 52), x + 2, y + 3, C_TEXT_DIM);
        drawToggleButton(ctx, x + w - 44, y, 44, s, clipTop, clipBottom);
        return y + 14;
    }

    private void drawToggleButton(DrawContext ctx, int x, int y, int w, ToggleSetting s, int clipTop, int clipBottom) {
        int h = 14;
        boolean on = s.isEnabled();
        HudStyle.fillRounded(ctx, x, y, w, h, h / 2f, on ? 0xE6FF6600 : 0xFF26262F);
        ctx.drawCenteredTextWithShadow(textRenderer, s.getDisplayValue(), x + w / 2, y + 3,
                on ? 0xFF14141C : C_TEXT_DIM);
        addRegion(x, y, w, h, clipTop, clipBottom, (cmx, cmy) -> s.toggle());
    }

    private int renderColorRow(DrawContext ctx, int x, int y, int w, ColorSetting s, int clipTop, int clipBottom) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(s.getName(), w - 8), x + 2, y, C_TEXT_DIM);
        int chipY = y + 11, chipH = 12;
        int col = s.getColor() | 0xFF000000;
        HudStyle.fillRounded(ctx, x + 2, chipY, w - 4, chipH, 5, col);
        ctx.drawTextWithShadow(textRenderer, s.getDisplayValue(), x + 6, chipY + 2, readableOn(col));
        addRegion(x + 2, chipY, w - 4, chipH, clipTop, clipBottom, (cmx, cmy) -> openColorPicker(s));
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
        } else if (page == PAGE_MODS && mx >= guiX && mx <= guiX + SIDE_W && my > guiY + HEADER_H) {
            configsScroll = (int) Math.min(0, configsScroll + vA * CFG_ROW_H);
            return true;
        } else if (page == PAGE_SETTINGS) {
            settingsScroll = (int) Math.min(0, settingsScroll + vA * 18);
            return true;
        } else if (page == PAGE_CONFIGS) {
            configsScroll = (int) Math.min(0, configsScroll + vA * 35);
            return true;
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
        List<Module> src = selectedCat == null
                ? VisualsMod.moduleManager.getVisibleModules()
                : VisualsMod.moduleManager.getByCategory(selectedCat);
        List<Module> r = new ArrayList<>();
        for (Module m : src) {
            if (com.visualmod.lang.Lang.matches(searchQuery, m.getName(), m.getDisplayName(), m.getDisplayDescription())) r.add(m);
        }
        return r;
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
