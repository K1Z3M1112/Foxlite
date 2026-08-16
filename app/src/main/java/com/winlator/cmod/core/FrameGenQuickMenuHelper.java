package com.winlator.cmod.core;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

public abstract class FrameGenQuickMenuHelper {
    public static class Settings {
        public final String backend;
        public final int multiplier;
        public final float flowScale;
        public final boolean performanceMode;

        public Settings(String backend, int multiplier, float flowScale, boolean performanceMode) {
            this.backend = FrameGenManager.normalizeBackend(backend);
            this.multiplier = sanitizeMultiplier(multiplier);
            this.flowScale = sanitizeFlowScale(flowScale);
            this.performanceMode = performanceMode;
        }
    }

    private FrameGenQuickMenuHelper() {}

    public static int sanitizeMultiplier(int multiplier) {
        if (multiplier < 2) return 0;
        return Math.max(2, Math.min(4, multiplier));
    }

    public static float sanitizeFlowScale(float flowScale) {
        return Math.max(0.25f, Math.min(1.0f, flowScale));
    }

    public static Settings readSettings(Container container) {
        String backend = FrameGenManager.getBackend(container);
        return new Settings(backend, container.getLsfgMultiplier(), container.getLsfgFlowScale(),
                container.getLsfgPerformanceMode());
    }

    public static Settings readSettings(Shortcut shortcut) {
        String backend = FrameGenManager.getBackend(shortcut);
        return new Settings(backend, shortcut.getLsfgMultiplier(), shortcut.getLsfgFlowScale(),
                shortcut.getLsfgPerformanceMode());
    }

    public static void applySettings(Container container, Settings settings) {
        container.setFrameGenBackend(settings.backend);
        container.setLsfgMultiplier(settings.multiplier);
        container.setLsfgFlowScale(settings.flowScale);
        container.setLsfgPerformanceMode(settings.performanceMode);
        container.setLsfgEnabled(settings.multiplier >= 2);
        container.saveData();
    }

    public static void applySettings(Shortcut shortcut, Settings settings) {
        shortcut.setFrameGenBackend(settings.backend);
        shortcut.setLsfgMultiplier(settings.multiplier);
        shortcut.setLsfgFlowScale(settings.flowScale);
        shortcut.setLsfgPerformanceMode(settings.performanceMode);
        shortcut.setLsfgEnabled(settings.multiplier >= 2);
        shortcut.saveData();
    }
}
