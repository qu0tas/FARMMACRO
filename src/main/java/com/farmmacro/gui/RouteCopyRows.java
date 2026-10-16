package com.farmmacro.gui;
import com.farmmacro.route.*;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;
/** Explicit copy/save; never modifies the original file or starts playback. */
final class RouteCopyRows {
 private static int dx,dy,dz;private static boolean mx,mz;
 static void build(List<Rows.Row> rows,Screen parent,Runnable refresh){
  var rb=RouteBuffer.INSTANCE;
  java.util.function.BooleanSupplier idle=()->!rb.isEmpty()&&!MacroManager.INSTANCE.isActive()&&!MacroManager.INSTANCE.isRecording();
  rows.add(new Rows.Section("Копия и перенос маршрута").key("r.copy"));
  rows.add(new Rows.Note("Сохранит ОТДЕЛЬНЫЙ маршрут. X — восток/запад, Y — этаж, Z — юг/север; шаг1блок. Зеркало вокруг центра первого блока, камера/направления спуска отражаются вместе. Оригинал не меняется.",Ui.SUB));
  rows.add(new Rows.Number("Сдвиг X, блоков",null,()->dx,v->dx=(int)v,-4096,4096,1,8,Rows::num).integer().enabledIf(idle));
  rows.add(new Rows.Number("Сдвиг Y, блоков","Например +4 — следующий этаж",()->dy,v->dy=(int)v,-4096,4096,1,4,Rows::num).integer().enabledIf(idle));
  rows.add(new Rows.Number("Сдвиг Z, блоков",null,()->dz,v->dz=(int)v,-4096,4096,1,8,Rows::num).integer().enabledIf(idle));
  rows.add(new Rows.Toggle("Зеркало X",null,()->mx,v->mx=v).enabledIf(idle));
  rows.add(new Rows.Toggle("Зеркало Z",null,()->mz,v->mz=v).enabledIf(idle));
  rows.add(new Rows.Note("Координаты блоков у действий переносятся, но текст команд НЕ переписывается. Альтернативные файлы маршрута исключаются из копии. После сохранения проверь точки/спуски/углы в редакторе. Геометрия не проверяет новый этаж на препятствия. Без камеры схемы/привязки угол выставляешь вручную.",Ui.WARN));
  rows.add(new Rows.Buttons(new Rows.Btn("Сохранить копию…",Rows.Style.PRIMARY,()->{
   if(!idle.getAsBoolean())return;
   var mc=Minecraft.getInstance();var snapshot=rb.route().copy();
   // Freeze linked presets before the pure reflection. Keep source bindings untouched.
   for(var c:snapshot.settings.camera.changes){c.yaw=c.yaw();c.pitch=c.pitch();c.presetId=-1;}
   final PointRoute copy;
   try{copy=RouteCopies.copy(snapshot,dx,dy,dz,mx,mz);}catch(RuntimeException e){MacroManager.INSTANCE.msgPublic(mc,"§cНе удалось сделать копию: проверь координаты маршрута");return;}
   final String original=rb.loadedName();
   mc.setScreen(new SaveMacroScreen(parent,new SaveMacroScreen.Target(){
    public String title(){return "Сохранить копию маршрута";}
    public String subtitle(){return copy.points.size()+" точек; оригинал не перезаписывается";}
    public String placeholder(){return "Новое имя, например «Ферма этаж2»";}
    public boolean exists(String name){return RouteStorage.INSTANCE.exists(name);}
    public boolean save(String name){if(name.equals(original)||RouteStorage.INSTANCE.exists(name)){MacroManager.INSTANCE.msgPublic(mc,"§cДля копии нужно новое свободное имя");return false;}return RouteStorage.INSTANCE.save(name,copy);}
    public void saved(String name){rb.load(name,copy);refresh.run();}
   },(original==null?"Маршрут":original)+"_copy"));
  }).enabledIf(idle),new Rows.Btn("Сбросить сдвиг",Rows.Style.SECONDARY,()->{dx=dy=dz=0;mx=mz=false;})));
 }
 private RouteCopyRows(){}
}
