package com.farmmacro.loading;

/** Layout, progress and fade math without Minecraft. One virtual canvas fits every GUI scale. */
public final class LoadingLook {
  public static final int BACKGROUND = 0xFF090D17,
      ACCENT = 0xFF587EFF,
      LINE = 0xFF192640,
      TEXT = 0xFFE1F0FF;
  public static final int WIDTH = 960, HEIGHT = 540;

  public record Rect(double x, double y, double w, double h) {}

  public record Layout(double scale, double x, double y) {
    public Rect box(double bx, double by, double bw, double bh) {
      return new Rect(x + bx * scale, y + by * scale, bw * scale, bh * scale);
    }
  }

  public static Layout layout(int w, int h) {
    double s = Math.max(0, Math.min(w / (double) WIDTH, h / (double) HEIGHT));
    return new Layout(s, (w - WIDTH * s) / 2, (h - HEIGHT * s) / 2);
  }

  public static float progress(float previous, float actual) {
    if (!Float.isFinite(previous)) previous = 0;
    if (!Float.isFinite(actual)) actual = 0;
    return Math.clamp(Math.max(previous, previous * .95f + actual * .05f), 0, 1);
  }

  public static int percent(float progress, boolean finished) {
    return finished
        ? 100
        : Math.clamp((int) Math.floor((Float.isFinite(progress) ? progress : 0) * 100), 0, 99);
  }

  public static int stage(int percent) {
    return percent >= 100 ? 3 : percent >= 85 ? 2 : percent >= 30 ? 1 : 0;
  }

  public static float opacity(long now, long fadeInStart, long fadeOutStart, boolean fadeIn) {
    if (fadeOutStart >= 0)
      return (float) Math.clamp(1 - (now - fadeOutStart - 1000) / 1000.0, 0, 1);
    if (!fadeIn) return 1;
    return (float) Math.clamp((now - fadeInStart) / 500.0, 0, 1);
  }

  public static boolean removed(long now, long fadeOutStart) {
    return fadeOutStart >= 0 && now - fadeOutStart >= 2000;
  }

  public static int tint(int color, float alpha) {
    return ((int) Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (color & 0xFFFFFF);
  }
}
