package com.visualmod.module.modules.combat;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ItemScroller extends Module implements Configurable {

    public static double delayMs = 60;
    public static boolean shiftAll = true;
    public static double maxStacks = 27;
    public static boolean skipCreative = true;
    public static boolean dragSplit = true;
    public static boolean pullUp = true;
    public static boolean fillEmpty = true;

    private static int dragOrigin = -1;
    // Shift ловим в момент захвата: если отпускать его раньше кнопки, делёжка превращалась в один стак.
    private static boolean dragShift = false;
    private static final List<Integer> dragPath = new ArrayList<>();

    // Очередь хранит предмет вместе со слотом: пока пачка летит, содержимое слота может измениться.
    private record Job(int slotId, ItemStack ref) {}

    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    private static Screen queueScreen;
    private static int queueSyncId = -1;
    // Единственный источник времени и для скролла, и для дренажа очереди.
    private static long lastActionNanos = 0L;
    /** Последний сдвинутый предмет: по нему решаем, чем заполнить наведённый пустой слот. */
    private static ItemStack lastMoved;

    public ItemScroller() {
        super("ItemScroller", "Scroll to quick-move stacks, scroll over an empty slot to fill it, Shift+LMB to drag and split", Category.COMBAT);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Delay", 0, 300, 20, () -> delayMs, v -> delayMs = v, "ms"));
        l.add(new ModeSetting("Shift = All Stacks", new String[]{"OFF", "ON"}, () -> shiftAll ? 1 : 0, i -> shiftAll = (i == 1)));
        l.add(new SliderSetting("Max Stacks", 1, 54, 1, () -> maxStacks, v -> maxStacks = v));
        l.add(new ModeSetting("Scroll Up Pull", new String[]{"OFF", "ON"}, () -> pullUp ? 1 : 0, i -> pullUp = (i == 1)));
        l.add(new ModeSetting("Skip Creative", new String[]{"OFF", "ON"}, () -> skipCreative ? 1 : 0, i -> skipCreative = (i == 1)));
        l.add(new ModeSetting("Fill Empty Slot", new String[]{"OFF", "ON"}, () -> fillEmpty ? 1 : 0, i -> fillEmpty = (i == 1)));
        l.add(new ModeSetting("Shift+Drag Split", new String[]{"OFF", "ON"}, () -> dragSplit ? 1 : 0, i -> dragSplit = (i == 1)));
        return l;
    }

    /** Креатив/наблюдатель определяем по режиму игрока, а не по типу экрана. */
    public static boolean blocked(MinecraftClient mc) {
        if (!skipCreative || mc == null || mc.player == null) return false;
        return mc.player.isCreative() || mc.player.isSpectator() || mc.player.getAbilities().creativeMode;
    }

    // --- Скролл ---

    /**
     * ВНИЗ = shift-клик по наведённому слоту, ВВЕРХ = тот же клик, но по подходящему стаку
     * с противоположной стороны — «достать» из контейнера. Нотч, который не удалось отработать
     * сразу (пачка в пути или не подошёл темп), не пропадает: цель встаёт в очередь.
     * Возвращает true, только если нотч реально съеден: иначе крутится ванильный скролл.
     */
    public static boolean onScroll(HandledScreen<?> screen, Slot hovered, double vertical, MinecraftClient mc) {
        if (vertical == 0 || hovered == null || isDragging()) return false;
        if (mc == null || mc.player == null || mc.interactionManager == null) return false;
        if (blocked(mc)) { clear(); return false; }
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) { clear(); return false; }

        boolean down = vertical < 0;
        if (!down && !pullUp) return false;
        if (busy() || tooFast()) { enqueue(handler, hovered, down, mc); return true; }

        Slot src = source(handler, hovered, down, mc);
        if (src != null) {
            quickMove(handler, src, mc);
            if (shiftAll && InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)) {
                fill(handler, src, lastMoved, mc.currentScreen);
                // Delay=0 не должен квантоваться тиком (20 TPS = шаг 50 мс), пачка уходит сразу.
                if (delayMs <= 0) drain(true);
            }
            return true;
        }
        return fillSlot(handler, hovered, mc);
    }

    /** Что переносим по нотчу: сам слот при скролле вниз, подходящий стак с другой стороны при вверх. */
    private static Slot source(ScreenHandler handler, Slot hovered, boolean down, MinecraftClient mc) {
        if (down) return hovered.hasStack() ? hovered : null;
        if (!hovered.hasStack()) return null;
        Slot src = oppositeMatch(handler, hovered, hovered.getStack(), mc);
        return src != null && src.hasStack() ? src : null;
    }

    /** Нотч не по темпу: кликаем не вслепую, а дописываем цель в очередь — drain() разложит по времени. */
    private static void enqueue(ScreenHandler handler, Slot hovered, boolean down, MinecraftClient mc) {
        if (queue.size() >= Math.max(1, (int) maxStacks)) return;
        Slot src = source(handler, hovered, down, mc);
        if (src == null) {
            if (!fillEmpty || hovered.hasStack() || lastMoved == null) return;
            src = oppositeMatch(handler, hovered, lastMoved, mc);
        }
        if (src == null || queued(src.id)) return;
        queue.addLast(new Job(src.id, src.getStack().copy()));
        queueScreen = mc.currentScreen;
        queueSyncId = handler.syncId;
    }

    private static boolean queued(int id) {
        for (Job j : queue) if (j.slotId() == id) return true;
        return false;
    }

    /** Один перенос: он же задаёт темп и он же обновляет эталон для пустых слотов. */
    private static void quickMove(ScreenHandler handler, Slot src, MinecraftClient mc) {
        lastMoved = src.getStack().copy();
        mc.interactionManager.clickSlot(handler.syncId, src.id, 0, SlotActionType.QUICK_MOVE, mc.player);
        stamp();
    }

    /**
     * Скролл по пустому слоту: кладём в него один подходящий стак с другой стороны — ровно туда,
     * куда наведён курсор, а не куда скажет ванильный quick-move. Остаток на курсоре добирается
     * обратно, предмет не должен остаться висеть в руке.
     */
    private static boolean fillSlot(ScreenHandler handler, Slot dst, MinecraftClient mc) {
        if (!fillEmpty || dst.hasStack() || lastMoved == null) return false;
        Slot src = oppositeMatch(handler, dst, lastMoved, mc);
        if (src == null || !dst.canInsert(src.getStack())) return false;

        int sync = handler.syncId;
        ItemStack moving = src.getStack().copy();
        // PICKUP в ванили — переключатель: пустой курсор забирает стак, занятый кладёт его целиком.
        mc.interactionManager.clickSlot(sync, src.id, 0, SlotActionType.PICKUP, mc.player);
        if (handler.getCursorStack().isEmpty()) return true;
        mc.interactionManager.clickSlot(sync, dst.id, 0, SlotActionType.PICKUP, mc.player);
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, src.id, 0, SlotActionType.PICKUP, mc.player);
        }
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, dst.id, 0, SlotActionType.PICKUP, mc.player);
        }
        lastMoved = moving;
        stamp();
        return true;
    }

    /** Слот игрока или сторона контейнера? Ориентир — инвентарь самого игрока. */
    private static boolean isPlayerSide(Slot slot, MinecraftClient mc) {
        return mc.player != null && slot.inventory == mc.player.getInventory();
    }

    /** Для «достать»: берём самый полный подходящий стак с другой стороны экрана. */
    private static Slot oppositeMatch(ScreenHandler handler, Slot hovered, ItemStack ref, MinecraftClient mc) {
        boolean side = isPlayerSide(hovered, mc);
        Slot best = null;
        for (Slot s : handler.slots) {
            if (s == hovered || !s.hasStack()) continue;
            if (isPlayerSide(s, mc) == side) continue;
            if (!ItemStack.areItemsAndComponentsEqual(ref, s.getStack())) continue;
            if (best == null || s.getStack().getCount() > best.getStack().getCount()) best = s;
        }
        return best;
    }

    /** Пакет таких же стоков, как уже кликнутый, с той же стороны экрана. */
    public static void fill(ScreenHandler handler, Slot origin, ItemStack ref, Screen current) {
        queue.clear();
        int limit = Math.max(1, (int) maxStacks);
        for (Slot s : handler.slots) {
            if (queue.size() >= limit) break;
            if (s == origin || s.inventory != origin.inventory || !s.hasStack()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(ref, s.getStack())) continue;
            queue.addLast(new Job(s.id, s.getStack().copy()));
        }
        queueScreen = current;
        queueSyncId = handler.syncId;
    }

    public static boolean busy() { return !queue.isEmpty(); }

    private static long msSinceLast() {
        return lastActionNanos == 0 ? Long.MAX_VALUE : (System.nanoTime() - lastActionNanos) / 1_000_000L;
    }

    public static boolean tooFast() { return msSinceLast() < (long) delayMs; }

    public static void stamp() { lastActionNanos = System.nanoTime(); }

    public static void clear() {
        queue.clear();
        queueScreen = null;
        queueSyncId = -1;
        lastMoved = null;
        dragOrigin = -1;
        dragShift = false;
        dragPath.clear();
    }

    /** burst=true используется при Delay=0: весь пакет уходит за один вызов, без кванта тика. */
    public static void drain(boolean burst) {
        if (queue.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.interactionManager == null) { clear(); return; }
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) { clear(); return; }

        // Пересобираем контекст каждый клик: экран, хендлер и syncId могли смениться на полпути.
        ScreenHandler handler = screen.getScreenHandler();
        if (screen != queueScreen || handler.syncId != queueSyncId || mc.player.currentScreenHandler != handler) {
            clear();
            return;
        }
        if (!handler.getCursorStack().isEmpty() || blocked(mc)) { clear(); return; }

        while (!queue.isEmpty()) {
            if (!burst && tooFast()) return;
            Job job = queue.pollFirst();
            Slot s = resolve(handler, job.slotId());
            // Слот мог уехать или опустеть — такой клик просто выбрасываем, не отправляем вслепую.
            if (s == null || !s.hasStack() || !ItemStack.areItemsAndComponentsEqual(job.ref(), s.getStack())) continue;
            lastMoved = job.ref();
            mc.interactionManager.clickSlot(handler.syncId, s.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            stamp();
            if (!burst) return;
        }
    }

    private static Slot resolve(ScreenHandler handler, int id) {
        if (id < 0 || id >= handler.slots.size()) return null;
        Slot s = handler.slots.get(id);
        return s != null && s.id == id ? s : null;
    }

    // --- Shift+ЛКМ: берём весь стак в курсор, тянем по слотам, отпускаем — раскидываем ---

    public static boolean isDragging() { return dragOrigin >= 0; }

    /**Press: забираем весь стак в курсор. Возвращает true, если клик перехвачен. */
    public static boolean beginDrag(Slot slot, ScreenHandler handler, MinecraftClient mc) {
        if (!dragSplit || slot == null || !slot.hasStack() || !handler.getCursorStack().isEmpty()) return false;
        if (slot.id < 0 || slot.id >= handler.slots.size()) return false;
        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
        if (handler.getCursorStack().isEmpty()) return false;
        dragOrigin = slot.id;
        dragShift = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
        dragPath.clear();
        dragPath.add(slot.id);
        return true;
    }

    public static void trackDrag(Slot slot) {
        if (dragOrigin < 0 || slot == null || slot.id < 0) return;
        // Повторный слот в пути сбивает делёжку (сервер делит на уникальные слоты), поэтому дубли отбрасываем.
        if (dragPath.contains(slot.id)) return;
        dragPath.add(slot.id);
    }

    /**Release: по пути QUICK_CRAFT раскидывает стак поровну между слотами. */
    public static void endDrag(ScreenHandler handler, MinecraftClient mc) {
        int origin = dragOrigin;
        boolean shift = dragShift;
        dragOrigin = -1;
        dragShift = false;
        if (origin < 0 || origin >= handler.slots.size()) { dragPath.clear(); return; }
        if (handler.getCursorStack().isEmpty()) { dragPath.clear(); return; }

        List<Integer> path = new ArrayList<>(dragPath);
        dragPath.clear();
        path.removeIf(id -> id < 0 || id >= handler.slots.size());
        if (path.isEmpty()) path.add(origin);
        if (path.size() < 2) path.add(origin);

        try {
            if (shift) {
                // 0 = начать перетаскивание, 1 = добавить слот, 2 = закончить (сервер делит стак).
                mc.interactionManager.clickSlot(handler.syncId, path.get(0), 0, SlotActionType.QUICK_CRAFT, mc.player);
                for (int id : path) {
                    mc.interactionManager.clickSlot(handler.syncId, id, 1, SlotActionType.QUICK_CRAFT, mc.player);
                }
                mc.interactionManager.clickSlot(handler.syncId, path.get(path.size() - 1), 2, SlotActionType.QUICK_CRAFT, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, path.get(path.size() - 1), 0, SlotActionType.PICKUP, mc.player);
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller.drag", t);
        }
    }

    public static void cancelDrag(MinecraftClient mc) {
        if (dragOrigin < 0) return;
        int origin = dragOrigin;
        dragOrigin = -1;
        dragShift = false;
        dragPath.clear();
        try {
            if (mc.currentScreen instanceof HandledScreen<?> screen) {
                ScreenHandler handler = screen.getScreenHandler();
                if (!handler.getCursorStack().isEmpty() && origin < handler.slots.size()) {
                    mc.interactionManager.clickSlot(handler.syncId, origin, 0, SlotActionType.PICKUP, mc.player);
                }
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("ItemScroller.cancel", t);
        }
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void onTick() {
        drain(false);
    }
}
