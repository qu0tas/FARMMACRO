package com.farmmacro.fun;

import java.util.regex.Pattern;

/** Only server system text/title is routed here, never ordinary player chat. */
public final class CropFeverCore {
  private static final Pattern START =
      Pattern.compile(
          "^WOAH! You caught a case of the CROP FEVER for (\\d{1,3}) seconds!$",
          Pattern.CASE_INSENSITIVE);
  public long began = -1, deadline = -1, ending = -1;
  private boolean active, endIssued;

  public boolean text(String raw, boolean title, long now) {
    String s = clean(raw);
    if (s.equalsIgnoreCase("GONE! Your CROP FEVER has been cured!")) {
      if (active) finish(now);
      return true;
    }
    var m = START.matcher(s);
    boolean match = m.matches();
    if (match || title && s.equalsIgnoreCase("CROP FEVER")) {
      if (!active) {
        active = true;
        endIssued = false;
        ending = -1;
        began = now;
        int secs = match ? Integer.parseInt(m.group(1)) : 60;
        deadline = now + Math.clamp(secs, 1, 300) * 1000L;
      }
      return true;
    }
    return false;
  }

  public static String clean(String raw) {
    return raw == null ? "" : raw.replaceAll("§.", "").trim();
  }

  public void tick(long now) {
    if (active && now >= deadline) finish(now);
  }

  private void finish(long now) {
    active = false;
    ending = now + 500;
    endIssued = false;
  }

  public boolean active() {
    return active;
  }

  public boolean startEnd(long now) {
    if (ending < 0 || now < ending || endIssued || active) return false;
    endIssued = true;
    return true;
  }

  public void reset() {
    active = false;
    began = deadline = ending = -1;
    endIssued = false;
  }
}
