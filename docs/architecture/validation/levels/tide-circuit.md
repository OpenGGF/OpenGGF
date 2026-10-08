# Tide Circuit act and route matrix

This is an original bounded S2 hosted-mod fixture, not a stock ROM act/parity
claim. Both exported acts use the same tagged destination and distinct zero-based
act identities. The [worked guide](../../../modding/guides/two-act-campaign.md)
and [creator source](../../../../src/test/resources/mods/sample-two-act-campaign-src/project/)
are the executable inputs. The owning delivery plan is
`2026-10-07-mod-framework-product-readiness.md`.

| Act / route | Concrete obligations | Test / oracle | Current evidence |
| --- | --- | --- | --- |
| Act 1, Sonic solo, native movement | Original floor grounding and horizontal traversal; initial water; event/channel/palette/scroll/render dispatch; state restore then forward step; fresh reload; native checkpoint, death/respawn and results completion enter Act 2 | `TestTwoActModCampaign.bothActsExecuteGameplayContributionsRewindSaveRespawnAndResultsHandoff`; original floor layout, explicit target heights, captured counters, engine destination plan | Implemented; queued execution pending |
| Act 2, Sonic solo, native movement | Independent act initialization and fresh runtime factory; distinct water/palette; same consumer/rewind/reload and checkpoint/death checks; original captured ring tally and results hold return to stock successor | Same test executes Act 2 separately, with recorded act and tagged save | Implemented; queued execution pending |
| Act 2, independent finish route | Native floor traversal to registered original gate; captured ring tally restore and identical replay; fresh load recreates the owner object; final authored progression | `independentActTwoFinishGateRestoresItsTallyAndRecreatesAfterFreshLoad` | Implemented; queued execution pending |
| Both acts, registration and progression | Independent bounded assets validated through scanner/SDK/classloader, one transaction, contiguous acts and results edges | `externalCampaignPublishesTwoBoundedActsWithIndependentFactoryLoads` plus `TestModZoneLoader.twoAuthoredActsUseOneTaggedZoneAndDistinctHostPayloadsAndResultsEdges` | Implemented; queued execution pending |
| Both acts, persistence | Tagged owner/local identity and act, reordered unrelated owners, missing/disabled owner recovery | Actual snapshot-provider capture + host destination resolver; loader reorder/disable tests | Implemented; queued execution pending |
| Both acts, native width/main/team breadth | 320/400/512/640/800; Sonic and Tails solo; Sonic+Tails and Tails+Sonic; grounding, traversal, event restore, state restore and identical forward replay | `shortNativeAndSupportedDonorRoutesRestoreAndReplay`, 20 native configurations × 2 independently loaded acts | Implemented; queued execution pending |
| Both acts, supported donor breadth | S1 Sonic solo; S3K Sonic/Tails/Knuckles solo, Sonic+Tails and Tails+Sonic, at 320 and 800; real initialized donor, traversal and rewind/replay | Same short test, 12 donor configurations × 2 acts; explicit ROM assumptions | Implemented; queued execution pending; S2-only invocation may skip donor cases |
| Both acts, event restoration | Capture, restore, missing reset, reconcile after restored objects, fresh handlers and one stable engine event identity; faults disable actual owner/dependents | `TestModZoneEventLifecycle`; integer state and explicit fresh-handler assertions | Implemented; queued execution pending |

The following required breadth remains open and is not certified by these short
checks: 320/400/512/640/800 viewport × receiving-game-supported donor lifecycle
cross-product beyond the explicitly listed receiving-game-compatible cases;
GPU/native presentation captures; width/donor/team checkpoint and complete tally
routes beyond the separate Sonic solo native route. The checkpoint/death and
physical signpost/tally assertions above are implemented but remain unverified
until the queued test executes. No route
combination is labeled unsupported to conceal a missing check. These rows must be
executed or retained as delivery limitations; full matrices for stock receiving
acts are separate inherited obligations.

Validation commands and exact result counts will be updated after completed
focused execution. Candidate API/source compilation and a load alone are not
acceptance evidence for a complete playable campaign.
