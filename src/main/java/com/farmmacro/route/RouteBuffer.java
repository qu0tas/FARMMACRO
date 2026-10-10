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
    /** Дополнительно выделенные точки (Alt+ЛКМ, Ctrl+A) — для группового смещения. Сбрасываются при изменении состава. */
    private final java.util.TreeSet<Integer> marked = new java.util.TreeSet<>();
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
    public void select(int i)         { selected = i >= 0 && i < size() ? i : -1; if (selected < 0) marked.clear(); revision++; }

    // ── Выделение нескольких точек ──
    public boolean isMarked(int i)    { return marked.contains(i); }
    public int markedCount()          { return marked.size(); }
    /** Alt+ЛКМ: добавить/убрать точку из выделения (выбранная тоже считается выделенной). */
    public void toggleMark(int i) {
        if (get(i) == null) return;
        if (!marked.remove(i)) marked.add(i);
        if (selected < 0) selected = i;
        revision++;
    }
    public void markAll() { marked.clear(); for (int i = 0; i < size(); i++) marked.add(i); if (selected < 0 && size() > 0) selected = 0; revision++; }
    public void clearMarks() { marked.clear(); revision++; }
    /** Выделенные точки: отмеченные + выбранная, по порядку. */
    public List<Integer> selection() {
        java.util.TreeSet<Integer> all = new java.util.TreeSet<>();
        for (int i : marked) if (i < size()) all.add(i);
        if (selected() >= 0) all.add(selected());
        return new java.util.ArrayList<>(all);
    }

    /** Сдвинуть смещение выделенных точек на (dx, dz), каждая обрезается до ±0.5. snapshot — сделать снимок для Ctrl+Z. */
    public int nudge(List<Integer> which, double dx, double dz, boolean snapshot) {
        if (which.isEmpty()) return 0;
        if (snapshot) snapshot();
        int n = 0;
        for (int i : which) {
            RoutePoint p = get(i);
            if (p == null) continue;
            p.setOffset(p.ox + dx, p.oz + dz);
            n++;
        }
        changed();
        return n;
    }

    /** Задать одно и то же смещение точкам (одно действие для Ctrl+Z). */
    public void setOffset(List<Integer> which, double ox, double oz) {
        if (which.isEmpty()) return;
        snapshot();
        for (int i : which) { RoutePoint p = get(i); if (p != null) p.setOffset(ox, oz); }
        changed();
    }

    public List<Integer> all() {
        List<Integer> l = new java.util.ArrayList<>(size());
        for (int i = 0; i < size(); i++) l.add(i);
        return l;
    }
    public boolean canUndo()          { return !undo.isEmpty(); }

    /** Снимок для отмены. Вызывать перед изменением (перетаскивание — один раз в начале). */
    public void snapshot() {
        undo.push(route.copy());
        while (undo.size() > UNDO_MAX) undo.removeLast();
    }

    /** Убрать последний снимок (окно точки закрыли без изменений). */
    public void undoDrop() { undo.poll(); }

    public boolean undo() {
        marked.clear();
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
        if (last != null) { p.pauseTicks = 0; p.jump = false; p.drop = false; p.dropDepth = 0; }
        route.points.add(p);
        marked.clear();
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
        p.pauseTicks = 0; p.jump = false; p.drop = false; p.dropDepth = 0;
        route.points.add(best + 1, p);
        marked.clear();
        selected = best + 1;
        changed();
        return selected;
    }

    /** Вставить готовую точку на место idx (со снимком для Ctrl+Z). @return индекс или -1 */
    public int insertAt(int idx, RoutePoint p) {
        if (size() >= MAX_POINTS || idx < 0 || idx > size()) return -1;
        marked.clear();
        snapshot();
        route.points.add(idx, p);
        selected = idx;
        changed();
        return idx;
    }

    public void remove(int i) {
        if (get(i) == null) return;
        marked.clear();
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
        p.moveCenter(x, y, z);              // x/z — центр блока под прицелом, смещение остаётся
        changed();
    }

    public void replaceAll(Minecraft mc, List<RoutePoint> pts) {
        marked.clear();
        snapshot();
        stamp(mc);
        route.points.clear();
        route.points.addAll(pts);
        if (!pts.isEmpty() && mc.level != null) { route.dimension = dimensionId(mc); route.world = worldId(mc); }
        selected = -1;
        changed();
    }

    /** Настройки текущего маршрута (окно «Настройки макроса»). */
    public com.farmmacro.macro.MacroSettings settings() {
        if (route.settings == null) route.settings = new com.farmmacro.macro.MacroSettings();
        return route.settings;
    }

    /** Настройки уже записаны в файл загруженного маршрута (⚙ у карточки) — взять копию, «изменён» не ставить. */
    public void replaceSettings(com.farmmacro.macro.MacroSettings s) {
        if (s == null) return;
        route.settings = s;
        revision++;
    }

    /** Новый пустой маршрут (без отмены). */
    public void clear() {
        marked.clear();
        MacroManager.INSTANCE.routeUnloaded();
        route = new PointRoute();
        loadedName = null;
        dirty = false;
        selected = -1;
        undo.clear();
        revision++;
    }

    /** Загрузить из файла. */
    public void load(String name, PointRoute r) {
        marked.clear();
        MacroManager.INSTANCE.routeUnloaded();
        MacroManager.INSTANCE.showNav();
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
