# Sonic 2 palette and ending boundary validation — 2026-10-22

Scope: the `s2_presentation` lane of the native-gap swarm, based on
`94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`, in the isolated
`bugfix/ai-gap-s2-presentation-20261022` worktree. The parent owns combined
smoke and integration. This is bounded presentation evidence, not whole-act,
whole-ending or whole-game certification.

## Native evidence and decisions

S2 World REV01 was read from the existing user ROM, with CRC32 `7B905383`
and SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`. References below use the
pinned S2 disassembly `34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`.
Runtime colours still come from the ROM pipeline.

- `PalCycle` (s2.asm:2703-2720), signed-word dispatch table at `$19F4`,
  maps ROM zones 4/5 to `$1A76` (MTZ), 13/14 to `$1D16` (CPZ), and 6/16 to
  `$1DB6` (WFZ). The missing MTZ3, DEZ and SCZ aliases now install those cycles.
- `PalCycle_CPZ` (s2.asm:2951-2984) has one timer gating all three streams.
  The three existing Java timer replicas must receive the same external
  `PalCycle_Timer` write. Freezing just the first replica was rejected after
  checking the routine's `bpl.s .return`: it would leave two streams animating
  during the ending fade. MTZ and WFZ retain their independent secondary timers.
- `ObjC7_SetupEnding` (s2.asm:83155-83174) tests `Vint_runcount & $1F`,
  decrements the signed follow offset, positions the robot, and only then calls
  `loc_3DFBA` (s2.asm:83842-83860). That explosion helper allocates before
  advancing RNG and subtracts `$30` from each shifted random byte. These common
  operations also run during the countdown; an entry-relative clock, the generic
  `$20` explosion offset and stopping the follow work during the fade were rejected.
- `loc_3D9BE` (s2.asm:83185-83191) arms byte timer `$16` and holds the palette
  cycle timer at `$7FFF`. `loc_3D9D6` (s2.asm:83194-83225) decrements first,
  performs 21 normal-palette passes, and on pass 22 fills all 64 colours white,
  requests the ending and deletes the body. `Pal_FadeToWhite.UpdateColour`
  (s2.asm:3623-3652) increments red to `$E`, then green, then blue; it does not
  raise all three channels together. Direct ROM opcodes at `$3D9BE` and `$25E0`
  confirm the immediates and branch order.
- Shipped `fixBugs=0` sends `$F9` through `PlaySound`, where explosion sounds
  can crowd it out; the fixed branch uses `PlayMusic`. Calling the direct
  music fade on threshold entry, or adding the shell's generic outgoing fade,
  changes that ownership. The internal `EndingEntryPresentation` capability
  keeps save requests, lifecycle binding and provider initialization while
  letting the native level/provider own both entry and reveal. It does not
  change the published `EndingProvider` API. Providers without the capability
  keep the generic shell flow.
- `EndgameCredits` (s2.asm:13216-13233), ROM `$9FF2`, selects `$18E` or `$144`
  from hardware-region bit 6, then uses `DBF`: the holds are 399 NTSC and 325
  PAL V-blanks. Each reveal/outgoing fade takes 22 V-blanks. The sentinel adds
  a second black fade before logo loading. All 21 slides therefore reach logo
  loading after 9,325 NTSC or 7,771 PAL provider calls from the first reveal,
  independently of a configured render FPS. This count excludes cutscene/art
  setup and the following logo-initialization call.

## Affected act and presentation matrix

| Contract | Executable check | Covered boundary | Inherited limits |
| --- | --- | --- | --- |
| MTZ Act 3 / ROM zone 5 | `TestSonic2PaletteCycleAliases` | Real level installation, three ROM streams through wraps, primary timer, capture/restore and full forward replay | No continuous act route, donor/team/viewport visual matrix |
| SCZ / ROM zone 16 | `TestSonic2PaletteCycleAliases` | Real installation, fire/belt toggle, native colours/cadence and replay of all streams | No Tornado/campaign/viewport visual certification |
| DEZ / ROM zone 14 | `TestSonic2PaletteCycleAliases` | Real installation, three CPZ streams, common timer hold, restore/replay | No cold boss clear or complete DEZ route |
| DEZ ending ingress | `TestDEZEndingFade` | Threshold/no early fade; all 64 normal colours; exact 22-pass handoff; VInt rumble/follow; native explosion coordinates; object/palette restore and forward replay; full native SST failure and available-slot RNG control | Seeded phase 6, mock main player; no full fight, team or donor breadth |
| Native boss → shell → ending | `TestSonic2NativeEndingEntry` | Real ROM/session, palette animation manager, sound request ordering, request consumption, provider initialization and all-white framebuffer | Seeded defeat ingress, Sonic solo at 320; no entire cutscene or credit rendering |
| Credits NTSC/PAL | `TestSonic2EndingProvider` | All 21 actual update/fade lifecycles; inclusive region waits; sentinel fade; logo-loading boundary; direct ROM instruction/table checks | Starts at first credit reveal; no cutscene/credit-art/logo pixel proof |
| Default shell behavior | Existing selected `TestGameLoop` methods | Save request, provider initialization and generic white reveal remain exercised | Selected methods only |

## Focused verification

Actual Java 25 / Lua 5.4 / PowerShell preflight passed using
`run_categories.py --base 94c40570a15a11b70caf7456e9b0d0a8eec8a9b4 --preflight`.
Every Maven test command uses `tools/testing/maven_queue.py`, the absolute S2 ROM
path, and this worktree's `target/`. Rendered checks unset `DISPLAY` and
`WAYLAND_DISPLAY` and select `-Dopenggf.headless.gl=surfaceless-egl`.
`GameplayCaptureSession` installs the production no-device audio owner.

Initial selection:
`-Dtest=TestDEZEndingFade,TestDEZDeathEggRobot,TestSonic2PaletteCycleAliases,TestSonic2PaletteOwnershipIntegration,TestSonic2EndingProvider,TestPlcProviderOwnedFadeLifecycle`.
82 cases ran: 79 passed, three new fixture errors because `StubObjectServices`
requires an explicit player query. There were no assertion failures or skips.
The fixture was corrected; only the affected tests need repeating.

The next selection was
`-Dtest=TestDEZEndingFade,TestSonic2NativeEndingEntry,TestGameLoop#testStepWritesSaveForS2CreditsTransition+testDoEnterEndingDoesNotWriteSaveForActiveSlot+doEnterEndingStartsRevealWhenPreviousFadeIsHoldingWhite,TestRewindCoverageGuard,TestRewindFieldDispositionGuard`.
It ran nine cases: the rendered native handoff, three default-shell methods,
two guards and the countdown case passed. Two remaining new unit-fixture errors
required an explicit rewind identity table and safe Mockito restubbing. These
were corrected without changing runtime code.

The final affected rerun, `--lean -Dmse=off -Dtest=TestDEZEndingFade test`,
passed all four cases with zero failures, errors or skips, including the added
real full-SST/available-slot RNG control. Across these incremental runs, all
89 distinct selected checks passed. The rendered check read back an entirely
white 320×224 framebuffer after the real provider initialized, observed `$F9`
then `$8A` native requests, and no generic FadeManager transition or direct
music fade. It does not compare PCM waveforms or exhaust explosion-versus-fade
mailbox arbitration.

The API-surface check, `--lean -Dmse=off -Dtest=TestModApiSignatureSurface test`,
passed all 11 cases with zero failures, errors or skips. The complete incremental
selection therefore covers 100 distinct passing cases. Commit identity is
recorded in the lane handoff.
No broad smoke, full suite, trace sweep, cold ending route or general parity
claim is made here. The parent retains those integration obligations.
