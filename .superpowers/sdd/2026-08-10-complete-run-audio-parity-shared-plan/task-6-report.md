# Task 6 report: exact BizHawk 2.11 GPGX observer identity

## Status

Implemented on `bugfix/ai-s1-audio-parity-frontier`. The branch remains
unmerged and unpushed for human review. This task freezes only the stock
runtime/source/toolchain and the generic typed observer metadata contract;
Task 7 still owns the feasibility-proven buffered observer ABI values and the
first patched native core.

## RED evidence

The Java tests were written first and run with:

```text
mvn -Dmse=off \
  -Dtest='com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace,com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore' \
  test
```

Test compilation failed with 29 expected missing-feature errors for
`ObserverRuntimeIdentity`, its callback/buffered records, the managed adapter,
new runtime artifact slots, and the required profile/metadata fields.

The C# source-lock tests were hand-registered in the non-SDK runner and run
before their locks/scripts existed:

```text
BIZHAWK_HOME=<repository-root>/docs/BizHawk-2.11-linux-x64 \
  tools/bizhawk-headless/test.sh \
  --filter GpgxAudioObserverSourceLockTests --jobs 1
```

The runner compiled. Three cases failed because the three lock JSON files did
not exist. The initial create-new probe passed for the wrong reason (a missing
script); it was tightened during GREEN to require the script's exact
`output already exists` refusal and now covers all four output-producing
scripts.

A final fail-closed audit added RED cases for the complete toolchain
file/symlink-tree digest, rejection of the scripts' earlier
`--untracked-files=no` behavior, no-replace publication, and snapshot-before-use
input handling. The C# suite failed 1 of 4 as expected at each audit boundary
before the scripts were tightened.

## Metadata and validation

- `Metadata` carries a required sealed `ObserverRuntimeIdentity`.
  `CallbackObserverIdentity` is a logical ID with no path semantics.
  `BufferedNativeObserverIdentity` fails closed on its stable installation/core
  IDs, positive bounds, 16-digit lowercase BuildID, lowercase watch-mask and
  service-manifest SHA-256 values, enabled state, in-capacity occupancy, and
  zero overflow.
- Every profile pins one typed observer identity for every producer kind, and
  registry snapshots include that map. Metadata/profile mismatches fail closed.
- `ProducerRuntimeIdentity` now records `CALLBACK_ONLY`, `REFLECTION`, or
  `FIRST_CLASS`. Buffered-native reference captures require the complete native
  artifact set. Managed patch/source DLL hashes are paired; reflection forbids
  both and first-class requires both.
- The old Java constructor remains and selects `CALLBACK_ONLY`, preserving the
  existing M68K callback producer API.
- Streaming metadata JSON has exact-field callback/buffered variants with
  strict duplicate detection. Fixed independent callback and buffered metadata
  vectors cover round-trip bytes and reject missing, duplicate, unknown,
  callback/adapter-mismatched, disabled, overflowed, out-of-capacity, and extra
  identity fields. No S2/S3K buffered profile and no provisional Task 7 ABI
  value is installed.

## Exact source and native toolchain

The machine-readable locks pin:

- BizHawk commit/tree `427556b5ef3ac437eba754d90c5e7e9096c9a8df` /
  `7281227ed2f3b89c0962b2792b28539e35361c6b`;
- Genesis Plus GX commit/tree `051d430d3d1b54625f9900c8f152d7f232e06daf` /
  `1bb96ca74d660d383e70d9cd56b88906a0773519`;
- musl commit/tree `2063abc4e16c84218757b1db10d3cdf9f36ef3f8` /
  `a9969a63cd1780cdcc4c09745a8789206a72b8b4`;
- every critical Waterbox input byte, exact Mantic package, compiler/linker,
  compiler runtime, host build executable/library, zstd source/binary, sysroot,
  environment, and recipe digest. The corrected
  `libclang-common-16-dev` SHA-256 is
  `ada57e3ac045bb324397c6d269dbad56a0b0f3608c89d321d1fed38206570ff5`.

Historical paths are present only as pinned UTF-8 hex and are decoded at
runtime. No source file contains the historical absolute path. Acquisition
fetches only commit object IDs, checks detached commits/trees/submodules and
critical bytes, and publishes only into an absent caller path. Input
verification rejects modified, untracked, ignored, wrong, or uninitialized
source and partial 2.11.1 caches. Toolchain preparation has no
package-repository access and refuses a different package, executable, library,
sysroot, zstd, file/symlink tree, or output path.
All build scripts snapshot caller-owned source/toolchain/package/SDK/NuGet and
stock inputs before validating and using the staged bytes. Publication reserves
the absent target with pinned same-filesystem
`mv -T --no-copy --no-clobber`; it publishes the complete sibling stage in one
rename and never replaces even a racing empty directory.
The pinned `/usr/bin/mv` SHA-256 is
`4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df`;
the C# suite proves both absent-target publication and racing-empty-target
retention.

The two independent source/toolchain commands were:

```text
TASK6_WORKTREE=$PWD
fetch-source.sh --output "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a"
fetch-source.sh --output "$TASK6_WORKTREE/target/audio-parity/native/task6-source-b"
fetch-source.sh --bizhawk-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a" --gpgx-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a/waterbox/gpgx/Genesis-Plus-GX" --musl-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a/waterbox/musl" --output "$TASK6_WORKTREE/target/audio-parity/native/task6-source-e"
fetch-source.sh --bizhawk-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a" --gpgx-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a/waterbox/gpgx/Genesis-Plus-GX" --musl-repository "$TASK6_WORKTREE/target/audio-parity/native/task6-source-a/waterbox/musl" --output "$TASK6_WORKTREE/target/audio-parity/native/task6-source-f"
prepare-toolchain.sh --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-e" --packages /tmp --output "$TASK6_WORKTREE/target/audio-parity/native/task6-toolchain-f"
prepare-toolchain.sh --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-f" --packages /tmp --output "$TASK6_WORKTREE/target/audio-parity/native/task6-toolchain-g"
```

They produced:

```text
source identities:       exact commits/trees above; both clean
sysroot files:           235
sysroot tree SHA-256:    fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1
zstd SHA-256:            7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307
emulibc object SHA-256:  c787fe4acc581a8b4787f737133425abe65a589200ce049aeec9780626afe620
verified input identity: 409b9debb122dd5e5d0719874e99d0f3d3f71c25cf8731bfa1ec61462d0c295b
complete toolchain tree: 9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521
```

The complete regular-file/symlink manifests from both final snapshot-first
toolchain runs are identical at `9caa5c02...`; both verify to the same
`409b9deb...` input identity.

## Stock core reproduction

The two required fresh invocations were:

```text
reproduce-stock-core.sh \
  --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-e" \
  --toolchain "$TASK6_WORKTREE/target/audio-parity/native/task6-toolchain-f" \
  --stock "$BIZHAWK_HOME" \
  --output "$TASK6_WORKTREE/target/audio-parity/native/task6-stock-g"
reproduce-stock-core.sh \
  --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-f" \
  --toolchain "$TASK6_WORKTREE/target/audio-parity/native/task6-toolchain-g" \
  --stock "$BIZHAWK_HOME" \
  --output "$TASK6_WORKTREE/target/audio-parity/native/task6-stock-h"
```

Each performed the unmodified emulibc/GPGX build at the decoded historical
path and compressed with `--stdout --ultra -22 --threads=0`. Results:

```text
gpgx.wbx size:        39558192
gpgx.wbx SHA-256:     b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218
BuildID:              7696adca7ad14b79
gpgx.wbx.zst size:    400161
gpgx.wbx.zst SHA-256: c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12
identity SHA-256:     6699430e19a1e0ef06bfe0c80924741648262305d2a0b3e5b7dfd97b1b4ed735
```

Both decompressed files, compressed files, and full-input identity manifests
compare equal to each other. Both core files compare byte-for-byte to stock.
The stock compressed core, managed assemblies, and Waterbox host retained their
locked hashes after all runs.

## Managed reproduction and adapter decision

The managed lock pins SDK 8.0.414 (archive SHA-256
`7786bbe5093e3a5d354a1ffa56083b6a32ad12837a83170f1f3b51ad7df28516`),
runtime 8.0.20, MSBuild 17.11.41, the exact source/build scripts, and a
114-package canonical NuGet manifest with SHA-256
`e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290`.
The repository's historical workflow only selected `.NET 8` and its current
Ubuntu runner; it did not freeze the SDK patch, runner image, or PowerShell.

The two independent managed commands were:

```text
reproduce-stock-managed.sh --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-e" --sdk-archive /tmp/dotnet-sdk-8.0.414-linux-x64.tar.gz --nuget-packages /tmp/task6-managed-nuget-8.0.414 --stock "$BIZHAWK_HOME" --output "$TASK6_WORKTREE/target/audio-parity/native/task6-managed-g"
reproduce-stock-managed.sh --source "$TASK6_WORKTREE/target/audio-parity/native/task6-source-f" --sdk-archive /tmp/dotnet-sdk-8.0.414-linux-x64.tar.gz --nuget-packages /tmp/task6-managed-nuget-8.0.414 --stock "$BIZHAWK_HOME" --output "$TASK6_WORKTREE/target/audio-parity/native/task6-managed-h"
```

Two offline fixed-path builds from fresh exact sources produced identical
mismatch manifests (SHA-256
`efadaf168670ce0ae5f8f5dc7705ddaa94e898bce96134b9ebc86c31ceb6d6d2`):

```text
                         stock                              reproduced
Cores size              8774144                            8779776
Cores SHA-256           0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7
                                                            f7e7ea11f05adb7bcdc1f55c09810f873abfe06debdc3f3b100185f20a69c031
Common size             422912                             421376
Common SHA-256          f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4
                                                            96f494af9be13f52dc63ab3d430b15641fc142cf469339a8bf013e67b99b757e
```

Both `cmp` gates failed as expected and the script exited 3. It retains only
the compact non-reconstructive identity evidence, not the generated DLLs. The
managed adapter is therefore locked to `REFLECTION`, and
`patched_managed_dll_permitted` is false. No managed DLL is shipped.
A final tightened-script run produced the same identity byte-for-byte. The
nested managed bubblewrap build cannot run inside the agent's outer workspace sandbox
(it exits 1 before MSBuild and leaves an empty log); the identical offline
command run on the host reaches the expected status 3 mismatch gate.

## GREEN verification

The final Task 1-3 Java regression command ran
`TestCompleteRunAudioTrace`, `TestCompleteRunAudioCaptureStore`, and
`TestCompleteRunAudioComparator`: 101 tests, 0 failures, 0 errors, 0 skipped.
The non-SDK C# source-lock suite is green: 4 passes. All lock JSON files parse,
and `bash -n` passes for all five scripts. `git diff --check` is clean.

## Concerns

- Task 7 must use the locked native baseline and must not infer ABI version,
  event size, capacity, or S2/S3K profile identity from this task.
- Task 8 must use reflection against the exact stock managed hashes. Recovering
  a first-class managed adapter would require a separately proven historical
  runner/SDK/PowerShell identity and two stock-byte matches.
- Detailed source, SDK, package, toolchain, and build directories remain
  ignored under the task's run roots. Only compact locks/scripts/report are
  intended for the commit.

## Fix round 1: trust boundary and durable two-run proof

This section supersedes the earlier script-count, recipe-identity, direct
managed-input invocation, and test-count details above. It records the
post-review implementation and final settled bytes.

### RED evidence

- A direct streaming parser built without Jackson duplicate detection accepted
  a second `OPENGGF_PRODUCER` artifact key, and the capture-store vector leaked
  a `JsonParseException`: 2 expected failures. Explicit enum-map duplicate
  rejection and capture-store error normalization made both green; the two
  relevant Java classes then passed 51/51.
- The registered C# source-lock suite was expanded before implementation. It
  failed 2 of 7 cases because `secure-runtime.sh` and `build-recipe.json` did
  not exist. The production helper, canonical recipe, managed manifest, and
  pair gate made the always-on portion green.
- The first real pair attempt failed closed when the isolated Git HOME/XDG
  directories were accidentally inside the fetched repository and therefore
  appeared as untracked input. Moving that configuration stage beside the Git
  stage made the cleanliness gate meaningful and the next run green.

### Security and provenance result

All reproduction entry points now start with `/usr/bin/bash -p`, source one
production `secure-runtime.sh`, reject ambient shell, Java/Mono/.NET,
compiler/make, Git/SSH/config/transport, Bash-function, and loader overrides,
and terminate conventional command names at readonly absolute utility
wrappers. Network fetches use empty HOME/XDG configuration, no hooks or
prompts, an HTTPS-only protocol allowlist, and the three immutable object IDs.
The documented pre-process trust root is the kernel, ELF loader, libc, Bash,
and pinned absolute OS utilities.

The shared publisher performs one same-parent
`/usr/bin/mv -T --no-copy --no-clobber` of a complete sibling stage. Registered
tests execute that helper for success, racing-empty/no-clobber, cross-device
post-validation failure without a partial target, and a snapshot whose caller-owned source is
subsequently mutated. Fake `PATH`, `git`, `sha256sum`, and `cmp` executables are
not invoked; `BASH_ENV` and `GIT_CONFIG_GLOBAL` are rejected.

`build-recipe.json` is canonical tracked provenance for the normalized
commands/environment, all relevant non-circular versioned script/lock inputs,
and the host trust-root bytes. `toolchain-lock.json` pins its SHA-256:

```text
57ea87848e924904cc3463e6a8b59c80eea62e22fe19f1c0d2c82c7bce33260a
```

`secure-runtime.sh verify-recipe` recomputes every input and host file.
`verify-inputs.sh` incorporates the recipe into the verified input identity:

```text
36dde84c81429343b2f4425ff66c04f8fbdf54bcaf42a2459e68c52f95e9a0d4
```

Changing a versioned script without updating the recipe now fails the
registered copied-fixture test. Native and managed reproduction identity JSON
include the recomputed recipe digest.

`managed-nuget-manifest.json` contains all 114 lowercase package IDs,
versions, relative filenames, and individual SHA-256 values. Its tracked-file
digest is:

```text
b609fa7cf733755415b9b878e53ea25e72cc55dca92a645e9a788f3b8e19ce86
```

`prepare-managed-inputs.sh` is offline-only, snapshots and verifies the exact
SDK archive (`7786bbe...`) and the copied destination bytes for all 114
packages, then publishes a create-new immutable input tree. Package payloads
remain ignored. Managed reproduction consumes only that prepared tree and
still emits no DLL.

### Registered standard and real gates

The always-on C# invocation is explicit about the expensive gate:

```text
BIZHAWK_HOME=<stock-bizhawk> tools/bizhawk-headless/test.sh \
  --filter GpgxAudioObserverSourceLockTests --jobs 1
```

It produced 6 passes and one genuine `SKIP`, naming the five required
`OPENGGF_TASK6_*` inputs. With those variables supplied, the same registered
slow case invoked `reproduce-stock-pair.sh` and passed. It does not inspect
script strings as a substitute for reproduction.

The final settled-recipe real output is ignored at
`target/audio-parity/native/task6-fix-round-pair-e`. The durable pair script
performed two independent HTTPS fetches, two toolchain preparations, two
native builds, one immutable managed-input preparation, and two managed
builds. It compared A/B raw, compressed, and identity bytes, and compared both
raw and both compressed native outputs to stock:

```text
native runs:              2
gpgx.wbx SHA-256:         b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218
gpgx.wbx.zst SHA-256:     c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12
BuildID:                  7696adca7ad14b79
native identity SHA-256:  b8ecf7dbc6d866c98ac48c7d65b8081856ee3d4570548f8a77f264a0adc777f9
managed runs:             2
managed identity SHA-256: 71223803c8e2d3fd0bf22e878cb8cacbe0990c493186afc0ae61119f7f2ba95c
managed result:           BYTE_MISMATCH / REFLECTION
pair identity SHA-256:    65ab6bd7f8557a51771c2a2c2024634054851150c4f47158a6376abbc2604dff
```

The managed candidates remain the exact locked mismatch (`Cores`
`f7e7ea11...`, `Common` `96f494af...`), both stock comparisons are false,
`selected_adapter` is `REFLECTION`, and `patched_managed_dll_permitted` is
false.

Final Task 1-3 Java regressions ran:

```text
mvn -Dmse=off \
  -Dtest='com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace,com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore,com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator' \
  test
```

Result: 103 tests, 0 failures, 0 errors, 0 skipped. All eight shell scripts
pass `bash -n`, all five JSON locks/manifests/recipes parse with `jq -e`, the
recipe verifier returns the settled digest above, and `git diff --check` is
clean.
