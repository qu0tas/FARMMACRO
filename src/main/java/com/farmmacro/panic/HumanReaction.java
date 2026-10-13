package com.farmmacro.panic;

import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.SplittableRandom;

/**
 * v1.16.3 «Человеческая реакция» на панику (тест, выкл по умолчанию): после стопа мод «оглядывается» (1–3 поворота
 * «как мышь» с задержкой реакции) и, может быть, пишет одну фразу в чат («?», «wtf», …). Звук, красный экран и
 * окно вперёд — как обычно. Любое движение мыши, клик или клавиша игрока ({@link #onUserInput}) сразу прекращает
 * реакцию — дальше играет человек. Какие детекторы вызывают реакцию — «Паника → Все детекторы», колонка «Реакц.».
 */
public final class HumanReaction {
    private HumanReaction() {}

    /** Первые N мс ввод не считаем: захват курсора после паники сам двигает мышь. */
    private static final long INPUT_GRACE_MS = 350;

    private static ReactionPlan.Run run;
    private static long startMs;
    private static float baseYaw, basePitch;
    private static String lastPhrase;
    private static double lastMx = Double.NaN, lastMy;

    public static boolean running() { return run != null; }

    /** Реагировать ли на этот детектор (null — общая паника без детектора: нет). */
    public static boolean reactsTo(String id) {
        ModConfig c = ModConfig.INSTANCE;
        return id != null && c.reactIds != null && c.reactIds.contains(id);
    }

    public static void onPanic(Minecraft mc, String id, String reason) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.reactEnabled || !reactsTo(id)) return;
        LocalPlayer p = mc.player;
        if (p == null) return;
        var spec = new ReactionPlan.Spec(c.reactLooksMin, c.reactLooksMax, c.reactDelayMin, c.reactDelayMax,
                c.reactChatChance, c.reactChatMinMs, c.reactChatMaxMs, ReactionPlan.parsePhrases(c.reactPhrases));
        var plan = ReactionPlan.make(spec, new SplittableRandom(System.nanoTime()), lastPhrase);
        run = new ReactionPlan.Run(plan);
        startMs = System.currentTimeMillis();
        baseYaw = p.getYRot();
        basePitch = p.getXRot();
        lastMx = Double.NaN;
        EventLog.log("REACT", "старт (" + Detectors.name(id) + "): " + run.describe());
    }

    /** Каждый тик (и вне макроса). */
    public static void tick(Minecraft mc) {
        if (run == null) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null) { stop("вышел из мира"); return; }
        if (mc.screen != null) { stop("открыто окно"); return; }
        long ms = System.currentTimeMillis() - startMs;
        ReactionPlan.Look l;
        while ((l = run.dueLook(ms)) != null)
            SmoothTurn.turnMouse(baseYaw + l.offYaw(), Math.max(-90f, Math.min(90f, basePitch + l.offPitch())), l.speed());
        String phrase = run.dueChat(ms);
        if (phrase != null) {
            lastPhrase = phrase;
            mc.getConnection().sendChat(phrase);
            EventLog.log("REACT", "фраза в чат: " + phrase);
        }
        if (run.finished(ms)) { EventLog.log("REACT", "закончил"); run = null; }
    }

    /** Ввод игрока (клавиша, кнопка мыши) — реакция прекращается, управление у человека. */
    public static void onUserInput(String what) {
        if (run == null) return;
        if (System.currentTimeMillis() - startMs < INPUT_GRACE_MS) return;
        stop("игрок взял управление (" + what + ")");
    }

    /** Движение курсора: больше 3 px от прошлого положения — это игрок. */
    public static void onMouseMove(double x, double y) {
        if (run == null) return;
        if (Double.isNaN(lastMx) || System.currentTimeMillis() - startMs < INPUT_GRACE_MS) { lastMx = x; lastMy = y; return; }
        if (Math.hypot(x - lastMx, y - lastMy) > 3) onUserInput("мышь");
        lastMx = x; lastMy = y;
    }

    public static void stop(String why) {
        if (run == null) return;
        run.cancel();
        run = null;
        SmoothTurn.stop();
        EventLog.log("REACT", "прекращено: " + why);
    }
}
