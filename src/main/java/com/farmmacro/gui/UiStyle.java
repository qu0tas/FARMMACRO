package com.farmmacro.gui;

/** Pure validation shared by settings and stress tests. */
public final class UiStyle {
  public static int color(String text, int fallback) {
    if (text == null || !text.matches("#[0-9a-fA-F]{6}")) return fallback;
    return (fallback & 0xFF000000) | Integer.parseInt(text.substring(1), 16);
  }

  public static boolean validColor(String text) {
    return text != null && (text.isEmpty() || text.matches("#[0-9a-fA-F]{6}"));
  }

  public static boolean safeName(String name) {
    return name != null && name.matches("[a-z0-9_]{1,40}");
  }

  public static int opacity(int argb, int percent) {
    return ((int) Math.round(Math.clamp(percent, 0, 100) * 2.55) << 24) | (argb & 0xFFFFFF);
  }
}
