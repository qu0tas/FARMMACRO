package com.farmmacro.loading;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Bootstrap assets are read from our JAR: font/resource reload is not yet available at first draw.
 */
public final class LoadingTextures {
  public static final Identifier LOGO = id("logo/k_atlas.png"),
      WORD = id("loading/word.png"),
      LABELS = id("loading/labels.png"),
      DIGITS = id("loading/digits.png"),
      FOXY = id("loading/foxy.png"),
      DANCERS = id("crop_fever/being.png");
  private static volatile boolean ready, failed;

  private static Identifier id(String file) {
    return Identifier.fromNamespaceAndPath("farmmacro", "textures/gui/" + file);
  }

  public static boolean ready() {
    return ready;
  }

  public static void register(TextureManager manager) {
    LoadingOptions.readEarly();
    ready = false;
    failed = false;
    for (var id : new Identifier[] {LOGO, WORD, LABELS, DIGITS, FOXY, DANCERS})
      manager.registerAndLoad(id, new BundledTexture(id));
    ready = !failed;
  }

  private static final class BundledTexture extends ReloadableTexture {
    private final Identifier id;

    private BundledTexture(Identifier id) {
      super(id);
      this.id = id;
    }

    @Override
    public TextureContents loadContents(ResourceManager ignored) throws IOException {
      String file = "/assets/" + id.getNamespace() + "/" + id.getPath();
      try (var in = LoadingTextures.class.getResourceAsStream(file)) {
        if (in == null) {
          failed = true;
          ready = false;
          throw new IOException("Missing Kaelix loading asset: " + file);
        }
        return new TextureContents(
            NativeImage.read(in), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0));
      }
    }
  }
}
