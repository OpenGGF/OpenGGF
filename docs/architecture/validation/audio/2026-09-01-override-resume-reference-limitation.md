# Override-resume reference authority limitation

**Date:** 2026-09-01
**Task:** sound-driver roadmap Task 8
**Terminal status:** `REFERENCE_LIMITATION`
**Limitation code:** `FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE`

## Decision

Task 8 stopped before fixture publication and before any Java production change. The two
TraceChaser producer milestones (`912fef0a` and `e3fdf73`) establish observation and
no-replace staging mechanics, but they do not confer reference authority. No override-resume
fixture was created or replaced, and no write or PCM expectation was inferred from a trace,
snapshot, or existing fixture.

The hard gate cannot be closed in the current environment:

1. The required current-session two-build observer reproduction gate has no configured
   `OPENGGF_GPGX_OBSERVER_SOURCE`, `_TOOLCHAIN_A`, `_TOOLCHAIN_B`, or `_STOCK` inputs and
   reports a skip rather than authority.
2. Even after removing the unrelated ambient `GIT_PAGER`, the locked recipe verifier fails
   closed with `secure-runtime: trust-root executable differs: /usr/bin/ar`. The recipe pins
   `/usr/bin/ar` to
   `69c93ee96fe89de9a071010905786a48c136fbabcdafff2fbd5bc4f2d7866f84`; the current host
   provides `0c1aceed56dde02eeed19228a4ba712f5e5d571bc7efb84f94960dd191b66656`.
3. The reviewed-capability guard also rejects the current source/capability pair. It hashes
   `CompleteRunAudioObserver.cs` as
   `d2ec919c783f3c3dc2a8c11ba2c167fd9975ca11dec9d78b10c8a3a4efb0ec72`, while the pinned
   capability records
   `e0be4819556cf74b82273cba7b748ddd13529f2dcc61029a302f4b0f8acfda89`. Task 8 permits
   refreshing only `task8_harness_executable_sha256`; changing this source field would break
   the pinned normalized capability-template identity and is not an authorised repair.
4. The stock BizHawk 2.11 tree fails installation authentication because
   `gpgx-audio-observer-source/identity.json` is absent. A pre-existing 2026-08-30 observer
   install passes the static identity test, but it was not freshly reproduced twice for this
   task and therefore remains assertion-only. The older 2026-08-10 install is quarantined and
   has a different identity hash.

The milestone's provenance inventory is also intentionally not represented as complete.
The draft attestation/metadata schemas do not yet carry all mandatory Task 8 fields: ROM
CRC-32; canonical BK2 relative path; TraceChaser gitlink; ProbeRuntime, probe, Lua contract,
producer, and service-manifest paths and hashes; BizHawk/core/adapter/Waterbox hashes;
capability-template and installed observer evidence; disassembly commits/routine citations;
the duplicated boundary/write/PCM inventory; normalization inventory; and exact capture,
validation, and publication commands. Consequently the closed publisher was tested only with
synthetic inputs and was not invoked against a production capture.

## Evidence

- S1 capture producer: 57 passed, 0 failed, 2 opt-in live-test skips.
- S2 capture producer and sink: 14 passed, 0 failed.
- Extractor/publisher: 6 passed, 0 failed.
- Closed CLI/launcher checks: 4 passed, 0 failed.
- Lua 5.4 contract suite: 10 passed, 0 failed.
- Two-copy deterministic build: executable SHA-256
  `0ba71e5c4f3dee9360b13c0c312a1dc21a61a5b4760ad3d15e02a9472179c8d3`;
  stale executable capability rejected, refreshed copy accepted;
  `DETERMINISTIC_BIZHAWK_HEADLESS_BUILD_OK`.
- Final host-visible process scan: zero task-owned `mono` or `EmuHawk` processes.

## Required continuation

A later task must first restore the exact locked observer build trust roots and supply two
fresh-copy reproduction inputs, reconcile the capability source identity without weakening the
pinned-template rule, and complete the mandatory provenance schemas/validator. Only then may it
run the two serial S1 captures, two serial S2 native captures, two ProbeRuntime diagnostics,
duplicate equality checks, and the first four-file no-replace publication. Java transition or
resume behaviour remains hard-blocked until all of those steps succeed.

## Independent mechanics review — 2026-09-02

TraceChaser commit `fa69e177328f35f55330637ddfc5c7a24dde839f` corrects three
independent review findings while preserving this limitation:

1. The extractor now enforces every required member, rejects every unknown member, and validates
   the complete types, ranges, identities, and relationships for raw metadata, the selected S1/S2
   boundary, PCM, each selected write, and both emitted closed contracts.
2. Attestations now have one fixed-order compact UTF-8 serialization with no BOM and exactly one
   LF. Duplicate comparison clones the authenticated bytes and replaces only the 20-byte UTC
   timestamp value; whitespace, member order, newline count, malformed/multiple records, and all
   other byte differences are rejected.
3. The publisher rejects symlinked `fixtureRoot/s1` and `fixtureRoot/s2` intermediates. Multi-file
   rollback keeps an `O_NOFOLLOW` directory handle and staged device/inode/type authority, moves a
   public final to a private no-replace quarantine, and unlinks only when that identity proves the
   entry is publisher-owned. Unsupported native operations fail before the first link.

The executable review evidence was 15/15 extractor tests and 21/21 publisher-filter tests. The
policy tests were 31/31, with exact registration of only the three planned small JSON schemas and
continued rejection of an adjacent unlisted `contracts/audio/*.json`; both repository and reachable
history scanners pass. All authorised test-run process inventories were empty before and after, and
no BizHawk, EmuHawk, capture, fixture, or Java work occurred. These stronger mechanics do not supply
the missing fresh authenticated native-GPGX observation or provenance, so the terminal status and
required continuation above are unchanged.
