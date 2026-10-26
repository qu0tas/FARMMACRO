package com.farmmacro.fishing;
import com.farmmacro.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
/** Upstream command names, routed to the exact same GUI preferences/manual run. */
public final class FishingCommands {
 private static void feedback(FabricClientCommandSource s){s.sendFeedback(Component.literal("Kaelix AutoFish · "+AutoFish.INSTANCE.status()+" · разрешение "+ModConfig.INSTANCE.fishing.enabled+" · recast "+ModConfig.INSTANCE.fishing.recast+" · Fireveil "+ModConfig.INSTANCE.fishing.fireveil+" · debug "+ModConfig.INSTANCE.fishing.debug));}
 private static LiteralArgumentBuilder<FabricClientCommandSource> flag(String name,java.util.function.Consumer<Boolean> set){
  return ClientCommands.literal(name).then(ClientCommands.literal("on").executes(c->{set.accept(true);ModConfig.save();feedback(c.getSource());return 1;}))
   .then(ClientCommands.literal("off").executes(c->{set.accept(false);ModConfig.save();feedback(c.getSource());return 1;}));
 }
 public static void register(){ClientCommandRegistrationCallback.EVENT.register((d,registry)->d.register(ClientCommands.literal("autofisher")
  .executes(c->{feedback(c.getSource());return 1;})
  .then(ClientCommands.literal("on").executes(c->{ModConfig.INSTANCE.fishing.enabled=true;ModConfig.save();AutoFish.INSTANCE.armFromCommand(Minecraft.getInstance());feedback(c.getSource());return 1;}))
  .then(ClientCommands.literal("off").executes(c->{AutoFish.INSTANCE.stop("команда off");ModConfig.INSTANCE.fishing.enabled=false;ModConfig.save();feedback(c.getSource());return 1;}))
  .then(flag("recast",v->ModConfig.INSTANCE.fishing.recast=v))
  .then(flag("debug",v->ModConfig.INSTANCE.fishing.debug=v))
  .then(flag("fireveil",v->{AutoFish.INSTANCE.stop("настройка Fireveil изменена");ModConfig.INSTANCE.fishing.fireveil=v;}))));}
 private FishingCommands(){}
}
