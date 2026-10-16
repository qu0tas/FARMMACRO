package com.farmmacro.stats;
import com.farmmacro.config.ModConfig;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Device-wide receipt/observed-XP totals, never executable state or an account/profile claim. */
public final class StatsLedger {
 public record Totals(int schema,String day,long income,long expense,double xp,long dayIncome,long dayExpense,double dayXp,long receipts){}
 private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
 private static final AtomicReference<Write> pending=new AtomicReference<>();private static final AtomicBoolean writing=new AtomicBoolean();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Kaelix-StatsIO");t.setDaemon(true);return t;});
 private static final Object LOCK=new Object();private static Totals totals=empty();private static volatile String status="Локальные итоги не сохраняются";private static volatile long generation;private record Write(Totals data,long epoch){} // generation is checked around disk work.
 private static Totals empty(){return new Totals(1,LocalDate.now().toString(),0,0,0,0,0,0,0);}
 private static Path file(){return ModConfig.modDir().resolve("statistics.json");}
 private static boolean amount(long n){return n>=0&&n<=1_000_000_000_000_000L;}
 private static boolean valid(Totals t){try{return t!=null&&t.schema==1&&LocalDate.parse(t.day)!=null&&amount(t.income)&&amount(t.expense)&&amount(t.dayIncome)&&amount(t.dayExpense)&&t.dayIncome<=t.income&&t.dayExpense<=t.expense&&Double.isFinite(t.xp)&&t.xp>=0&&t.xp<=1e15&&Double.isFinite(t.dayXp)&&t.dayXp>=0&&t.dayXp<=t.xp&&amount(t.receipts);}catch(Exception e){return false;}}
 public static void init(){synchronized(LOCK){generation++;pending.set(null);}totals=empty();if(!ModConfig.INSTANCE.telemetry.save){status="Локальные итоги не сохраняются";return;}try{if(Files.isRegularFile(file(),LinkOption.NOFOLLOW_LINKS)&&Files.size(file())<=4096){var t=JSON.fromJson(Files.readString(file()),Totals.class);if(!valid(t))throw new java.io.IOException("schema");totals=t;}status="Локальные итоги: включены, все профили устройства";}catch(Exception e){status="Итоги не прочитаны ("+e.getClass().getSimpleName()+")";}}
 private static void roll(){if(!totals.day.equals(LocalDate.now().toString()))totals=new Totals(1,LocalDate.now().toString(),totals.income,totals.expense,totals.xp,0,0,0,totals.receipts);}
 public static Totals totals(){roll();return totals;}public static String status(){return status;}
 public static void add(long income,long expense,double xp){if(!ModConfig.INSTANCE.telemetry.save)return;roll();var t=totals;var n=new Totals(1,t.day,t.income+income,t.expense+expense,t.xp+xp,t.dayIncome+income,t.dayExpense+expense,t.dayXp+xp,t.receipts+(income!=0||expense!=0?1:0));if(!valid(n)){status="Лимит статистики: итоги не увеличены";return;}totals=n;}
 public static void checkpoint(){if(!ModConfig.INSTANCE.telemetry.save)return;pending.set(new Write(totals,generation));if(writing.compareAndSet(false,true))IO.execute(StatsLedger::drain);}
 private static void drain(){try{for(Write write;(write=pending.getAndSet(null))!=null;){long epoch=write.epoch();Totals t=write.data();if(epoch!=generation)continue;if(!ModConfig.INSTANCE.telemetry.save)continue;Path tmp=null;try{Files.createDirectories(file().getParent());if(Files.isSymbolicLink(file()))throw new java.io.IOException("symlink");tmp=Files.createTempFile(file().getParent(),"stats-",".tmp");Files.writeString(tmp,JSON.toJson(t));synchronized(LOCK){if(epoch!=generation||!ModConfig.INSTANCE.telemetry.save)continue;try{Files.move(tmp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file(),StandardCopyOption.REPLACE_EXISTING);}}status="Локальные итоги сохранены";}catch(Exception e){status="Итоги не записаны ("+e.getClass().getSimpleName()+")";}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}}}finally{writing.set(false);if(pending.get()!=null&&writing.compareAndSet(false,true))IO.execute(StatsLedger::drain);}}
 public static void disabled(){synchronized(LOCK){generation++;pending.set(null);}status="Сохранение выключено; прежний файл не удалён";}
 public static void reset(){synchronized(LOCK){generation++;pending.set(null);}totals=empty();checkpoint();}
 private StatsLedger(){}
}
