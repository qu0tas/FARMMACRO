package com.farmmacro.panic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * v1.16.3 «Человеческая реакция» на панику, без Minecraft: план (повороты «оглядеться» и одна фраза в чат)
 * и ход по времени. Повороты — смещения от взгляда в момент паники; фраза — не больше одной за панику.
 * Любой ввод игрока ({@link Run#cancel}) прекращает всё сразу. Проверка — ReactionCheck.
 */
public final class ReactionPlan {
    private ReactionPlan() {}

    /** Один поворот: в момент atMs (от паники) повернуть к смещению (offYaw, offPitch) от исходного взгляда со скоростью speed °/с. */
    public record Look(long atMs, float offYaw, float offPitch, double speed) {}

    /** chatAtMs < 0 — без фразы. */
    public record Plan(List<Look> looks, long chatAtMs, String phrase) {
        public long endMs() {
            long e = chatAtMs;
            for (Look l : looks) e = Math.max(e, l.atMs() + turnMs(l, 0, 0) + 200);
            return Math.max(0, e);
        }
    }

    public record Spec(int looksMin, int looksMax, int delayMin, int delayMax,
                       int chatChance, int chatMin, int chatMax, List<String> phrases) {}

    public static final int MAX_LOOKS = 5, MAX_PHRASES = 30, MAX_PHRASE_LEN = 64;
    public static final String DEFAULT_PHRASES = "? | ?? | wtf | huh | lag? | what | bruh | ???";

    /** Фразы через «|» или с новой строки: без пустых, без команд («/…»), без §, до 64 символов, не больше 30. */
    public static List<String> parsePhrases(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String s : text.split("[|\\n]")) {
            String t = s.replaceAll("§.?", "").strip();
            while (t.startsWith("/")) t = t.substring(1).strip();
            if (t.isEmpty()) continue;
            if (t.length() > MAX_PHRASE_LEN) t = t.substring(0, MAX_PHRASE_LEN).strip();
            out.add(t);
            if (out.size() >= MAX_PHRASES) break;
        }
        return out;
    }

    private static int between(SplittableRandom r, int a, int b) {
        if (b < a) { int t = a; a = b; b = t; }
        return a == b ? a : a + r.nextInt(b - a + 1);
    }

    private static double between(SplittableRandom r, double a, double b) { return a + r.nextDouble() * (b - a); }

    /** Сколько мс займёт поворот от (fromYaw, fromPitch) к цели look (оценка по скорости). */
    static long turnMs(Look l, float fromYaw, float fromPitch) {
        double d = Math.hypot(l.offYaw() - fromYaw, l.offPitch() - fromPitch);
        return (long) Math.ceil(d / Math.max(1, l.speed()) * 1000 * 1.3) + 60;
    }

    /**
     * План: первая реакция через delayMin…delayMax мс, 1-й поворот — крупный (25–75° по yaw, ±4–18° по pitch),
     * дальше — в другую сторону, последний (если поворотов ≥ 2) — почти обратно (до 8° от исходного).
     * lastPhrase — прошлая фраза (не повторять подряд, если есть другая).
     */
    public static Plan make(Spec s, SplittableRandom r, String lastPhrase) {
        int lo = Math.max(0, Math.min(MAX_LOOKS, s.looksMin())), hi = Math.max(lo, Math.min(MAX_LOOKS, s.looksMax()));
        int n = between(r, lo, hi);
        long t = between(r, Math.max(0, s.delayMin()), Math.max(0, s.delayMax()));
        List<Look> looks = new ArrayList<>();
        float y = 0, p = 0;
        int sign = r.nextBoolean() ? 1 : -1;
        for (int i = 0; i < n; i++) {
            float ny, np;
            boolean last = i == n - 1 && n >= 2;
            if (last) {
                ny = (float) between(r, -8, 8);
                np = (float) between(r, -4, 4);
            } else if (i == 0) {
                ny = sign * (float) between(r, 25, 75);
                np = (r.nextBoolean() ? 1 : -1) * (float) between(r, 4, 18);
            } else {
                sign = -sign;
                ny = sign * (float) between(r, 15, 60);
                np = (r.nextBoolean() ? 1 : -1) * (float) between(r, 2, 14);
            }
            Look l = new Look(t, ny, np, between(r, 140, 320));
            looks.add(l);
            t += turnMs(l, y, p) + between(r, 250, 900);
            y = ny; p = np;
        }
        long chatAt = -1;
        String phrase = null;
        List<String> ph = s.phrases() == null ? List.of() : s.phrases();
        if (!ph.isEmpty() && r.nextInt(100) < Math.max(0, Math.min(100, s.chatChance()))) {
            int k = r.nextInt(ph.size());
            if (ph.size() > 1 && ph.get(k).equals(lastPhrase)) k = (k + 1 + r.nextInt(ph.size() - 1)) % ph.size();
            phrase = ph.get(k);
            int a = Math.max(0, s.chatMin()), b = Math.max(a, s.chatMax());
            chatAt = Math.max(between(r, a, b), looks.isEmpty() ? 0 : looks.get(0).atMs() + 150);
        }
        return new Plan(List.copyOf(looks), chatAt, phrase);
    }

    /** Ход плана по времени (мс от паники). */
    public static final class Run {
        private final Plan plan;
        private int next;
        private boolean chatDone, cancelled;

        public Run(Plan plan) { this.plan = plan; }

        public Plan plan() { return plan; }
        public boolean cancelled() { return cancelled; }
        public void cancel() { cancelled = true; }

        /** Поворот, который пора начать (по одному за вызов), или null. */
        public Look dueLook(long ms) {
            if (cancelled || next >= plan.looks().size()) return null;
            Look l = plan.looks().get(next);
            if (ms < l.atMs()) return null;
            next++;
            return l;
        }

        /** Фраза, которую пора отправить (один раз), или null. */
        public String dueChat(long ms) {
            if (cancelled || chatDone || plan.phrase() == null || plan.chatAtMs() < 0 || ms < plan.chatAtMs()) return null;
            chatDone = true;
            return plan.phrase();
        }

        public boolean finished(long ms) {
            return cancelled || (next >= plan.looks().size() && (chatDone || plan.phrase() == null) && ms >= plan.endMs());
        }

        public String describe() {
            StringBuilder b = new StringBuilder();
            b.append(plan.looks().size()).append(" поворот(а)");
            if (plan.phrase() != null) b.append(String.format(Locale.ROOT, ", фраза «%s» через %.1f с", plan.phrase(), plan.chatAtMs() / 1000.0));
            else b.append(", без фразы");
            return b.toString();
        }
    }
}
