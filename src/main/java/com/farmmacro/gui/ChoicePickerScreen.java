package com.farmmacro.gui;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Searchable explicit choices; opening/searching/scrolling does not apply a setting. */
public final class ChoicePickerScreen extends Screen {
    private final Screen parent;
    private final String heading, current;
    private final List<Rows.Choice> choices;
    private final Consumer<String> select;
    private List<Rows.Choice> filtered = List.of();
    private final List<FlatButton> buttons = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int offset, cursor, visibleRows, x, y, w, h;

    public ChoicePickerScreen(
            Screen parent, String heading, List<Rows.Choice> choices, String current, Consumer<String> select) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.current = current;
        this.select = select;
        var unique = new LinkedHashMap<String, Rows.Choice>();
        for (var choice : choices) unique.putIfAbsent(choice.id(), choice);
        this.choices = List.copyOf(unique.values());
    }

    @Override
    protected void init() {
        w = Math.max(80, Math.min(520, width - 16));
        h = Math.max(120, Math.min(440, height - 16));
        x = (width - w) / 2;
        y = (height - h) / 2;
        buttons.clear();
        visibleRows = Math.max(1, (h - 116) / 22);
        search = addRenderableWidget(
                new EditBox(font, x + 12, y + 35, w - 24, 20, Component.literal("Поиск режимов и мобов")));
        search.setMaxLength(80);
        search.setValue(query);
        for (int row = 0; row < visibleRows; row++) {
            final int slot = row;
            buttons.add(addRenderableWidget(
                    new FlatButton(x + 12, y + 62 + row * 22, w - 24, 20, "", b -> choose(offset + slot))));
        }
        addRenderableWidget(new FlatButton(x + w - 92, y + h - 27, 80, 20, "Назад", b -> onClose()));
        search.setResponder(value -> {
            query = value;
            filter();
        });
        filter();
        if (query.isBlank())
            for (int i = 0; i < filtered.size(); i++)
                if (filtered.get(i).id().equals(current)) {
                    cursor = i;
                    offset = Math.max(0, i - visibleRows / 2);
                    break;
                }
        refresh();
        setInitialFocus(search);
    }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = choices.stream()
                .filter(c -> (c.label() + " " + c.id()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        offset = cursor = 0;
        refresh();
    }

    private void refresh() {
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - visibleRows));
        cursor = Math.clamp(cursor, 0, Math.max(0, filtered.size() - 1));
        for (int i = 0; i < buttons.size(); i++) {
            int index = offset + i;
            var button = buttons.get(i);
            button.visible = index < filtered.size();
            button.active = button.visible;
            if (button.visible) {
                var choice = filtered.get(index);
                button.setMessage(Component.literal(
                        (choice.id().equals(current) ? "✓ " : index == cursor ? "› " : "  ") + choice.label()));
            }
        }
    }

    private void move(int amount) {
        cursor = Math.clamp(cursor + amount, 0, Math.max(0, filtered.size() - 1));
        if (cursor < offset) offset = cursor;
        if (cursor >= offset + visibleRows) offset = cursor - visibleRows + 1;
        refresh();
    }

    private void choose(int index) {
        if (index < 0 || index >= filtered.size()) return;
        select.accept(filtered.get(index).id());
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        switch (e.key()) {
            case 256 -> {
                onClose();
                return true;
            }
            case 265 -> {
                move(-1);
                return true;
            }
            case 264 -> {
                move(1);
                return true;
            }
            case 266 -> {
                move(-visibleRows);
                return true;
            }
            case 267 -> {
                move(visibleRows);
                return true;
            }
            case 257, 335 -> {
                choose(cursor);
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (Ui.inside(mx, my, x + 10, y + 60, w - 20, visibleRows * 22) && sy != 0) {
            offset += sy > 0 ? -3 : 3;
            refresh();
            cursor = Math.clamp(cursor, offset, Math.min(offset + visibleRows - 1, Math.max(0, filtered.size() - 1)));
            refresh();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.shadow(g, x, y, w, h, 8);
        Ui.window(g, x, y, w, h, 8);
        Ui.text(g, font, Ui.ellipsize(font, heading, w - 24), x + 12, y + 12, Ui.TEXT);
        if (filtered.isEmpty()) Ui.text(g, font, "Ничего не найдено", x + 14, y + 68, Ui.SUB);
        Ui.text(g, font, filtered.size() + " / " + choices.size() + " · ↑↓ · Enter", x + 12, y + h - 20, Ui.SUB);
        if (filtered.size() > visibleRows) {
            int track = visibleRows * 22, thumb = Math.max(12, track * visibleRows / filtered.size());
            int top = y + 62 + (track - thumb) * offset / Math.max(1, filtered.size() - visibleRows);
            g.fill(x + w - 7, top, x + w - 4, top + thumb, Ui.ACCENT);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
