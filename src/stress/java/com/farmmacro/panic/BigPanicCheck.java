package com.farmmacro.panic;

import java.util.List;

/** v1.13: проверка без Minecraft — лаги сервера, BPS, текст табло/чата, банвейв. */
public final class BigPanicCheck {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException("BigPanicCheck: " + msg);
    }

    public static void main(String... args) {
        // ── LagGuard ──
        LagGuard g = new LagGuard();
        check(!g.lagging(0), "без пакетов — не лаг");
        long t = 0, game = 0;
        g.onTime(t, game);
        check(!g.lagging(5000), "один пакет — сервер ещё не показал регулярность");
        for (int i = 0; i < 5; i++) { t += 1000; game += 20; g.onTime(t, game); }
        check(!g.lagging(t + 500), "пакеты раз в секунду — не лаг");
        check(Math.abs(g.tps() - 20) < 1e-9, "TPS 20, а " + g.tps());
        check(g.lagging(t + 1400), "1.4 с без пакета — лаг");
        t += 3000; game += 20; g.onTime(t, game);                 // пришёл после 3 с: 20 тиков за 3 с
        check(g.lagging(t + 100), "только что лагало (100 мс после)");
        check(g.lagging(t + 850), "только что лагало (850 мс после)");
        check(!g.lagging(t + 950), "после 0.9 с — уже не лаг");
        check(g.tps() < 20, "TPS упал после лага");
        check(!g.lagging(t + 40_000), "40 с тишины — сервер не шлёт время, не лаг");
        g.reset();
        check(!g.lagging(t + 5000) && Double.isNaN(g.tps()), "сброс");

        // ── BpsMeter ──
        BpsMeter m = new BpsMeter();
        for (int i = 0; i < 100; i++) { if (i % 2 == 0) m.onBroken(); m.tick(true, 100); }
        check(!m.ready(), "прогрев: окно + 2 с ещё не наработано");
        for (int i = 0; i < 40; i++) { if (i % 2 == 0) m.onBroken(); m.tick(true, 100); }
        check(m.ready(), "после прогрева — можно судить");
        check(Math.abs(m.bps() - 10) < 1e-9, "10 BPS, а " + m.bps());
        for (int i = 0; i < 100; i++) m.tick(false, 100);
        check(!m.ready() && Double.isNaN(m.bps()), "стоим без ЛКМ — не судим");
        for (int i = 0; i < 100; i++) m.tick(true, 100);
        check(m.ready() && m.bps() == 0, "идём с ЛКМ, ничего не ломается — 0 BPS");
        m.tick(true, 60);
        check(!m.ready(), "смена окна — кольцо заново");

        // ── ServerText ──
        check(ServerText.closingSeconds(List.of("SKYBLOCK", "§cServer closing: §f0:25")) == 25, "Server closing 0:25");
        check(ServerText.closingSeconds(List.of("Server closing: 1:05")) == 65, "1:05");
        check(ServerText.closingSeconds(List.of("Purse: 1,000")) == -1, "нет строки");
        check(ServerText.clean("Ser\uD83C\uDF82ver §aclosing") .equals("Server closing"), "эмодзи и коды убраны: " + ServerText.clean("Ser\uD83C\uDF82ver §aclosing"));
        check("restarting in".equals(ServerText.restartWord("[Important] This server is restarting in 30 seconds")) ||
              ServerText.restartWord("[Important] This server is restarting in 30 seconds") != null, "рестарт");
        check(ServerText.restartWord("You were spawned in Limbo.") != null, "лимбо");
        check(ServerText.restartWord("Сервер будет перезагружен через минуту") != null, "перезагруз");
        check(ServerText.restartWord("You harvested 64 Wheat") == null, "обычное сообщение");
        check(ServerText.isVisitor("[MVP+] Steve is visiting Your Garden!"), "гость");
        check(!ServerText.isVisitor("Steve joined the lobby"), "не гость");
        check(ServerText.jacobCollected(List.of("Jacob's Contest", "○ Wheat 18m30s", " Collected 12,345")) == 12345, "Collected");
        check(ServerText.jacobCollected(List.of("Jacob's Contest", "Wheat", "§6GOLD §fwith §e101,200")) == 101200, "GOLD with");
        check(ServerText.jacobCollected(List.of("Purse: 5", "Collected 100")) == -1, "без конкурса");

        // ── Банвейв ──
        var s0 = new BanwaveWatch.Sample(0, 1000, 500);
        var s1 = new BanwaveWatch.Sample(300_000, 1010, 502);
        var s2 = new BanwaveWatch.Sample(900_000, 1040, 505);
        var s3 = new BanwaveWatch.Sample(1_200_000, 1070, 506);
        check(BanwaveWatch.bansIn(List.of(s0), 0, 900_000, true) == -1, "один ответ — мало");
        check(BanwaveWatch.bansIn(List.of(s0, s1), 300_000, 900_000, true) == 12, "окно не набрано: с самого старого");
        check(BanwaveWatch.bansIn(List.of(s0, s1, s2, s3), 1_200_000, 900_000, false) == 60, "окно 15 мин: от 5-й минуты, только watchdog");
        check(BanwaveWatch.bansIn(List.of(s0, s1, s2, s3), 1_200_000, 900_000, true) == 64, "с staff");
        var p = BanwaveWatch.parse("{\"success\":true,\"watchdog_lastMinute\":3,\"staff_rollingDaily\":2000,\"watchdog_total\":9000000,"
                + "\"watchdog_rollingDaily\":4000,\"staff_total\":3000000}", 5);
        check(p != null && p.watchdogTotal() == 9_000_000 && p.staffTotal() == 3_000_000 && p.ms() == 5, "разбор ответа");
        check(BanwaveWatch.parse("{\"success\":false,\"cause\":\"Invalid API key\"}", 0) == null, "ошибка API");
        System.out.println("BigPanicCheck: OK");
    }
}
