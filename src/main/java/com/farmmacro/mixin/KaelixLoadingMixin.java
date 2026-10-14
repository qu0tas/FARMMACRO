package com.farmmacro.mixin;

import com.farmmacro.loading.*;
import com.farmmacro.util.Guard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only the visual extraction is replaced. Vanilla tick/onFinish/checkExceptions remain untouched.
 */
@Mixin(LoadingOverlay.class)
public abstract class KaelixLoadingMixin {
  @Shadow @Final private Minecraft minecraft;
  @Shadow @Final private ReloadInstance reload;
  @Shadow @Final private boolean fadeIn;
  @Shadow private float currentProgress;
  @Shadow private long fadeInStart, fadeOutStart;
  @Unique private long kaelix$began = -1;

  @Inject(method = "registerTextures", at = @At("TAIL"))
  private static void kaelix$assets(TextureManager textures, CallbackInfo ci) {
    Guard.runOrDisable("loading/Kaelix", () -> LoadingTextures.register(textures));
  }

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void kaelix$draw(
      GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    if (!LoadingOptions.current().enabled()
        || !LoadingTextures.ready()
        || Guard.isDisabled("loading/Kaelix")) return;
    long now = Util.getMillis();
    if (kaelix$began < 0) kaelix$began = now;
    if (fadeIn && fadeInStart < 0) fadeInStart = now;
    float alpha = LoadingLook.opacity(now, fadeInStart, fadeOutStart, fadeIn);
    currentProgress = LoadingLook.progress(currentProgress, reload.getActualProgress());
    boolean ok =
        Guard.runOrDisable(
            "loading/Kaelix",
            () -> {
              if (alpha < 1) {
                if (minecraft.screen != null)
                  minecraft.screen.extractRenderStateWithTooltipAndSubtitles(g, 0, 0, partialTick);
                else minecraft.gui.extractDeferredSubtitles();
              }
              g.nextStratum();
              KaelixLoading.draw(g, currentProgress, fadeOutStart >= 0, alpha, now - kaelix$began);
            });
    if (ok) {
      if (LoadingLook.removed(now, fadeOutStart)) minecraft.setOverlay(null);
      ci.cancel();
    } // fallback to vanilla after failure, never swallow a reload error
  }
}
