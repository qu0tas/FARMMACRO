package com.farmmacro.gui;

import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MacroStorage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Диалог «Сохранить макрос» в стиле меню мода: своё поле ввода (курсор, вставка, удаление слов),
 * предупреждение о перезаписи, Enter — сохранить, Esc — отмена (запись остаётся в буфере).
 */
public class SaveMacroScreen extends Screen implements Rows.Ctx {

    private static final int MAX_LEN = 48;

    private final Screen parent;
    /** Что сохраняем: макрос (кадры) или маршрут по точкам. */
    public interface Target {
        String title();
        String subtitle();
        String placeholder();
        boolean exists(String name);
        boolean save(String name);
        void saved(String name);
    }

    private final Target target;
    private String text;
    private int cursor;
    private String status = "";
    private int statusColor = Ui.SUB;
    private String confirmOverwrite;   // имя, для которого уже показали предупреждение
    private boolean wantHand, wantBeam;

    public SaveMacroScreen(Screen parent, List<MacroFrame> frames) { this(parent, frames, null); }

    public SaveMacroScreen(Screen parent, List<MacroFrame> frames, String defaultName) {
        this(parent, new Target() {
            public String title() { return "Сохранить макрос"; }
            public String subtitle() { return MacroManager.formatTicks(frames.size()) + " · " + frames.size() + " кадров"; }
            public String placeholder() { return "Название, например «Пшеница 3 этажа»"; }
            public boolean exists(String name) { return MacroStorage.INSTANCE.exists(name); }
            public boolean save(String name) { return MacroStorage.INSTANCE.save(name, frames); }
            public void saved(String name) { MacroManager.INSTANCE.markSaved(name); }
        }, defaultName);
    }

    public SaveMacroScreen(Screen parent, Target target, String defaultName) {
        super(Component.literal(target.title()));
        this.parent = parent;
        this.target = target;
        this.text = defaultName != null ? defaultName : "";
        this.cursor = text.length();
    }

    private int dW() { return Math.min(280, width - 20); }
    private int dH() { return 116; }
    private int dX() { return (width - dW()) / 2; }
    private int dY() { return (height - dH()) / 2; }
    private int fieldY() { return dY() + 40; }

    // ── Логика ───────────────────────────────────────────────────────────────

    private void trySave() {
        String name = text.trim();
        if (MacroStorage.sanitize(name).isEmpty()) { setStatus("Введи название", Ui.DANGER); return; }
        if (target.exists(name) && !name.equals(confirmOverwrite)) {
            confirmOverwrite = name;
            setStatus("Такой уже есть — нажми «Сохранить» ещё раз, чтобы заменить", Ui.WARN);
            return;
        }
        if (target.save(name)) {
            target.saved(name);
            if (minecraft.player != null)
                minecraft.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §aСохранено: «" + name + "»"));
            minecraft.setScreen(parent);
        } else {
            setStatus("Не удалось сохранить (подробности в логе)", Ui.DANGER);
        }
    }

    private void setStatus(String s, int color) { status = s; statusColor = color; }

    private void insert(String s) {
        StringBuilder clean = new StringBuilder();
        s.codePoints().filter(cp -> cp >= 32 && cp != 127).forEach(clean::appendCodePoint);
        String add = clean.toString();
        int room = MAX_LEN - text.length();
        if (room <= 0 || add.isEmpty()) return;
        if (add.length() > room) add = add.substring(0, room);
        text = text.substring(0, cursor) + add + text.substring(cursor);
        cursor += add.length();
        confirmOverwrite = null;
        status = "";
    }

    private int wordLeft() {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordRight() {
        int i = cursor;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        return i;
    }

    // ── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (!e.isAllowedChatCharacter()) return false;
        insert(e.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean ctrl = e.hasControlDown();
        switch (e.key()) {
            case 257, 335 -> { trySave(); return true; }                       // Enter
            case 256 -> { onClose(); return true; }                            // Esc
            case 259 -> {                                                      // Backspace
                if (cursor > 0) {
                    int from = ctrl ? wordLeft() : cursor - 1;
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                    confirmOverwrite = null;
                }
                return true;
            }
            case 261 -> {                                                      // Delete
                if (cursor < text.length()) {
                    int to = ctrl ? wordRight() : cursor + 1;
                    text = text.substring(0, cursor) + text.substring(to);
                    confirmOverwrite = null;
                }
                return true;
            }
            case 263 -> { cursor = ctrl ? wordLeft() : Math.max(0, cursor - 1); return true; }          // ←
            case 262 -> { cursor = ctrl ? wordRight() : Math.min(text.length(), cursor + 1); return true; } // →
            case 268 -> { cursor = 0; return true; }                           // Home
            case 269 -> { cursor = text.length(); return true; }               // End
            default -> { }
        }
        if (e.isPaste()) { insert(minecraft.keyboardHandler.getClipboard().replace('\n', ' ')); return true; }
        if (ctrl && e.key() == 65) { text = ""; cursor = 0; return true; }     // Ctrl+A — очистить поле
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int x = dX(), w = dW(), by = dY() + dH() - 28;
        int bw = (w - 28) / 2;
        if (Ui.inside(e.x(), e.y(), x + 10, by, bw, 18)) { clickSound(); trySave(); return true; }
        if (Ui.inside(e.x(), e.y(), x + 18 + bw, by, bw, 18)) { clickSound(); onClose(); return true; }
        if (Ui.inside(e.x(), e.y(), x + 10, fieldY(), w - 20, 18)) {
            // курсор туда, куда кликнули
            int rel = (int) e.x() - (x + 16);
            int best = text.length();
            for (int i = 0; i <= text.length(); i++) {
                if (font.width(text.substring(0, i)) >= rel) { best = i; break; }
            }
            cursor = best;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    // ── Отрисовка ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        wantHand = wantBeam = false;
        int x = dX(), y = dY(), w = dW(), h = dH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.round(g, x, y, w, h, 8, Ui.WINDOW);
        Ui.circle(g, x + 14, y + 14, 3, Ui.ACCENT);
        Ui.text(g, font, target.title(), x + 22, y + 10, Ui.TEXT);
        Ui.text(g, font, target.subtitle(),
                x + 12, y + 24, Ui.SUB);

        // поле ввода
        int fy = fieldY(), fw = w - 20;
        Ui.roundBordered(g, x + 10, fy, fw, 18, 4, Ui.FIELD, Ui.ACCENT);
        if (Ui.inside(mx, my, x + 10, fy, fw, 18)) wantBeam = true;
        int tx = x + 16;
        if (text.isEmpty()) {
            Ui.text(g, font, target.placeholder(), tx, fy + 5, Ui.DIM);
        }
        // если текст длиннее поля — показываем хвост
        String visible = text;
        int offset = 0;
        while (font.width(visible) > fw - 14 && offset < cursor) { offset++; visible = text.substring(offset); }
        visible = font.plainSubstrByWidth(visible, fw - 12);
        Ui.text(g, font, visible, tx, fy + 5, Ui.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + font.width(text.substring(offset, Math.max(offset, Math.min(cursor, offset + visible.length()))));
            g.fill(cx, fy + 4, cx + 1, fy + 14, Ui.ACCENT_HI);
        }

        if (!status.isEmpty()) {
            Ui.text(g, font, Ui.ellipsize(font, status, w - 24), x + 12, fy + 24, statusColor);
        } else {
            Ui.text(g, font, Ui.ellipsize(font, "Enter — сохранить · Esc — отмена (запись останется)", w - 24),
                    x + 12, fy + 24, Ui.DIM);
        }

        int by = y + h - 28, bw = (w - 28) / 2;
        Rows.drawButton(this, g, x + 10, by, bw, 18, "Сохранить", Rows.Style.PRIMARY, true, mx, my);
        Rows.drawButton(this, g, x + 18 + bw, by, bw, 18, "Отмена", Rows.Style.SECONDARY, true, mx, my);
        if (wantHand) g.requestCursor(CursorTypes.POINTING_HAND);
        else if (wantBeam) g.requestCursor(CursorTypes.IBEAM);
    }

    @Override public Font font() { return font; }
    @Override public void clickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
    @Override public void tooltip(String t) { }
    @Override public void hand() { wantHand = true; }
    @Override public boolean shift() { return minecraft.hasShiftDown(); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
