package com.visualmod.util;

import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public final class KeyNames {
    private KeyNames() {}

    /** Ровно те суффиксы, которые знает ванильный InputUtil$Type (1.21.4) — без unknown/world.*. */
    private static final String[] SUFFIXES = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
            "f13", "f14", "f15", "f16", "f17", "f18", "f19", "f20", "f21", "f22", "f23", "f24", "f25",
            "escape", "enter", "tab", "backspace", "space", "menu", "pause", "caps.lock", "num.lock", "scroll.lock",
            "print.screen", "insert", "delete", "home", "end", "page.up", "page.down",
            "up", "down", "left", "right",
            "left.shift", "right.shift", "left.control", "right.control", "left.alt", "right.alt", "left.win", "right.win",
            "minus", "equal", "grave.accent", "comma", "period", "slash", "backslash", "semicolon", "apostrophe",
            "left.bracket", "right.bracket",
            "keypad.0", "keypad.1", "keypad.2", "keypad.3", "keypad.4", "keypad.5", "keypad.6", "keypad.7", "keypad.8", "keypad.9",
            "keypad.add", "keypad.subtract", "keypad.multiply", "keypad.divide", "keypad.decimal", "keypad.equal", "keypad.enter",
    };

    /** Сокращения, которые печатать удобнее, чем полное имя клавиши. */
    private static final String[][] ABBR = {
            {"del", "delete"}, {"ins", "insert"}, {"pgup", "page.up"}, {"pgdn", "page.down"},
            {"bksp", "backspace"}, {"caps", "caps.lock"}, {"numlk", "num.lock"}, {"scrlock", "scroll.lock"},
            {"prtsc", "print.screen"}, {"apps", "menu"}, {"brk", "pause"},
            {"lshift", "left.shift"}, {"rshift", "right.shift"},
            {"lctrl", "left.control"}, {"rctrl", "right.control"}, {"ctrl", "left.control"}, {"control", "left.control"},
            {"lalt", "left.alt"}, {"ralt", "right.alt"}, {"alt", "left.alt"},
            {"lwin", "left.win"}, {"rwin", "right.win"}, {"win", "left.win"},
            {"lbracket", "left.bracket"}, {"rbracket", "right.bracket"}, {"lbrk", "left.bracket"}, {"rbrk", "right.bracket"},
            {"esc", "escape"}, {"return", "enter"}, {"spc", "space"},
            {"grave", "grave.accent"}, {"mns", "minus"}, {"eql", "equal"},
            {"semi", "semicolon"}, {"apos", "apostrophe"}, {"quot", "apostrophe"},
            {"comm", "comma"}, {"peri", "period"}, {"dot", "period"}, {"slsh", "slash"}, {"bksl", "backslash"},
            {"kpadd", "keypad.add"}, {"kpsub", "keypad.subtract"}, {"kpmul", "keypad.multiply"},
            {"kpdiv", "keypad.divide"}, {"kpdec", "keypad.decimal"}, {"kpeq", "keypad.equal"}, {"kpenter", "keypad.enter"}
    };

    /**
     * Символы клавиш как их печатают: `.macros add ` …`. И верхний, и нижний регистр
     * одного и того же символа ведут на одну клавишу — сдвиг на раскладке учитывать глупо.
     */
    private static final String[][] CHAR_KEYS = {
            {"`", "grave.accent"}, {"~", "grave.accent"},
            {"-", "minus"}, {"_", "minus"}, {"=", "equal"}, {"+", "equal"},
            {"[", "left.bracket"}, {"{", "left.bracket"}, {"]", "right.bracket"}, {"}", "right.bracket"},
            {"\\", "backslash"}, {"|", "backslash"},
            {";", "semicolon"}, {":", "semicolon"}, {"'", "apostrophe"}, {"\"", "apostrophe"},
            {",", "comma"}, {"<", "comma"}, {".", "period"}, {">", "period"},
            {"/", "slash"}, {"?", "slash"},
    };

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final List<String> TOKENS = new ArrayList<>();

    static {
        for (String s : SUFFIXES) {
            String key = "key.keyboard." + s;
            ALIASES.put(s.replace(".", ""), key);
            ALIASES.put(s, key);
        }
        for (String[] p : ABBR) {
            ALIASES.put(p[0], "key.keyboard." + p[1]);
        }
        for (int i = 0; i <= 9; i++) {
            ALIASES.put("numpad" + i, "key.keyboard.keypad." + i);
            ALIASES.put("kp" + i, "key.keyboard.keypad." + i);
        }
        for (String[] p : CHAR_KEYS) {
            ALIASES.put(p[0], "key.keyboard." + p[1]);
        }
        TreeSet<String> set = new TreeSet<>();
        java.util.Set<String> symbols = new java.util.HashSet<>();
        for (String[] p : CHAR_KEYS) symbols.add(p[0]);
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            // В подсказку не просим ни полные имена с точкой (page.up — есть компактный вариант),
            // ни сами символы: список дополняется табом, и «.`» ломает разбор аргументов.
            String k = e.getKey();
            if (!k.contains(".") && !symbols.contains(k)) set.add(k.toUpperCase(Locale.ROOT));
        }
        TOKENS.addAll(set);
    }

    public static final String RESERVED_INSERT = "key.keyboard.insert";

    public static String fromKeyCode(int keyCode, int scanCode) {
        return InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(keyCode, scanCode, 0)).getTranslationKey();
    }

    public static String parseUserInput(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) return null;
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (Character.isLetterOrDigit(c)) return "key.keyboard." + c;
        }
        String hit = ALIASES.get(s);
        if (hit != null) return hit;
        return ALIASES.get(s.replaceAll("[._\\- ]", ""));
    }

    /** Подсказки для списка клавиш: короткие сокращения (DEL, PGUP, LSHIFT...), отсортированные. */
    public static List<String> suggestionTokens() {
        return TOKENS;
    }

    public static String displayLabel(String translationKey) {
        if (translationKey == null) return "";
        if (translationKey.startsWith("key.mouse.")) {
            String b = translationKey.substring("key.mouse.".length());
            switch (b) {
                case "left": return "ЛКМ";
                case "middle": return "СКМ";
                case "right": return "ПКМ";
                default: return b.toUpperCase(Locale.ROOT);
            }
        }
        String s = translationKey.startsWith("key.keyboard.") ? translationKey.substring(13) : translationKey;
        return s.replace('.', ' ').toUpperCase(Locale.ROOT);
    }

    /**
     * Имя клавиши обратно в Key. В 1.21.11 InputUtil.fromTranslationKey разбирает только числовые
     * суффиксы (options.txt хранит коды), а kinds вроде key.keyboard.e он не знает, пока объект
     * не создан, поэтому таблица собирается сама один раз из кодов KEYSYM и MOUSE.
     */
    public static InputUtil.Key keyOf(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) return null;
        Map<String, InputUtil.Key> map = byName();
        InputUtil.Key k = map.get(translationKey);
        return k != null ? k : InputUtil.UNKNOWN_KEY;
    }

    /** Translation-ключ кнопки мыши: 0 = ЛКМ, 1 = СКМ, 2 = ПКМ. */
    public static String fromMouseButton(int button) {
        return InputUtil.Type.MOUSE.createFromCode(button).getTranslationKey();
    }

    private static Map<String, InputUtil.Key> byName() {
        Map<String, InputUtil.Key> m = BY_NAME;
        if (m != null) return m;
        m = new LinkedHashMap<>();
        for (int code = 0; code <= KEY_MAX; code++) {
            put(m, InputUtil.Type.KEYSYM.createFromCode(code));
        }
        for (int code = 0; code <= MOUSE_MAX; code++) {
            put(m, InputUtil.Type.MOUSE.createFromCode(code));
        }
        put(m, InputUtil.UNKNOWN_KEY);
        BY_NAME = m;
        return m;
    }

    private static void put(Map<String, InputUtil.Key> m, InputUtil.Key k) {
        if (k != null && k.getCode() != InputUtil.UNKNOWN_KEY.getCode()) m.putIfAbsent(k.getTranslationKey(), k);
    }

    private static final int KEY_MAX = 400;
    private static final int MOUSE_MAX = 8;
    private static volatile Map<String, InputUtil.Key> BY_NAME;

    public static String migrateLegacy(String stored) {
        if (stored == null || stored.isEmpty()) return null;
        if (stored.startsWith("key.")) return stored;
        if (stored.length() == 1 && Character.isLetterOrDigit(stored.charAt(0))) {
            return "key.keyboard." + stored.toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
