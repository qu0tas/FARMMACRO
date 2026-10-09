package com.farmmacro.route;

import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Текущий маршрут по точкам в памяти (то, что видно в мире, правит редактор и проходит автоход).
 * Каждое изменение увеличивает {@link #revision()} — по нему RouteRenderer пересчитывает геометрию.
 * Отмена (Ctrl+Z): перед каждым изменением кладётся копия маршрута, не больше {@link #UNDO_MAX}.
 */
public final class RouteBuffer {
    public static final RouteBuffer INSTANCE = new RouteBuffer();
    public static final int UNDO_MAX = 100;
    public static final int MAX_POINTS = 4096;

    private PointRoute route = new PointRoute();
    private String loadedName;
    private boolean dirty;
    private int revision;
    private int selected = -1;
    private final Deque<PointRoute> undo = new ArrayDeque<>();

    private RouteBuffer() {}

    public PointRoute route()         { return route; }
    public List<RoutePoint> points()  { return Collections.unmodifiableList(route.points); }
    public int  size()                { return route.points.size(); }
    public boolean isEmpty()          { return route.points.isEmpty(); }
    public RoutePoint get(int i)      { return i >= 0 && i < size() ? route.points.get(i) : null; }
    public int  revision()            { return revision; }
    public String loadedName()        { return loadedName; }
    public boolean isDirty()          { return dirty; }
    public int  selected()            { return selected < size() ? selected : -1; }
    public void select(int i)         { selected = i >= 0 && i < size() ? i : -1; revision++; }
    public boolean canUndo()          { return !undo.isEmpty(); }

    /** Снимок для отмены. Вызывать перед изменением (перетаскивание — один раз в начале). */
    public void snapshot() {
        undo.push(route.copy());
        while (undo.size() > UNDO_MAX) undo.removeLast();
    }

    /** Убрать последний снимок (окно точки закрыли без изменений). */
    public void undoDrop() { undo.poll(); }

    public boolean undo() {
        PointRoute prev = undo.poll();
        if (prev == null) return false;
        route = prev;
        if (selected >= size()) selected = size() - 1;
        changed();
        return true;
    }

    /** Что-то поменялось в точках (в т.ч. параметры из окна точки). */
    public void changed() {
        revision++;
        dirty = true;
        MacroManager.INSTANCE.useRouteSource();
    }

    /** Измерение и мир берутся при первой точке. */
    private void stamp(Minecraft mc) {
        if (!route.points.isEmpty() && route.dimension != null) return;
        if (mc.level != null) route.dimension = mc.level.dimension().identifier().toString();
        route.world = worldId(mc);
    }

    public static String worldId(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return null;
    }

    public static String dimensionId(Minecraft mc) {
        return mc.level != null ? mc.level.dimension().identifier().toString() : null;
    }

    /** Точка с параметрами предыдущей последней (удобно ставить ряд с одним действием). @return индекс или -1 */
    public int add(Minecraft mc, double x, double y, double z) {
        if (size() >= MAX_POINTS) return -1;
        snapshot();
        stamp(mc);
        RoutePoint last = get(size() - 1);
        RoutePoint p = last != null ? last.withPos(x, y, z) : new RoutePoint(x, y, z);
        if (last != null) { p.pauseTicks = 0; p.jump = false; }
        route.points.add(p);
        selected = size() - 1;
        changed();
        return selected;
    }

    /** Вставить точку в ближайший отрезок (по расстоянию от точки до отрезка в плоскости XZ). */
    public int insertNearest(Minecraft mc, double x, double y, double z) {
        if (size() < 2) return add(mc, x, y, z);
        if (size() >= MAX_POINTS) return -1;
        int best = 0; double bd = Double.MAX_VALUE;
        for (int i = 0; i + 1 < size(); i++) {
            RoutePoint a = route.points.get(i), b = route.points.get(i + 1);
            double d = distToSegmentXZ(x, z, a.x, a.z, b.x, b.z);
            if (d < bd) { bd = d; best = i; }
        }
        snapshot();
        RoutePoint a = route.points.get(best);
        RoutePoint p = a.withPos(x, y, z);
        p.pauseTicks = 0; p.jump = false;
        route.points.add(best + 1, p);
        selected = best + 1;
        changed();
        return selected;
    }

    public void remove(int i) {
        if (get(i) == null) return;
        snapshot();
        route.points.remove(i);
        if (selected == i) selected = -1;
        else if (selected > i) selected--;
        changed();
    }

    /** Сдвиг без снимка (перетаскивание: снимок делает редактор в начале). */
    public void moveNoUndo(int i, double x, double y, double z) {
        RoutePoint p = get(i);
        if (p == null) return;
        p.x = x; p.y = y; p.z = z;
        changed();
    }

    public void replaceAll(Minecraft mc, List<RoutePoint> pts) {
        snapshot();
        stamp(mc);
        route.points.clear();
        route.points.addAll(pts);
        if (!pts.isEmpty() && mc.level != null) { route.dimension = dimensionId(mc); route.world = worldId(mc); }
        selected = -1;
        changed();
    }

    public void setPitch(float pitch) {
        route.pitch = Math.max(-90, Math.min(90, pitch));
        changed();
    }

    /** Новый пустой маршрут (без отмены). */
    public void clear() {
        route = new PointRoute();
        loadedName = null;
        dirty = false;
        selected = -1;
        undo.clear();
        revision++;
    }

    /** Загрузить из файла. */
    public void load(String name, PointRoute r) {
        route = r;
        loadedName = name;
        dirty = false;
        selected = -1;
        undo.clear();
        revision++;
        MacroManager.INSTANCE.useRouteSource();
    }

    public void markSaved(String name) {
        loadedName = name;
        route.name = name;
        dirty = false;
    }

    static double distToSegmentXZ(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az, wx = px - ax, wz = pz - az;
        double vv = vx * vx + vz * vz;
        double t = vv < 1e-12 ? 0 : Math.max(0, Math.min(1, (wx * vx + wz * vz) / vv));
        return Math.hypot(wx - vx * t, wz - vz * t);
    }
}
