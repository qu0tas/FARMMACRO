package com.farmmacro.release;
import com.farmmacro.route.*;
import java.util.List;
/** Small route-only fixtures, no client launch. */
public final class StartHereQuickCheck {
 private static void ok(boolean v){if(!v)throw new IllegalStateException("StartHereQuickCheck");}
 public static void main(){
  var pts=List.of(new RoutePoint(0,64,0),new RoutePoint(30,64,0),new RoutePoint(30,64,20));
  var h=RouteStartHere.find(pts,15,64,0,3);ok(h!=null&&h.seg()==0&&h.target()==1&&h.t()==.5);
  h=RouteStartHere.find(pts,30,64,0,3);ok(h!=null&&h.seg()==1&&h.target()==2);
  ok(RouteStartHere.find(pts,15,64,3,3)!=null);ok(RouteStartHere.find(pts,15,64,3.001,3)==null);
  ok(RouteStartHere.find(pts,15,67,0,16)==null);ok(RouteStartHere.find(pts,Double.NaN,64,0,3)==null);
  ok(RouteStartHere.find(pts,15,Double.NaN,0,3)==null);ok(RouteStartHere.find(pts,15,64,0,Double.POSITIVE_INFINITY)==null);
  ok(RouteStartHere.find(List.of(pts.getFirst()),0,64,0,3)==null);
  var floors=List.of(new RoutePoint(0,64,0),new RoutePoint(30,64,0),new RoutePoint(30,68,20),new RoutePoint(30,68,0),new RoutePoint(0,68,0));
  h=RouteStartHere.find(floors,15,64,0,3);ok(h!=null&&h.seg()==0);
  h=RouteStartHere.find(floors,15,68,0,3);ok(h!=null&&h.seg()==3);
  var route=new PointRoute();route.points.addAll(pts);var core=new WalkCore();core.start(route,1,40);ok(core.segment()==0&&core.target()==1);
  System.out.println("StartHereQuickCheck: PASS (mid-segment, junction direction, floor, radius, invalid input, core index; no game)");
 }
 private StartHereQuickCheck(){}
}
