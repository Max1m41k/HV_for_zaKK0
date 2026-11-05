package com.visualmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Встроенная функция (без ModuleManager и GUI): на Holyworld выход в хаб во время PVP
 * сбрасывает бой и лут, поэтому пока сервер пишет «вы вошли в режим PVP!» команда /hub
 * не отправляется ни вручную, ни по макросу. Состояние сбрасывается при выходе из мира,
 * по сообщению сервера о выходе из PVP и по смерти: сервер сообщение «вы вышли из PVP»
 * при смерти не пишет, но режим PVP смерть обнуляет, поэтому держать блок /hub нельзя.
 */
public final class PvpSafe {

    private static boolean inPvp = false;
    private static boolean wasDead = false;

    private PvpSafe() {}

    public static boolean inPvp() {
        return inPvp;
    }

    /** Сырая строка чата: сообщения сервера приходят с §-кодами, их убираем до сравнения. */
    public static void onChatLine(String raw) {
        if (raw == null || raw.isEmpty()) return;
        String s = raw.toLowerCase().replaceAll("§.", "").trim();
        if (s.contains("вошли в режим pvp")) {
            if (!inPvp) notify("§e/hub теперь блокируется: §cрежим PVP включён");
            inPvp = true;
        } else if (s.contains("вышли из режима pvp")) {
            if (inPvp) notify("§a/hub снова доступен: §7режим PVP выключен");
            inPvp = false;
        } else if (s.contains("вы умерли") || s.contains("you died")) {
            if (inPvp) notify("§a/hub снова доступен: §7смерть сбросила режим PVP");
            inPvp = false;
        }
    }

    /** Вызывается из тика клиента: вне мира PVP-состояния нет, а смерть его обнуляет. */
    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            inPvp = false;
            wasDead = false;
            return;
        }
        if (mc.player == null) return;
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0f
                || mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen;
        // dead — момент смерти, wasDead — первый тик после возрождения (для мгновенного респавна).
        if (inPvp && (dead || wasDead)) {
            inPvp = false;
            notify("§a/hub снова доступен: §7смерть сбросила режим PVP");
        }
        wasDead = dead;
    }

    /** True — команду отправлять нельзя (сообщение в чат уже показаны). */
    public static boolean blocks(String command) {
        if (!inPvp || command == null) return false;
        String c = command.trim();
        while (c.startsWith("/")) c = c.substring(1).trim();
        int sp = c.indexOf(' ');
        String name = (sp < 0 ? c : c.substring(0, sp)).toLowerCase();
        if (!name.equals("hub")) return false;
        notify("§c/hub заблокирован: §7ты в режиме PVP, §eсначала выйди из PVP");
        return true;
    }

    private static void notify(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.sendMessage(Text.of("§d[TriadVisual] " + message), false);
    }
}
