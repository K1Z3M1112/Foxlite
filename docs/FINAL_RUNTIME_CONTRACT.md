# Final runtime contract

The runtime is considered complete only when these contracts are satisfied on the target Android device:

1. A selected Windows EXE is copied into a private app/runtime workspace.
2. Box64/FEX can execute the packaged Wine entry point with the exact ELF loader/library layout shipped by the runtime.
3. Wine creates/uses a WINEPREFIX and loads Windows DLLs.
4. DXVK handles D3D9/10/11 and emits Vulkan commands.
5. VKD3D-Proton handles D3D12 and emits Vulkan commands.
6. Vulkan uses the Android vendor driver directly.
7. Rendering stays GPU-side; no screenshot/framebuffer CPU readback is used for presentation.
8. Android Surface/ANativeWindow is used for presentation.
9. Shader/pipeline caches are persistent.
10. CPU and GPU synchronization does not use unnecessary device-wide idle waits.
11. Mali and Adreno are selected through separate driver/profile paths where appropriate.

The project currently provides the application, process bridge, runtime staging, CI, and native Vulkan foundation. Runtime binaries remain sourced by CI from upstream components rather than being fabricated.
