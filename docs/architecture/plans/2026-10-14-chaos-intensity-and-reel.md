# Sonic3K Chaos intensity and reel revision

Base: `5b2ff8e7851b99dd40998dfc41fbfd21c80031ef` on `develop`.
The original 105-effect implementation and first reel remain preserved. This
revision follows the user's request for a much worse Sonic crowd, recoverable
rings left behind, full-screen angled ring rain, correct bumper/ghost colours,
and a faster promo with continuous music and split-screen cuts.

## Runtime changes and decisions

The crowd child `be206cd3326c6291c7ba9c04a96d489051882f4b` is integrated as
`ee0400cb3a295d0c4ded8d9336e772c536da3b18`. Too Many Sonics expands from four
faint poses to 24 poses at 65–95% opacity, sampled every three active
presentations. Each pose admits at most 256 native tiles, freezing the actual
DPLC pixels, flips and priority. Private residency expands in step to 6,144
slots per owner, fitting ten complete banks within the existing atlas range.
An incomplete final range is never leased. Native collision and suppression
retain authority, and expired/restored logical state releases or reacquires
only its own residency.

Ring rain uses the full current viewport width rather than a player-centred
column. A separate 48-ring quota supports native lost-ring actors without
exhausting the ordinary 16-root encounter budget. Slight native horizontal
velocity gives the falling rings an angle; bounce and collection remain native.
Leaky Pockets spends rings only after native actor admission, leaves them
behind actual movement, delays pickup for 24 native ticks and preserves the
last carried ring. Its five-second independent lifetime survives lease expiry.
Rain's owned actors clear on lease expiry. Recreating these independent actors
must not attach the native hurt-spill countdown, which can legitimately be zero.

Spawned CNZ bumpers and SOZ ghosts keep native mapping palette bits and use
ROM donor palettes. Bumper art starts at CNZ Misc + `$13` with palette 2;
Hyudoro's body selects palette 1 from `Pal_SOZ2` (`$A955C`). Sandopolis's room
lighting changes palette lines 2/3, so darkening the ghost's line 1 was rejected.
Writing donor colours into host lines was rejected because it recolours native
level actors and interacts with donor/sidekick contexts. Private four-line
residency instead belongs to exact runtime/object/donor identity and its host.

Private GPU row numbers must not enter retained SAT snapshots. Published tiles
keep native palette bits; a trusted semantic subject resolver obtains current
residency when drawing, including after rewind and a different allocation order.
Logical state never restores a stale or reassigned GPU handle.

The 105 stable effect IDs, public policy schemas and settings format are
unchanged. `ring-leak` is displayed as **Leaky Pockets**. No ROM bytes are
bundled in the mod package, and no native zone palette is replaced.

## Media plan and capture ownership

The user requested Opus assistance. A completed T3-owned Claude Opus 5.5
planning round produced the external revision's `PLAN.md` and `edl.json`.
Its cut uses one ROM-backed HCZ2 music bed, absolute beat boundaries rounded
to 60 Hz, rapid full-frame shots and slanted pair/triple/quad layouts. Short
navy/gold ROM-glyph cards and fictional reviews preserve the earlier demos'
humour. Source music/SFX are mixed, so their tracks are muted rather than
claiming separable native effects; selected editorial ROM stings are explicit.

Durable media lives outside the repository in the dated Chaos demo task
directory, with a separate `revision-2/` subtree. Original masters and the first
reel remain unchanged. Each new master records source commit, authored input,
package/ROM hashes, declared initial conditions and actual presentation cadence.
Leaky Pockets starts with a declared 32-ring inventory for the loss shot; those
rings are not represented as having been earned within the clip.

At approximately 08:50 UTC an exclusive S1 allocator diagnostic detected an
unqueued Java child from this root's reel batch and correctly stopped before
Engine launch. The root stopped its batch after the active child finished.
Per-shot PIDs had not been retained, so adjacent capture logs cannot establish
which clip owned exited PID 4000062. Subsequent input authoring, capture,
compilation and SDK packaging use shared execution admission, pass the lease
descriptors to children, record PID/UTC intervals, and release between clips.
Existing queued requests were preserved; no foreign process or lock was touched.

## Verification and affected contexts

The crowd child's focused command was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestLivePresentationRuntime,TestPresentationGraphicsAccess,TestPresentationSpritePixels' \
  -Dopenggf.headless.gl=surfaceless-egl test -B
```

On `be206cd33`: 13 tests passed, zero failures/errors/skips, including actual
surfaceless-EGL pixels at 320/640, 24-pose capture/restore and complete-bank
exhaustion/reuse. These are child results, not final-union qualification.
Java 25/Lua 5.4/PowerShell preflight passed on the root worktree. Final native
palette/SAT restore checks, combined validation, package, captures and delivery
remain pending at this checkpoint.

| Context | Added obligation | Limit |
|---|---|---|
| Native Sonic crowd, 320/640 | Native admitted poses, opacity, distinct frozen art, rewind, owner bank exhaustion | Collision and other character/team routes are unchanged; complete act routes remain inherited |
| Ring rain, 320/400/800 | Width-scaled coverage, slight native angle, bounce, inventory/actor budget, lifetime and restore | No claim for every native terrain/transport context |
| Leaky Pockets | Trailing placement, delayed pickup, native recollection, spend only on success, last ring and post-expiry lifetime | Promo declares initial carried inventory |
| CNZ bumpers/SOZ ghosts in foreign native acts | ROM palette pixels, exact owner/descendant scope, unchanged host/donor colours, SAT restore after residency changes | No whole-scene or all-act route certification |
| Final example/package | Stable catalogue/schema, assembly/history and SDK validation | No new streamer/voting capability |

No trace frontier moved, and no timing optimization or measurement acceptance
is claimed. Required S3K route/load checks remain part of final integration.
