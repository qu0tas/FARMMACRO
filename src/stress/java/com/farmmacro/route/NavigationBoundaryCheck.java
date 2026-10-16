package com.farmmacro.route;
/** No Minecraft physics: boundary/negative checks for the observed navigation handoffs. */
public final class NavigationBoundaryCheck {
 private static void check(boolean v,String why){if(!v)throw new IllegalStateException("NavigationBoundaryCheck: "+why);}
 private static final class World implements PathFinder.World {
  boolean contact=true,support=true,body=true;int danger;
  public double top(int x,int y,int z){return support&&y==63?1:-1;}
  public int hazard(int x,int y,int z){if(danger!=0&&x==3&&y==64)return danger;return contact&&x==4&&z==0&&y>=64&&y<=68?PathFinder.H_CLIMB:PathFinder.H_NONE;}
  public boolean climbClear(double x,double y,double z,double r,double h){return body;}
 }
 public static void main(){
  var w=new World();var out=new WalkCore.Out();var target=new RoutePoint(4.5,64,.5);
  check(new NavigationClimb().approach(w,0,target,3.5,64,.5,-90,out)&&out.forward&&!out.jump&&!out.sprint,"bounded ladder approach");
  w.contact=false;check(!new NavigationClimb().approach(w,0,target,3.5,64,.5,-90,out),"requires observed climb");w.contact=true;
  w.support=false;check(!new NavigationClimb().approach(w,0,target,3.5,64,.5,-90,out),"no void step");w.support=true;
  w.body=false;check(!new NavigationClimb().approach(w,0,target,3.5,64,.5,-90,out),"body obstruction stops");w.body=true;
  for(int hazard:new int[]{PathFinder.H_WATER,PathFinder.H_LAVA,PathFinder.H_DANGER,PathFinder.H_DOOR}){w.danger=hazard;check(!new NavigationClimb().approach(w,0,target,3.5,64,.5,-90,out),"hazard veto "+hazard);}w.danger=0;
  check(!new NavigationClimb().approach(w,0,target,2,64,.5,-90,out),"not distant direct walking");
  check(!new NavigationClimb().approach(w,0,target,3.5,63,.5,-90,out),"no unsupported level change");
  var climb=new NavigationClimb();boolean stopped=false;
  for(int i=0;i<90;i++)if(!climb.approach(w,0,target,3.5,64,.5,-90,out)){stopped=true;break;}
  check(stopped,"bounded no-progress timeout");
  for(double distance:new double[]{.4,.55,.7,1.0}){
   new FlightGuide().tick(0,64,0,0,0,0,-90,distance,64,0,out);
   check(out.forward&&!out.back,"arrival intent not swallowed at distance="+distance);
  }
  w.support=false;
  check(new FlightArrival().tick(w,true,false,true,4.5,64,.5,0,0,0,-90,target,out)==FlightArrival.Result.FAIL,"cannot finish unsupported landing");
  System.out.println("NavigationBoundaryCheck: OK (near-target flight intent; ladder contact/support/body/hazards/distance/height/timeout; unsupported landing veto)");
 }
 private NavigationBoundaryCheck(){}
}
