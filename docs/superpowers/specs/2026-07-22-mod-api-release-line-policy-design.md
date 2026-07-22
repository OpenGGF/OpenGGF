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
2. The `develop` candidate is exactly one release line ahead of `master`.
3. The `next` candidate is exactly two release lines ahead of `master`.
4. A branch has at most one mutable candidate pin.
5. Internal changes regenerate the existing candidate pin without changing
   `ModApiVersion.CURRENT` and without adding compatibility shims.
6. Published signature pins are retained permanently and later current surfaces
   are checked for compatibility against every published baseline.
7. Promotion advances branch policy once. Repeated implementation changes on a
   branch do not advance it again.
8. The API version follows the target release line recorded by branch policy,
   not an incidental prerelease string in build metadata.

For the current topology, `next` therefore uses `0.7.0` for its entire internal
development cycle. Adding or removing creator APIs on `next` updates the
`mod-api-signatures-0.7.txt` candidate pin in place. It does not create 0.7.1,
0.8, or a bridge for an earlier internal 0.7 shape.

## Maintenance releases

Maintenance product releases do not automatically change the Mod API.

- An implementation defect that leaves the published API contract intact is
  fixed without an API version change.
- A backward-compatible correction to the published surface may use the API
  patch component, for example `0.5.0` to `0.5.1`. The earlier published pin is
  retained and remains supported.
- A correction that would break compiled mods cannot silently replace a
  published baseline. The maintenance release must retain a compatibility
  bridge or defer the breaking correction to a later release line.
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
`published`, `currentApi` must also appear in `publishedBaselines`.

The allowed signature-pin set is the union of every published baseline and the
current API. Candidate filenames contain the mutable major/minor line, matching
the existing `mod-api-signatures-0.7.txt` convention. Published filenames contain
the immutable full version, for example `mod-api-signatures-0.5.0.txt` and
`mod-api-signatures-0.5.1.txt`, so maintenance baselines cannot collide.

Publishing a candidate renames its pin from the line form to the full-version
form as part of release preparation. Ordinary internal changes never rename the
candidate pin. The next downstream candidate receives its own new line-form pin.

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
5. The set of pin files equals published baselines plus the current API.
6. Every published baseline remains binary/source-surface compatible with the
   current API under the existing signature rules.
7. Published baselines cannot be labeled as mutable candidates.
8. The three configured lines are the current successive release topology.
9. A supplied CI destination branch agrees with `targetBranch` for long-lived
   branch pushes and pull requests.

CI must supply the destination branch explicitly from its push branch or pull
request base ref. This avoids depending on detached-checkout Git heuristics.
Feature branches without that CI value validate their inherited descriptor and
all non-branch invariants.

Extend commit/merge policy validation so a staged change to
`ModApiVersion.CURRENT` requires the policy descriptor in the same change. The
CI destination check is the authoritative promotion gate: a PR into `develop`
or `master` fails until its descriptor is recalculated for that destination.

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

## Testing

Tests will cover:

- the current 0.5/0.6/0.7 topology;
- repeated internal API edits with an unchanged 0.7.0 candidate;
- rejection of 0.7.1 or 0.8.0 as a second `next` candidate bump;
- exact candidate-pin replacement;
- preservation and compatibility of published pins;
- a compatible master maintenance API patch;
- rejection of a breaking maintenance patch without a bridge;
- downstream 0.6.0/0.7.0 candidates remaining unchanged after master 0.5.1;
- promotion destination mismatch and corrected promotion metadata;
- missing, duplicate, malformed, or contradictory descriptor entries; and
- the staged-version-change coupling rule.

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
