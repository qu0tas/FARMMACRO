package com.farmmacro.route;

/**
 * Receding-horizon local steering + keyboard hysteresis. Vanilla owns acceleration/diagonal
 * physics.
 */
public final class GroundGuide {
  private static final int[] SIDES = {-1, 1};
  private static final double[] ANGLES = {25, 45, 65, 85, 110, 135};
  private static final int[][] KEYS = {
    {1, 0}, {1, 1}, {1, -1}, {0, 1}, {0, -1}, {-1, 0}, {-1, 1}, {-1, -1}
  };

  public enum State {
    WALK,
    CORNER,
    AVOID,
    BRAKE,
    BLOCKED,
    ALIGN
  }

  public State state = State.WALK;
  public double aimX, aimZ, desiredSpeed;
  public double speedLimit=.215;
  private int side, sideTicks, blocked;
  public final double[] rayX = new double[25], rayZ = new double[25];
  public final boolean[] rayPass = new boolean[25];
  public int rays;

  private void ray(double x, double z, boolean pass) {
    if (rays < rayX.length) {
      rayX[rays] = x;
      rayZ[rays] = z;
      rayPass[rays++] = pass;
    }
  }

  private boolean strafeLeft, strafeRight, align, braking;

  public void reset() {
    side = sideTicks = blocked = 0;
    strafeLeft = strafeRight = align = braking = false;
    state = State.WALK;
  }

  public int blockedTicks() {
    return blocked;
  }

  public void tick(
      GroundProbe probe,
      double x,
      double y,
      double z,
      double vx,
      double vz,
      float yaw,
      double tx,
      double tz,
      double remaining,
      boolean corner,
      WalkCore.Out out) {
    rays = 0;
    double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
    aimX = tx;
    aimZ = tz;
    state = corner ? State.CORNER : State.WALK;
    if (d < 1e-6) {
      out.forward = out.back = out.left = out.right = false;
      return;
    }
    double ux = dx / d, uz = dz / d, horizon = Math.min(d, 1.15 + Math.hypot(vx, vz) * 2);
    boolean direct = probe.clear(x, y, z, x + ux * horizon, z + uz * horizon, .31);
    ray(x + ux * horizon, z + uz * horizon, direct);
    if (sideTicks > 0) sideTicks--;
    if (!direct) {
      double best = Double.POSITIVE_INFINITY, bx = 0, bz = 0;
      int chosen = 0;
      for (int s : SIDES)
        for (double angle : ANGLES) {
          double a = Math.toRadians(angle * s),
              cx = ux * Math.cos(a) - uz * Math.sin(a),
              cz = ux * Math.sin(a) + uz * Math.cos(a);
          double len = .85;
          boolean pass = probe.clear(x, y, z, x + cx * len, z + cz * len, .31);
          ray(x + cx * len, z + cz * len, pass);
          if (!pass) continue;
          double score = Math.hypot(tx - x - cx * len, tz - z - cz * len) + angle * .004;
          if (sideTicks > 0 && side != s) score += .8;
          if (!probe.clear(x, y, z, x + cx * len, z + cz * len, .40)) score += .2;
          if (score < best) {
            best = score;
            bx = cx;
            bz = cz;
            chosen = s;
          }
        }
      if (chosen == 0) {
        blocked++;
        state = State.BLOCKED;
        out.forward = out.back = out.left = out.right = false;
        return;
      }
      if (chosen != side) {
        side = chosen;
        sideTicks = 14;
      }
      ux = bx;
      uz = bz;
      aimX = x + ux * .85;
      aimZ = z + uz * .85;
      state = State.AVOID;
    }
    double heading = WalkCore.yawTo(x, z, x + ux, z + uz),
        error = Math.abs(WalkCore.wrap((float) heading - yaw));
    if (align ? error < 55 : error > 110) align = !align;
    double speed = Math.hypot(vx, vz);
    desiredSpeed = Math.min(Math.clamp(speedLimit,.15,.29), Math.sqrt(Math.max(0, remaining - .12) * .11));
    if (corner) desiredSpeed = Math.min(desiredSpeed, .14);
    if (state == State.AVOID) desiredSpeed = Math.min(desiredSpeed, .15);
    out.sprint = false;
    if (align) {
      out.forward = out.back = out.left = out.right = false;
      state = State.ALIGN;
      return;
    }
    double ry = Math.toRadians(yaw),
        f = ux * -Math.sin(ry) + uz * Math.cos(ry),
        l = ux * Math.cos(ry) + uz * Math.sin(ry);
    strafeLeft = l > (strafeLeft ? .43 : .64);
    strafeRight = l < -(strafeRight ? .43 : .64);
    out.forward = f > .29;
    out.back = f < -.47;
    out.left = strafeLeft;
    out.right = strafeRight;
    // Predict inertia before committing a key. Releasing keys slows naturally; no alternating
    // reverse pulses.
    double forwardVelocity = vx * ux + vz * uz;
    // Keyboard walking has no analog throttle. Coast near the final stop, not pulse on every bend.
    boolean precision=remaining<1.4;
    braking=precision && forwardVelocity>0 && (braking ? speed>desiredSpeed-.04 : speed>desiredSpeed+.05);
    if (braking) {
      out.forward = out.back = out.left = out.right = false;
      state = State.BRAKE;
    }
    double fx = out.forward ? 1 : out.back ? -1 : 0,
        lx = out.left ? 1 : out.right ? -1 : 0,
        n = Math.hypot(fx, lx);
    if (n > 0) {
      fx /= n;
      lx /= n;
    }
    double ax = -Math.sin(ry) * fx + Math.cos(ry) * lx, az = Math.cos(ry) * fx + Math.sin(ry) * lx;
    double px = vx * .546 + ax * .098, pz = vz * .546 + az * .098;
    if (!probe.clear(x, y, z, x + px * 2, z + pz * 2, .30)) {
      // A safe geometric direction may fall between keyboard octants. Select another feasible
      // input rather than repeatedly releasing the same unsafe octant against a corner.
      double best = -Double.MAX_VALUE;
      int selected = -1;
      int[][] keys = KEYS;
      for (int i = 0; i < keys.length; i++) {
        double kf = keys[i][0], kl = keys[i][1], kn = Math.hypot(kf, kl);
        kf /= kn;
        kl /= kn;
        double kx = -Math.sin(ry) * kf + Math.cos(ry) * kl,
            kz = Math.cos(ry) * kf + Math.sin(ry) * kl;
        double dot = kx * ux + kz * uz;
        if (dot < .1) continue;
        double nx = vx * .546 + kx * .098, nz = vz * .546 + kz * .098;
        if (!probe.clear(x, y, z, x + nx * 2, z + nz * 2, .30)) continue;
        double score = dot - (keys[i][0] < 0 ? .1 : 0);
        if (score > best) {
          best = score;
          selected = i;
        }
      }
      out.forward = out.back = out.left = out.right = false;
      if (selected >= 0) {
        int[] k = keys[selected];
        out.forward = k[0] > 0;
        out.back = k[0] < 0;
        out.left = k[1] > 0;
        out.right = k[1] < 0;
        state = State.AVOID;
        blocked = 0;
      } else {
        state = State.BLOCKED;
        blocked++;
      }
    } else blocked = 0;
  }
}
