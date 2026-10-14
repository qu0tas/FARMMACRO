package com.farmmacro.route;

/** Simplified keyboard/inertia integration; real client integration is a separate harness. */
public final class GroundGuideCheck {
  static int checks;

  static void check(boolean v, String m) {
    if (!v) throw new IllegalStateException(m);
    checks++;
  }

  static final class World implements PathFinder.World {
    boolean obstacle;

    public double top(int x, int y, int z) {
      return y == 0 ? 1 : obstacle && x == 3 && z == 0 && y <= 2 && y > 0 ? 1 : -1;
    }

    public int hazard(int x, int y, int z) {
      return 0;
    }
  }

  public static void main() {
    World w = new World();
    GroundProbe probe = new GroundProbe(w);
    check(probe.clear(.5, 1, .5, 6, .5, .31), "open floor");
    w.obstacle = true;
    check(!probe.clear(.5, 1, .5, 6, .5, .31), "obstacle veto");
    check(probe.clear(.5, 1, 1.5, 6, 1.5, .31), "other side passable");
    PathFinder.World narrow =
        new PathFinder.World() {
          public double top(int x, int y, int z) {
            return y == 0 ? 1 : y < 3 && (z == 0 || z == 2) ? 1 : -1;
          }

          public int hazard(int x, int y, int z) {
            return 0;
          }
        };
    check(new GroundProbe(narrow).clear(.5, 1, 1.5, 8, 1.5, .31), "one-block corridor fits");
    check(!new GroundProbe(narrow).clear(.5, 1, 1.2, 8, 1.2, .31), "body edge hits wall");
    GroundGuide g = new GroundGuide();
    WalkCore.Out out = new WalkCore.Out();
    double x = .5, z = .5, vx = 0, vz = 0;
    float yaw = -90;
    boolean avoided = false;
    for (int i = 0; i < 500; i++) {
      g.tick(
          new GroundProbe(w),
          x,
          1,
          z,
          vx,
          vz,
          yaw,
          7.5,
          .5,
          Math.hypot(7.5 - x, .5 - z),
          false,
          out);
      avoided |= g.state == GroundGuide.State.AVOID;
      float want = WalkCore.yawTo(x, z, g.aimX, g.aimZ);
      yaw += Math.clamp(WalkCore.wrap(want - yaw), -8, 8);
      double f = out.forward ? 1 : out.back ? -1 : 0,
          l = out.left ? 1 : out.right ? -1 : 0,
          n = Math.hypot(f, l);
      if (n > 0) {
        f /= n;
        l /= n;
      }
      double r = Math.toRadians(yaw),
          ax = -Math.sin(r) * f + Math.cos(r) * l,
          az = Math.cos(r) * f + Math.sin(r) * l;
      vx = vx * .546 + ax * .098;
      vz = vz * .546 + az * .098;
      check(new GroundProbe(w).clear(x, 1, z, x + vx, z + vz, .299), "no obstacle intersection");
      x += vx;
      z += vz;
      if (Math.hypot(7.5 - x, .5 - z) < .35) break;
    }
    check(avoided, "local avoidance exercised");
    check(Math.hypot(7.5 - x, .5 - z) < .4, "reaches target after local detour");
    g.reset();
    check(g.blockedTicks() == 0, "reset");
    PathFinder.Params prm = new PathFinder.Params();
    prm.cancelled = () -> true;
    check(
        !new PathFinder(w, prm).find(.5, 1, .5, true, PathFinder.pointGoal(7.5, 1, .5, 0)).ok,
        "cancelled planner");
    PathFinder.World cliff =
        new PathFinder.World() {
          public double top(int x, int y, int z) {
            return y == 0 && x < 3 ? 1 : -1;
          }

          public int hazard(int x, int y, int z) {
            return 0;
          }
        };
    check(!new GroundProbe(cliff).clear(.5, 1, .5, 5, .5, .31), "no local correction across cliff");
    System.out.println("GroundGuideCheck: " + checks + " checks — OK");
  }
}
