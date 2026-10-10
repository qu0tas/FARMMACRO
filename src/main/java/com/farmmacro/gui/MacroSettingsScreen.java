package com.farmmacro.gui;

import com.farmmacro.camera.CameraBinding;
import com.farmmacro.camera.CameraPresets;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.HoldSettings;
import com.farmmacro.macro.HumanSettings;
import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MacroSettings;
import com.farmmacro.macro.MacroStorage;
import com.farmmacro.route.RouteBuffer;
import com.farmmacro.route.RouteStorage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
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
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно «Настройки макроса» (⚙ у карточки макроса или маршрута): привязка пресетов камеры и «Зажим мыши».
 * Что именно правится — {@link Target}: текущий макрос/маршрут в памяти или сохранённый файл.
 * Сохранённый файл записывается при закрытии окна, если что-то поменяли.
 */
public class MacroSettingsScreen extends Screen implements Rows.Ctx {

    /** Что правит окно. */
    public interface Target {
        String title();
        String subtitle();
        MacroSettings settings();
        /** После каждой правки. */
        void changed();
        /** Сколько кадров (точек). */
        int length();
        boolean route();
        /** Можно ли сейчас править (не во время записи/воспроизведения). */
        boolean editable();
        /** Доп. предупреждение или пояснение (может быть пустым). */
        String note();
        /** Окно закрыли; modified — были правки. */
        void close(boolean modified);
        /** Маршрут: сколько точек с галочкой «Зажим мыши» (−1 — неизвестно / не маршрут). */
        default int heldPoints() { return -1; }
    }

    private final Screen parent;
    private final Target t;
    private final List<Rows.Row> rows = new ArrayList<>();
    private double scroll;
    private boolean modified;
    private String tooltip;
    private boolean wantHand;

    public MacroSettingsScreen(Screen parent, Target target) {
        super(Component.literal("Настройки макроса"));
        this.parent = parent;
        this.t = target;
    }

    private static ModConfig cfg() { return ModConfig.INSTANCE; }
    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private String unit() { return t.route() ? "точки" : "кадра"; }

    private int wW() { return Math.min(340, width - 16); }
    private int wH() { return Math.min(320, height - 16); }
    private int wX() { return (width - wW()) / 2; }
    private int wY() { return (height - wH()) / 2; }
    private int cX() { return wX() + 10; }
    private int cY() { return wY() + 36; }
    private int cW() { return wW() - 20; }
    private int cH() { return wH() - 44; }

    @Override
    protected void init() {
        if (rows.isEmpty()) build();
    }

    private void changed() { modified = true; t.changed(); }

    private void edit(Runnable r) { r.run(); changed(); }

    private void build() {
        rows.clear();
        Rows.searching = false;
        buildCamera();
        buildHold();
        buildHuman();
        rows.add(new Rows.End());
        rows.add(new Rows.MoreToggle(() -> Rows.hiddenAdvanced(rows), () -> {
            cfg().uiAdvanced = !cfg().uiAdvanced;
            ModConfig.save();
        }));
        rows.add(new Rows.Note(() -> {
            String n = t.note();
            return n == null ? "" : n;
        }, Ui.SUB));
        rows.add(new Rows.Spacer(4));
        rows.add(new Rows.Buttons(new Rows.Btn("Готово", Rows.Style.PRIMARY, this::onClose)));
        Rows.link(rows);
    }

    // ── Камера ───────────────────────────────────────────────────────────────

    private static List<Rows.Choice> presetChoices(CameraBinding.Change cur) {
        List<Rows.Choice> l = new ArrayList<>();
        for (var p : cfg().camPresets) l.add(new Rows.Choice(String.valueOf(p.id), p.name));
        if (cur != null && !cur.presetExists()) l.add(new Rows.Choice(String.valueOf(cur.presetId), cur.label()));
        return l;
    }

    private static CameraBinding.Change newChange(int from) {
        var c = new CameraBinding.Change();
        c.from = from;
        var p = CameraPresets.selected();
        if (p != null) c.take(p);
        return c;
    }

    private String fmt(int index1) {
        return t.route() ? String.valueOf(index1) : index1 + " · " + f1((index1 - 1) / 20.0) + " с";
    }

    private void buildCamera() {
        CameraBinding b = t.settings().camera;
        java.util.function.BooleanSupplier en = t::editable;
        rows.add(new Rows.Section("Камера"));
        boolean noPresets = cfg().camPresets.isEmpty();
        rows.add(new Rows.Note("Камеру " + (t.route() ? "автоход" : "запись") + " не крутит. Можно поставить пресет: при старте и в "
                + (t.route() ? "точках" : "кадрах") + " смены камера встаёт в него мгновенно, дальше yaw/pitch не меняются. "
                + "Свой поворот не даёт панику." + (noPresets ? " Сначала создай пресет во вкладке «Камера»." : ""),
                noPresets ? Ui.WARN : Ui.SUB));
        List<Rows.Choice> modes = List.of(new Rows.Choice(CameraBinding.NONE, "Нет (как есть)"),
                new Rows.Choice(CameraBinding.WHOLE, "Один на всё"),
                new Rows.Choice(CameraBinding.POINTS, t.route() ? "По точкам" : "По кадрам"));
        rows.add(new Rows.Selector("Пресет камеры", "Нет / один на весь проход / с " + unit() + " N — пресет X",
                () -> modes, () -> b.mode, v -> {
            if (!CameraBinding.NONE.equals(v) && noPresetsNow() && b.changes.isEmpty()) return;
            edit(() -> {
                b.mode = v;
                if (!CameraBinding.NONE.equals(v) && b.changes.isEmpty()) b.changes.add(newChange(0));
            });
            build();
        }).enabledIf(en));
        if (CameraBinding.WHOLE.equals(b.mode) && !b.changes.isEmpty()) {
            var c = b.changes.get(0);
            rows.add(new Rows.Selector("Пресет", null, () -> presetChoices(c), () -> String.valueOf(c.presetId), v -> {
                var p = CameraPresets.byId(Integer.parseInt(v));
                if (p != null) edit(() -> c.take(p));
            }).enabledIf(en));
        } else if (CameraBinding.POINTS.equals(b.mode)) {
            for (int i = 0; i < b.changes.size(); i++) {
                var c = b.changes.get(i);
                int n = i + 1;
                rows.add(new Rows.Number("Смена " + n + ": с " + unit(), null, () -> c.from + 1,
                        v -> edit(() -> c.from = (int) v - 1), 1, Math.max(1, t.length()), 1,
                        t.route() ? 5 : 20, v -> fmt((int) v)).integer().enabledIf(en));
                rows.add(new Rows.Selector("Смена " + n + ": пресет", null, () -> presetChoices(c),
                        () -> String.valueOf(c.presetId), v -> {
                    var p = CameraPresets.byId(Integer.parseInt(v));
                    if (p != null) edit(() -> c.take(p));
                }).enabledIf(en));
                rows.add(new Rows.Buttons(new Rows.Btn("Убрать смену " + n, Rows.Style.SECONDARY, () -> {
                    edit(() -> {
                        b.changes.remove(c);
                        if (b.changes.isEmpty()) b.mode = CameraBinding.NONE;
                    });
                    build();
                }).enabledIf(en)));
            }
            rows.add(new Rows.Buttons(new Rows.Btn("+ Смена камеры", Rows.Style.PRIMARY, () -> {
                int last = -1;
                for (var c : b.changes) last = Math.max(last, c.from);
                int from = Math.max(0, Math.min(Math.max(0, t.length() - 1), last + 1));
                edit(() -> b.changes.add(newChange(from)));
                build();
            }).enabledIf(() -> en.getAsBoolean() && !noPresetsNow() && b.changes.size() < 256)
                    .tip("Новая смена — выбранный во вкладке «Камера» пресет")));
        }
        rows.add(new Rows.Note(() -> {
            if (!b.active()) return "";
            List<String> w = new ArrayList<>();
            for (var c : b.changes) {
                if (!c.presetExists()) w.add("пресет «" + c.label() + "» удалён — берутся сохранённые yaw " + CameraPresets.deg(c.yaw)
                        + "° / pitch " + CameraPresets.deg(c.pitch) + "°");
                if (CameraBinding.POINTS.equals(b.mode) && c.from >= t.length() && t.length() > 0)
                    w.add("смена с " + unit() + " " + (c.from + 1) + " за концом — не сработает");
                if (CameraBinding.WHOLE.equals(b.mode)) break;
            }
            if (!t.route() && cfg().replayCamera) w.add("«Повторять камеру» для этого макроса не действует, пока выбран пресет");
            return w.isEmpty() ? "" : "Внимание: " + String.join("; ", w) + ".";
        }, Ui.WARN));
    }

    private static boolean noPresetsNow() { return cfg().camPresets.isEmpty(); }

    // ── Зажим мыши ───────────────────────────────────────────────────────────

    private static final List<Rows.Choice> HOLD_BUTTONS = List.of(
            new Rows.Choice(HoldSettings.NONE, "Нет"), new Rows.Choice(HoldSettings.ATTACK, "ЛКМ"),
            new Rows.Choice(HoldSettings.USE, "ПКМ"));

    /** Целое из поля ввода в пределах [min, max], иначе null. */
    private static Integer parseInt(String v, int min, int max) {
        try {
            long n = Long.parseLong(v.trim().replace("+", ""));
            return n < min || n > max ? null : (int) n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String ticks(int t) { return t + " т · " + f1(t / 20.0) + " с"; }

    // ── Случайность ──────────────────────────────────────────────────────────

    private void buildHuman() {
        HumanSettings h = t.settings().human;
        rows.add(new Rows.Section("Случайность"));
        rows.add(new Rows.Note("Каждый проход немного другой: смазанные повороты на углах, разные паузы и задержки, иногда "
                + "остановки и идеальные круги. Общий выключатель — «Запуск → Случайность». Выкл — проход точно как раньше.", Ui.SUB));
        rows.add(new Rows.Note(() -> cfg().humanUseGlobal ? "Сейчас включена общая случайность (вкладка «Конфиги») — эти настройки не используются." : "", Ui.WARN));
        HumanRows.build(rows, h, t.route(), !t.route(), t::editable, this::edit, this::build);
        rows.add(new Rows.Buttons(
                new Rows.Btn("Взять общие", Rows.Style.SECONDARY, () -> {
                    edit(() -> t.settings().human = cfg().humanGlobal.copy());
                    build();
                }).enabledIf(t::editable).tip("Скопировать общую случайность из вкладки «Конфиги» сюда"),
                new Rows.Btn("Сделать общими", Rows.Style.SECONDARY, () -> {
                    cfg().humanGlobal = t.settings().human.copy();
                    ModConfig.save();
                }).tip("Скопировать эти настройки в общую случайность (вкладка «Конфиги»)")));
    }

    private void buildHold() {
        HoldSettings h = t.settings().hold;
        java.util.function.BooleanSupplier en = t::editable;
        rows.add(new Rows.Section("Зажим мыши"));
        rows.add(new Rows.Note("Держать кнопку мыши во время воспроизведения — вместе с тем, что жмёт сама запись или точка. "
                + "Отпускается при стопе, панике, End, открытии любого окна и выходе из мира. Ручной зажим ЛКМ без макроса — клавиша "
                + MacroManager.keyName(com.farmmacro.FarmMacroMod.keyHold) + ".", Ui.SUB));
        rows.add(new Rows.Selector("Кнопка", "Что держать: нет / ЛКМ (ломать) / ПКМ (ставить, использовать)",
                () -> HOLD_BUTTONS, () -> h.button, v -> { edit(() -> h.button = v); build(); }).enabledIf(en));
        if (!h.active()) return;
        List<Rows.Choice> modes = List.of(new Rows.Choice(HoldSettings.WHOLE, "Весь проход"),
                new Rows.Choice(HoldSettings.POINTS, t.route() ? "По точкам" : "По участкам"));
        rows.add(new Rows.Selector("Когда", t.route() ? "Весь проход / на отрезках от точек с галочкой «Зажим мыши»"
                : "Весь проход / только на заданных участках записи (в тиках = кадрах)",
                () -> modes, () -> h.mode, v -> {
            edit(() -> {
                h.mode = v;
                if (!t.route() && HoldSettings.POINTS.equals(v) && h.ranges.isEmpty())
                    h.ranges.add(new HoldSettings.Range(0, Math.max(0, t.length() - 1)));
            });
            build();
        }).enabledIf(en));
        rows.add(new Rows.TextField("Задержка старта, тиков", "Сколько тиков после начала воспроизведения не зажимать (20 тиков = 1 с). "
                + "0–" + HoldSettings.DELAY_MAX, () -> String.valueOf(h.delayTicks), v -> {
            Integer n = parseInt(v, 0, HoldSettings.DELAY_MAX);
            if (n == null) return false;
            if (n != h.delayTicks) edit(() -> h.delayTicks = n);
            return true;
        }, Rows.TextField.numeric(), 6, 70).enabledIf(en));
        rows.add(new Rows.Note(() -> h.delayTicks == 0 ? "" : "Зажим — с " + ticks(h.delayTicks) + " от старта.", Ui.SUB));
        if (!HoldSettings.POINTS.equals(h.mode)) return;
        if (t.route()) {
            rows.add(new Rows.Note(() -> {
                int n = t.heldPoints();
                return "Отметь «Зажим мыши» у нужных точек: редактор → Shift+ПКМ по точке. Кнопка зажата на отрезке от точки до следующей."
                        + (n >= 0 ? " Отмечено точек: " + n + " из " + t.length() + "." : "");
            }, t.heldPoints() == 0 ? Ui.WARN : Ui.SUB));
            return;
        }
        int max = Math.max(1, t.length());
        for (int i = 0; i < h.ranges.size(); i++) {
            var r = h.ranges.get(i);
            int n = i + 1;
            rows.add(new Rows.TextField("Участок " + n + ": с тика", "Номер кадра записи, 1–" + max,
                    () -> String.valueOf(r.from + 1), v -> {
                Integer x = parseInt(v, 1, max);
                if (x == null) return false;
                if (x - 1 != r.from) edit(() -> r.from = x - 1);
                return true;
            }, Rows.TextField.numeric(), 7, 70).enabledIf(en));
            rows.add(new Rows.TextField("Участок " + n + ": по тик", "Включительно, 1–" + max,
                    () -> String.valueOf(r.to + 1), v -> {
                Integer x = parseInt(v, 1, max);
                if (x == null) return false;
                if (x - 1 != r.to) edit(() -> r.to = x - 1);
                return true;
            }, Rows.TextField.numeric(), 7, 70).enabledIf(en));
            rows.add(new Rows.Note(() -> {
                int a = Math.min(r.from, r.to), b = Math.max(r.from, r.to);
                return "≈ " + f1(a / 20.0) + "–" + f1((b + 1) / 20.0) + " с, " + (b - a + 1) + " т"
                        + (r.from > r.to ? " (начало и конец перепутаны — учитывается как " + (a + 1) + "–" + (b + 1) + ")" : "");
            }, Ui.SUB));
            rows.add(new Rows.Buttons(new Rows.Btn("Убрать участок " + n, Rows.Style.SECONDARY, () -> {
                edit(() -> h.ranges.remove(r));
                build();
            }).enabledIf(en)));
        }
        rows.add(new Rows.Buttons(new Rows.Btn("+ Участок", Rows.Style.PRIMARY, () -> {
            int last = -1;
            for (var r : h.ranges) last = Math.max(last, Math.max(r.from, r.to));
            int from = Math.min(max - 1, last + 1);
            edit(() -> h.ranges.add(new HoldSettings.Range(from, Math.min(max - 1, from + 19))));
            build();
        }).enabledIf(() -> en.getAsBoolean() && h.ranges.size() < HoldSettings.RANGES_MAX)));
        if (h.ranges.isEmpty()) rows.add(new Rows.Note("Участков нет — кнопка зажиматься не будет.", Ui.WARN));
    }

    // ── Готовые цели ─────────────────────────────────────────────────────────

    /** Предупреждение «запись сделана с другим yaw». */
    private static String yawNote(MacroSettings s, MacroFrame f0) {
        var c0 = s.camera.at(0);
        if (f0 == null || c0 == null || Math.abs(Mth.wrapDegrees(c0.yaw() - f0.yaw)) <= 2f) return null;
        return "Запись сделана с yaw " + f1(Mth.wrapDegrees(f0.yaw)) + "°, пресет — " + CameraPresets.deg(c0.yaw())
                + "°: клавиши из записи поведут в другую сторону.";
    }

    private static String join(String... parts) {
        List<String> l = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isEmpty()) l.add(p);
        return String.join(" ", l);
    }

    private static boolean idle() { return MacroManager.INSTANCE.getState() == MacroManager.State.IDLE; }

    /** Текущий макрос в памяти. Файл (если макрос загружен) перезаписывается при закрытии окна. */
    public static Target currentMacro() {
        MacroManager mm = MacroManager.INSTANCE;
        return new Target() {
            public String title() { return mm.getLoadedName() != null ? "Макрос «" + mm.getLoadedName() + "»" : "Текущий макрос"; }
            public String subtitle() { return mm.getFrameCount() + " кадров · " + MacroManager.formatTicks(mm.getFrameCount()); }
            public MacroSettings settings() { return mm.getSettings(); }
            public void changed() { mm.settingsChanged(); }
            public int length() { return mm.getFrameCount(); }
            public boolean route() { return false; }
            public boolean editable() { return idle() && mm.getFrameCount() > 0; }
            public String note() {
                return join(yawNote(mm.getSettings(), mm.getStartFrame()), mm.getLoadedName() == null
                        ? "Запись не сохранена — настройки сохранятся вместе с ней." : "Сохраняется в файл макроса при закрытии окна.");
            }
            public void close(boolean modified) { mm.saveSettingsIfDirty(); }
        };
    }

    /** Текущий маршрут в памяти: правка = «изменён», Ctrl+Z отменяет всё окно разом. */
    public static Target currentRoute() {
        RouteBuffer rb = RouteBuffer.INSTANCE;
        rb.snapshot();
        return new Target() {
            public String title() { return rb.loadedName() != null ? "Маршрут «" + rb.loadedName() + "»" : "Текущий маршрут"; }
            public String subtitle() { return rb.size() + " точек"; }
            public MacroSettings settings() { return rb.settings(); }
            public void changed() { rb.changed(); }
            public int length() { return rb.size(); }
            public boolean route() { return true; }
            public boolean editable() { return idle() && !rb.isEmpty(); }
            public String note() { return "Маршрут в памяти: чтобы настройки попали в файл, нажми «Сохранить» у маршрута."; }
            public int heldPoints() { int n = 0; for (var p : rb.points()) if (p.hold) n++; return n; }
            public void close(boolean modified) { if (!modified) rb.undoDrop(); }
        };
    }

    /** Сохранённый макрос: файл читается сейчас, записывается при закрытии. null — файл не читается. */
    public static Target savedMacro(MacroStorage.MacroInfo info) {
        MacroManager mm = MacroManager.INSTANCE;
        boolean loaded = info.name().equals(mm.getLoadedName());
        if (loaded) mm.saveSettingsIfDirty();           // чтобы в файле были последние правки буфера
        MacroStorage.SavedMacro m = MacroStorage.INSTANCE.load(info.filename());
        if (m == null) return null;
        MacroSettings s = m.settings;
        int len = m.frames.size();
        MacroFrame f0 = m.frames.get(0);
        m.frames = null;                                 // кадры окну не нужны
        return new Target() {
            String error;
            public String title() { return "Макрос «" + info.name() + "»"; }
            public String subtitle() { return len + " кадров · " + MacroManager.formatTicks(len) + " · файл"; }
            public MacroSettings settings() { return s; }
            public void changed() {}
            public int length() { return len; }
            public boolean route() { return false; }
            public boolean editable() { return idle(); }
            public String note() {
                return join(yawNote(s, f0), error != null ? error : "Записывается в файл при закрытии окна"
                        + (loaded ? " и сразу действует для загруженного макроса." : "."));
            }
            public void close(boolean modified) {
                if (!modified) return;
                if (!MacroStorage.INSTANCE.saveSettings(info.filename(), s)) { msg("§c[FM] Не удалось записать настройки (подробности в логе)"); return; }
                if (info.name().equals(mm.getLoadedName())) mm.replaceSettings(s.copy());
                msg("§8[§cFM§8] §aНастройки «" + info.name() + "» сохранены");
            }
        };
    }

    /** Сохранённый маршрут: файл читается сейчас, записывается при закрытии. null — файл не читается. */
    public static Target savedRoute(RouteStorage.RouteInfo info) {
        var r = RouteStorage.INSTANCE.load(info.filename());
        if (r == null) return null;
        MacroSettings s = r.settings;
        int len = r.points.size();
        int held = (int) r.points.stream().filter(p -> p.hold).count();
        RouteBuffer rb = RouteBuffer.INSTANCE;
        boolean loaded = info.name().equals(rb.loadedName());
        return new Target() {
            public String title() { return "Маршрут «" + info.name() + "»"; }
            public String subtitle() { return len + " точек · файл"; }
            public int heldPoints() { return held; }
            public MacroSettings settings() { return s; }
            public void changed() {}
            public int length() { return len; }
            public boolean route() { return true; }
            public boolean editable() { return idle(); }
            public String note() {
                return "Записывается в файл при закрытии окна" + (loaded
                        ? " и заменяет настройки загруженного маршрута (точки в памяти не трогаются)." : ".");
            }
            public void close(boolean modified) {
                if (!modified) return;
                if (!RouteStorage.INSTANCE.saveSettings(info.filename(), s)) { msg("§c[FM] Не удалось записать настройки (подробности в логе)"); return; }
                if (info.name().equals(rb.loadedName())) rb.replaceSettings(s.copy());
                msg("§8[§cFM§8] §aНастройки «" + info.name() + "» сохранены");
            }
        };
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(text));
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        tooltip = null; wantHand = false;
        int x = wX(), y = wY(), w = wW(), h = wH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.round(g, x, y, w, h, 8, Ui.WINDOW);
        Ui.text(g, font, Ui.ellipsize(font, "⚙ " + t.title(), w - 20), x + 10, y + 8, Ui.TEXT);
        Ui.text(g, font, Ui.ellipsize(font, t.subtitle() + (t.editable() ? "" : " · править можно, когда макрос не играет и не пишется"),
                w - 20), x + 10, y + 19, Ui.SUB);
        g.fill(x + 10, y + 31, x + w - 10, y + 32, Ui.BORDER);

        int cx = cX(), cy = cY(), cw = cW(), ch = cH();
        int total = 0;
        for (Rows.Row r : rows) total += r.h(this, cw);
        scroll = Math.max(0, Math.min(Math.max(0, total - ch), scroll));
        boolean in = Ui.inside(mx, my, cx, cy, cw, ch);
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int ry = cy - (int) scroll;
        for (Rows.Row r : rows) {
            int rh = r.h(this, cw);
            r.lastX = cx; r.lastY = ry; r.lastW = cw; r.lastH = rh;
            if (rh > 0 && ry + rh > cy && ry < cy + ch)
                r.render(this, g, cx, ry, cw, in ? mx : -1, in ? my : -1, in && my >= ry && my < ry + rh);
            ry += rh;
        }
        g.disableScissor();
        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        if (tooltip != null) {
            g.nextStratum();
            var lines = font.split(Component.literal(tooltip), Math.min(220, width - 20));
            int tw = 0;
            for (var l : lines) tw = Math.max(tw, font.width(l));
            int tx = Math.min(mx + 10, width - tw - 14), ty = my + 12;
            if (ty + lines.size() * 10 + 8 > height - 4) ty = my - lines.size() * 10 - 10;
            Ui.roundBordered(g, tx, ty, tw + 12, lines.size() * 10 + 8, 4, 0xF00B0E14, Ui.BORDER);
            int ly = ty + 5;
            for (var l : lines) { g.text(font, l, tx + 6, ly, Ui.TEXT, false); ly += 10; }
        }
    }

    // ── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        Rows.clearWheelFocus();
        double mx = e.x(), my = e.y();
        Rows.Row typing = Rows.focusedRow();
        if (typing != null && !(my >= typing.lastY && my < typing.lastY + typing.lastH)) Rows.blurText();
        if (Ui.inside(mx, my, cX(), cY(), cW(), cH())) {
            for (Rows.Row r : new ArrayList<>(rows)) {
                if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                    if (r.click(this, mx, my, e.button())) return true;
                    break;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (Rows.Row r : rows) {
            if (r.lastH > 0 && my >= r.lastY && my < r.lastY + r.lastH) {
                if (r.scroll(this, mx, my, sy)) return true;
                break;
            }
        }
        Rows.clearWheelFocus();
        Rows.blurText();
        scroll -= sy * 20;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (Rows.textFocused() && Rows.textKey(e)) return true;
        if (e.key() == 257 || e.key() == 335) { onClose(); return true; }   // Enter
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (Rows.textChar(e)) return true;
        return super.charTyped(e);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Rows.clearWheelFocus();
        Rows.blurText();
        t.close(modified);
        minecraft.setScreen(parent);
    }

    // ── Rows.Ctx ─────────────────────────────────────────────────────────────
    @Override public Font font() { return font; }
    @Override public void clickSound() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }
    @Override public void tooltip(String text) { tooltip = text; }
    @Override public void hand() { wantHand = true; }
    @Override public boolean shift() { return minecraft.hasShiftDown(); }
}
