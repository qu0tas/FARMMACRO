package com.farmmacro.route;

import java.util.Locale;

/**
 * v1.11: «Аномалия скорости» для маршрута по точкам — без зависимостей от игры (проверяется в {@code VelocityCheck}-тесте
 * через ту же кинематику, что WalkSim).
 *
 * Модель — ванильная ходьба по земле с обычным трением (0.6 · 0.91 ≈ 0.546): за тик сдвиг
 * {@code d(n) = d(n−1)·KEEP + dir·speed·(1 − KEEP)}, где speed — {@link WalkCore#WALK}/{@link WalkCore#SPRINT}/
 * {@link WalkCore#SNEAK} по клавишам прошлого тика (0 — клавиш нет). Сравниваем вектор фактического сдвига с ожидаемым:
 * разница больше {@code limit} бл/т — аномалия. Всё, что модель не описывает (в воздухе, прыжок, столкновение, вода,
 * лёд/слизь/песок душ, пакет толчка или телепорта, спуск), — «оправдание»: в этот и следующие {@link #GRACE} тиков
 * не проверяем, ожидание подтягиваем к факту.
 */
public final class VelocityCheck {

    public static final double KEEP = 0.546;
    /** Сколько тиков не проверять после «оправдания» (инерция успевает сойтись с моделью). */
    public static final int GRACE = 3;

    private boolean has;
    private double px, pz, evx, evz, cmdx, cmdz;
    private int grace;
    /** Последняя разница (для лога/теста). */
    public double lastDiff;

    public void reset() { has = false; evx = evz = cmdx = cmdz = 0; grace = GRACE; lastDiff = 0; }

    /**
     * Шаг 1 — до решения автохода: позиция после прошлого тика.
     * @param excuse модель сейчас не применима (см. описание класса)
     * @return текст паники или null
     */
    public String observe(double x, double z, boolean excuse, double limit) {
        if (!has) { has = true; px = x; pz = z; grace = GRACE; return null; }
        double ax = x - px, az = z - pz;
        px = x; pz = z;
        evx = evx * KEEP + cmdx * (1 - KEEP);
        evz = evz * KEEP + cmdz * (1 - KEEP);
        if (excuse) { grace = GRACE; evx = ax; evz = az; lastDiff = 0; return null; }
        double diff = Math.hypot(ax - evx, az - evz);
        lastDiff = diff;
        if (grace > 0) { grace--; evx = ax; evz = az; return null; }   // инерция после «оправдания» — берём факт
        if (diff > limit) {
            String r = String.format(Locale.ROOT, "Аномалия скорости: %.3f бл/т вместо %.3f (разница %.3f > %.3f)",
                    Math.hypot(ax, az), Math.hypot(evx, evz), diff, limit);
            evx = ax; evz = az; grace = GRACE;
            return r;
        }
        return null;
    }

    /** Шаг 2 — после решения автохода: какие клавиши нажаты в этот тик (дадут сдвиг к следующему). */
    public void command(WalkCore.Out o, float yaw) {
        boolean moving = o.forward || o.back || o.left || o.right;
        double speed = !moving ? 0 : o.sneak ? WalkCore.SNEAK : o.sprint ? WalkCore.SPRINT : WalkCore.WALK;
        double[] d = WalkCore.moveDir(o, yaw);
        cmdx = d[0] * speed; cmdz = d[1] * speed;
    }
}
