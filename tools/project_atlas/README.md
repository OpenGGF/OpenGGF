# Project source atlas

Generate or update the [persisted interactive](../../docs/architecture/audits/project-source-atlas.html):

```bash
python3 tools/project_atlas/generate.py --ref develop
```

The default output is `docs/architecture/audits/project-source-atlas.html`. Open
the downloaded HTML in a modern browser; no server is needed. All project data,
styles and application code are embedded. The pinned D3 7.9.0 library loads from
jsDelivr, so viewing requires access to that CDN. Generation requires only Python
3 and Git, performs no network requests and needs no ROMs or Java build.

The generator resolves the ref once, reads committed Git blobs and history,
and atomically replaces the output. Dirty and untracked checkout files do not
enter the snapshot. It never switches branches, stages files or modifies its
input checkout. The generated file records its exact source SHA. Commit the
HTML and any intentional documentation changes through the ordinary delivery
workflow after reviewing an update.

Other outputs or historical refs:

```bash
python3 tools/project_atlas/generate.py --ref v0.6.20260911 --output target/atlas-v06.html
python3 tools/project_atlas/generate.py --ref develop --t3-limit
python3 tools/project_atlas/generate.py --help
```

`--t3-limit` additionally enforces T3's 512,000-character HTML input limit.
Without it, a larger standalone artifact remains supported as the project grows.
For an in-conversation update, read the resulting file and pass its HTML to
T3's `html_preview`, then `html_render`. No preview server is required.

## Views and time semantics

- **Source Universe:** stable, zoomable component/package/file layout; type and
  path search, source-scope and area controls. The time slider starts on the
  right at the source ref. Moving left visits older first-parent integrations,
  sampled at daily boundaries with the first and final commits retained.
  Solid colour means a path exists at the selected snapshot and in the current
  source ref. Dotted ghosts are absent at that snapshot. Solid grayscale means
  the path exists there but was subsequently removed from the current ref.
  Package/component presence is derived from its file descendants.
- **Import flows:** current-snapshot explicit Java imports between grouped
  components. These links are not a runtime call graph and do not travel with
  the time slider.
- **Repository:** tracked regular files by top-level directory and extension,
  plus direct libraries from the selected ref's root POM.
- **History:** independently toggleable Git diff and milestone charts, UTC date
  filters and text/Java/test-file scopes. Exact daily aggregate counts cover the
  full first-parent history. Per-commit/file drill-down is bundled for the 100
  most recent first-parent commits, with the five largest changed paths per
  scope. Milestones retain their source commit and qualification limits.

Path identity is deliberate: renames appear as retirement of one path and birth
of another; deletion and reintroduction produce separate lifetime intervals.
The slider reconstructs file presence, not every historical declaration or
dependency. Circle size and declaration metadata remain current or last-known
before deletion, keeping positions stable while scrubbing. The header census
always describes the selected source ref, not a count inferred for an older date.

Diffs compare each first-parent integration with its first parent, avoiding
double-counting all feature-branch ancestors. Dates are UTC committer timestamps;
slider ordering follows integration ancestry, so clock skew never changes the
actual Git state. Binary changes have file counts but no line height. Rename
detection is disabled. Release-tag points use target-commit timestamps, not
assumed publication dates. Activity volume is not a measure of quality or
completion.

Submodule interiors, ROM contents, untracked files, symlinks and generated
untracked output are excluded. Java types are lexically counted after masking
strings/comments, including nested/local named declarations and excluding
anonymous classes. Explicit imports resolve to repository top-level types;
same-package references, wildcard expansion, reflection and dynamic dispatch
are not inferred. Components are package/path groupings, not Maven modules.

## Release preparation

Every public release preparation must rebuild, review and commit the atlas after
preparing its source/version commit and before final candidate qualification or
the publishing push. Follow the
[release runbook](../../docs/project/release-publishing.md#refresh-the-project-atlas-for-a-release)
for the explicit source pin and final candidate SHA. The artifact records its
input commit rather than its own containing commit; the Release workflow, not
this tool, creates the public version tag.

## Verification

```bash
python3 -m unittest discover -s tools/project_atlas -p 'test_*.py' -v
```

The ROM-free fixture tests cover lexical declarations and compression, pinned
blob reads despite a dirty checkout, file creation/deletion/reintroduction,
moves, first-parent merge admission, exact diff totals and a complete portable
HTML build. Every real generation independently checks five historical Java
path sets against `git ls-tree` and reconciles cumulative Java additions minus
removals with the source census.

After renderer changes, verify in T3 at desktop and 320px widths: rightmost
slider default, earlier grayscale and ghost states, stable positions while
scrubbing, keyboard range control, all source filters/area modes, historical
path search, import/repository navigation and the History date/scope/toggle
controls. Also open the generated file outside T3 to check its fallback theme.

Origin: the 2026-10-27 project-class visualization and time-slider request.
The [dated audit](../../docs/architecture/audits/2026-10-27-project-source-atlas.md)
records the source pin, decisions, verification and limitations of the first
persisted snapshot.
