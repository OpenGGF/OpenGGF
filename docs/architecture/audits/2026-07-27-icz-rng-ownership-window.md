# ICZ RNG ownership and lifecycle audit

Date: 2026-07-27
Branch: `bugfix/ai-trace-s3k-icz-rng-ownership`
Baseline: `41f3bc62f2ee674b005bdab2c4536e7ce556dd64`

## Scope and baseline

This is a diagnosis-only follow-up to the original late-window audit. No
production behavior was changed. The complete ICZ replay reproduced:

```text
29 errors, 0 warnings
first error: frame 24140 -- rings mismatch (expected=3, actual=2)
```

The investigation compared the committed native per-frame RNG seed and SST
snapshots with temporary engine seed/caller/slot logging. All temporary Java
logging was removed after capture.

## Probe status

`tools/bizhawk/probes/icz_rng_ownership_probe.lua` uses `ProbeRuntime`, which
sets invisible emulation, sound off, uncapped emulation, and 6400% speed. Its
hooks are registered only after the semantic level/ICZ2/camera gate. It pairs
the `Random_Number` entry at `$001D24` with its RTS at `$001D4A`, asserts the
predicted result and post-seed, records the return PC plus A0/A1 SST context,
and unregisters/flushes/exits through the shared runtime.

The corrected dual-hook probe was run from this worktree with:

```bash
env BIZHAWK_HOME=/home/farrell/code/projects/OpenGGF/docs/BizHawk-2.11-linux-x64 \
  OGGF_OUT=/home/farrell/code/projects/OpenGGF/.worktrees/trace-s3k-icz-rng-ownership/target/icz-rng-native.txt \
  tools/bizhawk/run_bizhawk_lua.sh \
  tools/bizhawk/probes/icz_rng_ownership_probe.lua \
  src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2 \
  s3k.gen
```

BizHawk exited successfully and produced 43 paired RNG records in the
worktree-local scratch artifact `target/icz-rng-native.txt` (SHA-256
`1cb145cad2592f3de716133820bdd205b4b97111958a99f12119d033f94f43f5`).
The semantic camera gate armed at trace f17118; the capture ended on the first
snow return at f22733. It directly proves the native owner sequence only
within that late window:

| Trace frame | Return PC | SST | Native transition |
|---:|---:|---|---|
| 22486 | `$02C92E` | slot 5, `Obj_Animal`, routine `$02`, x/y `$4169/$06E3` | `528B07B3 -> 73EF3BAB` |
| 22733 | `$08B6C2` | slot 10, snow particle, routine `$02` | `73EF3BAB -> 1FB38E63` |

`$02C92E` returns from the subtype-zero animal RNG call at `loc_2C924`
(`docs/skdisasm/sonic3k.asm:61049-61055`). `$08B6C2` returns from the snow
particle RNG call in `loc_8B6AE`
(`docs/skdisasm/sonic3k.asm:189957-189985`).

This capture does **not** cover f14502. The slot-20 contrast at that earlier
frame comes from the committed native per-frame RNG/SST snapshots crosswalked
against temporary engine allocation logs and the disassembly allocation
routines. It remains a narrowed hypothesis requiring an aligned native
allocation/dispatch probe before a production fix.

## First persistent ownership mismatch

The first one-frame seed skew is f2661 and realigns at f2662. Several other
short timing skews also realign. The first mismatch that does not realign is
f14502:

| Frame | Native owner/result | Engine owner/result |
|---:|---|---|
| 14501 | no call; seed `E9697A23` | no call; seed `E9697A23` |
| 14502 | miniboss explosion controller: `E9697A23 -> F17F8F9B` | same controller draw, then snow particle: `F17F8F9B -> AD40FFD3` |
| 14503 | seed remains `F17F8F9B` | seed remains `AD40FFD3` |

The extra engine consumer was a first-dispatch `SnowdustParticle` in slot 20,
owned by an `IczSnowPileObjectInstance` emitter in slot 5. Native f14501 has
already retired the `loc_8B660` emitter SST: native slot 5 is code
`$0001CEF2`, subtype `$0D`. At f14502 native slot 20 is instead code
`$00083F68`, routine `$02`, created by the miniboss explosion topology.

The ROM emitter at `loc_8B660` increments its timer/count and calls
`AllocateObject`; only an allocated child later reaches `loc_8B6AE` and calls
`Random_Number` (`docs/skdisasm/sonic3k.asm:189930-189985`).

The initial conclusion that late emitter retirement caused the extra draw was
disproved. A focused experiment implemented the ROM owner boundary:
`BossDefeated` writes `$3F` to the boss body's `$2E`, and
`Wait_FadeToLevelMusic` reaches `loc_713E8`, which stops the active
`_unkFAAE`/`loc_8B660` emitter while the independent
`Child6_CreateBossExplosion` SST continues. Instrumentation showed the engine
stopping slot 5 at counter 21498, after its last snow-child first dispatch at
21492. No child first-dispatched after the stop, and the replay remained
exactly 29 errors with first error f24140. Moving the whole signpost flow to
that boundary introduced a separate f14779 control regression. All
production/test experiments were reverted.

The remaining discrepancy is allocation topology before the stop boundary:
the engine gives the snow child slot 20, while native slot 20 belongs to the
miniboss explosion topology. `CreateChild6_Simple` uses
`AllocateObjectAfterCurrent` and only calls `Random_Number` after successful
child allocation (`docs/skdisasm/sonic3k.asm:176737-176785,177119-177139`).

## Ice-cube allocation versus first dispatch

Temporary engine logging recorded both examined cube shatters. Every one of
the twelve children received a real SST slot and all twelve dispatched:

| Native frame / engine object frame | Parent | Child slots | Result |
|---|---|---|---|
| f19134 / 6802 | slot 18, x/y `$26C0/$00B3` | 20-31 | 12 allocated, 12 RNG draws |
| f19863 / 7531 | slot 16, x/y `$2CC3/$02F6` | 20-26 and 28-32 | 12 allocated, 12 RNG draws |

The native SST snapshots show the same parents, positions, child subtypes
`$00..$16`, and child routine `$02`. `CreateChild1_Normal` stops on allocation
failure (`docs/skdisasm/sonic3k.asm:176924-176957`); each allocated child
first dispatches `loc_8B432`, which then calls `Random_Number`
(`docs/skdisasm/sonic3k.asm:189690-189705`).

Consequently, the previously suspected eager `IceCubeDebris` construction is
not the observed owner of this trace's persistent mismatch. No examined child
failed allocation, and constructor-versus-dispatch timing does not explain
the f14502 extra call. The broader constructor-order concern remains a
separate architectural risk, but it must not be presented as the ICZ
f24140 root cause without a failing allocation reproduction.

## Downstream seed and owner crosswalk

The f14502 extra draw cascades through later owners:

| Event | Native | Engine |
|---|---|---|
| f22486 animal | one animal, `528B07B3 -> 73EF3BAB` | fourth engine animal consumes `1A2FF813 -> ECB9BB0B` |
| extra engine animals | none in that native dispatch | three additional animal draws occur before the corresponding late animal: `73EF3BAB -> 1FB38E63 -> E19CCDDB -> 1A2FF813` |
| f22733 first snow | `73EF3BAB -> 1FB38E63`, return `$08B6C2`, slot 10 | begins only after the four engine animals, from `ECB9BB0B` |

The three extra engine animal draws are therefore accounted for as downstream
object-lifetime/topology consumers, not independent RNG math errors. Native
uses those same successor values later for periodic snow calls at f22733,
f22742, f22751, and f22760; the engine has already spent them on animals.

## f24140 ring event

The first reported error is a missed collection, not a ring-counter write
discrepancy. Native rings change from 2 to 3 at f24140. Immediately before the
change, Sonic is near `$43ED/$06AD`; native lost-ring slot 35
(`Obj_Lost_Ring`, code `$0001A64A`) is at `$43FD/$0696`, within collection
range. The engine stays at 2 because its scattered lost-ring geometry differs.
That geometry is downstream of the persistent RNG ownership skew, so the
correction belongs to the earlier allocation mismatch—not ring counting,
collision tolerances, or an f24140 exception.

## Recommended narrow RED tests

Before a production correction, add tests at the owning lifecycle boundary:

1. A saturated/ordered object-manager test where the explosion controller and
   snow emitter compete for slot 20; assert the same
   `AllocateObjectAfterCurrent` winner and failure result as native.
2. A controller test which places the ICZ miniboss explosion owner in its
   observed slot and verifies its child receives slot 20 before the earlier
   slot-5 emitter's plain `AllocateObject` call.
3. A seed ownership test pinned to `E9697A23`: the explosion controller alone
   must advance to `F17F8F9B`, with no second draw from snowdust.
4. A trace-side object-lifetime assertion around f14501-f14503 comparing the
   semantic emitter presence and slot-20 owner, without copying trace state
   into the engine.

The fix owner should be the smallest inaccurate SST occupancy or
after-current allocation owner proven by that RED test. Do not special-case
trace frame 14502, the complete-run route, the RNG seed, ring count, or ICZ
inside shared allocation code.
