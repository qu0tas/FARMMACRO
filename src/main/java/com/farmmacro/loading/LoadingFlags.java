package com.farmmacro.loading;

import com.google.gson.*;

/** Only startup visual booleans are parsed; this never overwrites the user's full configuration. */
public record LoadingFlags(boolean enabled, boolean memes, boolean motion) {
  public static final LoadingFlags DEFAULT = new LoadingFlags(true, true, true);

  public static LoadingFlags parse(String json) {
    try {
      var root = JsonParser.parseString(json).getAsJsonObject();
      return new LoadingFlags(
          value(root, "kaelixLoading"), value(root, "loadingMemes"), value(root, "loadingMotion"));
    } catch (RuntimeException e) {
      return DEFAULT;
    }
  }

  private static boolean value(JsonObject o, String key) {
    var v = o.get(key);
    return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()
        ? v.getAsBoolean()
        : true;
  }
}
