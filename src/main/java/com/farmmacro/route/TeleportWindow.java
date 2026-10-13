package com.farmmacro.route;

/** Single-use expectation, never extended by a second command/fall in the same event. */
public final class TeleportWindow {
  private boolean opened, consumed, trailingRotation;
  private long deadline, consumedAt;

  public void reset() {
    opened = consumed = trailingRotation = false;
    deadline = 0;
  }

  public boolean open(long now, long duration) {
    if (opened) return false;
    opened = true;
    deadline = now + Math.max(1, duration);
    return true;
  }

  public boolean waiting(long now) {
    return opened && !consumed && now < deadline;
  }

  public boolean expired(long now) {
    return opened && !consumed && now >= deadline;
  }

  public boolean accept(long now, boolean teleport) {
    if (consumed) {
      if (!teleport && !trailingRotation && now < deadline && now - consumedAt <= 500) {
        trailingRotation = true;
        return true;
      }
      return false;
    }
    if (!waiting(now)) return false;
    if (teleport) {
      consumed = true;
      consumedAt = now;
    }
    return true;
  }

  public boolean consumed() {
    return consumed;
  }

  public long deadline() {
    return deadline;
  }
}
