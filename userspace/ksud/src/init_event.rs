use crate::module::{ScriptWait, handle_updated_modules, prune_modules};
use crate::utils::is_safe_mode;
use crate::{
    assets, defs, ksucalls, magic_mount, metamodule, mount_guard, restorecon,
    utils::{self},
};
use anyhow::{Context, Result};
use log::{error, info, warn};
use std::{path::Path, time::Instant};

pub fn on_post_fs_data() -> Result<()> {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_post_fs_data");
        return Ok(());
    }

    ksucalls::report_post_fs_data();

    // Restore user-claimed dynamic-manager signatures. The kernel table is
    // volatile, so replay the persisted list on every boot; it is idempotent.
    if let Err(e) = crate::dynamic_manager::load_and_apply() {
        warn!("apply dynamic manager signatures failed: {e}");
    }

    utils::umask(0);

    // Clear all temporary module configs early
    if let Err(e) = crate::module_config::clear_all_temp_configs() {
        warn!("clear temp configs failed: {e}");
    }

    let _ = catch_bootlog("logcat", &["logcat", "-b", "all"]);
    let _ = catch_bootlog("dmesg", &["dmesg", "-w", "-r"]);

    let boot_safe_mode = crate::utils::is_safe_mode();
    if boot_safe_mode && let Err(e) = crate::kernel_module::disable_all_for_safe_mode() {
        warn!("disable managed kernel modules failed: {e}");
    }

    if utils::has_magisk() {
        warn!("Magisk detected, skip post-fs-data!");
        return Ok(());
    }

    let safe_mode = boot_safe_mode;
    let wait = ScriptWait::Until(Instant::now() + defs::BOOT_STAGE_TIMEOUT);

    if safe_mode {
        // we should still ensure module directory exists in safe mode
        // because we may need to operate the module dir in safe mode
        warn!("safe mode, skip common post-fs-data.d scripts");
    } else {
        // Capture before common/module scripts can mount or replace any target.
        // A late-loaded SUSFS cannot recreate this snapshot at later stages.
        crate::susfs::boot("pre-mount");
        // Then exec common post-fs-data scripts
        if let Err(e) = crate::module::exec_common_scripts("post-fs-data.d", wait) {
            warn!("exec common post-fs-data scripts failed: {e}");
        }
    }

    assets::ensure_binaries(true).with_context(|| "Failed to extract bin assets")?;

    crate::hot_install::clean_trash();

    // if we are in safe mode, we should disable all modules
    if safe_mode {
        warn!("safe mode, skip post-fs-data scripts and disable all modules!");
        if let Err(e) = crate::module::disable_all_modules() {
            warn!("disable all modules failed: {e}");
        }
        return Ok(());
    }

    if let Err(e) = handle_updated_modules() {
        warn!("handle updated modules failed: {e}");
    }

    if let Err(e) = prune_modules() {
        warn!("prune modules failed: {e}");
    }

    // Reconcile next-boot state after changes made outside module commands.
    if let Err(e) = crate::module::regenerate_preinit() {
        warn!("regenerate preinit failed: {e}");
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {e}");
    }

    // load sepolicy.rule
    if crate::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }

    if let Err(e) = crate::profile::apply_sepolies() {
        warn!("apply root profile sepolicy failed: {e}");
    }

    // load feature config
    crate::feature::init_forced_features();
    if is_safe_mode() {
        warn!("safe mode, skip load feature config");
    } else if let Err(e) = crate::feature::init_features() {
        warn!("init features failed: {e}");
    }
    crate::uts_view::boot_apply();
    crate::netisolate::boot_apply();

    match crate::kernel_module::autoload_with_startup_timeout() {
        Ok(Some(report)) => {
            if !report.failures.is_empty() {
                for failure in &report.failures {
                    warn!(
                        "kernel module autoload {} failed: {}",
                        failure.id, failure.message
                    );
                }
            }
            info!(
                "kernel module autoload: {} attempted, {} loaded, {} skipped",
                report.attempted.len(),
                report.loaded.len(),
                report.skipped.len()
            );
        }
        Ok(None) => warn!("kernel module autoload is still running in the background"),
        Err(e) => warn!("kernel module autoload scheduler failed: {e:#}"),
    }

    // execute metamodule post-fs-data script first (priority)
    if let Err(e) = metamodule::exec_stage_script("post-fs-data", wait) {
        warn!("exec metamodule post-fs-data script failed: {e}");
    }

    // exec modules post-fs-data scripts
    if let Err(e) = crate::module::exec_stage_script("post-fs-data", wait) {
        warn!("exec post-fs-data scripts failed: {e}");
    }

    // Plugin callbacks share the same boot budget as the scripts above: a plugin
    // that no longer fits is left for the next boot rather than extending the wait.
    if let Err(e) = crate::plugin::stage::run(
        Path::new(defs::PLUGIN_DIR),
        Path::new(defs::PLUGIN_LOCK_DIR),
        Path::new(defs::PLUGIN_STATE_DIR),
        "post-fs-data",
        wait.deadline(),
    ) {
        warn!("exec plugin post-fs-data callbacks failed: {e}");
    }

    crate::boot_hide::boot_apply();

    // load system.prop
    if let Err(e) = crate::module::load_system_prop() {
        warn!("load system.prop failed: {e}");
    }

    // Folk Mount: run the selected provider (metamodule script or the built-in
    // executor; never both) before the post-mount stage to preserve the
    // KernelSU contract.
    if let Err(e) = magic_mount::mount_selected_provider() {
        warn!("module mount failed: {e:#}");
    }

    match crate::umount_config::apply() {
        Ok(report) if report.failed > 0 => warn!(
            "umount config: {} applied, {} present, {} failed",
            report.applied, report.existing, report.failed
        ),
        Ok(_) => {}
        Err(e) => warn!("umount config could not be read or applied: {e:#}"),
    }

    crate::susfs::boot("post-mount");
    run_stage("post-mount", wait);

    if let Err(e) = crate::zygisk::post_fs_data(safe_mode) {
        warn!("FolkZygisk startup skipped/failed: {e:#}");
    }

    if let Err(e) = crate::sui::post_fs_data(safe_mode) {
        warn!("Sui startup skipped/failed: {e:#}");
    }

    std::env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    Ok(())
}

pub fn run_stage(stage: &str, wait: ScriptWait) {
    utils::umask(0);

    if utils::has_magisk() {
        warn!("Magisk detected, skip {stage}");
        return;
    }

    if crate::utils::is_safe_mode() {
        warn!("safe mode, skip {stage} scripts");
        return;
    }

    crate::susfs::boot(stage);

    if let Err(e) = crate::module::exec_common_scripts(&format!("{stage}.d"), wait) {
        warn!("Failed to exec common {stage} scripts: {e}");
    }

    // execute metamodule stage script first (priority)
    if let Err(e) = metamodule::exec_stage_script(stage, wait) {
        warn!("Failed to exec metamodule {stage} script: {e}");
    }

    // execute regular modules stage scripts
    if let Err(e) = crate::module::exec_stage_script(stage, wait) {
        warn!("Failed to exec {stage} scripts: {e}");
    }

    // Later stages do not block the boot, so plugin callbacks are dispatched in
    // the background. The bounded stages above keep their deadline instead.
    if matches!(wait, ScriptWait::NoWait)
        && let Err(e) = crate::plugin::stage::background(stage)
    {
        warn!("Failed to dispatch plugin {stage} callbacks: {e}");
    }
}

pub fn on_services() {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_services");
        return;
    }

    match ksucalls::report_services() {
        Ok(true) => {}
        Ok(false) => {
            info!("services already started, skipping");
            return;
        }
        Err(e) => {
            error!("Failed to report services: {e:#}");
            return;
        }
    }

    info!("on_services triggered!");
    if let Err(e) = crate::zygisk::services() {
        warn!("FolkZygisk health check failed: {e:#}");
    }
    if let Err(e) = crate::sui::services() {
        warn!("Sui health check failed: {e:#}");
    }
    run_stage("service", ScriptWait::NoWait);
}

pub fn on_boot_completed() {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_boot_completed");
        return;
    }

    ksucalls::report_boot_complete();
    info!("on_boot_completed triggered!");
    crate::preinit::on_boot_completed();
    // The boot reached a usable state, so the step recorded for it finished:
    // dropping that record is what tells the next boot which provider may run
    // again (a record from an earlier boot has to stay, see `mount_guard`).
    if let Err(error) = mount_guard::clear_if_ours(std::path::Path::new(defs::MOUNT_INFLIGHT)) {
        warn!("could not clear the completed mount guard: {error:#}");
    }
    // A post-fs-data migration may have had nothing to adopt yet (the package
    // scan had not produced anything); by now it certainly has.
    if let Err(e) = crate::dynamic_manager::finish_migration() {
        warn!("finish dynamic manager migration failed: {e:#}");
    }
    if let Err(e) = crate::zygisk::boot_completed() {
        warn!("FolkZygisk boot receipt failed: {e:#}");
    }

    run_stage("boot-completed", ScriptWait::NoWait);
}

fn catch_bootlog(logname: &str, command: &[&str]) -> Result<()> {
    use std::os::unix::process::CommandExt;
    use std::process::Stdio;

    let logdir = Path::new(defs::LOG_DIR);
    utils::ensure_dir_exists(logdir)?;
    let bootlog = logdir.join(format!("{logname}.log"));
    let oldbootlog = logdir.join(format!("{logname}.old.log"));

    if bootlog.exists() {
        std::fs::rename(&bootlog, oldbootlog)?;
    }

    let bootlog = std::fs::File::create(bootlog)?;

    let mut args = vec!["-s", "9", defs::BOOTLOG_TIMEOUT];
    args.extend_from_slice(command);
    // timeout -s 9 30s logcat > boot.log
    let result = unsafe {
        std::process::Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                utils::switch_cgroups();
                Ok(())
            })
            .args(args)
            .stdout(Stdio::from(bootlog))
            .spawn()
    };

    if let Err(e) = result {
        warn!("Failed to start logcat: {e:#}");
    }

    Ok(())
}
