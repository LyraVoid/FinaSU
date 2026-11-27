# Legacy kernels: 4.14, 4.19 and 5.4

Everything else in this fork is about the loadable path: `kernelsu.ko` plus a
SUSFS guard, written into a GKI ramdisk by `boot-patch`. That path starts at
`android12-5.10`. A 4.14, 4.19 or 5.4 kernel — the usual Android 10, 11 and 12
non-GKI devices — has neither, so root and SUSFS have to be **built into the
kernel** there. The manager cannot patch those boot images, and this fork does
not pretend it can.

What it does ship for them is the patch set from
[susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu), the upstream the guards and
the ABI conventions already come from, copied **byte for byte**. Nothing is
backported, regenerated or trimmed here; `SOURCE.txt` in each zip records the
repository, the branch and the exact commit.

| zip | kernel line | SUSFS | source ref |
|---|---|---|---|
| `susfs-legacy-kernel-4.14.zip` | Linux 4.14 | v1.5.5 | `kernel-4.14` @ `77905b5a` |
| `susfs-legacy-kernel-4.19.zip` | Linux 4.19 | v1.5.5 | `kernel-4.19` @ `001e6991` |
| `susfs-legacy-kernel-5.4.zip` | Linux 5.4 | v1.5.5 | `kernel-5.4` @ `76affd70` |

Each zip contains:

```
kernel_patches/50_add_susfs_in_kernel-<line>.patch   the SUSFS hooks
kernel_patches/KernelSU/10_enable_susfs_for_ksu.patch
kernel_patches/fs/                                  susfs.c, sus_su.c
kernel_patches/include/linux/                       susfs.h, susfs_def.h, sus_su.h
ksu_module_susfs/                                   module + prebuilt ksu_susfs (arm64)
README.md, LICENSE, SOURCE.txt                      upstream's own docs
```

## Applying them

Upstream's README is authoritative; this is the short form of it, for a kernel
tree that already contains KernelSU:

```sh
cp kernel_patches/KernelSU/10_enable_susfs_for_ksu.patch  $KERNEL_ROOT/KernelSU/
cp kernel_patches/50_add_susfs_in_kernel-<line>.patch     $KERNEL_ROOT/
cp kernel_patches/fs/*                                    $KERNEL_ROOT/fs/
cp kernel_patches/include/linux/*                         $KERNEL_ROOT/include/linux/

(cd $KERNEL_ROOT/KernelSU && patch -p1 < 10_enable_susfs_for_ksu.patch)
(cd $KERNEL_ROOT && patch -p1 < 50_add_susfs_in_kernel-<line>.patch)
```

Then build the kernel with `CONFIG_KSU` and `CONFIG_KSU_SUSFS` enabled and flash
`ksu_module_susfs/` as a KernelSU module: it installs `ksu_susfs` (arm64, prebuilt
upstream) into `/data/adb/ksu/bin/`, which is what configures SUSFS on this
generation of kernels.

Two upstream notes that matter more than they look:

* The patches are written against the **official KernelSU** at its latest
  release tag, and expect the non-kprobe setup (`#if defined(CONFIG_KPROBES) && 0`)
  described in upstream's README. They are not written against KernelSU-Next.
* Failed hunks are normal on a vendor kernel: the patch is a diff against
  mainline, and vendor trees move code around. Resolve them by hand rather than
  forcing the patch.

## What does not carry over

* **`ksud susfs` and the built-in BRENE profile.** `ksud` drives SUSFS through
  the loadable kernel's ABI (`reboot(0xdeadbeef, …)`), which these kernels do not
  have. On a 4.14/4.19/5.4 build, use `ksu_susfs` from the module — the ABI, the
  command set and the SUSFS version are all older (v1.5.5 versus 2.3.0).
* **The manager's SUSFS screen.** It reads `/proc/susfs_*` from the guard. A
  kernel-patched SUSFS exposes an older control surface, so the screen reports
  what it finds and can do nothing else. That is a limitation of the kernel, not
  a bug to be worked around.
* **`boot-finasu.img`.** It is built for the X6833B and for GKI 5.10 only.

## Building a legacy kernel-patch image

Out of scope for the release: it needs the device's kernel source and its
toolchain, not a boot image patch. If you want that automated, the honest inputs
are the kernel tree, its defconfig and the three files above; there is no
generic boot image this fork can hand out.
