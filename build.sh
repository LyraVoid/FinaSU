#!/usr/bin/env bash
# ANDROID_NDK_HOME is exported inside a subshell on purpose: every build stage
# gets it for its own cargo invocation and the parent shell keeps none of it, so
# ShellCheck's subshell-scope notes (SC2030/SC2031) do not apply here.
# shellcheck disable=SC2030,SC2031
#
# FinaSU build driver - a local mirror of the GitHub Actions pipeline.
#
#   .github/workflows/build-lkm.yml       build-lkm       -> ./build.sh lkm
#   .github/workflows/ksud.yml            build-ksud      -> ./build.sh ksud
#   .github/workflows/build-manager.yml   build-manager   -> ./build.sh apk
#   .github/workflows/build-manager.yml   repack-manager  -> ./build.sh repack
#
# Typical full run:
#   ./build.sh all
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# --------------------------------------------------------------------------- #
# Defaults (every one of these can be overridden from the environment)
# --------------------------------------------------------------------------- #
# A single-KMI local build serves only that KMI; late-load/boot-patch will fail
# on other KMIs. Use a CI release package (pack_lkm: true, full KMI matrix) for
# all-KMI support, or widen KMIS here.
KMIS="${KMIS:-android15-6.6}"
ABIS="${ABIS:-arm64-v8a}"
DDK_RELEASE="${DDK_RELEASE:-20260828}"       # .github/workflows/ddk-lkm.yml default
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}" # same as .github/scripts/setup-rust-build.sh
KSUD_BUILD_TYPE="${KSUD_BUILD_TYPE:-release}"
APP_BUILD_TYPE="${APP_BUILD_TYPE:-release}"
SUI_BUILD_TYPE="${SUI_BUILD_TYPE:-release}"
OUT_DIR="${OUT_DIR:-dist}"
KEYSTORE_PROPS="${KEYSTORE_PROPS:-$REPO_ROOT/manager/keystore.properties}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
# CI builds against NDK r29. Newer NDKs break the static ksuinit link
# (duplicate symbol rust_eh_personality: their libc.a already provides it).
NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    if [[ -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]]; then
        ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
    else
        printf '\033[1;33m[!]\033[0m NDK %s not found under %s/ndk, falling back to the newest one\n' \
            "$NDK_VERSION" "$ANDROID_HOME" >&2
        ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
    fi
    export ANDROID_NDK_HOME
fi

# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

read_keystore_cfg() {
    [[ -f "$KEYSTORE_PROPS" ]] || die "missing $KEYSTORE_PROPS (copy sign.example.properties and fill it in)"
    set -a
    # The keystore file is a user-provided path, so its content cannot be
    # analysed up front.
    # shellcheck disable=SC1090
    source "$KEYSTORE_PROPS"
    set +a
    : "${KEYSTORE_FILE:?KEYSTORE_FILE not set in $KEYSTORE_PROPS}"
    : "${KEYSTORE_PASSWORD:?KEYSTORE_PASSWORD not set in $KEYSTORE_PROPS}"
    : "${KEY_ALIAS:?KEY_ALIAS not set in $KEYSTORE_PROPS}"
    : "${KEY_PASSWORD:=$KEYSTORE_PASSWORD}"
    case "$KEYSTORE_FILE" in
        /*) KEYSTORE_PATH="$KEYSTORE_FILE" ;;
        *) KEYSTORE_PATH="$(dirname "$(realpath "$KEYSTORE_PROPS")")/$KEYSTORE_FILE" ;;
    esac
    [[ -f "$KEYSTORE_PATH" ]] || die "keystore not found: $KEYSTORE_PATH"
}

# Exports the values manager/app/build.gradle.kts reads through the apksign plugin.
export_signing_env() {
    read_keystore_cfg
    export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_PATH"
    export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
    export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
    export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"
}

# Derives the pair the kernel expects for the *second* trusted manager signature.
cert_size_and_hash() {
    read_keystore_cfg
    local der; der="$(mktemp)"
    # shellcheck disable=SC2064
    trap "rm -f '$der'" RETURN
    keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_PATH" \
        -storepass "$KEYSTORE_PASSWORD" -file "$der" >/dev/null
    CERT_SIZE_HEX="$(printf '0x%04x' "$(stat -c%s "$der")")"
    CERT_HASH="$(sha256sum "$der" | awk '{print $1}')"
    log "manager cert: size=$CERT_SIZE_HEX sha256=$CERT_HASH"
}

# Runs a command inside the DDK image as root, then gives the artifacts back to us.
# (CI runs as root too; this just avoids leaving root-owned files in the tree.)
ddk_run() {
    local image="$1"; shift
    local git_common_dir
    local git_mounts=()
    git_common_dir="$(git rev-parse --path-format=absolute --git-common-dir)"
    case "$git_common_dir" in
        "$REPO_ROOT"/*) ;;
        *) git_mounts=(-v "$git_common_dir:$git_common_dir:ro") ;;
    esac
    docker run --rm --privileged \
        -v "$REPO_ROOT":/build -w /build/kernel \
        "${git_mounts[@]}" \
        -e CONFIG_KSU=m -e CC=clang \
        -e GIT_CONFIG_COUNT=1 \
        -e GIT_CONFIG_KEY_0=safe.directory \
        -e GIT_CONFIG_VALUE_0=/build \
        "$image" "$@"
    docker run --rm \
        -v "$REPO_ROOT/kernel":/k \
        --entrypoint /bin/sh "$image" \
        -c "chown -R $(id -u):$(id -g) /k" >/dev/null 2>&1 || \
        warn "could not restore ownership of kernel/ (artifacts may be root-owned)"
}

# --------------------------------------------------------------------------- #
# Steps
# --------------------------------------------------------------------------- #
build_lkm() {
    have docker || die "docker is required to build the LKM"
    cert_size_and_hash
    mkdir -p userspace/ksud/bin/aarch64

    local kmi image
    for kmi in $KMIS; do
        image="ghcr.io/ylarod/ddk-min:${kmi}-${DDK_RELEASE}"
        log "building LKM for $kmi ($image)"
        docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"

        rm -f kernel/kernelsu.ko
        ddk_run "$image" make \
            "KSU_REQUIRE_PREINIT=1" \
            "KSU_EXPECTED_SIZE2=$CERT_SIZE_HEX" \
            "KSU_EXPECTED_HASH2=$CERT_HASH" \
            "KSU_SECOND_SIGNATURE_RELEASE=1"

        [[ -f kernel/kernelsu.ko ]] || die "kernel/kernelsu.ko was not produced"

        local dest="kernel/${kmi}_kernelsu.ko"
        mv kernel/kernelsu.ko "$dest"
        if have llvm-strip; then
            llvm-strip -d "$dest"
        else
            warn "llvm-strip not found, skipping symbol strip"
        fi
        cp "$dest" userspace/ksud/bin/aarch64/
        log "staged userspace/ksud/bin/aarch64/${kmi}_kernelsu.ko"
    done
}

# NoMount's kernel module is vendored at userspace/nomount (GPL-2.0-only, see
# userspace/nomount/UPSTREAM.md) and built out-of-tree with the same DDK matrix
# as kernelsu.ko. The stripped module is staged into ksud's embedded assets as
# <kmi>_nomount.ko; it is never flashed and has no standalone module zip.
build_nomount() {
    have docker || die "docker is required to build the NoMount LKM"
    mkdir -p userspace/ksud/bin/aarch64 userspace/nomount/build

    local kmi image rel dest
    for kmi in $KMIS; do
        image="ghcr.io/ylarod/ddk-min:${kmi}-${DDK_RELEASE}"
        log "building NoMount LKM for $kmi ($image)"
        docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"

        # The container sees the repository mounted at /build, so the output
        # path has to be given in container coordinates.
        rel="userspace/nomount/build/${kmi}_nomount.ko"
        dest="$REPO_ROOT/$rel"
        rm -f "$dest"
        docker run --rm --privileged \
            -v "$REPO_ROOT":/build \
            -e CONFIG_NOMOUNT=m -e CC=clang -e NOMOUNT_OUT="/build/$rel" \
            "$image" sh /build/userspace/nomount/build-lkm.sh

        [[ -f "$dest" ]] || die "NoMount LKM for $kmi was not produced"
        cp "$dest" userspace/ksud/bin/aarch64/
        log "staged userspace/ksud/bin/aarch64/${kmi}_nomount.ko"

        # The container runs as root, so hand the artifacts back to us.
        docker run --rm -v "$REPO_ROOT/userspace/nomount":/n \
            --entrypoint /bin/sh "$image" \
            -c "chown -R $(id -u):$(id -g) /n" >/dev/null 2>&1 || \
            warn "could not restore ownership of userspace/nomount/ (artifacts may be root-owned)"
    done
}

abi_to_triple() {
    case "$1" in
        arm64-v8a)   echo aarch64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86)         echo i686-linux-android ;;
        x86_64)      echo x86_64-linux-android ;;
        riscv64)     echo riscv64-linux-android ;;
        *) die "unknown abi: $1" ;;
    esac
}

# Sui is vendored at userspace/sui (GPL-3.0, see userspace/sui/UPSTREAM.md). Its
# Gradle build produces the upstream release zip; package.py maps that archive
# into per-ABI ksud assets. Nothing here flashes a module or runs Sui's own
# installer/lifecycle scripts.
build_sui() {
    have java || die "a JDK is required to build the Sui payload"

    case "$SUI_BUILD_TYPE" in
        release|debug) ;;
        *) die "SUI_BUILD_TYPE must be release or debug" ;;
    esac
    log "building the Sui payload (gradle zip${SUI_BUILD_TYPE^})"
    ( cd userspace/sui && "./gradlew" "zip${SUI_BUILD_TYPE^}" --console=plain )

    local abi bindir candidate zip="" version version_code
    for candidate in userspace/sui/out/*-"$SUI_BUILD_TYPE".zip; do
        [[ -f "$candidate" ]] || continue
        if [[ -z "$zip" || "$candidate" -nt "$zip" ]]; then
            zip="$candidate"
        fi
    done
    [[ -f "$zip" ]] || die "the Sui payload zip was not produced"
    version="$(sed -n 's/^version=//p' userspace/sui/payload-version.txt)"
    version_code="$(sed -n 's/^version_code=//p' userspace/sui/payload-version.txt)"
    [[ -n "$version" ]] || die "userspace/sui/payload-version.txt has no version"
    [[ -n "$version_code" ]] || die "userspace/sui/payload-version.txt has no version_code"

    for abi in $ABIS; do
        case "$abi" in
            riscv64) warn "Sui has no riscv64 payload; skipping $abi"; continue ;;
        esac
        bindir="$(abi_to_bindir "$abi")"
        log "packaging the Sui payload for $abi"
        python3 userspace/sui/package.py --zip "$zip" --target "$bindir" \
            --assets "userspace/ksud/bin/$bindir" --version "$version" \
            --version-code "$version_code" --force
    done
}

abi_to_bindir() {
    case "$1" in
        arm64-v8a)   echo aarch64 ;;
        armeabi-v7a) echo armv7 ;;
        x86)         echo i686 ;;
        x86_64)      echo x86_64 ;;
        riscv64)     echo riscv64 ;;
        *) die "unknown abi: $1" ;;
    esac
}

build_folkzygisk() {
    have make || die "make is required"
    have python3 || die "python3 is required"
    local ndk="${ANDROID_NDK_HOME:-}"
    [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME is not set"

    local abi assets archs=() built=" " destination
    for abi in $ABIS; do
        case "$abi" in
            arm64-v8a)
                assets=aarch64
                archs=(arm64-v8a armeabi-v7a)
                ;;
            x86_64)
                assets=x86_64
                archs=(x86_64 x86)
                ;;
            *)
                warn "FolkZygisk has no package target for $abi; skipping"
                continue
                ;;
        esac
        if [[ "$built" == *" $assets "* ]]; then
            continue
        fi
        built+="$assets "

        log "building FolkZygisk for ${archs[*]}"
        make -C userspace/folkzygisk BUILD_TYPE=release ARCHS="${archs[*]}" \
            NDK_PATH="$ndk"

        destination="userspace/ksud/bin/$assets/folkzygisk"
        [[ "$destination" == userspace/ksud/bin/*/folkzygisk ]] ||
            die "unsafe FolkZygisk asset path: $destination"
        rm -rf -- "$destination"
        python3 userspace/folkzygisk/package.py \
            --stage userspace/folkzygisk/build/stage/release \
            --assets "userspace/ksud/bin/$assets" --target "$assets"
    done
}

# ksuinit is a separate binary that ksud embeds; boot-patch fails with
# "asset not found: ksuinit" if the built ksud does not carry it.
#
# ksuinit runs as the ramdisk `init`, before /system is mounted, so it MUST be
# statically linked: the dynamic linker (/system/bin/linker64) does not exist at
# that point, and a PIE build makes the device bootloop forever. This mirrors
# the RUSTFLAGS used by .github/workflows/ksuinit.yml.
build_ksuinit() {
    have cargo || die "cargo is required"
    local ndk="${ANDROID_NDK_HOME:-}"
    [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME is not set"

    local abi triple arch clang builtins bindir src
    for abi in $ABIS; do
        triple="$(abi_to_triple "$abi")"
        arch="${triple%%-*}"
        case "$arch" in armv7*) arch=arm ;; esac
        clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}${ANDROID_API_LEVEL}-clang"
        [[ -x "$clang" ]] || die "NDK clang not found: $clang"
        builtins="$("$clang" --print-resource-dir)/lib/linux/libclang_rt.builtins-${arch}-android.a"
        [[ -f "$builtins" ]] || die "libclang_rt.builtins not found: $builtins"

        log "building ksuinit for $abi (static, api $ANDROID_API_LEVEL)"
        (
            export ANDROID_NDK_HOME="$ndk"
            # shellcheck disable=SC1091
            source .github/scripts/setup-rust-build.sh "$triple" "$ANDROID_API_LEVEL"
            export RUSTFLAGS="-C target-feature=+crt-static -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wno-unused-command-line-argument -C link-arg=$builtins"
            cargo build --package ksuinit --target="$triple" "--$KSUD_BUILD_TYPE"
        )

        src="target/$triple/$KSUD_BUILD_TYPE/ksuinit"
        [[ -f "$src" ]] || die "ksuinit not found at $src"
        file "$src" | grep -q "statically linked" || die "ksuinit is not statically linked: $(file -b "$src")"
        bindir="$(abi_to_bindir "$abi")"
        mkdir -p "userspace/ksud/bin/$bindir"
        cp "$src" "userspace/ksud/bin/$bindir/ksuinit"
        log "staged userspace/ksud/bin/$bindir/ksuinit"
    done
}

build_ksud() {
    have cargo || die "cargo is required"
    local ndk="${ANDROID_NDK_HOME:-}"
    [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME is not set"
    build_folkzygisk
    # rust-embed compresses the native assets at compile time and does not
    # reliably notice their content changing in an incremental build.
    cargo clean --package ksud

    local abi triple
    for abi in $ABIS; do
        triple="$(abi_to_triple "$abi")"
        log "building ksud for $abi (api $ANDROID_API_LEVEL, $KSUD_BUILD_TYPE)"
        (
            export ANDROID_NDK_HOME="$ndk"
            # shellcheck disable=SC1091
            source .github/scripts/setup-rust-build.sh "$triple" "$ANDROID_API_LEVEL"
            cargo build --target="$triple" "--$KSUD_BUILD_TYPE" --manifest-path ./userspace/ksud/Cargo.toml
        )
        log "ksud[$abi] -> target/$triple/$KSUD_BUILD_TYPE/ksud"
    done
}

build_apk() {
    export_signing_env
    log "gradle :app:assemble$([[ $APP_BUILD_TYPE == release ]] && echo Release || echo Debug)"
    ( cd manager && "./gradlew" ":app:assemble${APP_BUILD_TYPE^}" )
    log "APK: $(find "manager/app/build/outputs/apk/$APP_BUILD_TYPE" -name '*.apk' -printf '%p\n' 2>/dev/null | tail -1)"
}

build_repack() {
    export_signing_env
    have python3 || die "python3 is required"

    local args=() abi
    for abi in $ABIS; do args+=(-a "$abi"); done

    log "repacking + resigning APK with ksud"
    python3 repack_apk.py repack \
        -b "$APP_BUILD_TYPE" -t "$KSUD_BUILD_TYPE" \
        "${args[@]}" \
        -K "$KEYSTORE_PATH" -A "$KEY_ALIAS" \
        -P "$KEYSTORE_PASSWORD" -S "$KEY_PASSWORD" \
        --strip -o "$OUT_DIR"
    log "done - see $OUT_DIR/"
}

usage() {
    cat <<'EOF'
FinaSU build driver

usage: ./build.sh <step> [step...]

steps:
  lkm      build the kernel module(s) via the DDK docker image, stage them
           into userspace/ksud/bin/aarch64/ so ksud embeds them
  nomount  build the vendored NoMount module(s) the same way, stage them as
           userspace/ksud/bin/aarch64/<kmi>_nomount.ko for ksud to embed
  sui      build the vendored Sui payload and stage it as
           userspace/ksud/bin/<bindir>/sui/ for ksud to embed
  folkzygisk
           build and stage the FolkZygisk binaries embedded by ksud
  ksuinit  cross-compile the ksuinit helper ksud embeds (needed by boot-patch)
  ksud     build packaged FolkZygisk binaries, then cross-compile ksud
  apk      gradle assembleRelease (signed with manager/keystore.properties)
  repack   inject the freshly built ksud into the APK and re-sign it
  all      lkm -> nomount -> sui -> ksuinit -> ksud -> apk -> repack

environment overrides:
  KMIS=android15-6.6        kernel modules to build
  ABIS=arm64-v8a            ABIs for ksud + repack
  DDK_RELEASE=20260828      DDK image release tag
  ANDROID_API_LEVEL=26      cargo-ndk api level
  KSUD_BUILD_TYPE=release   cargo profile (release/debug)
  APP_BUILD_TYPE=release    gradle build type (release/debug)
  SUI_BUILD_TYPE=release    Sui payload build type (release/debug; debug retains logs)
  OUT_DIR=dist              repack output directory
  KEYSTORE_PROPS=...        path to the signing properties file
EOF
}

main() {
    [[ $# -gt 0 ]] || { usage; exit 1; }
    local step
    for step in "$@"; do
        case "$step" in
            lkm)     build_lkm ;;
            nomount) build_nomount ;;
            sui)     build_sui ;;
            folkzygisk) build_folkzygisk ;;
            ksuinit) build_ksuinit ;;
            ksud)    build_ksud ;;
            apk)     build_apk ;;
            repack)  build_repack ;;
            all)     build_lkm; build_nomount; build_sui; build_ksuinit; build_ksud; build_apk; build_repack ;;
            -h|--help|help) usage ;;
            *) die "unknown step: $step (try --help)" ;;
        esac
    done
}

main "$@"
