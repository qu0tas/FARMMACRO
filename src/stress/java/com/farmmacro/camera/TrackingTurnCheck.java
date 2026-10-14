package com.farmmacro.camera;

public final class TrackingTurnCheck {
  static int checks;

  static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
    checks++;
  }

  public static void main() {
    for (int fps : new int[] {20, 30, 60, 144, 240})
      for (double sens : new double[] {0, .1, .5, 1}) {
        TrackingTurn c = new TrackingTurn();
        double y = 179, p = 0, max = 0;
        for (int i = 0; i < fps * 12; i++) {
          double ty = i < fps * 3 ? -110 : i < fps * 7 ? -55 : -58, tp = i < fps * 4 ? 17 : -12;
          var d = c.frame(y, p, ty, tp, sens, 180, 1.0 / fps);
          check(Double.isFinite(d.yaw()) && Double.isFinite(d.pitch()), "finite");
          check(Math.abs(d.yaw()) <= 180.0 / fps + MouseTurn.stepDeg(sens) * 2, "speed cap");
          check(
              Math.abs(d.pitch()) <= 117.0 / fps + MouseTurn.stepDeg(sens) * 2,
              "independent pitch speed");
          double step = MouseTurn.stepDeg(sens);
          check(Math.abs(d.yaw() / step - Math.rint(d.yaw() / step)) < 1e-7, "mouse lattice");
          y += d.yaw();
          p += d.pitch();
          max = Math.max(max, Math.abs(d.yaw()));
        }
        check(
            Math.abs(TrackingTurn.wrap(y + 58)) < .2 && Math.abs(p + 12) < .2,
            "settles at final target");
        for (int i = 0; i < fps; i++) {
          var d = c.frame(y, p, -58, -12, sens, 180, 1.0 / fps);
          check(Math.abs(d.yaw()) < .001, "no terminal jitter");
        }
        c.reset();
        check(c.yawVelocity() == 0, "reset velocity");
      }
    TrackingTurn c = new TrackingTurn();
    check(c.frame(0, 0, 90, 0, .5, 180, Double.NaN).yaw() == 0, "invalid dt");
    c.frame(0, 0, 90, 0, .5, 180, .05);
    double v = c.yawVelocity();
    c.frame(1, 0, 95, 0, .5, 180, .05);
    check(c.yawVelocity() > v, "retarget preserves acceleration");
    System.out.println("TrackingTurnCheck: " + checks + " checks — OK");
  }
}
