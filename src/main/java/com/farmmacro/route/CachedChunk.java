package com.farmmacro.route;

import java.util.Map;
import java.util.TreeMap;

/** Immutable coarse navigation cells, per 16³ section. No game classes or block IDs on disk. */
public final class CachedChunk {
    public final int x,z;
    private final Map<Integer,short[]> sections = new TreeMap<>();
    public CachedChunk(int x,int z,Map<Integer,short[]> data) {
        this.x=x; this.z=z;
        for(var e:data.entrySet()) {
            if(e.getValue().length!=4096) throw new IllegalArgumentException("section size");
            sections.put(e.getKey(),e.getValue().clone());
        }
    }
    public long cellBytes(){return sections.size()*8192L;}
    public Map<Integer,short[]> sections() {
        Map<Integer,short[]> copy=new TreeMap<>();
        sections.forEach((y,a) -> copy.put(y,a.clone())); return copy;
    }
    public static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    public short cell(int bx,int y,int bz) {
        short[] s=sections.get(y>>4);
        return s==null ? encode(-1,PathFinder.H_DANGER,0) : s[((y&15)<<8)|((bz&15)<<4)|(bx&15)];
    }
    public static short encode(double top,int hazard,int edges) {
        int t=top<0 ? 0 : 1+(int)Math.round(Math.min(1.5,top)*16);
        return (short)(t|((hazard&7)<<5)|((edges&15)<<8));
    }
    public static double top(short c) { return (c&31)==0 ? -1 : ((c&31)-1)/16.0; }
    public static int hazard(short c) { return (c>>>5)&7; }
    public static int edges(short c) { return (c>>>8)&15; }
    public static short cell(PathFinder.World w,int x,int y,int z) { return encode(w.top(x,y,z),w.hazard(x,y,z),w.edges(x,y,z)); }
}
