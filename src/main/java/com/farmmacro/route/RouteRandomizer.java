package com.farmmacro.route;

import java.util.List;
import java.util.SplittableRandom;
import static com.farmmacro.route.RouteRandomSettings.Range;

/** Seeded pure generator. Never mutates the ideal route, never relocates a drop or its neighbours. */
public final class RouteRandomizer {
    private RouteRandomizer() {}
    public static long variantSeed(long seed, long index) {
        long x = seed + 0x9e3779b97f4a7c15L * (index + 1);
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    public static PointRoute generate(PointRoute ideal, RouteRandomSettings settings, long seed) {
        RouteRandomSettings s = settings.copy(); s.sanitize();
        PointRoute r = ideal.copy();
        SplittableRandom rng = new SplittableRandom(seed);
        for (int i = 0; i < r.points.size(); i++) {
            RoutePoint p = r.points.get(i), original = ideal.points.get(i);
            double limit = Math.min(.15, sample(rng, pick(p.rndOffset, s.offset)));
            boolean pinned = i == 0 || p.drop || i > 0 && ideal.points.get(i - 1).drop
                    || i + 1 < r.points.size() && ideal.points.get(i + 1).drop;
            if (!pinned && limit > 0) {
                for (int attempt = 0; attempt < 24; attempt++) {
                    double a = rng.nextDouble(2 * Math.PI), d = rng.nextDouble(limit);
                    double nx = original.x + Math.cos(a) * d, nz = original.z + Math.sin(a) * d;
                    if (safeOffset(ideal.points, i, nx, nz, limit)) {
                        p.setOffset(nx - p.centerX(), nz - p.centerZ()); break;
                    }
                }
            }
            double chance = sample(rng, pick(p.rndChance, s.chance));
            int pause = rng.nextDouble(100) < chance ? ticks(rng, pick(p.rndPause, s.pause)) : 0;
            p.pauseTicks = Math.min(1200, p.pauseTicks + pause);
            if (p.drop) {
                p.beforeDropTicks = p.rndBeforeDrop != null || rng.nextDouble(100) < s.beforeChance
                        ? ticks(rng, pick(p.rndBeforeDrop, s.beforeDrop)) : 0;
                p.landPauseTicks = Math.min(1200, p.landPauseTicks + ticks(rng, pick(p.rndLanding, s.landing)));
                p.edgeReleaseTicks = ticks(rng, s.edgeRelease);
            }
        }
        return r;
    }
    public static boolean safeOffset(List<RoutePoint> pts, int i, double x, double z, double limit) {
        RoutePoint p = pts.get(i);
        if (Math.hypot(x - p.x, z - p.z) > limit + 1e-9) return false;
        // Strictly inside the original block, including non-centred ideal points.
        if ((int)Math.floor(x) != (int)Math.floor(p.x) || (int)Math.floor(z) != (int)Math.floor(p.z)) return false;
        for (int j = 0; j + 1 < pts.size(); j++) {
            if (j == i || j + 1 == i) continue;
            RoutePoint a = pts.get(j), b = pts.get(j + 1);
            if (a.drop || Math.abs(a.y - p.y) > .6 || Math.abs(b.y - p.y) > .6) continue;
            double old = dist(p.x, p.z, a.x, a.z, b.x, b.z);
            double now = dist(x, z, a.x, a.z, b.x, b.z);
            // Stay on our side of the midline to a neighbouring row (body margin where possible).
            if (old > .01 && now < old * .5 + Math.min(.3, old * .2)) return false;
        }
        return true;
    }
    private static double dist(double x, double z, double ax, double az, double bx, double bz) {
        double dx = bx-ax, dz = bz-az, n = dx*dx+dz*dz;
        double t = n < 1e-12 ? 0 : Math.max(0, Math.min(1, ((x-ax)*dx+(z-az)*dz)/n));
        return Math.hypot(x-ax-t*dx, z-az-t*dz);
    }
    private static Range pick(Range local, Range global) { return local == null ? global : local; }
    private static double sample(SplittableRandom r, Range v) { return v.max <= v.min ? v.min : r.nextDouble(v.min, v.max); }
    private static int ticks(SplittableRandom r, Range v) {
        int lo = (int)Math.ceil(v.min), hi = (int)Math.floor(v.max);
        return hi <= lo ? lo : r.nextInt(lo, hi + 1);
    }
}
