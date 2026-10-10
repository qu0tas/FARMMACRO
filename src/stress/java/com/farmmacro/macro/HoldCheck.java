package com.farmmacro.macro;

import com.google.gson.Gson;

/** Проверка «Зажима мыши» без игры: когда держать, задержка, участки, sanitize, чтение файлов. Вызывается из routeStress. */
public final class HoldCheck {

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("зажим мыши: " + what);
    }

    public static void main() {
        HoldSettings h = new HoldSettings();
        check(!h.active() && !h.wants(true, 100, false, 0, true), "none ничего не держит");

        h.button = HoldSettings.ATTACK;
        h.mode = HoldSettings.WHOLE;
        h.delayTicks = 40;
        check(!h.wants(true, 40, false, 0, false) && h.wants(true, 41, false, 0, false), "задержка 40 т: с 41-го тика");
        check(!h.wants(false, 100, false, 0, false), "ЛКМ не жмёт ПКМ");
        h.delayTicks = 0;
        check(h.wants(true, 1, false, 0, false), "без задержки — с первого тика");

        h.mode = HoldSettings.POINTS;
        h.ranges.add(new HoldSettings.Range(100, 199));
        h.ranges.add(new HoldSettings.Range(50, 10));          // перепутаны
        check(!h.wants(true, 5, false, 9, false) && h.wants(true, 5, false, 10, false) && h.wants(true, 5, false, 50, false)
                && !h.wants(true, 5, false, 51, false) && h.wants(true, 5, false, 199, false) && !h.wants(true, 5, false, 200, false),
                "участки записи включительно, перепутанные концы");
        check(h.wants(true, 5, true, 0, true) && !h.wants(true, 5, true, 150, false), "маршрут по точкам — только галочка");

        h.button = HoldSettings.USE;
        h.mode = HoldSettings.WHOLE;
        check(h.wants(false, 1, true, 0, false) && !h.wants(true, 1, true, 0, false), "ПКМ");

        HoldSettings c = h.copy();
        c.ranges.get(0).from = 7;
        check(h.ranges.get(0).from != 7 || h.ranges.get(0) != c.ranges.get(0), "copy() глубокая");

        HoldSettings bad = new HoldSettings();
        bad.button = "middle"; bad.mode = "x"; bad.delayTicks = -5; bad.ranges.add(null); bad.ranges.add(new HoldSettings.Range(-3, 9));
        HoldSettings.sanitize(bad);
        check(HoldSettings.NONE.equals(bad.button) && HoldSettings.WHOLE.equals(bad.mode) && bad.delayTicks == 0
                && bad.ranges.size() == 1 && bad.ranges.get(0).from == 0, "sanitize");
        bad.delayTicks = Integer.MAX_VALUE;
        check(HoldSettings.sanitize(bad).delayTicks == HoldSettings.DELAY_MAX && HoldSettings.sanitize(null) != null, "предел задержки, null");

        Gson gson = new Gson();
        // старые файлы — без hold
        MacroStorage.SavedMacro m3 = gson.fromJson("{\"name\":\"m\",\"version\":3,\"frames\":[{\"x\":1}]}", MacroStorage.SavedMacro.class);
        m3.sanitize();
        check(m3.settings.hold != null && !m3.settings.hold.active(), "макрос v3 без hold");
        MacroStorage.SavedMacro m4 = gson.fromJson("{\"name\":\"m\",\"version\":4,\"settings\":{\"hold\":{\"button\":\"attack\","
                + "\"mode\":\"points\",\"delayTicks\":20,\"ranges\":[{\"from\":30,\"to\":5}]}},\"frames\":[{\"x\":1}]}", MacroStorage.SavedMacro.class);
        m4.sanitize();
        HoldSettings mh = m4.settings.hold;
        check(mh.attack() && HoldSettings.POINTS.equals(mh.mode) && mh.delayTicks == 20 && mh.ranges.get(0).from == 5
                && mh.ranges.get(0).to == 30 && m4.settings.camera != null, "макрос v4 с hold");
        var r = gson.fromJson("{\"version\":3,\"points\":[{\"x\":0,\"y\":0,\"z\":0,\"hold\":true},{\"x\":1,\"y\":0,\"z\":0}],"
                + "\"settings\":{\"hold\":{\"button\":\"use\",\"mode\":\"points\"}}}", com.farmmacro.route.PointRoute.class);
        r.sanitize();
        check(r.points.get(0).hold && !r.points.get(1).hold && r.points.get(0).copy().hold && r.settings.hold.use(), "маршрут v3: галочка точки");
        var r2 = gson.fromJson("{\"version\":2,\"points\":[{\"x\":0,\"y\":0,\"z\":0}]}", com.farmmacro.route.PointRoute.class);
        r2.sanitize();
        check(!r2.points.get(0).hold && !r2.settings.hold.active(), "маршрут v2 без hold");

        System.out.println("Зажим мыши: задержка, весь проход, участки записи, галочки точек, ЛКМ/ПКМ, sanitize, файлы — OK");
    }
}
