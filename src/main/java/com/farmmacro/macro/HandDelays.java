package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * v1.15 «Задержки рук (3 слоя)» в игре: ЛКМ/ПКМ (нажать и отпустить) и смена слота от макроса проходят через
 * {@link ActionGate} с задержкой из {@link DelayModel}. Работает, пока играет макрос со «Случайностью»
 * (не на идеальном круге) и включено {@code delayLayers}. Стоп, паника, End — всё отпускается сразу, без задержек.
 */
public final class HandDelays {
    private HandDelays() {}

    public static final DelayModel MODEL = new DelayModel(System.nanoTime());
    private static final ActionGate.Button ATTACK = new ActionGate.Button(), USE = new ActionGate.Button();
    private static final ActionGate.Slot SLOT = new ActionGate.Slot();
    private static long tick;

    /** Каждый тик воспроизведения (MacroManager). */
    public static void tick() { tick++; }

    public static boolean active() {
        return ModConfig.INSTANCE.delayLayers && MacroManager.INSTANCE.isPlaying() && Humanizer.lap() != null;
    }

    public static DelayModel.Params params() {
        ModConfig c = ModConfig.INSTANCE;
        DelayModel.Params p = new DelayModel.Params();
        p.mean[0] = c.delayPressMean; p.sd[0] = c.delayPressSd; p.min[0] = c.delayPressMin; p.max[0] = c.delayPressMax;
        p.mean[1] = c.delayReleaseMean; p.sd[1] = c.delayReleaseSd; p.min[1] = c.delayReleaseMin; p.max[1] = c.delayReleaseMax;
        p.mean[2] = c.delaySlotMean; p.sd[2] = c.delaySlotSd; p.min[2] = c.delaySlotMin; p.max[2] = c.delaySlotMax;
        p.fatiguePct = c.fatiguePct; p.fatigueRampMin = c.fatigueRampMin; p.driftPct = c.driftPct; p.driftMin = c.driftMin;
        p.distractChance = c.distractChance; p.distractMinMs = c.distractMinMs; p.distractMaxMs = c.distractMaxMs;
        return p;
    }

    /** Время сессии для «усталости»: от начала сессии MacroManager (сброс — «Сбросить статистику»). */
    private static long now() { return System.currentTimeMillis(); }

    /** Для MacroManager.press: ЛКМ/ПКМ с задержкой; остальные клавиши — как есть. */
    public static boolean gate(KeyMapping key, boolean down) {
        Options o = Minecraft.getInstance().options;
        if (o == null) return down;
        ActionGate.Button b = key == o.keyAttack ? ATTACK : key == o.keyUse ? USE : null;
        if (b == null) return down;
        if (!active()) { b.reset(down); return down; }
        String what = b == ATTACK ? "ЛКМ" : "ПКМ";
        return b.update(down, tick, () -> MODEL.next(down ? DelayModel.Kind.PRESS : DelayModel.Kind.RELEASE, what, now(), params()).ticks());
    }

    /** Слот, который поставить сейчас: тот же, пока не прошла задержка смены. */
    public static int slot(int current, int want) {
        if (!active()) { SLOT.reset(); return want; }
        return SLOT.update(current, want, tick, () -> MODEL.next(DelayModel.Kind.SLOT, "слот " + (want + 1), now(), params()).ticks());
    }

    /** Стоп/паника: сразу всё как есть. */
    public static void reset() { ATTACK.reset(false); USE.reset(false); SLOT.reset(); }
}
