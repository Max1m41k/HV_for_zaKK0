package com.visualmod.screen;

import com.visualmod.HolyBadge;
import com.visualmod.lang.Lang;
import com.visualmod.util.ButtonStyle;
import com.visualmod.util.Diag;
import com.visualmod.util.HudStyle;
import com.visualmod.util.NickHeads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AltManager: карточки альтов с головой скина, тёмный арт-фон и мягкое появление окна.
 *
 * Строки и кнопки — виджеты, а не ручные хитбоксы: клик, фокус, Tab-навигация и пробел работают
 * так же, как в остальном интерфейсе игры.
 *
 * Порядок слоёв важен. Картинки и текст через DrawContext копятся в буфере и уходят на экран позже,
 * а HudStyle рисует сразу. Поэтому фон рисуется первым и принудительно сбрасывается
 * ctx.drawDeferredElements(): иначе арт лег бы поверх панели и закрыл её.
 */
public class AccountScreen extends Screen {
    private static final Identifier BG = Identifier.of("visualmod", "textures/gui/bg_hv.png");
    private static final Identifier LOGO = Identifier.of("visualmod", "textures/gui/hw_logo.png");
    private static final int BG_PX = 1376, BG_PY = 768;

    private static final int CARD_W = 258, ROW_H = 30, ROW_GAP = 4, HEAD = 22, PAD = 12;
    private static final int VISIBLE_ROWS = 7;
    private static final int OPEN_MS = 260;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private TextFieldWidget input;
    private String statusMsg = "";
    private int statusColor = 0xFFAAAAAA;
    private static String selectedAccount = null;

    private int panelX, panelY, panelW, panelH, rowsX, rowsTop, rowsBottom;
    private int visibleRows = VISIBLE_ROWS;
    private int scroll;
    private final long openedAt = System.currentTimeMillis();

    public AccountScreen(Screen parent) {
        super(Text.literal("AltManager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // всегда перечитываем диск: иначе после первого открытия экрана изменения (особенно
        // lastNick, записанный из autoRestoreAltSession) не попадают в UI.
        AccountManager.loadAccounts();

        List<String> accounts = AccountManager.accounts;
        // при низком GUI-scale семистрочная панель не влезает: число видимых строк подгоняем под экран
        visibleRows = Math.max(2, Math.min(VISIBLE_ROWS, (height - 170) / (ROW_H + ROW_GAP)));
        panelW = CARD_W + PAD * 2;
        panelX = (width - panelW) / 2;
        panelH = 92 + visibleRows * (ROW_H + ROW_GAP) + 34;
        panelY = Math.max(12, (height - panelH) / 2);
        rowsX = panelX + PAD;
        rowsTop = panelY + 86;
        rowsBottom = rowsTop + visibleRows * (ROW_H + ROW_GAP);

        scroll = Math.max(0, Math.min(scroll, accounts.size() - visibleRows));

        rows.clear();
        buttons.clear();
        for (int i = 0; i < accounts.size(); i++) rows.add(new Row(i, rowsX, accounts.get(i)));

        input = new TextFieldWidget(textRenderer, panelX + PAD, panelY + 56, CARD_W - 66, 20,
                Text.literal("Ник"));
        input.setMaxLength(16);
        input.setPlaceholder(Text.literal(Lang.isRu() ? "ник для добавления" : "nick to add"));
        addDrawableChild(input);

        for (Row row : rows) addDrawableChild(row);
        addButton(new Btn(panelX + panelW - PAD - 54, panelY + 55, 54, 20,
                Lang.isRu() ? "Добавить" : "Add") {
            @Override
            public void onPress() {
                addAccount();
            }
        });
        addButton(new Btn(panelX + PAD, panelY + panelH - 28, 88, 20,
                Lang.isRu() ? "Удалить" : "Delete") {
            @Override
            public void onPress() {
                deleteSelected();
            }
        });
        addButton(new Btn(panelX + panelW - PAD - 88, panelY + panelH - 28, 88, 20,
                Lang.isRu() ? "Назад" : "Back") {
            @Override
            public void onPress() {
                close();
            }
        });
    }

    private void addButton(Btn btn) {
        buttons.add(btn);
        addDrawableChild(btn);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float e = ease(0f);
        int dy = Math.round((1f - e) * 14f);

        drawWallpaper(ctx, e);
        ctx.drawDeferredElements();

        drawPanel(ctx, panelX, panelY + dy, e);
        relayout(dy);
        super.render(ctx, mouseX, mouseY, delta);

        if (AccountManager.accounts.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(Lang.isRu()
                    ? "список пуст — добавьте ник выше" : "empty — add a nick above"),
                    rowsX + 6, rowsTop + dy + 10, alpha(0xFF8A8A96, e));
        }
        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMsg), width / 2,
                    panelY + panelH + 8 + dy, alpha(statusColor, e));
        }
    }

    /** Прогресс появления: 0..1 с ease-out; delay сдвигает элементы друг относительно друга. */
    private float ease(float delay) {
        float t = (System.currentTimeMillis() - openedAt - delay) / (float) OPEN_MS;
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private void relayout(int dy) {
        for (Row row : rows) {
            int y = rowsTop + dy + row.index * (ROW_H + ROW_GAP) - scroll * (ROW_H + ROW_GAP);
            row.setX(rowsX);
            row.setY(y);
            row.visible = y + ROW_H > rowsTop && y < rowsBottom;
            row.fade = ease(90f + row.index * 45f);
        }
        float e = ease(40f);
        for (Btn btn : buttons) btn.fade = e;
    }

    private void drawWallpaper(DrawContext ctx, float e) {
        ctx.fill(0, 0, width, height, alpha(0xFF07060B, e));
        double scale = Math.max(width / (double) BG_PX, height / (double) BG_PY);
        int rw = Math.min(BG_PX, (int) Math.round(width / scale));
        int rh = Math.min(BG_PY, (int) Math.round(height / scale));
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BG, 0, 0, (BG_PX - rw) / 2f, (BG_PY - rh) / 2f,
                width, height, rw, rh, BG_PX, BG_PY);
        ctx.fill(0, 0, width, height, alpha(0xB307060B, e));
    }

    private void drawPanel(DrawContext ctx, int x, int y, float e) {
        float r = 10f;
        HudStyle.fillRounded(ctx, x + 2, y + 3, panelW, panelH, r, alpha(0x66000000, e));
        HudStyle.fillRounded(ctx, x, y, panelW, panelH, r, alpha(0xF00C0A12, e));
        HudStyle.fillRounded(ctx, x, y, panelW, 26, r, alpha(0x33FF7A00, e));
        HudStyle.fillRounded(ctx, x, y + panelH - 32, panelW, 32, r, alpha(0x14FFFFFF, e));
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x + 8, y + 5, 0f, 0f, 16, 16, 32, 32, 32, 32);
        ctx.drawTextWithShadow(textRenderer, Text.literal(
                Lang.isRu() ? "Аккаунты HolyWorld" : "HolyWorld accounts"), x + 30, y + 9,
                alpha(0xFFFFD9B0, e));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("AltManager"), width / 2, y - 24,
                alpha(0xFFFFFFFF, e));
    }

    /** Множитель альфы для ARGB-констант: весь fading считается в цвете, а не через RenderSystem. */
    private static int alpha(int argb, float a) {
        int top = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (Math.min(255, top) << 24) | (argb & 0xFFFFFF);
    }

    /** Строка списка: карточка, голова скина, ник, метка лицензии. */
    private final class Row extends ClickableWidget {
        private final int index;
        private final String nick;
        private float fade = 1f;

        private Row(int index, int x, String nick) {
            super(x, 0, CARD_W - 8, ROW_H, Text.literal(nick));
            this.index = index;
            this.nick = nick;
        }

        @Override
        public void onClick(Click click, boolean doubled) {
            onPress();
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            if (isInteractable() && input.isEnterOrSpace()) {
                playDownSound(MinecraftClient.getInstance().getSoundManager());
                onPress();
                return true;
            }
            return super.keyPressed(input);
        }

        public void onPress() {
            use(nick);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            float e = fade;
            boolean licensed = AccountManager.isLicensed(nick);
            boolean selected = nick.equals(selectedAccount);
            int x = getX(), y = getY(), w = getWidth();

            HudStyle.fillRounded(ctx, x, y, w, ROW_H, 7f, alpha(
                    selected ? 0x33FF7A00 : isHovered() ? 0x26FFFFFF : 0x14FFFFFF, e));
            if (selected) HudStyle.fillRounded(ctx, x, y, 3, ROW_H, 1.5f, alpha(ButtonStyle.ORANGE, e));

            int hx = x + 5, hy = y + (ROW_H - HEAD) / 2;
            HudStyle.fillRounded(ctx, hx - 1, hy - 1, HEAD + 2, HEAD + 2, 6f, alpha(
                    selected ? ButtonStyle.ORANGE : 0x4DFFFFFF, e));
            Identifier skin = NickHeads.head(nick);
            if (skin != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 0f, 0f, HEAD, HEAD, 32, 32, 32, 32);
            } else {
                HudStyle.fillRounded(ctx, hx, hy, HEAD, HEAD, 5f, alpha(0xFF241C2A, e));
                String glyph = nick.substring(0, 1).toUpperCase();
                ctx.drawText(textRenderer, glyph, hx + (HEAD - textRenderer.getWidth(glyph)) / 2, hy + 7,
                        alpha(0xFFB9A7C9, e), false);
            }

            int tx = hx + HEAD + 8;
            ctx.drawTextWithShadow(textRenderer, Text.literal(nick).styled(s -> s.withBold(true)),
                    tx, y + 5, alpha(0xFFFFFFFF, e));
            String tag = licensed ? (Lang.isRu() ? "лицензия" : "licensed")
                    : (Lang.isRu() ? "офлайн" : "offline");
            ctx.drawTextWithShadow(textRenderer, tag, tx, y + 17,
                    alpha(licensed ? 0xFF6BE07A : 0xFF9A9AA6, e));

            if (selected) {
                String now = Lang.isRu() ? "сейчас" : "active";
                ctx.drawTextWithShadow(textRenderer, now, x + w - 6 - textRenderer.getWidth(now), y + 11,
                        alpha(ButtonStyle.ORANGE_LIGHT, e));
            }
        }
    }

    /** Кнопка панели: та же скруглённая плашка, что и в остальном интерфейсе. */
    private abstract static class Btn extends ClickableWidget {
        private float fade = 1f;

        private Btn(int x, int y, int w, int h, String label) {
            super(x, y, w, h, Text.literal(label));
        }

        public abstract void onPress();

        @Override
        public void onClick(Click click, boolean doubled) {
            onPress();
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            if (isInteractable() && input.isEnterOrSpace()) {
                playDownSound(MinecraftClient.getInstance().getSoundManager());
                onPress();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            float e = fade;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            HudStyle.fillRounded(ctx, getX(), getY(), getWidth(), getHeight(), 6f, alpha(
                    isHovered() ? ButtonStyle.ORANGE : 0x2EFFFFFF, e));
            ctx.drawCenteredTextWithShadow(tr, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 9) / 2, alpha(isHovered() ? 0xFF1A1206 : 0xFFE8DCC8, e));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        }
    }

    private void addAccount() {
        try {
            String name = input.getText().trim();
            if (name.isEmpty() || AccountManager.accounts.contains(name)) {
                setStatus(name.isEmpty() ? (Lang.isRu() ? "Введите ник" : "Type a nick")
                        : (Lang.isRu() ? "Уже есть" : "Already there"), 0xFFFF6A6A);
                return;
            }
            // ключ больше не запрашиваем: раз лицензия активна на этом HWID,
            // все добавленные альты считаются licensed и переносят Nametags/треугольник.
            AccountManager.accounts.add(name);
            AccountManager.markLicensed(name);
            AccountManager.saveAccounts();
            input.setText("");
            setStatus((Lang.isRu() ? "Добавлен " : "Added ") + name, 0xFF6BE07A);
            this.clearAndInit();
        } catch (Throwable t) {
            Diag.once("AccountScreen.add", t);
        }
    }

    private void use(String nick) {
        try {
            boolean licensed = AccountManager.isLicensed(nick);
            selectedAccount = nick;
            applySession(sessionFor(nick));
            AccountManager.setLastNick(nick);
            if (licensed) HolyBadge.pingNow();
            setStatus(nick + (licensed
                    ? (Lang.isRu() ? " · лицензия перенесена" : " · license moved")
                    : (Lang.isRu() ? " · переключение" : " · switching")),
                    licensed ? 0xFF6BE07A : ButtonStyle.ORANGE_LIGHT);
            this.clearAndInit();
        } catch (Throwable t) {
            Diag.once("AccountScreen.use", t);
        }
    }

    private void deleteSelected() {
        if (selectedAccount == null) {
            setStatus(Lang.isRu() ? "Сначала выберите ник" : "Pick a nick first", 0xFFFF6A6A);
            return;
        }
        AccountManager.accounts.remove(selectedAccount);
        AccountManager.markUnlicensed(selectedAccount);
        AccountManager.saveAccounts();
        selectedAccount = null;
        this.clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, AccountManager.accounts.size() - visibleRows),
                    scroll - (int) Math.signum(vAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    private void setStatus(String s, int color) {
        statusMsg = s == null ? "" : s;
        statusColor = color;
    }

    /** Применяет ник из AltManager к текущему клиенту; вызывается и при автопереключении на старте. */
    public static void applySession(Session newSession) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Field sessionField = null;
            for (Field field : MinecraftClient.class.getDeclaredFields()) {
                if (field.getType() == Session.class) { sessionField = field; break; }
            }
            if (sessionField != null) {
                sessionField.setAccessible(true);
                sessionField.set(client, newSession);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Session sessionFor(String username) {
        return new Session(username,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()),
                "", Optional.empty(), Optional.empty());
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
