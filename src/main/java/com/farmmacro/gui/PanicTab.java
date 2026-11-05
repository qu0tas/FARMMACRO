package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.farmmacro.notify.DiscordNotify;
import com.farmmacro.panic.BanwaveWatch;
import com.farmmacro.panic.LagGuard;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicShot;
import com.farmmacro.panic.PanicSound;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 2.1.0: вкладка «Паника Garden» вынесена из {@link FarmMacroScreen} без изменения поведения — тот же набор
 * строк, те же ключи разделов. Общие помощники (toggle/number/cfg/save) остаются в экране.
 */
final class PanicTab {
    private final FarmMacroScreen s;

    PanicTab(FarmMacroScreen s) {
        this.s = s;
    }

    /** Вкладка «Паника»: детекторы по группам (свёрнуты, справа — сколько включено) + реакция (звук, экран, журнал). */
    void build() {
        BooleanSupplier master = () -> FarmMacroScreen.cfg().panicEnabled;
        s.rows.add(new Rows.Section("Паника Garden — общее").key("p.main"));
        s.rows.add(s.toggle(
                "Паника включена",
                "Главный выключатель всех детекторов",
                () -> FarmMacroScreen.cfg().panicEnabled,
                v -> FarmMacroScreen.cfg().panicEnabled = v));
        s.rows.add(new Rows.Note(
                () -> {
                    String r = PanicDetector.INSTANCE.getLastReason();
                    if (r == null) return "";
                    long ago = (System.currentTimeMillis() - PanicDetector.INSTANCE.getLastPanicMs()) / 1000;
                    return "Последняя паника " + (ago < 60 ? ago + " с" : ago / 60 + " мин") + " назад: " + r;
                },
                Ui.WARN));
        s.rows.add(new Rows.Note(
                        "Детекторы разложены по группам — нажми на заголовок, чтобы развернуть. Пороги появляются, "
                                + "когда детектор включён; тонкие пороги — в расширенном режиме.",
                        Ui.SUB)
                .adv());

        s.rows.add(new Rows.Note(
                "Эта вкладка — паника макроса/Garden. Боевая паника отдельно: Combat / Hunting → Паника Combat / Hunting. Главные выключатели и проверки независимы.",
                Ui.SUB));
        DetectorTable.addTo(s.rows, s); // v1.14.2-pre: сводная таблица (отдельный модуль, можно убрать этой строкой)

        s.rows.add(new Rows.Section("Остановка — макрос / Garden").key("p.handoff"));
        s.rows.add(s.toggle(
                        "Короткая задержка стопа фермы",
                        "Только некритические события: игроки, чат, титры, BPS/XP. Combat, телепорт, предмет, урон, мир, ошибки — сразу.",
                        () -> FarmMacroScreen.cfg().farmPanicDelay,
                        v -> FarmMacroScreen.cfg().farmPanicDelay = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Минимальная задержка стопа",
                        "Одна задержка на событие, повторы её не продлевают. Ручной ввод завершает остановку сразу.",
                        () -> FarmMacroScreen.cfg().farmPanicMinMs,
                        v -> FarmMacroScreen.cfg().farmPanicMinMs = (int) v,
                        0,
                        1200,
                        50,
                        100,
                        v -> (int) v + " мс")
                .integer()
                .under(() -> FarmMacroScreen.cfg().farmPanicDelay));
        s.rows.add(s.number(
                        "Максимальная задержка стопа",
                        "Не меньше минимальной; по умолчанию 350–750 мс. Никакого автоматического возобновления.",
                        () -> FarmMacroScreen.cfg().farmPanicMaxMs,
                        v -> FarmMacroScreen.cfg().farmPanicMaxMs = (int) v,
                        0,
                        1200,
                        50,
                        100,
                        v -> (int) v + " мс")
                .integer()
                .under(() -> FarmMacroScreen.cfg().farmPanicDelay));
        s.rows.add(new Rows.Note(
                "Подтверждение кнопкой B убрано. После остановки управляешь сам; повторный запуск — обычной кнопкой модуля.",
                Ui.SUB));
        s.rows.add(s.toggle(
                        "Стоп при телепорте во время автоматики",
                        "Любой серверный пакет позиции, включая нулевой сдвиг/лаг-откат, и респавн. Работает также при aim/осмотре/A-D; законный warp тоже остановит",
                        () -> FarmMacroScreen.cfg().detectAutomationTeleport,
                        v -> FarmMacroScreen.cfg().detectAutomationTeleport = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Стоп при потере/замене оружия",
                        "Предмет в руке исчез/сменился, включая SkyBlock ID/UUID, или сменился слот. Собственная смена слота модом учитывается; расходуемый стак может закончиться",
                        () -> FarmMacroScreen.cfg().detectAutomationWeapon,
                        v -> FarmMacroScreen.cfg().detectAutomationWeapon = v)
                .enabledIf(master));
        s.rows.add(new Rows.Note(
                "По умолчанию полный стоп и сигнал; без автоответа/оглядывания/автопродолжения. Отличить проверку от обычного отката/удаления предмета невозможно. Действие — в таблице детекторов.",
                Ui.WARN));
        s.rows.add(new Rows.Section("Камера и руки").key("p.hands").closed());
        BooleanSupplier rot = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectRotation;
        s.rows.add(s.toggle(
                        "Поворот камеры",
                        "Камеру сдвинули мышью во время макроса",
                        () -> FarmMacroScreen.cfg().detectRotation,
                        v -> FarmMacroScreen.cfg().detectRotation = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Порог по горизонтали",
                        null,
                        () -> FarmMacroScreen.cfg().yawThreshold,
                        v -> FarmMacroScreen.cfg().yawThreshold = (float) v,
                        0.001,
                        180,
                        0.5,
                        5,
                        v -> Rows.num(v) + "°")
                .under(rot));
        s.rows.add(s.number(
                        "Порог по вертикали",
                        null,
                        () -> FarmMacroScreen.cfg().pitchThreshold,
                        v -> FarmMacroScreen.cfg().pitchThreshold = (float) v,
                        0.001,
                        180,
                        0.5,
                        5,
                        v -> Rows.num(v) + "°")
                .under(rot));
        s.rows.add(new Rows.Note(
                () -> rot.getAsBoolean()
                                && Math.min(FarmMacroScreen.cfg().yawThreshold, FarmMacroScreen.cfg().pitchThreshold)
                                        < 2f
                        ? "Порог меньше 2°: лёгкое касание мыши уже даст панику. По умолчанию 5°."
                        : "",
                Ui.WARN));
        s.rows.add(s.toggle(
                        "Смена слота",
                        "Слот хотбара сменил не макрос (и сервер тоже)",
                        () -> FarmMacroScreen.cfg().detectSlotChange,
                        v -> FarmMacroScreen.cfg().detectSlotChange = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Предмет в руке",
                        "Сменился или пропал сам (слот тот же). Кончились семена — не считается",
                        () -> FarmMacroScreen.cfg().detectHeldItem,
                        v -> FarmMacroScreen.cfg().detectHeldItem = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Урон",
                        "Здоровье уменьшилось или пришло событие урона (даже 0). Падение на точке-спуске — нет",
                        () -> FarmMacroScreen.cfg().detectDamage,
                        v -> FarmMacroScreen.cfg().detectDamage = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Открылось окно",
                        "Любое окно, кроме паузы, чата и меню мода",
                        () -> FarmMacroScreen.cfg().detectGuiOpen,
                        v -> FarmMacroScreen.cfg().detectGuiOpen = v)
                .enabledIf(master));
        s.rows.add(new Rows.Section("Телепорт и толчки").key("p.tp").closed());
        BooleanSupplier mv = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectServerMove;
        BooleanSupplier sync = () -> mv.getAsBoolean() && FarmMacroScreen.cfg().detectServerSync;
        s.rows.add(s.toggle(
                        "Телепорт / поворот сервером",
                        "Сервер сам сдвинул или развернул игрока",
                        () -> FarmMacroScreen.cfg().detectServerMove,
                        v -> FarmMacroScreen.cfg().detectServerMove = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Мин. сдвиг",
                        "Мелкие откаты от лагов игнорируются",
                        () -> FarmMacroScreen.cfg().serverMoveThreshold,
                        v -> FarmMacroScreen.cfg().serverMoveThreshold = v,
                        0.001,
                        1024,
                        0.1,
                        1,
                        v -> Rows.num(v) + " бл")
                .under(mv));
        s.rows.add(s.number(
                        "Мин. поворот",
                        null,
                        () -> FarmMacroScreen.cfg().serverRotateThreshold,
                        v -> FarmMacroScreen.cfg().serverRotateThreshold = (float) v,
                        0.001,
                        180,
                        0.5,
                        5,
                        v -> Rows.num(v) + "°")
                .under(mv));
        s.rows.add(s.toggle(
                        "Любой пакет телепорта",
                        "Даже /tp на то же место (0 бл) и «посмотреть на». Откаты античита тоже",
                        () -> FarmMacroScreen.cfg().serverMoveAny,
                        v -> FarmMacroScreen.cfg().serverMoveAny = v)
                .under(mv));
        s.rows.add(s.toggle(
                        "Синхронизация (эпсилон)",
                        "Сервер поправил позицию или взгляд больше эпсилона. Меньше — в подозрительность",
                        () -> FarmMacroScreen.cfg().detectServerSync,
                        v -> FarmMacroScreen.cfg().detectServerSync = v)
                .under(mv));
        s.rows.add(s.number(
                        "Эпсилон позиции",
                        null,
                        () -> FarmMacroScreen.cfg().serverPosEpsilon,
                        v -> FarmMacroScreen.cfg().serverPosEpsilon = v,
                        0.0001,
                        16,
                        0.01,
                        0.1,
                        v -> Rows.num(v) + " бл")
                .under(sync)
                .adv());
        s.rows.add(s.number(
                        "Эпсилон взгляда",
                        "yaw и pitch отдельно",
                        () -> FarmMacroScreen.cfg().serverRotEpsilon,
                        v -> FarmMacroScreen.cfg().serverRotEpsilon = (float) v,
                        0.0001,
                        180,
                        0.01,
                        0.5,
                        v -> Rows.num(v) + "°")
                .under(sync)
                .adv());
        s.rows.add(s.toggle(
                        "Толчок",
                        "Сервер задал скорость игроку: отдача, удочка, взрыв, плагин",
                        () -> FarmMacroScreen.cfg().detectKnockback,
                        v -> FarmMacroScreen.cfg().detectKnockback = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Мин. толчок",
                        "Слабее — в подозрительность",
                        () -> FarmMacroScreen.cfg().knockbackThreshold,
                        v -> FarmMacroScreen.cfg().knockbackThreshold = v,
                        0.001,
                        10,
                        0.01,
                        0.1,
                        v -> Rows.num(v) + " бл/т")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectKnockback)
                .adv());

        s.rows.add(new Rows.Section("Движение и маршрут").key("p.move").closed());
        s.rows.add(s.toggle(
                        "Параметры движения",
                        "Сервер изменил скорость, прыжок, гравитацию, высоту шага, размер, скорость ходьбы/полёта",
                        () -> FarmMacroScreen.cfg().detectMoveAttrs,
                        v -> FarmMacroScreen.cfg().detectMoveAttrs = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Допуск параметров",
                        "Спринт и снег не считаются",
                        () -> FarmMacroScreen.cfg().attrEpsilon,
                        v -> FarmMacroScreen.cfg().attrEpsilon = v,
                        0.01,
                        100,
                        0.5,
                        5,
                        v -> Rows.num(v) + " %")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectMoveAttrs)
                .adv());
        s.rows.add(s.toggle(
                        "Аномалия скорости",
                        "Маршрут по точкам: скорость разошлась с ожидаемой без толчка и без стены",
                        () -> FarmMacroScreen.cfg().detectVelocity,
                        v -> FarmMacroScreen.cfg().detectVelocity = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Допуск скорости",
                        "Ходьба ≈ 0.22 бл/т, бег ≈ 0.28",
                        () -> FarmMacroScreen.cfg().velocityAnomaly,
                        v -> FarmMacroScreen.cfg().velocityAnomaly = v,
                        0.01,
                        5,
                        0.01,
                        0.1,
                        v -> Rows.num(v) + " бл/т")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectVelocity)
                .adv());
        s.rows.add(s.toggle(
                        "Застревание",
                        "Игрок должен идти, а стоит на месте",
                        () -> FarmMacroScreen.cfg().detectStuck,
                        v -> FarmMacroScreen.cfg().detectStuck = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Анти-застревание: попыток",
                        "Маршрут по точкам: застрял — отойти назад с прыжком и идти дальше; не помогло N раз за 30 с — паника. 0 — сразу паника",
                        () -> FarmMacroScreen.cfg().antiStuckTries,
                        v -> FarmMacroScreen.cfg().antiStuckTries = (int) v,
                        0,
                        5,
                        1,
                        1,
                        v -> v == 0 ? "выкл" : String.valueOf((int) v))
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectStuck));
        s.rows.add(s.number(
                        "Окно проверки",
                        null,
                        () -> FarmMacroScreen.cfg().stuckThresholdTicks,
                        v -> FarmMacroScreen.cfg().stuckThresholdTicks = (int) v,
                        1,
                        12000,
                        5,
                        20,
                        FarmMacroScreen::ticksFmt)
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectStuck));
        s.rows.add(s.toggle(
                        "Сход с маршрута",
                        "Траектория разошлась с записью / линией маршрута",
                        () -> FarmMacroScreen.cfg().detectDrift,
                        v -> FarmMacroScreen.cfg().detectDrift = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Допуск",
                        null,
                        () -> FarmMacroScreen.cfg().driftThreshold,
                        v -> FarmMacroScreen.cfg().driftThreshold = v,
                        0.01,
                        1024,
                        0.5,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectDrift));
        s.rows.add(s.toggle(
                        "Этажи",
                        "Маршрут по точкам: после спуска упал не туда / не упал, точка на другом этаже без спуска",
                        () -> FarmMacroScreen.cfg().detectFloor,
                        v -> FarmMacroScreen.cfg().detectFloor = v)
                .enabledIf(master));
        BooleanSupplier bpsOn = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectBps;
        s.rows.add(s.toggle(
                        "Урожай не ломается (BPS)",
                        "Только непрерывный сбор с ЛКМ+движением и наблюдавшимся урожаем. Навигация/песты/полёт/пауза/лаг сбрасывают окно; низкий BPS подтверждается20тиков",
                        () -> FarmMacroScreen.cfg().detectBps,
                        v -> FarmMacroScreen.cfg().detectBps = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Мин. BPS",
                        "Текущий BPS — на плашке HUD во время макроса",
                        () -> FarmMacroScreen.cfg().bpsMin,
                        v -> FarmMacroScreen.cfg().bpsMin = v,
                        0.1,
                        40,
                        0.5,
                        2,
                        v -> Rows.num(v) + " бл/с")
                .under(bpsOn));
        s.rows.add(s.number(
                        "Окно BPS",
                        "Сколько секунд усреднять; прогрев — окно + 2 с",
                        () -> FarmMacroScreen.cfg().bpsWindowSec,
                        v -> FarmMacroScreen.cfg().bpsWindowSec = v,
                        2,
                        30,
                        1,
                        5,
                        v -> Rows.num(v) + " с")
                .under(bpsOn)
                .adv());
        s.rows.add(s.toggle(
                        "Пропал текст Farming XP",
                        "Выключено по умолчанию. Только после наблюдавшейся XP-строки в текущем непрерывном сборе; HP/мана не чат. Другие HUD-моды могут скрыть текст — это не доказательство ghost blocks",
                        () -> FarmMacroScreen.cfg().detectFarmingXp,
                        v -> FarmMacroScreen.cfg().detectFarmingXp = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Ожидание Farming XP",
                        "Прогрев40тиков, отсутствие текста заданное время +10тиков подтверждения. Пауза/лаг/переход сбрасывают наблюдение",
                        () -> FarmMacroScreen.cfg().farmingXpMissingSec,
                        v -> FarmMacroScreen.cfg().farmingXpMissingSec = v,
                        3,
                        60,
                        1,
                        8,
                        v -> Rows.num(v) + " с")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectFarmingXp));
        s.rows.add(s.toggle(
                        "Рассинхрон",
                        "Сломанные блоки через 1 с снова на месте (сервер не принял ломание). При лаге — в подозрительность",
                        () -> FarmMacroScreen.cfg().detectDesync,
                        v -> FarmMacroScreen.cfg().detectDesync = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Доля вернувшихся",
                        "Из последних 20 сломанных (минимум 10)",
                        () -> FarmMacroScreen.cfg().desyncPct,
                        v -> FarmMacroScreen.cfg().desyncPct = v,
                        10,
                        100,
                        5,
                        10,
                        v -> Rows.num(v) + " %")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectDesync)
                .adv());
        s.rows.add(s.toggle(
                        "Откат назад — подозрительность",
                        "Сервер вернул на место, где ты был за последние 3 с (< 4 бл): не паника, а очки",
                        () -> FarmMacroScreen.cfg().lagbackSoft,
                        v -> FarmMacroScreen.cfg().lagbackSoft = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Вес: откат назад",
                        null,
                        () -> FarmMacroScreen.cfg().suspWeightLagback,
                        v -> FarmMacroScreen.cfg().suspWeightLagback = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().lagbackSoft)
                .adv());

        s.rows.add(new Rows.Section("Блоки, эффекты, режим").key("p.world").closed());
        s.rows.add(s.toggle(
                        "Блок рядом",
                        "Сервер поставил твёрдый блок вплотную к игроку",
                        () -> FarmMacroScreen.cfg().detectBlockInFace,
                        v -> FarmMacroScreen.cfg().detectBlockInFace = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Зона вокруг хитбокса",
                        "Свои постановки блоков не считаются",
                        () -> FarmMacroScreen.cfg().blockDetectRadius,
                        v -> FarmMacroScreen.cfg().blockDetectRadius = v,
                        0,
                        8,
                        0.25,
                        1,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectBlockInFace)
                .adv());
        s.rows.add(s.toggle(
                        "Препятствие впереди",
                        "Сервер поставил твёрдый блок на луче взгляда или по ходу автохода. Рост урожая и поршни — нет",
                        () -> FarmMacroScreen.cfg().detectObstacle,
                        v -> FarmMacroScreen.cfg().detectObstacle = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Дальность луча",
                        null,
                        () -> FarmMacroScreen.cfg().obstacleRange,
                        v -> FarmMacroScreen.cfg().obstacleRange = v,
                        1,
                        16,
                        0.5,
                        2,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectObstacle)
                .adv());
        s.rows.add(s.toggle(
                        "Эффекты",
                        "Новый эффект или снятие раньше срока (маяк — не считается)",
                        () -> FarmMacroScreen.cfg().detectPotionEffect,
                        v -> FarmMacroScreen.cfg().detectPotionEffect = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Режим игры и полёт",
                        "Сменили режим, дали/забрали полёт, респавн/другой мир, посадили на сущность",
                        () -> FarmMacroScreen.cfg().detectGameMode,
                        v -> FarmMacroScreen.cfg().detectGameMode = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Горение",
                        "Игрок загорелся. Горел на старте — не считается, пока не потухнет",
                        () -> FarmMacroScreen.cfg().detectFire,
                        v -> FarmMacroScreen.cfg().detectFire = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Паутина и вязкие блоки",
                        "Сервер поставил паутину, куст ягод или рыхлый снег рядом — или ты в нём застрял",
                        () -> FarmMacroScreen.cfg().detectSticky,
                        v -> FarmMacroScreen.cfg().detectSticky = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Радиус вязких блоков",
                        null,
                        () -> FarmMacroScreen.cfg().stickyRadius,
                        v -> FarmMacroScreen.cfg().stickyRadius = v,
                        1,
                        32,
                        1,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectSticky)
                .adv());
        s.rows.add(s.toggle(
                        "Клетка",
                        "Над головой и с 3–4 сторон бедрок или барьер, и хотя бы один из них поставил сервер",
                        () -> FarmMacroScreen.cfg().detectCage,
                        v -> FarmMacroScreen.cfg().detectCage = v)
                .enabledIf(master));
        BooleanSupplier farm = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectFarmBlock;
        s.rows.add(s.toggle(
                        "Блок на ферме",
                        "Сервер поставил полный блок на месте воздуха/урожая/воды — очки; упёрся в него — паника",
                        () -> FarmMacroScreen.cfg().detectFarmBlock,
                        v -> FarmMacroScreen.cfg().detectFarmBlock = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Радиус фермы",
                        "Свои сломанные блоки, рост урожая и поршни не считаются",
                        () -> FarmMacroScreen.cfg().farmBlockRadius,
                        v -> FarmMacroScreen.cfg().farmBlockRadius = v,
                        2,
                        64,
                        1,
                        8,
                        v -> Rows.num(v) + " бл")
                .under(farm)
                .adv());
        s.rows.add(s.number(
                        "Вес: блок на ферме",
                        null,
                        () -> FarmMacroScreen.cfg().suspWeightFarmBlock,
                        v -> FarmMacroScreen.cfg().suspWeightFarmBlock = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(farm)
                .adv());
        BooleanSupplier bb = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectBedrockBurst;
        s.rows.add(s.toggle(
                        "Бедрок очередью",
                        "Сервер поставил несколько блоков бедрока рядом почти сразу (стройка клетки)",
                        () -> FarmMacroScreen.cfg().detectBedrockBurst,
                        v -> FarmMacroScreen.cfg().detectBedrockBurst = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Бедрок: блоков",
                        null,
                        () -> FarmMacroScreen.cfg().bedrockBurstMin,
                        v -> FarmMacroScreen.cfg().bedrockBurstMin = (int) v,
                        1,
                        64,
                        1,
                        2,
                        v -> (int) v + " шт.")
                .integer()
                .under(bb)
                .adv());
        s.rows.add(s.number(
                        "Бедрок: радиус",
                        null,
                        () -> FarmMacroScreen.cfg().bedrockBurstRadius,
                        v -> FarmMacroScreen.cfg().bedrockBurstRadius = v,
                        1,
                        64,
                        1,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(bb)
                .adv());
        s.rows.add(s.number(
                        "Бедрок: за время",
                        null,
                        () -> FarmMacroScreen.cfg().bedrockBurstMs,
                        v -> FarmMacroScreen.cfg().bedrockBurstMs = (int) v,
                        50,
                        10000,
                        50,
                        250,
                        v -> (int) v + " мс")
                .integer()
                .under(bb)
                .adv());
        BooleanSupplier cc = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectCageCount;
        s.rows.add(s.toggle(
                        "Клетка по числу блоков",
                        "Сервер поставил несколько твёрдых блоков у ног и головы почти сразу — любые, не только бедрок",
                        () -> FarmMacroScreen.cfg().detectCageCount,
                        v -> FarmMacroScreen.cfg().detectCageCount = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Клетка: блоков",
                        null,
                        () -> FarmMacroScreen.cfg().cageCountMin,
                        v -> FarmMacroScreen.cfg().cageCountMin = (int) v,
                        2,
                        26,
                        1,
                        2,
                        v -> (int) v + " шт.")
                .integer()
                .under(cc)
                .adv());
        s.rows.add(s.number(
                        "Клетка: радиус",
                        "От ног и от головы",
                        () -> FarmMacroScreen.cfg().cageCountRadius,
                        v -> FarmMacroScreen.cfg().cageCountRadius = v,
                        0.5,
                        4,
                        0.25,
                        1,
                        v -> Rows.num(v) + " бл")
                .under(cc)
                .adv());
        s.rows.add(s.number(
                        "Клетка: за время",
                        null,
                        () -> FarmMacroScreen.cfg().cageCountMs,
                        v -> FarmMacroScreen.cfg().cageCountMs = (int) v,
                        100,
                        10000,
                        100,
                        500,
                        v -> (int) v + " мс")
                .integer()
                .under(cc)
                .adv());

        s.rows.add(new Rows.Section("Чат и игроки").key("p.social").closed());
        BooleanSupplier chat = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectChat;
        s.rows.add(s.toggle(
                        "Чат",
                        "Сообщение с твоим ником или словом из списка (свои — не считаются)",
                        () -> FarmMacroScreen.cfg().detectChat,
                        v -> FarmMacroScreen.cfg().detectChat = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Обращение игрока рядом",
                        "Hi/hello/привет, твой ник или слова чата от известного недавнего соседа: полный стоп, звук/экран по настройкам. Без автоответа/оглядывания",
                        () -> FarmMacroScreen.cfg().detectNearbyChat,
                        v -> FarmMacroScreen.cfg().detectNearbyChat = v)
                .enabledIf(master));
        BooleanSupplier nearbyChat = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectNearbyChat;
        s.rows.add(s.number(
                        "Чат рядом: радиус",
                        "Запоминать настоящих загруженных игроков; неизвестного автора не угадывать",
                        () -> FarmMacroScreen.cfg().nearbyChatRadius,
                        v -> FarmMacroScreen.cfg().nearbyChatRadius = v,
                        1,
                        32,
                        1,
                        5,
                        v -> Rows.num(v) + " бл")
                .under(nearbyChat));
        s.rows.add(s.number(
                        "Чат рядом: память",
                        "Игрок мог отойти после обращения. Память сбрасывается при смене мира/телепорте",
                        () -> FarmMacroScreen.cfg().nearbyChatMemorySec,
                        v -> FarmMacroScreen.cfg().nearbyChatMemorySec = (int) v,
                        3,
                        60,
                        1,
                        5,
                        v -> Rows.num(v) + " с")
                .under(nearbyChat));
        s.rows.add(s.toggle(
                        "Чат: мой ник",
                        null,
                        () -> FarmMacroScreen.cfg().chatMentionName,
                        v -> FarmMacroScreen.cfg().chatMentionName = v)
                .under(chat));
        s.rows.add(new Rows.TextField(
                        "Чат: слова",
                        "Через запятую. Обычные — без учёта регистра; ЗАГЛАВНЫЕ (ROTATED) — точно и целым словом. Пусто — только ник",
                        () -> FarmMacroScreen.cfg().chatKeywords,
                        v -> {
                            FarmMacroScreen.cfg().chatKeywords = v.strip();
                            FarmMacroScreen.save();
                            return true;
                        },
                        null,
                        1000,
                        0)
                .under(chat));
        s.rows.add(s.toggle(
                        "Титры",
                        "Обычный title/subtitle на экране. HP/мана/способности над хотбаром не считаются",
                        () -> FarmMacroScreen.cfg().detectTitle,
                        v -> FarmMacroScreen.cfg().detectTitle = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Игрок рядом",
                        "Подошёл другой игрок (невидимый тоже). Кто был рядом на старте — не считается",
                        () -> FarmMacroScreen.cfg().detectPlayerNear,
                        v -> FarmMacroScreen.cfg().detectPlayerNear = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Радиус",
                        "Дальше — в подозрительность",
                        () -> FarmMacroScreen.cfg().playerNearRadius,
                        v -> FarmMacroScreen.cfg().playerNearRadius = v,
                        1,
                        128,
                        1,
                        8,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectPlayerNear));
        s.rows.add(s.number(
                        "Игрок рядом: задержка",
                        "Паника, только если игрок пробыл в радиусе столько секунд. 0 — сразу",
                        () -> FarmMacroScreen.cfg().playerNearDelaySec,
                        v -> FarmMacroScreen.cfg().playerNearDelaySec = v,
                        0,
                        120,
                        0.5,
                        5,
                        v -> v == 0 ? "сразу" : Rows.num(v) + " с")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectPlayerNear));
        s.rows.add(s.toggle(
                        "Наблюдатель",
                        "Игрок в Tab стал наблюдателем (его не видно, а смену режима — видно)",
                        () -> FarmMacroScreen.cfg().detectSpectator,
                        v -> FarmMacroScreen.cfg().detectSpectator = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Вход / выход игроков",
                        "Зашёл кто-то или пропал из Tab (часто так выглядит «ваниш»)",
                        () -> FarmMacroScreen.cfg().detectPlayerJoin,
                        v -> FarmMacroScreen.cfg().detectPlayerJoin = v)
                .enabledIf(master));
        BooleanSupplier staff = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectStaffNear;
        s.rows.add(s.toggle(
                        "Staff рядом",
                        "Игрок с [ADMIN], [MOD], [HELPER], [GM], [OWNER], [MOJANG] в имени рядом или появился в Tab ([YOUTUBE] — нет)",
                        () -> FarmMacroScreen.cfg().detectStaffNear,
                        v -> FarmMacroScreen.cfg().detectStaffNear = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Staff: радиус",
                        null,
                        () -> FarmMacroScreen.cfg().staffRadius,
                        v -> FarmMacroScreen.cfg().staffRadius = v,
                        1,
                        128,
                        1,
                        8,
                        v -> Rows.num(v) + " бл")
                .under(staff)
                .adv());
        s.rows.add(s.toggle(
                        "Staff: появился в Tab",
                        "Даже далеко — по префиксу в списке игроков",
                        () -> FarmMacroScreen.cfg().staffTab,
                        v -> FarmMacroScreen.cfg().staffTab = v)
                .under(staff));
        s.rows.add(s.toggle(
                        "Двойник",
                        "Рядом другой игрок с твоим ником (подмена при проверке)",
                        () -> FarmMacroScreen.cfg().detectTwin,
                        v -> FarmMacroScreen.cfg().detectTwin = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Двойник: радиус",
                        null,
                        () -> FarmMacroScreen.cfg().twinRadius,
                        v -> FarmMacroScreen.cfg().twinRadius = v,
                        1,
                        128,
                        1,
                        8,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectTwin)
                .adv());
        BooleanSupplier mass = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectMassTp;
        s.rows.add(s.toggle(
                        "Массовый телепорт",
                        "Сразу несколько игроков появились рядом. Не после своего телепорта/варпа и не в первые секунды в мире",
                        () -> FarmMacroScreen.cfg().detectMassTp,
                        v -> FarmMacroScreen.cfg().detectMassTp = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Массовый ТП: игроков",
                        null,
                        () -> FarmMacroScreen.cfg().massTpMin,
                        v -> FarmMacroScreen.cfg().massTpMin = (int) v,
                        2,
                        50,
                        1,
                        5,
                        v -> (int) v + " шт.")
                .integer()
                .under(mass));
        s.rows.add(s.number(
                        "Массовый ТП: радиус",
                        null,
                        () -> FarmMacroScreen.cfg().massTpRadius,
                        v -> FarmMacroScreen.cfg().massTpRadius = v,
                        1,
                        64,
                        1,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(mass)
                .adv());
        s.rows.add(s.number(
                        "Массовый ТП: за время",
                        null,
                        () -> FarmMacroScreen.cfg().massTpWindowMs,
                        v -> FarmMacroScreen.cfg().massTpWindowMs = (int) v,
                        100,
                        10000,
                        100,
                        500,
                        v -> (int) v + " мс")
                .integer()
                .under(mass)
                .adv());
        s.rows.add(s.number(
                        "Массовый ТП: не в первые",
                        "Секунды после входа в мир",
                        () -> FarmMacroScreen.cfg().massTpWorldGraceSec,
                        v -> FarmMacroScreen.cfg().massTpWorldGraceSec = (int) v,
                        0,
                        120,
                        1,
                        5,
                        v -> (int) v + " с")
                .integer()
                .under(mass)
                .adv());
        BooleanSupplier nl = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectNameless;
        s.rows.add(s.toggle(
                        "Игрок без имени",
                        "Рядом стоит игрок без ника над головой (скрытый бот проверки). Невидимый с ником — нет",
                        () -> FarmMacroScreen.cfg().detectNameless,
                        v -> FarmMacroScreen.cfg().detectNameless = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Без имени: радиус",
                        null,
                        () -> FarmMacroScreen.cfg().namelessRadius,
                        v -> FarmMacroScreen.cfg().namelessRadius = v,
                        1,
                        64,
                        1,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(nl)
                .adv());
        s.rows.add(s.number(
                        "Без имени: дольше",
                        null,
                        () -> FarmMacroScreen.cfg().namelessDwellMs,
                        v -> FarmMacroScreen.cfg().namelessDwellMs = (int) v,
                        50,
                        30000,
                        250,
                        1000,
                        v -> (int) v + " мс")
                .integer()
                .under(nl)
                .adv());
        s.rows.add(s.toggle(
                        "Личные сообщения",
                        "«From [RANK] Ник: …» — тебе написали в ЛС",
                        () -> FarmMacroScreen.cfg().detectPm,
                        v -> FarmMacroScreen.cfg().detectPm = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "«You are AFK»",
                        "Сервер предупредил, что ты AFK",
                        () -> FarmMacroScreen.cfg().detectAfkMsg,
                        v -> FarmMacroScreen.cfg().detectAfkMsg = v)
                .enabledIf(master));

        s.rows.add(new Rows.Section("Друзья").key("p.friends").closed());
        s.rows.add(new Rows.Note(
                "Ники друзей: на них не срабатывают отмеченные ниже проверки. Регистр не важен.", Ui.SUB));
        s.rows.add(new Rows.TextField(
                "Друзья",
                "Ники через запятую",
                () -> FarmMacroScreen.cfg().friendNames,
                v -> {
                    FarmMacroScreen.cfg().friendNames = v.strip();
                    FarmMacroScreen.save();
                    return true;
                },
                null,
                1000,
                0));
        s.rows.add(new Rows.Buttons(new Rows.Btn("Добавить игроков рядом", Rows.Style.SECONDARY, s::addNearbyFriends)
                .tip("Все игроки из Tab в 32 блоках (NPC без записи в Tab — нет)")));
        s.rows.add(new Rows.Note(() -> s.friendMsg, Ui.ACCENT));
        s.rows.add(s.toggle(
                "Не считать: игрок рядом",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnoreNear,
                v -> FarmMacroScreen.cfg().friendsIgnoreNear = v));
        s.rows.add(s.toggle(
                "Не считать: вход / выход",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnoreJoin,
                v -> FarmMacroScreen.cfg().friendsIgnoreJoin = v));
        s.rows.add(s.toggle(
                "Не считать: наблюдатель",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnoreSpectator,
                v -> FarmMacroScreen.cfg().friendsIgnoreSpectator = v));
        s.rows.add(s.toggle(
                "Не считать: чат",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnoreChat,
                v -> FarmMacroScreen.cfg().friendsIgnoreChat = v));
        s.rows.add(s.toggle(
                "Не считать: ЛС",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnorePm,
                v -> FarmMacroScreen.cfg().friendsIgnorePm = v));
        s.rows.add(s.toggle(
                "Не считать: массовый телепорт",
                null,
                () -> FarmMacroScreen.cfg().friendsIgnoreMass,
                v -> FarmMacroScreen.cfg().friendsIgnoreMass = v));

        s.rows.add(new Rows.Section("Сервер и Skyblock").key("p.server").closed());
        s.rows.add(s.toggle(
                        "Лаги сервера",
                        "Нет пакетов времени > 1.3 с: «Застрял» и «Аномалия скорости» ждут, поправки позиции — в подозрительность",
                        () -> FarmMacroScreen.cfg().lagGuard,
                        v -> FarmMacroScreen.cfg().lagGuard = v)
                .enabledIf(master));
        s.rows.add(new Rows.Note(
                () -> {
                    double tps = LagGuard.INSTANCE.tps();
                    if (!FarmMacroScreen.cfg().lagGuard) return "";
                    return LagGuard.INSTANCE.lagging()
                            ? "Сейчас: сервер лагает"
                            : Double.isNaN(tps)
                                    ? "TPS: ещё нет данных (нужно несколько секунд на сервере)"
                                    : String.format(Locale.ROOT, "Сейчас: TPS %.1f", tps);
                },
                Ui.SUB));
        s.rows.add(s.toggle(
                        "Отключение от сервера",
                        "Кикнули или оборвалась связь во время макроса — сирена и запись в журнал",
                        () -> FarmMacroScreen.cfg().detectDisconnect,
                        v -> FarmMacroScreen.cfg().detectDisconnect = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                        "Тишина сервера",
                        "Ни одного пакета столько секунд — мод сам закрывает соединение, дальше как вылет (Переподключение)",
                        () -> FarmMacroScreen.cfg().detectSilence,
                        v -> FarmMacroScreen.cfg().detectSilence = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Тишина: секунд",
                        null,
                        () -> FarmMacroScreen.cfg().silenceSec,
                        v -> FarmMacroScreen.cfg().silenceSec = (int) v,
                        10,
                        300,
                        5,
                        30,
                        v -> (int) v + " с")
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectSilence));
        s.rows.add(s.toggle(
                        "Перезагрузка / лимбо",
                        "«Server closing: 0:25» на табло (< 30 с), слова restart/reboot/перезагрузка/limbo в системном чате и титрах",
                        () -> FarmMacroScreen.cfg().detectRestart,
                        v -> FarmMacroScreen.cfg().detectRestart = v)
                .enabledIf(master));
        BooleanSupplier ban = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectBanwave;
        s.rows.add(s.toggle(
                        "Банвейв (Hypixel)",
                        "Банов Watchdog/staff за окно больше порога. Нужен свой API-ключ с developer.hypixel.net",
                        () -> FarmMacroScreen.cfg().detectBanwave,
                        v -> FarmMacroScreen.cfg().detectBanwave = v)
                .enabledIf(master));
        s.rows.add(new Rows.TextField(
                        "API-ключ Hypixel",
                        "Хранится отдельно (hypixel-api-key.txt), в профили настроек не попадает",
                        BanwaveWatch::key,
                        BanwaveWatch::setKey,
                        ch -> ch.matches("[0-9a-fA-F\\-]"),
                        36,
                        0)
                .errorText("Ключ — UUID вида xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
                .under(ban));
        s.rows.add(s.number(
                        "Порог банов",
                        null,
                        () -> FarmMacroScreen.cfg().banwaveThreshold,
                        v -> FarmMacroScreen.cfg().banwaveThreshold = (int) v,
                        1,
                        100000,
                        5,
                        50,
                        v -> Rows.num(v))
                .integer()
                .under(ban));
        s.rows.add(s.number(
                        "Окно банвейва",
                        null,
                        () -> FarmMacroScreen.cfg().banwaveWindowMin,
                        v -> FarmMacroScreen.cfg().banwaveWindowMin = (int) v,
                        2,
                        60,
                        1,
                        5,
                        v -> Rows.num(v) + " мин")
                .integer()
                .under(ban)
                .adv());
        s.rows.add(s.toggle(
                        "Считать баны staff",
                        "Watchdog + баны модераторов",
                        () -> FarmMacroScreen.cfg().banwaveWithStaff,
                        v -> FarmMacroScreen.cfg().banwaveWithStaff = v)
                .under(ban)
                .adv());
        s.rows.add(new Rows.Note(
                () -> FarmMacroScreen.cfg().detectBanwave ? "Банвейв: " + BanwaveWatch.describe() : "", Ui.SUB));
        s.rows.add(s.toggle(
                        "Jacob's Contest: лимит",
                        "Skyblock: собрано в конкурсе ≥ лимита (по табло) — обычный стоп, не паника",
                        () -> FarmMacroScreen.cfg().detectJacob,
                        v -> FarmMacroScreen.cfg().detectJacob = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Лимит конкурса",
                        "0 — без лимита",
                        () -> FarmMacroScreen.cfg().jacobLimit,
                        v -> FarmMacroScreen.cfg().jacobLimit = (int) v,
                        0,
                        2_000_000_000,
                        1000,
                        100000,
                        v -> Rows.num(v))
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().detectJacob));
        s.rows.add(s.toggle(
                        "Гость на ферме",
                        "Skyblock: «Ник is visiting Your Garden/Island»",
                        () -> FarmMacroScreen.cfg().detectVisitor,
                        v -> FarmMacroScreen.cfg().detectVisitor = v)
                .enabledIf(master));
        s.rows.add(s.toggle(
                "Плашка TPS · BPS",
                "В HUD во время макроса: TPS сервера, блоков в секунду, баны",
                () -> FarmMacroScreen.cfg().serverHud,
                v -> FarmMacroScreen.cfg().serverHud = v));

        s.rows.add(new Rows.Section("Подозрительность").key("p.susp").closed());
        s.rows.add(new Rows.Note(
                "Мелкие события ниже порогов дают очки, очки затухают. Сумма дошла до лимита — паника «Много мелких аномалий».",
                Ui.SUB));
        BooleanSupplier susp = () -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().suspicionEnabled;
        s.rows.add(s.toggle(
                        "Подозрительность",
                        "Серия мелких аномалий = паника",
                        () -> FarmMacroScreen.cfg().suspicionEnabled,
                        v -> FarmMacroScreen.cfg().suspicionEnabled = v)
                .enabledIf(master));
        s.rows.add(s.number(
                        "Лимит",
                        null,
                        () -> FarmMacroScreen.cfg().suspicionLimit,
                        v -> FarmMacroScreen.cfg().suspicionLimit = v,
                        0.1,
                        1000,
                        1,
                        5,
                        v -> Rows.num(v) + " оч.")
                .under(susp));
        s.rows.add(s.number(
                        "Полураспад",
                        "Через столько вклад события вдвое меньше",
                        () -> FarmMacroScreen.cfg().suspicionHalfLifeSec,
                        v -> FarmMacroScreen.cfg().suspicionHalfLifeSec = v,
                        1,
                        3600,
                        5,
                        30,
                        v -> Rows.num(v) + " с")
                .under(susp));
        s.rows.add(s.number(
                        "Вес: пакет ниже эпсилона",
                        "Телепорт/поворот меньше эпсилона, в т.ч. без сдвига",
                        () -> FarmMacroScreen.cfg().suspWeightSync,
                        v -> FarmMacroScreen.cfg().suspWeightSync = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(susp)
                .adv());
        s.rows.add(s.number(
                        "Вес: лаг-откат",
                        "Больше эпсилона, но «Синхронизация» выкл",
                        () -> FarmMacroScreen.cfg().suspWeightRollback,
                        v -> FarmMacroScreen.cfg().suspWeightRollback = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(susp)
                .adv());
        s.rows.add(s.number(
                        "Вес: слабый толчок",
                        "Меньше «Мин. толчок»",
                        () -> FarmMacroScreen.cfg().suspWeightKnock,
                        v -> FarmMacroScreen.cfg().suspWeightKnock = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(susp)
                .adv());
        s.rows.add(s.number(
                        "Вес: игрок за радиусом",
                        "Раз за запуск на игрока; кто был на старте — нет",
                        () -> FarmMacroScreen.cfg().suspWeightPlayerFar,
                        v -> FarmMacroScreen.cfg().suspWeightPlayerFar = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(susp)
                .adv());
        s.rows.add(s.toggle(
                        "Слова без ника — в подозрительность",
                        "Слово из списка без ника даёт очки, а не панику. Ник — всегда паника",
                        () -> FarmMacroScreen.cfg().chatWordsSoft,
                        v -> FarmMacroScreen.cfg().chatWordsSoft = v)
                .under(susp));
        s.rows.add(s.number(
                        "Вес: слово в чате",
                        null,
                        () -> FarmMacroScreen.cfg().suspWeightChatWord,
                        v -> FarmMacroScreen.cfg().suspWeightChatWord = v,
                        0,
                        100,
                        0.5,
                        2,
                        Rows::num)
                .under(() -> susp.getAsBoolean() && FarmMacroScreen.cfg().chatWordsSoft));

        buildReaction();
    }

    private static final List<Rows.Choice> SOUND_OUT = List.of(
            new Rows.Choice("game", "Через игру (безопасно)"),
            new Rows.Choice("files", "Через игру + свои файлы"),
            new Rows.Choice("system", "Через систему"));

    /** «Безопасный звук» и «Мимо громкости игры» — один выбор (раньше два зависимых переключателя). */
    private static String soundOut() {
        return FarmMacroScreen.cfg().panicSoundSafe
                ? "game"
                : FarmMacroScreen.cfg().panicSoundSystem ? "system" : "files";
    }

    /** Реакция на панику: звук, красный экран, журнал (раньше — отдельная вкладка «Реакция»). */
    private void buildReaction() {
        BooleanSupplier snd = () -> FarmMacroScreen.cfg().panicSoundEnabled;
        s.rows.add(new Rows.Section("Звук паники").key("p.sound").closed());
        s.rows.add(s.toggle(
                "Звук паники",
                null,
                () -> FarmMacroScreen.cfg().panicSoundEnabled,
                v -> FarmMacroScreen.cfg().panicSoundEnabled = v));
        s.rows.add(new Rows.Selector(
                        "Сигнал",
                        "Встроенные, свои файлы или звуки Minecraft",
                        () -> s.soundOptions,
                        () -> FarmMacroScreen.cfg().panicSound,
                        v -> {
                            FarmMacroScreen.cfg().panicSound = v;
                            FarmMacroScreen.save();
                            PanicSound.preload(v);
                        })
                .under(snd));
        s.rows.add(new Rows.Buttons(
                        new Rows.Btn("▶ Прослушать", Rows.Style.PRIMARY, PanicSound::playPanic),
                        new Rows.Btn("Папка звуков", Rows.Style.SECONDARY, () -> s.openFolder(PanicSound.soundsDir()))
                                .tip("Положи туда .ogg или .wav и нажми «Обновить»"),
                        new Rows.Btn("Обновить", Rows.Style.SECONDARY, () -> {
                            PanicSound.clearCache();
                            s.refreshLists();
                        }))
                .under(snd));
        s.rows.add(new Rows.Selector(
                        "Вывод звука",
                        "Безопасно — только движок игры. Свои файлы — без «безопасно». Система — слышно даже при выключенном звуке MC",
                        () -> SOUND_OUT,
                        PanicTab::soundOut,
                        v -> {
                            FarmMacroScreen.cfg().panicSoundSafe = "game".equals(v);
                            FarmMacroScreen.cfg().panicSoundSystem = "system".equals(v);
                            FarmMacroScreen.save();
                            if (!FarmMacroScreen.cfg().panicSoundSafe) PanicSound.preloadAll();
                        })
                .under(snd));
        s.rows.add(new Rows.Note(
                () -> {
                    if (!FarmMacroScreen.cfg().panicSoundEnabled) return "";
                    if (!FarmMacroScreen.cfg().panicSoundSafe && PanicSound.systemAudioBroken())
                        return "Системный звук сломался в этой сессии — играет через игру (подробности в логе).";
                    if (FarmMacroScreen.cfg().panicSoundSafe
                            && FarmMacroScreen.cfg().panicSound.startsWith("file:"))
                        return "Свой файл играется только без «безопасно». Сейчас вместо него — встроенная сирена.";
                    return "";
                },
                Ui.WARN));
        s.rows.add(s.number(
                        "Громкость",
                        null,
                        () -> FarmMacroScreen.cfg().panicSoundVolume * 100,
                        v -> FarmMacroScreen.cfg().panicSoundVolume = (float) (v / 100),
                        0,
                        100,
                        5,
                        25,
                        v -> Rows.num(v) + "%")
                .under(snd));
        s.rows.add(s.number(
                        "Повторов",
                        null,
                        () -> FarmMacroScreen.cfg().panicSoundRepeats,
                        v -> FarmMacroScreen.cfg().panicSoundRepeats = (int) v,
                        1,
                        100,
                        1,
                        5,
                        v -> String.valueOf((int) v))
                .integer()
                .under(snd));
        s.rows.add(s.number(
                        "Тон",
                        null,
                        () -> FarmMacroScreen.cfg().panicSoundPitch,
                        v -> FarmMacroScreen.cfg().panicSoundPitch = (float) v,
                        0.5,
                        2,
                        0.05,
                        0.25,
                        v -> "×" + Rows.num(v))
                .under(snd)
                .adv());
        s.rows.add(s.number(
                        "Пауза между повторами",
                        null,
                        () -> FarmMacroScreen.cfg().panicSoundRepeatDelayTicks,
                        v -> FarmMacroScreen.cfg().panicSoundRepeatDelayTicks = (int) v,
                        1,
                        1200,
                        5,
                        20,
                        FarmMacroScreen::ticksFmt)
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicSoundEnabled && FarmMacroScreen.cfg().panicSoundRepeats > 1)
                .adv());
        s.rows.add(new Rows.Note(
                        "Свои звуки: .ogg или .wav в config/farmmacro/sounds/. Звуки «MC:» всегда идут через игру.",
                        Ui.ACCENT)
                .adv());

        s.rows.add(new Rows.Section("Экран и журнал").key("p.screen").closed());
        s.rows.add(s.toggle(
                "Красный экран",
                "Вспышка с причиной паники",
                () -> FarmMacroScreen.cfg().panicRedScreenEnabled,
                v -> FarmMacroScreen.cfg().panicRedScreenEnabled = v));
        s.rows.add(s.number(
                        "Длительность",
                        null,
                        () -> FarmMacroScreen.cfg().panicRedScreenTicks,
                        v -> FarmMacroScreen.cfg().panicRedScreenTicks = (int) v,
                        1,
                        1200,
                        5,
                        20,
                        FarmMacroScreen::ticksFmt)
                .integer()
                .under(() -> FarmMacroScreen.cfg().panicRedScreenEnabled)
                .adv());
        s.rows.add(s.toggle(
                "Журнал событий",
                "config/farmmacro/logs/events-ДАТА.log: флаги, паники, старт/стоп, круги, чат, очки",
                () -> FarmMacroScreen.cfg().eventLogEnabled,
                v -> FarmMacroScreen.cfg().eventLogEnabled = v));
        s.rows.add(new Rows.Buttons(new Rows.Btn("Папка журналов", Rows.Style.SECONDARY, () -> {
                    Path d = ModConfig.modDir().resolve("logs");
                    try {
                        Files.createDirectories(d);
                    } catch (Exception ignored) {
                    }
                    s.openFolder(d);
                }))
                .under(() -> FarmMacroScreen.cfg().eventLogEnabled)
                .adv());
        s.rows.add(s.toggle(
                "Скриншот при панике",
                "config/farmmacro/screenshots/ — сохраняется сразу после стопа",
                () -> FarmMacroScreen.cfg().panicScreenshot,
                v -> FarmMacroScreen.cfg().panicScreenshot = v));
        s.rows.add(new Rows.Buttons(new Rows.Btn("Папка скриншотов", Rows.Style.SECONDARY, () -> {
                    Path d = PanicShot.dir();
                    try {
                        Files.createDirectories(d);
                    } catch (Exception ignored) {
                    }
                    s.openFolder(d);
                }))
                .under(() -> FarmMacroScreen.cfg().panicScreenshot)
                .adv());
        s.rows.add(s.toggle(
                        "Диагностика: стоит без дела",
                        "Только запись в журнал, не паника: макрос играет, а ни одна клавиша не нажата",
                        () -> FarmMacroScreen.cfg().diagIdle,
                        v -> FarmMacroScreen.cfg().diagIdle = v)
                .enabledIf(() -> FarmMacroScreen.cfg().eventLogEnabled));
        s.rows.add(s.number(
                        "Без дела дольше",
                        null,
                        () -> FarmMacroScreen.cfg().diagIdleSec,
                        v -> FarmMacroScreen.cfg().diagIdleSec = (int) v,
                        3,
                        3600,
                        5,
                        30,
                        v -> (int) v + " с")
                .integer()
                .under(() -> FarmMacroScreen.cfg().eventLogEnabled && FarmMacroScreen.cfg().diagIdle)
                .adv());
        s.rows.add(s.toggle(
                        "Диагностика: табло не читается",
                        "Запись в журнал, если табло не читается минуту (нужно для перезагрузки и Jacob's)",
                        () -> FarmMacroScreen.cfg().diagScoreboard,
                        v -> FarmMacroScreen.cfg().diagScoreboard = v)
                .enabledIf(() -> FarmMacroScreen.cfg().eventLogEnabled));

        s.rows.add(s.toggle(
                "Окно игры вперёд",
                "Развернуть свёрнутую игру, поднять над другими окнами и дать фокус. "
                        + "Если система не даёт фокус (Windows) — окно мигает в панели задач",
                () -> FarmMacroScreen.cfg().panicFocusWindow,
                v -> FarmMacroScreen.cfg().panicFocusWindow = v));
        s.rows.add(s.toggle(
                "Выйти из «картинки в картинке»",
                "Сразу обычное окно",
                () -> FarmMacroScreen.cfg().panicExitPip,
                v -> FarmMacroScreen.cfg().panicExitPip = v));

        s.rows.add(new Rows.Section("После остановки").key("p.react").closed());
        s.rows.add(new Rows.Note(
                "Управление сразу у тебя. Мод не ждёт B, не возобновляется, не поворачивает камеру и не отвечает в чат после паники. Короткая задержка фермы настраивается в разделе «Остановка».",
                Ui.SUB));

        s.rows.add(new Rows.Section("Discord").key("p.discord").closed());
        BooleanSupplier wh = () -> FarmMacroScreen.cfg().webhookEnabled;
        s.rows.add(s.toggle(
                "Сообщать в Discord",
                "При панике — сообщение в свой канал через webhook (отдельный поток, игре не мешает)",
                () -> FarmMacroScreen.cfg().webhookEnabled,
                v -> {
                    FarmMacroScreen.cfg().webhookEnabled = v;
                    DiscordNotify.settingsChanged();
                }));
        s.rows.add(new Rows.TextField(
                        "Webhook URL",
                        "Хранится отдельно (discord-webhook.txt), в профили и журнал не попадает. Пусто — удалить",
                        DiscordNotify::maskedUrl,
                        DiscordNotify::setUrl,
                        null,
                        300,
                        0)
                .errorText("Нужна ссылка https://discord.com/api/webhooks/…")
                .under(wh));
        s.rows.add(s.toggle(
                        "Событие: паника",
                        "Сообщение при каждой панике (как раньше)",
                        () -> FarmMacroScreen.cfg().webhookOnPanic,
                        v -> {
                            FarmMacroScreen.cfg().webhookOnPanic = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(wh));
        s.rows.add(s.toggle(
                        "Событие: запуск завершён",
                        "Штатный конец: лимит времени, конец маршрута, Jacob's. Ручной стоп не отправляется",
                        () -> FarmMacroScreen.cfg().webhookOnSessionEnd,
                        v -> {
                            FarmMacroScreen.cfg().webhookOnSessionEnd = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(wh));
        s.rows.add(s.toggle(
                        "Прикладывать скриншот",
                        "Нужен «Скриншот при панике»",
                        () -> FarmMacroScreen.cfg().webhookScreenshot,
                        v -> {
                            FarmMacroScreen.cfg().webhookScreenshot = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(() -> FarmMacroScreen.cfg().webhookEnabled && FarmMacroScreen.cfg().panicScreenshot));
        s.rows.add(s.toggle(
                        "Подробная причина паники (приватность)",
                        "OFF: только тип тревоги, без текста игроков/координат. ON: известные ники/цитаты/секреты маскируются, но полная анонимность не гарантируется",
                        () -> FarmMacroScreen.cfg().webhookDetails,
                        v -> {
                            FarmMacroScreen.cfg().webhookDetails = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(wh));
        s.rows.add(s.toggle(
                        "Показывать адрес сервера в Discord",
                        "По умолчанию адрес скрыт; числовой IP всё равно маскируется",
                        () -> FarmMacroScreen.cfg().webhookServer,
                        v -> {
                            FarmMacroScreen.cfg().webhookServer = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(wh));
        s.rows.add(s.toggle(
                        "Согласен отправлять незамаскированный снимок",
                        "Отдельное разрешение OFF. На PNG могут быть чат, ники, табло и приватные данные: маскирование текста НЕ редактирует все пиксели",
                        () -> FarmMacroScreen.cfg().webhookMediaConsent,
                        v -> {
                            FarmMacroScreen.cfg().webhookMediaConsent = v;
                            DiscordNotify.settingsChanged();
                        })
                .under(wh));
        s.rows.add(s.number(
                        "Антиспам одинаковых уведомлений",
                        "15–3600 секунд. Общая отправка не чаще 5 с, очередь4; паника приоритетнее ожидающих статусов. Сообщения могут пропускаться",
                        () -> FarmMacroScreen.cfg().webhookCooldownSec,
                        v -> FarmMacroScreen.cfg().webhookCooldownSec = (int) v,
                        15,
                        3600,
                        5,
                        30,
                        v -> (int) v + " с")
                .integer()
                .under(wh));
        s.rows.add(new Rows.Buttons(
                        new Rows.Btn("Сбросить ошибку Discord", Rows.Style.SECONDARY, DiscordNotify::resetError))
                .under(wh));
        s.rows.add(new Rows.Note(
                "Webhook хранится открытым текстом в отдельном локальном файле (POSIX 600, где доступно), не является хранилищем паролей. Токен/ID скрыты в поле, секреты/URL исключаются из переносимых настроек и журнала. HTTP429 ждёт Retry-After; 4xx блокирует до ручного сброса. Уже отправленный запрос отозвать нельзя.",
                Ui.SUB));
        s.rows.add(new Rows.Buttons(new Rows.Btn(
                        "Проверить",
                        Rows.Style.SECONDARY,
                        () -> DiscordNotify.send(
                                "✅ Kaelix: проверка вебхука · " + PanicShot.humanTime(), null, "проверка")))
                .under(() -> FarmMacroScreen.cfg().webhookEnabled && DiscordNotify.configured()));
        s.rows.add(new Rows.Note(() -> FarmMacroScreen.cfg().webhookEnabled ? DiscordNotify.status() : "", Ui.SUB));
        s.rows.add(new Rows.End());
        s.rows.add(new Rows.Spacer(4));
        s.rows.add(new Rows.Buttons(new Rows.Btn("⚠ Проверить панику", Rows.Style.DANGER, () -> {
                    s.onClose();
                    PanicDetector.INSTANCE.preview(s.mc());
                })
                .tip("Сирена и красный экран без остановки чего-либо")));
    }
}
