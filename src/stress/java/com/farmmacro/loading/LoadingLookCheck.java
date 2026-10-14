package com.farmmacro.loading;

import java.io.*;
import javax.imageio.ImageIO;

public final class LoadingLookCheck {
  private static int checks;

  private static void check(boolean ok, String text) {
    checks++;
    if (!ok) throw new IllegalStateException("LoadingLookCheck: " + text);
  }

  public static void main() {
    for (int w : new int[] {160, 240, 320, 480, 640, 854, 960, 1280, 1920, 2560})
      for (int h : new int[] {90, 180, 240, 360, 480, 540, 720, 1080}) {
        var l = LoadingLook.layout(w, h);
        check(l.scale() > 0, "positive scale");
        for (var rect :
            new LoadingLook.Rect[] {
              l.box(424, 50, 112, 112),
              l.box(302, 176, 356, 89),
              l.box(325, 303, 310, 20),
              l.box(250, 342, 460, 8),
              l.box(300, 486, 360, 23),
              l.box(36, 296, 174, 109),
              l.box(719, 264, 112, 112),
              l.box(818, 323, 114, 114)
            })
          check(
              rect.x() >= 0
                  && rect.y() >= 0
                  && rect.x() + rect.w() <= w + .001
                  && rect.y() + rect.h() <= h + .001,
              "art stays within viewport");
      }
    float p = 0;
    for (int i = 0; i < 1000; i++) {
      float next = LoadingLook.progress(p, i / 999f);
      check(next >= p && next <= 1, "monotonic actual reload progress");
      p = next;
    }
    check(LoadingLook.progress(.6f, .1f) >= .6, "does not jump backwards");
    check(LoadingLook.progress(Float.NaN, Float.NaN) == 0, "invalid values safe");
    check(
        LoadingLook.percent(1, false) == 99 && LoadingLook.percent(.7f, true) == 100,
        "100 only after completion");
    check(LoadingLook.opacity(0, 0, -1, false) == 1, "initial startup visible");
    check(
        LoadingLook.opacity(0, 0, -1, true) == 0
            && LoadingLook.opacity(250, 0, -1, true) == .5f
            && LoadingLook.opacity(500, 0, -1, true) == 1,
        "fade in");
    check(
        LoadingLook.opacity(1000, 0, 1000, false) == 1
            && LoadingLook.opacity(2000, 0, 1000, false) == 1
            && LoadingLook.opacity(2500, 0, 1000, false) == .5f
            && LoadingLook.opacity(3000, 0, 1000, false) == 0,
        "vanilla fade out times");
    check(
        !LoadingLook.removed(2999, 1000) && LoadingLook.removed(3000, 1000),
        "overlay removes after 2 s");
    check(LoadingFlags.parse("{}").equals(LoadingFlags.DEFAULT), "old config inherits defaults");
    var flags =
        LoadingFlags.parse(
            "{\"kaelixLoading\":false,\"loadingMemes\":false,\"loadingMotion\":false}");
    check(!flags.enabled() && !flags.memes() && !flags.motion(), "all startup switches respected");
    check(
        LoadingFlags.parse("broken").equals(LoadingFlags.DEFAULT)
            && LoadingFlags.parse("null").equals(LoadingFlags.DEFAULT),
        "malformed config fallback");
    check(LoadingFlags.parse("{\"loadingMotion\":\"bad\"}").motion(), "wrong type doesn't crash");
    for (String name : new String[] {"word", "labels", "digits", "foxy"}) {
      try (var in =
          LoadingLookCheck.class.getResourceAsStream(
              "/assets/farmmacro/textures/gui/loading/" + name + ".png")) {
        check(in != null, "bundled asset " + name);
        var im = ImageIO.read(in);
        check(im != null && im.getColorModel().hasAlpha(), "RGBA asset " + name);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    System.out.println(
        "LoadingLookCheck: "
            + checks
            + " checks — OK (layout, fades, real progress, early options, RGBA resources)");
  }
}
