package com.farmmacro.route;

import java.util.ArrayList;
import java.util.List;

/** Geometry/timing variation; independent of Minecraft and input humanization. */
public final class RouteRandomSettings {
    public static final String OFF = "off", EACH = "each", FIXED = "fixed";
    public String mode = OFF;
    public long seed = 1;
    public int variants = 5;
    public Range offset = new Range(0, 0.15), pause = new Range(0, 8), chance = new Range(15, 15);
    public Range beforeDrop = new Range(0, 8), landing = new Range(0, 8), edgeRelease = new Range(0, 2);
    public double beforeChance = 15;
    /** Base route has weight one; alternatives are resolved once at run start. */
    public List<Alternative> alternatives = new ArrayList<>();
    public static final class Alternative {
        public String filename;
        public double weight = 1;
        public Alternative() {}
        public Alternative(String file) { filename = file; }
    }
    public static final class Range {
        public double min, max;
        public Range() {}
        public Range(double min, double max) { this.min = min; this.max = max; }
        public Range copy() { return new Range(min, max); }
        public void sanitize(double limit) {
            min = finiteClamp(min, limit); max = finiteClamp(max, limit);
            if (max < min) { double t = min; min = max; max = t; }
        }
    }
    private static double finiteClamp(double v, double max) { return Double.isFinite(v) ? Math.max(0, Math.min(max, v)) : 0; }
    public void sanitize() {
        if (!List.of(OFF, EACH, FIXED).contains(mode == null ? "" : mode)) mode = OFF;
        variants = Math.max(1, Math.min(100, variants));
        if (offset == null) offset = new Range(0, .15); offset.sanitize(.15);
        if (pause == null) pause = new Range(0, 8); pause.sanitize(1200);
        if (chance == null) chance = new Range(15, 15); chance.sanitize(100);
        if (beforeDrop == null) beforeDrop = new Range(0, 8); beforeDrop.sanitize(1200);
        if (landing == null) landing = new Range(0, 8); landing.sanitize(1200);
        if (edgeRelease == null) edgeRelease = new Range(0, 2); edgeRelease.sanitize(6);
        beforeChance = finiteClamp(beforeChance, 100);
        if (alternatives == null) alternatives = new ArrayList<>();
        alternatives.removeIf(a -> a == null || a.filename == null || !a.filename.matches("[^/\\\\]+\\.json") || a.filename.contains(".."));
        if (alternatives.size() > 64) alternatives = new ArrayList<>(alternatives.subList(0, 64));
        for (Alternative a : alternatives) a.weight = Double.isFinite(a.weight) ? Math.max(0, Math.min(1000, a.weight)) : 0;
    }
    public RouteRandomSettings copy() {
        RouteRandomSettings s = new RouteRandomSettings();
        s.mode = mode; s.seed = seed; s.variants = variants; s.beforeChance = beforeChance;
        s.offset = offset == null ? new Range(0,.15) : offset.copy();
        s.pause = pause == null ? new Range(0,8) : pause.copy();
        s.chance = chance == null ? new Range(15,15) : chance.copy();
        s.beforeDrop = beforeDrop == null ? new Range(0,8) : beforeDrop.copy();
        s.landing = landing == null ? new Range(0,8) : landing.copy();
        s.edgeRelease = edgeRelease == null ? new Range(0,2) : edgeRelease.copy();
        s.alternatives.clear();
        if(alternatives!=null) for (Alternative a : alternatives) {
            if(a!=null) { Alternative b = new Alternative(a.filename); b.weight = a.weight; s.alternatives.add(b); }
        }
        s.sanitize();
        return s;
    }
}
