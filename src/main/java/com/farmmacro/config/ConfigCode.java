package com.farmmacro.config;

import com.farmmacro.notify.PrivateText;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * v1.16.2 «Строка настроек»: JSON настроек ↔ одна строка для копирования (KAELIX1: + base64url(gzip(JSON))).
 * Без Minecraft — проверка в stress (ConfigCodeCheck). Что входит в строку — решает {@link ConfigProfiles#exportCode}.
 */
public final class ConfigCode {
    private ConfigCode() {}

    public static final String PREFIX = "KAELIX1:";
    /** Больше этого после распаковки — не настройки (защита от «zip-бомбы»). */
    public static final int MAX_JSON = 2 * 1024 * 1024;

    public static String encode(String json) {
        return encode(PREFIX, json);
    }

    /** 2.1.0: тот же формат с другим префиксом (например, строка маршрута). */
    public static String encode(String prefix, String json) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
                gz.write(PrivateText.portable(JsonParser.parseString(json))
                        .toString()
                        .getBytes(StandardCharsets.UTF_8));
            }
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Строка → JSON. Пробелы/переносы внутри (скопировали из чата) — убираются. @throws IllegalArgumentException с понятной причиной */
    public static String decode(String code) {
        return decode(PREFIX, code, "настроек");
    }

    /** 2.1.0: разбор строки с другим префиксом; {@code what} — «настроек», «маршрута» (для сообщений). */
    public static String decode(String prefix, String code, String what) {
        if (code == null) throw new IllegalArgumentException("Буфер обмена пуст");
        if (code.length() > 4 * MAX_JSON) throw new IllegalArgumentException("Слишком большая строка " + what);
        String s = code.replaceAll("\\s+", "");
        if (s.isEmpty()) throw new IllegalArgumentException("Буфер обмена пуст");
        if (!s.startsWith(prefix))
            throw new IllegalArgumentException(
                    "Это не строка " + what + " Kaelix (должна начинаться с " + prefix + ")");
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(s.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Строка повреждена (не base64) — скопируй целиком");
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_JSON)
                    throw new IllegalArgumentException("Слишком большая строка — это не строка " + what);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Строка повреждена (обрезана?) — скопируй целиком");
        }
    }
}
