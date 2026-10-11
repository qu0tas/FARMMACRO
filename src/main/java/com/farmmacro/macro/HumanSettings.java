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
    /** v1.11.2: «Камера точно» — случайность камеру не трогает совсем (взгляд, оглядеться, пресет ±, шум записи — 0). */
    public boolean cameraExact = false;

    // ── круги (v1.10) ──
    /** Шанс, что круг пройдёт «идеально» — точно как маршрут/запись, без случайности и остановок. */
    public int     perfectLapChance = 8;
    /** Если круг выпал идеальным: шанс, что подряд будет серия идеальных streakMin…streakMax кругов. */
    public int     perfectStreakChance = 30, streakMin = 2, streakMax = 4;
    /** Шанс за круг встать посреди маршрута на midStopMin…midStopMax тиков и потом продолжить. */
    public int     midStopChance = 12, midStopMin = 10, midStopMax = 40;
    /** Маленький шанс за круг «долго протупить» — постоять afkMin…afkMax тиков. */
    public int     afkChance = 2, afkMin = 100, afkMax = 500;

    // ── v1.11.1: распределения, порядок на углах, микропаузы ──
    /**
     * Распределение одной случайной величины: «равномерное» (как до 1.11.1 — пределы из старых полей, например
     * 1…gapMax) или «гаусс» — усечённое нормальное (mean, sd) в пределах min…max. Тики (у паузы в точке — % от паузы).
     */
    public static final class Dist {
        public static final String UNIFORM = "uniform", GAUSS = "gauss";
        public String type = UNIFORM;
        public double mean, sd, min, max;

        public Dist() {}
        public Dist(double mean, double sd, double min, double max) { this.mean = mean; this.sd = sd; this.min = min; this.max = max; }
        public Dist gauss() { type = GAUSS; return this; }
        public boolean isGauss() { return GAUSS.equals(type); }
        public Dist copy() { Dist d = new Dist(mean, sd, min, max); d.type = type; return d; }
    }

    /** Ключи величин с выбором распределения (порядок — для меню и проверок). */
    public enum Var {
        PAUSE("Пауза в точке", "%", -100, 100, 0, 12, -25, 25),
        HESITATE("Заминка", "т", 1, 100, 4, 2, 1, 10),
        GAP("Зазор на углу", "т", 1, 20, 2, 0.8, 1, 4),
        OVERLAP("Перекрытие на углу", "т", 1, 20, 2, 1, 1, 4),
        STEP("Шаг назад/вбок на углу", "т", 1, 20, 2, 0.8, 1, 3),
        MICRO("Микропауза", "т", 0, 20, 2, 1.5, 0, 6),
        STOP("Встать посреди пути", "т", 1, 6000, 35, 12, 15, 70),
        AFK("Протупить", "т", 1, 72000, 300, 120, 120, 700),
        ACTION("ЛКМ/ПКМ позже", "т", 0, 10, 1, 0.7, 0, 3),
        START("Старт: подождать", "т", 0, 200, 10, 5, 0, 25);

        public final String label, unit;
        /** Допустимые пределы min/max (как у равномерных полей) и значения по умолчанию для гаусса. */
        public final double lo, hi, dMean, dSd, dMin, dMax;
        Var(String label, String unit, double lo, double hi, double dMean, double dSd, double dMin, double dMax) {
            this.label = label; this.unit = unit; this.lo = lo; this.hi = hi;
            this.dMean = dMean; this.dSd = dSd; this.dMin = dMin; this.dMax = dMax;
        }
        public Dist defaults() { return new Dist(dMean, dSd, dMin, dMax); }
    }

    /** Распределения по {@link Var#name()}; нет ключа — равномерное (старые файлы). */
    public java.util.Map<String, Dist> dists = new java.util.LinkedHashMap<>();
    /** «Без повторов подряд»: выпало то же значение, что в прошлый раз у этой величины, — перебросить (до 3 раз). */
    public boolean noRepeat = true;

    /**
     * Порядок действий на углу (v1.11.1). false — как раньше: шанс перекрытия, иначе шанс зазора.
     * true — с шансом cornerChance выбирается один из трёх вариантов по весам: перекрытие («нажать новую → отпустить
     * старую»), зазор («отпустить старую → нажать новую»), шаг — короткое движение назад или вбок (не дальше cornerSlop).
     */
    public boolean cornerWeighted = false;
    public int     cornerChance = 60, weightOverlap = 50, weightGap = 30, weightStep = 20;
    /** Шаг на углу: 1…stepMax тиков (равномерно) — назад против старого направления или «проскочить» без новой клавиши. */
    public int     stepMax = 2;
    /** Микропауза после каждого отрезка маршрута: шанс, % (0 — выкл, как раньше); длительность — {@link Var#MICRO}. */
    public int     microPauseChance = 0;

    /** Распределение величины (никогда не null: нет — равномерное с пределами по умолчанию). */
    public Dist dist(Var v) {
        Dist d = dists == null ? null : dists.get(v.name());
        return d != null ? d : UNIFORM_DEFAULT;
    }
    private static final Dist UNIFORM_DEFAULT = new Dist();

    /** Включить гаусс у величины (если не было — значения по умолчанию). */
    public Dist gauss(Var v) {
        if (dists == null) dists = new java.util.LinkedHashMap<>();
        return dists.computeIfAbsent(v.name(), k -> v.defaults()).gauss();
    }

    /** Задать усечённый гаусс величине (для наборов). */
    public HumanSettings setGauss(Var v, double mean, double sd, double min, double max) {
        Dist d = gauss(v); d.mean = mean; d.sd = sd; d.min = min; d.max = max;
        return this;
    }

    /**
     * Ходьба набора «Человек» (v1.11.1, подобрано configSweep: 1500 кандидатов, перепроверка 200 кругов × 4 фермы × 3 сида).
     * Только поля ходьбы/кругов; камеру, шум и «оглядеться» дописывает ConfigProfiles.
     */
    public static HumanSettings presetHuman() {
        HumanSettings h = new HumanSettings();
        h.enabled = true; h.seed = 0;
        h.reachJitter = 0.18; h.cornerSlop = 0.38;
        h.cornerWeighted = true; h.cornerChance = 50;
        h.weightOverlap = 15; h.weightGap = 60; h.weightStep = 25; h.stepMax = 2;
        h.overlapChance = 25; h.overlapMax = 2; h.gapChance = 35; h.gapMax = 3;
        h.hesitateChance = 5; h.hesitateMax = 7;
        h.pauseJitterPct = 35; h.pauseJitterTicks = 2;
        h.jumpDelayMax = 3; h.sprintDelayMax = 2; h.startDelayMax = 12;
        h.perfectLapChance = 10; h.perfectStreakChance = 10; h.streakMin = 2; h.streakMax = 3;
        h.midStopChance = 20; h.midStopMin = 30; h.midStopMax = 120;
        h.afkChance = 4; h.afkMin = 240; h.afkMax = 600;
        h.microPauseChance = 20;
        h.noRepeat = true;
        h.setGauss(Var.PAUSE, 0, 15, -35, 35);
        h.setGauss(Var.GAP, 2, 0.7, 1, 3);
        h.setGauss(Var.STEP, 1.5, 0.5, 1, 2);
        h.setGauss(Var.MICRO, 2, 1.5, 0, 6);
        h.setGauss(Var.STOP, 60, 25, 30, 120);
        h.setGauss(Var.AFK, 360, 100, 240, 600);
        h.setGauss(Var.START, 5, 3, 0, 12);
        return h;
    }

    /** Ходьба набора «Осторожный» (v1.11.1): углы теснее, чаще «встать»/«протупить»/заминки, круги разнообразнее. */
    public static HumanSettings presetCareful() {
        HumanSettings h = presetHuman();
        h.reachJitter = 0.12; h.cornerSlop = 0.30;
        h.cornerChance = 45; h.weightOverlap = 40; h.weightGap = 25; h.weightStep = 35; h.stepMax = 1;
        h.overlapChance = 45; h.overlapMax = 1; h.gapMax = 3;
        h.hesitateChance = 14; h.hesitateMax = 4;
        h.pauseJitterPct = 27; h.pauseJitterTicks = 1;
        h.jumpDelayMax = 2; h.sprintDelayMax = 1; h.startDelayMax = 14;
        h.perfectLapChance = 6; h.perfectStreakChance = 40; h.streakMin = 2; h.streakMax = 3;
        h.midStopChance = 28; h.midStopMin = 10; h.midStopMax = 40;
        h.afkChance = 7; h.afkMin = 250; h.afkMax = 520;
        h.microPauseChance = 16;
        h.setGauss(Var.PAUSE, 0, 12, -27, 27);
        h.setGauss(Var.HESITATE, 2.5, 1, 1, 4);
        h.setGauss(Var.STOP, 22, 8, 10, 40);
        h.setGauss(Var.AFK, 360, 70, 250, 520);
        h.setGauss(Var.START, 6, 3, 0, 14);
        h.dists.remove(Var.STEP.name());               // шаг ≤1 — выбирать не из чего
        return h;
    }

    /**
     * v1.12.1: своя случайность нового маршрута/записи в наборе «Оптимально» (по настройкам игрока):
     * идеальный круг 15 %, серия 10 % × 1–2, встать 2 % на 10–40 т, протупить 1 % на 100–500 т,
     * радиус +0.12, смазанный поворот 0.35, углы «как раньше» (не сразу отпустил 40 % до 4 т).
     */
    public static HumanSettings presetRouteOptimal() {
        HumanSettings h = new HumanSettings();
        h.enabled = true; h.seed = 0;
        h.perfectLapChance = 15; h.perfectStreakChance = 10; h.streakMin = 1; h.streakMax = 2;
        h.midStopChance = 2; h.midStopMin = 10; h.midStopMax = 40;
        h.afkChance = 1; h.afkMin = 100; h.afkMax = 500;
        h.reachJitter = 0.12; h.cornerSlop = 0.35;
        h.cornerWeighted = false;
        h.overlapChance = 40; h.overlapMax = 4;
        return h;
    }

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
        h.dists = new java.util.LinkedHashMap<>();
        if (dists != null) for (var e : dists.entrySet()) if (e.getValue() != null) h.dists.put(e.getKey(), e.getValue().copy());
        h.noRepeat = noRepeat; h.cameraExact = cameraExact;
        h.cornerWeighted = cornerWeighted; h.cornerChance = cornerChance;
        h.weightOverlap = weightOverlap; h.weightGap = weightGap; h.weightStep = weightStep; h.stepMax = stepMax;
        h.microPauseChance = microPauseChance;
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
        h.cornerChance = ci(h.cornerChance, 0, 100);
        h.weightOverlap = ci(h.weightOverlap, 0, 100); h.weightGap = ci(h.weightGap, 0, 100); h.weightStep = ci(h.weightStep, 0, 100);
        h.stepMax = ci(h.stepMax, 1, 20);
        h.microPauseChance = ci(h.microPauseChance, 0, 100);
        java.util.Map<String, Dist> clean = new java.util.LinkedHashMap<>();
        if (h.dists != null) for (Var v : Var.values()) {
            Dist d = h.dists.get(v.name());
            if (d == null) continue;
            if (!Dist.GAUSS.equals(d.type)) d.type = Dist.UNIFORM;
            d.min = cd(d.min, v.lo, v.hi, v.dMin);
            d.max = cd(d.max, d.min, v.hi, Math.max(d.min, v.dMax));
            d.mean = cd(d.mean, d.min, d.max, (d.min + d.max) / 2);
            d.sd = cd(d.sd, 0, v.hi - v.lo, v.dSd);
            clean.put(v.name(), d);
        }
        h.dists = clean;                                       // неизвестные ключи выкидываются
        return h;
    }
}
