#!/bin/sh
set -eu

GKI_ROOT=$(pwd)
FINASU_URL="${FINASU_URL:-${FOLKSU_URL:-https://github.com/LyraVoid/FinaSU.git}}"
FINASU_REF="${FINASU_REF:-${FOLKSU_REF:-dev}}"
FINASU_DIR="${FINASU_DIR:-${FOLKSU_DIR:-}}"
FINASU_KEEP_SOURCE="${FINASU_KEEP_SOURCE:-${FOLKSU_KEEP_SOURCE:-0}}"

display_usage() {
    echo "Usage: $0 [--cleanup] [--dir <path>] [<commit-or-tag>]"
    echo "  --cleanup:              Reverts the changes made by this script."
    echo "  --dir <path>:           Uses an existing FinaSU checkout instead of cloning."
    echo "  <commit-or-tag>:        Checks out the given ref (default: \$FINASU_REF, $FINASU_REF)."
    echo "  -h, --help:             Displays this usage information."
    echo "  (no args):              Clones or updates FinaSU and hooks it into the kernel tree."
    echo ""
    echo "Environment: FINASU_URL, FINASU_REF, FINASU_DIR, FINASU_KEEP_SOURCE"
    echo "Run this from the kernel source root (the directory that contains drivers/)."
}

initialize_variables() {
    if test -d "$GKI_ROOT/common/drivers"; then
        DRIVER_DIR="$GKI_ROOT/common/drivers"
    elif test -d "$GKI_ROOT/drivers"; then
        DRIVER_DIR="$GKI_ROOT/drivers"
    else
        echo '[ERROR] "drivers/" directory not found.'
        exit 127
    fi

    DRIVER_MAKEFILE=$DRIVER_DIR/Makefile
    DRIVER_KCONFIG=$DRIVER_DIR/Kconfig
}

perform_cleanup() {
    echo "[+] Cleaning up..."
    if [ -L "$DRIVER_DIR/kernelsu" ]; then
        rm "$DRIVER_DIR/kernelsu"
        echo "[-] Symlink removed."
    fi
    if grep -q "kernelsu" "$DRIVER_MAKEFILE"; then
        sed -i '/kernelsu/d' "$DRIVER_MAKEFILE"
        echo "[-] Makefile reverted."
    fi
    if grep -q 'drivers/kernelsu/Kconfig' "$DRIVER_KCONFIG"; then
        sed -i '/drivers\/kernelsu\/Kconfig/d' "$DRIVER_KCONFIG"
        echo "[-] Kconfig reverted."
    fi
    if [ -d "$GKI_ROOT/FinaSU" ] && [ "$FINASU_KEEP_SOURCE" != "1" ]; then
        rm -rf "$GKI_ROOT/FinaSU"
        echo "[-] FinaSU directory deleted."
    fi
}

setup_finasu() {
    ref="${1:-$FINASU_REF}"

    if [ -n "$FINASU_DIR" ]; then
        SRC_DIR="$FINASU_DIR"
    else
        SRC_DIR="$GKI_ROOT/FinaSU"
        if [ ! -d "$SRC_DIR" ]; then
            git clone "$FINASU_URL" "$SRC_DIR"
        fi
        cd "$SRC_DIR"
        git fetch origin --tags || true
        git checkout "$ref"
        cd "$GKI_ROOT"
    fi

    [ -d "$SRC_DIR/kernel" ] || {
        echo "[ERROR] $SRC_DIR/kernel not found."
        exit 127
    }

    cd "$DRIVER_DIR"
    ln -sf "$(realpath --relative-to="$DRIVER_DIR" "$SRC_DIR/kernel")" "kernelsu"
    echo "[+] Symlink created."

    if grep -q "kernelsu" "$DRIVER_MAKEFILE"; then
        echo "[-] Makefile already references kernelsu."
    else
        printf "\nobj-\$(CONFIG_KSU) += kernelsu/\n" >>"$DRIVER_MAKEFILE"
        echo "[+] Modified Makefile."
    fi

    if grep -q 'drivers/kernelsu/Kconfig' "$DRIVER_KCONFIG"; then
        echo "[-] Kconfig already sources drivers/kernelsu/Kconfig."
    else
        sed -i "/endmenu/i source \"drivers/kernelsu/Kconfig\"" "$DRIVER_KCONFIG"
        echo "[+] Modified Kconfig."
    fi

    echo '[+] Done.'
    echo "    Next: enable CONFIG_KSU=y and build the kernel image."
    echo "    Manager signature: pass KSU_EXPECTED_SIZE/KSU_EXPECTED_HASH (see docs/builtin-integration.md)."
}

if [ "$#" -eq 0 ]; then
    initialize_variables
    setup_finasu
else
    REF=""
    while [ "$#" -gt 0 ]; do
        case "$1" in
        -h | --help)
            display_usage
            exit 0
            ;;
        --cleanup)
            initialize_variables
            perform_cleanup
            exit 0
            ;;
        --dir)
            shift
            [ "$#" -gt 0 ] || {
                echo "[ERROR] --dir requires a path."
                exit 127
            }
            FINASU_DIR="$1"
            ;;
        -*)
            echo "[ERROR] Unknown option: $1"
            display_usage
            exit 127
            ;;
        *)
            REF="$1"
            ;;
        esac
        shift
    done
    initialize_variables
    setup_finasu "$REF"
fi
