package com.farmmacro.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ConfigMigratorTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void everyVersionHasAStep() {
        assertEquals(ConfigMigrator.CURRENT, ConfigMigrator.stepCount());
    }

    @Test
    void legacyConfigWithoutWelcomeDoneSkipsWizard() {
        JsonObject o = json("{\"panicEnabled\":true}");
        ConfigMigrator.Result r = ConfigMigrator.migrate(o);
        assertEquals(0, r.from());
        assertEquals(ConfigMigrator.CURRENT, r.to());
        assertTrue(r.changed());
        assertTrue(o.get("welcomeDone").getAsBoolean());
        assertEquals(ConfigMigrator.CURRENT, o.get("schemaVersion").getAsInt());
        assertTrue(o.get("panicEnabled").getAsBoolean(), "прочие поля не трогаем");
    }

    @Test
    void explicitWelcomeDoneFalseIsKept() {
        JsonObject o = json("{\"welcomeDone\":false}");
        ConfigMigrator.migrate(o);
        assertFalse(o.get("welcomeDone").getAsBoolean());
    }

    @Test
    void currentVersionIsUntouched() {
        JsonObject o = json("{\"schemaVersion\":" + ConfigMigrator.CURRENT + ",\"welcomeDone\":false}");
        JsonObject before = o.deepCopy();
        ConfigMigrator.Result r = ConfigMigrator.migrate(o);
        assertFalse(r.changed());
        assertEquals(before, o);
    }

    @Test
    void newerVersionIsNotDowngraded() {
        JsonObject o = json("{\"schemaVersion\":99,\"futureField\":1}");
        ConfigMigrator.Result r = ConfigMigrator.migrate(o);
        assertTrue(r.newerThanThisBuild());
        assertEquals(99, o.get("schemaVersion").getAsInt());
        assertFalse(o.has("welcomeDone"));
    }

    @Test
    void garbageVersionCountsAsLegacy() {
        assertEquals(0, ConfigMigrator.versionOf(json("{\"schemaVersion\":\"x\"}")));
        assertEquals(0, ConfigMigrator.versionOf(json("{\"schemaVersion\":1.5}")));
        assertEquals(0, ConfigMigrator.versionOf(json("{\"schemaVersion\":-3}")));
        assertEquals(1, ConfigMigrator.versionOf(json("{\"schemaVersion\":1}")));
    }

    @Test
    void renameMovesValueOnlyWhenTargetMissing() {
        JsonObject o = json("{\"old\":1}");
        ConfigMigrator.rename(o, "old", "neu");
        assertEquals(1, o.get("neu").getAsInt());
        assertFalse(o.has("old"));
        JsonObject both = json("{\"old\":1,\"neu\":2}");
        ConfigMigrator.rename(both, "old", "neu");
        assertEquals(2, both.get("neu").getAsInt());
    }
}
