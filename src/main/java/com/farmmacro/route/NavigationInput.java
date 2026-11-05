package com.farmmacro.route;

import com.farmmacro.camera.Freecam;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.combat.CombatSway;
import com.farmmacro.combat.SpecialZealotHunt;
import com.farmmacro.macro.HandDelays;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.motion.MotionController;
import com.farmmacro.motion.MotionPolicy;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Client-thread navigation lease: keys and camera share one owner; manual input always takes priority. */
public final class NavigationInput {
    private static Object owner, cameraRequest;
    private static boolean mouseKnown;
    private static double mouseX, mouseY;
    private static Object jumpPlayer, jumpWorld;
    private static int lastJumpEdge = -100, edgeTick = -100;
    /** Jump edges within vanilla's seven-tick window toggle flight. Cruise must not generate them. */
    public static void takeoffJump(Object token, Options o, boolean wanted) {
        pressJump(token, o, wanted, true);
    }

    private static void pressJump(Object token, Options o, boolean wanted, boolean takeoff) {
        if (!owns(token) || o == null || !MotionController.movement(MotionPolicy.Owner.NAVIGATION, wanted)) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) {
            o.keyJump.setDown(false);
            return;
        }
        if (jumpPlayer != p || jumpWorld != mc.level) {
            jumpPlayer = p;
            jumpWorld = mc.level;
            lastJumpEdge = edgeTick = p.tickCount - 100;
        }
        // Compare with the input vanilla actually sampled, not transient release()/apply() writes this tick.
        boolean rising = wanted && !p.input.keyPresses.jump();
        if (rising && !takeoff && p.getAbilities().flying && p.tickCount - lastJumpEdge < 9) wanted = false;
        if (wanted && rising && edgeTick != p.tickCount) {
            lastJumpEdge = edgeTick = p.tickCount;
        }
        o.keyJump.setDown(wanted);
    }

    public static boolean acquire(Object token) {
        if (Freecam.active() || token == null || owner != null && owner != token || SmoothTurn.bindingBusy())
            return false;
        if (owner == null) {
            HandDelays.releaseButtons();
            SmoothTurn.stop();
            CombatSway.INSTANCE.stop(Minecraft.getInstance(), "навигация получила управление");
            mouseKnown = false;
        }
        owner = token;
        return true;
    }

    public static boolean busy() {
        return owner != null;
    }

    public static String technicalState() {
        return owner instanceof ReturnWalker w ? w.technicalState() : owner == null ? "IDLE" : "SERVICE_LEASE";
    }

    public static boolean owns(Object token) {
        return token != null && owner == token;
    }

    public static boolean cameraAllowed() {
        return owner == null || cameraRequest == owner;
    }

    public static Object cameraOwner() {
        return cameraRequest;
    }

    public static boolean cameraValid(Object token) {
        return token == null ? owner == null : owns(token);
    }

    public static <T> T camera(Object token, Supplier<T> action) {
        if (!owns(token)) throw new IllegalStateException("navigation camera without lease");
        Object previous = cameraRequest;
        cameraRequest = token;
        try {
            return action.get();
        } finally {
            cameraRequest = previous;
        }
    }

    public static void camera(Object token, Runnable action) {
        camera(token, () -> {
            action.run();
            return null;
        });
    }

    public static void release(Object token) {
        if (owns(token)) {
            owner = null;
            mouseKnown = false;
            HandDelays.releaseButtons();
        }
    }

    public static void userInput() {
        if (owner instanceof ReturnWalker walker) {
            var mc = Minecraft.getInstance();
            walker.manualStop(mc);
            SpecialZealotHunt.INSTANCE.userInput();
            MacroManager.INSTANCE.stopPlayback(mc, "§eНавигация: ручное управление");
        }
    }

    public static void onMouseMove(double x, double y) {
        var mc = Minecraft.getInstance();
        if (!busy() || mc.screen != null || !mc.isWindowActive() || !mc.mouseHandler.isMouseGrabbed()) {
            mouseKnown = false;
            return;
        }
        if (mouseKnown && Math.hypot(x - mouseX, y - mouseY) > .7) userInput();
        mouseX = x;
        mouseY = y;
        mouseKnown = true;
    }

    public static void press(Object token, Options o, KeyMapping key, boolean wanted) {
        if (!owns(token) || o == null || !MotionController.movement(MotionPolicy.Owner.NAVIGATION, wanted)) return;
        if (key == o.keyJump) {
            pressJump(token, o, wanted, false);
            return;
        }
        if (key == o.keyShift || key == o.keySprint) {
            if (key.isDown() == wanted) return;
            boolean toggle = (key == o.keyShift ? o.toggleCrouch() : o.toggleSprint()).get();
            key.setDown(toggle ? true : wanted);
        } else key.setDown(wanted); // No MouseHold or delayed farming write may enter this lease.
    }

    private NavigationInput() {}
}
