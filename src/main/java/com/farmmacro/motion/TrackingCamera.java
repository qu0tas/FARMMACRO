package com.farmmacro.motion;
import com.farmmacro.combat.*;
import com.farmmacro.config.ModConfig;
import com.farmmacro.camera.TrackingTurn;
import com.farmmacro.camera.MouseTurn;
import java.util.concurrent.ThreadLocalRandom;
/** Single Combat camera integrator. Navigation owns the camera exclusively while walking. */
public class TrackingCamera {
    private final FastAimTurn precise=new FastAimTurn();
    private AetherTurn aether;
    private final HumanAimTurn human=new HumanAimTurn();
    private Object owner;private String mode="";
    private double multiplier=1,speed=0,driftClock,settledFor;
    private double appliedYaw=Double.NaN,appliedPitch=Double.NaN;
    private long lastFrame;
    private boolean acquiring,suspended;
    private String phase="ожидание";
    public void reset(){precise.reset();human.reset();aether=null;owner=null;lastFrame=0;speed=0;driftClock=settledFor=0;appliedYaw=appliedPitch=Double.NaN;acquiring=false;suspended=false;phase="ожидание";}
    /** Stop once on occlusion; keep the target lock outside this controller and re-perceive on return. */
    public void suspend(){if(!suspended){reset();suspended=true;}phase="перекрытие — ожидание";}
    public String status(){return "Камера: "+("aether".equals(mode)?"Aether · ":"")+phase+" · предел "+Math.round(speed)+"°/с";}
    public boolean perceived(Object target){return owner==target&&(!"human".equals(mode)||human.perceived());}
    /** Actual post-Entity.turn view, not requested/rounded delta: detect external camera discontinuities. */
    public void applied(double yaw,double pitch){appliedYaw=yaw;appliedPitch=pitch;}
    public FastAimTurn.Delta frame(Object nextOwner,boolean search,boolean priority,double yaw,double pitch,double goalYaw,double goalPitch,
            double sensitivity,double baseSpeed,double yawMargin,double pitchMargin){
        if(nextOwner==null||!Double.isFinite(yaw)||!Double.isFinite(pitch)||!Double.isFinite(goalYaw)||!Double.isFinite(goalPitch)
            ||!Double.isFinite(sensitivity)||!Double.isFinite(baseSpeed)||!Double.isFinite(yawMargin)||!Double.isFinite(pitchMargin)){
            reset();return new FastAimTurn.Delta(0,0);
        }
        var c=ModConfig.INSTANCE;String nextMode=c.motion.aether()?"aether":search?"precise":c.motion.camera.mode(c.motion);
        suspended=false;
        long now=System.nanoTime();
        double elapsed=lastFrame==0?1/60.0:Math.max(0,(now-lastFrame)/1e9);
        double externalTolerance=Math.max(.35,MouseTurn.stepDeg(Math.clamp(sensitivity,0,1))*2);
        boolean external=Double.isFinite(appliedYaw)&&(Math.abs(TrackingTurn.wrap(yaw-appliedYaw))>externalTolerance
            ||(c.aimPitch||c.aimScytheOnly)&&Math.abs(pitch-appliedPitch)>externalTolerance);
        // Never reuse a previous owner's velocity after a preset/server/external view discontinuity.
        if(elapsed>.25||external){reset();elapsed=1/60.0;}
        double dt=Math.min(elapsed,.05);lastFrame=now;
        if(!nextMode.equals(mode)){precise.reset();human.reset();aether=null;mode=nextMode;owner=null;speed=0;}
        var r=ThreadLocalRandom.current();boolean changed=owner!=nextOwner;
        if(changed){
            aether=null;
            owner=nextOwner;driftClock=settledFor=0;acquiring=true;
            human.handoff(TrackingTurn.wrap(goalYaw-yaw));
            human.retarget(yaw,pitch,search?.25:c.motion.camera.aimHumanStrength/100.0,c.motion.camera.aimReactionMinMs/1000.0,c.motion.camera.aimReactionMaxMs/1000.0);
        }
        boolean humanMode="human".equals(mode),waiting=humanMode&&!human.perceived();
        // Phase transitions use the same delayed perception as motion, never the newest target position.
        double error=humanMode?human.error(yaw,pitch):Math.hypot(TrackingTurn.wrap(goalYaw-yaw),goalPitch-pitch);
        double settled=Math.max(.45,Math.hypot(yawMargin,pitchMargin)*1.5);
        if(!search&&!waiting){
            if(acquiring){
                settledFor=error<=settled?settledFor+elapsed:0;
                if(settledFor>=.08){acquiring=false;settledFor=0;}
            }else if(error>Math.max(10,settled*3)){acquiring=true;settledFor=0;}
        }
        double brakingZone=Math.max(6,Math.min(24,baseSpeed*.035));
        phase=search?"осмотр":waiting?"реакция":!acquiring?"сопровождение":error<=settled||humanMode&&human.correcting()?"доводка":
            error<=brakingZone?"торможение":priority?"разворот к Special":"основной перевод";
        driftClock-=elapsed;
        if(driftClock<=0){
            double spread=c.motion.camera.aimSpeedVariationPct/100.0;
            multiplier=spread==0?1:r.nextDouble(1-spread,1+spread);driftClock=r.nextDouble(.55,1.5);
        }
        double phaseSpeed=search?baseSpeed:baseSpeed*(acquiring?c.motion.camera.aimAcquireSpeedPct:c.motion.camera.aimTrackSpeedPct)/100.0;
        if(!search&&priority&&acquiring)phaseSpeed=Math.max(phaseSpeed,c.motion.camera.aimSpecialTurnSpeed);
        double wanted=Math.clamp(phaseSpeed*multiplier,20,1440);
        if(speed==0)speed=wanted;else speed+=(wanted-speed)*(1-Math.exp(-(acquiring&&!search?14:5)*dt));
        if("aether".equals(mode)){
            if(aether==null)aether=new AetherTurn(yaw,pitch,goalYaw,goalPitch,true,c.motion,java.util.concurrent.ThreadLocalRandom.current());
            var d=aether.frame(yaw,pitch,goalYaw,goalPitch,sensitivity,speed,dt);return new FastAimTurn.Delta(d.yaw(),d.pitch());
        }
        if(humanMode)return human.frame(yaw,pitch,goalYaw,goalPitch,sensitivity,speed,elapsed,
            search?.25:c.motion.camera.aimHumanStrength/100.0,yawMargin,pitchMargin,c.motion.camera.aimReactionMinMs/1000.0,c.motion.camera.aimReactionMaxMs/1000.0);
        return precise.frame(yaw,pitch,goalYaw,goalPitch,sensitivity,speed,dt);
    }
}
