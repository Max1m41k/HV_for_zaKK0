package com.visualmod.screen;

import net.minecraft.client.gl.RenderPipelines;
import com.visualmod.module.modules.hud.HolyAnarchyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;

public class HolyWorldScreen extends Screen {
    private final Screen parent;
    private int selectedCategory = 0;
    public static String commandToSend = null;

    private static final Identifier BG = Identifier.of("visualmod", "textures/gui/bg_tv.png");
    private static final Identifier BLUE_CUBE = Identifier.of("visualmod", "textures/gui/blue_cube.png");

    private final List<ButtonWidget> anarchyButtons = new ArrayList<>();

    public HolyWorldScreen(Screen parent) {
        super(Text.of("Выбор Лайт анархии"));
        this.parent = parent;
        HolyAnarchyManager.load();
    }

    @Override
    protected void init() {
        this.anarchyButtons.clear();
        int startX = this.width / 2 - 100;
        int categoryY = 40;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Очистить все"), btn -> {
            HolyAnarchyManager.data.solo = 0;
            HolyAnarchyManager.data.duo = 0;
            HolyAnarchyManager.data.trio = 0;
            HolyAnarchyManager.data.clan = 0;
            HolyAnarchyManager.save();
            selectedCategory = 0;
            this.clearAndInit();
        }).dimensions(this.width - 110, 10, 100, 20).build());

        this.addDrawableChild(TextureIconButton.create(startX, categoryY, 40, 40, "armor_stand_1.png", "СолоЛайт анархии", b -> {
            selectedCategory = 1;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 45, categoryY, 40, 40, "armor_stand_2.png", "ДуоЛайт анархии", b -> {
            selectedCategory = 2;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 90, categoryY, 40, 40, "armor_stand_3.png", "ТриоЛайт анархии", b -> {
            selectedCategory = 3;
            this.clearAndInit();
        }));
        this.addDrawableChild(TextureIconButton.create(startX + 135, categoryY, 40, 40, "armor_stand_16.png", "КланЛайт анархии", b -> {
            selectedCategory = 4;
            this.clearAndInit();
        }));

        if (selectedCategory > 0) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> {
                if (selectedCategory == 1) HolyAnarchyManager.data.solo++;
                else if (selectedCategory == 2) HolyAnarchyManager.data.duo++;
                else if (selectedCategory == 3) HolyAnarchyManager.data.trio++;
                else if (selectedCategory == 4) HolyAnarchyManager.data.clan++;

                HolyAnarchyManager.save();
                this.clearAndInit();
            }).dimensions(startX + 180, categoryY + 2, 20, 16).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), btn -> {
                if (selectedCategory == 1 && HolyAnarchyManager.data.solo > 0) HolyAnarchyManager.data.solo--;
                else if (selectedCategory == 2 && HolyAnarchyManager.data.duo > 0) HolyAnarchyManager.data.duo--;
                else if (selectedCategory == 3 && HolyAnarchyManager.data.trio > 0) HolyAnarchyManager.data.trio--;
                else if (selectedCategory == 4 && HolyAnarchyManager.data.clan > 0) HolyAnarchyManager.data.clan--;

                HolyAnarchyManager.save();
                this.clearAndInit();
            }).dimensions(startX + 180, categoryY + 22, 20, 16).build());

            int count = 0;
            String categoryKey = "SOLO";

            if (selectedCategory == 1) {
                count = HolyAnarchyManager.data.solo;
                categoryKey = "SOLO";
            } else if (selectedCategory == 2) {
                count = HolyAnarchyManager.data.duo;
                categoryKey = "DUO";
            } else if (selectedCategory == 3) {
                count = HolyAnarchyManager.data.trio;
                categoryKey = "TRIO";
            } else if (selectedCategory == 4) {
                count = HolyAnarchyManager.data.clan;
                categoryKey = "CLAN";
            }

            int gridStartX = this.width / 2 - 120;
            int gridStartY = 95;
            int columns = 8;

            for (int i = 0; i < count; i++) {
                final int indexInsideCategory = i + 1;
                final String currentCat = categoryKey;

                int row = i / columns;
                int col = i % columns;

                ButtonWidget btn = ButtonWidget.builder(Text.of(""), b -> {
                    int globalAnarchyId = HolyAnarchyManager.getGlobalId(currentCat, indexInsideCategory);
                    connectAndCommand("msk.holyworld.ru", "/an " + globalAnarchyId);
                }).dimensions(gridStartX + (col * 30), gridStartY + (row * 26), 26, 22).build();

                this.anarchyButtons.add(btn);
                this.addDrawableChild(btn);
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.of("Назад"), b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    public static void connectAndCommand(String ip, String cmd) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo serverInfo = new ServerInfo("Server", ip, ServerInfo.ServerType.OTHER);
        commandToSend = cmd;
        ConnectScreen.connect(client.currentScreen, client, net.minecraft.client.network.ServerAddress.parse(ip), serverInfo, false, null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderCustomBackground(context, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, "Выбор Лайт анархии:", this.width / 2, 15, 0xFFFFF5F5);
        super.render(context, mouseX, mouseY, delta);

        if (selectedCategory > 0) {
            for (int i = 0; i < anarchyButtons.size(); i++) {
                ButtonWidget button = anarchyButtons.get(i);
                int currentDisplayNumber = i + 1;

                context.drawTexture(RenderPipelines.GUI_TEXTURED, BLUE_CUBE, button.getX() + 2, button.getY() + 1, 0.0F, 0.0F, 22, 20, 22, 20);

                String numStr = String.valueOf(currentDisplayNumber);
                int textWidth = this.textRenderer.getWidth(numStr);
                context.drawTextWithShadow(this.textRenderer, numStr, button.getX() + 13 - (textWidth / 2), button.getY() + 7, 0xFFFFFFFF);
            }
        }
    }

    private void renderCustomBackground(DrawContext context, float delta) {
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

            int triangleX = (int) (MathHelper.cos(currentAngle) * radius);
            int triangleY = (int) (MathHelper.sin(currentAngle) * radius);

            int color = 0xFFAA5500;
            context.fill(triangleX - 5, triangleY - 5, triangleX + 5, triangleY + 5, color);
        }

        context.getMatrices().popMatrix();
    }
}
