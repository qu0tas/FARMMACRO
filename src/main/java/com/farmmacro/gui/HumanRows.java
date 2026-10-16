package com.farmmacro.gui;

import com.farmmacro.macro.HumanSettings;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Строки настроек «Случайности» — общие для окна ⚙ макроса/маршрута и вкладки «Конфиги» (общая случайность).
 * route/record — какие разделы показывать (во вкладке «Конфиги» — оба).
 */
final class HumanRows {
    private HumanRows() {}

    /**
     * @param edit    обёртка правки (помечает «изменено», сохраняет)
     * @param rebuild пересобрать строки (после «Включить»)
     */
    static void build(List<Rows.Row> rows, HumanSettings h, boolean route, boolean record,
                      BooleanSupplier editable, Consumer<Runnable> edit, Runnable rebuild) {
        if(route)rows.add(new Rows.Note("Это настройки Kaelix. При выборе Aether в Kaelix Motion старый Humanizer маршрута не применяется; значения сохранены для переключения назад. Запись использует свой Humanizer.",Ui.SUB));
        BooleanSupplier on = () -> editable.getAsBoolean() && h.enabled;
        class N {
            Rows.Row n(String label, String tip, DoubleSupplier get, DoubleConsumer set, double min, double max,
                       double step, double big, DoubleFunction<String> fmt, boolean integer) {
                Rows.Number r = new Rows.Number(label, tip, get, v -> edit.accept(() -> set.accept(v)), min, max, step, big, fmt);
                if (integer) r.integer();
                return r.enabledIf(on);
            }
            /** Поле для диапазона «от – до» (подпись и подсказка — у строки Range). */
            Rows.Number num(DoubleSupplier get, DoubleConsumer set, double min, double max, double step, double big,
                            DoubleFunction<String> fmt) {
                Rows.Number r = new Rows.Number("", null, get, v -> edit.accept(() -> set.accept(v)), min, max, step, big, fmt);
                r.integer();
                return r;
            }
            Rows.Row range(String label, String tip, Rows.Number lo, Rows.Number hi) {
                return new Rows.Range(label, tip, lo, hi).enabledIf(on);
            }
        }
        N b = new N();
        rows.add(new Rows.Toggle("Включить", null, () -> h.enabled, v -> { edit.accept(() -> h.enabled = v); rebuild.run(); })
                .enabledIf(editable));
        if (!h.enabled) return;
        rows.add(new Rows.TextField("Сид", "0 — новый каждый запуск (виден в логе); число — запуск повторяется", () -> String.valueOf(h.seed), v -> {
            try {
                long n = Long.parseLong(v.trim());
                if (n != h.seed) edit.accept(() -> h.seed = n);
                return true;
            } catch (NumberFormatException e) { return false; }
        }, Rows.TextField.numeric(), 19, 110).enabledIf(editable).adv());

        rows.add(new Rows.Section("Круги"));
        rows.add(new Rows.Note("Идеальный круг — точно как маршрут/запись: без смазанных углов, остановок и шума камеры.", Ui.SUB).adv());
        rows.add(b.n("Идеальный круг: шанс", "Круг пройдёт ровно так, как записан/построен", () -> h.perfectLapChance,
                v -> h.perfectLapChance = (int) v, 0, 100, 1, 5, HumanRows::pc, true));
        rows.add(b.n("Серия идеальных: шанс", "Если круг выпал идеальным — несколько идеальных подряд", () -> h.perfectStreakChance,
                v -> h.perfectStreakChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true));
        rows.add(b.range("Серия идеальных: кругов", null,
                b.num(() -> h.streakMin, v -> { h.streakMin = (int) v; if (h.streakMax < h.streakMin) h.streakMax = h.streakMin; }, 1, 100, 1, 2, v -> (int) v + " кр."),
                b.num(() -> h.streakMax, v -> h.streakMax = Math.max(h.streakMin, (int) v), 1, 100, 1, 2, v -> (int) v + " кр."))
                .showIf(() -> h.perfectStreakChance > 0));
        rows.add(b.n("Встать посреди пути: шанс", "За круг: постоять и пойти дальше" + (record ? " (запись — только где игрок и так стоял)" : ""),
                () -> h.midStopChance, v -> h.midStopChance = (int) v, 0, 100, 1, 5, HumanRows::pc, true));
        rows.add(b.range("Встать: сколько", "От – до",
                b.num(() -> h.midStopMin, v -> { h.midStopMin = (int) v; if (h.midStopMax < h.midStopMin) h.midStopMax = h.midStopMin; }, 1, 6000, 1, 20, HumanRows::tks),
                b.num(() -> h.midStopMax, v -> h.midStopMax = Math.max(h.midStopMin, (int) v), 1, 6000, 1, 20, HumanRows::tks))
                .showIf(() -> h.midStopChance > 0));
        rows.add(b.n("Долго протупить: шанс", "За круг, лучше маленький: постоять долго, «отвлёкся»", () -> h.afkChance,
                v -> h.afkChance = (int) v, 0, 100, 1, 1, HumanRows::pc, true));
        rows.add(b.range("Протупить: сколько", "От – до",
                b.num(() -> h.afkMin, v -> { h.afkMin = (int) v; if (h.afkMax < h.afkMin) h.afkMax = h.afkMin; }, 1, 72000, 20, 200, HumanRows::tks),
                b.num(() -> h.afkMax, v -> h.afkMax = Math.max(h.afkMin, (int) v), 1, 72000, 20, 200, HumanRows::tks))
                .showIf(() -> h.afkChance > 0));

        if (route) {
            rows.add(new Rows.Section("Углы и отрезки"));
            rows.add(b.n("Радиус точки +", "Поворот раньше, чуть срезая угол", () -> h.reachJitter, v -> h.reachJitter = v,
                    0, 1, 0.01, 0.05, v -> "+" + Rows.num(v) + " бл", false));
            rows.add(b.n("Смазанный поворот: не дальше", "От угла — чтобы не наступить на соседний ряд", () -> h.cornerSlop,
                    v -> h.cornerSlop = v, 0.05, 1, 0.01, 0.05, v -> Rows.num(v) + " бл", false).adv());
            rows.add(new Rows.Toggle("Порядок на углу по весам", "Выкл — как раньше: шанс перекрытия, иначе шанс зазора. Вкл — "
                    + "шанс на угол и веса трёх вариантов: перекрытие, зазор, шаг назад/вбок",
                    () -> h.cornerWeighted, v -> { edit.accept(() -> h.cornerWeighted = v); rebuild.run(); }).enabledIf(on));
            BooleanSupplier weighted = () -> h.cornerWeighted;
            BooleanSupplier legacy = () -> !h.cornerWeighted;
            rows.add(b.n("Не сразу отпустил: шанс", "Старая клавиша держится ещё немного (диагональ на углу)", () -> h.overlapChance,
                    v -> h.overlapChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true).showIf(legacy));
            rows.add(b.n("Не сразу отпустил: до", null, () -> h.overlapMax, v -> h.overlapMax = (int) v, 1, 20, 1, 2, HumanRows::tk, true)
                    .showIf(() -> h.cornerWeighted ? h.weightOverlap > 0 : h.overlapChance > 0));
            rows.add(b.n("Зазор между клавишами: шанс", "На углу на миг ничего не нажато", () -> h.gapChance,
                    v -> h.gapChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true).showIf(legacy));
            rows.add(b.n("Зазор: до", null, () -> h.gapMax, v -> h.gapMax = (int) v, 1, 20, 1, 2, HumanRows::tk, true)
                    .showIf(() -> h.cornerWeighted ? h.weightGap > 0 : h.gapChance > 0));
            rows.add(b.n("Угол: шанс", "Шанс, что на смене направления что-то произойдёт (иначе — ровно)", () -> h.cornerChance,
                    v -> h.cornerChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true).showIf(weighted));
            rows.add(b.n("Вес: перекрытие", "Нажать новую → отпустить старую (короткая диагональ)", () -> h.weightOverlap,
                    v -> h.weightOverlap = (int) v, 0, 100, 1, 10, v -> String.valueOf((int) v), true).showIf(weighted));
            rows.add(b.n("Вес: зазор", "Отпустить старую → нажать новую (миг без клавиш)", () -> h.weightGap,
                    v -> h.weightGap = (int) v, 0, 100, 1, 10, v -> String.valueOf((int) v), true).showIf(weighted));
            rows.add(b.n("Вес: шаг назад/вбок", "Коротко назад или «проскочить» старой клавишей — не дальше «Смазанный поворот»",
                    () -> h.weightStep, v -> h.weightStep = (int) v, 0, 100, 1, 10, v -> String.valueOf((int) v), true).showIf(weighted));
            rows.add(b.n("Шаг: до", null, () -> h.stepMax, v -> h.stepMax = (int) v, 1, 20, 1, 2, HumanRows::tk, true)
                    .showIf(() -> h.cornerWeighted && h.weightStep > 0));
            rows.add(b.n("Заминка в начале отрезка: шанс", "Постоять без клавиш, будто отвлёкся", () -> h.hesitateChance,
                    v -> h.hesitateChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true));
            rows.add(b.n("Заминка: до", null, () -> h.hesitateMax, v -> h.hesitateMax = (int) v, 1, 100, 1, 5, HumanRows::tk, true)
                    .showIf(() -> h.hesitateChance > 0));
            rows.add(b.n("Микропауза после отрезка: шанс", "После каждой точки постоять 0–6 т (гаусс; «Застрял» не считается)",
                    () -> h.microPauseChance, v -> h.microPauseChance = (int) v, 0, 100, 1, 10, HumanRows::pc, true));
            rows.add(b.n("Пауза в точке ±", "Процент от паузы точки", () -> h.pauseJitterPct,
                    v -> h.pauseJitterPct = (int) v, 0, 100, 1, 10, HumanRows::pc, true));
            rows.add(b.n("Пауза в точке ± тиков", null, () -> h.pauseJitterTicks, v -> h.pauseJitterTicks = (int) v, 0, 200, 1, 5, HumanRows::tk, true).adv());
            rows.add(b.n("Старт: подождать до", "Без клавиш в начале прохода", () -> h.startDelayMax,
                    v -> h.startDelayMax = (int) v, 0, 200, 1, 5, HumanRows::tk, true));
            rows.add(b.n("Прыжок позже: до", null, () -> h.jumpDelayMax, v -> h.jumpDelayMax = (int) v, 0, 20, 1, 2, HumanRows::tk, true).adv());
            rows.add(b.n("Спринт позже: до", null, () -> h.sprintDelayMax, v -> h.sprintDelayMax = (int) v, 0, 40, 1, 2, HumanRows::tk, true).adv());
            rows.add(new Rows.Section("Спуск"));
            rows.add(b.range("После приземления", "Добавка к паузе точки-спуска, от – до",
                    b.num(() -> h.landDelayMin, v -> { h.landDelayMin = (int) v; if (h.landDelayMax < h.landDelayMin) h.landDelayMax = h.landDelayMin; }, 0, 200, 1, 5, HumanRows::tk),
                    b.num(() -> h.landDelayMax, v -> h.landDelayMax = Math.max(h.landDelayMin, (int) v), 0, 200, 1, 5, HumanRows::tk)));
            rows.add(b.n("Отпустить у края: до", "После отрыва от края клавиша держится ещё 0…N тиков", () -> h.edgeReleaseMax,
                    v -> h.edgeReleaseMax = (int) v, 0, 20, 1, 2, HumanRows::tk, true).adv());
        }
        if (record) {
            rows.add(new Rows.Section("Запись"));
            rows.add(b.n("Стоячие кадры: повтор/пропуск", "Шанс, пока игрок стоит на месте (паузы чуть длиннее/короче)",
                    () -> h.idleStretchPct, v -> h.idleStretchPct = (int) v, 0, 100, 1, 5, HumanRows::pc, true));
            rows.add(b.n("ЛКМ/ПКМ позже: до", null, () -> h.actionDelayMax, v -> h.actionDelayMax = (int) v, 0, 10, 1, 1, HumanRows::tk, true).adv());
            rows.add(b.n("Шум камеры", "Только с «Повторять камеру»: плавно ± N°", () -> h.camNoiseDeg,
                    v -> h.camNoiseDeg = v, 0, 10, 0.05, 0.5, v -> v == 0 ? "выкл" : "±" + Rows.num(v) + "°", false));
        }
        // v1.11.1: распределения
        rows.add(new Rows.Section("Распределения"));
        rows.add(new Rows.Note("Равномерное — как раньше (пределы из строк выше). Гаусс — чаще около среднего, редко у краёв.", Ui.SUB).adv());
        rows.add(new Rows.Toggle("Без повторов подряд", "То же значение, что в прошлый раз, — перебросить (до 3 раз)",
                () -> h.noRepeat, v -> edit.accept(() -> h.noRepeat = v)).enabledIf(on));
        for (HumanSettings.Var v : HumanSettings.Var.values()) {
            boolean routeVar = v != HumanSettings.Var.ACTION;
            boolean recVar = v == HumanSettings.Var.STOP || v == HumanSettings.Var.AFK || v == HumanSettings.Var.ACTION;
            if (!(route && routeVar || record && recVar)) continue;
            String u = " " + v.unit;
            DoubleFunction<String> f = x -> Rows.num(x) + u;
            BooleanSupplier g = () -> h.dist(v).isGauss();
            rows.add(new Rows.Selector(v.label, v == HumanSettings.Var.PAUSE ? "Гаусс — в % от паузы точки" : null, () -> DIST,
                    () -> h.dist(v).isGauss() ? HumanSettings.Dist.GAUSS : HumanSettings.Dist.UNIFORM, id -> {
                edit.accept(() -> { if (HumanSettings.Dist.GAUSS.equals(id)) h.gauss(v); else if (h.dists != null && h.dists.get(v.name()) != null) h.dists.get(v.name()).type = HumanSettings.Dist.UNIFORM; });
                rebuild.run();
            }).enabledIf(on).adv());
            rows.add(b.n("  среднее", null, () -> h.dist(v).mean, x -> h.gauss(v).mean = x, v.lo, v.hi, 0.5, 5, f, false).showIf(g).adv());
            rows.add(b.n("  отклонение", null, () -> h.dist(v).sd, x -> h.gauss(v).sd = x, 0, v.hi - v.lo, 0.1, 1, f, false).showIf(g).adv());
            rows.add(b.n("  минимум", null, () -> h.dist(v).min, x -> h.gauss(v).min = x, v.lo, v.hi, 1, 5, f, false).showIf(g).adv());
            rows.add(b.n("  максимум", null, () -> h.dist(v).max, x -> h.gauss(v).max = x, v.lo, v.hi, 1, 5, f, false).showIf(g).adv());
        }

        rows.add(new Rows.Section("Камера и зажим"));
        rows.add(new Rows.Toggle("Камера точно", "Случайность не трогает камеру совсем: взгляд на ходу, оглядеться, пресет ±, шум записи — выкл",
                () -> h.cameraExact, v -> { edit.accept(() -> h.cameraExact = v); rebuild.run(); }).enabledIf(on));
        java.util.function.BooleanSupplier camOn = () -> !h.cameraExact;
        rows.add(b.n("Взгляд на ходу ±", "Pitch чуть «гуляет» (yaw не трогаем — от него зависит направление)", () -> h.lookPitchDeg,
                v -> h.lookPitchDeg = v, 0, 5, 0.05, 0.25, v -> v == 0 ? "выкл" : "±" + Rows.num(v) + "°", false).showIf(camOn));
        rows.add(b.n("Оглядеться на остановке ±", "Yaw и pitch, возвращается до начала движения", () -> h.stopLookDeg,
                v -> h.stopLookDeg = v, 0, 20, 0.1, 1, v -> v == 0 ? "выкл" : "±" + Rows.num(v) + "°", false).showIf(camOn));
        rows.add(b.n("Пресет камеры ±", "Привязка пресетов: каждый раз чуть не точно", () -> h.bindJitterDeg,
                v -> h.bindJitterDeg = v, 0, 10, 0.05, 0.5, v -> v == 0 ? "точно" : "±" + Rows.num(v) + "°", false).showIf(camOn).adv());
        rows.add(b.n("Зажим мыши позже: до", "К задержке старта зажима", () -> h.holdDelayJitter,
                v -> h.holdDelayJitter = (int) v, 0, 200, 1, 5, HumanRows::tk, true).adv());
    }

    private static final List<Rows.Choice> DIST = List.of(new Rows.Choice(HumanSettings.Dist.UNIFORM, "равномерное"),
            new Rows.Choice(HumanSettings.Dist.GAUSS, "гаусс"));

    static String tk(double v) { return (int) v + " т"; }
    static String pc(double v) { return (int) v + " %"; }
    static String tks(double v) { return (int) v + " т · " + String.format(java.util.Locale.ROOT, "%.1f", v / 20) + " с"; }
}
