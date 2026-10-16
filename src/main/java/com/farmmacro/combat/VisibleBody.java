package com.farmmacro.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Bounded body probes shared by acquisition, frame aiming and real-view shot validation.
 * Points stay inside the real hitbox; armor-stand labels are never aim geometry. */
public final class VisibleBody {
    public record Sample(Vec3 point,int part) {}
    private Mob owner;
    private int part;
    private long preferUntil;

    public void reset(){owner=null;part=0;preferUntil=0;}
    public boolean partial(){return part!=0;}
    public String status(){return partial()?"открытая часть тела":"центр тела";}

    /** Retain a still-open body part briefly instead of flickering around a cover edge. */
    public Vec3 point(Minecraft mc,Mob mob,Vec3 eye,Vec3 center){
        if(owner!=mob){reset();owner=mob;}
        long now=System.nanoTime();
        Sample sample=find(mc,mob,eye,center,part,now<preferUntil);
        if(sample==null)return null;
        if(part!=sample.part()){
            part=sample.part();
            preferUntil=part==0?0:now+250_000_000L;
        }
        return sample.point();
    }

    public static boolean valid(Minecraft mc,Mob mob){
        return mc.player!=null&&mc.level!=null&&mob!=null&&mob.level()==mc.level
            &&mob.isAlive()&&!mob.isDeadOrDying()&&!mob.isRemoved()&&!mob.isInvisibleTo(mc.player);
    }

    /** At most nine rays; prefer the center when no selected-part hold is active. */
    public static Sample find(Minecraft mc,Mob mob,Vec3 eye,Vec3 center){
        return find(mc,mob,eye,center,0,false);
    }

    private static Sample find(Minecraft mc,Mob mob,Vec3 eye,Vec3 center,int preferred,boolean hold){
        if(!valid(mc,mob))return null;
        var box=mob.getBoundingBox();
        double width=box.maxX-box.minX,depth=box.maxZ-box.minZ,height=box.maxY-box.minY;
        if(width<=0||depth<=0||height<=0)return null;
        double dx=center.x-eye.x,dz=center.z-eye.z,length=Math.hypot(dx,dz);
        double lx=length>.001?-dz/length:1,lz=length>.001?dx/length:0;
        // Intersect screen-horizontal direction with the box, then inset by 22%.
        double reach=Math.min(Math.abs(lx)>.001?width*.39/Math.abs(lx):Double.POSITIVE_INFINITY,
            Math.abs(lz)>.001?depth*.39/Math.abs(lz):Double.POSITIVE_INFINITY);
        Vec3 side=new Vec3(lx*reach,0,lz*reach);
        int first=hold?preferred:0,second=hold?0:preferred;
        Vec3 point=bodyPoint(center,side,height,first);
        if(ShotPath.clear(mc,eye,point))return new Sample(point,first);
        if(second!=first){
            point=bodyPoint(center,side,height,second);
            if(ShotPath.clear(mc,eye,point))return new Sample(point,second);
        }
        for(int i=1;i<9;i++){
            if(i==first||i==second)continue;
            point=bodyPoint(center,side,height,i);
            if(ShotPath.clear(mc,eye,point))return new Sample(point,i);
        }
        return null;
    }

    private static Vec3 bodyPoint(Vec3 center,Vec3 side,double height,int part){
        return switch(part){
            case 1 -> center.add(side);
            case 2 -> center.subtract(side);
            case 3 -> center.add(0,-height*.28,0);
            case 4 -> center.add(0,height*.28,0);
            case 5 -> center.add(side).add(0,-height*.28,0);
            case 6 -> center.subtract(side).add(0,-height*.28,0);
            case 7 -> center.add(side).add(0,height*.28,0);
            case 8 -> center.subtract(side).add(0,height*.28,0);
            default -> center;
        };
    }
}