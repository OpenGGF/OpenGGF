# Architecture documentation

This directory owns OpenGGF's architectural reference material and dated
engineering artifacts.

## Current architecture

The Markdown files directly in this directory describe the current engine and
its architectural policies.

## Dated artifacts

- [Designs](designs/) contain approved designs, specifications, and
  architectural decisions.
- [Plans](plans/) contain implementation plans, delivery plans, work ledgers,
  and execution diaries.
- [Research](research/) contains investigations and supporting research that
  has not become a design.
- [Audits](audits/) contain point-in-time assessments, inventories, reviews,
  and gap analyses.
- [Validation](validation/) contains validation reports, baselines,
  checklists, and recorded results.

These project paths override the default output paths prescribed by agent
skills. Do not create tool-named document directories such as
`docs/superpowers`, and do not recreate the legacy top-level `docs/plans`
directory.

Domain subfolders may group related artifacts within a category, but a topic
must not replace classification by purpose.

## Mod and challenge blueprints

[Mutators, Multigame and hardened S3&K](designs/2026-10-07-mod-challenges-blueprint.md)
define researched prototype, MVP and polished-product stages, with detailed
lifecycle, engine ownership, authoring and validation gates. These are proposed
implementations, not claims of delivered gameplay or APIs.

[Sonic3K Chaos Mode](designs/2026-10-13-sonic3k-chaos-mode.md) proposes a
30-effect catalogue, configurable random scheduling, rare displacement and
later streamer voting, with current mutator reuse and required host API additions.
