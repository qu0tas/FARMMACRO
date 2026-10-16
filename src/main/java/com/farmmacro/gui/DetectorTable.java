package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.Detectors;
import com.farmmacro.panic.Detectors.Action;
import com.farmmacro.panic.Detectors.Def;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * v1.14.2-pre «Паника → Все детекторы»: сводная таблица всех проверок из реестра {@link Detectors}.
 * Отдельный модуль: чтобы убрать таблицу, удалить этот файл и строку {@code DetectorTable.addTo(rows, this)}
 * в {@code FarmMacroScreen.buildPanic}. Старые группы меню не трогает — те же поля настроек.
 *
 * Столбцы: Вкл · Детектор · Действие ▾ · Звук ▾ · Тест ▶ · Сраб. · ⚙ (пороги).
 */
final class DetectorTable {
    private DetectorTable() {}

    /** Состояние таблицы живёт между перестройками меню (в пределах сессии игры). */
    private static String query = "";
    private static String filter = "all";
    private static final Set<String> gearOpen = new HashSet<>(), logOpen = new HashSet<>();

    private static final List<Rows.Choice> FILTERS = List.of(
            new Rows.Choice("all", "Все"), new Rows.Choice("on", "Включённые"),
            new Rows.Choice("off", "Выключенные"), new Rows.Choice("fired", "Сработали за сессию"));

    private static ModConfig cfg() { return ModConfig.INSTANCE; }
    private static void save() { ModConfig.save(); }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е').strip(); }

    /** Подходит ли детектор под поиск и фильтр таблицы. */
    static boolean visible(Def d) {
        switch (filter) {
            case "on" -> { if (!d.enabled()) return false; }
            case "off" -> { if (d.enabled()) return false; }
            case "fired" -> { if (Detectors.count(d.id()) == 0) return false; }
            default -> { }
        }
        String q = norm(query);
        if (q.isEmpty()) return true;
        String t = norm(d.name() + " " + d.hint() + " " + d.group() + " " + d.id());
        for (String w : q.split("\\s+")) if (!t.contains(w)) return false;
        return true;
    }

    private static boolean anyVisible(String group) {
        for (Def d : Detectors.all()) if (d.group().equals(group) && visible(d)) return true;
        return false;
    }

    /** Добавить раздел «Все детекторы» (свёрнут по умолчанию). */
    static void addTo(List<Rows.Row> rows, FarmMacroScreen screen) {
        rows.add(new Rows.Section("Все детекторы").key("p.table").closed());
        rows.add(new Rows.Note("Все проверки в одной таблице. Действие (клик — дальше, ПКМ — назад): паника, очки подозрительности, "
                + "только журнал или выкл. ▶ — показать экран и звук без остановки. «Сраб.» — за эту сессию, клик — последние записи. "
                + "⚙ — пороги и свой звук.", Ui.SUB));
        rows.add(new SearchRow());
        rows.add(new Rows.Selector("Показать", null, () -> FILTERS, () -> filter, v -> filter = v));
        rows.add(new HeaderRow());
        for (String g : Detectors.GROUPS) {
            rows.add(new GroupRow(g));
            for (Def d : Detectors.all()) {
                if (!d.group().equals(g)) continue;
                rows.add(new DetectorRow(d, screen));
                rows.add(new LogRow(d));
                for (Rows.Row r : gear(d)) {
                    BooleanSupplier was = r.visible;
                    r.showIf(() -> gearOpen.contains(d.id()) && visible(d) && was.getAsBoolean());
                    rows.add(r);
                }
            }
        }
        rows.add(new Rows.Spacer(4));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Включить все", Rows.Style.SECONDARY, () -> { Detectors.setAll(true); save(); })
                        .tip("Кроме тех, кому нужны свои данные или которые часто ложно срабатывают: BPS, банвейв, Jacob, сход с маршрута, откат назад"),
                new Rows.Btn("Выключить все", Rows.Style.DANGER, () -> { Detectors.setAll(false); save(); })
                        .tip("Все детекторы выкл (главный выключатель «Паника включена» не трогает)"),
                new Rows.Btn("Сбросить счётчики", Rows.Style.SECONDARY, Detectors::resetStats)
                        .tip("Обнулить «Сраб.» и последние записи этой сессии")));
        rows.add(new Rows.Note(() -> "Включено " + Detectors.enabledCount() + " из " + Detectors.all().size()
                + " · сработок за сессию: " + Detectors.totalCount()
                + (cfg().panicEnabled ? "" : " · ⚠ паника выключена главным выключателем"), Ui.ACCENT));
    }

    // ── Пороги (⚙) ──────────────────────────────────────────────────────────

    private static Rows.Number num(String label, String hint, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, double big) {
        return new Rows.Number(label, hint, get, v -> { set.accept(v); save(); }, min, max, step, big, Rows::num);
    }

    private static Rows.Number inum(String label, String hint, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, double big) {
        return num(label, hint, get, set, min, max, step, big).integer();
    }

    private static Rows.Toggle tog(String label, String hint, BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new Rows.Toggle(label, hint, get, v -> { set.accept(v); save(); });
    }

    /** Пороги детектора + вес (для «Подозрительности») + свой звук. Диапазоны — как в ModConfig.sanitize. */
    private static List<Rows.Row> gear(Def d) {
        ModConfig c = cfg();
        List<Rows.Row> l = new ArrayList<>();
        switch (d.id()) {
            case "rotation" -> {
                l.add(num("Порог yaw, °", null, () -> cfg().yawThreshold, v -> cfg().yawThreshold = (float) v, 0.001, 180, 0.5, 5));
                l.add(num("Порог pitch, °", null, () -> cfg().pitchThreshold, v -> cfg().pitchThreshold = (float) v, 0.001, 180, 0.5, 5));
            }
            case "serverMove" -> {
                l.add(num("Сдвиг, бл", "Меньше — не паника", () -> cfg().serverMoveThreshold, v -> cfg().serverMoveThreshold = v, 0.001, 1024, 0.1, 1));
                l.add(num("Поворот, °", null, () -> cfg().serverRotateThreshold, v -> cfg().serverRotateThreshold = (float) v, 0.001, 180, 0.5, 5));
            }
            case "serverSync" -> {
                l.add(num("Позиция, бл", null, () -> cfg().serverPosEpsilon, v -> cfg().serverPosEpsilon = v, 0.0001, 16, 0.01, 0.1));
                l.add(num("Взгляд, °", null, () -> cfg().serverRotEpsilon, v -> cfg().serverRotEpsilon = (float) v, 0.0001, 180, 0.01, 0.5));
            }
            case "knockback" -> l.add(num("Скорость, бл/т", null, () -> cfg().knockbackThreshold, v -> cfg().knockbackThreshold = v, 0.001, 10, 0.01, 0.1));
            case "stuck" -> l.add(inum("Окно, тиков", "20 тиков = 1 с", () -> cfg().stuckThresholdTicks, v -> cfg().stuckThresholdTicks = (int) v, 1, 12000, 1, 10));
            case "drift" -> l.add(num("Отклонение, бл", null, () -> cfg().driftThreshold, v -> cfg().driftThreshold = v, 0.01, 1024, 0.5, 2));
            case "velocity" -> l.add(num("Порог, бл/т", null, () -> cfg().velocityAnomaly, v -> cfg().velocityAnomaly = v, 0.01, 5, 0.01, 0.1));
            case "moveAttrs" -> l.add(num("Изменение, %", null, () -> cfg().attrEpsilon, v -> cfg().attrEpsilon = v, 0.01, 100, 0.5, 5));
            case "blockNear" -> l.add(num("Радиус, бл", null, () -> cfg().blockDetectRadius, v -> cfg().blockDetectRadius = v, 0, 8, 0.05, 0.5));
            case "obstacle" -> l.add(num("Дальность, бл", null, () -> cfg().obstacleRange, v -> cfg().obstacleRange = v, 1, 16, 0.5, 2));
            case "sticky" -> l.add(num("Радиус, бл", null, () -> cfg().stickyRadius, v -> cfg().stickyRadius = v, 1, 32, 1, 4));
            case "farmBlock" -> l.add(num("Радиус, бл", null, () -> cfg().farmBlockRadius, v -> cfg().farmBlockRadius = v, 2, 64, 1, 4));
            case "bedrockBurst" -> {
                l.add(inum("Блоков", null, () -> cfg().bedrockBurstMin, v -> cfg().bedrockBurstMin = (int) v, 1, 64, 1, 4));
                l.add(num("Радиус, бл", null, () -> cfg().bedrockBurstRadius, v -> cfg().bedrockBurstRadius = v, 1, 64, 1, 4));
                l.add(inum("Окно, мс", null, () -> cfg().bedrockBurstMs, v -> cfg().bedrockBurstMs = (int) v, 50, 10_000, 50, 500));
            }
            case "cageCount" -> {
                l.add(inum("Блоков", null, () -> cfg().cageCountMin, v -> cfg().cageCountMin = (int) v, 2, 26, 1, 4));
                l.add(num("Радиус, бл", null, () -> cfg().cageCountRadius, v -> cfg().cageCountRadius = v, 0.5, 4, 0.1, 0.5));
                l.add(inum("Окно, мс", null, () -> cfg().cageCountMs, v -> cfg().cageCountMs = (int) v, 100, 10_000, 50, 500));
            }
            case "chat" -> l.add(tog("Мой ник в чате", null, () -> cfg().chatMentionName, v -> cfg().chatMentionName = v));
            case "playerNear" -> {
                l.add(num("Радиус, бл", null, () -> cfg().playerNearRadius, v -> cfg().playerNearRadius = v, 1, 128, 1, 4));
                l.add(num("Задержка, с", "0 — сразу", () -> cfg().playerNearDelaySec, v -> cfg().playerNearDelaySec = v, 0, 120, 0.5, 5));
            }
            case "staff" -> {
                l.add(num("Радиус, бл", null, () -> cfg().staffRadius, v -> cfg().staffRadius = v, 1, 128, 1, 8));
                l.add(tog("Ещё и в Tab", "Новый staff в списке игроков", () -> cfg().staffTab, v -> cfg().staffTab = v));
            }
            case "twin" -> l.add(num("Радиус, бл", null, () -> cfg().twinRadius, v -> cfg().twinRadius = v, 1, 128, 1, 8));
            case "massTp" -> {
                l.add(inum("Игроков", null, () -> cfg().massTpMin, v -> cfg().massTpMin = (int) v, 2, 50, 1, 5));
                l.add(num("Радиус, бл", null, () -> cfg().massTpRadius, v -> cfg().massTpRadius = v, 1, 64, 1, 4));
                l.add(inum("Окно, мс", null, () -> cfg().massTpWindowMs, v -> cfg().massTpWindowMs = (int) v, 100, 10_000, 100, 1000));
                l.add(inum("После входа в мир, с", "Не считать первые N с", () -> cfg().massTpWorldGraceSec, v -> cfg().massTpWorldGraceSec = (int) v, 0, 120, 1, 10));
            }
            case "nameless" -> {
                l.add(num("Радиус, бл", null, () -> cfg().namelessRadius, v -> cfg().namelessRadius = v, 1, 64, 1, 4));
                l.add(inum("Дольше, мс", null, () -> cfg().namelessDwellMs, v -> cfg().namelessDwellMs = (int) v, 50, 30_000, 50, 500));
            }
            case "silence" -> l.add(inum("Тишина, с", null, () -> cfg().silenceSec, v -> cfg().silenceSec = (int) v, 10, 300, 1, 10));
            case "bps" -> {
                l.add(num("Минимум, блоков/с", null, () -> cfg().bpsMin, v -> cfg().bpsMin = v, 0.1, 40, 0.5, 2));
                l.add(num("Окно, с", null, () -> cfg().bpsWindowSec, v -> cfg().bpsWindowSec = v, 2, 30, 1, 5));
            }
            case "farmingXp" -> l.add(num("Без текста XP, с","Нужно ранее увидеть XP в том же сборе; +10тиков подтверждения",()->cfg().farmingXpMissingSec,v->cfg().farmingXpMissingSec=v,3,60,1,8));
            case "desync" -> l.add(num("Вернулось, %", null, () -> cfg().desyncPct, v -> cfg().desyncPct = v, 10, 100, 5, 10));
            case "banwave" -> {
                l.add(inum("Банов", null, () -> cfg().banwaveThreshold, v -> cfg().banwaveThreshold = (int) v, 1, 100_000, 1, 50));
                l.add(inum("Окно, мин", null, () -> cfg().banwaveWindowMin, v -> cfg().banwaveWindowMin = (int) v, 2, 60, 1, 5));
                l.add(tog("Считать баны staff", null, () -> cfg().banwaveWithStaff, v -> cfg().banwaveWithStaff = v));
            }
            case "jacob" -> l.add(inum("Лимит урожая", "0 — выкл", () -> cfg().jacobLimit, v -> cfg().jacobLimit = (int) v, 0, 2_000_000_000, 1000, 100_000));
            case "suspicion" -> {
                l.add(num("Лимит очков", null, () -> cfg().suspicionLimit, v -> cfg().suspicionLimit = v, 0.1, 1000, 0.5, 5));
                l.add(num("Полураспад, с", "За сколько очки уменьшаются вдвое", () -> cfg().suspicionHalfLifeSec, v -> cfg().suspicionHalfLifeSec = v, 1, 3600, 5, 60));
            }
            default -> { }
        }
        if (d.allowed().contains(Action.SUSP))
            l.add(num("Вес для «Подозрительности»", "Очков за срабатывание", () -> Detectors.weight(d.id()), v -> Detectors.setWeight(d.id(), v), 0, 100, 0.5, 2)
                    .showIf(() -> Detectors.action(d.id()) == Action.SUSP));
        if (d.hasSound())
            l.add(new Rows.Selector("Звук паники", "Свой звук этого детектора или общий", DetectorTable::soundChoices,
                    () -> { String s = Detectors.sound(d.id()); return s == null ? "" : s; },
                    v -> { Detectors.setSound(d.id(), v.isEmpty() ? null : v); save(); }));
        if (l.isEmpty()) l.add(new Rows.Note(d.kind() == Detectors.Kind.GUARD ? "Защита, порогов нет: только «Учитывать» или «Выкл»." : "Порогов нет.", Ui.SUB));
        return l;
    }

    private static List<Rows.Choice> soundChoices() {
        List<Rows.Choice> l = new ArrayList<>();
        l.add(new Rows.Choice("", "Общий"));
        for (PanicSound.Option o : PanicSound.options()) l.add(new Rows.Choice(o.id(), o.label()));
        return l;
    }

    private static String soundShort(String id) {
        if (id == null) return "общий";
        String l = PanicSound.label(id);
        return l.startsWith("Файл: ") ? l.substring(6) : l;
    }

    private static int actionColor(Action a) {
        return switch (a) {
            case PANIC -> Ui.DANGER;
            case SUSP -> Ui.WARN;
            case LOG -> Ui.ACCENT_HI;
            case OFF -> Ui.DIM;
        };
    }

    // ── Раскладка столбцов ───────────────────────────────────────────────────

    private static boolean reacts(String id) {
        if(com.farmmacro.panic.AutomationSafety.manualHandoff(id))return false;
        var ids = ModConfig.INSTANCE.reactIds;
        return (ids == null ? ModConfig.REACT_DEFAULT : ids).contains(id);
    }

    /** Узкое меню: столбец «Звук» прячем (звук — в ⚙). */
    private static boolean wide(int w) { return w >= 340; }

    private record Cols(int toggle, int name, int nameW, int action, int sound, int react, int test, int count, int gear) {}

    private static final int A_W = 76, S_W = 64, R_W = 14, T_W = 16, C_W = 26, G_W = 14, GAP = 4;

    private static Cols cols(int x, int w) {
        int r = x + w - 4;
        int gear = r - G_W;
        int count = gear - GAP - C_W;
        int test = count - GAP - T_W;
        int react = test - GAP - R_W;                      // v1.16.3 «Реакц.» — человеческая реакция на этот детектор
        int sound = wide(w) ? react - GAP - S_W : react;
        int action = sound - GAP - A_W;
        int name = x + 34;
        return new Cols(x + 6, name, action - GAP - name, action, sound, react, test, count, gear);
    }

    // ── Строки ───────────────────────────────────────────────────────────────

    /** Поиск по таблице: фильтрует сразу, пока печатаешь. */
    static final class SearchRow extends Rows.Input {
        SearchRow() { super("Поиск по детекторам", null); }
        int height(Rows.Ctx c, int w) { return 22; }
        int controlWidth(Rows.Ctx c) { return lastW - 12; }
        int maxLen() { return 40; }
        boolean allowed(String ch) { return true; }
        String initialText() { return query; }
        String commitText(String text) { query = text.strip(); return null; }
        @Override String editTip() { return "Фильтрует таблицу по названию и подсказке · Esc — выйти из поля"; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int cx = x + 2, cy = y + 3, cw = w - 4;
            if (focused()) {
                query = buf.strip();
                drawEditing(c, g, cx, cy, cw, hover);
                return;
            }
            boolean hf = Ui.inside(mx, my, cx, cy, cw, 14);
            g.fill(cx, cy + 14, cx + cw, cy + 15, hf ? Ui.SUB : Ui.BORDER);
            if (query.isEmpty()) Ui.text(g, c.font(), "⌕ Найти детектор…", cx + 6, cy + 3, Ui.DIM);
            else {
                Ui.text(g, c.font(), Ui.ellipsize(c.font(), "⌕ " + query, cw - 30), cx + 6, cy + 3, Ui.TEXT);
                boolean hx = Ui.inside(mx, my, cx + cw - 16, cy, 16, 14);
                Ui.textCentered(g, c.font(), "✕", cx + cw - 8, cy + 3, hx ? Ui.DANGER : Ui.SUB);
                if (hx) c.tooltip("Очистить");
            }
            if (hf) c.hand();
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int cx = lastX + 2, cy = lastY + 3, cw = lastW - 4;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            if (!focused() && !query.isEmpty() && Ui.inside(mx, my, cx + cw - 16, cy, 16, 14)) { c.clickSound(); query = ""; return true; }
            if (!focused()) focus(c);
            else placeCursor(c, mx, cx);
            return true;
        }
    }

    /** Заголовки столбцов. */
    static final class HeaderRow extends Rows.Row {
        int height(Rows.Ctx c, int w) { return 14; }
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            Font f = c.font();
            Cols k = cols(x, w);
            int ty = y + 3;
            Ui.text(g, f, "Вкл", k.toggle(), ty, Ui.DIM);
            Ui.text(g, f, "Детектор", k.name(), ty, Ui.DIM);
            Ui.text(g, f, "Действие ▾", k.action() + 2, ty, Ui.DIM);
            if (wide(w)) Ui.text(g, f, "Звук ▾", k.sound() + 2, ty, Ui.DIM);
            Ui.textCentered(g, f, "☺", k.react() + R_W / 2, ty, Ui.DIM);
            Ui.textCentered(g, f, "Тест", k.test() + T_W / 2, ty, Ui.DIM);
            Ui.textCentered(g, f, "Сраб.", k.count() + C_W / 2, ty, Ui.DIM);
        }
    }

    /** Заголовок группы детекторов. */
    static final class GroupRow extends Rows.Row {
        private final String group;
        GroupRow(String group) { this.group = group; }
        @Override public String searchText() { return group; }
        @Override public boolean shown() { return super.shown() && anyVisible(group); }
        int height(Rows.Ctx c, int w) { return 16; }
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int on = 0, all = 0;
            for (Def d : Detectors.all()) if (d.group().equals(group)) { all++; if (d.enabled()) on++; }
            Ui.text(g, c.font(), group, x + 4, y + 5, Ui.ACCENT_HI);
            String r = on + "/" + all;
            Ui.textRight(g, c.font(), r, x + w - 4, y + 5, Ui.DIM);
            int lx = x + 10 + c.font().width(group), rx = x + w - 10 - c.font().width(r);
            if (rx > lx) g.fill(lx, y + 9, rx, y + 10, Ui.BORDER);
        }
    }

    /** Строка детектора. */
    static final class DetectorRow extends Rows.Row {
        private final Def d;
        private final FarmMacroScreen screen;
        DetectorRow(Def d, FarmMacroScreen screen) { this.d = d; this.screen = screen; }

        @Override public String searchText() { return d.name() + " " + d.hint(); }
        @Override public boolean shown() { return super.shown() && visible(d); }
        int height(Rows.Ctx c, int w) { return 20; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            Font f = c.font();
            Cols k = cols(x, w);
            boolean on = d.enabled();
            Action a = Detectors.action(d.id());
            Ui.listBg(g, x, y, w, 18, 4, false, hover);
            int cy = y + 5;
            // Вкл
            int tx = k.toggle(), ty = y + 3;
            boolean hT = Ui.inside(mx, my, tx - 2, y, 26, 18);
            Ui.pill(g, tx, ty, 22, 12, on ? Ui.TOGGLE_ON : Ui.TRACK_OFF);
            Ui.circle(g, on ? tx + 16 : tx + 6, ty + 6, 4, on ? Ui.KNOB_ON : Ui.KNOB_OFF);
            if (hT) { c.hand(); c.tooltip(on ? "Выключить детектор" : "Включить детектор"); }
            // Имя
            boolean hN = Ui.inside(mx, my, k.name(), y, k.nameW(), 18);
            Ui.text(g, f, Ui.ellipsize(f, d.name(), k.nameW()), k.name(), cy, on ? Ui.TEXT : Ui.DIM);
            if (hN) c.tooltip(d.name() + " — " + d.hint());
            // Действие
            boolean hA = Ui.inside(mx, my, k.action(), y + 2, A_W, 14);
            int ac = actionColor(a);
            Ui.round(g, k.action(), y + 2, A_W, 14, 3, Ui.alpha(ac, hA ? 0.30f : 0.16f));
            Ui.text(g, f, Ui.ellipsize(f, Detectors.actionLabel(d, a), A_W - 6), k.action() + 4, cy, ac);
            if (hA) { c.hand(); c.tooltip(actionTip(a)); }
            // Звук
            if (wide(w)) {
                boolean snd = d.hasSound() && a == Action.PANIC;
                String sl = d.hasSound() ? soundShort(Detectors.sound(d.id())) : "—";
                boolean hS = d.hasSound() && Ui.inside(mx, my, k.sound(), y + 2, S_W, 14);
                if (hS) Ui.round(g, k.sound(), y + 2, S_W, 14, 3, Ui.alpha(Ui.BORDER, 0.8f));
                Ui.text(g, f, Ui.ellipsize(f, sl, S_W - 6), k.sound() + 3, cy,
                        !d.hasSound() ? Ui.DIM : Detectors.sound(d.id()) != null ? Ui.TEXT : snd ? Ui.SUB : Ui.DIM);
                if (hS) { c.hand(); c.tooltip("Звук паники этого детектора: клик — следующий, ПКМ — предыдущий. «общий» — как в «Звук паники»"); }
            }
            // Реакция (v1.16.3)
            if (d.hasSound()) {
                boolean r = reacts(d.id());
                boolean hR = Ui.inside(mx, my, k.react(), y + 2, R_W, 14);
                Ui.textCentered(g, f, r ? "☺" : "·", k.react() + R_W / 2, cy,
                        !r ? Ui.DIM : ModConfig.INSTANCE.reactEnabled ? Ui.ACCENT_HI : Ui.SUB);
                if (hR) { c.hand(); c.tooltip((r ? "Человеческая реакция: да" : "Человеческая реакция: нет")
                        + (ModConfig.INSTANCE.reactEnabled ? "" : " (сама реакция выключена: «Паника → Человеческая реакция»)") + ". Клик — переключить"); }
            }
            // Тест
            if (d.testable()) {
                boolean hX = Ui.inside(mx, my, k.test(), y + 2, T_W, 14);
                Ui.textCentered(g, f, "▶", k.test() + T_W / 2, cy, hX ? Ui.TEXT : Ui.SUB);
                if (hX) { c.hand(); c.tooltip("Показать экран и звук паники с примером причины (ничего не останавливает)"); }
            }
            // Сраб.
            int n = Detectors.count(d.id());
            boolean hC = Ui.inside(mx, my, k.count(), y + 2, C_W, 14);
            Ui.textCentered(g, f, n > 999 ? "999+" : String.valueOf(n), k.count() + C_W / 2, cy,
                    n == 0 ? Ui.DIM : logOpen.contains(d.id()) ? Ui.ACCENT_HI : Ui.WARN);
            if (hC) { c.hand(); c.tooltip("Срабатываний за сессию. Клик — последние записи"); }
            // ⚙
            boolean hG = Ui.inside(mx, my, k.gear(), y + 2, G_W, 14);
            Ui.textCentered(g, f, "⚙", k.gear() + G_W / 2, cy, gearOpen.contains(d.id()) ? Ui.ACCENT_HI : hG ? Ui.TEXT : Ui.SUB);
            if (hG) { c.hand(); c.tooltip(gearOpen.contains(d.id()) ? "Скрыть пороги" : "Пороги и свой звук"); }
        }

        private String actionTip(Action a) {
            String now = switch (a) {
                case PANIC -> d.kind() == Detectors.Kind.GUARD ? "Защита включена" : d.ownLabel() != null ? d.ownLabel() + " (своё действие детектора)" : "Паника: стоп, экран, звук";
                case SUSP -> "Очки подозрительности (+" + Rows.num(Detectors.weight(d.id())) + "), паника — когда сумма дойдёт до лимита";
                case LOG -> "Только строка в журнале событий (не чаще раза в 5 с)";
                case OFF -> "Выключен";
            };
            return now + ". Клик — следующее, ПКМ — предыдущее";
        }

        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0 && button != 1) return false;
            int dir = button == 0 ? 1 : -1;
            Cols k = cols(lastX, lastW);
            int y = lastY;
            if (button == 0 && Ui.inside(mx, my, k.toggle() - 2, y, 26, 18)) {
                d.setEnabled(!d.enabled()); save(); c.clickSound(); return true;
            }
            if (Ui.inside(mx, my, k.action(), y + 2, A_W, 14)) {
                Detectors.setAction(d.id(), Detectors.next(d.id(), dir)); save(); c.clickSound(); return true;
            }
            if (wide(lastW) && d.hasSound() && Ui.inside(mx, my, k.sound(), y + 2, S_W, 14)) {
                List<Rows.Choice> ch = soundChoices();
                String cur = Detectors.sound(d.id());
                int i = 0;
                for (int j = 0; j < ch.size(); j++) if (ch.get(j).id().equals(cur == null ? "" : cur)) i = j;
                String nx = ch.get(Math.floorMod(i + dir, ch.size())).id();
                Detectors.setSound(d.id(), nx.isEmpty() ? null : nx); save(); c.clickSound(); return true;
            }
            if (button != 0) return false;
            if (d.hasSound() && !com.farmmacro.panic.AutomationSafety.manualHandoff(d.id()) && Ui.inside(mx, my, k.react(), y + 2, R_W, 14)) {
                var ids = ModConfig.INSTANCE.reactIds;
                if (ids == null) ids = ModConfig.INSTANCE.reactIds = new java.util.ArrayList<>(ModConfig.REACT_DEFAULT);
                if (!ids.remove(d.id())) ids.add(d.id());
                save(); c.clickSound(); return true;
            }
            if (d.testable() && Ui.inside(mx, my, k.test(), y + 2, T_W, 14)) {
                c.clickSound();
                Minecraft mc = Minecraft.getInstance();
                screen.onClose();      // как «Проверить панику»: закрыть меню, чтобы было видно экран
                PanicDetector.INSTANCE.preview(mc, "Тест: " + d.name() + " — " + d.example(), Detectors.sound(d.id()));
                return true;
            }
            if (Ui.inside(mx, my, k.count(), y + 2, C_W, 14)) { toggle(logOpen); c.clickSound(); return true; }
            if (Ui.inside(mx, my, k.gear(), y + 2, G_W, 14) || Ui.inside(mx, my, k.name(), y, k.nameW(), 18)) {
                toggle(gearOpen); c.clickSound(); return true;
            }
            return false;
        }

        private void toggle(Set<String> s) { if (!s.remove(d.id())) s.add(d.id()); }
    }

    /** Последние записи детектора (клик по «Сраб.»). */
    static final class LogRow extends Rows.Row {
        private final Def d;
        private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");
        LogRow(Def d) { this.d = d; }

        @Override public boolean shown() { return super.shown() && logOpen.contains(d.id()) && visible(d); }

        private List<String> lines() {
            List<String> out = new ArrayList<>();
            for (Detectors.Entry e : Detectors.recent(d.id()))
                out.add(TIME.format(new Date(e.ms())) + " · " + Detectors.actionLabel(d, e.action()) + " · " + e.text());
            if (out.isEmpty()) out.add("За эту сессию не срабатывал");
            return out;
        }

        int height(Rows.Ctx c, int w) { return lines().size() * 10 + 8; }

        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            List<String> l = lines();
            int h = l.size() * 10 + 8;
            Ui.round(g, x + 12, y, w - 12, h - 3, 4, Ui.alpha(Ui.ACCENT, 0.08f));
            int ly = y + 3;
            for (String s : l) {
                String e = Ui.ellipsize(c.font(), s, w - 28);
                Ui.text(g, c.font(), e, x + 18, ly, Ui.SUB);
                if (hover && my >= ly - 1 && my < ly + 9 && !e.equals(s)) c.tooltip(s);
                ly += 10;
            }
        }
    }
}
