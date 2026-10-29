#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
set -eu

SELF_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(dirname "$SELF_DIR")

EXPECTED_COMMIT=4a4cb1be85a4694372d3efb9420de69982eac29a
DEFAULT_KMI=android15-6.6

usage() {
    cat <<'EOF'
Usage: check-susfs-upstream.sh --upstream DIR [--kernel-source DIR] [--kmi KMI]

Checks a pinned SUSFS patch set against this checkout.

  --upstream DIR       Checkout of the SUSFS patch repository
  --kernel-source DIR  Pristine kernel source for the selected KMI
  --kmi KMI            Kernel KMI (default: android15-6.6)
  -h, --help           Show this help

Inside an Android DDK image, --kernel-source defaults to /opt/ddk/src/<KMI>.
The patch repository commit and the 10-patch failure fingerprint must be
reviewed deliberately before either baseline is updated.
EOF
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

upstream=
kernel_source=${SUSFS_KERNEL_SOURCE:-}
kmi=$DEFAULT_KMI

while [ "$#" -gt 0 ]; do
    case "$1" in
    --upstream)
        [ "$#" -ge 2 ] || die "--upstream requires a path"
        upstream=$2
        shift 2
        ;;
    --kernel-source)
        [ "$#" -ge 2 ] || die "--kernel-source requires a path"
        kernel_source=$2
        shift 2
        ;;
    --kmi)
        [ "$#" -ge 2 ] || die "--kmi requires a value"
        kmi=$2
        shift 2
        ;;
    -h | --help)
        usage
        exit 0
        ;;
    *)
        die "unknown argument: $1"
        ;;
    esac
done

[ -n "$upstream" ] || {
    usage >&2
    exit 2
}

if [ -z "$kernel_source" ] && [ -d "/opt/ddk/src/$kmi" ]; then
    kernel_source=/opt/ddk/src/$kmi
fi
[ -n "$kernel_source" ] || die "--kernel-source is required outside a DDK image"
[ -d "$kernel_source" ] || die "kernel source not found: $kernel_source"

patch_dir=$upstream/kernel_patches
ten_patch=$patch_dir/KernelSU/10_enable_susfs_for_ksu.patch
fifty_patch=$patch_dir/50_add_susfs_in_gki-$kmi.patch
susfs_def=$patch_dir/include/linux/susfs_def.h

[ -d "$upstream/.git" ] || die "not a git checkout: $upstream"
for file in "$ten_patch" "$fifty_patch" "$susfs_def"; do
    [ -f "$file" ] || die "missing patch input: $file"
done

actual_commit=$(git -c safe.directory="$upstream" -C "$upstream" rev-parse HEAD 2>/dev/null) ||
    die "cannot read upstream commit"
[ "$actual_commit" = "$EXPECTED_COMMIT" ] ||
    die "upstream is $actual_commit, expected $EXPECTED_COMMIT; review the new patch set first"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/finasu-susfs-check.XXXXXX")
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

echo "== interfaces =="
sed -n 's/^+\(menu\)\{0,1\}config \(KSU_SUSFS[A-Z0-9_]*\).*/\2/p' "$ten_patch" |
    LC_ALL=C sort -u >"$tmp/upstream-options"
sed -n 's/^[[:space:]]*\(menu\)\{0,1\}config \(KSU_SUSFS[A-Z0-9_]*\).*/\2/p' \
    "$ROOT/kernel/Kconfig" | LC_ALL=C sort -u >"$tmp/local-options"
if ! diff -u "$tmp/upstream-options" "$tmp/local-options"; then
    die "SUSFS Kconfig options differ"
fi
printf 'Kconfig options: %s\n' "$(wc -l <"$tmp/local-options" | tr -d ' ')"

grep -oE '\b(ksu|my)_[A-Za-z0-9_]+|\bfake_status\b|\binitialize_fake_status\b' \
    "$fifty_patch" | LC_ALL=C sort -u >"$tmp/required-symbols"
missing_symbol=0
while IFS= read -r symbol; do
    if ! grep -RqsF --include='*.c' --include='*.h' -- "$symbol" "$ROOT/kernel"; then
        printf 'missing symbol: %s\n' "$symbol" >&2
        missing_symbol=1
    fi
done <"$tmp/required-symbols"
[ "$missing_symbol" -eq 0 ] || die "50-patch compatibility symbols are incomplete"
printf 'KSU-side symbols: %s\n' "$(wc -l <"$tmp/required-symbols" | tr -d ' ')"

sed -n '/deprecated/!s/^#define \(CMD_SUSFS_[A-Z0-9_]*\).*/\1/p' "$susfs_def" |
    LC_ALL=C sort -u >"$tmp/commands"
missing_command=0
while IFS= read -r command; do
    if ! grep -Fqs -- "$command" "$ROOT/kernel/extension/susfs.c"; then
        printf 'missing command: %s\n' "$command" >&2
        missing_command=1
    fi
done <"$tmp/commands"
[ "$missing_command" -eq 0 ] || die "SUSFS command dispatch is incomplete"
printf 'Supercall commands: %s\n' "$(wc -l <"$tmp/commands" | tr -d ' ')"

echo "== 10-patch compatibility =="
mkdir "$tmp/tree"
(
    cd "$ROOT"
    tar \
        --exclude='*.o' \
        --exclude='*.ko' \
        --exclude='.*.cmd' \
        --exclude='*.mod' \
        --exclude='*.mod.c' \
        --exclude='Module.symvers' \
        --exclude='compile_commands.json' \
        -cf - kernel
) | tar -C "$tmp/tree" -xf -

ten_status=0
patch -d "$tmp/tree" -p1 --dry-run --batch --forward <"$ten_patch" \
    >"$tmp/ten.log" 2>&1 || ten_status=$?
case "$ten_status" in
0 | 1) ;;
*)
    cat "$tmp/ten.log" >&2
    die "10 patch probe did not run"
    ;;
esac

if ! grep -q '^checking file ' "$tmp/ten.log"; then
    cat "$tmp/ten.log" >&2
    die "10 patch produced no file results"
fi
if grep -Eq "can't find file to patch|Only garbage|malformed patch" "$tmp/ten.log"; then
    cat "$tmp/ten.log" >&2
    die "10 patch references a file outside the compatible tree"
fi

awk '
/^checking file / {
    file = $3
    sub(/^kernel\//, "", file)
    next
}
/^[[:space:]]*Hunk #[0-9]+ FAILED/ {
    line = $0
    sub(/^[[:space:]]*Hunk #/, "", line)
    sub(/ FAILED.*/, "", line)
    print file ":" line
}
/^[[:space:]]*Reversed \(or previously applied\)/ {
    print file ":reversed"
}
' "$tmp/ten.log" | LC_ALL=C sort >"$tmp/ten.observed"

# runtime hunk 11 removes local init-rc, exec and input owners; this tree keeps them.
cat >"$tmp/ten.expected" <<'EOF'
core/init.c:3
core/init.c:5
core/init.c:7
core/init.c:8
feature/adb_root.c:1
feature/adb_root.c:6
feature/selinux_hide.c:1
feature/selinux_hide.c:10
feature/selinux_hide.c:13
feature/selinux_hide.c:15
feature/selinux_hide.c:3
feature/selinux_hide.c:4
feature/selinux_hide.c:5
feature/selinux_hide.c:6
feature/selinux_hide.c:7
feature/selinux_hide.c:8
feature/selinux_hide.c:9
feature/sucompat.c:2
feature/sucompat.c:3
feature/sucompat.c:4
feature/sucompat.h:1
hook/setuid_hook.c:1
hook/setuid_hook.c:2
hook/setuid_hook.h:reversed
runtime/ksud_integration.c:11
runtime/ksud_integration.c:5
selinux/rules.c:1
selinux/selinux.c:1
supercall/dispatch.c:1
supercall/dispatch.c:3
supercall/dispatch.c:4
supercall/dispatch.c:6
supercall/dispatch.c:7
supercall/dispatch.c:8
EOF

if ! diff -u "$tmp/ten.expected" "$tmp/ten.observed"; then
    cat "$tmp/ten.log" >&2
    die "10 patch failure fingerprint changed; review every hunk before updating the baseline"
fi
printf 'known ported hunks: %s\n' "$(wc -l <"$tmp/ten.observed" | tr -d ' ')"

awk '
/^checking file / {
    file = $3
    sub(/^kernel\//, "", file)
    next
}
/succeeded .*fuzz/ && file ~ /^(Kbuild|Kconfig|Makefile)$/ {
    print file
}
' "$tmp/ten.log" | LC_ALL=C sort -u >"$tmp/dangerous-fuzz"
if [ -s "$tmp/dangerous-fuzz" ]; then
    printf 'warning: do not apply the 10 patch; it fuzzes build files: %s\n' \
        "$(tr '\n' ' ' <"$tmp/dangerous-fuzz")"
fi

echo "== 50-patch kernel source =="
fifty_status=0
patch -d "$kernel_source" -p1 --dry-run --batch --forward <"$fifty_patch" \
    >"$tmp/fifty.log" 2>&1 || fifty_status=$?
if [ "$fifty_status" -ne 0 ] ||
    grep -Eq 'FAILED|Reversed \(or previously applied\)' "$tmp/fifty.log"; then
    cat "$tmp/fifty.log" >&2
    die "50 patch does not apply cleanly to $kernel_source"
fi
offset_count=$(grep -Ec 'offset -?[0-9]+ lines' "$tmp/fifty.log" || true)
fuzz_count=$(grep -Ec 'fuzz [0-9]+' "$tmp/fifty.log" || true)
printf 'clean apply; offsets: %s, fuzzed hunks: %s\n' "$offset_count" "$fuzz_count"

echo "PASS: pinned SUSFS interface and patch inputs match this checkout"
