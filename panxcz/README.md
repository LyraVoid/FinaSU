# PanxCZ fork of FinaSU

Every change here is about installing root as a loadable module on a GKI device
with SUSFS in the same ramdisk. The tree is rebased on `LyraVoid/FinaSU@b37e2031`
(the upstream state this fork tracks), and the diff is deliberately small:

1. **`boot_patch` writes the SUSFS guard into the ramdisk.** The stock
   `boot-patch` already adds `init` (ksuinit) and the `<kmi>_kernelsu.ko` that
   matches the device. This fork also adds `susfs_guard_lkm.ko` from the
   embedded `<kmi>_susfs_guard.ko`, and removes it again in `restore`.
2. **`ksuinit` loads that guard.** `panxcz/ksuinit-load-susfs-guard.patch` makes
   the shim load `/susfs_guard_lkm.ko` with `expose_proc=1` right after
   `kernelsu.ko`, so the `/proc/susfs_*` control surface exists before Android's
   init starts. The patch is applied in CI, not committed, so the diff against
   upstream stays readable.
3. **The workflow builds every KMI.** `.github/workflows/panxcz-lkm.yml` compiles
   `kernelsu.ko` for `android12-5.10`, `android13-5.10`, `android13-5.15`,
   `android14-5.15`, `android14-6.1`, `android15-6.6`, `android16-6.12` and
   `android17-6.18`, fetches the matching guard from
   `inforcqb/susfs4ksu-lkm@v2.3.0-r18.4`, and stages one pair per KMI. A single
   APK therefore serves every supported device: `boot-patch` picks the pair that
   matches the KMI it detected.

The result is that a manager built from this fork patches a boot image with
root **and** SUSFS 2.3.0 in one step: no separate ReSuSFS module, no AnyKernel3
zip, no kernel rebuild.

## Checking that the APK really carries the modules

`ksud` embeds `userspace/ksud/bin/aarch64/*` through rust-embed's `compression`
feature, which stores each file as a bare DEFLATE stream inside `libksud.so`,
with no index. Neither file names nor sizes can be trusted to prove the payloads
are there, so `panxcz/deflate-find.c` inflates every stream and
`panxcz/verify-embedded-assets.py` compares the SHA-256 with the staged files:

```sh
python3 panxcz/verify-embedded-assets.py dist/*.apk userspace/ksud/bin/aarch64 \
    --require _kernelsu.ko --require _susfs_guard.ko
```

CI runs exactly that against the repacked APK, after checking that the APK's
signing certificate is the one baked into the modules as the second trusted slot
(`KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets).

## Notes

- Only `arm64-v8a` is built: the guards are arm64 modules, and so is the target
  device family. Other ABIs still use the upstream workflows and carry no guard.
- The guard is prebuilt upstream, not compiled here; the workflow verifies its
  `vermagic` family and that it exposes `expose_proc` before embedding it.
- Modules are built in `ghcr.io/ylarod/ddk-min:<kmi>-20260828` with the release
  signature in the second trusted slot, same as upstream.
