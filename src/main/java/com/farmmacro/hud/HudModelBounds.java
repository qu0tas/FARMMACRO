package com.farmmacro.hud;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** GUI entity PiP states use absolute GUI coordinates, unlike ordinary posed HUD draws. */
record HudModelBounds(int left,int top,int right,int bottom,int size) {
    static HudModelBounds project(Matrix3x2fc pose,int left,int top,int right,int bottom,int size) {
        if(right<=left||bottom<=top||size<=0)throw new IllegalArgumentException("empty model bounds");
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        Vector2f point=new Vector2f();
        for(int x:new int[]{left,right})for(int y:new int[]{top,bottom}){
            pose.transformPosition(x,y,point);
            minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);
            maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);
        }
        double scale=Math.sqrt(Math.abs(pose.m00()*pose.m11()-pose.m01()*pose.m10()));
        if(!Float.isFinite(minX)||!Float.isFinite(minY)||!Float.isFinite(maxX)||!Float.isFinite(maxY)||!Double.isFinite(scale)||scale<=0)
            throw new IllegalArgumentException("invalid model transform");
        return new HudModelBounds((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX),(int)Math.ceil(maxY),Math.max(1,(int)Math.round(size*scale)));
    }
    float centerX(){return (left+right)/2f;}
    float centerY(){return (top+bottom)/2f;}
}
