package com.farmmacro.combat;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;

/** Manual camera only. Never casts, clicks, moves, changes slots or sends packets. */
public final class ManualScytheAim {
    private LivingEntity owner;
    private Vec3 last, velocity = Vec3.ZERO;
    private long sampledAt;
    private String status = "центр";

    public void reset() {
        owner = null;
        last = null;
        velocity = Vec3.ZERO;
        sampledAt = 0;
        status = "центр";
    }

    public String status() {
        return status;
    }
    /** One real-position sample per client tick, not render interpolation or unreliable remote deltaMovement. */
    public void sample(LivingEntity e) {
        long now = System.nanoTime();
        Vec3 p = e.position();
        if (owner != e || last == null) {
            reset();
            owner = e;
            last = p;
            sampledAt = now;
            return;
        }
        double dt = (now - sampledAt) / 1e9;
        Vec3 delta = p.subtract(last);
        last = p;
        sampledAt = now;
        if (!ProjectileLead.plausibleStep(delta.x, delta.y, delta.z, dt)) {
            velocity = Vec3.ZERO;
            return;
        }
        Vec3 measured = delta.scale(1 / dt);
        velocity = velocity.scale(.35).add(measured.scale(.65));
    }
    /** Hunting keeps its own tuning; never applies Scythe floor/feet logic to lasso/net/bow. */
    public Vec3 huntingPoint(Minecraft mc, LivingEntity e, Vec3 eye, float partial, ModConfig c) {
        Vec3 center = e.getBoundingBox().getCenter().add(e.getPosition(partial).subtract(e.position()));
        status = "Hunting: центр";
        if (!ShotPath.clear(mc, eye, center)) return null;
        var item = mc.player.getMainHandItem();
        boolean projectile = item.is(Items.BOW) || item.is(Items.CROSSBOW) || ShotPath.holdingScythe(mc);
        if (!c.huntingAimLead
                || !projectile && !c.huntingAimLeadOtherItems
                || owner != e
                || System.nanoTime() - sampledAt > 200_000_000L) return center;
        var relative = center.subtract(eye);
        var lead = ProjectileLead.solve(
                relative.x,
                relative.y,
                relative.z,
                velocity.x,
                velocity.y,
                velocity.z,
                c.huntingAimProjectileSpeed,
                c.huntingAimLeadDelayMs / 1000.0,
                c.huntingAimMaxLead,
                4);
        var predicted = center.add(lead.x(), lead.y(), lead.z());
        if (!ShotPath.clear(mc, eye, predicted)) {
            status = "Hunting: центр (прогноз закрыт)";
            return center;
        }
        status = predicted.distanceToSqr(center) > .0001 ? "Hunting: упреждение" : "Hunting: центр";
        return predicted;
    }

    public Vec3 point(Minecraft mc, LivingEntity e, Vec3 eye, float partial, ModConfig c) {
        Vec3 interpolation = e.getPosition(partial).subtract(e.position());
        AABB real = e.getBoundingBox().move(interpolation);
        Vec3 center = real.getCenter();
        status = "центр";
        if (!ShotPath.holdingScythe(mc) || (!c.aimManualScytheLead && !c.aimManualScytheFeet))
            return ShotPath.clear(mc, eye, center) ? center : null;
        // Current target must actually be visible; prediction never acquires a body behind a wall.
        if (!ShotPath.clear(mc, eye, center)) return null;
        Vec3 offset = Vec3.ZERO;
        if (c.aimManualScytheLead && owner == e && System.nanoTime() - sampledAt <= 200_000_000L) {
            var relative = center.subtract(eye);
            var lead = ProjectileLead.solve(
                    relative.x,
                    relative.y,
                    relative.z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    c.aimManualScytheSpeed,
                    c.aimManualScytheDelayMs / 1000.0,
                    c.aimManualScytheMaxLead,
                    4);
            offset = new Vec3(lead.x(), lead.y(), lead.z());
        }
        AABB predicted = real.move(offset);
        Vec3 aim = predicted.getCenter();
        if (c.aimManualScytheFeet) {
            double horizontal = Math.hypot(aim.x - eye.x, aim.z - eye.z);
            // Keep the floor intersection near the body even on a long, shallow downward shot.
            double height = Math.min(.18, Math.max(.035, (eye.y - predicted.minY) * .8 / (horizontal + .8)));
            Vec3 feet =
                    new Vec3(aim.x, predicted.minY + Math.min(height, (predicted.maxY - predicted.minY) * .2), aim.z);
            Vec3 direction = feet.subtract(eye);
            double distance = direction.length();
            if (direction.y < -.02 && distance > .1 && ShotPath.clear(mc, eye, feet)) {
                Vec3 end = feet.add(direction.scale(6 / distance));
                var hit = ShotPath.trace(mc, eye, end);
                if (hit != null
                        && hit.getType() == HitResult.Type.BLOCK
                        && hit.getDirection() == Direction.UP
                        && predicted.clip(eye, hit.getLocation()).isPresent()
                        && predicted.distanceToSqr(hit.getLocation()) <= 2.3 * 2.3
                        && ShotPath.clear(mc, hit.getLocation().add(0, .03, 0), aim)) {
                    status = offset.lengthSqr() > .0001 ? "упреждение + ноги / пол" : "ноги / пол";
                    return feet;
                }
            }
        }
        if (ShotPath.clear(mc, eye, aim)) {
            status = offset.lengthSqr() > .0001 ? "упреждение в тело" : "центр (нет подходящего пола)";
            return aim;
        }
        status = "центр (упреждение закрыто)";
        return center;
    }
}
