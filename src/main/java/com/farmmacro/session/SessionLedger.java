package com.farmmacro.session;
import com.farmmacro.config.ModConfig;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Passive bounded disk checkpoint; it never starts, resumes, connects or schedules anything. */
public final class SessionLedger {
 public record Snapshot(int schema,long recordedAt,long elapsedMs,long activeMs,long breakMs,int cycles,String state){}
 private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
 private static final AtomicReference<Snapshot> pending=new AtomicReference<>();private static final AtomicBoolean draining=new AtomicBoolean();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Kaelix-SessionIO");t.setDaemon(true);return t;});
 private static volatile Snapshot last;private static volatile String error="";private static volatile boolean enabled;
 private static Path file(){return ModConfig.modDir().resolve("session-state.json");}
 public static void init(){enabled=ModConfig.INSTANCE.sessionSave;last=null;error="";if(!enabled)return;try{if(Files.isSymbolicLink(file())||!Files.isRegularFile(file())||Files.size(file())>8192)return;var s=JSON.fromJson(Files.readString(file()),Snapshot.class);
  if(s!=null&&s.schema()==1&&s.recordedAt()>0&&s.elapsedMs()>=0&&s.elapsedMs()<=31_536_000_000L&&s.activeMs()>=0&&s.activeMs()<=s.elapsedMs()&&s.breakMs()>=0&&s.breakMs()<=s.elapsedMs()&&s.cycles()>=0&&s.cycles()<=100000&&s.state()!=null&&s.state().matches("[A-Z_]{1,32}"))last=s;
 }catch(Exception e){error="Не удалось прочитать историю ("+e.getClass().getSimpleName()+")";}}
 public static void record(Snapshot s){if(!ModConfig.INSTANCE.sessionSave)return;enabled=true;last=s;pending.set(s);if(draining.compareAndSet(false,true))IO.execute(SessionLedger::drain);}
 private static void drain(){try{for(Snapshot s;(s=pending.getAndSet(null))!=null;){if(!enabled)continue;Path tmp=null;try{Files.createDirectories(file().getParent());if(Files.isSymbolicLink(file()))throw new java.io.IOException("symlink");tmp=Files.createTempFile(file().getParent(),"session-",".tmp");Files.writeString(tmp,JSON.toJson(s),StandardCharsets.UTF_8);try{Files.move(tmp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file(),StandardCopyOption.REPLACE_EXISTING);}error="";}catch(Exception e){error="История не сохранена ("+e.getClass().getSimpleName()+")";}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}}}finally{draining.set(false);if(pending.get()!=null&&draining.compareAndSet(false,true))IO.execute(SessionLedger::drain);}}
 public static void disabled(){enabled=false;pending.set(null);}
 public static void clear(){record(new Snapshot(1,System.currentTimeMillis(),0,0,0,0,"CLEARED"));}
 public static String summary(){if(!ModConfig.INSTANCE.sessionSave)return "Сохранение истории выключено";if(!error.isEmpty())return error;var s=last;if(s==null)return "Истории пока нет";return "Последняя запись: "+s.state()+" • всего "+s.elapsedMs()/60000+" мин • работа "+s.activeMs()/60000+" мин • перерыв "+s.breakMs()/60000+" мин • циклов "+s.cycles()+". Только справка, не автовосстановление.";}
 private SessionLedger(){}
}
