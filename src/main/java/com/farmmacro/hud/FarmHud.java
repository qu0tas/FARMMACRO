package com.farmmacro.hud;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.util.Guard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import com.farmmacro.config.HudSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HUD мода (правый верхний угол): панель статуса, навигатор к точке старта/остановки,
 * крупный обратный отсчёт по центру. Стиль совпадает с меню (палитра {@link Ui}).
 */
public final class FarmHud {
    private FarmHud() {}

    static final int MARGIN = HudLayout.MARGIN;
    static final int PANEL_W = 132, WIDE = PANEL_W + 20, GAP = 4;

    /** v1.14 «Одна общая плашка»: блоки рисуются без своей подложки внутри общей панели. */
    private static boolean merged;
    /** Цель навигатора на этот кадр. */
    private static Target target;

    /** Блок HUD: g == null — только посчитать высоту. @return высота (0 — блоку нечего показать) */
    interface Drawer { int draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w); }

    public record Block(String id, String label, int width, Drawer drawer) {}

    /** Порядок — как в стопке справа (до v1.14 — единственный вариант). */
    public static final List<Block> BLOCKS = List.of(
            new Block("status", "Статус", PANEL_W, FarmHud::drawStatus),
            new Block("editor", "Редактор маршрута", WIDE, FarmHud::drawEditor),
            new Block("hold", "Зажим мыши", WIDE, FarmHud::drawHold),
            new Block("mouselock", "Блокировка мыши", WIDE, FarmHud::drawMouseLock),
            new Block("view", "Freelook · Low render · PERF", WIDE, FarmHud::drawView),
            new Block("human", "Случайность", WIDE, FarmHud::drawHuman),
            new Block("return", "Возврат на маршрут", WIDE, FarmHud::drawReturn),
            new Block("delays", "Задержки рук (отладка)", 210, FarmHud::drawDelays),
            new Block("susp", "Подозрительность", WIDE, FarmHud::drawSuspicion),
            new Block("server", "TPS · BPS · баны", WIDE, FarmHud::drawServer),
            new Block("schedule", "Расписание и выход", WIDE, FarmHud::drawSchedule),
            new Block("nav", "Навигатор", PANEL_W, FarmHud::drawNavigator));

    /** Место плашки на экране (для редактора HUD). id "all" — общая панель; sub — строка внутри общей панели. */
    public record Rect(String id, String label, int x, int y, int w, int h, boolean hidden, boolean sub) {
        public boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "hud"), Guard.hud("hud", (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            Ui.sync(ModConfig.INSTANCE.uiTheme);
            if (mc.player == null || mc.options.hideGui) return;
            if (mc.screen instanceof HudEditorScreen) return;           // редактор рисует HUD сам
            if (mc.screen == null) {
                float pt = delta.getGameTimeDeltaPartialTick(true);
                target = target(mc, pt);
                layout(g, mc, false);
                drawCrosshairArrow(g, mc, target);
            }
            drawCountdown(g, mc);
        }));
    }

    /** Для редактора HUD: цель навигатора по текущему кадру. */
    static void prepare(Minecraft mc) { target = mc.player != null ? target(mc, 1f) : null; }

    private static HudSlot slot(String id) { return ModConfig.INSTANCE.hudSlots.get(id); }

    /**
     * v1.14: раскладка HUD. Без мест из редактора — стопка у правого края, как раньше. g == null — только места.
     * edit — режим редактора: пустые блоки показываются заглушкой, скрытые — затемнёнными.
     */
    public static List<Rect> layout(GuiGraphicsExtractor g, Minecraft mc, boolean edit) {
        ModConfig c = ModConfig.INSTANCE;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        List<Rect> out = new ArrayList<>();
        Font f = mc.font;
        if (c.hudMerged) {
            int w = c.hudWidth + 20;
            List<Block> list = new ArrayList<>();
            List<Integer> hs = new ArrayList<>();
            merged = true;
            try {
                for (Block b : BLOCKS) {
                    HudSlot sl = slot(b.id());
                    boolean hid = sl != null && sl.hidden;
                    if (hid && !edit) continue;
                    int h = b.drawer().draw(null, mc, 0, 0, w);
                    if (h <= 0 && !edit) continue;
                    list.add(b);
                    hs.add(h <= 0 ? -12 : h);                         // минус — заглушка в редакторе
                }
            } finally { merged = false; }
            if (list.isEmpty() && !edit) return out;
            int total = 4;
            for (int h : hs) total += Math.abs(h) + 2;
            total = Math.max(total, 16);
            HudSlot all = slot("all");
            double sc = (all != null ? all.scale : 1) * c.hudScale;
            int pw = (int) Math.round(w * sc), ph = (int) Math.round(total * sc);
            int[] pos = all != null && all.placed ? HudLayout.place(all, pw, ph, sw, sh) : new int[]{sw - pw - MARGIN, MARGIN};
            out.add(new Rect("all", "Общая плашка", pos[0], pos[1], pw, ph, false, false));
            int yy = 2;
            Matrix3x2fStack pose = g != null ? g.pose() : null;
            if (pose != null) { pose.pushMatrix(); pose.translate(pos[0], pos[1]); pose.scale((float) sc, (float) sc); }
            try {
                if (g != null) Ui.round(g, 0, 0, w, total, 5, bg());
                if (list.isEmpty() && g != null) Ui.text(g, f, "Общая плашка (пусто)", 8, 4, Ui.DIM);
                merged = true;
                for (int i = 0; i < list.size(); i++) {
                    Block b = list.get(i);
                    int h = hs.get(i);
                    boolean empty = h < 0;
                    h = Math.abs(h);
                    HudSlot sl = slot(b.id());
                    boolean hid = sl != null && sl.hidden;
                    if (g != null) {
                        if (empty) Ui.text(g, f, Ui.ellipsize(f, b.label() + " — сейчас пусто", w - 14), 8, yy + 2, Ui.DIM);
                        else b.drawer().draw(g, mc, 0, yy, w);
                        if (edit && hid) dimHidden(g, f, 0, yy, w, h);
                    }
                    out.add(new Rect(b.id(), b.label(), pos[0], pos[1] + (int) Math.round(yy * sc), pw, (int) Math.round(h * sc), hid, true));
                    yy += h + 2;
                }
            } finally {
                merged = false;
                if (pose != null) pose.popMatrix();
            }
            return out;
        }
        int stack = MARGIN;
        for (Block b : BLOCKS) {
            HudSlot sl = slot(b.id());
            boolean hid = sl != null && sl.hidden;
            if (hid && !edit) continue;
            int bw = width(b);
            int h = b.drawer().draw(null, mc, 0, 0, bw);
            boolean empty = h <= 0;
            if (empty && !edit) continue;
            if (empty) h = 15;
            double sc = (sl != null ? sl.scale : 1) * c.hudScale;
            int pw = (int) Math.round(bw * sc), ph = (int) Math.round(h * sc);
            int x, y;
            if (sl != null && sl.placed) { int[] p = HudLayout.place(sl, pw, ph, sw, sh); x = p[0]; y = p[1]; }
            else { x = sw - pw - MARGIN; y = stack; stack += ph + GAP; }
            out.add(new Rect(b.id(), b.label(), x, y, pw, ph, hid, false));
            if (g == null) continue;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(x, y);
                pose.scale((float) sc, (float) sc);
                if (empty) {
                    Ui.round(g, 0, 0, bw, h, 5, Ui.alpha(Ui.HUD_BG, 0.72f));
                    Ui.text(g, f, Ui.ellipsize(f, b.label() + " — сейчас пусто", bw - 14), 8, 4, Ui.DIM);
                } else b.drawer().draw(g, mc, 0, 0, bw);
                if (edit && hid) dimHidden(g, f, 0, 0, bw, h);
            } finally {
                pose.popMatrix();
            }
        }
        return out;
    }

    private static void dimHidden(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xB0000000);
        Ui.text(g, f, "скрыто · ПКМ — показать", x + w - f.width("скрыто · ПКМ — показать") - 4, y + Math.max(0, h / 2 - 4), Ui.WARN);
    }

    /** Подложка и цветная полоска слева (в общей плашке — только полоска). */
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        if (!merged) Ui.round(g, x, y, w, h, 5, bg());
        g.fill(x, y + 3, x + 2, y + h - 3, col);
    }

    // ── Цвета HUD (Визуал → HUD): «по умолчанию» — как раньше ─────────────────

    /** Фон панелей: по умолчанию тёмно-синий 78 %, иначе выбранный цвет с той же прозрачностью. */
    private static int bg() {
        int rgb = com.farmmacro.visual.HatColors.rgbOr(ModConfig.INSTANCE.hudBgColor, -1);
        return rgb < 0 ? Ui.HUD_BG : 0xC8000000 | rgb;
    }

    private static int accent() {
        int rgb = com.farmmacro.visual.HatColors.rgbOr(ModConfig.INSTANCE.hudAccentColor, -1);
        return rgb < 0 ? Ui.ACCENT : 0xFF000000 | rgb;
    }

    private static int accentHi() {
        int rgb = com.farmmacro.visual.HatColors.rgbOr(ModConfig.INSTANCE.hudAccentColor, -1);
        return rgb < 0 ? Ui.ACCENT_HI : 0xFF000000 | rgb;
    }

    // ── Статус ───────────────────────────────────────────────────────────────

    private static int drawStatus(GuiGraphicsExtractor g, Minecraft mc, int x0, int y, int w0) {
        MacroManager m = MacroManager.INSTANCE;
        ModConfig c = ModConfig.INSTANCE;
        Font f = mc.font;
        MacroManager.State st = m.getState();

        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String head; int col;
        switch (st) {
            case RECORDING -> { head = "ЗАПИСЬ · " + MacroManager.formatTicks(m.getFrameCount()); col = Ui.DANGER; }
            case COUNTDOWN -> { head = m.getCountdownTicks() > 0 ? "СТАРТ ЧЕРЕЗ " + (m.getCountdownTicks() + 19) / 20 : "ПОВОРОТ КАМЕРЫ"; col = Ui.WARN; }
            case PLAYING -> { head = "ИГРАЕТ"; col = Ui.ON; }
            default -> { head = "СТОП"; col = Ui.SUB; }
        }

        if (!c.statsHudEnabled) {
            if (st == MacroManager.State.IDLE) return 0;
            // компактная «пилюля», чтобы запись/игра никогда не шли незаметно
            if (g == null) return 14;
            String s = st == MacroManager.State.PLAYING ? "▶ " + percent(m) + "%" : head;
            int w = Math.min(w0, f.width(s) + 16);
            int x = x0 + w0 - w;
            if (!merged) Ui.pill(g, x, y, w, 14, Ui.alpha(Ui.HUD_BG, 0.96f));
            Ui.circle(g, x + 7, y + 7, 2, blink || st != MacroManager.State.RECORDING ? col : Ui.alpha(col, 0.3f));
            Ui.text(g, f, Ui.ellipsize(f, s, w - 14), x + 12, y + 3, Ui.TEXT);
            return 14;
        }
        if (st == MacroManager.State.IDLE && m.getSessionRuns() == 0) return 0;

        boolean playing = st == MacroManager.State.PLAYING;
        int h = playing ? 46 : 36;
        if (g == null) return h;
        int x = x0, W = w0;
        panel(g, x, y, W, h, col);

        Ui.circle(g, x + 9, y + 8, 2, blink || st != MacroManager.State.RECORDING ? col : Ui.alpha(col, 0.3f));
        Ui.text(g, f, Ui.ellipsize(f, head, W - 20), x + 15, y + 4, col);

        String name;
        if (m.getSourceKind() == MacroManager.SourceKind.ROUTE && st != MacroManager.State.RECORDING) {
            var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
            name = "⌁ " + (rb.loadedName() != null ? rb.loadedName() : rb.isEmpty() ? "пустой маршрут" : "маршрут не сохранён");
        } else {
            name = m.getLoadedName() != null ? m.getLoadedName()
                    : m.getFrameCount() > 0 ? "несохранённая запись" : "нет макроса";
        }
        Ui.text(g, f, Ui.ellipsize(f, name, W - 14), x + 8, y + 15, Ui.TEXT);

        String line;
        if (playing) {
            int pct = percent(m);
            int bx = x + 8, bw = W - 16, by = y + 27;
            Ui.pill(g, bx, by, bw, 4, 0xFF2A3245);
            Ui.pill(g, bx, by, Math.max(4, bw * pct / 100), 4, Ui.ON);
            long sec = (System.currentTimeMillis() - m.getRunStartMs()) / 1000;
            String loops = c.loopEnabled ? "круг " + (m.getLoopsDone() + 1) + (c.loopLimit > 0 ? "/" + c.loopLimit : "") + " · " : "";
            String prog = m.isRoutePlaying() ? "точка " + (m.getProgress() + 1) + "/" + m.getProgressTotal() : pct + "%";
            line = loops + prog + " · " + clock(sec);
            Ui.text(g, f, Ui.ellipsize(f, line, W - 14), x + 8, y + 35, Ui.SUB);
        } else {
            long sec = m.getSessionStartMs() > 0 ? (System.currentTimeMillis() - m.getSessionStartMs()) / 1000 : 0;
            line = st == MacroManager.State.RECORDING
                    ? MacroManager.keyName(FarmMacroMod.keyRecord) + " — остановить"
                    : "сессия " + clock(sec) + " · кругов " + m.getSessionRuns();
            Ui.text(g, f, Ui.ellipsize(f, line, W - 14), x + 8, y + 25, Ui.SUB);
        }
        return h;
    }

    private static int percent(MacroManager m) {
        int total = m.getProgressTotal();
        return total == 0 ? 0 : Math.min(100, m.getProgress() * 100 / total);
    }

    private static String clock(long sec) {
        return sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    // ── Редактор маршрута ────────────────────────────────────────────────────

    private static final String[] EDITOR_HINTS = {
            "ЛКМ — точка · зажать — двигать", "Shift+ЛКМ — вставить", "ПКМ — удалить",
            "Shift+ПКМ — параметры", "Alt+ЛКМ — выделить ещё · Ctrl+A — все",
            "Стрелки — сдвиг в блоке (Shift — мелко)", "Ctrl+D — спуск на этаж ниже", "Ctrl+Z — отменить"};

    private static int drawEditor(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        if (!com.farmmacro.route.RouteEditor.isActive()) return 0;
        Font f = mc.font;
        var rb = com.farmmacro.route.RouteBuffer.INSTANCE;
        String issue = com.farmmacro.visual.RouteRenderer.editorIssue;
        if (issue != null && com.farmmacro.visual.RouteRenderer.editorIssueCount > 1)
            issue += " (+" + (com.farmmacro.visual.RouteRenderer.editorIssueCount - 1) + ")";
        List<String> body = new ArrayList<>();
        List<Integer> bodyCol = new ArrayList<>();
        if (issue != null) for (String l : wrap(f, "⚠ " + issue, w - 14)) { body.add(l); bodyCol.add(0xFFFF5A5A); }
        for (String hint : EDITOR_HINTS) for (String l : wrap(f, hint, w - 14)) { body.add(l); bodyCol.add(Ui.SUB); }
        int h = 26 + body.size() * 10;
        if (g == null) return h;
        int col = 0xFFD08CFF;
        panel(g, x, y, w, h, col);
        Ui.text(g, f, Ui.ellipsize(f, "РЕДАКТОР · " + MacroManager.keyName(FarmMacroMod.keyEditor) + " — выход", w - 14), x + 8, y + 4, col);
        int sel = rb.selected();
        int many = rb.selection().size();
        var sp = rb.get(sel);
        String line = rb.size() + " точек" + (many > 1 ? " · выделено " + many : sel >= 0 ? " · выбрана " + (sel + 1) : "")
                + (sp != null && (sp.ox != 0 || sp.oz != 0) ? " · смещ. " + com.farmmacro.gui.Rows.num(sp.ox) + "/" + com.farmmacro.gui.Rows.num(sp.oz) : "")
                + (rb.isDirty() ? " · не сохранён" : "");
        Ui.text(g, f, Ui.ellipsize(f, line, w - 14), x + 8, y + 15, Ui.TEXT);
        int ly = y + 26;
        for (int i = 0; i < body.size(); i++) { Ui.text(g, f, body.get(i), x + 8, ly, bodyCol.get(i)); ly += 10; }
        return h;
    }

    // ── Однострочные плашки ──────────────────────────────────────────────────

    /**
     * v1.15.1: ширина блока — из «Визуал → HUD → Ширина» (узкие — статус и навигатор, остальные на 20 шире).
     */
    static int width(Block b) {
        int base = ModConfig.INSTANCE.hudWidth;
        return b.width() == PANEL_W ? base : b.width() == WIDE ? base + 20 : Math.max(b.width(), base + 60);
    }

    /** v1.15.1: перенос по словам (длинное слово — по символам) в ширину maxW; без переноса — одна строка с «…». */
    static List<String> wrap(Font f, String s, int maxW) {
        List<String> out = new ArrayList<>();
        if (!ModConfig.INSTANCE.hudWrap || f.width(s) <= maxW) { out.add(f.width(s) <= maxW ? s : Ui.ellipsize(f, s, maxW)); return out; }
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String tryS = cur.length() == 0 ? word : cur + " " + word;
            if (f.width(tryS) <= maxW) { cur.setLength(0); cur.append(tryS); continue; }
            if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            while (f.width(word) > maxW) {
                int n = 1;
                while (n < word.length() && f.width(word.substring(0, n + 1)) <= maxW) n++;
                out.add(word.substring(0, n));
                word = word.substring(n);
            }
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > 6) { List<String> cut = new ArrayList<>(out.subList(0, 6)); cut.set(5, Ui.ellipsize(f, cut.get(5) + "…", maxW)); return cut; }
        return out;
    }

    /** Плашка с текстом: одна строка — высота 15, длинный текст переносится (+10 на строку). */
    private static int line(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, String text, int col) {
        List<String> ls = wrap(mc.font, text, w - 14);
        int h = 5 + ls.size() * 10;
        if (g == null) return h;
        panel(g, x, y, w, h, col);
        for (int i = 0; i < ls.size(); i++) Ui.text(g, mc.font, ls.get(i), x + 8, y + 4 + i * 10, col);
        return h;
    }

    /** Плашка «ЗАЖИМ ЛКМ» — ручной зажим или зажим от играющего макроса. */
    private static int drawHold(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        var mh = com.farmmacro.macro.MouseHold.INSTANCE;
        boolean manual = mh.isManual();
        boolean attackNow = mh.wants(mc, mc.options.keyAttack), useNow = mh.wants(mc, mc.options.keyUse);
        if (!manual && !attackNow && !useNow) return 0;
        String text = "ЗАЖИМ " + (attackNow || manual ? "ЛКМ" : "") + (useNow ? (attackNow || manual ? " + ПКМ" : "ПКМ") : "")
                + (manual ? " · " + MacroManager.keyName(FarmMacroMod.keyHold) + " — выкл" : " · от макроса");
        return line(g, mc, x, y, w, text, 0xFFFFB347);
    }

    /** Плашка «СЛУЧАЙНОСТЬ · сид» — пока играет проход со случайностью. */
    private static int drawHuman(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        var h = com.farmmacro.macro.Humanizer.ACTIVE;
        if (h == null || !MacroManager.INSTANCE.isPlaying()) return 0;
        return line(g, mc, x, y, w, "СЛУЧАЙНОСТЬ · " + h.status() + " · сид " + h.seed, 0xFFE08CFF);
    }

    /** v1.15: плашка «ВОЗВРАТ НА МАРШРУТ · N бл» — пока идёт свой путь к маршруту. */
    private static int drawReturn(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        MacroManager m = MacroManager.INSTANCE;
        if (!m.isReturning()) return 0;
        var rw = m.returnWalker();
        String text = rw.turningToRoute() ? "ВОЗВРАТ · поворот к маршруту"
                : String.format(java.util.Locale.ROOT, "ВОЗВРАТ %s · %.1f бл%s", rw.what(), rw.remaining(mc.player),
                rw.replans() > 0 ? " · путь ×" + (rw.replans() + 1) : "");
        return line(g, mc, x, y, w, text, 0xFF7FD8FF);
    }

    /** v1.15: оверлей «Задержки рук»: усталость, отвлечения, последние задержки и разброс по видам. */
    private static int drawDelays(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.delayDebugHud || !c.delayLayers) return 0;
        var model = com.farmmacro.macro.HandDelays.MODEL;
        var last = model.last(6);
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> cols = new java.util.ArrayList<>();
        boolean on = com.farmmacro.macro.HandDelays.active();
        lines.add(String.format(java.util.Locale.ROOT, "ЗАДЕРЖКИ · усталость ×%.2f · отвлечений %d%s",
                model.fatigue(System.currentTimeMillis(), com.farmmacro.macro.HandDelays.params()), model.distracts(), on ? "" : " · ждут"));
        cols.add(0xFFFFD27F);
        for (int i = last.size() - 1; i >= 0; i--) { lines.add(last.get(i).line()); cols.add(last.get(i).distractMs() > 0 ? Ui.WARN : Ui.SUB); }
        for (var k : com.farmmacro.macro.DelayModel.Kind.values()) {
            var st = model.stats(k);
            if (st.n() == 0) continue;
            lines.add(String.format(java.util.Locale.ROOT, "%s: %.0f±%.0f мс, %.0f–%.0f (n=%d)", k.label, st.mean(), st.sd(), st.min(), st.max(), st.n()));
            cols.add(0xFFB0E0FF);
        }
        List<String> wl = new ArrayList<>();
        List<Integer> wc = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) for (String l : wrap(mc.font, lines.get(i), w - 14)) { wl.add(l); wc.add(cols.get(i)); }
        int h = 6 + wl.size() * 10;
        if (g == null) return h;
        panel(g, x, y, w, h, 0xFFFFD27F);
        for (int i = 0; i < wl.size(); i++) Ui.text(g, mc.font, wl.get(i), x + 8, y + 4 + i * 10, wc.get(i));
        return h;
    }

    /** v1.11: полоска «ПОДОЗР. N/10» — очки «Подозрительности», пока играет макрос. */
    private static int drawSuspicion(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.suspicionEnabled || !c.suspicionHud || !c.panicEnabled || !MacroManager.INSTANCE.isPlaying()) return 0;
        if (g == null) return 15;
        double s = com.farmmacro.panic.PanicDetector.INSTANCE.suspicionScore(), lim = c.suspicionLimit;
        double frac = Math.max(0, Math.min(1, s / lim));
        Font f = mc.font;
        int h = 15;
        int col = frac < 0.5 ? Ui.ON : frac < 0.8 ? Ui.WARN : Ui.DANGER;
        panel(g, x, y, w, h, col);
        String text = String.format(java.util.Locale.ROOT, "ПОДОЗР. %.1f/%.0f", s, lim);
        Ui.text(g, f, text, x + 8, y + 4, col);
        int bx = x + 8 + f.width(text) + 6, bw = Math.max(10, x + w - 8 - bx), by = y + 6;
        Ui.pill(g, bx, by, bw, 4, 0xFF2A3245);
        if (frac > 0) Ui.pill(g, bx, by, Math.max(4, (int) Math.round(bw * frac)), 4, col);
        return h;
    }

    /** Плашка «МЫШЬ ЗАБЛОК.» — блокировка мыши (клавиша M). */
    private static int drawMouseLock(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        if (!com.farmmacro.camera.MouseLock.isLocked()) return 0;
        return line(g, mc, x, y, w, "МЫШЬ ЗАБЛОК. · " + MacroManager.keyName(FarmMacroMod.keyMouseLock) + " — снять", 0xFF6EC8FF);
    }

    /** v1.13.1: плашка расписания / таймера выхода. */
    private static int drawSchedule(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        var ph = com.farmmacro.macro.Scheduler.phase();
        long leave = com.farmmacro.macro.Scheduler.leaveLeftMs();
        if (ph == com.farmmacro.macro.Scheduler.Phase.OFF && leave < 0) return 0;
        if (g == null) return 15;
        StringBuilder t = new StringBuilder();
        if (ph == com.farmmacro.macro.Scheduler.Phase.FARM) t.append("ПЕРЕРЫВ ЧЕРЕЗ ").append(clock(com.farmmacro.macro.Scheduler.farmLeftTicks() / 20));
        else if (ph == com.farmmacro.macro.Scheduler.Phase.BREAK) t.append("ПЕРЕРЫВ ").append(clock(com.farmmacro.macro.Scheduler.breakLeftTicks() / 20));
        if (leave >= 0) t.append(t.length() > 0 ? " · " : "").append("ВЫХОД ЧЕРЕЗ ").append(clock(leave / 1000));
        int col = ph == com.farmmacro.macro.Scheduler.Phase.BREAK ? Ui.WARN : 0xFFC8A8FF;
        return line(g, mc, x, y, w, t.toString(), col);
    }

    /** v1.13: плашка «TPS · BPS · баны» во время макроса. */
    private static int drawServer(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.serverHud || !MacroManager.INSTANCE.isPlaying()) return 0;
        if (g == null) return 15;
        double tps = com.farmmacro.panic.LagGuard.INSTANCE.tps();
        boolean lag = com.farmmacro.panic.LagGuard.INSTANCE.lagging();
        double bps = com.farmmacro.panic.PanicDetector.INSTANCE.bps();
        StringBuilder t = new StringBuilder();
        t.append(lag ? "ЛАГ СЕРВЕРА" : Double.isNaN(tps) ? "TPS —" : String.format(java.util.Locale.ROOT, "TPS %.1f", tps));
        t.append(Double.isNaN(bps) ? " · BPS —" : String.format(java.util.Locale.ROOT, " · BPS %.1f", bps));
        if (c.detectBanwave) {
            long b = com.farmmacro.panic.BanwaveWatch.bans();
            t.append(b < 0 ? " · баны —" : " · баны " + b + "/" + c.banwaveThreshold);
        }
        boolean bad = lag || (!Double.isNaN(bps) && c.detectBps && bps < c.bpsMin);
        return line(g, mc, x, y, w, t.toString(), bad ? Ui.WARN : 0xFF8FB8FF);
    }

    /** v1.12: плашки «FREELOOK» и «LOW RENDER». */
    private static int drawView(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        boolean fl = com.farmmacro.camera.Freelook.active(), lr = com.farmmacro.visual.LowRender.active();
        boolean ug = com.farmmacro.camera.Ungrab.active(), pf = com.farmmacro.visual.PerformanceMode.active();
        if (!fl && !lr && !ug && !pf) return 0;
        List<String> parts = new ArrayList<>();
        if (fl) parts.add("FREELOOK");
        if (lr) parts.add("LOW RENDER " + ModConfig.INSTANCE.lowRenderRadius + " бл");
        if (pf) parts.add("PERF " + ModConfig.INSTANCE.perfFps + " FPS");
        if (ug) parts.add("КУРСОР ОТПУЩЕН");
        return line(g, mc, x, y, w, String.join(" · ", parts), 0xFF9BE07A);
    }

    // ── Навигатор ────────────────────────────────────────────────────────────

    /** Цель навигатора, посчитанная по интерполированной позиции и взгляду игрока. */
    private record Target(String title, String hint, double dist, double dy, float relAngle, boolean arrived) {}

    /** null — навигатор не нужен (макрос идёт или цели нет). */
    private static Target target(Minecraft mc, float pt) {
        MacroManager m = MacroManager.INSTANCE;
        LocalPlayer p = mc.player;
        if (p == null || m.getState() != MacroManager.State.IDLE || m.isNavDismissed()) return null;
        double tx, ty, tz; String title, hint;
        if (m.savedMatchesSource()) {
            tx = m.getSavedX(); ty = m.getSavedY(); tz = m.getSavedZ();
            title = "Точка остановки";
            hint = MacroManager.keyName(FarmMacroMod.keyResume) + " — продолжить";
        } else {
            double[] start = m.getStartPosition();
            if (start == null) return null;
            tx = start[0]; ty = start[1]; tz = start[2];
            title = "Точка старта";
            hint = MacroManager.keyName(FarmMacroMod.keyPlay) + " — запуск";
        }
        var pos = p.getPosition(pt);
        double dist = Math.sqrt(sq(pos.x - tx) + sq(pos.z - tz));
        double dy = ty - pos.y;
        double targetYaw = Math.toDegrees(Math.atan2(tz - pos.z, tx - pos.x)) - 90.0;
        float rel = (float) Math.toRadians(targetYaw - p.getViewYRot(pt));
        // «на месте» — в пределах допуска точки старта (раньше панель в этот момент просто исчезала)
        boolean arrived = Math.sqrt(dist * dist + dy * dy) <= ModConfig.INSTANCE.startPointWarnDistance;
        return new Target(title, hint, dist, dy, rel, arrived);
    }

    private static int drawNavigator(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        Target t = target;
        if (t == null || !ModConfig.INSTANCE.navHudEnabled) return 0;
        int h = 36;
        if (g == null) return h;
        Font f = mc.font;
        int accent = t.arrived() ? Ui.ON : accent();

        float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 300.0));
        panel(g, x, y, w, h, Ui.alpha(accent, 0.5f + 0.5f * pulse));
        Ui.text(g, f, Ui.ellipsize(f, t.arrived() ? "✔ На месте" : t.title(), w - 44), x + 8, y + 4,
                t.arrived() ? Ui.ON : accentHi());
        Ui.text(g, f, distText(t), x + 8, y + 15, Ui.TEXT);
        Ui.text(g, f, Ui.ellipsize(f, t.hint(), w - 40), x + 8, y + 25, Ui.SUB);

        int ax = x + w - 18, ay = y + h / 2;
        Ui.circle(g, ax, ay, 12, 0xFF1C2230);
        if (t.dist() < 0.75) Ui.circle(g, ax, ay, 4, Ui.ON);
        else drawArrow(g, ax, ay, t.relAngle(), t.arrived() ? Ui.ON : accentHi());
        return h;
    }

    /** Маленькая стрелка вокруг прицела, указывает на цель; рядом расстояние. */
    private static void drawCrosshairArrow(GuiGraphicsExtractor g, Minecraft mc, Target t) {
        if (t == null || !ModConfig.INSTANCE.navCrosshairEnabled) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2, cy = sh / 2;
        float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 250.0));
        if (t.dist() < 0.6) {
            // стоим на точке: зелёное кольцо вокруг прицела
            int col = Ui.alpha(Ui.ON, 0.55f + 0.45f * pulse);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int px = cx + (int) Math.round(Math.cos(a) * 9), py = cy + (int) Math.round(Math.sin(a) * 9);
                g.fill(px - 1, py - 1, px + 1, py + 1, col);
            }
        } else {
            float r = 17f;
            int ax = cx + Math.round((float) Math.sin(t.relAngle()) * r);
            int ay = cy - Math.round((float) Math.cos(t.relAngle()) * r);
            drawArrow(g, ax, ay, t.relAngle(), Ui.alpha(t.arrived() ? Ui.ON : accentHi(), 0.75f + 0.25f * pulse));
        }
        String d = distText(t);
        Ui.textCentered(g, mc.font, d, cx, cy + 24, Ui.alpha(t.arrived() ? Ui.ON : 0xFFFFFFFF, 0.9f));
    }

    private static String distText(Target t) {
        return String.format(Locale.ROOT, "%.1f бл", t.dist())
                + (Math.abs(t.dy()) >= 1 ? String.format(Locale.ROOT, "  %s%.0f", t.dy() > 0 ? "↑" : "↓", Math.abs(t.dy())) : "");
    }

    /** Стрелка из прямоугольников, повёрнутая матрицей (0 = вперёд/вверх по экрану). */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float angle, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(cx, cy);
            pose.rotate(angle);
            g.fill(-1, -2, 1, 7, color);                 // древко
            for (int i = 0; i < 5; i++) g.fill(-i, -7 + i, i + 1, -6 + i, color);   // наконечник
        } finally {
            pose.popMatrix();
        }
    }

    private static double sq(double v) { return v * v; }

    // ── Обратный отсчёт ──────────────────────────────────────────────────────

    private static void drawCountdown(GuiGraphicsExtractor g, Minecraft mc) {
        MacroManager m = MacroManager.INSTANCE;
        if (!m.isCountingDown()) return;
        int ticks = m.getCountdownTicks();
        int sec = (ticks + 19) / 20;
        float frac = (ticks % 20) / 20f;              // 1 → 0 в течение секунды
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        try {
            float scale = 4f + frac * 1.5f;
            pose.translate(sw / 2f, sh / 2f - 30);
            pose.scale(scale, scale);
            String s = sec > 0 ? String.valueOf(sec) : "◎";      // 0 — ждём поворот к пресету культуры
            g.text(mc.font, s, -mc.font.width(s) / 2, -4, Ui.alpha(Ui.WARN, 0.35f + 0.65f * frac), true);
        } finally {
            pose.popMatrix();
        }
        Ui.textCentered(g, mc.font, "макрос стартует · " + MacroManager.keyName(FarmMacroMod.keyPlay) + " — отмена",
                sw / 2, sh / 2 - 4, Ui.SUB);
    }
}
