package com.farmmacro.panic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.14: правила про игроков и чат без Minecraft (проверка — PlayersCheck в routeStress).
 * Идеи — blazebot (GPL-3), код свой: staff-теги в начале имени, игрок без имени, личные сообщения, «You are AFK».
 */
public final class PlayerRules {
    private PlayerRules() {}

    /** Теги staff Hypixel. [YOUTUBE] — не staff (ютубер рядом — просто чужой игрок). */
    public static final List<String> STAFF_TAGS = List.of("OWNER", "ADMIN", "GM", "GAME MASTER", "MOD", "MODERATOR", "HELPER", "MOJANG");

    private static final Pattern LEAD_TAG = Pattern.compile("^\\[([^\\]]{1,24})\\]\\s*");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    /** «[MVP+] Ник: текст», «From [ADMIN] Ник: текст», «Ник: текст». */
    private static final Pattern SENDER = Pattern.compile("^(?:(From|To) )?((?:\\[[^\\]]{1,24}\\]\\s*)*)([A-Za-z0-9_]{1,16})(?: [^:]{0,24})?: ");

    /**
     * Staff-тег среди тегов в начале имени: «[ADMIN] Ник», «[302] [MOD] Ник» (уровень Skyblock впереди).
     * @return тег («ADMIN») или null
     */
    public static String staffRank(String displayName) {
        String s = ServerText.clean(displayName);
        for (int i = 0; i < 4; i++) {
            Matcher m = LEAD_TAG.matcher(s);
            if (!m.find()) return null;
            String tag = m.group(1).strip().toUpperCase(Locale.ROOT);
            if (STAFF_TAGS.contains(tag)) return tag;
            s = s.substring(m.end());
        }
        return null;
    }

    /** Игрок без имени: пустой ник профиля или пустое отображаемое имя (после очистки кодов цвета). */
    public static boolean nameless(String profileName, String displayName) {
        return ServerText.clean(profileName).isEmpty() || (displayName != null && ServerText.clean(displayName).isEmpty());
    }

    /** Личное сообщение Hypixel: «From [RANK] Ник: …». @return ник отправителя или null */
    public static String pmSender(String text) {
        String s = ServerText.clean(text);
        Matcher m = SENDER.matcher(s);
        return m.find() && "From".equals(m.group(1)) ? m.group(3) : null;
    }

    /** Автор строки чата: «[MVP+] Ник: …» / «From … Ник: …» / «Ник: …». null — не похоже на сообщение игрока. */
    public static String chatSender(String text) {
        String s = ServerText.clean(text);
        Matcher m = SENDER.matcher(s);
        return m.find() ? m.group(3) : null;
    }

    public static boolean isAfkWarning(String text) {
        return ServerText.clean(text).contains("You are AFK");
    }

    /** Ники из строки «Ник1, Ник2 ник3» (запятая/пробел/точка с запятой), в нижнем регистре, без повторов. */
    public static Set<String> parseNames(String list) {
        Set<String> out = new LinkedHashSet<>();
        if (list == null) return out;
        for (String w : list.split("[,;\\s]+")) {
            String n = w.strip();
            if (NAME.matcher(n).matches()) out.add(n.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Дописать ник в список (без повтора, без учёта регистра). */
    public static String addName(String list, String name) {
        if (name == null || !NAME.matcher(name).matches()) return list == null ? "" : list;
        if (parseNames(list).contains(name.toLowerCase(Locale.ROOT))) return list;
        String base = list == null ? "" : list.strip();
        return base.isEmpty() ? name : base + ", " + name;
    }

    public static boolean isFriend(Set<String> friends, String name) {
        return name != null && !friends.isEmpty() && friends.contains(ServerText.clean(name).toLowerCase(Locale.ROOT));
    }

    /** Похоже на настоящий ник (не NPC с пустым/служебным именем). */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches() && name.length() >= 3;
    }
}
