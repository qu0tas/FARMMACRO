package com.farmmacro.camera;

import com.farmmacro.config.ModConfig;
import com.farmmacro.config.ModConfig.CamPreset;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.11 (промт 3в): пресеты культур и загрузка старого конфига. Без игры, запускается из routeStress.
 *  • пусто → 7 стандартных; свои + стандартные без дублей по имени, свои не тронуты; повторный запуск ничего не дописывает;
 *  • предел 16 пресетов; «Сбросить к стандартным» сохраняет id совпавших по имени;
 *  • углы 0.1°, yaw −180…180, pitch −90…90; культура → пресет группы;
 *  • старый farmmacro.json с auto* и snake* читается Gson без ошибок, sanitize проходит, пресеты дописываются.
 */
public final class CropPresetsCheck {
    private CropPresetsCheck() {}

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CropPresetsCheck: " + what);
    }

    private static CamPreset own(int id, String name, String crop, float yaw, float pitch) {
        CamPreset p = new CamPreset(crop, yaw, pitch);
        p.id = id; p.name = name;
        return p;
    }

    public static void main() {
        int[] next = {1};
        // 1. пусто → 7
        List<CamPreset> empty = new ArrayList<>();
        int rev = CropPresets.ensureDefaults(empty, 0, CameraPresets.MAX, () -> next[0]++);
        check(empty.size() == 7 && rev == CropPresets.DEFAULTS_REV, "пусто → 7, а " + empty.size());
        check(empty.get(5).name.equals("Cactus") && empty.get(5).yaw == -157.9f && empty.get(5).pitch == -17.4f, "Cactus −157.9/−17.4");
        check(empty.get(2).pitch == -40f && empty.get(4).yaw == 165f && empty.get(6).yaw == 90f && empty.get(6).pitch == -30f,
                "Melon −40, Mushroom 165, Cocoa 90/−30");
        check(empty.stream().map(p -> p.id).distinct().count() == 7, "у стандартных разные id");
        int again = CropPresets.ensureDefaults(empty, rev, CameraPresets.MAX, () -> next[0]++);
        check(empty.size() == 7 && again == rev, "повторный запуск не должен дописывать");

        // 2. свои + стандартные без дублей
        List<CamPreset> mine = new ArrayList<>(List.of(
                own(3, "Моя тыква", "pumpkin", 12.3f, -41f),
                own(7, "cactus", "cactus", -150f, -10f)));      // то же имя без учёта регистра — не дублировать
        int[] n2 = {8};
        CropPresets.ensureDefaults(mine, 0, CameraPresets.MAX, () -> n2[0]++);
        check(mine.size() == 2 + 6, "свои 2 + 6 стандартных (Cactus уже есть), а " + mine.size());
        check(mine.get(0).name.equals("Моя тыква") && mine.get(0).yaw == 12.3f && mine.get(1).yaw == -150f, "свои не тронуты");
        check(mine.stream().filter(p -> p.name.equalsIgnoreCase("Cactus")).count() == 1, "дубль Cactus");
        int before = mine.size();
        CropPresets.ensureDefaults(mine, CropPresets.DEFAULTS_REV, CameraPresets.MAX, () -> n2[0]++);
        check(mine.size() == before, "после миграции свои удалённые стандартные не возвращаются");

        // 3. предел 16
        List<CamPreset> full = new ArrayList<>();
        for (int i = 1; i <= 14; i++) full.add(own(i, "P" + i, "other", 0, 0));
        int[] n3 = {15};
        CropPresets.ensureDefaults(full, 0, CameraPresets.MAX, () -> n3[0]++);
        check(full.size() == 16, "не больше 16, а " + full.size());

        // 4. сброс к стандартным: id совпавших по имени сохраняется
        List<CamPreset> reset = CropPresets.resetToStandard(mine, () -> n2[0]++);
        check(reset.size() == 7 && reset.get(5).id == 7 && reset.get(5).name.equals("Cactus") && reset.get(5).yaw == -157.9f,
                "сброс: 7 штук, Cactus с id 7 и стандартными углами");

        // 5. углы 0.1°
        check(CropPresets.yaw(270f) == -90f && CropPresets.yaw(-157.94f) == -157.9f && CropPresets.yaw(180.04f) == -180f
                && CropPresets.yaw(-180f) == 180f && CropPresets.yaw(Float.NaN) == 0f && CropPresets.yaw(135.0004f) == 135f, "yaw");
        check(CropPresets.pitch(95f) == 90f && CropPresets.pitch(-17.44f) == -17.4f && CropPresets.pitch(-0.04f) == 0f
                && Float.floatToIntBits(CropPresets.pitch(-0.04f)) == Float.floatToIntBits(0f), "pitch");

        // 6. культура → пресет
        check(CropPresets.indexFor(empty, "wheat") == 0 && CropPresets.indexFor(empty, "carrot") == 1
                && CropPresets.indexFor(empty, "sunflower") == 3 && CropPresets.indexFor(empty, "cocoa") == 6, "культура → группа");
        check(CropPresets.indexFor(empty, "none") == -1 && CropPresets.indexFor(new ArrayList<>(), "wheat") == -1, "нет пресета → -1");
        check(CropPresets.indexFor(mine, "pumpkin") == 0, "своя культура важнее стандартного имени");

        // 7. старый конфиг с полями авто-маршрута и «змейки»
        String old = "{\"panicEnabled\":true,\"autoStepAuto\":false,\"autoSurface\":\"sand\",\"autoWalk\":\"low\","
                + "\"autoReturnToStart\":true,\"autoEndPause\":5,\"autoRouteDepth\":8,\"autoRouteColumnsPerTick\":512,\"configRev\":3,"
                + "\"snakeStep\":4,\"snakeAxis\":\"z\",\"snakeRowAction\":\"use\",\"snakeTurnAction\":\"none\",\"snakeAppend\":true,"
                + "\"snakeOffsetX\":0.2,\"snakeOffsetZ\":0,\"snakeFloors\":2,\"snakeFloorStep\":3,\"snakePreviewColor\":\"cyan\","
                + "\"camPresets\":[{\"id\":1,\"name\":\"Старый\",\"crop\":\"beetroot\",\"yaw\":270.0,\"pitch\":12.345}],"
                + "\"camSelected\":0,\"camNextId\":2,\"routeReachRadius\":0.3}";
        ModConfig c = new Gson().fromJson(old, ModConfig.class);
        c.sanitize();
        check(c.camPresets.size() == 1 && c.camPresets.get(0).yaw == -90f && c.camPresets.get(0).pitch == 12.3f
                && "other".equals(c.camPresets.get(0).crop), "старый пресет: yaw −90, pitch 12.3, свёкла → «Другое»");
        check(c.ensureCamDefaults() && c.camPresets.size() == 8 && c.camPresets.get(0).name.equals("Старый")
                && c.camDefaultsRev == CropPresets.DEFAULTS_REV, "старый конфиг: свой + 7 стандартных");
        check(c.camPresets.stream().map(p -> p.id).distinct().count() == 8 && c.camNextId == 9, "id без повторов, camNextId 9");
        check(!c.ensureCamDefaults(), "второй запуск — без изменений");
        check("none".equals(c.launchCrop), "launchCrop по умолчанию none");
        c.launchCrop = "beetroot"; c.sanitize();
        check("none".equals(c.launchCrop), "неизвестная культура → none");
        String back = new Gson().toJson(c);
        check(!back.contains("snake") && !back.contains("autoStep") && !back.contains("autoSurface") && !back.contains("configRev"),
                "после сохранения удалённых полей нет");
        System.out.println("Пресеты культур: пусто → 7, свои + стандартные без дублей, предел 16, сброс, 0.1°, "
                + "культура → пресет, старый конфиг с auto*/snake* — OK");
    }
}
