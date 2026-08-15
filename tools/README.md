The CI workflow is the reproducible component bootstrap:

1. Download upstream Winlator release APK.
2. Extract its runtime assets.
3. Build the Android app.
4. Upload the APK.

It also builds a native Box64 CI artifact from upstream sources as a verification target.
