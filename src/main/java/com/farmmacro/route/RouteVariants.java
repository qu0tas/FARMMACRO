package com.farmmacro.route;

import com.farmmacro.macro.MacroManager;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Runtime selection and editor preview, independent from the ideal buffer. */
public final class RouteVariants {
    private RouteVariants() {}
    public static final class Session {
        private final List<PointRoute> routes = new ArrayList<>();
        private final List<Double> weights = new ArrayList<>();
        private final RouteRandomSettings settings;
        private final SplittableRandom rng;
        private int lap;
        public int number;
        public long variantSeed;
        public Session(PointRoute ideal, List<PointRoute> alternatives, List<Double> alternativeWeights) {
            settings = ideal.settings.routeRandom.copy(); settings.sanitize();
            rng = new SplittableRandom(settings.seed);
            routes.add(ideal.copy()); weights.add(1.0);
            for (int i = 0; i < alternatives.size(); i++) {
                PointRoute r = alternatives.get(i);
                if (compatible(ideal, r) && alternativeWeights.get(i) > 0) {
                    routes.add(r.copy()); weights.add(alternativeWeights.get(i));
                }
            }
        }
        public PointRoute next() { return next(true); }
        public PointRoute next(boolean chooseAlternative) {
            if(routes.getFirst().settings.farm.enabled){number=0;variantSeed=0;return routes.getFirst().copy();}
            double total = weights.stream().mapToDouble(Double::doubleValue).sum(), roll = rng.nextDouble(total);
            int chosen = 0;
            for (int i = 0; i < weights.size(); i++) { roll -= weights.get(i); if (roll < 0) { chosen = i; break; } }
            PointRoute r = routes.get(chooseAlternative ? chosen : 0);
            if (RouteRandomSettings.OFF.equals(settings.mode)) { number = 0; return r.copy(); }
            number = RouteRandomSettings.FIXED.equals(settings.mode) ? rng.nextInt(settings.variants) + 1 : ++lap;
            variantSeed = RouteRandomizer.variantSeed(settings.seed, number - 1L);
            return RouteRandomizer.generate(r, settings, variantSeed);
        }
    }
    public static double startDistance(PointRoute a, PointRoute b) {
        if (a == null || b == null || a.points.isEmpty() || b.points.isEmpty()) return Double.POSITIVE_INFINITY;
        RoutePoint p = a.points.getFirst(), q = b.points.getFirst();
        return Math.sqrt(Math.pow(p.x-q.x,2)+Math.pow(p.y-q.y,2)+Math.pow(p.z-q.z,2));
    }
    public static boolean compatible(PointRoute a, PointRoute b) {
        return startDistance(a,b) <= 2 && java.util.Objects.equals(a.dimension,b.dimension)
                && java.util.Objects.equals(a.world,b.world);
    }
    private static PointRoute preview;
    private static int revision = -1, number;
    public static PointRoute preview() { return revision == RouteBuffer.INSTANCE.revision() ? preview : null; }
    public static int previewNumber() { return number; }
    public static void nextPreview() {
        if (MacroManager.INSTANCE.getState() != MacroManager.State.IDLE) return;
        int rev = RouteBuffer.INSTANCE.revision();
        if (revision != rev) number = 0;
        revision = rev;
        PointRoute ideal = RouteBuffer.INSTANCE.route();
        preview = RouteRandomizer.generate(ideal, ideal.settings.routeRandom,
                RouteRandomizer.variantSeed(ideal.settings.routeRandom.seed, number++));
    }
    public static void clearPreview() { preview = null; revision = -1; number = 0; }
    public static boolean saveCopy() {
        PointRoute p = preview();
        if (p == null) return false;
        PointRoute copy = p.copy();
        String stem = (p.name == null || p.name.isBlank() ? "route" : p.name) + "_rnd", name = stem;
        for (int n=2; RouteStorage.INSTANCE.exists(name); n++) name = stem + "_" + n;
        copy.settings.routeRandom.mode = RouteRandomSettings.OFF;
        copy.settings.routeRandom.alternatives.clear();
        for (RoutePoint q : copy.points) q.rndPause = q.rndBeforeDrop = q.rndLanding = q.rndChance = q.rndOffset = null;
        return RouteStorage.INSTANCE.save(name, copy);
    }
}
