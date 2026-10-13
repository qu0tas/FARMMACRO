package com.farmmacro.route;

import java.nio.file.*;
import java.util.*;

/** Region roundtrip, negative coordinates, limits/corruption, unloaded routing and mismatch/replan. */
public final class ChunkCacheCheck {
    private static int checks;
    private static void check(boolean ok,String name) { if(!ok) throw new IllegalStateException("ChunkCacheCheck: "+name);checks++; }
    private static CachedChunk chunk(PathFinder.World w,int cx,int cz) {
        Map<Integer,short[]> sections=new TreeMap<>();
        for(int sy=3;sy<=4;sy++) {
            short[] cells=new short[4096];
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                cells[(y<<8)|(z<<4)|x]=CachedChunk.cell(w,(cx<<4)+x,(sy<<4)+y,(cz<<4)+z);
            sections.put(sy,cells);
        }
        return new CachedChunk(cx,cz,sections);
    }
    public static void main() {
        Path tmp=null;
        try {
            tmp=Files.createTempDirectory("kaelix-cache-check-");
            var grid=new PathCheck.Grid().floor(-32,-4,63,6,63);
            grid.set(-3,62,-2,.9375).haz(-4,64,-2,PathFinder.H_CLIMB).edge(-5,64,-2,PathFinder.E_PX)
                    .haz(-6,64,-2,PathFinder.H_DOOR).haz(-7,64,-2,PathFinder.H_WATER).haz(-8,64,-2,PathFinder.H_LAVA);
            var store=new ChunkCacheStore(tmp,"example.org:25565","minecraft:overworld",64L*1024*1024);
            var negative=chunk(grid,-1,-1);store.put(negative);
            check(Files.exists(store.directory().resolve("r.-1.-1.bin")),"negative region name");
            var loaded=store.load(Set.of(CachedChunk.key(-1,-1)));check(loaded.size()==1,"read negative chunk");
            var round=loaded.get(CachedChunk.key(-1,-1));
            for(int y=48;y<80;y++)for(int z=-16;z<0;z++)for(int x=-16;x<0;x++)check(round.cell(x,y,z)==negative.cell(x,y,z),"cells roundtrip");
            check(CachedChunk.top(round.cell(-3,62,-2))==.9375,"farmland top exact");
            check(CachedChunk.hazard(round.cell(-4,64,-2))==PathFinder.H_CLIMB,"ladder");
            check(CachedChunk.edges(round.cell(-5,64,-2))==PathFinder.E_PX,"plate edge");
            check(CachedChunk.hazard(round.cell(-6,64,-2))==PathFinder.H_DOOR,"door");
            check(CachedChunk.hazard(round.cell(-7,64,-2))==PathFinder.H_WATER && CachedChunk.hazard(round.cell(-8,64,-2))==PathFinder.H_LAVA,"liquids");
            check(CachedChunk.hazard(round.cell(-3,128,-2))==PathFinder.H_DANGER,"missing section unknown");
            check(!ChunkCacheStore.component("a:b").equals(ChunkCacheStore.component("a/b")),"sanitized collision hash");
            check(new ChunkCacheStore(tmp,"../world","../../dimension",1024).directory().normalize().startsWith(tmp),"path traversal blocked");
            check(new ChunkCacheStore(tmp,"example.org:25565","minecraft:the_nether",1024).load(Set.of(CachedChunk.key(-1,-1))).isEmpty(),"dimension isolation");
            check(new ChunkCacheStore(tmp,"other:25565","minecraft:overworld",1024).load(Set.of(CachedChunk.key(-1,-1))).isEmpty(),"server isolation");
            // A* crossing a virtually unloaded middle chunk.
            store.put(chunk(grid,1,0));
            var cached=store.load(Set.of(CachedChunk.key(1,0)));
            java.util.function.LongPredicate fresh=k -> (int)(k>>32)!=1;
            var mixed=new CachedPathWorld(grid,fresh,cached);
            var goals=PathFinder.pointGoal(42.5,64,2.5,0);
            var params=new PathFinder.Params();params.maxDist=64;
            var result=new PathFinder(mixed,params).find(2.5,64,2.5,true,goals);
            check(result.ok,"path through unloaded cached chunk: "+result.reason);
            var witnesses=mixed.witnesses(result,2.5,64,2.5);
            check(!witnesses.isEmpty(),"path labelled cached");
            check(!CachedPathWorld.mismatch(witnesses,grid,k -> true),"reloaded same data match");
            var unknown=new CachedPathWorld(grid,fresh,Map.of());
            check(!new PathFinder(unknown,params).find(2.5,64,2.5,true,goals).ok,"unloaded without cache is unsafe");
            grid.wall(22,2,22,2,64,67,1);
            check(!CachedPathWorld.mismatch(witnesses,grid,k -> false),"do not read unloaded world");
            check(CachedPathWorld.mismatch(witnesses,grid,k -> true),"fresh terrain mismatch detected");
            var updated=new CachedPathWorld(grid,k -> true,cached);
            var replan=new PathFinder(updated,params).find(2.5,64,2.5,true,goals);
            check(replan.ok,"replan around new wall");
            check(replan.points.size()>result.points.size(),"new path is detour, not stale straight line");
            check(updated.witnesses(replan,2.5,64,2.5).isEmpty(),"loaded terrain wins over cache");
            // Partial snapshot updates must retain other sections.
            Map<Integer,short[]> partial=Map.of(4,new short[4096]);store.put(new CachedChunk(1,0,partial));
            check(store.load(Set.of(CachedChunk.key(1,0))).get(CachedChunk.key(1,0)).sections().containsKey(3),"partial update retains lower section");
            // Global disk size cap, including different servers; deterministic incompressible-ish cells.
            var tiny=new ChunkCacheStore(tmp,"tiny","dimension",4096);
            for(int i=0;i<8;i++) {
                short[] cells=new short[4096];var rng=new Random(i);
                for(int j=0;j<4096;j++)cells[j]=CachedChunk.encode(rng.nextInt(25)/16.0,rng.nextInt(6),rng.nextInt(16));
                tiny.put(new CachedChunk(i*32,0,Map.of(0,cells)));
            }
            tiny.trim();long total;
            try(var files=Files.walk(tmp)) {total=files.filter(p -> p.toString().endsWith(".bin")).mapToLong(p -> {try{return Files.size(p);}catch(Exception e){throw new RuntimeException(e);}}).sum();}
            check(total<=4096,"global cap");
            store.put(negative);
            Files.write(store.directory().resolve("r.-1.-1.bin"),new byte[]{1,2,3});
            check(store.load(Set.of(CachedChunk.key(-1,-1))).isEmpty(),"corruption is unknown, not air");
            store.put(negative);check(store.load(Set.of(CachedChunk.key(-1,-1))).size()==1,"overwrite corrupted region");
            ChunkCacheStore.clear(tmp);
            try(var files=Files.walk(tmp)) {check(files.count()==1,"clear all worlds");}
            System.out.println("ChunkCacheCheck: OK ("+checks+" checks; cached path + mismatch detour)");
        } catch(Exception e) {throw new IllegalStateException(e);}
        finally { if(tmp!=null) try {ChunkCacheStore.clear(tmp);Files.deleteIfExists(tmp);}catch(Exception ignored){} }
    }
}
