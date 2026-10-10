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

    private static final String[] TABS = {"Паника", "Запуск", "Макросы", "Маршруты", "Камера", "Визуал", "Конфиги"};
    private static final String[] TAB_ICONS = {"⚠", "▶", "☰", "⌖", "◎", "✦", "⚙"};
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
    private List<com.farmmacro.route.RouteStorage.RouteInfo> routes = List.of();
    private long confirmClearRouteUntil;
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
        routes = com.farmmacro.route.RouteStorage.INSTANCE.list();
    }

    private void switchTab(int t) {
        if (t == tab && search.query().isEmpty()) return;
        if (!search.query().isEmpty()) { search.clear(); pendingRebuild = false; }
        tab = lastTab = t;
        scroll = scrollTarget = 0;
        Rows.clearWheelFocus();
        Rows.blurText();
        buildTab();
    }

    // ── Содержимое вкладок ───────────────────────────────────────────────────

    private static ModConfig cfg() { return ModConfig.INSTANCE; }
    private static void save() { ModConfig.save(); }

    // ── v1.11: вкладки, поиск, простой/расширенный режим ─────────────────────

    /** Поиск живёт между перестройками списка (в нём фокус ввода). Перестраиваем в начале кадра, не посреди отрисовки. */
    private final Rows.Search search = new Rows.Search(q -> { pendingRebuild = true; scrollTarget = 0; });
    private boolean pendingRebuild;
    private String pendingJump;        // ключ раздела, к которому прокрутить после перестройки

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е').strip(); }

    private String title() { return search.query().isEmpty() ? TABS[tab] : "Поиск"; }

    private void buildTab() {
        rows.clear();
        String q = norm(search.query());
        if (!q.isEmpty()) { buildSearch(q); return; }
        Rows.searching = false;
        rows.add(search);
        buildContent(tab);
        rows.add(new Rows.End());
        rows.add(new Rows.MoreToggle(() -> Rows.hiddenAdvanced(rows), this::toggleAdvanced));
        Rows.link(rows);
    }

    private void buildContent(int t) {
        switch (t) {
            case 0 -> buildPanic();
            case 1 -> buildLaunch();
            case 2 -> buildMacros();
            case 3 -> buildRoutes();
            case 4 -> buildCamera();
            case 5 -> buildVisual();
            case 6 -> buildConfigs();
        }
    }

    /** Результаты поиска: подходящие строки всех вкладок, сгруппированные «Вкладка › Раздел». */
    private void buildSearch(String q) {
        Rows.searching = false;
        List<Rows.Row> out = new ArrayList<>();
        out.add(search);
        String[] words = q.split("\\s+");
        for (int t = 0; t < TABS.length; t++) {
            rows.clear();
            buildContent(t);
            List<Rows.Row> part = new ArrayList<>(rows);
            Rows.Section cur = null, header = null;
            boolean curMatch = false;
            for (Rows.Row r : part) {
                if (r instanceof Rows.Section s) {
                    cur = s; header = null;
                    curMatch = matches(s.title(), words) || matches(TABS[t] + " " + s.title(), words);
                    continue;
                }
                if (r instanceof Rows.End) { cur = null; header = null; curMatch = false; continue; }
                if (r instanceof Rows.Spacer) continue;
                if (!curMatch && !matches(r.searchText(), words)) continue;
                if (header == null) { header = searchHeader(t, cur); out.add(header); }
                out.add(r);
            }
        }
        rows.clear();
        rows.addAll(out);
        if (out.size() == 1) rows.add(new Rows.Note("Ничего не найдено. Попробуй другое слово: «звук», «шаг», «камера», «HUD»…", Ui.SUB));
        Rows.searching = true;
        Rows.link(rows);
    }

    private static boolean matches(String text, String[] words) {
        String t = norm(text);
        if (t.isEmpty()) return false;
        for (String w : words) if (!t.contains(w)) return false;
        return true;
    }

    private Rows.Section searchHeader(int t, Rows.Section s) {
        String name = TABS[t] + (s != null ? " › " + s.title() : "");
        String key = s != null ? s.key() : null;
        return new Rows.Section(name).key("search:" + name).onClick(() -> {
            if (key != null) { cfg().uiSections.put(key, true); save(); }
            Rows.blurText();
            Rows.clearWheelFocus();
            search.clear();
            tab = lastTab = t;
            scroll = scrollTarget = 0;
            pendingJump = key;
            pendingRebuild = true;
        });
    }

    private void toggleAdvanced() {
        cfg().uiAdvanced = !cfg().uiAdvanced;
        save();
    }

    /** Перестроить список, если просили (поиск изменился, переход из поиска), и убрать фокус со скрытого поля. */
    private void applyPending() {
        if (pendingRebuild) {
            pendingRebuild = false;
            buildTab();
            if (pendingJump != null) {
                int y = 0, cw = contW();
                for (Rows.Row r : rows) {
                    if (r instanceof Rows.Section s && pendingJump.equals(s.key())) break;
                    y += r.h(this, cw);
                }
                scroll = scrollTarget = y;
                pendingJump = null;
            }
        }
        Rows.Row f = Rows.focusedRow();
        if (f != null && (!rows.contains(f) || !f.shown())) Rows.blurText();
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

    /** Вкладка «Паника»: детекторы по группам (свёрнуты, справа — сколько включено) + реакция (звук, экран, журнал). */
    private void buildPanic() {
        java.util.function.BooleanSupplier master = () -> cfg().panicEnabled;
        rows.add(toggle("Паника включена", "Главный выключатель всех детекторов",
                () -> cfg().panicEnabled, v -> cfg().panicEnabled = v));
        rows.add(new Rows.Note(() -> {
            String r = PanicDetector.INSTANCE.getLastReason();
            if (r == null) return "";
            long ago = (System.currentTimeMillis() - PanicDetector.INSTANCE.getLastPanicMs()) / 1000;
            return "Последняя паника " + (ago < 60 ? ago + " с" : ago / 60 + " мин") + " назад: " + r;
        }, Ui.WARN));
        rows.add(new Rows.Note("Детекторы разложены по группам — нажми на заголовок, чтобы развернуть. Пороги появляются, "
                + "когда детектор включён; тонкие пороги — в расширенном режиме.", Ui.SUB).adv());

        rows.add(new Rows.Section("Камера и руки").key("p.hands").closed());
        java.util.function.BooleanSupplier rot = () -> cfg().panicEnabled && cfg().detectRotation;
        rows.add(toggle("Поворот камеры", "Камеру сдвинули мышью во время макроса",
                () -> cfg().detectRotation, v -> cfg().detectRotation = v).enabledIf(master));
        rows.add(number("Порог по горизонтали", null, () -> cfg().yawThreshold, v -> cfg().yawThreshold = (float) v,
                0.001, 180, 0.5, 5, v -> Rows.num(v) + "°").under(rot));
        rows.add(number("Порог по вертикали", null, () -> cfg().pitchThreshold, v -> cfg().pitchThreshold = (float) v,
                0.001, 180, 0.5, 5, v -> Rows.num(v) + "°").under(rot));
        rows.add(new Rows.Note(() -> rot.getAsBoolean() && Math.min(cfg().yawThreshold, cfg().pitchThreshold) < 2f
                ? "Порог меньше 2°: лёгкое касание мыши уже даст панику. По умолчанию 5°." : "", Ui.WARN));
        rows.add(toggle("Смена слота", "Слот хотбара сменил не макрос (и сервер тоже)",
                () -> cfg().detectSlotChange, v -> cfg().detectSlotChange = v).enabledIf(master));
        rows.add(toggle("Предмет в руке", "Сменился или пропал сам (слот тот же). Кончились семена — не считается",
                () -> cfg().detectHeldItem, v -> cfg().detectHeldItem = v).enabledIf(master));
        rows.add(toggle("Урон", "Здоровье уменьшилось или пришло событие урона (даже 0). Падение на точке-спуске — нет",
                () -> cfg().detectDamage, v -> cfg().detectDamage = v).enabledIf(master));
        rows.add(toggle("Открылось окно", "Любое окно, кроме паузы, чата и меню мода",
                () -> cfg().detectGuiOpen, v -> cfg().detectGuiOpen = v).enabledIf(master));

        rows.add(new Rows.Section("Телепорт и толчки").key("p.tp").closed());
        java.util.function.BooleanSupplier mv = () -> cfg().panicEnabled && cfg().detectServerMove;
        java.util.function.BooleanSupplier sync = () -> mv.getAsBoolean() && cfg().detectServerSync;
        rows.add(toggle("Телепорт / поворот сервером", "Сервер сам сдвинул или развернул игрока",
                () -> cfg().detectServerMove, v -> cfg().detectServerMove = v).enabledIf(master));
        rows.add(number("Мин. сдвиг", "Мелкие откаты от лагов игнорируются",
                () -> cfg().serverMoveThreshold, v -> cfg().serverMoveThreshold = v,
                0.001, 1024, 0.1, 1, v -> Rows.num(v) + " бл").under(mv));
        rows.add(number("Мин. поворот", null, () -> cfg().serverRotateThreshold, v -> cfg().serverRotateThreshold = (float) v,
                0.001, 180, 0.5, 5, v -> Rows.num(v) + "°").under(mv));
        rows.add(toggle("Любой пакет телепорта", "Даже /tp на то же место (0 бл) и «посмотреть на». Откаты античита тоже",
                () -> cfg().serverMoveAny, v -> cfg().serverMoveAny = v).under(mv));
        rows.add(toggle("Синхронизация (эпсилон)", "Сервер поправил позицию или взгляд больше эпсилона. Меньше — в подозрительность",
                () -> cfg().detectServerSync, v -> cfg().detectServerSync = v).under(mv));
        rows.add(number("Эпсилон позиции", null, () -> cfg().serverPosEpsilon, v -> cfg().serverPosEpsilon = v,
                0.0001, 16, 0.01, 0.1, v -> Rows.num(v) + " бл").under(sync).adv());
        rows.add(number("Эпсилон взгляда", "yaw и pitch отдельно", () -> cfg().serverRotEpsilon, v -> cfg().serverRotEpsilon = (float) v,
                0.0001, 180, 0.01, 0.5, v -> Rows.num(v) + "°").under(sync).adv());
        rows.add(toggle("Толчок", "Сервер задал скорость игроку: отдача, удочка, взрыв, плагин",
                () -> cfg().detectKnockback, v -> cfg().detectKnockback = v).enabledIf(master));
        rows.add(number("Мин. толчок", "Слабее — в подозрительность", () -> cfg().knockbackThreshold, v -> cfg().knockbackThreshold = v,
                0.001, 10, 0.01, 0.1, v -> Rows.num(v) + " бл/т").under(() -> cfg().panicEnabled && cfg().detectKnockback).adv());

        rows.add(new Rows.Section("Движение и маршрут").key("p.move").closed());
        rows.add(toggle("Параметры движения", "Сервер изменил скорость, прыжок, гравитацию, высоту шага, размер, скорость ходьбы/полёта",
                () -> cfg().detectMoveAttrs, v -> cfg().detectMoveAttrs = v).enabledIf(master));
        rows.add(number("Допуск параметров", "Спринт и снег не считаются", () -> cfg().attrEpsilon, v -> cfg().attrEpsilon = v,
                0.01, 100, 0.5, 5, v -> Rows.num(v) + " %").under(() -> cfg().panicEnabled && cfg().detectMoveAttrs).adv());
        rows.add(toggle("Аномалия скорости", "Маршрут по точкам: скорость разошлась с ожидаемой без толчка и без стены",
                () -> cfg().detectVelocity, v -> cfg().detectVelocity = v).enabledIf(master));
        rows.add(number("Допуск скорости", "Ходьба ≈ 0.22 бл/т, бег ≈ 0.28", () -> cfg().velocityAnomaly, v -> cfg().velocityAnomaly = v,
                0.01, 5, 0.01, 0.1, v -> Rows.num(v) + " бл/т").under(() -> cfg().panicEnabled && cfg().detectVelocity).adv());
        rows.add(toggle("Застревание", "Игрок должен идти, а стоит на месте",
                () -> cfg().detectStuck, v -> cfg().detectStuck = v).enabledIf(master));
        rows.add(number("Окно проверки", null, () -> cfg().stuckThresholdTicks, v -> cfg().stuckThresholdTicks = (int) v,
                1, 12000, 5, 20, FarmMacroScreen::ticksFmt).integer().under(() -> cfg().panicEnabled && cfg().detectStuck));
        rows.add(toggle("Сход с маршрута", "Траектория разошлась с записью / линией маршрута",
                () -> cfg().detectDrift, v -> cfg().detectDrift = v).enabledIf(master));
        rows.add(number("Допуск", null, () -> cfg().driftThreshold, v -> cfg().driftThreshold = v,
                0.01, 1024, 0.5, 4, v -> Rows.num(v) + " бл").under(() -> cfg().panicEnabled && cfg().detectDrift));
        rows.add(toggle("Этажи", "Маршрут по точкам: после спуска упал не туда / не упал, точка на другом этаже без спуска",
                () -> cfg().detectFloor, v -> cfg().detectFloor = v).enabledIf(master));

        rows.add(new Rows.Section("Блоки, эффекты, режим").key("p.world").closed());
        rows.add(toggle("Блок рядом", "Сервер поставил твёрдый блок вплотную к игроку",
                () -> cfg().detectBlockInFace, v -> cfg().detectBlockInFace = v).enabledIf(master));
        rows.add(number("Зона вокруг хитбокса", "Свои постановки блоков не считаются",
                () -> cfg().blockDetectRadius, v -> cfg().blockDetectRadius = v,
                0, 8, 0.25, 1, v -> Rows.num(v) + " бл").under(() -> cfg().panicEnabled && cfg().detectBlockInFace).adv());
        rows.add(toggle("Препятствие впереди", "Сервер поставил твёрдый блок на луче взгляда или по ходу автохода. Рост урожая и поршни — нет",
                () -> cfg().detectObstacle, v -> cfg().detectObstacle = v).enabledIf(master));
        rows.add(number("Дальность луча", null, () -> cfg().obstacleRange, v -> cfg().obstacleRange = v,
                1, 16, 0.5, 2, v -> Rows.num(v) + " бл").under(() -> cfg().panicEnabled && cfg().detectObstacle).adv());
        rows.add(toggle("Эффекты", "Новый эффект или снятие раньше срока (маяк — не считается)",
                () -> cfg().detectPotionEffect, v -> cfg().detectPotionEffect = v).enabledIf(master));
        rows.add(toggle("Режим игры и полёт", "Сменили режим, дали/забрали полёт, респавн/другой мир, посадили на сущность",
                () -> cfg().detectGameMode, v -> cfg().detectGameMode = v).enabledIf(master));

        rows.add(new Rows.Section("Чат и игроки").key("p.social").closed());
        java.util.function.BooleanSupplier chat = () -> cfg().panicEnabled && cfg().detectChat;
        rows.add(toggle("Чат", "Сообщение с твоим ником или словом из списка (свои — не считаются)",
                () -> cfg().detectChat, v -> cfg().detectChat = v).enabledIf(master));
        rows.add(toggle("Чат: мой ник", null, () -> cfg().chatMentionName, v -> cfg().chatMentionName = v).under(chat));
        rows.add(new Rows.TextField("Чат: слова", "Через запятую, без учёта регистра. Пусто — только ник", () -> cfg().chatKeywords, v -> {
            cfg().chatKeywords = v.strip();
            save();
            return true;
        }, null, 1000, 0).under(chat));
        rows.add(toggle("Титры", "/title на экране; надпись над хотбаром — только с ником или словом",
                () -> cfg().detectTitle, v -> cfg().detectTitle = v).enabledIf(master));
        rows.add(toggle("Игрок рядом", "Подошёл другой игрок (невидимый тоже). Кто был рядом на старте — не считается",
                () -> cfg().detectPlayerNear, v -> cfg().detectPlayerNear = v).enabledIf(master));
        rows.add(number("Радиус", "Дальше — в подозрительность", () -> cfg().playerNearRadius, v -> cfg().playerNearRadius = v,
                1, 128, 1, 8, v -> Rows.num(v) + " бл").under(() -> cfg().panicEnabled && cfg().detectPlayerNear));
        rows.add(toggle("Наблюдатель", "Игрок в Tab стал наблюдателем (его не видно, а смену режима — видно)",
                () -> cfg().detectSpectator, v -> cfg().detectSpectator = v).enabledIf(master));
        rows.add(toggle("Вход / выход игроков", "Зашёл кто-то или пропал из Tab (часто так выглядит «ваниш»)",
                () -> cfg().detectPlayerJoin, v -> cfg().detectPlayerJoin = v).enabledIf(master));

        rows.add(new Rows.Section("Подозрительность").key("p.susp").closed());
        rows.add(new Rows.Note("Мелкие события ниже порогов дают очки, очки затухают. Сумма дошла до лимита — паника «Много мелких аномалий».", Ui.SUB));
        java.util.function.BooleanSupplier susp = () -> cfg().panicEnabled && cfg().suspicionEnabled;
        rows.add(toggle("Подозрительность", "Серия мелких аномалий = паника",
                () -> cfg().suspicionEnabled, v -> cfg().suspicionEnabled = v).enabledIf(master));
        rows.add(number("Лимит", null, () -> cfg().suspicionLimit, v -> cfg().suspicionLimit = v,
                0.1, 1000, 1, 5, v -> Rows.num(v) + " оч.").under(susp));
        rows.add(number("Полураспад", "Через столько вклад события вдвое меньше", () -> cfg().suspicionHalfLifeSec, v -> cfg().suspicionHalfLifeSec = v,
                1, 3600, 5, 30, v -> Rows.num(v) + " с").under(susp));
        rows.add(number("Вес: пакет ниже эпсилона", "Телепорт/поворот меньше эпсилона, в т.ч. без сдвига", () -> cfg().suspWeightSync, v -> cfg().suspWeightSync = v,
                0, 100, 0.5, 2, Rows::num).under(susp).adv());
        rows.add(number("Вес: лаг-откат", "Больше эпсилона, но «Синхронизация» выкл", () -> cfg().suspWeightRollback, v -> cfg().suspWeightRollback = v,
                0, 100, 0.5, 2, Rows::num).under(susp).adv());
        rows.add(number("Вес: слабый толчок", "Меньше «Мин. толчок»", () -> cfg().suspWeightKnock, v -> cfg().suspWeightKnock = v,
                0, 100, 0.5, 2, Rows::num).under(susp).adv());
        rows.add(number("Вес: игрок за радиусом", "Раз за запуск на игрока; кто был на старте — нет", () -> cfg().suspWeightPlayerFar, v -> cfg().suspWeightPlayerFar = v,
                0, 100, 0.5, 2, Rows::num).under(susp).adv());
        rows.add(number("Вес: надпись над хотбаром", "Новая надпись без ника/слова; цифры не различаются", () -> cfg().suspWeightActionBar, v -> cfg().suspWeightActionBar = v,
                0, 100, 0.5, 2, Rows::num).under(susp).adv());
        rows.add(toggle("Слова без ника — в подозрительность", "Слово из списка без ника даёт очки, а не панику. Ник — всегда паника",
                () -> cfg().chatWordsSoft, v -> cfg().chatWordsSoft = v).under(susp));
        rows.add(number("Вес: слово в чате", null, () -> cfg().suspWeightChatWord, v -> cfg().suspWeightChatWord = v,
                0, 100, 0.5, 2, Rows::num).under(() -> susp.getAsBoolean() && cfg().chatWordsSoft));

        buildReaction();
    }

    private static final List<Rows.Choice> SOUND_OUT = List.of(
            new Rows.Choice("game", "Через игру (безопасно)"),
            new Rows.Choice("files", "Через игру + свои файлы"),
            new Rows.Choice("system", "Через систему"));

    /** «Безопасный звук» и «Мимо громкости игры» — один выбор (раньше два зависимых переключателя). */
    private static String soundOut() {
        return cfg().panicSoundSafe ? "game" : cfg().panicSoundSystem ? "system" : "files";
    }

    /** Реакция на панику: звук, красный экран, журнал (раньше — отдельная вкладка «Реакция»). */
    private void buildReaction() {
        java.util.function.BooleanSupplier snd = () -> cfg().panicSoundEnabled;
        rows.add(new Rows.Section("Звук паники").key("p.sound").closed());
        rows.add(toggle("Звук паники", null, () -> cfg().panicSoundEnabled, v -> cfg().panicSoundEnabled = v));
        rows.add(new Rows.Selector("Сигнал", "Встроенные, свои файлы или звуки Minecraft",
                () -> soundOptions, () -> cfg().panicSound,
                v -> { cfg().panicSound = v; save(); PanicSound.preload(v); }).under(snd));
        rows.add(new Rows.Buttons(
                new Rows.Btn("▶ Прослушать", Rows.Style.PRIMARY, PanicSound::playPanic),
                new Rows.Btn("Папка звуков", Rows.Style.SECONDARY, () -> openFolder(PanicSound.soundsDir()))
                        .tip("Положи туда .ogg или .wav и нажми «Обновить»"),
                new Rows.Btn("Обновить", Rows.Style.SECONDARY, () -> { PanicSound.clearCache(); refreshLists(); })
        ).under(snd));
        rows.add(new Rows.Selector("Вывод звука", "Безопасно — только движок игры. Свои файлы — без «безопасно». Система — слышно даже при выключенном звуке MC",
                () -> SOUND_OUT, FarmMacroScreen::soundOut, v -> {
            cfg().panicSoundSafe = "game".equals(v);
            cfg().panicSoundSystem = "system".equals(v);
            save();
            if (!cfg().panicSoundSafe) PanicSound.preloadAll();
        }).under(snd));
        rows.add(new Rows.Note(() -> {
            if (!cfg().panicSoundEnabled) return "";
            if (!cfg().panicSoundSafe && PanicSound.systemAudioBroken())
                return "Системный звук сломался в этой сессии — играет через игру (подробности в логе).";
            if (cfg().panicSoundSafe && cfg().panicSound.startsWith("file:"))
                return "Свой файл играется только без «безопасно». Сейчас вместо него — встроенная сирена.";
            return "";
        }, Ui.WARN));
        rows.add(number("Громкость", null, () -> cfg().panicSoundVolume * 100, v -> cfg().panicSoundVolume = (float) (v / 100),
                0, 100, 5, 25, v -> Rows.num(v) + "%").under(snd));
        rows.add(number("Повторов", null, () -> cfg().panicSoundRepeats, v -> cfg().panicSoundRepeats = (int) v,
                1, 100, 1, 5, v -> String.valueOf((int) v)).integer().under(snd));
        rows.add(number("Тон", null, () -> cfg().panicSoundPitch, v -> cfg().panicSoundPitch = (float) v,
                0.5, 2, 0.05, 0.25, v -> "×" + Rows.num(v)).under(snd).adv());
        rows.add(number("Пауза между повторами", null, () -> cfg().panicSoundRepeatDelayTicks,
                v -> cfg().panicSoundRepeatDelayTicks = (int) v, 1, 1200, 5, 20, FarmMacroScreen::ticksFmt).integer()
                .under(() -> cfg().panicSoundEnabled && cfg().panicSoundRepeats > 1).adv());
        rows.add(new Rows.Note("Свои звуки: .ogg или .wav в config/farmmacro/sounds/. Звуки «MC:» всегда идут через игру.", Ui.ACCENT).adv());

        rows.add(new Rows.Section("Экран и журнал").key("p.screen").closed());
        rows.add(toggle("Красный экран", "Вспышка с причиной паники", () -> cfg().panicRedScreenEnabled,
                v -> cfg().panicRedScreenEnabled = v));
        rows.add(number("Длительность", null, () -> cfg().panicRedScreenTicks, v -> cfg().panicRedScreenTicks = (int) v,
                1, 1200, 5, 20, FarmMacroScreen::ticksFmt).integer().under(() -> cfg().panicRedScreenEnabled).adv());
        rows.add(toggle("Журнал событий", "config/farmmacro/logs/events-ДАТА.log: флаги, паники, старт/стоп, круги, чат, очки",
                () -> cfg().eventLogEnabled, v -> cfg().eventLogEnabled = v));
        rows.add(new Rows.Buttons(new Rows.Btn("Папка журналов", Rows.Style.SECONDARY, () -> {
            java.nio.file.Path d = ModConfig.modDir().resolve("logs");
            try { java.nio.file.Files.createDirectories(d); } catch (Exception ignored) {}
            openFolder(d);
        })).under(() -> cfg().eventLogEnabled).adv());
        rows.add(new Rows.End());
        rows.add(new Rows.Spacer(4));
        rows.add(new Rows.Buttons(new Rows.Btn("⚠ Проверить панику", Rows.Style.DANGER, () -> {
            onClose();
            PanicDetector.INSTANCE.preview(minecraft);
        }).tip("Сирена и красный экран без остановки чего-либо")));
    }

    // ── Конфиги ──────────────────────────────────────────────────────────────

    private String profileName = "";
    private String profileMsg = "";
    private String confirmProfileDelete;

    private void buildConfigs() {
        rows.add(new Rows.Section("Готовые наборы"));
        rows.add(new Rows.Note("Меняют детекторы и общую случайность. Визуал, звук, HUD и маршруты не трогают.", Ui.SUB));
        for (var p : com.farmmacro.config.ConfigProfiles.PRESETS)
            rows.add(new Rows.Buttons(new Rows.Btn(p.label(), "human".equals(p.id()) ? Rows.Style.PRIMARY : Rows.Style.SECONDARY, () -> {
                com.farmmacro.config.ConfigProfiles.applyPreset(p.id());
                profileMsg = "Применён набор «" + p.label() + "»";
                buildTab();
            }).tip(p.tip())));

        rows.add(new Rows.Section("Мои профили"));
        rows.add(new Rows.Note("Профиль — все настройки мода (детекторы, реакция, запуск, визуал, общая случайность), "
                + "кроме пресетов камеры и клавиш. Файлы — config/farmmacro/configs/.", Ui.SUB));
        rows.add(new Rows.TextField("Имя профиля", "Буквы, цифры, пробел, _ - .", () -> profileName, v -> {
            profileName = v.strip();
            return true;
        }, null, com.farmmacro.config.ConfigProfiles.NAME_MAX, 0));
        rows.add(new Rows.Buttons(
                new Rows.Btn(() -> com.farmmacro.config.ConfigProfiles.exists(profileName) ? "Перезаписать" : "Сохранить текущие",
                        () -> Rows.Style.PRIMARY, () -> {
                    String err = com.farmmacro.config.ConfigProfiles.save(profileName);
                    profileMsg = err != null ? err : "Сохранено: " + com.farmmacro.config.ConfigProfiles.clean(profileName);
                    buildTab();
                }).enabledIf(() -> com.farmmacro.config.ConfigProfiles.clean(profileName) != null),
                new Rows.Btn("Папка", Rows.Style.SECONDARY, () -> {
                    try { java.nio.file.Files.createDirectories(com.farmmacro.config.ConfigProfiles.dir()); } catch (Exception ignored) {}
                    openFolder(com.farmmacro.config.ConfigProfiles.dir());
                }),
                new Rows.Btn("Обновить", Rows.Style.SECONDARY, this::buildTab)));
        rows.add(new Rows.Note(() -> profileMsg, Ui.ON));
        var list = com.farmmacro.config.ConfigProfiles.list();
        if (list.isEmpty()) rows.add(new Rows.Note("Профилей пока нет.", Ui.SUB));
        for (String name : list) {
            rows.add(new Rows.Note("● " + name, Ui.TEXT));
            rows.add(new Rows.Buttons(
                    new Rows.Btn("Загрузить", Rows.Style.PRIMARY, () -> {
                        String err = com.farmmacro.config.ConfigProfiles.load(name);
                        profileMsg = err != null ? err : "Загружен профиль «" + name + "»";
                        refreshLists();
                        buildTab();
                    }).enabledIf(() -> MacroManager.INSTANCE.getState() == MacroManager.State.IDLE)
                            .tip("Нельзя во время записи/воспроизведения"),
                    new Rows.Btn("Перезаписать", Rows.Style.SECONDARY, () -> {
                        String err = com.farmmacro.config.ConfigProfiles.save(name);
                        profileMsg = err != null ? err : "Перезаписан «" + name + "» текущими настройками";
                    }),
                    new Rows.Btn(() -> name.equals(confirmProfileDelete) ? "Точно удалить?" : "Удалить",
                            () -> Rows.Style.DANGER, () -> {
                        if (!name.equals(confirmProfileDelete)) { confirmProfileDelete = name; return; }
                        confirmProfileDelete = null;
                        com.farmmacro.config.ConfigProfiles.delete(name);
                        profileMsg = "Удалён «" + name + "»";
                        buildTab();
                    })));
        }

        rows.add(new Rows.Section("Общая случайность"));
        rows.add(toggle("Использовать для всех", "Вкл — макросы и маршруты берут случайность отсюда, а не из своих ⚙",
                () -> cfg().humanUseGlobal, v -> cfg().humanUseGlobal = v));
        rows.add(new Rows.Note(() -> !cfg().humanMaster ? "Случайность выключена общим выключателем «Запуск → Случайность»." : "", Ui.WARN));
        HumanRows.build(rows, cfg().humanGlobal, true, true, () -> true, r -> { r.run(); save(); }, this::buildTab);
    }


    private void buildLaunch() {
        MacroManager mm = MacroManager.INSTANCE;
        rows.add(new Rows.Selector("Клавиша запуска играет", MacroManager.keyName(FarmMacroMod.keyPlay)
                + " — запись по кадрам или маршрут по точкам", () -> SOURCES, () -> mm.getSourceKind().name(),
                v -> { if ("ROUTE".equals(v)) mm.useRouteSource(); else mm.useRecordingSource(); })
                .enabledIf(() -> mm.getState() == MacroManager.State.IDLE));

        rows.add(new Rows.Section("Цикл").key("l.loop"));
        rows.add(toggle("Зациклить", "После последнего кадра начинать заново",
                () -> cfg().loopEnabled, v -> cfg().loopEnabled = v));
        rows.add(number("Кругов", "0 — бесконечно", () -> cfg().loopLimit, v -> cfg().loopLimit = (int) v,
                0, 1000000, 1, 10, v -> v == 0 ? "∞" : String.valueOf((int) v)).integer().under(() -> cfg().loopEnabled));
        rows.add(number("Лимит времени", "0 — без лимита", () -> cfg().timeLimitMinutes, v -> cfg().timeLimitMinutes = (int) v,
                0, 10080, 5, 30, v -> v == 0 ? "нет" : (int) v + " мин").integer());

        rows.add(new Rows.Section("Автостоп").key("l.stop"));
        rows.add(toggle("Полный инвентарь", "Стоп, когда не осталось пустых слотов (можно продолжить)",
                () -> cfg().stopWhenInventoryFull, v -> cfg().stopWhenInventoryFull = v));
        rows.add(toggle("Сигнал по окончании", "Мягкий «дзинь», когда макрос закончился сам",
                () -> cfg().finishSoundEnabled, v -> cfg().finishSoundEnabled = v));

        rows.add(new Rows.Section("Старт").key("l.start"));
        rows.add(number("Обратный отсчёт", "Удобно для записи видео", () -> cfg().startCountdownSeconds,
                v -> cfg().startCountdownSeconds = (int) v, 0, 600, 1, 5, v -> v == 0 ? "выкл" : (int) v + " с").integer());
        rows.add(toggle("Строго с точки старта", "Не запускать, если игрок дальше допуска",
                () -> cfg().requireStartPoint, v -> cfg().requireStartPoint = v));
        rows.add(number("Точка старта: допуск", "Дальше — предупреждение и стрелка в HUD",
                () -> cfg().startPointWarnDistance, v -> cfg().startPointWarnDistance = v,
                0.01, 1024, 0.5, 4, v -> Rows.num(v) + " бл").adv());
        rows.add(new Rows.Selector("Культура", "При старте макроса или маршрута повернуть к пресету этой культуры "
                + "(плавно, как " + MacroManager.keyName(FarmMacroMod.keyCamApply) + "); выбранный пресет — и для "
                + MacroManager.keyName(FarmMacroMod.keyCamApply) + ". Не действует, если у макроса своя привязка камеры (⚙)",
                () -> LAUNCH_CROPS, () -> cfg().launchCrop, v -> {
                    cfg().launchCrop = v;
                    int i = com.farmmacro.camera.CameraPresets.indexForCrop(v);
                    if (i >= 0) cfg().camSelected = i;
                    save();
                }));
        rows.add(new Rows.Note(() -> {
            String crop = cfg().launchCrop;
            if (com.farmmacro.camera.CropPresets.NONE.equals(crop)) return "";
            int i = com.farmmacro.camera.CameraPresets.indexForCrop(crop);
            return i < 0 ? "Нет пресета для этой культуры — добавь во вкладке «Камера» или «Сбросить к стандартным»."
                    : "Пресет: " + com.farmmacro.camera.CameraPresets.describe(cfg().camPresets.get(i));
        }, Ui.SUB));
        rows.add(toggle("Повторять камеру", "Повороты камеры из записи (выкл — камера как есть)",
                () -> cfg().replayCamera, v -> cfg().replayCamera = v));
        rows.add(new Rows.Note("Пресеты камеры под культуры (yaw/pitch) — во вкладке «Камера».", Ui.SUB).adv());

        rows.add(new Rows.Section("Случайность").key("l.human"));
        rows.add(toggle("Случайность", "Общий выключатель. Настройки — у каждого макроса/маршрута (⚙ → «Случайность») или общие",
                () -> cfg().humanMaster, v -> cfg().humanMaster = v));
        rows.add(new Rows.Note(() -> !cfg().humanMaster ? "" : cfg().humanUseGlobal
                ? "Сейчас для всех — общая: «Конфиги → Общая случайность»."
                : "Своя у каждого макроса/маршрута: ⚙ → «Случайность». Одну на всех — «Конфиги → Общая случайность».", Ui.SUB));

        rows.add(new Rows.Section("Блокировка мыши").key("l.mouse"));
        rows.add(toggle("Блокировать при старте", "Мышь не крутит камеру, пока идёт макрос (клавиша M — вручную)",
                () -> cfg().mouseLockOnStart, v -> cfg().mouseLockOnStart = v));
        rows.add(toggle("Снимать при стопе", "Паника, End и выход из мира снимают блокировку всегда",
                () -> cfg().mouseUnlockOnStop, v -> cfg().mouseUnlockOnStop = v));

        rows.add(new Rows.Section("Сессия").key("l.session"));
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

    private static final List<Rows.Choice> CROPS = com.farmmacro.camera.CameraPresets.CROPS.stream()
            .map(c -> new Rows.Choice(c.id(), c.label())).toList();
    /** «Запуск → Культура»: «Не менять» + культуры (без «Другое»). */
    private static final List<Rows.Choice> LAUNCH_CROPS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(new Rows.Choice(com.farmmacro.camera.CropPresets.NONE, "Не менять камеру")),
            CROPS.stream().filter(c -> !com.farmmacro.camera.CropPresets.OTHER.equals(c.id()))).toList();
    private long confirmCamResetUntil;

    private static ModConfig.CamPreset selPreset() { return com.farmmacro.camera.CameraPresets.selected(); }

    private static String presetHint() {
        return MacroManager.keyName(FarmMacroMod.keyCamApply) + " — применить выбранный, "
                + MacroManager.keyName(FarmMacroMod.keyCamNext) + " — следующий, "
                + MacroManager.keyName(FarmMacroMod.keyCamSave) + " — записать текущий взгляд в выбранный.";
    }

    /** Вкладка «Камера»: пресеты yaw/pitch, не привязанные к макросам; ввод чисел, «Взять текущие», применение. */
    private void buildCamera() {
        rows.add(new Rows.Note("Пресет — имя, культура, yaw и pitch (точность 0.1°). Стандартные пресеты культур можно "
                + "переименовать, поменять и удалить. " + presetHint()
                + " Работает в любой момент, кроме записи, которая играет с «Повторять камеру». "
                + "«Запуск → Культура» ставит пресет культуры при старте.", Ui.SUB));

        rows.add(new Rows.Section("Применение"));
        rows.add(toggle("Плавный поворот", "При применении пресета: постоянная скорость с торможением в конце, выкл — мгновенно",
                () -> cfg().camSmooth, v -> cfg().camSmooth = v));
        rows.add(number("Скорость поворота", "Градусов в секунду", () -> cfg().camTurnSpeed,
                v -> cfg().camTurnSpeed = v, 1, 3600, 10, 60, v -> Rows.num(v) + "°/с").enabledIf(() -> cfg().camSmooth));
        rows.add(number("Скорость ±", "Каждый поворот чуть быстрее или медленнее (0 — всегда одинаково)", () -> cfg().smoothTurnJitterPct,
                v -> cfg().smoothTurnJitterPct = (int) v, 0, 90, 1, 10, v -> v == 0 ? "выкл" : "±" + (int) v + " %").integer()
                .under(() -> cfg().camSmooth).adv());

        rows.add(new Rows.Section("Пресеты"));
        List<ModConfig.CamPreset> list = cfg().camPresets;
        if (list.isEmpty()) rows.add(new Rows.Note("Пока пусто. Встань как нужно и нажми «+ Новый из текущего взгляда».", Ui.SUB));
        for (int i = 0; i < list.size(); i++) rows.add(new CamCard(i));
        rows.add(new Rows.Buttons(
                new Rows.Btn("+ Новый из текущего взгляда", Rows.Style.PRIMARY, () -> {
                    com.farmmacro.camera.CameraPresets.addFromCurrent(minecraft);
                    buildTab();
                }).enabledIf(() -> cfg().camPresets.size() < com.farmmacro.camera.CameraPresets.MAX),
                new Rows.Btn(() -> System.currentTimeMillis() < confirmCamResetUntil ? "Точно сбросить?" : "Сбросить к стандартным",
                        () -> System.currentTimeMillis() < confirmCamResetUntil ? Rows.Style.DANGER : Rows.Style.SECONDARY, () -> {
                    if (System.currentTimeMillis() >= confirmCamResetUntil) {
                        confirmCamResetUntil = System.currentTimeMillis() + 3000;
                        return;
                    }
                    confirmCamResetUntil = 0;
                    com.farmmacro.camera.CameraPresets.resetToStandard();
                    buildTab();
                }).tip("Заменить все пресеты на 7 стандартных пресетов культур (свои удалятся). Нажми ещё раз для подтверждения")));

        if (list.isEmpty()) return;
        java.util.function.BooleanSupplier has = () -> selPreset() != null;
        rows.add(new Rows.Section("Выбранный пресет"));
        rows.add(new Rows.TextField("Имя", null, () -> selPreset() != null ? selPreset().name : "", v -> {
            var p = selPreset();
            String n = v.strip();
            if (p == null || n.isEmpty()) return false;
            p.name = n.length() > ModConfig.CAM_NAME_MAX ? n.substring(0, ModConfig.CAM_NAME_MAX) : n;
            save();
            return true;
        }, null, ModConfig.CAM_NAME_MAX, 0).enabledIf(has));
        rows.add(new Rows.Selector("Культура", "Для подписи и «Запуск → Культура»", () -> CROPS,
                () -> selPreset() != null ? selPreset().crop : "other",
                v -> { if (selPreset() != null) { selPreset().crop = v; save(); } }).enabledIf(has));
        rows.add(new Rows.TextField("Yaw", "По горизонтали, как в F3: −180…180 (270 → −90), точность 0.1°",
                () -> selPreset() != null ? com.farmmacro.camera.CameraPresets.deg(selPreset().yaw) : "", v -> {
            Float f = com.farmmacro.camera.CameraPresets.parseDeg(v);
            var p = selPreset();
            if (f == null || p == null) return false;
            p.yaw = com.farmmacro.camera.CropPresets.yaw(f);
            save();
            return true;
        }, Rows.TextField.numeric(), 16, 92).enabledIf(has));
        rows.add(new Rows.TextField("Pitch", "По вертикали: −90 вверх … 90 вниз, точность 0.1°",
                () -> selPreset() != null ? com.farmmacro.camera.CameraPresets.deg(selPreset().pitch) : "", v -> {
            Float f = com.farmmacro.camera.CameraPresets.parseDeg(v);
            var p = selPreset();
            if (f == null || p == null || f < -90f || f > 90f) return false;
            p.pitch = com.farmmacro.camera.CropPresets.pitch(f);
            save();
            return true;
        }, Rows.TextField.numeric(), 16, 92).enabledIf(has));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Взять текущие", Rows.Style.SECONDARY, () -> {
                    var p = selPreset();
                    if (p != null && minecraft.player != null) {
                        p.yaw = com.farmmacro.camera.CropPresets.yaw(minecraft.player.getYRot());
                        p.pitch = com.farmmacro.camera.CropPresets.pitch(minecraft.player.getXRot());
                        save();
                    }
                }).enabledIf(() -> has.getAsBoolean() && minecraft.player != null)
                        .tip("Записать в выбранный пресет, куда ты сейчас смотришь (yaw и pitch)"),
                new Rows.Btn("Yaw ровно по оси", Rows.Style.SECONDARY, () -> {
                    var p = selPreset();
                    if (p != null) { p.yaw = com.farmmacro.camera.CropPresets.yaw(Math.round(p.yaw / 45f) * 45f); save(); }
                }).enabledIf(has).tip("Округлить yaw до ближайших 45° — чтобы идти ровно вдоль ряда"),
                new Rows.Btn("Применить", Rows.Style.SUCCESS, () ->
                        com.farmmacro.camera.CameraPresets.apply(minecraft, cfg().camSelected))
                        .enabledIf(() -> has.getAsBoolean() && minecraft.player != null)
                        .tip("Повернуть камеру к пресету (как " + MacroManager.keyName(FarmMacroMod.keyCamApply) + ")")));
    }

    /** Карточка пресета камеры: выбрать, повернуться, удалить. */
    private final class CamCard extends Rows.Row {
        private final int index;
        CamCard(int index) { this.index = index; }

        int height(Rows.Ctx c, int w) { return 22; }

        private int[] bx() { int right = lastX + lastW - 6; int del = right - 18, go = del - 4 - 22; return new int[]{go, del}; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            if (index >= cfg().camPresets.size()) return;
            var p = cfg().camPresets.get(index);
            boolean sel = index == cfg().camSelected;
            lastX = x; lastW = w;
            Ui.round(g, x, y, w, 19, 4, sel ? Ui.alpha(Ui.ACCENT, 0.22f) : hover ? Ui.CARD_HOVER : Ui.CARD);
            if (sel) g.fill(x, y + 3, x + 2, y + 16, Ui.ACCENT);
            int[] b = bx();
            Ui.text(g, font, Ui.ellipsize(font, (index + 1) + ". " + com.farmmacro.camera.CameraPresets.describe(p), b[0] - x - 12),
                    x + 8, y + 6, sel ? Ui.TEXT : Ui.SUB);
            if (Rows.drawButton(c, g, b[0], y + 2, 22, 15, "◎", Rows.Style.SUCCESS, minecraft.player != null, mx, my))
                c.tooltip("Применить пресет: повернуть камеру");
            if (Rows.drawButton(c, g, b[1], y + 2, 18, 15, "✕", Rows.Style.SECONDARY, true, mx, my)) c.tooltip("Удалить пресет");
            if (hover && mx >= 0 && mx < b[0]) c.hand();
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0 || index >= cfg().camPresets.size()) return false;
            int[] b = bx();
            if (Ui.inside(mx, my, b[1], lastY + 2, 18, 15)) {
                c.clickSound();
                cfg().camPresets.remove(index);
                cfg().camSelected = Math.max(0, Math.min(cfg().camSelected, cfg().camPresets.size() - 1));
                save();
                buildTab();
                return true;
            }
            c.clickSound();
            cfg().camSelected = index;
            save();
            if (Ui.inside(mx, my, b[0], lastY + 2, 22, 15)) com.farmmacro.camera.CameraPresets.apply(minecraft, index);
            return true;
        }
    }

    private static final List<Rows.Choice> HAT_STYLES = com.farmmacro.visual.HatColors.STYLES.stream()
            .map(a -> new Rows.Choice(a[0], a[1])).toList();
    private static final List<Rows.Choice> HAT_COLORS = com.farmmacro.visual.HatColors.PRESETS.stream()
            .map(p -> new Rows.Choice(p.id(), p.label())).toList();

    /** «По умолчанию» + все цвета (включая чёрный и тёмно-серый) — для отдельных элементов. */
    private static final List<Rows.Choice> ELEMENT_COLORS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(new Rows.Choice(com.farmmacro.visual.HatColors.DEFAULT, "По умолчанию")),
            HAT_COLORS.stream()).toList();

    private Rows.Selector colorRow(String label, String hint, java.util.function.Supplier<String> get,
                                   java.util.function.Consumer<String> set) {
        return new Rows.Selector(label, hint, () -> ELEMENT_COLORS, get, v -> { set.accept(v); save(); });
    }

    private static final List<Rows.Choice> ROUTE_MODES = List.of(
            new Rows.Choice("always", "Всегда"), new Rows.Choice("playing", "Только при игре"));
    private static final List<Rows.Choice> ROUTE_XRAY = List.of(
            new Rows.Choice("off", "Нет"), new Rows.Choice("dim", "Слегка"), new Rows.Choice("full", "Ярко"));

    private void buildVisual() {
        java.util.function.BooleanSupplier ro = () -> cfg().routeEnabled;
        rows.add(new Rows.Section("Маршрут").key("v.route"));
        rows.add(toggle("Показывать маршрут", "Лента по кадрам макроса: впереди ярко, пройдено тускло, запись — красным",
                () -> cfg().routeEnabled, v -> cfg().routeEnabled = v));
        rows.add(new Rows.Selector("Когда", null, () -> ROUTE_MODES, () -> cfg().routeMode,
                v -> { cfg().routeMode = v; save(); }).under(ro));
        rows.add(new Rows.Selector("Цвет ленты", "Цвет участка впереди", () -> HAT_COLORS, () -> cfg().routeColor,
                v -> { cfg().routeColor = v; save(); }).under(ro));
        rows.add(number("Непрозрачность", null, () -> cfg().routeOpacity, v -> cfg().routeOpacity = (int) v,
                1, 100, 5, 25, v -> (int) v + "%").integer().under(ro));
        rows.add(toggle("Свечение", "Светлая середина и мягкий ореол — лента ярче на любом фоне",
                () -> cfg().routeGlow, v -> cfg().routeGlow = v).under(ro));
        rows.add(new Rows.Selector("Сквозь стены", "Слегка — видно за блоками, ярко там, где не перекрыто",
                () -> ROUTE_XRAY, () -> cfg().routeSeeThrough, v -> { cfg().routeSeeThrough = v; save(); }).under(ro));
        rows.add(toggle("Стрелки направления", null, () -> cfg().routeArrows, v -> cfg().routeArrows = v).under(ro));
        rows.add(toggle("ЛКМ / ПКМ, прыжки", "Ломание — оранжевым, ПКМ — фиолетовым; точки прыжка и приседания",
                () -> cfg().routeShowActions, v -> cfg().routeShowActions = v).under(ro));
        rows.add(number("Толщина", null, () -> cfg().routeWidth, v -> cfg().routeWidth = v,
                0.005, 1, 0.02, 0.1, v -> Rows.num(v) + " бл").under(ro).adv());
        rows.add(number("Радиус отрисовки", "Дальше от игрока маршрут не рисуется", () -> cfg().routeRadius,
                v -> cfg().routeRadius = (int) v, 1, 1024, 8, 32, v -> (int) v + " бл").integer().under(ro).adv());
        rows.add(number("Шаг стрелок", null, () -> cfg().routeArrowSpacing, v -> cfg().routeArrowSpacing = v,
                0.5, 64, 1, 4, v -> Rows.num(v) + " бл").under(() -> cfg().routeEnabled && cfg().routeArrows).adv());
        rows.add(toggle("От точки запуска", "Во время игры сдвигать маршрут туда, откуда реально запущен макрос",
                () -> cfg().routeRelative, v -> cfg().routeRelative = v).under(ro).adv());

        rows.add(new Rows.Section("Цвета маршрута").key("v.colors").closed().adv());
        rows.add(colorRow("Цвет точек", "Точки маршрута по точкам", () -> cfg().routePointColor, v -> cfg().routePointColor = v).under(ro).adv());
        rows.add(colorRow("Цвет номеров", "Номера над точками (выбранная — жёлтая)", () -> cfg().routeLabelColor, v -> cfg().routeLabelColor = v).under(ro).adv());
        rows.add(colorRow("Маяк старта", "По умолчанию зелёный", () -> cfg().routeStartColor, v -> cfg().routeStartColor = v).under(ro).adv());
        rows.add(colorRow("Маяк остановки", "По умолчанию красный", () -> cfg().routeStopColor, v -> cfg().routeStopColor = v).under(ro).adv());
        rows.add(colorRow("Цвет стрелок", "По умолчанию белые", () -> cfg().routeArrowColor, v -> cfg().routeArrowColor = v).under(ro).adv());

        rows.add(new Rows.Section("HUD").key("v.hud"));
        rows.add(toggle("Панель статуса", "Состояние, прогресс, круги, время сессии",
                () -> cfg().statsHudEnabled, v -> cfg().statsHudEnabled = v));
        rows.add(toggle("Навигатор", "Панель со стрелкой к точке старта или остановки",
                () -> cfg().navHudEnabled, v -> cfg().navHudEnabled = v));
        rows.add(toggle("Стрелка у прицела", "Маленькая стрелка к цели вокруг прицела и расстояние",
                () -> cfg().navCrosshairEnabled, v -> cfg().navCrosshairEnabled = v));
        rows.add(toggle("Полоска подозрительности", "«ПОДОЗР. N/10», пока играет макрос (нужна «Паника → Подозрительность»)",
                () -> cfg().suspicionHud, v -> cfg().suspicionHud = v).enabledIf(() -> cfg().panicEnabled && cfg().suspicionEnabled));
        rows.add(colorRow("Цвет HUD", "Акцент навигатора и стрелки у прицела", () -> cfg().hudAccentColor, v -> cfg().hudAccentColor = v).adv());
        rows.add(colorRow("Фон HUD", "Подложка панелей (полупрозрачная)", () -> cfg().hudBgColor, v -> cfg().hudBgColor = v).adv());

        java.util.function.BooleanSupplier on = () -> cfg().hatEnabled;
        rows.add(new Rows.Section("China Hat").key("v.hat"));
        rows.add(toggle("Шляпа", "Полупрозрачный конус над головой (видишь только ты)",
                () -> cfg().hatEnabled, v -> cfg().hatEnabled = v));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Фиолет → оранж", Rows.Style.SECONDARY, () -> {
                    cfg().hatStyle = "gradient"; cfg().hatColor1 = "purple"; cfg().hatColor2 = "orange"; save();
                }),
                new Rows.Btn("Голубой", Rows.Style.SECONDARY, () -> {
                    cfg().hatStyle = "solid"; cfg().hatColor1 = "cyan"; save();
                })
        ).under(on));
        rows.add(new Rows.Selector("Стиль цвета", "Градиент по кругу, один цвет или перелив радуги",
                () -> HAT_STYLES, () -> cfg().hatStyle, v -> { cfg().hatStyle = v; save(); }).under(on));
        rows.add(new Rows.Selector("Цвет 1", null, () -> HAT_COLORS, () -> cfg().hatColor1,
                v -> { cfg().hatColor1 = v; save(); })
                .under(() -> cfg().hatEnabled && !"rainbow".equals(cfg().hatStyle)));
        rows.add(new Rows.Selector("Цвет 2", "Второй цвет градиента", () -> HAT_COLORS, () -> cfg().hatColor2,
                v -> { cfg().hatColor2 = v; save(); })
                .under(() -> cfg().hatEnabled && "gradient".equals(cfg().hatStyle)));
        rows.add(number("Прозрачность", "0 — не видно, 100 — непрозрачная", () -> cfg().hatOpacity,
                v -> cfg().hatOpacity = (int) v, 0, 100, 5, 25, v -> (int) v + "%").integer().under(on));
        rows.add(toggle("От первого лица", "Видно, если посмотреть вверх. В F5 видно всегда",
                () -> cfg().hatFirstPerson, v -> cfg().hatFirstPerson = v).under(on));
        rows.add(toggle("На всех игроках", "Шляпы на других игроках (видишь только ты)",
                () -> cfg().hatAllPlayers, v -> cfg().hatAllPlayers = v).under(on));
        rows.add(number("Радиус полей", null, () -> cfg().hatRadius, v -> cfg().hatRadius = v,
                0.05, 3, 0.05, 0.25, v -> Rows.num(v) + " бл").under(on).adv());
        rows.add(number("Высота конуса", null, () -> cfg().hatHeight, v -> cfg().hatHeight = v,
                0.01, 2, 0.05, 0.2, v -> Rows.num(v) + " бл").under(on).adv());
        rows.add(number("Смещение по высоте", "Насколько поля выше макушки", () -> cfg().hatOffset,
                v -> cfg().hatOffset = v, -1, 2, 0.02, 0.1, v -> Rows.num(v) + " бл").under(on).adv());
        rows.add(number("Скорость", "Вращение градиента / перелива, 0 — стоит", () -> cfg().hatSpeed,
                v -> cfg().hatSpeed = v, 0, 20, 0.1, 1, v -> "×" + Rows.num(v)).under(on).adv());
        rows.add(number("Сегменты", "Качество круга", () -> cfg().hatSegments,
                v -> cfg().hatSegments = (int) v, 3, 256, 4, 16, v -> String.valueOf((int) v)).integer().under(on).adv());
    }

    private static final List<Rows.Choice> SOURCES = List.of(
            new Rows.Choice("RECORDING", "Запись"), new Rows.Choice("ROUTE", "Маршрут по точкам"));
    private void buildMacros() {
        rows.add(new Rows.Section("Текущий макрос"));
        rows.add(new BufferCard());
        rows.add(new Rows.Note(() -> MacroManager.INSTANCE.getFrameCount() == 0 ? "" : MacroManager.INSTANCE.getSettings().summary(false)
                + " · ⚙ — настройки макроса (камера, зажим мыши)", Ui.SUB));
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

    /** Вкладка «Маршруты»: текущий маршрут (точки), подсказка по редактору, автоход и редактор, сохранённые. */
    private void buildRoutes() {
        var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
        rows.add(new Rows.Section("Маршрут по точкам").key("r.cur"));
        rows.add(new RouteBufferCard());
        rows.add(new Rows.Note(() -> rb.isEmpty() ? "" : rb.settings().summary(true)
                + " · ⚙ — настройки маршрута (камера, зажим мыши)", Ui.SUB));

        rows.add(new Rows.Section("Подсказка: редактор").key("r.help").closed());
        rows.add(new Rows.Note("Редактор (" + MacroManager.keyName(FarmMacroMod.keyEditor) + "): ЛКМ — точка, зажать — двигать, "
                + "Shift+ЛКМ — вставить, ПКМ — удалить, Shift+ПКМ — параметры точки, "
                + "Alt+ЛКМ — выделить ещё, Ctrl+A — все, стрелки — сдвиг выделенных внутри блока (Shift — мелко), "
                + "Ctrl+D — спуск на этаж ниже, Ctrl+Z — отменить.", Ui.SUB));
        rows.add(new Rows.Note("Автоход не поворачивает камеру: идёт клавишами W/A/S/D относительно текущего yaw. "
                + "Поставь yaw вдоль рядов (пресет культуры во вкладке «Камера») — тогда ряды идут чистыми W/S.", Ui.SUB));

        rows.add(new Rows.Section("Автоход и редактор").key("r.walk"));
        rows.add(number("Точка достигнута", "Радиус по горизонтали", () -> cfg().routeReachRadius,
                v -> cfg().routeReachRadius = v, 0.01, 2, 0.05, 0.2, v -> Rows.num(v) + " бл"));
        rows.add(number("Досягаемость редактора", "Как далеко ставить и выбирать точки", () -> cfg().routeEditReach,
                v -> cfg().routeEditReach = v, 1, 128, 4, 16, v -> Rows.num(v) + " бл").adv());
        rows.add(number("Шаг сдвига стрелками", "Редактор: выбрать точку → стрелки сдвигают её внутри блока",
                () -> cfg().routeOffsetStep, v -> cfg().routeOffsetStep = v, 0.001, 0.5, 0.01, 0.05, v -> Rows.num(v) + " бл").adv());
        rows.add(number("Мелкий шаг (Shift)", null, () -> cfg().routeOffsetFineStep, v -> cfg().routeOffsetFineStep = v,
                0.001, 0.5, 0.005, 0.01, v -> Rows.num(v) + " бл").adv());

        rows.add(new Rows.Section("Сохранённые маршруты").key("r.saved"));
        if (routes.isEmpty()) {
            rows.add(new Rows.Note("Пока пусто. Построй маршрут в редакторе и нажми «Сохранить».", Ui.SUB));
        } else {
            for (var info : routes) rows.add(new RouteCard(info));
        }
        rows.add(new Rows.Spacer(2));
        rows.add(new Rows.Buttons(new Rows.Btn("Открыть папку маршрутов", Rows.Style.SECONDARY,
                () -> openFolder(com.farmmacro.route.RouteStorage.DIR))));
    }


    // ── Окно «Настройки макроса» ─────────────────────────────────────────────

    private void openSettings(MacroSettingsScreen.Target t) {
        if (t == null) {
            if (minecraft.player != null)
                minecraft.player.sendOverlayMessage(Component.literal("§c[FM] Не удалось прочитать файл (подробности в логе)"));
            return;
        }
        Rows.blurText();
        minecraft.setScreen(new MacroSettingsScreen(this, t));
    }

    /** Кнопка ⚙ в правом верхнем углу карточки текущего макроса/маршрута. */
    private static boolean drawGear(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, boolean en, int mx, int my) {
        return Rows.drawButton(c, g, x, y, 18, 16, "⚙", Rows.Style.SECONDARY, en, mx, my);
    }

    private void saveRoute() {
        var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
        var store = com.farmmacro.route.RouteStorage.INSTANCE;
        minecraft.setScreen(new SaveMacroScreen(this, new SaveMacroScreen.Target() {
            public String title() { return "Сохранить маршрут"; }
            public String subtitle() { return rb.size() + " точек"; }
            public String placeholder() { return "Название, например «Морковь, ферма у дома»"; }
            public boolean exists(String name) { return store.exists(name); }
            public boolean save(String name) { return store.save(name, rb.route()); }
            public void saved(String name) { rb.markSaved(name); }
        }, rb.loadedName()));
    }

    /** Карточка текущего маршрута. */
    private final class RouteBufferCard extends Rows.Row {
        int height(Rows.Ctx c, int w) { return 52; }

        private int bw() { return (lastW - 16 - 12) / 4; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
            MacroManager m = MacroManager.INSTANCE;
            boolean has = !rb.isEmpty();
            boolean selected = m.getSourceKind() == MacroManager.SourceKind.ROUTE;
            Ui.round(g, x, y, w, 49, 5, Ui.CARD);
            g.fill(x, y + 3, x + 2, y + 46, has ? (selected ? Ui.ON : Ui.ACCENT) : Ui.BORDER);
            String title = !has ? "Пусто" : rb.loadedName() != null ? rb.loadedName() + (rb.isDirty() ? " · изменён" : "")
                    : "Новый маршрут · не сохранён";
            String sub;
            if (!has) sub = "Открой редактор и поставь точки";
            else {
                var p0 = rb.get(0);
                sub = rb.size() + " точек";
                if (rb.route().dimension != null) sub += " · " + rb.route().dimension.replace("minecraft:", "");
                if (minecraft.player != null) sub += String.format(Locale.ROOT, " · до точки 1 %.1f бл",
                        Math.sqrt(minecraft.player.distanceToSqr(p0.x, p0.y, p0.z)));
                if (selected) sub += " · играет по " + MacroManager.keyName(FarmMacroMod.keyPlay);
            }
            Ui.text(g, font, Ui.ellipsize(font, title, w - 40), x + 8, y + 5, Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, sub, w - 40), x + 8, y + 16, Ui.SUB);
            if (drawGear(c, g, x + w - 24, y + 5, has, mx, my)) c.tooltip("Настройки маршрута: камера, зажим мыши");
            int bw = bw(), by = y + 28;
            boolean idle = m.getState() == MacroManager.State.IDLE;
            boolean active = m.isRoutePlaying();
            boolean confirm = System.currentTimeMillis() < confirmClearRouteUntil;
            Rows.drawButton(c, g, x + 8, by, bw, 16, com.farmmacro.route.RouteEditor.isActive() ? "✎ Закрыть" : "✎ Редактор",
                    Rows.Style.PRIMARY, idle, mx, my);
            Rows.drawButton(c, g, x + 12 + bw, by, bw, 16, active ? "■ Стоп" : "▶ Запустить",
                    active ? Rows.Style.DANGER : Rows.Style.SUCCESS, has && (idle || active), mx, my);
            Rows.drawButton(c, g, x + 16 + bw * 2, by, bw, 16, "Сохранить", Rows.Style.SECONDARY, has, mx, my);
            Rows.drawButton(c, g, x + 20 + bw * 3, by, bw, 16, confirm ? "Точно?" : "Очистить",
                    confirm ? Rows.Style.DANGER : Rows.Style.SECONDARY, has && idle, mx, my);
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
            MacroManager m = MacroManager.INSTANCE;
            int bw = bw(), by = lastY + 28;
            boolean has = !rb.isEmpty(), idle = m.getState() == MacroManager.State.IDLE;
            if (has && Ui.inside(mx, my, lastX + lastW - 24, lastY + 5, 18, 16)) {
                c.clickSound();
                openSettings(MacroSettingsScreen.currentRoute());
                return true;
            }
            if (idle && Ui.inside(mx, my, lastX + 8, by, bw, 16)) {
                c.clickSound();
                boolean open = !com.farmmacro.route.RouteEditor.isActive();
                minecraft.setScreen(null);
                com.farmmacro.route.RouteEditor.setActive(minecraft, open);
                return true;
            }
            if (has && Ui.inside(mx, my, lastX + 12 + bw, by, bw, 16)) {
                c.clickSound();
                if (m.isRoutePlaying()) { m.stopPlayback(minecraft, "§e■ Остановлено из меню"); return true; }
                if (!idle) return true;
                m.useRouteSource();
                startMacroAndClose();
                return true;
            }
            if (has && Ui.inside(mx, my, lastX + 16 + bw * 2, by, bw, 16)) {
                c.clickSound();
                saveRoute();
                return true;
            }
            if (has && idle && Ui.inside(mx, my, lastX + 20 + bw * 3, by, bw, 16)) {
                c.clickSound();
                if (System.currentTimeMillis() < confirmClearRouteUntil) {
                    rb.clear();
                    m.useRecordingSource();
                    confirmClearRouteUntil = 0;
                } else {
                    confirmClearRouteUntil = System.currentTimeMillis() + 3000;
                }
                return true;
            }
            return false;
        }
    }

    /** Карточка сохранённого маршрута: ▶, загрузить, удалить с подтверждением. */
    private final class RouteCard extends Rows.Row {
        private final com.farmmacro.route.RouteStorage.RouteInfo info;
        RouteCard(com.farmmacro.route.RouteStorage.RouteInfo info) { this.info = info; }

        int height(Rows.Ctx c, int w) { return 30; }

        private int[] buttonsX() {
            int right = lastX + lastW - 6;
            int del = right - 18, load = del - 4 - 62, play = load - 4 - 22, gear = play - 4 - 18;
            return new int[]{play, load, del, gear};
        }

        private String key() { return "route:" + info.filename(); }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
            boolean current = info.name().equals(rb.loadedName());
            Ui.round(g, x, y, w, 27, 4, hover ? Ui.CARD_HOVER : Ui.CARD);
            if (current) g.fill(x, y + 3, x + 2, y + 24, Ui.ON);
            lastX = x; lastW = w;
            int[] bx = buttonsX();
            int textW = bx[3] - x - 14;
            Ui.text(g, font, Ui.ellipsize(font, "⌁ " + info.name(), textW), x + 8, y + 4, current ? Ui.ON : Ui.TEXT);
            String sub = info.points() + " точек" + (info.dimension() != null ? " · " + info.dimension().replace("minecraft:", "") : "")
                    + (info.world() != null ? " · " + info.world().replace("singleplayer:", "") : "");
            Ui.text(g, font, Ui.ellipsize(font, sub, textW), x + 8, y + 15, Ui.SUB);
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = key().equals(confirmDelete) && System.currentTimeMillis() < confirmUntil;
            int by = y + 5;
            if (Rows.drawButton(c, g, bx[0], by, 22, 16, "▶", Rows.Style.SUCCESS, idle, mx, my))
                c.tooltip("Загрузить и запустить");
            if (Rows.drawButton(c, g, bx[3], by, 18, 16, "⚙", Rows.Style.SECONDARY, idle, mx, my))
                c.tooltip("Настройки «" + info.name() + "»: камера, зажим мыши");
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
            boolean confirming = key().equals(confirmDelete) && System.currentTimeMillis() < confirmUntil;
            if (confirming && Ui.inside(mx, my, bx[1], by, bx[2] + 18 - bx[1], 16)) {
                c.clickSound();
                com.farmmacro.route.RouteStorage.INSTANCE.delete(info.filename());
                confirmDelete = null;
                refreshLists();
                buildTab();
                return true;
            }
            if (idle && Ui.inside(mx, my, bx[3], by, 18, 16)) {
                c.clickSound();
                openSettings(MacroSettingsScreen.savedRoute(info));
                return true;
            }
            if (!confirming && Ui.inside(mx, my, bx[2], by, 18, 16)) {
                c.clickSound();
                confirmDelete = key();
                confirmUntil = System.currentTimeMillis() + 3000;
                return true;
            }
            if (idle && (Ui.inside(mx, my, bx[0], by, 22, 16) || (!confirming && Ui.inside(mx, my, bx[1], by, 62, 16)))) {
                c.clickSound();
                var r = com.farmmacro.route.RouteStorage.INSTANCE.load(info.filename());
                if (r == null) {
                    if (minecraft.player != null)
                        minecraft.player.sendOverlayMessage(Component.literal("§c[FM] Не удалось прочитать маршрут (подробности в логе)"));
                    return true;
                }
                var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
                if (rb.isDirty() && !rb.isEmpty() && minecraft.player != null)
                    minecraft.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §6Несохранённый маршрут заменён"));
                rb.load(info.name(), r);
                if (minecraft.player != null)
                    minecraft.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §aЗагружен маршрут «" + info.name() + "»: "
                            + r.points.size() + " точек"));
                if (mx < bx[1]) startMacroAndClose();
                return true;
            }
            return false;
        }
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
            Ui.text(g, font, Ui.ellipsize(font, title, w - 40), x + 8, y + 5, Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, sub, w - 40), x + 8, y + 16, Ui.SUB);
            int bw = (w - 16 - 8) / 3, by = y + 28;
            boolean has = m.getFrameCount() > 0;
            if (drawGear(c, g, x + w - 24, y + 5, has && !m.isRecording(), mx, my)) c.tooltip("Настройки макроса: камера, зажим мыши");
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
            if (Ui.inside(mx, my, lastX + lastW - 24, lastY + 5, 18, 16)) {
                c.clickSound();
                openSettings(MacroSettingsScreen.currentMacro());
                return true;
            }
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

        private int[] buttonsX() {          // ▶, Загрузить, ✕, ⚙
            int right = lastX + lastW - 6;
            int del = right - 18, load = del - 4 - 62, play = load - 4 - 22, gear = play - 4 - 18;
            return new int[]{play, load, del, gear};
        }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean current = info.name().equals(MacroManager.INSTANCE.getLoadedName());
            Ui.round(g, x, y, w, 27, 4, hover ? Ui.CARD_HOVER : Ui.CARD);
            if (current) g.fill(x, y + 3, x + 2, y + 24, Ui.ON);
            lastX = x; lastW = w;
            int[] bx = buttonsX();
            int textW = bx[3] - x - 14;
            Ui.text(g, font, Ui.ellipsize(font, info.name(), textW), x + 8, y + 4, current ? Ui.ON : Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, MacroManager.formatTicks(info.frameCount()) + " · "
                    + info.frameCount() + " кадров", textW), x + 8, y + 15, Ui.SUB);
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = info.filename().equals(confirmDelete) && System.currentTimeMillis() < confirmUntil;
            int by = y + 5;
            if (Rows.drawButton(c, g, bx[0], by, 22, 16, "▶", Rows.Style.SUCCESS, idle, mx, my))
                c.tooltip("Загрузить и запустить");
            if (Rows.drawButton(c, g, bx[3], by, 18, 16, "⚙", Rows.Style.SECONDARY, idle, mx, my))
                c.tooltip("Настройки «" + info.name() + "»: камера, зажим мыши");
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
            if (idle && Ui.inside(mx, my, bx[3], by, 18, 16)) {
                c.clickSound();
                openSettings(MacroSettingsScreen.savedMacro(info));
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
                var saved = MacroStorage.INSTANCE.load(info.filename());
                if (saved == null) {
                    minecraft.player.sendOverlayMessage(Component.literal("§c[FM] Не удалось прочитать файл макроса (подробности в логе)"));
                    return true;
                }
                if (MacroManager.INSTANCE.loadMacro(info.name(), saved.frames, saved.settings, minecraft) && mx < bx[1]) startMacroAndClose();
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
        applyPending();

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
            int rh = r.h(this, cw);
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
        for (Rows.Row r : rows) t += r.h(this, cw);
        return t + 4;
    }

    private int modeX, modeW;

    private void renderHeader(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        int hx = x + sideW() + 10;
        // заголовок вкладки
        String title = title();
        Ui.text(g, font, title, hx, y + 11, Ui.TEXT);
        // режим меню: простой / расширенный (тонкие настройки)
        boolean adv = cfg().uiAdvanced;
        String mode = adv ? "Расширенный" : "Простой";
        int px = hx + font.width(title) + 8, pw = font.width(mode) + 12;
        boolean hp = Ui.inside(mx, my, px, y + 8, pw, 14);
        Ui.pill(g, px, y + 8, pw, 14, hp ? Ui.CARD_HOVER : Ui.alpha(adv ? Ui.ACCENT : Ui.SUB, 0.16f));
        Ui.text(g, font, mode, px + 6, y + 11, adv ? Ui.ACCENT_HI : Ui.SUB);
        if (hp) { hand(); tooltip("Режим меню. Простой — основное, расширенный — ещё пороги, веса, цвета и служебные шаги. "
                + "Скрытые настройки продолжают работать"); }
        modeX = px; modeW = pw;
        // статус справа
        MacroManager m = MacroManager.INSTANCE;
        String status; int col;
        switch (m.getState()) {
            case RECORDING -> { status = "● ЗАПИСЬ " + MacroManager.formatTicks(m.getFrameCount()); col = Ui.DANGER; }
            case COUNTDOWN -> { status = m.getCountdownTicks() > 0 ? "◷ СТАРТ ЧЕРЕЗ " + ((m.getCountdownTicks() + 19) / 20) : "◎ ПОВОРОТ КАМЕРЫ"; col = Ui.WARN; }
            case PLAYING -> { status = (m.isRoutePlaying() ? "▶ точка " + (m.getProgress() + 1) : "▶ " + m.getProgress()) + "/" + m.getProgressTotal()
                    + (cfg().loopEnabled ? " · круг " + (m.getLoopsDone() + 1) : ""); col = Ui.ON; }
            default -> {
                boolean ready = m.getStartPosition() != null;
                status = ready ? (m.getSourceKind() == MacroManager.SourceKind.ROUTE ? "■ ГОТОВ · МАРШРУТ" : "■ ГОТОВ")
                        : "■ НЕТ МАКРОСА";
                col = ready ? Ui.SUB : Ui.DIM;
            }
        }
        // кнопка закрытия
        int bx = x + w - 24, by = y + 7;
        boolean hc = Ui.inside(mx, my, bx, by, 16, 16);
        Ui.round(g, bx, by, 16, 16, 4, hc ? Ui.DANGER : Ui.CARD);
        Ui.textCentered(g, font, "✕", bx + 8, by + 4, hc ? 0xFFFFFFFF : Ui.SUB);
        if (hc) hand();

        int maxChip = Math.max(40, bx - 8 - (px + pw + 8));
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
        int ky = y + winH() - 12 - 3 * 11;
        if (!compact && ky > ty) {
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
        Rows.Row typing = Rows.focusedRow();
        if (typing != null && !(my >= typing.lastY && my < typing.lastY + typing.lastH)) Rows.blurText();
        int x = winX(), y = winY(), w = winW();
        // закрыть
        if (Ui.inside(mx, my, x + w - 24, y + 7, 16, 16)) { clickSound(); onClose(); return true; }
        if (modeW > 0 && Ui.inside(mx, my, modeX, y + 8, modeW, 14)) { clickSound(); toggleAdvanced(); return true; }
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
        if (Rows.textFocused() && Rows.textKey(e)) return true;   // печатаем в поле — клавиши меню не работают
        if (FarmMacroMod.keyOpenGui != null && FarmMacroMod.keyOpenGui.matches(e)) { onClose(); return true; }
        if (e.key() == 258) {               // Tab — следующая вкладка (Shift+Tab — предыдущая)
            switchTab(Math.floorMod(tab + (e.hasShiftDown() ? -1 : 1), TABS.length));
            return true;
        }
        if (e.key() == 266) { scrollTarget -= contH() * 0.8; return true; }   // PageUp
        if (e.key() == 267) { scrollTarget += contH() * 0.8; return true; }   // PageDown
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (Rows.textChar(e)) return true;
        return super.charTyped(e);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() {
        Rows.searching = false;
        Rows.clearWheelFocus();
        Rows.blurText();
        MacroManager.INSTANCE.saveSettingsIfDirty();
        ModConfig.save();
        minecraft.setScreen(parent);
    }
}
