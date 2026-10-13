package com.farmmacro.route;

import java.util.List;

/** v6 extended waypoint instruction. Simple mode ignores it without modifying the file. */
public class PointAction {
  public String type = "ordinary", itemId = "", command = "";
  public int waitMs = 1000, jitterMs = 0, timeoutMs = 10000, slot = 0, cooldownMs = 500;
  public double radius = .5;
  public boolean panicOnTimeout = true, lookBlock = false;
  public int blockX, blockY, blockZ;
  public static final List<String> TYPES =
      List.of("ordinary", "wait", "position", "item", "jump", "command");

  public boolean active() {
    return !"ordinary".equals(type);
  }

  public PointAction copy() {
    var a = new PointAction();
    a.type = type;
    a.itemId = itemId;
    a.command = command;
    a.waitMs = waitMs;
    a.jitterMs = jitterMs;
    a.timeoutMs = timeoutMs;
    a.slot = slot;
    a.cooldownMs = cooldownMs;
    a.radius = radius;
    a.panicOnTimeout = panicOnTimeout;
    a.lookBlock = lookBlock;
    a.blockX = blockX;
    a.blockY = blockY;
    a.blockZ = blockZ;
    return a;
  }

  public void sanitize() {
    if (type == null || !TYPES.contains(type)) type = "ordinary";
    waitMs = Math.clamp(waitMs, 0, 600000);
    jitterMs = Math.clamp(jitterMs, 0, 600000);
    timeoutMs = Math.clamp(timeoutMs, 50, 600000);
    cooldownMs = Math.clamp(cooldownMs, 0, 60000);
    slot = Math.clamp(slot, 0, 9);
    radius = Double.isFinite(radius) ? Math.clamp(radius, .05, 30) : .5;
    itemId = itemId == null ? "" : itemId.trim();
    if (itemId.length() > 128) itemId = itemId.substring(0, 128);
    command = RouteEndSettings.cleanCommand(command);
  }
}
