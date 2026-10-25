# S3K native competition upfront placement prerequisite

The explicit native S3K world binds one upfront object-placement policy before
reset. This is a prerequisite with no menu, level-profile or title activation.
It follows shipped FixBugs=0 source, independently of S2's dual-camera loader.

## Source contract

`loc_1B6CA` (sonic3k.asm:37469-37480) clears the ordinary `$300`-byte
Object_respawn_table unless Respawn_table_keep is set. `loc_1B720`
(sonic3k.asm:37499-37525) advances Object_load_routine to terminal routine 12
before its first AllocateObject probe. That routine dispatches directly to RTS
(`Load_Sprites`, sonic3k.asm:37433-37448). Full RAM therefore terminates the
initial load permanently, rather than parking a camera cursor for retry.

The first allocation probe precedes the first X comparison. Records are read
in authored order until the first unsigned X >= `$800`, with no Y filter or
camera eligibility check. The unrelated `$1701` zone/act exception is outside
this explicit competition policy. `sub_1BA0C` (sonic3k.asm:37829-37847) writes
placement X, masked Y, flips, object routine and subtype, then tails into
`CreateNewSprite4` (sonic3k.asm:37934-37944). Subsequent probes scan strictly
forward through the original allocation budget, including after the final
record. A failure keeps already written objects and completes the routine.

`AllocateObject` (sonic3k.asm:37951-37968), with the RAM layout in
sonic3k.constants.asm:310-316, probes global SST slots 4 through 93 inclusive.
The existing ObjectSlotLayout and SlotAllocator remain the occupancy authority;
this change does not edit their allocation or reservation methods. Upfront
placement never requests the S2 general allocation cap.

Every record receives the SAME `_unkF712` respawn address. Neither the caller
nor sub_1BA0C increments that pointer, tests its byte or sets bit 7. The controller
represents this separate byte beyond the ordinary per-entry table in its
existing captured state array. It retains full-width spawn-counter mappings
on restore. The common loader reset does not clear this separate scratch byte.

`loc_1B74C` (sonic3k.asm:37516-37526) fixes Camera_X_pos_coarse_back to zero.
Standard `Sprite_OnScreen_Test` (sonic3k.asm:37302-37318) still deletes when
unsigned quantized X exceeds `$280` and clears bit 7 at respawn_addr. The shared
manager therefore retains that fixed-origin standard cull, while each object's
custom range and persistence rules retain authority. Direct
`Delete_Current_Sprite` (sonic3k.asm:36148-36167) does not clear the byte.
A universal competition cull bypass and the unused two-camera cull routine
would both differ from shipped source.

## Integration

```java
ObjectManagerInternalAccess.enableNativeCompetitionUpfrontPlacement(objects, 0x800);
objects.reset(initialCameraX);
rewind.registerOrRefresh(
        ObjectManagerInternalAccess.nativeCompetitionUpfrontPlacementRewindAdapter(objects));
```

Register the ordinary object snapshot as well. The private adapter key is
`native_competition_upfront_placement`; its immutable payload contains only the
initialization flag and consumed source-record index. The existing object
snapshot owns actors, slots, respawn bytes and spawn mappings. Restore both
before forward execution. Restoring a pre-init state allows its first load;
restoring a terminal state, including full RAM, never rearms loading.

The enable call requires an active explicit NativeCompetitionRoster. Upfront
and S2 streaming configuration reject one another. The loader preserves the
original input list independently of the ordinary camera-column sort, including
duplicates distinguished by layoutIndex. Ordinary worlds use the existing
placement path. Deferred reset/hydration materializes the one-shot pass when
the ordinary initial preload is invoked; camera movement, deletion, post-camera
placement and later preload calls do not restart it. An explicit editor spawn-list
resync starts one pass over its replacement source and resizes the separate
scratch slot when the layout grows.

No per-camera upfront cursors exist to swap. Real S3K load-profile binding,
scroll/camera setup, domain-specific persistent objects and native-world rewind
registration belong to the integrating owner.

## Verification

Focused tests cover source ordering across ordinary columns, ordered duplicates,
`$7FF` versus `$800`, Y/camera independence, all 90 slots, initial full RAM,
strictly forward allocation after an earlier hole opens, deletion/reset,
shared scratch low/high bits, ordinary-world isolation, mutually exclusive
policies, and composite object/two-camera rewind restoration before and after
initialization with forward replay. A separate full-RAM checkpoint proves that
restore cannot rearm the load.

The existing Sonic3kObjectPlacement parser supplies actual ROM records for all
five competition layouts: ALZ `0x1FC560` (8), BPZ `0x1FC5A2` (15), DPZ
`0x1FC60E` (17), CGZ `0x1FC686` (15), EMZ `0x1FC6F2` (15). These are the
SpriteLocPtrs entries (sonic3k.asm:202539-202548). All current records in these
layouts are below `$800`; synthetic boundary and failure cases establish the
remaining loader branches independently of those data. Runtime bytes come only
from the ROM pipeline, with CRC32 `63522553` and SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6` verified locally.

The first focused run on base `6e67d8b98` plus this change passed 78 tests,
zero failures/errors/skips, including the five real-ROM layouts. Review then
identified the explicit editor resync reset omission; a focused growth/resync
regression was added with the bounded correction. The final run includes that
regression and the repository's named S3K ordinary-path checks. That run completed
139 tests with one failure in the new resync growth case and zero skips. The
other 138 checks passed, including all 60 named S3K ordinary-path cases. The
resync failure exposed a second half of the same boundary: ordinary reset
clears slot occupancy after clearing object maps, while editor resync does not.
The upfront-specific resync now releases the discarded placement owners' SST
slots and preserves unrelated dynamic owners; the regression covers both.
Its combined command was:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestNativeCompetitionUpfrontPlacement,TestNativeCompetitionObjectStreaming,TestObjectManagerVerticalPlacement,TestObjectManagerRewindSnapshot,TestObjectManagerInitialS3kSetupPass,TestObjectManagerFixedSstInitialization,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM_PATH" test
```

DISPLAY and WAYLAND_DISPLAY are unset for the invocation. After the gated
resync correction, only the affected seven-class manager/native/API selection
is repeated, using `--lean` without the GL flag and the same absolute ROM path:

```text
TestNativeCompetitionUpfrontPlacement,TestNativeCompetitionObjectStreaming,
TestObjectManagerVerticalPlacement,TestObjectManagerRewindSnapshot,
TestObjectManagerInitialS3kSetupPass,TestObjectManagerFixedSstInitialization,
TestModApiSignatureSurface
```

Final affected run: 79 tests passed, zero failures/errors/skips (including all
12 upfront cases and the five ROM layouts), Maven execution 1:25 and no admission
wait. The already passing S3K ordinary level checks
are not repeated for a change gated to explicit upfront editor resync.
The strict citation check on the new policy, focused test and this note has
no stale findings. Existing unrelated manager citation issues are unchanged.
No broad smoke, full suite, S2 replay sweep, actual native-world activation or
render qualification is claimed by this prerequisite.
