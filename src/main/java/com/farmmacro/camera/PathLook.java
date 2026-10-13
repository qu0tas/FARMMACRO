package com.farmmacro.camera;

import com.farmmacro.route.*;
import java.util.List;

/** Distance-based terrain lookahead; player eye height means a mild downward walking gaze. */
public final class PathLook {
  public static float[] angles(
      List<RoutePoint> points,
      int target,
      double x,
      double y,
      double z,
      double ahead,
      float fallbackYaw) {
    if (points.isEmpty()) return new float[] {fallbackYaw, 0};
    double ax = x, ay = y, az = z, need = Math.clamp(ahead, .5, 8), lx = x, ly = y, lz = z;
    for (int i = Math.clamp(target, 0, points.size() - 1); i < points.size(); i++) {
      var p = points.get(i);
      double dist =
          Math.sqrt(Math.pow(p.x - ax, 2) + Math.pow(p.y - ay, 2) + Math.pow(p.z - az, 2));
      double k = dist < 1e-6 ? 1 : Math.min(1, need / dist);
      lx = ax + (p.x - ax) * k;
      ly = ay + (p.y - ay) * k;
      lz = az + (p.z - az) * k;
      if (dist >= need || i == points.size() - 1) break;
      need -= dist;
      ax = p.x;
      ay = p.y;
      az = p.z;
    }
    double h = Math.hypot(lx - x, lz - z);
    float yaw = h > .1 ? WalkCore.yawTo(x, z, lx, lz) : fallbackYaw;
    float pitch =
        (float)
            Math.clamp(
                -Math.toDegrees(Math.atan2(ly + 1.15 - (y + 1.62), Math.max(.5, h))), -45, 45);
    return new float[] {yaw, pitch};
  }
}
