package com.farmmacro.camera;

/**
 * Continuous retargetable mouse controller. No per-target restart, random jitter or camera snap.
 */
public final class TrackingTurn {
  private final Axis yaw = new Axis(), pitch = new Axis();

  public record Delta(double yaw, double pitch) {}

  public Delta frame(
      double currentYaw,
      double currentPitch,
      double targetYaw,
      double targetPitch,
      double sensitivity,
      double maxSpeed,
      double dt) {
    if (!Double.isFinite(dt) || dt <= 0) return new Delta(0, 0);
    dt = Math.min(.1, dt);
    double step = MouseTurn.stepDeg(Math.clamp(sensitivity, 0, 1));
    double limit = Double.isFinite(maxSpeed) ? Math.clamp(maxSpeed, 20, 720) : 180;
    return new Delta(
        yaw.frame(wrap(targetYaw - currentYaw), step, limit, dt),
        pitch.frame(Math.clamp(targetPitch, -89, 89) - currentPitch, step, limit * .65, dt));
  }

  public void reset() {
    yaw.reset();
    pitch.reset();
  }

  public double yawVelocity() {
    return yaw.v;
  }

  public static double wrap(double x) {
    x %= 360;
    return x >= 180 ? x - 360 : x < -180 ? x + 360 : x;
  }

  private static final class Axis {
    double v, residual;

    void reset() {
      v = residual = 0;
    }

    double frame(double error, double step, double maxSpeed, double dt) {
      if (!Double.isFinite(error)) {
        reset();
        return 0;
      }
      if (Math.abs(error) < step * .55 && Math.abs(v) < 3) {
        reset();
        return 0;
      }
      double delta = 0, acc = maxSpeed * 4;
      int n = Math.max(1, (int) Math.ceil(dt / .008));
      double h = dt / n;
      for (int i = 0; i < n; i++) {
        double e = error - delta;
        double desired =
            Math.copySign(
                Math.min(maxSpeed, Math.min(Math.abs(e) * 7, Math.sqrt(2 * acc * Math.abs(e)))), e);
        v += Math.clamp(desired - v, -acc * h, acc * h);
        double d = v * h;
        if (Math.abs(d) > Math.abs(e) && Math.signum(d) == Math.signum(e)) {
          d = e;
          v = 0;
        }
        delta += d;
      }
      double quantized = Math.rint((delta + residual) / step) * step;
      // Never quantize beyond the goal. Deadband avoids perpetual one-pixel reversals.
      if (Math.signum(quantized) == Math.signum(error) && Math.abs(quantized) > Math.abs(error))
        quantized = Math.copySign(Math.floor(Math.abs(error) / step) * step, error);
      residual = delta + residual - quantized;
      residual = Math.clamp(residual, -step, step);
      return quantized;
    }
  }
}
