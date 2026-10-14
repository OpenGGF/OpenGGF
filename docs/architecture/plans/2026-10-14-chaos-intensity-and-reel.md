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
Native child `fba7cef04fc5d7026e0080e2a6b284a9f82fac9f` is integrated as
`352a2a40a`. Its final affected command was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestScopedPalettePresentationRewind,TestUnderwaterPaletteUpload,TestScopedPaletteGraphicsAccess,TestPooledRenderCommandDiscard,TestModApiSignatureSurface' \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

On the committed inputs: 28 passed, zero failures/errors/skips. Three real GPU
methods cover dry, priority-water and acquisition between draws. Nine water
checks cover exact-world rebinding, published fade/basis and pending writes;
command leases and canonical API signatures also pass. Earlier retained native
runtime/ring checks passed 59 cases and bumper/ghost checks passed 23, with no
skips. Shared public API signatures remain unchanged. A textual hook mistook a
private nested record inside an annotated class for a supported API delta;
private lease metadata was moved to an unannotated package-private record,
retaining the actual canonical pin and passing the normal hook.

Independent read-only review found a real underwater blocker before integration:
private rows were absent from the expanded underwater texture. Below-water
priority sprites sampled transparent rows. Adding those rows alone exposed two
ordering defects: an ordinary batch could use the new row count with the old
texture height, and deferred uploads could overwrite the active atlas binding
while cached IDs prevented rebinding. Published native water/fade inputs now
rebuild derived residency at execution boundaries, preserving pending native
writes; scoped uploads/releases and rebuilds invalidate command binding caches.
The failing real pixel cases were reproduced before the repairs and pass after.

The private-palette change initially imposed a 64-row limit on ordinary donor
contexts. A matched baseline/candidate probe reproduced the regression: the
sixteenth ordinary context previously grew the atlas to 68 rows, whereas the
candidate threw. Child `04d183234` is integrated as `45fff2130`; ordinary contexts
retain dynamic growth, while private banks scan only complete, unoccupied ranges
within their bounded pool. The two kinds coexist without reserving the same
rows. Focused tests cover more than sixteen ordinary contexts, high-row lookup,
fifteen private banks, exhaustion and safe reuse.

Current `develop` was composed at `97880ca2f`. Read-only upstream review retained
creator service-factory and callable-dependency discovery. The final composed
focused run selected fourteen affected classes, including ordinary/donor palette
allocation, live presentation, native ring actors, scoped palette/SAT rewind,
actual underwater pixels, API signatures and Second Strike monitor/art seams:
**74 tests passed, zero failures/errors/skips**. It used Java 25, Lua 5.4, the
verified absolute S3K ROM and actual production surfaceless EGL. These checks
qualify those consumers, not every act or a rebuilt Windows native bundle.

SDK packaging on the same host commit passed with **zero findings** and warnings
treated as errors. The external example JAR is 80,855 bytes, SHA-256
`e5460b29643b176683ba0a62eaeeff413a24d717aed112c9e84294ae36be1d02`;
it contains no ROM bytes and targets the unchanged 0.7 candidate API.

The completed second reel is 4,864 frames at 60 Hz, 1920×1080, lasting 81.067
seconds with 45 chapters and **46 qualified effects**. Actual source CSV/picture
review includes bumper contact, angled full-width rain, ring loss and subsequent
native recollection, and the correctly coloured ghost reveal. Split-screen
panels use independent captures rather than implying simultaneous policies in
one world. Buddy arrival and board movement are shown without claiming a rescue
or a completed minigame. Every cut and midpoint was inspected in the decoded
export; full video/audio decode, cadence, chapters and source intervals pass.
The continuous HCZ2 bed and explicit editorial ROM cues measure −14.28 LUFS and
−1.45 dBTP in the final AAC. Listening review is not claimed. The audio remux
preserved all 4,864 encoded H.264 packets and their timestamps.

Thirty-six new lossless masters were compared against every captured RGB frame
and native PCM sample before redundant raw outputs were removed. Authored input,
state CSV, initial conditions, original masters and both reels remain in the
external task archive. The public production sources and external asset catalogue
are published in [OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/sonic3k-chaos-mode)
at `494a17f2a38aa775aef5fab1a5aedd4444bcc409`. That publication preserves 451
Chaos sources, including the unchanged first reel and prior draft. Repository
catalogue checks verified 6,515 source hashes/sizes and 14,118 external entries;
eight public committed paths were fetched and matched byte-for-byte. Footage,
ROM assets, JAR and source ZIP remain external.

Java 25/Lua 5.4/PowerShell preflight passed for the complete change-based smoke
selection of 3,256 candidate classes and separate guards. The first invocation
on `543c076fd` completed 59 of 66 ordinary batches: 25,369 tests, zero
failures/errors and 61 explicit skips. It then exited 130 through the signal
handler before batch 60 completed; no execution/idle timeout was reported and
the signal's origin was not independently recorded. This is an incomplete run,
not a smoke pass. Its owned children and retained worktree lease released.

The continuation rederives the complete original selection and checks the exact
source/tree fingerprint, completed-batch count and previous failure status.
Every one of the 306 uncompleted candidates (seven original batches), followed
by guards, uses the unchanged official runner and admission rules. Completed
coverage remains attributed to the first invocation; no raw logs or pass cache
are archived. The final record must report the two-command union and all skips.
Continuation and engine integration/push remain pending at this checkpoint.

| Context | Added obligation | Limit |
|---|---|---|
| Native Sonic crowd, 320/640 | Native admitted poses, opacity, distinct frozen art, rewind, owner bank exhaustion | Collision and other character/team routes are unchanged; complete act routes remain inherited |
| Ring rain, 320/400/800 | Width-scaled coverage, slight native angle, bounce, inventory/actor budget, lifetime and restore | No claim for every native terrain/transport context |
| Leaky Pockets | Trailing placement, delayed pickup, native recollection, spend only on success, last ring and post-expiry lifetime | Promo declares initial carried inventory |
| CNZ bumpers/SOZ ghosts in foreign native acts | ROM palette pixels, exact owner/descendant scope, unchanged host/donor colours, SAT restore after residency changes | No whole-scene or all-act route certification |
| Final example/package | Stable catalogue/schema, assembly/history and SDK validation | No new streamer/voting capability |

No trace frontier moved, and no timing optimization or measurement acceptance
is claimed. Required S3K route/load checks remain part of final integration.
