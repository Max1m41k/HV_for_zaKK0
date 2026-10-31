package com.visualmod.gui;

import com.visualmod.viewmodel.ViewModelStore;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Locale;

public class ViewModelEditorScreen extends Screen {

    private static final int PANEL_X = 16, PANEL_Y = 28, PANEL_W = 252;
    private static final int HEADER_H = 20, TABS_H = 36, TABS_W_ITEM = 32;
    private static final int SUB_COLS = 5, CHIP_H = 13;
    private static final int ROW_H = 17, LABEL_W = 58, VALUE_W = 48, PAD = 10;
    private static final int BG = 0xE40D1018;
    private static final int ACCENT = 0xFF38A8F5;
    private static final int WHITE = 0xFFEFF6FF;
    private static final int DIM = 0xFF6E8AA3;

    private final ViewModelStore.Kind[] kinds = ViewModelStore.Kind.values();
    private final String[] swordStyles = com.visualmod.module.modules.render.CustomSword.styleIds();
    private int tab = 0;
    /** -1 — общий профиль меча, иначе индекс swordStyles. */
    private int sub = -1;
    private int dragging = -1;
    private ViewModelStore.Kind lastHeld;
    private int lastHeldStyle = -2;
    private boolean opened;
    private String toast = "";
    private long toastUntil = 0;

    public ViewModelEditorScreen() {
        super(Text.literal("ViewModel Editor"));
    }

    @Override
    public void init() {
        super.init();
        if (opened) return;
        opened = true;
        lastHeld = heldKind();
        if (lastHeld != null) tab = lastHeld.ordinal();
        lastHeldStyle = heldStyle();
        if (lastHeld == ViewModelStore.Kind.SWORD && lastHeldStyle >= 0) sub = lastHeldStyle;
    }

    private static final class Row {
        final String label;
        final double min, max, step;
        final boolean deg;
        final String suffix;
        final java.util.function.ToDoubleFunction<ViewModelStore.Profile> get;
        final Setter set;

        Row(String label, double min, double max, double step, boolean deg, String suffix,
            java.util.function.ToDoubleFunction<ViewModelStore.Profile> get, Setter set) {
            this.label = label; this.min = min; this.max = max; this.step = step;
            this.deg = deg; this.suffix = suffix; this.get = get; this.set = set;
        }

        double read(ViewModelStore.Profile p) {
            return Math.max(min, Math.min(max, get.applyAsDouble(p)));
        }

        void write(ViewModelStore.Profile p, double v) {
            double c = Math.round(Math.max(min, Math.min(max, v)) / step) * step;
            set.accept(p, Math.max(min, Math.min(max, c)));
        }

        String format(double v) {
            return deg ? String.format(Locale.ROOT, "%.0f%s", v, suffix)
                       : String.format(Locale.ROOT, "%.3f%s", v, suffix);
        }
    }

    interface Setter { void accept(ViewModelStore.Profile p, double v); }

    private final Row[] rows = {
            new Row("Pos X", -0.5, 0.5, 0.005, false, "", p -> p.x, (p, v) -> p.x = v),
            new Row("Pos Y", -0.5, 0.5, 0.005, false, "", p -> p.y, (p, v) -> p.y = v),
            new Row("Pos Z", -0.5, 0.5, 0.005, false, "", p -> p.z, (p, v) -> p.z = v),
            new Row("Pitch", -180, 180, 1, true, "°", p -> p.pitch, (p, v) -> p.pitch = v),
            new Row("Yaw", -180, 180, 1, true, "°", p -> p.yaw, (p, v) -> p.yaw = v),
            new Row("Roll", -180, 180, 1, true, "°", p -> p.roll, (p, v) -> p.roll = v),
            new Row("Scale", 0.25, 2.5, 0.01, false, "x", p -> p.scale, (p, v) -> p.scale = v),
            new Row("Pivot X", -0.5, 0.5, 0.005, false, "", p -> p.pivotX, (p, v) -> p.pivotX = v),
            new Row("Pivot Y", -0.5, 0.5, 0.005, false, "", p -> p.pivotY, (p, v) -> p.pivotY = v),
            new Row("Pivot Z", -0.5, 0.5, 0.005, false, "", p -> p.pivotZ, (p, v) -> p.pivotZ = v),
    };

    private ViewModelStore.Kind kind() {
        return kinds[Math.max(0, Math.min(kinds.length - 1, tab))];
    }

    /** Ключ профиля, который правится сейчас: стиль меча или базовый для вкладки. */
    private String key() {
        ViewModelStore.Kind k = kind();
        if (k == ViewModelStore.Kind.SWORD && sub >= 0 && sub < swordStyles.length) {
            return ViewModelStore.styleKey(k, swordStyles[sub]);
        }
        return k.key();
    }

    private boolean isStyleProfile() {
        return !key().equals(kind().key());
    }

    private ViewModelStore.Profile profile() {
        return ViewModelStore.profileFor(key());
    }

    private int tabsY() { return PANEL_Y + HEADER_H; }
    private int rowsY() { return tabsY() + TABS_H + subAreaH() + 4; }
    private int buttonsY() { return rowsY() + rows.length * ROW_H + 6; }
    private int panelH() { return buttonsY() + 16 + 46 - PANEL_Y; }
    private int trackX() { return PANEL_X + PAD + LABEL_W; }
    private int trackW() { return PANEL_W - 2 * PAD - LABEL_W - VALUE_W - 6; }
    private int rowTop(int i) { return rowsY() + i * ROW_H; }
    private int tabSlotW() { return (PANEL_W - 2 * PAD) / kinds.length; }
    private int tabX(int i) { return PANEL_X + PAD + i * tabSlotW(); }

    /** Под вкладкой «Меч» — чипы стилей CustomSword: по одному профилю на каждый меч. */
    private boolean subVisible() { return kind() == ViewModelStore.Kind.SWORD; }
    private int subCount() { return 1 + swordStyles.length; }
    private int subRows() { return subVisible() ? (subCount() + SUB_COLS - 1) / SUB_COLS : 0; }
    private int subAreaH() { return subRows() * (CHIP_H + 2); }
    private int subSlotW() { return (PANEL_W - 2 * PAD) / SUB_COLS; }
    private int subX(int i) { return PANEL_X + PAD + (i % SUB_COLS) * subSlotW(); }
    private int subY(int i) { return tabsY() + TABS_H + 2 + (i / SUB_COLS) * (CHIP_H + 2); }
    private String subLabel(int i) {
        if (i == 0) return "Общий";
        String id = swordStyles[i - 1];
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** Зелёная точка на чипе: у стиля есть свои значения, созданные из общего профиля меча. */
    private boolean differsFromCommon(String key) {
        ViewModelStore.Profile a = ViewModelStore.peek(key);
        ViewModelStore.Profile b = ViewModelStore.of(ViewModelStore.Kind.SWORD);
        if (a == null || a == b) return false;
        return a.x != b.x || a.y != b.y || a.z != b.z || a.pitch != b.pitch || a.yaw != b.yaw
                || a.roll != b.roll || a.scale != b.scale
                || a.pivotX != b.pivotX || a.pivotY != b.pivotY || a.pivotZ != b.pivotZ;
    }

    private int hoverSub(double mx, double my) {
        for (int i = 0; i < subCount(); i++) {
            if (mx >= subX(i) && mx <= subX(i) + subSlotW() - 3
                    && my >= subY(i) && my <= subY(i) + CHIP_H) return i;
        }
        return -1;
    }

    private ViewModelStore.Kind heldKind() {
        return client == null || client.player == null ? null : ViewModelStore.match(client.player.getMainHandStack());
    }

    /** Стиль меча в руке, только когда CustomSword включён: он же ключ стилевого профиля. */
    private int heldStyle() {
        String id = com.visualmod.module.modules.render.CustomSword.activeStyleId();
        if (id == null) return -1;
        for (int i = 0; i < swordStyles.length; i++) if (swordStyles[i].equals(id)) return i;
        return -1;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        syncTabWithHand();
        ViewModelStore.Kind held = heldKind();

        com.visualmod.util.HudStyle.fillRounded(ctx, PANEL_X, PANEL_Y, PANEL_W, panelH(), 12, BG);
        String title = kind().label.toUpperCase(Locale.ROOT);
        if (isStyleProfile()) title += " · " + swordStyles[Math.max(0, Math.min(swordStyles.length - 1, sub))]
                .toUpperCase(Locale.ROOT);
        ctx.drawText(textRenderer, "VIEWMODEL · " + title, PANEL_X + PAD, PANEL_Y + 8, ACCENT, false);

        for (int i = 0; i < kinds.length; i++) {
            int x = tabX(i), y = tabsY() + 2, w = tabSlotW() - 4;
            boolean sel = i == tab;
            com.visualmod.util.HudStyle.fillRounded(ctx, x, y, w, TABS_H - 6, 6, sel ? 0xCC16344C : 0x66121A26);
            if (sel) ctx.fill(x, y + TABS_H - 8, x + w, y + TABS_H - 6, ACCENT);
            int ix = x + (w - 20) / 2;
            ctx.drawItem(new ItemStack(kinds[i].icon), ix, y + (TABS_H - 6 - TABS_W_ITEM) / 2 + 6);
            if (held == kinds[i]) ctx.drawText(textRenderer, "в руке", x + 4, y + TABS_H - 20, 0xFF7BE3A1, false);
        }

        if (subVisible()) {
            for (int i = 0; i < subCount(); i++) {
                int x = subX(i), y = subY(i), w = subSlotW() - 3;
                int selIdx = i - 1;
                boolean sel = selIdx == sub;
                boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + CHIP_H;
                int bg = sel ? (hov ? 0xFFA584FF : ACCENT) : (hov ? 0xFF32323F : 0xFF22222D);
                com.visualmod.util.HudStyle.fillRounded(ctx, x, y, w, CHIP_H, 4, bg);
                String label = textRenderer.trimToWidth(subLabel(i), w - 8);
                ctx.drawText(textRenderer, label, x + 4, y + 3, sel ? 0xFF0E1620 : DIM, false);
                String probe = i == 0 ? kind().key() : ViewModelStore.styleKey(ViewModelStore.Kind.SWORD, swordStyles[selIdx]);
                if (i != 0 && differsFromCommon(probe)) ctx.fill(x + w - 4, y + 2, x + w - 2, y + 4, 0xFF7BE3A1);
            }
        }

        ViewModelStore.Profile p = profile();
        int tx = trackX(), tw = trackW();
        int hover = hoverRow(mx, my);
        for (int i = 0; i < rows.length; i++) {
            Row r = rows[i];
            int top = rowTop(i) + 4;
            double v = r.read(p);
            ctx.drawText(textRenderer, r.label, PANEL_X + PAD, top + 2, i == hover ? WHITE : DIM, false);
            com.visualmod.util.HudStyle.fillRounded(ctx, tx, top + 5, tw, 3, 1.5f, 0x33FFFFFF);
            int fillW = (int) (tw * ((v - r.min) / (r.max - r.min)));
            com.visualmod.util.HudStyle.fillRounded(ctx, tx, top + 5, Math.max(1, fillW), 3, 1.5f, ACCENT);
            com.visualmod.util.HudStyle.fillRounded(ctx, tx + fillW - 2, top + 2, 5, 9, 2.5f, 0xFFFFFFFF);
            ctx.drawText(textRenderer, r.format(v), tx + tw + 6, top + 2, WHITE, false);
        }

        drawButton(ctx, "SAVE", PANEL_X + PAD, buttonsY(), 66, mx, my);
        drawButton(ctx, "RESET", PANEL_X + PAD + 72, buttonsY(), 66, mx, my);
        drawButton(ctx, "CLOSE", PANEL_X + PAD + 144, buttonsY(), 66, mx, my);

        ctx.drawText(textRenderer, "Меняется сразу — держи оружие в руке", PANEL_X + PAD, buttonsY() + 24, DIM, false);
        ctx.drawText(textRenderer, "Колесо над строкой — точная подстройка", PANEL_X + PAD, buttonsY() + 34, DIM, false);

        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int w = textRenderer.getWidth(toast) + 14;
            com.visualmod.util.HudStyle.fillRounded(ctx, PANEL_X + PANEL_W + 8, PANEL_Y, w, 18, 6, 0xE60A2030);
            ctx.drawText(textRenderer, toast, PANEL_X + PANEL_W + 15, PANEL_Y + 5, 0xFF7BE3A1, false);
        }
    }

    private void drawButton(DrawContext ctx, String label, int x, int y, int w, int mx, int my) {
        boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + 16;
        com.visualmod.util.HudStyle.fillRounded(ctx, x, y, w, 16, 5, hot ? 0xCC1B4A68 : 0x99121A26);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 4, hot ? WHITE : 0xFFBBD3E6);
    }

    private void syncTabWithHand() {
        ViewModelStore.Kind held = heldKind();
        if (held != lastHeld) {
            lastHeld = held;
            if (held != null) tab = held.ordinal();
        }
        if (kind() != ViewModelStore.Kind.SWORD) return;
        int style = heldStyle();
        if (style == lastHeldStyle) return;
        lastHeldStyle = style;
        if (style >= 0) sub = style;
    }

    private int hoverRow(int mx, int my) {
        if (mx < PANEL_X + PAD || mx > trackX() + trackW() + VALUE_W) return -1;
        for (int i = 0; i < rows.length; i++) if (my >= rowTop(i) && my < rowTop(i) + ROW_H) return i;
        return -1;
    }

    private boolean hitBtn(double mx, double my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + 16;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        for (int i = 0; i < kinds.length; i++) {
            int x = tabX(i), y = tabsY() + 2;
            if (mx >= x && mx <= x + tabSlotW() - 4 && my >= y && my <= y + TABS_H - 6) {
                tab = i;
                return true;
            }
        }
        if (btn == 0) {
            if (hitBtn(mx, my, PANEL_X + PAD, buttonsY(), 66)) { ViewModelStore.save(); say("Сохранено"); return true; }
            if (hitBtn(mx, my, PANEL_X + PAD + 72, buttonsY(), 66)) { ViewModelStore.reset(key()); say("Сброшено"); return true; }
            if (hitBtn(mx, my, PANEL_X + PAD + 144, buttonsY(), 66)) { closeEditor(); return true; }
            if (subVisible()) {
                int hs = hoverSub(mx, my);
                if (hs >= 0) { sub = hs - 1; return true; }
            }
            int hr = hoverRow((int) mx, (int) my);
            if (hr >= 0) { dragging = hr; setFromMouse(rows[hr], mx); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging >= 0) { setFromMouse(rows[dragging], click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int hr = hoverRow((int) mx, (int) my);
        if (hr < 0) return super.mouseScrolled(mx, my, hAmt, vAmt);
        Row r = rows[hr];
        r.write(profile(), r.read(profile()) + vAmt * r.step * 4);
        return true;
    }

    private void setFromMouse(Row r, double mx) {
        double frac = (mx - trackX()) / (double) Math.max(1, trackW());
        r.write(profile(), r.min + Math.max(0, Math.min(1, frac)) * (r.max - r.min));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == 257 || keyCode == 289) { ViewModelStore.save(); say("Сохранено"); return true; }
        return super.keyPressed(input);
    }

    private void say(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 1500;
    }

    private void closeEditor() {
        if (client != null) client.setScreen(null);
    }

    @Override
    public void close() {
        ViewModelStore.save();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {  }
}
