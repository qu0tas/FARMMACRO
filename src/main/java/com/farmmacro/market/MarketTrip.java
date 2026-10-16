package com.farmmacro.market;
import com.farmmacro.panic.*;
import com.farmmacro.pest.PestHunter;
import com.farmmacro.route.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One exact recorded landing per requested /hub or /warp garden or /is. Not a time-only teleport grace. */
public final class MarketTrip {
 public static final MarketTrip INSTANCE=new MarketTrip();
 private SaleSettings.Point goal;private long sentAt;private boolean position,respawn,abilities,attributes;private int stable;private List<ItemStack> inventory=List.of();
 public static boolean hypixel(Minecraft mc){if(mc.getCurrentServer()==null||mc.isSingleplayer())return false;String host=mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":")[0];return host.equals("hypixel.net")||host.endsWith(".hypixel.net");}
 public static String realm(Minecraft mc){if(!hypixel(mc)||mc.player==null||mc.level==null)return "";if(PestHunter.garden(mc))return "GARDEN";
  for(String line:PanicDetector.sidebar(mc)){String s=ServerText.clean(line);if(Set.of("Village","Bazaar Alley","Hub").contains(s))return "HUB";if(Set.of("Your Island","Private Island").contains(s))return "ISLAND";}
  if(mc.getConnection()!=null)for(var p:mc.getConnection().getListedOnlinePlayers()){var d=p.getTabListDisplayName();if(d==null)continue;String s=ServerText.clean(d.getString());if(s.equals("Area: Hub"))return "HUB";if(s.equals("Area: Private Island"))return "ISLAND";}
  return "";
 }
 public static SaleSettings.Point point(Minecraft mc){var p=new SaleSettings.Point();p.saved=true;p.server=mc.getCurrentServer().ip;p.dimension=mc.level.dimension().identifier().toString();p.realm=realm(mc);p.x=mc.player.getX();p.y=mc.player.getY();p.z=mc.player.getZ();p.yaw=mc.player.getYRot();p.pitch=mc.player.getXRot();return p;}
 public boolean waiting(){return goal!=null&&StashSales.INSTANCE.active()&&sentAt>0&&System.currentTimeMillis()-sentAt<=6000;}
 public boolean loadingScreen(net.minecraft.client.gui.screens.Screen s){return waiting()&&s instanceof LevelLoadingScreen;}
 public void begin(Minecraft mc,SaleSettings.Point p,String command){
  if(!p.saved||!hypixel(mc)||!p.server.equals(mc.getCurrentServer().ip)||!Set.of("hub","warp garden","is").contains(command)||!NavigationInput.owns(StashSales.INSTANCE)||mc.screen!=null)throw new IllegalStateException("Trip context/recorded landing");
  if(command.equals("hub")&&!p.realm.equals("HUB")||command.equals("warp garden")&&!p.realm.equals("GARDEN")||command.equals("is")&&!p.realm.equals("ISLAND"))throw new IllegalStateException("Trip realm/command mismatch");
  goal=p;sentAt=System.currentTimeMillis();position=respawn=abilities=attributes=false;stable=0;inventory=SalePolicy.snapshot(mc);
  mc.player.connection.sendCommand(command);
 }
 public boolean claimRespawn(ClientboundRespawnPacket packet){
  if(!waiting())return false;var info=packet.commonPlayerSpawnInfo();if(respawn||!goal.dimension.equals(info.dimension().identifier().toString())||info.gameType()!=GameType.SURVIVAL){StashSales.INSTANCE.cancel(Minecraft.getInstance(),"неожиданный respawn при варпе");return false;}
  respawn=true;return true;
 }
 
 public boolean spawnAbilities(boolean fly,boolean canFly,float walk,float speed){if(!waiting()||!respawn||abilities||!Float.isFinite(walk)||!Float.isFinite(speed)||walk<0||walk>1||speed<0||speed>1)return false;abilities=true;return true;}
 public boolean spawnAttributes(int entity,java.util.List<net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot> values){var mc=Minecraft.getInstance();if(!waiting()||!respawn||attributes||mc.player==null||mc.player.getId()!=entity||values==null||values.size()>128||values.stream().anyMatch(v->!Double.isFinite(v.base())||v.modifiers().size()>128||v.modifiers().stream().anyMatch(m->!Double.isFinite(m.amount()))))return false;attributes=true;return true;}
 public boolean move(Minecraft mc,String kind,double distance){
  if(!waiting())return false;
  if(!"телепорта".equals(kind)||position||!Double.isFinite(distance)||mc.player==null||mc.level==null||!Double.isFinite(mc.player.getX())||!Double.isFinite(mc.player.getY())||!Double.isFinite(mc.player.getZ())||!Float.isFinite(mc.player.getYRot())||!Float.isFinite(mc.player.getXRot())||!NavigationInput.owns(StashSales.INSTANCE)||!goal.dimension.equals(mc.level.dimension().identifier().toString())||mc.player.position().distanceTo(new Vec3(goal.x,goal.y,goal.z))>1.25||Math.abs(mc.player.getY()-goal.y)>.5||ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),goal.yaw,goal.pitch)>6){StashSales.INSTANCE.cancel(mc,"варп пришёл не в записанную точку/камеру");return false;}
  position=true;return true;
 }
 public boolean tick(Minecraft mc){
  if(goal==null||System.currentTimeMillis()-sentAt>6000)throw new IllegalStateException("Warp not confirmed in 6s");
  if(mc.player==null||mc.level==null)return false;
  boolean valid=position&&goal.realm.equals(realm(mc))&&goal.dimension.equals(mc.level.dimension().identifier().toString())&&mc.player.position().distanceTo(new Vec3(goal.x,goal.y,goal.z))<=1.25&&mc.player.getDeltaMovement().length()<.1&&mc.level.hasChunkAt(mc.player.blockPosition())&&SalePolicy.same(inventory,SalePolicy.snapshot(mc));
  stable=valid?stable+1:0;if(stable<3)return false;
  reset();PanicDetector.INSTANCE.beginRun();return true;
 }
 public void reset(){goal=null;sentAt=0;inventory=List.of();position=respawn=abilities=attributes=false;stable=0;}
 private MarketTrip(){}
}
