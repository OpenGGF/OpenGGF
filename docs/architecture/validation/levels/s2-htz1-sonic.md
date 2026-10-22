# S2 Hill Top act 1: partial Sonic matrix

Canonical slot: `HILL_TOP_1`, engine zone 4 / act 0. This is a local regression
matrix under the [level standard](../../../guide/contributing/level-test-standard.md),
not an act-completion certificate. Evidence and exact commands are in the
[2026-10-22 validation record](../2026-10-22-native-gap-assertions.md).

| Obligation | Executable assertion / inlet | Qualified configuration | Remaining gap |
| --- | --- | --- | --- |
| OBJECT / EVENT / ROUTE local earthquake crossing | `TestS2Htz1Headless.testPositionedWalkThroughEarthquakeZoneNoStall`: positioned 100 pixels before the old X=6635 collision coordinate; the camera must naturally activate the earthquake, Sonic must remain alive and cross to X>=6699 without a grounded stall | Shared production HTZ1; Sonic; default fixture viewport/donor/team only | Positioned inlet, not traversal from spawn. All five production width presets, supported donors and team shapes remain unqualified |
| ENTRY / LIFE / LOAD / BOSS / whole ROUTE | No new executable obligation discharged by this change | Unrun | Spawn-to-exit traversal, checkpoints/death/reload, boss path and outgoing load need independent real-boundary checks |
| REWIND | No new earthquake crossing replay in this slice | Unrun | Before/active/after event, terrain/platform contact and release; each supported width/donor; team ownership |
| PRESENT / ORACLE | Real ROM terrain and event state are consumed; no pixel/audio comparison | Logic only | Native scene rendering/audio, earthquake timing comparison and remaining local boundary/negative-activation checks |

The former spawn-based 2,000-frame Right-only test ended at X=172 while its
target was X=6635. That passing test was no evidence of reaching the earthquake.
The replacement deliberately seeds only a documented local position; it does
not force the earthquake flag or seed the successful crossing.
