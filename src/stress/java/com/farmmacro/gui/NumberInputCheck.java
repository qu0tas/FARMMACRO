package com.farmmacro.gui;

/** Проверка ввода чисел в меню без игры (Rows.parseNumber / Rows.num). Вызывается из routeStress. */
public final class NumberInputCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("ввод чисел: " + what);
    }

    private static Double p(String s, double min, double max, boolean integer) {
        double[] out = new double[1];
        return Rows.parseNumber(s, min, max, integer, out) == null ? out[0] : null;
    }

    private static String err(String s, double min, double max, boolean integer) {
        return Rows.parseNumber(s, min, max, integer, new double[1]);
    }

    public static void main() {
        check(p("0.001", 0.001, 180, false) == 0.001, "порог 0.001");
        check(p(" 12,5 ", 0, 100, false) == 12.5 && p("+3", 0, 10, true) == 3.0, "запятая, пробелы, плюс");
        check(err("", 0, 1, false).startsWith("Пусто"), "пусто");
        check(err("abc", 0, 1, false).contains("не число") && err("1e3", 0, 9999, false).contains("не число")
                && err("NaN", 0, 1, false) != null && err("Infinity", 0, 1, false) != null, "не число / NaN / e-запись");
        check(err("-1", 0, 10, false).contains("отрицательное"), "отрицательное: " + err("-1", 0, 10, false));
        check(err("0.0001", 0.001, 1, false).startsWith("Не меньше 0.001"), "минимум");
        check(err("200", 0, 180, false).startsWith("Не больше 180"), "максимум");
        check(err("2.5", 0, 10, true).equals("Нужно целое число") && p("7", 0, 10, true) == 7.0, "целые");
        check(Rows.num(0.001).equals("0.001") && Rows.num(2.0).equals("2") && Rows.num(0.1 + 0.2).equals("0.3")
                && Rows.num(-0.0).equals("0") && Rows.num(-0.25).equals("-0.25"), "вывод: " + Rows.num(0.1 + 0.2));
        System.out.println("Ввод чисел: точность 0.001, запятая, пусто/не число/NaN, пределы, целые, вывод без хвостов — OK");
    }
}
