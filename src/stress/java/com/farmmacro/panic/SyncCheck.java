package com.farmmacro.panic;

import java.util.ArrayList;
import java.util.List;

/** v1.11: эпсилоны синхронизации, расчёт атрибутов (без спринта), луч по клеткам. */
public final class SyncCheck {
    private static int checks;

    private static void ok(boolean cond, String what) {
        checks++;
        if (!cond) throw new IllegalStateException("SyncCheck: " + what);
    }

    public static void main() {
        // эпсилоны: строго больше
        ok(!SyncMath.posOver(0.02, 0.03), "0.02 бл ≤ 0.03 — не паника");
        ok(!SyncMath.posOver(0.03, 0.03), "ровно эпсилон — не паника");
        ok(SyncMath.posOver(0.031, 0.03), "0.031 бл > 0.03 — паника");
        ok(!SyncMath.rotOver(0.049, 0.05) && SyncMath.rotOver(0.051, 0.05), "эпсилон взгляда");
        String r = SyncMath.syncReason("телепорта", 0.042, 0.03, 0.0, 0.0, 0.05);
        ok(r.contains("0.042") && r.contains("0.030") && !r.contains("взгляд"), "текст: только позиция с цифрами — " + r);
        r = SyncMath.syncReason("поворота", 0.0, 0.03, 0.2, 0.01, 0.05);
        ok(r.contains("yaw 0.200") && !r.contains("позицию"), "текст: только взгляд — " + r);

        // атрибуты: формула как в игре
        List<SyncMath.Mod> m = new ArrayList<>();
        m.add(new SyncMath.Mod("x:add", 1, 0));
        m.add(new SyncMath.Mod("x:base", 0.5, 1));
        m.add(new SyncMath.Mod("x:total", 1, 2));
        ok(Math.abs(SyncMath.value(1, m) - 6) < 1e-9, "1 + 1 → 2; +2·0.5 → 3; ×2 → 6");
        // спринт (+30 % total) и снег не считаются: сервер с модификатором спринта = клиент без него
        List<SyncMath.Mod> sprint = List.of(new SyncMath.Mod("minecraft:sprinting", 0.3, 2));
        ok(!SyncMath.changed(SyncMath.value(0.1, List.of()), SyncMath.value(0.1, sprint), 1.0), "спринт — не изменение");
        ok(!SyncMath.changed(0.1, SyncMath.value(0.1, List.of(new SyncMath.Mod("minecraft:powder_snow", -0.05, 0))), 1.0), "снег — не изменение");
        ok(SyncMath.changed(0.1, 0.15, 1.0), "скорость 0.1 → 0.15 (+50 %) — паника");
        ok(!SyncMath.changed(0.1, 0.1005, 1.0), "0.5 % < 1 % — не паника");
        ok(SyncMath.changed(0.08, 0.0808 + 1e-6, 1.0), "гравитация +1.0 % с хвостиком — паника");
        ok(SyncMath.changed(0.1, SyncMath.value(0.1, List.of(new SyncMath.Mod("plugin:slow", -0.5, 2))), 1.0), "чужой модификатор −50 % — паника");
        ok(Math.abs(SyncMath.relPct(0.6, 0.9) - 50) < 1e-9, "относительное изменение в %");

        // луч по клеткам (3D DDA)
        List<int[]> cells = new ArrayList<>();
        SyncMath.cells(0.5, 0.5, 0.5, 1, 0, 0, 4, (x, y, z, d) -> { cells.add(new int[]{x, y, z}); return true; });
        ok(cells.size() == 5 && cells.get(4)[0] == 4 && cells.get(0)[0] == 0, "луч +X на 4 бл: клетки 0..4, а не " + cells.size());
        cells.clear();
        SyncMath.cells(0.5, 64.2, 0.5, -1, 0, -1, 3, (x, y, z, d) -> { cells.add(new int[]{x, y, z}); return true; });
        int[] last = cells.get(cells.size() - 1);
        ok(last[0] <= -1 && last[2] <= -1 && cells.stream().allMatch(c -> c[1] == 64), "диагональ −X−Z на своём уровне");
        double[] dist = new double[1];
        SyncMath.cells(0.5, 1.6, 0.5, 0, 0, 1, 10, (x, y, z, d) -> { if (z == 3) { dist[0] = d; return false; } return true; });
        ok(Math.abs(dist[0] - 2.5) < 1e-9, "расстояние до входа в клетку z=3: 2.5, а не " + dist[0]);
        int[] n = new int[1];
        SyncMath.cells(0, 0, 0, 0, 0, 0, 4, (x, y, z, d) -> { n[0]++; return true; });
        ok(n[0] == 0, "нулевое направление — нет клеток");

        System.out.printf("%nSyncCheck (v1.11: эпсилоны, атрибуты, луч): %d проверок — OK%n", checks);
    }
}
