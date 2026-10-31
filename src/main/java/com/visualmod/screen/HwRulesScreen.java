package com.visualmod.screen;

import com.visualmod.util.HudStyle;
import com.visualmod.util.HwRulesData;
import com.visualmod.util.HwRulesData.Rule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Click;

/** Scrollable HolyWorld rules table: six sections on the left, expandable rule rows on the right. */
public class HwRulesScreen extends Screen {
    private final Screen parent;

    private int selectedSection = 0;
    private final Set<String> expanded = new HashSet<>();
    private double scroll = 0;

    private int panelX, panelY, panelW, panelH;
    private int navX, navW, listX, listW;
    private int navItemH = 22;

    public HwRulesScreen(Screen parent) {
        super(Text.literal("Правила HolyWorld"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(520, width - 20);
        panelH = Math.min(300, height - 30);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        navW = 140;
        navX = panelX + 8;
        listX = panelX + navW + 20;
        listW = panelW - navW - 30;
        navItemH = 22;
        scroll = 0;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        List<Rule> rules = rulesFor(selectedSection);
        double contentH = listContentHeight(rules);
        double maxScroll = Math.max(0, contentH - (panelH - 40));
        scroll = Math.max(0, Math.min(maxScroll, scroll - vertical * 18));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xAA000000);
        HudStyle.fillRounded(ctx, panelX, panelY, panelW, panelH, 10, 0xF20B0B12);
        ctx.drawTextWithShadow(textRenderer, "§l§6Правила HolyWorld", panelX + 10, panelY + 8, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Лайт Анархия · ред. 13 апр 2025",
                panelX + panelW - textRenderer.getWidth("§7Лайт Анархия · ред. 13 апр 2025") - 10,
                panelY + 9, 0xFFFFFFFF);
        ctx.fill(panelX + 8, panelY + 22, panelX + panelW - 8, panelY + 23, 0x33FFFFFF);

        drawNav(ctx, mx, my);
        drawList(ctx, mx, my);
    }

    private void drawNav(DrawContext ctx, int mx, int my) {
        List<String> sections = HwRulesData.sections();
        int y = panelY + 30;
        for (int i = 0; i < sections.size(); i++) {
            String name = sections.get(i);
            boolean active = i == selectedSection;
            boolean hov = mx >= navX && mx <= navX + navW && my >= y && my < y + navItemH;
            int bg = active ? 0xE0FF8800 : hov ? 0x33FFFFFF : 0x14FFFFFF;
            int fg = active ? 0xFF14141C : HudStyle.TEXT;
            HudStyle.fillRounded(ctx, navX, y, navW, navItemH, 5, bg);
            String short_ = shortenSection(name);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(short_, navW - 16), navX + 8, y + 8, fg);
            y += navItemH + 3;
        }
        int count = 0;
        for (Rule r : HwRulesData.all()) if (sectionIndex(r.section()) == selectedSection) count++;
        ctx.drawTextWithShadow(textRenderer, "§7Всего в разделе: §f" + count, navX + 4, panelY + panelH - 14, 0xFFFFFFFF);
    }

    private void drawList(DrawContext ctx, int mx, int my) {
        List<Rule> rules = rulesFor(selectedSection);
        int top = panelY + 28;
        int bottom = panelY + panelH - 8;
        ctx.enableScissor(listX, top, listX + listW, bottom);

        int y = top - (int) scroll;
        if (rules.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "§7Нет правил в этом разделе", listX + 4, y + 6, 0xFFFFFFFF);
        }
        for (Rule r : rules) {
            int rowH = rowHeight(r);
            boolean hov = mx >= listX && mx <= listX + listW && my >= y && my < y + rowH;
            if (hov) HudStyle.fillRounded(ctx, listX, y, listW, rowH, 4, 0x22FFFFFF);
            drawRow(ctx, r, listX + 4, y, listW - 8, hov);
            y += rowH + 2;
        }

        ctx.disableScissor();
    }

    private void drawRow(DrawContext ctx, Rule r, int x, int y, int w, boolean hov) {
        boolean isExpanded = expanded.contains(r.code());
        String marker = isExpanded ? "§e▼ " : "§7▶ ";
        String code = "§6" + r.code();
        String title = "§f" + r.title();
        ctx.drawTextWithShadow(textRenderer, marker + code + " " + textRenderer.trimToWidth(title, w - 60), x, y + 4, 0xFFFFFFFF);

        int pw = textRenderer.getWidth(shortPenalty(r.penalty()));
        ctx.drawTextWithShadow(textRenderer, "§c" + shortPenalty(r.penalty()), x + w - pw, y + 4, 0xFFFFFFFF);

        if (isExpanded) {
            int bodyY = y + 15;
            for (String line : wrapAll(r.body(), w - 6)) {
                ctx.drawTextWithShadow(textRenderer, "§7" + line, x + 6, bodyY, 0xFFFFFFFF);
                bodyY += 10;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
            double mx = click.x(), my = click.y();
            int btn = click.button();
        if (btn != 0) return super.mouseClicked(click, doubled);

        List<String> sections = HwRulesData.sections();
        int ny = panelY + 30;
        for (int i = 0; i < sections.size(); i++) {
            if (mx >= navX && mx <= navX + navW && my >= ny && my < ny + navItemH) {
                selectedSection = i;
                scroll = 0;
                return true;
            }
            ny += navItemH + 3;
        }

        int top = panelY + 28;
        int bottom = panelY + panelH - 8;
        if (mx >= listX && mx <= listX + listW && my >= top && my <= bottom) {
            int y = top - (int) scroll;
            for (Rule r : rulesFor(selectedSection)) {
                int rowH = rowHeight(r);
                if (my >= y && my < y + rowH) {
                    if (!expanded.add(r.code())) expanded.remove(r.code());
                    return true;
                }
                y += rowH + 2;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private List<Rule> rulesFor(int sectionIndex) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : HwRulesData.all()) if (sectionIndex(r.section()) == sectionIndex) out.add(r);
        return out;
    }

    private static int sectionIndex(String section) {
        List<String> sections = HwRulesData.sections();
        int i = sections.indexOf(section);
        return i < 0 ? 0 : i;
    }

    private int rowHeight(Rule r) {
        if (!expanded.contains(r.code())) return 16;
        int lines = 0;
        for (String line : r.body()) lines += wrap(line, listW - 12).size();
        return 15 + lines * 10 + 4;
    }

    private double listContentHeight(List<Rule> rules) {
        double h = 0;
        for (Rule r : rules) h += rowHeight(r) + 2;
        return h;
    }

    private List<String> wrapAll(List<String> lines, int maxW) {
        List<String> out = new ArrayList<>();
        for (String s : lines) out.addAll(wrap(s, maxW));
        return out;
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (textRenderer.getWidth(test) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            } else {
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static String shortPenalty(String penalty) {
        if (penalty == null || penalty.isEmpty() || penalty.equals("—")) return "";
        return penalty;
    }

    private static String shortenSection(String section) {
        int dot = section.indexOf(". ");
        if (dot < 0) return section;
        String head = section.substring(0, dot);
        String body = section.substring(dot + 2);
        if (body.length() > 22) body = body.substring(0, 21) + "…";
        return head + ". " + body;
    }

    // рисуем фон сами — renderBackground() в ванили зовёт blur по всему фреймбуферу.
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    public static void openFromToggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new HwRulesScreen(mc.currentScreen));
    }
}
