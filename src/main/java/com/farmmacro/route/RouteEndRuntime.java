package com.farmmacro.route;

import com.farmmacro.macro.*;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;

/** Main-thread adapter. Only expected position/rotation packets bypass their detector. */
public final class RouteEndRuntime {
  public static final RouteEndRuntime INSTANCE = new RouteEndRuntime();
  public final RouteEndCore core = new RouteEndCore();
  private String command;
  private boolean begun;

  public boolean begun() {
    return begun;
  }

  public void begin(Minecraft mc) {
    if(com.farmmacro.panic.PanicHandoff.pending()){cancel();return;}
    begun = true;
    var w = RouteWalker.INSTANCE;
    var s = w.activeSettings().routeEnd;
    command = s.command;
    core.start(s, w.activePoints(), now(), w.variantSeed() ^ System.nanoTime());
    MouseHold.INSTANCE.suspend(true);
    release(mc);
  }

  public static long now() {
    return System.nanoTime() / 1000000;
  }

  public boolean tick(Minecraft mc) {
    if(com.farmmacro.panic.PanicHandoff.pending()){cancel();release(mc);return false;}
    var p = mc.player;
    if (p == null) {
      cancel();
      return false;
    }
    MouseHold.INSTANCE.suspend(true);
    release(mc);
    core.tick(now(), p.getX(), p.getY(), p.getZ(), p.onGround());
    if (RouteWalker.INSTANCE.activeSettings().routeEnd.walks()
        && !p.onGround()
        && (core.phase == RouteEndCore.Phase.WAIT || core.phase == RouteEndCore.Phase.DELAY))
      PanicDetector.INSTANCE.allowFall(10);
    if (core.phase == RouteEndCore.Phase.EXIT) {
      WalkCore.Out o = new WalkCore.Out();
      WalkCore.keysFor(core.dx, core.dz, p.getYRot(), o);
      MacroManager.press(mc.options.keyUp, o.forward);
      MacroManager.press(mc.options.keyDown, o.back);
      MacroManager.press(mc.options.keyLeft, o.left);
      MacroManager.press(mc.options.keyRight, o.right);
      PanicDetector.INSTANCE.allowFall(10);
    }
    if (core.phase == RouteEndCore.Phase.FALL) PanicDetector.INSTANCE.allowFall(10);
    if (core.sendCommand) send(mc, command);
    if (core.phase == RouteEndCore.Phase.FAIL) {
      PanicDetector.INSTANCE.triggerPanic(mc, core.failure);
      return false;
    }
    return core.phase == RouteEndCore.Phase.DONE;
  }

  public boolean serverMove(String kind, double distance) {
    boolean tp = "телепорта".equals(kind);
    boolean rot = "поворота".equals(kind) || "взгляда (look at)".equals(kind);
    if (!tp && !rot) return false;
    return core.serverMove(now(), tp);
  }

  public static void send(Minecraft mc, String text) {
    if(com.farmmacro.panic.PanicHandoff.pending())return;
    text = RouteEndSettings.cleanCommand(text);
    if (text.isBlank() || mc.getConnection() == null) return;
    if (text.startsWith("/")) {
      if (text.length() > 1) mc.getConnection().sendCommand(text.substring(1));
    } else mc.getConnection().sendChat(text);
  }

  private static void release(Minecraft mc) {
    var o = mc.options;
    for (var k :
        new net.minecraft.client.KeyMapping[] {
          o.keyUp,
          o.keyDown,
          o.keyLeft,
          o.keyRight,
          o.keyJump,
          o.keySprint,
          o.keyShift,
          o.keyAttack,
          o.keyUse
        }) MacroManager.press(k, false);
  }

  public void cancel() {
    core.cancel();
    begun = false;
  }
}
