package com.farmmacro.visual;

/** v1.12, без зависимостей от игры: попадает ли коробка в радиус «Low render» вокруг игрока. */
public final class LowRenderMath {
    private LowRenderMath() {}

    public static final int OUTSIDE = 0, PARTIAL = 1, INSIDE = 2;
    /** Запас для отсечения коробок: центр блока на радиусе r даёт коробку до r + 0.5·√2 от центра. */
    public static final double MARGIN = 1.0;

    /**
     * v1.12.2, поблочно: блок (bx, by, bz) виден, если центр его клетки по горизонтали не дальше r от центра клетки
     * игрока (cx, cz) и по высоте не дальше r блоков (круг «пару блоков рядом», как в Low render у других модов).
     */
    public static boolean blockInside(int bx, int by, int bz, int cx, int cy, int cz, int r) {
        long dx = bx - cx, dz = bz - cz, dy = by - cy;
        return dx * dx + dz * dz <= (long) r * r + r && Math.abs(dy) <= r;   // + r — круг без «зубцов» по осям
    }

    /**
     * Цилиндр: по горизонтали — радиус r от игрока, по высоте — ± r. Коробка (minX…maxX и т. д.).
     * @return OUTSIDE — целиком снаружи, INSIDE — целиком внутри, PARTIAL — пересекает границу
     */
    public static int classify(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                               double px, double py, double pz, double r) {
        double nx = Math.max(Math.max(minX - px, 0), px - maxX);
        double nz = Math.max(Math.max(minZ - pz, 0), pz - maxZ);
        double ny = Math.max(Math.max(minY - py, 0), py - maxY);
        if (nx * nx + nz * nz > r * r || ny > r) return OUTSIDE;
        double fx = Math.max(Math.abs(minX - px), Math.abs(maxX - px));
        double fz = Math.max(Math.abs(minZ - pz), Math.abs(maxZ - pz));
        double fy = Math.max(Math.abs(minY - py), Math.abs(maxY - py));
        return fx * fx + fz * fz <= r * r && fy <= r ? INSIDE : PARTIAL;
    }
}
