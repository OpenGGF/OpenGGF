# AIZ2 End-Boss Splash Evidence

Date: 2026-08-09

Branch: `feature/ai-aiz2-splash-evidence`

Base: `origin/develop` `9de7ecf7230100626fb7084b3f678daa6a5f478c`

## Disposition

The AIZ2 end-boss waterfall splash is now source-verified, exercised through
the ROM-backed production event/object route, and independently evidenced by
the committed native BizHawk complete run. Two lifecycle defects were found
and corrected:

1. The engine deleted the child immediately when its raw-animation callback
   fired. ROM `Go_Delete_Sprite` instead installs `Delete_Current_Sprite` and
   status bit 7, returns, and leaves that operation in the SST until the next
   object dispatch.
2. The child inherited the engine's generic off-screen unload. None of its ROM
   live routines calls `Sprite_OnScreen_Test`, `MarkObjGone`, or another range
   tail; its animation callback is its only lifetime owner.

This is not a claim that the full Java AIZ complete-run replay is green. That
comparison remains blocked before gameplay by an existing v5 hardware-timing
schedule-admission error described below.

## ROM audit

| Contract | Native source | Engine result |
|---|---|---|
| Emerge allocation | `AIZEndBoss_StartEmerge` loads `ChildObjDat_AIZEndBossWaterfall` and jumps to `CreateChild1_Normal` (`sonic3k.asm:138080-138103`) | Subtype 0 uses forward allocation from the boss slot. |
| Re-submerge subtype | `AIZEndBoss_StartSubmerge` allocates the same child, then writes subtype 2 through returned `a1` (`sonic3k.asm:138193-138203`) | The allocated child captures effective subtype 2 before its later slot dispatch. |
| Init boundary | `AIZEndBossWaterfall_Init` calls `SetUp_ObjAttributes2`, installs the animate operation/callback, and returns (`sonic3k.asm:138701-138716`) | A higher-slot child performs init only in the allocation pass; it neither animates nor draws. |
| Mapping and movement | Both raw scripts contain thirteen zero-delay pairs before `$F4`; subtype 2 installs `y_vel=$800`, then `MoveSprite2` precedes animation (`sonic3k.asm:138718-138739,139193-139221`) | Initial frame `$24`, FlipX toggles, callback boundaries, no-move start-drop row, and +8px drop steps match. |
| Art attributes | `ObjDat_AIZEndBossWaterfall` uses AIZ boss art, palette 0, high-priority art, priority `$100`, mapping `$24` (`sonic3k.asm:139024-139026`) | ROM-backed AIZ end-boss renderer, explicit palette 0, high priority, bucket 2, mapping `$24`. |
| Deletion | `Go_Delete_Sprite` writes `Delete_Current_Sprite`, sets status bit 7, and returns (`sonic3k.asm:179136-179143`; locked-on ROM `$852A0 -> $1ABB6`) | A rewind-captured pending state retains the marker for one dispatch, then destroys the object. |
| Range lifetime | Init, animate, start-drop, and drop contain no off-screen delete call (`sonic3k.asm:138701-138739`) | Object-local `isPersistent()` prevents the shared synthetic range tail. |

`Animate_RawNoSSTMultiDelayFlipX` pre-increments `anim_frame` by two before
reading a pair (`sonic3k.asm:177628-177650`). That explains why ObjDat's
initial mapping `$24` remains visible through init and the first animation
dispatch consumes the script's second pair, also `$24`; skipping or
pre-consuming the first pair in Java would be incorrect.

## Native end-to-end evidence

The existing fixture at `src/test/resources/traces/s3k/aiz_completerun` is a
26,228-frame `complete_run` segment recorded by
`native-bizhawk-headless` 3.0 with BizHawk 2.11/Genplus-gx from
`s3k-complete-sonic-tails.bk2`. It covers AIZ1, the seamless AIZ2 transition,
the AIZ2 end boss, and the next-zone handoff. Its payload SHA-256 values are:

| File | SHA-256 |
|---|---|
| `physics.csv.gz` | `1b60c8120b60aaf815e42123a58307e1b088201da6d6767c9703e40d16a51ce8` |
| `aux_state.jsonl.gz` | `e580a0d5cf616cbc8ffbe0f46d565cf9a9ff2425a4ee5156da1f6af5ac6829bb` |
| `hardware_timing.jsonl` | `b8ebb4662c7361984e21541824166fbd597970171eed5025b6fdadbee6b4df24` |
| `metadata.json` | `93f47c154d0bc83cf38a2a3c4b987a13a7796dd92cfae1508b58b61c0d1d8de4` |

The comparison-only aux stream records these native transitions:

| Trace frames | Native observation |
|---|---|
| 23508 -> 23509 | Boss slot 5 changes from `$6923E` routine `$0C` to routine `$02`; newly allocated slot 6 already reports `$699EA`, subtype 0, at the boss's `$4938,$01CF` position. This is the post-pass signature of forward allocation plus same-pass init. |
| 23509-23521 -> 23522 | Slot 6 remains `$699EA`, then retains `$1ABB6`, status `$80`, for one row before removal. |
| 23829 -> 23830 | Boss changes routine `$08 -> $0A`; with slots 6-14 occupied, the child takes first-free-forward slot 15 as `$699EA`, subtype 2, at `$4938,$01CC`. |
| 23842 -> 23843 | Slot 15 changes `$699EA -> $69A1A` without moving, matching `AIZEndBossWaterfall_StartDrop`. |
| 23844-23856 | Y advances `$01D4,$01DC,...,$0234`, exactly +`$8` per dispatch; frame 23856 retains `$1ABB6`, status `$80`, before removal at frame 23857. |

Later cycles repeat the same signatures (including subtype-0 frames
23971-23983 and subtype-2 start-drop at frame 24305), so the evidence is not a
single accidental occurrence. Trace data was read only as comparison evidence;
it is not consumed by production gameplay or the focused tests.

## Production and rewind evidence

`TestSonic3kAIZEvents#aiz2EndBossSplashChildrenRunThroughLiveEventAndSlotTimeline`
boots ROM-backed AIZ2 through `HeadlessTestFixture`, executes the normal
object-before-dynamic-events frame order at the Sonic arena, and verifies:

- the layout boss wins over the event fallback, with no duplicate boss;
- ship, arm, and splash allocations produce the exact fourth
  first-free-forward slot on initial emerge;
- the higher-slot splash consumes init in the parent allocation pass but does
  not draw or advance mapping;
- both thirteen-dispatch callback boundaries, the subtype-2 handoff, thirteen
  +8px drop steps,
  one-dispatch delete marker, and following slot clear;
- the retreat splash uses the exact first free slot at its allocation point.

`TestS3kAizEndBossGraphRewind` independently exercises both subtypes, the boss
position snapshot, ROM palette/mapping call, forward allocation under occupied
slots, active-drop restore, and a second rewind taken while
`Delete_Current_Sprite` is installed. The restored marker retains its object
identity and deletes only on its following update.

JDK 21 focused verification selected the complete AIZ event class, boss and
rewind owner tests, ROM art/KosM structural tests, and rewind architecture,
field-disposition, link-tolerance, graph-classification, dynamic-coverage, and
static-state guards: 116 tests passed with no failures, errors, or skips. After
the final production-order and render-priority assertions, the route test
passed 1/1 and the graph/rewind class passed 5/5. The committed timing-fixture
and trace-compression guards passed 4/4.

## Replay and recapture status

The strongest Java comparison command was run on JDK 21:

```bash
mvn -Ptrace-replay -Dmse=off -Dsurefire.forkCount=1 \
  -Dsurefire.runOrder=alphabetical \
  -Dtest=com.openggf.tests.trace.s3k.TestS3kAizCompleteRunTraceReplay \
  -Ds3k.rom.path=s3k.gen test
```

It errors before gameplay while compiling the timing schedule:

```text
unsupported-row-POST: raw_frame=6351 has no scheduled object/POST phase;
phase=VBLANK_ONLY; kind=KOS_MODULE_QUEUE, ordinal=16;
reason=unsupported-held-row-POST
```

This is an existing trace-infrastructure admission issue, not an observed
splash mismatch. The exact next comparison action is to model that held
`VBLANK_ONLY` POST observation within the existing cross-game hardware-timing
contract, then rerun the command above unchanged.

A scratch native recapture was also attempted with the documented command, but
the harness correctly refused before creating output because this environment
does not contain the required BizHawk 2.11 directory:

```bash
BIZHAWK_HOME=/absolute/path/to/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom s3k.gen \
  --movie src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2 \
  --output /tmp/aiz2-splash-native-20260809 \
  --trace-profile complete_run
```

Once BizHawk 2.11 is available, use a new non-existent output path, verify the
ROM CRC32 is `63522553`, run that command, and compare the emitted AIZ segment
and manifest/metadata against the hashes and native transitions above. Do not
replace the committed fixture merely to make replay admission succeed.
