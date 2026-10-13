package com.farmmacro.route;

import java.util.SplittableRandom;

public final class PointActionCore {
  public enum Result {
    WAIT,
    DONE,
    PANIC
  }

  private PointAction a;
  private long deadline;
  private boolean issued;
  public String failure;

  public void start(PointAction action, long now, long seed) {
    a = action.copy();
    a.sanitize();
    issued = false;
    failure = null;
    int j = a.jitterMs;
    long wait = a.waitMs + (j == 0 ? 0 : new SplittableRandom(seed).nextInt(-j, j + 1));
    deadline = now + ("position".equals(a.type) ? a.timeoutMs : Math.max(0, wait));
  }

  public Result tick(long now, double distance) {
    return switch (a.type) {
      case "wait" -> now >= deadline ? Result.DONE : Result.WAIT;
      case "position" -> {
        if (distance <= a.radius) yield Result.DONE;
        if (now < deadline) yield Result.WAIT;
        if (a.panicOnTimeout) {
          failure = "Точка: таймаут ожидания позиции";
          yield Result.PANIC;
        }
        yield Result.DONE;
      }
      default -> Result.DONE;
    };
  }

  public boolean issueOnce() {
    if (issued) return false;
    issued = true;
    return true;
  }
}
