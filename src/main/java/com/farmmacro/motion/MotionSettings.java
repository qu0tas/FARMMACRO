package com.farmmacro.motion;
import java.util.List;
/** Separate backend; no legacy Humanizer settings are overwritten. Milliseconds unless stated. */
public final class MotionSettings {
 public static final List<String> PRESETS=List.of("normal","safe","efficient");
 public SharedCameraSettings camera=new SharedCameraSettings();
 public String backend="kaelix",preset="normal",curve="MANAGER";
 public int rotationMs=100,laneMinMs=0,laneMaxMs=500;
 public double msPerDegree=2,inFactor=2,outFactor=2,noiseMinPct=2,noiseMaxPct=6;
 // Own navigation adapter setting, not an upstream universal navigation preset.
 public double trackingMs=150,yawRange=0,pitchRange=0;
 public boolean easeIn=true,easeOut=true,boundaryPauses=true,smoothBindings=true;
 public double bindingSpeed=120;
 public int cornerMinMs=80,cornerMaxMs=240,floorMinMs=150,floorMaxMs=350;
 public boolean aether(){return "aether".equals(backend);}
 public void preset(String name){
  if(name==null||!PRESETS.contains(name))return;
  preset=name;rotationMs=100;msPerDegree="efficient".equals(name)?6:2;
  easeIn=easeOut=true;inFactor=outFactor=2;noiseMinPct=2;noiseMaxPct=6;
  laneMinMs=0;laneMaxMs="normal".equals(name)?500:1000;yawRange=pitchRange=0;curve="MANAGER";
 }
 public MotionSettings copy(){
  MotionSettings s=new MotionSettings();s.camera=camera==null?new SharedCameraSettings():camera.copy();s.backend=backend;s.preset=preset;s.curve=curve;s.rotationMs=rotationMs;
  s.laneMinMs=laneMinMs;s.laneMaxMs=laneMaxMs;s.msPerDegree=msPerDegree;s.inFactor=inFactor;s.outFactor=outFactor;
  s.noiseMinPct=noiseMinPct;s.noiseMaxPct=noiseMaxPct;s.trackingMs=trackingMs;s.yawRange=yawRange;s.pitchRange=pitchRange;
  s.easeIn=easeIn;s.easeOut=easeOut;s.boundaryPauses=boundaryPauses;s.cornerMinMs=cornerMinMs;s.cornerMaxMs=cornerMaxMs;
  s.floorMinMs=floorMinMs;s.floorMaxMs=floorMaxMs;s.smoothBindings=smoothBindings;s.bindingSpeed=bindingSpeed;s.sanitize();return s;
 }
 public void sanitize(){
  if(camera==null)camera=new SharedCameraSettings();camera.sanitize();
  if(!"aether".equals(backend))backend="kaelix";
  if(!PRESETS.contains(preset==null?"":preset))preset="normal";
  if(curve==null)curve="MANAGER";
  if(!"MANAGER".equals(curve))try{AetherEasing.valueOf(curve);}catch(IllegalArgumentException e){curve="MANAGER";}
  bindingSpeed=finite(bindingSpeed,20,360,120);
  rotationMs=bound(rotationMs,0,2000);laneMinMs=bound(laneMinMs,0,2000);laneMaxMs=bound(laneMaxMs,laneMinMs,2000);
  msPerDegree=finite(msPerDegree,0,20,2);inFactor=finite(inFactor,1,5,2);outFactor=finite(outFactor,1,5,2);
  noiseMinPct=finite(noiseMinPct,0,15,2);noiseMaxPct=finite(noiseMaxPct,noiseMinPct,15,6);
  trackingMs=finite(trackingMs,30,1000,150);yawRange=finite(yawRange,0,2,0);pitchRange=finite(pitchRange,0,10,0);
  cornerMinMs=bound(cornerMinMs,0,2000);cornerMaxMs=bound(cornerMaxMs,cornerMinMs,2000);
  floorMinMs=bound(floorMinMs,0,2000);floorMaxMs=bound(floorMaxMs,floorMinMs,2000);
 }
 private static int bound(int n,int lo,int hi){return Math.max(lo,Math.min(hi,n));}
 private static double finite(double n,double lo,double hi,double fallback){return Double.isFinite(n)?Math.max(lo,Math.min(hi,n)):Math.max(lo,Math.min(hi,fallback));}
}
