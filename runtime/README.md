Runtime assets under app/src/main/assets/runtime/ are committed directly in
this repo (rootfs, Box64, DXVK/VKD3D/D8VK/D7VK, GPU drivers, Wine
components, input-control profiles, wallpapers). CI no longer downloads
them from an upstream Winlator release — it builds the app around what's
already here.

Do not commit redistributable binaries you don't have the rights to
redistribute; check LICENSES-THIRD-PARTY.md.
