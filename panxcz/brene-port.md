# BRENE v0.0.72 → FinaSU `susfs/brene.rs`

BRENE is a KernelSU module: `post-fs-data.sh`, `boot-completed.sh`, the
`ksu_susfs`/`susfs` helper and a WebUI. FinaSU already owns the SUSFS control
path, so the module is not installed and its scripts are not executed. The two
stages below run inside `ksud` instead, from `susfs::boot("post-mount")` and
`susfs::boot("boot-completed")`; an explicit **Apply now** runs both
immediately.

Defaults mirror BRENE's `config.sh`. The profile is controlled by the
`brene` object in the SUSFS JSON and by the new **Built-in hardening** page in
the manager.

## post-fs-data stage

| BRENE | implementation |
|---|---|
| `hide_sus_mnts_for_non_su_procs 1` | ABI `0x55561` |
| `enable_avc_log_spoofing 1` | `/proc/susfs_avc_spoof`, ABI `0x60010` fallback |
| `set_cmdline_or_bootconfig` | generated from `/proc/bootconfig` or `/proc/cmdline`, ABI `0x555b0` |
| `set_uname` | auto/GKI release from `/proc/version` + KMI, or `custom_uname_*` |
| `spoof_system_properties` / fingerprint / date / UTC / security patch | `ResetProp` + `rebuild_all(force)` |
| `hide_addon_d`, `hide_custom_rom_paths`, `hide_suspicious_pty` | `add_sus_path[_loop]` / `add_sus_map` |
| `spoof_hosts` | fake hosts file, `add_open_redirect` scheme 3, static kstat |
| `custom_sus_*` lists | `brene.sus_paths`, `sus_path_loops`, `sus_maps`, `redirects` |

## boot-completed stage

| BRENE | implementation |
|---|---|
| `feature set su_compat / kernel_umount / selinux_hide` + save | `feature::set_feature` / `feature::save_config` |
| developer options, USB, wireless debugging | tri-state `Option<bool>`; **default is "leave unchanged"** |
| phantom-process restriction prop | `persist.sys.fflag.override.settings_enable_monitor_phantom_procs=false` |
| logcat/logd 64K | `logcat -G 64K` + `persist.logd.size` |
| SELinux enforcing | `/sys/fs/selinux/enforce` |
| SurfaceFlinger saturation / refresh rate | `service call` (off by default) |
| remove custom ROM / PIF props | property table scan + delete |
| `/data/local/tmp` hiding and permissions | `add_sus_path_loop`, `chmod 0771`, `shell:shell`, `shell_data_file`, static kstat |
| non-standard `/sdcard/Android` and `/sdcard` dirs | `add_sus_path_loop` |
| custom recovery traces | fixed path list |
| framework-res.apk / Lineage strings | `add_sus_map` / empty-target redirects |
| libstagefright redirect | empty fake file, `add_open_redirect` scheme 3 |
| `..5.u.S` cleanup + inotifyd | native cleanup + `ksud susfs brene-watch` inotify loop |
| KSU folders in shared storage | removed at boot and by the watcher |
| `set_priority_uid_filter` | sent as ABI `0x60030`; the shipped LKM does not implement it, so it is listed under `skipped` |
| `notify-module-mounted` + kernel umount list | `ksucalls::report_module_mounted` + `umount_list_add(path, 2)` |
| `resetprop -c --force` | `rebuild_all(true)` |

## Deliberate divergences

* Developer options, USB debugging and wireless debugging are tri-state. BRENE
  writes `0` on first boot; this port leaves them alone unless the user chooses
  a value, because losing ADB is not recoverable for everyone.
* `hide_third_party_apps` depends on a kernel command this guard does not have.
  It is attempted and reported as skipped; BRENE on the same kernel would fail
  the same way, silently.
* BRENE's `custom_*.txt` files under `/data/adb/brene` are not read. The same
  lists live in the FinaSU JSON (`sus_paths`, `sus_path_loops`, `sus_maps`,
  `kernel_umounts`, `redirects`).
* The watcher is a `ksud` subprocess, not `inotifyd` + shell scripts.

## Safety

* Every ABI path is checked for absolute form, length, NUL/control bytes and
  `.`/`..` components; generated names are allowed to contain spaces.
* The profile obeys safe mode, requires root, and never runs when SUSFS is not
  `compatible`.
* UTS View wins: if it is active, auto-uname is skipped. An explicit
  `settings.uname` also disables the built-in uname generator.
