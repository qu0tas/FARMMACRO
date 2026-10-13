package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Bounded PNG loading off-thread; texture ownership and replacement only on client thread. */
public final class CustomUiAssets {
  private record Texture(Identifier id, int w, int h) {}

  private static final Map<String, Texture> textures = new HashMap<>();
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "Kaelix-ui-assets");
            t.setDaemon(true);
            return t;
          });
  private static int generation;
  public static String status = "Не загружены";

  public static void reload() {
    int gen = ++generation;
    status = "Загрузка…";
    var mc = Minecraft.getInstance();
    IO.execute(
        () -> {
          Map<String, byte[]> images = new HashMap<>();
          String error = "";
          try {
            Path dir = ModConfig.modDir().resolve("ui");
            Files.createDirectories(dir.resolve("icons"));
            if (!Files.exists(dir.resolve("README.txt")))
              Files.writeString(
                  dir.resolve("README.txt"),
                  "background.png — фон меню; hud.png — фон HUD; icons/<id>.png — значки HUD. PNG"
                      + " до 2 МБ и 2048×2048. После правки: Визуал → Перезагрузить изображения."
                      + " Id: status editor hold mouselock view human return delays susp panic"
                      + " server schedule nav.\n");
            List<String> keys = new ArrayList<>(List.of("background", "hud"));
            for (var b : com.farmmacro.hud.FarmHud.BLOCKS) keys.add("icon_" + b.id());
            for (String key : keys) {
              Path f =
                  key.startsWith("icon_")
                      ? dir.resolve("icons").resolve(key.substring(5) + ".png")
                      : dir.resolve(key + ".png");
              if (!Files.exists(f)) continue;
              if (Files.size(f) > 2 * 1024 * 1024)
                throw new IllegalArgumentException(f.getFileName() + ": больше 2 МБ");
              byte[] bytes = Files.readAllBytes(f);
              if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504E470D0A1A0AL)
                throw new IllegalArgumentException("Не PNG: " + f.getFileName());
              var head = ByteBuffer.wrap(bytes);
              int w = head.getInt(16), h = head.getInt(20);
              if (w < 1 || h < 1 || w > 2048 || h > 2048)
                throw new IllegalArgumentException("Размер PNG должен быть 1…2048");
              images.put(key, bytes);
            }
          } catch (Exception e) {
            error = e.getMessage();
          }
          String err = error;
          mc.execute(
              () -> {
                if (gen != generation) return;
                for (var t : textures.values()) mc.getTextureManager().release(t.id);
                textures.clear();
                for (var e : images.entrySet()) {
                  NativeImage img = null;
                  DynamicTexture texture = null;
                  try {
                    img = NativeImage.read(e.getValue());
                    int w = img.getWidth(), h = img.getHeight();
                    var id =
                        Identifier.fromNamespaceAndPath("farmmacro", "custom_ui/" + e.getKey());
                    texture = new DynamicTexture(() -> "Kaelix UI " + e.getKey(), img);
                    img = null;
                    mc.getTextureManager().register(id, texture);
                    textures.put(e.getKey(), new Texture(id, w, h));
                    texture = null;
                  } catch (Exception ex) {
                    com.farmmacro.FarmMacroMod.LOGGER.warn(
                        "UI PNG {}: {}", e.getKey(), ex.toString());
                  } finally {
                    if (texture != null) texture.close();
                    if (img != null) img.close();
                  }
                }
                status = err.isEmpty() ? "Загружено: " + textures.size() : "Ошибка: " + err;
              });
        });
  }

  public static boolean draw(
      GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int opacity) {
    if (!ModConfig.INSTANCE.customUiImages) return false;
    Texture t = textures.get(name);
    if (t == null) return false;
    g.blit(
        RenderPipelines.GUI_TEXTURED,
        t.id,
        x,
        y,
        0f,
        0f,
        w,
        h,
        t.w,
        t.h,
        t.w,
        t.h,
        UiStyle.opacity(0xFFFFFFFF, opacity));
    return true;
  }
}
