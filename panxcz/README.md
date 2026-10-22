# PanxCZ fork of FinaSU

Two changes on top of `LyraVoid/FinaSU@ef67206`, both about installing root as a
loadable module on a GKI `android12-5.10` device (Infinix Note 30, X6833B):

1. **`boot_patch` writes the SUSFS guard into the ramdisk.** The stock
   `boot-patch` already adds `init` (ksuinit) and `kernelsu.ko`; this fork also
   adds `susfs_guard_lkm.ko` from the embedded asset
   `<kmi>_susfs_guard.ko`, and removes it again in `restore`.
2. **`ksuinit` loads that guard.** `panxcz/ksuinit-load-susfs-guard.patch`
   makes the shim load `/susfs_guard_lkm.ko` with `expose_proc=1` right after
   `kernelsu.ko`, so the `/proc/susfs_*` control surface exists before Android's
   init starts. The patch is applied in CI, not committed, so the diff against
   upstream stays readable.

The result is that a manager built from this fork patches a boot image with
root **and** SUSFS 2.3.0 in one step: no separate ReSuSFS module, no AnyKernel3
zip, no kernel rebuild.

Build (see `.github/workflows/panxcz-lkm.yml`): the module is compiled in
`ghcr.io/ylarod/ddk-min:android12-5.10-20260828`, and the APK is signed with the
keystore in the repository secrets (`KEYSTORE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`). The module's second trusted certificate slot is
derived from that same keystore, so the manager and the module agree.

Only `arm64-v8a` / `android12-5.10` is built here. Other KMIs still use the
upstream workflows and carry no guard.
