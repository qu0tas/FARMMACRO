package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.16.2 «Что нового»: один раз после обновления мода (в главном меню игры) — главное из этой версии
 * (assets/farmmacro/whatsnew.txt). Закрыли — {@link ModConfig#lastSeenVersion} = текущая, больше само не покажется;
 * снова — кнопка «Что нового» во вкладке «Конфиги».
 */
public class WhatsNewScreen extends Screen implements Rows.Ctx {
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("farmmacro")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");

    private final Screen parent;
    private final List<String> items = load();
    private double scroll;
    private String tooltip;

    public WhatsNewScreen(Screen parent) {
        super(Component.literal("Что нового в Kaelix " + VERSION));
        this.parent = parent;
    }

    /** Показать ли само (обновились, мастер первого запуска уже пройден). */
    public static boolean shouldShow() {
        ModConfig c = ModConfig.INSTANCE;
        return c.welcomeDone && !"?".equals(VERSION) && !VERSION.equals(c.lastSeenVersion);
    }

    /** Отметить текущую версию просмотренной. */
    public static void markSeen() {
        ModConfig c = ModConfig.INSTANCE;
        if (VERSION.equals(c.lastSeenVersion)) return;
        c.lastSeenVersion = VERSION;
        ModConfig.save();
    }

    static List<String> load() {
        List<String> out = new ArrayList<>();
        try (InputStream in = WhatsNewScreen.class.getResourceAsStream("/assets/farmmacro/whatsnew.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                l = l.strip();
                if (l.isEmpty() || l.startsWith("# ")) continue;
                out.add(l);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private int wW() { return Math.min(380, width - 16); }
    private int wH() { return Math.min(280, height - 16); }
    private int wX() { return (width - wW()) / 2; }
    private int wY() { return (height - wH()) / 2; }
    private int bY() { return wY() + wH() - 26; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        tooltip = null;
        int x = wX(), y = wY(), w = wW(), h = wH();
        Ui.shadow(g, x, y, w, h, 8);
        Ui.window(g, x, y, w, h, 8);
        Ui.text(g, font, getTitle().getString(), x + 10, y + 9, Ui.TEXT);
        g.fill(x + 10, y + 22, x + w - 10, y + 23, Ui.BORDER);
        int cx = x + 12, cy = y + 28, cw = w - 24, ch = bY() - cy - 6;
        List<String[]> lines = new ArrayList<>();       // {текст, тип: h — заголовок, b — пункт, c — продолжение}
        for (String it : items) {
            boolean head = it.startsWith("## ");
            String t = head ? it.substring(3) : it;
            var split = font.split(Component.literal(t), cw - (head ? 0 : 10));
            for (int i = 0; i < split.size(); i++) {
                StringBuilder sb = new StringBuilder();
                split.get(i).accept((idx, st, cp) -> { sb.appendCodePoint(cp); return true; });
                lines.add(new String[]{sb.toString(), head ? "h" : i == 0 ? "b" : "c"});
            }
        }
        int total = 0;
        for (String[] l : lines) total += "h".equals(l[1]) ? 15 : 10;
        scroll = Math.max(0, Math.min(Math.max(0, total - ch), scroll));
        g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        int ly = cy - (int) scroll;
        for (String[] l : lines) {
            if ("h".equals(l[1])) { ly += 4; Ui.text(g, font, l[0], cx, ly, Ui.ACCENT); ly += 11; continue; }
            if ("b".equals(l[1])) Ui.text(g, font, "•", cx + 1, ly, Ui.SUB);
            Ui.text(g, font, l[0], cx + 10, ly, Ui.TEXT);
            ly += 10;
        }
        g.disableScissor();
        if (lines.isEmpty()) Ui.text(g, font, "Список изменений — в CHANGELOG.md", cx, cy, Ui.SUB);
        int bw = 110;
        Rows.drawButton(this, g, x + w - bw - 10, bY(), bw, 18, "Понятно", Rows.Style.PRIMARY, true, mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int bw = 110;
        if (e.button() == 0 && Ui.inside(e.x(), e.y(), wX() + wW() - bw - 10, bY(), bw, 18)) {
            clickSound();
            onClose();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= sy * 20;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { onClose(); return true; }
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return true; }

    @Override
    public void onClose() {
        markSeen();
        minecraft.setScreen(parent);
    }

    @Override public Font font() { return font; }
    @Override public void clickSound() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }
    @Override public void tooltip(String text) { tooltip = text; }
    @Override public void hand() {}
    @Override public boolean shift() { return minecraft.hasShiftDown(); }
}
