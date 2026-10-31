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
import com.visualmod.util.HudStyle;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public class MainMenuButtonMixin extends Screen {

    private static final Identifier BG = Identifier.of("visualmod", "textures/gui/bg_hv.png");
    private final List<ClickableWidget> customMenuButtons = new ArrayList<>();
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
        this.clearChildren();

        int btnWidth = 160;
        int btnHeight = 20;
        int startX = this.width / 2 - (btnWidth / 2);

        int startY = this.height / 2 - 50;

        ButtonWidget sp = ButtonWidget.builder(Text.of("Singleplayer"), btn -> {
            if (this.client != null) this.client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(this));
        }).dimensions(startX, startY, btnWidth, btnHeight).build();
        this.addDrawableChild(sp); customMenuButtons.add(sp);
        startY += 24;

        ButtonWidget mp = ButtonWidget.builder(Text.of("Multiplayer"), btn -> {
            if (this.client != null) this.client.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(this));
        }).dimensions(startX, startY, btnWidth, btnHeight).build();
        this.addDrawableChild(mp); customMenuButtons.add(mp);
        startY += 24;

        ButtonWidget accBtn = ButtonWidget.builder(Text.of("AltManager"), b -> this.client.setScreen(new AccountScreen(this)))
                .dimensions(startX, startY, btnWidth, btnHeight).build();
        this.addDrawableChild(accBtn); customMenuButtons.add(accBtn);
        startY += 24;

        ButtonWidget hwBtn = ButtonWidget.builder(Text.of("HolyWorld"), b -> {
            if (this.client != null) {
                com.visualmod.screen.HolyWorldScreen.connectAndCommand("msk.holyworld.ru", null);
            }
        }).dimensions(startX, startY, btnWidth, btnHeight).build();
        this.addDrawableChild(hwBtn); customMenuButtons.add(hwBtn);
        startY += 24;

        ButtonWidget ftBtn = ButtonWidget.builder(Text.of("Funt1m1"), b -> this.client.setScreen(new FunTimeScreen(this)))
                .dimensions(startX, startY, btnWidth, btnHeight).build();
        this.addDrawableChild(ftBtn); customMenuButtons.add(ftBtn);

        int topRightX = this.width - 110;

        ButtonWidget optBtn = ButtonWidget.builder(Text.of("Настройки"), btn -> this.client.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(this, this.client.options)))
                .dimensions(topRightX, 10, 100, btnHeight).build();
        this.addDrawableChild(optBtn); customMenuButtons.add(optBtn);

        ButtonWidget qBtn = ButtonWidget.builder(Text.of("Выйти"), btn -> {
            if (this.client != null) this.client.scheduleStop();
        }).dimensions(topRightX, 34, 100, btnHeight).build();
        this.addDrawableChild(qBtn); customMenuButtons.add(qBtn);

        if (com.visualmod.util.LauncherBackground.hasAny()) {
            ButtonWidget bgBtn = ButtonWidget.builder(Text.of("Смена фона игры"),
                            btn -> this.client.setScreen(new com.visualmod.screen.GameBackgroundScreen(this)))
                    .dimensions(topRightX, 58, 100, btnHeight).build();
            this.addDrawableChild(bgBtn); customMenuButtons.add(bgBtn);
        }

        this.buttonsInitialized = true;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void visuals$renderMainMenuBackground(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.children().size() > customMenuButtons.size() || !buttonsInitialized) {
            clearAndInitCustom();
        }

        long timeMs = System.currentTimeMillis();

        com.visualmod.util.GameBackground.draw(context, BG, this.width, this.height);

        context.fillGradient(0, this.height * 2 / 3, this.width, this.height, 0x00000000, 0x2AFF7A00);
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
        String titleVer = "  1.21.4";
        float scale = 1.6F;
        int fullW = this.textRenderer.getWidth(titleMain) + this.textRenderer.getWidth(titleVer);

        context.getMatrices().pushMatrix();
        float titleX = (this.width / 2.0F) / scale - (fullW / 2.0F);
        float titleY = 24.0F / scale;
        context.getMatrices().scale(scale, scale);
        context.drawTextWithShadow(this.textRenderer, "§l" + titleMain, (int) titleX, (int) titleY, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§6" + titleVer, (int) titleX + this.textRenderer.getWidth(titleMain), (int) titleY, 0xFFFF8800);
        context.getMatrices().popMatrix();

        context.drawCenteredTextWithShadow(this.textRenderer, "§7Visual client для HolyWorld",
                this.width / 2, 46, 0xFF8A8A8A);

        float pulse = 0.5f + 0.5f * (float) Math.sin(timeMs * 0.0024);
        for (ClickableWidget button : customMenuButtons) {
            boolean hovered = mouseX >= button.getX() && mouseY >= button.getY()
                    && mouseX < button.getX() + button.getWidth() && mouseY < button.getY() + button.getHeight();

            float r = 9f;
            int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();

            if (hovered) {
                HudStyle.fillRounded(context, x - 4, y - 4, w + 8, h + 8, r + 4,
                        HudStyle.pack(1f, 0.53f, 0.10f, 0.16f));
                HudStyle.fillRounded(context, x - 2, y - 2, w + 4, h + 4, r + 2,
                        HudStyle.pack(1f, 0.60f, 0.15f, 0.28f));
            }

            HudStyle.glassPanel(context, x, y, w, h, r, hovered ? 0.7f + 0.3f * pulse : pulse * 0.45f);

            int textColor = hovered ? 0xFFFFC27A : 0xFFF2F2F2;
            int textWidth = this.textRenderer.getWidth(button.getMessage());
            context.drawTextWithShadow(this.textRenderer, button.getMessage(),
                    x + (w / 2) - (textWidth / 2), y + (h / 2) - 4, textColor);
        }

        ci.cancel();
    }
}
