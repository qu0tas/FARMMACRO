package com.farmmacro.session;
import com.farmmacro.macro.*;
import com.farmmacro.config.ModConfig;
import com.farmmacro.route.*;
import com.farmmacro.panic.PanicHandoff;
import com.farmmacro.pest.PestItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Memory-only permission for one exact saved source; never restored from disk or transferable profiles. */
public final class ResumeTicket {
 private final String hash,server,dimension,area;private final Object world;private final ModConfig config;
 private final double x,y,z;private final float yaw,pitch;private final ItemStack hand;private final boolean flying;private final float health;
 private ResumeTicket(Minecraft mc){hash=signature();server=RouteBuffer.worldId(mc);dimension=RouteBuffer.dimensionId(mc);world=mc.level;config=ModConfig.INSTANCE;
  var m=MacroManager.INSTANCE;x=m.hasSavedPosition()?m.getSavedX():mc.player.getX();y=m.hasSavedPosition()?m.getSavedY():mc.player.getY();z=m.hasSavedPosition()?m.getSavedZ():mc.player.getZ();yaw=mc.player.getYRot();pitch=mc.player.getXRot();hand=mc.player.getMainHandItem().copy();flying=mc.player.getAbilities().flying;health=mc.player.getHealth();area=area(mc);}
 public static ResumeTicket live(Minecraft mc){return mc.player!=null&&mc.level!=null?new ResumeTicket(mc):null;}
 public static ResumeTicket disconnect(Minecraft mc,ResumeTicket fallback){
  if(mc.player!=null&&mc.level!=null&&RouteBuffer.worldId(mc)!=null)return capture(mc);
  return fallback!=null&&fallback.sourceValid()?new ResumeTicket(fallback):null;
 }
 private ResumeTicket(ResumeTicket t){hash=t.hash;server=t.server;dimension=t.dimension;area=t.area;world=t.world;config=t.config;hand=t.hand.copy();flying=t.flying;health=t.health;yaw=t.yaw;pitch=t.pitch;var m=MacroManager.INSTANCE;x=m.getSavedX();y=m.getSavedY();z=m.getSavedZ();}
 public static ResumeTicket capture(Minecraft mc){if(mc.player==null||mc.level==null||!MacroManager.INSTANCE.savedMatchesSource())return null;return new ResumeTicket(mc);}
 public static String signature(){try{
  var digest=java.security.MessageDigest.getInstance("SHA-256");
  try(var out=new java.io.OutputStreamWriter(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(),digest),java.nio.charset.StandardCharsets.UTF_8)){
   var m=MacroManager.INSTANCE;var gson=new com.google.gson.Gson();out.write(m.getSourceKind().name());
   if(m.getSourceKind()==MacroManager.SourceKind.ROUTE)gson.toJson(RouteBuffer.INSTANCE.route(),out);
   else{gson.toJson(m.getFrames(),out);gson.toJson(m.getSettings(),out);} }
  return HexFormat.of().formatHex(digest.digest());
 }catch(Exception e){throw new IllegalStateException("Source fingerprint unavailable");}}
 private static String area(Minecraft mc){String known=com.farmmacro.market.MarketTrip.realm(mc);if(!known.isEmpty())return known;
  if(mc.getConnection()!=null)for(var i:mc.getConnection().getListedOnlinePlayers()){var t=i.getTabListDisplayName();if(t!=null){String s=com.farmmacro.panic.ServerText.clean(t.getString());if(s.startsWith("Area: "))return s;}}return "";}
 public boolean sourceValid(){return config==ModConfig.INSTANCE&&MacroManager.INSTANCE.savedMatchesSource()&&hash.equals(signature());}
 public boolean ready(Minecraft mc,boolean reconnect,boolean walking){
  if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null||!mc.isWindowActive()||mc.isPaused()||!mc.player.isAlive()||PanicHandoff.pending()||MacroManager.INSTANCE.isActive()||MacroManager.INSTANCE.isRecording()||RouteEditor.isActive()||NavigationInput.busy())return false;
  if(com.farmmacro.market.StashSales.INSTANCE.active()||com.farmmacro.visitor.Visitors.INSTANCE.active()||com.farmmacro.loadout.GlobalLoadouts.INSTANCE.active()||com.farmmacro.pest.PestHunter.INSTANCE.active()||com.farmmacro.combat.AimAssist.INSTANCE.keyActive()||com.farmmacro.combat.SpecialZealotHunt.INSTANCE.active()||com.farmmacro.combat.SpecialZealotHunt.INSTANCE.pending()||com.farmmacro.combat.CombatSway.INSTANCE.active()||MouseHold.INSTANCE.isManual())return false;
  if(!Objects.equals(server,RouteBuffer.worldId(mc))||!Objects.equals(dimension,RouteBuffer.dimensionId(mc))||!area.isEmpty()&&!area.equals(area(mc))||!reconnect&&mc.level!=world)return false;
  var h=mc.player.getMainHandItem();if(hand.isEmpty()!=h.isEmpty()||!hand.isEmpty()&&(hand.getItem()!=h.getItem()||!PestItems.id(hand).equals(PestItems.id(h))||!PestItems.uuid(hand).equals(PestItems.uuid(h))))return false;
  if(!Double.isFinite(mc.player.getX())||!Double.isFinite(mc.player.getY())||!Double.isFinite(mc.player.getZ()))return false;
  if(!reconnect&&mc.player.getHealth()<health-.01||!walking&&mc.player.getAbilities().flying!=flying||walking&&flying&&!FlightCapability.allowed(mc.player))return false;
  if(!walking&&mc.player.distanceToSqr(x,y,z)>(reconnect?.75*.75:.35*.35))return false;
  return reconnect||ReturnCore.angle(mc.player.getYRot(),mc.player.getXRot(),yaw,pitch)<=4;
 }
 public double x(){return x;}public double y(){return y;}public double z(){return z;}
 private ResumeTicket(){throw new UnsupportedOperationException();}
}
