package com.farmmacro.pest;
import com.farmmacro.config.ModConfig;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.combat.ShotPath;
import com.farmmacro.panic.*;
import com.farmmacro.route.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.regex.*;
/** Garden trap maintenance: recorded route points, exact own menus, packet-confirmed actions, no purchases. */
public final class PestTraps {
    public static final PestTraps INSTANCE=new PestTraps();
    public enum Result{RUNNING,DONE,FAIL}
    private enum Phase{IDLE,NAV,OPEN,MENU,RELEASE,PICK,DEPOSIT,RESTORE,VERIFY,PET_OPEN,PET_MENU,PET_VERIFY}
    private Phase phase=Phase.IDLE;
    private static final Pattern MARKER=Pattern.compile("(?i)^(?:Pest|Mouse|Vermin) Trap\\s*#([1-3])$"),TITLE=Pattern.compile("(?i)^(?:Pest|Mouse|Vermin) Trap(?: #([1-3]))?$"),PET=Pattern.compile("^\\[Lvl \\d+\\] (.+)$"),COUNT=Pattern.compile("(?i)(?:bait|amount|stored)[: ]+([0-9,]+)");
    private final ReturnWalker walker=new ReturnWalker();private final Deque<PestAssistSettings.TrapPoint> queue=new ArrayDeque<>();
    private PestAssistSettings.TrapPoint point;private Entity marker;private net.minecraft.client.multiplayer.ClientLevel world;
    private AbstractContainerMenu menu;private Screen screen;private int age,ticks,clicks,legs,source,baitSlot,remaining,want,beforeCursor,beforeBait,petSlot;private long expectUntil,revision,sentAt;private String expectedTitle,petName,status="Ловушки выключены";private boolean warpTried,walking,preDone,postDone,released,releasedAny,filled,petAfter,fullBefore;
    private ItemStack selectedPet=ItemStack.EMPTY;
    private Set<Integer> full=Set.of(),empty=Set.of();private long fullAt,emptyAt;private net.minecraft.client.multiplayer.ClientLevel tabWorld;
    private static PestAssistSettings cfg(){return ModConfig.INSTANCE.pests.assist;}
    public boolean active(){return phase!=Phase.IDLE;}
    public String status(){return status;}
    public boolean releasedAny(){return releasedAny;}
    public void sample(Minecraft mc){
        if(tabWorld!=mc.level){tabWorld=mc.level;fullAt=emptyAt=0;full=empty=Set.of();}
        if(!PestHunter.garden(mc)){fullAt=emptyAt=0;return;}var c=mc.getConnection();if(c==null)return;
        for(var p:c.getOnlinePlayers()){var d=p.getTabListDisplayName();if(d==null)continue;for(String line:d.getString().split("\\n")){
            String s=ServerText.clean(line);if(s.startsWith("Full Traps:")){var ids=parse(s.substring(11));if(ids!=null){full=ids;fullAt=System.currentTimeMillis();}}
            if(s.startsWith("No Bait:")){var ids=parse(s.substring(8));if(ids!=null){empty=ids;emptyAt=System.currentTimeMillis();}}
        }}
    }
    private static Set<Integer> parse(String s){s=s.trim();if(s.isEmpty()||s.equalsIgnoreCase("None")||s.equals("0"))return Set.of();Set<Integer> out=new HashSet<>();for(String token:s.split(",\\s*")){if(!token.matches("#[1-3]"))return null;out.add(Integer.parseInt(token.substring(1)));}return Set.copyOf(out);}
    private boolean fresh(long at){return System.currentTimeMillis()-at<10_000;}
    public boolean needed(){return cfg().traps&&cfg().autoTraps&&(cfg().clearTraps&&fresh(fullAt)&&!full.isEmpty()||cfg().refillTraps&&fresh(emptyAt)&&!empty.isEmpty());}
    public boolean capture(Minecraft mc){
        if(active()||PestHunter.INSTANCE.active()||!PestHunter.garden(mc)||!mc.player.onGround()||mc.player.getAbilities().flying||!(mc.hitResult instanceof EntityHitResult hit))return false;
        Entity e=hit.getEntity();Integer id=markerId(e);if(id==null||mc.player.distanceTo(e)>6)return false;
        var p=new PestAssistSettings.TrapPoint();p.id=id;p.plot=PestPlots.plotAt(e.getX(),e.getZ());p.world=RouteBuffer.worldId(mc);p.dimension=RouteBuffer.dimensionId(mc);p.x=mc.player.getX();p.y=mc.player.getY();p.z=mc.player.getZ();p.tx=e.getX();p.ty=e.getY();p.tz=e.getZ();
        if(!p.valid()||!new GroundProbe(new PathLevel(mc.level)).clear(p.x,p.y,p.z,p.x,p.z,.31))return false;cfg().points.removeIf(x->x.id==id);cfg().points.add(p);ModConfig.save();return true;
    }
    private static Integer markerId(Entity e){if(!(e instanceof ArmorStand)||e.getCustomName()==null)return null;var m=MARKER.matcher(ServerText.clean(e.getCustomName().getString()));return m.matches()?Integer.parseInt(m.group(1)):null;}
    public boolean begin(Minecraft mc,boolean manual){
        if(!cfg().traps||!cfg().clearTraps&&!cfg().refillTraps||!PestHunter.garden(mc)||PanicHandoff.pending()||NavigationInput.busy())return false;
        queue.clear();for(var p:cfg().points)if(Objects.equals(p.world,RouteBuffer.worldId(mc))&&Objects.equals(p.dimension,RouteBuffer.dimensionId(mc))&&(manual||cfg().clearTraps&&fresh(fullAt)&&full.contains(p.id)||cfg().refillTraps&&fresh(emptyAt)&&empty.contains(p.id)))queue.add(p);
        if(queue.isEmpty()){status="Ловушки: нет сохранённых применимых точек";return false;}
        var sorted=new ArrayList<>(queue);sorted.sort(Comparator.comparingDouble(p->mc.player.position().distanceToSqr(new Vec3(p.x,p.y,p.z))));queue.clear();queue.addAll(sorted);
        world=mc.level;preDone=postDone=releasedAny=false;ticks=clicks=legs=0;PestMenuEvidence.reset();return next(mc);
    }
    private boolean next(Minecraft mc){
        closeOwn(mc);walker.stop(mc);releaseLease(mc);if(queue.isEmpty()){
            if(cfg().postPet&&!postDone&&!cfg().petAfter.isBlank()){postDone=true;return startPet(mc,cfg().petAfter,true);}
            phase=Phase.IDLE;status="Ловушки: обслуживание закончено; выпуск не означает убийство";return true;
        }
        point=queue.removeFirst();warpTried=false;released=filled=false;marker=null;walking=false;setPhase(Phase.NAV);status="Ловушки: подход к #"+point.id;return true;
    }
    private void setPhase(Phase p){phase=p;age=0;sentAt=System.currentTimeMillis();}
    private boolean startPet(Minecraft mc,String name,boolean after){
        walker.stop(mc);if(!NavigationInput.acquire(this))return false;petName=name;petAfter=after;expectedTitle="Pets";expectUntil=System.currentTimeMillis()+2000;menu=null;screen=null;setPhase(Phase.PET_OPEN);mc.player.connection.sendCommand("pets");status="Ловушки: жду собственное Pets для "+name;return true;
    }
    /** Only a short expectation from our specific request; never any arbitrary inventory/trap screen. */
    public boolean expectScreen(Screen s){
        if(!active()||world!=Minecraft.getInstance().level||PanicHandoff.pending()||System.currentTimeMillis()>expectUntil||expectedTitle==null||!(s instanceof AbstractContainerScreen<?>))return false;
        String t=ServerText.clean(s.getTitle().getString());if(expectedTitle.equals("Pets"))return t.equals("Pets")||t.matches("Pets \\(\\d+/\\d+\\)");
        var m=TITLE.matcher(t);return m.matches()&&(m.group(1)==null||point!=null&&Integer.parseInt(m.group(1))==point.id);
    }
    public boolean ownsScreen(Screen s){return active()&&screen==s&&menu!=null&&s instanceof AbstractContainerScreen<?> c&&c.getMenu()==menu;}
    private boolean bind(Minecraft mc){if(!(mc.screen instanceof AbstractContainerScreen<?> c)||!expectScreen(mc.screen))return false;screen=c;menu=c.getMenu();expectUntil=0;expectedTitle=null;return menu==mc.player.containerMenu&&menu.getCarried().isEmpty();}
    public Result tick(Minecraft mc){
        if(!active())return Result.DONE;
        if(mc.player==null||mc.level!=world||mc.gameMode==null||!mc.isWindowActive()||PanicHandoff.pending()||!cfg().traps||++ticks>cfg().trapSeconds*20||System.currentTimeMillis()-sentAt>(phase==Phase.NAV?cfg().trapSeconds*1000L:12_000L))return fail(mc,"контекст/общий или шаговый таймаут");
        age++;if(mc.screen!=null&&!ownsScreen(mc.screen)&&!expectScreen(mc.screen))return fail(mc,"чужое/изменённое меню");
        if(phase==Phase.NAV){
            if(cfg().preMosquito&&cfg().clearTraps&&!preDone){preDone=true;if(!startPet(mc,"Mosquito",false))return fail(mc,"Pets input занят");return Result.RUNNING;}
            if(mc.screen!=null)return fail(mc,"GUI до подхода");
            if(walking){var res=walker.tick(mc,ModConfig.INSTANCE);status="Ловушки #"+point.id+": "+walker.what();if(res==ReturnWalker.Status.FAIL)return fail(mc,walker.failText());if(res==ReturnWalker.Status.RUNNING)return Result.RUNNING;walking=false;}
            if(mc.player.position().distanceTo(new Vec3(point.x,point.y,point.z))>.8){
                double hd=Math.hypot(point.x-mc.player.getX(),point.z-mc.player.getZ());
                if(++legs>32)return fail(mc,"лимит навигационных участков к ловушкам");
                if(hd>ModConfig.INSTANCE.returnMaxDist){
                    var plot=PestPlots.plot(point.plot);
                    if(!warpTried){warpTried=true;if(plot!=null&&PestTravel.INSTANCE.toPlot(mc,plot,PestHunter.INSTANCE.travelVacuum()))return Result.RUNNING;}
                    if(plot!=null&&PestTravel.INSTANCE.advance(mc,plot,PestHunter.INSTANCE.travelVacuum()))return Result.RUNNING;
                    if(!ReturnWalker.canFly(mc.player))return fail(mc,"дальний подход: нет разрешённого полёта/проверяемого warp");
                    double length=Math.min(24,Math.max(4,ModConfig.INSTANCE.returnMaxDist-1)),k=Math.min(1,length/hd),x=mc.player.getX()+(point.x-mc.player.getX())*k,z=mc.player.getZ()+(point.z-mc.player.getZ())*k;
                    walker.landAtGoal=false;walker.finishFacing=false;
                    double y=Math.max(mc.player.getY(),point.y+2);
                    if(!walker.beginFlightLeg(mc,x,y,z,"к плоту ловушки"))return fail(mc,"дальний подход: нет loaded-clear воздушного участка");walking=true;return Result.RUNNING;
                }
                walker.landAtGoal=true;walker.finishFacing=false;walker.begin(mc,PathFinder.pointGoal(point.x,point.y,point.z,0),j->new float[]{mc.player.getYRot(),mc.player.getXRot()},"к сохранённой ловушке");walking=true;return Result.RUNNING;
            }
            int seen=0;for(Entity e:com.farmmacro.util.LoadedEntities.get(mc)){if(++seen>1024)break;if(Objects.equals(markerId(e),point.id)&&e.position().distanceTo(new Vec3(point.tx,point.ty,point.tz))<1&&mc.player.distanceTo(e)<4){if(marker!=null)return fail(mc,"две метки одного ID");marker=e;}}
            if(marker==null)return fail(mc,"нет загруженной точной метки #"+point.id);if(!NavigationInput.acquire(this))return fail(mc,"ввод занят");setPhase(Phase.OPEN);
        }
        if(NavigationInput.owns(this)){var o=mc.options;for(var k:new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse})NavigationInput.press(this,o,k,false);}
        if(phase==Phase.OPEN){
            if(mc.screen!=null){if(!bind(mc))return fail(mc,"неподтверждённое меню ловушки");setPhase(Phase.MENU);return Result.RUNNING;}
            if(age>60)return fail(mc,"ловушка не открылась");if(expectUntil>0)return Result.RUNNING;
            Vec3 aim=marker.getBoundingBox().getCenter(),eye=mc.player.getEyePosition();if(!ShotPath.clear(mc,eye,aim))return fail(mc,"ловушка закрыта блоком");float yaw=WalkCore.yawTo(eye.x,eye.z,aim.x,aim.z),pitch=(float)-Math.toDegrees(Math.atan2(aim.y-eye.y,Math.hypot(aim.x-eye.x,aim.z-eye.z)));
            NavigationInput.camera(this,()->SmoothTurn.followMouse(yaw,pitch,ModConfig.INSTANCE.pests.aimSpeed));
            var ray=marker.getBoundingBox().inflate(.05).clip(eye,eye.add(mc.player.getLookAngle().scale(Math.min(3.8,mc.player.entityInteractionRange()))));if(ray.isEmpty()||ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch)>3)return Result.RUNNING;
            expectedTitle="Trap";expectUntil=System.currentTimeMillis()+1800;SmoothTurn.stop();mc.gameMode.interact(mc.player,marker,new EntityHitResult(marker,ray.get()),InteractionHand.MAIN_HAND);return Result.RUNNING;
        }
        if(phase==Phase.PET_OPEN){if(mc.screen==null){if(age>45)return fail(mc,"Pets не открылось");return Result.RUNNING;}if(!bind(mc))return fail(mc,"неподтверждённое Pets");setPhase(Phase.PET_MENU);return Result.RUNNING;}
        if(phase==Phase.PET_MENU){
            int found=-1;for(int i=0;i<menu.slots.size();i++){var slot=menu.slots.get(i);if(slot.container==mc.player.getInventory())continue;var st=slot.getItem();var m=PET.matcher(PestItems.name(st));if(!m.matches()||!m.group(1).equalsIgnoreCase(petName)||!PestItems.id(st).equals("PET"))continue;if(found>=0)return fail(mc,"несколько питомцев с таким именем — неоднозначность");found=i;}
            if(found<0)return fail(mc,"питомец не найден на видимой странице Pets");var st=menu.slots.get(found).getItem();String lore=PestItems.lore(st).toLowerCase(Locale.ROOT);
            if(lore.contains("click to despawn!")){endPet(mc);return Result.RUNNING;}if(!lore.contains("click to summon!"))return fail(mc,"нет достоверной кнопки summon");petSlot=found;selectedPet=st.copy();click(mc,found,0);setPhase(Phase.PET_VERIFY);return Result.RUNNING;
        }
        if(phase==Phase.PET_VERIFY){if(!ownsScreen(mc.screen))return fail(mc,"Pets закрыто до подтверждения");var st=menu.slots.get(petSlot).getItem();if(ack()&&PestItems.name(st).equals(PestItems.name(selectedPet))&&PestItems.lore(PestMenuEvidence.observed(menu,petSlot)).toLowerCase(Locale.ROOT).contains("click to despawn!")&&PestMenuEvidence.stamp(menu,petSlot)>revision&&PestItems.id(PestMenuEvidence.observed(menu,petSlot)).equals("PET")&&(PestItems.uuid(selectedPet).isEmpty()||PestItems.uuid(selectedPet).equals(PestItems.uuid(PestMenuEvidence.observed(menu,petSlot))))){endPet(mc);return Result.RUNNING;}if(age>60)return fail(mc,"активный питомец не подтверждён");return Result.RUNNING;}
        if(!ownsScreen(mc.screen)){if(phase==Phase.RELEASE&&fullBefore&&fresh(fullAt)&&fullAt>sentAt&&!full.contains(point.id)){released=releasedAny=true;menu=null;screen=null;setPhase(Phase.NAV);marker=null;return Result.RUNNING;}return fail(mc,"меню исчезло до результата");}
        if(phase==Phase.MENU){
            if(PestMenuEvidence.revision(menu)==0||age<4)return Result.RUNNING;
            if(cfg().clearTraps&&!released){int n=countPests(mc);if(n==0)released=true;else{int release=find(mc,"Release All Pests");if(release>=0&&(n>0||fresh(fullAt)&&full.contains(point.id))){fullBefore=fresh(fullAt)&&full.contains(point.id);click(mc,release,0);setPhase(Phase.RELEASE);status="Ловушка #"+point.id+": жду подтверждение выпуска";return Result.RUNNING;}if(!released)return fail(mc,"нет достоверного заполнения/кнопки выпуска");}}
            if(cfg().refillTraps&&!filled){
                baitSlot=findContains(mc,"Trap Bait");if(baitSlot<0)return fail(mc,"неизвестный bait-control; не кликаю вслепую");beforeBait=baitCount(PestMenuEvidence.observed(menu,baitSlot));if(beforeBait<0)return fail(mc,"нет числового количества bait в menu lore");if(beforeBait>0&&!PestItems.lore(PestMenuEvidence.observed(menu,baitSlot)).toLowerCase(Locale.ROOT).contains(cfg().bait.toLowerCase(Locale.ROOT).replace('_',' '))&&!PestItems.id(PestMenuEvidence.observed(menu,baitSlot)).equals(cfg().bait))return fail(mc,"имеющийся тип bait отличается/неизвестен");want=Math.max(0,cfg().baitAmount-beforeBait);if(want==0){filled=true;next(mc);return Result.RUNNING;}
                source=-1;for(int i=0;i<menu.slots.size();i++){var slot=menu.slots.get(i);if(slot.container==mc.player.getInventory()&&PestItems.id(slot.getItem()).equals(cfg().bait)&&slot.getItem().getCount()>=want){source=i;break;}}
                if(source<0)return fail(mc,"нет нужной приманки в одном инвентарном стеке; покупки выключены");if(!menu.getCarried().isEmpty())return fail(mc,"курсор занят");beforeCursor=menu.slots.get(source).getItem().getCount();click(mc,source,0);setPhase(Phase.PICK);return Result.RUNNING;
            }
            next(mc);return active()?Result.RUNNING:Result.DONE;
        }
        if(phase==Phase.RELEASE){
            if(ack()&&(countPests(mc)==0||fullBefore&&fresh(fullAt)&&fullAt>sentAt&&!full.contains(point.id))){released=releasedAny=true;setPhase(Phase.MENU);status="Выпуск подтверждён; песты ещё не убиты";return Result.RUNNING;}
            if(age>70)return fail(mc,"выпуск не подтверждён");return Result.RUNNING;
        }
        if(phase==Phase.PICK){if(cursorAck()&&PestItems.id(PestMenuEvidence.observed(menu,-1)).equals(cfg().bait)&&PestMenuEvidence.observed(menu,-1).getCount()==beforeCursor){remaining=want;setPhase(Phase.DEPOSIT);}else if(age>60)return fail(mc,"поднятие bait не подтверждено");return Result.RUNNING;}
        if(phase==Phase.DEPOSIT){
            if(remaining==0){if(menu.getCarried().isEmpty()){setPhase(Phase.VERIFY);return Result.RUNNING;}if(!menu.slots.get(source).getItem().isEmpty())return fail(mc,"исходный слот занят; ничего не выбрасываю");click(mc,source,0);setPhase(Phase.RESTORE);return Result.RUNNING;}
            if(age==1||cursorAck()){if(age!=1){if(PestMenuEvidence.observed(menu,-1).getCount()!=beforeCursor-1)return fail(mc,"bait transfer не подтверждён");remaining--;beforeCursor--;if(remaining==0){age=0;return Result.RUNNING;}}
                if(!PestItems.id(menu.getCarried()).equals(cfg().bait)||beforeCursor<1)return fail(mc,"курсор/приманка изменились");click(mc,baitSlot,1);age=1;}
            if(age>60)return fail(mc,"нет подтверждения внесения bait");return Result.RUNNING;
        }
        if(phase==Phase.RESTORE){if(cursorAck()&&PestMenuEvidence.observed(menu,-1).isEmpty()&&PestMenuEvidence.stamp(menu,source)>revision&&PestItems.id(PestMenuEvidence.observed(menu,source)).equals(cfg().bait)&&PestMenuEvidence.observed(menu,source).getCount()==beforeCursor){setPhase(Phase.VERIFY);}else if(age>60)return fail(mc,"остаток bait не подтверждён в исходном слоте");return Result.RUNNING;}
        if(phase==Phase.VERIFY){if(menu.getCarried().isEmpty()&&PestMenuEvidence.observed(menu,-1).isEmpty()&&PestMenuEvidence.stamp(menu,baitSlot)>0&&baitCount(PestMenuEvidence.observed(menu,baitSlot))>=beforeBait+want){filled=true;status="Ловушка #"+point.id+": количество bait подтверждено";next(mc);return active()?Result.RUNNING:Result.DONE;}if(age>60)return fail(mc,"новое количество bait не подтверждено");}
        return Result.RUNNING;
    }
    private void endPet(Minecraft mc){closeOwn(mc);releaseLease(mc);if(petAfter){next(mc);}else{setPhase(Phase.NAV);status="Mosquito подтверждён, продолжаю обслуживание";}}
    private int find(Minecraft mc,String name){int result=-1;for(int i=0;i<menu.slots.size();i++)if(menu.slots.get(i).container!=mc.player.getInventory()&&PestItems.name(menu.slots.get(i).getItem()).equals(name)){if(result>=0)return -1;result=i;}return result;}
    private int findContains(Minecraft mc,String name){int result=-1;for(int i=0;i<menu.slots.size();i++)if(menu.slots.get(i).container!=mc.player.getInventory()&&PestItems.name(menu.slots.get(i).getItem()).equals(name)){if(result>=0)return -1;result=i;}return result;}
    private int countPests(Minecraft mc){for(var s:menu.slots)if(s.container!=mc.player.getInventory()){var m=Pattern.compile("(?i)(?:trapped pests|pests trapped|caught pests)[: ]+(\\d+)").matcher(PestItems.lore(PestMenuEvidence.observed(menu,s.index)));if(m.find())return Integer.parseInt(m.group(1));}return -1;}
    private int baitCount(ItemStack s){var m=COUNT.matcher(PestItems.lore(s));if(!m.find())return -1;try{return Integer.parseInt(m.group(1).replace(",",""));}catch(NumberFormatException e){return -1;}}
    private boolean cursorAck(){return PestMenuEvidence.stamp(menu,-1)>revision;}
    private boolean ack(){return PestMenuEvidence.revision(menu)>revision;}
    private void click(Minecraft mc,int index,int button){if(!ownsScreen(mc.screen)||index<0||index>=menu.slots.size()||++clicks>210)throw new IllegalStateException("trap menu click budget/context");revision=PestMenuEvidence.revision(menu);mc.gameMode.handleContainerInput(menu.containerId,index,button,ContainerInput.PICKUP,mc.player);}
    private void closeOwn(Minecraft mc){if(ownsScreen(mc.screen)&&menu.getCarried().isEmpty())mc.player.closeContainer();menu=null;screen=null;expectedTitle=null;expectUntil=0;}
    private void releaseLease(Minecraft mc){if(NavigationInput.owns(this)){SmoothTurn.stop();NavigationInput.release(this);}}
    private Result fail(Minecraft mc,String why){cancel(mc);status="Ловушки: "+why+". Если bait на курсоре, верни вручную; мод ничего не выбрасывает";return Result.FAIL;}
    public void cancel(Minecraft mc){walker.stop(mc);releaseLease(mc);phase=Phase.IDLE;queue.clear();expectedTitle=null;expectUntil=0;menu=null;screen=null;PestMenuEvidence.reset();}
    public List<Vec3> navigationPath(){var out=new ArrayList<Vec3>();for(var p:walker.path()){if(out.size()>=64)break;out.add(new Vec3(p.x,p.y,p.z));}return List.copyOf(out);}
    public Vec3 destination(){return point!=null&&active()?new Vec3(point.x,point.y,point.z):null;}
    private PestTraps(){}
}
