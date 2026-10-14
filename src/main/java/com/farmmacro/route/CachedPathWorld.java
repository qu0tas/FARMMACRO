package com.farmmacro.route;

import java.util.*;
import java.util.function.LongPredicate;

/** Fresh chunks always win. Only cells on the final path are retained as revalidation witnesses. */
public final class CachedPathWorld implements PathFinder.World {
    public record Cell(int x,int y,int z) {}
    private final PathFinder.World fresh;
    private final LongPredicate loaded;
    private final Map<Long,CachedChunk> cache;
    public CachedPathWorld(PathFinder.World fresh,LongPredicate loaded,Map<Long,CachedChunk> cache) {
        this.fresh=fresh; this.loaded=loaded; this.cache=cache;
    }
    private boolean fresh(int x,int z) { return loaded.test(CachedChunk.key(x>>4,z>>4)); }
    private short cached(int x,int y,int z) {
        CachedChunk c=cache.get(CachedChunk.key(x>>4,z>>4));
        short v=c==null ? CachedChunk.encode(-1,PathFinder.H_DANGER,0) : c.cell(x,y,z);
        return v;
    }
    @Override public boolean bodyClear(double x,double y,double z,double radius,double height) {
        for(int bx=(int)Math.floor(x-radius);bx<=(int)Math.floor(x+radius);bx++)
          for(int bz=(int)Math.floor(z-radius);bz<=(int)Math.floor(z+radius);bz++)
            if(!fresh(bx,bz)) return true; // Cached cells are checked conservatively by the planner.
        return fresh.bodyClear(x,y,z,radius,height);
    }
    public double top(int x,int y,int z) { return fresh(x,z) ? fresh.top(x,y,z) : CachedChunk.top(cached(x,y,z)); }
    public int hazard(int x,int y,int z) { return fresh(x,z) ? fresh.hazard(x,y,z) : CachedChunk.hazard(cached(x,y,z)); }
    public int edges(int x,int y,int z) { return fresh(x,z) ? fresh.edges(x,y,z) : CachedChunk.edges(cached(x,y,z)); }
    public Map<Cell,Short> witnesses(PathFinder.Result result,double sx,double sy,double sz) {
        if(!result.ok) return Map.of();
        Set<Cell> corridor=new HashSet<>();
        double ax=sx,ay=sy,az=sz;
        for(RoutePoint p:result.points) {
            int n=Math.max(1,(int)Math.ceil(Math.max(Math.hypot(p.x-ax,p.z-az),Math.abs(p.y-ay))*4));
            for(int i=0;i<=n;i++) {
                double t=(double)i/n; int x=(int)Math.floor(ax+(p.x-ax)*t),y=(int)Math.floor(ay+(p.y-ay)*t),z=(int)Math.floor(az+(p.z-az)*t);
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int dy=-4;dy<=2;dy++) corridor.add(new Cell(x+dx,y+dy,z+dz));
            }
            ax=p.x;ay=p.y;az=p.z;
        }
        Map<Cell,Short> out=new HashMap<>();
        for(Cell p:corridor) {
            if(!fresh(p.x,p.z) && cache.containsKey(CachedChunk.key(p.x>>4,p.z>>4)))
                out.put(p,cached(p.x,p.y,p.z));
        }
        return out;
    }
    public static boolean mismatch(Map<Cell,Short> witnesses,PathFinder.World live,LongPredicate loaded) {
        for(var e:witnesses.entrySet()) { Cell c=e.getKey();
            if(loaded.test(CachedChunk.key(c.x>>4,c.z>>4)) && e.getValue()!=CachedChunk.cell(live,c.x,c.y,c.z)) return true;
        }
        return false;
    }
}
