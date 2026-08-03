# Trace Version Consolidation

## Status

Proposed for the pre-release `develop` trace fleet. This design replaces every
earlier trace generation, including the fixtures present on `master`. Old trace
files are not a supported interchange format or user-facing compatibility
surface.

## Problem

`master` contains eleven trace metadata files. Its production trace contract is
identified by `csv_version: 4`; it has no `trace_schema`, hardware-timing
schema, run-manifest schema, S2 fleet, or S3K fleet. The only two commits on
`origin/master` after its common ancestor with `develop` do not touch tracing.

During development, independent recorder campaigns incremented several version
axes:

- native recorder stamps range across S1 `3.x`, S2 `9.x`, and S3K `6.x`;
- level trace schemas range through 3, 5, 6, 7, 9, and 10;
- the current 42-column level CSV is called version 7;
- S3K hardware timing has schemas 1 and 2;
- special-stage rows carry a separate schema counter that the Java metadata
  loader does not consume;
- run manifests have schemas 1 and 2; and
- the native-prelude bootstrap decision parses the S2 recorder version.

Most of those generations were never released. Supporting them makes
`develop` backward-compatible with its own discarded intermediate states and
forces recorder differential tests to enumerate historical literals that no
current fixture uses.

## Decision

There is exactly one supported generation: **develop v5**. Version 5 is one
format generation above master's `csv_version: 4` contract, but v4 is a
numbering baseline rather than a compatibility target. No old trace is required
to load.

Every useful route is regenerated into v5. The eight S1 credits-demo fixtures,
their concrete replay classes, and focused fixture consumers are retained. A
first-class native credits-demo mode in the BizHawk headless harness ports the
ROM ending-demo selection and lifecycle from
`tools/retro/s1_credits_trace_recorder.py`. The stable-retro script is reference
behavior only; it is not part of the resulting capture pipeline. The native
mode records the ROM's own controller stream and does not invent a BK2
dependency. Credits replay continues to source that same ROM demo data through
`DemoInputPlayer`.

The noncanonical `metadata_retro.json`, `physics_retro.csv`, and
`aux_state_retro.jsonl` alternate sidecars are deleted after their evidence is
accounted for. No canonical credits fixture directory is deleted.

## Canonical develop contract

### Metadata envelope

Every current fixture, including special-stage segments, declares:

```json
"recorder": "native-bizhawk-headless",
"recorder_version": "3.0",
"trace_schema": 5
```

`3.0` is one recorder major above the highest ordinary recorder version on
master (`2.2`). Game and mode remain explicit in existing fields such as
`game`, `trace_profile`, `trace_type`, and `run_id`; they are not encoded in the
version string.

Current fixtures do not emit `lua_script_version`, and the Java loader does not
parse it. Neither parser behavior nor replay behavior may branch on
`recorder_version`.

### Level physics rows

For `trace_schema: 5`, the level `physics.csv` contract is the current symmetric
42-column player/sidekick row formerly called CSV v7. Current metadata does not
emit a separate `csv_version`; the trace schema identifies the row shape.

All 11-, 18-, 19-, 20-, 22-, 37-, and 38-column compatibility readers and tests
are removed. Synthetic fixtures are rewritten to v5 or deleted. The ordinary
level parser accepts exactly the 42-column v5 row.

`TraceMetadata` no longer models `luaScriptVersion` or `csvVersion`.
`TraceData` performs strict profile-aware dispatch instead of passing a version
fallback to `TraceFrame`: ordinary level profiles require one 42-column v5 row,
while special-stage profiles use their dedicated game-owned readers.
Animation and subpixel availability are inherent in the v5 level contract;
their predicates no longer inspect a removed CSV version.

### Special-stage rows

Special stages use their dedicated, game-owned row readers. They also carry
`trace_schema: 5`. The game and `trace_profile` select one fixed current row
shape: 14 columns for S1, 48 for S2, and 20 for S3K. `ss_csv_version` is removed;
ordinary level parsing never interprets a special-stage row.

### Hardware timing

V5 has one hardware-timing grammar: the current complete authority registry
formerly called timing schema 2. It supports both `kos_module_queue` and
`kos_decompression_queue`, with the existing kind, ordinal, stable submission
fingerprint, and service-boundary checks.

`hardware_timing_schema` is removed. The presence of
`hardware_timing.jsonl` in a v5 trace opts that fixture into the timing port;
absence means no recorded timing input. The former module-only registry is
removed. This removes a redundant develop-only version axis without broadening
the hardware-timing authority described by hard rule 4.

### Run manifests

Run manifests declare `trace_schema: 5`, not `run_schema`. V5 includes the
current `dynamic_art_gap_transitions` structure formerly called run schema 2.
All manifests emit the array, including an empty array when there are no gaps.
The permissive schema-1 path is removed.

Their provenance envelope matches trace metadata:

```json
"recorder": "native-bizhawk-headless",
"recorder_version": "3.0",
"trace_schema": 5
```

They do not emit `lua_script_version`. `TraceRunManifest` removes that property
and all schema-1/schema-2 branches.

### Capabilities and bootstrap

Optional diagnostic data remains opt-in through `aux_schema_extras`. Capability
names describe a semantic feature, not a migration generation. The
develop-only `dynamic_art_transfer_state_per_frame_v1` name becomes
`dynamic_art_transfer_state_per_frame`.

The S2 `lua_script_version >= 9.2-s2` bootstrap gate is replaced by an explicit
`native_prelude_bootstrap` capability. The recorder advertises it only when the
frame-0 snapshot evidence required by `compareBootstrapFrame0` is present.
Fixtures without the capability are ineligible; no recorder-version inference
exists.

## Recorder and fixture migration

All native writers move to the shared v5 metadata contract before capture.
The Lua recorders remain non-authoritative but their emitted metadata and
manifest envelopes also move to v5, using
`recorder: lua-bizhawk-diagnostic` and `recorder_version: 3.0`. Their internal
source-history comments may retain historical names. This keeps hook-driven
scratch output loadable by the strict v5 tools without creating a compatibility
parser or allowing Lua output to become canonical.

Each production capture family is regenerated with the reviewed native writer.
Publication follows the existing exact-byte contract:

1. capture to scratch;
2. freeze segment inventory, lengths, row/event counts, and SHA-256 values;
3. compare decompressed physics, aux, timing, and manifests with the predecessor;
4. classify every byte delta;
5. install the native output byte-for-byte; and
6. run the recorder gates, Java fixture guards, and replay frontier sweep.

Metadata and manifest envelope changes, manifest gap-array additions, and the
removal of redundant version fields are expected one-time migration deltas.
They are frozen literally in the candidate report and require exact-byte user
approval just like payload changes; this design does not create a normalization
exception. Physics and auxiliary payloads must otherwise remain byte-identical
unless the recorder correction already approved for that family explains a
delta. Timing must remain identical to the former schema-2 semantics; its
authority and events do not change.

The native S1 credits mode captures all eight ROM ending-demo routes with the
canonical 42-column v5 level writer and current S1 auxiliary-event engine. It
preserves the existing `trace_type: credits_demo`,
`input_source: rom_ending_demo`, demo index, and demo slug semantics. For each
route, the candidate report compares every column shared with the predecessor's
20-column evidence, field by field. New v5 columns and auxiliary-event changes
are reported and classified separately. All eight candidates remain subject to
the same inventory, hash, exact-byte approval, and replay gates as every other
fixture family. The old fixture remains installed until its native replacement
has passed those gates and is approved.

Legacy alternate sidecar deletion is recorded separately from regenerated
candidate output so deletion cannot be mistaken for a capture delta.

## Guards

A committed-fleet guard enforces:

- `trace_schema: 5` on every fixture;
- no `lua_script_version` on current fixtures;
- `recorder: native-bizhawk-headless` and `recorder_version: 3.0` on current
  fixtures;
- no `csv_version`, `ss_csv_version`, `hardware_timing_schema`, or `run_schema`;
- the fixed v5 row width selected by the fixture's game and profile;
- the current full timing grammar for every `hardware_timing.jsonl`; and
- required dynamic-art gap evidence in every run manifest; and
- no historical S1/S2/S3K recorder-stamp compatibility literals in native
  differential gates.

`TestHardwareTimingAuthorityGuard` follows the unversioned
`dynamic_art_transfer_state_per_frame` capability so the rename cannot weaken
its authority scan.

There is no legacy path allowlist.

## Testing strategy

Implementation is test-first:

- writer tests first require the v5 metadata keys and reject historical keys;
- parser tests first prove the current v5 shape and reject every old row width;
- timing tests first prove v5 authorizes both S3K work kinds without a second
  schema selector;
- run-manifest tests first prove v5 requires gap transitions;
- bootstrap tests first prove capability-based eligibility without recorder
  version parsing;
- credits-recorder tests first prove all eight ROM ending-demo identities,
  lifecycle boundaries, ROM-owned input capture, and canonical v5 output;
- the fleet guard first fails on all intermediate develop metadata; and
- differential and publication tests compare current output directly rather
  than normalizing historical version literals.

After fixture publication, the native recorder suite, focused Java contract
tests, all `*TraceReplay` tests, and the full Maven suite run on JDK 21.

## Documentation and policy migration

The version consolidation updates `AGENTS.md` and `CLAUDE.md` together. Hard
rule 4 describes the one v5 timing grammar rather than the removed timing
schema-1/schema-2 split. The cross-game hardware-timing architecture contract
gets an explicit supersession section; maintained recorder behavior,
publication, trace-guide, and README documentation describes v5 only.

Both mirrored trace-replay skill trees are updated together because their
current workflow text treats S3K timing schemas 1 and 2 as live choices.
Historical audits, validation reports, and old frontier entries remain
historical evidence and are not rewritten to pretend their captures used v5.

## Alternatives rejected

### Keep all fields and merely renumber them

Resetting schema 7 to 5 and timing schema 2 to 1 without removing old branches
would make the labels smaller while retaining the maintenance debt.

### Retain independent schema counters reset to 1

This still leaves four version axes whose only supported combination is the
current one. Because traces and manifests are repository-owned evidence rather
than public interchange formats, lockstep migration is preferable to permanent
cross-product validation. The single `trace_schema` covers the complete trace
evidence contract.

### Preserve old committed fixtures unchanged

That makes both released test data and unshipped intermediate outputs permanent
compatibility obligations even though traces are repository-owned test
evidence, not a public interchange format. The native recorder and exact-byte
publication gates make a one-time migration safer and cheaper than maintaining
the branches indefinitely.
