#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
set -eu

[ "$#" -eq 1 ] || {
    echo "usage: $0 <kernel-source>" >&2
    exit 2
}

kernel_source=$1
[ -d "$kernel_source" ] || {
    echo "SUSFS Inline: kernel source not found: $kernel_source" >&2
    exit 1
}

failed=0

require_hook() {
    file=$1
    symbol=$2

    if [ ! -f "$kernel_source/$file" ]; then
        echo "SUSFS Inline: missing kernel file: $file" >&2
        failed=1
    elif ! awk -v symbol="$symbol" '
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            pos = index(line, symbol "(")
            prefix = pos ? substr(line, 1, pos - 1) : ""
            if (line !~ /^extern([[:space:]]|$)/ && pos && prefix !~ /\/\/|\/\*/)
                found = 1
        }
        END { exit !found }
    ' "$kernel_source/$file"; then
        echo "SUSFS Inline: missing $symbol call in $file" >&2
        failed=1
    fi
}

reject_hook() {
    file=$1
    symbol=$2

    if [ -f "$kernel_source/$file" ] && grep -Fq "$symbol" "$kernel_source/$file"; then
        echo "SUSFS Inline: conflicting hook $symbol in $file" >&2
        failed=1
    fi
}

require_hook drivers/input/input.c ksu_handle_input_handle_event
require_hook fs/exec.c ksu_handle_execveat
require_hook fs/exec.c ksu_handle_execveat_sucompat
require_hook fs/exec.c ksu_handle_post_execveat_sucompat
require_hook fs/exec.c ksu_setup_preinit_file
require_hook fs/exec.c ksu_preinit_handle_post_execveat
require_hook fs/open.c ksu_handle_faccessat
require_hook fs/read_write.c ksu_handle_sys_read
require_hook fs/stat.c ksu_handle_stat
require_hook fs/stat.c ksu_handle_vfs_fstat
require_hook kernel/reboot.c ksu_handle_sys_reboot
require_hook kernel/sys.c ksu_handle_setresuid

for file in drivers/input/input.c fs/exec.c fs/open.c fs/read_write.c fs/stat.c kernel/reboot.c kernel/sys.c; do
    reject_hook "$file" CONFIG_KSU_MANUAL_HOOK
done

reject_hook fs/read_write.c ksu_vfs_read_hook
reject_hook drivers/input/input.c ksu_input_hook
reject_hook fs/exec.c ksu_execveat_hook
reject_hook fs/read_write.c ksu_init_rc_hook
reject_hook fs/stat.c ksu_init_rc_hook

if [ "$failed" -ne 0 ]; then
    exit 1
fi

echo "SUSFS Inline: direct entry-point check passed for $kernel_source"
