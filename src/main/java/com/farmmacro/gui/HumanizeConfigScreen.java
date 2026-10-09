package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Экран детальной настройки параметров humanize.
 */
public class HumanizeConfigScreen extends Screen {

    private static final int BG_DARK   = 0xFF0D0D0D;
    private static final int BG_PANEL  = 0xFF161616;
    private static final int BORDER    = 0xFF2A2A2A;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY  = 0xFF888888;
    private static final int TEXT_LIGHT = 0xFFCCCCCC;
    private static final int ACCENT     = 0xFFE02020;

    private final Screen parent;

    // Поля ввода
    private TextFieldWidget fStartMin, fStartMax;
    private TextFieldWidget fDelayMin, fDelayMax;
    private TextFieldWidget fSlowdown;
    private TextFieldWidget fWallOffset;
    private TextFieldWidget fMicroPauseDurMin, fMicroPauseDurMax;
    private TextFieldWidget fMicroPauseEveryMin, fMicroPauseEveryMax;

    public HumanizeConfigScreen(Screen parent) {
        super(Text.literal("Настройки Humanize"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig c = ModConfig.INSTANCE;

        int pw = Math.min(width - 60, 420);
        int ph = Math.min(height - 60, 430);
        int px = (width  - pw) / 2;
        int py = (height - ph) / 2;

        int lx = px + 14;
        int r  = py + 28;

        // Задержка старта
        addField(lx, r, "\u23f1 \u0421\u0442\u0430\u0440\u0442 \u043c\u0438\u043d (\u0441\u0435\u043a):", c.humanizeStartDelayMin + "");
        fStartMin = lastField;
        r += 24;
        addField(lx, r, "\u23f1 \u0421\u0442\u0430\u0440\u0442 \u043c\u0430\u043a\u0441 (\u0441\u0435\u043a):", c.humanizeStartDelayMax + "");
        fStartMax = lastField;
        r += 30;

        // Паузы на поворотах
        addField(lx, r, "\ud83d\udd04 \u041f\u0430\u0443\u0437\u0430 \u043c\u0438\u043d (\u0442\u0438\u043a\u0438):", c.humanizeDelayMin + "");
        fDelayMin = lastField;
        r += 24;
        addField(lx, r, "\ud83d\udd04 \u041f\u0430\u0443\u0437\u0430 \u043c\u0430\u043a\u0441 (\u0442\u0438\u043a\u0438):", c.humanizeDelayMax + "");
        fDelayMax = lastField;
        r += 24;
        addField(lx, r, "\ud83d\udd04 \u0422\u0438\u043a\u0438 \u0437\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u044f:", c.humanizeSlowdownTicks + "");
        fSlowdown = lastField;
        r += 30;

        // Смена стены
        addField(lx, r, "\ud83e\uddf1 \u0421\u043c\u0435\u0449\u0435\u043d\u0438\u0435 \u0441\u0442\u0435\u043d\u044b (\u0431\u043b):", c.humanizeWallOffset + "");
        fWallOffset = lastField;
        r += 36;

        // Микро-паузы
        addField(lx, r, "\u23f8 \u041c\u0438\u043a\u0440\u043e\u043f\u0430\u0443\u0437\u0430 \u043c\u0438\u043d (\u0441\u0435\u043a):", c.humanizeMicroPauseDurMin + "");
        fMicroPauseDurMin = lastField;
        r += 24;
        addField(lx, r, "\u23f8 \u041c\u0438\u043a\u0440\u043e\u043f\u0430\u0443\u0437\u0430 \u043c\u0430\u043a\u0441 (\u0441\u0435\u043a):", c.humanizeMicroPauseDurMax + "");
        fMicroPauseDurMax = lastField;
        r += 24;
        addField(lx, r, "\u23f8 \u0420\u0430\u0437 \u0432 N \u0437\u0430\u043f\u0443\u0441\u043a\u043e\u0432 (\u043c\u0438\u043d):", c.humanizeMicroPauseEveryMin + "");
        fMicroPauseEveryMin = lastField;
        r += 24;
        addField(lx, r, "\u23f8 \u0420\u0430\u0437 \u0432 N \u0437\u0430\u043f\u0443\u0441\u043a\u043e\u0432 (\u043c\u0430\u043a\u0441):", c.humanizeMicroPauseEveryMax + "");
        fMicroPauseEveryMax = lastField;
        r += 30;

        // Кнопки
        int btnW = (pw - 20) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 \u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c"),
                b -> { saveAll(); client.setScreen(parent); }
        ).dimensions(px + 4, py + ph - 24, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2716 \u041e\u0442\u043c\u0435\u043d\u0430"),
                b -> client.setScreen(parent)
        ).dimensions(px + btnW + 12, py + ph - 24, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("\u00d7"),
                b -> { saveAll(); client.setScreen(parent); }
        ).dimensions(px + pw - 20, py + 4, 16, 14).build());
    }

    private TextFieldWidget lastField;

    private void addField(int lx, int r, String label, String defaultVal) {
        int pw = Math.min(width - 60, 420);
        int px = (width - pw) / 2;
        int fx = px + pw - 90;

        TextFieldWidget field = new TextFieldWidget(
                textRenderer, fx, r, 80, 16, Text.literal(label));
        field.setMaxLength(12);
        field.setText(defaultVal);
        addDrawableChild(field);
        lastField = field;

        labelTexts.add(label);
        labelX.add(lx);
        labelY.add(r + 4);
    }

    private final java.util.List<String>  labelTexts = new java.util.ArrayList<>();
    private final java.util.List<Integer> labelX     = new java.util.ArrayList<>();
    private final java.util.List<Integer> labelY     = new java.util.ArrayList<>();

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xCC000000);

        int pw = Math.min(width - 60, 420);
        int ph = Math.min(height - 60, 430);
        int px = (width  - pw) / 2;
        int py = (height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, BG_PANEL);
        ctx.fill(px,          py,          px + pw,     py + 1,  BORDER);
        ctx.fill(px,          py + ph - 1, px + pw,     py + ph, BORDER);
        ctx.fill(px,          py,          px + 1,      py + ph, BORDER);
        ctx.fill(px + pw - 1, py,          px + pw,     py + ph, BORDER);

        ctx.fill(px, py, px + pw, py + 22, 0xFF1A0000);
        ctx.drawText(textRenderer, "\u2728  Humanize \u2014 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438", px + 8, py + 7, ACCENT, false);

        // Разделитель перед секцией микро-паузы
        int divY = py + 28 + 24 + 30 + 24 + 24 + 30 + 36 - 8;
        ctx.fill(px + 8, divY, px + pw - 8, divY + 1, BORDER);
        ctx.drawText(textRenderer, "\u041c\u0438\u043a\u0440\u043e-\u043f\u0430\u0443\u0437\u044b", px + 14, divY + 4, TEXT_GRAY, false);

        for (int i = 0; i < labelTexts.size(); i++) {
            ctx.drawText(textRenderer, labelTexts.get(i), labelX.get(i), labelY.get(i), TEXT_LIGHT, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void saveAll() {
        ModConfig c = ModConfig.INSTANCE;
        try { c.humanizeStartDelayMin      = Float.parseFloat(fStartMin.getText());           } catch (Exception ignored) {}
        try { c.humanizeStartDelayMax      = Float.parseFloat(fStartMax.getText());           } catch (Exception ignored) {}
        try { c.humanizeDelayMin           = Integer.parseInt(fDelayMin.getText());            } catch (Exception ignored) {}
        try { c.humanizeDelayMax           = Integer.parseInt(fDelayMax.getText());            } catch (Exception ignored) {}
        try { c.humanizeSlowdownTicks      = Integer.parseInt(fSlowdown.getText());            } catch (Exception ignored) {}
        try { c.humanizeWallOffset         = Float.parseFloat(fWallOffset.getText());         } catch (Exception ignored) {}
        try { c.humanizeMicroPauseDurMin   = Float.parseFloat(fMicroPauseDurMin.getText());   } catch (Exception ignored) {}
        try { c.humanizeMicroPauseDurMax   = Float.parseFloat(fMicroPauseDurMax.getText());   } catch (Exception ignored) {}
        try { c.humanizeMicroPauseEveryMin = Integer.parseInt(fMicroPauseEveryMin.getText()); } catch (Exception ignored) {}
        try { c.humanizeMicroPauseEveryMax = Integer.parseInt(fMicroPauseEveryMax.getText()); } catch (Exception ignored) {}

        // Санитизация
        c.humanizeStartDelayMin      = Math.max(0, c.humanizeStartDelayMin);
        c.humanizeStartDelayMax      = Math.max(c.humanizeStartDelayMin, c.humanizeStartDelayMax);
        c.humanizeDelayMin           = Math.max(0, c.humanizeDelayMin);
        c.humanizeDelayMax           = Math.max(c.humanizeDelayMin, c.humanizeDelayMax);
        c.humanizeSlowdownTicks      = Math.max(0, Math.min(20, c.humanizeSlowdownTicks));
        c.humanizeWallOffset         = Math.max(0.01f, Math.min(0.5f, c.humanizeWallOffset));
        c.humanizeMicroPauseDurMin   = Math.max(0.1f, c.humanizeMicroPauseDurMin);
        c.humanizeMicroPauseDurMax   = Math.max(c.humanizeMicroPauseDurMin, c.humanizeMicroPauseDurMax);
        c.humanizeMicroPauseEveryMin = Math.max(1, c.humanizeMicroPauseEveryMin);
        c.humanizeMicroPauseEveryMax = Math.max(c.humanizeMicroPauseEveryMin, c.humanizeMicroPauseEveryMax);

        ModConfig.save();
    }

    @Override
    public boolean shouldPause() { return false; }
}
