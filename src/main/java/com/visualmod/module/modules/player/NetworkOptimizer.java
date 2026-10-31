package com.visualmod.module.modules.player;

import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class NetworkOptimizer extends Module implements Configurable {

    public static boolean tcpNoDelay = true;
    public static boolean packetBatching = true;
    public static boolean lagCompensation = true;
    public static int packetSendRate = 20;
    public static boolean smoothPing = true;

    private static long lastPacketTime = 0;
    private static long packetInterval = 1000 / 20;

    public NetworkOptimizer() {
        super("NetworkOptimizer", "Optimizes network socket settings to reduce ping and latency", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§a[NetworkOptimizer] Сетевой оптимизатор активирован!"), false);
            mc.player.sendMessage(Text.literal("§7- TCP NoDelay: " + (tcpNoDelay ? "ON" : "OFF")), false);
            mc.player.sendMessage(Text.literal("§7- Packet Batching: " + (packetBatching ? "ON" : "OFF")), false);
            mc.player.sendMessage(Text.literal("§7- Lag Compensation: " + (lagCompensation ? "ON" : "OFF")), false);
        }
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new ModeSetting("TCP NoDelay", new String[]{"OFF", "ON"}, () -> tcpNoDelay ? 1 : 0, i -> {
            tcpNoDelay = (i == 1);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§e[NetworkOptimizer] TCP NoDelay: " + (tcpNoDelay ? "ON" : "OFF")), false);
            }
        }));
        l.add(new ModeSetting("Packet Batching", new String[]{"OFF", "ON"}, () -> packetBatching ? 1 : 0, i -> packetBatching = (i == 1)));
        l.add(new ModeSetting("Lag Compensation", new String[]{"OFF", "ON"}, () -> lagCompensation ? 1 : 0, i -> lagCompensation = (i == 1)));
        l.add(new ModeSetting("Smooth Ping", new String[]{"OFF", "ON"}, () -> smoothPing ? 1 : 0, i -> smoothPing = (i == 1)));
        l.add(new SliderSetting("Send Rate", 10, 60, 5, () -> packetSendRate, v -> {
            packetSendRate = (int) v;
            packetInterval = 1000 / packetSendRate;
        }, "pps"));
        return l;
    }

    public static boolean isActive() {
        if (VisualsMod.moduleManager == null) return false;
        NetworkOptimizer m = VisualsMod.moduleManager.getModule(NetworkOptimizer.class);
        return m != null && m.isEnabled();
    }

    public static boolean canSendPacket() {
        if (!isActive() || !packetBatching) return true;
        
        long now = System.currentTimeMillis();
        if (now - lastPacketTime >= packetInterval) {
            lastPacketTime = now;
            return true;
        }
        return false;
    }

    public static int getSmoothedPing(int currentPing) {
        if (!isActive() || !smoothPing) return currentPing;
        
        return currentPing;
    }
}
