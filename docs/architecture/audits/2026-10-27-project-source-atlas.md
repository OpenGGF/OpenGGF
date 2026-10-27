# Project source atlas — 2026-10-27

[Open the interactive source atlas](project-source-atlas.html), or download the
HTML and open it in a modern browser. No server is required; the pinned D3
library loads from jsDelivr. [Generator and usage](../../../tools/project_atlas/README.md).

## Source and scope

The persisted snapshot reads `develop` at
`ca939333924a19826156cce871e31a3befbd450b`: 10,649 current Java files,
19,363 named types, 389 packages and 2,386,814 source lines. It includes 553
historical-only source items and follows 629 verified namespace moves. The initial
chat atlas used `9a796ec60f8437fab1f2fffb755174a73c7f7659`; the first persisted
snapshot used `c27aa5c1929be583e87de6169b7783b544cf37e3`. Regeneration includes
the intervening validation-tooling and atlas/runbook integrations.

The source census, source universe, import flows, repository inventory and Git
history are views of one pinned ref. All project data, styles and application
code are embedded in the generated HTML. Runtime ROM contents, dirty/untracked
checkout files, generated untracked output and submodule interiors are excluded.
Component labels group paths/packages; they do not assert Maven module boundaries
or runtime call relationships.

## Time slider contract

The Source Universe slider starts at its right endpoint, the source snapshot.
Moving left visits older first-parent integrations, sampled at daily integration
boundaries and both sides of verified namespace migrations; the first and final
commits are retained. This snapshot has 232 stops across 3,975 first-parent commits.
Each stop names an actual Git commit and UTC
committer date; it is not an interpolated source state.

- A solid coloured item exists at the selected snapshot and at the current ref.
- A dotted/ghost item is absent at the selected snapshot.
- A solid grayscale item exists at that snapshot but was removed from the current
  ref. Such items are recovered from Git, not inferred from current filenames.

Packages and components derive presence from their descendant paths. The layout
is the union of current and historical paths and remains stable while scrubbing.
Deletion/reintroduction produces separate intervals. Verified moves from
`uk.co.jamesj999.sonic` to `com.openggf` retain one identity and layout position;
the inspector and search retain original-path provenance. Other moves remain
separate; continuity is not guessed from basenames alone.

This is **file-presence history**, not a replay of every version of each class.
Circle area and declaration metadata use current content, or the final blob
before deletion for removed paths. The header remains the current-ref census.
Historical imports and per-version nested declaration changes are not reconstructed.
These limits are stated beside the controls and in source details.

## Git activity and milestones

The History view independently toggles diff and milestone charts and supports
date windows and text/Java/test scopes. Exact daily aggregate totals cover the
complete first-parent history. A merge is diffed against its first parent so
feature-branch commits are not counted again as integrations. UTC timestamps
determine chart bins; first-parent ancestry determines slider state order.

The 100 most recent first-parent commits retain per-commit details and the five
largest changed paths per scope. Older intervals retain complete counts with
their drill-down limit displayed. Milestone source commits and qualification
notes remain inspectable. Release-index tags use target-commit timestamps,
not presumed publication dates. Line churn is not a quality or completion score.

## Decisions and rejected approaches

The first combined source/history payload exceeded T3's 512,000-character HTML
input limit. Pruning source classes or hiding old activity was rejected: it would
make the requested whole-project picture incomplete. The delivered representation
losslessly compresses repeated primary declaration names and groups adjacency
data, retaining all current source/type/import records. History keeps exact daily
totals and a clearly bounded recent-commit detail window. The time-aware artifact
is about 466,000 characters at this pin.

Current-file introduction dates alone were also rejected: they cannot render
code that existed in the past and was removed. The generator instead replays all
first-parent Java add/delete changes, records repeated lifetime intervals, and
reads each removed path's final blob before retirement. It compares reconstructed
path identities with independent `git ls-tree` snapshots.

### Package-refactor continuity correction

The original path-only model incorrectly retired the old namespace and birthed
the new namespace at `ff21d6cfef54083ffdc3b00e7523b2eb13a15a2f` (2026-03-04
first-parent integration). Its parent-to-child diff contains 629 exact namespace
path pairs. The revised model joins only paired deletion/addition events in one
integration, with matching normalized packages and top-level type identities,
and no overlapping lifetime intervals. `ProfileSnapshot` and `SensorResult`
change from records to ordinary classes in that diff; both retain their named
class identity. Class-to-interface changes and concurrent compatibility copies
remain distinct. Basename-only matching and merging coexisting paths were
rejected because they would hide real source items.

Both namespace paths share a single circle and canonical package position;
historical aliases remain searchable and the inspector shows the actual old path
before the refactor. Raw Git path checks remain independent of the collapsed
identities, whose present counts must agree with physical Git tree counts.
The slider explicitly includes the integration's before/after states.

## Verification and update

```bash
python3 -m unittest discover -s tools/project_atlas -p 'test_*.py' -v
python3 tools/project_atlas/generate.py --ref develop --t3-limit
```

Fourteen ROM-free fixture tests pass, covering lexical declarations, lossless
encoding, resource-package grouping, pinned blob reads despite dirty files,
creation/deletion/reintroduction, renames, first-parent merge admission, diff
totals, namespace move continuity, record conversion, rejected mismatches and
overlapping copies, and portable HTML generation. Real generation reconciles Java additions
minus removals to all 2,386,814 current source lines and independently compares
seven historical Java path sets with Git trees, including both sides of the
namespace refactor. Browser scenario checks cover
slider endpoints, all three presence styles, stable geometry, historical source
selection, native range-input events, scope/area controls and retained History navigation.

Validation is scoped to the Python/Git analysis and standalone document UI. No
engine behavior, Java API, build selection or runtime asset pipeline changes;
engine smoke/ROM/trace suites are not claims made by this artifact.
The change planner at the pre-task destination selected all 4,236 smoke classes
because the new standalone tool and its ignore allowlist are unclassified.
That selection is disproportionate to this read-only analysis path; the focused
fixture, real-history, browser and documentation checks replace it. The saved
HTML also booted in a private headless Chromium profile outside T3 with its
fallback theme, current snapshot label and application state intact.

Use the generator command above whenever an updated interactive is wanted. Its
default destination is this audit directory's `project-source-atlas.html`; a
different `--ref` or `--output` can retain another snapshot. Commit intentional
updates through the repository's normal delivery flow.
