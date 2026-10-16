package com.farmmacro.config;

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
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(com.farmmacro.notify.PrivateText.portable(com.google.gson.JsonParser.parseString(json)).toString().getBytes(StandardCharsets.UTF_8)); }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Строка → JSON. Пробелы/переносы внутри (скопировали из чата) — убираются. @throws IllegalArgumentException с понятной причиной */
    public static String decode(String code) {
        if (code == null) throw new IllegalArgumentException("Буфер обмена пуст");
        if(code.length()>4*MAX_JSON)throw new IllegalArgumentException("Слишком большая строка настроек");
        String s = code.replaceAll("\\s+", "");
        if (s.isEmpty()) throw new IllegalArgumentException("Буфер обмена пуст");
        if (!s.startsWith(PREFIX)) throw new IllegalArgumentException("Это не строка настроек Kaelix (должна начинаться с " + PREFIX + ")");
        byte[] raw;
        try { raw = Base64.getUrlDecoder().decode(s.substring(PREFIX.length())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Строка повреждена (не base64) — скопируй целиком"); }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_JSON) throw new IllegalArgumentException("Слишком большая строка — это не настройки");
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Строка повреждена (обрезана?) — скопируй целиком");
        }
    }
}
