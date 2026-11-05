package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.camera.CameraPresets;
import com.farmmacro.camera.CropPresets;
import com.farmmacro.config.ConfigProfiles;
import com.farmmacro.config.ModConfig;
import com.farmmacro.hud.HudEditorScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MacroStorage;
import com.farmmacro.panic.PanicSound;
import com.farmmacro.panic.PlayerRules;
import com.farmmacro.route.ChunkCache;
import com.farmmacro.route.ReturnWalker;
import com.farmmacro.route.RouteBuffer;
import com.farmmacro.route.RouteCode;
import com.farmmacro.route.RouteEditor;
import com.farmmacro.route.RouteStorage;
import com.farmmacro.route.RouteVariants;
import com.farmmacro.util.Clock;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * Главное меню мода. Свой интерфейс без текстур Minecraft: боковые вкладки, карточки настроек,
 * анимированные переключатели, числовые поля со стрелками, прокрутка.
 * Все значения читаются из конфига при каждой отрисовке и сохраняются сразу при изменении.
 */
public class FarmMacroScreen extends Screen implements Rows.Ctx {

    private static final String[] TABS = {
        "Паника",
        "Запуск",
        "Макросы",
        "Маршруты",
        "Камера",
        "Визуал",
        "Конфиги",
        "Автоматизация",
        "Combat / Hunting",
        "Клавиши"
    };
    private static final String[] TAB_ICONS = {"⚠", "▶", "☰", "⌖", "◎", "✦", "⚙", "↗", "⊕", "⌨"};
    private static int lastTab = 0; // вкладка запоминается между открытиями

    private final Screen parent;
    private boolean moduleHome = true;
    private static int lastCategory;
    private int category = lastCategory;
    private Set<String> moduleSections = Set.of();
    private String moduleTitle;
    private int moduleSelection;
    private final List<ModuleCatalog.Module> visibleModules = new ArrayList<>();
    private int tab = lastTab;
    final List<Rows.Row> rows = new ArrayList<>();
    private final PanicTab panicTab = new PanicTab(this);
    private final LaunchTab launchTab = new LaunchTab(this);
    private final VisualTab visualTab = new VisualTab(this);

    private double scroll, scrollTarget;
    private long lastFrameNs;
    private boolean draggingScrollbar;
    private String tooltip;
    private boolean wantHand;

    List<Rows.Choice> soundOptions = List.of();
    private List<MacroStorage.MacroInfo> macros = List.of();
    private List<RouteStorage.RouteInfo> routes = List.of();
    private long confirmClearRouteUntil;
    private String confirmDelete; // имя файла, ожидающего подтверждения удаления
    private long confirmUntil;

    public FarmMacroScreen(Screen parent) {
        super(Component.literal("Kaelix"));
        this.parent = parent;
    }

    private FarmMacroScreen(Screen parent, int tab, String title, Set<String> sections) {
        this(parent);
        this.moduleHome = false;
        this.tab = tab;
        this.moduleTitle = title;
        this.moduleSections = sections;
    }

    /** Экран одного модуля, как после клика по его карточке (используют и game-тесты). */
    static FarmMacroScreen module(Screen parent, ModuleCatalog.Module module) {
        var next = new FarmMacroScreen(parent, module.tab(), module.title(), module.sections());
        next.category = module.category();
        return next;
    }

    private void openModule(ModuleCatalog.Module module) {
        Rows.blurText();
        Rows.clearWheelFocus();
        var next = new FarmMacroScreen(this, module.tab(), module.title(), module.sections());
        next.category = module.category();
        minecraft.setScreen(next);
    }

    /** Legacy whole-tab settings remain available, including full global setting search. */
    private void openAllSettings() {
        Rows.blurText();
        Rows.clearWheelFocus();
        minecraft.setScreen(new FarmMacroScreen(this, lastTab, null, Set.of()));
    }

    private void switchCategory(int value) {
        if (category == value) return;
        category = lastCategory = value;
        moduleSelection = 0;
        scroll = scrollTarget = 0;
        Rows.blurText();
        Rows.clearWheelFocus();
        buildTab();
    }

    private void buildModules() {
        Rows.searching = false;
        rows.add(search);
        visibleModules.clear();
        rows.add(new Rows.Buttons(
                new Rows.Btn(
                        "Редактор HUD", Rows.Style.SECONDARY, () -> minecraft.setScreen(new HudEditorScreen(this))),
                new Rows.Btn("Все параметры", Rows.Style.SECONDARY, this::openAllSettings)));
        rows.add(new Rows.Note("Enter — настройки · запуск отдельно", Ui.SUB));
        String[] words = norm(search.query()).split("\\s+");
        int found = 0;
        for (var module : ModuleCatalog.ALL) {
            if (category != 0 && module.category() != category) continue;
            if (!search.query().isBlank()
                    && !matches(
                            module.title() + " " + module.description() + " "
                                    + ModuleCatalog.category(module.category()),
                            words)) continue;
            int index = found;
            visibleModules.add(module);
            rows.add(new ModuleCardRow(
                    module,
                    () -> {
                        moduleSelection = index;
                        openModule(module);
                    },
                    () -> moduleSelection == index));
            found++;
        }
        moduleSelection = Math.clamp(moduleSelection, 0, Math.max(0, found - 1));
        if (found == 0)
            rows.add(new Rows.Note("Модули не найдены. Для глобального поиска открой «Все параметры».", Ui.SUB));
        Rows.link(rows);
    }

    /** Filter by original stable section keys; never duplicate or reimplement the controls. */
    private void filterModule(int from) {
        if (moduleTitle == null) return;
        boolean keep = false;
        var selected = new ArrayList<Rows.Row>();
        for (var row : new ArrayList<>(rows.subList(from, rows.size()))) {
            if (row instanceof Rows.Section section) {
                keep = moduleSections.contains(section.key());
                if (keep) section.expandedLocally();
            }
            if (keep) selected.add(row);
            if (row instanceof Rows.End) keep = false;
        }
        rows.subList(from, rows.size()).clear();
        rows.addAll(selected);
    }

    // ── Геометрия (всё от размера окна, поэтому ничего не наезжает на любом масштабе GUI) ──

    private MenuLayout.Window geometry() {
        return MenuLayout.window(width, height, cfg().guiOffsetX, cfg().guiOffsetY);
    }

    private int winW() {
        return geometry().w();
    }

    private int winH() {
        return geometry().h();
    }

    private int winX() {
        return geometry().x();
    }

    private int winY() {
        return geometry().y();
    }

    private boolean draggingWindow;
    private double dragFromX, dragFromY;
    private int dragOffX, dragOffY;
    private long lastHeaderClick;

    private int sideW() {
        return geometry().side();
    }

    private static final int HEADER_H = MenuLayout.HEADER;

    private int tabStride() {
        return Math.min(25, Math.max(10, (winH() - HEADER_H - 30) / TABS.length));
    }

    private int contX() {
        return geometry().content().x();
    }

    private int contY() {
        return geometry().content().y();
    }

    private int contW() {
        return geometry().content().w();
    }

    private int contH() {
        return geometry().content().h();
    }

    private void browseCategory(int value) {
        if (moduleHome) {
            switchCategory(value);
            return;
        }
        Rows.blurText();
        Rows.clearWheelFocus();
        if (parent instanceof FarmMacroScreen home && home.moduleHome) {
            home.search.clear();
            home.switchCategory(value);
            home.buildTab();
            minecraft.setScreen(home);
        } else {
            var home = new FarmMacroScreen(parent);
            home.category = lastCategory = value;
            minecraft.setScreen(home);
        }
    }

    @Override
    protected void init() {
        refreshLists();
        buildTab();
    }

    void refreshLists() {
        List<Rows.Choice> opts = new ArrayList<>();
        for (PanicSound.Option o : PanicSound.options()) opts.add(new Rows.Choice(o.id(), o.label()));
        String cur = ModConfig.INSTANCE.panicSound;
        if (opts.stream().noneMatch(o -> o.id().equals(cur))) opts.add(new Rows.Choice(cur, PanicSound.label(cur)));
        soundOptions = opts;
        macros = MacroStorage.INSTANCE.listMacros();
        routes = RouteStorage.INSTANCE.list();
    }

    private void switchTab(int t) {
        if (moduleTitle != null) return;
        if (t == tab && search.query().isEmpty() && moduleTitle == null) return;
        moduleSections = Set.of();
        moduleTitle = null;
        if (!search.query().isEmpty()) {
            search.clear();
            pendingRebuild = false;
        }
        tab = lastTab = t;
        scroll = scrollTarget = 0;
        Rows.clearWheelFocus();
        Rows.blurText();
        buildTab();
    }

    // ── Содержимое вкладок ───────────────────────────────────────────────────

    /** Для вынесенных вкладок: {@code minecraft} у Screen защищённый. */
    Minecraft mc() {
        return minecraft;
    }

    static ModConfig cfg() {
        return ModConfig.INSTANCE;
    }

    static void save() {
        ModConfig.save();
    }

    // ── v1.11: вкладки, поиск, простой/расширенный режим ─────────────────────

    /** Поиск живёт между перестройками списка (в нём фокус ввода). Перестраиваем в начале кадра, не посреди отрисовки. */
    private final Rows.Search search = new Rows.Search(q -> {
        pendingRebuild = true;
        scrollTarget = 0;
    });

    private boolean pendingRebuild;
    private String pendingJump; // ключ раздела, к которому прокрутить после перестройки

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
    }

    private String title() {
        return moduleHome
                ? "Модули"
                : search.query().isEmpty() ? moduleTitle != null ? moduleTitle : TABS[tab] : "Поиск";
    }

    private void buildTab() {
        rows.clear();
        if (moduleHome) {
            buildModules();
            return;
        }
        String q = norm(search.query());
        if (!q.isEmpty()) {
            buildSearch(q);
            return;
        }
        Rows.searching = false;
        rows.add(search);
        if (moduleTitle != null)
            rows.add(new Rows.Buttons(new Rows.Btn("← Все модули", Rows.Style.SECONDARY, this::onClose)));
        int from = rows.size();
        buildContent(tab);
        filterModule(from);
        rows.add(new Rows.End());
        rows.add(new Rows.MoreToggle(() -> Rows.hiddenAdvanced(rows), this::toggleAdvanced));
        Rows.link(rows);
    }

    private void buildContent(int t) {
        switch (t) {
            case 0 -> panicTab.build();
            case 1 -> launchTab.build();
            case 2 -> buildMacros();
            case 3 -> buildRoutes();
            case 4 -> buildCamera();
            case 5 -> visualTab.build();
            case 6 -> buildConfigs();
            case 7 -> buildAutomation();
            case 8 -> CombatRows.build(rows, FarmMacroScreen::save, this::buildTab);
            case 9 -> KeybindRows.build(rows, this);
        }
    }

    /** Результаты поиска: подходящие строки всех вкладок, сгруппированные «Вкладка › Раздел». */
    private void buildSearch(String q) {
        Rows.searching = false;
        List<Rows.Row> out = new ArrayList<>();
        out.add(search);
        String[] words = q.split("\\s+");
        for (int t = 0; t < TABS.length; t++) {
            if (moduleTitle != null && t != tab) continue;
            rows.clear();
            buildContent(t);
            filterModule(0);
            List<Rows.Row> part = new ArrayList<>(rows);
            Rows.Section cur = null, header = null;
            boolean curMatch = false;
            for (Rows.Row r : part) {
                if (r instanceof Rows.Section s) {
                    cur = s;
                    header = null;
                    curMatch = matches(s.title(), words) || matches(TABS[t] + " " + s.title(), words);
                    continue;
                }
                if (r instanceof Rows.End) {
                    cur = null;
                    header = null;
                    curMatch = false;
                    continue;
                }
                if (r instanceof Rows.Spacer) continue;
                if (!curMatch && !matches(r.searchText(), words)) continue;
                if (header == null) {
                    header = searchHeader(t, cur);
                    out.add(header);
                }
                out.add(r);
            }
        }
        rows.clear();
        rows.addAll(out);
        if (out.size() == 1)
            rows.add(
                    new Rows.Note("Ничего не найдено. Попробуй другое слово: «звук», «шаг», «камера», «HUD»…", Ui.SUB));
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
            if (key != null) {
                cfg().uiSections.put(key, true);
                save();
            }
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
                int y = 0;
                layout(contW());
                for (Rows.Row r : rows)
                    if (r instanceof Rows.Section s && pendingJump.equals(s.key())) {
                        y = r.ly;
                        break;
                    }
                scroll = scrollTarget = y;
                pendingJump = null;
            }
        }
        Rows.Row f = Rows.focusedRow();
        if (f != null && (!rows.contains(f) || !f.shown())) Rows.blurText();
    }

    Rows.Toggle toggle(String label, String hint, BooleanSupplier get, Consumer<Boolean> set) {
        return new Rows.Toggle(label, hint, get, v -> {
            set.accept(v);
            save();
        });
    }

    Rows.Number number(
            String label,
            String hint,
            DoubleSupplier get,
            DoubleConsumer set,
            double min,
            double max,
            double step,
            double big,
            DoubleFunction<String> fmt) {
        return new Rows.Number(
                label,
                hint,
                get,
                v -> {
                    set.accept(v);
                    save();
                },
                min,
                max,
                step,
                big,
                fmt);
    }

    /** v1.15: база задержки одного вида — среднее, разброс, мин, макс (мс). */
    void addDelayKind(
            String label,
            BooleanSupplier on,
            IntSupplier mean,
            IntConsumer setMean,
            IntSupplier sd,
            IntConsumer setSd,
            IntSupplier min,
            IntConsumer setMin,
            IntSupplier max,
            IntConsumer setMax) {
        rows.add(number(
                        label + ": среднее",
                        "Слой 1: база (нормальное распределение)",
                        mean::getAsInt,
                        v -> setMean.accept((int) v),
                        0,
                        5000,
                        5,
                        50,
                        v -> (int) v + " мс")
                .integer()
                .under(on));
        rows.add(number(
                        label + ": разброс σ",
                        null,
                        sd::getAsInt,
                        v -> setSd.accept((int) v),
                        0,
                        2000,
                        5,
                        25,
                        v -> (int) v + " мс")
                .integer()
                .under(on)
                .adv());
        rows.add(number(
                        label + ": не меньше",
                        null,
                        min::getAsInt,
                        v -> setMin.accept((int) v),
                        0,
                        5000,
                        5,
                        50,
                        v -> (int) v + " мс")
                .integer()
                .under(on)
                .adv());
        rows.add(number(
                        label + ": не больше",
                        null,
                        max::getAsInt,
                        v -> setMax.accept((int) v),
                        0,
                        5000,
                        5,
                        50,
                        v -> (int) v + " мс")
                .integer()
                .under(on)
                .adv());
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static String ticksFmt(double v) {
        return (int) v + " т · " + f1(v / 20) + " с";
    }

    // ── Конфиги ──────────────────────────────────────────────────────────────

    private String profileName = "";
    private String profileMsg = "";
    private String codeMsg = "";
    private String confirmProfileDelete;

    private void buildConfigs() {
        rows.add(new Rows.Section("Готовые наборы"));
        rows.add(new Rows.Note(
                "Меняют детекторы и общую случайность. Визуал, HUD и сохранённые маршруты не трогают; «Оптимально» ставит ещё звук паники и настройки для новых маршрутов и записей.",
                Ui.SUB));
        for (var p : ConfigProfiles.PRESETS)
            rows.add(new Rows.Buttons(new Rows.Btn(
                            p.label(), "optimal".equals(p.id()) ? Rows.Style.PRIMARY : Rows.Style.SECONDARY, () -> {
                                ConfigProfiles.applyPreset(p.id());
                                profileMsg = "Применён набор «" + p.label() + "»";
                                buildTab();
                            })
                    .tip(p.tip())));

        rows.add(new Rows.Buttons(new Rows.Btn(
                        "Мастер настройки", Rows.Style.SECONDARY, () -> minecraft.setScreen(new WelcomeScreen(this)))
                .tip("Пошагово: где играешь, друзья, осторожность, звук и экран паники, удобство")));

        rows.add(new Rows.Section("Мои профили"));
        rows.add(new Rows.Note(
                "Профиль — все настройки мода (детекторы, реакция, запуск, визуал, общая случайность), "
                        + "кроме пресетов камеры и клавиш. Файлы — config/farmmacro/configs/.",
                Ui.SUB));
        rows.add(new Rows.TextField(
                "Имя профиля",
                "Буквы, цифры, пробел, _ - .",
                () -> profileName,
                v -> {
                    profileName = v.strip();
                    return true;
                },
                null,
                ConfigProfiles.NAME_MAX,
                0));
        rows.add(new Rows.Buttons(
                new Rows.Btn(
                                () -> ConfigProfiles.exists(profileName) ? "Перезаписать" : "Сохранить текущие",
                                () -> Rows.Style.PRIMARY,
                                () -> {
                                    String err = ConfigProfiles.save(profileName);
                                    profileMsg = err != null ? err : "Сохранено: " + ConfigProfiles.clean(profileName);
                                    buildTab();
                                })
                        .enabledIf(() -> ConfigProfiles.clean(profileName) != null),
                new Rows.Btn("Папка", Rows.Style.SECONDARY, () -> {
                    try {
                        Files.createDirectories(ConfigProfiles.dir());
                    } catch (Exception ignored) {
                    }
                    openFolder(ConfigProfiles.dir());
                }),
                new Rows.Btn("Обновить", Rows.Style.SECONDARY, this::buildTab)));
        rows.add(new Rows.Note(() -> profileMsg, Ui.ON));
        var list = ConfigProfiles.list();
        if (list.isEmpty()) rows.add(new Rows.Note("Профилей пока нет.", Ui.SUB));
        for (String name : list) {
            rows.add(new Rows.Note("● " + name, Ui.TEXT));
            rows.add(new Rows.Buttons(
                    new Rows.Btn("Загрузить", Rows.Style.PRIMARY, () -> {
                                String err = ConfigProfiles.load(name);
                                profileMsg = err != null ? err : "Загружен профиль «" + name + "»";
                                refreshLists();
                                buildTab();
                            })
                            .enabledIf(() -> MacroManager.INSTANCE.getState() == MacroManager.State.IDLE)
                            .tip("Нельзя во время записи/воспроизведения"),
                    new Rows.Btn("Перезаписать", Rows.Style.SECONDARY, () -> {
                        String err = ConfigProfiles.save(name);
                        profileMsg = err != null ? err : "Перезаписан «" + name + "» текущими настройками";
                    }),
                    new Rows.Btn(
                            () -> name.equals(confirmProfileDelete) ? "Точно удалить?" : "Удалить",
                            () -> Rows.Style.DANGER,
                            () -> {
                                if (!name.equals(confirmProfileDelete)) {
                                    confirmProfileDelete = name;
                                    return;
                                }
                                confirmProfileDelete = null;
                                ConfigProfiles.delete(name);
                                profileMsg = "Удалён «" + name + "»";
                                buildTab();
                            })));
        }

        // v1.16.2: все настройки одной строкой
        rows.add(new Rows.Section("Строка настроек"));
        rows.add(new Rows.Note(
                "Все настройки (как профиль: без пресетов камеры и клавиш) одной строкой — скопируй и отправь "
                        + "другу или сохрани в заметках. В строке есть ники друзей и команда переподключения; адрес вебхука — нет.",
                Ui.SUB));
        rows.add(
                new Rows.Buttons(
                        new Rows.Btn("Скопировать строку", Rows.Style.PRIMARY, () -> {
                            String code = ConfigProfiles.exportCode();
                            minecraft.keyboardHandler.setClipboard(code);
                            codeMsg = "Строка настроек скопирована (" + code.length() + " символов)";
                        }),
                        new Rows.Btn("Вставить из буфера", Rows.Style.SECONDARY, () -> {
                                    String err = ConfigProfiles.importCode(minecraft.keyboardHandler.getClipboard());
                                    codeMsg = err != null ? err : "Настройки загружены из строки";
                                    if (err == null) {
                                        refreshLists();
                                        buildTab();
                                    }
                                })
                                .enabledIf(() -> MacroManager.INSTANCE.getState() == MacroManager.State.IDLE)
                                .tip(
                                        "Заменит текущие настройки (пресеты камеры и клавиши останутся). Нельзя во время записи/воспроизведения")));
        rows.add(new Rows.Note(() -> codeMsg, Ui.ON));
        rows.add(new Rows.Buttons(new Rows.Btn(
                "Что нового в v" + WhatsNewScreen.VERSION,
                Rows.Style.SECONDARY,
                () -> minecraft.setScreen(new WhatsNewScreen(this)))));

        rows.add(new Rows.Section("Общая случайность"));
        rows.add(toggle(
                "Использовать для всех",
                "Вкл — макросы и маршруты берут случайность отсюда, а не из своих ⚙",
                () -> cfg().humanUseGlobal,
                v -> cfg().humanUseGlobal = v));
        rows.add(new Rows.Note(
                () -> !cfg().humanMaster ? "Случайность выключена общим выключателем «Запуск → Случайность»." : "",
                Ui.WARN));
        HumanRows.build(
                rows,
                cfg().humanGlobal,
                true,
                true,
                () -> true,
                r -> {
                    r.run();
                    save();
                },
                this::buildTab);
    }

    private static final List<Rows.Choice> CROPS = CameraPresets.CROPS.stream()
            .map(c -> new Rows.Choice(c.id(), c.label()))
            .toList();
    /** «Запуск → Культура»: «Не менять» + культуры (без «Другое»). */
    static final List<Rows.Choice> LAUNCH_CROPS = Stream.concat(
                    Stream.of(new Rows.Choice(CropPresets.NONE, "Не менять камеру")),
                    CROPS.stream().filter(c -> !CropPresets.OTHER.equals(c.id())))
            .toList();

    private static final List<Rows.Choice> CAM_SOURCES =
            List.of(new Rows.Choice("turn", "Повернуть самому"), new Rows.Choice("mousemat", "Squeaky Mousemat"));
    private static final List<Rows.Choice> MAT_CLICKS =
            List.of(new Rows.Choice("attack", "ЛКМ (Snap)"), new Rows.Choice("use", "ПКМ"));

    private long confirmCamResetUntil;

    private static ModConfig.CamPreset selPreset() {
        return CameraPresets.selected();
    }

    private static String presetHint() {
        return MacroManager.keyName(FarmMacroMod.keyCamApply) + " — применить выбранный, "
                + MacroManager.keyName(FarmMacroMod.keyCamNext) + " — следующий, "
                + MacroManager.keyName(FarmMacroMod.keyCamSave) + " — записать текущий взгляд в выбранный.";
    }

    /** Вкладка «Камера»: пресеты yaw/pitch, не привязанные к макросам; ввод чисел, «Взять текущие», применение. */
    private void buildCamera() {
        rows.add(new Rows.Note(
                "Пресет — имя, культура, yaw и pitch (точность 0.1°). Стандартные пресеты культур можно "
                        + "переименовать, поменять и удалить. " + presetHint()
                        + " Работает в любой момент, кроме записи, которая играет с «Повторять камеру». "
                        + "«Запуск → Культура» ставит пресет культуры при старте.",
                Ui.SUB));

        rows.add(new Rows.Section("Применение"));
        rows.add(new Rows.Note(
                "Стиль и скорость поворота настраиваются в Kaelix Motion. Здесь — только сохранённые рабочие углы.",
                Ui.SUB));

        rows.add(new Rows.Section("Пресеты"));
        List<ModConfig.CamPreset> list = cfg().camPresets;
        if (list.isEmpty())
            rows.add(new Rows.Note("Пока пусто. Встань как нужно и нажми «+ Новый из текущего взгляда».", Ui.SUB));
        for (int i = 0; i < list.size(); i++) rows.add(new CamCard(i));
        rows.add(
                new Rows.Buttons(
                        new Rows.Btn("+ Новый из текущего взгляда", Rows.Style.PRIMARY, () -> {
                                    CameraPresets.addFromCurrent(minecraft);
                                    buildTab();
                                })
                                .enabledIf(() -> cfg().camPresets.size() < CameraPresets.MAX),
                        new Rows.Btn(
                                        () -> Clock.millis() < confirmCamResetUntil
                                                ? "Точно сбросить?"
                                                : "Сбросить к стандартным",
                                        () -> Clock.millis() < confirmCamResetUntil
                                                ? Rows.Style.DANGER
                                                : Rows.Style.SECONDARY,
                                        () -> {
                                            if (Clock.millis() >= confirmCamResetUntil) {
                                                confirmCamResetUntil = Clock.millis() + 3000;
                                                return;
                                            }
                                            confirmCamResetUntil = 0;
                                            CameraPresets.resetToStandard();
                                            buildTab();
                                        })
                                .tip(
                                        "Заменить все пресеты на 7 стандартных пресетов культур (свои удалятся). Нажми ещё раз для подтверждения")));

        if (list.isEmpty()) return;
        BooleanSupplier has = () -> selPreset() != null;
        rows.add(new Rows.Section("Выбранный пресет"));
        rows.add(new Rows.TextField(
                        "Имя",
                        null,
                        () -> selPreset() != null ? selPreset().name : "",
                        v -> {
                            var p = selPreset();
                            String n = v.strip();
                            if (p == null || n.isEmpty()) return false;
                            p.name = n.length() > ModConfig.CAM_NAME_MAX ? n.substring(0, ModConfig.CAM_NAME_MAX) : n;
                            save();
                            return true;
                        },
                        null,
                        ModConfig.CAM_NAME_MAX,
                        0)
                .enabledIf(has));
        rows.add(new Rows.Selector(
                        "Культура",
                        "Для подписи и «Запуск → Культура»",
                        () -> CROPS,
                        () -> selPreset() != null ? selPreset().crop : "other",
                        v -> {
                            if (selPreset() != null) {
                                selPreset().crop = v;
                                save();
                            }
                        })
                .enabledIf(has));
        rows.add(new Rows.TextField(
                        "Yaw",
                        "По горизонтали, как в F3: −180…180 (270 → −90), точность 0.1°",
                        () -> selPreset() != null ? CameraPresets.deg(selPreset().yaw) : "",
                        v -> {
                            Float f = CameraPresets.parseDeg(v);
                            var p = selPreset();
                            if (f == null || p == null) return false;
                            p.yaw = CropPresets.yaw(f);
                            save();
                            return true;
                        },
                        Rows.TextField.numeric(),
                        16,
                        92)
                .enabledIf(has));
        rows.add(new Rows.TextField(
                        "Pitch",
                        "По вертикали: −90 вверх … 90 вниз, точность 0.1°",
                        () -> selPreset() != null ? CameraPresets.deg(selPreset().pitch) : "",
                        v -> {
                            Float f = CameraPresets.parseDeg(v);
                            var p = selPreset();
                            if (f == null || p == null || f < -90f || f > 90f) return false;
                            p.pitch = CropPresets.pitch(f);
                            save();
                            return true;
                        },
                        Rows.TextField.numeric(),
                        16,
                        92)
                .enabledIf(has));
        // v1.16.3: Squeaky Mousemat
        rows.add(new Rows.Selector(
                        "Как ставить",
                        "«Squeaky Mousemat» — клик предметом Skyblock, угол ставит сервер "
                                + "(в предмете должен быть сохранён этот же угол)",
                        () -> CAM_SOURCES,
                        () -> selPreset() != null ? selPreset().source : "turn",
                        v -> {
                            if (selPreset() != null) {
                                selPreset().source = v;
                                save();
                            }
                        })
                .enabledIf(has));
        BooleanSupplier mm = () -> selPreset() != null && "mousemat".equals(selPreset().source);
        rows.add(number(
                        "Слот Mousemat",
                        "1–9 — этот слот хотбара (если у тебя несколько Mousemat с разными углами), 0 — любой",
                        () -> selPreset() != null ? selPreset().matSlot : 0,
                        v -> {
                            if (selPreset() != null) selPreset().matSlot = (int) v;
                        },
                        0,
                        9,
                        1,
                        1,
                        v -> (int) v == 0 ? "любой" : String.valueOf((int) v))
                .integer()
                .under(mm));
        rows.add(new Rows.Selector(
                        "Mousemat: клик", "На Hypixel «Snap» — ЛКМ", () -> MAT_CLICKS, () -> cfg().mousematClick, v -> {
                            cfg().mousematClick = v;
                            save();
                        })
                .under(mm)
                .adv());
        rows.add(number(
                        "Mousemat: допуск",
                        "Насколько поворот от сервера может отличаться от пресета (иначе — обычная проверка)",
                        () -> cfg().mousematTolerance,
                        v -> cfg().mousematTolerance = v,
                        0.05,
                        10,
                        0.05,
                        0.5,
                        v -> Rows.num(v) + "°")
                .under(mm)
                .adv());
        rows.add(toggle(
                        "Mousemat: нет предмета — повернуть самому",
                        null,
                        () -> cfg().mousematFallback,
                        v -> cfg().mousematFallback = v)
                .under(mm));
        rows.add(toggle(
                        "Mousemat: вернуть слот",
                        "После поворота взять в руку прежний слот",
                        () -> cfg().mousematRestoreSlot,
                        v -> cfg().mousematRestoreSlot = v)
                .under(mm)
                .adv());
        rows.add(new Rows.Buttons(
                new Rows.Btn("Взять текущие", Rows.Style.SECONDARY, () -> {
                            var p = selPreset();
                            if (p != null && minecraft.player != null) {
                                p.yaw = CropPresets.yaw(minecraft.player.getYRot());
                                p.pitch = CropPresets.pitch(minecraft.player.getXRot());
                                save();
                            }
                        })
                        .enabledIf(() -> has.getAsBoolean() && minecraft.player != null)
                        .tip("Записать в выбранный пресет, куда ты сейчас смотришь (yaw и pitch)"),
                new Rows.Btn("Yaw ровно по оси", Rows.Style.SECONDARY, () -> {
                            var p = selPreset();
                            if (p != null) {
                                p.yaw = CropPresets.yaw(Math.round(p.yaw / 45f) * 45f);
                                save();
                            }
                        })
                        .enabledIf(has)
                        .tip("Округлить yaw до ближайших 45° — чтобы идти ровно вдоль ряда"),
                new Rows.Btn("Применить", Rows.Style.SUCCESS, () -> CameraPresets.apply(minecraft, cfg().camSelected))
                        .enabledIf(() -> has.getAsBoolean() && minecraft.player != null)
                        .tip("Повернуть камеру к пресету (как " + MacroManager.keyName(FarmMacroMod.keyCamApply)
                                + ")")));
    }

    /** Карточка пресета камеры: выбрать, повернуться, удалить. */
    private final class CamCard extends Rows.Row {
        private final int index;

        CamCard(int index) {
            this.index = index;
        }

        @Override
        int height(Rows.Ctx c, int w) {
            return 22;
        }

        private int[] bx() {
            int right = lastX + lastW - 6;
            int del = right - 18, go = del - 4 - 22;
            return new int[] {go, del};
        }

        @Override
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            if (index >= cfg().camPresets.size()) return;
            var p = cfg().camPresets.get(index);
            boolean sel = index == cfg().camSelected;
            lastX = x;
            lastW = w;
            Ui.listBg(g, x, y, w, 19, 4, sel, hover);
            if (sel) g.fill(x, y + 3, x + 2, y + 16, Ui.ACCENT);
            int[] b = bx();
            Ui.text(
                    g,
                    font,
                    Ui.ellipsize(font, (index + 1) + ". " + CameraPresets.describe(p), b[0] - x - 12),
                    x + 8,
                    y + 6,
                    sel ? Ui.TEXT : Ui.SUB);
            if (Rows.drawButton(c, g, b[0], y + 2, 22, 15, "◎", Rows.Style.SUCCESS, minecraft.player != null, mx, my))
                c.tooltip("Применить пресет: повернуть камеру");
            if (Rows.drawButton(c, g, b[1], y + 2, 18, 15, "✕", Rows.Style.SECONDARY, true, mx, my))
                c.tooltip("Удалить пресет");
            if (hover && mx >= 0 && mx < b[0]) c.hand();
        }

        @Override
        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0 || index >= cfg().camPresets.size()) return false;
            int[] b = bx();
            if (Ui.inside(mx, my, b[1], lastY + 2, 18, 15)) {
                c.clickSound();
                cfg().camPresets.remove(index);
                cfg().camSelected =
                        Math.max(0, Math.min(cfg().camSelected, cfg().camPresets.size() - 1));
                save();
                buildTab();
                return true;
            }
            c.clickSound();
            cfg().camSelected = index;
            save();
            if (Ui.inside(mx, my, b[0], lastY + 2, 22, 15)) CameraPresets.apply(minecraft, index);
            return true;
        }
    }

    static final List<Rows.Choice> SOURCES =
            List.of(new Rows.Choice("RECORDING", "Запись"), new Rows.Choice("ROUTE", "Маршрут по точкам"));

    private void buildMacros() {
        rows.add(new Rows.Section("Запись действий · дополнительная функция"));
        rows.add(new Rows.Buttons(new Rows.Btn(
                        () -> MacroManager.INSTANCE.isRecording() ? "Остановить запись" : "Начать запись",
                        () -> MacroManager.INSTANCE.isRecording() ? Rows.Style.DANGER : Rows.Style.PRIMARY,
                        () -> {
                            var m = MacroManager.INSTANCE;
                            if (m.isActive() && !m.isRecording()) return;
                            onClose();
                            m.toggleRecording(minecraft);
                        })
                .enabledIf(() -> !MacroManager.INSTANCE.isActive() || MacroManager.INSTANCE.isRecording())));
        rows.add(new BufferCard());
        rows.add(new Rows.Note(
                () -> MacroManager.INSTANCE.getFrameCount() == 0
                        ? ""
                        : MacroManager.INSTANCE.getSettings().summary(false)
                                + " · ⚙ — настройки макроса (камера, зажим мыши)",
                Ui.SUB));
        rows.add(new Rows.Section("Сохранённые"));
        if (macros.isEmpty()) {
            rows.add(new Rows.Note(
                    "Пока пусто. Нажми «Начать запись»; остановка — здесь же в меню. Основной режим — маршруты по точкам.",
                    Ui.SUB));
        } else {
            for (MacroStorage.MacroInfo info : macros) rows.add(new MacroCard(info));
        }
        rows.add(new Rows.Spacer(2));
        rows.add(new Rows.Buttons(new Rows.Btn(
                "Открыть папку макросов", Rows.Style.SECONDARY, () -> openFolder(MacroStorage.MACRO_DIR))));
    }

    /** Вкладка «Маршруты»: текущий маршрут (точки), подсказка по редактору, автоход и редактор, сохранённые. */
    private void buildAutomation() {
        FishingRows.build(rows, FarmMacroScreen::save);
        AutomationRows.build(rows, () -> ModConfig.save());
        // v1.15 «Возврат на маршрут»
        rows.add(new Rows.Section("Возврат на маршрут").key("r.return"));
        BooleanSupplier ret = () -> cfg().returnEnabled;
        rows.add(toggle(
                "Возврат на маршрут",
                "Сам ищет путь (подъём на 1 блок прыжком, спуск до 3, плиты, грядки, заборы; "
                        + "вода и лава — в обход) и идёт к маршруту, камера — как мышью",
                ret,
                v -> cfg().returnEnabled = v));
        rows.add(number(
                        "Не дальше",
                        "Маршрут дальше по горизонтали — не идёт, обычный стоп с сообщением",
                        () -> cfg().returnMaxDist,
                        v -> cfg().returnMaxDist = v,
                        8,
                        256,
                        4,
                        16,
                        v -> Rows.num(v) + " бл")
                .integer()
                .under(ret));
        rows.add(toggle(
                        "После толчка / отката",
                        "Маршрут: сбили с линии (или упал на другой этаж) — вернуться по пути, а не стоп «Сошёл с маршрута»",
                        () -> cfg().returnAfterPush,
                        v -> cfg().returnAfterPush = v)
                .under(ret));
        rows.add(number(
                        "Сбили, если дальше",
                        "От линии текущего отрезка (≥ 4 тиков подряд)",
                        () -> cfg().returnOffRoute,
                        v -> cfg().returnOffRoute = v,
                        0.5,
                        16,
                        0.25,
                        1,
                        v -> Rows.num(v) + " бл")
                .under(() -> cfg().returnEnabled && cfg().returnAfterPush));
        rows.add(new Rows.Section("Полёт и подходы").key("a.flight"));
        BooleanSupplier nav = () -> cfg().returnEnabled;
        rows.add(toggle(
                        "Автоопределение полёта",
                        "Читать реальные mayfly/flying в Survival; показывать свежий пакет сервера. Не выдаёт полёт сам",
                        () -> cfg().returnFlyAuto,
                        v -> cfg().returnFlyAuto = v)
                .under(nav));
        rows.add(toggle(
                        "Показывать путь автовозврата",
                        "Только найденный путь общего навигатора; не рисует вымышленную дорогу до окончания поиска",
                        () -> cfg().returnPathVisible,
                        v -> cfg().returnPathVisible = v)
                .under(nav));
        rows.add(new Rows.Note(() -> ReturnWalker.flightStatus(Minecraft.getInstance()), Ui.SUB).under(nav));
        rows.add(toggle(
                        "Есть полёт",
                        "Разрешить воздушный маршрут при реальных mayfly/flying. Ранний взлёт, 3 попытки с подтверждением; "
                                + "Пробел вверх, Shift вниз. У маршрута — подтверждённая посадка; нет разрешения — пешком",
                        () -> cfg().returnFly,
                        v -> cfg().returnFly = v)
                .under(nav));
        rows.add(number(
                        "Полёт не выше",
                        "Над стартом и маршрутом, блоков",
                        () -> cfg().returnFlyCeil,
                        v -> cfg().returnFlyCeil = (int) v,
                        2,
                        64,
                        1,
                        4,
                        v -> (int) v + " бл")
                .integer()
                .under(() -> (cfg().returnEnabled) && (cfg().returnFly || cfg().returnFlyAuto))
                .adv());
        rows.add(number(
                        "Скорость поворота",
                        "Камера при возврате — плавно, как мышью (через чувствительность)",
                        () -> cfg().returnTurnSpeed,
                        v -> cfg().returnTurnSpeed = v,
                        20,
                        720,
                        10,
                        60,
                        v -> Rows.num(v) + "°/с")
                .under(nav)
                .adv());
        rows.add(number(
                        "Перестроить путь",
                        "Лимит поиска заново для возврата/подхода: нет сближения с точкой, заблокирован подъём, сбили или упал. Туда-сюда не считается прогрессом",
                        () -> cfg().returnReplans,
                        v -> cfg().returnReplans = (int) v,
                        0,
                        20,
                        1,
                        5,
                        v -> (int) v + " раз")
                .integer()
                .under(nav)
                .adv());
        rows.add(new Rows.Note(
                () -> cfg().returnEnabled
                        ? "Во время возврата ЛКМ/ПКМ отпущены, слот не меняется; «Аномалия скорости» "
                                + "и «Препятствие впереди» не проверяются. Путь не найден — обычный стоп с сообщением (не паника)."
                        : "",
                Ui.SUB));
    }

    private String routeClipMsg = "";

    private void buildRoutes() {
        var rb = RouteBuffer.INSTANCE;
        rows.add(new Rows.Section("Маршрут по точкам").key("r.cur"));
        rows.add(new RouteBufferCard());
        rows.add(new Rows.Buttons(new Rows.Btn("▶ Начать с места", Rows.Style.PRIMARY, () -> {
                    minecraft.setScreen(null);
                    MacroManager.INSTANCE.startRouteHere(minecraft);
                })
                .enabledIf(() -> rb.size() >= 2 && MacroManager.INSTANCE.getState() == MacroManager.State.IDLE)));
        rows.add(new Rows.Number(
                "Допуск старта с места",
                "Расстояние до ближайшего отрезка; этаж учитывается. Не автовозврат к началу.",
                () -> cfg().resumeAnywhereMaxDist,
                v -> cfg().resumeAnywhereMaxDist = v,
                .5,
                16,
                .5,
                1,
                v -> Rows.num(v) + " бл"));
        rows.add(new Rows.Note(
                "Начать с места — продолжить загруженный маршрут по его направлению. Бинд назначается в «Клавиши».",
                Ui.SUB));
        rows.add(new Rows.Note(
                () -> rb.isEmpty()
                        ? ""
                        : rb.settings().summary(true) + " · ⚙ — настройки маршрута (камера, зажим мыши)",
                Ui.SUB));

        FarmSchemeRows.builder(rows);
        rows.add(new Rows.Section("Подсказка: редактор").key("r.help").closed());
        rows.add(new Rows.Note(
                "Редактор (" + MacroManager.keyName(FarmMacroMod.keyEditor) + "): ЛКМ — точка, зажать — двигать, "
                        + "Shift+ЛКМ — вставить, ПКМ — удалить, Shift+ПКМ — параметры точки, "
                        + "Alt+ЛКМ — выделить ещё, Ctrl+A — все, стрелки — сдвиг выделенных внутри блока (Shift — мелко), "
                        + "Ctrl+D — спуск на этаж ниже, Ctrl+Z — отменить, Ctrl+Y (Ctrl+Shift+Z) — повторить.",
                Ui.SUB));
        rows.add(new Rows.Section("Буфер обмена").key("r.clip").closed());
        rows.add(new Rows.Note(
                "2.1.0: маршрут одной строкой (KAELIX-ROUTE1:…) — отправить другу или перенести между ПК. "
                        + "В строку входят точки и настройки маршрута, без имени файла, мира и сервера. Вставка открывает маршрут "
                        + "в редакторе несохранённым; Ctrl+Z в редакторе вернёт прежний.",
                Ui.SUB));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Копировать маршрут", Rows.Style.SECONDARY, () -> {
                            minecraft.keyboardHandler.setClipboard(RouteCode.encode(rb.route()));
                            routeClipMsg = "Скопировано: " + rb.size() + " точек";
                        })
                        .enabledIf(() -> !rb.isEmpty()),
                new Rows.Btn("Вставить маршрут", Rows.Style.SECONDARY, () -> {
                            try {
                                var r = RouteCode.decode(minecraft.keyboardHandler.getClipboard());
                                rb.importRoute(minecraft, r);
                                routeClipMsg = "Вставлено: " + r.points.size() + " точек — сохрани, если нужен файл";
                            } catch (IllegalArgumentException ex) {
                                routeClipMsg = ex.getMessage();
                            }
                        })
                        .enabledIf(() -> MacroManager.INSTANCE.getState() == MacroManager.State.IDLE)));
        rows.add(new Rows.Note(() -> routeClipMsg, Ui.SUB));
        rows.add(new Rows.Note(
                "Автоход не поворачивает камеру: идёт клавишами W/A/S/D относительно текущего yaw. "
                        + "Поставь yaw вдоль рядов (пресет культуры во вкладке «Камера») — тогда ряды идут чистыми W/S.",
                Ui.SUB));

        RouteCopyRows.build(rows, this, () -> pendingRebuild = true);

        rows.add(new Rows.Buttons(
                new Rows.Btn("Зарандомить", Rows.Style.SECONDARY, () -> {
                    if (MacroManager.INSTANCE.getState() != MacroManager.State.IDLE) return;
                    RouteVariants.clearPreview();
                    RouteVariants.nextPreview();
                    RouteEditor.setActive(minecraft, true);
                    minecraft.setScreen(null);
                }),
                new Rows.Btn("Ещё", Rows.Style.SECONDARY, () -> {
                    if (MacroManager.INSTANCE.getState() != MacroManager.State.IDLE) return;
                    RouteVariants.nextPreview();
                    RouteEditor.setActive(minecraft, true);
                    minecraft.setScreen(null);
                }),
                new Rows.Btn("Сохранить копией", Rows.Style.SECONDARY, () -> {
                            boolean ok = RouteVariants.saveCopy();
                            MacroManager.INSTANCE.msgPublic(
                                    minecraft,
                                    ok
                                            ? "§aВариант сохранён как новый маршрут _rnd"
                                            : "§cНет превью или ошибка сохранения");
                            pendingRebuild = true;
                        })
                        .enabledIf(() -> RouteVariants.preview() != null)));
        rows.add(new Rows.Section("Кэш чанков"));
        rows.add(toggle(
                "Кэш чанков",
                "Поиск видит ранее загруженные области; свежий мир всегда важнее кэша",
                () -> cfg().chunkCacheEnabled,
                v -> cfg().chunkCacheEnabled = v));
        rows.add(number(
                        "Лимит кэша, МБ",
                        "Общий лимит всех серверов/измерений; старые регионы удаляются первыми",
                        () -> cfg().chunkCacheMb,
                        v -> cfg().chunkCacheMb = (int) v,
                        1,
                        4096,
                        1,
                        16,
                        v -> (int) v + " МБ")
                .integer());
        rows.add(new Rows.Buttons(new Rows.Btn("Очистить кэш", Rows.Style.SECONDARY, ChunkCache::clear)));
        rows.add(new Rows.Section("Автоход и редактор").key("r.walk"));
        rows.add(number(
                "Точка достигнута",
                "Радиус по горизонтали",
                () -> cfg().routeReachRadius,
                v -> cfg().routeReachRadius = v,
                0.01,
                2,
                0.05,
                0.2,
                v -> Rows.num(v) + " бл"));
        rows.add(number(
                        "Досягаемость редактора",
                        "Как далеко ставить и выбирать точки",
                        () -> cfg().routeEditReach,
                        v -> cfg().routeEditReach = v,
                        1,
                        128,
                        4,
                        16,
                        v -> Rows.num(v) + " бл")
                .adv());
        rows.add(number(
                        "Шаг сдвига стрелками",
                        "Редактор: выбрать точку → стрелки сдвигают её внутри блока",
                        () -> cfg().routeOffsetStep,
                        v -> cfg().routeOffsetStep = v,
                        0.001,
                        0.5,
                        0.01,
                        0.05,
                        v -> Rows.num(v) + " бл")
                .adv());
        rows.add(number(
                        "Мелкий шаг (Shift)",
                        null,
                        () -> cfg().routeOffsetFineStep,
                        v -> cfg().routeOffsetFineStep = v,
                        0.001,
                        0.5,
                        0.005,
                        0.01,
                        v -> Rows.num(v) + " бл")
                .adv());

        rows.add(new Rows.Section("Сохранённые маршруты").key("r.saved"));
        if (routes.isEmpty()) {
            rows.add(new Rows.Note("Пока пусто. Построй маршрут в редакторе и нажми «Сохранить».", Ui.SUB));
        } else {
            for (var info : routes) rows.add(new RouteCard(info));
        }
        rows.add(new Rows.Spacer(2));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Открыть папку маршрутов", Rows.Style.SECONDARY, () -> openFolder(RouteStorage.DIR))));
    }

    // ── Окно «Настройки макроса» ─────────────────────────────────────────────

    private void openSettings(MacroSettingsScreen.Target t) {
        if (t == null) {
            if (minecraft.player != null)
                minecraft.player.sendOverlayMessage(
                        Component.literal("§c[FM] Не удалось прочитать файл (подробности в логе)"));
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
        var rb = RouteBuffer.INSTANCE;
        var store = RouteStorage.INSTANCE;
        minecraft.setScreen(new SaveMacroScreen(
                this,
                new SaveMacroScreen.Target() {
                    @Override
                    public String title() {
                        return "Сохранить маршрут";
                    }

                    @Override
                    public String subtitle() {
                        return rb.size() + " точек";
                    }

                    @Override
                    public String placeholder() {
                        return "Название, например «Морковь, ферма у дома»";
                    }

                    @Override
                    public boolean exists(String name) {
                        return store.exists(name);
                    }

                    @Override
                    public boolean save(String name) {
                        return store.save(name, rb.route());
                    }

                    @Override
                    public void saved(String name) {
                        rb.markSaved(name);
                    }
                },
                rb.loadedName()));
    }

    /** Карточка текущего маршрута. */
    private final class RouteBufferCard extends Rows.Row {
        @Override
        int height(Rows.Ctx c, int w) {
            return 52;
        }

        private int bw() {
            return (lastW - 16 - 12) / 4;
        }

        @Override
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            var rb = RouteBuffer.INSTANCE;
            MacroManager m = MacroManager.INSTANCE;
            boolean has = !rb.isEmpty();
            boolean selected = m.getSourceKind() == MacroManager.SourceKind.ROUTE;
            Ui.box(g, x, y, w, 49, 5);
            g.fill(x, y + 3, x + 2, y + 46, has ? (selected ? Ui.ON : Ui.ACCENT) : Ui.BORDER);
            String title = !has
                    ? "Пусто"
                    : rb.loadedName() != null
                            ? rb.loadedName() + (rb.isDirty() ? " · изменён" : "")
                            : "Новый маршрут · не сохранён";
            String sub;
            if (!has) sub = "Открой редактор и поставь точки";
            else {
                var p0 = rb.get(0);
                sub = rb.size() + " точек";
                if (rb.route().dimension != null)
                    sub += " · " + rb.route().dimension.replace("minecraft:", "");
                if (minecraft.player != null)
                    sub += String.format(
                            Locale.ROOT,
                            " · до точки 1 %.1f бл",
                            Math.sqrt(minecraft.player.distanceToSqr(p0.x, p0.y, p0.z)));
                if (selected) sub += " · играет по " + MacroManager.keyName(FarmMacroMod.keyPlay);
            }
            Ui.text(g, font, Ui.ellipsize(font, title, w - 40), x + 8, y + 5, Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, sub, w - 40), x + 8, y + 16, Ui.SUB);
            if (drawGear(c, g, x + w - 24, y + 5, has, mx, my)) c.tooltip("Настройки маршрута: камера, зажим мыши");
            int bw = bw(), by = y + 28;
            boolean idle = m.getState() == MacroManager.State.IDLE;
            boolean active = m.isRoutePlaying();
            boolean confirm = Clock.millis() < confirmClearRouteUntil;
            Rows.drawButton(
                    c,
                    g,
                    x + 8,
                    by,
                    bw,
                    16,
                    RouteEditor.isActive() ? "✎ Закрыть" : "✎ Редактор",
                    Rows.Style.PRIMARY,
                    idle,
                    mx,
                    my);
            Rows.drawButton(
                    c,
                    g,
                    x + 12 + bw,
                    by,
                    bw,
                    16,
                    active ? "■ Стоп" : "▶ Запустить",
                    active ? Rows.Style.DANGER : Rows.Style.SUCCESS,
                    has && (idle || active),
                    mx,
                    my);
            Rows.drawButton(c, g, x + 16 + bw * 2, by, bw, 16, "Сохранить", Rows.Style.SECONDARY, has, mx, my);
            Rows.drawButton(
                    c,
                    g,
                    x + 20 + bw * 3,
                    by,
                    bw,
                    16,
                    confirm ? "Точно?" : "Очистить",
                    confirm ? Rows.Style.DANGER : Rows.Style.SECONDARY,
                    has && idle,
                    mx,
                    my);
        }

        @Override
        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            var rb = RouteBuffer.INSTANCE;
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
                boolean open = !RouteEditor.isActive();
                minecraft.setScreen(null);
                RouteEditor.setActive(minecraft, open);
                return true;
            }
            if (has && Ui.inside(mx, my, lastX + 12 + bw, by, bw, 16)) {
                c.clickSound();
                if (m.isRoutePlaying()) {
                    m.stopPlayback(minecraft, "§e■ Остановлено из меню");
                    return true;
                }
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
                if (Clock.millis() < confirmClearRouteUntil) {
                    rb.clear();
                    m.useRecordingSource();
                    confirmClearRouteUntil = 0;
                } else {
                    confirmClearRouteUntil = Clock.millis() + 3000;
                }
                return true;
            }
            return false;
        }
    }

    /** Карточка сохранённого маршрута: ▶, загрузить, удалить с подтверждением. */
    private final class RouteCard extends Rows.Row {
        private final RouteStorage.RouteInfo info;

        RouteCard(RouteStorage.RouteInfo info) {
            this.info = info;
        }

        @Override
        int height(Rows.Ctx c, int w) {
            return 30;
        }

        private int[] buttonsX() {
            int right = lastX + lastW - 6;
            int del = right - 18, load = del - 4 - 62, play = load - 4 - 22, gear = play - 4 - 18;
            return new int[] {play, load, del, gear};
        }

        private String key() {
            return "route:" + info.filename();
        }

        @Override
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            var rb = RouteBuffer.INSTANCE;
            boolean current = info.name().equals(rb.loadedName());
            Ui.listBg(g, x, y, w, 27, 4, false, hover);
            if (current) g.fill(x, y + 3, x + 2, y + 24, Ui.ON);
            lastX = x;
            lastW = w;
            int[] bx = buttonsX();
            int textW = bx[3] - x - 14;
            Ui.text(g, font, Ui.ellipsize(font, "⌁ " + info.name(), textW), x + 8, y + 4, current ? Ui.ON : Ui.TEXT);
            String sub = info.points() + " точек"
                    + (info.dimension() != null ? " · " + info.dimension().replace("minecraft:", "") : "")
                    + (info.world() != null ? " · " + info.world().replace("singleplayer:", "") : "");
            Ui.text(g, font, Ui.ellipsize(font, sub, textW), x + 8, y + 15, Ui.SUB);
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = key().equals(confirmDelete) && Clock.millis() < confirmUntil;
            int by = y + 5;
            if (Rows.drawButton(c, g, bx[0], by, 22, 16, "▶", Rows.Style.SUCCESS, idle, mx, my))
                c.tooltip("Загрузить и запустить");
            if (Rows.drawButton(c, g, bx[3], by, 18, 16, "⚙", Rows.Style.SECONDARY, idle, mx, my))
                c.tooltip("Настройки «" + info.name() + "»: камера, зажим мыши");
            if (confirming) {
                Rows.drawButton(
                        c, g, bx[1], by, bx[2] + 18 - bx[1], 16, "Точно удалить?", Rows.Style.DANGER, true, mx, my);
            } else {
                Rows.drawButton(
                        c,
                        g,
                        bx[1],
                        by,
                        62,
                        16,
                        current ? "Загружен" : "Загрузить",
                        Rows.Style.SECONDARY,
                        idle,
                        mx,
                        my);
                if (Rows.drawButton(c, g, bx[2], by, 18, 16, "✕", Rows.Style.SECONDARY, true, mx, my))
                    c.tooltip("Удалить «" + info.name() + "»");
            }
        }

        @Override
        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int[] bx = buttonsX();
            int by = lastY + 5;
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = key().equals(confirmDelete) && Clock.millis() < confirmUntil;
            if (confirming && Ui.inside(mx, my, bx[1], by, bx[2] + 18 - bx[1], 16)) {
                c.clickSound();
                RouteStorage.INSTANCE.delete(info.filename());
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
                confirmUntil = Clock.millis() + 3000;
                return true;
            }
            if (idle
                    && (Ui.inside(mx, my, bx[0], by, 22, 16)
                            || (!confirming && Ui.inside(mx, my, bx[1], by, 62, 16)))) {
                c.clickSound();
                var r = RouteStorage.INSTANCE.load(info.filename());
                if (r == null) {
                    if (minecraft.player != null)
                        minecraft.player.sendOverlayMessage(
                                Component.literal("§c[FM] Не удалось прочитать маршрут (подробности в логе)"));
                    return true;
                }
                var rb = RouteBuffer.INSTANCE;
                if (rb.isDirty() && !rb.isEmpty() && minecraft.player != null)
                    minecraft.player.sendOverlayMessage(
                            Component.literal("§8[§9Kaelix§8] §6Несохранённый маршрут заменён"));
                rb.load(info.name(), r);
                if (minecraft.player != null)
                    minecraft.player.sendOverlayMessage(Component.literal(
                            "§8[§9Kaelix§8] §aЗагружен маршрут «" + info.name() + "»: " + r.points.size() + " точек"));
                if (mx < bx[1]) startMacroAndClose();
                return true;
            }
            return false;
        }
    }

    String friendMsg = "";

    /** v1.14: дописать в друзья всех игроков из Tab в 32 блоках. */
    void addNearbyFriends() {
        var mc = minecraft;
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) {
            friendMsg = "Нужно быть в мире";
            return;
        }
        String list = cfg().friendNames;
        int added = 0;
        for (var o : mc.level.players()) {
            if (o == mc.player || o.distanceTo(mc.player) > 32) continue;
            if (mc.getConnection().getPlayerInfo(o.getUUID()) == null) continue;
            String name = o.getGameProfile().name();
            if (!PlayerRules.validName(name)) continue;
            String next = PlayerRules.addName(list, name);
            if (!next.equals(list)) {
                list = next;
                added++;
            }
        }
        cfg().friendNames = list;
        save();
        friendMsg = added == 0 ? "Новых игроков рядом нет" : "Добавлено: " + added;
    }

    void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
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
        @Override
        int height(Rows.Ctx c, int w) {
            return 52;
        }

        @Override
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            MacroManager m = MacroManager.INSTANCE;
            Ui.box(g, x, y, w, 49, 5);
            g.fill(x, y + 3, x + 2, y + 46, m.getFrameCount() > 0 ? Ui.ACCENT : Ui.BORDER);
            String title, sub;
            if (m.getFrameCount() == 0) {
                title = "Пусто";
                sub = "Запиши или загрузи макрос из списка ниже";
            } else {
                title = m.getLoadedName() != null ? m.getLoadedName() : "Новая запись · не сохранена";
                sub = MacroManager.formatTicks(m.getFrameCount()) + " · " + m.getFrameCount() + " кадров";
                if (minecraft != null && minecraft.player != null)
                    sub += String.format(Locale.ROOT, " · до старта %.1f бл", m.distanceToFrame(minecraft.player, 0));
            }
            Ui.text(g, font, Ui.ellipsize(font, title, w - 40), x + 8, y + 5, Ui.TEXT);
            Ui.text(g, font, Ui.ellipsize(font, sub, w - 40), x + 8, y + 16, Ui.SUB);
            int bw = (w - 16 - 8) / 3, by = y + 28;
            boolean has = m.getFrameCount() > 0;
            if (drawGear(c, g, x + w - 24, y + 5, has && !m.isRecording(), mx, my))
                c.tooltip("Настройки макроса: камера, зажим мыши");
            boolean active = m.isActive();
            Rows.drawButton(
                    c,
                    g,
                    x + 8,
                    by,
                    bw,
                    16,
                    active ? "■ Стоп" : "▶ Запустить",
                    active ? Rows.Style.DANGER : Rows.Style.SUCCESS,
                    has && !m.isRecording(),
                    mx,
                    my);
            Rows.drawButton(
                    c, g, x + 12 + bw, by, bw, 16, "Сохранить", Rows.Style.PRIMARY, has && !m.isRecording(), mx, my);
            Rows.drawButton(
                    c,
                    g,
                    x + 16 + bw * 2,
                    by,
                    bw,
                    16,
                    "Очистить",
                    Rows.Style.SECONDARY,
                    has && !active && !m.isRecording(),
                    mx,
                    my);
        }

        @Override
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
                minecraft.setScreen(
                        new SaveMacroScreen(FarmMacroScreen.this, new ArrayList<>(m.getFrames()), m.getLoadedName()));
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

        MacroCard(MacroStorage.MacroInfo info) {
            this.info = info;
        }

        @Override
        int height(Rows.Ctx c, int w) {
            return 30;
        }

        private int[] buttonsX() { // ▶, Загрузить, ✕, ⚙
            int right = lastX + lastW - 6;
            int del = right - 18, load = del - 4 - 62, play = load - 4 - 22, gear = play - 4 - 18;
            return new int[] {play, load, del, gear};
        }

        @Override
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean current = info.name().equals(MacroManager.INSTANCE.getLoadedName());
            Ui.listBg(g, x, y, w, 27, 4, false, hover);
            if (current) g.fill(x, y + 3, x + 2, y + 24, Ui.ON);
            lastX = x;
            lastW = w;
            int[] bx = buttonsX();
            int textW = bx[3] - x - 14;
            Ui.text(g, font, Ui.ellipsize(font, info.name(), textW), x + 8, y + 4, current ? Ui.ON : Ui.TEXT);
            Ui.text(
                    g,
                    font,
                    Ui.ellipsize(
                            font,
                            MacroManager.formatTicks(info.frameCount()) + " · " + info.frameCount() + " кадров",
                            textW),
                    x + 8,
                    y + 15,
                    Ui.SUB);
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = info.filename().equals(confirmDelete) && Clock.millis() < confirmUntil;
            int by = y + 5;
            if (Rows.drawButton(c, g, bx[0], by, 22, 16, "▶", Rows.Style.SUCCESS, idle, mx, my))
                c.tooltip("Загрузить и запустить");
            if (Rows.drawButton(c, g, bx[3], by, 18, 16, "⚙", Rows.Style.SECONDARY, idle, mx, my))
                c.tooltip("Настройки «" + info.name() + "»: камера, зажим мыши");
            if (confirming) {
                Rows.drawButton(
                        c, g, bx[1], by, bx[2] + 18 - bx[1], 16, "Точно удалить?", Rows.Style.DANGER, true, mx, my);
            } else {
                Rows.drawButton(
                        c,
                        g,
                        bx[1],
                        by,
                        62,
                        16,
                        current ? "Загружен" : "Загрузить",
                        Rows.Style.SECONDARY,
                        idle,
                        mx,
                        my);
                if (Rows.drawButton(c, g, bx[2], by, 18, 16, "✕", Rows.Style.SECONDARY, true, mx, my))
                    c.tooltip("Удалить «" + info.name() + "»");
            }
        }

        @Override
        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int[] bx = buttonsX();
            int by = lastY + 5;
            boolean idle = MacroManager.INSTANCE.getState() == MacroManager.State.IDLE;
            boolean confirming = info.filename().equals(confirmDelete) && Clock.millis() < confirmUntil;
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
                confirmUntil = Clock.millis() + 3000;
                return true;
            }
            if (idle
                    && (Ui.inside(mx, my, bx[0], by, 22, 16)
                            || (!confirming && Ui.inside(mx, my, bx[1], by, 62, 16)))) {
                c.clickSound();
                var saved = MacroStorage.INSTANCE.load(info.filename());
                if (saved == null) {
                    minecraft.player.sendOverlayMessage(
                            Component.literal("§c[FM] Не удалось прочитать файл макроса (подробности в логе)"));
                    return true;
                }
                if (MacroManager.INSTANCE.loadMacro(info.name(), saved.frames, saved.settings, minecraft) && mx < bx[1])
                    startMacroAndClose();
                return true;
            }
            return false;
        }
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.sync(cfg().uiTheme);
        super.extractBackground(g, mx, my, delta);
        drawWindow(g);
    }

    /** Подложка окна (отдельно от фона мира, чтобы её можно было рисовать в превью-стенде). */
    void drawWindow(GuiGraphicsExtractor g) {
        int x = winX(), y = winY(), w = winW(), h = winH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.window(g, x, y, w, h, 8);
        // боковая панель
        Ui.round(g, x + 1, y + 1, sideW() + 7, h - 2, 8, Ui.SIDEBAR);
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
        if (moduleHome) {
            renderModuleSidebar(g, x, y, mx, my);
            renderModuleHeader(g, x, y, w, mx, my);
        } else {
            renderSidebar(g, x, y, mx, my);
            renderHeader(g, x, y, w, mx, my);
        }

        // содержимое с прокруткой
        int cx = contX(), cy = contY(), cw = contW(), ch = contH();
        int total = contentHeight(cw);
        double maxScroll = Math.max(0, total - ch);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 16);
        if (Math.abs(scrollTarget - scroll) < 0.3) scroll = scrollTarget;

        boolean inContent = Ui.inside(mx, my, cx, cy, cw, ch);
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int top = cy - (int) Math.round(scroll);
        for (Rows.Row r : rows) {
            int rh = r.h(this, r.lw);
            int rx = cx + r.lx, ry = top + r.ly;
            r.lastX = rx;
            r.lastY = ry;
            r.lastW = r.lw;
            r.lastH = rh;
            if (rh > 0 && ry + rh > cy && ry < cy + ch) {
                boolean hover = inContent && my >= ry && my < ry + rh && mx >= rx && mx < rx + r.lw;
                r.render(this, g, rx, ry, r.lw, inContent ? mx : -1, inContent ? my : -1, hover);
            }
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
            if (scroll > 1)
                g.fillGradient(cx - 2, cy, cx + cw + 2, cy + 8, Ui.alpha(Ui.WINDOW, 0.75f), Ui.alpha(Ui.WINDOW, 0f));
            if (scroll < maxScroll - 1)
                g.fillGradient(
                        cx - 2, cy + ch - 8, cx + cw + 2, cy + ch, Ui.alpha(Ui.WINDOW, 0f), Ui.alpha(Ui.WINDOW, 0.75f));
        }

        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        if (tooltip != null) drawTooltip(g, tooltip, mx, my);
    }

    private int contentHeight(int cw) {
        if (moduleHome) {
            int y = 0, index = 0, cardTop = -1;
            for (var row : rows) {
                if (row instanceof ModuleCardRow) {
                    if (cardTop < 0) cardTop = y + 4;
                    var box = MenuLayout.card(index++, cw, cardTop);
                    row.lx = box.x();
                    row.ly = box.y();
                    row.lw = box.w();
                    y = Math.max(y, box.y() + box.h());
                } else {
                    row.lx = 0;
                    row.ly = y;
                    row.lw = cw;
                    y += row.h(this, cw);
                }
            }
            return y + 4;
        }
        return layout(cw) + 4;
    }

    /** v1.16.0: колонки — в минималистичных темах при ширине от 440 разделы из простых строк идут в две колонки. */
    static final int COL_GAP = 18;

    private static boolean simple(Rows.Row r) {
        return r instanceof Rows.Section
                || r instanceof Rows.Toggle
                || r instanceof Rows.Number
                || r instanceof Rows.Range
                || r instanceof Rows.Selector
                || r instanceof Rows.TextField
                || r instanceof Rows.Note
                || r instanceof Rows.Spacer
                || r instanceof Rows.Buttons;
    }

    /**
     * Раскладка строк: блок = раздел (Section и строки до следующего Section/End). Блок из простых строк —
     * в более короткую колонку, остальное (таблицы, карточки макросов, поиск) — на всю ширину ниже обеих колонок.
     * Заполняет Row.lx/ly/lw. @return высота содержимого
     */
    private int layout(int cw) {
        boolean two = moduleTitle == null && cw >= 520;
        int colW = (cw - COL_GAP) / 2;
        int[] colY = {0, 0};
        int i = 0, n = rows.size();
        while (i < n) {
            int j = i + 1;
            if (rows.get(i) instanceof Rows.Section)
                while (j < n && !(rows.get(j) instanceof Rows.Section) && !(rows.get(j) instanceof Rows.End)) j++;
            else while (j < n && !(rows.get(j) instanceof Rows.Section)) j++;
            boolean half = two && rows.get(i) instanceof Rows.Section;
            for (int k = i; half && k < j; k++) if (!simple(rows.get(k))) half = false;
            if (half) {
                int c = colY[0] <= colY[1] ? 0 : 1;
                for (int k = i; k < j; k++) {
                    Rows.Row r = rows.get(k);
                    r.lx = c * (colW + COL_GAP);
                    r.lw = colW;
                    r.ly = colY[c];
                    colY[c] += r.h(this, colW);
                }
            } else {
                int y = Math.max(colY[0], colY[1]);
                for (int k = i; k < j; k++) {
                    Rows.Row r = rows.get(k);
                    r.lx = 0;
                    r.lw = cw;
                    r.ly = y;
                    y += r.h(this, cw);
                }
                colY[0] = colY[1] = y;
            }
            i = j;
        }
        return Math.max(colY[0], colY[1]);
    }

    private int modeX, modeW;

    /** Текст и цвет статуса в заголовке. */
    private int statusOf(String[] out) {
        MacroManager m = MacroManager.INSTANCE;
        String status;
        int col;
        switch (m.getState()) {
            case RECORDING -> {
                status = "● ЗАПИСЬ " + MacroManager.formatTicks(m.getFrameCount());
                col = Ui.DANGER;
            }
            case COUNTDOWN -> {
                status = m.getCountdownTicks() > 0
                        ? "◷ СТАРТ ЧЕРЕЗ " + ((m.getCountdownTicks() + 19) / 20)
                        : "◎ ПОВОРОТ КАМЕРЫ";
                col = Ui.WARN;
            }
            case PLAYING -> {
                status = (m.isRoutePlaying() ? "▶ точка " + (m.getProgress() + 1) : "▶ " + m.getProgress()) + "/"
                        + m.getProgressTotal() + (cfg().loopEnabled ? " · круг " + (m.getLoopsDone() + 1) : "");
                col = Ui.ON;
            }
            default -> {
                boolean ready = m.getStartPosition() != null;
                status = ready
                        ? (m.getSourceKind() == MacroManager.SourceKind.ROUTE ? "■ ГОТОВ · МАРШРУТ" : "■ ГОТОВ")
                        : "■ НЕТ МАКРОСА";
                col = ready ? Ui.SUB : Ui.DIM;
            }
        }
        out[0] = status;
        return col;
    }

    private void renderHeader(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        String[] fs = new String[1];
        int fc = statusOf(fs);
        renderHeaderFlat(g, x, y, w, mx, my, fs[0], fc);
    }

    /** v1.16.0 минималистичный заголовок: крупное название вкладки, режим тонким текстом, статус точкой. */
    private void renderHeaderFlat(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, String status, int col) {
        int hx = contX(), bx = geometry().close().x();
        boolean adv = cfg().uiAdvanced;
        String mode = adv ? "Расширенные" : "Основные";
        if (contW() < 300) mode = adv ? "Расш." : "Прост.";
        modeW = font.width(mode) + 14;
        modeX = bx - modeW - 8;
        int titleWidth = Math.max(10, modeX - hx - 8);
        Ui.heading(g, font, title(), hx, y + 10, titleWidth);
        Ui.text(
                g,
                font,
                Ui.ellipsize(font, status.replaceFirst("^[■▶●◷◎] ?", ""), contW() - 26),
                hx,
                y + 31,
                col == Ui.DANGER || col == Ui.WARN ? col : Ui.SUB);
        boolean hp = Ui.inside(mx, my, modeX, y + 12, modeW, 20);
        Ui.roundBordered(g, modeX, y + 12, modeW, 20, 5, hp ? Ui.CARD_HOVER : Ui.CARD, Ui.BORDER);
        Ui.textCentered(g, font, mode, modeX + modeW / 2, y + 18, adv ? Ui.ACCENT_HI : Ui.SUB);
        if (hp) {
            hand();
            tooltip("Основные / расширенные параметры только этого модуля; значения не сбрасываются.");
        }
        renderClose(g, mx, my);
        g.fill(hx, y + HEADER_H, x + w - 12, y + HEADER_H + 1, Ui.BORDER);
    }

    private void renderClose(GuiGraphicsExtractor g, int mx, int my) {
        var box = geometry().close();
        boolean hover = box.contains(mx, my);
        if (hover) {
            Ui.round(g, box.x(), box.y(), box.w(), box.h(), 4, Ui.CARD_HOVER);
            hand();
            tooltip("Закрыть / назад · Esc");
        }
        Ui.textCentered(g, font, "×", box.x() + 10, box.y() + 6, hover ? Ui.DANGER : Ui.SUB);
    }

    /** v1.16.0 минималистичная боковая панель: логотип, вкладки текстом с тонкой полоской, внизу клавиши, ник и версия. */
    private void renderSidebarFlat(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Logo.draw(g, x + 10, y + 5, 20);
        Logo.word(g, font, "KAELIX", x + 33, y + 11, 2);
        int ty = y + HEADER_H + 6;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + 6, tw = sideW() - 12, th = tabStride() - 3;
            boolean active = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, tw, th);
            if (active) g.fill(tx, ty + Math.min(4, th / 3), tx + 2, ty + th - Math.min(4, th / 3), Ui.ACCENT);
            Ui.text(
                    g,
                    font,
                    Ui.ellipsize(font, TABS[i], tw - 16),
                    tx + 10,
                    ty + Math.max(0, (th - 8) / 2),
                    active ? Ui.TEXT : hover ? Ui.TEXT : Ui.SUB);
            if (hover && !active) hand();
            ty += tabStride();
        }
        String nick = minecraft != null && minecraft.getUser() != null
                ? minecraft.getUser().getName()
                : "";
        int by = y + winH() - 30;
        int ky = by - 6 - 3 * 11;
        if (ky > ty) {
            keyHint(g, x + 10, ky + 11, "Старт/стоп", FarmMacroMod.keyPlay);
            keyHint(g, x + 10, ky + 22, "Продолжить", FarmMacroMod.keyResume);
        }
        if (by > ty) {
            Ui.text(g, font, Ui.ellipsize(font, nick, sideW() - 20), x + 10, by, Ui.SUB);
            Ui.text(g, font, "v" + VERSION, x + 10, by + 11, Ui.DIM);
        }
    }

    private void renderModuleHeader(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        modeW = 0;
        String label = category == 0 ? "Модули" : ModuleCatalog.category(category);
        Ui.heading(g, font, label, contX(), y + 10, contW() - 30);
        Ui.text(
                g,
                font,
                Ui.ellipsize(font, "Найдено: " + visibleModules.size() + " · запуск отдельно", contW() - 24),
                contX(),
                y + 31,
                Ui.SUB);
        renderClose(g, mx, my);
        g.fill(contX(), y + HEADER_H, x + w - 12, y + HEADER_H + 1, Ui.BORDER);
    }

    private void renderModuleSidebar(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        boolean compact = sideW() < 60;
        Logo.draw(g, x + 10, y + 12, 20);
        if (!compact) {
            Logo.word(g, font, "KAELIX", x + 38, y + 16, 1);
            Ui.text(g, font, "WORKSPACE", x + 38, y + 30, Ui.DIM);
        }
        for (int i = 0; i < ModuleCatalog.CATEGORIES.length; i++) {
            var box = geometry().category(i);
            boolean hover = box.contains(mx, my), active = i == category;
            if (active || hover)
                Ui.round(
                        g,
                        box.x(),
                        box.y(),
                        box.w(),
                        box.h(),
                        5,
                        active ? Ui.lerp(Ui.SIDEBAR, Ui.ACCENT, .13f) : Ui.CARD_HOVER);
            int ix = compact ? box.x() + (box.w() - 16) / 2 : box.x() + 8, iy = box.y() + (box.h() - 16) / 2;
            if (i == 0) {
                for (int yy = 0; yy < 2; yy++)
                    for (int xx = 0; xx < 2; xx++)
                        Ui.round(g, ix + xx * 8, iy + yy * 8, 6, 6, 1, active ? Ui.ACCENT_HI : Ui.SUB);
            } else
                ModuleGlyphs.draw(
                        g,
                        switch (i) {
                            case 1 -> 0;
                            case 2 -> 4;
                            case 3 -> 8;
                            default -> 16;
                        },
                        ix,
                        iy,
                        16);
            if (!compact)
                Ui.text(
                        g,
                        font,
                        Ui.ellipsize(font, ModuleCatalog.category(i), box.w() - 34),
                        box.x() + 31,
                        box.y() + (box.h() - 8) / 2,
                        active ? Ui.TEXT : Ui.SUB);
            if (hover) {
                hand();
                tooltip(ModuleCatalog.category(i) + (moduleHome ? "" : " · вернуться к каталогу"));
            }
        }
        var last = geometry().category(4);
        if (!compact && last.y() + last.h() + 30 < y + winH()) {
            int fy = y + winH() - 32;
            g.fill(x + 12, fy - 8, x + sideW() - 12, fy - 7, Ui.BORDER);
            Ui.text(g, font, "Запуск — вручную", x + 12, fy, Ui.SUB);
            Ui.text(g, font, Ui.ellipsize(font, "v" + VERSION, sideW() - 24), x + 12, fy + 13, Ui.DIM);
        }
    }

    private static final String VERSION = FabricLoader.getInstance()
            .getModContainer("farmmacro")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("?");

    private void renderSidebar(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        if (moduleTitle != null) {
            renderModuleSidebar(g, x, y, mx, my);
            return;
        }
        if (sideW() >= 60) {
            renderSidebarFlat(g, x, y, mx, my);
            return;
        }
        // Узкое окно — только иконки вкладок (широкое рисует renderSidebarFlat).
        Logo.draw(g, x + sideW() / 2 - 9, y + 6, 18);
        int ty = y + HEADER_H + 6;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + 6, tw = sideW() - 12, th = tabStride() - 3;
            boolean active = i == tab;
            boolean hover = Ui.inside(mx, my, tx, ty, tw, th);
            if (active) Ui.round(g, tx, ty, tw, th, 5, Ui.alpha(Ui.ACCENT, 0.07f));
            else if (hover) Ui.round(g, tx, ty, tw, th, 5, Ui.CARD);
            if (active)
                Ui.pill(g, tx, ty + Math.min(5, th / 3), 2, Math.max(1, th - 2 * Math.min(5, th / 3)), Ui.ACCENT);
            int col = active ? Ui.TEXT : hover ? Ui.TEXT : Ui.SUB;
            Ui.textCentered(
                    g, font, TAB_ICONS[i], tx + tw / 2, ty + Math.max(0, (th - 8) / 2), active ? Ui.ACCENT_HI : col);
            if (hover) tooltip(TABS[i]);
            if (hover && !active) hand();
            ty += tabStride();
        }
    }

    private void keyHint(GuiGraphicsExtractor g, int x, int y, String what, KeyMapping key) {
        String k = MacroManager.keyName(key);
        int kw = font.width(k) + 6;
        int right = x + sideW() - 20;
        Ui.text(g, font, Ui.ellipsize(font, what, right - kw - x - 4), x, y + 1, Ui.DIM);
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
        Ui.roundBordered(g, tx, ty, tw + 12, th + 2, 4, Ui.TOOLTIP, Ui.BORDER);
        int ly = ty + 5;
        for (var l : lines) {
            g.text(font, l, tx + 6, ly, Ui.TEXT, false);
            ly += 10;
        }
    }

    // ── Rows.Ctx ─────────────────────────────────────────────────────────────

    @Override
    public Font font() {
        return font;
    }

    @Override
    public void clickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void tooltip(String text) {
        tooltip = text;
    }

    @Override
    public void hand() {
        wantHand = true;
    }

    @Override
    public boolean shift() {
        return minecraft.hasShiftDown();
    }

    // ── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        Rows.clearWheelFocus(); // фокус колёсика снова поставит строка, по полю которой кликнули
        Rows.Row typing = Rows.focusedRow();
        if (typing != null
                && !(my >= typing.lastY
                        && my < typing.lastY + typing.lastH
                        && mx >= typing.lastX
                        && mx < typing.lastX + typing.lastW)) Rows.blurText();
        int x = winX(), y = winY(), w = winW();
        // закрыть
        if (e.button() == 0 && geometry().close().contains(mx, my)) {
            clickSound();
            onClose();
            return true;
        }
        if (e.button() == 0 && modeW > 0 && Ui.inside(mx, my, modeX, y + 12, modeW, 20)) {
            clickSound();
            toggleAdvanced();
            return true;
        }
        // v1.15.1: заголовок — тащить окно; двойной клик — обратно в центр
        if (e.button() == 0
                && (Ui.inside(mx, my, x, y, w - 28, HEADER_H)
                        || Ui.inside(mx, my, x, y, w, winH()) && !Ui.inside(mx, my, x + 4, y + 4, w - 8, winH() - 8))) {
            long now = Clock.millis();
            if (now - lastHeaderClick < 350) {
                cfg().guiOffsetX = 0;
                cfg().guiOffsetY = 0;
                save();
                lastHeaderClick = 0;
                return true;
            }
            lastHeaderClick = now;
            draggingWindow = true;
            dragFromX = mx;
            dragFromY = my;
            dragOffX = winX() - (width - winW()) / 2;
            dragOffY = winY() - (height - winH()) / 2;
            return true;
        }
        if (moduleHome || moduleTitle != null) {
            for (int i = 0; i < ModuleCatalog.CATEGORIES.length; i++)
                if (geometry().category(i).contains(mx, my)) {
                    if (e.button() == 0) {
                        clickSound();
                        browseCategory(i);
                    }
                    return true;
                }
        }
        // вкладки
        int ty = y + HEADER_H + 6;
        for (int i = 0; !moduleHome && moduleTitle == null && i < TABS.length; i++) {
            if (Ui.inside(mx, my, x + 6, ty, sideW() - 12, tabStride() - 3)) {
                if (i != tab) clickSound();
                switchTab(i);
                return true;
            }
            ty += tabStride();
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
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH && mx >= r.lastX && mx < r.lastX + r.lastW) {
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
        if (draggingScrollbar) {
            dragScroll(e.y());
            return true;
        }
        if (Rows.drag(this, e.x())) return true;
        if (draggingWindow) {
            int ox = dragOffX + (int) Math.round(e.x() - dragFromX),
                    oy = dragOffY + (int) Math.round(e.y() - dragFromY);
            int maxX = (width - winW() + 1) / 2, maxY = (height - winH() + 1) / 2;
            cfg().guiOffsetX = Math.max(-maxX, Math.min(maxX, ox));
            cfg().guiOffsetY = Math.max(-maxY, Math.min(maxY, oy));
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingScrollbar = false;
        Rows.dragEnd();
        if (draggingWindow) {
            draggingWindow = false;
            save();
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int cx = contX(), cy = contY(), cw = contW(), ch = contH();
        if (Ui.inside(mx, my, cx, cy, cw, ch)) {
            for (Rows.Row r : rows) {
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH && mx >= r.lastX && mx < r.lastX + r.lastW) {
                    if (r.scroll(this, mx, my, sy)) return true;
                    break;
                }
            }
            Rows.clearWheelFocus(); // список поехал — поле под курсором уже другое
            scrollTarget -= sy * 24;
            return true;
        }
        if (moduleTitle != null && Ui.inside(mx, my, winX(), winY(), sideW(), winH())) return true;
        // колесо над вкладками — листать вкладки
        if (Ui.inside(mx, my, winX(), winY(), sideW(), winH())) {
            if (moduleHome)
                switchCategory(Math.floorMod(category + (sy > 0 ? -1 : 1), ModuleCatalog.CATEGORIES.length));
            else switchTab(Math.floorMod(tab + (sy > 0 ? -1 : 1), TABS.length));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (Rows.textFocused() && Rows.textKey(e)) return true; // печатаем в поле — клавиши меню не работают
        if (FarmMacroMod.keyOpenGui != null && FarmMacroMod.keyOpenGui.matches(e)) {
            onClose();
            return true;
        }
        if (moduleHome && !visibleModules.isEmpty()) {
            if (e.key() >= 262 && e.key() <= 265) {
                moduleSelection = MenuLayout.nextSelection(
                        moduleSelection, visibleModules.size(), MenuLayout.columns(contW()), e.key());
                contentHeight(contW());
                int index = 0;
                for (var row : rows)
                    if (row instanceof ModuleCardRow) {
                        if (index++ == moduleSelection) {
                            scrollTarget = Math.max(0, row.ly - Math.max(0, contH() - MenuLayout.CARD_HEIGHT) / 2.0);
                            break;
                        }
                    }
                return true;
            }
            if (e.key() == 257 || e.key() == 335) {
                openModule(visibleModules.get(moduleSelection));
                return true;
            }
        }
        if (e.key() == 258 && moduleTitle == null) { // Tab — следующая вкладка (Shift+Tab — предыдущая)
            if (moduleHome)
                switchCategory(Math.floorMod(category + (e.hasShiftDown() ? -1 : 1), ModuleCatalog.CATEGORIES.length));
            else switchTab(Math.floorMod(tab + (e.hasShiftDown() ? -1 : 1), TABS.length));
            return true;
        }
        if (e.key() == 266) {
            scrollTarget -= contH() * 0.8;
            return true;
        } // PageUp
        if (e.key() == 267) {
            scrollTarget += contH() * 0.8;
            return true;
        } // PageDown
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (Rows.textChar(e)) return true;
        return super.charTyped(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

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
