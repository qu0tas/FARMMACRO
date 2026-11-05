package com.farmmacro.route;

import java.util.List;

/** Bounded manual route join, finite coordinates and floor-aware projection. No game dependencies. */
public final class RouteStartHere {
    public static RouteProjection.Hit find(List<RoutePoint> points, double x, double y, double z, double radius) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Double.isFinite(radius)
                || radius <= 0
                || points == null
                || points.size() < 2) return null;
        for (var p : points)
            if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return null;
        var hit = RouteProjection.nearest(points, x, y, z);
        return hit != null && hit.dist() <= radius && hit.dy() <= .6 ? hit : null;
    }

    private RouteStartHere() {}
}
