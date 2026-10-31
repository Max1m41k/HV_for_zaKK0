package com.visualmod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AdminLoginScreen extends Screen {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Gson GSON = new Gson();

    private final LockScreen parent;
    private TextFieldWidget emailField, codeField;
    private ButtonWidget sendButton, loginButton;
    private boolean codeStep = false;
    private boolean busy = false;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;

    public AdminLoginScreen(LockScreen parent) {
        super(Text.literal("Admin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;

        emailField = new TextFieldWidget(textRenderer, cx - 100, cy - 30, 200, 20, Text.literal("Email"));
        emailField.setMaxLength(100);
        emailField.setPlaceholder(Text.literal("§8Почта администратора"));
        addDrawableChild(emailField);

        codeField = new TextFieldWidget(textRenderer, cx - 100, cy, 200, 20, Text.literal("Code"));
        codeField.setMaxLength(8);
        codeField.setPlaceholder(Text.literal("§8Код из Telegram (8 цифр)"));
        codeField.setTextPredicate(s -> s.chars().allMatch(Character::isDigit));
        codeField.setVisible(codeStep);
        addDrawableChild(codeField);

        sendButton = ButtonWidget.builder(Text.literal(codeStep ? "Отправить код ещё раз" : "Отправить код"), b -> requestCode())
                .dimensions(cx - 100, cy + 30, 98, 20).build();
        addDrawableChild(sendButton);

        loginButton = ButtonWidget.builder(Text.literal("Войти"), b -> verifyCode())
                .dimensions(cx + 2, cy + 30, 98, 20).build();
        loginButton.active = codeStep;
        addDrawableChild(loginButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
                .dimensions(cx - 40, cy + 58, 80, 20).build());
    }

    private void requestCode() {
        String email = emailField.getText().trim();
        if (busy || email.isEmpty()) return;
        busy = true;
        setStatus("Отправка запроса...", 0xFFAAAAAA);

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        CompletableFuture.runAsync(() -> {
            body.addProperty("hwid", LockScreen.currentHwid());
            post("/admin/request", body, res -> {
                busy = false;
                if (res != null && res.has("ok") && res.get("ok").getAsBoolean()) {
                    codeStep = true;
                    codeField.setVisible(true);
                    loginButton.active = true;
                    sendButton.setMessage(Text.literal("Отправить код ещё раз"));
                    setStatus("Если почта верна, код отправлен в Telegram (действует 5 минут)", 0xFF55FF55);
                } else {
                    setStatus(messageOf(res, "Не удалось отправить запрос"), 0xFFFF5555);
                }
            });
        });
    }

    private void verifyCode() {
        String email = emailField.getText().trim();
        String code = codeField.getText().trim();
        if (busy || email.isEmpty() || code.length() != 8) {
            if (code.length() != 8) setStatus("Код — 8 цифр", 0xFFFF5555);
            return;
        }
        busy = true;
        setStatus("Проверка...", 0xFFAAAAAA);

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("code", code);
        post("/admin/verify", body, res -> {
            busy = false;
            if (res != null && res.has("ok") && res.get("ok").getAsBoolean() && res.has("token")) {
                MinecraftClient.getInstance().setScreen(parent);
                parent.onAdminVerified(res.get("token").getAsString());
            } else {
                codeField.setText("");
                setStatus(messageOf(res, "Неверная почта или код"), 0xFFFF5555);
            }
        });
    }

    static void post(String path, JsonObject body, java.util.function.Consumer<JsonObject> callback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String base = LockScreen.ADMIN_API_URL;
        if (base == null || base.isBlank() || base.contains("YOUR-")) {
            mc.execute(() -> callback.accept(error("Сервер админ-входа не настроен (ADMIN_API_URL в LockScreen.java)")));
            return;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
            JsonObject parsed = null;
            if (err == null) {
                try { parsed = GSON.fromJson(resp.body(), JsonObject.class); } catch (Exception ignored) {}
            } else {
                parsed = error("Ошибка сети. Проверьте подключение.");
            }
            JsonObject result = parsed;
            mc.execute(() -> callback.accept(result));
        });
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("message", message);
        return o;
    }

    private static String messageOf(JsonObject res, String fallback) {
        return res != null && res.has("message") ? res.get("message").getAsString() : fallback;
    }

    private void setStatus(String s, int color) { status = s; statusColor = color; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0A0A0F);
        int cx = width / 2, cy = height / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§5Вход администратора"), cx, cy - 70, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(codeStep ? "§7Введите код, пришедший в Telegram" : "§7Введите почту — код придёт в Telegram"), cx, cy - 52, 0xFFCCCCCC);
        if (!status.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, status, cx, cy + 88, statusColor);
        super.render(ctx, mx, my, delta);
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}
    @Override protected void applyBlur(DrawContext ctx) {}

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
