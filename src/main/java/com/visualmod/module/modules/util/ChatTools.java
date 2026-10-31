package com.visualmod.module.modules.util;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.screen.ChatToolsScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/** Поиск по чату, фильтры, закреплённые сообщения, метки времени, группировка повторов и кликабельные координаты. */
public class ChatTools extends Module implements Configurable {

    public static int timestamps = 1;
    public static boolean grouping = true;
    public static boolean coordCopy = true;

    public ChatTools() {
        super("ChatTools", "Chat search, filters, pins, timestamps, duplicate grouping, clickable coords", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Open Search", new String[]{"OFF", "ON"}, () -> 0, i -> {
            if (i == 1) {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> mc.setScreen(new ChatToolsScreen(mc.currentScreen)));
            }
        }));
        l.add(new ModeSetting("Timestamps", new String[]{"OFF", "HH:MM", "HH:MM:SS"},
                () -> timestamps, i -> timestamps = i));
        l.add(new ModeSetting("Group Duplicates", new String[]{"OFF", "ON"},
                () -> grouping ? 1 : 0, i -> grouping = (i == 1)));
        l.add(new ModeSetting("Coord Copy", new String[]{"OFF", "ON"},
                () -> coordCopy ? 1 : 0, i -> coordCopy = (i == 1)));
        return l;
    }

    public static boolean active() {
        if (VisualsMod.moduleManager == null) return false;
        ChatTools m = VisualsMod.moduleManager.getModule(ChatTools.class);
        return m != null && m.isEnabled();
    }

    /** ChatRules уступает: метку времени ставим мы, когда модуль включён и формат выбран. */
    public static boolean willStampTime() { return active() && timestamps > 0; }

    public static boolean groupsDuplicates() { return active() && grouping; }
}
