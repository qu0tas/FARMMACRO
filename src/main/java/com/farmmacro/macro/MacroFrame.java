package com.farmmacro.macro;

/**
 * Один "кадр" макроса — снапшот состояния игрока за один тик.
 */
public class MacroFrame {

    // Позиция
    public double x, y, z;
    // Поворот камеры
    public float yaw, pitch;
    // Нажатые клавиши движения
    public boolean forward, back, left, right, jump, sneak, sprint;
    // Нажатие кнопок мыши
    public boolean attackPressed;   // ЛКМ (ломать)
    public boolean usePressed;      // ПКМ (использовать)
    // Текущий слот в хотбаре
    public int selectedSlot;

    public MacroFrame(
            double x, double y, double z,
            float yaw, float pitch,
            boolean forward, boolean back, boolean left, boolean right,
            boolean jump, boolean sneak, boolean sprint,
            boolean attackPressed, boolean usePressed,
            int selectedSlot
    ) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.forward = forward; this.back = back;
        this.left = left; this.right = right;
        this.jump = jump; this.sneak = sneak; this.sprint = sprint;
        this.attackPressed = attackPressed;
        this.usePressed = usePressed;
        this.selectedSlot = selectedSlot;
    }
}
