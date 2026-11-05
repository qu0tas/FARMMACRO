package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.camera.CameraPresets;
import com.farmmacro.camera.CropPresets;
import com.farmmacro.macro.MacroManager;
import java.util.function.BooleanSupplier;

/**
 * 2.1.0: вкладка «Запуск» вынесена из {@link FarmMacroScreen} без изменения поведения — тот же набор
 * строк, те же ключи разделов. Общие помощники (toggle/number/cfg/save) остаются в экране.
 */
final class LaunchTab {
    private final FarmMacroScreen s;

    LaunchTab(FarmMacroScreen s) {
        this.s = s;
    }

    void build() {
        MacroManager mm = MacroManager.INSTANCE;
        s.rows.add(new Rows.Selector(
                        "Клавиша запуска играет",
                        MacroManager.keyName(FarmMacroMod.keyPlay) + " — запись по кадрам или маршрут по точкам",
                        () -> FarmMacroScreen.SOURCES,
                        () -> mm.getSourceKind().name(),
                        v -> {
                            if ("ROUTE".equals(v)) mm.useRouteSource();
                            else mm.useRecordingSource();
                        })
                .enabledIf(() -> mm.getState() == MacroManager.State.IDLE));

        s.rows.add(new Rows.Section("Цикл").key("l.loop"));
        s.rows.add(s.toggle(
                "Зациклить",
                "После последнего кадра начинать заново",
                () -> FarmMacroScreen.cfg().loopEnabled,
                v -> FarmMacroScreen.cfg().loopEnabled = v));
        s.rows.add(s.number(
                        "Кругов",
                        "0 — бесконечно",
                        () -> FarmMacroScreen.cfg().loopLimit,
                        v -> FarmMacroScreen.cfg().loopLimit = (int) v,
                        0,
                        1000000,
                        1,
                        10,
                        v -> v == 0 ? "∞" : String.valueOf((int) v))
                .integer()
                .under(() -> FarmMacroScreen.cfg().loopEnabled));
        s.rows.add(s.number(
                        "Лимит времени",
                        "0 — без лимита",
                        () -> FarmMacroScreen.cfg().timeLimitMinutes,
                        v -> FarmMacroScreen.cfg().timeLimitMinutes = (int) v,
                        0,
                        10080,
                        5,
                        30,
                        v -> v == 0 ? "нет" : (int) v + " мин")
                .integer());

        s.rows.add(new Rows.Section("Автостоп").key("l.stop"));
        s.rows.add(s.toggle(
                "Сигнал по окончании",
                "Мягкий «дзинь», когда макрос закончился сам",
                () -> FarmMacroScreen.cfg().finishSoundEnabled,
                v -> FarmMacroScreen.cfg().finishSoundEnabled = v));

        s.rows.add(new Rows.Section("Старт").key("l.start"));
        s.rows.add(s.number(
                        "Обратный отсчёт",
                        "Удобно для записи видео",
                        () -> FarmMacroScreen.cfg().startCountdownSeconds,
                        v -> FarmMacroScreen.cfg().startCountdownSeconds = (int) v,
                        0,
                        600,
                        1,
                        5,
                        v -> v == 0 ? "выкл" : (int) v + " с")
                .integer());
        s.rows.add(new Rows.Note(
                "Обычный старт — рядом с первой точкой. «Маршруты → Начать с места» продолжает ближайший отрезок; автоматического подхода нет.",
                Ui.SUB));
        s.rows.add(s.number(
                        "Точка старта: допуск",
                        "Дальше — отказ запуска; подойди вручную",
                        () -> FarmMacroScreen.cfg().startPointWarnDistance,
                        v -> FarmMacroScreen.cfg().startPointWarnDistance = v,
                        0.01,
                        2,
                        0.05,
                        .2,
                        v -> Rows.num(v) + " бл")
                .adv());
        s.rows.add(new Rows.Selector(
                "Культура",
                "При старте макроса или маршрута повернуть к пресету этой культуры "
                        + "(плавно, как " + MacroManager.keyName(FarmMacroMod.keyCamApply)
                        + "); выбранный пресет — и для "
                        + MacroManager.keyName(FarmMacroMod.keyCamApply)
                        + ". Не действует, если у макроса своя привязка камеры (⚙)",
                () -> FarmMacroScreen.LAUNCH_CROPS,
                () -> FarmMacroScreen.cfg().launchCrop,
                v -> {
                    FarmMacroScreen.cfg().launchCrop = v;
                    int i = CameraPresets.indexForCrop(v);
                    if (i >= 0) FarmMacroScreen.cfg().camSelected = i;
                    FarmMacroScreen.save();
                }));
        s.rows.add(new Rows.Note(
                () -> {
                    String crop = FarmMacroScreen.cfg().launchCrop;
                    if (CropPresets.NONE.equals(crop)) return "";
                    int i = CameraPresets.indexForCrop(crop);
                    return i < 0
                            ? "Нет пресета для этой культуры — добавь во вкладке «Камера» или «Сбросить к стандартным»."
                            : "Пресет: "
                                    + CameraPresets.describe(
                                            FarmMacroScreen.cfg().camPresets.get(i));
                },
                Ui.SUB));
        s.rows.add(s.toggle(
                "Повторять камеру",
                "Повороты камеры из записи (выкл — камера как есть)",
                () -> FarmMacroScreen.cfg().replayCamera,
                v -> FarmMacroScreen.cfg().replayCamera = v));
        s.rows.add(new Rows.Note("Пресеты камеры под культуры (yaw/pitch) — во вкладке «Камера».", Ui.SUB).adv());

        s.rows.add(new Rows.Section("Случайность").key("l.human"));
        s.rows.add(s.toggle(
                "Случайность",
                "Общий выключатель. Настройки — у каждого макроса/маршрута (⚙ → «Случайность») или общие",
                () -> FarmMacroScreen.cfg().humanMaster,
                v -> FarmMacroScreen.cfg().humanMaster = v));
        s.rows.add(new Rows.Note(
                () -> !FarmMacroScreen.cfg().humanMaster
                        ? ""
                        : FarmMacroScreen.cfg().humanUseGlobal
                                ? "Сейчас для всех — общая: «Конфиги → Общая случайность»."
                                : "Своя у каждого макроса/маршрута: ⚙ → «Случайность». Одну на всех — «Конфиги → Общая случайность».",
                Ui.SUB));

        // v1.15 «Задержки рук (3 слоя)»
        s.rows.add(new Rows.Section("Задержки рук (3 слоя)").key("l.delays").closed());
        BooleanSupplier dl = () -> FarmMacroScreen.cfg().delayLayers;
        s.rows.add(s.toggle(
                "Задержки рук",
                "ЛКМ/ПКМ (нажать и отпустить) и смена слота — с задержкой: база + усталость + отвлечения. "
                        + "Только со «Случайностью», не на идеальном круге",
                dl,
                v -> FarmMacroScreen.cfg().delayLayers = v));
        s.addDelayKind(
                "Нажать ЛКМ/ПКМ",
                dl,
                () -> FarmMacroScreen.cfg().delayPressMean,
                v -> FarmMacroScreen.cfg().delayPressMean = v,
                () -> FarmMacroScreen.cfg().delayPressSd,
                v -> FarmMacroScreen.cfg().delayPressSd = v,
                () -> FarmMacroScreen.cfg().delayPressMin,
                v -> FarmMacroScreen.cfg().delayPressMin = v,
                () -> FarmMacroScreen.cfg().delayPressMax,
                v -> FarmMacroScreen.cfg().delayPressMax = v);
        s.addDelayKind(
                "Отпустить ЛКМ/ПКМ",
                dl,
                () -> FarmMacroScreen.cfg().delayReleaseMean,
                v -> FarmMacroScreen.cfg().delayReleaseMean = v,
                () -> FarmMacroScreen.cfg().delayReleaseSd,
                v -> FarmMacroScreen.cfg().delayReleaseSd = v,
                () -> FarmMacroScreen.cfg().delayReleaseMin,
                v -> FarmMacroScreen.cfg().delayReleaseMin = v,
                () -> FarmMacroScreen.cfg().delayReleaseMax,
                v -> FarmMacroScreen.cfg().delayReleaseMax = v);
        s.addDelayKind(
                "Смена слота",
                dl,
                () -> FarmMacroScreen.cfg().delaySlotMean,
                v -> FarmMacroScreen.cfg().delaySlotMean = v,
                () -> FarmMacroScreen.cfg().delaySlotSd,
                v -> FarmMacroScreen.cfg().delaySlotSd = v,
                () -> FarmMacroScreen.cfg().delaySlotMin,
                v -> FarmMacroScreen.cfg().delaySlotMin = v,
                () -> FarmMacroScreen.cfg().delaySlotMax,
                v -> FarmMacroScreen.cfg().delaySlotMax = v);
        s.rows.add(s.number(
                        "Усталость",
                        "Слой 2: к концу разгона задержки длиннее на столько (за сессию, сброс — «Сбросить статистику»)",
                        () -> FarmMacroScreen.cfg().fatiguePct,
                        v -> FarmMacroScreen.cfg().fatiguePct = v,
                        0,
                        200,
                        1,
                        10,
                        v -> Rows.num(v) + " %")
                .under(dl));
        s.rows.add(s.number(
                        "Разгон усталости",
                        "За сколько минут сессии усталость набирается полностью",
                        () -> FarmMacroScreen.cfg().fatigueRampMin,
                        v -> FarmMacroScreen.cfg().fatigueRampMin = v,
                        5,
                        600,
                        5,
                        30,
                        v -> Rows.num(v) + " мин")
                .under(dl)
                .adv());
        s.rows.add(s.number(
                        "Дрейф",
                        "Слой 2: медленные колебания темпа ± столько",
                        () -> FarmMacroScreen.cfg().driftPct,
                        v -> FarmMacroScreen.cfg().driftPct = v,
                        0,
                        50,
                        1,
                        5,
                        v -> Rows.num(v) + " %")
                .under(dl)
                .adv());
        s.rows.add(s.number(
                        "Период дрейфа",
                        "За сколько минут темп заметно меняется",
                        () -> FarmMacroScreen.cfg().driftMin,
                        v -> FarmMacroScreen.cfg().driftMin = v,
                        1,
                        120,
                        1,
                        10,
                        v -> Rows.num(v) + " мин")
                .under(dl)
                .adv());
        s.rows.add(s.number(
                        "Отвлечения",
                        "Слой 3: шанс на действие, что рука «задумается»",
                        () -> FarmMacroScreen.cfg().distractChance,
                        v -> FarmMacroScreen.cfg().distractChance = v,
                        0,
                        20,
                        0.1,
                        1,
                        v -> Rows.num(v) + " %")
                .under(dl));
        s.rows.add(s.number(
                        "Отвлечение от",
                        null,
                        () -> FarmMacroScreen.cfg().distractMinMs,
                        v -> FarmMacroScreen.cfg().distractMinMs = (int) v,
                        50,
                        20000,
                        50,
                        500,
                        v -> (int) v + " мс")
                .integer()
                .under(dl)
                .adv());
        s.rows.add(s.number(
                        "Отвлечение до",
                        null,
                        () -> FarmMacroScreen.cfg().distractMaxMs,
                        v -> FarmMacroScreen.cfg().distractMaxMs = (int) v,
                        50,
                        20000,
                        50,
                        500,
                        v -> (int) v + " мс")
                .integer()
                .under(dl)
                .adv());
        s.rows.add(s.toggle(
                        "Оверлей задержек (HUD)",
                        "Последние задержки, усталость, среднее и разброс — для отладки",
                        () -> FarmMacroScreen.cfg().delayDebugHud,
                        v -> FarmMacroScreen.cfg().delayDebugHud = v)
                .under(dl));
        s.rows.add(new Rows.Note(
                () -> FarmMacroScreen.cfg().delayLayers
                        ? "Задержки округляются до тиков (50 мс) случайно, в среднем — как задано. "
                                + "Стоп, паника и End отпускают всё сразу, без задержек."
                        : "",
                Ui.SUB));

        s.rows.add(new Rows.Section("Блокировка мыши").key("l.mouse"));
        s.rows.add(s.toggle(
                "Блокировать при старте",
                "Мышь не крутит камеру, пока идёт макрос (клавиша M — вручную)",
                () -> FarmMacroScreen.cfg().mouseLockOnStart,
                v -> FarmMacroScreen.cfg().mouseLockOnStart = v));
        s.rows.add(s.toggle(
                "Снимать при стопе",
                "Паника, End и выход из мира снимают блокировку всегда",
                () -> FarmMacroScreen.cfg().mouseUnlockOnStop,
                v -> FarmMacroScreen.cfg().mouseUnlockOnStop = v));
        s.rows.add(s.toggle(
                "Отпускать курсор при старте",
                "Курсор свободен — пользуйся ПК, макрос дальше ходит и ломает (клавиша "
                        + MacroManager.keyName(FarmMacroMod.keyUngrab)
                        + " — вручную). Паника, End и стоп возвращают курсор",
                () -> FarmMacroScreen.cfg().ungrabOnStart,
                v -> FarmMacroScreen.cfg().ungrabOnStart = v));
    }
}
