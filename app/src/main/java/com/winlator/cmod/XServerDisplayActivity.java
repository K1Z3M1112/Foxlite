package com.winlator.cmod;

import static com.winlator.cmod.core.AppUtils.showToast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PictureInPictureParams;
import android.graphics.Rect;
import android.util.Rational;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.DebugDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.PerformanceControlDialog;
import com.winlator.cmod.contentdialog.WineD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.FrameGenManager;
import com.winlator.cmod.core.FrameGenQuickMenuHelper;
import com.winlator.cmod.core.LsfgVkManager;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.RuntimeBackendProbe;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.ThemeUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineRequestHandler;
import com.winlator.cmod.core.WineStartMenuCreator;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.midi.MidiHandler;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.renderer.ASurfaceRenderer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.FSREffect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.HDREffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.SeekBar;
import com.winlator.cmod.widget.WinlatorHUD;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.LogView;
import com.winlator.cmod.widget.MagnifierView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.OnGetProcessInfoListener;
import com.winlator.cmod.winhandler.ProcessInfo;
import com.winlator.cmod.winhandler.TaskManagerSidebar;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.xenvironment.components.ALSAServerComponent;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;
import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.xenvironment.components.XServerComponent;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.ScreenInfo;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {
    private static final String WRAPPER_DEFAULT_BUNDLE_VERSION = "pipetto-e91223d-20260721";
    private static final String WRAPPER_GAMENATIVE_BUNDLE_VERSION = "20260724";
    private static final int[] VULKAN_UPSCALER_FILTER_VALUES = {2, 4, 5, 3};
    private static final String GRAPHICS_SIDEBAR_SCALING_MODE_KEY = "graphicsSidebarScalingMode";
    private static final int GRAPHICS_SCALING_NONE = 0;
    // Mode 1 used to be labelled Linear. A linear 2D texture sampler is bilinear,
    // so retain the saved value while using the technically accurate label.
    private static final int GRAPHICS_SCALING_BILINEAR = 1;
    private static final int GRAPHICS_SCALING_NEAREST = 2;
    private static final int GRAPHICS_SCALING_SGSR = 3;
    private static final int GRAPHICS_SCALING_FSR = 4;
    private static final int GRAPHICS_SCALING_FSR_FIT = 5;
    private static final int GRAPHICS_SCALING_DLS = 6;
    private static final int GRAPHICS_SCALING_NIS = 7;

    private static final boolean DISABLE_TOUCHSCREEN_AUTO_HIDE = true;

    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    protected Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private FrameRating classicHud = null;   
    private WinlatorHUD modernHud = null;     
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    private String graphicsDriverConfigData = Container.DEFAULT_GRAPHICSDRIVERCONFIG;
    private HashMap<String, String> graphicsDriverConfig;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String emulator = Container.DEFAULT_EMULATOR;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private String startupSelection;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private boolean forceGraphicsDriverExtraction = false;
    private SharedPreferences preferences;
    private OnExtractFileListener onExtractFileListener;
    private WinHandler winHandler;
    private TaskManagerSidebar taskManagerSidebar;
    private WineRequestHandler wineRequestHandler;
    private float globalCursorSpeed = 1.0f;
    private MagnifierView magnifierView;
    private boolean softStretchEnabled = false;
    private DebugDialog debugDialog;
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    private final HashMap<Integer, Integer> bigCoreAffinitySnapshot = new HashMap<>();
    private String wineCpuTopologyValue = "";
    private int frameRatingWindowId = -1;
    private boolean performanceControlsReverted;

    private int activeRendererWindowId = -1;
    private String lastRendererName = null;
    private boolean cursorLock; 
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    private String vkbasaltConfig = "";
    PreloaderDialog preloaderDialog = null;
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    private boolean isRelativeMouseMovement = false;
    private boolean isMouseDisabled = false;
    private boolean simulateTouchScreen = false;

    private SensorManager sensorManager;

    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;

    private String getLaunchGraphicsExtra(String key, String fallback) {
        if (shortcut != null) {
            return shortcut.getExtra(key, container != null ? container.getExtra(key, fallback) : fallback);
        }
        return container != null ? container.getExtra(key, fallback) : fallback;
    }

    private int resolveLaunchFullscreenMode() {
        String shortcutMode = shortcut != null ? shortcut.getExtra("fullscreenMode") : "";
        String legacyShortcutStretch = shortcut != null ? shortcut.getExtra("fullscreenStretched") : "";
        if (shortcut != null && shortcutMode != null && !shortcutMode.isEmpty()) {
            try {
                return Integer.parseInt(shortcutMode);
            } catch (NumberFormatException ignored) {
            }
        }
        if (shortcut != null && legacyShortcutStretch != null && !legacyShortcutStretch.isEmpty()) {
            return "1".equals(legacyShortcutStretch) ? Container.FULLSCREEN_STRETCH : Container.FULLSCREEN_OFF;
        }
        return container != null ? container.getFullscreenMode() : Container.FULLSCREEN_OFF;
    }

    private int getFullscreenModeLabelRes(int fullscreenMode) {
        switch (fullscreenMode) {
            case Container.FULLSCREEN_FIT:
                return R.string.fullscreen_mode_fit;
            case Container.FULLSCREEN_STRETCH:
                return R.string.fullscreen_mode_stretch;
            case Container.FULLSCREEN_FILL:
                return R.string.fullscreen_mode_fill;
            case Container.FULLSCREEN_INTEGER:
                return R.string.fullscreen_mode_integer;
            case Container.FULLSCREEN_OFF:
            default:
                return R.string.fullscreen_mode_off;
        }
    }

    private void updateFullscreenModeUi(int fullscreenMode) {
        TextView titleView = findViewById(R.id.TVToggleFullscreenTitle);
        TextView valueView = findViewById(R.id.TVToggleFullscreenValue);
        if (titleView != null) titleView.setText(R.string.fullscreen_mode);
        if (valueView != null) valueView.setText(getFullscreenModeLabelRes(fullscreenMode));
        setSelectedModeButton(R.id.BTFullscreenOff, fullscreenMode == Container.FULLSCREEN_OFF);
        setSelectedModeButton(R.id.BTFullscreenFit, fullscreenMode == Container.FULLSCREEN_FIT);
        setSelectedModeButton(R.id.BTFullscreenStretch, fullscreenMode == Container.FULLSCREEN_STRETCH);
        setSelectedModeButton(R.id.BTFullscreenFill, fullscreenMode == Container.FULLSCREEN_FILL);
        setSelectedModeButton(R.id.BTFullscreenInteger, fullscreenMode == Container.FULLSCREEN_INTEGER);
    }

    private void persistFullscreenModeSelection(int fullscreenMode) {
        if (shortcut != null) {
            shortcut.putExtra("fullscreenMode", String.valueOf(fullscreenMode));
            shortcut.putExtra("fullscreenStretched", null);
            shortcut.saveData();
        } else if (container != null) {
            container.setFullscreenMode(fullscreenMode);
            container.saveData();
        }
    }

    private void setRendererFullscreenMode(int fullscreenMode, boolean persist) {
        if (xServerView == null) return;
        HostRenderer rendererRef = xServerView.getRenderer();
        int previousMode = rendererRef.getFullscreenMode();
        rendererRef.setFullscreenMode(fullscreenMode);
        if (touchpadView != null && previousMode != fullscreenMode) {
            touchpadView.toggleFullscreen();
        }
        updateFullscreenModeUi(fullscreenMode);
        if (persist) persistFullscreenModeSelection(fullscreenMode);
    }

    private int getSavedUpscalerSelection(String savedFilter) {
        if (savedFilter == null || savedFilter.isEmpty()) return 0;
        try {
            int mode = Integer.parseInt(savedFilter);
            for (int i = 0; i < VULKAN_UPSCALER_FILTER_VALUES.length; i++) {
                if (VULKAN_UPSCALER_FILTER_VALUES[i] == mode) return i;
            }
            return 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getSelectedUpscalerFilterMode(Spinner spinner) {
        int index = spinner != null ? spinner.getSelectedItemPosition() : 0;
        if (index < 0 || index >= VULKAN_UPSCALER_FILTER_VALUES.length) index = 0;
        return VULKAN_UPSCALER_FILTER_VALUES[index];
    }

    private void setSelectedModeButton(int viewId, boolean selected) {
        View view = findViewById(viewId);
        if (view != null) view.setSelected(selected);
    }

    private void setOnClickListenerIfPresent(int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) view.setOnClickListener(listener);
    }

    private int getPersistentRendererFilterMode() {
        if (shortcut != null) return shortcut.getRendererFilterMode();
        if (container != null) return container.getRendererFilterMode();
        return 0;
    }

    private void setPersistentRendererFilterMode(int mode) {
        if (shortcut != null) {
            shortcut.setRendererFilterMode(mode);
        } else if (container != null) {
            container.setRendererFilterMode(mode);
        }
    }

    private void setPersistentRendererNative(boolean enabled) {
        if (shortcut != null) {
            shortcut.setRendererNative(enabled);
        } else if (container != null) {
            container.setRendererNative(enabled);
        }
    }

    private void saveLaunchGraphicsExtra(String key, String value) {
        if (shortcut != null) {
            shortcut.putExtra(key, value);
        } else if (container != null) {
            container.putExtra(key, value);
        }
    }

    private void persistLaunchGraphicsPreset() {
        if (shortcut != null) {
            shortcut.saveData();
        } else if (container != null) {
            container.saveData();
        }
    }

    private boolean getLaunchGraphicsBoolean(String key, boolean fallback) {
        String value = getLaunchGraphicsExtra(key, fallback ? "1" : "0");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private float getLaunchGraphicsSharpnessPercent() {
        String savedSharp = getLaunchGraphicsExtra("graphicsSharpness", "");
        if (savedSharp == null || savedSharp.isEmpty()) return 50f;
        try {
            float raw = Float.parseFloat(savedSharp);
            // Renderer defaults store 0.00..1.00 while the sidebar preset stores 0..100.
            if (raw <= 1.0f && savedSharp.contains(".")) raw *= 100f;
            return Math.max(0f, Math.min(100f, raw));
        } catch (NumberFormatException ignored) {
            return 50f;
        }
    }

    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    private boolean isDarkMode;

    private String screenEffectProfile;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    private void createNotifcationChannel() {
        String name = "Winlator";
        String description = "Winlator XServer Messages";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }

    private float pickHighestRefreshRate() {
        android.view.Display display = getWindowManager().getDefaultDisplay();
        android.view.Display.Mode[] modes = display.getSupportedModes();

        float maxRefresh = 0f;

        for (android.view.Display.Mode mode : modes) {
            if (mode.getRefreshRate() > maxRefresh)
                maxRefresh = mode.getRefreshRate();
        }

        Log.d("XServerDisplayActivity", "Picking refresh rate " + maxRefresh);

        return maxRefresh;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtils.getFullscreenThemeResId(this));
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);

        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = pickHighestRefreshRate();
        getWindow().setAttributes(params);

        setContentView(R.layout.xserver_display_activity);

        preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        cursorLock = preferences.getBoolean("cursor_lock", true);

        isDarkMode = preferences.getBoolean("dark_mode", false);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        boolean xinputDisabledFromShortcut = false;

        startTime = System.currentTimeMillis();

        handler = new Handler(Looper.getMainLooper());
        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);

        hideControlsRunnable = () -> {
            if (DISABLE_TOUCHSCREEN_AUTO_HIDE) {
                return;
            }

            if (preferences.getBoolean("touchscreen_timeout_enabled", false)
                    && inputControlsView != null
                    && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");
            }
        };

        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener(
                (view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                openSidebarPanel(activeSidebarItemId, activeSidebarPanelId);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                hideAllSidebarPanels();
            }
        });

        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false)
                || preferences.getBoolean("enable_box64_logs", false);

        wireSidebarListeners(enableLogs);

        imageFs = ImageFs.find(this);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
            for (int i = 0; i < 4; i++) {
                File eventFile = new File(devInputDir, "event" + i);
                if (eventFile.exists())
                    eventFile.delete();
            }
        }

        winHandler = new WinHandler(this);
        winHandler.setFakeInputPath(devInputDir.getAbsolutePath());

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);

        int containerId = getIntent().getIntExtra("container_id", 0);
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == 0) {
            Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");

        }

        if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
            File shortcutFile = new File(shortcutPath);
            containerId = parseContainerIdFromDesktopFile(shortcutFile);
            Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
        }

        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            if (shortcutName == null || shortcutName.isEmpty()) {
                shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));
                Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);
            }
        } else {
            Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");
        }

        incrementPlayCount();

        Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);

        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish(); 
            return;
        }

        containerManager.activateContainer(container);

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
        }

        boolean sustainedPerformance = resolvedPerformanceBoolean(
                com.winlator.cmod.perf.PerformanceSettings.KEY_SUSTAINED);
        boolean preferBigCores = resolvedPerformanceBoolean(
                com.winlator.cmod.perf.PerformanceSettings.KEY_BIG_CORES);
        getWindow().setSustainedPerformanceMode(sustainedPerformance);
        com.winlator.cmod.perf.TempWatchdog.INSTANCE.start(this);

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        String affinityCpuList = container.getCPUList(true);

        if (shortcut != null) {
            affinityCpuList = shortcut.getExtra("cpuList", container.getCPUList(true));
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(affinityCpuList);
            taskAffinityMaskWoW64 = taskAffinityMask;
        }

        if (preferBigCores) {
            String bigCoreList = com.winlator.cmod.perf.CpuTopology.INSTANCE.detectBigCoreCpuList();
            if (bigCoreList != null && !bigCoreList.isEmpty()) {
                affinityCpuList = bigCoreList;
                taskAffinityMask = (short) ProcessHelper.getAffinityMask(bigCoreList);
                taskAffinityMaskWoW64 = taskAffinityMask;
            }
        }

        if (com.winlator.cmod.perf.RootManager.INSTANCE.isGranted()) {
            applyEffectiveRootPerformance();
        } else {
            handler.postDelayed(this::applyEffectiveRootPerformance, 3000);
        }

        boolean syncCpuTopology = shortcut != null
                ? shortcut.getExtra("syncCpuTopology", container.isSyncCpuTopology() ? "1" : "").equals("1")
                : container.isSyncCpuTopology();

        wineCpuTopologyValue = "";
        if (syncCpuTopology && affinityCpuList != null && !affinityCpuList.isEmpty()) {
            int coreCount = affinityCpuList.split(",").length;
            wineCpuTopologyValue = coreCount + ":" + affinityCpuList;
        }

        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("imgVersion").isEmpty();

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();
        if (enableLogs) {
            LogView.setFilename(getExecutable());
            ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        }

        graphicsDriver = container.getGraphicsDriver();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriver = container.getAudioDriver();
        emulator = container.getEmulator();
        midiSoundFont = container.getMIDISoundFont();
        dxwrapper = container.getDXWrapper();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();
        winHandler.setInputType((byte) container.getInputType());
        lc_all = container.getLC_ALL();

        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());
            String inputType = shortcut.getExtra("inputType");
            if (!inputType.isEmpty())
                winHandler.setInputType(Byte.parseByte(inputType));
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);

            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness="
                        + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100 + ";" + "dlsDenoise="
                        + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);

            simulateTouchScreen = shortcut.getExtra("simTouchScreen").equals("1");
        }

        this.graphicsDriverConfigData = graphicsDriverConfig;
        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/"))
                    return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        boolean removeLoadingBarWhenBootingGames = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBarWhenBootingGames) preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = { false };

        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    if (!simulateTouchScreen) {
                        xServerView.getRenderer().setCursorVisible(true);
                    }
                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                }

                if (frameRatingWindowId == window.id) {
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                } else if (frameRatingWindowId == -1 && window.isApplicationWindow()
                        && ((modernHud != null && modernHud.isUserEnabled())
                         || (classicHud != null && classicHud.isUserEnabled()))) {

                    frameRatingWindowId = window.id;
                    activeRendererWindowId = window.id;
                    if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                }
            }

            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                changeFrameRatingVisibility(window, property);
            }

            @Override
            public void onDestroyWindow(Window window) {
                changeFrameRatingVisibility(window, null);
            }
        });

        if (!midiSoundFont.equals("")) {
            try {
                final InputStream in;
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);
                } else {
                    in = null;
                }
                MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {
                    @Override
                    public void onSuccess(SF2Soundbank soundbank) {
                        midiHandler = new MidiHandler();
                        midiHandler.setSoundBank(soundbank);
                        midiHandler.start();
                    }

                    @Override
                    public void onFailed(Exception e) {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e2) {
                            }
                        }
                    }
                };
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    MidiManager.load(in, callback);
                } else {
                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);
                }
            } catch (Exception e) {
            }
        }

        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";

        Runnable runnable = () -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
                CountDownLatch uiReady = new CountDownLatch(1);
                runOnUiThread(() -> {
                    setupUI();
                    setupSidebarInputControls();
                    if (controlsProfile.isEmpty()) {
                        simulateConfirmInputControlsDialog();
                    }
                    uiReady.countDown();
                });
                try {
                    uiReady.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    setupXEnvironment();
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
            runnable.run();
    }

    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        containerId = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    private boolean parseBoolean(String value) {

        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }

        return false;
    }

    private void handleCapturedPointer(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_BUTTON_RELEASE: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE: {
                float[] p = XForm.transformPoint(xform, event.getX(), event.getY());
                int dx = (int) p[0];
                int dy = (int) p[1];
                if (xServer.isRelativeMouseMovement())
                    winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
                else
                    xServer.injectPointerMoveDelta(dx, dy);
                break;
            }
            case MotionEvent.ACTION_SCROLL: {
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                    }
                } else if (scrollY >= 1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
            com.winlator.cmod.perf.TempWatchdog.INSTANCE.start(this);
            applyEffectiveRootPerformance();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        if (!isInPictureInPictureMode())
            ProcessHelper.resumeAllWineProcesses();
    }

    @Override
    public void onPause() {
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        super.onPause();

        if (!isInPictureInPictureMode()) {
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }

            ProcessHelper.pauseAllWineProcesses();
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        int w = xServer.screenInfo.width;
        int h = xServer.screenInfo.height;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(w, h));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && xServerView != null) {
            int[] loc = new int[2];
            xServerView.getLocationOnScreen(loc);
            Rect hint = new Rect(loc[0], loc[1],
                    loc[0] + xServerView.getWidth(), loc[1] + xServerView.getHeight());
            builder.setSourceRectHint(hint);
        }
        enterPictureInPictureMode(builder.build());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (xServerView == null) return;
        xServerView.getRenderer().setPipMode(isInPictureInPictureMode);
        if (!isInPictureInPictureMode) {
            xServerView.post(() -> {
                if (xServerView == null) return;
                int w = xServerView.getWidth();
                int h = xServerView.getHeight();
                if (w > 0 && h > 0) xServerView.getRenderer().onHostSurfaceChanged(w, h);
            });
        }
    }

    private void savePlaytimeData() {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        editor.apply();

        startTime = System.currentTimeMillis();
    }

    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.apply();
    }

    private void exit() {
        stopAndRevertPerformanceControls();
        boolean removeLoadingBar = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBar) preloaderDialog.showOnUiThread(R.string.shutdown);

        if (xServerView != null) {
            xServerView.getRenderer().forceCleanup();
            xServerView.setVisibility(View.GONE);
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);

        if (midiHandler != null)
            midiHandler.stop();

        if (environment != null)
            environment.stopEnvironmentComponents();
        if (winHandler != null)
            winHandler.stop();
        if (wineRequestHandler != null)
            wineRequestHandler.stop();

        Executors.newSingleThreadExecutor().execute(() -> {

            ProcessHelper.terminateAllWineProcesses();

            long start = System.currentTimeMillis();
            while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= 1500) {

                    for (String pid : ProcessHelper.listRunningWineProcesses()) {
                        ProcessHelper.killProcess(Integer.parseInt(pid));
                    }
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            preloaderDialog.closeOnUiThread();
            AppUtils.restartApplication(getApplicationContext());
        });
    }

    @Override
    protected void onDestroy() {
        stopAndRevertPerformanceControls();
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        if (handler != null) {
            handler.removeCallbacks(savePlaytimeRunnable);
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else
                drawerLayout.closeDrawers();
        }
    }

    private void showVibrationDialog() {
        if (winHandler == null)
            return;

        Context context = this;
        int maxControllers = winHandler.getMaxControllers();
        boolean[] checkedItems = new boolean[maxControllers];
        String[] items = new String[maxControllers];

        for (int i = 0; i < maxControllers; i++) {
            items[i] = getString(R.string.vibration_slot, i + 1);
            checkedItems[i] = winHandler.isVibrationEnabledForSlot(i);
        }

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.vibration)
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    winHandler.setVibrationEnabledForSlot(which, isChecked);
                })
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (touchpadView == null) return;

        if (hasFocus && cursorLock)
            touchpadView.requestPointerCapture();
        else if (!hasFocus)
            touchpadView.releasePointerCapture();
    }

    private void setupWineSystemFiles() {
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("imgVersion").equals(imgVersion)) {
            applyGeneralPatches(container);
            container.putExtra("imgVersion", imgVersion);
            containerDataChanged = true;
        }

        String dxwrapper = this.dxwrapper;
        String graphicsDriverState = graphicsDriver + ";" + graphicsDriverConfigData;
        String graphicsDriverArchive = resolveGraphicsDriverArchiveName();
        if ("wrapper".equals(graphicsDriverArchive)) {
            graphicsDriverState += ";bundle=" + WRAPPER_DEFAULT_BUNDLE_VERSION;
        } else if ("wrapper-gamenative".equals(graphicsDriverArchive)) {
            // Include the bundled wrapper revision in the extraction state so existing
            // containers replace an older libvulkan_wrapper.so after an app update.
            graphicsDriverState += ";bundle=" + WRAPPER_GAMENATIVE_BUNDLE_VERSION;
        }

        forceGraphicsDriverExtraction = !graphicsDriverState.equals(container.getExtra("graphicsDriver"));
        if (forceGraphicsDriverExtraction) {
            container.putExtra("graphicsDriver", graphicsDriverState);
            containerDataChanged = true;
        }

        if (dxwrapper.contains("dxvk")) {
            String dxvkWrapper = "dxvk-" + dxwrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
        } else if (dxwrapper.contains("vegas")) {
            String vegasVersion = dxwrapperConfig.get("version");
            if (vegasVersion == null || vegasVersion.isEmpty()) {
                vegasVersion = DefaultVersion.getVegasDefault();
            }
            String vkd3dWrapper = dxwrapper.contains("+vkd3d")
                    ? "vkd3d-" + dxwrapperConfig.get("vkd3dVersion")
                    : "None";
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = "vegas-" + vegasVersion + ";" + vkd3dWrapper + ";" + ddrawrapper;
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents())
                : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme + "," + xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme + "," + xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        int inputType = container.getInputType();
        if (shortcut != null) {
            String shortcutInputType = shortcut.getExtra("inputType");
            if (!shortcutInputType.isEmpty()) {
                inputType = Byte.parseByte(shortcutInputType);
            }
        }
        boolean dinputEnabled = (inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT;

        boolean exclusiveXInput = container.isExclusiveXInput();
        if (shortcut != null) {
            String extra = shortcut.getExtra("exclusiveXInput");
            if (!extra.isEmpty())
                exclusiveXInput = extra.equals("1");
        }

        WineUtils.setJoystickRegistryKeys(container, dinputEnabled, exclusiveXInput);

        if (shortcut != null)
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        if (containerDataChanged)
            container.saveData();
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels",
                SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty()
                ? "+" + wineDebugChannels.replace(",", ",+")
                : "-all");

        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        guestProgramLauncherComponent = new GuestProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(container.getWineVersion()),
                shortcut);

        if (container != null) {
            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {

            }
            guestProgramLauncherComponent.setContainer(this.container);
            guestProgramLauncherComponent.setWineInfo(this.wineInfo);

            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());

            if (shortcut != null)
                envVars.putAll(shortcut.getExtra("envVars"));

            if (!wineCpuTopologyValue.isEmpty()) {
                envVars.put("WINE_CPU_TOPOLOGY", wineCpuTopologyValue);
            }

            if (!envVars.has("WINEESYNC")) {
                envVars.put("WINEESYNC", "1");
            }

            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset());

            guestProgramLauncherComponent.setFEXCorePreset(
                    shortcut != null
                            ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())
                            : container.getFEXCorePreset());
        }

        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars.clear(); 
        }

        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(
                new XServerComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)));

        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            environment.addComponent(
                    new ALSAServerComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)));
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);
            environment.addComponent(
                    new PulseAudioComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> runOnUiThread(this::exit));

        environment.addComponent(guestProgramLauncherComponent);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {

        }

        environment.startEnvironmentComponents();

        if (resolvedPerformanceBoolean(com.winlator.cmod.perf.PerformanceSettings.KEY_PRIORITY)) {
            handler.postDelayed(
                    () -> com.winlator.cmod.perf.PerfPriority.INSTANCE.boost(
                            GuestProgramLauncherComponent.getPid()),
                    5000);
        }

        winHandler.start();

        if (wineRequestHandler != null)
            wineRequestHandler.start();

        dxwrapperConfig = null;

    }

    private void createWrapperScript(String path, String content) {
        File scriptFile = new File(path);
        FileUtils.writeString(scriptFile, content);
        scriptFile.setExecutable(true);
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        String rendererType = shortcut != null ? shortcut.getRenderer()
                : (container != null ? container.getRenderer() : "vulkan");
        if ("surfaceflinger".equalsIgnoreCase(rendererType) && !ASurfaceRenderer.isSupported()) {
            rendererType = "vulkan";
        }
        xServerView.initRenderer(rendererType);
        final HostRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        final String rendererLabel = "gl".equalsIgnoreCase(rendererType) ? "OpenGL"
                : "surfaceflinger".equalsIgnoreCase(rendererType) ? "SurfaceFlinger" : "Vulkan";

        if (renderer instanceof VulkanRenderer) {
            VulkanRenderer vkRenderer = (VulkanRenderer) renderer;
            String rendererDriverId = shortcut != null ? shortcut.getRendererDriverId()
                    : (container != null ? container.getRendererDriverId() : "");
            if (rendererDriverId == null || rendererDriverId.isEmpty()) {
                rendererDriverId = graphicsDriverConfig != null ? graphicsDriverConfig.get("version") : null;
            }
            if (rendererDriverId != null && !rendererDriverId.isEmpty() && !rendererDriverId.equalsIgnoreCase("system")) {
                try {
                    String driverPath = getFilesDir().getAbsolutePath() + "/contents/adrenotools/" + rendererDriverId + "/";
                    AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
                    String libraryName = adrenotoolsManager.getLibraryName(rendererDriverId);
                    String nativeLibDir = AppUtils.getNativeLibDir(this);
                    if (!libraryName.isEmpty()) vkRenderer.setDriverInfo(driverPath, libraryName, nativeLibDir);
                } catch (Exception ignored) {
                }
            }

            String presentMode = shortcut != null ? shortcut.getRendererPresentMode()
                    : (container != null ? container.getRendererPresentMode() : "fifo");
            boolean lsfgArmedForPresent = shortcut != null ? LsfgVkManager.isArmed(shortcut) : LsfgVkManager.isArmed(container);
            if (lsfgArmedForPresent && "fifo".equalsIgnoreCase(presentMode)) {
                // FIFO blocks QueuePresentKHR on vsync, and the host compositor keeps only
                // the latest texture per window (texMap is overwrite-in-place, not a queue —
                // see updateWindowContent/updateWindowContentAHB). Every generated frame that
                // arrives from the guest's LSFG layer while the host is blocked on a FIFO
                // present gets silently clobbered by the next update before it's ever shown,
                // i.e. it's dropped after the (expensive) generation work is already done.
                // MAILBOX never blocks the present call, so the host loop keeps draining
                // texMap as fast as the GPU allows and there's no artificial vsync stall for
                // freshly-generated frames to pile up behind. Only overrides the *default*
                // fifo — an explicit non-fifo choice from RendererOptionsDialog is untouched.
                presentMode = "mailbox";
                Log.d("XServerDisplayActivity", "Frame gen armed: overriding renderer present mode fifo -> mailbox to avoid dropping generated frames");
            }
            vkRenderer.setVkPresentMode(com.winlator.cmod.contentdialog.RendererOptionsDialog.toVkPresentMode(presentMode));
            vkRenderer.setFilterMode(shortcut != null ? shortcut.getRendererFilterMode()
                    : (container != null ? container.getRendererFilterMode() : 0));
            vkRenderer.setSwapRB(shortcut != null ? shortcut.getRendererSwapRB()
                    : (container != null && container.getRendererSwapRB()));
            softStretchEnabled = getLaunchGraphicsBoolean("graphicsStretchMode", false);
            vkRenderer.setStretchMode(softStretchEnabled ? 1 : 0);
        } else if (renderer instanceof GLRenderer) {
            GLRenderer glRenderer = (GLRenderer) renderer;
            int rendererFilterMode = shortcut != null ? shortcut.getRendererFilterMode()
                    : (container != null ? container.getRendererFilterMode() : 0);
            // Renderer settings use 0=Bilinear, 1=Nearest. GLRenderer's runtime API uses
            // 0=Linear and 2=Nearest because mode 2 is also its effect/upscaler path.
            glRenderer.setFilterMode(rendererFilterMode == 1 ? 2 : 0);
            glRenderer.setSwapRB(shortcut != null ? shortcut.getRendererSwapRB()
                    : (container != null && container.getRendererSwapRB()));
            boolean nativeMode = shortcut != null ? shortcut.getRendererNative()
                    : (container != null && container.isRendererNative());
            glRenderer.setInitialNativeMode(nativeMode);
        } else if (renderer instanceof ASurfaceRenderer) {
            ASurfaceRenderer asrRenderer = (ASurfaceRenderer) renderer;
            asrRenderer.setSfCompatMode(shortcut != null ? shortcut.getRendererSfCompatMode()
                    : (container == null || container.getRendererSfCompatMode()));
            asrRenderer.setDirectRgbaGameFrames(isDefaultWrapperDirectRgbaMode());
            String savedGraphicsFilter = getLaunchGraphicsExtra("graphicsFilterMode", "0");
            try {
                asrRenderer.setFilterMode(Integer.parseInt(savedGraphicsFilter));
            } catch (NumberFormatException ignored) {
                asrRenderer.setFilterMode(0);
            }
            asrRenderer.setSharpness(getLaunchGraphicsSharpnessPercent() / 100f);
        }

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMouseEnabled(!isMouseDisabled);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.openDrawer(GravityCompat.START);
        });
        View.OnCapturedPointerListener capturedPointerListener = new View.OnCapturedPointerListener() {
            @Override
            public boolean onCapturedPointer(View view, MotionEvent event) {
                handleCapturedPointer(event);
                return true;
            }
        };
        touchpadView.setOnCapturedPointerListener(cursorLock ? capturedPointerListener : null);
        touchpadView.setFocusable(true);
        touchpadView.setFocusableInTouchMode(true);
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);
        inputControlsView
                .setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (isTimeoutEnabled) {
            startTouchscreenTimeout();
        }

        if (container != null) {
            String hudModeExtra = container.getExtra("hudMode");
            int hudMode = !hudModeExtra.isEmpty()
                    ? Integer.parseInt(hudModeExtra)
                    : (container.isShowFPS() ? 1 : 0);

            if (hudMode == 1) {

                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                renderer.setFrameRating(classicHud);
                classicHud.setRenderer(rendererLabel);
                classicHud.enableByUser();
            } else if (hudMode == 2) {

                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                modernHud.enableByUser();
                renderer.setFrameRating(modernHud);
                modernHud.setRenderer(rendererLabel);
            }
        }

        setRendererFullscreenMode(resolveLaunchFullscreenMode(), false);

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null)
                    showInputControls(profile);
            }

            String simTouchScreen = shortcut.getExtra("simTouchScreen");
            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));
            if (simulateTouchScreen) {
                renderer.setCursorVisible(false);
            }
        }

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);

        setupSidebarHudControls();
        setupSidebarGraphicsControls();
        setupRuntimeBackendIndicator();
    }

    private void setupRuntimeBackendIndicator() {
        TextView indicator = findViewById(R.id.TVRuntimeBackend);
        if (indicator == null || wineInfo == null) return;

        boolean arm64ec = wineInfo.isArm64EC();
        String arch = arm64ec ? "arm64ec" : "x86-64";
        String normalizedEmulator = emulator == null ? "" : emulator.toLowerCase(java.util.Locale.ROOT);
        String translator = !arm64ec ? "Box64"
                : normalizedEmulator.contains("wowbox64") ? "wowbox64" : "FEXCore";
        String baseLabel = arch + " · " + translator;

        indicator.setText(baseLabel + (arm64ec ? " · N/A" : ""));
        indicator.setVisibility(View.VISIBLE);
        if (!arm64ec) return;

        new Thread(() -> {
            RuntimeBackendProbe.FexMode mode = RuntimeBackendProbe.FexMode.NA;
            for (int i = 0; i < 15 && mode == RuntimeBackendProbe.FexMode.NA; i++) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                mode = RuntimeBackendProbe.detect(GuestProgramLauncherComponent.getPid());
            }

            RuntimeBackendProbe.FexMode detectedMode = mode;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                String modeLabel = detectedMode == RuntimeBackendProbe.FexMode.UNIXLIB
                        ? "unixlib" : detectedMode == RuntimeBackendProbe.FexMode.DLL ? "DLL" : "N/A";
                indicator.setText(baseLabel + " · " + modeLabel);
                indicator.setTextColor(detectedMode == RuntimeBackendProbe.FexMode.UNIXLIB
                        ? android.graphics.Color.rgb(76, 175, 80)
                        : ThemeUtils.getColorAttr(this, R.attr.colorOnSurfaceVariant));
            });
        }, "fex-runtime-probe").start();
    }

    private void applyScreenEffects(GLRenderer renderer, float brightness, float contrast, float gamma,
            boolean fxaaEn, boolean crtEn, boolean toonEn, boolean ntscEn) {
        ColorEffect ce = (ColorEffect) renderer.getEffectComposer().getEffect(ColorEffect.class);
        if (brightness == 0 && contrast == 0 && gamma == 1.0f) {
            if (ce != null) renderer.getEffectComposer().removeEffect(ce);
        } else {
            if (ce == null) ce = new ColorEffect();
            ce.setBrightness(brightness / 100f);
            ce.setContrast(contrast / 100f);
            ce.setGamma(gamma);
            renderer.getEffectComposer().addEffect(ce);
        }

        FXAAEffect fxaa = (FXAAEffect) renderer.getEffectComposer().getEffect(FXAAEffect.class);
        if (fxaaEn) {
            if (fxaa == null) renderer.getEffectComposer().addEffect(new FXAAEffect());
        } else if (fxaa != null) {
            renderer.getEffectComposer().removeEffect(fxaa);
        }

        CRTEffect crt = (CRTEffect) renderer.getEffectComposer().getEffect(CRTEffect.class);
        if (crtEn) {
            if (crt == null) renderer.getEffectComposer().addEffect(new CRTEffect());
        } else if (crt != null) {
            renderer.getEffectComposer().removeEffect(crt);
        }

        ToonEffect toon = (ToonEffect) renderer.getEffectComposer().getEffect(ToonEffect.class);
        if (toonEn) {
            if (toon == null) renderer.getEffectComposer().addEffect(new ToonEffect());
        } else if (toon != null) {
            renderer.getEffectComposer().removeEffect(toon);
        }

        NTSCCombinedEffect ntsc = (NTSCCombinedEffect) renderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);
        if (ntscEn) {
            if (ntsc == null) renderer.getEffectComposer().addEffect(new NTSCCombinedEffect());
        } else if (ntsc != null) {
            renderer.getEffectComposer().removeEffect(ntsc);
        }
    }

    private void saveScreenEffectProfile(String name, float brightness, float contrast, float gamma,
            boolean fxaa, boolean crt, boolean toon, boolean ntsc) {
        KeyValueSet settings = new KeyValueSet();
        settings.put("brightness", brightness);
        settings.put("contrast", contrast);
        settings.put("gamma", gamma);
        settings.put("fxaa", fxaa);
        settings.put("crt_shader", crt);
        settings.put("toon_shader", toon);
        settings.put("ntsc_effect", ntsc);

        java.util.Set<String> oldProfiles = new java.util.LinkedHashSet<>(
                preferences.getStringSet("screen_effect_profiles", new java.util.LinkedHashSet<>()));
        java.util.Set<String> newProfiles = new java.util.LinkedHashSet<>();
        for (String p : oldProfiles) {
            String n = p.split(":")[0];
            newProfiles.add(n.equals(name) ? name + ":" + settings.toString() : p);
        }
        preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply();
    }

    private ActivityResultLauncher<Intent> controlsEditorActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (editInputControlsCallback != null) {
                    editInputControlsCallback.run();
                    editInputControlsCallback = null;
                }
            });

    private String parseShortcutNameFromDesktopFile(File desktopFile) {
        String shortcutName = "";
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Name=")) {
                        shortcutName = line.split("=")[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);
            }
        }
        return shortcutName;
    }

    private void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {

                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {

                ((TextView) child).setTextColor(color);
            }
        }
    }

    private void wireSidebarListeners(boolean enableLogs) {
        applySidebarSpinnerTheme();

        View btItemLogs = findViewById(R.id.BTItemLogs);
        if (btItemLogs != null)
            btItemLogs.setVisibility(enableLogs ? View.VISIBLE : View.GONE);

        if (XrActivity.isEnabled(this)) {
            View btItemMagnifier = findViewById(R.id.BTItemMagnifier);
            if (btItemMagnifier != null)
                btItemMagnifier.setVisibility(View.GONE);
        }

        toggleOnClick(R.id.BTItemInput, R.id.LLSubInput);
        toggleOnClick(R.id.BTItemMouse, R.id.LLSubMouse);
        toggleOnClick(R.id.BTItemFPS, R.id.LLSubFPS);
        toggleOnClick(R.id.BTItemGraphics, R.id.LLSubGraphics);
        toggleOnClick(R.id.BTItemScreen, R.id.LLSubScreen);
        openSidebarPanel(R.id.BTItemFPS, R.id.LLSubFPS);

        ViewGroup btItemPause = (ViewGroup) findViewById(R.id.BTItemPause);
        if (btItemPause != null) {
            ImageView pauseIcon = (ImageView) btItemPause.getChildAt(0);
            btItemPause.setOnClickListener(v -> {
                if (isPaused) {
                    ProcessHelper.resumeAllWineProcesses();
                    if (pauseIcon != null) pauseIcon.setImageResource(R.drawable.icon_pause);
                } else {
                    ProcessHelper.pauseAllWineProcesses();
                    if (pauseIcon != null) pauseIcon.setImageResource(R.drawable.icon_play);
                }
                isPaused = !isPaused;
                drawerLayout.closeDrawers();
            });
        }

        View btSubKeyboard = findViewById(R.id.BTSubKeyboard);
        if (btSubKeyboard != null) {
            btSubKeyboard.setOnClickListener(v -> {
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
            });
        }

        setupSidebarInputControls();

        View btSubVibration = findViewById(R.id.BTSubVibration);
        if (btSubVibration != null) {
            btSubVibration.setOnClickListener(v -> {
                showVibrationDialog();
                drawerLayout.closeDrawers();
            });
        }

        Switch swRelativeMouse = findViewById(R.id.SWRelativeMouse);
        if (swRelativeMouse != null) {
            swRelativeMouse.setChecked(isRelativeMouseMovement);
            swRelativeMouse.setOnCheckedChangeListener((btn, checked) -> {
                isRelativeMouseMovement = checked;
                if (xServer != null)
                    xServer.setRelativeMouseMovement(isRelativeMouseMovement);
            });
        }

        Switch swDisableMouse = findViewById(R.id.SWDisableMouse);
        if (swDisableMouse != null) {
            swDisableMouse.setChecked(isMouseDisabled);
            swDisableMouse.setOnCheckedChangeListener((btn, checked) -> {
                isMouseDisabled = checked;
                if (touchpadView != null)
                    touchpadView.setMouseEnabled(!isMouseDisabled);
            });
        }

        View btItemPipMode = findViewById(R.id.BTItemPipMode);
        if (btItemPipMode != null) {
            btItemPipMode.setOnClickListener(v -> {
                enterPipMode();
                drawerLayout.closeDrawers();
            });
        }

        View btItemToggleFullscreen = findViewById(R.id.BTItemToggleFullscreen);
        if (btItemToggleFullscreen != null) {
            btItemToggleFullscreen.setOnClickListener(v -> {
                if (xServerView != null) {
                    HostRenderer rendererRef = xServerView.getRenderer();
                    int nextFullscreenMode = Container.nextFullscreenMode(rendererRef.getFullscreenMode());
                    setRendererFullscreenMode(nextFullscreenMode, true);
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemMagnifier = findViewById(R.id.BTItemMagnifier);
        if (btItemMagnifier != null) {
            btItemMagnifier.setOnClickListener(v -> {
                if (xServerView != null) {
                    final HostRenderer renderer = xServerView.getRenderer();
                    if (magnifierView == null) {
                        FrameLayout flContainer = findViewById(R.id.FLXServerDisplay);
                        magnifierView = new MagnifierView(this);
                        magnifierView.setZoomButtonCallback(value -> {
                            renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                            magnifierView.setZoomValue(renderer.getMagnifierZoom());
                        });
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                        magnifierView.setHideButtonCallback(() -> {
                            flContainer.removeView(magnifierView);
                            magnifierView = null;
                        });
                        flContainer.addView(magnifierView);
                    }
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemSoftStretch = findViewById(R.id.BTItemSoftStretch);
        if (btItemSoftStretch != null) {
            btItemSoftStretch.setSelected(softStretchEnabled);
            btItemSoftStretch.setOnClickListener(v -> {
                if (xServerView != null) {
                    softStretchEnabled = !softStretchEnabled;
                    HostRenderer rendererRef = xServerView.getRenderer();
                    if (rendererRef instanceof VulkanRenderer) {
                        ((VulkanRenderer) rendererRef).setStretchMode(softStretchEnabled ? 1 : 0);
                    }
                    btItemSoftStretch.setSelected(softStretchEnabled);
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemTaskManager = findViewById(R.id.BTItemTaskManager);
        if (btItemTaskManager != null) {
            btItemTaskManager.setOnClickListener(v -> {
                openSidebarPanel(R.id.BTItemTaskManager, R.id.LLSubTaskManager);
                View taskPanel = findViewById(R.id.LLSubTaskManager);
                if (taskPanel != null) {
                    if (taskManagerSidebar == null)
                        taskManagerSidebar = new TaskManagerSidebar(this, taskPanel);
                    taskManagerSidebar.start();
                }
            });
        }

        if (btItemLogs != null) {
            btItemLogs.setOnClickListener(v -> {
                if (debugDialog != null)
                    debugDialog.show();
                drawerLayout.closeDrawers();
            });
        }

        View btItemExit = findViewById(R.id.BTItemExit);
        if (btItemExit != null) {
            btItemExit.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                exit();
            });
        }

        View performanceControls = findViewById(R.id.BTPowerUserPerformance);
        if (performanceControls != null) {
            performanceControls.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                PerformanceControlDialog.showInGame(
                        this,
                        shortcut,
                        this::applyPerformanceKeyLive);
            });
        }
    }

    private int activeSidebarItemId = R.id.BTItemFPS;
    private int activeSidebarPanelId = R.id.LLSubFPS;

    private final int[] sidebarPanelIds = {
        R.id.LLSubInput,
        R.id.LLSubMouse,
        R.id.LLSubFPS,
        R.id.LLSubGraphics,
        R.id.LLSubScreen,
        R.id.LLSubTaskManager
    };

    private final int[] sidebarItemIds = {
        R.id.BTItemInput,
        R.id.BTItemMouse,
        R.id.BTItemFPS,
        R.id.BTItemGraphics,
        R.id.BTItemScreen,
        R.id.BTItemTaskManager
    };

    private void hideAllSidebarPanels() {
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        for (int panelId : sidebarPanelIds) {
            View panel = findViewById(panelId);
            if (panel != null) panel.setVisibility(View.GONE);
        }
    }

    private void setSidebarActiveItem(int activeId) {
        for (int itemId : sidebarItemIds) {
            View item = findViewById(itemId);
            if (item == null) continue;
            if (itemId == activeId) {
                item.setBackgroundResource(R.drawable.sidebar_nav_icon_active);
                item.animate().scaleX(1.025f).scaleY(1.025f).setDuration(105).start();
            } else {
                item.setBackgroundResource(R.drawable.sidebar_nav_icon_bg);
                item.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start();
            }
        }
    }

    private void openSidebarPanel(int parentId, int subId) {
        hideAllSidebarPanels();
        View sub = findViewById(subId);
        if (sub != null) {
            float density = getResources().getDisplayMetrics().density;
            sub.setVisibility(View.VISIBLE);
            sub.setAlpha(0.0f);
            sub.setTranslationX(-8.0f * density);
            sub.animate().alpha(1.0f).translationX(0.0f).setDuration(130).start();
        }
        setSidebarActiveItem(parentId);
        if (parentId != R.id.BTItemMouse && parentId != R.id.BTItemPause) {
            activeSidebarItemId = parentId;
            activeSidebarPanelId = subId;
        }
    }

    private void toggleOnClick(int parentId, int subId) {
        View parent = findViewById(parentId);
        View sub = findViewById(subId);
        if (parent != null && sub != null) {
            parent.setOnClickListener(v -> openSidebarPanel(parentId, subId));
        }
    }

    private ArrayAdapter<String> createSidebarSpinnerAdapter(String[] items) {
        return ThemeUtils.createSpinnerAdapter(this, items);
    }

    private void applySidebarSpinnerTheme() {
        int[] spinnerIds = {
            R.id.SPNativeFPS,
            R.id.SPUpscalerMode,
            R.id.SPPostFXMode,
            R.id.SPColorMode,
            R.id.SPInputControlsProfile,
            R.id.SPHudStyle
        };

        for (int spinnerId : spinnerIds) {
            Spinner spinner = findViewById(spinnerId);
            if (spinner != null) ThemeUtils.applySpinnerTheme(spinner);
        }
    }

    private void setupSidebarHudControls() {
        Switch       swHudMaster     = findViewById(R.id.SWHudMaster);
        Spinner      spHudStyle      = findViewById(R.id.SPHudStyle);
        LinearLayout llHudStyleRow   = findViewById(R.id.LLHudStyleRow);
        LinearLayout llModernOptions = findViewById(R.id.LLModernHudOptions);
        CheckBox     cbFps           = findViewById(R.id.CBHudFps);
        CheckBox     cbGpu           = findViewById(R.id.CBHudGpu);
        CheckBox     cbCpuRam        = findViewById(R.id.CBHudCpuRam);
        CheckBox     cbRam           = findViewById(R.id.CBHudRam);
        CheckBox     cbBattTemp      = findViewById(R.id.CBHudBattTemp);
        CheckBox     cbGraph         = findViewById(R.id.CBHudGraph);
        CheckBox     cbRenderer      = findViewById(R.id.CBHudRenderer);
        SeekBar      sbScale         = findViewById(R.id.SBHudScale);
        SeekBar      sbAlpha         = findViewById(R.id.SBHudAlpha);
        View         btResetHud      = findViewById(R.id.BTResetHud);

        int currentMode = 0;
        if      (modernHud  != null) currentMode = 2;
        else if (classicHud != null) currentMode = 1;
        else if (container  != null) {
            String extra = container.getExtra("hudMode");
            if (!extra.isEmpty())           currentMode = Integer.parseInt(extra);
            else if (container.isShowFPS()) currentMode = 1;
        }

        boolean hudOn    = currentMode != 0;
        boolean isModern = currentMode == 2;

        if (spHudStyle != null) {
            ArrayAdapter<String> styleAdapter = createSidebarSpinnerAdapter(new String[]{"Classic", "Modern"});
            spHudStyle.setAdapter(styleAdapter);
            spHudStyle.setSelection(isModern ? 1 : 0, false);
        }
        if (llHudStyleRow  != null) llHudStyleRow.setVisibility(hudOn ? View.VISIBLE : View.GONE);
        if (llModernOptions != null) llModernOptions.setVisibility(isModern ? View.VISIBLE : View.GONE);

        if (modernHud != null) {
            modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
            if (cbRam != null) cbRam.setChecked(true);
            bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
        }
        if (sbScale != null) sbScale.setOnValueChangeListener((sb, v) -> {
            if (modernHud != null) modernHud.setHudScale(1f + (v - 50f) / 50f);
        });
        if (sbAlpha != null) sbAlpha.setOnValueChangeListener((sb, v) -> {
            if (modernHud != null) modernHud.setHudAlpha(v / 100f);
        });
        if (btResetHud != null) btResetHud.setOnClickListener(v -> {
            if (modernHud != null) modernHud.forceReset();
        });

        if (swHudMaster != null) {
            swHudMaster.setChecked(hudOn);
            swHudMaster.setOnCheckedChangeListener((btn, checked) -> {
                int style = resolveSelectedStyle(spHudStyle);
                if (checked) {
                    enableHudLazily(style);
                    if (llHudStyleRow  != null) llHudStyleRow.setVisibility(View.VISIBLE);
                    if (llModernOptions != null)
                        llModernOptions.setVisibility(style == 2 ? View.VISIBLE : View.GONE);
                    if (style == 2 && modernHud != null) {
                        modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
                        if (cbRam != null) cbRam.setChecked(true);
                        bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
                    }
                    saveHudModeToContainer(style);
                } else {
                    if (classicHud != null) classicHud.disableByUser();
                    if (modernHud  != null) modernHud.disableByUser();
                    if (llHudStyleRow  != null) llHudStyleRow.setVisibility(View.GONE);
                    if (llModernOptions != null) llModernOptions.setVisibility(View.GONE);
                    saveHudModeToContainer(0);
                }
            });
        }

        if (spHudStyle != null) {
            spHudStyle.post(() -> spHudStyle.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (swHudMaster == null || !swHudMaster.isChecked()) return;
                        int newStyle = (pos == 1) ? 2 : 1;
                        if (classicHud != null) classicHud.disableByUser(false);
                        if (modernHud  != null) modernHud.disableByUser(false);
                        enableHudLazily(newStyle);
                        if (llModernOptions != null)
                            llModernOptions.setVisibility(newStyle == 2 ? View.VISIBLE : View.GONE);
                        if (newStyle == 2 && modernHud != null) {
                            modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
                            if (cbRam != null) cbRam.setChecked(true);
                            bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
                        }
                        saveHudModeToContainer(newStyle);
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                }
            ));
        }
    }

    private void enableHudLazily(int style) {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        if (rootView == null || xServerView == null) return;
        final HostRenderer renderer = xServerView.getRenderer();

        boolean rendererAlreadyActive = (activeRendererWindowId != -1);

        if (style == 2) {
            if (modernHud == null) {
                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                if (renderer != null) renderer.setFrameRating(modernHud);
                if (rendererAlreadyActive) {

                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);

                    final String name = lastRendererName;
                    modernHud.onRendererDetected(name);
                }
            }
            modernHud.enableByUser();
        } else {
            if (classicHud == null) {
                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                if (renderer != null) renderer.setFrameRating(classicHud);
                if (rendererAlreadyActive) {
                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);
                    runOnUiThread(() -> classicHud.update());
                }
            }
            classicHud.enableByUser();
        }
    }

    private void bindModernHudCheckboxes(CheckBox cbFps, CheckBox cbGpu, CheckBox cbCpuRam,
            CheckBox cbRam, CheckBox cbBattTemp, CheckBox cbRenderer) {
        if (modernHud == null) return;
        if (cbFps      != null) cbFps.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(0, v));
        if (cbGpu      != null) cbGpu.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(2, v));
        if (cbCpuRam   != null) cbCpuRam.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(3, v));
        if (cbRam      != null) cbRam.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(7, v));
        if (cbBattTemp != null) cbBattTemp.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(4, v));
        if (cbRenderer != null) cbRenderer.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(6, v));
    }

    private int resolveSelectedStyle(Spinner spHudStyle) {
        if (spHudStyle == null) return 1;
        return spHudStyle.getSelectedItemPosition() == 1 ? 2 : 1;
    }

    private void saveHudModeToContainer(int mode) {
        if (container == null) return;
        container.putExtra("hudMode", String.valueOf(mode));
        container.setShowFPS(mode != 0);
        container.saveData();
    }

    private void setupSidebarGraphicsControls() {
        if (xServerView == null) return;
        final HostRenderer renderer = xServerView.getRenderer();
        final VulkanRenderer vkRenderer = renderer instanceof VulkanRenderer ? (VulkanRenderer) renderer : null;
        final GLRenderer glRenderer = renderer instanceof GLRenderer ? (GLRenderer) renderer : null;
        final ASurfaceRenderer asrRenderer = renderer instanceof ASurfaceRenderer ? (ASurfaceRenderer) renderer : null;

        Spinner spNativeFPS        = findViewById(R.id.SPNativeFPS);
        View    llStandardOptions  = findViewById(R.id.LLStandardOptions);
        Switch  swEnableFSR        = findViewById(R.id.SWEnableFSR);
        Spinner spUpscalerMode     = findViewById(R.id.SPUpscalerMode);
        View    lblSharpnessHeader = findViewById(R.id.LBLSharpnessHeader);
        SeekBar sbSharpness        = findViewById(R.id.SBSharpness);
        TextView tvSharpnessValue  = findViewById(R.id.TVSharpnessValue);
        Spinner spPostFXMode       = findViewById(R.id.SPPostFXMode);
        Spinner spColorMode        = findViewById(R.id.SPColorMode);
        View    btSaveGraphicsPreset = findViewById(R.id.BTSaveGraphicsPreset);
        View    btScalingNone      = findViewById(R.id.BTScalingNone);
        View    btScalingBilinear  = findViewById(R.id.BTScalingBilinear);
        View    btScalingNearest   = findViewById(R.id.BTScalingNearest);
        View    btScalingSgsr      = findViewById(R.id.BTScalingSgsr);
        View    btScalingFsr       = findViewById(R.id.BTScalingFsr);
        View    btScalingFsrFit    = findViewById(R.id.BTScalingFsrFit);
        View    btScalingDls       = findViewById(R.id.BTScalingDls);
        View    btScalingNis       = findViewById(R.id.BTScalingNis);

        if (spColorMode    != null) spColorMode.setVisibility(View.GONE);

        final int[]    fpsValues = {0, 30, 60, 90, 120};
        final String[] fpsLabels = {"Off", "30 FPS", "60 FPS", "90 FPS", "120 FPS"};

        if (spNativeFPS != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(fpsLabels);
            spNativeFPS.setAdapter(a);
            String savedFps = getLaunchGraphicsExtra("graphicsFpsPreset", "");
            int savedFpsPos = savedFps.isEmpty() ? 0 : Integer.parseInt(savedFps);
            if (savedFpsPos < 0 || savedFpsPos >= fpsLabels.length) savedFpsPos = 0;
            spNativeFPS.setSelection(savedFpsPos);
            renderer.setFpsLimit(savedFpsPos < fpsValues.length ? fpsValues[savedFpsPos] : 0);
            spNativeFPS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (llStandardOptions != null) llStandardOptions.setVisibility(View.VISIBLE);
                    renderer.setFpsLimit(pos < fpsValues.length ? fpsValues[pos] : 0);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        final String[] upscalerLabels = {"SGSR", "FSR / FidelityFX-CAS", "DLS", "NVScaler"};
        final Runnable[] applyGlEffectsRef = new Runnable[1];
        final int[] selectedBaseFilterMode = {getPersistentRendererFilterMode()};
        final int[] selectedScalingMode = {GRAPHICS_SCALING_NONE};

        if (spUpscalerMode != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(upscalerLabels);
            spUpscalerMode.setAdapter(a);
            spUpscalerMode.setSelection(getSavedUpscalerSelection(getLaunchGraphicsExtra("graphicsFilterMode", "")));
        }

        final String[] pfxLabels = {"None", "DLS", "CRT", "HDR", "Natural"};
        if (spPostFXMode != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(pfxLabels);
            spPostFXMode.setAdapter(a);

            String savedPFX = getLaunchGraphicsExtra("graphicsPostFXMode", "");
            int initPFX = savedPFX.isEmpty() ? 0 : Integer.parseInt(savedPFX);
            if (initPFX < 0 || initPFX >= pfxLabels.length) initPFX = 0;

            spPostFXMode.setSelection(initPFX, false);
            if (initPFX > 0 && vkRenderer != null) vkRenderer.setPostFXMode(initPFX);
        }

        float initSharp = getLaunchGraphicsSharpnessPercent();
        if (sbSharpness != null) {
            sbSharpness.setValue(initSharp);
            if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(initSharp)));
            if (vkRenderer != null) {
                vkRenderer.setSharpness(initSharp / 100f);
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    vkRenderer.setSharpness(v / 100f);
                });
            } else if (asrRenderer != null) {
                asrRenderer.setSharpness(initSharp / 100f);
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    asrRenderer.setSharpness(v / 100f);
                });
            } else if (glRenderer != null) {
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    if (applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
                });
            }
        }

        Runnable updateSharpnessVis = () -> {
            if (lblSharpnessHeader != null) lblSharpnessHeader.setVisibility(View.VISIBLE);
            if (sbSharpness != null) sbSharpness.setVisibility(View.VISIBLE);
        };

        if (glRenderer != null) {
            applyGlEffectsRef[0] = () -> {
                FSREffect fsrEffect = (FSREffect) glRenderer.getEffectComposer().getEffect(FSREffect.class);
                if (fsrEffect != null) glRenderer.getEffectComposer().removeEffect(fsrEffect);
                CRTEffect crtEffect = (CRTEffect) glRenderer.getEffectComposer().getEffect(CRTEffect.class);
                if (crtEffect != null) glRenderer.getEffectComposer().removeEffect(crtEffect);
                HDREffect hdrEffect = (HDREffect) glRenderer.getEffectComposer().getEffect(HDREffect.class);
                if (hdrEffect != null) glRenderer.getEffectComposer().removeEffect(hdrEffect);
                ColorEffect colorEffect = (ColorEffect) glRenderer.getEffectComposer().getEffect(ColorEffect.class);
                if (colorEffect != null) glRenderer.getEffectComposer().removeEffect(colorEffect);

                int sharpnessPos = sbSharpness != null ? Math.round(sbSharpness.getValue()) : 50;
                boolean fsrEnabled = swEnableFSR != null && swEnableFSR.isChecked();
                int postFxPos = spPostFXMode != null ? spPostFXMode.getSelectedItemPosition() : 0;

                if (fsrEnabled) {
                    FSREffect newFsr = new FSREffect();
                    newFsr.setMode(postFxPos == 1 ? FSREffect.MODE_DLS : FSREffect.MODE_SUPER_RESOLUTION);
                    newFsr.setLevel((float) sharpnessPos / 25.0f + 1.0f);
                    glRenderer.getEffectComposer().addEffect(newFsr);
                }

                if (postFxPos == 2) {
                    glRenderer.getEffectComposer().addEffect(new CRTEffect());
                } else if (postFxPos == 3) {
                    HDREffect newHdr = new HDREffect();
                    newHdr.setStrength(1.0f);
                    glRenderer.getEffectComposer().addEffect(newHdr);
                } else if (postFxPos == 4) {
                    ColorEffect natural = new ColorEffect();
                    natural.setContrast(0.10f);
                    natural.setGamma(1.05f);
                    glRenderer.getEffectComposer().addEffect(natural);
                }
                glRenderer.getXServerView().requestRender();
            };
        }

        final Runnable updateScalingButtons = () -> {
            setSelectedModeButton(R.id.BTScalingNone, selectedScalingMode[0] == GRAPHICS_SCALING_NONE);
