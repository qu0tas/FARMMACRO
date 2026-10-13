package com.farmmacro.route;

import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.macro.*;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class PointActionRuntime {
  private int index = -1, ticks;
  private long started, clicked = -1;
  private PointAction a;
  private final PointActionCore core = new PointActionCore();
  private boolean aimed, restoring;
  private float oldYaw, oldPitch;
  private int oldSlot;

  public void cancel() {
    if (aimed || restoring) SmoothTurn.stop();
    index = -1;
    clicked = -1;
    aimed = restoring = false;
  }

  public boolean tick(Minecraft mc, RoutePoint point, int idx, long seed) {
    var p = mc.player;
    long now = RouteEndRuntime.now();
    if (index != idx) {
      cancel();
      index = idx;
      a = point.pointAction.copy();
      oldSlot = p.getInventory().getSelectedSlot();
      oldYaw = p.getYRot();
      oldPitch = p.getXRot();
      started = now;
      ticks = 0;
      core.start(a, now, seed ^ idx);
    }
    MouseHold.INSTANCE.suspend(true);
    for (var k :
        new KeyMapping[] {
          mc.options.keyUp,
          mc.options.keyDown,
          mc.options.keyLeft,
          mc.options.keyRight,
          mc.options.keyJump,
          mc.options.keySprint,
          mc.options.keyShift,
          mc.options.keyAttack,
          mc.options.keyUse
        }) MacroManager.press(k, false);
    double d =
        Math.sqrt(
            Math.pow(p.getX() - point.x, 2)
                + Math.pow(p.getY() - point.y, 2)
                + Math.pow(p.getZ() - point.z, 2));
    switch (a.type) {
      case "wait", "position":
        {
          var result = core.tick(now, d);
          if (result == PointActionCore.Result.PANIC) {
            PanicDetector.INSTANCE.triggerPanic(mc, core.failure);
            return false;
          }
          return result == PointActionCore.Result.DONE;
        }
      case "command":
        if (core.issueOnce()) RouteEndRuntime.send(mc, a.command);
        return true;
      case "jump":
        MacroManager.press(mc.options.keyJump, ++ticks <= 2);
        return ticks > 2;
      case "item":
        if (restoring) {
          MacroManager.selectSlot(p, oldSlot);
          return !SmoothTurn.isActive() && p.getInventory().getSelectedSlot() == oldSlot;
        }
        boolean done = item(mc, point, now);
        if (done && aimed) {
          restoring = true;
          SmoothTurn.turnMouse(
              oldYaw, oldPitch, com.farmmacro.config.ModConfig.INSTANCE.returnTurnSpeed);
          return false;
        }
        return done;
      default:
        return true;
    }
  }

  private static boolean matches(ItemStack s, String id) {
    if (s.isEmpty()) return false;
    if (BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equalsIgnoreCase(id)) return true;
    var cd = s.get(DataComponents.CUSTOM_DATA);
    if (cd == null) return false;
    var tag = cd.copyTag();
    return id.equalsIgnoreCase(tag.getStringOr("id", ""))
        || id.equalsIgnoreCase(tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", ""));
  }

  private boolean item(Minecraft mc, RoutePoint point, long now) {
    var p = mc.player;
    if (clicked >= 0) return now - clicked >= a.cooldownMs;
    int slot = a.slot > 0 ? a.slot - 1 : p.getInventory().getSelectedSlot();
    if (!a.itemId.isBlank()) {
      slot = -1;
      for (int i = 0; i < 9; i++)
        if (matches(p.getInventory().getItem(i), a.itemId)) {
          slot = i;
          break;
        }
    }
    if (slot < 0 || p.getInventory().getItem(slot).isEmpty()) return true;
    MacroManager.selectSlot(p, slot);
    if (p.getInventory().getSelectedSlot() != slot) return now - started > 3000;
    if (!aimed) {
      aimed = true;
      double tx = (a.lookBlock ? a.blockX : Math.floor(point.x)) + .5,
          ty = (a.lookBlock ? a.blockY : Math.floor(point.y) - 1) + .5,
          tz = (a.lookBlock ? a.blockZ : Math.floor(point.z)) + .5;
      double dx = tx - p.getX(), dy = ty - p.getEyeY(), dz = tz - p.getZ();
      SmoothTurn.turnMouse(
          WalkCore.yawTo(0, 0, dx, dz),
          (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))),
          com.farmmacro.config.ModConfig.INSTANCE.returnTurnSpeed);
    }
    int bx = a.lookBlock ? a.blockX : (int) Math.floor(point.x),
        by = a.lookBlock ? a.blockY : (int) Math.floor(point.y) - 1,
        bz = a.lookBlock ? a.blockZ : (int) Math.floor(point.z);
    boolean hit =
        mc.hitResult instanceof BlockHitResult b
            && b.getBlockPos().getX() == bx
            && b.getBlockPos().getY() == by
            && b.getBlockPos().getZ() == bz;
    if (!SmoothTurn.isActive() && hit) {
      if (core.issueOnce()) {
        KeyMapping.click(
            net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(
                mc.options.keyUse));
        clicked = now;
      }
      return a.cooldownMs == 0;
    }
    return now - started > 4000;
  }
}
