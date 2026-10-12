package com.farmmacro.route;

/**
 * v1.11 «Авто-маршрут»: снимок области фермы (без предела по размеру — сколько хватит памяти: 3 байта на блок) — верх коллизии и метка каждого блока.
 * Без зависимостей от игры: в игре заполняет {@code FarmScanner} частями по тикам, в проверках — сам тест.
 * Вне области — воздух.
 */
public final class FarmGrid implements Terrain.Cells {
    /**
     * CROP — низкие культуры на грядке (пшеница, морковь, картофель, свёкла, незерский нарост, грибы): по ним не ходят,
     * ходят в канале ниже (обычно по воде). CROP_TALL — культуры «на уровне» (арбуз, тыква и их стебли, тростник,
     * кактус, какао, цветы): идут на одном уровне с ними.
     */
    public static final byte NONE = 0, CROP = 1, WATER = 2, CLIMB = 3, DANGER = 4, FARMLAND = 5, SAND = 6, SOUL = 7, CROP_TALL = 8;

    public static boolean isCrop(byte t) { return t == CROP || t == CROP_TALL; }

    public final int x0, y0, z0, w, h, d;
    /** Верх коллизии в тысячных блока (−1000 — нет): short вместо float — 3 байта на блок (1000×1000×13 ≈ 39 МБ). */
    private final short[] top;
    private final byte[] tag;
    /** Колонок не было в загруженных чанках (заполнены воздухом). */
    public int unloaded;

    public FarmGrid(int x0, int y0, int z0, int w, int h, int d) {
        if (w < 1 || d < 1 || h < 1 || (long) w * h * d > Integer.MAX_VALUE - 8)
            throw new IllegalArgumentException("область " + w + "×" + d + "×" + h);
        this.x0 = x0; this.y0 = y0; this.z0 = z0; this.w = w; this.h = h; this.d = d;
        top = new short[w * h * d];
        tag = new byte[w * h * d];
        java.util.Arrays.fill(top, (short) -1000);
    }

    public boolean inXZ(int x, int z) { return x >= x0 && x < x0 + w && z >= z0 && z < z0 + d; }
    private boolean in(int x, int y, int z) { return inXZ(x, z) && y >= y0 && y < y0 + h; }
    private int idx(int x, int y, int z) { return ((x - x0) * d + (z - z0)) * h + (y - y0); }

    /** Задать блок: верх коллизии (−1 — нет), метка. */
    public FarmGrid set(int x, int y, int z, double t, byte g) {
        if (!in(x, y, z)) return this;
        int i = idx(x, y, z);
        top[i] = t < 0 ? (short) -1000 : (short) Math.round(Math.min(1.5, t) * 1000);
        tag[i] = g;
        return this;
    }

    /** Заполнить прямоугольник одного слоя. */
    public FarmGrid fill(int xa, int xb, int y, int za, int zb, double t, byte g) {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
            for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) set(x, y, z, t, g);
        return this;
    }

    @Override public double top(int x, int y, int z) { if (!in(x, y, z)) return -1;
        short v = top[idx(x, y, z)];
        return v < 0 ? -1 : v / 1000.0;
    }
    public byte tag(int x, int y, int z) { return in(x, y, z) ? tag[idx(x, y, z)] : NONE; }
    public long cells() { return (long) w * h * d; }
}
