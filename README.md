# Native Win Runtime — final integration package

This package contains the complete source/CI integration target for a high-performance Android Windows runtime.

Architecture:

Windows EXE
→ Box64/FEX
→ Wine
→ DXVK / VKD3D-Proton
→ Android Vulkan
→ vendor GPU driver
→ Android Surface

The project deliberately does not contain invented Wine/Box64/DXVK/VKD3D binaries. GitHub Actions stages the selected upstream runtime assets and builds the APK.

### What is included
- Android application and EXE picker
- runtime staging
- process bridge
- WINEPREFIX/HOME setup
- Box64/Wine execution contract
- native Vulkan device foundation
- CI build and runtime staging
- third-party license notes
- final runtime acceptance contract

### What still requires device validation
A real Android device must validate the exact ELF loader/library layout and graphics-driver behavior of the staged runtime. Source code alone cannot honestly guarantee that every Windows EXE will run.

Build with GitHub Actions using `.github/workflows/build-apk.yml`.


## Build fix
The Android module explicitly aligns Java and Kotlin to JVM 17, matching the CI JDK and avoiding Gradle JVM-target incompatibility.


## Bundled runtime
The supplied NativeWinRuntime runtime-assets archive has been merged into `app/src/main/assets/runtime`. The APK build therefore packages the staged runtime assets rather than requiring a separate runtime download at first launch.


## UI / emulator integration update
The Android front end is now a responsive console-style shell with separate Home, PC Emulator, Logs, and Settings pages. It follows device rotation and keeps the activity resizeable for portrait and landscape layouts. Runtime preparation is performed off the UI thread, and the app can auto-prepare the bundled runtime on first launch.

The UI does not claim that arbitrary Windows software is universally compatible: actual execution still depends on the bundled Wine/Box64 runtime and the device's Vulkan driver.
