package com.winlator.cmod.core;

import android.content.Context;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

public abstract class FrameGenManager {
    public static final String BACKEND_LSFG_VK = "lsfg_vk";

    private FrameGenManager() {}

    public static String normalizeBackend(String backend) {
        // LSFG-VK is the only supported Frame Generation backend.
        return BACKEND_LSFG_VK;
    }

    public static String getBackend(Container container) {
        return BACKEND_LSFG_VK;
    }

    public static String getBackend(Shortcut shortcut) {
        return BACKEND_LSFG_VK;
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        return LsfgVkManager.ensureRuntimeInstalled(context, container);
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        return shortcut != null && LsfgVkManager.ensureRuntimeInstalled(context, shortcut);
    }

    public static boolean writeConfig(Container container) {
        return LsfgVkManager.writeConfig(container);
    }

    public static boolean writeConfig(Shortcut shortcut) {
        return shortcut != null && LsfgVkManager.writeConfig(shortcut);
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        return LsfgVkManager.applyLaunchEnv(container, envVars);
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        return LsfgVkManager.applyLaunchEnv(shortcut, envVars);
    }

}
