package com.farmmacro.route;

import com.farmmacro.camera.PathLook;
import com.farmmacro.fun.CropFeverCore;
import com.farmmacro.gui.UiStyle;
import com.google.gson.Gson;
import java.util.*;

/**
 * Integration-free tests for 1.17.0 policy, parser, timing, serialization and safety boundaries.
 */
public final class Release117Check {
  private static int checks;

  private static void ok(boolean yes, String name) {
    checks++;
    if (!yes) throw new IllegalStateException("Release117Check: " + name);
  }

  private static List<RoutePoint> points() {
    return List.of(new RoutePoint(.5, 64, .5), new RoutePoint(10.5, 64, .5));
  }

  public static void main() {
    ends();
    actions();
    fever();
    camera();
    styles();
    System.out.println("Release117Check: " + checks + " checks — OK");
  }

  private static void ends() {
    var s = new RouteEndSettings();
    ok(!s.enabled(), "legacy no end");
    s.distance = 999;
    s.windowSeconds = -1;
    s.command = "/warp garden\n";
    s.sanitize();
    ok(
        s.distance == 30 && s.windowSeconds == 1 && s.command.equals("/warp garden"),
        "end sanitizes");
    s = new RouteEndSettings();
    s.mode = "walk";
    var pts = points();
    var c = new RouteEndCore();
    c.start(s, pts, 100, 77);
    ok(
        c.phase == RouteEndCore.Phase.EXIT && c.goalX == 13.5 && c.goalZ == .5,
        "exit last heading 3 blocks");
    for (String dir : List.of("+x", "-x", "+z", "-z")) {
      s.direction = dir;
      var v = s.vector(pts);
      ok(Math.abs(v[0]) + Math.abs(v[1]) == 1, "cardinal " + dir);
    }
    s.direction = "auto";
    c.start(s, pts, 100, 77);
    c.tick(200, 13.4, 64, .5, true);
    ok(
        c.phase == RouteEndCore.Phase.FALL && !c.window.waiting(200),
        "walk distance does not open teleport window before fall");
    c.tick(250, 13.4, 63.5, .5, false);
    ok(c.phase == RouteEndCore.Phase.WAIT && c.window.deadline() == 8250, "fall opens window");
    ok(c.serverMove(300, false) && c.window.waiting(300), "rotation during window");
    ok(c.serverMove(301, true) && c.phase == RouteEndCore.Phase.POST, "one teleport accepted");
    ok(!c.serverMove(302, true), "no second teleport grace after consumption");
    ok(
        c.serverMove(303, false) && !c.serverMove(304, false),
        "one associated rotation after teleport, no repeats");
    c.tick(5000, 0, 64, 0, true);
    ok(c.phase == RouteEndCore.Phase.DONE, "post pause completes");
    c.start(s, pts, 0, 8);
    c.tick(10, 11, 63, .5, false);
    c.tick(8010, 11, 62, .5, false);
    ok(
        c.phase == RouteEndCore.Phase.FAIL && c.failure.equals("Rewarp: нет телепорта"),
        "timeout exact");
    c.cancel();
    ok(!c.serverMove(8011, true) && !c.active(), "cancel revokes expectation");
    c.start(s, pts, 0, 8);
    c.tick(10, 13.5, 64, .5, true);
    c.tick(2010, 13.5, 64, .5, true);
    ok(c.phase == RouteEndCore.Phase.FAIL, "no edge fails without opening window");
    s.mode = "command";
    s.delayMinMs = s.delayMaxMs = 200;
    c.start(s, pts, 0, 3);
    c.tick(199, 10.5, 64, .5, true);
    ok(!c.sendCommand && !c.window.waiting(199), "command delay");
    c.tick(200, 10.5, 64, .5, true);
    ok(c.sendCommand && c.window.deadline() == 8200, "command issued once");
    c.tick(201, 10.5, 64, .5, true);
    ok(!c.sendCommand, "command not repeated");
    s.mode = "both";
    c.start(s, pts, 0, 3);
    c.tick(100, 11, 63, .5, false);
    long deadline = c.window.deadline();
    c.tick(300, 11, 62, .5, false);
    ok(
        c.sendCommand && c.window.deadline() == deadline,
        "combined never reopens/extends after fall");
    c.tick(deadline, 11, 62, .5, true);
    ok(c.phase == RouteEndCore.Phase.FAIL, "combined timeout original event");
    c.start(s, pts, 0, 3);
    c.tick(100, 11, 63, .5, false);
    ok(c.serverMove(150, true), "teleport during command delay");
    c.tick(350, 0, 64, 0, true);
    ok(!c.sendCommand && c.phase == RouteEndCore.Phase.POST, "early teleport cancels command");
    for (int seed = 0; seed < 500; seed++) {
      var a = new RouteEndCore();
      var b = new RouteEndCore();
      s.mode = "command";
      s.delayMinMs = 0;
      s.delayMaxMs = 1000;
      a.start(s, pts, 0, seed);
      b.start(s, pts, 0, seed);
      for (int t = 0; t <= 1000; t += 50) {
        a.tick(t, 0, 64, 0, true);
        b.tick(t, 0, 64, 0, true);
        ok(a.phase == b.phase && a.sendCommand == b.sendCommand, "seeded timing");
      }
      ok(a.window.waiting(1000), "bounded command delay");
    }
    var w = new TeleportWindow();
    ok(w.open(0, 8) && !w.open(1, 100) && w.deadline() == 8, "open once");
    ok(!w.accept(8, true) && w.expired(8), "deadline exclusive");
    ok(!w.open(9, 100), "expired event cannot reopen");
  }

  private static void actions() {
    var json = new Gson();
    for (int v = 1; v <= 6; v++) {
      var r =
          json.fromJson(
              "{\"version\":" + v + ",\"points\":[{\"x\":0.5,\"y\":64,\"z\":0.5}]}",
              PointRoute.class);
      r.sanitize();
      ok(
          !r.settings.routeEnd.enabled() && !r.points.getFirst().pointAction.active(),
          "read old v" + v);
    }
    var a = new PointAction();
    a.type = "unknown";
    a.radius = Double.NaN;
    a.slot = 30;
    a.command = "/hello\r\n";
    a.sanitize();
    ok(
        a.type.equals("ordinary") && a.slot == 9 && a.radius == .5 && a.command.equals("/hello"),
        "point sanitizer");
    for (String type : PointAction.TYPES) {
      a.type = type;
      var p = new RoutePoint(.5, 64, .5);
      p.pointAction = a;
      var p2 = json.fromJson(json.toJson(p), RoutePoint.class);
      p2.sanitize();
      ok(p2.pointAction.type.equals(type), "point roundtrip " + type);
      var copy = p.copy();
      copy.pointAction.waitMs = 99;
      ok(p.pointAction.waitMs != 99, "deep copy " + type);
    }
    a = new PointAction();
    a.type = "wait";
    a.waitMs = 1000;
    a.jitterMs = 100;
    var core = new PointActionCore();
    core.start(a, 0, 3);
    ok(
        core.tick(899, 0) == PointActionCore.Result.WAIT
            && core.tick(1100, 0) == PointActionCore.Result.DONE,
        "jitter bounds");
    a.type = "position";
    a.radius = .2;
    a.timeoutMs = 500;
    core.start(a, 0, 3);
    ok(core.tick(100, .3) == PointActionCore.Result.WAIT, "position waits");
    ok(core.tick(100, .1) == PointActionCore.Result.DONE, "position radius");
    ok(core.tick(500, 1) == PointActionCore.Result.PANIC, "position timeout panic");
    a.panicOnTimeout = false;
    core.start(a, 0, 3);
    ok(core.tick(500, 1) == PointActionCore.Result.DONE, "position timeout continue");
    ok(core.issueOnce() && !core.issueOnce(), "single action issuance");
    var r = new PointRoute();
    var p = new RoutePoint(.5, 64, .5);
    p.pointAction.type = "wait";
    r.points.add(p);
    var walk = new WalkCore();
    var out = new WalkCore.Out();
    walk.start(r, 0, 30);
    walk.tick(.5, 64, .5, true, 0, .3, true, 30, true, 1, true, out);
    ok(walk.actionPoint() == 0 && !walk.done() && !out.forward, "gate before advancing last point");
    for (int i = 0; i < 80; i++) walk.tick(.5, 64, .5, true, 0, .3, true, 30, true, 1, true, out);
    ok(out.panic == null && !walk.done(), "waiting never stuck");
    walk.completeAction();
    walk.tick(.5, 64, .5, true, 0, .3, true, 30, true, 1, true, out);
    ok(walk.done(), "last action then finish");
  }

  private static void fever() {
    var f = new CropFeverCore();
    ok(!f.text("Player: CROP FEVER", false, 0) && !f.active(), "player text not start");
    ok(f.text("§6CROP FEVER", true, 100) && f.active() && f.deadline == 60100, "title fallback");
    f.text("WOAH! You caught a case of the CROP FEVER for 60 seconds!", false, 200);
    ok(f.began == 100 && f.deadline == 60100, "duplicate title chat not extend");
    ok(f.text("GONE! Your CROP FEVER has been cured!", false, 1000) && !f.active(), "system end");
    ok(!f.startEnd(1499) && f.startEnd(1500) && !f.startEnd(1501), "end exactly half-second once");
    f.reset();
    f.text("WOAH! You caught a case of the CROP FEVER for 60 seconds!", false, 0);
    f.tick(59999);
    ok(f.active(), "60 seconds active");
    f.tick(60000);
    ok(!f.active() && !f.startEnd(60499) && f.startEnd(60500), "timer fallback");
    f.reset();
    f.text("CROP FEVER", true, 0);
    f.text("GONE! Your CROP FEVER has been cured!", false, 50);
    f.text("CROP FEVER", true, 60);
    ok(f.active() && !f.startEnd(550), "new fever cancels pending end");
    ok(!f.text("CROP FEVER soon", true, 100), "exact title only");
  }

  private static void camera() {
    var p = points();
    var a = PathLook.angles(p, 1, .5, 64, .5, 2.5, 0);
    ok(Math.abs(a[0] + 90) < .01 && a[1] > 0 && a[1] < 20, "flat natural gaze");
    var high = List.of(new RoutePoint(4.5, 67, .5));
    a = PathLook.angles(high, 0, .5, 64, .5, 5, 0);
    ok(a[1] < 0 && a[1] >= -45, "uphill pitch");
    var low = List.of(new RoutePoint(4.5, 60, .5));
    a = PathLook.angles(low, 0, .5, 64, .5, 5, 0);
    ok(a[1] > 0 && a[1] <= 45, "downhill bounded");
    for (int i = 0; i < 100; i++) {
      a = PathLook.angles(p, 1, i * .1, 64, .5, 2.5, 0);
      ok(Float.isFinite(a[0]) && Float.isFinite(a[1]), "finite gaze");
    }
  }

  private static void styles() {
    ok(UiStyle.color("#123456", 0xFF000000) == 0xFF123456, "hex palette");
    ok(UiStyle.color("bad", 0xFFAABBCC) == 0xFFAABBCC, "invalid fallback");
    ok(
        UiStyle.opacity(0xFF112233, 0) == 0x00112233
            && UiStyle.opacity(0xFF112233, 100) == 0xFF112233,
        "opacity bounds");
    ok(!UiStyle.safeName("../bad") && UiStyle.safeName("status"), "asset path safety");
  }
}
