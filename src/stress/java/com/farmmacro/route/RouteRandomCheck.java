package com.farmmacro.route;

import com.google.gson.Gson;
import java.util.List;

/** Pure seeded generation, migration, lane bounds, fixed variants, multi-route weights, and core timings. */
public final class RouteRandomCheck {
    private static int checks;
    private static void check(boolean ok,String name) { if(!ok) throw new IllegalStateException("RouteRandomCheck: "+name);checks++; }
    private static final Gson JSON=new Gson();
    public static void main() {
        PointRoute ideal=WalkSim.route(TestRoutes.rows(0,0,31,15,1,64));
        ideal.name="ideal";ideal.world="server";ideal.dimension="minecraft:overworld";
        var s=ideal.settings.routeRandom;s.mode="each";s.seed=812;s.chance=new RouteRandomSettings.Range(100,100);
        String before=JSON.toJson(ideal);
        var r1=RouteRandomizer.generate(ideal,s,13);var r2=RouteRandomizer.generate(ideal,s,13);
        check(JSON.toJson(r1).equals(JSON.toJson(r2)),"same seed exact variant");
        check(!JSON.toJson(r1).equals(JSON.toJson(RouteRandomizer.generate(ideal,s,14))),"different seed");
        for(int seed=0;seed<500;seed++) {
            var r=RouteRandomizer.generate(ideal,s,seed);
            for(int i=0;i<r.points.size();i++) {
                var p=r.points.get(i);var q=ideal.points.get(i);
                check(Math.floor(p.x)==Math.floor(q.x)&&Math.floor(p.z)==Math.floor(q.z),"inside block");
                check(Math.hypot(p.x-q.x,p.z-q.z)<=.150001,"radius");
                check(RouteRandomizer.safeOffset(ideal.points,i,p.x,p.z,.15),"row strip/neighbour check");
                check(p.pauseTicks>=0 && p.pauseTicks<=8,"pause range");
            }
            var walk=WalkSim.run(r,90,30,true,-1,-1,30000);
            check(walk.panic()==null && walk.reached()==r.points.size(),"walk "+seed+": "+walk.panic());
        }
        check(JSON.toJson(ideal).equals(before),"ideal not mutated");
        check(r1.points.getFirst().x==ideal.points.getFirst().x,"start anchored");
        var drop=ideal.copy();drop.points.get(3).drop=true;drop.points.get(3).dropDir="auto";drop.points.get(3).dropDepth=4;
        var dr=RouteRandomizer.generate(drop,s,19);
        for(int i=2;i<=4;i++) check(dr.points.get(i).x==drop.points.get(i).x && dr.points.get(i).z==drop.points.get(i).z,"drop and direction anchored");
        check(dr.points.get(3).y==drop.points.get(3).y && dr.points.get(3).dropDepth==4,"drop data");
        var p=ideal.points.get(2);p.rndPause=new RouteRandomSettings.Range(22,22);p.rndChance=new RouteRandomSettings.Range(100,100);p.rndOffset=new RouteRandomSettings.Range(0,0);
        var local=RouteRandomizer.generate(ideal,s,3).points.get(2);
        check(local.pauseTicks==22 && local.x==p.x && local.z==p.z,"local overrides, zero disables");
        var copy=ideal.copy();copy.points.get(2).rndPause.min=1;
        check(p.rndPause.min==22,"deep copy local");
        var old=JSON.fromJson("{\"version\":5,\"points\":[{\"x\":0.5,\"y\":64,\"z\":0.5}]}",PointRoute.class);old.sanitize();
        check(old.settings.routeRandom.mode.equals("off") && old.points.getFirst().rndPause==null,"legacy inherits disabled");
        var round=JSON.fromJson(JSON.toJson(ideal),PointRoute.class);round.sanitize();
        check(round.points.get(2).rndPause.min==22,"JSON local roundtrip");
        s.mode="fixed";s.variants=4;
        var a=new RouteVariants.Session(ideal,List.of(),List.of());var b=new RouteVariants.Session(ideal,List.of(),List.of());
        boolean[] seen=new boolean[4];
        for(int lap=0;lap<80;lap++) {
            var ar=a.next();var br=b.next();
            check(a.number>=1 && a.number<=4,"fixed number bounds");seen[a.number-1]=true;
            check(JSON.toJson(ar).equals(JSON.toJson(br)),"session deterministic");
            check(JSON.toJson(ar).equals(JSON.toJson(RouteRandomizer.generate(ideal,s,RouteRandomizer.variantSeed(s.seed,a.number-1)))),"fixed pool repeats exactly");
        }
        for(boolean yes:seen)check(yes,"all pool entries chosen");
        var alternative=ideal.copy();alternative.name="alternate";alternative.points.removeLast();
        var far=ideal.copy();far.name="far";far.points.getFirst().x+=3;
        check(!RouteVariants.compatible(ideal,far),"start beyond 2 blocked");
        var otherWorld=ideal.copy();otherWorld.world="other";check(!RouteVariants.compatible(ideal,otherWorld),"world isolated");
        var weighted=new RouteVariants.Session(ideal,List.of(alternative,far),List.of(9.0,1000.0));
        int altCount=0;
        for(int i=0;i<1000;i++) { var next=weighted.next();if("alternate".equals(next.name))altCount++;check(!"far".equals(next.name),"incompatible skipped"); }
        check(altCount>850 && altCount<950,"weighted choice: "+altCount);
        var zero=new RouteVariants.Session(ideal,List.of(alternative),List.of(0.0));
        for(int i=0;i<10;i++)check(zero.next().name.equals("ideal"),"zero weight");
        // Concrete pre-drop pause and edge release in WalkCore, with no Minecraft.
        PointRoute descent=new PointRoute();
        var top=new RoutePoint(.5,64,.5);top.drop=true;top.dropDir="+x";top.beforeDropTicks=7;top.edgeReleaseTicks=3;top.landPauseTicks=4;
        descent.points.add(top);descent.points.add(new RoutePoint(4.5,60,.5));
        WalkCore core=new WalkCore();WalkCore.Out out=new WalkCore.Out();core.start(descent,0,30);
        int paused=0;
        for(int t=0;t<7;t++) {core.tick(.5,64,.5,true,90,.3,false,30,false,4,true,out);if(!out.forward&&!out.back&&!out.left&&!out.right)paused++;}
        check(paused==7,"before drop pause exactly seven ticks");
        core.tick(.5,64,.5,true,90,.3,false,30,false,4,true,out);check(out.forward||out.back||out.left||out.right,"move after pause");
        core.tick(1.1,63.7,.5,false,90,.3,false,30,false,4,true,out);
        for(int t=0;t<3;t++) {core.tick(1.1,63.5-t*.2,.5,false,90,.3,false,30,false,4,true,out);check(out.forward||out.back||out.left||out.right,"edge delay");}
        core.tick(1.1,62.5,.5,false,90,.3,false,30,false,4,true,out);check(!out.forward&&!out.back&&!out.left&&!out.right,"edge released");
        core.tick(1.1,60,.5,true,90,.3,false,30,false,4,true,out);check(out.panic==null,"landing correct floor");
        PointRoute endPause=new PointRoute();endPause.points.add(new RoutePoint(.5,64,.5));
        endPause.points.getFirst().pauseTicks=5;
        WalkCore terminal=new WalkCore();terminal.start(endPause,0,30);
        for(int i=0;i<5;i++) { terminal.tick(.5,.5,90,.3,false,30,false,4,out);check(!out.forward&&!out.back&&!out.left&&!out.right,"terminal pause released keys"); }
        check(terminal.done(),"terminal pause completes after five ticks");
        var sparse=new RouteRandomSettings();sparse.offset=null;sparse.pause=null;sparse.alternatives=null;
        check(RouteRandomizer.generate(ideal,sparse,1).points.size()==ideal.points.size(),"sparse settings copied safely");
        System.out.println("RouteRandomCheck: OK ("+checks+" checks, 500 seeded route simulations)");
    }
}
