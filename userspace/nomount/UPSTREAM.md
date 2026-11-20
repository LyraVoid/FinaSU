# NoMount upstream provenance

NoMount is vendored into FinaSU so that the kernel module is built by this
repository's own pipeline instead of being downloaded from upstream releases.
This mirrors how FolkZygisk vendors ReZygisk under `userspace/folkzygisk`.

Only the kernel-side sources are imported here. FinaSU reimplements the
userspace control path (the `nm` CLI's keyring payload protocol) in Rust inside
`userspace/ksud`, and generates injection rules from the module directory in
Rust, so no upstream shell scripts or WebUI are imported.

## Pinned sources

- NoMount: https://github.com/maxsteeel/nomount
  commit `dcd77d03e9912f397a586d7da81694d1628f170e` (tag `v2.3.1`).
  Imported from `kernel/src/` only:

  | upstream path | vendored path | sha256 |
  | --- | --- | --- |
  | `kernel/src/nomount.c` | `kernel/src/nomount.c` | `041c09ba94ab76ab67c25241bda563dd8cdbb6567e476c557909108088cf2813` |
  | `kernel/src/nomount.h` | `kernel/src/nomount.h` | `a396b6469fc4912e9d93179ace982468dc466f8b2a83ce6e4b606857b015941c` |
  | `kernel/src/Kconfig` | `kernel/src/Kconfig` | `68e1d5a393eed6026b071149d0998d1652a598dfbcd4e4cd98f23e178337dbca` |
  | `kernel/src/Makefile` | `kernel/src/Makefile` | `1191fe9ef8e2f3549419fd90a118fbe3d11fc02a04b6353bae0e81be27e03e62` |
  | `kernel/LICENSE` | `kernel/LICENSE` | `8177f97513213526df2cf6184d8ff986c675afb514d4e68a404010521b880643` |
  | `LICENSE` | `LICENSE` | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |

All imported files are byte-identical to upstream. No copyright notice has been
replaced or added inside them; FinaSU attribution is not applied to upstream
files.

## v2.3.1 integration boundary

The imported driver speaks protocol v23. Its rule-add and list headers are 24
bytes (`flags`, `uid`, `uid_end`, `deny_start`, `deny_end`, `v_len`, `r_len`)
and its delete header is 18 bytes (the same UID fields followed by `v_len`).
v23 list pagination uses an opaque cursor and an all-ones completion marker;
v22 uses an index instead.

FinaSU's Rust control path accepts v23 and rejects older drivers. Existing
rules keep the protocol's default scope (`uid = uid_end = 0`, no denied range),
and a single explicit UID is encoded with `uid_end = uid`. The Rust codec can
parse and report range, denied-range, and opaque fields for diagnostics, but
the built-in planner does not generate UID ranges or opaque rules. Opaque and
`.replace` directories therefore remain subject to FinaSU's file-merge and
directory-allowlist protections rather than upstream's `.replace` behavior.
`.remove` is handled as a whiteout only for unprotected directories; protected
partition roots and `lib*`/`bin`/`etc` keep their file-merge fallback.

Upstream's `module/common.sh` bootloop detection and KernelSU/APatch injection
ordering are not imported: ksud owns driver loading, rule injection, UI
readiness and boot guards.

## Deliberately not imported

- `module/*.sh` (`customize.sh`, `metainstall.sh`, `metamount.sh`,
  `post-fs-data.sh`, `rule-paths.sh`, `service.sh`, `boot-completed.sh`):
  FinaSU's built-in backend performs driver loading, rule generation and UID
  filtering itself.
- `module/webroot/**`: the built-in backend ships its own manager UI instead of
  a module WebUI.
- `userspace/src/nm.c`, `userspace/src/nm.h`: the keyring payload protocol is
  reimplemented in Rust. This also avoids shipping a GPL-3 binary and the
  `/data/adb/ksu/bin/nm` symlink the upstream installer relies on.
- `kernel/setup.sh`: it patches a full GKI source tree (`fs/Kconfig`,
  `fs/Makefile`) for in-tree integration. FinaSU builds NoMount as an
  out-of-tree module, so this script is not vendored.

## License mapping

Upstream splits its licenses by directory and carries no SPDX identifiers:

- `kernel/**` (everything vendored here): GNU General Public License v2.0,
  full text in `kernel/LICENSE`. The module itself declares
  `MODULE_LICENSE("GPL")` and `MODULE_AUTHOR("maxsteeel")`.
- Everything outside `kernel/**` upstream: GNU General Public License v3.0.
  That text is retained verbatim as `LICENSE` for provenance even though no
  non-kernel upstream file is vendored.

Upstream source files contain no copyright headers, so provenance rests on the
repository notice and the pinned commit above. Do not advertise the vendored
part as GPL-3-only. FinaSU releases must keep both license texts and provide
matching source for the exact revision they build.

Files authored by FinaSU in this directory (`UPSTREAM.md`, `NOTICE`,
`.gitignore`, `build-lkm.sh`) are not upstream files. `build-lkm.sh` carries
`SPDX-License-Identifier: GPL-2.0-only` because it mirrors upstream's LKM build
steps and builds GPL-2.0-only code.

Release source obligations are already met by the existing per-target source
tarball (`.github/workflows/ksud.yml` runs `git archive HEAD`, attached by
`release.yml`), which contains this directory verbatim.

## FinaSU modifications

None applied to the imported files as of the pinned revision. Any local change
must be recorded here with its rationale and kept minimal, so that re-syncing
from upstream stays a mechanical diff.

## Build contract

`kernel/src/` is built as an out-of-tree kernel module with the same Android
DDK image matrix used for `kernelsu.ko`:

```
CONFIG_NOMOUNT=m make -C "$KDIR" ARCH=arm64 LLVM=1 LLVM_IAS=1 \
    M=<repo>/userspace/nomount/kernel/src modules
```

The KMI name is derived from the DDK image (`ghcr.io/ylarod/ddk-min:<kmi>-<ddk_release>`),
matching `userspace/ksud/bin/aarch64/<kmi>_kernelsu.ko`. Output is staged as
`userspace/ksud/bin/aarch64/<kmi>_nomount.ko` for ksud's embedded assets.

The upstream `kernel/src/Makefile` appends a commit count to
`NOMOUNT_VERSION` only when the source tree's enclosing git repository has an
origin URL containing `nomount`. Inside this repository that condition is
false, so the built module reports the plain base version (`23` for v2.3.1).
FinaSU's driver check therefore compares against the base version, not a commit
count.

## Open items

- `android17-6.18` is in FinaSU's KMI matrix but is not in upstream's test
  matrix; compiling NoMount for it is unverified.
- Upstream's LKM workflow strips page-pinner symbols from the DDK headers before
  building; the same workaround is needed here.
- Stripped module size per KMI is not yet measured, so the APK size delta is
  unknown.
