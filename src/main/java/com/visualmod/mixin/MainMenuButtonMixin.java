package com.visualmod.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.visualmod.screen.AccountScreen;
import com.visualmod.screen.HolyWorldScreen;
import com.visualmod.screen.FunTimeScreen;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import net.minecraft.client.gui.tooltip.Tooltip;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public class MainMenuButtonMixin extends Screen {

    private static final Identifier BG = Identifier.of("visualmod", "textures/gui/bg_hv.png");
    private static final int MAIN_W = 188, MAIN_H = 22, ICON = 24, ICON_GAP = 6;

    private final List<ClickableWidget> customMenuButtons = new ArrayList<>();
    private final List<Icon> buttonIcons = new ArrayList<>();
    private boolean buttonsInitialized = false;

    protected MainMenuButtonMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void visuals$initCustomButtons(CallbackInfo ci) {
        this.clearAndInitCustom();
    }

    private void clearAndInitCustom() {
        this.customMenuButtons.clear();
        this.buttonIcons.clear();
        this.clearChildren();

        int startX = this.width / 2 - MAIN_W / 2;
        int startY = this.height / 2 - 50;

        addMain("Singleplayer", "person", btn -> {
            if (this.client != null) this.client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(this));
        }, startX, startY, false);
        addMain("Multiplayer", "server", btn -> {
            if (this.client != null) this.client.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(this));
        }, startX, startY + MAIN_H + 6, false);
        addMain("Quit", "power", btn -> {
            if (this.client != null) this.client.scheduleStop();
        }, startX, startY + (MAIN_H + 6) * 2, true);

        addIcon("key", Text.of("AltManager"), b -> this.client.setScreen(new AccountScreen(this)));
        addIcon("compass", Text.of("HolyWorld"), b ->
                com.visualmod.screen.HolyWorldScreen.connectAndCommand("msk.holyworld.ru", null));
        addIcon("star", Text.of("Funt1m1"), b -> this.client.setScreen(new FunTimeScreen(this)));
        addIcon("gear", Text.of("Настройки"), b -> this.client.setScreen(
                new net.minecraft.client.gui.screen.option.OptionsScreen(this, this.client.options)));
        if (com.visualmod.util.LauncherBackground.hasAny()) {
            addIcon("eye", Text.of("Смена фона игры"), b -> this.client.setScreen(
                    new com.visualmod.screen.GameBackgroundScreen(this)));
        }

        layoutIcons();
        this.buttonsInitialized = true;
    }

    private void addMain(String label, String icon, java.util.function.Consumer<net.minecraft.client.gui.widget.ButtonWidget> action,
                         int x, int y, boolean danger) {
        ButtonWidget b = ButtonWidget.builder(Text.of(label), action::accept)
                .dimensions(x, y, MAIN_W, MAIN_H).build();
        this.addDrawableChild(b);
        customMenuButtons.add(b);
        buttonIcons.add(new Icon(b, icon, danger));
    }

    /** Маленькая квадратная кнопка без подписи — ряд под главными. */
    private void addIcon(String icon, Text tooltip,
                         java.util.function.Consumer<net.minecraft.client.gui.widget.ButtonWidget> action) {
        ButtonWidget b = ButtonWidget.builder(tooltip, action::accept)
                .dimensions(0, this.height - ICON - 12, ICON, ICON).build();
        b.setTooltip(Tooltip.of(tooltip));
        this.addDrawableChild(b);
        customMenuButtons.add(b);
        buttonIcons.add(new Icon(b, icon, false));
    }

    private void layoutIcons() {
        int n = buttonIcons.size() - 3;
        int total = n * ICON + (n - 1) * ICON_GAP;
        int x = this.width / 2 - total / 2;
        for (int i = 3; i < buttonIcons.size(); i++) {
            ClickableWidget w = buttonIcons.get(i).button;
            w.setX(x + (i - 3) * (ICON + ICON_GAP));
            w.setY(this.height - ICON - 12);
        }
    }

    private record Icon(ClickableWidget button, String glyph, boolean danger) {}

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void visuals$renderMainMenuBackground(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.children().size() > customMenuButtons.size() || !buttonsInitialized) {
            clearAndInitCustom();
        }

        long timeMs = System.currentTimeMillis();

        com.visualmod.util.GameBackground.draw(context, BG, this.width, this.height);
        // Размыть уже нарисованное нельзя — весь GUI уходит во фреймбуфер только после render().
        // Поэтому «мыло» референса собираем тонировкой: плотное затемнение + мягкий верхний градиент.
        context.fill(0, 0, this.width, this.height, 0xB307080C);

        context.fillGradient(0, this.height * 2 / 3, this.width, this.height, 0x00000000, 0x2AFFFFFF);
        context.fillGradient(0, 0, this.width, 56, 0x66000000, 0x00000000);

        for (int i = 0; i < 26; i++) {
            float speed = 12 + (i * 7919 % 25);
            float size = 1.2f + (i * 31 % 10) / 5f;
            long cycle = (long) ((this.height + 80) / speed * 1000);
            long tt = (timeMs + i * 733L) % cycle;
            float ey = this.height + 30 - tt / 1000f * speed;
            float ex = (i * 2654435761L) % Math.max(1, this.width)
                    + (float) Math.sin(tt / 1100.0 + i * 1.7) * (8 + i % 12);
            if (ex < 0) ex += this.width;
            float fade = Math.min(1f, Math.min(tt / 1500f, (this.height + 30 - ey) / 60f));
            float alpha = 0.45f * Math.max(0f, fade) * (0.5f + (i % 5) * 0.12f);
            context.fill((int) ex, (int) ey, (int) (ex + size), (int) (ey + size),
                    HudStyle.pack(1f, 0.55f, 0.12f, alpha));
        }

        String titleMain = "HOLY VISUALS";
        String titleVer = "  1.21.11";
        float scale = 1.6F;
        int fullW = this.textRenderer.getWidth(titleMain) + this.textRenderer.getWidth(titleVer);

        context.getMatrices().pushMatrix();
        float titleX = (this.width / 2.0F) / scale - (fullW / 2.0F);
        float titleY = 24.0F / scale;
        context.getMatrices().scale(scale, scale);
        context.drawTextWithShadow(this.textRenderer, "§l" + titleMain, (int) titleX, (int) titleY, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§5" + titleVer, (int) titleX + this.textRenderer.getWidth(titleMain), (int) titleY, HudStyle.ACCENT);
        context.getMatrices().popMatrix();

        context.drawCenteredTextWithShadow(this.textRenderer, "§7Visual client для HolyWorld",
                this.width / 2, 46, 0xFF8A8A8A);

        float pulse = 0.5f + 0.5f * (float) Math.sin(timeMs * 0.0024);
        for (Icon icon : buttonIcons) {
            ClickableWidget button = icon.button();
            boolean hovered = mouseX >= button.getX() && mouseY >= button.getY()
                    && mouseX < button.getX() + button.getWidth() && mouseY < button.getY() + button.getHeight();

            int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
            float r = w == h ? 8f : HudStyle.pill(h);

            if (hovered) {
                int glow = icon.danger() ? HudStyle.pack(1f, 0.30f, 0.32f, 0.22f)
                        : HudStyle.withAlpha(HudStyle.ACCENT, 0x38);
                HudStyle.fillRounded(context, x - 3, y - 3, w + 6, h + 6, r + 3, glow);
            }

            int base = icon.danger() ? 0xE63A1520 : 0xE6111520;
            // lerpColor выбрасывает альфу, поэтому тонировку возвращаем отдельно.
            int face = hovered ? HudStyle.withAlpha(HudStyle.lerpColor(base, 0xFFFFFFFF, 0.12f), 0xE6) : base;
            HudStyle.fillRounded(context, x, y, w, h, r, face);

            int glyphColor = hovered ? HudStyle.ACCENT : 0xFFB4BAC8;
            int textWidth = button.getWidth() == button.getHeight() ? 0 : this.textRenderer.getWidth(button.getMessage());
            int groupW = textWidth == 0 ? HudIcons.SIZE : HudIcons.SIZE + 5 + textWidth;
            int gx = x + (w - groupW) / 2, gy = y + (h - HudIcons.SIZE) / 2;
            HudIcons.draw(context, icon.glyph(), gx, gy, glyphColor);
            if (textWidth > 0) {
                context.drawTextWithShadow(this.textRenderer, button.getMessage(),
                        gx + HudIcons.SIZE + 5, y + (h - this.textRenderer.fontHeight) / 2,
                        hovered ? 0xFFFFFFFF : 0xFFF2F2F2);
            }
            if (hovered && pulse > 0f) {
                // лёгкая «живая» подсветка нижней кромки, чтобы плашка не была плоской
                HudStyle.fillRounded(context, x + 6, y + h - 1.5f, w - 12, 1.5f, 0.75f,
                        HudStyle.withAlpha(icon.danger() ? 0xFFFF5566 : HudStyle.ACCENT, (int) (0x40 + 0x40 * pulse)));
            }
        }

        ci.cancel();
    }
}
