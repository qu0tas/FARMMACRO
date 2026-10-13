package com.farmmacro.fun;

public final class FeverMotionCheck {
  private static int checks;

  private static void check(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
    checks++;
  }

  public static void main() {
    for (int seed = 0; seed < 12; seed++)
      for (int index = 0; index < 8; index++)
        for (int t = 0; t <= 60000; t += 50) {
          var p = FeverMotion.pose(seed, t, index, 640, 360, 100, 100);
          check(
              Double.isFinite(p.x()) && Double.isFinite(p.y()) && Float.isFinite(p.radians()),
              "finite pose");
          double radius = index % 3 == 2 ? Math.hypot(100, 100) / 2 : 50;
          check(
              p.x() - radius >= -1e-5
                  && p.x() + radius <= 640.00001
                  && p.y() - radius >= -1e-5
                  && p.y() + radius <= 360.00001,
              "rotated sprite stays inside screen");
          var again = FeverMotion.pose(seed, t, index, 640, 360, 100, 100);
          check(p.equals(again), "seeded motion");
          if (index % 3 != 0 && t > 0) {
            var prev = FeverMotion.pose(seed, t - 50, index, 640, 360, 100, 100);
            check(
                Math.hypot(p.x() - prev.x(), p.y() - prev.y()) < 15, "continuous runner, no jumps");
          }
        }
    var a = FeverMotion.pose(2, 0, 1, 640, 360, 100, 100);
    var b = FeverMotion.pose(2, 1000, 1, 640, 360, 100, 100);
    check(Math.hypot(a.x() - b.x(), a.y() - b.y()) > 20, "runner moves");
    check(FeverMotion.pose(2, 1000, 2, 640, 360, 100, 100).radians() != 0, "some dancers rotate");
    check(
        FeverMotion.pose(2, 1000, 0, 640, 360, 100, 100).radians() == 0, "not all dancers rotate");
    System.out.println("FeverMotionCheck: " + checks + " checks — OK");
  }
}
