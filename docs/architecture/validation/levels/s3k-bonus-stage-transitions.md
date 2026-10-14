# S3K bonus-stage load and return matrix

Qualification is limited to production boundary pilots on b930df4868 plus
`TestS3kBonusStageReturnProducts`. Request12506 completes2026-10-14 15:17:45 UTC:
all five cases pass, no errors/skips. Exact command and test hashes are in the
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md#ssz-cold-boundaries-and-bonus-return-pilots-2026-10-14).

| Stage | Main / ordered followers | Width / donor | Interior replay | Returned origin replay |
|---|---|---|---|---|
| Gumball | Sonic / Tails |320 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Pachinko (Glowing Sphere) |Knuckles / Tails,Sonic|352 / off|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Tails / Sonic,Sonic|400 / off|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|
| Gumball |Tails / Sonic,Sonic|800 / S2|Two18-tick whole-world cycles|Two45-tick whole-world cycles|
| Slot Machine |Sonic / Sonic,Sonic|800 / S1|18 ordinary ticks; whole-stage rewind unsupported|Two45-tick whole-world cycles|

Each case boots SOZ1, makes the declared logical stage-entry request and waits
for the actual GameLoop load/title/fade release. It checks coordinator type,
saved origin, fresh object manager, main identity, width and lives. Slot owns
the interior player and removes active followers. A declared requestExit
stimulates completion; the actual load returns to the saved origin, restores
the ordered roster, clears Slot runtime and retires both outgoing timelines.
Full-world comparisons retain every registry key, with both restore directions
and two forward replays. No raw restore crosses a load.

These pilots do not certify cold traversal of the starpost portal, physical
entry, ring/reward/outcome mechanisms, all native/donor character branches,
528px or the finite supported roster product. Whole Slot rewind remains an
explicit unsupported contract, not implied by a controller-only snapshot.
Broader stage mechanisms, actual exit causes, load/respawn boundaries and
matched native screenshots/audio remain open.
