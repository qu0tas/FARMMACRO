package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.camera.Freecam;
import com.farmmacro.config.HudSlot;
import com.farmmacro.config.ModConfig;
import com.farmmacro.hud.HudEditorScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.visual.CheekMath;
import com.farmmacro.visual.HatColors;
import com.farmmacro.visual.NickText;
import com.farmmacro.window.Pip;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 2.1.0: вкладка «Внешний вид» вынесена из {@link FarmMacroScreen} без изменения поведения — тот же набор
 * строк, те же ключи разделов. Общие помощники (toggle/number/cfg/save) остаются в экране.
 */
final class VisualTab {
    private final FarmMacroScreen s;

    VisualTab(FarmMacroScreen s) {
        this.s = s;
    }

    private static final List<Rows.Choice> PIP_CORNERS = List.of(
            new Rows.Choice("0", "Левый верх"),
            new Rows.Choice("1", "Правый верх"),
            new Rows.Choice("2", "Левый низ"),
            new Rows.Choice("3", "Правый низ"));
    private static final List<Rows.Choice> NICK_MODES = List.of(
            new Rows.Choice("off", "Нет"),
            new Rows.Choice("always", "Всегда"),
            new Rows.Choice("shots", "Только на скриншотах паники"));

    private static final List<Rows.Choice> HAT_STYLES =
            HatColors.STYLES.stream().map(a -> new Rows.Choice(a[0], a[1])).toList();
    private static final List<Rows.Choice> CHEEK_STYLES = CheekMath.STYLES.stream()
            .map(x -> new Rows.Choice(x.id(), x.label()))
            .toList();
    private static final List<Rows.Choice> CHEEK_COLORS = CheekMath.COLORS.stream()
            .map(x -> new Rows.Choice(x.id(), x.label()))
            .toList();
    private static final List<Rows.Choice> HAT_COLORS = HatColors.PRESETS.stream()
            .map(p -> new Rows.Choice(p.id(), p.label()))
            .toList();

    /** «По умолчанию» + все цвета (включая чёрный и тёмно-серый) — для отдельных элементов. */
    private static final List<Rows.Choice> ELEMENT_COLORS = Stream.concat(
                    Stream.of(new Rows.Choice(HatColors.DEFAULT, "По умолчанию")), HAT_COLORS.stream())
            .toList();

    private Rows.Selector colorRow(String label, String hint, Supplier<String> get, Consumer<String> set) {
        return new Rows.Selector(label, hint, () -> ELEMENT_COLORS, get, v -> {
            set.accept(v);
            FarmMacroScreen.save();
        });
    }

    private static final List<Rows.Choice> ROUTE_MODES =
            List.of(new Rows.Choice("always", "Всегда"), new Rows.Choice("playing", "Только при игре"));
    private static final List<Rows.Choice> ROUTE_XRAY =
            List.of(new Rows.Choice("off", "Нет"), new Rows.Choice("dim", "Слегка"), new Rows.Choice("full", "Ярко"));

    private static final List<Rows.Choice> THEME_CHOICES;

    static {
        List<Rows.Choice> l = new ArrayList<>();
        for (String[] t : Ui.THEMES) l.add(new Rows.Choice(t[0], t[1]));
        THEME_CHOICES = List.copyOf(l);
    }

    void build() {
        CustomizationRows.build(s.rows, FarmMacroScreen::save);
        BooleanSupplier ro = () -> FarmMacroScreen.cfg().routeEnabled;
        s.rows.add(new Rows.Section("Интерфейс").key("v.ui"));
        s.rows.add(new Rows.Selector(
                "Тема",
                "Цвета меню и HUD. Меняется сразу",
                () -> THEME_CHOICES,
                () -> FarmMacroScreen.cfg().uiTheme,
                v -> {
                    FarmMacroScreen.cfg().uiTheme = v;
                    Ui.applyTheme(v);
                    FarmMacroScreen.save();
                }));
        s.rows.add(s.toggle(
                "Подписи под настройками",
                "Выкл — подсказка всплывает при наведении (строже и компактнее)",
                () -> FarmMacroScreen.cfg().uiHints,
                v -> FarmMacroScreen.cfg().uiHints = v));
        s.rows.add(new Rows.Section("Маршрут").key("v.route"));
        s.rows.add(s.toggle(
                "Показывать маршрут",
                "Лента по кадрам макроса: впереди ярко, пройдено тускло, запись — красным",
                () -> FarmMacroScreen.cfg().routeEnabled,
                v -> FarmMacroScreen.cfg().routeEnabled = v));
        s.rows.add(new Rows.Selector("Когда", null, () -> ROUTE_MODES, () -> FarmMacroScreen.cfg().routeMode, v -> {
                    FarmMacroScreen.cfg().routeMode = v;
                    FarmMacroScreen.save();
                })
                .under(ro));
        s.rows.add(new Rows.Selector(
                        "Цвет ленты",
                        "Цвет участка впереди",
                        () -> HAT_COLORS,
                        () -> FarmMacroScreen.cfg().routeColor,
                        v -> {
                            FarmMacroScreen.cfg().routeColor = v;
                            FarmMacroScreen.save();
                        })
                .under(ro));
        s.rows.add(s.number(
                        "Непрозрачность",
                        null,
                        () -> FarmMacroScreen.cfg().routeOpacity,
                        v -> FarmMacroScreen.cfg().routeOpacity = (int) v,
                        1,
                        100,
                        5,
                        25,
                        v -> (int) v + "%")
                .integer()
                .under(ro));
        s.rows.add(s.toggle(
                        "Свечение",
                        "Светлая середина и мягкий ореол — лента ярче на любом фоне",
                        () -> FarmMacroScreen.cfg().routeGlow,
                        v -> FarmMacroScreen.cfg().routeGlow = v)
                .under(ro));
        s.rows.add(new Rows.Selector(
                        "Сквозь стены",
                        "Слегка — видно за блоками, ярко там, где не перекрыто",
                        () -> ROUTE_XRAY,
                        () -> FarmMacroScreen.cfg().routeSeeThrough,
                        v -> {
                            FarmMacroScreen.cfg().routeSeeThrough = v;
                            FarmMacroScreen.save();
                        })
                .under(ro));
        s.rows.add(s.toggle(
                        "Стрелки направления",
                        null,
                        () -> FarmMacroScreen.cfg().routeArrows,
                        v -> FarmMacroScreen.cfg().routeArrows = v)
                .under(ro));
        s.rows.add(s.toggle(
                        "ЛКМ / ПКМ, прыжки",
                        "Ломание — оранжевым, ПКМ — фиолетовым; точки прыжка и приседания",
                        () -> FarmMacroScreen.cfg().routeShowActions,
                        v -> FarmMacroScreen.cfg().routeShowActions = v)
                .under(ro));
        s.rows.add(s.number(
                        "Толщина",
                        null,
                        () -> FarmMacroScreen.cfg().routeWidth,
                        v -> FarmMacroScreen.cfg().routeWidth = v,
                        0.005,
                        1,
                        0.02,
                        0.1,
                        v -> Rows.num(v) + " бл")
                .under(ro)
                .adv());
        s.rows.add(s.number(
                        "Радиус отрисовки",
                        "Дальше от игрока маршрут не рисуется",
                        () -> FarmMacroScreen.cfg().routeRadius,
                        v -> FarmMacroScreen.cfg().routeRadius = (int) v,
                        1,
                        1024,
                        8,
                        32,
                        v -> (int) v + " бл")
                .integer()
                .under(ro)
                .adv());
        s.rows.add(s.number(
                        "Шаг стрелок",
                        null,
                        () -> FarmMacroScreen.cfg().routeArrowSpacing,
                        v -> FarmMacroScreen.cfg().routeArrowSpacing = v,
                        0.5,
                        64,
                        1,
                        4,
                        v -> Rows.num(v) + " бл")
                .under(() -> FarmMacroScreen.cfg().routeEnabled && FarmMacroScreen.cfg().routeArrows)
                .adv());
        s.rows.add(s.toggle(
                        "От точки запуска",
                        "Во время игры сдвигать маршрут туда, откуда реально запущен макрос",
                        () -> FarmMacroScreen.cfg().routeRelative,
                        v -> FarmMacroScreen.cfg().routeRelative = v)
                .under(ro)
                .adv());

        s.rows.add(new Rows.Section("Цвета маршрута").key("v.colors").closed().adv());
        s.rows.add(colorRow(
                        "Цвет точек",
                        "Точки маршрута по точкам",
                        () -> FarmMacroScreen.cfg().routePointColor,
                        v -> FarmMacroScreen.cfg().routePointColor = v)
                .under(ro)
                .adv());
        s.rows.add(colorRow(
                        "Цвет номеров",
                        "Номера над точками (выбранная — жёлтая)",
                        () -> FarmMacroScreen.cfg().routeLabelColor,
                        v -> FarmMacroScreen.cfg().routeLabelColor = v)
                .under(ro)
                .adv());
        s.rows.add(colorRow(
                        "Маяк старта",
                        "По умолчанию зелёный",
                        () -> FarmMacroScreen.cfg().routeStartColor,
                        v -> FarmMacroScreen.cfg().routeStartColor = v)
                .under(ro)
                .adv());
        s.rows.add(colorRow(
                        "Маяк остановки",
                        "По умолчанию красный",
                        () -> FarmMacroScreen.cfg().routeStopColor,
                        v -> FarmMacroScreen.cfg().routeStopColor = v)
                .under(ro)
                .adv());
        s.rows.add(colorRow(
                        "Цвет стрелок",
                        "По умолчанию белые",
                        () -> FarmMacroScreen.cfg().routeArrowColor,
                        v -> FarmMacroScreen.cfg().routeArrowColor = v)
                .under(ro)
                .adv());

        PerformanceRows.build(s.rows, FarmMacroScreen::save);
        s.rows.add(new Rows.Section("Freecam / Freelook / Low render").key("v.view"));
        s.rows.add(new Rows.Note(
                "Freecam — свободная камера без перемещения игрока. Назначь клавишу в разделе Клавиши. Включение останавливает макрос и Combat; выключение не возобновляет их. WASD, Space/Shift; только уже загруженный мир. На сервере игрок остаётся уязвимым.",
                Ui.SUB));
        s.rows.add(new Rows.Buttons(new Rows.Btn("Freecam: вкл / выкл", Rows.Style.SECONDARY, () -> {
            s.mc().setScreen(null);
            Freecam.toggle(s.mc());
        })));
        s.rows.add(s.number(
                "Freecam: скорость",
                null,
                () -> FarmMacroScreen.cfg().freecamSpeed,
                v -> FarmMacroScreen.cfg().freecamSpeed = v,
                1,
                40,
                1,
                5,
                v -> Rows.num(v) + " бл/с"));
        s.rows.add(s.number(
                "Freecam: радиус от старта",
                "Ограничение локальной камеры; не расширяет загрузку чанков",
                () -> FarmMacroScreen.cfg().freecamRadius,
                v -> FarmMacroScreen.cfg().freecamRadius = v,
                4,
                64,
                4,
                8,
                v -> Rows.num(v) + " бл"));
        s.rows.add(new Rows.Note(
                () -> "Freelook (" + MacroManager.keyName(FarmMacroMod.keyFreelook)
                        + ") — вид со стороны: мышь крутит только камеру, "
                        + "игрок и автоход не поворачиваются. Low render ("
                        + MacroManager.keyName(FarmMacroMod.keyLowRender)
                        + ") — рисовать только пару блоков вокруг игрока, остальное — пусто.",
                Ui.SUB));
        s.rows.add(s.toggle(
                "Freelook: держать клавишу",
                "Вкл — пока зажата; выкл — нажал: вкл, ещё раз: выкл",
                () -> FarmMacroScreen.cfg().freelookHold,
                v -> FarmMacroScreen.cfg().freelookHold = v));
        s.rows.add(s.toggle(
                "Freelook: выключать при панике",
                "Сразу вернуть свой вид от первого лица",
                () -> FarmMacroScreen.cfg().freelookOffOnPanic,
                v -> FarmMacroScreen.cfg().freelookOffOnPanic = v));
        s.rows.add(s.toggle(
                "Производительность при старте",
                "Режим производительности (" + MacroManager.keyName(FarmMacroMod.keyPerf)
                        + ") на время автоматики (ферма/Combat/сервисы): лимит FPS, без облаков и теней, минимум частиц. После — всё как было",
                () -> FarmMacroScreen.cfg().perfOnStart,
                v -> FarmMacroScreen.cfg().perfOnStart = v));
        s.rows.add(s.number(
                        "Производительность: FPS",
                        null,
                        () -> FarmMacroScreen.cfg().perfFps,
                        v -> FarmMacroScreen.cfg().perfFps = (int) v,
                        10,
                        260,
                        5,
                        30,
                        v -> (int) v + " FPS")
                .integer());
        s.rows.add(s.number(
                        "Low render: радиус",
                        "Рисуются только блоки в этом радиусе вокруг игрока, остальное — пусто (небо). "
                                + "Меняется сразу при следующем шаге",
                        () -> FarmMacroScreen.cfg().lowRenderRadius,
                        v -> FarmMacroScreen.cfg().lowRenderRadius = (int) v,
                        1,
                        32,
                        1,
                        2,
                        v -> (int) v + " бл")
                .integer());

        // v1.16.3: картинка в картинке и скрытие ника
        s.rows.add(new Rows.Section("Окно: картинка в картинке").key("v.pip"));
        s.rows.add(new Rows.Note(
                () -> "Клавиша «Картинка в картинке» (" + MacroManager.keyName(FarmMacroMod.keyPip)
                        + ", назначь в «Управление → Kaelix») — окно игры становится маленьким в углу экрана, поверх всех окон; "
                        + "ещё раз — прежний размер. Макрос в нём работает как обычно.",
                Ui.SUB));
        s.rows.add(new Rows.Buttons(new Rows.Btn("Вкл / выкл сейчас", Rows.Style.SECONDARY, () -> {
                    s.onClose();
                    Pip.toggle(s.mc());
                })
                .tip("То же, что клавиша")));
        s.rows.add(s.number(
                        "Ширина",
                        null,
                        () -> FarmMacroScreen.cfg().pipWidth,
                        v -> FarmMacroScreen.cfg().pipWidth = (int) v,
                        160,
                        3840,
                        10,
                        80,
                        v -> (int) v + " px")
                .integer());
        s.rows.add(s.number(
                        "Высота",
                        null,
                        () -> FarmMacroScreen.cfg().pipHeight,
                        v -> FarmMacroScreen.cfg().pipHeight = (int) v,
                        90,
                        2160,
                        10,
                        45,
                        v -> (int) v + " px")
                .integer());
        s.rows.add(new Rows.Selector(
                "Угол экрана", null, () -> PIP_CORNERS, () -> String.valueOf(FarmMacroScreen.cfg().pipCorner), v -> {
                    FarmMacroScreen.cfg().pipCorner = Integer.parseInt(v);
                    FarmMacroScreen.save();
                }));
        s.rows.add(s.number(
                        "Отступ от края",
                        null,
                        () -> FarmMacroScreen.cfg().pipMargin,
                        v -> FarmMacroScreen.cfg().pipMargin = (int) v,
                        0,
                        400,
                        2,
                        16,
                        v -> (int) v + " px")
                .integer()
                .adv());
        s.rows.add(s.toggle(
                        "Поверх всех окон",
                        null,
                        () -> FarmMacroScreen.cfg().pipOnTop,
                        v -> FarmMacroScreen.cfg().pipOnTop = v)
                .adv());
        s.rows.add(s.toggle(
                        "Без рамки",
                        null,
                        () -> FarmMacroScreen.cfg().pipBorderless,
                        v -> FarmMacroScreen.cfg().pipBorderless = v)
                .adv());
        s.rows.add(s.toggle(
                "Отпустить курсор во время макроса",
                "Как U: можно работать в других программах, макрос фармит дальше",
                () -> FarmMacroScreen.cfg().pipUngrab,
                v -> FarmMacroScreen.cfg().pipUngrab = v));

        s.rows.add(new Rows.Section("Скрытие ника").key("v.nick").closed());
        s.rows.add(new Rows.Note(
                "Свой ник заменяется на твой текст: чат, Tab, ник над головой, табло, титры. Только у тебя на экране — "
                        + "на сервер ничего не уходит, проверки видят настоящий ник.",
                Ui.SUB));
        s.rows.add(
                new Rows.Selector("Скрывать", null, () -> NICK_MODES, () -> FarmMacroScreen.cfg().nickHideMode, v -> {
                    FarmMacroScreen.cfg().nickHideMode = v;
                    FarmMacroScreen.save();
                }));
        BooleanSupplier nh = () -> !"off".equals(FarmMacroScreen.cfg().nickHideMode);
        s.rows.add(new Rows.TextField(
                        "Вместо ника",
                        "До 16 символов",
                        () -> FarmMacroScreen.cfg().nickHideText,
                        v -> {
                            FarmMacroScreen.cfg().nickHideText = NickText.clean(v, "Player");
                            FarmMacroScreen.save();
                            return true;
                        },
                        null,
                        16,
                        120)
                .under(nh));
        s.rows.add(s.toggle(
                        "Скрывать и друзей",
                        "Ники из «Паника → Друзья»",
                        () -> FarmMacroScreen.cfg().nickHideFriends,
                        v -> FarmMacroScreen.cfg().nickHideFriends = v)
                .under(nh));
        s.rows.add(new Rows.TextField(
                        "Вместо ника друга",
                        "До 16 символов",
                        () -> FarmMacroScreen.cfg().nickFriendText,
                        v -> {
                            FarmMacroScreen.cfg().nickFriendText = NickText.clean(v, "Friend");
                            FarmMacroScreen.save();
                            return true;
                        },
                        null,
                        16,
                        120)
                .under(() -> nh.getAsBoolean() && FarmMacroScreen.cfg().nickHideFriends));

        StatsRows.build(s.rows, FarmMacroScreen::save);
        s.rows.add(new Rows.Section("HUD").key("v.hud"));
        s.rows.add(s.toggle(
                "HUD только по делу",
                "Вне активной работы убирает статистику/питомца/инвентарь. Аварийные и реально включённые режимы остаются видны",
                () -> FarmMacroScreen.cfg().hudContextOnly,
                v -> FarmMacroScreen.cfg().hudContextOnly = v));
        s.rows.add(new Rows.Selector(
                "Стиль плашек",
                "Kaelix: спокойный Slate, тонкая рамка и короткая метка. Legacy оставляет прежние цветные полосы. Позиции/личные цвета не сбрасываются",
                () -> List.of(
                        new Rows.Choice("KAELIX", "Kaelix / Slate"), new Rows.Choice("LEGACY", "Legacy / прежний")),
                () -> FarmMacroScreen.cfg().hudStyle,
                v -> {
                    FarmMacroScreen.cfg().hudStyle = v;
                    FarmMacroScreen.save();
                }));
        s.rows.add(s.toggle(
                        "Legacy: цвет сверху и снизу",
                        "Только в прежнем стиле. Сохранённые параметры стекла не удаляются",
                        () -> FarmMacroScreen.cfg().hudGlassBands,
                        v -> FarmMacroScreen.cfg().hudGlassBands = v)
                .under(() -> FarmMacroScreen.cfg().hudStyle.equals("LEGACY")));
        s.rows.add(s.number(
                        "Плотность стекла",
                        "Независима от прежней непрозрачности; личная непрозрачность плашки имеет приоритет",
                        () -> FarmMacroScreen.cfg().hudGlassOpacity,
                        v -> FarmMacroScreen.cfg().hudGlassOpacity = (int) v,
                        15,
                        90,
                        5,
                        10,
                        v -> (int) v + "%")
                .integer()
                .under(() -> FarmMacroScreen.cfg().hudStyle.equals("LEGACY") && FarmMacroScreen.cfg().hudGlassBands));
        s.rows.add(colorRow(
                        "Акцент стеклянного HUD",
                        "По умолчанию зелёный; индивидуальный акцент плашки имеет приоритет, паника остаётся красной",
                        () -> FarmMacroScreen.cfg().hudBandColor,
                        v -> FarmMacroScreen.cfg().hudBandColor = v)
                .under(() -> FarmMacroScreen.cfg().hudStyle.equals("LEGACY") && FarmMacroScreen.cfg().hudGlassBands));
        s.rows.add(s.toggle(
                "Инвентарь HUD",
                "Только отображение 36 слотов. Не открывает меню и не перемещает предметы",
                () -> FarmMacroScreen.cfg().hudInventory,
                v -> {
                    FarmMacroScreen.cfg().hudInventory = v;
                    if (v) {
                        var s = FarmMacroScreen.cfg().hudSlots.computeIfAbsent("inventory", id -> new HudSlot());
                        s.hidden = false;
                        s.always = true;
                    }
                }));
        s.rows.add(s.toggle(
                        "Модель игрока в инвентаре HUD",
                        "В узкой общей плашке модель скрыта, чтобы не перекрывать слоты",
                        () -> FarmMacroScreen.cfg().hudInventoryPlayer,
                        v -> FarmMacroScreen.cfg().hudInventoryPlayer = v)
                .under(() -> FarmMacroScreen.cfg().hudInventory));
        s.rows.add(s.toggle(
                "Панель статуса",
                "Состояние, прогресс, круги, время сессии",
                () -> FarmMacroScreen.cfg().statsHudEnabled,
                v -> FarmMacroScreen.cfg().statsHudEnabled = v));
        s.rows.add(s.toggle(
                "Навигатор",
                "Панель со стрелкой к точке старта или остановки",
                () -> FarmMacroScreen.cfg().navHudEnabled,
                v -> FarmMacroScreen.cfg().navHudEnabled = v));
        s.rows.add(s.toggle(
                "Стрелка у прицела",
                "Маленькая стрелка к цели вокруг прицела и расстояние",
                () -> FarmMacroScreen.cfg().navCrosshairEnabled,
                v -> FarmMacroScreen.cfg().navCrosshairEnabled = v));
        s.rows.add(s.toggle(
                        "Полоска подозрительности",
                        "«ПОДОЗР. N/10», пока играет макрос (нужна «Паника → Подозрительность»)",
                        () -> FarmMacroScreen.cfg().suspicionHud,
                        v -> FarmMacroScreen.cfg().suspicionHud = v)
                .enabledIf(() -> FarmMacroScreen.cfg().panicEnabled && FarmMacroScreen.cfg().suspicionEnabled));
        s.rows.add(colorRow(
                        "Цвет HUD",
                        "Акцент навигатора и стрелки у прицела",
                        () -> FarmMacroScreen.cfg().hudAccentColor,
                        v -> FarmMacroScreen.cfg().hudAccentColor = v)
                .adv());
        s.rows.add(colorRow(
                        "Фон HUD",
                        "Подложка панелей (полупрозрачная)",
                        () -> FarmMacroScreen.cfg().hudBgColor,
                        v -> FarmMacroScreen.cfg().hudBgColor = v)
                .adv());
        s.rows.add(s.toggle(
                "Одна общая плашка",
                "Все блоки HUD — в одной панели вместо стопки",
                () -> FarmMacroScreen.cfg().hudMerged,
                v -> FarmMacroScreen.cfg().hudMerged = v));
        s.rows.add(s.number(
                        "Ширина плашек",
                        "Шире — длинный текст влезает в строку (v1.15.1: было 132)",
                        () -> FarmMacroScreen.cfg().hudWidth,
                        v -> FarmMacroScreen.cfg().hudWidth = (int) v,
                        120,
                        480,
                        10,
                        40,
                        v -> (int) v + " px")
                .integer());
        s.rows.add(s.number(
                "Размер HUD",
                "Весь HUD крупнее/мельче (вместе с масштабом плашек в F8)",
                () -> FarmMacroScreen.cfg().hudScale,
                v -> FarmMacroScreen.cfg().hudScale = v,
                0.5,
                3,
                0.05,
                0.25,
                v -> Math.round(v * 100) + " %"));
        s.rows.add(s.toggle(
                "Переносить длинный текст",
                "Не обрезать «…», а переносить на следующую строку",
                () -> FarmMacroScreen.cfg().hudWrap,
                v -> FarmMacroScreen.cfg().hudWrap = v));
        s.rows.add(s.number(
                        "Последняя паника",
                        "Плашка: какой детектор сработал, причина и время — столько минут после паники (0 — не показывать)",
                        () -> FarmMacroScreen.cfg().hudPanicMinutes,
                        v -> FarmMacroScreen.cfg().hudPanicMinutes = (int) v,
                        0,
                        1440,
                        5,
                        30,
                        v -> (int) v == 0 ? "выкл" : (int) v + " мин")
                .integer());
        s.rows.add(new Rows.Buttons(new Rows.Btn("Редактор HUD", Rows.Style.PRIMARY, () -> {
                    FarmMacroScreen.save();
                    s.mc().setScreen(new HudEditorScreen(s));
                })
                .tip("Тащить, масштаб, скрыть. Клавиша: " + MacroManager.keyName(FarmMacroMod.keyHudEdit))));

        BooleanSupplier on = () -> FarmMacroScreen.cfg().hatEnabled;
        s.rows.add(new Rows.Section("China Hat").key("v.hat"));
        s.rows.add(s.toggle(
                "Шляпа",
                "Полупрозрачный конус над головой (видишь только ты)",
                () -> FarmMacroScreen.cfg().hatEnabled,
                v -> FarmMacroScreen.cfg().hatEnabled = v));
        s.rows.add(new Rows.Buttons(
                        new Rows.Btn("Фиолет → оранж", Rows.Style.SECONDARY, () -> {
                            FarmMacroScreen.cfg().hatStyle = "gradient";
                            FarmMacroScreen.cfg().hatColor1 = "purple";
                            FarmMacroScreen.cfg().hatColor2 = "orange";
                            FarmMacroScreen.save();
                        }),
                        new Rows.Btn("Голубой", Rows.Style.SECONDARY, () -> {
                            FarmMacroScreen.cfg().hatStyle = "solid";
                            FarmMacroScreen.cfg().hatColor1 = "cyan";
                            FarmMacroScreen.save();
                        }))
                .under(on));
        s.rows.add(new Rows.Selector(
                        "Стиль цвета",
                        "Градиент по кругу, один цвет или перелив радуги",
                        () -> HAT_STYLES,
                        () -> FarmMacroScreen.cfg().hatStyle,
                        v -> {
                            FarmMacroScreen.cfg().hatStyle = v;
                            FarmMacroScreen.save();
                        })
                .under(on));
        s.rows.add(new Rows.Selector("Цвет 1", null, () -> HAT_COLORS, () -> FarmMacroScreen.cfg().hatColor1, v -> {
                    FarmMacroScreen.cfg().hatColor1 = v;
                    FarmMacroScreen.save();
                })
                .under(() -> FarmMacroScreen.cfg().hatEnabled && !"rainbow".equals(FarmMacroScreen.cfg().hatStyle)));
        s.rows.add(new Rows.Selector(
                        "Цвет 2",
                        "Второй цвет градиента",
                        () -> HAT_COLORS,
                        () -> FarmMacroScreen.cfg().hatColor2,
                        v -> {
                            FarmMacroScreen.cfg().hatColor2 = v;
                            FarmMacroScreen.save();
                        })
                .under(() -> FarmMacroScreen.cfg().hatEnabled && "gradient".equals(FarmMacroScreen.cfg().hatStyle)));
        s.rows.add(s.number(
                        "Прозрачность",
                        "0 — не видно, 100 — непрозрачная",
                        () -> FarmMacroScreen.cfg().hatOpacity,
                        v -> FarmMacroScreen.cfg().hatOpacity = (int) v,
                        0,
                        100,
                        5,
                        25,
                        v -> (int) v + "%")
                .integer()
                .under(on));
        s.rows.add(s.toggle(
                        "От первого лица",
                        "Видно, если посмотреть вверх. В F5 видно всегда",
                        () -> FarmMacroScreen.cfg().hatFirstPerson,
                        v -> FarmMacroScreen.cfg().hatFirstPerson = v)
                .under(on));
        s.rows.add(s.toggle(
                        "На всех игроках",
                        "Шляпы на других игроках (видишь только ты)",
                        () -> FarmMacroScreen.cfg().hatAllPlayers,
                        v -> FarmMacroScreen.cfg().hatAllPlayers = v)
                .under(on));
        s.rows.add(s.number(
                        "Радиус полей",
                        null,
                        () -> FarmMacroScreen.cfg().hatRadius,
                        v -> FarmMacroScreen.cfg().hatRadius = v,
                        0.05,
                        3,
                        0.05,
                        0.25,
                        v -> Rows.num(v) + " бл")
                .under(on)
                .adv());
        s.rows.add(s.number(
                        "Высота конуса",
                        null,
                        () -> FarmMacroScreen.cfg().hatHeight,
                        v -> FarmMacroScreen.cfg().hatHeight = v,
                        0.01,
                        2,
                        0.05,
                        0.2,
                        v -> Rows.num(v) + " бл")
                .under(on)
                .adv());
        s.rows.add(s.number(
                        "Смещение по высоте",
                        "Насколько поля выше макушки",
                        () -> FarmMacroScreen.cfg().hatOffset,
                        v -> FarmMacroScreen.cfg().hatOffset = v,
                        -1,
                        2,
                        0.02,
                        0.1,
                        v -> Rows.num(v) + " бл")
                .under(on)
                .adv());
        s.rows.add(s.number(
                        "Скорость",
                        "Вращение градиента / перелива, 0 — стоит",
                        () -> FarmMacroScreen.cfg().hatSpeed,
                        v -> FarmMacroScreen.cfg().hatSpeed = v,
                        0,
                        20,
                        0.1,
                        1,
                        v -> "×" + Rows.num(v))
                .under(on)
                .adv());
        s.rows.add(s.number(
                        "Сегменты",
                        "Качество круга",
                        () -> FarmMacroScreen.cfg().hatSegments,
                        v -> FarmMacroScreen.cfg().hatSegments = (int) v,
                        3,
                        256,
                        4,
                        16,
                        v -> String.valueOf((int) v))
                .integer()
                .under(on)
                .adv());

        // v1.16.1: анимешные щёчки на лице модели игрока
        BooleanSupplier ch = () -> FarmMacroScreen.cfg().cheeksEnabled;
        s.rows.add(new Rows.Section("Анимешные щёчки").key("v.cheeks"));
        s.rows.add(s.toggle(
                "Щёчки",
                "Румянец на лице твоего персонажа: видно от 3-го лица (F5), во Freelook и в инвентаре. Видишь только ты",
                () -> FarmMacroScreen.cfg().cheeksEnabled,
                v -> FarmMacroScreen.cfg().cheeksEnabled = v));
        s.rows.add(
                new Rows.Custom(CheekPreview.HEIGHT, (g, x, y, w) -> CheekPreview.draw(g, s.mc(), x, y, w)).under(ch));
        s.rows.add(new Rows.Selector("Стиль", null, () -> CHEEK_STYLES, () -> FarmMacroScreen.cfg().cheeksStyle, v -> {
                    FarmMacroScreen.cfg().cheeksStyle = v;
                    FarmMacroScreen.save();
                })
                .under(ch));
        s.rows.add(new Rows.Selector("Цвет", null, () -> CHEEK_COLORS, () -> FarmMacroScreen.cfg().cheeksColor, v -> {
                    FarmMacroScreen.cfg().cheeksColor = v;
                    FarmMacroScreen.save();
                })
                .under(ch));
        s.rows.add(s.number(
                        "Яркость",
                        "0 — не видно, 100 — во всю щёку",
                        () -> FarmMacroScreen.cfg().cheeksOpacity,
                        v -> FarmMacroScreen.cfg().cheeksOpacity = (int) v,
                        0,
                        100,
                        5,
                        25,
                        v -> (int) v + "%")
                .integer()
                .under(ch));
        s.rows.add(s.toggle(
                        "Пульсация",
                        "Румянец то сильнее, то слабее — как будто смущается",
                        () -> FarmMacroScreen.cfg().cheeksPulse,
                        v -> FarmMacroScreen.cfg().cheeksPulse = v)
                .under(ch));
        s.rows.add(s.toggle(
                        "У всех игроков",
                        "Щёчки и на других игроках (тоже видишь только ты)",
                        () -> FarmMacroScreen.cfg().cheeksAllPlayers,
                        v -> FarmMacroScreen.cfg().cheeksAllPlayers = v)
                .under(ch));
        s.rows.add(s.toggle(
                        "Поверх второго слоя скина",
                        "Выкл — щёчки под слоем «шляпы» скина: маска или борода на скине их закроет",
                        () -> FarmMacroScreen.cfg().cheeksOverHat,
                        v -> FarmMacroScreen.cfg().cheeksOverHat = v)
                .under(ch)
                .adv());
        s.rows.add(s.number(
                        "Размер",
                        null,
                        () -> FarmMacroScreen.cfg().cheeksSize,
                        v -> FarmMacroScreen.cfg().cheeksSize = (int) v,
                        50,
                        200,
                        5,
                        25,
                        v -> (int) v + "%")
                .integer()
                .under(ch)
                .adv());
        s.rows.add(s.number(
                        "Высота",
                        "Пиксели лица от макушки: глаза Стива — 4, щёки — 5–6",
                        () -> FarmMacroScreen.cfg().cheeksHeight,
                        v -> FarmMacroScreen.cfg().cheeksHeight = v,
                        3,
                        7.5,
                        0.1,
                        0.5,
                        v -> Rows.num(v) + " px")
                .under(ch)
                .adv());
        s.rows.add(s.number(
                        "Расстояние от носа",
                        "Насколько щёки разведены к краям лица",
                        () -> FarmMacroScreen.cfg().cheeksSpread,
                        v -> FarmMacroScreen.cfg().cheeksSpread = v,
                        1,
                        3.5,
                        0.05,
                        0.25,
                        v -> Rows.num(v) + " px")
                .under(ch)
                .adv());
        s.rows.add(new Rows.Buttons(new Rows.Btn("Сбросить щёчки", Rows.Style.SECONDARY, () -> {
            ModConfig d = new ModConfig();
            FarmMacroScreen.cfg().cheeksStyle = d.cheeksStyle;
            FarmMacroScreen.cfg().cheeksColor = d.cheeksColor;
            FarmMacroScreen.cfg().cheeksOpacity = d.cheeksOpacity;
            FarmMacroScreen.cfg().cheeksSize = d.cheeksSize;
            FarmMacroScreen.cfg().cheeksHeight = d.cheeksHeight;
            FarmMacroScreen.cfg().cheeksSpread = d.cheeksSpread;
            FarmMacroScreen.cfg().cheeksOverHat = d.cheeksOverHat;
            FarmMacroScreen.cfg().cheeksPulse = d.cheeksPulse;
            FarmMacroScreen.save();
        })));
    }
}
