package com.visualmod.module.modules.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.KeySetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.Diag;
import com.visualmod.util.KeyNames;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slots Switch: список пар «откуда → куда». Пока модуль включён, назначение клавиши из левой
 * части пары переезжает в правую (а то, что было в правой, — в левую). Клавиши и слоты хотбара
 * выбираются независимо, строк столько, сколько заполнено. Выключил — бинды вернулись байт в байт.
 */
public class SlotsSwitch extends Module implements Configurable {

    public static final int MAX_PAIRS = 4;
    private static final String UNKNOWN = InputUtil.UNKNOWN_KEY.getTranslationKey();

    public static String[] from = {
            "key.keyboard.e", UNKNOWN, UNKNOWN, UNKNOWN
    };
    public static String[] to = {
            "key.keyboard.2", UNKNOWN, UNKNOWN, UNKNOWN
    };

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Setting> settings = new ArrayList<>();
    /** id привязки → её оригинальная клавиша. Первая запись выигрывает: повторный переезд не должен
     *  затирать исходное значение, иначе откат вернёт не туда. */
    private final Map<String, Moved> moved = new LinkedHashMap<>();
    private String signature = "";
    private boolean applied = false;
    private boolean orphanChecked = false;

    public SlotsSwitch() {
        super("SlotsSwitch", "Each filled pair moves one key binding to another key while this is on",
                Category.UTIL);
        for (int i = 0; i < MAX_PAIRS; i++) {
            final int index = i;
            settings.add(new KeySetting("Откуда " + (i + 1), () -> from[index], k -> from[index] = norm(k)));
            settings.add(new KeySetting("Куда " + (i + 1), () -> to[index], k -> to[index] = norm(k)));
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> sync());
    }

    private static String norm(String k) {
        return k == null || k.isEmpty() ? UNKNOWN : k;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public void onEnable() {
        sync();
    }

    @Override
    public void onDisable() {
        sync();
    }

    /** Клавиши можно менять на ходу, а включение из конфига приходит не только через onEnable. */
    private void sync() {
        if (!isEnabled()) {
            if (applied) revert();
            else recoverOrphan();
            return;
        }
        if (applied && sig().equals(signature)) return;
        if (applied) revert();
        applyPairs();
    }

    /** Игра могла закрыться с уже поменянными биндами — вернуть их, даже когда модуль выключен. */
    private void recoverOrphan() {
        if (orphanChecked) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        orphanChecked = true;
        try {
            File f = journal();
            if (f == null || !f.exists()) return;
            if (readJournal()) {
                restore();
                KeyBinding.updatePressedStates();
            }
            deleteJournal();
        } catch (Throwable t) {
            Diag.once("SlotsSwitch.recover", t);
        }
    }

    private static String sig() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < MAX_PAIRS; i++) b.append(from[i]).append('>').append(to[i]).append('|');
        return b.toString();
    }

    private void applyPairs() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        applied = true;
        signature = sig();

        // Журнал прошлой сессии: игра могла закрыться уже с поменянными биндами.
        if (readJournal()) {
            restore();
            KeyBinding.updatePressedStates();
        }

        for (int i = 0; i < MAX_PAIRS; i++) {
            InputUtil.Key a = KeyNames.keyOf(from[i]);
            InputUtil.Key b = KeyNames.keyOf(to[i]);
            if (a == null || b == null) continue;
            String sa = a.getTranslationKey(), sb = b.getTranslationKey();
            if (sa.equals(sb) || UNKNOWN.equals(sa) || UNKNOWN.equals(sb)) continue;
            swapPair(mc, a, b, sa, sb);
        }
        if (moved.isEmpty()) deleteJournal();
        else writeJournal();
    }

    /** Обе клавиши меняются своими назначениями: ничего не теряется, и откат однозначен. */
    private void swapPair(MinecraftClient mc, InputUtil.Key a, InputUtil.Key b, String sa, String sb) {
        List<KeyBinding> toB = new ArrayList<>();
        List<KeyBinding> toA = new ArrayList<>();
        for (KeyBinding k : mc.options.allKeys) {
            if (k == null) continue;
            String bound = k.getBoundKeyTranslationKey();
            if (sa.equals(bound)) toB.add(k);
            else if (sb.equals(bound)) toA.add(k);
        }
        for (KeyBinding k : toB) {
            record(k);
            k.setBoundKey(b);
        }
        for (KeyBinding k : toA) {
            record(k);
            k.setBoundKey(a);
        }
        KeyBinding.updatePressedStates();
    }

    private void record(KeyBinding k) {
        moved.putIfAbsent(k.getId(), new Moved(k.getId(), k.getBoundKeyTranslationKey()));
    }

    private void revert() {
        restore();
        moved.clear();
        KeyBinding.updatePressedStates();
        deleteJournal();
        applied = false;
        signature = "";
    }

    private void restore() {
        for (Moved m : moved.values()) {
            KeyBinding k = KeyBinding.byId(m.id);
            InputUtil.Key orig = KeyNames.keyOf(m.original);
            if (k != null && orig != null) k.setBoundKey(orig);
        }
        moved.clear();
    }

    private boolean readJournal() {
        try {
            File f = journal();
            if (f == null || !f.exists()) return false;
            Journal j = GSON.fromJson(new FileReader(f, java.nio.charset.StandardCharsets.UTF_8), Journal.class);
            moved.clear();
            if (j == null || j.entries == null) return false;
            for (Moved m : j.entries) {
                if (m != null && m.id != null) moved.putIfAbsent(m.id, m);
            }
            return !moved.isEmpty();
        } catch (Throwable t) {
            Diag.once("SlotsSwitch.journal", t);
            return false;
        }
    }

    private void writeJournal() {
        try {
            File f = journal();
            if (f == null) return;
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            Journal j = new Journal();
            j.keys = signature;
            j.entries = new ArrayList<>(moved.values());
            try (FileWriter w = new FileWriter(f, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(j, w);
            }
        } catch (Throwable t) {
            Diag.once("SlotsSwitch.save", t);
        }
    }

    private static void deleteJournal() {
        try {
            File f = journal();
            if (f != null && f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static File journal() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        return new File(new File(mc.runDirectory, "visualmod_configs"), "slots_switch.json");
    }

    private static final class Moved {
        String id;
        String original;

        Moved() {}

        Moved(String id, String original) {
            this.id = id;
            this.original = original;
        }
    }

    private static final class Journal {
        String keys;
        List<Moved> entries = new ArrayList<>();
    }
}
