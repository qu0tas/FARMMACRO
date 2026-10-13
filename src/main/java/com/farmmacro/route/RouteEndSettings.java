package com.farmmacro.route;

import java.util.List;

/** Route-local end policy. Defaults preserve old routes. Times are milliseconds. */
public class RouteEndSettings {
  public String mode = "none", direction = "auto", command = "/warp garden";
  public double distance = 3;
  public int delayMinMs = 500,
      delayMaxMs = 1200,
      windowSeconds = 8,
      postPauseMs = 1000,
      postJitterMs = 300;

  public boolean walks() {
    return "walk".equals(mode) || "both".equals(mode);
  }

  public boolean commands() {
    return "command".equals(mode) || "both".equals(mode);
  }

  public boolean enabled() {
    return walks() || commands();
  }

  public RouteEndSettings copy() {
    var s = new RouteEndSettings();
    s.mode = mode;
    s.direction = direction;
    s.command = command;
    s.distance = distance;
    s.delayMinMs = delayMinMs;
    s.delayMaxMs = delayMaxMs;
    s.windowSeconds = windowSeconds;
    s.postPauseMs = postPauseMs;
    s.postJitterMs = postJitterMs;
    return s;
  }

  public static String cleanCommand(String s) {
    return s == null
        ? ""
        : s.replace("\r", "")
            .replace("\n", "")
            .trim()
            .substring(0, Math.min(256, s.replace("\r", "").replace("\n", "").trim().length()));
  }

  public void sanitize() {
    if (!List.of("none", "walk", "command", "both").contains(mode == null ? "" : mode))
      mode = "none";
    if (!RoutePoint.DROP_DIRS.contains(direction == null ? "" : direction)) direction = "auto";
    distance = Double.isFinite(distance) ? Math.clamp(distance, 1, 30) : 3;
    command = cleanCommand(command);
    if (command.isEmpty()) command = "/warp garden";
    delayMinMs = Math.clamp(delayMinMs, 0, 60000);
    delayMaxMs = Math.clamp(delayMaxMs, delayMinMs, 60000);
    windowSeconds = Math.clamp(windowSeconds, 1, 60);
    postPauseMs = Math.clamp(postPauseMs, 0, 60000);
    postJitterMs = Math.clamp(postJitterMs, 0, 60000);
  }

  public double[] vector(List<RoutePoint> pts) {
    if (pts.isEmpty()) return new double[] {0, 0};
    var p = pts.getLast().copy();
    p.dropDir = direction;
    var prev = pts.size() > 1 ? pts.get(pts.size() - 2) : p;
    return p.dropVector(prev.x, prev.z, pts.size() > 1, null);
  }
}
