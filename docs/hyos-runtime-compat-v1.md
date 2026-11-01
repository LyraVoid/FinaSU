# HyperOS Rust Runtime compatibility v1

Status: callback subset implemented; target-device acceptance pending

## Goal

Add an independent HyperOS Rust Runtime v1 compatibility plane to FolkZygisk so that
Zygisk Next modules declared for `/system_ext/bin/hyos_spawner` can be discovered,
loaded through a verified file descriptor, and receive one `onAppSpecialized`
callback per successfully specialized HyperOS application child.

Hyper Runtime means Xiaomi's native HyperOS Rust Runtime. It is not ART, JNI, a Java
VM, or a hypervisor. Magisk Zygisk in ordinary Zygote processes remains unchanged.

## Non-goals

- No DenyList, property-spoofing, trace-hiding, custom-linker, anonymous-memory, or
  arbitrary init-service work.
- No second daemon, generic plugin framework, or new settings/UI page.
- No claim of full Zygisk Next compatibility when a module uses an API that is not
  implemented. The first compatibility level is the Hyper Runtime callback subset.
- No claim of HyperOS device acceptance without a HyperOS image or device.

## Compatibility level

This change targets `Zygisk Next API v4` layout and `HyperOS Runtime API v1`.

Implemented in v1:

- `zn_modules.txt` discovery for the exact HyperOS spawner target;
- `zn_module` validation and `onModuleLoaded`;
- `getRuntime`, runtime type/version validation, and `registerModule`;
- one-shot `onAppSpecialized` after a successful
  `selinux_android_setcontext`;
- caller-oriented PLT hooking through the existing PLTI implementation.

Explicitly unsupported in v1:

- inline hook and inline unhook;
- arbitrary ELF symbol resolver enumeration;
- ZN companion process creation.

Unsupported calls return `ZN_FAILED`, `nullptr`, or `-1` and are not represented as
success. A module which requires one of these APIs is outside the v1 compatibility
claim and must be rejected by its own error handling or addressed in a follow-up.

## ABI contract

The public ABI is defined by field order, not by compiler packing.

| Type | 32-bit size | 64-bit size | Alignment |
|---|---:|---:|---:|
| `struct ZygiskNextAPI` | 40 | 80 | pointer |
| `struct ZygiskNextModule` | 8 | 16 | pointer |
| `struct ZygiskNextRuntime` | 12 | 16 | pointer |
| `struct ZnHyosAppSpecializeArgs` | 12 | 24 | pointer |
| `struct ZygiskNextHyosModule` | 8 | 16 | pointer |
| `struct ZygiskNextCompanionModule` | 12 | 24 | pointer |

`ZygiskNextAPI` function order:

1. `pltHook`
2. `inlineHook`
3. `inlineUnhook`
4. `newSymbolResolver`
5. `freeSymbolResolver`
6. `getBaseAddress`
7. `symbolLookup`
8. `forEachSymbols`
9. `connectCompanion`
10. `getRuntime`

Constants are fixed at API version 4, Hyper Runtime version 1, `ZN_SUCCESS=0`, and
`ZN_FAILED=1`. Function pointers use the process C ABI (AAPCS64 on arm64).

`getRuntime()` rules:

| Process | Return |
|---|---|
| ordinary Zygote / app / Magisk Zygisk plane | `nullptr` |
| non-spawner process | `nullptr` |
| injected `/system_ext/bin/hyos_spawner` | non-null Hyper Runtime v1 |

## Correctness invariants

1. Hyper mode is process-local and enabled only by the `hyos_spawner` injection
   kind; Zygote injection always returns `nullptr` from `getRuntime`.
2. Module libraries are opened read-only from a canonical path beneath an enabled
   module directory, validated as a little-endian shared-object ELF for the current
   architecture, and transferred by FD.
3. Spawner PID/start-time/state is stored separately from Zygote PID, injected flag,
   daemon health, restart count, and startup protection.
4. `onAppSpecialized` is called only when the original
   `selinux_android_setcontext` returns zero.
5. Fork-child state is copy-on-write. The parent never delivers a callback; each
   child has an independent one-shot flag; sibling state cannot cross.
6. `registerModule` copies `ZygiskNextHyosModule` before returning. Callback strings
   are read-only callback-duration views.
7. Spawner injection failures do not mutate Zygote state and do not increment the
   three-failure Zygote startup latch.
8. Injection, module registration, and callback delivery are distinct status
   fields. A loaded library is not evidence of an application callback.
9. Child callback delivery performs no loader work, allocator-heavy work, thread
   creation, or module IPC.
10. Every parser, ELF, PLT, registration, and callback failure has a reason and a
    recoverable status.

## Module discovery contract

Each line in an enabled module's `zn_modules.txt` is:

```text
path=<absolute executable path> [companion] <library>
name=<basename> [companion] <library>
```

Rules:

- disabled or remove-marked modules are ignored;
- module IDs are processed in bytewise lexical order, declarations retain file
  order, and duplicate library identities (`st_dev`, `st_ino`) are loaded once;
- `path=` matching is exact, not suffix matching;
- `name=` matching compares the target basename exactly;
- the only HyperOS target accepted by FolkZygisk is the real executable
  `/system_ext/bin/hyos_spawner`;
- relative and absolute library declarations must canonicalize beneath the same
  module directory;
- symlinks may not move a library outside that directory;
- files must be regular, non-empty, little-endian ELF shared objects with the
  current class and machine;
- malformed lines and invalid libraries are skipped with a reason; one bad module
  does not disable unrelated modules.

The daemon builds the plan from its selected root modules tree and sends a versioned
frame containing generation, module slot, companion flag, module ID, and library FD.
It does not trust a caller-supplied target path: it reads `/proc/<peer>/exe`.

## Target discovery contract

The monitor accepts only a real executable whose `/proc/<pid>/exe` resolves exactly
to `/system_ext/bin/hyos_spawner`. It verifies a regular file identity through
`stat`, checks the ELF class/type, records `ro.build.fingerprint`, and chooses the
matching 64-bit or 32-bit tracer.

There is no suffix, substring, command-name, or `comm` fallback. On ROMs without the
file the branch is never considered. Unknown HyperOS firmware is treated as
unsupported until its path, identity, ELF metadata, and behavior are recorded.

The current local acceptance device is a OnePlus PKX110 without
`/system_ext/bin/hyos_spawner`; it can only verify that ordinary FolkZygisk behavior
is unchanged.

## Lifecycle state machine

```text
PLANNED
  -> SPAWNER_ARMED
  -> INJECTED
  -> RUNTIME_READY
  -> CALLBACK_DELIVERED (per child)

Any stage -> FAILED(reason)
No target -> SKIPPED
```

- `PLANNED`: a matching declaration exists, but the spawner has not executed.
- `SPAWNER_ARMED`: exact target and process identity were accepted.
- `INJECTED`: the injected library reported from the spawner process.
- `RUNTIME_READY`: at least one module completed `onModuleLoaded` and Hyper Runtime
  hooks were installed.
- `CALLBACK_DELIVERED`: a child callback event was reported after successful SELinux
  specialization. This is kept separate from Zygote `injected`.

The callback report is a best-effort post-specialization IPC event. If the app
domain cannot reach the daemon, the module callback may still have run while the
monitor remains at `RUNTIME_READY`; that absence is not treated as delivery proof.

State is additive `hyper` data in runtime schema v1. Existing ksud and Manager
readers ignore it; no schema bump is required. The current UI does not display
Hyper callback completion because it has no Hyper result to report before this
field exists.

## Fork and callback model

The injected library registers one `pthread_atfork` handler. The prepare and parent
handlers do no work. The child handler clears the callback flag and marks the child.
The parent remains un-specialized.

The callback path also compares the current kernel PID from a direct `getpid`
syscall with the spawner PID captured before `main`. A differing PID is sufficient
to identify a post-fork child even if a non-bionic fork path skipped atfork
handlers. This does not preserve library state across an `exec`, so a process
created by fork-plus-exec is only observable if it is itself an injection target.

After specialization, the library PLT-hooks `selinux_android_setcontext` in the
spawner image. The wrapper:

1. calls the original function;
2. returns immediately on failure;
3. on success and only in a fork child, atomically claims the one-shot callback;
4. obtains the process name, package name, and `se_info` as callback-duration
   strings;
5. invokes each copied module callback in registration order;
6. reports callback completion to the daemon.

If the spawner uses an uninstrumentable raw `clone` path, v1 cannot observe the
child and reports that no callback was delivered. This is a documented limitation,
not a success.

## Runtime registration model

- null module or null `onAppSpecialized`: `ZN_FAILED`;
- `target_api_version > 1`: `ZN_FAILED`;
- duplicate callback or full fixed registry: `ZN_FAILED`;
- valid registration copies the structure and installs missing hooks;
- the runtime remains valid for the process lifetime;
- module handles carry a magic value, generation, slot, and companion flag.

The fixed registry bounds memory and avoids allocator use in callback dispatch.

## Failure and recovery

| Failure | Result |
|---|---|
| spawner absent | no Hyper branch; Zygote unchanged |
| target identity invalid | spawner skipped; reason recorded |
| tracer or injection failure | `SPAWNER_ARMED` remains unconfirmed or becomes `FAILED`; no Zygote latch |
| daemon unavailable | injection library exits without affecting process beyond added mapping |
| plan/parser/FD/ELF failure | module skipped with reason; runtime can still serve other modules |
| PLT setcontext failure | `RUNTIME_READY` withheld; no callback claimed |
| original setcontext failure | no callback; failure recorded |
| module callback throws/crashes | child failure is isolated from Zygote and startup guard |

Recovery is reboot or process restart. No new automatic retry loop is introduced for
the auxiliary target.

## Candidate selection

- A: reuse the init ptrace monitor and add an auxiliary exact target. Selected.
- B: reuse the Zygote state machine and boot failure counter. Rejected because it
  pollutes Zygote PID, acknowledgement, retry, and startup-protection semantics.
- C: add a spawner worker/daemon. Rejected because existing monitor and zygiskd can
  carry target injection and FD planning without a second lifecycle.

## Originality and licensing

Zygisk Next is closed-source and its current terms prohibit modification,
redistribution, and extraction. NyaZygisk and ZNNext are GPL-3.0 behavior oracles.
No source, test binary, or large implementation block from either oracle is copied.

The compatibility header is reconstructed from the published field/function order
needed for source and binary interoperability. FolkZygisk retains its existing
upstream notices. New glue code is original and uses the already-vendored PLTI and
CSOLoader through their existing APIs. No vendored source is modified.

## Pinned evidence

- FolkSU baseline: `90d5aa22b262f55d9aa432a06f1d985eed007330`
- Zygisk Next docs: `b5086bcf5415273221e697e958bd4f8d18c13482`
  (2026-08-26), repository HEAD `cddf38d5b1501d0cbf08393767afed09ac478b14`
- Zygisk Next sample: `3dd921bef650086685eac64c75bd833a1c766ad3`
  (2026-06-04)
- NyaZygisk oracle: `ced5ba0efa41d92d44a05064a198364501547f8c`
  (2026-10-29)
- ZNNext oracle: `02c51c881d21cfc5aacc4f3c212901b99a37d5a8`
  (2026-11-01)
- AOSP frameworks/base: `android-17.0.0_r1`
  `23149ba144e850fe494a91ffa13f8019455c0804`
- AOSP bionic: `android-17.0.0_r1`
  `7eb60ba263fcba76b9a227530781f74ae84d3515`
- AOSP external/selinux: `android-17.0.0_r1`
  `4828d3a1b1c2a918bf088c29f520e070ddeebf09`

## Validation gates

- host parsing and status tests;
- arm64-v8a and armeabi-v7a compile-time ABI assertions;
- arm64-v8a release build;
- ksud `cargo ndk check`, `clippy`, and `fmt --check`;
- non-HyperOS device regression evidence;
- HyperOS identity, injection, runtime registration, callback count/identity,
  failure injection, and differential tests remain unverified until a target
  firmware or device is supplied.

## Validation record (2026-11-01)

Branch: `yuki/hyos-runtime-compat`, based on
`90d5aa22b262f55d9aa432a06f1d985eed007330` with a clean worktree.

Passed:

- FolkZygisk host suite: 8 tests covering SCM_RIGHTS FD framing, secure module
  discovery, wrong architecture, duplicate identity, ordering,
  `zn_modules.txt` matching, runtime registration validation/copying, state JSON,
  packaging, and existing umount behavior.
- `make -C userspace/folkzygisk BUILD_TYPE=release ARCHS='arm64-v8a armeabi-v7a'`
  with NDK `29.0.14206865`.
- ksud `cargo ndk -t arm64-v8a check`, `cargo ndk -t arm64-v8a clippy`, and
  `cargo fmt --check`.
- The same static ABI assertions compile against both the pinned official
  Zygisk Next header and the product compatibility header for
  `aarch64-linux-android25` and `armv7a-linux-androideabi25`.
- Staged binaries are ELF64/AArch64 or ELF32/ARM as expected. `libzygisk.so`
  retains dependencies `liblog.so`, `libm.so`, `libdl.so`, and `libc.so`, and
  exports only `entry`.
- The aarch64 pair packages all 12 expected binary/notice files.

Target-device observations:

- Attached device: OnePlus PKX110, Android 17, security patch 2026-09-01.
- `ksud 1.3.2-58-g90d5aa22` reports `rezygisk` as the active external provider.
- `/system_ext/bin/hyos_spawner` is absent.

Not verified:

- HyperOS spawner identity, injection, symbol/PLT shape, runtime registration,
  callback timing/counts/identity, SELinux behavior, crash recovery, 20-reboot
  stability, differential behavior, cold-start performance, and actual target
  module compatibility.
- Ordinary Zygote regression could not be exercised with this branch's engine on
  the attached non-HyperOS device because the external provider correctly owns
  provider precedence. Existing baseline checks remain green.

These unverified items prohibit a "target device accepted" claim.
