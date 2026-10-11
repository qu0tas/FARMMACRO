package com.farmmacro.camera;

/** v1.12, без зависимостей от игры: углы freelook (как Entity.turn: ×0.15, pitch в −90…90). */
public final class FreelookMath {
    private FreelookMath() {}

    /** @return {yaw, pitch} после движения мыши dx/dy (сырые единицы, как в LocalPlayer.turn). */
    public static float[] turn(float yaw, float pitch, double dx, double dy) {
        float y = yaw + (float) (dx * 0.15);
        float p = pitch + (float) (dy * 0.15);
        return new float[]{y, Math.max(-90f, Math.min(90f, p))};
    }
}
