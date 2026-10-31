package com.visualmod.util;

import java.util.Locale;
import java.util.Set;

public final class ConflictRules {
    private ConflictRules() {}

    public static final Set<String> OWN_IDS = Set.of("visualsmod", "visualmod");

    public static final Set<String> BLOCKED_IDS = Set.of(
            "fevervisual", "wildvisual", "celestial", "celestialclient", "neverhook", "richsense",
            "akrien", "nursultan", "nursultanclient", "expensivesense", "expensive", "deadcode");

    public static final Set<String> BLOCKED_NAME_BRANDS = Set.of(
            "fevervisual", "wildvisual", "celestial", "neverhook", "richsense",
            "akrien", "nursultan", "expensivesense", "deadcode");

    public static boolean isInfrastructure(String id) {
        String n = normalize(id);
        return n.equals("minecraft") || n.equals("java") || n.equals("fabricloader") || n.equals("mixinextras")
                || n.startsWith("fabricapi") || n.startsWith("fabric");
    }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isConflict(String id, String name, Set<String> extraIds, Set<String> allowed) {
        String nid = normalize(id);
        if (nid.isEmpty() || OWN_IDS.contains(nid) || isInfrastructure(id)) return false;
        if (allowed.contains(nid)) return false;

        if (BLOCKED_IDS.contains(nid) || extraIds.contains(nid)) return true;

        String nname = normalize(name);
        for (String brand : BLOCKED_NAME_BRANDS) {
            if (nname.equals(brand) || nname.startsWith(brand) || nname.endsWith(brand)) return true;
        }
        return false;
    }
}
