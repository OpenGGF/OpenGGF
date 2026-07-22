# Mod API release-line policy design

**Date:** 2026-07-22
**Status:** Approved design
**Scope:** Mod API version ownership, published compatibility baselines, internal
candidate revisions, branch promotion, maintenance releases, agent guidance,
and repository enforcement.

## Problem

OpenGGF's product branches already represent successive release lines, but the
Mod API previously acquired several version numbers while it was still being
developed internally. That created compatibility shims and historical signature
pins for API shapes that no official release had published.

The Mod API must follow the product release-line model:

| Branch | Release line in the current topology | Mod API line |
|---|---:|---:|
| `master` | 0.5 | 0.5 |
| `develop` | 0.6 | 0.6 |
| `next` | 0.7 | 0.7 |

The branch's release-line identity is authoritative even when `pom.xml` still
contains an older prerelease artifact string. API work on `develop` and `next`
may change repeatedly, but those revisions are internal candidates rather than
published contracts. They must not consume new API versions or require
compatibility layers between revisions.

Compatibility begins only when an API baseline ships from `master`.
No Mod API baseline has shipped yet. In particular, 0.7 is the mutable candidate
for `next`, not the first published Mod API. Existing code, tests, or maintained
documentation that call 0.7 "published" must be corrected as part of this work.

## Terminology

### Release line

The product's major/minor release identity, such as 0.5, 0.6, or 0.7. Product
maintenance releases such as 0.5.1 remain on the 0.5 release line.

### Candidate baseline

The one mutable Mod API baseline for a branch's target release line. Its patch
component is normally zero, such as `0.7.0`. Internal API changes replace this
pin in place. Earlier candidate contents have no compatibility promise.

### Published baseline

A Mod API version released from `master`. Its signature pin is immutable and
must remain supported by later API surfaces. Every published baseline is kept,
including a maintenance API patch such as `0.5.1`.

### Compatibility shim

A constructor, method, field, alias, adapter, or other surface retained only so
code compiled against an older API shape continues to work. Shims are justified
only for published baselines, never for superseded internal candidate shapes.

## Policy

1. The normal API candidate on `master` matches the master release line.
2. `masterLine`, `developLine`, and `nextLine` record the ordered product
   release-line topology, with `develop` the successor of `master` and `next`
   the successor of `develop`.
3. A branch has at most one mutable candidate pin.
4. Internal changes regenerate the existing candidate pin without changing
   `ModApiVersion.CURRENT` and without adding compatibility shims.
5. Published signature pins are retained permanently and later current surfaces
   are checked for compatibility against every published baseline.
6. Promotion advances branch policy once. Repeated implementation changes on a
   branch do not advance it again.
7. The API version follows the target release line recorded by branch policy,
   not an incidental prerelease string in build metadata.
8. Runtime compatibility is evaluated against the engine's supported-contract
   set: the current candidate plus every published baseline retained by that
   engine. A mod is eligible when its declared `engineApiRange` contains at
   least one member of that set. Checking only `ModApiVersion.CURRENT` is not
   sufficient after the first publication.

For the current topology, `next` therefore uses `0.7.0` for its entire internal
development cycle. Adding or removing creator APIs on `next` updates the
`mod-api-signatures-0.7.txt` candidate pin in place. It does not create 0.7.1,
0.8, or a bridge for an earlier internal 0.7 shape.

## Maintenance releases

Maintenance product releases do not automatically change the Mod API.

- An implementation defect that leaves the published API contract intact is
  fixed without an API version change.
- A maintenance API patch, for example `0.5.0` to `0.5.1`, must have the same
  checked signature surface as the earlier published baseline. It may correct
  implementation behavior or non-contract metadata, but it may not add, remove,
  or change a checked signature. An additive surface change waits for the next
  release-line candidate.
- A correction that would require adding or changing an API member, including a
  new compatibility bridge, is not a maintenance API patch under this policy.
  Keep the published surface intact for the maintenance release and defer the
  contract change to the next release-line candidate.
- A maintenance patch on `master` does not propagate its patch component to the
  normal downstream candidates. With master API 0.5.1, `develop` remains 0.6.0
  and `next` remains 0.7.0.

This maintenance exception is the only reason the same release line may contain
more than one published Mod API version.

## Branch-policy descriptor

Add a root-level `mod-api-release-policy.properties` file as the machine-readable
authority for branch topology and API state. The initial descriptor represents:

```properties
schemaVersion=1
targetBranch=next
masterLine=0.5
developLine=0.6
nextLine=0.7
currentApi=0.7.0
currentStatus=candidate
publishedBaselines=
```

`currentStatus` is `candidate` while the current pin is mutable and `published`
once that exact baseline has shipped from `master`. When the status is
`published`, `currentApi` must also appear in `publishedBaselines`. The empty
list above is intentional: OpenGGF has not yet published any Mod API.

A published baseline may predate all three lines in the current topology; it is
not required to equal `masterLine`, `developLine`, or `nextLine`. It must not be
later than `currentApi`. Compare release lines lexicographically by major/minor.
Published baselines on an older line use forward compatibility. If more than one
published patch exists on the current API's line, every such pin must have the
same checked surface as the current API and therefore as each other.

The expected signature-pin map is normalized as follows:

- each entry in `publishedBaselines` maps to exactly one immutable full-version
  filename, such as `mod-api-signatures-0.5.0.txt`;
- when `currentStatus=candidate`, `currentApi` maps to exactly one mutable line
  filename, such as `mod-api-signatures-0.7.txt`;
- when `currentStatus=published`, `currentApi` contributes no candidate filename;
  its entry in `publishedBaselines` supplies the one full-version filename; and
- no other Mod API signature-pin files are allowed.

Publishing a candidate renames its pin from the line form to the full-version
form as part of release preparation. Ordinary internal changes never rename the
candidate pin. The next downstream candidate receives its own new line-form pin.
This is a rename, not two pins for the same surface.

The descriptor is the machine-readable authority for version and publication
state. `docs/architecture/mod-api-compatibility.md` remains the explanatory
compatibility contract and must reflect, rather than independently define, the
descriptor.

## Promotion workflow

Normal API development on `develop` or `next` is:

1. Change the API surface.
2. Regenerate the existing candidate pin in place.
3. Update current documentation, samples, and manifests as needed.
4. Do not change the candidate version.
5. Do not preserve the superseded internal shape with compatibility code.

Branch promotion is a deliberate version-policy operation:

1. Freeze the baseline that is being released from `master` and list it as
   published.
2. Recalculate all three release-line values for the destination topology.
3. Set `targetBranch` for the destination branch.
4. Set the destination branch's one current API candidate.
5. Create or rename only the candidate pin required by the new target line.
6. Preserve every published pin.
7. Update manifest ranges, examples, version prose, and changelog entries.
8. Run compatibility checks against all published baselines and exact-surface
   checks against the new candidate.

For example, after `develop` 0.6 is released to `master` and `next` 0.7 advances
to `develop`, the topology becomes:

```text
master 0.6 | develop 0.7 | next 0.8
```

The policy descriptor must be recalculated for the destination branch as part
of merge preparation. A conflict in this file must never be resolved by blindly
choosing either side.

The topology is ordered product policy, not arithmetic on decimal-looking
numbers. Promotion explicitly supplies the three distinct, increasing lines;
the validator must not calculate a successor by incrementing a minor component.
This permits transitions such as 0.9 to 1.0 and deliberate skipped product lines.

For a release transition, the expected pin operations are explicit:

| Destination state | Published pins | Candidate pin |
|---|---|---|
| Current `next` before any API release | none | `mod-api-signatures-0.7.txt` |
| A candidate published from `master` | renamed full-version pin for the released API, plus all older published pins | none while `currentStatus=published` |
| Downstream branch prepared for its next line | all full-version published pins | one line-form pin for that branch's `currentApi` |

Each long-lived branch carries a descriptor for its own target. Consequently,
after a coordinated promotion, the `master`, `develop`, and `next` descriptors
may share the topology values while differing in `targetBranch`, `currentApi`,
`currentStatus`, and candidate pin. Promotion instructions must list the exact
before/after descriptor and filenames for every affected destination rather
than implying that either side of a merge conflict is authoritative.

## Agent directives

`AGENTS.md` and `CLAUDE.md` will contain a concise mandatory rule that names
`mod-api-release-policy.properties` and requires agents to update it whenever:

- `ModApiVersion.CURRENT` changes;
- a release line advances;
- an API maintenance patch is prepared;
- a merge between `next`, `develop`, and `master` is prepared; or
- a candidate becomes a published baseline.

The directive will require agents to verify all of the following in the same
change:

- destination `targetBranch`;
- master, develop, and next release lines;
- current API and candidate/published status;
- published-baseline list and signature pins;
- manifest ranges, samples, documentation, and changelog references.

Feature branches inherit the policy of their intended integration branch.
Ordinary API edits on a feature branch regenerate the inherited candidate pin
without editing policy metadata. A version change or promotion-preparation
change must update the policy descriptor in the same commit.

## Repository enforcement

Add a focused policy parser and validator under test code, with a JUnit 5 guard
covering these invariants:

1. The descriptor is complete, uniquely keyed, and semantically valid.
2. The current API major/minor matches the descriptor's target release line.
3. Normal `develop` and `next` candidates have patch zero.
4. The current surface exactly matches the current candidate pin.
5. The set and names of pin files exactly match the normalized published/current
   mapping defined above, including deduplication when current is published.
6. Every published baseline remains compatible with the current API under the
   repository's checked signature-surface rules. Removals or changes fail.
   Additions are allowed whenever the current API belongs to a later configured
   release line than the baseline, including across a major boundary such as
   0.9 to 1.0. Candidate exactness and same-line maintenance-patch equality are
   separate checks and never use this forward-compatibility allowance.
7. Published baselines cannot be labeled as mutable candidates.
8. The three configured lines are distinct and strictly ordered according to
   the explicitly configured product topology; no numeric successor arithmetic
   is inferred.
9. A supplied CI destination branch agrees with `targetBranch` for long-lived
   branch pushes and pull requests.
10. Runtime and SDK manifest validation use the supported-contract set, not
    `ModApiVersion.CURRENT` alone.

CI must supply the destination branch explicitly from its push branch or pull
request base ref. Workflows must cover pushes to and pull requests targeting
`next`, `develop`, and `master`; release-only coverage of `master` and PR-only
coverage of `develop` is insufficient. A manual workflow with no destination
must run non-branch invariants and require an explicit destination input before
claiming promotion validation. This avoids depending on detached-checkout Git heuristics.
Feature branches without that CI value validate their inherited descriptor and
all non-branch invariants.

Extend the shell and PowerShell commit/merge policy validators with symmetric
changed-path coupling according to this matrix:

| Change | Required companion change |
|---|---|
| `ModApiVersion.CURRENT` | descriptor plus the normalized candidate/publication pin operation |
| detectable `@ModApi` surface delta | current candidate pin content |
| candidate pin content-only update | detectable API surface delta; descriptor unchanged |
| candidate/full pin add, delete, or rename | descriptor publication/promotion metadata |
| descriptor-only topology, destination, or status edit | only the pin operation implied by the resulting normalized map; no unconditional pin content rewrite |

Merge commits remain governed by the existing merge policy plus the final-tree
CI guard. The CI destination check is the authoritative promotion gate: a PR
into `next`, `develop`, or `master` fails until its descriptor is correct for
that destination.

Existing provisional-shim marker guards remain in force. Their purpose is
broadened explicitly: lineage comments or compatibility members for unpublished
candidate revisions are defects, not harmless historical documentation.

## Failure messages

Policy failures must explain the required action. Representative messages are:

- `next targets release line 0.7; ModApiVersion.CURRENT must remain 0.7.0`
- `internal candidate changes must regenerate mod-api-signatures-0.7.txt`
- `published baseline 0.6.0 is missing or was reclassified as a candidate`
- `current API breaks published baseline 0.6.0`
- `policy target next does not match pull-request destination develop; recalculate mod-api-release-policy.properties during promotion preparation`
- `ModApiVersion.CURRENT changed without mod-api-release-policy.properties`
- `manifest compatibility must be checked against current and published supported contracts`

## Testing

Tests will cover:

- the current 0.5/0.6/0.7 topology;
- repeated internal API edits with an unchanged 0.7.0 candidate;
- rejection of 0.7.1 or 0.8.0 as a second `next` candidate bump;
- exact candidate-pin replacement;
- preservation and compatibility of published pins;
- a signature-identical master maintenance API patch;
- rejection of additive or breaking signature changes in a maintenance patch;
- downstream 0.6.0/0.7.0 candidates remaining unchanged after master 0.5.1;
- promotion destination mismatch and corrected promotion metadata;
- missing, duplicate, malformed, or contradictory descriptor entries; and
- bidirectional changed-path coupling for version, descriptor, pins, and API
  surface changes;
- a 0.7-range mod accepted by a later engine that retains published 0.7.0;
- a manifest rejected when its range contains no supported contract;
- push and pull-request destination checks for all three long-lived branches;
  and
- a 0.9 to 1.0 topology transition without minor-version arithmetic.
- additive forward compatibility across a 0.9 to 1.0 transition, while still
  rejecting removals and same-line maintenance additions.

Focused tests will extend the existing Mod API signature, documentation, SDK,
and provisional-shim suites. The full repository suite retains its separately
accepted unfinished-zone baseline; this policy work must not introduce new
failures outside that baseline.

## Non-goals

- Deriving packaged API versions dynamically from the current Git branch.
- Creating compatibility guarantees for internal candidate revisions.
- Automatically performing branch promotions or releases.
- Changing the product's release numbering scheme.
- Treating `pom.xml` prerelease text as the Mod API version authority.
- Claiming full Java binary or source compatibility beyond the checked signature
  model. Stronger claims require a dedicated compatibility tool and compiled
  fixtures for each published baseline.
