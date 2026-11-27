#!/usr/bin/env bash
# Fetch the official SUSFS kernel patches for non-GKI kernels from
# https://gitlab.com/simonpunk/susfs4ksu (the upstream the patches come from).
#
# The fork cannot patch a 4.14 / 4.19 / 5.4 boot image the way it patches a GKI
# one: those kernels have no loadable "kernelsu.ko" and no SUSFS LKM guard, so
# SUSFS has to be built into the kernel. What this fork can honestly ship for
# them is upstream's own patch set, unmodified, with the module that goes with
# it. Nothing here is rewritten, generated or backported.
#
#   usage: panxcz/fetch-legacy-susfs.sh [output-dir]   (default: legacy)
#
# Each ref is pinned to the commit this fork was built against; a moved branch
# is an error, not a silent upgrade, so a published zip can always be traced
# back to one upstream commit.
set -euo pipefail

REPO_URL=${SUSFS_REPO_URL:-https://gitlab.com/simonpunk/susfs4ksu.git}
OUT_DIR=${1:-legacy}

# ref -> commit id as of 2025-02-23, the last legacy release (SUSFS v1.5.5).
declare -A REFS=(
    [kernel-4.14]=77905b5a071e6f3669e3b1814cea30147c0801da
    [kernel-4.19]=001e69919c6271f690fd00b17e4c721c9e599152
    [kernel-5.4]=76affd70abf61d77feb0a132f61365d6848505df
)

mkdir -p "$OUT_DIR"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

for ref in "${!REFS[@]}"; do
    want=${REFS[$ref]}
    kernel=${ref#kernel-}
    name="susfs-legacy-$ref"
    echo "=== $ref ($want)"

    git clone --quiet --depth 1 --branch "$ref" "$REPO_URL" "$work/$ref"
    got=$(git -C "$work/$ref" rev-parse HEAD)
    if [ "$got" != "$want" ]; then
        echo "$ref moved: expected $want, got $got" >&2
        echo "Review the new commit before publishing it." >&2
        exit 1
    fi

    rm -rf "$OUT_DIR/$name"
    mkdir -p "$OUT_DIR/$name"
    # kernel_patches/{50_*.patch, KernelSU/10_*.patch, fs/, include/} are what the
    # upstream README tells a user to copy into their kernel tree.
    cp -a "$work/$ref/kernel_patches" "$OUT_DIR/$name/"
    cp -a "$work/$ref/ksu_module_susfs" "$OUT_DIR/$name/"
    cp -a "$work/$ref/README.md" "$work/$ref/LICENSE" "$OUT_DIR/$name/"

    version=$(sed -n 's/^#define SUSFS_VERSION "\(.*\)"/\1/p' \
        "$OUT_DIR/$name/kernel_patches/include/linux/susfs.h")
    test -n "$version"
    test -s "$OUT_DIR/$name/kernel_patches/50_add_susfs_in_kernel-$kernel.patch"
    test -s "$OUT_DIR/$name/kernel_patches/KernelSU/10_enable_susfs_for_ksu.patch"
    test -s "$OUT_DIR/$name/ksu_module_susfs/tools/ksu_susfs_arm64"

    cat > "$OUT_DIR/$name/SOURCE.txt" <<EOF
source:  $REPO_URL
ref:     $ref
commit:  $want
susfs:   $version (kernel patch set for Linux $kernel, non-GKI)
license: GPL-2.0-only, see LICENSE

Fetch with panxcz/fetch-legacy-susfs.sh. Files are byte-identical to upstream.
EOF

    (cd "$OUT_DIR" && rm -f "$name.zip" && zip -qr "$name.zip" "$name")
    echo "    $name.zip  $(du -h "$OUT_DIR/$name.zip" | cut -f1)  $version"
done
