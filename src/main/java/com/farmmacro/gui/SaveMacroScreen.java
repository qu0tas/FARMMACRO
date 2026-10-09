package com.farmmacro.gui;

import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * SaveMacroScreen — маленькое диалоговое окно, которое появляется
 * сразу после остановки записи макроса.
 *
 * Игрок вводит название → нажимает Сохранить → макрос пишется в файл.
 * Нажимает Отмена → просто закрывается без сохранения.
 */
public class SaveMacroScreen extends Screen {

    private static final int BG_DARK    = 0xFF0D0D0D;
    private static final int ACCENT_RED = 0xFFE02020;
    private static final int TEXT_GRAY  = 0xFF888888;
    private static final int TEXT_LIGHT = 0xFFCCCCCC;
    private static final int BORDER     = 0xFF2A2A2A;

    private final Screen         parent;
    private final List<MacroFrame> frames;
    private TextFieldWidget      nameField;
    private String               statusMsg = "";
    private int                  statusColor = TEXT_LIGHT;

    public SaveMacroScreen(Screen parent, List<MacroFrame> frames) {
        super(Text.literal("Сохранить макрос"));
        this.parent = parent;
        this.frames = frames;
    }

    // ── Размеры диалога ───────────────────────────────────────────────────────

    private int dW() { return 260; }
    private int dH() { return 110; }
    private int dX() { return (width  - dW()) / 2; }
    private int dY() { return (height - dH()) / 2; }

    // ── init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int x = dX(), y = dY(), w = dW();

        // Поле ввода имени
        nameField = new TextFieldWidget(textRenderer,
                x + 10, y + 36, w - 20, 20, Text.empty());
        nameField.setMaxLength(48);
        nameField.setPlaceholder(Text.literal("Название макроса..."));
        nameField.setFocused(true);
        addDrawableChild(nameField);

        // Кнопки
        int btnW = (w - 28) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),
                b -> trySave()
        ).dimensions(x + 8, y + dH() - 30, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),
                b -> client.setScreen(parent)
        ).dimensions(x + btnW + 12, y + dH() - 30, btnW, 20).build());
    }

    // ── Логика сохранения ─────────────────────────────────────────────────────

    private void trySave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            statusMsg   = "Введи название!";
            statusColor = ACCENT_RED;
            return;
        }
        boolean ok = MacroStorage.INSTANCE.save(name, frames);
        if (ok) {
            client.setScreen(parent);
        } else {
            statusMsg   = "Ошибка сохранения";
            statusColor = ACCENT_RED;
        }
    }

    // ── keyPressed — Enter сохраняет ──────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == 257) { trySave(); return true; } // ENTER
        if (input.key() == 256) { client.setScreen(parent); return true; } // ESC
        return super.keyPressed(input);
    }

    // ── Рендер ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        int x = dX(), y = dY(), w = dW(), h = dH();

        // Тень
        ctx.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x88000000);
        // Фон
        ctx.fill(x, y, x + w, y + h, BG_DARK);
        // Красная полоска сверху
        ctx.fill(x, y, x + w, y + 2, ACCENT_RED);
        // Рамка
        ctx.fill(x,     y,     x + w,     y + 1,     BORDER);
        ctx.fill(x,     y + h, x + w,     y + h + 1, BORDER);
        ctx.fill(x,     y,     x + 1,     y + h,     BORDER);
        ctx.fill(x + w, y,     x + w + 1, y + h,     BORDER);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int x = dX(), y = dY();

        ctx.drawTextWithShadow(textRenderer,
                "Сохранить макрос", x + 10, y + 8, ACCENT_RED);
        ctx.drawTextWithShadow(textRenderer,
                "Кадров: " + frames.size(), x + 10, y + 22, TEXT_GRAY);

        if (!statusMsg.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, statusMsg, x + 10, y + 60, statusColor);
        }
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void close() { client.setScreen(parent); }
}
