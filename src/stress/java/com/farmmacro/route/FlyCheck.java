package com.farmmacro.route;

import java.util.List;
import java.util.Locale;

/**
 * v1.15.1 «Есть полёт»: путь через пропасть и через высокую стену только с полётом, предел высоты, пешком — если можно,
 * симуляция: взлёт (двойной прыжок), Пробел/Shift по высоте, посадка у цели (полёт выключается на земле).
 */
public final class FlyCheck {
    private FlyCheck() {}

    static int checks;
    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("FlyCheck: " + what);
        checks++;
    }

    static PathFinder.Result find(PathCheck.Grid g, double x, double y, double z, List<PathFinder.Goal> goals, boolean fly, int ceil) {
        PathFinder.Params p = new PathFinder.Params();
        p.maxDist = 64; p.fly = fly; p.flyCeil = ceil;
        return new PathFinder(g, p).find(x, y, z, true, goals);
    }

    static int flyPts(PathFinder.Result r) { int n = 0; for (RoutePoint p : r.points) if (p.fly) n++; return n; }
    static double maxY(PathFinder.Result r) { double m = -1e9; for (double[] n : r.nodes) m = Math.max(m, n[1]); return m; }

    /** Полёт: инерция 0.91, ускорение 0.1 бл/т по клавишам; вверх/вниз 0.375 бл/т (как в игре 0.15 × 2.5). */
    record Sim(boolean done, int ticks, double x, double y, double z, boolean flying, int takeoffs, String why) {}

    static Sim fly(PathCheck.Grid g, double x, double y, double z, List<PathFinder.Goal> goals, int maxTicks) {
        PathFinder.Result r = find(g, x, y, z, goals, true, 12);
        if (!r.ok) return new Sim(false, 0, x, y, z, false, 0, r.reason);
        ReturnCore rc = new ReturnCore();
        rc.steeringWorld=g;
        FlightArrival arrival=new FlightArrival();
        double previousX=x,previousY=y,previousZ=z;
        rc.start(r.points, x, z, 0f, 0f);
        WalkCore.Out out = new WalkCore.Out();
        float yaw = 0, pitch = 0;
        double vx = 0, vz = 0;
        boolean flying = false, onGround = true;
        int takeoffs = 0, replans = 0;
        for (int t = 0; t < maxTicks; t++) {
            ReturnCore.Phase ph = rc.tick(x, y, z, onGround, flying, yaw, pitch, false, out);
            if (ph == ReturnCore.Phase.DONE) {
                // ReturnWalker owns final arrival: ReturnCore DONE alone is not a landing.
                var result=arrival.tick(g,true,onGround,flying,x,y,z,x-previousX,y-previousY,z-previousZ,yaw,r.points.getLast(),out);
                if(result==FlightArrival.Result.FAIL)return new Sim(false,t,x,y,z,flying,takeoffs,arrival.reason);
                if(result==FlightArrival.Result.DONE)return new Sim(true,t,x,y,z,flying,takeoffs,null);
                if(Math.hypot(r.points.getLast().x-x,r.points.getLast().z-z)>.2)
                    rc.wantYaw=WalkCore.yawTo(x,z,r.points.getLast().x,r.points.getLast().z);
            }
            previousX=x;previousY=y;previousZ=z;
            if (ph == ReturnCore.Phase.REPLAN) {
                if (++replans > 5) return new Sim(false, t, x, y, z, flying, takeoffs, "replans: " + rc.reason);
                PathFinder.Params p = new PathFinder.Params(); p.fly = true; p.flyingNow = flying;
                PathFinder.Result r2 = new PathFinder(g, p).find(x, y, z, onGround, goals);
                if (!r2.ok) return new Sim(false, t, x, y, z, flying, takeoffs, r2.reason);
                r=r2;arrival.reset();
                rc.start(r2.points, x, z, 0f, 0f);
                continue;
            }
            if (rc.needFly) { flying = true; takeoffs++; y += 0.1; onGround = false; continue; }   // двойной прыжок
            float dy = WalkCore.wrap(rc.wantYaw - yaw);
            yaw += (float) Math.max(-9, Math.min(9, dy));
            double[] dir = WalkCore.moveDir(out, yaw);
            boolean moving = out.forward || out.back || out.left || out.right;
            if (flying) {
                vx = vx * 0.91 + (moving ? dir[0] * 0.1 : 0) * 0.5; vz = vz * 0.91 + (moving ? dir[1] * 0.1 : 0) * 0.5;
                double vy = out.jump ? 0.375 : out.sneak ? -0.375 : 0;
                double ny = y + vy;
                double f = PathCheck.support(g, x, y + 0.01, z);
                if (vy < 0 && !Double.isNaN(f) && ny <= f) { ny = f; onGround = true; flying = false; }   // сел — полёт выкл
                else onGround = !Double.isNaN(f) && Math.abs(f - y) < 1e-6;
                if (vy > 0 && PathCheck.blocked(g, x, ny, z)) return new Sim(false, t, x, y, z, flying, takeoffs, "головой в блок");
                y = ny;
            } else {
                vx = vx * 0.55 + dir[0] * (moving ? WalkCore.WALK : 0) * 0.45; vz = vz * 0.55 + dir[1] * (moving ? WalkCore.WALK : 0) * 0.45;
                double f = PathCheck.support(g, x + vx, y + 0.01, z + vz);
                if (Double.isNaN(f) || f < y - 1e-6) return new Sim(false, t, x, y, z, flying, takeoffs, "шагнул в пропасть пешком");
            }
            double nx = x + vx, nz = z + vz;
            if (PathCheck.blocked(g, nx, y, nz)) return new Sim(false, t, x, y, z, flying, takeoffs, String.format(Locale.ROOT, "врезался в стену у (%.1f %.1f %.1f)", nx, y, nz));
            x = nx; z = nz;
        }
        return new Sim(false, maxTicks, x, y, z, flying, takeoffs, "не долетел");
    }

    public static void main() {
        long t0 = System.nanoTime();
        StringBuilder rep = new StringBuilder();

        // 1) два острова в пустоте: 0..4 и 15..19, пол на y=63 (ноги 64)
        PathCheck.Grid gap = new PathCheck.Grid().floor(0, 0, 4, 4, 63).floor(15, 0, 19, 4, 63);
        List<PathFinder.Goal> g1 = PathCheck.at(17.5, 64, 2.5);
        PathFinder.Result walk = find(gap, 2.5, 64, 2.5, g1, false, 12);
        check(!walk.ok, "пропасть пешком — нет пути: " + walk);
        PathFinder.Result fl = find(gap, 2.5, 64, 2.5, g1, true, 12);
        check(fl.ok && flyPts(fl) > 0, "пропасть с полётом: " + fl);
        RoutePoint last = fl.points.get(fl.points.size() - 1);
        check(Math.abs(last.x - 17.5) < 1e-6 && Math.abs(last.y - 64) < 1e-6, "садится на цель");
        Sim s1 = fly(gap, 2.5, 64, 2.5, g1, 1500);
        check(s1.done && !s1.flying && Math.hypot(s1.x - 17.5, s1.z - 2.5) < 0.6 && Math.abs(s1.y - 64) < 1e-6, "долетел и сел: " + s1);
        rep.append(String.format(Locale.ROOT, "пропасть 10 бл: пешком нет, с полётом %d точек (%d в воздухе), долетел и сел за %d т; ",
                fl.points.size(), flyPts(fl), s1.ticks));

        // 2) цель в «колодце»: стены высотой 6 вокруг, внутри пол
        PathCheck.Grid well = new PathCheck.Grid().floor(-10, -10, 30, 30, 63)
                .wall(8, 8, 14, 8, 64, 69, 1.0).wall(8, 14, 14, 14, 64, 69, 1.0).wall(8, 8, 8, 14, 64, 69, 1.0).wall(14, 8, 14, 14, 64, 69, 1.0);
        List<PathFinder.Goal> g2 = PathCheck.at(11.5, 64, 11.5);
        check(!find(well, 0.5, 64, 0.5, g2, false, 12).ok, "колодец пешком — нет пути");
        PathFinder.Result fw = find(well, 0.5, 64, 0.5, g2, true, 12);
        check(fw.ok && maxY(fw) >= 70, "колодец с полётом — через стену: " + fw);
        PathFinder.Result lim = find(well, 0.5, 64, 0.5, g2, true, 3);
        check(!lim.ok, "предел высоты 3 — через стену 6 нельзя");
        rep.append(String.format(Locale.ROOT, "«нет пути» с полётом: %d клеток за %.0f мс; ", lim.expanded, lim.micros / 1000.0));
        Sim s2 = fly(well, 0.5, 64, 0.5, g2, 2000);
        check(s2.done && !s2.flying && Math.abs(s2.y - 64) < 1e-6, "перелетел стену и сел: " + s2);
        rep.append(String.format(Locale.ROOT, "колодец (стены 6): пешком нет, полёт до высоты %.0f, сел за %d т, предел 3 — нет; ", maxY(fw), s2.ticks));

        // 3) ровное поле — пешком, без полёта; маленькая яма в 1 блок — тоже пешком
        PathCheck.Grid flat = new PathCheck.Grid().floor(-5, -5, 25, 5, 63);
        PathFinder.Result ff = find(flat, 0.5, 64, 0.5, PathCheck.at(15.5, 64, 0.5), true, 12);
        check(ff.ok && flyPts(ff) == 0, "поле — пешком: " + ff);
        flat.clearCell(5, 63, 0);
        PathFinder.Result fh = find(flat, 0.5, 64, 0.5, PathCheck.at(15.5, 64, 0.5), true, 12);
        check(fh.ok && flyPts(fh) == 0, "ямка — обход пешком: " + fh);
        // 4) над лавой — можно лететь (тело не в лаве)
        PathCheck.Grid lava = new PathCheck.Grid().floor(0, -2, 20, 2, 62);
        for (int x = 4; x <= 14; x++) for (int z = -2; z <= 2; z++) { lava.clearCell(x, 62, z); lava.set(x, 61, z, 1.0); lava.haz(x, 62, z, PathFinder.H_LAVA); }
        for (int x = 0; x <= 20; x++) { lava.wall(x, -3, x, -3, 63, 66, 1.0); lava.wall(x, 3, x, 3, 63, 66, 1.0); }
        List<PathFinder.Goal> g4 = PathCheck.at(18.5, 63, 0.5);
        check(!find(lava, 1.5, 63, 0.5, g4, false, 12).ok, "лава поперёк — пешком нельзя");
        PathFinder.Result fl4 = find(lava, 1.5, 63, 0.5, g4, true, 12);
        check(fl4.ok && flyPts(fl4) > 0, "над лавой с полётом: " + fl4);
        Sim s4 = fly(lava, 1.5, 63, 0.5, g4, 1500);
        check(s4.done, "перелетел лаву: " + s4);
        rep.append(String.format(Locale.ROOT, "поле и ямка — пешком; лава — перелёт за %d т", s4.ticks));

        System.out.printf(Locale.ROOT, "FlyCheck (v1.15.1 полёт): %d проверок — OK (%.0f мс)%n  %s%n", checks, (System.nanoTime() - t0) / 1e6, rep);
    }
}
