package com.farmmacro.combat;
/** Pure bounded constant-velocity intercept. Units: blocks and seconds, no server physics claim. */
public final class ProjectileLead {
    public record Offset(double x, double y, double z, double seconds) {}

    public static Offset solve(
            double rx,
            double ry,
            double rz,
            double vx,
            double vy,
            double vz,
            double speed,
            double delay,
            double cap,
            double maxOffset) {
        if (!finite(rx, ry, rz, vx, vy, vz, speed, delay, cap, maxOffset) || speed <= 0 || cap <= 0 || maxOffset <= 0)
            return new Offset(0, 0, 0, 0);
        delay = Math.clamp(delay, 0, .2);
        cap = Math.clamp(cap, 0, 1);
        maxOffset = Math.clamp(maxOffset, 0, 6);
        double v2 = vx * vx + vy * vy + vz * vz;
        if (v2 > 24 * 24) return new Offset(0, 0, 0, 0);
        double x = rx + vx * delay, y = ry + vy * delay, z = rz + vz * delay;
        double a = v2 - speed * speed, b = 2 * (x * vx + y * vy + z * vz), c = x * x + y * y + z * z;
        double t = Double.NaN;
        if (Math.abs(a) < 1e-8) {
            if (b < -1e-8) t = -c / b;
        } else {
            double disc = b * b - 4 * a * c;
            if (disc >= 0) {
                double d = Math.sqrt(disc), u = (-b - d) / (2 * a), v = (-b + d) / (2 * a);
                if (u >= 0) t = u;
                if (v >= 0 && (!Double.isFinite(t) || v < t)) t = v;
            }
        }
        if (!Double.isFinite(t) || t < 0) return new Offset(0, 0, 0, 0);
        t = Math.min(cap, t + delay);
        double length = Math.sqrt(v2) * t;
        if (length > maxOffset) t *= maxOffset / length;
        return new Offset(vx * t, vy * t, vz * t, t);
    }

    public static boolean plausibleStep(double dx, double dy, double dz, double dt) {
        return finite(dx, dy, dz, dt) && dt > 0 && dt <= .25 && (dx * dx + dy * dy + dz * dz) <= Math.pow(24 * dt, 2);
    }

    private static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }

    private ProjectileLead() {}
}
