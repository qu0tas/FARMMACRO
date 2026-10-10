package com.farmmacro.macro;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * «Зажим мыши»: держать ЛКМ/ПКМ без пальца на кнопке.
 *  • ручной — клавиша «Зажим ЛКМ вкл/выкл» (по умолчанию L), работает и без макроса;
 *  • от макроса — по {@link HoldSettings} играющего макроса/маршрута ({@link #begin}).
 * Во время воспроизведения источники жмут клавиши через {@link MacroManager#press}, который добавляет
 * сюда «или зажато»; вне воспроизведения ручной зажим жмёт сам в {@link #tick}.
 * Не жмёт, пока открыто любое окно. Обязательно отпускает: стоп и паника ({@link #stopAll}), End,
 * открытие любого окна (ручной зажим выключается), выход из мира, открытие редактора маршрута.
 */
public final class MouseHold {
    public static final MouseHold INSTANCE = new MouseHold();

    private boolean manual;
    /** Настройки играющего макроса (копия) или null. */
    private HoldSettings run;
    private boolean runRoute;
    private int runTicks;
    /** Запись: текущий кадр; маршрут: отрезок от точки с галочкой. */
    private int frame;
    private boolean pointHold;
    /** Ручной зажим нажал ЛКМ сам (вне воспроизведения) — значит, и отпустить должен сам. */
    private boolean pressedByUs;

    private MouseHold() {}

    public boolean isManual() { return manual; }

    /** Клавиша «Зажим ЛКМ». */
    public void toggleManual(Minecraft mc) {
        if (!manual && com.farmmacro.route.RouteEditor.isActive()) {
            msg(mc, "§cЗажим ЛКМ: сначала закрой редактор маршрута");
            return;
        }
        manual = !manual;
        if (!manual) releaseOwn(mc);
        msg(mc, manual ? "§a● Зажим ЛКМ включён §7(" + MacroManager.keyName(com.farmmacro.FarmMacroMod.keyHold) + " — выключить)"
                : "§7○ Зажим ЛКМ выключен");
    }

    // ── от макроса ───────────────────────────────────────────────────────────

    /** Начало воспроизведения (после отсчёта). */
    /** «Случайность»: стоим посреди пути — зажим макроса на это время отпущен. */
    private boolean suspended;
    public void suspend(boolean s) { suspended = s; }

    public void begin(HoldSettings s, boolean route) {
        suspended = false;
        run = s == null || !s.active() ? null : s.copy();
        runRoute = route;
        runTicks = 0;
        frame = 0;
        pointHold = false;
    }

    /** Каждый тик воспроизведения, до кадра источника. */
    public void tickRun() { if (run != null) runTicks++; }

    /** Где сейчас источник: кадр записи / галочка точки текущего отрезка маршрута. */
    public void position(int frameIndex, boolean pointHoldFlag) { frame = frameIndex; pointHold = pointHoldFlag; }

    /** Зажато ли от макроса прямо сейчас (для HUD). */
    public HoldSettings running() { return run; }

    private boolean runWants(boolean attack) {
        HoldSettings s = run;
        return s != null && !suspended && s.wants(attack, runTicks, runRoute, frame, pointHold);
    }

    /** Нужно ли держать эту клавишу (keyAttack / keyUse) сейчас. */
    public boolean wants(Minecraft mc, KeyMapping key) {
        if (mc == null || mc.options == null || mc.screen != null || mc.player == null) return false;
        Options o = mc.options;
        if (key == o.keyAttack) return manual || runWants(true);
        if (key == o.keyUse) return runWants(false);
        return false;
    }

    // ── тик и отпуск ─────────────────────────────────────────────────────────

    /** Конец тика: ручной зажим вне воспроизведения; без игрока — всё сбросить. */
    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {        // вышли из мира
            if (manual || pressedByUs) { manual = false; releaseOwn(mc); }
            return;
        }
        if (manual && com.farmmacro.route.RouteEditor.isActive()) {
            manual = false;
            releaseOwn(mc);
            msg(mc, "§7○ Зажим ЛКМ выключен: открыт редактор маршрута");
            return;
        }
        if (MacroManager.INSTANCE.isPlaying()) {
            // ЛКМ жмёт источник через press(); здесь — только не забыть про «нажали сами»
            pressedByUs = false;
            return;
        }
        boolean want = wants(mc, mc.options.keyAttack);
        if (want) { set(mc.options.keyAttack, true); pressedByUs = true; }
        else if (pressedByUs) releaseOwn(mc);
    }

    /** Открывается окно (любое, кроме закрытия): ручной зажим выключается, кнопки отпускаются. */
    public void onScreenOpening(Screen screen) {
        if (screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (manual) {
            manual = false;
            msg(mc, "§7○ Зажим ЛКМ выключен: открыто окно");
        }
        if (pressedByUs) releaseOwn(mc);
        if (run != null && mc.options != null) { set(mc.options.keyAttack, false); set(mc.options.keyUse, false); }
    }

    /** Стоп, паника, выход из мира: выключить всё. Клавиши отпускает вызывающий (releaseAll) — и здесь на всякий случай. */
    public void stopAll(Minecraft mc) {
        boolean had = run != null || manual || pressedByUs;
        run = null;
        manual = false;
        pressedByUs = false;
        if (had && mc != null && mc.options != null) { set(mc.options.keyAttack, false); set(mc.options.keyUse, false); }
    }

    private void releaseOwn(Minecraft mc) {
        pressedByUs = false;
        if (mc != null && mc.options != null) set(mc.options.keyAttack, false);
    }

    private static void set(KeyMapping key, boolean down) {
        KeyMapping.set(KeyMappingHelper.getBoundKeyOf(key), down);
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
