package com.visualmod.mixin;

import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatCommandMixin {

    /**
     * pvpsafe: и введенный вручную, и макросный /hub идут сюда (ClientPlayerEntity
     * режет «/» и зовёт sendChatCommand), поэтому блокировка одна и работает для биндов тоже.
     */
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void visuals$pvpSafe(String command, CallbackInfo ci) {
        if (com.visualmod.util.PvpSafe.blocks(command)) ci.cancel();
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void visuals$onSendMessage(String content, CallbackInfo ci) {
        if (content.startsWith(".")) {
            String[] args = content.substring(1).split(" ");
            String command = args[0].toLowerCase();
            var player = MinecraftClient.getInstance().player;

            if (command.equals("savecfg") || command.equals("saveconfig")) {
                if (args.length > 1) {
                    boolean ok = ConfigManager.saveConfig(args[1]);
                    String shown = ConfigManager.currentConfigName;
                    if (player != null) player.sendMessage(Text.of(ok
                            ? "§d[HolyVisuals] §aКонфиг §e" + shown + " §aсохранен! §7(модули, настройки, бинды, макросы, косметика, позиции HUD)"
                            : "§d[HolyVisuals] §cНе удалось сохранить конфиг §e" + shown + " (нет доступа к папке visualmod_configs)"), false);
                } else if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cУкажите имя: .saveconfig <имя>"), false);
                ci.cancel();
            }
            else if (command.equals("loadcfg") || command.equals("loadconfig")) {
                if (args.length > 1) {
                    if (ConfigManager.loadConfig(args[1], false)) {
                        String beta = ConfigManager.lastBetaSkipped > 0
                                ? " §7(BETA: " + ConfigManager.lastBetaSkipped + " расширенных модулей не включилось)" : "";
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §bКонфиг §e" + ConfigManager.currentConfigName + " §bзагружен!" + beta), false);
                    } else if (player != null) {
                        String why = ConfigManager.lastError != null ? ConfigManager.lastError
                                : "файл visualmod_configs/" + args[1] + ".json";
                        player.sendMessage(Text.of("§d[HolyVisuals] §cНе загрузил конфиг: §e" + why), false);
                    }
                } else if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cУкажите имя: .loadconfig <имя>"), false);
                ci.cancel();
            }

            else if (command.equals("configs") || command.equals("configlist")) {
                if (player != null) {
                    java.io.File dir = new java.io.File(MinecraftClient.getInstance().runDirectory, "visualmod_configs");
                    java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
                    StringBuilder sb = new StringBuilder("§d[HolyVisuals] §7конфиги: ");
                    if (files == null || files.length == 0) sb.append("§8нет");
                    else {
                        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        for (int i = 0; i < files.length; i++) {
                            String n = files[i].getName().replace(".json", "");
                            sb.append(n.equals(ConfigManager.currentConfigName) ? "§a" : "§7").append(n);
                            if (i < files.length - 1) sb.append("§8, ");
                        }
                    }
                    player.sendMessage(Text.of(sb.toString()), false);
                }
                ci.cancel();
            }

            else if (command.equals("bind")) {
                if (args.length > 1) {
                    String action = args[1].toLowerCase();
                    if (action.equals("add") && args.length > 3) {
                        String key = com.visualmod.util.KeyNames.parseUserInput(args[2]);
                        String modName = args[3].toLowerCase();
                        com.visualmod.module.Module target =
                                ConfigManager.moduleManager.getModuleByName(modName);

                        if (key == null) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cНеизвестная клавиша: §e" + args[2] + " §c(пример: z, f6, rshift, DEL, INS, PGUP, PRTSC)"), false);
                        } else if (key.equals(com.visualmod.util.KeyNames.RESERVED_INSERT)) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cInsert зарезервирован под разблокировку SafeMode."), false);
                        } else if (target == null) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cМодуль " + modName + " не найден!"), false);
                        } else if (target.isHidden()) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cФункция §e" + modName + " §cсейчас скрыта — бинд назначить нельзя."), false);
                        } else if (com.visualmod.util.BindRegistry.setBind(modName, key)) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aБинд установлен: §e" + com.visualmod.util.KeyNames.displayLabel(key) + " §7-> §b" + modName), false);
                        }
                    }
                    else if (action.equals("delete") && args.length > 2) {
                        String key = com.visualmod.util.KeyNames.parseUserInput(args[2]);
                        if (key != null) ConfigManager.keybinds.remove(key);
                        ConfigManager.autoSave();
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aБинд для клавиши §e" + args[2] + " §aудален."), false);
                    }
                    else if (action.equals("clear")) {
                        ConfigManager.keybinds.clear();
                        ConfigManager.autoSave();
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aВсе бинды очищены."), false);
                    }
                } else if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cИспользуйте: .bind <add/delete/clear>"), false);
                ci.cancel();
            }

            else if (command.equals("macros")) {
                if (args.length > 1) {
                    String action = args[1].toLowerCase();
                    if (action.equals("add") && args.length > 3) {
                        String key = com.visualmod.util.KeyNames.parseUserInput(args[2]);
                        if (key == null) {
                            if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cНеизвестная клавиша: §e" + args[2] + " §c(пример: z, f6, rshift, DEL, INS, PGUP, PRTSC)"), false);
                            ci.cancel();
                            return;
                        }
                        StringBuilder cmdBuilder = new StringBuilder();
                        for (int i = 3; i < args.length; i++) {
                            cmdBuilder.append(args[i]).append(" ");
                        }
                        String finalCommand = cmdBuilder.toString().trim();

                        ConfigManager.macros.put(key, finalCommand);
                        ConfigManager.autoSave();
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aМакрос добавлен: §e" + com.visualmod.util.KeyNames.displayLabel(key) + " §7-> §d" + finalCommand), false);
                    }
                    else if (action.equals("list")) {
                        if (player != null) {
                            if (ConfigManager.macros.isEmpty()) {
                                player.sendMessage(Text.of("§d[HolyVisuals] §7Макросов нет. §f.macros add <клавиша> <команда>"), false);
                            } else {
                                player.sendMessage(Text.of("§d[HolyVisuals] §fМакросы (§e" + ConfigManager.macros.size() + "§f):"), false);
                                for (java.util.Map.Entry<String, String> e : ConfigManager.macros.entrySet()) {
                                    player.sendMessage(Text.of("  §e" + com.visualmod.util.KeyNames.displayLabel(e.getKey()) + " §7-> §d" + e.getValue()), false);
                                }
                            }
                        }
                    }
                    else if (action.equals("delete") && args.length > 2) {
                        String key = com.visualmod.util.KeyNames.parseUserInput(args[2]);
                        if (key != null) ConfigManager.macros.remove(key);
                        ConfigManager.autoSave();
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aМакрос для клавиши §e" + args[2] + " §aудален."), false);
                    }
                    else if (action.equals("clear")) {
                        ConfigManager.macros.clear();
                        ConfigManager.autoSave();
                        if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aВсе макросы очищены."), false);
                    }
                } else if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §cИспользуйте: .macros <add/list/delete/clear>"), false);
                ci.cancel();
            }

            else if (command.equals("cfg")) {
                if (player != null) player.sendMessage(Text.of(com.visualmod.util.ConfigGuard.command(args)), false);
                ci.cancel();
            }

            else if (command.equals("gps")) {
                String feedback = com.visualmod.util.WaypointManager.command(args, MinecraftClient.getInstance());
                if (player != null) player.sendMessage(Text.of(feedback), false);
                ci.cancel();
            }

            else if (command.equals("lang")) {
                String v = args != null && args.length >= 2 ? args[1].toLowerCase() : "";
                if (v.equals("ru") || v.equals("рус") || v.equals("russian")) {
                    com.visualmod.lang.Lang.mode = com.visualmod.lang.Lang.Mode.RU;
                    if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aЯзык интерфейса: русский."), false);
                } else if (v.equals("en") || v.equals("english")) {
                    com.visualmod.lang.Lang.mode = com.visualmod.lang.Lang.Mode.EN;
                    if (player != null) player.sendMessage(Text.of("§d[HolyVisuals] §aLanguage: English."), false);
                } else if (player != null) {
                    player.sendMessage(Text.of("§d[HolyVisuals] §cИспользуйте: .lang <ru|en>"), false);
                }
                ConfigManager.autoSave();
                ci.cancel();
            }

            else if (command.equals("ct") || command.equals("chattools")) {
                if (player != null && args.length > 1 && args[1].equalsIgnoreCase("pins")) {
                    java.util.List<String> pins = com.visualmod.util.ChatLog.pins();
                    StringBuilder sb = new StringBuilder("§d[HolyVisuals] §7закреплено (§e" + pins.size() + "§7): ");
                    if (pins.isEmpty()) sb.append("§8нет");
                    else for (int i = 0; i < pins.size(); i++) {
                        sb.append("§f").append(pins.get(i));
                        if (i < pins.size() - 1) sb.append("§8 · ");
                    }
                    player.sendMessage(Text.of(sb.toString()), false);
                } else if (player != null && args.length > 1) {
                    String needle = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    java.util.List<com.visualmod.util.ChatLog.Entry> found = com.visualmod.util.ChatLog.query(needle, 0);
                    player.sendMessage(Text.of("§d[HolyVisuals] §7найдено §e" + found.size() + " §7по «§f" + needle + "§7». §e.ct §7— окно поиска"), false);
                    for (int i = 0; i < Math.min(5, found.size()); i++) {
                        com.visualmod.util.ChatLog.Entry e = found.get(i);
                        player.sendMessage(Text.of("  §8" + com.visualmod.util.ChatLog.clock(e.timeMs, 1) + " §f" + e.text), false);
                    }
                } else if (player != null) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (!com.visualmod.module.modules.util.ChatTools.active()) {
                        player.sendMessage(Text.of("§d[HolyVisuals] §cМодуль §eChatTools §cвыключен — включи его, потом .ct"), false);
                    } else if (mc != null) {
                        mc.setScreen(new com.visualmod.screen.ChatToolsScreen(mc.currentScreen));
                        player.sendMessage(Text.of("§d[HolyVisuals] §7поиск по чату: §fклик по строке копирует, ☆ закрепляет"), false);
                    }
                }
                ci.cancel();
            }

            else if (command.equals("media") || command.equals("play") || command.equals("playpause")) {
                String feedback = com.visualmod.util.MediaCommand.run(args);
                if (player != null) {
                    for (String line : feedback.split("\n")) player.sendMessage(Text.of("§d[HolyVisuals] " + line), false);
                }
                ci.cancel();
            }
        }
    }
}
