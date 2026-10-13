package com.farmmacro.fun;

import java.util.SplittableRandom;

/** Pure, time-based cosmetic motion. No world input or player controls. */
public final class FeverMotion {
  public record Pose(double x, double y, float radians) {}

  public static Pose pose(
      long seed, long elapsedMs, int index, int screenW, int screenH, int w, int h) {
    double sec = Math.max(0, elapsedMs) / 1000.0;
    var random = new SplittableRandom(seed ^ (0x9e3779b97f4a7c15L * (index + 1)));
    double startX = random.nextDouble(), startY = random.nextDouble();
    double speedX = random.nextDouble(.18, .34), speedY = random.nextDouble(.12, .25);
    if (random.nextBoolean()) speedX = -speedX;
    if (random.nextBoolean()) speedY = -speedY;
    boolean running = index % 3 != 0, spinning = index % 3 == 2;
    float angle =
        spinning
            ? (float)
                Math.IEEEremainder(
                    sec * random.nextDouble(.9, 2.1) * (random.nextBoolean() ? 1 : -1), Math.PI * 2)
            : 0;
    double halfX = spinning ? Math.hypot(w, h) / 2.0 : w / 2.0;
    double halfY = spinning ? Math.hypot(w, h) / 2.0 : h / 2.0;
    double usableX = Math.max(0, screenW - 2 * halfX - 4),
        usableY = Math.max(0, screenH - 2 * halfY - 4);
    double nx, ny;
    if (running) {
      nx = bounce(startX + sec * speedX);
      ny = bounce(startY + sec * speedY);
    } else {
      long step = (long) (sec / 1.8);
      var from = new SplittableRandom(seed ^ (step * 7919 + index * 104729));
      var to = new SplittableRandom(seed ^ ((step + 1) * 7919 + index * 104729));
      double u = Math.min(1, (sec - step * 1.8) / .75);
      u = u * u * (3 - 2 * u);
      double fx = from.nextDouble(), fy = from.nextDouble();
      nx = fx + (to.nextDouble() - fx) * u;
      ny = fy + (to.nextDouble() - fy) * u;
    }
    return new Pose(
        screenW / 2.0 + (nx - .5) * usableX, screenH / 2.0 + (ny - .5) * usableY, angle);
  }

  private static double bounce(double value) {
    double v = value - 2 * Math.floor(value / 2);
    return v <= 1 ? v : 2 - v;
  }
}
