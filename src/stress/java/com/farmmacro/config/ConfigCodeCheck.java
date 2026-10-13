package com.farmmacro.config;

/** v1.16.2 «Строка настроек»: туда-обратно, пробелы/переносы из чата, чужая строка, обрезанная, пустая. */
public final class ConfigCodeCheck {
    private ConfigCodeCheck() {}

    static int checks;
    static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("ConfigCodeCheck: " + what); checks++; }

    static String err(String code) {
        try { ConfigCode.decode(code); return null; } catch (IllegalArgumentException e) { return e.getMessage(); }
    }

    public static void main() {
        StringBuilder big = new StringBuilder("{\"friendNames\":\"Вадик, Терина\",\"panicEnabled\":true,\"list\":[");
        for (int i = 0; i < 2000; i++) big.append(i).append(',');
        big.append("0]}");
        String json = big.toString();
        String code = ConfigCode.encode(json);
        check(code.startsWith(ConfigCode.PREFIX), "префикс");
        check(code.length() < json.length(), "сжатие: " + code.length() + " ≥ " + json.length());
        check(code.chars().allMatch(ch -> ch < 128 && ch != ' ' && ch != '\n'), "только ASCII без пробелов");
        check(json.equals(ConfigCode.decode(code)), "туда-обратно");
        // из чата: разбили переносами и пробелами
        StringBuilder chat = new StringBuilder();
        for (int i = 0; i < code.length(); i += 37) chat.append(code, i, Math.min(code.length(), i + 37)).append(i % 2 == 0 ? "\n " : " ");
        check(json.equals(ConfigCode.decode("  " + chat + "\t")), "с пробелами и переносами");
        check(err("") != null && err(null) != null, "пустой буфер");
        check(err("{\"a\":1}") != null && err("{\"a\":1}").contains(ConfigCode.PREFIX), "JSON без префикса — понятная ошибка");
        check(err(code.substring(0, code.length() / 2)) != null, "обрезанная строка");
        check(err(ConfigCode.PREFIX + "!!!") != null, "не base64");
        // «бомба»: 3 МБ пробелов — отказ
        String bomb = ConfigCode.encode(" ".repeat(3 * 1024 * 1024));
        check(err(bomb) != null, "слишком большая");
        System.out.printf("ConfigCodeCheck (v1.16.2 строка настроек): %d проверок — OK (строка %d симв. из JSON %d)%n", checks, code.length(), json.length());
    }
}
