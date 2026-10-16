package com.farmmacro.pest;
import java.util.*;
/** K008 opt-ins; unknown tool/type/result never silently falls back from Hunting to killing. */
public final class PestAssistSettings {
    public String mode="vacuum",lassoTypes="Fly,Cricket,Locust,Rat,Mosquito,Earthworm,Mite,Moth,Slug,Beetle,Firefly,Dragonfly,Praying Mantis,Lunar Moth,Field Mouse";
    public boolean route=true;
    public int routeVisualRevision=0;
    public boolean vacuumOthers,stun,tracers,notify,focus,manualSwap,traps,autoTraps,clearTraps,refillTraps,preMosquito,postPet;
    public String stunTypes="",bait="COMPOST",petAfter="";
    public int lassoRetries=2,lassoSeconds=25,stunTicks=3,baitAmount=16,trapSeconds=90;
    public List<TrapPoint> points=new ArrayList<>();
    public static final class TrapPoint {
        public int id,plot;public String world,dimension;public double x,y,z,tx,ty,tz;
        public boolean valid(){return id>=1&&id<=3&&plot>=0&&plot<=24&&world!=null&&dimension!=null&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Double.isFinite(tx)&&Double.isFinite(ty)&&Double.isFinite(tz)&&Math.abs(tx)<1024&&Math.abs(tz)<1024&&ty>-64&&ty<320&&Math.abs(x)<1024&&Math.abs(z)<1024&&y>-64&&y<320&&PestPlots.plotAt(tx,tz)==plot&&new net.minecraft.world.phys.Vec3(x,y,z).distanceTo(new net.minecraft.world.phys.Vec3(tx,ty,tz))<8;}
    }
    public void sanitize(){
        if(routeVisualRevision<1){route=true;routeVisualRevision=1;}
        if(!Set.of("vacuum","lasso","manual").contains(mode==null?"":mode))mode="vacuum";
        lassoTypes=types(lassoTypes);stunTypes=types(stunTypes);lassoRetries=cap(lassoRetries,1,4);lassoSeconds=cap(lassoSeconds,5,90);stunTicks=cap(stunTicks,1,6);
        if(!Set.of("COMPOST","PLANT_MATTER","DUNG","HONEY_JAR","TASTY_CHEESE","JELLY","MOONDEW").contains(bait==null?"":bait))bait="COMPOST";
        baitAmount=cap(baitAmount,1,64);trapSeconds=cap(trapSeconds,20,240);if(petAfter==null)petAfter="";petAfter=petAfter.replaceAll("[^a-zA-Z '-]","").trim();if(petAfter.length()>40)petAfter=petAfter.substring(0,40);
        if(points==null)points=new ArrayList<>();points.removeIf(p->p==null||!p.valid());var ids=new HashSet<Integer>();points.removeIf(p->!ids.add(p.id));if(points.size()>3)points=new ArrayList<>(points.subList(0,3));
    }
    private static int cap(int n,int a,int b){return Math.max(a,Math.min(n,b));}
    public static String types(String text){var out=new LinkedHashSet<String>();if(text!=null)for(String t:text.split("[,;]+")){String type=PestItems.type(t);if(type!=null)out.add(type);}return String.join(",",out);}
    public static boolean contains(String list,String type){return type!=null&&Arrays.stream((list==null?"":list).split(",")).anyMatch(type::equals);}
}
