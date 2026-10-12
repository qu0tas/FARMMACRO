package com.farmmacro.notify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * v1.14: сборка запроса Discord-webhook без Minecraft и без сети (проверка — PlayersCheck).
 * Execute Webhook: POST JSON {content, allowed_mentions} или multipart/form-data с payload_json и files[0].
 * allowed_mentions.parse = [] — текст причины (например, чат с «@everyone») никого не пингует.
 */
public final class Webhook {
    private Webhook() {}

    private static final Pattern URL = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d{5,25}/[A-Za-z0-9_\\-]{20,120}/?$");
    public static final int CONTENT_MAX = 1900;

    public static boolean validUrl(String url) { return url != null && URL.matcher(url.strip()).matches(); }

    /** Для поля в меню: адрес без токена. */
    public static String masked(String url) {
        if (url == null || url.isBlank()) return "";
        int i = url.lastIndexOf('/', url.length() - 2);
        return i > 0 ? url.substring(0, i + 1) + "••••••" : "••••••";
    }

    public static String panicText(String reason, String time, String server) {
        String s = "⚠ **Паника:** " + reason + "\nВремя: " + time + "\nСервер: " + server;
        return s.length() <= CONTENT_MAX ? s : s.substring(0, CONTENT_MAX - 1) + "…";
    }

    public static String payload(String content) {
        JsonObject o = new JsonObject();
        o.addProperty("content", content.length() <= CONTENT_MAX ? content : content.substring(0, CONTENT_MAX - 1) + "…");
        o.addProperty("username", "FarmMacro");
        JsonObject am = new JsonObject();
        am.add("parse", new JsonArray());
        o.add("allowed_mentions", am);
        return o.toString();
    }

    /** multipart/form-data: payload_json + files[0] (PNG). */
    public static byte[] multipart(String boundary, String payloadJson, String fileName, byte[] png) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 1024);
        String safeName = fileName.replaceAll("[^A-Za-z0-9_.\\-]", "_");
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + payloadJson + "\r\n");
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n");
        out.writeBytes(png);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) { out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); }
}
