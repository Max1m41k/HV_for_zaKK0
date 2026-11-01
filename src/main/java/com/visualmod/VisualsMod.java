package com.visualmod;

import net.minecraft.client.MinecraftClient;
import com.visualmod.gui.ClickGUI;
import com.visualmod.module.ModuleManager;

import com.visualmod.module.modules.hud.HudPosition;
import com.visualmod.module.modules.hud.HudRenderer;

import com.visualmod.module.modules.hud.NotificationManager;
import com.visualmod.module.modules.render.WorldFeatureRenderer;
import com.visualmod.util.ConflictDetector;
import com.visualmod.util.WindowIconChanger;
import com.visualmod.api.LiteApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisualsMod implements ClientModInitializer {
    public static final String MOD_ID = "visualmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ModuleManager moduleManager;
    public static ClickGUI clickGUI;
    private static KeyBinding guiKey;
    private static boolean configManagerInitialized = false;

    @Override
    public void onInitializeClient() {
        com.visualmod.hv.kit.HvIntegrity.verify();
        LOGGER.info("VisualsMod initializing...");

        com.visualmod.HolyBadge.start();
        ConflictDetector.checkAndProtect();
        com.visualmod.util.LauncherBackground.init();

        com.visualmod.util.ShortcutIcon.start();

        HudPosition.load();
        moduleManager = new ModuleManager();
        autoRestoreAltSession();
        HudRenderer.init();
        WorldFeatureRenderer.init();
        NotificationManager.init();
        com.visualmod.cosmetics.CosmeticRenderer.initWorldPass();
        com.visualmod.module.modules.combat.CombatEffects.init();
        com.visualmod.module.modules.movement.MovementEffects.init();
        com.visualmod.module.modules.render.SkyRenderer.init();
        com.visualmod.particle.ModParticles.init();
        com.visualmod.particle.ModParticles.registerFactories();
        com.visualmod.sound.ModSounds.init();
        com.visualmod.util.WaypointManager.load();
        com.visualmod.util.WaypointHud.init();
        com.visualmod.util.GuiWave.init();
        com.visualmod.util.BeatPulse.init();
        com.visualmod.sound.RespawnCue.init();
        com.visualmod.weapon.SwordInspect.init();
        com.visualmod.util.WorldFade.init();
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visualsmod.opengui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, net.minecraft.client.option.KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (!configManagerInitialized) {
                configManagerInitialized = true;
                com.visualmod.config.ConfigManager.init(moduleManager);
            }

            if (guiKey.wasPressed()) {
                if (clickGUI != null && client.currentScreen == clickGUI) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    if (clickGUI == null) clickGUI = new ClickGUI();
                    com.visualmod.module.modules.hud.GUIAnimations.playOpen();
                    client.setScreen(clickGUI);
                }
            }
            moduleManager.onTick();
            com.visualmod.particle.CustomParticleManager.tick();
            com.visualmod.util.EventGPS.tick(client);
            com.visualmod.util.PvpSafe.tick(client);
            WindowIconChanger.tick(client);
            com.visualmod.util.FileDropHandler.tick(client);
            com.visualmod.util.WelcomeBanner.tick(client);
            com.visualmod.util.WorldFade.tick(client);

        });
        LiteApi.init();
        LOGGER.info("VisualsMod initialized!");
    }

    /** Если в AltManager последний ник отличается от того, под которым запустился лаунчер — подставляем его. */
    private static void autoRestoreAltSession() {
        try {
            com.visualmod.screen.AccountManager.loadAccounts();
            String last = com.visualmod.screen.AccountManager.lastNick();
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (last == null || mc == null || mc.getSession() == null) return;
            String current = mc.getSession().getUsername();
            if (last.equalsIgnoreCase(current)) return;
            com.visualmod.screen.AccountScreen.applySession(com.visualmod.screen.AccountScreen.sessionFor(last));
            LOGGER.info("AltManager: сессия автоматически переключена с {} на {}", current, last);
            if (com.visualmod.screen.AccountManager.isLicensed(last)) {
                com.visualmod.HolyBadge.pingNow();
            }
        } catch (Throwable t) {
            com.visualmod.util.Diag.once("AltManager.autoSwitch", t);
        }
    }
}
