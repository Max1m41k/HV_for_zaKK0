package com.visualmod.module.modules.util;

import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.util.Diag;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ServerProfiles: у каждого сервера свой конфиг. При входе подхватывается профиль srv_&lt;адрес&gt;,
 * при выходе текущее состояние сохраняется в него же. Если профиля ещё нет — он создаётся из
 * текущих настроек, поэтому первое подключение ничего не ломает.
 */
public class ServerProfiles extends Module implements Configurable {

    public static boolean autoLoad = true;
    public static boolean autoSave = true;
    public static boolean createMissing = true;
    public static boolean notify = false;

    private static String address;
    private static String profile;

    public ServerProfiles() {
        super("ServerProfiles", "Per-server settings profile, loaded on join and saved on leave", Category.UTIL);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("Auto Load", new String[]{"OFF", "ON"}, () -> autoLoad ? 1 : 0, i -> autoLoad = (i == 1)));
        l.add(new ModeSetting("Auto Save", new String[]{"OFF", "ON"}, () -> autoSave ? 1 : 0, i -> autoSave = (i == 1)));
        l.add(new ModeSetting("Create Missing", new String[]{"OFF", "ON"}, () -> createMissing ? 1 : 0, i -> createMissing = (i == 1)));
        l.add(new ModeSetting("Notify", new String[]{"OFF", "ON"}, () -> notify ? 1 : 0, i -> notify = (i == 1)));
        return l;
    }

    @Override
    public void onEnable() {
        address = null;
        profile = null;
    }

    @Override
    public void onDisable() {
        flush();
        address = null;
        profile = null;
    }

    @Override
    public void onTick() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                if (address != null) {
                    flush();
                    address = null;
                    profile = null;
                }
                return;
            }
            ServerInfo info = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getServerInfo();
            String now = info == null || info.address == null ? null : sanitize(info.address);
            if (now == null || now.isEmpty()) {
                if (address != null) {
                    flush();
                    address = null;
                    profile = null;
                }
                return;
            }
            if (now.equals(address)) return;

            flush();
            address = now;
            profile = "srv_" + now;
            apply(mc);
        } catch (Throwable t) {
            Diag.once("ServerProfiles.tick", t);
        }
    }

    private static void apply(MinecraftClient mc) {
        if (!autoLoad || profile == null) return;
        try {
            if (!ConfigManager.listConfigs().contains(profile)) {
                if (!createMissing) return;
                ConfigManager.saveConfig(profile);
                say(mc, "§d[HolyVisuals] §7Создан профиль сервера §e" + profile);
                return;
            }
            if (ConfigManager.loadConfig(profile, false)) {
                say(mc, "§d[HolyVisuals] §7Профиль сервера: §e" + profile);
            }
        } catch (Throwable t) {
            Diag.once("ServerProfiles.apply", t);
        }
    }

    private static void flush() {
        if (!autoSave || profile == null) return;
        try {
            ConfigManager.saveConfig(profile);
        } catch (Throwable t) {
            Diag.once("ServerProfiles.flush", t);
        }
    }

    private static void say(MinecraftClient mc, String text) {
        if (!notify || mc.player == null) return;
        mc.player.sendMessage(Text.literal(text), false);
    }

    private static String sanitize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 40; i++) {
            char c = s.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }
}
