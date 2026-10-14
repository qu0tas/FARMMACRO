package com.farmmacro.route;

/** Bounded live swept-footprint test for walking only, not a jump/fall/climb planner. */
public final class GroundProbe {
  private final PathFinder.World world;

  public GroundProbe(PathFinder.World world) {
    this.world = world;
  }

  private static int floor(double n) {
    return (int) Math.floor(n);
  }

  public boolean clear(double ax, double ay, double az, double bx, double bz, double radius) {
    double len = Math.hypot(bx - ax, bz - az);
    int n = Math.max(1, (int) Math.ceil(len / .12));
    if (n > 80) return false;
    double lastY = ay;
    for (int i = 0; i <= n; i++) {
      double t = (double) i / n, x = ax + (bx - ax) * t, z = az + (bz - az) * t;
      double effectiveRadius = Math.min(radius, .295 + len * t * .1);
      double y = support(x, z, lastY);
      if (!Double.isFinite(y) || Math.abs(y - lastY) > .6) return false;
      if (!world.bodyClear(x, y + .001, z, effectiveRadius, PathFinder.BODY - .002)) return false;
      for (int ix = -1; ix <= 1; ix++)
        for (int iz = -1; iz <= 1; iz++) {
          double ox = ix * effectiveRadius, oz = iz * effectiveRadius;
          int cx = floor(x + ox), cz = floor(z + oz);
          double f = support(x + ox, z + oz, y);
          if (!Double.isFinite(f) || Math.abs(f - y) > .6) return false;
          for (int cy = floor(y + .01); cy <= floor(y + PathFinder.BODY - .01); cy++) {
            int hz = world.hazard(cx, cy, cz);
            if (hz != PathFinder.H_NONE)
              return false; // Special actions retain their dedicated controller.
            double top = world.top(cx, cy, cz);
            if (top >= 0 && cy + top > y + .01) return false;
            int ed = world.edges(cx, cy, cz);
            if (((ed & PathFinder.E_NX) != 0 && x - effectiveRadius < cx + .2)
                || ((ed & PathFinder.E_PX) != 0 && x + effectiveRadius > cx + .8)
                || ((ed & PathFinder.E_NZ) != 0 && z - effectiveRadius < cz + .2)
                || ((ed & PathFinder.E_PZ) != 0 && z + effectiveRadius > cz + .8)) return false;
          }
        }
      lastY = y;
    }
    return true;
  }

  private double support(double x, double z, double y) {
    int cx = floor(x), cz = floor(z);
    for (int cy = floor(y + .6); cy >= floor(y - .6) - 1; cy--) {
      double top = world.top(cx, cy, cz), f = cy + top;
      if (top >= 0 && f <= y + .601 && f >= y - .601) {
        int hz = world.hazard(cx, cy, cz);
        if (hz == PathFinder.H_DANGER || hz == PathFinder.H_LAVA || hz == PathFinder.H_WATER)
          return Double.NaN;
        return f;
      }
    }
    return Double.NaN;
  }
}
