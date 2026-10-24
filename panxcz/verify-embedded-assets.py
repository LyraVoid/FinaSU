#!/usr/bin/env python3
"""Prove that libksud.so carries the files boot-patch will ask it for.

ksud embeds userspace/ksud/bin/<abi>/* through rust-embed's `compression`
feature, which stores each file as a bare DEFLATE stream next to the rest of the
ELF, with no index.  deflate-find.c inflates those streams; this script compares
their SHA-256 with the files that were staged for embedding.

Usage:
    verify-embedded-assets.py dist/*.apk userspace/ksud/bin/aarch64 \
        --require _susfs_guard.ko --require _kernelsu.ko

With an .apk the script reads lib/arm64-v8a/libksud.so out of the archive;
anything else is treated as the shared library itself.
"""

import argparse
import hashlib
import os
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "deflate-find.c")


def read_library(path, workdir):
    if not zipfile.is_zipfile(path):
        return path

    with zipfile.ZipFile(path) as apk:
        names = [n for n in apk.namelist() if n.endswith("/libksud.so")]
        if not names:
            sys.exit(f"{path}: no libksud.so inside")
        if len(names) > 1:
            sys.exit(f"{path}: several libksud.so ({', '.join(names)})")
        target = os.path.join(workdir, "libksud.so")
        with open(target, "wb") as fh:
            fh.write(apk.read(names[0]))
        print(f"reading {names[0]} from {path}")
        return target


def build_scanner(workdir):
    scanner = os.path.join(workdir, "deflate-find")
    subprocess.run(
        ["cc", "-O2", "-o", scanner, SOURCE, "-lz"],
        check=True,
    )
    return scanner


def payload_hashes(scanner, binary, workdir):
    outdir = os.path.join(workdir, "payloads")
    os.makedirs(outdir, exist_ok=True)
    result = subprocess.run(
        [scanner, binary, outdir],
        check=True,
        capture_output=True,
        text=True,
    )
    digests = {}
    for line in result.stdout.splitlines():
        name, _, size = line.partition(" ")
        path = os.path.join(outdir, name)
        if not os.path.isfile(path):
            continue
        with open(path, "rb") as fh:
            digests[hashlib.sha256(fh.read()).hexdigest()] = int(size)
    return digests


def digest(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", help="libksud.so or the APK that contains it")
    parser.add_argument("assets", help="directory staged for embedding")
    parser.add_argument(
        "--require",
        action="append",
        default=[],
        metavar="SUFFIX",
        help="asset suffix that must be present (repeatable)",
    )
    args = parser.parse_args()

    expected = {}
    for name in sorted(os.listdir(args.assets)):
        if name.endswith(".ko") or name == "ksuinit":
            expected[name] = digest(os.path.join(args.assets, name))
    if not expected:
        sys.exit(f"{args.assets}: nothing staged")

    for suffix in args.require:
        if not any(name.endswith(suffix) for name in expected):
            sys.exit(f"{args.assets}: no asset ends with {suffix}")

    with tempfile.TemporaryDirectory() as workdir:
        library = read_library(args.binary, workdir)
        scanner = build_scanner(workdir)
        present = payload_hashes(scanner, library, workdir)

    missing = [name for name, sha in expected.items() if sha not in present]
    for name in expected:
        mark = "ok  " if expected[name] in present else "MISS"
        print(f"{mark} {name}")
    if missing:
        sys.exit("not embedded: " + ", ".join(missing))

    print(f"all {len(expected)} assets are embedded")


if __name__ == "__main__":
    main()
