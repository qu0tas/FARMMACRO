package com.farmmacro.camera;

import com.farmmacro.macro.MacroStorage;
import com.farmmacro.route.PointRoute;
import com.google.gson.Gson;

/**
 * Проверка привязки камеры без игры: какая смена действует на элементе, когда её ставить (как Tracker),
 * чтение старых и новых файлов маршрутов (v1–v3) и макросов (v2–v4, перенос camera → settings), ввод углов. Вызывается из routeStress.
 */
public final class CameraBindingCheck {

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("привязка камеры: " + what);
    }

    private static CameraBinding.Change ch(int from, int id, float yaw) {
        CameraBinding.Change c = new CameraBinding.Change();
        c.from = from; c.presetId = id; c.name = "p" + id; c.yaw = yaw; c.pitch = 30;
        return c;
    }

    public static void main() {
        CameraBinding b = new CameraBinding();
        check(b.at(0) == null && !b.active(), "none не трогает камеру");

        b.mode = CameraBinding.WHOLE;
        b.changes.add(ch(5, 1, 90));
        check(b.at(0) == b.changes.get(0) && b.at(1000) == b.changes.get(0), "whole действует везде (from игнорируется)");

        b.mode = CameraBinding.POINTS;
        b.changes.clear();
        b.changes.add(ch(4, 2, 0));       // не по порядку — как во время правки в меню
        b.changes.add(ch(0, 1, 90));
        b.changes.add(ch(9, 3, -90));
        check(b.at(0).presetId == 1 && b.at(3).presetId == 1 && b.at(4).presetId == 2
                && b.at(8).presetId == 2 && b.at(9).presetId == 3 && b.at(99).presetId == 3, "points по from без сортировки");

        // как Tracker в проходе: сколько раз и где ставится камера
        StringBuilder applied = new StringBuilder();
        CameraBinding.Change last = null;
        boolean any = false;
        for (int i = 0; i < 12; i++) {
            CameraBinding.Change c = b.at(i);
            if (c != null && !(any && c == last)) { applied.append(i).append(':').append(c.presetId).append(' '); last = c; any = true; }
        }
        check(applied.toString().equals("0:1 4:2 9:3 "), "установки в проходе: " + applied);

        CameraBinding late = new CameraBinding();
        late.mode = CameraBinding.POINTS;
        late.changes.add(ch(3, 7, 45));
        check(late.at(0) == null && late.at(2) == null && late.at(3) != null, "до первой смены камера как есть");

        // sanitize: мусор, повторы from, NaN, неизвестный режим
        CameraBinding dirty = new CameraBinding();
        dirty.mode = "spin";
        dirty.changes.add(null);
        dirty.changes.add(ch(-5, 1, Float.NaN));
        dirty.changes.add(ch(2, 2, 400));
        dirty.changes.add(ch(2, 3, 10));
        CameraBinding.sanitize(dirty);
        check(CameraBinding.NONE.equals(dirty.mode), "неизвестный режим → none");
        check(dirty.changes.size() == 2 && dirty.changes.get(0).from == 0 && dirty.changes.get(0).yaw == 0
                && dirty.changes.get(1).presetId == 3 && dirty.changes.get(1).yaw == 10, "sanitize смен: " + dirty.changes.size());
        CameraBinding empty = new CameraBinding();
        empty.mode = CameraBinding.POINTS;
        check(CameraBinding.NONE.equals(CameraBinding.sanitize(empty).mode), "points без смен → none");
        check(CameraBinding.sanitize(null) != null, "null → пустая привязка");

        Gson gson = new Gson();
        // маршрут v1 (до 1.6.0): поле pitch у маршрута и точки, без camera
        String v1 = "{\"version\":1,\"name\":\"old\",\"dimension\":\"minecraft:overworld\",\"pitch\":35.5,"
                + "\"points\":[{\"x\":0.5,\"y\":64,\"z\":0.5,\"action\":\"attack\",\"pitch\":20},{\"x\":9.5,\"y\":64,\"z\":0.5}]}";
        PointRoute r1 = gson.fromJson(v1, PointRoute.class);
        r1.sanitize();
        check(r1.points.size() == 2 && r1.camera == null && !r1.settings.camera.active() && r1.points.get(0).attack(), "маршрут v1");
        String out = gson.toJson(r1);
        check(!out.contains("\"pitch\":35.5") && out.contains("\"settings\"") && !out.contains("\"camera\":{\"mode\":\"none\",\"changes\":[]},\"camera\""),
                "маршрут v1 пересохраняется без pitch, с settings");
        // маршрут v2 с привязкой
        PointRoute r2 = gson.fromJson("{\"version\":2,\"points\":[{\"x\":0,\"y\":0,\"z\":0}],\"camera\":{\"mode\":\"points\","
                + "\"changes\":[{\"from\":0,\"presetId\":4,\"name\":\"Морковь\",\"yaw\":90.0,\"pitch\":12.345}]}}", PointRoute.class);
        r2.sanitize();
        check(r2.camera == null && r2.settings.camera.active() && r2.settings.camera.at(0).pitch == 12.345f, "маршрут v2 → settings.camera");
        PointRoute copy = r2.copy();
        check(copy.settings != r2.settings && copy.settings.camera != r2.settings.camera
                && copy.settings.camera.at(0).presetId == 4, "copy() копирует настройки");
        String out2 = gson.toJson(r2);
        check(out2.startsWith("{") && !out2.contains(",\"camera\":") && out2.contains("\"settings\":{\"camera\":{\"mode\":\"points\""),
                "маршрут v2 пересохраняется в settings: " + out2);
        // маршрут v3: settings важнее старого поля
        PointRoute r3 = gson.fromJson("{\"version\":3,\"points\":[{\"x\":0,\"y\":0,\"z\":0}],\"settings\":{\"camera\":{\"mode\":\"whole\","
                + "\"changes\":[{\"from\":0,\"presetId\":8,\"yaw\":10,\"pitch\":5}]}},\"camera\":{\"mode\":\"whole\",\"changes\":"
                + "[{\"from\":0,\"presetId\":9,\"yaw\":20,\"pitch\":5}]}}", PointRoute.class);
        r3.sanitize();
        check(r3.settings.camera.at(0).presetId == 8, "маршрут v3: settings, а не старое camera");
        PointRoute r3b = gson.fromJson("{\"version\":3,\"points\":[{\"x\":0,\"y\":0,\"z\":0}]}", PointRoute.class);
        r3b.sanitize();
        check(r3b.settings != null && !r3b.settings.camera.active(), "маршрут без settings → по умолчанию");

        // макрос v2 (без camera) и v3
        MacroStorage.SavedMacro m2 = gson.fromJson("{\"name\":\"m\",\"version\":2,\"frameCount\":1,\"frames\":[{\"x\":1}]}",
                MacroStorage.SavedMacro.class);
        m2.sanitize();
        check(m2.frames.size() == 1 && m2.settings != null && !m2.settings.camera.active(), "макрос v2");
        MacroStorage.SavedMacro m3 = gson.fromJson("{\"name\":\"m\",\"version\":3,\"camera\":{\"mode\":\"whole\",\"changes\":"
                + "[{\"from\":0,\"presetId\":1,\"yaw\":-45.5,\"pitch\":60}]},\"frames\":[{\"x\":1}]}", MacroStorage.SavedMacro.class);
        m3.sanitize();
        check(m3.camera == null && m3.settings.camera.active() && m3.settings.camera.at(500).yaw == -45.5f, "макрос v3 → settings.camera");
        String mOut = gson.toJson(m3);
        check(!mOut.contains("\"camera\":{\"mode\":\"whole\",\"changes\":[{\"from\":0,\"presetId\":1,\"name\":\"\",\"yaw\":-45.5,\"pitch\":60.0}]},\"frames")
                && mOut.contains("\"settings\":{\"camera\""), "макрос v3 пересохраняется в settings: " + mOut);
        MacroStorage.SavedMacro m4 = gson.fromJson("{\"name\":\"m\",\"version\":4,\"settings\":{\"camera\":{\"mode\":\"points\",\"changes\":"
                + "[{\"from\":3,\"presetId\":2,\"yaw\":1,\"pitch\":2}]}},\"frames\":[{\"x\":1}]}", MacroStorage.SavedMacro.class);
        m4.sanitize();
        check(m4.settings.camera.at(2) == null && m4.settings.camera.at(3).presetId == 2, "макрос v4");

        // ввод чисел панели «Камера»
        check(CameraPresets.deg(90f).equals("90.0") && CameraPresets.deg(12.345f).equals("12.345")
                && CameraPresets.deg(-0f).equals("0.0") && CameraPresets.deg(-179.99f).equals("-179.99"), "вывод углов");
        Float a = CameraPresets.parseDeg(" 12,345° "), bad = CameraPresets.parseDeg("abc"), inf = CameraPresets.parseDeg("1e40");
        check(a != null && a == 12.345f && bad == null && inf == null && CameraPresets.parseDeg("") == null, "разбор углов");

        System.out.println("\nПривязка камеры: none/whole/points, порядок смен, sanitize, файлы маршрута v1/v2/v3 и макроса v2/v3/v4 (перенос camera → settings), "
                + "ввод углов — OK (установки в проходе: " + applied.toString().trim() + ")");
    }
}
