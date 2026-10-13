package com.farmmacro.gui;

/**
 * Shared edge/header dragging for route and point dialogs. Offsets survive resize within one
 * opening.
 */
final class DragWindow {
  private int ox, oy;
  private boolean drag;
  private double sx, sy;
  private int oldX, oldY;

  int x(int width, int w) {
    return Math.clamp((width - w) / 2 + ox, 0, Math.max(0, width - w));
  }

  int y(int height, int h) {
    return Math.clamp((height - h) / 2 + oy, 0, Math.max(0, height - h));
  }

  boolean click(
      double mx, double my, int button, boolean doubleClick, int width, int height, int w, int h) {
    if (button != 0) return false;
    int x = x(width, w), y = y(height, h);
    boolean inside = Ui.inside(mx, my, x, y, w, h);
    boolean edge = inside && !Ui.inside(mx, my, x + 4, y + 4, w - 8, h - 8);
    boolean header = Ui.inside(mx, my, x, y, w, 24);
    if (!edge && !header) return false;
    if (doubleClick) {
      ox = oy = 0;
      return true;
    }
    sx = mx;
    sy = my;
    oldX = x - (width - w) / 2;
    oldY = y - (height - h) / 2;
    drag = true;
    return true;
  }

  boolean move(double mx, double my) {
    if (!drag) return false;
    ox = oldX + (int) Math.round(mx - sx);
    oy = oldY + (int) Math.round(my - sy);
    return true;
  }

  boolean release() {
    boolean was = drag;
    drag = false;
    return was;
  }
}
