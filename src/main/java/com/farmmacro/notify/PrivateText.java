package com.farmmacro.notify;
import com.google.gson.*;
import java.util.regex.Pattern;
/** Secret boundary for portable settings and diagnostic text, not a promise to anonymize pixels. */
public final class PrivateText {
 private static volatile java.util.List<String> tokens=java.util.List.of();
 private static final Pattern LINKS=Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>\\\"']+");
 private static final Pattern EMAIL=Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
 private static final Pattern IP=Pattern.compile("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?");
 private static final Pattern UUID=Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
 public static synchronized void secret(String url){if(url==null||url.isEmpty())return;String s=url.endsWith("/")?url.substring(0,url.length()-1):url;String t=s.substring(s.lastIndexOf('/')+1);if(t.length()<20)return;var copy=new java.util.ArrayList<>(tokens);copy.remove(t);copy.add(t);while(copy.size()>8)copy.removeFirst();tokens=java.util.List.copyOf(copy);}
 public static String secrets(String s){if(s==null)return "";for(String t:tokens)s=s.replace(t,"[SECRET]");return LINKS.matcher(s).replaceAll("[URL]").replaceAll("(?i)(?:token|password|secret|authorization)\\s*[:=]\\s*[^\\s,;]+","[SECRET]");}
 public static String outgoing(String s){s=secrets(s);s=EMAIL.matcher(s).replaceAll("[EMAIL]");s=IP.matcher(s).replaceAll("[IP]");s=UUID.matcher(s).replaceAll("[UUID]");s=s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");return s.length()>1900?s.substring(0,1899)+"…":s;}
 public static JsonElement portable(JsonElement e){
  if(e==null||e.isJsonNull())return JsonNull.INSTANCE;
  if(e.isJsonObject()){JsonObject o=new JsonObject();for(var a:e.getAsJsonObject().entrySet()){String k=a.getKey();if(k.matches("(?i).*(?:password|secret|authorization|webhookurl|webhook_url|token|api[_-]?key).*"))continue;o.add(k,portable(a.getValue()));}return o;}
  if(e.isJsonArray()){JsonArray a=new JsonArray();for(var v:e.getAsJsonArray())a.add(portable(v));return a;}
  if(e.getAsJsonPrimitive().isString())return new JsonPrimitive(secrets(e.getAsString()));return e.deepCopy();
 }
 private PrivateText(){}
}
