package com.farmmacro.camera;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * v1.12 «Freelook»: вид от третьего лица, мышь крутит только камеру вокруг игрока, а сам игрок (yaw/pitch,
 * направление W/A/S/D автохода, детектор «Камера повернулась») не поворачивается.
 * Мышь: {@code FreelookMouseMixin} перехватывает LocalPlayer.turn в MouseHandler.turnPlayer;
 * камера: {@code FreelookCameraMixin} подменяет взгляд игрока в Camera.alignWithEntity.
 * Режим «держать» (freelookHold) — пока зажата клавиша, иначе — вкл/выкл нажатием.
 * Снимается: выход из мира, паника (freelookOffOnPanic), End.
 */
public final class Freelook {
    private static boolean active;
    private static float yaw, pitch;
    private static CameraType prevType;

    private Freelook() {}

    public static boolean active() { return active; }
    public static float yaw()   { return yaw; }
    public static float pitch() { return pitch; }

    /** Движение мыши во время freelook (из миксина). */
    public static void turn(double dx, double dy) {
        float[] r = FreelookMath.turn(yaw, pitch, dx, dy);
        yaw = r[0]; pitch = r[1];
    }

    public static void toggle(Minecraft mc) {
        if (active) off(mc, null); else on(mc);
    }

    public static void on(Minecraft mc) {
        if (active || Freecam.active() || mc.player == null) return;
        yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
        prevType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        active = true;
        if (!ModConfig.INSTANCE.freelookHold)
            msg(mc, "§b◉ Freelook §7(" + MacroManager.keyName(FarmMacroMod.keyFreelook) + " — выкл)");
    }

    /** why = null — вручную. */
    public static void off(Minecraft mc, String why) {
        if (!active) return;
        active = false;
        if (mc != null && mc.options != null) mc.options.setCameraType(prevType != null ? prevType : CameraType.FIRST_PERSON);
        prevType = null;
        if (why != null) msg(mc, "§e○ Freelook выключен: " + why);
    }

    /** Каждый тик: режим «держать» и выход из мира. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { if (active) { active = false; prevType = null; } return; }
        if (ModConfig.INSTANCE.freelookHold && FarmMacroMod.keyFreelook != null) {
            boolean down = FarmMacroMod.keyFreelook.isDown() && mc.screen == null;
            if (down && !active) on(mc);
            else if (!down && active) off(mc, null);
        }
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] §r" + text));
    }
}
