package com.farmmacro.combat;

import com.farmmacro.panic.ServerText;
import java.util.*;
import java.util.regex.Pattern;

/** Local observed names, not proof of ownership, encounter state, tier or server damage. */
public final class BossTargets {
    public static final List<String> BOSSES =
            SlayerCatalog.BRANCHES.stream().flatMap(b -> b.bosses().stream()).toList();
    private static final List<String> NAMED_FORMS =
            SlayerCatalog.BRANCHES.stream().flatMap(b -> b.bossForms().stream()).toList();
    private static final List<Pattern> PATTERNS = NAMED_FORMS.stream()
            .map(n -> Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(n) + "(?![a-z])"))
            .toList();

    public static String canonical(String raw) {
        String dungeon = DungeonTargets.canonical(raw);
        if (!dungeon.isEmpty()) return dungeon;
        String clean = ServerText.clean(raw);
        for (int i = 0; i < NAMED_FORMS.size(); i++)
            if (PATTERNS.get(i).matcher(clean).find()) return NAMED_FORMS.get(i);
        return "";
    }

    public static String label(String selection, String custom) {
        if ("slayers".equals(selection)) return "Slayer: все боссы";
        if ("dungeon:all".equals(selection)) return "Данжи: боссы + мини";
        if ("dungeon:bosses".equals(selection)) return "Данжи: боссы";
        if ("dungeon:minis".equals(selection)) return "Данжи: мини-боссы";
        if ("custom".equals(selection)) return custom == null ? "Своё имя" : custom;
        for (var b : SlayerCatalog.BRANCHES) {
            if (("slayer:" + b.id() + ":boss").equals(selection)) return b.title() + ": босс / формы";
            if (("slayer:" + b.id() + ":encounter").equals(selection)) return b.title() + ": босс + мини / формы";
        }
        return selection == null ? "Slayer" : selection;
    }

    public static boolean matches(String selection, String observed, String custom) {
        if (selection == null || observed == null) return false;
        if (HuntingTargets.selection(selection)) return HuntingTargets.matches(selection, observed);
        if (selection.startsWith("dungeon:")) return DungeonTargets.matches(selection, observed);
        if (selection.startsWith("slayer:")) return SlayerCatalog.matches(selection, observed);
        if ("slayers".equals(selection)) return BOSSES.stream().anyMatch(n -> n.equalsIgnoreCase(observed));
        return observed.equalsIgnoreCase("custom".equals(selection) ? custom : selection);
    }

    public static boolean compatible(String name, String type) {
        if (DungeonTargets.ALL.contains(name)) return DungeonTargets.compatible(name, type);
        if (name.equals("Quazii")) return type.endsWith(".wither_skeleton");
        if (name.equals("Typhoeus")) return type.endsWith(".zombified_piglin");
        if (name.equals("Conjoined Brood")) return type.endsWith(".spider") || type.endsWith(".cave_spider");
        if (!BOSSES.contains(name)) return true;
        if (name.equals("Revenant Horror") || name.equals("Riftstalker Bloodfiend")) return type.endsWith(".zombie");
        if (name.contains("Broodfather")) return type.endsWith(".spider") || type.endsWith(".cave_spider");
        if (name.equals("Sven Packmaster")) return type.endsWith(".wolf");
        if (name.equals("Voidgloom Seraph")) return type.endsWith(".enderman");
        return type.endsWith(".blaze");
    }
    /** Acquire closest crosshair angle, then distance; an existing valid lock is kept by the caller. */
    public static double score(double angle, double distance) {
        return angle + Math.min(64, Math.max(0, distance)) * .2;
    }

    private BossTargets() {}
}
