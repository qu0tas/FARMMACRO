package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v1.14: проверки игроков по идеям blazebot (код свой): staff рядом и в Tab, двойник (мой ник), массовый телепорт,
 * игрок без имени, личные сообщения, «You are AFK». Белый список друзей — {@link #friends}.
 * Всё в главном потоке. Паника — через {@link PanicDetector#flag} / причину из {@link #tick}: только стоп + экран + звук.
 */
final class PlayerGuards {
    private final PanicDetector d;
    private final ArrivalWatch arrivals = new ArrivalWatch();
    private final DwellTimer<Integer> hidden = new DwellTimer<>();
    /** Staff в Tab на старте запуска (null — ещё не смотрели): «появился» — только новые. */
    private Set<UUID> tabStaffSeen;
    private int tickNo;
    private ClientLevel lastLevel;
    private boolean hasLast;
    private double lx, ly, lz;
    private String friendsSrc;
    private Set<String> friends = Set.of();

    PlayerGuards(PanicDetector d) { this.d = d; }

    void beginRun() { tabStaffSeen = null; hidden.clear(); arrivals.reset(); }

    /** Друзья из «Паника → Друзья» (кэш по строке). */
    Set<String> friends() {
        String s = ModConfig.INSTANCE.friendNames;
        if (!java.util.Objects.equals(s, friendsSrc)) { friendsSrc = s; friends = PlayerRules.parseNames(s); }
        return friends;
    }

    boolean isFriend(String name) { return PlayerRules.isFriend(friends(), name); }

    /** Каждый тик, даже без макроса: возраст мира и свои телепорты/варпы (для «Массового телепорта»). */
    void tickAlways(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hasLast = false;
            if (mc.level != null) arrivals.onWorld(now);
        }
        LocalPlayer p = mc.player;
        if (p == null) { hasLast = false; return; }
        if (hasLast) {
            double dx = p.getX() - lx, dy = p.getY() - ly, dz = p.getZ() - lz;
            if (dx * dx + dy * dy + dz * dz > 25) arrivals.onSelfTeleport(now);    // > 5 бл за тик — свой телепорт/варп
        }
        lx = p.getX(); ly = p.getY(); lz = p.getZ(); hasLast = true;
    }

    /** @return причина паники или null. Пока макрос играет и паника включена. */
    String tick(Minecraft mc, LocalPlayer p, ModConfig c) {
        if (mc.level == null) return null;
        long now = System.currentTimeMillis();
        String r = null;
        String me = p.getGameProfile().name();
        Map<Integer, Double> dist = new HashMap<>();
        Set<Integer> hiddenNear = new HashSet<>();
        for (var o : mc.level.players()) {
            if (o == p || o.getId() == p.getId()) continue;
            double dd = o.distanceTo(p);
            String name = o.getGameProfile().name();
            String disp = o.getDisplayName().getString();
            if (r == null && c.detectStaffNear && dd <= c.staffRadius) {
                String tag = PlayerRules.staffRank(disp);
                if (tag != null) r = String.format(Locale.ROOT, "Staff рядом: [%s] %s (%.1f бл)", tag, name, dd);
            }
            if (r == null && c.detectTwin && dd <= c.twinRadius && me != null && me.equalsIgnoreCase(name))
                r = String.format(Locale.ROOT, "Двойник: игрок с твоим ником в %.1f бл", dd);
            if (dd <= c.namelessRadius && PlayerRules.nameless(name, disp)) hiddenNear.add(o.getId());
            if (!(c.friendsIgnoreMass && isFriend(name))) dist.put(o.getId(), dd);
        }
        int n = arrivals.tick(now, dist, c.massTpRadius, c.massTpWindowMs, c.massTpWorldGraceSec * 1000L);
        if (r == null && c.detectMassTp && n >= c.massTpMin)
            r = String.format(Locale.ROOT, "Массовый телепорт: %d игроков появились ближе %.0f бл за %.1f с", n, c.massTpRadius, c.massTpWindowMs / 1000.0);
        Integer h = hidden.update(now, hiddenNear, c.namelessDwellMs);
        if (r == null && c.detectNameless && h != null)
            r = String.format(Locale.ROOT, "Игрок без имени ближе %.0f бл дольше %.1f с (сущность #%d)", c.namelessRadius, c.namelessDwellMs / 1000.0, h);

        if (c.detectStaffNear && c.staffTab && ++tickNo % 10 == 0 && mc.getConnection() != null) {
            Set<UUID> staff = new HashSet<>();
            String who = null;
            for (var info : mc.getConnection().getOnlinePlayers()) {
                UUID id = info.getProfile().id();
                if (id == null || id.equals(p.getUUID())) continue;
                String tag = PlayerRules.staffRank(mc.gui.getTabList().getNameForDisplay(info).getString());
                if (tag == null) continue;
                staff.add(id);
                if (tabStaffSeen != null && !tabStaffSeen.contains(id) && who == null) who = "[" + tag + "] " + info.getProfile().name();
            }
            if (tabStaffSeen == null) {
                tabStaffSeen = staff;
                if (!staff.isEmpty()) EventLog.log("STAFF", "в Tab на старте: " + staff.size() + " (не паника, считаются только новые)");
            } else tabStaffSeen.addAll(staff);
            if (r == null && who != null) r = "Staff появился в Tab: " + who;
        }
        return r;
    }

    /** Системный чат: личное сообщение и «You are AFK». */
    void onSystemText(String text, ModConfig c) {
        if (text == null || text.isBlank()) return;
        if (c.detectPm) {
            String from = PlayerRules.pmSender(text);
            if (from != null) {
                if (c.friendsIgnorePm && isFriend(from)) EventLog.log("FRIEND", "личное сообщение от друга " + from);
                else { d.flag("Личное сообщение от " + from + ": «" + clip(text) + "»"); return; }
            }
        }
        if (c.detectAfkMsg && PlayerRules.isAfkWarning(text)) d.flag("Сервер: «You are AFK»");
    }

    private static String clip(String s) {
        s = ServerText.clean(s);
        return s.length() <= 60 ? s : s.substring(0, 59) + "…";
    }
}
