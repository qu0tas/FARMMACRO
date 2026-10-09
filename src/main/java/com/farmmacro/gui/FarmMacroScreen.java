package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MacroStorage;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicSound;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Главное меню мода. Свой интерфейс без текстур Minecraft: боковые вкладки, карточки настроек,
 * анимированные переключатели, числовые поля со стрелками, прокрутка.
 * Все значения читаются из конфига при каждой отрисовке и сохраняются сразу при изменении.
 */
public class FarmMacroScreen extends Screen implements Rows.Ctx {

    private static final String[] TABS = {"Детекторы", "Реакция", "Запуск", "Макросы", "Визуал"};
    private static final String[] TAB_ICONS = {"⚠", "♪", "▶", "☰", "✦"};
    private static int lastTab = 0;   // вкладка запоминается между открытиями

    private final Screen parent;
    private int tab = lastTab;
    private final List<Rows.Row> rows = new ArrayList<>();

    private double scroll, scrollTarget;
    private long lastFrameNs;
    private boolean draggingScrollbar;
    private String tooltip;
    private boolean wantHand;

    private List<Rows.Choice> soundOptions = List.of();
    private List<MacroStorage.MacroInfo> macros = List.of();
    private String confirmDelete;      // имя файла, ожидающего подтверждения удаления
    private long confirmUntil;

    public FarmMacroScreen(Screen parent) {
        super(Component.literal("FarmMacro"));
        this.parent = parent;
    }

    // ── Геометрия (всё от размера окна, поэтому ничего не наезжает на любом масштабе GUI) ──

    private int winW()  { return Math.max(Math.min(width - 16, 500), Math.min(width - 4, 300)); }
    private int winH()  { return Math.max(Math.min(height - 16, 330), Math.min(height - 4, 180)); }
    private int winX()  { return (width - winW()) / 2; }
    private int winY()  { return (height - winH()) / 2; }
    private int sideW() { return winW() < 400 ? 30 : 104; }  // узкое окно — только иконки
    private static final int HEADER_H = 30;
    private int contX() { return winX() + sideW() + 10; }
    private int contY() { return winY() + HEADER_H + 6; }
    private int contW() { return winX() + winW() - 10 - contX() - 6; }  // 6 — под полосу прокрутки
    private int contH() { return winY() + winH() - 8 - contY(); }

    @Override
    protected void init() {
        refreshLists();
        buildTab();
    }

    private void refreshLists() {
        List<Rows.Choice> opts = new ArrayList<>();
        for (PanicSound.Option o : PanicSound.options()) opts.add(new Rows.Choice(o.id(), o.label()));
        String cur = ModConfig.INSTANCE.panicSound;
        if (opts.stream().noneMatch(o -> o.id().equals(cur))) opts.add(new Rows.Choice(cur, PanicSound.label(cur)));
        soundOptions = opts;
        macros = MacroStorage.INSTANCE.listMacros();
    }

    private void switchTab(int t) {
        if (t == tab) return;
        tab = lastTab = t;
        scroll = scrollTarget = 0;
        Rows.clearWheelFocus();
        buildTab();
    }

    // ── Содержимое вкладок ───────────────────────────────────────────────────

    private static ModConfig cfg() { return ModConfig.INSTANCE; }
    private static void save() { ModConfig.save(); }

    private void buildTab() {
        rows.clear();
        switch (tab) {
            case 0 -> buildDetectors();
            case 1 -> buildReaction();
            case 2 -> buildAutomation();
            case 3 -> buildMacros();
            case 4 -> buildVisual();
        }
    }

    private Rows.Toggle toggle(String label, String hint, java.util.function.BooleanSupplier get,
                               java.util.function.Consumer<Boolean> set) {
        return new Rows.Toggle(label, hint, get, v -> { set.accept(v); save(); });
    }

    private Rows.Number number(String label, String hint, java.util.function.DoubleSupplier get,
                               java.util.function.DoubleConsumer set, double min, double max, double step,
                               double big, java.util.function.DoubleFunction<String> fmt) {
        return new Rows.Number(label, hint, get, v -> { set.accept(v); save(); }, min, max, step, big, fmt);
    }

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String ticksFmt(double v) { return (int) v + " т · " + f1(v / 20) + " с"; }

    private void buildDetectors() {
        java.util.function.BooleanSupplier master = () -> cfg().panicEnabled;
        rows.add(toggle("Паника включена", "Главный выключатель всех детекторов",
                () -> cfg().panicEnabled, v -> cfg().panicEnabled = v));
        rows.add(new Rows.Note(() -> {
            String r = PanicDetector.INSTANCE.getLastReason();
            if (r == null) return "";
            long ago = (System.currentTimeMillis() - PanicDetector.INSTANCE.getLastPanicMs()) / 1000;
            return "Последняя паника " + (ago < 60 ? ago + " с" : ago / 60 + " мин") + " назад: " + r;
        }, Ui.WARN));

        rows.add(new Rows.Section("Игрок"));
        rows.add(toggle("Поворот камеры", "Камеру сдвинули мышью во время макроса",
                () -> cfg().detectRotation, v -> cfg().detectRotation = v).enabledIf(master));
        rows.add(number("Порог по горизонтали", null, () -> cfg().yawThreshold, v -> cfg().yawThreshold = (float) v,
                0.5, 90, 0.5, 5, v -> f1(v) + "°").enabledIf(() -> cfg().panicEnabled && cfg().detectRotation));
        rows.add(number("Порог по вертикали", null, () -> cfg().pitchThreshold, v -> cfg().pitchThreshold = (float) v,
                0.5, 90, 0.5, 5, v -> f1(v) + "°").enabledIf(() -> cfg().panicEnabled && cfg().detectRotation));
        rows.add(new Rows.Note(() -> cfg().panicEnabled && cfg().detectRotation
                && Math.min(cfg().yawThreshold, cfg().pitchThreshold) < 2f
                ? "Порог меньше 2°: лёгкое касание мыши уже даст панику. По умолчанию 5°." : "", Ui.WARN));
        rows.add(toggle("Смена слота", "Слот хотбара сменил не макрос",
                () -> cfg().detectSlotChange, v -> cfg().detectSlotChange = v).enabledIf(master));
        rows.add(toggle("Урон", "Здоровье уменьшилось (учти падения на маршруте)",
                () -> cfg().detectDamage, v -> cfg().detectDamage = v).enabledIf(master));
        rows.add(toggle("Открылось окно", "Любое окно, кроме паузы, чата и меню мода",
                () -> cfg().detectGuiOpen, v -> cfg().detectGuiOpen = v).enabledIf(master));

        rows.add(new Rows.Section("Сервер"));
        rows.add(toggle("Телепорт / поворот сервером", "Сервер сам сдвинул или развернул игрока",
                () -> cfg().detectServerMove, v -> cfg().detectServerMove = v).enabledIf(master));
        rows.add(number("Мин. сдвиг", "Мелкие откаты от лагов игнорируются",
                () -> cfg().serverMoveThreshold, v -> cfg().serverMoveThreshold = v,
                0.1, 64, 0.1, 1, v -> f1(v) + " бл").enabledIf(() -> cfg().panicEnabled && cfg().detectServerMove));
        rows.add(number("Мин. поворот", null, () -> cfg().serverRotateThreshold, v -> cfg().serverRotateThreshold = (float) v,
                0.5, 180, 0.5, 5, v -> f1(v) + "°").enabledIf(() -> cfg().panicEnabled && cfg().detectServerMove));
        rows.add(toggle("Блок рядом", "Сервер поставил твёрдый блок вплотную к игроку",
                () -> cfg().detectBlockInFace, v -> cfg().detectBlockInFace = v).enabledIf(master));
        rows.add(number("Зона вокруг хитбокса", "Свои постановки блоков не считаются",
                () -> cfg().blockDetectRadius, v -> cfg().blockDetectRadius = v,
                0, 4, 0.25, 1, v -> f2(v) + " бл").enabledIf(() -> cfg().panicEnabled && cfg().detectBlockInFace));
        rows.add(toggle("Эффекты", "Новый эффект или снятие раньше срока (маяк — не считается)",
                () -> cfg().detectPotionEffect, v -> cfg().detectPotionEffect = v).enabledIf(master));

        rows.add(new Rows.Section("Маршрут"));
        rows.add(toggle("Застревание", "По записи игрок идёт, а на деле стоит на месте",
                () -> cfg().detectStuck, v -> cfg().detectStuck = v).enabledIf(master));
        rows.add(number("Окно проверки", null, () -> cfg().stuckThresholdTicks, v -> cfg().stuckThresholdTicks = (int) v,
                10, 400, 5, 20, FarmMacroScreen::ticksFmt).enabledIf(() -> cfg().panicEnabled && cfg().detectStuck));
        rows.add(toggle("Сход с маршрута", "Траектория разошлась с записью (относительно точки старта)",
                () -> cfg().detectDrift, v -> cfg().detectDrift = v).enabledIf(master));
        rows.add(number("Допуск", null, () -> cfg().driftThreshold, v -> cfg().driftThreshold = v,
                1, 64, 0.5, 4, v -> f1(v) + " бл").enabledIf(() -> cfg().panicEnabled && cfg().detectDrift));
    }

    private void buildReaction() {
        java.util.function.BooleanSupplier snd = () -> cfg().panicSoundEnabled;
        rows.add(new Rows.Section("Звук"));
        rows.add(toggle("Звук паники", null, () -> cfg().panicSoundEnabled, v -> cfg().panicSoundEnabled = v));
        rows.add(new Rows.Selector("Сигнал", "Встроенные, свои файлы или звуки Minecraft",
                () -> soundOptions, () -> cfg().panicSound,
                v -> { cfg().panicSound = v; save(); PanicSound.preload(v); }).enabledIf(snd));
        rows.add(new Rows.Buttons(
                new Rows.Btn("▶ Прослушать", Rows.Style.PRIMARY, PanicSound::playPanic),
                new Rows.Btn("Папка звуков", Rows.Style.SECONDARY, () -> openFolder(PanicSound.soundsDir()))
                        .tip("Положи туда .ogg или .wav и нажми «Обновить»"),
                new Rows.Btn("Обновить", Rows.Style.SECONDARY, () -> { PanicSound.clearCache(); refreshLists(); })
        ).enabledIf(snd));
        rows.add(toggle("Безопасный звук", "Только через движок игры (рекомендуется, пока системный режим не проверен)",
                () -> cfg().panicSoundSafe, v -> { cfg().panicSoundSafe = v; if (!v) PanicSound.preloadAll(); })
                .enabledIf(snd));
        rows.add(toggle("Мимо громкости игры", "Через систему: слышно даже при выключенном звуке MC",
                () -> cfg().panicSoundSystem, v -> cfg().panicSoundSystem = v)
                .enabledIf(() -> cfg().panicSoundEnabled && !cfg().panicSoundSafe));
        rows.add(new Rows.Note(() -> {
            if (!cfg().panicSoundEnabled) return "";
            if (!cfg().panicSoundSafe && PanicSound.systemAudioBroken())
                return "Системный звук сломался в этой сессии — играет через игру (подробности в логе).";
            if (cfg().panicSoundSafe && cfg().panicSound.startsWith("file:"))
                return "Свой файл играется только без «Безопасного звука». Сейчас вместо него — встроенная сирена.";
            return "";
        }, Ui.WARN));
        rows.add(number("Громкость", null, () -> cfg().panicSoundVolume * 100, v -> cfg().panicSoundVolume = (float) (v / 100),
                0, 100, 5, 25, v -> (int) Math.round(v) + "%").enabledIf(snd));
        rows.add(number("Тон", null, () -> cfg().panicSoundPitch, v -> cfg().panicSoundPitch = (float) v,
                0.5, 2, 0.05, 0.25, v -> "×" + f2(v)).enabledIf(snd));
        rows.add(number("Повторов", null, () -> cfg().panicSoundRepeats, v -> cfg().panicSoundRepeats = (int) v,
                1, 20, 1, 5, v -> String.valueOf((int) v)).enabledIf(snd));
        rows.add(number("Пауза между повторами", null, () -> cfg().panicSoundRepeatDelayTicks,
                v -> cfg().panicSoundRepeatDelayTicks = (int) v, 5, 200, 5, 20, FarmMacroScreen::ticksFmt)
                .enabledIf(() -> cfg().panicSoundEnabled && cfg().panicSoundRepeats > 1));
        rows.add(new Rows.Note("Свои звуки: .ogg или .wav в config/farmmacro/sounds/. Звуки «MC:» всегда идут через игру.", Ui.ACCENT));

        rows.add(new Rows.Section("Экран"));
        rows.add(toggle("Красный экран", "Вспышка с причиной паники", () -> cfg().panicRedScreenEnabled,
                v -> cfg().panicRedScreenEnabled = v));
        rows.add(number("Длительность", null, () -> cfg().panicRedScreenTicks, v -> cfg().panicRedScreenTicks = (int) v,
                10, 400, 5, 20, FarmMacroScreen::ticksFmt).enabledIf(() -> cfg().panicRedScreenEnabled));
        rows.add(new Rows.Spacer(4));
        rows.add(new Rows.Buttons(new Rows.Btn("⚠ Проверить панику", Rows.Style.DANGER, () -> {
            onClose();
            PanicDetector.INSTANCE.preview(minecraft);
        }).tip("Сирена и красный экран без остановки чего-либо")));
    }

    private void buildAutomation() {
        rows.add(new Rows.Section("Цикл"));
        rows.add(toggle("Зациклить", "После последнего кадра начинать заново",
                () -> cfg().loopEnabled, v -> cfg().loopEnabled = v));
        rows.add(number("Кругов", "0 — бесконечно", () -> cfg().loopLimit, v -> cfg().loopLimit = (int) v,
                0, 10000, 1, 10, v -> v == 0 ? "∞" : String.valueOf((int) v)).enabledIf(() -> cfg().loopEnabled));
        rows.add(number("Лимит времени", "0 — без лимита", () -> cfg().timeLimitMinutes, v -> cfg().timeLimitMinutes = (int) v,
                0, 1440, 5, 30, v -> v == 0 ? "нет" : (int) v + " мин"));

        rows.add(new Rows.Section("Автостоп"));
        rows.add(toggle("Полный инвентарь", "Стоп, когда не осталось пустых слотов (можно продолжить)",
                () -> cfg().stopWhenInventoryFull, v -> cfg().stopWhenInventoryFull = v));
        rows.add(toggle("Сигнал по окончании", "Мягкий «дзинь», когда макрос закончился сам",
                () -> cfg().finishSoundEnabled, v -> cfg().finishSoundEnabled = v));

        rows.add(new Rows.Section("Старт"));
        rows.add(number("Обратный отсчёт", "Удобно для записи видео", () -> cfg().startCountdownSeconds,
                v -> cfg().startCountdownSeconds = (int) v, 0, 30, 1, 5, v -> v == 0 ? "выкл" : (int) v + " с"));
        rows.add(number("Точка старта: допуск", "Дальше — предупреждение и стрелка в HUD",
                () -> cfg().startPointWarnDistance, v -> cfg().startPointWarnDistance = v,
                0.5, 64, 0.5, 4, v -> f1(v) + " бл"));
        rows.add(toggle("Строго с точки старта", "Не запускать, если игрок дальше допуска",
                () -> cfg().requireStartPoint, v -> cfg().requireStartPoint = v));
        rows.add(toggle("Повторять камеру", "Повороты камеры из записи (выкл — камера как есть)",
                () -> cfg().replayCamera, v -> cfg().replayCamera = v));

        rows.add(new Rows.Section("HUD"));
        rows.add(toggle("Панель статуса", "Состояние, прогресс, круги, время сессии",
                () -> cfg().statsHudEnabled, v -> cfg().statsHudEnabled = v));
        rows.add(toggle("Навигатор", "Панель со стрелкой к точке старта или остановки",
                () -> cfg().navHudEnabled, v -> cfg().navHudEnabled = v));
        rows.add(toggle("Стрелка у прицела", "Маленькая стрелка к цели вокруг прицела и расстояние",
                () -> cfg().navCrosshairEnabled, v -> cfg().navCrosshairEnabled = v));
        rows.add(new Rows.Note(() -> {
            MacroManager m = MacroManager.INSTANCE;
            if (m.getSessionRuns() == 0) return "Статистика сессии появится после первого запуска.";
            long sec = (System.currentTimeMillis() - m.getSessionStartMs()) / 1000;
            return String.format(Locale.ROOT, "Сессия: %d:%02d:%02d · кругов запущено: %d",
                    sec / 3600, sec / 60 % 60, sec % 60, m.getSessionRuns());
        }, Ui.ON));
        rows.add(new Rows.Buttons(new Rows.Btn("Сбросить статистику", Rows.Style.SECONDARY,
                () -> MacroManager.INSTANCE.resetStats()).enabledIf(() -> MacroManager.INSTANCE.getSessionRuns() > 0)));
    }

    private static final List<Rows.Choice> HAT_STYLES = com.farmmacro.visual.HatColors.STYLES.stream()
            .map(a -> new Rows.Choice(a[0], a[1])).toList();
    private static final List<Rows.Choice> HAT_COLORS = com.farmmacro.visual.HatColors.PRESETS.stream()
            .map(p -> new Rows.Choice(p.id(), p.label())).toList();

    private static final List<Rows.Choice> ROUTE_MODES = List.of(
            new Rows.Choice("always", "Всегда"), new Rows.Choice("playing", "Только при игре"));
    private static final List<Rows.Choice> ROUTE_XRAY = List.of(
            new Rows.Choice("off", "Нет"), new Rows.Choice("dim", "Слегка"), new Rows.Choice("full", "Ярко"));

    private void buildVisual() {
        java.util.function.BooleanSupplier ro = () -> cfg().routeEnabled;
        rows.add(new Rows.Section("Маршрут"));
        rows.add(toggle("Показывать маршрут", "Лента по кадрам макроса: впереди ярко, пройдено тускло, запись — красным",
                () -> cfg().routeEnabled, v -> cfg().routeEnabled = v));
        rows.add(new Rows.Selector("Когда", null, () -> ROUTE_MODES, () -> cfg().routeMode,
                v -> { cfg().routeMode = v; save(); }).enabledIf(ro));
        rows.add(new Rows.Selector("Сквозь стены", "Слегка — видно за блоками, ярко там, где не перекрыто",
                () -> ROUTE_XRAY, () -> cfg().routeSeeThrough, v -> { cfg().routeSeeThrough = v; save(); }).enabledIf(ro));
        rows.add(number("Толщина", null, () -> cfg().routeWidth, v -> cfg().routeWidth = v,
                0.02, 0.4, 0.02, 0.1, v -> f2(v) + " бл").enabledIf(ro));
        rows.add(number("Радиус отрисовки", "Дальше от игрока маршрут не рисуется", () -> cfg().routeRadius,
                v -> cfg().routeRadius = (int) v, 8, 256, 8, 32, v -> (int) v + " бл").enabledIf(ro));
        rows.add(toggle("Стрелки направления", null, () -> cfg().routeArrows, v -> cfg().routeArrows = v).enabledIf(ro));
        rows.add(number("Шаг стрелок", null, () -> cfg().routeArrowSpacing, v -> cfg().routeArrowSpacing = v,
                1, 32, 1, 4, v -> (int) v + " бл").enabledIf(() -> cfg().routeEnabled && cfg().routeArrows));
        rows.add(toggle("ЛКМ / ПКМ, прыжки", "Ломание — оранжевым, ПКМ — фиолетовым; точки прыжка и приседания",
                () -> cfg().routeShowActions, v -> cfg().routeShowActions = v).enabledIf(ro));
        rows.add(toggle("От точки запуска", "Во время игры сдвигать маршрут туда, откуда реально запущен макрос",
                () -> cfg().routeRelative, v -> cfg().routeRelative = v).enabledIf(ro));

        java.util.function.BooleanSupplier on = () -> cfg().hatEnabled;
        rows.add(new Rows.Section("China Hat"));
        rows.add(toggle("Шляпа", "Полупрозрачный конус над головой (видишь только ты)",
                () -> cfg().hatEnabled, v -> cfg().hatEnabled = v));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Фиолет → оранж", Rows.Style.SECONDARY, () -> {
                    cfg().hatStyle = "gradient"; cfg().hatColor1 = "purple"; cfg().hatColor2 = "orange"; save();
                }),
                new Rows.Btn("Голубой", Rows.Style.SECONDARY, () -> {
                    cfg().hatStyle = "solid"; cfg().hatColor1 = "cyan"; save();
                })
        ).enabledIf(on));
        rows.add(new Rows.Selector("Стиль цвета", "Градиент по кругу, один цвет или перелив радуги",
                () -> HAT_STYLES, () -> cfg().hatStyle, v -> { cfg().hatStyle = v; save(); }).enabledIf(on));
        rows.add(new Rows.Selector("Цвет 1", null, () -> HAT_COLORS, () -> cfg().hatColor1,
                v -> { cfg().hatColor1 = v; save(); })
                .enabledIf(() -> cfg().hatEnabled && !"rainbow".equals(cfg().hatStyle)));
        rows.add(new Rows.Selector("Цвет 2", "Второй цвет градиента", () -> HAT_COLORS, () -> cfg().hatColor2,
                v -> { cfg().hatColor2 = v; save(); })
                .enabledIf(() -> cfg().hatEnabled && "gradient".equals(cfg().hatStyle)));
        rows.add(number("Прозрачность", "0 — не видно, 100 — непрозрачная", () -> cfg().hatOpacity,
                v -> cfg().hatOpacity = (int) v, 0, 100, 5, 25, v -> (int) v + "%").enabledIf(on));
        rows.add(number("Радиус полей", null, () -> cfg().hatRadius, v -> cfg().hatRadius = v,
                0.3, 1.5, 0.05, 0.25, v -> f2(v) + " бл").enabledIf(on));
        rows.add(number("Высота конуса", null, () -> cfg().hatHeight, v -> cfg().hatHeight = v,
                0.05, 0.8, 0.05, 0.2, v -> f2(v) + " бл").enabledIf(on));
        rows.add(number("Смещение по высоте", "Насколько поля выше макушки", () -> cfg().hatOffset,
                v -> cfg().hatOffset = v, -0.3, 0.8, 0.02, 0.1, v -> f2(v) + " бл").enabledIf(on));
        rows.add(number("Скорость", "Вращение градиента / перелива, 0 — стоит", () -> cfg().hatSpeed,
                v -> cfg().hatSpeed = v, 0, 5, 0.1, 1, v -> "×" + f1(v)).enabledIf(on));
        rows.add(number("Сегменты", "Качество круга", () -> cfg().hatSegments,
                v -> cfg().hatSegments = (int) v, 8, 96, 4, 16, v -> String.valueOf((int) v)).enabledIf(on));
        rows.add(toggle("От первого лица", "Видно, если посмотреть вверх. В F5 видно всегда",
                () -> cfg().hatFirstPerson, v -> cfg().hatFirstPerson = v).enabledIf(on));
        rows.add(toggle("На всех игроках", "Шляпы на других игроках (видишь только ты)",
                () -> cfg().hatAllPlayers, v -> cfg().hatAllPlayers = v).enabledIf(on));
    }

    private void buildMacros() {
        rows.add(new Rows.Section("Текущий макрос"));
        rows.add(new BufferCard());
        rows.add(new Rows.Section("Сохранённые"));
        if (macros.isEmpty()) {
            rows.add(new Rows.Note("Пока пусто. Запиши макрос (" + MacroManager.keyName(FarmMacroMod.keyRecord)
                    + "), останови — появится окно сохранения.", Ui.SUB));
        } else {
            for (MacroStorage.MacroInfo info : macros) rows.add(new MacroCard(info));
        }
        rows.add(new Rows.Spacer(2));
        rows.add(new Rows.Buttons(new Rows.Btn("Открыть папку макросов", Rows.Style.SECONDARY,
                () -> openFolder(MacroStorage.MACRO_DIR))));
    }

    private void openFolder(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
        } catch (Exception e) {
            FarmMacroMod.LOGGER.warn("Не удалось открыть папку {}: {}", dir, e.toString());
        }
    }

    private void startMacroAndClose() {
        minecraft.setScreen(null);
        if (!MacroManager.INSTANCE.isActive()) MacroManager.INSTANCE.togglePlayback(minecraft);
    }

    /** Карточка буфера: что загружено и быстрые действия. */
    private final class BufferCard extends Rows.Row {
        int height(Rows.Ctx c, int w) { return 52; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            MacroManager m = MacroManager.INSTANCE;
            Ui.round(g, x, y, w, 49, 5, Ui.CARD);
            g.fill(x, y + 3, x + 2, y + 46, m.getFrameCount() > 0 ? Ui.ACCENT : Ui.BORDER);
            String title, sub;
            if (m.getFrameCount() == 0) { title = "Пусто"; sub = "Запиши или загрузи макрос из списка ниже"; }
            else {
                title = m.getLoadedName() != null ? m.getLoadedName() : "Новая запись · не сохранена";
                sub = MacroManager.formatTicks(m.getFrameCount()) + " · " + m.getFrameCount() + " кадров";
                if (minecraft != null && minecraft.player != null) sub += String.format(Locale.ROOT, " · до старта %.1f бл",
                        m.distanceToFrame(minecraft.player, 0));
            }
            Ui.text(g, font, Ui.ellipsize(font, title, w - 16), x + 8, y + 5, Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, sub, w - 16), x + 8, y + 16, Ui.SUB);
            int bw = (w - 16 - 8) / 3, by = y + 28;
            boolean has = m.getFrameCount() > 0;
            boolean active = m.isActive();
            Rows.drawButton(c, g, x + 8, by, bw, 16, active ? "■ Стоп" : "▶ Запустить",
                    active ? Rows.Style.DANGER : Rows.Style.SUCCESS, has && !m.isRecording(), mx, my);
            Rows.drawButton(c, g, x + 12 + bw, by, bw, 16, "Сохранить", Rows.Style.PRIMARY,
                    has && !m.isRecording(), mx, my);
            Rows.drawButton(c, g, x + 16 + bw * 2, by, bw, 16, "Очистить", Rows.Style.SECONDARY,
                    has && !active && !m.isRecording(), mx, my);
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            MacroManager m = MacroManager.INSTANCE;
            int bw = (lastW - 24) / 3, by = lastY + 28;
            boolean has = m.getFrameCount() > 0;
            if (!has || m.isRecording() || button != 0) return false;
            if (Ui.inside(mx, my, lastX + 8, by, bw, 16)) {
                c.clickSound();
                if (m.isActive()) m.stopPlayback(minecraft, "§e■ Остановлено из меню");
                else startMacroAndClose();
                return true;
            }
            if (Ui.inside(mx, my, lastX + 12 + bw, by, bw, 16)) {
                c.clickSound();
                minecraft.setScreen(new SaveMacroScreen(FarmMacroScreen.this, new ArrayList<>(m.getFrames()),
                        m.getLoadedName()));
                return true;
            }
            if (!m.isActive() && Ui.inside(mx, my, lastX + 16 + bw * 2, by, bw, 16)) {
                c.clickSound();
                m.clearRecording(minecraft);
                return true;
            }
            return false;
        }
    }

    /** Карточка сохранённого макроса. */
    private final class MacroCard extends Rows.Row {
        private final MacroStorage.MacroInfo info;
        MacroCard(MacroStorage.MacroInfo info) { this.info = info; }

        int height(Rows.Ctx c, int w) { return 30; }

        private int[] buttonsX() {          // ▶, Загрузить, ✕
            int right = lastX + lastW - 6;
            int del = right - 18, load = del - 4 - 62, play = load - 4 - 22;
            return new int[]{play, load, del};
        }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean current = info.name().equals(MacroManager.INSTANCE.getLoadedName());
            Ui.round(g, x, y, w, 27, 4, hover ? Ui.CARD_HOVER : Ui.CARD);
            if (current) g.fill(x, y + 3, x + 2, y + 24, Ui.ON);
            lastX = x; lastW = w;
            int[] bx = buttonsX();
            int textW = bx[0] - x - 14;
            Ui.text(g, font, Ui.ellipsize(font, info.name(), textW), x + 8, y + 4, current ? Ui.ON : Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, MacroManager.formatTicks(info.frameCount()) + " · "
                    + info.frameCount() + " кадров", textW), x + 8, y + 15, Ui.SUB);
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = info.filename().equals(confirmDelete) && System.currentTimeMillis() < confirmUntil;
            int by = y + 5;
            if (Rows.drawButton(c, g, bx[0], by, 22, 16, "▶", Rows.Style.SUCCESS, idle, mx, my))
                c.tooltip("Загрузить и запустить");
            if (confirming) {
                Rows.drawButton(c, g, bx[1], by, bx[2] + 18 - bx[1], 16, "Точно удалить?", Rows.Style.DANGER, true, mx, my);
            } else {
                Rows.drawButton(c, g, bx[1], by, 62, 16, current ? "Загружен" : "Загрузить", Rows.Style.SECONDARY, idle, mx, my);
                if (Rows.drawButton(c, g, bx[2], by, 18, 16, "✕", Rows.Style.SECONDARY, true, mx, my))
                    c.tooltip("Удалить «" + info.name() + "»");
            }
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int[] bx = buttonsX();
            int by = lastY + 5;
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = info.filename().equals(confirmDelete) && System.currentTimeMillis() < confirmUntil;
            if (confirming && Ui.inside(mx, my, bx[1], by, bx[2] + 18 - bx[1], 16)) {
                c.clickSound();
                MacroStorage.INSTANCE.delete(info.filename());
                confirmDelete = null;
                refreshLists();
                buildTab();
                return true;
            }
            if (!confirming && Ui.inside(mx, my, bx[2], by, 18, 16)) {
                c.clickSound();
                confirmDelete = info.filename();
                confirmUntil = System.currentTimeMillis() + 3000;
                return true;
            }
            if (idle && (Ui.inside(mx, my, bx[0], by, 22, 16) || (!confirming && Ui.inside(mx, my, bx[1], by, 62, 16)))) {
                c.clickSound();
                var frames = MacroStorage.INSTANCE.load(info.filename());
                if (frames == null) {
                    minecraft.player.sendOverlayMessage(Component.literal("§c[FM] Не удалось прочитать файл макроса (подробности в логе)"));
                    return true;
                }
                if (MacroManager.INSTANCE.loadMacro(info.name(), frames, minecraft) && mx < bx[1]) startMacroAndClose();
                return true;
            }
            return false;
        }
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        drawWindow(g);
    }

    /** Подложка окна (отдельно от фона мира, чтобы её можно было рисовать в превью-стенде). */
    void drawWindow(GuiGraphicsExtractor g) {
        int x = winX(), y = winY(), w = winW(), h = winH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.round(g, x, y, w, h, 8, Ui.WINDOW);
        // боковая панель
        Ui.round(g, x, y, sideW() + 8, h, 8, Ui.SIDEBAR);
        g.fill(x + sideW(), y, x + sideW() + 8, y + h, Ui.WINDOW);
        g.fill(x + sideW(), y + 6, x + sideW() + 1, y + h - 6, Ui.BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        tooltip = null;
        wantHand = false;
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
        lastFrameNs = now;

        int x = winX(), y = winY(), w = winW();
        renderSidebar(g, x, y, mx, my);
        renderHeader(g, x, y, w, mx, my);

        // содержимое с прокруткой
        int cx = contX(), cy = contY(), cw = contW(), ch = contH();
        int total = contentHeight(cw);
        double maxScroll = Math.max(0, total - ch);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 16);
        if (Math.abs(scrollTarget - scroll) < 0.3) scroll = scrollTarget;

        boolean inContent = Ui.inside(mx, my, cx, cy, cw, ch);
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int ry = cy - (int) Math.round(scroll);
        for (Rows.Row r : rows) {
            int rh = r.height(this, cw);
            r.lastX = cx; r.lastY = ry; r.lastW = cw; r.lastH = rh;
            if (rh > 0 && ry + rh > cy && ry < cy + ch) {
                boolean hover = inContent && my >= ry && my < ry + rh;
                r.render(this, g, cx, ry, cw, inContent ? mx : -1, inContent ? my : -1, hover);
            }
            ry += rh;
        }
        g.disableScissor();

        // полоса прокрутки
        if (maxScroll > 0) {
            int sx = cx + cw + 3;
            int barH = Math.max(18, (int) ((double) ch * ch / total));
            int barY = cy + (int) ((ch - barH) * (scroll / maxScroll));
            Ui.pill(g, sx, cy, 3, ch, Ui.alpha(Ui.BORDER, 0.6f));
            boolean hb = Ui.inside(mx, my, sx - 2, cy, 7, ch);
            Ui.pill(g, sx, barY, 3, barH, hb || draggingScrollbar ? Ui.ACCENT_HI : Ui.ACCENT);
            // мягкое затухание у краёв (новый слой, иначе текст рисуется поверх градиента)
            g.nextStratum();
            if (scroll > 1) g.fillGradient(cx - 2, cy, cx + cw + 2, cy + 8, 0xC0151A24, 0x00151A24);
            if (scroll < maxScroll - 1) g.fillGradient(cx - 2, cy + ch - 8, cx + cw + 2, cy + ch, 0x00151A24, 0xC0151A24);
        }

        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        if (tooltip != null) drawTooltip(g, tooltip, mx, my);
    }

    private int contentHeight(int cw) {
        int t = 0;
        for (Rows.Row r : rows) t += r.height(this, cw);
        return t + 4;
    }

    private void renderHeader(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        int hx = x + sideW() + 10;
        // заголовок вкладки
        Ui.text(g, font, TABS[tab], hx, y + 11, Ui.TEXT);
        // статус справа
        MacroManager m = MacroManager.INSTANCE;
        String status; int col;
        switch (m.getState()) {
            case RECORDING -> { status = "● ЗАПИСЬ " + MacroManager.formatTicks(m.getFrameCount()); col = Ui.DANGER; }
            case COUNTDOWN -> { status = "◷ СТАРТ ЧЕРЕЗ " + ((m.getCountdownTicks() + 19) / 20); col = Ui.WARN; }
            case PLAYING -> { status = "▶ " + m.getPlaybackIndex() + "/" + m.getFrameCount()
                    + (cfg().loopEnabled ? " · круг " + (m.getLoopsDone() + 1) : ""); col = Ui.ON; }
            default -> {
                status = m.getFrameCount() > 0 ? "■ ГОТОВ" : "■ НЕТ МАКРОСА";
                col = m.getFrameCount() > 0 ? Ui.SUB : Ui.DIM;
            }
        }
        // кнопка закрытия
        int bx = x + w - 24, by = y + 7;
        boolean hc = Ui.inside(mx, my, bx, by, 16, 16);
        Ui.round(g, bx, by, 16, 16, 4, hc ? Ui.DANGER : Ui.CARD);
        Ui.textCentered(g, font, "✕", bx + 8, by + 4, hc ? 0xFFFFFFFF : Ui.SUB);
        if (hc) hand();

        int maxChip = Math.max(40, bx - 8 - (hx + font.width(TABS[tab]) + 10));
        status = Ui.ellipsize(font, status, maxChip - 12);
        int sw = font.width(status) + 12;
        int sx = bx - 6 - sw;
        Ui.pill(g, sx, y + 8, sw, 14, Ui.alpha(col, 0.16f));
        Ui.text(g, font, status, sx + 6, y + 11, col);
        g.fill(x + sideW() + 10, y + HEADER_H, x + w - 10, y + HEADER_H + 1, Ui.BORDER);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        boolean compact = sideW() < 60;
        // логотип
        if (compact) {
            Ui.circle(g, x + sideW() / 2, y + 15, 5, Ui.ACCENT);
        } else {
            Ui.circle(g, x + 14, y + 15, 4, Ui.ACCENT);
            Ui.text(g, font, "Farm", x + 22, y + 11, Ui.TEXT);
            Ui.text(g, font, "Macro", x + 22 + font.width("Farm"), y + 11, Ui.ACCENT_HI);
        }
        int ty = y + HEADER_H + 6;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + 6, tw = sideW() - 12, th = 20;
            boolean active = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, tw, th);
            if (active) Ui.round(g, tx, ty, tw, th, 5, Ui.alpha(Ui.ACCENT, 0.18f));
            else if (hover) Ui.round(g, tx, ty, tw, th, 5, Ui.CARD);
            if (active) Ui.pill(g, tx, ty + 5, 2, th - 10, Ui.ACCENT);
            int col = active ? Ui.TEXT : hover ? Ui.TEXT : Ui.SUB;
            if (compact) {
                Ui.textCentered(g, font, TAB_ICONS[i], tx + tw / 2, ty + 6, active ? Ui.ACCENT_HI : col);
                if (hover) tooltip(TABS[i]);
            } else {
                Ui.text(g, font, TAB_ICONS[i], tx + 7, ty + 6, active ? Ui.ACCENT_HI : Ui.DIM);
                Ui.text(g, font, Ui.ellipsize(font, TABS[i], tw - 24), tx + 20, ty + 6, col);
            }
            if (hover && !active) hand();
            ty += th + 3;
        }
        // подсказки по клавишам внизу
        if (!compact) {
            int ky = y + winH() - 12 - 3 * 11;
            keyHint(g, x + 10, ky, "Запись", FarmMacroMod.keyRecord);
            keyHint(g, x + 10, ky + 11, "Старт/стоп", FarmMacroMod.keyPlay);
            keyHint(g, x + 10, ky + 22, "Продолжить", FarmMacroMod.keyResume);
        }
    }

    private void keyHint(GuiGraphicsExtractor g, int x, int y, String what, net.minecraft.client.KeyMapping key) {
        String k = MacroManager.keyName(key);
        int kw = font.width(k) + 6;
        int right = x + sideW() - 20;
        Ui.text(g, font, Ui.ellipsize(font, what, right - kw - x - 4), x, y + 1, Ui.DIM);
        Ui.round(g, right - kw, y, kw, 10, 3, Ui.CARD);
        Ui.text(g, font, k, right - kw + 3, y + 1, Ui.SUB);
    }

    private void drawTooltip(GuiGraphicsExtractor g, String text, int mx, int my) {
        g.nextStratum();
        int maxW = Math.min(220, width - 20);
        var lines = font.split(Component.literal(text), maxW);
        int tw = 0;
        for (var l : lines) tw = Math.max(tw, font.width(l));
        int th = lines.size() * 10 + 6;
        int tx = Math.min(mx + 10, width - tw - 14), ty = my + 12;
        if (ty + th > height - 4) ty = my - th - 4;
        Ui.roundBordered(g, tx, ty, tw + 12, th + 2, 4, 0xF00B0E14, Ui.BORDER);
        int ly = ty + 5;
        for (var l : lines) { g.text(font, l, tx + 6, ly, Ui.TEXT, false); ly += 10; }
    }

    // ── Rows.Ctx ─────────────────────────────────────────────────────────────

    @Override public Font font() { return font; }
    @Override public void clickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
    @Override public void tooltip(String text) { tooltip = text; }
    @Override public void hand() { wantHand = true; }
    @Override public boolean shift() { return minecraft.hasShiftDown(); }

    // ── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        Rows.clearWheelFocus();          // фокус колёсика снова поставит строка, по полю которой кликнули
        int x = winX(), y = winY(), w = winW();
        // закрыть
        if (Ui.inside(mx, my, x + w - 24, y + 7, 16, 16)) { clickSound(); onClose(); return true; }
        // вкладки
        int ty = y + HEADER_H + 6;
        for (int i = 0; i < TABS.length; i++) {
            if (Ui.inside(mx, my, x + 6, ty, sideW() - 12, 20)) {
                if (i != tab) clickSound();
                switchTab(i);
                return true;
            }
            ty += 23;
        }
        // полоса прокрутки
        int cx = contX(), cy = contY(), cw = contW(), ch = contH();
        if (Ui.inside(mx, my, cx + cw + 1, cy, 7, ch) && contentHeight(cw) > ch) {
            draggingScrollbar = true;
            dragScroll(my);
            return true;
        }
        // строки
        if (Ui.inside(mx, my, cx, cy, cw, ch)) {
            for (Rows.Row r : new ArrayList<>(rows)) {
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                    if (r.click(this, mx, my, e.button())) return true;
                    break;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    private void dragScroll(double my) {
        int cy = contY(), ch = contH();
        int total = contentHeight(contW());
        double maxScroll = Math.max(0, total - ch);
        int barH = Math.max(18, (int) ((double) ch * ch / total));
        double t = (my - cy - barH / 2.0) / Math.max(1, ch - barH);
        scrollTarget = scroll = Math.max(0, Math.min(maxScroll, t * maxScroll));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingScrollbar) { dragScroll(e.y()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingScrollbar = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int cx = contX(), cy = contY(), cw = contW(), ch = contH();
        if (Ui.inside(mx, my, cx, cy, cw, ch)) {
            for (Rows.Row r : rows) {
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                    if (r.scroll(this, mx, my, sy)) return true;
                    break;
                }
            }
            Rows.clearWheelFocus();      // список поехал — поле под курсором уже другое
            scrollTarget -= sy * 24;
            return true;
        }
        // колесо над вкладками — листать вкладки
        if (Ui.inside(mx, my, winX(), winY(), sideW(), winH())) {
            switchTab(Math.floorMod(tab + (sy > 0 ? -1 : 1), TABS.length));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (FarmMacroMod.keyOpenGui != null && FarmMacroMod.keyOpenGui.matches(e)) { onClose(); return true; }
        if (e.key() == 258) {               // Tab — следующая вкладка (Shift+Tab — предыдущая)
            switchTab(Math.floorMod(tab + (e.hasShiftDown() ? -1 : 1), TABS.length));
            return true;
        }
        if (e.key() == 266) { scrollTarget -= contH() * 0.8; return true; }   // PageUp
        if (e.key() == 267) { scrollTarget += contH() * 0.8; return true; }   // PageDown
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() {
        Rows.clearWheelFocus();
        ModConfig.save();
        minecraft.setScreen(parent);
    }
}
