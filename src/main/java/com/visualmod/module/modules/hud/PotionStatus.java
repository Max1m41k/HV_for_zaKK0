package com.visualmod.module.modules.hud;

import net.minecraft.client.gl.RenderPipelines;
import com.visualmod.module.Module;
import com.visualmod.module.settings.ColorSetting;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import com.visualmod.module.settings.ModeSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class PotionStatus extends Module implements Configurable {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int accentColor = 0xFF7B5CF0;
    private HudStyle.FillStyle fillStyle = HudStyle.FillStyle.SOLID;

    private final List<Setting> settings = List.of(
            new ColorSetting("Accent Color", () -> accentColor, c -> accentColor = c),
            new ModeSetting(
                    "Fill Style",
                    Arrays.stream(HudStyle.FillStyle.values()).map(Enum::name).toArray(String[]::new),
                    () -> fillStyle.ordinal(),
                    i -> fillStyle = HudStyle.FillStyle.values()[i]
            )
    );

    public PotionStatus() { super("PotionStatus", "Show active potion effects with their icons", Category.HUD); }

    @Override
    public List<Setting> getSettings() { return settings; }

    /**
     * Ванильные иконки эффектов прячутся, пока модуль включён: свои рисует {@link #render},
     * и два набора рядом выглядит как задвоенный HUD.
     */
    public static boolean hidesVanilla() {
        if (com.visualmod.VisualsMod.moduleManager == null) return false;
        PotionStatus m = com.visualmod.VisualsMod.moduleManager.getModule(PotionStatus.class);
        return m != null && m.isEnabled();
    }

    private record EndedToast(String name, boolean beneficial, long until) {}

    private static final int TOAST_BENEFICIAL_MS = 5000;
    private static final int TOAST_HARMFUL_MS = 2000;
    private final Map<StatusEffect, Boolean> seen = new HashMap<>();
    private final List<EndedToast> toasts = new ArrayList<>();

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) {
            seen.clear();
            return;
        }
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> t.until() <= now);

        Map<StatusEffect, Boolean> current = new HashMap<>();
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            current.put(e.getEffectType().value(), e.getEffectType().value().isBeneficial());
        }
        for (Map.Entry<StatusEffect, Boolean> old : seen.entrySet()) {
            if (current.containsKey(old.getKey())) continue;
            toasts.add(new EndedToast(
                    old.getKey().getName().getString(),
                    old.getValue(),
                    now + (old.getValue() ? TOAST_BENEFICIAL_MS : TOAST_HARMFUL_MS)));
        }
        seen.clear();
        seen.putAll(current);
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null) return;
        var effects = mc.player.getStatusEffects();
        if (!effects.isEmpty()) renderPanel(ctx, x, y, effects);
        renderToasts(ctx);
    }

    private static final int HEAD_H = 13;
    private static final int ICON_SIZE = 10;
    private static final Identifier NO_ICON = Identifier.of("visualmod", "textures/hud/missing.png");
    /** путь эффекта -> его текстура из ванильного атласа mob_effect, либо NO_ICON. */
    private final Map<String, Identifier> iconCache = new HashMap<>();

    private void renderPanel(DrawContext ctx, int x, int y, Iterable<StatusEffectInstance> effects) {
        int rowH = 14;
        int w = 0;
        List<Row> rows = new ArrayList<>();
        for (StatusEffectInstance e : effects) {
            StatusEffect type = e.getEffectType().value();
            String name = type.getName().getString();
            int amp = e.getAmplifier() + 1;
            boolean beneficial = type.isBeneficial();
            String text = name + (amp > 1 ? " " + amp : "") + " §8" + format(e.getDuration());
            rows.add(new Row(text, beneficial ? "↑" : "↓", beneficial ? 0xFF55FFAA : 0xFFFF5555, iconFor(type)));
            w = Math.max(w, mc.textRenderer.getWidth(text) + 30);
        }
        String count = String.valueOf(rows.size());
        int headW = HudIcons.STEP + mc.textRenderer.getWidth(count) + 6;
        w = Math.max(w, Math.max(90, headW));
        int h = HEAD_H + rows.size() * rowH + 8;
        HudStyle.drawPanelRounded(ctx, x, y, w, h, 1f, accentColor, fillStyle);

        HudIcons.draw(ctx, "flask", x + 5, y + 2, accentColor);
        ctx.drawTextWithShadow(mc.textRenderer, count, x + 5 + HudIcons.STEP, y + 2, HudStyle.TEXT);

        int i = 0;
        for (Row row : rows) {
            int ry = y + HEAD_H + 2 + i * rowH;
            HudStyle.fillRounded(ctx, x + 4, ry, ICON_SIZE + 2, ICON_SIZE + 2, 3,
                    (0x55 << 24) | (row.dotColor & 0xFFFFFF));
            if (row.icon == NO_ICON) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, row.arrow, x + 10, ry + 2, row.dotColor);
            } else {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, row.icon, x + 5, ry + 1,
                        0f, 0f, ICON_SIZE, ICON_SIZE, 18, 18, 18, 18);
            }
            ctx.drawTextWithShadow(mc.textRenderer, row.text, x + 20, ry + 1, HudStyle.TEXT);
            i++;
        }
    }

    private record Row(String text, String arrow, int dotColor, Identifier icon) {}

    /** Ванильная иконка эффекта: textures/mob_effect/<имя>.png; NO_ICON — если файла нет. */
    private Identifier iconFor(StatusEffect effect) {
        try {
            Identifier key = Registries.STATUS_EFFECT.getId(effect);
            if (key == null) return NO_ICON;
            String path = key.getNamespace() + "/" + key.getPath();
            Identifier cached = iconCache.get(path);
            if (cached != null) return cached;
            Identifier id = Identifier.of(key.getNamespace(), "textures/mob_effect/" + key.getPath() + ".png");
            Identifier found = mc.getResourceManager().getResource(id).isPresent() ? id : NO_ICON;
            iconCache.put(path, found);
            return found;
        } catch (Throwable t) {
            return NO_ICON;
        }
    }

    private void renderToasts(DrawContext ctx) {
        if (toasts.isEmpty()) return;
        long now = System.currentTimeMillis();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int rowH = 14;
        for (int idx = 0; idx < toasts.size(); idx++) {
            EndedToast t = toasts.get(idx);
            float f = Math.min(1f, Math.max(0f, (t.until() - now) / 500f));
            String text = t.name() + " §7закончился";
            int w = mc.textRenderer.getWidth(text) + 12;
            int x = sw - w - 6;
            int y = sh - 6 - (toasts.size() - idx) * rowH;
            int fg = (int) (255 * f) << 24 | (t.beneficial() ? 0xFF5555 : 0x55FF55);
            int bg = (int) (210 * f) << 24 | 0x0A0A0F;
            HudStyle.fillRounded(ctx, x, y, w, rowH - 2, 4, bg);
            ctx.drawTextWithShadow(mc.textRenderer, text, x + 6, y + 2, fg);
        }
    }

    private static String format(int ticks) {
        int s = Math.max(0, ticks / 20);
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        if (s >= 60) return String.format("%d:%02d", s / 60, s % 60);
        return s + "s";
    }

    public void render(DrawContext ctx) { render(ctx, 10, 200); }
}
