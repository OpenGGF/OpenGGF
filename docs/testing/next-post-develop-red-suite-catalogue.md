# `next` post-`develop` red-suite catalogue

Snapshot: merge commit `593237ef2b3b9e6e666b4c7d7f7adb4226c13005`, from `next` `2051af8ca78fbde66e0b8db6357324d47b3d554d` and green `develop` `6a4c394880a2c89e34c23c294a4048ad509102bc`.

The clean full-suite run (`mvn clean test`) discovered 15,089 tests: 14,937 passed, 94 failed, 34 errored, and 24 were skipped. The 128 red method identities split into 73 deferred unfinished-zone reds and 55 in-scope reds.

## Scope split

| Scope | Reds | Classes | Treatment |
|---|---:|---:|---|
| MHZ unfinished implementation | 51 | 11 | Deferred |
| FBZ unfinished implementation | 22 | 7 | Deferred |
| SOZ and later unfinished S&K zones | 0 | 0 | Deferred when encountered |
| Cross-cutting and implemented-zone work | 55 | 29 | In scope |

The exact 73-method deferred set is maintained in `docs/testing/unfinished-sk-zone-red-exclusions.txt`. Cross-cutting guards remain in scope even if their diagnostic names an MHZ or FBZ production file.

## In-scope root-cause catalogue

| Root cause | Reds | Kind | Lineage | Recommended owner/action |
|---|---:|---|---|---|
| Provisional Mod API publication and compatibility surface was internally inconsistent | 6 | API/ABI | Existing `next`; merge widened already-red diagnostics | Remove provisional shims, decide intentional exposure, then establish the first 0.7 baseline |
| Mod object-art count/preflight contract is incomplete | 14 | Runtime/API | Existing `next` | Define explicit empty counts and one preflight/cache order for stock and mod providers |
| S3K custom-zone factory inventory has drifted | 2 | Runtime/API | Existing `next` | Audit each S3KL/SKL factory dependency and declare stock-bound semantics explicitly |
| Static and dynamic virtual-pattern ranges overlap | 2 | Architecture | Existing `next` | Assign one owner to the `0x108000` address space; do not allow ambiguous static/dynamic ownership |
| Packaged sample launch remains at master title | 1 | Runtime | Existing `next` | Restore standalone launch dispatch into gameplay |
| Rewind fixtures supply null or unstubbed rewind adapters/keys | 3 | Test fixture | Existing `next`/newly exposed | Replace mocks with real or explicitly key-bearing adapters; keep the registry fail-closed |
| Dragonfly graph fixture omits the post-camera render phase | 2 | Test fixture | Existing `next` | Drive `refreshPostCameraRenderState()` before dispatch; reassess graph behavior only after the fixture reaches assertions |
| HCZ end-boss graph fixture omits `playerQuery` | 2 | Test fixture | Newly exposed by merge | Supply an empty explicit player query and rerun the graph assertions |
| Stale exact rewind policy and duplicate object annotation | 3 | Architecture | Existing `next` | Remove the deleted Tension Bridge policy and the redundant MHZ object annotation; retain central policy ownership |
| HCZ vortex 16.16 motion and rewind state were lost | 3 | Production regression | Existing `next`, confirmed by the post-merge suite | Restore `xSub`/`ySub`, fixed-point integration, and rewind capture from reviewed commit `482d347a4` |
| HCZ hand-launcher ROM solid/control semantics were lost | 2 | Production regression | Existing `next`, confirmed by the post-merge suite | Port develop’s equal d3 height and native bits 0–6 policy into next’s per-player architecture |
| MGZ twisting-loop cleanup cannot recognize its own rider ownership | 3 | Production bug | Existing `next` | Repair the generic ownership fingerprint without clearing replacement control |
| MGZ2 BG-rise route triggers fatal dual-plane overlap before rising | 1 | Production bug | Existing `next` | Correct BG collision state/order; preserve generic ROM `sub_F846` and avoid zone/frame carve-outs |
| FBZ visual tooling violates service ownership; crypto factories are guard false positives | 2 | Architecture | Existing `next` | Inject configuration ownership and narrowly recognize JDK crypto factories |
| Debug emerald fixture registers two null-key mocks | 2 | Test fixture | Existing `next` | Use real collision and layout-mutation adapters |
| Special-stage trace launch omits native aspect resolution | 1 | Trace bootstrap | Existing `next` | Reunify with canonical pre-launch configuration; do not hydrate engine state from trace rows |
| Terrain reflection fixture requests the wrong compatibility overload | 4 | Test fixture | Merge fixture regression | Use next’s six-argument compatibility overload and rerun all nine terrain assertions |
| Child-slot wait calculation mixes relative and global indices | 1 | Production bug | Existing `next` | Compare the reserved global slot with the global execution cursor |
| `LevelManager` exceeds its frozen extraction budget (2,838 vs 2,819) | 1 | Architecture | Merge composition | Move oscillator suppression to `OscillationManager` and persistent respawn handoff to the checkpoint/reset collaborator; do not raise the budget |
| **Total** | **55** |  |  |  |

## Landing assessment

The merge parents and conflict resolutions are otherwise coherent, and 179 focused merge tests pass. It is not ready to advance the real `next` branch until the two HCZ behavior clusters confirmed by the post-merge run, the terrain merge-fixture mismatch, and the accidental frozen-Mod-API exposure are resolved and reviewed. The remaining historical reds can then be carried as an explicit, accurately classified remediation queue.

## Remediation order

1. Clear merge blockers: HCZ vortex, HCZ hand launcher, terrain fixture, and unintended Mod API exposure.
2. Repair fixture blockers so hidden behavior assertions become observable: null-key adapters, HCZ player query, Dragonfly render phase.
3. Fix shared foundations: pattern ownership, child-slot indexing, service ownership, rewind policy cleanup, and trace bootstrap.
4. Fix implemented-zone behavior: MGZ loop ownership and MGZ2 BG collision semantics.
5. Stabilize mod contracts: object-art preflight/count, S3K custom-zone inventory, sample launch, legacy compatibility, and new minor API publication.
6. Extract `LevelManager` responsibilities and rerun the complete suite; keep all 73 unfinished-zone identities separately accounted for.
