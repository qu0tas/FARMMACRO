package com.farmmacro.fun;

import com.farmmacro.config.ModConfig;
import com.google.gson.Gson;

public final class FeverLightsCheck {
  private static int checks;

  private static void check(boolean value, String label) {
    if (!value) throw new IllegalStateException(label);
    checks++;
  }

  public static void main() {
    for (int strength : new int[] {-50, 0, 55, 100, 1000})
      for (int band = 0; band < FeverLights.BANDS; band++)
        for (long t = 0; t <= 60000; t += 16) {
          var w = FeverLights.sample(t, band, strength);
          check(w.equals(FeverLights.sample(t, band, strength)), "deterministic");
          for (int color : new int[] {w.top(), w.bottom()}) {
            int alpha = color >>> 24;
            check(alpha <= FeverLights.MAX_ALPHA, "bounded transparency");
            if (strength <= 0 || t == 0) check(alpha == 0, "off / fade start");
          }
          if (t > 0) {
            var p = FeverLights.sample(t - 16, band, strength);
            for (int shift : new int[] {0, 8, 16, 24}) {
              check(
                  Math.abs(((w.top() >>> shift) & 255) - ((p.top() >>> shift) & 255)) <= 3,
                  "smooth channels, no abrupt strobe");
            }
          }
        }
    var gson = new Gson();
    var old = gson.fromJson("{}", ModConfig.class);
    check(old.cropFeverLights && old.cropFeverLightStrength == 55, "old config defaults");
    var custom =
        gson.fromJson(
            "{\"cropFeverLights\":false,\"cropFeverLightStrength\":200}", ModConfig.class);
    custom.sanitize();
    check(
        !custom.cropFeverLights && custom.cropFeverLightStrength == 100, "sanitize high / toggle");
    custom.cropFeverLightStrength = -1;
    custom.sanitize();
    check(custom.cropFeverLightStrength == 0, "sanitize low");
    check(FeverLights.sample(-10, 0, 100).top() >>> 24 == 0, "negative elapsed");
    check(FeverLights.sample(1000, -1, 55).equals(FeverLights.sample(1000, 0, 55)), "band low");
    check(FeverLights.sample(1000, 99, 55).equals(FeverLights.sample(1000, 31, 55)), "band high");
    check(
        !FeverLights.sample(1000, 0, 55).equals(FeverLights.sample(2000, 0, 55)),
        "animated colours");
    System.out.println("FeverLightsCheck: " + checks + " checks — OK");
  }
}
