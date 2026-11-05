package com.farmmacro.panic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Compatibility seam: the persistent B acknowledgement/input latch was removed in alpha.5.
 * Panic is a local stop; a new explicit start needs no acknowledgement. Never auto-resumes.
 */
public final class PanicHandoff {
    public static boolean pending() {
        return false;
    }

    public static String reason() {
        return "";
    }

    public static String detector() {
        return "";
    }

    public static long ageSeconds() {
        return 0;
    }

    public static String keyName() {
        return "";
    }

    public static void show(String id, String text) {}

    public static void clear() {}

    public static void takeControl(Minecraft mc) {
        PanicDetector.INSTANCE.finishDelayed(mc);
    }

    public static boolean allowStart(Minecraft mc) {
        return !PanicDetector.INSTANCE.delayed();
    }

    public static boolean onKey(Minecraft mc, int action, KeyEvent event) {
        return false;
    }

    public static boolean onButton(Minecraft mc, int action, MouseButtonInfo info) {
        return false;
    }

    public static boolean blockStarts(Minecraft mc) {
        return false;
    }

    private PanicHandoff() {}
}
