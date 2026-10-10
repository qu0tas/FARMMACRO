package com.farmmacro.macro;

/**
 * «Случайность» одного макроса/маршрута (окно ⚙, раздел «Случайность»). Хранится в {@code settings.human}
 * (макрос v5, маршрут v5); в старых файлах нет — выключено, поведение как в 1.8.0. Чистые данные, без игры.
 * Тики — игровые (20 = 1 с), проценты — 0…100.
 */
public class HumanSettings {
    public boolean enabled = false;
    /** 0 — новый сид на каждый проход (время старта); иначе — один и тот же сид (проход можно повторить). */
    public long    seed = 0;

    // ── маршрут по точкам ──
    /** + к радиусу достижения точки, блоков (поворот раньше, срезая угол; позже — перекрытие клавиш). */
    public double  reachJitter = 0.12;
    /** Насколько смазанный поворот может уйти от угла, блоков (не наступить на соседний ряд). */
    public double  cornerSlop = 0.35;
    /** Шанс на углу держать старую клавишу ещё 1…overlapMax тиков («не сразу отпустил», диагональ). */
    public int     overlapChance = 35, overlapMax = 3;
    /** Шанс на углу отпустить всё на 1…gapMax тиков (зазор между клавишами). */
    public int     gapChance = 20, gapMax = 2;
    /** Шанс в начале отрезка «замешкаться» на 1…hesitateMax тиков без клавиш. По умолчанию выкл. */
    public int     hesitateChance = 0, hesitateMax = 6;
    /** Пауза в точке: ± pauseJitterPct % и ± pauseJitterTicks тиков (только у точек с паузой). */
    public int     pauseJitterPct = 20, pauseJitterTicks = 2;
    /** После приземления (спуск): ещё landDelayMin…landDelayMax тиков к паузе точки. */
    public int     landDelayMin = 0, landDelayMax = 6;
    /** Спуск: после отрыва от края держать клавишу ещё 0…edgeReleaseMax тиков (если «в полёте» — отпустить). */
    public int     edgeReleaseMax = 2;
    /** Прыжок / начало спринта — позже на 0…N тиков. */
    public int     jumpDelayMax = 2, sprintDelayMax = 3;
    /** Старт прохода маршрута: 0…startDelayMax тиков без клавиш. */
    public int     startDelayMax = 10;
    /** Зажим мыши: задержка старта + 0…holdDelayJitter тиков. */
    public int     holdDelayJitter = 6;

    // ── запись ──
    /** Стоячие кадры (игрок не двигается): шанс повторить или пропустить кадр, %. */
    public int     idleStretchPct = 15;
    /** ЛКМ/ПКМ из записи: нажатие/отпускание позже на 0…N тиков. */
    public int     actionDelayMax = 1;
    /** «Повторять камеру»: плавный шум yaw/pitch ± N° (0 — выкл). */
    public double  camNoiseDeg = 0;

    // ── камера ──
    /** Привязка пресетов: разброс yaw/pitch ± N° при каждой установке (0 — точно в пресет). */
    public double  bindJitterDeg = 0;
    /** Пока идёт: плавный шум pitch ± N° (yaw не трогаем — от него зависит направление). 0 — выкл. */
    public double  lookPitchDeg = 0.4;
    /** На остановках/«протупить»: камера плавно гуляет ± N° по yaw и pitch и возвращается до начала движения. */
    public double  stopLookDeg = 2.5;

    // ── круги (v1.10) ──
    /** Шанс, что круг пройдёт «идеально» — точно как маршрут/запись, без случайности и остановок. */
    public int     perfectLapChance = 8;
    /** Если круг выпал идеальным: шанс, что подряд будет серия идеальных streakMin…streakMax кругов. */
    public int     perfectStreakChance = 30, streakMin = 2, streakMax = 4;
    /** Шанс за круг встать посреди маршрута на midStopMin…midStopMax тиков и потом продолжить. */
    public int     midStopChance = 12, midStopMin = 10, midStopMax = 40;
    /** Маленький шанс за круг «долго протупить» — постоять afkMin…afkMax тиков. */
    public int     afkChance = 2, afkMin = 100, afkMax = 500;

    public HumanSettings copy() {
        HumanSettings h = new HumanSettings();
        h.enabled = enabled; h.seed = seed;
        h.reachJitter = reachJitter; h.cornerSlop = cornerSlop;
        h.overlapChance = overlapChance; h.overlapMax = overlapMax; h.gapChance = gapChance; h.gapMax = gapMax;
        h.hesitateChance = hesitateChance; h.hesitateMax = hesitateMax;
        h.pauseJitterPct = pauseJitterPct; h.pauseJitterTicks = pauseJitterTicks;
        h.landDelayMin = landDelayMin; h.landDelayMax = landDelayMax; h.edgeReleaseMax = edgeReleaseMax;
        h.jumpDelayMax = jumpDelayMax; h.sprintDelayMax = sprintDelayMax; h.startDelayMax = startDelayMax;
        h.holdDelayJitter = holdDelayJitter;
        h.idleStretchPct = idleStretchPct; h.actionDelayMax = actionDelayMax; h.camNoiseDeg = camNoiseDeg;
        h.bindJitterDeg = bindJitterDeg; h.lookPitchDeg = lookPitchDeg; h.stopLookDeg = stopLookDeg;
        h.perfectLapChance = perfectLapChance; h.perfectStreakChance = perfectStreakChance; h.streakMin = streakMin; h.streakMax = streakMax;
        h.midStopChance = midStopChance; h.midStopMin = midStopMin; h.midStopMax = midStopMax;
        h.afkChance = afkChance; h.afkMin = afkMin; h.afkMax = afkMax;
        return h;
    }

    private static int ci(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double cd(double v, double lo, double hi, double def) { return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : def; }

    public static HumanSettings sanitize(HumanSettings h) {
        if (h == null) return new HumanSettings();
        h.reachJitter = cd(h.reachJitter, 0, 1, 0.12);
        h.cornerSlop = cd(h.cornerSlop, 0.05, 1, 0.35);
        h.overlapChance = ci(h.overlapChance, 0, 100); h.overlapMax = ci(h.overlapMax, 1, 20);
        h.gapChance = ci(h.gapChance, 0, 100); h.gapMax = ci(h.gapMax, 1, 20);
        h.hesitateChance = ci(h.hesitateChance, 0, 100); h.hesitateMax = ci(h.hesitateMax, 1, 100);
        h.pauseJitterPct = ci(h.pauseJitterPct, 0, 100); h.pauseJitterTicks = ci(h.pauseJitterTicks, 0, 200);
        h.landDelayMin = ci(h.landDelayMin, 0, 200); h.landDelayMax = ci(h.landDelayMax, 0, 200);
        if (h.landDelayMax < h.landDelayMin) h.landDelayMax = h.landDelayMin;
        h.edgeReleaseMax = ci(h.edgeReleaseMax, 0, 20);
        h.jumpDelayMax = ci(h.jumpDelayMax, 0, 20); h.sprintDelayMax = ci(h.sprintDelayMax, 0, 40);
        h.startDelayMax = ci(h.startDelayMax, 0, 200); h.holdDelayJitter = ci(h.holdDelayJitter, 0, 200);
        h.idleStretchPct = ci(h.idleStretchPct, 0, 100); h.actionDelayMax = ci(h.actionDelayMax, 0, 10);
        h.camNoiseDeg = cd(h.camNoiseDeg, 0, 10, 0);
        h.bindJitterDeg = cd(h.bindJitterDeg, 0, 10, 0);
        h.lookPitchDeg = cd(h.lookPitchDeg, 0, 5, 0.4);
        h.stopLookDeg = cd(h.stopLookDeg, 0, 20, 2.5);
        h.perfectLapChance = ci(h.perfectLapChance, 0, 100); h.perfectStreakChance = ci(h.perfectStreakChance, 0, 100);
        h.streakMin = ci(h.streakMin, 1, 100); h.streakMax = ci(h.streakMax, h.streakMin, 100);
        h.midStopChance = ci(h.midStopChance, 0, 100); h.midStopMin = ci(h.midStopMin, 1, 6000); h.midStopMax = ci(h.midStopMax, h.midStopMin, 6000);
        h.afkChance = ci(h.afkChance, 0, 100); h.afkMin = ci(h.afkMin, 1, 72000); h.afkMax = ci(h.afkMax, h.afkMin, 72000);
        return h;
    }
}
