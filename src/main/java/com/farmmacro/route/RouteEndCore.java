package com.farmmacro.route;

import java.util.List;
import java.util.SplittableRandom;

/** End-of-route state machine: no game dependencies, injectable monotonic time and seed. */
public final class RouteEndCore {
  public enum Phase {
    IDLE,
    EXIT,
    FALL,
    DELAY,
    WAIT,
    POST,
    DONE,
    FAIL
  }

  public Phase phase = Phase.IDLE;
  public String failure;
  public boolean sendCommand;
  public final TeleportWindow window = new TeleportWindow();
  private RouteEndSettings s;
  private SplittableRandom random;
  private long until, exitDeadline;
  private boolean commandSent;
  private double fromY, fromX, fromZ;
  public double dx, dz, goalX, goalZ;

  public void start(RouteEndSettings settings, List<RoutePoint> points, long now, long seed) {
    cancel();
    s = settings.copy();
    s.sanitize();
    random = new SplittableRandom(seed);
    if (!s.enabled() || points.isEmpty()) {
      phase = Phase.DONE;
      return;
    }
    var p = points.getLast();
    fromX = p.x;
    fromY = p.y;
    fromZ = p.z;
    var v = s.vector(points);
    dx = v[0];
    dz = v[1];
    goalX = p.x + dx * s.distance;
    goalZ = p.z + dz * s.distance;
    exitDeadline = now + 30000;
    if (s.walks()) {
      if (Math.hypot(dx, dz) < .1) {
        fail("Rewarp: направление выхода не определено");
        return;
      }
      phase = Phase.EXIT;
    } else delay(now);
  }

  private int sample(int a, int b) {
    return a == b ? a : random.nextInt(a, b + 1);
  }

  private void delay(long now) {
    phase = Phase.DELAY;
    until = now + sample(s.delayMinMs, s.delayMaxMs);
  }

  private void expect(long now) {
    window.open(now, s.windowSeconds * 1000L);
    phase = Phase.WAIT;
  }

  public void tick(long now, double x, double y, double z, boolean ground) {
    sendCommand = false;
    if (phase == Phase.EXIT) {
      if (!ground && y < fromY - .25) {
        expect(now);
        if (s.commands()) delay(now);
      } else if ((x - fromX) * dx + (z - fromZ) * dz >= s.distance - .15) {
        if (s.commands()) delay(now);
        else {
          phase = Phase.FALL;
          until = now + 2000;
        }
      } else if (now >= exitDeadline) fail("Rewarp: не дошёл до выхода");
    }
    if (phase == Phase.FALL) {
      if (!ground && y < fromY - .25) expect(now);
      else if (now >= until) fail("Rewarp: выход не привёл к падению");
    }
    if (window.expired(now)) {
      fail("Rewarp: нет телепорта");
      return;
    }
    if (phase == Phase.DELAY && now >= until) {
      if (!commandSent) {
        commandSent = true;
        sendCommand = true;
      }
      expect(now);
    }
    if (phase == Phase.POST && now >= until) phase = Phase.DONE;
  }

  public boolean serverMove(long now, boolean teleport) {
    if (!window.accept(now, teleport)) return false;
    if (teleport) {
      sendCommand = false;
      phase = Phase.POST;
      until = now + Math.max(0, s.postPauseMs + sample(-s.postJitterMs, s.postJitterMs));
    }
    return true;
  }

  private void fail(String text) {
    failure = text;
    phase = Phase.FAIL;
  }

  public void cancel() {
    phase = Phase.IDLE;
    window.reset();
    sendCommand = false;
    commandSent = false;
    failure = null;
  }

  public boolean active() {
    return phase != Phase.IDLE && phase != Phase.DONE && phase != Phase.FAIL;
  }
}
