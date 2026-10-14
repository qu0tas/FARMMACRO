package com.farmmacro.navtest;

import com.farmmacro.config.ModConfig;
import com.farmmacro.route.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Opt-in local integration test. Not shipped in production JAR. Never connect this to a public
 * server.
 */
public final class NavClientTest implements ClientModInitializer {
  final ReturnWalker walker = new ReturnWalker();
  int scene = -1, tick, wait = 140;
  boolean running, done;
  double goalX = 15.5, goalY=64, goalZ = .5;
  final boolean heightOnly=Boolean.getBoolean("farmmacro.navHeightOnly");
  int longLeg;
  double lastX, lastZ, lastYaw, maxYawTick, maxSpeed;
  int motionTicks, idleTicks;
  final List<String> results = new ArrayList<>();
  String[] names = {
    "open",
    "wall-corner",
    "one-block-corridor",
    "single-obstacle",
    "slalom",
    "steps",
    "dead-end",
    "dynamic-obstacle",
    "stop-at-goal",
    "teleport-cancel",
    "moving-target",
    "long-run",
    "death-cancel"
  };

  public void onInitializeClient() {
    if (!Boolean.getBoolean("farmmacro.navTest")) return;
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(
        ctx -> {
          var mc = Minecraft.getInstance();
          if (running && walker.active() && ModConfig.INSTANCE.pathDebug && mc.player != null)
            com.farmmacro.visual.RouteRenderer.collectNavigation(ctx, mc, walker);
        });
    HudElementRegistry.addLast(
        Identifier.fromNamespaceAndPath("farmmacro", "navtest"),
        (g, d) -> {
          g.text(
              Minecraft.getInstance().font,
              "LOCAL IN-GAME NAV TEST "
                  + (scene < 0 ? "setup" : names[Math.min(scene, names.length - 1)])
                  + " | "
                  + (running ? walker.motionState() : "setup"),
              8,
              8,
              0xff80ffff,
              true);
        });
  }

  void command(Minecraft mc, String s) {
    mc.getConnection().sendCommand(s);
  }

  void setup(Minecraft mc) {
    walker.stop(mc);
    ModConfig.INSTANCE.pathDebug = scene != 0;
    mc.options.guiScale().set(scene < 4 ? 2 : 3);
    mc.resizeGui();
    command(mc, "fill -20 64 -20 25 70 20 air");
    command(mc, "fill -20 63 -20 25 63 20 stone");
    command(mc, "time set day");
    command(mc, "weather clear");
    command(mc, "tp @s 0.5 64 0.5 -90 0");
    switch (scene) {
      case 1 -> command(mc, "fill 5 64 -4 6 66 4 stone");
      case 2 -> {
        command(mc, "fill -2 64 -1 17 66 -1 stone");
        command(mc, "fill -2 64 1 17 66 1 stone");
      }
      case 3 -> command(mc, "fill 5 64 0 5 65 0 stone");
      case 4 -> {
        command(mc, "fill 4 64 -5 4 66 0 stone");
        command(mc, "fill 8 64 0 8 66 5 stone");
      }
      case 5 -> {
        command(mc, "fill 4 64 -3 7 64 3 stone");
        command(mc, "fill 8 64 -3 10 65 3 stone");
      }
      case 6 -> {
        command(mc, "fill -2 64 -2 2 66 -2 stone");
        command(mc, "fill -2 64 2 2 66 2 stone");
        command(mc, "fill -2 64 -2 -2 66 2 stone");
        command(mc, "fill 2 64 -2 2 66 2 stone");
      }
    }
    goalX = scene==5 ? 9.5 : 15.5;
    goalY = scene==5 ? 66 : 64;
    goalZ = .5;
    longLeg = 0;
    tick = 0;
    maxYawTick = maxSpeed = 0;
    motionTicks = idleTicks = 0;
    wait = 45;
    running = false;
  }

  void tick(Minecraft mc) {
    if (done || mc.player == null || mc.level == null) return;
    if (mc.getCurrentServer() == null || !mc.getCurrentServer().ip.equals("127.0.0.1:25567"))
      throw new IllegalStateException("Nav test is local only");
    if(scene<0 && !mc.player.isAlive()){mc.player.respawn();return;}
    if (mc.screen != null) {
      mc.setScreen(null);
    }
    if (wait-- > 0) return;
    if (scene < 0) {
      scene = heightOnly ? 5 : 0;
      setup(mc);
      return;
    }
    if (!running) {
      ModConfig.INSTANCE.returnMaxDist = 32;
      ModConfig.INSTANCE.pathNaturalLook = true;
      ModConfig.INSTANCE.returnTurnSpeed = 180;
      ModConfig.INSTANCE.returnFly = false;
      lastX = mc.player.getX();
      lastZ = mc.player.getZ();
      lastYaw = mc.player.getYRot();
      walker.begin(
          mc,
          PathFinder.pointGoal(goalX, goalY, goalZ, 0),
          j -> new float[] {-90, 0},
          "local integration test");
      running = true;
      tick = 0;
      return;
    }
    tick++;
    if (scene == 7 && tick == 25) command(mc, "fill 6 64 -3 6 66 3 stone");
    if (scene == 10 && tick == 30) {
      goalZ = 6.5;
      walker.updateGoal(mc, goalX, 64, goalZ);
    }
    if (scene == 9 && tick == 25) command(mc, "tp @s 20 64 10 -90 0");
    if (scene == 12 && tick == 25) command(mc, "kill @s");
    double speed = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ);
    maxSpeed = Math.max(maxSpeed, speed);
    maxYawTick =
        Math.max(maxYawTick, Math.abs(WalkCore.wrap(mc.player.getYRot() - (float) lastYaw)));
    if (speed > .02) motionTicks++;
    else idleTicks++;
    lastX = mc.player.getX();
    lastZ = mc.player.getZ();
    lastYaw = mc.player.getYRot();
    ReturnWalker.Status status = walker.tick(mc, ModConfig.INSTANCE);
    if (scene == 11 && status == ReturnWalker.Status.DONE && longLeg < 7) {
      longLeg++;
      double[][] corners = {{15.5, .5}, {15.5, 10.5}, {.5, 10.5}, {.5, .5}};
      goalX = corners[longLeg % 4][0];
      goalZ = corners[longLeg % 4][1];
      walker.begin(
          mc,
          PathFinder.pointGoal(goalX, 64, goalZ, 0),
          j -> new float[] {mc.player.getYRot(), mc.player.getXRot()},
          "long run");
      return;
    }
    if (status != ReturnWalker.Status.RUNNING || tick > 1600) {
      double error = Math.hypot(mc.player.getX() - goalX, mc.player.getZ() - goalZ);
      boolean expectedFail = scene == 6 || scene == 9 || scene == 12;
      boolean pass =
          expectedFail
              ? status == ReturnWalker.Status.FAIL
              : status == ReturnWalker.Status.DONE && error < .65 && Math.abs(mc.player.getY()-goalY)<.65;
      String result =
          String.format(
              Locale.ROOT,
              "%s: %s status=%s ticks=%d goalError=%.3f maxYaw/tick=%.2f maxSpeed/tick=%.3f"
                  + " moving=%d idle=%d replans=%d fail=%s",
              names[scene],
              pass ? "PASS" : "FAIL",
              status,
              tick,
              error,
              maxYawTick,
              maxSpeed,
              motionTicks,
              idleTicks,
              walker.replans(),
              walker.failText());
      results.add(result);
      System.out.println("NAVTEST " + result);
      net.minecraft.client.Screenshot.grab(
          new java.io.File("."), "navtest-" + scene + ".png", mc.getMainRenderTarget(), 1, c -> {});
      walker.stop(mc);
      release(mc);
      try {
        Files.write(Path.of("navtest-results.txt"), results);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      scene++;
      if (heightOnly || scene >= names.length) {
        done = true;
        System.out.println("NAVTEST COMPLETE");
        mc.stop();
        return;
      }
      setup(mc);
    }
  }

  void release(Minecraft mc) {
    for (var k :
        new net.minecraft.client.KeyMapping[] {
          mc.options.keyUp,
          mc.options.keyDown,
          mc.options.keyLeft,
          mc.options.keyRight,
          mc.options.keyJump,
          mc.options.keyShift,
          mc.options.keySprint
        }) k.setDown(false);
  }
}
