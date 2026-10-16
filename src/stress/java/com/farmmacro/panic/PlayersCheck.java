package com.farmmacro.panic;

import com.farmmacro.config.HudSlot;
import com.farmmacro.config.ModConfig;
import com.farmmacro.hud.HudLayout;
import com.farmmacro.notify.Webhook;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** v1.14: проверка без Minecraft — правила игроков, массовый телепорт, очереди блоков, тишина, HUD, webhook. */
public final class PlayersCheck {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException("PlayersCheck: " + msg);
    }

    public static void main(String... args) {
        // ── PlayerRules ──
        check("ADMIN".equals(PlayerRules.staffRank("§c[ADMIN] Someone")), "[ADMIN]");
        check("MOD".equals(PlayerRules.staffRank("[302] [MOD] Someone")), "уровень + [MOD]");
        check("GAME MASTER".equals(PlayerRules.staffRank("[GAME MASTER] X")), "[GAME MASTER]");
        check(PlayerRules.staffRank("[YOUTUBE] Star") == null, "[YOUTUBE] — не staff");
        check(PlayerRules.staffRank("[MVP+] Player") == null, "[MVP+] — не staff");
        check(PlayerRules.staffRank("Player [ADMIN]") == null, "тег не в начале — не staff");
        check(PlayerRules.nameless("", "x") && PlayerRules.nameless("Bob", "§r") && !PlayerRules.nameless("Bob", "Bob"), "без имени");
        check("Steve".equals(PlayerRules.pmSender("From [MVP+] Steve: hi")), "ЛС");
        check(PlayerRules.pmSender("To [MVP+] Steve: hi") == null, "своё ЛС — нет");
        check(PlayerRules.pmSender("[MVP+] Steve: hi") == null, "общий чат — не ЛС");
        check("Steve".equals(PlayerRules.chatSender("[MVP+] Steve: hi")), "автор чата");
        check(PlayerRules.isAfkWarning("§cYou are AFK. Move around to return."), "AFK");
        Set<String> fr = PlayerRules.parseNames("Alice, bob;  Carol x!y");
        check(fr.equals(Set.of("alice", "bob", "carol")), "parseNames: " + fr);
        check(PlayerRules.isFriend(fr, "ALICE") && !PlayerRules.isFriend(fr, "dave") && !PlayerRules.isFriend(Set.of(), "a"), "isFriend");
        check(PlayerRules.addName("Alice", "alice").equals("Alice"), "без повтора");
        check(PlayerRules.addName("", "Bob").equals("Bob") && PlayerRules.addName("A1", "Bob").equals("A1, Bob"), "addName");
        check(PlayerRules.addName("A1", "bad name").equals("A1"), "неверный ник не дописывается");
        check(PlayerRules.validName("Bob") && !PlayerRules.validName("ab") && !PlayerRules.validName("§eNPC"), "validName");

        // ── ArrivalWatch ──
        ArrivalWatch a = new ArrivalWatch();
        Map<Integer, Double> far = new HashMap<>(), near = new HashMap<>();
        for (int i = 0; i < 6; i++) { far.put(i, 50.0); near.put(i, 3.0); }
        check(a.tick(0, far, 8, 1000, 10_000) == 0, "без мира — 0");
        a.onWorld(0);
        a.tick(1000, far, 8, 1000, 10_000);
        check(a.tick(1050, near, 8, 1000, 10_000) == 0, "первые 10 с в мире — 0");
        a.tick(20_000, far, 8, 1000, 10_000);
        check(a.tick(20_050, near, 8, 1000, 10_000) == 6, "6 игроков прыгнули ближе");
        a.tick(30_000, far, 8, 1000, 10_000);
        a.onSelfTeleport(30_010);
        check(a.tick(30_050, near, 8, 1000, 10_000) == 0, "после своего телепорта — 0");
        Map<Integer, Double> walk = new HashMap<>();
        for (int i = 0; i < 6; i++) walk.put(i, 20.0);
        a.tick(40_000, walk, 8, 1000, 10_000);
        check(a.tick(40_050, near, 8, 1000, 10_000) == 0, "подошли пешком (было 20 бл) — не телепорт");
        a.tick(50_000, far, 8, 1000, 10_000);
        Map<Integer, Double> two = new HashMap<>(far); two.put(0, 3.0); two.put(1, 3.0);
        check(a.tick(50_050, two, 8, 1000, 10_000) == 2, "двое");
        check(a.tick(52_000, two, 8, 1000, 10_000) == 0, "окно прошло");

        // ── BurstWatch ──
        BurstWatch b = new BurstWatch();
        b.add(0, 1); b.add(100, 1); b.add(200, 2);
        check(b.count(250, 300) == 2, "две разные позиции");
        check(b.count(450, 300) == 1, "старое ушло из окна");
        b.clear();
        check(b.count(500, 300) == 0, "clear");

        // ── DwellTimer ──
        DwellTimer<String> d = new DwellTimer<>();
        check(d.update(0, Set.of("x"), 1000) == null, "только пришёл");
        check(d.update(999, Set.of("x"), 1000) == null, "999 мс");
        check("x".equals(d.update(1000, Set.of("x"), 1000)), "1000 мс");
        d.update(1100, Set.of(), 1000);
        check(d.update(1200, Set.of("x"), 1000) == null, "ушёл и вернулся — заново");
        check(d.update(0, Set.of("z"), 0) != null, "0 мс — сразу");

        // ── SilenceWatch ──
        SilenceWatch s = new SilenceWatch();
        check(!s.shouldClose(100_000, 30_000), "не на сервере");
        s.onJoin(0);
        check(!s.shouldClose(29_000, 20_000), "первые 30 с после входа");
        s.onPacket(31_000);
        check(!s.shouldClose(60_000, 30_000), "29 с тишины — рано");
        check(s.shouldClose(61_000, 30_000), "30 с тишины — закрыть");
        check(!s.shouldClose(62_000, 30_000), "повтор не раньше чем через минуту");
        check(s.silentMs(62_000) == 31_000, "silentMs");
        s.reset();
        check(s.silentMs(70_000) == -1 && !s.shouldClose(200_000, 30_000), "reset");

        // ── LagGuard + тишина ──
        LagGuard g = new LagGuard();
        long t = 0, game = 0;
        for (int i = 0; i < 6; i++) { t += 1000; game += 20; g.onTime(t, game); }
        check(!g.lagging(t + 40_000, 100), "время молчит, пакеты идут — не лаг");
        check(g.lagging(t + 40_000, 40_000), "молчит всё — лаг");
        check(!g.lagging(t + 40_000, -1), "неизвестно — как раньше (не лаг)");

        // ── ChatMatch: слова ЗАГЛАВНЫМИ ──
        String kw = "macro, ROTATED, BEDROCK CAGED";
        check(ChatMatch.hit("You have been ROTATED!", "Me", true, kw) != null, "ROTATED");
        check(ChatMatch.hit("the crops rotated nicely", "Me", true, kw) == null, "rotated строчными — нет");
        check(ChatMatch.hit("UNROTATED", "Me", true, kw) == null, "не целое слово — нет");
        check(ChatMatch.hit("You were BEDROCK CAGED", "Me", true, kw) != null, "две слова");
        check(ChatMatch.hit("is this a MaCrO?", "Me", true, kw) != null, "обычные — без регистра");

        // ── Миграция слов чата ──
        ModConfig mc = new ModConfig();
        mc.chatKeywords = "бот, ROTATED";
        mc.chatKeywordsRev = 0;
        mc.sanitize();
        check(mc.chatKeywords.startsWith("бот, ROTATED") && mc.chatKeywords.contains("KNOCKED OUT")
                && mc.chatKeywords.indexOf("ROTATED") == mc.chatKeywords.lastIndexOf("ROTATED") && mc.chatKeywordsRev >= 1, "миграция: " + mc.chatKeywords);
        String once = mc.chatKeywords;
        mc.sanitize();
        check(once.equals(mc.chatKeywords), "миграция один раз");
        check(new ModConfig().chatKeywords.contains("DIRT CHECKED"), "по умолчанию");

        // ── HudLayout ──
        int sw = 400, sh = 300;
        HudSlot sl = new HudSlot();
        HudLayout.store(sl, 300, 10, 90, 20, sw, sh);
        check(sl.placed && sl.ax == 1 && sl.ay == 0, "правый верх");
        int[] p = HudLayout.place(sl, 90, 20, sw, sh);
        check(p[0] == 300 && p[1] == 10, "туда-обратно " + p[0] + "," + p[1]);
        p = HudLayout.place(sl, 90, 20, 800, 600);
        check(p[0] == 800 - 90 - 10 && p[1] == 10, "у правого края при другом окне");
        HudLayout.store(sl, 155, 140, 90, 20, sw, sh);
        check(sl.ax == 0.5 && sl.ay == 0.5, "центр");
        p = HudLayout.place(sl, 90, 20, sw, sh);
        check(p[0] == 155 && p[1] == 140, "центр туда-обратно");
        check(HudLayout.snap(8, 50, sw) == 6 && HudLayout.snap(400 - 50 - 9, 50, sw) == 344, "к краям");
        check(HudLayout.snap(176, 50, sw) == 175 && HudLayout.snap(100, 50, sw) == 100, "к центру / нет");
        check(HudLayout.snap(-30, 50, sw) == 0 && HudLayout.snap(500, 50, sw) == 350, "на экране");
        check(HudLayout.scaleStep(1, 1) == 1.1 && HudLayout.scaleStep(1, -1) == 0.9, "масштаб ±0.1");
        check(HudLayout.scaleStep(2, 1) == 2 && HudLayout.scaleStep(0.5, -1) == 0.5, "масштаб 0.5–2");
        HudSlot bad = new HudSlot(); bad.scale = Double.NaN; bad.ox = Double.POSITIVE_INFINITY; bad.ax = 0.3; bad.sanitize();
        check(bad.scale == 1 && bad.ox == 6 && bad.ax == 0.5, "HudSlot.sanitize");

        // ── Webhook ──
        String url = "https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz_ABC-123";
        check(Webhook.validUrl(url) && Webhook.validUrl(url.replace("discord.com", "canary.discord.com")), "верный URL");
        check(!Webhook.validUrl("http://discord.com/api/webhooks/1/x") && !Webhook.validUrl("https://evil.com/api/webhooks/123456/abcdefghijklmnopqrstuvwxyz")
                && !Webhook.validUrl(null), "неверный URL");
        String m = Webhook.masked(url);
        check(!m.contains("abcdefghij") && !m.contains("123456789012345678") && m.equals("https://discord.com/api/webhooks/••••/••••"), "маска должна скрывать и ID, и токен: " + m);
        String text = Webhook.panicText("Игрок рядом", "12:00", "mc.hypixel.net");
        check(text.contains("Паника") && text.contains("Игрок рядом") && text.contains("mc.hypixel.net"), "текст");
        check(Webhook.panicText("x".repeat(5000), "t", "s").length() <= Webhook.CONTENT_MAX, "длина");
        String pl = Webhook.payload("@everyone \"q\"");
        check(pl.contains("\"allowed_mentions\":{\"parse\":[]}") && pl.contains("\\\"q\\\""), "payload: " + pl);
        byte[] mp = Webhook.multipart("BND", pl, "pa nic/../x.png", new byte[]{1, 2, 3});
        String ms = new String(mp, StandardCharsets.ISO_8859_1);
        check(ms.startsWith("--BND\r\n") && ms.endsWith("\r\n--BND--\r\n") && ms.contains("filename=\"pa_nic_.._x.png\""), "multipart");

        // ── Reconnect.delaySec ──
        check(com.farmmacro.macro.Reconnect.delaySec(30, 1, true) == 30 && com.farmmacro.macro.Reconnect.delaySec(30, 3, true) == 120, "рост ×2");
        check(com.farmmacro.macro.Reconnect.delaySec(30, 3, false) == 30 && com.farmmacro.macro.Reconnect.delaySec(300, 5, true) == 600, "без роста / потолок");

        System.out.println("PlayersCheck: OK");
    }
}
