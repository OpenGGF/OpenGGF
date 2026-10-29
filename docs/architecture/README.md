# Architecture documentation

This directory owns OpenGGF's architectural reference material and dated
engineering artifacts.

## Current architecture

The Markdown files directly in this directory describe the current engine and
its architectural policies.

The [interactive project source atlas](audits/project-source-atlas.html) explores
components and classes, historical file presence, import connections and Git
milestones. Its [dated audit](audits/2026-10-27-project-source-atlas.md) records
the snapshot and limits; the [generator](../../tools/project_atlas/README.md)
updates it on demand.

## Dated artifacts

- [Quantitative stock-game parity](designs/2026-10-29-quantitative-stock-parity.md)
  defines original-behavior obligations, evidence authority and measured-scope
  reporting. Its initial reporting core has a partial catalogue and no qualified
  producers; native comparison integrations remain explicit follow-up work.

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

[Runtime mod compatibility evidence](designs/2026-10-13-mod-runtime-compatibility.md)
records the implemented diagnostic levels, warning-first policy and validation.

[Sonic3K Chaos Mode](designs/2026-10-13-sonic3k-chaos-mode.md) proposes a
55-effect catalogue, including moving HUDs, object fields and temporary native
encounters, with configurable random scheduling, rare displacement and later
streamer voting. It distinguishes current mutator/native-code reuse from required
host API additions.

The [top 50 additional Chaos candidates](research/2026-10-13-chaos-effect-shortlist.md)
rank distinct adaptations from Zolika's other published catalogues, with source
links, Sonic behavior, severity and qualification boundaries. This shortlist
does not automatically expand the proposed pool.
