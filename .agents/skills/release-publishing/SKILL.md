---
name: release-publishing
description: Publish an OpenGGF GitHub release or diagnose a skipped release job in GitHub Actions.
---

# Release publishing

Read [the publishing guide](../../../docs/project/release-publishing.md) and
inspect the release workflow at the commit being published or investigated.
Use [release rollover](../../../docs/project/release-rollover.md) when
promoting versions.

- A non-deletion push to `master` is the publication trigger. It runs
  validation/builds, then publishes a public release. Pull requests and
  manual dispatches do not publish; do not add a manual-dispatch step after
  delivering an authorized release to `master`.
- After publication, preserve `release/<version>` at the published tag when
  requested. Follow the rollover guide to synchronize release fixes and promote
  development branches; do not push the unchanged released version to `master`
  as part of branch housekeeping.
- Full ordinary validation is public-release-only: use explicit `-Pfull-suite`
  for release preparation. Plain Maven, category runs, PRs and routine manual CI
  use smoke. The Release workflow explicitly opts into the full suite.
- Local release preparation also runs the existing AIZ1/HCZ1 obligation pilot
  with `--run --expected-candidate <reviewed-full-SHA>` and an absolute existing
  original S3&K ROM path. Strict mode binds the committed inventory and clean
  candidate inputs, checks before/after source/fixture/ROM state, rebuilds through
  the queue and rejects any missing, duplicate, failed or skipped required case.
  It remains a 26-case pilot; full-suite, guards, required ROM/trace and campaign
  gates remain separate. Keep ROMs local/private, never upload/copy/symlink them.
  Hosted builds and automatic publication remain independent of private fixtures;
  preserve the manual-ROM opt-in. Record concise results, not pass receipts.
- Development branches may intentionally share a candidate commit through
  `candidateBranchAliases`. Before master promotion, clear aliases and set
  `targetBranch=master`; never carry the shared-development alias into publication.
- Read the POM version and check its remote `v<version>` tag before pushing.
  The workflow creates the tag at the validated `github.sha` and rejects an
  existing tag. Do not pre-create, delete, or move tags to make a retry pass.
  A later release needs a new version rather than overwriting existing assets.
- Preserve session authorization: an authorized release push can proceed
  without another approval ceremony. A request to investigate a skipped job
  alone does not authorize a new push or publication. Do not describe a
  `master` push as validation-only.
- For a skipped job, inspect the event, ref, commit, workflow definition, and
  dependency results. Old runs can retain the former manual-only gate.
  Optional ROM validation and packaging steps for other operating systems
  can be skipped independently.
- Report the push-triggered run URL and distinguish queued/running builds
  from completed publication. Confirm the `release` job, release URL, tag,
  and all four downloads before claiming the release is published. Inspect
  partial publication before retrying a failed run.
