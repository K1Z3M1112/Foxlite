package com.winlator.cmod.core;

import android.content.Context;

import com.winlator.cmod.box64.Box64Preset;

/**
 * Recommends container settings based on the device's actual hardware (GPU vendor/model,
 * CPU core count) instead of one hardcoded value for every phone.
 *
 * These are only ever used to PRE-FILL a brand-new container's spinners so that a sane,
 * working configuration is already selected the first time the create-container screen
 * opens. Every value produced here is a plain string identifier the user sees selected in
 * a normal spinner and can change to anything else afterward, exactly like before — nothing
 * here is ever force-applied to an existing container, and nothing here overrides a value
 * the user has already picked (see the addIfMissing-aware AppUtils.setSpinnerSelectionFromValue
 * for how a user's own past choice is preserved even if a list gets rebuilt).
 */
public abstract class HardwareDefaults {

    /**
     * Graphics driver *version* string for the DEFAULT_GRAPHICSDRIVERCONFIG "version=" field.
     * Adreno + Turnip support detected -> the bundled Turnip build; otherwise the generic
     * "System" (Zink/passthrough) driver, which works everywhere else (Mali, PowerVR, etc.).
     */
    public static String recommendedGraphicsDriverVersion(Context context) {
        try {
            return GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context)
                    ? DefaultVersion.WRAPPER_ADRENO
                    : DefaultVersion.WRAPPER;
        } catch (Throwable t) {
            // GPU probing touches a native library; never let a detection failure block
            // container creation, just fall back to the universally-safe driver.
            return DefaultVersion.WRAPPER;
        }
    }

    /**
     * Box64 dynarec preset scaled to how many CPU cores the device actually reports.
     * Low core count phones get the safer/slower preset since aggressive dynarec caching
     * costs memory and background-thread contention that a 4-core phone feels a lot more
     * than an 8-core one; high core count phones get the faster preset since they can
     * absorb it.
     */
    public static String recommendedBox64Preset() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return Box64Preset.PERFORMANCE;
        if (cores >= 6) return Box64Preset.INTERMEDIATE;
        return Box64Preset.COMPATIBILITY;
    }
}
