package com.farmmacro.combat;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Approximate server head direction + clear collision ray; not staff identification. */
public final class PlayerGazeGuard {
    private static final GazeTimer timer = new GazeTimer();
    private static Object world;

    public static void reset() {
        timer.clear();
        world = null;
    }

    public static boolean tick(Minecraft mc, boolean active) {
        var c = ModConfig.INSTANCE;
        var p = c.combatPanic;
        if (!active
                || AimAssist.manualMode()
                || !c.aimAssistEnabled
                || p == null
                || !p.enabled
                || !p.playerGaze
                || !(c.aimZealotsOnly || "zealots".equals(c.combatProfileId))
                || mc.player == null
                || mc.level == null
                || mc.getConnection() == null
                || mc.screen != null
                || PanicHandoff.pending()) {
            reset();
            return false;
        }
        if (world != mc.level) {
            reset();
            world = mc.level;
        }
        long now = System.nanoTime();
        Set<UUID> present = new HashSet<>();
        var friends = PlayerRules.parseNames(p.friends);
        for (var other : mc.level.players()) {
            if (present.size() >= 64) break;
            if (other == mc.player
                    || other.isRemoved()
                    || !other.isAlive()
                    || other.isSpectator()
                    || other.isInvisibleTo(mc.player)
                    || other.distanceToSqr(mc.player) > p.gazeRadius * p.gazeRadius
                    || mc.getConnection().getPlayerInfo(other.getUUID()) == null
                    || p.ignoreFriends
                            && PlayerRules.isFriend(
                                    friends, other.getGameProfile().name())) continue;
            present.add(other.getUUID());
            var eye = other.getEyePosition();
            var hit = mc.player
                    .getBoundingBox()
                    .inflate(.3)
                    .clip(
                            eye,
                            eye.add(Vec3.directionFromRotation(other.getXRot(), other.getYHeadRot())
                                    .scale(p.gazeRadius + 2)));
            boolean looking = hit.isPresent() && ShotPath.clear(mc, eye, hit.get());
            if (timer.sample(other.getUUID(), looking, now, p.gazeSeconds)) {
                reset();
                PanicDetector.INSTANCE.triggerPanic(
                        mc,
                        "combatGaze",
                        "Игрок смотрит на тебя не менее " + p.gazeSeconds
                                + " с — Combat остановлен (не определение staff)");
                return true;
            }
        }
        timer.retain(present);
        return false;
    }

    private PlayerGazeGuard() {}
}
