# Sitar Hero authored career model

Date: 2026-10-07. Model refinement based on the authored world-tour design at
`1e89086fdfbb50b2ad6700ddf463dc252a422fb3`, in
`.worktrees/ai-sitar-hero-career` / `feature/ai-sitar-hero-career`.

The [authored world-tour refinement](2026-10-07-sitar-hero-full-version.md#authored-world-tour-refinement)
owns the agreed story and integration flow. This artifact records only the pure
model, its persistence contract and the evidence for this implementation seam.
Scene navigation, dialogue, natural-completion eligibility and shared product
documentation belong to the parent task.

## Route and public seams

`CareerWorld` is the immutable record `(String id, String title,
List<String> requiredSongIds, List<String> sideSongIds)`. `CareerTour` is
`(String id, String game, String title, String subtitle, List<CareerWorld> worlds)`.
Both copy incoming lists and reject empty required routes, duplicate assignments,
invalid IDs/labels, and duplicate or foreign-game worlds. The journal only
recognises canonical metadata, including equivalent immutable record copies;
altering a known world's setlist cannot bypass its gate.

`CareerTours.all()` returns `List<CareerTour>` in S1, S2, S3K order. Its stable
tour IDs are `s1`, `s2`, and `s3k`, matching their game IDs. World IDs are exactly
the game-prefixed IDs specified in the shared design. Each world supplies one
known `world.id() + "-intro"` scene; each tour supplies `tour.id() + "-outro"`.

| Tour | Worlds | Required songs | Optional songs | Total songs |
| --- | ---: | ---: | ---: | ---: |
| s1 | 8 | 9 | 2 | 11 |
| s2 | 13 | 14 | 8 | 22 |
| s3k | 14 | 27 | 19 | 46 |
| Total | 35 | 50 | 29 | 79 |

Every substantive `SongCatalog` entry belongs exactly once, to a required or
optional assignment. Main zone themes/act variants are required. Native final
battles gate the finale, including both S3K Final Boss and Doomsday. Native
ending and final credits gate the encore. S3's earlier ending/credits remain
optional at Launch Base; they cannot substitute for the S&K encore. S3K Flying
Battery follows Mushroom Hill, independently of its earlier music-header slots.
Optional boss, bonus, competition and earlier-bank songs sit at related native
milestones and never contribute to required counts.

`CareerTours.available(List<SongSpec>)` returns whole authored tours only if
every required song exists with its matching game donor. It never shortens a
required setlist. Missing optional songs do not prevent a tour being available.
Each installed-ROM tour is self-contained; completion of another game is not
a prerequisite.

`CareerJournal` exposes these public methods:

```java
CareerJournal()
boolean record(PerformanceResult result, boolean earnedCareer)
boolean cleared(String songId)
int cleared(CareerWorld world)
int cleared(CareerTour tour)
boolean complete(CareerWorld world)
boolean complete(CareerTour tour)
boolean unlocked(CareerTour tour, CareerWorld world)
boolean seen(String sceneId)
boolean markSeen(String sceneId)
void read(String text)
String encode()
```

The first native world is open. Every earlier world's entire required setlist
must clear before a later world unlocks. Clears use stable song IDs alone;
instrument, difficulty and cosmetic performer never reset or partition them.
`cleared(world/tour)` counts only required songs. `record` returns true only for
a new known-song clear; side gigs can have their own clear markers without
advancing a gate. `markSeen` returns true only on the first mark of a known scene.
There are exactly 79 known songs and 38 known scenes. Unknown/null IDs cannot
grow either set; foreign or modified tour metadata cannot manufacture progress.

`earnedCareer` is the explicit scene-owned authority seam. The scene supplies
true only for naturally completed, eligible normal Career play. The journal
also rejects failed, zero-note, unknown-song and null results. It cannot infer
quick play, no-fail practice, demo, multiplayer, abandonment or debug provenance
from a `PerformanceResult`; those mode/completion decisions remain in the scene.
The model tests exercise this boolean seam and real completed rhythm results;
they do not claim to verify all scene flows.

## Persistence and preserved data

The independent version-1 schema is:

```text
sitar-career=1
clear=green-hill
seen=s1-green-hill-intro
```

Encoding sorts clear rows, then seen rows, for deterministic output. Reading
valid text replaces the journal; the header alone represents a valid empty
journal. CRLF input is accepted. Unsupported versions, duplicate IDs, unknown
IDs/fields, blank rows and malformed rows reject the entire input while leaving
the current journal unchanged. Parsing is bounded before allocation: at most
16,384 characters, 128 lines, and 96 characters per row. Membership and duplicate
checks further limit stored data to the 79-song/38-scene universe; the fully
populated current schema uses 118 lines including its header.

`PlayerProfile`, its version-1 save schema and existing best-score/star data are
unchanged by this refinement. No old profile or score-only legacy import is
converted into a Career clear: those records cannot prove that the player earned
a natural Career completion. The parent stores the journal separately.
All runtime collection fields are instance-owned. `CareerTours` constructs its
immutable route per call; no static object collections violate creator policy.

## Superseded approach and verification

The original model commit `f75fba41a003e763c6c196b51eb660fb756690c3` supplied
`CareerProgress`'s three-song/two-clear ladder and independent role/difficulty
records. The user-directed refinement at `1e89086fd` supersedes that ladder for
the authored journey: it could unlock a later stop with missing main acts and
partitioned what must now be shared progress. The parent removed its production
scene uses and explicitly extended this worker's scope to delete `CareerProgress`
and its three generic-tier-only tests. The unrelated profile query-validation
checks remain, together with profile/result/practice checks. `PlayerProfile`
and performance/high-score functionality remain intact.

Test-first direct compilation reproduced the absent authored-tour APIs. A
further direct regression reproduced null-tour lookup failing inside immutable
list membership; null metadata now returns false/zero rather than manufacturing
completion. The final direct invocation compiles only pure model/catalogue and
model-check sources against an empty application classpath under Java 21. It
runs `WorldTourChecks.main`, all nine retained `CareerChecks` methods and
`RhythmChecks.main`: **9 authored world-tour, 9 profile/result/practice and 10
rhythm checks passed**, exit 0. These are 28 direct behavior checks, not a
Maven/JUnit or full-suite result. Temporary classes and the runner were removed.

The dedicated `TestSitarHeroWorldTour` compiles only model, catalogue and model
check sources with an empty engine/application classpath and executes them with
a platform-only parent class loader. Its assertions cover immutable metadata, all 79 assignments and
native order, all eight ROM subsets (including empty), missing/wrong-donor
required songs, every required-act gate, optional non-gates, explicit earned
eligibility, shared progress, canonical membership, bounded seen state, atomic
corrupt-save rejection and full-journal round trips.

Queued focused validation:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSitarHeroWorldTour,TestSitarHeroCareer,TestSitarHeroModel test -B
```

The first waiting request was cancelled only after explicit parent steering,
before admission/test execution (exit 130), to remove the obsolete ladder and
change the new bridge to the agreed pure-source strategy. The revised identical
class selection contains 28 tests: nine authored-world checks, nine independent
profile/result/practice checks and ten rhythm checks. Result: pending completion
at the authorized internal source checkpoint. The request remains alive on
unchanged production/test sources; its terminal result will be reported
separately. This checkpoint is not delivery or completed validation.

Full example packaging is explicitly deferred to parent integration: this
worker's stale `SitarScene` at `1e89086fd` still uses the deleted ladder, while
the parent's updated scene uses the journal and the separate `CareerStory`
patch. The parent reported Java 21 compilation of an exact frozen source snapshot,
real `GgfModCli` creator-package validation and five new `TestSitarHeroArcade`
consumer checks. This worker independently matched all four new model source
SHA-256 digests to that snapshot's parent-owned digest file; the package/UI
results are parent-reported evidence, not runs performed in this child tree.
Actual-tree packaging and normal combined gates remain parent responsibilities.
No source overlays, scene edits, build-tree sharing or ROM-backed/rendered
claims occur in this worker's pure-model verification. Parent-owned combined validation remains required
against pinned base `09282b17305cb5794e43a26855cd2b9543b4ff5f`, followed by
integration, push and owned-worktree cleanup in the parent delivery flow.
