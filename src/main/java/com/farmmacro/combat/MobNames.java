package com.farmmacro.combat;
import com.farmmacro.panic.ServerText;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;
import java.util.regex.Pattern;

/** Real Mob hitboxes only. Armor stands supply a name, never become attack/aim targets. */
public final class MobNames {
    private static final Pattern LEVEL=Pattern.compile("(?i)^\\[\\s*Lv\\s*[\\d,]+\\s*]\\s*"),
        HEALTH=Pattern.compile("(?i)\\s+[\\d,.]+[kmbt]?(?:/[\\d,.]+[kmbt]?)?\\s*$");
    private static final Set<String> KNOWN=MobCatalog.GROUPS.stream().flatMap(g->g.mobs().stream())
        .filter(n->!n.isBlank()).map(n->n.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static net.minecraft.client.multiplayer.ClientLevel cachedWorld;
    private static final Map<Mob,String> CONFIRMED=new WeakHashMap<>();
    private static final Pattern ZEALOT=Pattern.compile("(?i)(?<![a-z])(?:special\\s+)?zealot(?:\\s+bruiser)?(?![a-z])");
    public record Named(Mob mob,String name){}
    public static String clean(String raw){
        String s=ServerText.clean(raw);s=LEVEL.matcher(s).replaceFirst("");
        s=HEALTH.matcher(s).replaceFirst("").strip();
        var z=ZEALOT.matcher(s);
        if(z.find()){
            String n=z.group().toLowerCase(Locale.ROOT);
            if(n.startsWith("special"))return "Special Zealot";
            return n.contains("bruiser")?"Zealot Bruiser":"Zealot";
        }
        String boss=BossTargets.canonical(s);return boss.isEmpty()?s:boss;
    }
    public static List<Named> nearby(Minecraft mc,double radius){
        if(cachedWorld!=mc.level){cachedWorld=mc.level;CONFIRMED.clear();}
        if(mc.player==null||mc.level==null)return List.of();
        CONFIRMED.keySet().removeIf(m->m.isRemoved()||!m.isAlive());
        com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.NAME_SCANS);
        List<Mob> mobs=new ArrayList<>();Map<Long,List<Label>> labels=new HashMap<>();
        double rr=(radius+4)*(radius+4);
        for(Entity e:com.farmmacro.util.LoadedEntities.get(mc)){
            if(e.distanceToSqr(mc.player)>rr)continue;
            if(e instanceof Mob m && m.isAlive()&&!m.isDeadOrDying()&&!m.isRemoved())mobs.add(m);
            else if(e instanceof ArmorStand && e.getCustomName()!=null){
                String raw=e.getCustomName().getString(),candidate=clean(raw);
                if(candidate.codePoints().anyMatch(Character::isLetter)&&(KNOWN.contains(candidate.toLowerCase(Locale.ROOT))||raw.indexOf('❤')>=0||raw.indexOf('♥')>=0||raw.indexOf('\uE010')>=0))
                    labels.computeIfAbsent(cell((int)Math.floor(e.getX()/4),(int)Math.floor(e.getZ()/4)),k->new ArrayList<>()).add(new Label(e,candidate));
            }
        }
        // One armor-stand label has one nearest compatible real body, independent of iteration order.
        Map<Entity,LabelMatch> owners=new IdentityHashMap<>();
        for(Mob m:mobs){
            if(!generic(m))continue;
            int cx=(int)Math.floor(m.getX()/4),cz=(int)Math.floor(m.getZ()/4);
            for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
                for(Label parsed:labels.getOrDefault(cell(x,z),List.of())){
                    Entity label=parsed.entity();String candidate=parsed.name();
                    if(AimAssist.zealot(candidate)&&!m.getType().getDescriptionId().endsWith(".enderman"))continue;
                    if(!BossTargets.compatible(candidate,m.getType().getDescriptionId()))continue;
                    double dx=label.getX()-m.getX(),dz=label.getZ()-m.getZ(),dy=label.getY()-m.getBoundingBox().maxY;
                    if(dx*dx+dz*dz>2.25||label.getY()<m.getBoundingBox().minY+.5||dy>3.5)continue;
                    double score=4*(dx*dx+dz*dz)+dy*dy;
                    LabelMatch old=owners.get(label);
                    if(old==null||score<old.score()||score==old.score()&&m.getId()<old.mob().getId())
                        owners.put(label,new LabelMatch(m,candidate,score,label.getId()));
                }
        }
        Map<Mob,LabelMatch> names=new IdentityHashMap<>();
        for(LabelMatch match:owners.values()){
            LabelMatch old=names.get(match.mob());
            // A generic health/name line must not win over a confirmed Zealot label for the same body.
            boolean exact=AimAssist.zealot(match.name()),oldExact=old!=null&&AimAssist.zealot(old.name());
            if(old==null||exact&&!oldExact||exact==oldExact&&(match.score()<old.score()||match.score()==old.score()&&match.labelId()<old.labelId()))names.put(match.mob(),match);
        }
        List<Named> out=new ArrayList<>();
        for(Mob m:mobs){
            String name=m.getCustomName()!=null?clean(m.getCustomName().getString()):"";
            boolean confirmed=!generic(m);
            if(!confirmed){
                LabelMatch label=names.get(m);
                if(label!=null){name=label.name();confirmed=true;}
                else if(CONFIRMED.containsKey(m)){name=CONFIRMED.get(m);confirmed=true;}
            }
            if(confirmed)CONFIRMED.put(m,name);
            if(name.isBlank())name=canonical(m);
            out.add(new Named(m,name));
        }
        return out;
    }
    private record Label(Entity entity,String name){}
    private record LabelMatch(Mob mob,String name,double score,int labelId){}
    private static boolean generic(Mob m){
        if(m.getCustomName()==null)return true;
        String name=clean(m.getCustomName().getString());
        return name.isBlank()||name.equalsIgnoreCase(canonical(m))||name.equals(m.getType().getDescriptionId());
    }
    private static String canonical(Mob m){
        String key=m.getType().getDescriptionId();key=key.substring(key.lastIndexOf('.')+1);
        StringBuilder name=new StringBuilder();
        for(String part:key.split("_"))if(!part.isBlank()){
            if(!name.isEmpty())name.append(' ');name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }
    private static long cell(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private MobNames(){}
}
