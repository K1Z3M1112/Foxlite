The CI workflow (.github/workflows/build-apk.yml) is now a plain Android
build: checkout, install the Android/NDK toolchain, verify the bundled
runtime assets exist, build the APK, upload it as an artifact. There is no
runtime-download step; the runtime is already part of the repo.
