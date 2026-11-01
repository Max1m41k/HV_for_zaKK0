package com.visualmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.hud.BindsHUD;
import com.visualmod.module.modules.hud.HudPosition;
import com.visualmod.module.modules.hud.HudRadius;
import com.visualmod.module.modules.hud.HudRenderer;
import com.visualmod.module.modules.hud.InterfaceHUD;
import com.visualmod.module.modules.hud.ScoreboardEdit;
import com.visualmod.module.modules.util.ChatPlus;
import com.visualmod.screen.EventTableScreen;
import com.visualmod.util.HudAlign;
import com.visualmod.util.HudIcons;
import com.visualmod.util.HudStyle;
import com.visualmod.util.ModuleList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;

@Mixin(ChatScreen.class)
public abstract class ChatHudEditMixin extends Screen {

    protected ChatHudEditMixin(Text title) { super(title); }

    @Unique private static boolean visuals$editable(Module m) {
        if (m.getName().equals("EventNotification")) return true;
        if (m.getCategory() != Module.Category.HUD) return false;
        // Плашку показываем только там, где модуль действительно рисуется в точке из HudPosition.
        // Иначе в редакторе HUD стоят пустые прямоугольники, которые не двигаются и перехватывают
        // клики: BlurGUI/GUIAnimations на весь экран, DeathCard и баннеры — по центру.
        return HudRenderer.movable(m.getName())
                || m.getName().equals("InterfaceHUD")
                || m.getName().equals("SwapFlash")
                || m.getName().equals("Scoreboard");
    }

    @Unique private String visuals$dragging;
    @Unique private int visuals$offX, visuals$offY;
    @Unique private String visuals$radiusFor;
    @Unique private boolean visuals$radiusDrag;
    @Unique private boolean visuals$sizeDrag;

    @Inject(method = "render", at = @At("TAIL"))
    private void visuals$drawHudBoxes(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (VisualsMod.moduleManager == null || this.client == null) return;

        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || visuals$dragging != null) {
            boolean any = false;
            HudAlign.drawGrid(ctx, this.width, this.height, 0x14FFFFFF);
            for (Module m : VisualsMod.moduleManager.getModules()) {
                if (!m.isEnabled() || !visuals$editable(m)) continue;
                any = true;
                if (m.getName().equals("InterfaceHUD")) {
                    visuals$drawParts(ctx);
                    continue;
                }
                int[] b = visuals$box(m);
                boolean drag = m.getName().equals(visuals$dragging);
                boolean sel = m.getName().equals(visuals$radiusFor);
                ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], drag ? 0xAA7B5CF0 : 0x66000000);
                ctx.drawStrokedRectangle(b[0], b[1], b[2], b[3], drag ? 0xFFB399FF : sel ? 0xFF7B5CF0 : 0xFFFFFFFF);
                if (sel && !drag) visuals$drawHandles(ctx, b);
                HudIcons.drawForModule(ctx, m.getName(), b[0] + 4, b[1] + 3, 0xFFFFFFFF);
                ctx.drawTextWithShadow(this.client.textRenderer, m.getDisplayName(),
                        b[0] + 3 + HudIcons.STEP, b[1] + 2, 0xFFFFFFFF);
            }
            visuals$drawGuides(ctx);
            if (any) {
                String hint = InterfaceHUD.sizeEditMode()
                        ? "§7стрелки — сдвиг · полоса ниже — размер"
                        : "§7Ctrl + перетащить HUD · стрелки — сдвиг выбранного";
                ctx.drawTextWithShadow(this.client.textRenderer, hint, this.width - 236, 4, 0xFFFFFFFF);
                visuals$drawToolbar(ctx, mouseX, mouseY);
            }
        } else {
            ctx.drawTextWithShadow(this.client.textRenderer, "§8Ctrl — двигать HUD  ·  клик — закругление  ·  Ctrl+стрелки — сдвиг  ·  EventNotification — таблица", 4, 4, 0xFFFFFFFF);
        }

        if (visuals$radiusFor != null && !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            visuals$drawRadiusBar(ctx, mouseX, mouseY);
            if (InterfaceHUD.sizeEditMode()) visuals$drawSizeBar(ctx, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void visuals$clickDrag(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        if (button != 0 || VisualsMod.moduleManager == null) return;
        // Кнопки «+» chat+ проверяем до ванильной обработки: в 1.21.11 у ChatHud своего mouseClicked
        // нет, и это единственная точка, где клик по чату ещё можно перехватить.
        if (!InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) && ChatPlus.handleClick(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (!InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) return;

        if (visuals$toolbarClick(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }

        Module binds = VisualsMod.moduleManager.getModuleByName("BindsHUD");
        if (binds != null && binds.isEnabled()) {
            if (BindsHUD.clickChip(mouseX, mouseY) || BindsHUD.clickRow(mouseX, mouseY)) {
                cir.setReturnValue(true);
                return;
            }
        }
        // Части InterfaceHUD таскаются по отдельности: проверяем их раньше общего бокса модуля.
        Module iface = VisualsMod.moduleManager.getModuleByName("InterfaceHUD");
        if (iface != null && iface.isEnabled()) {
            int part = InterfaceHUD.partAt(mouseX, mouseY);
            if (part >= 0) {
                int[] b = InterfaceHUD.partBox(part);
                visuals$dragging = InterfaceHUD.keyOf(part);
                visuals$radiusFor = InterfaceHUD.keyOf(part);
                visuals$offX = (int) mouseX - b[0];
                visuals$offY = (int) mouseY - b[1];
                cir.setReturnValue(true);
                return;
            }
        }
        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (!m.isEnabled() || !visuals$editable(m)) continue;
            int[] b = visuals$box(m);
            if (visuals$hit(mouseX, mouseY, b)) {
                visuals$dragging = m.getName();
                visuals$radiusFor = m.getName();
                visuals$offX = (int) mouseX - b[0];
                visuals$offY = (int) mouseY - b[1];
                cir.setReturnValue(true);
                return;
            }
        }
    }

    /**
     * Остальные клики обрабатываются ПОСЛЕ ванильного mouseClicked: ChatScreen+ChatHud должны
     * сначала сами забрать клик (кнопки копирования чата, поля ввода). Если объявить метод
     * через @Override, mixin вытесняет тело ChatScreen целиком — именно так chat+ и перестал
     * работать.
     */
    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    private void visuals$clickEdit(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        if (button != 0 || VisualsMod.moduleManager == null || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) return;
        // TAIL-инъекция: ванильный mouseClicked уже отработал, а его результат лежит в CIR
        // (Mixin передаёт return-значение в конструктор). Если чат забрал клик (кнопки
        // копирования chat+) — не перехватываем его у ChatScreen.
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;

        if (visuals$radiusFor != null && InterfaceHUD.sizeEditMode()) {
            int[] sbar = visuals$sizeBar();
            if (visuals$hit(mouseX, mouseY, sbar)) {
                visuals$setSizeFromX(sbar, mouseX);
                visuals$sizeDrag = true;
                cir.setReturnValue(true);
                return;
            }
        }

        if (visuals$radiusFor != null) {
            int[] bar = visuals$radiusBar();
            if (visuals$hit(mouseX, mouseY, bar)) {
                int[] knob = visuals$knob(bar);
                if (visuals$hit(mouseX, mouseY, knob)) {
                    float v = HudRadius.bump(visuals$radiusFor);
                    visuals$syncScoreboard(visuals$radiusFor, v);
                } else {
                    visuals$setFromX(bar, mouseX);
                    visuals$radiusDrag = true;
                }
                cir.setReturnValue(true);
                return;
            }
        }

        if (InterfaceHUD.listPartActive() && this.client != null
                && (ModuleList.clickRow(this.client, mouseX, mouseY) || ModuleList.clickChip(mouseX, mouseY))) {
            cir.setReturnValue(true);
            return;
        }

        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (!m.isEnabled() || !visuals$editable(m)) continue;
            int[] b = visuals$box(m);
            if (!visuals$hit(mouseX, mouseY, b)) continue;

            if (m.getName().equals("EventNotification")) {
                if (this.client != null) {
                    this.client.setScreen(new EventTableScreen(this));
                }
                visuals$radiusFor = null;
            } else if (m.getName().equals(visuals$radiusFor)) {
                visuals$radiusFor = null;
            } else {
                visuals$radiusFor = m.getName();
            }
            cir.setReturnValue(true);
            return;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
            double mouseX = click.x(), mouseY = click.y();
            int button = click.button();
        if (visuals$dragging != null) {
            int nx = (int) mouseX - visuals$offX, ny = (int) mouseY - visuals$offY;
            int[] sz = visuals$dragSize(visuals$dragging);
            int[] snapped = HudAlign.resolve(nx, ny, sz[0], sz[1],
                    visuals$others(visuals$dragging), this.width, this.height);
            nx = snapped[0];
            ny = snapped[1];
            if (InterfaceHUD.isPartKey(visuals$dragging)) {
                InterfaceHUD.setPartPos(visuals$dragging, nx, ny);
                return true;
            }
            if (visuals$dragging.equals("InterfaceHUD")) {
                InterfaceHUD.setPos(nx, ny);
                return true;
            }
            int[] old = HudPosition.get(visuals$dragging);
            if (old[0] < 0) nx = Math.min(-1, nx - this.width);
            if (old[1] < 0) ny = Math.min(-1, ny - this.height);
            HudPosition.set(visuals$dragging, nx, ny);
            return true;   // файл пишет mouseReleased — на каждый кадр драга не надо
        }
        if (visuals$radiusDrag && visuals$radiusFor != null) {
            visuals$setFromX(visuals$radiusBar(), mouseX);
            return true;
        }
        if (visuals$sizeDrag && visuals$radiusFor != null) {
            visuals$setSizeFromX(visuals$sizeBar(), mouseX);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    /**
     * Ctrl + стрелки — сдвиг выбранного элемента на 1 px (Shift — на 4).
     *
     * Inject, а не @Override: тело ChatScreen.keyPressed должно остаться — на нём держится
     * ввод текста и история чата.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void visuals$keyPressed(net.minecraft.client.input.KeyInput input,
                                    CallbackInfoReturnable<Boolean> cir) {
        int keyCode = input.key();
        if (visuals$radiusFor == null || !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) return;
        int step = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) ? 4 : 1;
        int dx = 0, dy = 0;
        if (keyCode == GLFW.GLFW_KEY_LEFT) dx = -step;
        else if (keyCode == GLFW.GLFW_KEY_RIGHT) dx = step;
        else if (keyCode == GLFW.GLFW_KEY_UP) dy = -step;
        else if (keyCode == GLFW.GLFW_KEY_DOWN) dy = step;
        else return;
        visuals$move(visuals$radiusFor, dx, dy);
        cir.setReturnValue(true);
    }

    @Unique
    private void visuals$move(String key, int dx, int dy) {
        int[] p = HudPosition.get(key);
        if (InterfaceHUD.isPartKey(key)) InterfaceHUD.setPartPos(key, p[0] + dx, p[1] + dy);
        else if ("InterfaceHUD".equals(key)) InterfaceHUD.setPos(p[0] + dx, p[1] + dy);
        else HudPosition.set(key, p[0] + dx, p[1] + dy);
        HudPosition.save();
    }

    /** Размер перетаскиваемой штуки — по нему HudAlign ищет грани для привязки. */
    @Unique
    private int[] visuals$dragSize(String key) {
        if (InterfaceHUD.isPartKey(key)) {
            for (int i = 0; i < InterfaceHUD.PART_COUNT; i++) {
                if (InterfaceHUD.keyOf(i).equals(key)) {
                    int[] b = InterfaceHUD.partBox(i);
                    return new int[]{b[2], b[3]};
                }
            }
            return new int[]{40, 14};
        }
        if ("InterfaceHUD".equals(key)) {
            int[] b = InterfaceHUD.bounds();
            return new int[]{b[2], b[3]};
        }
        Module m = VisualsMod.moduleManager.getModuleByName(key);
        if (m == null) return new int[]{40, 12};
        int[] b = visuals$box(m);
        return new int[]{b[2], b[3]};
    }

    @Unique
    private List<int[]> visuals$others(String skip) {
        List<int[]> out = new ArrayList<>();
        for (Module m : VisualsMod.moduleManager.getModules()) {
            if (!m.isEnabled() || !visuals$editable(m)) continue;
            if (m.getName().equals("InterfaceHUD")) {
                for (int i = 0; i < InterfaceHUD.PART_COUNT; i++) {
                    if (!InterfaceHUD.partVisible(i) || InterfaceHUD.keyOf(i).equals(skip)) continue;
                    out.add(InterfaceHUD.partBox(i));
                }
                if (!"InterfaceHUD".equals(skip) && !InterfaceHUD.isPartKey(skip)) out.add(InterfaceHUD.bounds());
                continue;
            }
            if (m.getName().equals(skip)) continue;
            out.add(visuals$box(m));
        }
        return out;
    }

    @Unique
    private void visuals$drawGuides(DrawContext ctx) {
        if (visuals$dragging == null) return;
        for (int[] g : HudAlign.guidesV) ctx.fill(g[0], g[1], g[0] + 1, g[3], 0x80FF4FD8);
        for (int[] g : HudAlign.guidesH) ctx.fill(g[0], g[1], g[2], g[1] + 1, 0x80FF4FD8);
    }

    /** Угловые ручки выбранного элемента — чтобы было видно, какой именно сейчас на прицеле. */
    @Unique
    private void visuals$drawHandles(DrawContext ctx, int[] b) {
        int t = 3, c = 0xFFFFFFFF;
        ctx.fill(b[0] - 1, b[1] - 1, b[0] + t, b[1] + t, c);
        ctx.fill(b[0] + b[2] - t, b[1] - 1, b[0] + b[2] + 1, b[1] + t, c);
        ctx.fill(b[0] - 1, b[1] + b[3] - t, b[0] + t, b[1] + b[3] + 1, c);
        ctx.fill(b[0] + b[2] - t, b[1] + b[3] - t, b[0] + b[2] + 1, b[1] + b[3] + 1, c);
    }

    @Override
    public boolean mouseReleased(Click click) {
            double mouseX = click.x(), mouseY = click.y();
            int button = click.button();
        if (visuals$dragging != null) {
            visuals$dragging = null;
            HudAlign.guidesV.clear();
            HudAlign.guidesH.clear();
            HudPosition.save();
            return true;
        }
        if (visuals$radiusDrag) {
            visuals$radiusDrag = false;
            HudRadius.save();
            return true;
        }
        if (visuals$sizeDrag) {
            visuals$sizeDrag = false;
            HudPosition.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void visuals$scroll(double mouseX, double mouseY, double horiz, double vert,
                                CallbackInfoReturnable<Boolean> cir) {
        if (VisualsMod.moduleManager == null) return;
        if (InterfaceHUD.listPartActive() && this.client != null
                && ModuleList.scroll(this.client, mouseX, mouseY, vert)) {
            cir.setReturnValue(true);
            return;
        }
        if (visuals$radiusFor != null && InterfaceHUD.sizeEditMode()
                && visuals$hit(mouseX, mouseY, visuals$sizeBar())) {
            float s = HudPosition.clampScale(HudPosition.getScale(visuals$radiusFor)
                    + (float) Math.signum(vert) * 0.05f);
            HudPosition.setScale(visuals$radiusFor, s);
            HudPosition.save();
            cir.setReturnValue(true);
            return;
        }
        if (visuals$radiusFor != null) {
            int[] bar = visuals$radiusBar();
            if (visuals$hit(mouseX, mouseY, bar)) {
                float v = HudRadius.clamp(HudRadius.get(visuals$radiusFor) + (float) Math.signum(vert) * 1f);
                HudRadius.set(visuals$radiusFor, v);
                visuals$syncScoreboard(visuals$radiusFor, v);
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private String[] visuals$toolLabels() {
        return new String[]{
                HudAlign.grid ? "§aСЕТКА" : "§7СЕТКА",
                HudAlign.snap ? "§aСНАП" : "§7СНАП",
                "§fШАГ " + HudAlign.step(),
                visuals$radiusFor != null ? "§6СБРОС" : "§8СБРОС"
        };
    }

    /** {x, y, w, h, id} — по центру сверху, чтобы не спорить с чатным полем внизу. */
    @Unique
    private List<int[]> visuals$toolbarBoxes() {
        String[] labels = visuals$toolLabels();
        int gap = 4, h = 12, pad = 6;
        int total = -gap;
        for (String l : labels) total += this.client.textRenderer.getWidth(l) + pad * 2 + gap;
        int x = Math.max(2, (this.width - total) / 2), y = 18;
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            int w = this.client.textRenderer.getWidth(labels[i]) + pad * 2;
            out.add(new int[]{x, y, w, h, i});
            x += w + gap;
        }
        return out;
    }

    @Unique
    private void visuals$drawToolbar(DrawContext ctx, int mouseX, int mouseY) {
        String[] labels = visuals$toolLabels();
        for (int[] b : visuals$toolbarBoxes()) {
            boolean hov = visuals$hit(mouseX, mouseY, b);
            HudStyle.fillRounded(ctx, b[0], b[1], b[2], b[3], 6, hov ? 0xE632323E : 0xCC141414);
            ctx.drawTextWithShadow(this.client.textRenderer, labels[b[4]], b[0] + 6, b[1] + 2, 0xFFFFFFFF);
        }
    }

    /** id: 0 — сетка, 1 — привязка к соседям, 2 — шаг сетки, 3 — сброс позиции выбранного. */
    @Unique
    private boolean visuals$toolbarClick(double mouseX, double mouseY) {
        for (int[] b : visuals$toolbarBoxes()) {
            if (!visuals$hit(mouseX, mouseY, b)) continue;
            switch (b[4]) {
                case 0 -> HudAlign.grid = !HudAlign.grid;
                case 1 -> HudAlign.snap = !HudAlign.snap;
                case 2 -> HudAlign.nextStep();
                default -> {
                    if (visuals$radiusFor != null) HudPosition.reset(visuals$radiusFor);
                }
            }
            return true;
        }
        return false;
    }

    /** Рамки вокруг каждой части интерфейса: их можно таскать по отдельности. */
    @Unique
    private void visuals$drawParts(DrawContext ctx) {
        boolean group = "InterfaceHUD".equals(visuals$dragging);
        for (int i = 0; i < InterfaceHUD.PART_COUNT; i++) {
            if (!InterfaceHUD.partVisible(i)) continue;
            int[] b = InterfaceHUD.partBox(i);
            boolean drag = group || InterfaceHUD.keyOf(i).equals(visuals$dragging);
            boolean sel = InterfaceHUD.keyOf(i).equals(visuals$radiusFor);
            ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], drag ? 0xAA7B5CF0 : 0x66000000);
            ctx.drawStrokedRectangle(b[0], b[1], b[2], b[3], drag ? 0xFFB399FF : sel ? 0xFF7B5CF0 : 0xFFFFFFFF);
            if (sel && !drag) visuals$drawHandles(ctx, b);
            ctx.drawTextWithShadow(this.client.textRenderer, InterfaceHUD.partLabel(i),
                    b[0] + 2, Math.max(0, b[1] - 10), 0xFFFFFFFF);
        }
    }

    @Unique
    private int[] visuals$box(Module m) {        // InterfaceHUD сам домножает свои границы на масштаб — дважды масштабировать нельзя.
        if (m.getName().equals("InterfaceHUD")) return InterfaceHUD.bounds();
        int[] b = visuals$rawBox(m);
        // Ванильный скорборд миксин только сдвигает, но не масштабирует — плашка повторяет это.
        if (m.getName().equals("Scoreboard")) return b;
        float s = HudPosition.getScale(m.getName());
        if (com.visualmod.util.HudScale.on(s)) {
            b[2] = com.visualmod.util.HudScale.scaled(b[2], s);
            b[3] = com.visualmod.util.HudScale.scaled(b[3], s);
        }
        return b;
    }

    @Unique
    private int[] visuals$rawBox(Module m) {
        if (m.getName().equals("BindsHUD")) return BindsHUD.bounds();
        int[] g = HudPosition.glide(m.getName(), this.width, this.height);
        int x = g[0], y = g[1];
        if (m.getName().equals("Scoreboard")) {
            int[] sb = ScoreboardEdit.bounds(this.width, this.height);
            if (sb != null) return sb;
        }
        int w = this.client.textRenderer.getWidth(m.getDisplayName()) + 8, h = 12;
        return HudRadius.boundsOrLabel(m.getName(), x, y, w, h);
    }

    @Unique
    private int[] visuals$barAnchor() {
        Module m = VisualsMod.moduleManager.getModuleByName(visuals$radiusFor);
        if (m == null) return new int[]{10, 10, 80, 18};
        // Якорь — НЕмасштабированный прямоугольник: полоса не зависит от текущего размера,
        // поэтому не уезжает из-под курсора и не сжимает свою шкалу во время драга.
        int[] b = visuals$rawBox(m);
        int w = Math.max(96, b[2]);
        int x = Math.max(2, Math.min(this.width - w - 2, b[0]));
        int y = b[1] + b[3] + 3;
        // Под элементом две полосы (радиус + размер). Если снизу не влезает — ставим их сверху,
        // иначе у элемента у нижнего края полосы недоступны и уменьшить его нельзя.
        if (y + 34 > this.height) y = Math.max(2, b[1] - 36);
        return new int[]{x, y, w};
    }

    @Unique
    private int[] visuals$sizeBar() {
        int[] a = visuals$barAnchor();
        // Полоса размера — под полосой закругления.
        return new int[]{a[0], a[1] + 17, a[2], 14};
    }

    @Unique
    private int[] visuals$sizeKnob(int[] bar) {
        float t = (HudPosition.getScale(visuals$radiusFor) - HudPosition.MIN_SCALE)
                / (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE);
        int pad = 8;
        int cx = bar[0] + pad + Math.round(t * (bar[2] - pad * 2));
        return new int[]{cx - 6, bar[1] + 1, 12, 12};
    }

    @Unique
    private void visuals$setSizeFromX(int[] bar, double mouseX) {
        int pad = 8;
        float t = (float) ((mouseX - bar[0] - pad) / (bar[2] - pad * 2.0));
        float s = HudPosition.clampScale(HudPosition.MIN_SCALE + t * (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE));
        HudPosition.setScale(visuals$radiusFor, s);
    }

    @Unique
    private void visuals$drawSizeBar(DrawContext ctx, int mouseX, int mouseY) {
        int[] bar = visuals$sizeBar();
        float s = HudPosition.getScale(visuals$radiusFor);
        HudStyle.fillRounded(ctx, bar[0], bar[1], bar[2], bar[3], 7, 0xEE141414);
        int trackY = bar[1] + 5, trackH = 4;
        HudStyle.fillRounded(ctx, bar[0] + 8, trackY, bar[2] - 16, trackH, 2, 0xFF2A2A36);
        float t = (s - HudPosition.MIN_SCALE) / (HudPosition.MAX_SCALE - HudPosition.MIN_SCALE);
        int fillW = Math.max(4, Math.round(t * (bar[2] - 16)));
        HudStyle.fillRounded(ctx, bar[0] + 8, trackY, fillW, trackH, 2, HudStyle.ACCENT);
        int[] knob = visuals$sizeKnob(bar);
        boolean hov = visuals$hit(mouseX, mouseY, knob);
        HudStyle.fillRounded(ctx, knob[0], knob[1], knob[2], knob[3], 6, hov ? 0xFFB399FF : 0xFFFFFFFF);
        String label = String.format("x%.2f", s);
        ctx.drawTextWithShadow(this.client.textRenderer, label, bar[0] + bar[2] + 4, bar[1] + 3, 0xFFAAAAAA);
    }

    @Unique
    private static boolean visuals$hit(double mx, double my, int[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    @Unique
    private int[] visuals$radiusBar() {
        int[] a = visuals$barAnchor();
        return new int[]{a[0], a[1], a[2], 14};
    }

    @Unique
    private int[] visuals$knob(int[] bar) {
        float t = (HudRadius.get(visuals$radiusFor) - HudRadius.MIN) / (HudRadius.MAX - HudRadius.MIN);
        int pad = 8;
        int cx = bar[0] + pad + Math.round(t * (bar[2] - pad * 2));
        return new int[]{cx - 6, bar[1] + 1, 12, 12};
    }

    @Unique
    private void visuals$setFromX(int[] bar, double mouseX) {
        int pad = 8;
        float t = (float) ((mouseX - bar[0] - pad) / (bar[2] - pad * 2.0));
        float v = HudRadius.clamp(HudRadius.MIN + t * (HudRadius.MAX - HudRadius.MIN));
        HudRadius.set(visuals$radiusFor, v, false);
        visuals$syncScoreboard(visuals$radiusFor, v);
    }

    @Unique
    private static void visuals$syncScoreboard(String name, float v) {
        if ("Scoreboard".equals(name)) ScoreboardEdit.radius = Math.round(v);
    }

    @Unique
    private void visuals$drawRadiusBar(DrawContext ctx, int mouseX, int mouseY) {
        int[] bar = visuals$radiusBar();
        float r = HudRadius.get(visuals$radiusFor);
        HudStyle.fillRounded(ctx, bar[0], bar[1], bar[2], bar[3], 7, 0xEE141414);
        int trackY = bar[1] + 5, trackH = 4;
        HudStyle.fillRounded(ctx, bar[0] + 8, trackY, bar[2] - 16, trackH, 2, 0xFF2A2A36);
        float t = (r - HudRadius.MIN) / (HudRadius.MAX - HudRadius.MIN);
        int fillW = Math.max(4, Math.round(t * (bar[2] - 16)));
        HudStyle.fillRounded(ctx, bar[0] + 8, trackY, fillW, trackH, 2, HudStyle.ACCENT);
        int[] knob = visuals$knob(bar);
        boolean hov = visuals$hit(mouseX, mouseY, knob);
        HudStyle.fillRounded(ctx, knob[0], knob[1], knob[2], knob[3], 6, hov ? 0xFFB399FF : 0xFFFFFFFF);
        String label = String.valueOf(Math.round(r));
        ctx.drawTextWithShadow(this.client.textRenderer, label, bar[0] + bar[2] + 4, bar[1] + 3, 0xFFAAAAAA);
    }
}
