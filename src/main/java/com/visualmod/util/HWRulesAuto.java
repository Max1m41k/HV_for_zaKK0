package com.visualmod.util;

import com.visualmod.util.HwRulesData.Rule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** Built-in (not in ModuleManager): highlights HolyWorld rule codes in chat and shows a tooltip on hover. */
public final class HWRulesAuto {
    private HWRulesAuto() {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static final int COLOR = 0xFF66FF88;
    private static final int TIP_BG = 0xF00C1218;
    private static final int TIP_BORDER = 0xB066FF88;
    private static final int TIP_CODE = 0xFF66FF88;
    private static final int TIP_PENALTY = 0xFFFF7777;
    private static final int TIP_TITLE = 0xFFFFFFFF;
    private static final int TIP_BODY = 0xFFC9D3E0;

    /** Reset before each ChatHud.render pass; per-message hits are re-collected during that pass. */
    public static void beginFrame() { HITS.clear(); }

    public static void renderHighlight(DrawContext ctx, TextRenderer tr,
                                        String raw, int x, int y, float chatScale) {
        if (raw == null || raw.isEmpty()) return;
        try {
            Matcher m = HwRulesData.matcher(raw);
            while (m.find()) {
                String code = m.group(1);
                if (HwRulesData.byCode(code) == null) continue;
                ctx.drawTextWithShadow(tr, code, x + tr.getWidth(raw.substring(0, m.start(1))), y, COLOR);
                int wx = (int) ((x + tr.getWidth(raw.substring(0, m.start(1)))) * chatScale);
                int wy = (int) (y * chatScale);
                int ww = (int) (tr.getWidth(code) * chatScale);
                int wh = (int) (9 * chatScale);
                HITS.add(new Hit(code, wx, wy, wx + ww, wy + Math.max(wh, 9)));
            }
        } catch (Throwable t) {
            Diag.once("HWRulesAuto.render", t);
        }
    }

    public static void renderTooltip(DrawContext ctx, TextRenderer tr, int mx, int my, int screenW, int screenH) {
        Hit hit = null;
        for (int i = HITS.size() - 1; i >= 0; i--) {
            Hit h = HITS.get(i);
            if (mx >= h.x1 && mx <= h.x2 && my >= h.y1 && my <= h.y2) { hit = h; break; }
        }
        if (hit == null) return;
        Rule r = HwRulesData.byCode(hit.code);
        if (r == null) return;
        try {
            int maxW = Math.min(280, screenW - 12);
            List<String> lines = new ArrayList<>();
            lines.add("§a" + r.code() + " §f" + r.title());
            if (r.penalty() != null && !r.penalty().isEmpty() && !r.penalty().equals("—"))
                lines.add("§c" + r.penalty());
            for (String b : r.body()) lines.addAll(wrap(tr, b, maxW - 12));
            int w = 0;
            for (String s : lines) w = Math.max(w, tr.getWidth(strip(s)));
            w += 12;
            int h = lines.size() * 10 + 10;
            int bx = Math.min(Math.max(2, mx + 6), screenW - w - 2);
            int by = my - h - 4;
            if (by < 2) by = Math.min(my + 14, screenH - h - 2);

            HudStyle.fillRounded(ctx, bx, by, w, h, 5, TIP_BG);
            ctx.drawStrokedRectangle(bx, by, w, h, TIP_BORDER);

            int ty = by + 5;
            ctx.drawTextWithShadow(tr, strip(lines.get(0)), bx + 6, ty, TIP_CODE);
            // overlay the title in white after the code
            String code = r.code();
            int codeW = tr.getWidth(code + " ");
            ctx.drawTextWithShadow(tr, r.title(), bx + 6 + codeW, ty, TIP_TITLE);
            ty += 10;
            int li = 1;
            if (li < lines.size() && lines.get(li).startsWith("§c")) {
                ctx.drawTextWithShadow(tr, strip(lines.get(li)), bx + 6, ty, TIP_PENALTY);
                ty += 10;
                li++;
            }
            for (; li < lines.size(); li++) {
                ctx.drawTextWithShadow(tr, lines.get(li), bx + 6, ty, TIP_BODY);
                ty += 10;
            }
        } catch (Throwable t) {
            Diag.once("HWRulesAuto.tooltip", t);
        }
    }

    private static String strip(String s) {
        int i = s.indexOf('§');
        if (i < 0) return s;
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '§' && k + 1 < s.length()) { k++; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private static List<String> wrap(TextRenderer tr, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (tr.getWidth(test) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            } else {
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add(text);
        return out;
    }

    private static final class Hit {
        final String code;
        final int x1, y1, x2, y2;
        Hit(String code, int x1, int y1, int x2, int y2) {
            this.code = code; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }
}
