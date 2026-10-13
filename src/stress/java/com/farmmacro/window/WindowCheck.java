package com.farmmacro.window;

import com.farmmacro.camera.MousematMath;
import com.farmmacro.panic.ReactionPlan;
import com.farmmacro.visual.NickText;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/** v1.16.3: картинка в картинке (место окна), скрытие ника, Squeaky Mousemat, человеческая реакция — без игры. */
public final class WindowCheck {
    private WindowCheck() {}

    static int checks;
    static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("WindowCheck: " + what); checks++; }

    public static void main() {
        pip();
        nick();
        mousemat();
        reaction();
        System.out.printf("WindowCheck (v1.16.3 PiP, ник, Mousemat, реакция): %d проверок — OK%n", checks);
    }

    static void pip() {
        // монитор 1920×1040 (панель задач снизу), окно 480×270, правый низ, отступ 16
        var r = PipMath.place(0, 0, 1920, 1040, 480, 270, 3, 16);
        check(r.x() == 1920 - 16 - 480 && r.y() == 1040 - 16 - 270 && r.w() == 480 && r.h() == 270, "правый низ " + r);
        r = PipMath.place(0, 0, 1920, 1040, 480, 270, 0, 16);
        check(r.x() == 16 && r.y() == 16, "левый верх " + r);
        r = PipMath.place(-1920, 0, 1920, 1080, 480, 270, 1, 16);
        check(r.x() == -16 - 480 && r.y() == 16, "второй монитор слева, правый верх " + r);
        // больше экрана — ужимается внутрь
        r = PipMath.place(0, 0, 800, 600, 5000, 5000, 2, 16);
        check(r.w() == 800 - 32 && r.h() == 600 - 32 && r.x() == 16 && r.y() == 16, "ужать до области " + r);
        r = PipMath.place(0, 0, 1920, 1080, 10, 10, 3, 16);
        check(r.w() == PipMath.MIN_W && r.h() == PipMath.MIN_H, "минимум " + r);
        for (int corner = -3; corner < 8; corner++) {
            var q = PipMath.place(100, 50, 1280, 720, 400, 225, corner, 20);
            check(q.x() >= 100 && q.y() >= 50 && q.x() + q.w() <= 1380 && q.y() + q.h() <= 770, "внутри области, угол " + corner);
        }
        int[][] areas = {{0, 0, 1920, 1040}, {1920, 0, 2560, 1400}};
        check(PipMath.monitorFor(areas, 2500, 500) == 1, "второй монитор");
        check(PipMath.monitorFor(areas, 100, 100) == 0, "первый монитор");
        check(PipMath.monitorFor(areas, -5000, 100) == 0, "мимо — первый");
        check(PipMath.monitorFor(new int[0][], 0, 0) == -1, "без мониторов");
    }

    static void nick() {
        var pairs = NickText.pairs("Vadik", "Player", true, Set.of("terina", "vadik", "bob"), "Friend");
        check(pairs.size() == 3, "свой ник не дублируется в друзьях: " + pairs);
        check(pairs.get(0).from().length() >= pairs.get(pairs.size() - 1).from().length(), "длинные первыми");
        check(NickText.replace("<Vadik> hi", pairs).equals("<Player> hi"), "чат");
        check(NickText.replace("[MVP+] vadik: hello", pairs).equals("[MVP+] Player: hello"), "без учёта регистра");
        check(NickText.replace("Vadiks Vadik_2 xVadik", pairs).equals("Vadiks Vadik_2 xVadik"), "только целое слово");
        check(NickText.replace("§aVadik§r joined", pairs).equals("§aPlayer§r joined"), "код цвета перед ником — граница");
        check(NickText.replace("Bob и Terina тут", pairs).equals("Friend и Friend тут"), "друзья");
        check(NickText.replace("Vadik,Vadik.", pairs).equals("Player,Player."), "несколько подряд");
        var own = NickText.pairs("Vadik", "", false, Set.of("bob"), "x");
        check(own.size() == 1 && own.get(0).to().equals("Player"), "пустая замена → Player, друзья выкл");
        check(NickText.replace("Bob", own).equals("Bob"), "друзей не трогаем, если выкл");
        check(NickText.clean("§cОченьдлинноеимяещёдлиннее", "P").length() == NickText.MAX_TEXT, "обрезка и без §");
        int[] cps = "a Vadik b".codePoints().toArray();
        var hits = NickText.find(cps, pairs);
        check(hits.size() == 1 && hits.get(0)[0] == 2 && hits.get(0)[1] == 7, "find по кодовым точкам");
        check(NickText.find("Вадик Vadik".codePoints().toArray(), pairs).size() == 1, "кириллица рядом");
        check(NickText.replace("", pairs).isEmpty() && NickText.replace(null, pairs) == null, "пусто");
        check(NickText.pairs(null, "P", false, null, "F").isEmpty(), "без ника — нет замен");
    }

    static void mousemat() {
        check(MousematMath.isMat("SQUEAKY_MOUSEMAT", "x"), "по id");
        check(MousematMath.isMat(null, "§6Squeaky Mousemat"), "по имени с кодом");
        check(MousematMath.isMat(null, "§dShiny Squeaky Mousemat ✪"), "с префиксом");
        check(!MousematMath.isMat("RANCHERS_BOOTS", "Rancher's Boots"), "не он");
        boolean[] mats = {false, false, false, true, false, true, false, false, false};
        check(MousematMath.pickSlot(mats, 0, 0) == 3, "любой — первый слева");
        check(MousematMath.pickSlot(mats, 0, 5) == 5, "любой — текущий, если он Mousemat");
        check(MousematMath.pickSlot(mats, 6, 0) == 5, "слот 6");
        check(MousematMath.pickSlot(mats, 1, 3) == -1, "в слоте 1 нет");
        check(MousematMath.pickSlot(new boolean[9], 0, 0) == -1, "нет вообще");
        check(MousematMath.matches(179.8f, 3.0f, -179.9f, 3.2f, 0.5), "через ±180");
        check(!MousematMath.matches(90f, 3.0f, 90f, 4.0f, 0.5), "pitch дальше допуска");
        check(MousematMath.matches(-45.04f, 10f, -45f, 10f, 0.05), "точно");
    }

    static void reaction() {
        List<String> ph = ReactionPlan.parsePhrases(" ? | /tp me | wtf\n§chuh |  | " + "x".repeat(100));
        check(ph.equals(List.of("?", "tp me", "wtf", "huh", "x".repeat(ReactionPlan.MAX_PHRASE_LEN))), "фразы: " + ph);
        check(ReactionPlan.parsePhrases(ReactionPlan.DEFAULT_PHRASES).size() >= 5, "фразы по умолчанию");
        var spec = new ReactionPlan.Spec(1, 3, 300, 900, 60, 1500, 4000, ReactionPlan.parsePhrases(ReactionPlan.DEFAULT_PHRASES));
        int chats = 0;
        String last = null;
        int repeats = 0;
        for (int seed = 0; seed < 2000; seed++) {
            var p = ReactionPlan.make(spec, new SplittableRandom(seed), last);
            check(p.looks().size() >= 1 && p.looks().size() <= 3, "1–3 поворота");
            check(p.looks().get(0).atMs() >= 300 && p.looks().get(0).atMs() <= 900, "задержка реакции");
            long prev = -1;
            for (var l : p.looks()) {
                check(l.atMs() > prev, "по порядку");
                prev = l.atMs();
                check(Math.abs(l.offYaw()) <= 75 && Math.abs(l.offPitch()) <= 18 && l.speed() >= 140 && l.speed() <= 320, "границы поворота");
            }
            if (p.looks().size() >= 2) {
                var e = p.looks().get(p.looks().size() - 1);
                check(Math.abs(e.offYaw()) <= 8 && Math.abs(e.offPitch()) <= 4, "последний — почти обратно");
            }
            if (p.phrase() != null) {
                chats++;
                check(p.chatAtMs() >= 1500 && p.chatAtMs() <= 4000 || p.chatAtMs() == p.looks().get(0).atMs() + 150, "время фразы");
                if (p.phrase().equals(last)) repeats++;
                last = p.phrase();
            }
            // ход: одна фраза максимум, все повороты, конец
            var run = new ReactionPlan.Run(p);
            int looks = 0, said = 0;
            for (long t = 0; t < 20000 && !run.finished(t); t += 50) {
                while (run.dueLook(t) != null) looks++;
                if (run.dueChat(t) != null) said++;
            }
            check(looks == p.looks().size() && said == (p.phrase() != null ? 1 : 0), "ход плана");
            check(seed % 7 != 0 || ReactionPlan.make(spec, new SplittableRandom(seed), null).looks().equals(
                    ReactionPlan.make(spec, new SplittableRandom(seed), null).looks()), "сид → тот же план");
        }
        check(chats > 2000 * 0.5 && chats < 2000 * 0.7, "шанс фразы ≈ 60 %: " + chats);
        check(repeats == 0, "фраза не повторяется подряд: " + repeats);
        // отмена: после cancel ничего
        var p = ReactionPlan.make(new ReactionPlan.Spec(3, 3, 0, 0, 100, 0, 0, List.of("?")), new SplittableRandom(1), null);
        var run = new ReactionPlan.Run(p);
        check(run.dueLook(0) != null, "первый поворот сразу");
        run.cancel();
        check(run.dueLook(100000) == null && run.dueChat(100000) == null && run.finished(0), "отмена — всё");
        var none = ReactionPlan.make(new ReactionPlan.Spec(0, 0, 0, 0, 0, 0, 0, List.of()), new SplittableRandom(2), null);
        check(none.looks().isEmpty() && none.phrase() == null, "0 поворотов и 0 % — пусто");
        check(new ReactionPlan.Run(none).finished(0), "пустой план сразу закончен");
    }
}
