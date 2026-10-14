package com.farmmacro.fun;

/** Pure cosmetic colour wash: smooth 108 BPM pulse, not an audio beat detector. */
public final class FeverLights {
  public static final int BANDS = 32;
  public static final int MAX_ALPHA = 46;

  public record Wash(int top, int bottom) {}

  public static Wash sample(long elapsedMs, int band, int strength) {
    double t = Math.max(0, elapsedMs) / 1000.0;
    double x = (Math.clamp(band, 0, BANDS - 1) + .5) / BANDS;
    double pulse = .5 + .5 * Math.sin(2 * Math.PI * 1.8 * t);
    double sweep = .5 + .5 * Math.cos(2 * Math.PI * (x - t / 4.0));
    double fade = Math.min(1, t / .4);
    int alpha =
        (int)
            Math.round(
                MAX_ALPHA
                    * Math.clamp(strength, 0, 100)
                    / 100.0
                    * fade
                    * (.55 + .45 * pulse)
                    * (.55 + .45 * sweep));
    double hue = (t / 8 + x * .32) % 1;
    return new Wash((alpha << 24) | rgb(hue), (alpha << 24) | rgb((hue + .18) % 1));
  }

  private static int rgb(double hue) {
    double h = hue * 6;
    int sector = (int) h;
    double f = h - sector, p = .22, q = 1 - .78 * f, t = .22 + .78 * f;
    double r, g, b;
    switch (sector % 6) {
      case 0 -> {
        r = 1;
        g = t;
        b = p;
      }
      case 1 -> {
        r = q;
        g = 1;
        b = p;
      }
      case 2 -> {
        r = p;
        g = 1;
        b = t;
      }
      case 3 -> {
        r = p;
        g = q;
        b = 1;
      }
      case 4 -> {
        r = t;
        g = p;
        b = 1;
      }
      default -> {
        r = 1;
        g = p;
        b = q;
      }
    }
    return ((int) Math.round(r * 255) << 16)
        | ((int) Math.round(g * 255) << 8)
        | (int) Math.round(b * 255);
  }
}
