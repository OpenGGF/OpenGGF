# Mod API 0.7 Baseline Reset Design

**Date:** 2026-07-22

**Status:** Approved

## Context

OpenGGF's creator API has not been released, but development currently describes a
provisional compatibility lineage from 1.1.0 through 2.5.0. That lineage has caused
the repository to preserve signature snapshots, compatibility-only overloads, sample
version ranges, and migration prose for contracts that no external release has made.

The creator API will instead begin with one clean pre-1.0 baseline: Mod API 0.7.
Machine-readable versions use the canonical semantic version `0.7.0`; prose may call
the release "Mod API 0.7".

## Goals

- Publish the current intended creator surface as the sole `0.7.0` contract.
- Remove compatibility promises and implementation baggage for provisional 1.x and
  2.x API shapes.
- Give active documentation, tooling, fixtures, and sample mods one consistent
  version and compatibility range.
- Preserve the existing manifest format version as a separate concern.
- Leave unrelated engine behavior unchanged.

## Non-goals

- Rework the contents of the creator API beyond removing members that exist solely
  as compatibility shims for provisional baselines.
- Change `.ggfmod`, level, sheet, or manifest format versions.
- Rewrite dated design and implementation records that are retained only as
  historical evidence. Active documents must not depend on their obsolete version
  claims.
- Promise compatibility with any provisional 1.x or 2.x mod binary.

## Version Contract

`ModApiVersion.CURRENT` becomes `0.7.0`. Code-bearing maintained samples and generated
templates declare `engineApiRange: ">=0.7.0 <0.8.0"`. The upper bound reflects
pre-1.0 semantic-versioning practice: a later minor version may deliberately change
the API.

The manifest `formatVersion` remains `1`. Documentation must explicitly keep this
wire-format version separate from the engine Mod API version.

## Signature Baseline and Compatibility Code

The repository will contain one authoritative signature pin:
`src/test/resources/mods/mod-api-signatures-0.7.txt`. It is generated from the final
recursive `@ModApi` surface using the existing deterministic snapshot tool.

The provisional `mod-api-signatures-1.1.txt` through
`mod-api-signatures-2.5.txt` files will be deleted. Signature tests will compare the
live surface exactly with the 0.7 pin and retain the annotation, recursive closure,
sorting, and SDK inventory guards. Tests that compare the provisional lineage or
classify its breaking/additive transitions will be removed.

Constructors, overloads, aliases, or comments whose only purpose is binary or source
compatibility with a provisional 1.x/2.x baseline will be removed. A member that is
useful in the intended 0.7 API independently of that history remains, with neutral
0.7 documentation. Removal decisions must be justified by a direct compatibility
marker or by comparison with the current canonical shape; this reset is not a broad
API redesign.

## Documentation and Samples

Active sources of truth will describe one 0.7 surface and no published predecessor:

- `AGENTS.md`, `CLAUDE.md`, `README.md`, and current changelog material;
- `docs/architecture/mod-api-compatibility.md`;
- the active modding handbook, guides, quickstarts, format references, and backlog;
- SDK templates and maintained sample manifests/readmes;
- production and test code comments, assertions, test names, and diagnostic text.

Prior changelog entries whose only purpose is narrating provisional API bumps will be
consolidated into one Mod API 0.7 entry describing the accumulated creator surface.
Dated plans and specs may retain their original development narrative, but active
guidance must not point to those documents as current version policy.

## Validation

Implementation is complete when:

1. `ModApiVersion.CURRENT` is exactly `0.7.0`.
2. The live recursive surface exactly matches `mod-api-signatures-0.7.txt`.
3. No provisional signature baseline remains.
4. Focused semantic-version, manifest-range, signature-surface, SDK packaging,
   validator, and maintained sample integration tests pass.
5. A repository scan finds no active 1.x/2.x Mod API claims, version-gated samples,
   compatibility comments, diagnostics, or test names. Dated historical plans/specs
   are excluded from this active-source scan.
6. The broader relevant Maven suite passes, or any pre-existing unrelated failures
   are reported with evidence.

## Delivery

The work is performed on `feature/ai-mod-api-0-7-reset`. Existing unrelated working
tree changes are not staged or modified. The final implementation commit follows the
repository trailer policy and updates the changelog and agent documentation because
the creator-facing engine contract changes.
