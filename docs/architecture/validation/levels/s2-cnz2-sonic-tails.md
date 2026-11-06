# S2 Casino Night act 2: partial Sonic and Tails matrix

Canonical `CASINO_NIGHT_2`, engine zone3/act1; native zone `$0C`, act1.
This is bounded evidence under the [level standard](../../../guide/contributing/level-test-standard.md),
not an act-completion, viewport, donor or whole-campaign certificate.

| Obligation | Contract and executable evidence | Qualified inlet / remaining gap |
| --- | --- | --- |
| OBJECT / touch | `TestTouchResponseManager.offscreenSonic2CrawlHurtPhaseStillTouchesSidekick` keeps a real Crawl resident in the touch list outside the camera, selects its native `$97` hurt phase and requires sidekick knockback dispatch. Original profile fails; corrected profile passes. `TestCrawlBadnikInstance` retains special-property, actor, attack and overlap policies. | Synthetic local geometry independent of movie/frame; native S2 rules. Other badnik profiles are not changed or certified. |
| ROUTE / recorded oracle | `TestS2CompleteEmeraldRunChain.ehz1ToDeathEggAcrossEverySpecialStage` uses the original complete-emeralds BK2. CNZ2 matches through row7848 after removing the row2390 Tails hurt disagreement. | Sonic+Tails, recorded default viewport/donation. Still red at7849 `y_speed`, native `-$0A58` versus engine `$0A58`; the level-advance boundary is still absent at moviecursor138828. |
| ENTRY / local control | `TestS2Cnz2LevelSelectTraceReplay` remains passing at the ERROR gate, with its unchanged recorded slot-machine warning scope. | Level-select route only; not equivalent to complete-chain entry. |
| REWIND / LIFE | No new persistent state: the correction changes Crawl's immutable touch profile and matching compatibility accessor only. Existing broader touch/registry checks remain in the focused selection. | No new CNZ2 death/checkpoint/whole-world before/active/after route certificate; those inherited obligations remain open. |
| EVENT / CAMERA / LOAD / BOSS / exit | Other S2 chain segment JSONs and the dynamic-gap report remain identical in the focused control. | Existing ARZ2 and transition-gap failures remain; no completion claim. |
| PRESENT / width / donor / team | Native touch has no render-visibility predicate; the local discriminator deliberately places actor/object outside the camera. | No new native pixel/audio evidence, width-preset product, alternate donors/characters/teams or competition qualification. |

## Native owner and qualification, 2026-11-05

`Touch_Loop` (s2.asm:85209-85224) tests `collision_flags` and iterates without
checking render flags. Crawl's non-rolling special contact selects `$97` in
`loc_3D36C` (s2.asm:82572-82584). The engine's Crawl-specific profile incorrectly
required render visibility. Only that requirement changes; shared touch code,
player movement, CPU, collision boxes, art clocks and fixture data are unchanged.
There is no alternate FixBugs branch at this native visibility test.

Original row2390 observations agree on Tails `$074F/$0470` beside Crawl
`$075A/$0470`, while camera X is `$07B9`. Native Tails enters hurt with
`-$0200/-$0400`; the old engine keeps `$02DF/0`. The new runtime reproduces the
native hurt and all comparisons remain error-free until7849.

One uninstrumented and one comparison-only observed candidate run reproduce all
24 complete report payloads exactly. The inherited candidate's complete CNZ2
report equals the delivered baseline, allowing its existing actual row stream
to be reused. On the same13045 rows, raw ERRORs fall276895 to188220;58260 added
value tuples and146935 removed tuples. No addition occurs on2390 baseline-clean
rows. First added tuple7851 is already-wrong `dynamic_art.edges`, after remaining
physics divergence7849. At7851 both runs have Y `$01EC` versus ROM `$01D5` and
Y speed0 versus `-$09E8`; the corrected sidekick position now agrees.

Dynamic-art field sets vary on3193 rows as real edge/request arrays change;
these are preserved coverage differences, not assumed before observations.
Shared expected values are identical and no recorded row is dropped. Full raw
fields, first-added snapshots, source/fixture controls and rejected test-fixture
attempts are retained in the dated task artifact. Temporary logging is restored;
full combined-profile and shared-smoke qualification remain lead-owned.
