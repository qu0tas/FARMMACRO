package com.farmmacro.loading;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Branded loading view; no Minecraft Font, sound, world, keys, network or reload callbacks. */
public final class KaelixLoading {
  private static final int[] DURATIONS =
      new int[] {
        60, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70,
        60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70,
        70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60,
        70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70,
        60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70
      };

  public static void draw(
      GuiGraphicsExtractor g, float progress, boolean done, float alpha, long elapsed) {
    var options = LoadingOptions.current();
    int sw = g.guiWidth(), sh = g.guiHeight();
    if (alpha <= 0) return;
    g.fill(0, 0, sw, sh, LoadingLook.tint(LoadingLook.BACKGROUND, alpha));
    // Quiet horizontal blue bands, sparse grid and route-like corner markers.
    for (int y = 0; y < sh; y += 12) {
      float light =
          (float) (.045 * Math.exp(-Math.pow((y - sh * .3) / (Math.max(1, sh * .35)), 2)));
      g.fill(0, y, sw, Math.min(sh, y + 12), LoadingLook.tint(LoadingLook.ACCENT, alpha * light));
    }
    var l = LoadingLook.layout(sw, sh);
    var pose = g.pose();
    pose.pushMatrix();
    try {
      pose.translate((float) l.x(), (float) l.y());
      pose.scale((float) l.scale(), (float) l.scale());
      for (int x = 20; x < 960; x += 80)
        g.fill(x, 20, x + 1, 520, LoadingLook.tint(LoadingLook.LINE, alpha * .3f));
      for (int y = 20; y < 540; y += 80)
        g.fill(20, y, 940, y + 1, LoadingLook.tint(LoadingLook.LINE, alpha * .3f));
      int frame = options.motion() ? (int) (Math.floorMod(elapsed, 6000) * 96 / 6000) : 0;
      sprite(
          g,
          LoadingTextures.LOGO,
          424,
          50,
          112,
          112,
          (frame % 12) * 64,
          (frame / 12) * 64,
          64,
          64,
          768,
          512,
          alpha);
      sprite(g, LoadingTextures.WORD, 302, 176, 356, 89, 0, 0, 512, 128, 512, 128, alpha);
      int pc = LoadingLook.percent(progress, done);
      label(g, LoadingLook.stage(pc), 325, 303, 310, 20, alpha);
      g.fill(250, 342, 710, 350, LoadingLook.tint(0xFF1A2542, alpha));
      int end = 250 + (int) Math.round(460 * (done ? 1 : Math.clamp(progress, 0, 1)));
      g.fill(250, 342, end, 350, LoadingLook.tint(LoadingLook.ACCENT, alpha));
      if (end > 250)
        g.fill(Math.max(250, end - 16), 342, end, 350, LoadingLook.tint(0xFF9CC7FF, alpha));
      String digits = pc + "%";
      int left = 480 - digits.length() * 8;
      for (int i = 0; i < digits.length(); i++) {
        char ch = digits.charAt(i);
        int n = ch == '%' ? 10 : ch - '0';
        sprite(
            g,
            LoadingTextures.DIGITS,
            left + i * 16,
            370,
            16,
            24,
            n * 16,
            0,
            16,
            24,
            176,
            24,
            alpha);
      }
      label(g, 4, 300, 486, 360, 23, alpha);
      if (options.memes()) {
        int f = options.motion() ? (int) (Math.floorMod(elapsed, 1600) / 200) : 3;
        sprite(
            g,
            LoadingTextures.FOXY,
            36,
            296,
            174,
            109,
            (f % 4) * 256,
            (f / 4) * 160,
            256,
            160,
            1024,
            320,
            alpha * .9f);
        label(g, 5, 44, 438, 156, 18, alpha);
        dancer(g, elapsed, 0, 775, 320, 88, alpha);
        dancer(g, elapsed, 1, 875, 380, 80, alpha);
        label(g, 6, 728, 438, 196, 22, alpha);
      }
    } finally {
      pose.popMatrix();
    }
  }

  private static void label(GuiGraphicsExtractor g, int row, int x, int y, int w, int h, float a) {
    int sx = row >= 5 ? 112 : 0, sw = row >= 5 ? 288 : 512;
    sprite(g, LoadingTextures.LABELS, x, y, w, h, sx, row * 32, sw, 32, 512, 224, a);
  }

  private static void dancer(
      GuiGraphicsExtractor g, long elapsed, int index, int x, int y, int size, float a) {
    boolean motion = LoadingOptions.current().motion();
    long t = motion ? Math.floorMod(elapsed + index * 1000L, 7130) : 0;
    int f = 0;
    while (f < DURATIONS.length - 1 && t >= DURATIONS[f]) t -= DURATIONS[f++];
    float bob = motion ? (float) (Math.sin(elapsed / 650.0 + index) * 9) : 0;
    float angle = motion && index == 1 ? (float) (Math.sin(elapsed / 1400.0) * .45) : 0;
    var p = g.pose();
    p.pushMatrix();
    try {
      p.translate(x, y + bob);
      p.rotate(angle);
      sprite(
          g,
          LoadingTextures.DANCERS,
          -size / 2,
          -size / 2,
          size,
          size,
          (f % 10) * 176,
          (f / 10) * 176,
          176,
          176,
          1760,
          1936,
          a * .9f);
    } finally {
      p.popMatrix();
    }
  }

  private static void sprite(
      GuiGraphicsExtractor g,
      Identifier id,
      int x,
      int y,
      int w,
      int h,
      int u,
      int v,
      int srcW,
      int srcH,
      int texW,
      int texH,
      float a) {
    g.blit(
        RenderPipelines.GUI_TEXTURED,
        id,
        x,
        y,
        u,
        v,
        w,
        h,
        srcW,
        srcH,
        texW,
        texH,
        LoadingLook.tint(0xFFFFFFFF, a));
  }
}
