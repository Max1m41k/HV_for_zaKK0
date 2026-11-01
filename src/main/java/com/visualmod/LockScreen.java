package com.visualmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import com.visualmod.util.HudStyle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class LockScreen extends Screen {

    private static final String AUTH_SERVER_URL = "https://visualmod-auth-system2.2011shakurov.workers.dev";

    static final String ADMIN_API_URL = "https://visualmod-admin.2011shakurov.workers.dev";

    private static final File ADMIN_TOKEN_FILE = new File(
            MinecraftClient.getInstance().runDirectory, "visualmod_admin.token");
    private static boolean adminAutoTried = false;
    private boolean adminSession = false;

    private static final File UNLOCK_FILE = new File(
            MinecraftClient.getInstance().runDirectory, "visualmod_license.dat");

    private static boolean sessionUnlocked = false;

    /** Действующий ключ ровно на этот HWID — не зависит от того, под каким аккаунтом играем. */
    private static Boolean hwidOk;

    public static boolean hwidLicensed() {
        if (hwidOk != null) return hwidOk;
        boolean ok = false;
        if (UNLOCK_FILE.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(UNLOCK_FILE))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 2 || parts.length == 3) {
                        long expireTime = Long.parseLong(parts[0]);
                        String savedHWID = parts[1];
                        ok = HWIDManager.getHWID().equals(savedHWID)
                                && (expireTime == -1 || expireTime > System.currentTimeMillis());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        hwidOk = ok;
        return ok;
    }

    public static boolean shouldLock() {
        if (sessionUnlocked) return false;
        if (hwidLicensed()) {
            sessionUnlocked = true;
            return false;
        }
        return true;
    }

    public static String savedKey() {
        try (BufferedReader r = new BufferedReader(new FileReader(UNLOCK_FILE))) {
            String[] p = r.readLine().trim().split(":");
            return p.length == 3 ? p[2] : null;
        } catch (Exception e) { return null; }
    }

    public static boolean isBetaLicense() {
        String k = savedKey();
        return k != null && k.trim().toUpperCase().endsWith("-BETA");
    }

    public static void dropLicense() {
        UNLOCK_FILE.delete();
        hwidOk = false;
    }

    private String pendingKey = "";

    private enum State { IDLE, WRONG, CRACKING, BREAKING, FADE_OUT }
    private State state = State.IDLE;
    private long stateStart = 0;
    private static final long DUR_WRONG = 1000, DUR_CRACKING = 1800, DUR_BREAKING = 1000, DUR_FADEOUT = 800;

    private TextFieldWidget keyField;
    private String errorMsg = "";
    private int shakeX = 0;
    private float[][] shards;
    private float fadeAlpha = 1f;
    private long targetExpirationTime = 0;

    private int[] applyPill, adminPill, fieldRect;
    private int cardX, cardY, cardW = 300, cardH = 216;

    private static final int A_ACCENT = 0xFFFF7A00;
    private static final int A_ACCENT2 = 0xFFFFB347;
    private static final int A_CARD = 0xF2121218;
    private static final int A_LINE = 0x33FFAA55;

    private static final int[][] CRACKS = {{-5,-8,8,-2},{-8,0,3,5},{2,-5,10,2},{-3,3,7,8},{-9,-3,-2,4},{4,1,12,6}};

    public LockScreen() { super(Text.literal("Lock")); }

    @Override
    protected void init() {
        cardX = width / 2 - cardW / 2;
        cardY = height / 2 - cardH / 2 - 8;
        int fx = cardX + 22, fw = cardW - 44;
        fieldRect = new int[]{fx, cardY + 148, fw, 22};
        keyField = new TextFieldWidget(textRenderer, fx + 5, fieldRect[1] + 6, fw - 10, 12, Text.literal("Key"));
        keyField.setMaxLength(36);
        keyField.setDrawsBackground(false);
        keyField.setPlaceholder(Text.literal("§8HOLY-XXXX-XXXX"));
        addDrawableChild(keyField);
        applyPill = new int[]{fx, cardY + 180, 110, 22};
        adminPill = new int[]{fx + 118, cardY + 180, fw - 118, 22};

        setInitialFocus(keyField);

        tryAdminAutoLogin();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int button = click.button();
        if (button == 0 && (state == State.IDLE || state == State.WRONG)) {
            if (inRect(applyPill, mx, my)) { tryUnlockOnline(); return true; }
            if (inRect(adminPill, mx, my)) {
                if (client != null) client.setScreen(new AdminLoginScreen(this));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
            int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if ((keyCode == 257 || keyCode == 335) && (state == State.IDLE || state == State.WRONG)) {
            tryUnlockOnline();
            return true;
        }
        return super.keyPressed(input);
    }

    private static boolean inRect(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    static String currentHwid() { return HWIDManager.getHWID(); }

    private void tryAdminAutoLogin() {
        if (adminAutoTried || !ADMIN_TOKEN_FILE.exists()) return;
        adminAutoTried = true;
        String token;
        try (BufferedReader r = new BufferedReader(new FileReader(ADMIN_TOKEN_FILE))) {
            token = r.readLine();
        } catch (Exception e) { return; }
        if (token == null || token.isBlank()) return;

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("token", token.trim());
        AdminLoginScreen.post("/admin/session", body, res -> {
            if (res != null && res.has("ok") && res.get("ok").getAsBoolean()) {
                onAdminVerified(null);
            } else {
                ADMIN_TOKEN_FILE.delete();
            }
        });
    }

    public void onAdminVerified(String token) {
        if (token != null) {
            try (FileWriter w = new FileWriter(ADMIN_TOKEN_FILE)) { w.write(token); } catch (Exception ignored) {}
        }
        adminSession = true;
        errorMsg = "";
        state = State.CRACKING;
        stateStart = System.currentTimeMillis();
    }

    private void tryUnlockOnline() {
        if (state != State.IDLE && state != State.WRONG) return;

        String inputKey = keyField.getText().trim().toUpperCase();
        if (inputKey.isEmpty()) return;

        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("key", inputKey);
                body.addProperty("hwid", HWIDManager.getHWID());
                body.addProperty("channel", BuildInfo.channel());

                HttpURLConnection conn = (HttpURLConnection) new URL(AUTH_SERVER_URL + "/api/activate").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
                if (conn.getResponseCode() != 200) { setError("Сервер авторизации недоступен"); return; }

                StringBuilder response = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line; while ((line = in.readLine()) != null) response.append(line);
                }
                JsonObject res = JsonParser.parseString(response.toString()).getAsJsonObject();

                if (res.has("success") && res.get("success").getAsBoolean()) {
                    targetExpirationTime = res.has("expires") ? res.get("expires").getAsLong() : System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000;
                    pendingKey = inputKey;
                    state = State.CRACKING;
                    stateStart = System.currentTimeMillis();
                    errorMsg = "";
                } else {
                    setError(res.has("message") ? res.get("message").getAsString() : "Ошибка активации ключа");
                }
            } catch (Exception e) {
                setError("Ошибка сети. Проверь подключение.");
                e.printStackTrace();
            }
        }).start();
    }

    private void setError(String msg) {
        state = State.WRONG;
        stateStart = System.currentTimeMillis();
        errorMsg = msg;
        keyField.setText("");
    }

    private void startBreaking(){
        state = State.BREAKING;
        stateStart = System.currentTimeMillis();
        shards = new float[12][6];
        for(int i = 0; i < shards.length; i++){
            float a = (float)(Math.random() * Math.PI * 2);
            shards[i] = new float[]{0, 0, (float)(Math.cos(a) * (1 + Math.random() * 2)), (float)(Math.sin(a) * (1 + Math.random() * 2)), (float)(4 + Math.random() * 6), (float)(Math.random() * 360)};
        }
    }

    private void finish() {
        if (adminSession) {
            sessionUnlocked = true;
            applyTier();
            MinecraftClient.getInstance().setScreen(new TitleScreen());
            return;
        }
        try {
            if (!UNLOCK_FILE.getParentFile().exists()) {
                UNLOCK_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(UNLOCK_FILE)) {
                String currentHWID = HWIDManager.getHWID();
                writer.write(targetExpirationTime + ":" + currentHWID + (pendingKey.isEmpty() ? "" : ":" + pendingKey));
            }
        } catch (Exception ignored) {}

        hwidOk = null;
        sessionUnlocked = true;
        applyTier();
        MinecraftClient.getInstance().setScreen(new TitleScreen());
    }

    private static void applyTier() {
        if (VisualsMod.moduleManager != null) VisualsMod.moduleManager.applyLicenseTier();
    }

    @Override
    public void tick(){
        super.tick();
        long now = System.currentTimeMillis(), el = now - stateStart;
        switch(state){
            case WRONG -> { shakeX = el < DUR_WRONG ? (int)(Math.sin(el / 55.0 * Math.PI) * 7) : 0; if(el >= DUR_WRONG){ state = State.IDLE; errorMsg = ""; shakeX = 0; } }
            case CRACKING -> { if(el >= DUR_CRACKING) startBreaking(); }
            case BREAKING -> { if(el >= DUR_BREAKING){ state = State.FADE_OUT; stateStart = now; } }
            case FADE_OUT -> { fadeAlpha = 1f - Math.min(1f, el / (float)DUR_FADEOUT); if(el >= DUR_FADEOUT) finish(); }
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        int ga = (int) (fadeAlpha * 255);
        float t = now / 1000f;
        int cx = width / 2;

        ctx.fill(0, 0, width, height, applyA(0xFF07070C, ga));
        glow(ctx, (int) (width * (0.3f + 0.05f * (float) Math.sin(t * 0.21))), (int) (height * 0.32f), 150, applyA(0x22FF7A00, ga));
        glow(ctx, (int) (width * (0.74f + 0.04f * (float) Math.cos(t * 0.17))), (int) (height * 0.74f), 120, applyA(0x222C86C8, ga));
        drawGH(ctx, 0, 0, width, 2, applyA(A_ACCENT, ga), applyA(0xFF2C86C8, ga));
        drawGH(ctx, 0, height - 2, width, height, applyA(0xFF2C86C8, ga), applyA(A_ACCENT, ga));
        int scan = (int) ((t * 26f) % (height + 80)) - 40;
        ctx.fill(0, scan, width, scan + 1, applyA(A_LINE, ga));

        int px = cardX + shakeX, py = cardY;
        HudStyle.fillRounded(ctx, px + 3f, py + 4f, cardW, cardH, 11f, applyA(0x66000000, ga));
        HudStyle.fillRounded(ctx, px - 1f, py - 1f, cardW + 2f, cardH + 2f, 11f, applyA(0x55FF9A40, ga));
        HudStyle.fillRounded(ctx, px, py, cardW, cardH, 10f, applyA(A_CARD, ga));
        gradientBar(ctx, px + 2, py + 2, cardW - 4, 3, A_ACCENT, A_ACCENT2, ga);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§6HOLY §fVISUALS"), cx + shakeX, py + 14, applyA(0xFFFFFFFF, ga));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§8проверка лицензии"), cx + shakeX, py + 27, applyA(0xFFBFBFBF, ga));

        renderLock(ctx, cx + shakeX, py + 74, now, ga);

        if (state == State.IDLE || state == State.WRONG) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Введите ключ доступа"), cx + shakeX, py + 128, applyA(0xFFCCCCCC, ga));
        }
        if (!errorMsg.isEmpty() && state == State.WRONG) {
            float p = (float) (0.6 + 0.4 * Math.sin((now - stateStart) / 120.0 * Math.PI));
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§c✗ " + errorMsg), cx, py + 132, applyA(0xFFFF5A5A, (int) (ga * p)));
        }
        if (state == State.CRACKING) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§a✓ Ключ принят, снимаем блок..."), cx, py + 132, applyA(0xFF66E38A, ga));
        }

        if (fieldRect != null) {
            boolean focused = keyField.isFocused();
            int fx = fieldRect[0], fy = fieldRect[1], fw = fieldRect[2], fh = fieldRect[3];
            HudStyle.fillRounded(ctx, fx - 1f, fy - 1f, fw + 2f, fh + 2f, 7f, applyA(focused ? A_ACCENT : 0x44AAAAAA, ga));
            HudStyle.fillRounded(ctx, fx, fy, fw, fh, 6f, applyA(0xFF0C0C12, ga));
        }

        drawPill(ctx, applyPill, "ПРИМЕНИТЬ", true, mx, my, ga);
        drawPill(ctx, adminPill, "админ", false, mx, my, ga);

        if (state == State.IDLE || state == State.WRONG) super.render(ctx, mx, my, delta);
    }

    private void drawPill(DrawContext ctx, int[] r, String label, boolean primary, int mx, int my, int ga) {
        if (r == null) return;
        boolean hov = inRect(r, mx, my) && (state == State.IDLE || state == State.WRONG);
        int x = r[0] + shakeX;
        if (primary) {
            HudStyle.fillRounded(ctx, x - 1f, r[1] - 1f, r[2] + 2f, r[3] + 2f, 7f, applyA(0x66FF7A00, ga));
            HudStyle.fillRounded(ctx, x, r[1], r[2], r[3], 6f, applyA(hov ? A_ACCENT2 : A_ACCENT, ga));
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + r[2] / 2, r[1] + (r[3] - 8) / 2, applyA(0xFF1A1206, ga));
        } else {
            HudStyle.fillRounded(ctx, x, r[1], r[2], r[3], 6f, applyA(hov ? 0xFF232330 : 0xFF181820, ga));
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + r[2] / 2, r[1] + (r[3] - 8) / 2, applyA(0xFFCCCCCC, ga));
        }
    }

    private static void glow(DrawContext ctx, int cx, int cy, float r, int argb) {
        int a = ((argb >>> 24) & 0xFF) / 4;
        int rgb = argb & 0xFFFFFF;
        for (int i = 5; i >= 0; i--) {
            float k = r * (0.4f + 0.12f * i);
            HudStyle.fillRounded(ctx, cx - k, cy - k * 0.55f, k * 2f, k * 1.1f, k * 0.45f, (a << 24) | rgb);
        }
    }

    private static void gradientBar(DrawContext ctx, int x, int y, int w, int h, int c1, int c2, int ga) {
        int steps = Math.max(1, w / 10);
        for (int i = 0; i < steps; i++) {
            int x1 = x + i * w / steps, x2 = (i + 1 == steps) ? x + w : x + (i + 1) * w / steps;
            ctx.fill(x1, y, x2, y + h, applyA(lerpC(c1, c2, i / (float) Math.max(1, steps - 1)), ga));
        }
    }

    private void renderLock(DrawContext ctx, int cx, int cy, long now, int ga) {
        long el = now - stateStart;
        float crackP = state == State.CRACKING ? Math.min(1f, el / (float) DUR_CRACKING) : 0f;
        float breakP = state == State.BREAKING ? Math.min(1f, el / (float) DUR_BREAKING) : 0f;
        float idle = (float) (0.5 + 0.5 * Math.sin(now / 700.0));

        int lc;
        if (state == State.WRONG) lc = applyA(lerpC(A_ACCENT, 0xFFFF4444, (float) (0.5 + 0.5 * Math.sin(el / 90.0))), ga);
        else if (state == State.CRACKING) lc = applyA(lerpC(A_ACCENT, 0xFF66E38A, crackP), ga);
        else lc = applyA(A_ACCENT, ga);

        if (state == State.BREAKING) {
            if (shards == null) return;
            int a = (int) ((1 - breakP) * ga);
            for (float[] s : shards) {
                float d = breakP * 50;
                int sx = (int) (cx + s[0] + s[2] * d), sy = (int) (cy + 14 + s[1] + s[3] * d);
                int sz = Math.max(1, (int) (s[4] * (1 - breakP)));
                HudStyle.fillRounded(ctx, sx - sz, sy - sz, sz * 2f, sz * 2f, sz * 0.6f, applyA(A_ACCENT2, a));
            }
            return;
        }

        glow(ctx, cx, cy + 8, 26, applyA(0x18FF7A00, (int) (ga * (0.5f + 0.5f * idle))));
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6.0 - 90);
            int dotX = (int) (cx + Math.cos(a) * 25), dotY = (int) (cy + 8 + Math.sin(a) * 25);
            boolean on = state == State.CRACKING && i / 60f <= crackP;
            ctx.fill(dotX, dotY, dotX + 1, dotY + 1, on ? applyA(A_ACCENT2, ga) : applyA(0x22FFFFFF, ga));
        }

        int shk = state == State.CRACKING ? (int) (Math.sin(now / 60.0) * crackP * 2) : 0;
        int aw = 22, ax = cx - aw / 2 + shk, ay = cy - 22;
        HudStyle.fillRounded(ctx, ax, ay + 8, 4, 16, 2f, lc);
        HudStyle.fillRounded(ctx, ax + aw - 4, ay + 8, 4, 16, 2f, lc);
        HudStyle.fillRounded(ctx, ax + 2, ay, aw - 4, 10, 5f, lc);
        HudStyle.fillRounded(ctx, ax + 6, ay + 4, aw - 12, 8, 4f, applyA(0xFF07070C, ga));

        int bw = 38, bh = 28, bx = cx - bw / 2 + shk, by = cy - 2;
        HudStyle.fillRounded(ctx, bx, by, bw, bh, 6f, applyA(lerpC(0xFF1B1B26, 0xFF0D2010, crackP), ga));
        HudStyle.fillRounded(ctx, bx, by, bw, 3, 2f, applyA(lerpC(A_ACCENT2, 0xFF66E38A, crackP), (int) (ga * 0.6f)));

        int kx = cx + shk, ky = by + 9;
        HudStyle.fillRounded(ctx, kx - 3, ky, 6, 6, 3f, applyA(0xFF050508, ga));
        HudStyle.fillRounded(ctx, kx - 1, ky + 5, 2, 7, 1f, applyA(0xFF050508, ga));

        if (state == State.CRACKING && crackP > 0.1f) {
            int ca = (int) (Math.min(1f, (crackP - 0.1f) / 0.5f) * 200);
            int nc = (int) (CRACKS.length * crackP);
            for (int i = 0; i < nc && i < CRACKS.length; i++) {
                int[] c = CRACKS[i];
                ctx.fill(bx + bw / 2 + c[0] + shk, by + bh / 2 + c[1], bx + bw / 2 + c[2] + shk, by + bh / 2 + c[3], applyA(A_ACCENT2, ca));
            }
        }
    }

    @Override public boolean shouldCloseOnEsc(){ return false; }
    @Override public boolean shouldPause(){ return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d){}
    @Override protected void applyBlur(DrawContext ctx){}

    private static int applyA(int color, int alpha){ int o = (color >> 24) & 0xFF; return(color & 0x00FFFFFF) | ((o * alpha / 255) << 24); }
    private static int lerpC(int a, int b, float t){ int aA=(a>>24)&0xFF, aR=(a>>16)&0xFF, aG=(a>>8)&0xFF, aB=a&0xFF, bA=(b>>24)&0xFF, bR=(b>>16)&0xFF, bG=(b>>8)&0xFF, bB=b&0xFF; return((int)(aA+(bA-aA)*t)<<24)|((int)(aR+(bR-aR)*t)<<16)|((int)(aG+(bG-aG)*t)<<8)|(int)(aB+(bB-aB)*t); }
    private static void drawGH(DrawContext ctx, int x1, int y1, int x2, int y2, int cL, int cR){ int s=20, w=x2-x1; for(int i=0; i<s; i++) ctx.fill(x1+i*w/s, y1, x1+(i+1)*w/s, y2, lerpC(cL, cR, i/(float)(s-1))); }
}
