package com.visualmod.screen;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public class FunTimeScreen extends Screen {
    private final Screen parent;
    private int selectedCategory = 0;

    private static final Identifier BG = Identifier.of("visualmod", "textures/gui/bg_hl.png");
    private static final Identifier MINECART = Identifier.of("visualmod", "textures/gui/minecart_chest.png");

    private final List<ButtonWidget> cartButtons = new ArrayList<>();
    private final List<Integer> currentCartIds = new ArrayList<>();

    public FunTimeScreen(Screen parent) {
        super(Text.of("Выбор режима FunTime"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.cartButtons.clear();
        this.currentCartIds.clear();

        int startX = this.width / 2 - 115;
        int categoryY = 40;

        this.addDrawableChild(TextureIconButton.create(startX, categoryY, 40, 40, "totem_1.png", "Команды X1", b -> {
            selectedCategory = 1;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 45, categoryY, 40, 40, "totem_2.png", "Команды X2", b -> {
            selectedCategory = 2;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 90, categoryY, 40, 40, "totem_3.png", "Команды X3", b -> {
            selectedCategory = 3;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 135, categoryY, 40, 40, "totem_5.png", "Команды X5", b -> {
            selectedCategory = 4;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 180, categoryY, 40, 40, "totem_10.png", "Команды X10", b -> {
            selectedCategory = 5;
            this.clearAndInit();
        }));

        if (selectedCategory > 0) {
            if (selectedCategory == 1) addRange(currentCartIds, 101, 121);
            else if (selectedCategory == 2) addRange(currentCartIds, 201, 228);
            else if (selectedCategory == 3) addRange(currentCartIds, 301, 328);
            else if (selectedCategory == 4) addRange(currentCartIds, 501, 521);
            else if (selectedCategory == 5) addRange(currentCartIds, 901, 914);

            int cartStartX = this.width / 2 - 135;
            int cartStartY = 95;
            int columns = 7;

            for (int i = 0; i < currentCartIds.size(); i++) {
                final int cartId = currentCartIds.get(i);
                int row = i / columns;
                int col = i % columns;

                ButtonWidget btn = ButtonWidget.builder(Text.of(""), b -> {
                    HolyWorldScreen.connectAndCommand("play.funtime.su", "/an" + cartId);
                }).dimensions(cartStartX + (col * 38), cartStartY + (row * 24), 34, 20).build();

                this.cartButtons.add(btn);
                this.addDrawableChild(btn);
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.of("Назад"), b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void addRange(List<Integer> list, int start, int end) {
        for (int i = start; i <= end; i++) {
            list.add(i);
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderCustomBackground(context, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, "Выберите тип режима:", this.width / 2, 15, 0xFFFFF5F5);
        super.render(context, mouseX, mouseY, delta);

        if (selectedCategory > 0) {
            for (int i = 0; i < cartButtons.size(); i++) {
                ButtonWidget button = cartButtons.get(i);
                int cartId = currentCartIds.get(i);

                context.drawTexture(RenderPipelines.GUI_TEXTURED, MINECART, button.getX() + 2, button.getY() + 1, 0.0F, 0.0F, 30, 18, 30, 18);

                String idStr = String.valueOf(cartId);
                int textWidth = this.textRenderer.getWidth(idStr);
                context.drawTextWithShadow(this.textRenderer, idStr, button.getX() + 17 - (textWidth / 2), button.getY() + 6, 0xFFFFFFFF);
            }
        }
    }

    private void renderCustomBackground(net.minecraft.client.gui.DrawContext context, float delta) {
        com.visualmod.util.GameBackground.draw(context, BG, this.width, this.height);

        long time = System.currentTimeMillis();
        float angle = (float) ((time % 3000) / 3000.0 * Math.PI * 2.0);

        int centerX = this.width / 2;
        int centerY = this.height - 65;
        int radius = 18;

        context.getMatrices().pushMatrix();

        context.getMatrices().translate(centerX, centerY);

        for (int i = 0; i < 3; i++) {
            float currentAngle = angle + (i * (float) Math.PI * 2.0F / 3.0F);

            int triangleX = (int) (net.minecraft.util.math.MathHelper.cos(currentAngle) * radius);
            int triangleY = (int) (net.minecraft.util.math.MathHelper.sin(currentAngle) * radius);

            int color = 0xFFAA5500;

            context.fill(triangleX - 5, triangleY - 5, triangleX + 5, triangleY + 5, color);
        }

        context.getMatrices().popMatrix();
    }
}
