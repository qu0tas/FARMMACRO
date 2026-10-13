package com.farmmacro.fun;

import com.farmmacro.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;

/** Cosmetic overlay. Audio belongs to Minecraft sound engine and stops on disconnect/disable. */
public final class CropFever {
  private static final CropFeverCore CORE = new CropFeverCore();
  private static FeverLoop loop;
  private static SoundInstance endSound;
  private static long endStart = -1;
  private static final Animation BEING =
      new Animation(
          "being",
          176,
          176,
          10,
          1760,
          1936,
          new int[] {
            60, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70,
            70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70,
            60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60,
            70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70,
            70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70
          });
  private static final Animation END =
      new Animation(
          "end",
          386,
          480,
          4,
          1544,
          2400,
          new int[] {70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 70});
  private static final SoundEvent
      MUSIC =
          SoundEvent.createVariableRangeEvent(
              Identifier.fromNamespaceAndPath("farmmacro", "crop_fever.being")),
      END_SOUND =
          SoundEvent.createVariableRangeEvent(
              Identifier.fromNamespaceAndPath("farmmacro", "crop_fever.end"));

  public static void register() {
    HudElementRegistry.addLast(
        Identifier.fromNamespaceAndPath("farmmacro", "crop_fever"),
        com.farmmacro.util.Guard.hud("crop_fever", (g, d) -> draw(g)));
  }

  public static boolean onText(String text, boolean title) {
    return CORE.text(text, title, System.nanoTime() / 1000000);
  }

  public static void reset(Minecraft mc) {
    CORE.reset();
    endStart = -1;
    stopMusic(mc);
    if (endSound != null) {
      mc.getSoundManager().stop(endSound);
      endSound = null;
    }
  }

  public static void tick(Minecraft mc) {
    var c = ModConfig.INSTANCE;
    long now = System.nanoTime() / 1000000;
    if (mc.player == null || mc.level == null || !c.cropFeverEnabled) {
      CORE.reset();
      endStart = -1;
      stopMusic(mc);
      if (endSound != null) {
        mc.getSoundManager().stop(endSound);
        endSound = null;
      }
      return;
    }
    CORE.tick(now);
    if (CORE.active()) {
      endStart = -1;
      if (endSound != null) {
        mc.getSoundManager().stop(endSound);
        endSound = null;
      }
      if (loop == null && c.cropFeverVolume > 0) {
        loop = new FeverLoop();
        mc.getSoundManager().play(loop);
      }
    } else {
      stopMusic(mc);
      if (CORE.startEnd(now)) {
        endStart = now;
        if (c.cropFeverVolume > 0) {
          endSound = SimpleSoundInstance.forUI(END_SOUND, 1, c.cropFeverVolume / 100f);
          mc.getSoundManager().play(endSound);
        }
      }
    }
  }

  private static void stopMusic(Minecraft mc) {
    if (loop != null) {
      mc.getSoundManager().stop(loop);
      loop = null;
    }
  }

  private static void draw(GuiGraphicsExtractor g) {
    var mc = Minecraft.getInstance();
    var c = ModConfig.INSTANCE;
    if (!c.cropFeverEnabled || mc.level == null) return;
    long now = System.nanoTime() / 1000000;
    int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
    if (CORE.active()) {
      int w =
          Math.max(
              1,
              Math.min(
                  (int) Math.clamp(sw * c.cropFeverScale / 100.0, 40, 240),
                  (int) (Math.min(sw, sh) * .68)));
      int h = w * BEING.h / BEING.w;
      long elapsed = now - CORE.began;
      for (int i = 0; i < c.cropFeverDancers; i++) {
        var p = FeverMotion.pose(CORE.began, elapsed, i, sw, sh, w, h);
        var pose = g.pose();
        pose.pushMatrix();
        try {
          pose.translate((float) p.x(), (float) p.y());
          pose.rotate(p.radians());
          BEING.draw(g, elapsed + i * 137L, -w / 2, -h / 2, w, h, true);
        } finally {
          pose.popMatrix();
        }
      }
    } else if (endStart >= 0 && now - endStart < Math.max(END.duration(), 3100)) {
      int h = Math.min(sh - 8, 240), w = h * END.w / END.h;
      END.draw(g, now - endStart, (sw - w) / 2, (sh - h) / 2, w, h, false);
    }
  }

  private static final class FeverLoop extends AbstractTickableSoundInstance {
    FeverLoop() {
      super(MUSIC, SoundSource.MASTER, RandomSource.create());
      looping = true;
      delay = 0;
      relative = true;
      attenuation = Attenuation.NONE;
      volume = ModConfig.INSTANCE.cropFeverVolume / 100f;
    }

    public void tick() {
      volume = ModConfig.INSTANCE.cropFeverVolume / 100f;
      if (!CORE.active()
          || !ModConfig.INSTANCE.cropFeverEnabled
          || Minecraft.getInstance().level == null) stop();
    }
  }

  private static final class Animation {
    final Identifier id;
    final int w, h, cols, aw, ah;
    final int[] durations;

    Animation(String name, int w, int h, int cols, int aw, int ah, int[] ms) {
      id = Identifier.fromNamespaceAndPath("farmmacro", "textures/gui/crop_fever/" + name + ".png");
      this.w = w;
      this.h = h;
      this.cols = cols;
      this.aw = aw;
      this.ah = ah;
      durations = ms;
    }

    int duration() {
      int n = 0;
      for (int d : durations) n += d;
      return n;
    }

    void draw(GuiGraphicsExtractor g, long t, int x, int y, int dw, int dh, boolean repeat) {
      t = repeat ? Math.floorMod(t, duration()) : Math.min(t, duration() - 1);
      int f = 0;
      while (f < durations.length - 1 && t >= durations[f]) t -= durations[f++];
      g.blit(
          RenderPipelines.GUI_TEXTURED,
          id,
          x,
          y,
          (float) ((f % cols) * w),
          (float) ((f / cols) * h),
          dw,
          dh,
          w,
          h,
          aw,
          ah);
    }
  }
}
