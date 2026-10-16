package com.farmmacro.combat;
import com.farmmacro.FarmMacroMod;
import com.farmmacro.camera.*;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.*;
import com.farmmacro.pest.PestHunter;
import com.farmmacro.route.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Explicit key-controlled aim with optional guarded Glacial Scythe use. Acquire by distance and camera-turn cost, keep the same target until death
 * or invalidation. No melee attacks or slot changes; movement is a separate explicit opt-in. */
public final class AimAssist {
    public static final AimAssist INSTANCE=new AimAssist();
    private final com.farmmacro.motion.TrackingCamera camera=new com.farmmacro.motion.TrackingCamera();
    private final AutoScytheFire autoFire=new AutoScytheFire();
    private final ScytheAreaAim areaAim=new ScytheAreaAim();
    private final AimJitter jitter=new AimJitter();
    private final IdleLook idleLook=new IdleLook();
    private final VisibleBody body=new VisibleBody();
    private final TargetDiagnostics diagnostics=new TargetDiagnostics();
    /** Tick-local reuse: scan and selection must agree, but shots/frame aim recheck fresh geometry. */
    private final Map<Mob,VisibleBody.Sample> exposure=new IdentityHashMap<>();
    private final Map<Mob,Vec3> highlightPoints=new IdentityHashMap<>();
    private boolean searchAllowed;
    private String localStopReason="";
    private int blockedCandidates;
    private static final int BODY_BUDGET=128;private long bodyCursor;private String technicalGate="ACQUIRING";
    private final Set<Mob> budgetSkipped=Collections.newSetFromMap(new IdentityHashMap<>());
    public String technicalState(){return !ModConfig.INSTANCE.aimAssistEnabled?"DISABLED":com.farmmacro.panic.PanicHandoff.pending()?"HANDOFF":active&&target!=null?"TRACKING":requested()?technicalGate:localStopReason.isEmpty()?"WAIT_KEY":"PAUSED_LOCAL";}
    public String safeTargetDiagnostics(){return diagnostics.safeDetails();}
    public String searchStatus(){return manualMode()?"Осмотр отключён: только наведение":idleLook.status();}
    public String cameraStatus(){if(manualMode())return "Motion: точный центр · "+Math.round(ModConfig.INSTANCE.motion.camera.aimTurnSpeed)+"°/с · без рандома";return camera.status()+(active&&target!=null?" · "+body.status():"");}
    public String swayStatus(){return manualMode()?"Движение вручную":CombatSway.INSTANCE.status();}
    public String targetDiagnostics(){return diagnostics.details();}
    /** Deliberately omits chat, nicknames, coordinates, server address, custom profile names and filters. */
    public String diagnosticReport(){
        var c=ModConfig.INSTANCE;
        String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("farmmacro")
            .map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        return "Kaelix "+version+"\nCombat profile: "+("zealots".equals(c.combatProfileId)?"zealots":"custom")
            +"\nTracking: "+c.motion.camera.mode(c.motion)+"; key: "+c.aimKeyMode
            +"; autoFire: "+c.aimAutoFire+"; loadedRange: "+c.aimLoadedRange
            +"; local wait: "+c.aimChainRearWaitMs+"ms"
            +"\nFilters: selectedOnly="+c.aimSelectedOnly+"; zealotsOnly="+c.aimZealotsOnly
            +"; frontOnly="+c.aimFrontOnly+"; FOV="+c.aimFov+"; range="+c.aimRange
            +"\n"+camera.status()+"\n"+diagnostics.details();
    }
    private final TargetEngagements engagements=new TargetEngagements();
    private final SpecialZealotHunt special=SpecialZealotHunt.INSTANCE;
    public static boolean manualMode(){return ModConfig.INSTANCE.aimManualOnly;}
    public boolean keyActive(){return !PanicHandoff.pending()&&requested();}
    public boolean contextFree(Minecraft mc){return free(mc);}
    public String specialStatus(){return manualMode()?"Поиск Special отключён":special.status();}
    public String fireStatus(){if(manualMode())return "Только наведение: атаки и выстрелы вручную";return autoFire.status()+"; "+engagements.summary(System.nanoTime());}
    private void resetTracking(){camera.reset();areaAim.reset();jitter.reset();body.reset();idleLook.reset();}
    private void pauseOccluded(){active=false;lastFrame=0;camera.suspend();}
    private ClientLevel world;
    private Mob target;
    private boolean lastManual;
    private final EggSacAim eggSacs=new EggSacAim();
    private String targetName="",status="Aim assist выключен",lastMode="hold";
    private boolean active,mouseSeen,latched,wasDown,holdBlocked;
    private double mouseX,mouseY;
    private long manualUntil,lastFrame;
    private int ticks,occludedTicks,scanTick=-1;
    private Vec3 lastPlayer,chainPoint;
    private Mob chainMob;
    private long chainAt,chainRearUntil;
    private List<MobNames.Named> candidates=List.of();
    private List<MobNames.Named> visible=List.of();
    public List<MobNames.Named> visibleMobs(){return visible;}
    /** Same live eligibility and scan opening as acquisition; highlighting does not imply weapon reach. */
    public TargetDiagnostics.Reason highlightReason(MobNames.Named named){
        Minecraft mc=Minecraft.getInstance();var c=ModConfig.INSTANCE;Mob mob=named.mob();
        if(!VisibleBody.valid(mc,mob))return TargetDiagnostics.Reason.INVALID;
        if(manualMode()){
            if(!manualAllowed(named.name(),c))return TargetDiagnostics.Reason.FILTER;
            if(mc.player.getEyePosition().distanceTo(mob.getBoundingBox().getCenter())>c.aimManualRange)return TargetDiagnostics.Reason.RANGE;
            if(selected(mob))return TargetDiagnostics.Reason.CHOSEN;
            return highlightPoints.containsKey(mob)?TargetDiagnostics.Reason.READY:TargetDiagnostics.Reason.COVER;
        }
        if(!allowed(mob,named.name(),c))return TargetDiagnostics.Reason.FILTER;
        if(!inRange(mc,mob,c))return TargetDiagnostics.Reason.RANGE;
        if(selected(mob))return TargetDiagnostics.Reason.CHOSEN;
        if(special.active()&&special.focus()!=mob)return TargetDiagnostics.Reason.SPECIAL;
        if(c.aimNextAfterShot&&engagements.waiting(mob,System.nanoTime()))return TargetDiagnostics.Reason.WAITING;
        Vec3 point=highlightPoints.get(mob);
        if(point==null)return TargetDiagnostics.Reason.COVER;
        return withinFov(mc,point,c)?TargetDiagnostics.Reason.READY:TargetDiagnostics.Reason.FOV;
    }
    private boolean withinFov(Minecraft mc,Vec3 point,ModConfig c){
        if(!c.aimFrontOnly||special.active())return true;
        float[] a=angles(mc.player.getEyePosition(),point,mc.player.getYRot());
        return Math.hypot(Math.abs(WalkCore.wrap(a[0]-mc.player.getYRot())),(c.aimPitch||c.aimScytheOnly)?a[1]-mc.player.getXRot():0)<=c.aimFov;
    }
    public boolean selected(Mob mob){return active&&target==mob;}
    public static boolean zealot(String name){return name.equalsIgnoreCase("Zealot")||name.equalsIgnoreCase("Zealot Bruiser")||name.equalsIgnoreCase("Special Zealot");}
    private boolean inRange(Minecraft mc,Mob mob,ModConfig c){return special.active()&&special.focus()==mob||c.aimLoadedRange||mc.player.getEyePosition().distanceTo(mob.getBoundingBox().getCenter())<=c.aimRange;}
    private VisibleBody.Sample exposed(Minecraft mc,Mob mob){
        if(!VisibleBody.valid(mc,mob))return null;
        if(exposure.containsKey(mob))return exposure.get(mob);
        if(exposure.size()>=BODY_BUDGET&&mob!=target&&mob!=special.focus()){
            if(budgetSkipped.add(mob))com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.BODY_LIMIT);
            return null;
        }
        com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.BODY_PROBES);
        var sample=VisibleBody.find(mc,mob,mc.player.getEyePosition(),mob.getBoundingBox().getCenter());
        exposure.put(mob,sample);return sample;
    }
    private boolean shootable(Minecraft mc,Mob mob){return exposed(mc,mob)!=null;}
    private void scan(Minecraft mc,ModConfig c,boolean force){
        if(mc.player==null||mc.level==null||!c.aimAssistEnabled){visible=List.of();candidates=List.of();highlightPoints.clear();blockedCandidates=0;return;}
        Mob focus=special.focus();
        if(!force&&!requested()&&!c.aimHighlight){visible=List.of();candidates=List.of();highlightPoints.clear();blockedCandidates=0;return;}
        if(force||ticks%(!requested()?4:2)==0||focus!=null&&focus!=target){
            com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.COMBAT_SCANS);
            List<MobNames.Named> fresh=new ArrayList<>(MobNames.nearby(mc,c.aimLoadedRange?Double.POSITIVE_INFINITY:special.active()?Math.max(24,c.aimRange):c.aimRange));
            // A title-confirmed focus must not wait for an older two-tick name/visibility snapshot.
            if(VisibleBody.valid(mc,focus)&&fresh.stream().noneMatch(n->n.mob()==focus))fresh.add(new MobNames.Named(focus,"Special Zealot"));
            fresh.sort(Comparator.comparingDouble(n->n.mob()==focus?-1:mc.player.distanceToSqr(n.mob())));
            if(fresh.size()>BODY_BUDGET){int start=32,n=fresh.size()-start,offset=(int)(bodyCursor%n);bodyCursor+=96;
                // Rotating the tail keeps blocked near mobs from starving farther loaded bodies forever.
                Collections.rotate(fresh.subList(start,fresh.size()),-offset);
            }
            candidates=List.copyOf(fresh);
            List<MobNames.Named> open=new ArrayList<>();highlightPoints.clear();blockedCandidates=0;
            for(var n:candidates){
                if(!VisibleBody.valid(mc,n.mob())||!inRange(mc,n.mob(),c)||!allowed(n.mob(),n.name(),c))continue;
                var sample=exposed(mc,n.mob());
                if(sample!=null){open.add(n);highlightPoints.put(n.mob(),sample.point());}
                else if(!budgetSkipped.contains(n.mob()))blockedCandidates++;
            }
            visible=List.copyOf(open);
            scanTick=ticks;
        }
    }
    public void configure(boolean enabled){com.farmmacro.panic.AutomationSafety.INSTANCE.clearManualInput();localStopReason="";lastMode=ModConfig.INSTANCE.aimKeyMode;ModConfig.INSTANCE.aimAssistEnabled=enabled;resetActivation();release();autoFire.reset();clearChain();engagements.clear();diagnostics.clear(enabled?"ожидание клавиши":"выключен");special.cancel(Minecraft.getInstance(),"настройка/стоп");CombatSway.INSTANCE.stop(Minecraft.getInstance(),"настройка/стоп");status=enabled?"Aim assist: ожидаю клавишу":"Aim assist выключен";}
    public void disable(){configure(false);}
    /** Local combat stop: keep the saved master permission, require deliberate reactivation. */
    public void pauseLocal(String why){
        com.farmmacro.panic.AutomationSafety.INSTANCE.clearManualInput();
        var mc=Minecraft.getInstance();localStopReason=why;resetActivation();release();autoFire.reset();clearChain();engagements.clear();
        special.cancel(mc,"локальный стоп");CombatSway.INSTANCE.pauseForHandoff(mc,why);
        manualUntil=System.nanoTime()+180_000_000L;status="Combat на паузе: "+why+" — нажми клавишу заново";
        diagnostics.state("локальный стоп; требуется новое нажатие");
    }
    /** Deliberate manual/context handoff releases even the crouch retained by a local panic. */
    public void handoff(String why){
        var mc=Minecraft.getInstance();
        if(requested()||active||special.active()||special.pending()||CombatSway.INSTANCE.holdingCrouch())
            pauseLocal(why);
        CombatSway.INSTANCE.userTakeover(mc);
    }
    public String status(){return status;}
    private void release(){release(false);}
    private void release(boolean handoff){
        eggSacs.clear();
        if(target!=null&&target.level()==world){chainMob=target;chainPoint=target.getBoundingBox().getCenter();chainAt=System.nanoTime();}
        active=false;target=null;targetName="";lastFrame=0;occludedTicks=0;body.reset();if(!handoff)resetTracking();else {areaAim.reset();jitter.reset();}}
    private void clearChain(){chainMob=null;chainPoint=null;chainAt=chainRearUntil=0;}
    private String diagnosticKind(Mob mob,String name){
        if(special.focus()==mob||name.equalsIgnoreCase("Special Zealot"))return "Special Zealot";
        if(name.equalsIgnoreCase("Zealot Bruiser"))return "Zealot Bruiser";
        if(name.equalsIgnoreCase("Zealot"))return "Zealot";
        return mob.getType().getDescriptionId();
    }
    private void diagnostic(MobNames.Named named,TargetDiagnostics.Reason reason,double distance,int band){
        Mob mob=named.mob();
        diagnostics.record(mob.getId(),engagements.knownNumber(mob),diagnosticKind(mob,named.name()),reason,distance,band);
    }
    private void resetActivation(){
        idleLook.reset();latched=false;
        var key=FarmMacroMod.keyAim;holdBlocked=key!=null&&key.isDown();wasDown=holdBlocked;
        if(key!=null)while(key.consumeClick()){ /* A queued pre-stop click must never rearm this session. */ }
    }
    private boolean requested(){
        var key=FarmMacroMod.keyAim;if(key==null)return false;
        return "toggle".equals(ModConfig.INSTANCE.aimKeyMode)?latched:key.isDown()&&!holdBlocked;
    }
    private boolean updateActivation(Minecraft mc,ModConfig c){
        var key=FarmMacroMod.keyAim;if(key==null)return false;
        boolean pressed=false;while(key.consumeClick())pressed=true;
        boolean down=key.isDown();
        if(!c.aimKeyMode.equals(lastMode)){lastMode=c.aimKeyMode;resetActivation();release();}
        if(!down)holdBlocked=false;
        boolean context=(manualMode()?manualFree(mc):free(mc))&&!PanicHandoff.pending();
        if(!context||!c.aimAssistEnabled){if(down&&!context)holdBlocked=true;latched=false;wasDown=down;return false;}
        if("toggle".equals(c.aimKeyMode)&&pressed&&!wasDown){latched=!latched;release();}
        wasDown=down;return requested();
    }
    public void onMouseMove(double x,double y){
        if(manualMode()){mouseSeen=false;return;}
        if(!mcFocusedMouse()){mouseSeen=false;return;}
        special.onMouseMove(x,y);
        if(mouseSeen&&Math.hypot(x-mouseX,y-mouseY)>.7&&CombatSway.INSTANCE.holdingCrouch()){
            CombatSway.INSTANCE.userTakeover(Minecraft.getInstance());pauseLocal("ручное движение мыши");
        }
        if((ModConfig.INSTANCE.motion.camera.aimYieldMouse||idleLook.running())&&mouseSeen&&Math.hypot(x-mouseX,y-mouseY)>.7){manualUntil=System.nanoTime()+180_000_000L;release();clearChain();idleLook.reset();diagnostics.state("ручная мышь; короткая пауза");}
        mouseX=x;mouseY=y;mouseSeen=true;
    }
    private boolean mcFocusedMouse(){var mc=Minecraft.getInstance();return mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed()&&!Ungrab.active();}
    private boolean free(Minecraft mc){
        return mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.screen==null&&!mc.isPaused()
            && !MacroManager.INSTANCE.isActive()&&!MacroManager.INSTANCE.isRecording()&&!RouteEditor.isActive()
            && com.farmmacro.window.BackgroundWork.combatInput(mc)&&!Mousemat.busy()
            &&!com.farmmacro.camera.Freecam.active()
            && !PestHunter.INSTANCE.active()&&(!NavigationInput.busy()||special.walking())&&(!SmoothTurn.isActive()||special.walking())&&!Freelook.active()&&!MouseLock.isLocked()&&!HumanReaction.running();
    }
    private String blockedReason(Minecraft mc){
        if(mc.screen!=null)return "открыто окно";
        if(!com.farmmacro.window.BackgroundWork.permitted(mc))return "фоновая работа выключена";
        if(!com.farmmacro.window.BackgroundWork.combatInput(mc))return "курсор освобождён — выключи Ungrab (U)";
        if(NavigationInput.busy()||SmoothTurn.isActive()||Mousemat.busy())return "другая функция управляет камерой";
        if(PestHunter.INSTANCE.active())return "идёт охота на пестов";
        if(MacroManager.INSTANCE.isActive()||MacroManager.INSTANCE.isRecording()||RouteEditor.isActive())return "макрос/редактор";
        if(Freelook.active()||MouseLock.isLocked())return "freelook / блокировка мыши";
        return "игрок/мир/пауза/реакция";
    }
    private boolean allowed(Mob mob,String name,ModConfig c){
        if(special.active()&&special.focus()==mob)return true;
        if(c.aimZealotsOnly)return zealot(name)||c.aimUnnamedEndermen&&name.equalsIgnoreCase("Enderman")&&mob.getType().getDescriptionId().endsWith(".enderman");
        if(!c.aimSelectedOnly)return true;
        for(String n:c.aimMobNames)if(n.equalsIgnoreCase(name))return true;
        for(String n:c.aimExtraNames.split("[,;\\n]"))if(n.strip().equalsIgnoreCase(name))return true;
        return false;
    }
    private boolean location(Minecraft mc,ModConfig c){
        if(!c.aimLimitLocation||c.aimPreset.equals("nearby"))return true;
        for(String line:PanicDetector.sidebar(mc))for(String place:MobCatalog.get(c.aimPreset).locations())
            if(!place.isBlank()&&line.toLowerCase(Locale.ROOT).contains(place.toLowerCase(Locale.ROOT)))return true;
        return false;
    }
    private float[] angles(Vec3 eye,Vec3 point,float fallback){
        double dx=point.x-eye.x,dz=point.z-eye.z;
        return new float[]{Math.hypot(dx,dz)>.05?WalkCore.yawTo(eye.x,eye.z,point.x,point.z):fallback,
            (float)Math.clamp(-Math.toDegrees(Math.atan2(point.y-eye.y,Math.hypot(dx,dz))),-89,89)};
    }
    public void tick(Minecraft mc){
        if(lastManual!=manualMode()){configure(ModConfig.INSTANCE.aimAssistEnabled);lastManual=manualMode();}
        if(manualMode()){manualTick(mc);return;}
        updateAim(mc);
        long now=System.nanoTime();
        IdleLook.Context searchContext=target!=null?(occludedTicks>0?IdleLook.Context.COVERED:IdleLook.Context.INACTIVE):
            searchAllowed?(blockedCandidates>0?IdleLook.Context.COVERED:IdleLook.Context.EMPTY):IdleLook.Context.WAITING;
        idleLook.update(mc,ModConfig.INSTANCE.aimAssistEnabled&&requested()&&free(mc)
            &&!special.active()&&!special.pending()&&now>=manualUntil
            &&target==null&&searchAllowed&&(!ModConfig.INSTANCE.aimScytheOnly||ShotPath.holdingScythe(mc))&&location(mc,ModConfig.INSTANCE),searchContext);
        boolean armed=ModConfig.INSTANCE.aimAssistEnabled&&active&&requested()&&free(mc)&&now>=manualUntil
            &&!special.pending()&&(!special.active()||special.focus()==target)
            &&lastFrame!=0&&now-lastFrame<250_000_000L&&target!=null&&camera.perceived(target)&&allowed(target,targetName,ModConfig.INSTANCE);
        if(autoFire.tick(mc,target,armed)&&ModConfig.INSTANCE.aimNextAfterShot&&!(special.active()&&special.focus()==target)){
            int number=engagements.number(target);engagements.sent(target,now,ModConfig.INSTANCE.aimShotObservationMs);
            chainRearUntil=now+(long)ModConfig.INSTANCE.aimChainRearWaitMs*1_000_000L;
            release(true);status="Выстрел по #"+number+" отправлен; выбираю следующую без ожидания убийства";
            diagnostics.state("выстрел по #"+number+" отправлен; переход к следующей");
        }
    }
    private void updateAim(Minecraft mc){
        searchAllowed=false;
        exposure.clear();budgetSkipped.clear();technicalGate="ACQUIRING";
        var c=ModConfig.INSTANCE;
        if(!free(mc))handoff(blockedReason(mc));
        if(world!=mc.level){world=mc.level;resetActivation();release();autoFire.reset();clearChain();engagements.clear();diagnostics.clear("смена мира");special.cancel(mc,"смена мира");CombatSway.INSTANCE.stop(mc,"смена мира");candidates=List.of();visible=List.of();mouseSeen=false;lastPlayer=null;manualUntil=System.nanoTime()+500_000_000L;}
        if(mc.player!=null){
            Vec3 position=mc.player.position();
            if(lastPlayer!=null&&position.distanceTo(lastPlayer)>8){resetActivation();release();clearChain();manualUntil=System.nanoTime()+1_000_000_000L;}
            lastPlayer=position;
        }
        engagements.prune(mc.level);
        ticks++;boolean wants=updateActivation(mc,c);
        if(wants)localStopReason="";
        if(stopForPlayerOverlap(mc)){technicalGate="PLAYER_OVERLAP";return;}
        if(AutomationSafety.INSTANCE.tick(mc)){technicalGate="SAFETY_STOP";return;}
        if(special.active()||special.pending()){
            CombatSway.INSTANCE.stop(mc,"приоритет поиска special");
        }
        special.tick(mc,c.aimAssistEnabled&&wants&&free(mc)&&System.nanoTime()>=manualUntil);
        CombatSway.INSTANCE.tick(mc,c.aimAssistEnabled&&wants&&free(mc)&&!special.active()&&!special.pending()
            &&System.nanoTime()>=manualUntil,wants);
        boolean acquiring=wants&&target==null&&free(mc)&&System.nanoTime()>=manualUntil
            &&(!c.aimScytheOnly||ShotPath.holdingScythe(mc));
        scan(mc,c,acquiring);
        if(!c.aimAssistEnabled){technicalGate="DISABLED";release();status="Выключен — включи разрешение в Combat / Hunting";return;}
        if(!wants){technicalGate="WAIT_KEY";release();clearChain();diagnostics.state("ожидание клавиши; ниже последний поиск");status=localStopReason.isEmpty()?("toggle".equals(c.aimKeyMode)?"Нажми":"Удерживай")+" "+MacroManager.keyName(FarmMacroMod.keyAim)+" для наведения":"Combat остановлен: "+localStopReason+" — новый запуск только клавишей";return;}
        if(!free(mc)){technicalGate="CONTEXT_BLOCKED";release();clearChain();diagnostics.state("пауза: "+blockedReason(mc));status="Aim assist: пауза — "+blockedReason(mc);return;}
        if(System.nanoTime()<manualUntil){technicalGate="INPUT_GRACE";active=false;lastFrame=0;diagnostics.state("пауза после входа/перемещения/ручного ввода");status="Aim assist: короткая пауза после входа/телепорта/мыши";return;}
        if(c.aimScytheOnly&&!ShotPath.holdingScythe(mc)){technicalGate="WEAPON_REQUIRED";release();diagnostics.state("нужна Glacial Scythe");status="Возьми Glacial Scythe в основную руку";return;}
        if(special.active()&&(special.walking()||special.focus()==null)){technicalGate="SPECIAL_SEARCH";release();diagnostics.state("Special: поиск/подход, обычные цели приостановлены");status=special.status();return;}
        if(!special.active()&&!location(mc,c)){technicalGate="LOCATION_FILTER";release();diagnostics.state("вне выбранной локации");status="Aim assist: вне выбранной локации";return;}
        // A locked living target does not get replaced just because another mob comes closer.
        if(target!=null){
            boolean valid=target.isAlive()&&!target.isDeadOrDying()&&!target.isRemoved()&&!target.isInvisibleTo(mc.player)
                && allowed(target,targetName,c)&&inRange(mc,target,c)
                && (!special.active()||special.focus()==target)
                && !(c.aimNextAfterShot&&engagements.waiting(target,System.nanoTime())&&!(special.active()&&special.focus()==target));
            if(!valid)release(true);
            else if(!shootable(mc,target)){
                pauseOccluded();status="Захват сохранён: "+targetName+" — тело закрыто, выстрел запрещён";
                technicalGate="OCCLUDED_HOLD";diagnostics.state("захват #"+engagements.number(target)+" сохранён; краткое перекрытие");
                if(++occludedTicks<=8)return;release(true);
            }else{occludedTicks=0;active=true;diagnostics.state("живой захват #"+engagements.number(target)+"; другие цели не переключают его");status="Цель #"+engagements.number(target)+": "+targetName;return;}
        }
        
        // A target can become invalid after the earlier scan decision in this same tick.
        if(scanTick!=ticks)scan(mc,c,true);
        Mob best=null;String name="";double bestScore=Double.POSITIVE_INFINITY,bestDistance=Double.POSITIVE_INFINITY;
        Vec3 eye=mc.player.getEyePosition();
        long selectionNow=System.nanoTime();
        if(chainPoint!=null&&selectionNow-chainAt>6_000_000_000L)clearChain();
        if(chainPoint!=null&&VisibleBody.valid(mc,chainMob))chainPoint=chainMob.getBoundingBox().getCenter();
        Vec3 previous=special.active()?null:chainPoint;
        float previousYaw=previous==null?mc.player.getYRot():angles(eye,previous,mc.player.getYRot())[0];
        double localRadius=previous==null?0:Math.clamp(eye.distanceTo(previous)*.35,8,24);
        int bestBand=Integer.MAX_VALUE,bestId=Integer.MAX_VALUE,pendingBand=Integer.MAX_VALUE;
        int rejectedNames=0,rejectedFov=0,rejectedPath=0,deferred=0,unnamed=0,waiting=0;
        diagnostics.begin();
        for(var named:candidates){
            Mob mob=named.mob();
            if(!VisibleBody.valid(mc,mob)){diagnostic(named,TargetDiagnostics.Reason.INVALID,Double.NaN,-1);continue;}
            Vec3 center=mob.getBoundingBox().getCenter();double distance=eye.distanceTo(center);
            if(named.name().equalsIgnoreCase("Enderman"))unnamed++;
            if(special.active()&&mob!=special.focus()){diagnostic(named,TargetDiagnostics.Reason.SPECIAL,distance,-1);continue;}
            if(!allowed(mob,named.name(),c)){rejectedNames++;diagnostic(named,TargetDiagnostics.Reason.FILTER,distance,-1);continue;}
            if(!inRange(mc,mob,c)){diagnostic(named,TargetDiagnostics.Reason.RANGE,distance,-1);continue;}
            engagements.number(mob);
            var open=exposed(mc,mob);
            if(open==null){if(budgetSkipped.contains(mob))deferred++;else rejectedPath++;diagnostic(named,budgetSkipped.contains(mob)?TargetDiagnostics.Reason.BUDGET:TargetDiagnostics.Reason.COVER,distance,-1);continue;}
            float[] a=angles(eye,open.point(),mc.player.getYRot());
            double yaw=Math.abs(WalkCore.wrap(a[0]-mc.player.getYRot()));
            double pitch=(c.aimPitch||c.aimScytheOnly)?Math.abs(a[1]-mc.player.getXRot()):0;
            if(!withinFov(mc,open.point(),c)){rejectedFov++;diagnostic(named,TargetDiagnostics.Reason.FOV,distance,-1);continue;}
            double score=TargetChoice.score(distance,yaw,pitch,c.aimZealotPriority&&zealot(named.name()));
            int band=yaw<=110?0:1;
            if(previous!=null){
                double fromPrevious=center.distanceTo(previous),turn=Math.abs(WalkCore.wrap(a[0]-previousYaw));
                band=TargetChoice.chainBand(fromPrevious,turn,yaw,localRadius);
                score=TargetChoice.chainScore(fromPrevious,score,turn);
            }
            if(c.aimNextAfterShot&&engagements.waiting(mob,selectionNow)&&!(special.active()&&special.focus()==mob)){
                waiting++;pendingBand=Math.min(pendingBand,band);diagnostic(named,TargetDiagnostics.Reason.WAITING,distance,band);continue;
            }
            diagnostic(named,TargetDiagnostics.Reason.READY,distance,band);
            if(!TargetChoice.better(band,score,distance,mob.getId(),bestBand,bestScore,bestDistance,bestId))continue;
            bestBand=band;bestScore=score;bestDistance=distance;bestId=mob.getId();best=mob;name=named.name();
        }
        if(TargetChoice.deferFallback(previous!=null,bestBand,pendingBand,selectionNow,chainRearUntil)){
            release(true);technicalGate="WAIT_LOCAL_CHAIN";diagnostics.state("коротко жду локальную очередь, без дальнего/заднего перевода");
            status="Очередь: жду соседей ещё "+Math.max(0,(chainRearUntil-selectionNow)/1_000_000L)+" мс вместо дальнего разворота";return;
        }
        if(best==null){
            // Occluded mobs elsewhere are not a reason to freeze a camera-only sector search.
            // A live lock keeps its eight-tick cover grace; shots still require fresh geometry.
            searchAllowed=waiting==0&&!special.active()&&!special.pending();
            release(true);technicalGate=waiting>0?"WAIT_PROJECTILE":"NO_OPEN_TARGET";diagnostics.state(waiting>0?"ожидание результата выстрелов":"нет подходящей открытой цели");
            status="Нет захвата: ожидают результата "+waiting+", видимых "+visible.size()+", фильтр имён "+rejectedNames+", FOV "+rejectedFov+", закрыто тело "+rejectedPath+", отложено бюджетом "+deferred;
            if(c.aimZealotsOnly&&unnamed>0)status+="; Enderman без имени: "+unnamed+" (опция дальних целей)";
            return;
        }
        diagnostics.chosen(best.getId(),engagements.number(best),diagnosticKind(best,name),bestDistance,bestBand);
        diagnostics.state(special.active()?"приоритетный Special":(engagements.returning(best,selectionNow)?"повторно живая цель #":"выбрана цель #")+engagements.number(best)+", сектор "+bestBand);
        areaAim.reset();jitter.reset();lastFrame=0;idleLook.reset();target=best;targetName=name;active=true;status="Цель #"+engagements.number(target)+": "+targetName;
    }
    public void onFrame(){
        if(manualMode()){manualFrame(Minecraft.getInstance());return;}
        Minecraft mc=Minecraft.getInstance();var c=ModConfig.INSTANCE;
        if(!free(mc))handoff(blockedReason(mc));
        if(mc.player==null||world!=mc.level||mc.screen!=null||!com.farmmacro.window.BackgroundWork.permitted(mc)||mc.isPaused()){
            resetActivation();release();clearChain();CombatSway.INSTANCE.stop(mc,"окно/фокус/пауза");return;
        }
        if(!requested()){release();clearChain();CombatSway.INSTANCE.stopWhenInactive(mc,"клавиша выключена");return;}
        if(PanicHandoff.pending())return;
        if(stopForPlayerOverlap(mc)){technicalGate="PLAYER_OVERLAP";return;}
        if(AutomationSafety.INSTANCE.tick(mc)){technicalGate="SAFETY_STOP";return;}
        if(NavigationInput.busy()||SmoothTurn.isActive()||special.pending()||special.walking()||special.active()&&special.focus()==null){idleLook.reset();camera.reset();return;}
        if(target==null&&searchAllowed&&idleLook.running()){
            if(!c.aimIdleLook||!c.aimAssistEnabled||!free(mc)||System.nanoTime()<manualUntil
                ||c.aimScytheOnly&&!ShotPath.holdingScythe(mc)||!location(mc,c)){idleLook.reset();return;}
            var goal=idleLook.goal(mc);if(goal==null)return;
            var look=camera.frame(idleLook,true,false,mc.player.getYRot(),mc.player.getXRot(),goal.yaw(),goal.pitch(),
                mc.options.sensitivity().get(),goal.speed(),.3,.15);
            if(!c.aimPitch&&!c.aimScytheOnly)look=new FastAimTurn.Delta(look.yaw(),0);
            applyCamera(mc,look);
            return;
        }
        if(!active||!c.aimAssistEnabled||!free(mc)||System.nanoTime()<manualUntil||target==null)return;
        if(target.isRemoved()||!target.isAlive()||target.isDeadOrDying()||target.isInvisibleTo(mc.player)){release(true);return;}
        if(!inRange(mc,target,c)||c.aimScytheOnly&&!ShotPath.holdingScythe(mc)){release();return;}
        long now=System.nanoTime();double dt=lastFrame==0?1/60.0:Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye=mc.player.getEyePosition(partial),center=target.getBoundingBox().getCenter().add(target.getPosition(partial).subtract(target.position()));
        Vec3 point=body.point(mc,target,eye,center);
        if(point==null){pauseOccluded();return;}
        // Near a cover edge aim at the verified opening, not a random offset behind the wall.
        if(!body.partial())point=areaAim.point(mc,target,eye,center,dt);
        if(!ShotPath.clear(mc,eye,point)){pauseOccluded();return;}
        float[] a=angles(eye,point,mc.player.getYRot());
        boolean vertical=c.aimPitch||c.aimScytheOnly;
        var box=target.getBoundingBox();double distance=Math.max(.5,eye.distanceTo(point));
        double yawMargin=body.partial()?0:Math.min(1.5,Math.toDegrees(Math.atan2(Math.min(box.maxX-box.minX,box.maxZ-box.minZ)*.2,distance)));
        double pitchMargin=vertical&&!body.partial()?Math.min(.7,Math.toDegrees(Math.atan2((box.maxY-box.minY)*.1,distance))):0;
        if("human".equals(c.motion.camera.mode(c.motion))&&c.motion.camera.aimJitterEnabled&&!body.partial()){
            var offset=jitter.frame(dt,Math.min(c.motion.camera.aimJitterDegrees,yawMargin));
            a[0]+=(float)Math.clamp(offset.yaw(),-yawMargin,yawMargin);
            if(vertical)a[1]=(float)Math.clamp(a[1]+Math.clamp(offset.pitch(),-pitchMargin,pitchMargin),-89,89);
        }else jitter.reset();
        double pitchGoal=vertical?a[1]:mc.player.getXRot();
        boolean priority=special.active()&&special.focus()==target||targetName.equalsIgnoreCase("Special Zealot");
        var d=camera.frame(target,false,priority,mc.player.getYRot(),mc.player.getXRot(),a[0],pitchGoal,
            mc.options.sensitivity().get(),c.motion.camera.aimTurnSpeed,yawMargin,pitchMargin);
        if(!vertical)d=new FastAimTurn.Delta(d.yaw(),0);
        applyCamera(mc,d);
    }
    private boolean manualAllowed(String name,ModConfig c){return BossTargets.matches(c.aimManualTarget,name,c.aimManualName);}
    private boolean manualFree(Minecraft mc){return free(mc)&&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed();}
    /** Camera-only lifecycle. Never ticks Special, Sway, IdleLook or AutoScytheFire. */
    private void manualTick(Minecraft mc){
        var c=ModConfig.INSTANCE;searchAllowed=false;
        if(world!=mc.level){world=mc.level;resetActivation();release();candidates=visible=List.of();highlightPoints.clear();lastPlayer=null;}
        if(!manualFree(mc))handoff(blockedReason(mc));
        ticks++;boolean wants=updateActivation(mc,c);
        if(!c.aimAssistEnabled||!wants||!manualFree(mc)||PanicHandoff.pending()){
            release();visible=List.of();highlightPoints.clear();technicalGate="WAIT_MANUAL_KEY";
            status="Только наведение: "+(!c.aimAssistEnabled?"включи разрешение":!manualFree(mc)?"нужны фокус игры и свободный контекст":"нажми/удерживай "+MacroManager.keyName(FarmMacroMod.keyAim));return;
        }
        if(AutomationSafety.INSTANCE.tick(mc))return;
        localStopReason="";var eye=mc.player.getEyePosition();
        if(eggSacs.update(mc,c,target,targetName,ticks)){
            active=true;occludedTicks=0;technicalGate="MANUAL_EGG_SAC";
            status="Tarantula: мешок с яйцами · стреляй сам; затем следующий / босс";return;
        }
        if(target!=null){
            boolean valid=VisibleBody.valid(mc,target)&&manualAllowed(targetName,c)&&eye.distanceTo(target.getBoundingBox().getCenter())<=c.aimManualRange;
            if(!valid)release();
            else if(ShotPath.clear(mc,eye,target.getBoundingBox().getCenter())){active=true;occludedTicks=0;technicalGate="MANUAL_TRACKING";status="Только наведение: "+targetName+" · движение/атаки вручную";return;}
            else{active=false;lastFrame=0;technicalGate="MANUAL_COVER";status="Центр закрыт: "+targetName+" · захват временно сохранён";if(++occludedTicks<=8)return;release();}
        }
        if(ticks%2!=0)return;
        var list=new ArrayList<MobNames.Named>(MobNames.nearby(mc,c.aimManualRange));
        list.sort(Comparator.comparingDouble(n->mc.player.distanceToSqr(n.mob())));
        candidates=List.copyOf(list);var open=new ArrayList<MobNames.Named>();highlightPoints.clear();
        Mob best=null;String name="";double bestScore=Double.POSITIVE_INFINITY;int probes=0;
        for(var n:list){
            var mob=n.mob();if(!VisibleBody.valid(mc,mob)||!manualAllowed(n.name(),c))continue;
            var center=mob.getBoundingBox().getCenter();double dist=eye.distanceTo(center);if(dist>c.aimManualRange)continue;
            float[] a=angles(eye,center,mc.player.getYRot());double error=Math.hypot(WalkCore.wrap(a[0]-mc.player.getYRot()),a[1]-mc.player.getXRot());
            if(error>c.aimManualFov||++probes>BODY_BUDGET||!ShotPath.clear(mc,eye,center))continue;
            open.add(n);highlightPoints.put(mob,center);double score=BossTargets.score(error,dist);
            if(score<bestScore||score==bestScore&&(best==null||mob.getId()<best.getId())){best=mob;name=n.name();bestScore=score;}
        }
        visible=List.copyOf(open);
        if(best==null){technicalGate="MANUAL_NO_TARGET";status="Только наведение: нет видимого центра выбранного моба в радиусе/FOV";return;}
        target=best;targetName=name;active=true;lastFrame=0;occludedTicks=0;technicalGate="MANUAL_TRACKING";
        status="Только наведение: "+name+" · движение/атаки вручную";
    }
    private void manualFrame(Minecraft mc){
        var c=ModConfig.INSTANCE;
        if(!manualFree(mc)){handoff("ручной аим: окно/фокус/другая задача");return;}
        if(PanicHandoff.pending()||!requested()||!c.aimAssistEnabled||!active)return;
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 point;
        if(eggSacs.locked()){
            point=eggSacs.framePoint(mc,c,partial);if(point==null){lastFrame=0;return;}
        }else{
            if(target==null)return;
            if(!VisibleBody.valid(mc,target)||!manualAllowed(targetName,c)){release();return;}
            point=target.getBoundingBox().getCenter().add(target.getPosition(partial).subtract(target.position()));
        }
        Vec3 eye=mc.player.getEyePosition(partial);
        if(eye.distanceTo(point)>c.aimManualRange||!ShotPath.clear(mc,eye,point)){lastFrame=0;return;}
        long now=System.nanoTime();double dt=lastFrame==0?1/60.0:(now-lastFrame)/1e9;lastFrame=now;
        float[] a=angles(eye,point,mc.player.getYRot());var d=com.farmmacro.motion.DirectAimTurn.frame(mc.player.getYRot(),mc.player.getXRot(),a[0],a[1],c.motion.camera.aimTurnSpeed,dt);
        if(!com.farmmacro.motion.MotionController.turn(com.farmmacro.motion.MotionPolicy.Owner.COMBAT,d.yaw(),d.pitch()))lastFrame=0;
    }
    private void applyCamera(Minecraft mc,FastAimTurn.Delta d){
        // Last-moment arbiter: own Special workflow may navigate, but combat must not also turn that frame.
        if(stopForPlayerOverlap(mc)){camera.reset();return;}
        if(mc.player==null||mc.level!=world||!free(mc)||NavigationInput.busy()||SmoothTurn.isActive()
            ||PanicHandoff.pending()||!requested()||!ModConfig.INSTANCE.aimAssistEnabled||System.nanoTime()<manualUntil){camera.reset();return;}
        if(!Double.isFinite(d.yaw())||!Double.isFinite(d.pitch())){camera.reset();return;}
        float y0=mc.player.getYRot(),p0=mc.player.getXRot();
        if(d.yaw()!=0||d.pitch()!=0){
            if(!com.farmmacro.motion.MotionController.turn(com.farmmacro.motion.MotionPolicy.Owner.COMBAT,d.yaw(),d.pitch())){camera.reset();return;}
        }
        camera.applied(mc.player.getYRot(),mc.player.getXRot());
    }
    /** Live player list and actual body overlap, never discovery/ESP cache or a staff heuristic. */
    public boolean stopForPlayerOverlap(Minecraft mc){
        var c=ModConfig.INSTANCE;var p=c.combatPanic;
        if(mc.player==null||mc.level==null||!c.aimAssistEnabled||!requested()||PanicHandoff.pending()
            ||p==null||!p.enabled||!p.playerOverlap
            ||!(c.aimZealotsOnly||"zealots".equals(c.combatProfileId)||zealot(targetName)))return false;
        var own=mc.player.getBoundingBox();
        for(var other:mc.level.players()){
            if(other==mc.player||other.isRemoved()||!other.isAlive()||other.isSpectator())continue;
            if(!own.intersects(other.getBoundingBox()))continue;
            // No wait, evasive response, chat or auto-resume. Existing panic owns the visible notice.
            PanicDetector.INSTANCE.triggerPanic(mc,"combatOverlap","Другой игрок пересёк твоего персонажа — Combat остановлен");
            return true;
        }
        return false;
    }
    /** A renderer error stops this run, not the saved master permission. Guard keeps its stack trace. */
    public void frameFailed(){
        handoff("ошибка кадровой камеры; смотри журнал combat/aim-frame");
        var mc=Minecraft.getInstance();
        if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Kaelix: Combat остановлен — ошибка камеры (журнал combat/aim-frame)"));
    }
    private AimAssist(){}
}
