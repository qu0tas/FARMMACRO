package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;

/**
 * Что именно играет MacroManager: запись по кадрам или маршрут по точкам.
 * Общее (отсчёт, точка старта, лимиты кругов/времени, полный инвентарь, точка возобновления, паника, HUD)
 * живёт в MacroManager; источник отвечает только за «что нажать в этот тик» и свои проверки застревания/схода.
 */
public interface PlaybackSource {

    /** Число элементов: кадров или точек. */
    int length();

    /** Мировая позиция элемента (точка старта, навигатор). */
    double[] position(int index);

    /** Начало круга с элемента index (index > 0 — продолжение с места остановки). */
    void startPass(Minecraft mc, int index);

    /** Круг пройден — менеджер решает: следующий круг или конец. */
    boolean passDone();

    /**
     * Один тик воспроизведения. Детекторы паники уже отработали.
     * @return true — источник сам вызвал панику (застрял / сошёл с маршрута), дальше не играть
     */
    boolean tick(Minecraft mc, ModConfig c);

    /** Текущий элемент (кадр или точка, к которой идём). */
    int progress();

    /** С какого элемента продолжить после остановки. */
    int resumeIndex();

    /** Источник сам крутит камеру (пресеты камеры в это время не применяются). */
    boolean controlsCamera(ModConfig c);

    /** Остановка: отпустить то, чем источник владеет сам (клавиши отпускает менеджер). */
    default void stop(Minecraft mc) {}
}
