package com.farmmacro.route;

import com.farmmacro.camera.TrackingTurn;
import java.util.*;

public final class ReturnGuidedCheck {
  static int checks;

  static void check(boolean v, String m) {
    if (!v) throw new IllegalStateException(m);
    checks++;
  }

  static final class World implements PathFinder.World {
    Set<Long> blocks = new HashSet<>();

    long key(int x, int z) {
      return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public double top(int x, int y, int z) {
      return y == 0 ? 1 : y >= 1 && y < 4 && blocks.contains(key(x, z)) ? 1 : -1;
    }

    public int hazard(int x, int y, int z) {
      return 0;
    }
  }

  public static void main() {
    for (int seed = 0; seed < 60; seed++) {
      World world = new World();
      SplittableRandom rnd = new SplittableRandom(seed);
      for (int i = 0; i < 16; i++) {
        int x = rnd.nextInt(3, 14), z = rnd.nextInt(-5, 6);
        world.blocks.add(world.key(x, z));
      }
      var prm = new PathFinder.Params();
      prm.maxDist = 32;
      var result =
          new PathFinder(world, prm).find(.5, 1, .5, true, PathFinder.pointGoal(16.5, 1, .5, 0));
      check(result.ok, "path exists seed " + seed);
      ReturnCore c = new ReturnCore();
      c.naturalLook = true;
      c.steeringWorld = world;
      c.start(result.points, .5, .5, -90, 0);
      TrackingTurn camera = new TrackingTurn();
      double x = .5, z = .5, vx = 0, vz = 0;
      float yaw = (float) rnd.nextDouble(-180, 180), pitch = 0;
      WalkCore.Out out = new WalkCore.Out();
      int replans = 0, collisions = 0;
      for (int tick = 0; tick < 1600 && c.phase() != ReturnCore.Phase.DONE; tick++) {
        var phase = c.tick(x, 1, z, true, false, yaw, pitch, false, out);
        if (phase == ReturnCore.Phase.REPLAN) {
          check(
              ++replans <= 4,
              "replan budget seed "
                  + seed
                  + ": "
                  + c.reason
                  + " x="
                  + x
                  + " z="
                  + z
                  + " target="
                  + c.target()
                  + " points="
                  + c.points().stream().map(a -> a.x + "/" + a.z).toList());
          result =
              new PathFinder(world, prm).find(x, 1, z, true, PathFinder.pointGoal(16.5, 1, .5, 0));
          check(result.ok, "replanned path seed " + seed);
          c.start(result.points, x, z, -90, 0);
          continue;
        }
        for (int f = 0; f < 3; f++) {
          var d = camera.frame(yaw, pitch, c.wantYaw, c.wantPitch, .5, 180, 1.0 / 60);
          yaw += d.yaw();
          pitch += d.pitch();
        }
        double forward = out.forward ? 1 : out.back ? -1 : 0,
            left = out.left ? 1 : out.right ? -1 : 0,
            n = Math.hypot(forward, left);
        if (n > 0) {
          forward /= n;
          left /= n;
        }
        double a = Math.toRadians(yaw);
        vx = vx * .546 + (-Math.sin(a) * forward + Math.cos(a) * left) * .098;
        vz = vz * .546 + (Math.cos(a) * forward + Math.sin(a) * left) * .098;
        if (new GroundProbe(world).clear(x, 1, z, x + vx, z + vz, .299)) {
          x += vx;
          z += vz;
        } else {
          vx = vz = 0;
          collisions++;
        }
        check(Double.isFinite(x) && Double.isFinite(z), "finite movement");
      }
      check(
          c.phase() == ReturnCore.Phase.DONE,
          "arrives seed "
              + seed
              + " phase "
              + c.phase()
              + " x="
              + x
              + " z="
              + z
              + " reason="
              + c.reason);
      check(Math.hypot(x - 16.5, z - .5) < .65, "goal tolerance seed " + seed);
      check(collisions < 20, "collision bound seed " + seed);
    }
    System.out.println(
        "ReturnGuidedCheck: "
            + checks
            + " checks — OK (60 obstacle fields, keyboard inertia + continuous camera)");
  }
}
