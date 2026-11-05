package com.farmmacro.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmmacro.config.ConfigCode;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class RouteCodeTest {
    private static PointRoute sample() {
        return new Gson()
                .fromJson(
                        "{\"version\":7,\"name\":\"farm\",\"world\":\"mc.example.net\",\"dimension\":\"minecraft:overworld\","
                                + "\"points\":[{\"x\":1.5,\"y\":64,\"z\":2.5},{\"x\":10.5,\"y\":64,\"z\":2.5},{\"x\":10.5,\"y\":64,\"z\":20.5}]}",
                        PointRoute.class);
    }

    @Test
    void roundTripKeepsPointsAndDropsWorld() {
        PointRoute r = sample();
        r.sanitize();
        String code = RouteCode.encode(r);
        assertTrue(code.startsWith(RouteCode.PREFIX));
        assertTrue(!code.contains("example"), "мир/сервер не должен попадать в строку");
        PointRoute back = RouteCode.decode("  " + code.substring(0, 20) + "\n" + code.substring(20) + " ");
        assertEquals(3, back.points.size());
        assertEquals(10.5, back.points.get(1).x, 1e-9);
        assertNull(back.world);
        assertNull(back.dimension);
    }

    @Test
    void rejectsOtherCodesAndGarbage() {
        String settings = ConfigCode.encode("{\"a\":1}");
        var e = assertThrows(IllegalArgumentException.class, () -> RouteCode.decode(settings));
        assertTrue(e.getMessage().contains("маршрута"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RouteCode.decode(RouteCode.PREFIX + "!!!"));
        assertThrows(IllegalArgumentException.class, () -> RouteCode.decode(""));
        String empty = ConfigCode.encode(RouteCode.PREFIX, "{\"points\":[]}");
        assertThrows(IllegalArgumentException.class, () -> RouteCode.decode(empty));
        String nan = ConfigCode.encode(RouteCode.PREFIX, "{\"points\":[{\"x\":\"NaN\",\"y\":0,\"z\":0}]}");
        assertThrows(IllegalArgumentException.class, () -> RouteCode.decode(nan));
    }

    @Test
    void settingsCodeStillUsesItsOwnPrefix() {
        String code = ConfigCode.encode("{\"a\":1}");
        assertTrue(code.startsWith(ConfigCode.PREFIX));
        assertEquals("{\"a\":1}", ConfigCode.decode(code));
    }
}
