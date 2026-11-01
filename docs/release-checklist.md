# Release Checklist

Maintainer-facing checklist for cutting a release and for the update path users actually
experience. Keep it in sync with `.github/workflows/release.yml`,
`manager/app/src/main/java/me/weishu/kernelsu/ui/util/Downloader.kt` and `manager/build.gradle.kts`.

## 1. How a release reaches a user

1. Pushing a `v*` tag runs the `Release` workflow: the DDK matrix builds `kernelsu.ko` and
   `nomount.ko` per KMI, `sui.yml` builds the Sui payload, `ksuinit` and `ksud` follow, the manager
   APK is built and repacked with that `ksud`, and `softprops/action-gh-release` publishes the APK
   plus a zip containing the standalone `kernelsu.ko` and `ksud` builds.
2. The manager calls `GET https://api.github.com/repos/LyraVoid/FinaSU/releases/latest` every time
   the Home screen becomes visible or the app returns to the foreground (`checkNewVersion()`),
   unless the "Check for updates" setting is off.
3. It picks the first asset whose name ends in `.apk` and parses `v<versionName>_<versionCode>` out
   of the file name (regex `v(.+?)_(\d+)`, pinned by `DownloaderTest`).
4. `HomeUiState.hasUpdate` is just `latestVersionInfo.versionCode > BuildConfig.VERSION_CODE`, so the
   update is offered only when the release carries a strictly higher version code.
5. The Home banner opens the release body as the changelog dialog (or jumps straight to the download
   when the body is empty), then hands the APK URL to the browser. There is no in-app install.

## 2. Version numbers

- `versionCode = 30000 + git rev-list --count HEAD`, in `manager/build.gradle.kts` for the APK and in
  `kernel/Kbuild` for the module, so the APK, `ksud` and the embedded LKM always share one number.
- `versionName = git describe --tags --always`. The tag must sit exactly on the released commit,
  otherwise the name becomes `0.1.0-3-gabc1234` and that is the string shown in the update banner.
- Never release from a commit older than the previous release: a lower version code means nobody is
  offered the update. Backport fixes by merging forward and tagging the new tip.
- Reference point: `v0.1.0-dev1` shipped version code `32817` and `v1.0.0` shipped `32932`, so any tag
  placed on a later commit is offered to every existing installation.

## 3. Tag convention

| Tag | Published as | Reaches users through the update check |
| --- | --- | --- |
| `v0.1.0` | full release | yes, becomes `releases/latest` |
| `v0.1.0-beta1`, `v0.1.0-dev1`, `v0.1.0-rc1` | GitHub pre-release | no, skipped by `releases/latest` |

The workflow derives this from the tag name (`prerelease: ${{ contains(github.ref_name, '-') }}`), so
any hyphen in the tag makes it a test build. This matters because `/releases/latest` returns the most
recently *published* full release: publishing a plain-tagged test build (or anything after the stable
release) takes over the update prompt for everyone.

## 4. Steps

1. Confirm a clean tree and that the release commit is on the remote. Development and release tags are
   cut from `dev` (tracking `origin/dev`, `push.default=upstream`), so publish it with a plain
   `git push`; the tag itself is what CI builds, so any remote branch carrying that commit is enough.
   `master` only moves when `dev` is merged into it.
2. Tag and push: `git tag -a v0.1.0 -m "FinaSU v0.1.0" && git push origin v0.1.0`.
3. Wait for the `Release` workflow (LKM and NoMount via DDK, the Sui payload, ksuinit, ksud for three
   Android ABIs plus the desktop targets, manager APK, repack and resign, then the GitHub release).
4. Write the release notes. The body is the changelog shown in the manager's update dialog; an empty
   body silently skips that dialog. It can be edited after the workflow finishes, or generated with
   `generate_release_notes: true` on the release step.
5. Run the verification below.
6. Announce it. The Telegram post is automatic when `BOT_TOKEN` and `CHAT_ID` secrets exist; the
   manager picks the rest up by itself on the next app open.

## 5. Verification

```bash
curl -s https://api.github.com/repos/LyraVoid/FinaSU/releases/latest |
  jq -r '.tag_name, .prerelease, (.assets[] | select(.name | endswith(".apk")) | .name)'
```

- `tag_name` is the tag just pushed, `prerelease` is `false` for a stable release.
- The APK asset name follows `<AppName>_<versionName>_<versionCode>-release.apk`; the embedded version
  code must exceed the previous release. A name that does not match the regex (for example
  `app-release.apk`) makes the update invisible to every user.
- One APK asset per release: the parser returns the first match it finds. The accompanying
  `FinaSU_<tag>_kernel-bundle.zip` is ignored by the updater.
- The release job fails early when the asset name does not match
  `<app>_v<versionName>_<versionCode>-<buildType>.apk`, so a silent update outage is caught in CI.
- `aapt2 dump badging` on the downloaded APK should report `package: name='me.yuki.fina'` with the
  expected version code and name.
- The signing key must stay the same across releases, otherwise Android refuses to install the update
  over an existing one. The current releases use `manager/key.jks` (CN=FolkSU, certificate SHA-256
  `2d89b50a...4458`), which is also the `KEYSTORE` secret used by CI.

## 6. What the APK carries

| Payload | arm64 | x86_64 | riscv64 |
| --- | --- | --- | --- |
| `kernelsu.ko` (8 KMIs) | yes | yes | no |
| `nomount.ko` (8 KMIs) | yes | yes | no |
| FolkZygisk, 32 and 64 bit | yes | yes | no |
| Sui (`sui/payload.json` and its files) | yes | yes | no |
| `ksuinit`, `waitsys`, `bootctl`, `busybox`, `mkbootfs` | yes | yes | yes |

- The modules, the FolkZygisk tree and the Sui tree live under `userspace/ksud/bin/<abi>/`, are
  gitignored, and are staged by CI before `ksud` is compiled: modules in `ddk-lkm.yml`, FolkZygisk in
  `ksud.yml`, Sui in `sui.yml` plus `userspace/sui/package.py`. Whatever is not staged is silently
  absent from the APK.
- riscv64 has no LKM, NoMount, FolkZygisk or Sui payload upstream. The manager reports the missing
  module or driver instead of failing, and NoMount falls back to the built-in Folk Mount engine.
- Release assets are the APK and `FinaSU_<tag>_kernel-bundle.zip`. The zip contains `arm64/` and
  `x86_64/` directories with all eight KMI `kernelsu.ko` files for each architecture, plus `ksud/`
  with the Android and desktop builds. Other build outputs stay on the run page as workflow
  artifacts, and GitHub attaches the tag's source archives, which also keeps the source available
  for the licenses bundled into the payload.
- Users in LKM mode see "A newer bundled LKM is available. Tap to update." on Home whenever the
  running module version differs from the APK version code; they install it from the Install page and
  reboot, with no PC and no boot image flashing.
- Every packaged `kernelsu.ko` uses the default Tracepoint backend. SUSFS Inline requires an ARM64
  Built-In kernel and is never selected by the LKM release path.
- Users running a built-in kernel must rebuild and reflash the boot image themselves from the tag.

## 7. Verify the packaged payloads

```bash
unzip -p FinaSU_vX.Y.Z_<code>-release.apk lib/arm64-v8a/libksud.so > /tmp/libksud.so
for p in 'android1[2-7]-[0-9]+\.[0-9]+_kernelsu\.ko' 'android1[2-7]-[0-9]+\.[0-9]+_nomount\.ko' \
         'folkzygisk/AGPL-3\.0\.txt' 'sui/payload\.json'; do
  printf '%-46s %s\n' "$p" "$(grep -a -o -E -- "$p" /tmp/libksud.so | wc -l)"
done
```

Expect `8`, `8`, `1`, `1` on arm64 and x86_64 and `0` for all of them on riscv64. A zero where the
table above says yes means a staging step was skipped and the APK shipped without that payload.

```bash
unzip -Z1 FinaSU_<tag>_kernel-bundle.zip |
  awk '/_kernelsu\.ko$/ { ko++ } /^ksud\// { ksud++ } END { print ko, ksud, (ko == 16 && ksud == 8 ? "ok" : "bad") }'
```

The bundle must contain 16 `kernelsu.ko` files and eight `ksud` binaries.

## 8. Pitfalls

- Do not set `draft: true` on the release step: `action-gh-release` then uploads the assets and never
  publishes the draft.
- Do not cut releases with `workflow_dispatch`. There is no tag, so the release would be named after
  the branch and, being hyphen-free, would publish as a full release and take over `releases/latest`.
  Guarding the `release` job with `if: github.ref_type == 'tag'` is the inverse fix.
- If the repository ever has no published full release (only pre-releases), `releases/latest` does not
  resolve and the manager silently reports no update.
- Every test build published before this convention existed (`v0.1.0-pre4` through `v0.1.0-dev1`) is
  a full release. They stay eligible for `releases/latest` until a newer full release supersedes them.
- A payload that a local `build.sh all` stages but CI does not ships as an APK that looks complete and
  silently lacks it. `v1.0.0` shipped without the Sui payload exactly that way; run the check in
  section 7 on every release build.
- The vendored Sui tree is excluded from ShellCheck (`ignore_paths` in `shellcheck.yml`) because its
  upstream scripts are device-side content that has to stay byte for byte identical.
