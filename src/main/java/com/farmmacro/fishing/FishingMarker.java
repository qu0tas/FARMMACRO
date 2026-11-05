package com.farmmacro.fishing;
/** Squared distance ownership heuristic, NOT guaranteed attribution in overlapping fishing crowds. */
public final class FishingMarker {
    public static boolean candidate(double ownDistanceSq) {
        return Double.isFinite(ownDistanceSq) && ownDistanceSq >= 0 && ownDistanceSq <= 16;
    }

    public static boolean competitor(double ownDistanceSq, double otherDistanceSq) {
        return !Double.isFinite(otherDistanceSq) || otherDistanceSq <= ownDistanceSq + .25;
    }

    private FishingMarker() {}
}
