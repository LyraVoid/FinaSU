# SUSFS kernel contract

What a SUSFS patch set requires from this tree, and what changed between the versions
that have been looked at. Read this before moving the pin in
`scripts/check-susfs-upstream.sh`.

The contract has two directions:

- SUSFS calls into this tree. Those symbols must stay exported with the listed
  signature, or the kernel does not link.
- This tree calls into SUSFS. Those call sites must exist in the driver, or the kernel
  links but the hiding silently does nothing.

## Versions

| Version | Upstream commit | Date | Note |
| --- | --- | --- | --- |
| v2.3.0 | `4a4cb1b` (current pin) | 2026-10-19 | SELinux hide lives in the driver |
| v2.4.0 | `b74a5a2` | 2026-11-08 | refactor `8934afa` moved SELinux hide into `security/selinux` |

## Symbols SUSFS expects from this tree

| Symbol | Signature | 2.3.0 | 2.4.0 |
| --- | --- | --- | --- |
| `ksu_selinux_hide_running` | `bool` | required | required |
| `ksu_selinux_hide_enabled` | `bool` | required | declared, unused |
| `fake_status` | `struct page *` | required | required |
| `fake_status_initialize_key` | `struct static_key_false` | required | required |
| `initialize_fake_status` | `void (void)` | required | required |
| `my_write_context` | `ssize_t (struct file *, char *, size_t)` | required | not called |
| `my_write_access` | `ssize_t (struct file *, char *, size_t)` | required | not called |
| `my_setprocattr` | `int (const char *, void *, size_t)` | required | not called |

2.4.0 keeps the three `my_*` hooks un-static in its driver patch but no longer calls
them; the kernel-side patch carries its own copies in `security/selinux/selinuxfs.c`
and `security/selinux/hooks.c`. Keeping them exported is harmless.

## Call sites this tree must provide

| Call site | 2.3.0 | 2.4.0 |
| --- | --- | --- |
| `susfs_add_sus_type()` from `add_type()` | not needed | **required** |
| `susfs_add_sus_avd_allowed_by_type()` from `add_rule_raw()` | not needed | **required** |
| `latest_granting = old + 1` in `apply_kernelsu_rules()` | expected | must be removed |
| `latest_granting = old + 1` in `handle_sepolicy()` | expected | must be removed |
| `reset_avc_cache(seqno)` | expected | must be `reset_avc_cache(0)` |
| `susfs_set_batch_sid()` after a policy reload | required | required |

The two registration calls must run before the rule bitmap is modified, so the recorded
`avd.allowed` is the pre-injection value:

```c
/* add_rule_raw(), right after the avtab node is resolved */
susfs_add_sus_avd_allowed_by_type(src->value, tgt->value, cls->value, node->datum.u.data);

/* add_type(), just before returning true */
susfs_add_sus_type(type_name, type->value);
```

## Why the 2.4.0 changes are not optional

2.4.0 answers `/sys/fs/selinux/context` and `/sys/fs/selinux/access` from
`security/selinux/ss/services.c` using two hash tables:

- `SUS_TYPE_HLIST`: types recorded by `susfs_add_sus_type()`. A lookup of a recorded
  type returns `-EINVAL`, which is what an app sees for a type that does not exist.
- `SUS_AVD_ALLOWED_HLIST`: the pre-injection `avd.allowed` recorded by
  `susfs_add_sus_avd_allowed_by_type()`, restored by `susfs_spoof_avd_allowed_rcu()`.

Both tables are empty until this tree registers entries. An empty table means the
driver-added types resolve normally and the injected allow rules show up in access
decisions, which is exactly the fingerprint the hiding is supposed to remove.

The `latest_granting` bump matters for the same reason. 2.4.0 computes the access reply
with stock `avd_init()`, which sets `avd.seqno = policy->latest_granting`. If the driver
still increments that value on reload, the reply carries a sequence number a stock boot
never produces. 2.3.0 hid this by answering from `backup_sepolicy` and hardcoding
`avd.seqno = 1`; 2.4.0 no longer goes through that path.

## Checking a new patch set

1. Diff `kernel_patches/KernelSU/10_enable_susfs_for_ksu.patch` against the pinned
   version. New hunks touching `kernel/selinux/sepolicy.c`, `kernel/selinux/rules.c`,
   or `kernel/feature/selinux_hide.c` are contract changes.
2. Diff `kernel_patches/50_add_susfs_in_<kmi>.patch`. New `extern` declarations for
   `ksu_*`, `my_*`, `fake_status` or `initialize_fake_status` are new requirements on
   this tree.
3. Run `scripts/check-susfs-upstream.sh --upstream <checkout> --kernel-source <dir>`.
   It covers the 50-patch symbol list, the Kconfig set, the command dispatch and the
   10-patch failure fingerprint, but it does **not** check the call sites in the table
   above.
4. Build and boot. A missing registration has no build-time symptom.

## Known gaps in this tree

- `kernel/selinux/sepolicy.c` has no SUSFS call sites, so the 2.4.0 tables stay empty.
- `kernel/selinux/rules.c` still increments `latest_granting` and passes it to
  `reset_avc_cache()`.
- `scripts/check-susfs-upstream.sh` does not inspect the driver-side call sites.
- `builtin-integration.md` marks the 2.4.0 rows as not applied; flip them once the call
  sites above exist.
