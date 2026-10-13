package com.farmmacro.fun;

import com.farmmacro.panic.SoundDecoder;
import com.google.gson.*;
import java.nio.ByteBuffer;

public final class CropFeverMediaCheck {
  public static void main() {
    try {
      for (String key : new String[] {"being", "end"}) {
        String base = "/assets/farmmacro/";
        byte[] png;
        try (var in =
            CropFeverMediaCheck.class.getResourceAsStream(
                base + "textures/gui/crop_fever/" + key + ".png")) {
          if (in == null) throw new IllegalStateException("missing PNG");
          png = in.readAllBytes();
        }
        if (key.equals("being")) {
          var im = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
          boolean transparent = false, opaque = false;
          for (int y = 0; y < 176; y++)
            for (int x = 0; x < 176; x++) {
              int pixel = im.getRGB(x, y);
              int alpha = pixel >>> 24;
              transparent |= alpha == 0;
              opaque |= alpha == 255;
            }
          if (!transparent || !opaque) throw new IllegalStateException("video alpha missing");
          if ((im.getRGB(0, 0) >>> 24) != 0)
            throw new IllegalStateException("green background not transparent");
        }
        JsonObject meta;
        try (var in =
            CropFeverMediaCheck.class.getResourceAsStream(
                base + "textures/gui/crop_fever/" + key + ".json")) {
          meta =
              JsonParser.parseString(
                      new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                  .getAsJsonObject();
        }
        int w = meta.get("width").getAsInt(),
            h = meta.get("height").getAsInt(),
            cols = meta.get("columns").getAsInt(),
            frames = meta.getAsJsonArray("frameDurationsMs").size();
        var b = ByteBuffer.wrap(png);
        if (b.getInt(16) != w * cols || b.getInt(20) != h * ((frames + cols - 1) / cols))
          throw new IllegalStateException("atlas dimensions");
        for (var ms : meta.getAsJsonArray("frameDurationsMs"))
          if (ms.getAsInt() < 20) throw new IllegalStateException("invalid frame timing");
        byte[] raw;
        try (var in =
            CropFeverMediaCheck.class.getResourceAsStream(
                base + "sounds/crop_fever/" + key + ".ogg")) {
          raw = in.readAllBytes();
        }
        double expected = key.equals("being") ? 13.61 : 3.096;
        for (int i = 0; i < 10; i++) {
          var clip = SoundDecoder.decodeOgg(raw);
          if (Math.abs(clip.seconds() - expected) > .1)
            throw new IllegalStateException("audio duration " + clip.seconds());
        }
        System.out.println(
            "CropFeverMediaCheck: "
                + key
                + " "
                + frames
                + " frames, atlas "
                + b.getInt(16)
                + "×"
                + b.getInt(20)
                + ", 10 audio decodes — OK");
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
