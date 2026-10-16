package com.farmmacro.notify;
import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.EventLog;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
/** Bounded outbound-only webhook transport. No network request is retried automatically. */
public final class DiscordNotify {
 private static final ThreadPoolExecutor POOL=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"Kaelix-Discord");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private static volatile String status="Уведомления ещё не отправлялись",url;private static volatile long generation,blockedUntil;private static volatile boolean suspended;
 private static final LinkedHashMap<String,Long> recent=new LinkedHashMap<>();private static long lastQueued,lastPanicQueued,lastPost;private static int skipped;
 private static HttpClient client;
 private static Path file(){return ModConfig.modDir().resolve("discord-webhook.txt");}
 public static synchronized String url(){if(url==null){try{url=Files.isRegularFile(file(),LinkOption.NOFOLLOW_LINKS)&&Files.size(file())<=2048?Files.readString(file(),StandardCharsets.UTF_8).strip():"";if(!Webhook.validUrl(url))url="";}catch(Exception e){url="";status="Не удалось прочитать webhook ("+e.getClass().getSimpleName()+")";}PrivateText.secret(url);}return url;}
 public static String maskedUrl(){return Webhook.masked(url());}
 public static synchronized boolean setUrl(String value){String s=value==null?"":value.strip();if(!s.isEmpty()&&s.equals(maskedUrl()))return true;if(!s.isEmpty()&&!Webhook.validUrl(s))return false;
  Path tmp=null;try{Files.createDirectories(file().getParent());if(Files.isSymbolicLink(file()))throw new java.io.IOException("symlink");
   if(s.isEmpty())Files.deleteIfExists(file());else{tmp=Files.createTempFile(file().getParent(),"discord-",".tmp");try{Files.setPosixFilePermissions(tmp,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}Files.writeString(tmp,s,StandardCharsets.UTF_8);try{Files.move(tmp,file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file(),StandardCopyOption.REPLACE_EXISTING);}}
   url=s;PrivateText.secret(s);resetTransport();status=s.isEmpty()?"Webhook удалён":"Webhook сохранён отдельно; токен скрыт";return true;
  }catch(Exception e){status="Webhook не сохранён ("+e.getClass().getSimpleName()+")";return false;}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
 }
 public static boolean configured(){return Webhook.validUrl(url());}
 public static String status(){long seconds=blockedUntil==0?0:Math.max(0,(blockedUntil-System.nanoTime()+999999999L)/1000000000L);return status+(seconds>0?" • пауза "+seconds+" с":"")+" • очередь "+POOL.getQueue().size()+"/4 • подавлено "+skipped;}
 private static synchronized void resetTransport(){generation++;POOL.getQueue().clear();recent.clear();suspended=false;lastQueued=0;}
 public static synchronized void resetError(){resetTransport();status="Ошибка сброшена вручную; автоматической повторной отправки нет";}
 public static synchronized void settingsChanged(){generation++;POOL.getQueue().clear();status="Настройки Discord изменены; очередь очищена";}
 public static synchronized void disabled(){generation++;POOL.getQueue().clear();status="Discord выключен; очередь очищена";}
 /** Compatibility entrypoint: all existing callers still cross the permission/privacy/anti-spam boundary. */
 public static synchronized void send(String content,Path png,String why){
  var c=ModConfig.INSTANCE;if(!c.webhookEnabled)return;String u=url();if(!Webhook.validUrl(u)){status="Нет корректного webhook";return;}
  long now=System.nanoTime();if(suspended||blockedUntil!=0&&now-blockedUntil<0){skipped++;return;}
  String kind=Set.of("паника","проверка","сессия","переподключение").contains(why==null?"":why)?why:"уведомление";String safe=PrivateText.outgoing(content==null?"":content);String key=kind+":"+safe.replaceAll("[0-9]+","#");
  boolean panic=kind.equals("паника");
  Long duplicateAt=recent.get(key);
  if((panic?lastPanicQueued!=0&&now-lastPanicQueued<5000000000L:lastQueued!=0&&now-lastQueued<5000000000L)||duplicateAt!=null&&now-duplicateAt<Math.max(15,c.webhookCooldownSec)*1000000000L){skipped++;status="Повтор/слишком частое уведомление подавлено";return;}
  if(panic){lastPanicQueued=now;skipped+=POOL.getQueue().size();POOL.getQueue().clear();}
  recent.put(key,now);while(recent.size()>32)recent.remove(recent.keySet().iterator().next());lastQueued=now;
  long epoch=generation;Path image=c.webhookScreenshot&&c.webhookMediaConsent?png:null;boolean media=image!=null;status="Уведомление поставлено в очередь";
  try{POOL.execute(()->post(u,safe,image,kind,epoch,media));}catch(RejectedExecutionException e){skipped++;status="Очередь Discord заполнена; уведомление пропущено";}
 }
 private static boolean allowed(long epoch,String u,boolean media){return generation==epoch&&ModConfig.INSTANCE.webhookEnabled&&u.equals(url)&&!suspended&&(blockedUntil==0||System.nanoTime()-blockedUntil>=0)&&(!media||ModConfig.INSTANCE.webhookScreenshot&&ModConfig.INSTANCE.webhookMediaConsent);}
 private static void post(String u,String content,Path png,String kind,long epoch,boolean media){
  if(!allowed(epoch,u,media))return;
  try{
   long wait=5000000000L-(System.nanoTime()-lastPost);if(lastPost!=0&&wait>0)TimeUnit.NANOSECONDS.sleep(wait);if(!allowed(epoch,u,media))return;
   if(client==null)client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
   String json=Webhook.payload(content);var rb=HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(20));byte[] image=null;
   if(png!=null){Path root=com.farmmacro.panic.PanicShot.dir().toAbsolutePath().normalize(),p=png.toAbsolutePath().normalize();if(p.startsWith(root)&&!Files.isSymbolicLink(p)&&Files.isRegularFile(p)&&Files.size(p)>0&&Files.size(p)<8000000){try(var in=Files.newInputStream(p)){byte[] bytes=in.readNBytes(8000001);byte[] magic={(byte)137,80,78,71,13,10,26,10};if(bytes.length>=8&&bytes.length<8000000&&java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),magic))image=bytes;}}}
   if(image==null)rb.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json,StandardCharsets.UTF_8));
   else{String boundary="Kaelix"+UUID.randomUUID().toString().replace("-","");rb.header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(Webhook.multipart(boundary,json,"panic.png",image)));}
   if(!allowed(epoch,u,media))return;
   lastPost=System.nanoTime();var response=client.send(rb.build(),HttpResponse.BodyHandlers.discarding());if(generation!=epoch)return;int code=response.statusCode();
   if(code/100==2){status="Отправлено: "+kind;String remaining=response.headers().firstValue("X-RateLimit-Remaining").orElse("");if(remaining.equals("0"))block(response.headers().firstValue("X-RateLimit-Reset-After").orElse("5"));}
   else if(code==429){block(response.headers().firstValue("Retry-After").orElse("60"));POOL.getQueue().clear();status="Discord 429: ожидание Retry-After, сообщение НЕ повторяется";}
   else if(code>=400&&code<500){suspended=true;POOL.getQueue().clear();status="Discord "+code+": проверь/замени webhook и сбрось ошибку вручную";}
   else{block("60");status="Discord "+code+": сообщение не подтверждено, автоматических повторов нет";}
   EventLog.log("DISCORD",kind+": HTTP "+code+(image!=null?", PNG opt-in":""));
  }catch(Exception e){if(generation==epoch){block("60");status="Нет подтверждения Discord ("+e.getClass().getSimpleName()+"); повторов нет";EventLog.log("DISCORD","Ошибка "+e.getClass().getSimpleName());}if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
 }
 private static void until(long time){if(blockedUntil==0||time-blockedUntil>0)blockedUntil=time;}
 private static void block(String header){try{double sec=Double.parseDouble(header);if(!Double.isFinite(sec)||sec<0||sec>604800){suspended=true;return;}until(System.nanoTime()+(long)Math.ceil(Math.max(1,sec)*1000000000));}catch(Exception e){until(System.nanoTime()+60000000000L);}}
 private DiscordNotify(){}
}
