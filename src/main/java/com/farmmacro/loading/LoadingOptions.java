package com.farmmacro.loading;

import com.farmmacro.config.ModConfig;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/**
 * One bounded read during texture bootstrap, not on render frames. After client init, GUI changes
 * are live.
 */
public final class LoadingOptions {
  private static LoadingFlags early = LoadingFlags.DEFAULT;
  private static boolean clientReady;

  public static void clientReady() {
    clientReady = true;
  }

  public static LoadingFlags current() {
    if (!clientReady) return early;
    var c = ModConfig.INSTANCE;
    return new LoadingFlags(c.kaelixLoading, c.loadingMemes, c.loadingMotion);
  }

  public static void readEarly() {
    if (clientReady) return;
    try {
      Path p = FabricLoader.getInstance().getConfigDir().resolve("farmmacro.json");
      if (Files.isRegularFile(p) && Files.size(p) <= 1024 * 1024)
        early = LoadingFlags.parse(Files.readString(p));
    } catch (Exception ignored) {
      early = LoadingFlags.DEFAULT;
    }
  }
}
