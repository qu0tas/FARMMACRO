package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ConfigProfiles;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.Detectors;
import com.farmmacro.panic.PanicOverlayRenderer;
import com.farmmacro.panic.PanicSound;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * v1.14.2-pre: мастер первого запуска (7 шагов). Показывается один раз в главном меню (welcomeDone = false),
 * снова — «Конфиги → Мастер настройки». Esc / «Пропустить» — закрыть без изменений (больше не показывать).
 */
public class WelcomeScreen extends Screen implements Rows.Ctx {
    private static final int PAGES = 7;
    private static final String[] TITLES = {"Добро пожаловать", "Где играешь", "Друзья", "Осторожность",
            "Звук и экран паники", "Удобство", "Готово"};

    private final Screen parent;
    private int page;
    private final List<Rows.Row> rows = new ArrayList<>();
    private double scroll;
    private String tooltip;
    private boolean wantHand;
    private String friendMsg = "";
    /** Что уже применено мастером (чтобы «Назад/Далее» не сбрасывали заново). */
    private String whereApplied, presetApplied;
    /** Показ красного экрана на странице звука: до какого момента (мс). */
    private long previewUntil;
    private static final long PREVIEW_MS = 3000;

    public WelcomeScreen(Screen parent) {
        super(Component.literal("Мастер настройки Kaelix"));
        this.parent = parent;
    }

    private static ModConfig cfg() { return ModConfig.INSTANCE; }
    private static void save() { ModConfig.save(); }

    private int wW() { return Math.min(380, width - 16); }
    private int wH() { return Math.min(300, height - 16); }
    private int wX() { return (width - wW()) / 2; }
    private int wY() { return (height - wH()) / 2; }
    private int cX() { return wX() + 10; }
    private int cY() { return wY() + 30; }
    private int cW() { return wW() - 20; }
    private int cH() { return wH() - 30 - 32; }
    private int bY() { return wY() + wH() - 26; }

    @Override
    protected void init() { if (rows.isEmpty()) build(); }

    // ── Действия мастера ─────────────────────────────────────────────────────

    /** Где играешь: набор детекторов Skyblock и команда после переподключения. */
    static void applyWhere(String where) {
        ModConfig c = cfg();
        switch (where) {
            case "skyblock" -> {
                c.detectRestart = true; c.detectVisitor = true; c.detectStaffNear = true; c.staffTab = true;
                c.detectPm = true; c.detectAfkMsg = true;
                c.reconnectCommand = "/warp garden";
            }
            case "friends" -> {
                c.detectVisitor = false; c.detectJacob = false; c.detectBanwave = false;
                c.detectStaffNear = false; c.staffTab = false;
                c.reconnectCommand = "";
            }
            default -> {
                c.detectVisitor = false; c.detectJacob = false; c.detectBanwave = false;
                c.detectStaffNear = true;
                c.reconnectCommand = "";
            }
        }
        save();
    }

    private void applyChoices() {
        String w = cfg().welcomeWhere, p = cfg().welcomePreset;
        if (page >= 3 && !p.equals(presetApplied)) {          // набор меняет детекторы — «где играешь» поверх него
            ConfigProfiles.applyPreset(p);
            presetApplied = p;
            whereApplied = null;
        }
        if (page >= 1 && !w.equals(whereApplied)) { applyWhere(w); whereApplied = w; }
    }

    private void go(int to) {
        Rows.blurText();
        Rows.clearWheelFocus();
        if (to > page) applyChoices();
        page = Math.max(0, Math.min(PAGES - 1, to));
        scroll = 0;
        build();
    }

    private void finish() {
        applyChoices();
        cfg().welcomeDone = true;
        save();
        close();
    }

    private void close() {
        Rows.clearWheelFocus();
        Rows.blurText();
        minecraft.setScreen(parent);
    }

    // ── Страницы ─────────────────────────────────────────────────────────────

    private void build() {
        rows.clear();
        switch (page) {
            case 0 -> pageIntro();
            case 1 -> pageWhere();
            case 2 -> pageFriends();
            case 3 -> pagePreset();
            case 4 -> pageSound();
            case 5 -> pageComfort();
            default -> pageSummary();
        }
    }

    private void pageIntro() {
        rows.add(new Rows.Note("Kaelix записывает твой проход по ферме (или маршрут из точек) и повторяет его. "
                + "Если что-то не так — сервер повернул камеру, телепортировал, подошёл игрок — срабатывает паника: "
                + "макрос сразу останавливается, экран краснеет и звучит сигнал. Дальше решаешь ты.", Ui.ACCENT));
        rows.add(new Rows.Section("Главные клавиши"));
        rows.add(key("Запись прохода", FarmMacroMod.keyRecord));
        rows.add(key("Пуск / стоп", FarmMacroMod.keyPlay));
        rows.add(key("Продолжить с места остановки", FarmMacroMod.keyResume));
        rows.add(key("Стоп и сброс всего", FarmMacroMod.keyResetPos));
        rows.add(key("Меню мода", FarmMacroMod.keyOpenGui));
        rows.add(key("Редактор маршрута", FarmMacroMod.keyEditor));
        rows.add(key("Low render (не рисовать дальние блоки)", FarmMacroMod.keyLowRender));
        rows.add(key("Свободный курсор", FarmMacroMod.keyUngrab));
        rows.add(key("Экономия ресурсов", FarmMacroMod.keyPerf));
        rows.add(key("Freelook (осмотреться)", FarmMacroMod.keyFreelook));
        rows.add(key("Редактор HUD", FarmMacroMod.keyHudEdit));
        rows.add(new Rows.Note("Клавиши меняются в «Настройки → Управление → Kaelix». Мастер займёт минуту; "
                + "«Пропустить» — оставить всё по умолчанию.", Ui.SUB));
    }

    private Rows.Row key(String label, KeyMapping k) {
        return new Info(label, () -> MacroManager.keyName(k));
    }

    private void pageWhere() {
        rows.add(new Rows.Note("От этого зависят проверки Skyblock и команда после переподключения.", Ui.SUB));
        rows.add(new Choice("skyblock", "Hypixel Skyblock",
                "Перезагрузка/лимбо, гости на ферме, staff рядом и в Tab, личные сообщения, «You are AFK». "
                        + "После переподключения — /warp garden.", () -> cfg().welcomeWhere, v -> cfg().welcomeWhere = v));
        rows.add(new Choice("friends", "Свой сервер с друзьями",
                "Без проверок Skyblock (гости, Jacob, банвейв) и без поиска staff. Команды после переподключения нет.",
                () -> cfg().welcomeWhere, v -> cfg().welcomeWhere = v));
        rows.add(new Choice("other", "Другой сервер",
                "Без проверок Skyblock, но staff рядом ищем. Команды после переподключения нет.",
                () -> cfg().welcomeWhere, v -> cfg().welcomeWhere = v));
    }

    private void pageFriends() {
        rows.add(new Rows.Note("Ники друзей: на них не срабатывают «Игрок рядом», вход/выход, чат, личные сообщения и "
                + "массовый телепорт (галочки — «Паника → Друзья»). Регистр не важен.", Ui.SUB));
        rows.add(new Rows.TextField("Друзья", "Ники через запятую, Enter — принять", () -> cfg().friendNames, v -> {
            cfg().friendNames = v.strip();
            save();
            return true;
        }, null, 1000, 0));
        rows.add(new Rows.Buttons(new Rows.Btn("Добавить игроков рядом", Rows.Style.SECONDARY, this::addNearby)
                .enabledIf(() -> minecraft != null && minecraft.level != null)
                .tip("Все игроки из Tab в 32 блоках (нужно быть в мире)")));
        rows.add(new Rows.Note(() -> minecraft != null && minecraft.level == null
                ? "«Добавить игроков рядом» работает в мире — потом можно в «Паника → Друзья»." : friendMsg, Ui.ACCENT));
        rows.add(new Rows.Note(() -> {
            int n = com.farmmacro.panic.PlayerRules.parseNames(cfg().friendNames).size();
            return n == 0 ? "Друзей пока нет — можно пропустить." : "Друзей: " + n;
        }, Ui.SUB));
    }

    private void addNearby() {
        var mc = minecraft;
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) { friendMsg = "Нужно быть в мире"; return; }
        String list = cfg().friendNames;
        int added = 0;
        for (var o : mc.level.players()) {
            if (o == mc.player || o.distanceTo(mc.player) > 32) continue;
            if (mc.getConnection().getPlayerInfo(o.getUUID()) == null) continue;
            String name = o.getGameProfile().name();
            if (!com.farmmacro.panic.PlayerRules.validName(name)) continue;
            String next = com.farmmacro.panic.PlayerRules.addName(list, name);
            if (!next.equals(list)) { list = next; added++; }
        }
        cfg().friendNames = list;
        save();
        friendMsg = added == 0 ? "Новых игроков рядом нет" : "Добавлено: " + added;
    }

    private void pagePreset() {
        rows.add(new Rows.Note("Насколько чутко ловить подозрительное. Меняет детекторы и случайность движений; "
                + "свои действия из таблицы «Все детекторы» сбрасываются.", Ui.SUB));
        rows.add(new Choice("optimal", "Оптимально (рекомендую)",
                "Почти все детекторы, без «игрока рядом» и входа/выхода; звук и экран паники по умолчанию.",
                () -> cfg().welcomePreset, v -> cfg().welcomePreset = v));
        rows.add(new Choice("careful", "Осторожно",
                "Все детекторы, игрок рядом — 24 бл, теснее лимит подозрительности, больше случайности.",
                () -> cfg().welcomePreset, v -> cfg().welcomePreset = v));
        rows.add(new Choice("soft", "Мягко",
                "Для лагающих и людных серверов: меньше ложных срабатываний, мелочи — в очки подозрительности.",
                () -> cfg().welcomePreset, v -> cfg().welcomePreset = v));
        rows.add(new Rows.Note("Потом всё можно поменять: «Конфиги → Готовые наборы» и «Паника».", Ui.SUB));
    }

    private void pageSound() {
        rows.add(new Rows.Toggle("Звук паники", null, () -> cfg().panicSoundEnabled, v -> { cfg().panicSoundEnabled = v; save(); }));
        rows.add(new Rows.Selector("Сигнал", "Свои .ogg/.wav — в config/farmmacro/sounds", () -> {
            List<Rows.Choice> l = new ArrayList<>();
            for (PanicSound.Option o : PanicSound.options()) l.add(new Rows.Choice(o.id(), o.label()));
            return l;
        }, () -> cfg().panicSound, v -> { cfg().panicSound = v; save(); }).enabledIf(() -> cfg().panicSoundEnabled));
        rows.add(new Rows.Number("Громкость", null, () -> cfg().panicSoundVolume, v -> { cfg().panicSoundVolume = (float) v; save(); },
                0, 1, 0.05, 0.25, v -> Math.round(v * 100) + " %").enabledIf(() -> cfg().panicSoundEnabled));
        rows.add(new Rows.Buttons(new Rows.Btn("▶ Прослушать", Rows.Style.SECONDARY, () -> PanicSound.playPanic())
                .enabledIf(() -> cfg().panicSoundEnabled)));
        rows.add(new Rows.Toggle("Красный экран", "Вспышка и причина паники на весь экран", () -> cfg().panicRedScreenEnabled,
                v -> { cfg().panicRedScreenEnabled = v; save(); }));
        rows.add(new Rows.Buttons(new Rows.Btn("Показать экран", Rows.Style.DANGER, () -> previewUntil = System.currentTimeMillis() + PREVIEW_MS)
                .enabledIf(() -> cfg().panicRedScreenEnabled).tip("Так будет выглядеть паника (3 с, без звука)")));
    }

    private void pageComfort() {
        rows.add(new Rows.Toggle("Свободный курсор при запуске", "Мышь не захвачена игрой, пока макрос работает — можно в другое окно ("
                + MacroManager.keyName(FarmMacroMod.keyUngrab) + ")", () -> cfg().ungrabOnStart, v -> { cfg().ungrabOnStart = v; save(); }));
        rows.add(new Rows.Toggle("Экономия ресурсов при запуске", "Меньше FPS и эффектов, пока макрос работает ("
                + MacroManager.keyName(FarmMacroMod.keyPerf) + ")", () -> cfg().perfOnStart, v -> { cfg().perfOnStart = v; save(); }));
        rows.add(new Rows.Number("FPS в экономии", null, () -> cfg().perfFps, v -> { cfg().perfFps = (int) v; save(); },
                10, 260, 5, 30, v -> String.valueOf((int) v)).integer().under(() -> cfg().perfOnStart));
        rows.add(new Rows.Number("Low render: радиус", "Рисовать блоки только рядом (" + MacroManager.keyName(FarmMacroMod.keyLowRender)
                + " — вкл/выкл)", () -> cfg().lowRenderRadius, v -> { cfg().lowRenderRadius = (int) v; save(); },
                1, 32, 1, 4, v -> (int) v + " бл").integer());
        rows.add(new Rows.Note("Паника всегда возвращает курсор, графику и обычный вид — чтобы ты сразу видел, что происходит.", Ui.SUB));
    }

    private void pageSummary() {
        rows.add(new Rows.Note(this::summary, Ui.ON));
        rows.add(new Rows.Note("Дальше: запиши проход (" + MacroManager.keyName(FarmMacroMod.keyRecord) + ") или поставь точки в редакторе ("
                + MacroManager.keyName(FarmMacroMod.keyEditor) + "), потом " + MacroManager.keyName(FarmMacroMod.keyPlay)
                + " — пуск. Перед фермой нажми «Паника → Проверить панику».", Ui.SUB));
        rows.add(new Rows.Note("Мастер можно открыть снова: меню (" + MacroManager.keyName(FarmMacroMod.keyOpenGui)
                + ") → «Конфиги» → «Мастер настройки».", Ui.SUB));
    }

    private String summary() {
        ModConfig c = cfg();
        String where = switch (c.welcomeWhere) { case "skyblock" -> "Hypixel Skyblock"; case "friends" -> "свой сервер с друзьями"; default -> "другой сервер"; };
        String preset = switch (c.welcomePreset) { case "careful" -> "Осторожно"; case "soft" -> "Мягко"; default -> "Оптимально"; };
        int friends = com.farmmacro.panic.PlayerRules.parseNames(c.friendNames).size();
        List<String> comfort = new ArrayList<>();
        if (c.ungrabOnStart) comfort.add("свободный курсор");
        if (c.perfOnStart) comfort.add("экономия " + c.perfFps + " FPS");
        comfort.add("low render " + c.lowRenderRadius + " бл");
        return "Где: " + where + (c.reconnectCommand.isEmpty() ? "" : " (после переподключения " + c.reconnectCommand + ")")
                + "\nНабор: " + preset
                + "\nДрузей: " + friends
                + "\nДетекторов включено: " + Detectors.enabledCount() + " из " + Detectors.all().size()
                + "\nЗвук: " + (c.panicSoundEnabled ? PanicSound.label(c.panicSound) + ", " + Math.round(c.panicSoundVolume * 100) + " %" : "выкл")
                + " · красный экран: " + (c.panicRedScreenEnabled ? "да" : "нет")
                + "\nУдобство: " + String.join(", ", comfort);
    }

    // ── Свои строки ──────────────────────────────────────────────────────────

    /** Подпись слева, значение справа (клавиши). */
    static final class Info extends Rows.Row {
        private final String label;
        private final Supplier<String> value;
        Info(String label, Supplier<String> value) { this.label = label; this.value = value; }
        int height(Rows.Ctx c, int w) { return 16; }
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            Font f = c.font();
            Ui.round(g, x, y, w, 14, 3, Ui.CARD);
            String v = value.get();
            int vw = f.width(v) + 10;
            Ui.text(g, f, Ui.ellipsize(f, label, w - vw - 16), x + 8, y + 3, Ui.TEXT);
            Ui.round(g, x + w - vw - 4, y + 1, vw, 12, 3, Ui.FIELD);
            Ui.text(g, f, v, x + w - vw + 1, y + 3, Ui.ACCENT_HI);
        }
    }

    /** Карточка выбора (одна из нескольких): заголовок и описание. */
    static final class Choice extends Rows.Row {
        private final String id, title, desc;
        private final Supplier<String> get;
        private final java.util.function.Consumer<String> set;
        Choice(String id, String title, String desc, Supplier<String> get, java.util.function.Consumer<String> set) {
            this.id = id; this.title = title; this.desc = desc; this.get = get; this.set = set;
        }
        private List<net.minecraft.util.FormattedCharSequence> lines(Rows.Ctx c, int w) {
            return c.font().split(Component.literal(desc), w - 30);
        }
        int height(Rows.Ctx c, int w) { return 20 + lines(c, w).size() * 10 + 4; }
        void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean sel = id.equals(get.get());
            int h = height(c, w) - 4;
            if (sel) Ui.round(g, x - 1, y - 1, w + 2, h + 2, 5, Ui.ACCENT);
            Ui.listBg(g, x, y, w, h, 4, false, hover);
            Ui.circle(g, x + 12, y + 10, 4, sel ? Ui.ACCENT_HI : Ui.TRACK_OFF);
            if (sel) Ui.circle(g, x + 12, y + 10, 2, Ui.ON_ACCENT);
            Ui.text(g, c.font(), title, x + 22, y + 6, sel ? Ui.TEXT : Ui.SUB);
            int ly = y + 19;
            for (var l : lines(c, w)) { g.text(c.font(), l, x + 22, ly, Ui.SUB, false); ly += 10; }
            if (hover) c.hand();
        }
        boolean click(Rows.Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            c.clickSound();
            set.accept(id);
            save();
            return true;
        }
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    private record Btn(String label, Rows.Style style, int x, int w, Runnable action, BooleanSupplier enabled) {}

    private List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        int x = wX(), w = wW();
        l.add(new Btn("Пропустить", Rows.Style.SECONDARY, x + 10, 80, this::skip, () -> page < PAGES - 1));
        boolean last = page == PAGES - 1;
        l.add(new Btn(last ? "Готово" : "Далее", last ? Rows.Style.SUCCESS : Rows.Style.PRIMARY, x + w - 10 - 80, 80,
                () -> { if (last) finish(); else go(page + 1); }, () -> true));
        l.add(new Btn("Назад", Rows.Style.SECONDARY, x + w - 10 - 80 - 4 - 64, 64, () -> go(page - 1), () -> page > 0));
        return l;
    }

    private void skip() {
        cfg().welcomeDone = true;
        save();
        close();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        tooltip = null; wantHand = false;
        int x = wX(), y = wY(), w = wW(), h = wH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.window(g, x, y, w, h, 8);
        Ui.text(g, font, TITLES[page], x + 10, y + 9, Ui.TEXT);
        Ui.textRight(g, font, "Шаг " + (page + 1) + " из " + PAGES, x + w - 10, y + 9, Ui.SUB);
        g.fill(x + 10, y + 22, x + w - 10, y + 23, Ui.BORDER);

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
        if (total > ch) {                                     // полоса прокрутки
            int bh = Math.max(16, ch * ch / total), by = cy + (int) ((ch - bh) * (scroll / (total - ch)));
            Ui.round(g, x + w - 6, by, 3, bh, 1, Ui.BORDER);
        }

        // низ: точки шагов и кнопки
        g.fill(x + 10, bY() - 6, x + w - 10, bY() - 5, Ui.BORDER);
        int dotsW = PAGES * 10, dx = x + w / 2 - dotsW / 2 - 30;
        for (int i = 0; i < PAGES; i++)
            Ui.circle(g, dx + i * 10 + 5, bY() + 9, i == page ? 3 : 2, i == page ? Ui.ACCENT_HI : i < page ? Ui.SUB : Ui.TRACK_OFF);
        for (Btn b : buttons())
            if (b.enabled().getAsBoolean()) Rows.drawButton(this, g, b.x(), bY(), b.w(), 18, b.label(), b.style(), true, mx, my);

        // «Показать экран»: красный экран поверх мастера (HUD под экранами не рисуется)
        long left = previewUntil - System.currentTimeMillis();
        if (left > 0) {
            g.nextStratum();
            PanicOverlayRenderer.draw(g, width, height, Math.min(1, left / (float) PREVIEW_MS * 1.5f), "Проверка — так выглядит паника");
        }

        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        if (tooltip != null) {
            g.nextStratum();
            var lines = font.split(Component.literal(tooltip), Math.min(220, width - 20));
            int tw = 0;
            for (var l : lines) tw = Math.max(tw, font.width(l));
            int tx = Math.min(mx + 10, width - tw - 14), ty = my + 12;
            if (ty + lines.size() * 10 + 8 > height - 4) ty = my - lines.size() * 10 - 10;
            Ui.roundBordered(g, tx, ty, tw + 12, lines.size() * 10 + 8, 4, Ui.TOOLTIP, Ui.BORDER);
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
        if (e.button() == 0) {
            for (Btn b : buttons()) {
                if (b.enabled().getAsBoolean() && Ui.inside(mx, my, b.x(), bY(), b.w(), 18)) {
                    clickSound();
                    b.action().run();
                    return true;
                }
            }
        }
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
        if (e.key() == 257 || e.key() == 335) { if (page == PAGES - 1) finish(); else go(page + 1); return true; }   // Enter
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (Rows.textChar(e)) return true;
        return super.charTyped(e);
    }

    @Override public boolean isPauseScreen() { return true; }

    /** Esc — как «Пропустить»: закрыть, больше не показывать сам. */
    @Override public void onClose() { skip(); }

    // ── Rows.Ctx ─────────────────────────────────────────────────────────────
    @Override public Font font() { return font; }
    @Override public void clickSound() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }
    @Override public void tooltip(String text) { tooltip = text; }
    @Override public void hand() { wantHand = true; }
    @Override public boolean shift() { return minecraft.hasShiftDown(); }
}
