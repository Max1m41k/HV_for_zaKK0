package com.visualmod.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.visualmod.config.ConfigManager;
import com.visualmod.module.Module;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class CommandSuggestionsMixin {

    @Shadow @Final TextFieldWidget textField;

    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("TAIL"))
    private void visuals$addCustomDotSuggestions(CallbackInfo ci) {
        String text = this.textField.getText();

        if (text.startsWith(".")) {
            String[] args = text.substring(1).split(" ", -1);
            String currentArg = args[args.length - 1].toLowerCase();

            List<String> variants = new ArrayList<>();

            if (args.length == 1) {
                variants.add(".savecfg");
                variants.add(".loadcfg");
                variants.add(".configs");
                variants.add(".cfg");
                variants.add(".bind");
                variants.add(".macros");
                variants.add(".media");
                variants.add(".play");
                variants.add(".playpause");
                variants.add(".gps");
            }
            else if (args.length == 2) {
                String root = args[0].toLowerCase();
                if (root.equals("bind")) {
                    variants.add("add");
                    variants.add("delete");
                    variants.add("clear");
                } else if (root.equals("macros")) {
                    variants.add("add");
                    variants.add("list");
                    variants.add("delete");
                    variants.add("clear");
                } else if (root.equals("cfg")) {
                    variants.add("restore");
                    variants.add("delete");
                    variants.add("backups");
                } else if (root.equals("gps")) {
                    variants.add("add");
                    variants.add("remove");
                    variants.add("s");
                } else if (root.equals("media")) {
                    variants.add("next");
                    variants.add("prev");
                    variants.add("play");
                    variants.add("stop");
                    variants.add("list");
                    variants.add("refresh");
                    variants.add("folder");
                    variants.add("drop");
                }
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("cfg")
                    && (args[1].equalsIgnoreCase("restore") || args[1].equalsIgnoreCase("delete"))) {
                int have = com.visualmod.util.ConfigGuard.list(ConfigManager.currentConfigName).length;
                for (int i = 1; i <= have; i++) variants.add(String.valueOf(i));
            }
            else if (args.length == 3
                    && (args[0].equalsIgnoreCase("macros") || args[0].equalsIgnoreCase("bind"))
                    && args[1].equalsIgnoreCase("add")) {
                // Список клавиш при создании макроса/бина — как подсказка ников по Tab.
                variants.addAll(com.visualmod.util.KeyNames.suggestionTokens());
            }
            else if (args.length == 4 && args[0].equalsIgnoreCase("bind") && args[1].equalsIgnoreCase("add")) {
                if (ConfigManager.moduleManager != null) {
                    for (Module m : ConfigManager.moduleManager.getVisibleModules()) {
                        variants.add(m.getName().toLowerCase());
                    }
                }
            }

            int startIndex = text.lastIndexOf(' ') + 1;
            SuggestionsBuilder builder = new SuggestionsBuilder(text, startIndex);

            for (String var : variants) {
                // currentArg снят с текста после точки, поэтому сравниваем без ведущей точки,
                // а предлагаем строку целиком — иначе ваниль затирает «.» при подстановке.
                String matchKey = var.startsWith(".") ? var.substring(1) : var;
                if (matchKey.toLowerCase().startsWith(currentArg)) builder.suggest(var);
            }

            this.pendingSuggestions = CompletableFuture.completedFuture(builder.build());

            this.show(false);
        }
    }
}
