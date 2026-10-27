# Sonic CD handover — paused after Sonic Hole

Source milestone: 26 October 2026; delivery: 27 October 2026. The ordinary controller route returns through **5414**; first **5415** reaches unsupported ordinary time warp. Sonic Hole is implemented within the limits below. Full USA completion, followed by Japan/Europe certification, remains unfinished.

[Delivery branch](https://github.com/OpenGGF/OpenGGF/tree/feature/ai-nothing-to-see-here) is based on pinned next **58e1abb44c716415bf308cc591776d2429c85a41**. The verified Java implementation is **a8e73909bc287431b75e1078c3af331898a02097**. Final code/reference commit **be5e5594f10e03a642a45f5a4d4327a04dcb5c81** adds only the reference-tracking corrections below and was pushed with its remote SHA verified on 27 October. Next remains in first-parent ancestry; private final **2d9c2734bb087c6115ac135b40abe6a3abf49cab** is retained through merge **affc54d14c0280a7432f5ab52f108533eb3b7b35**. This stable code pin precedes the documentation-only handover commit.

Implementation is **paused** after this delivery. No further implementation, main mutation/switch/push, other-branch push, PR, tag or release is authorized. Main stays `develop`.

## What works and where it stops

Implemented components cover authenticated disc/release/source admission, normal WorldSession/GameLoop integration, player movement, terrain, camera/SAT/Plane B, placements, touch/Solid/object owners, current-art PLC service, life/death/restart/checkpoint, full/course rewind and ordered sound intent. Static entry and the playable DEV opening-object entry remain distinct.

The permanent input holds Right throughout and Start false; B is pressed on **1201–1232, 1770–1801, 3002–3033, 5201–5232**. Sonic Hole, physical slot **46**, executes Init **5323** and Main **5324**. Its body reads existing raw2C control, conditionally reads X/Y, handles FM_A3/saved bit6 and Draw/reload, including saved value 0. Source17/C31/664 spans are unchanged: no new Frame writer, art, bank, Region or C32.

On completed **5414**, TimePost requests `SCMD_PASTSFX` and raw direction **255**, with timer/charge **0/0**. Call 5415 throws `CdPlayerMovement.UnsupportedMotion`, whose `boundary()` is `CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE`; Runtime/Frame close, preserving the original exception and suppressed cleanup. Observed Body, Environment, full 96 and saved 766 remain unchanged; VInt advances **5414→5415**. This is a terminal failed composite with completed prefixes, not a healthy checkpoint.

Natural control stays 0: no Hole activation, A3, natural Hole pixels or controlled Sonic Body is proved. Full/course cuts retain all previous edges and add **5322→5323, 5323→5324, 5413→5414**, comparing complete checkpoint-aware state before and after every forward replay.

## Validation and source corrections

Counts use **tests/failures/errors/skips**. These are separate bounded runs, not a full-suite or native-parity result.

- Private metadata `35566`: **180/0/0/0**; component `91006`: **591/0/0/0**. Runtime `56391` was **494/0/1/0**, missing a factory-fixture art sink. Two test-only RECORDING lines repaired it: narrow `17880` passed 1; fresh `52519` passed **494/0/0/0**, 21:14:30Z. Separate permanent World `78472` passed **53/0/0/0**, 21:27:25Z. These are not one 547-case run. Hole static reviews have no open P1/P2; the temporary frontier observer was removed.
- Shared owned-PLC fixture `25647` was **235/0/1/0**. Test splitting preserved raw-token correction and terminal owned-token retirement; normal `74348` passed **32/0/0/0**. Lean `12381` exhausted javac's 1 GiB heap before JUnit: failed build, not a pass.
- Focused `66364`, 22:52:34Z, at affc plus five dirty Java files: **684/2/1/0** across 19 classes. Runtime 486, actual 5414 route, EGL 3, scopes 32, HCZ 8, native rewind 9, competition 23, GPU 2, Stageplay 2 and mod signature 11 passed. Two fixture issues and the pre-existing assertion guard kept this run red.
- Follow-up `38153`, 23:23:19Z: **67/0/0/0**, World 53+Cinematic 14, including actual 5414 route; Maven execution 41.319s, queue 1224s. World R2 conditionally guards only two opening-entry snapshots; cinematic tests remove the obsolete capture spy and verify exactly two full native preparations, each refresh plus eight ordered buckets, without live-clock/gameplay calls. This is not a fresh all-green 684 or 751 run.

The fresh class/case identities, XML, Java 25/cwd, actual media, authenticated S2/S3K, surfaceless rendering and explicit EGL opt-in were checked for affected runs. The 67-case follow-up's unchanged tracked diff `26828091b59dc1a04bc85a75ff8895a638605edf463c3d95bb46b0eddefb371d` was committed identically as a8: six files, 497 additions/13 deletions. Production remains unchanged since renderer R2 `87d8b64c2d6226077af8ce9345c5e8b64af5fed8ffe277a52157a4f602585a31`.

That generic renderer correction admits actual CPU/SAT preparation through its private core inside an installed scope; the public setup guard remains. All 32 scope checks passed. Shared rewind fixtures use private projections of real adapters; HCZ 8/native 9 passed without production scope-guard changes. Generic directed CD cinematic bounds remain unsupported: outer setup must reject before the CD callback.

Matched tests reproduce on clean next 58e1: CNZ 95282 and NoAssertionFreeDiagnostics 14048 are each **2/1/0/0**; DEZ 10939 is **23/23/0/0**; LRZ/MHZ/HPZ 30068 is **9/9/0/0**. Every disputed identity, type and full message matched its candidate failure, with fresh XML, Java 25, baseline cwd and matching ROM/media/GL checked. These are separate baseline runs, not a combined 34-case invocation. The assertion guard intentionally nests an empty `TestTestReelExtension#bodyPasses` under an outer teardown oracle; it is not repaired with a tautological assertion. Campaign/SOZ/SSZ 91283 is **13/9/0/0**, with the final nine failed identities/type/full messages matching exactly; four other parameter invocations pass. All 42 disputed broad failures are baseline-reproduced, plus the separately reproduced assertion guard. Tool checks separately passed 401 Python cases with 8 explicit optional-native skips, 48 disassembly cases and 8 asset cases.

The broad run subsequently exposed four terrain-player errors on the old renderer source. Fresh 62132 on clean a8 passes **8/0/0/0** at 00:41:01Z, including all three regional real-EGL cases and the USA normal-input rendered replay. Fresh identities/Java 25/cwd/media/GL and source equality were checked; no additional production or test edit was needed.

Original run 20261026T212100Z-f39f97d5 on 71da plus two prose edits stopped at its 120-minute execution limit: **47928/43/13/102**, 81 of 88 ordinary batches completed and part of 82, 4,076 reports, zero failed/skip omissions; guards were unrun. This is incomplete, not a pass or source-a8 smoke result. Consumed diagnostics were acknowledged and deleted. All 288 unfinished candidates were retained in six focused smoke-profile batches on clean a8: **2102/0/0/3** across 268 fresh XML suites. Twenty-one candidates were excluded by unchanged slow/native/audio tags; nested suites account for the report count. Some dynamic cases reuse display names, so XML nodes are counted without claiming unique names. Skips require a packaged creator arena, a local deterministic BizHawk reference and an explicitly requested local timeline capture.

Broad skips include 23 explicitly opted-out CD EGL products, 32 act-content prerequisites, optional diagnostic/mod/measurement prerequisites and one unmet spin-tube capture/release assumption. The separately opted-in three-product EGL check remains independent. All 42 non-task broad failures reproduce on clean next; the one task fixture failure and 13 errors are covered by the recorded corrections/focused positives. No unrelated route/physics/expected-value tuning was performed.

Full guards 14329 at 01:26:13Z, are **811/3/0/0** across 97 fresh suites. The assertion guard matches its existing baseline. Clock terminology also reproduces in baseline 33751 (**2/1/0/0**, including a passing reference-control case): all three upstream test violations and the complete message match after replacing only the checkout prefix. That guard and the three offending source files are byte-identical to next. These two baseline failures remain unfixed.

The integration failure was a stale ignore rule after CD became a canonical optional reference. One-line correction **a212efcc3ca71b84d77d97f6df63e01d1a988798** passes the exact guard in 47374 (**1/0/0/0**). The merge had also dropped the private branch's module registration: **be5e5594f10e03a642a45f5a4d4327a04dcb5c81** restores its exact `.gitmodules` entry and mode `160000` native pin **1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff**. Three reference/checkout checks in 4843 pass **3/0/0/0** at 01:38:18Z; the actual tree/index mapping and uninitialized path were checked directly. The generated local link alone was removed; its target was preserved. All Java/tools/hooks/POM bytes remain identical to a8. The 811 run remains red; narrow corrections do not create a fresh all-green 811 or whole-suite result. No release-only full suite or native-parity certification was run.

## Ownership and remaining limits

All runtime bytes come through authenticated media, never disassembly fallback. Preserve release/region/revision, BIOS/environment and overlay identity. Source17 immutable decoding does not establish live PLC readiness/residency. Current art owns bytes and known masks; restarts preserve actual art/queue state, not a convenient cold image.

Frame owns global/player publication; Runtime owns cached bodies and ascending physical visits, including same-pass children. Preserve full 96 dynamic slots 32–127, separate reserved 1–31/Frame 0, full 64-byte images, saved-bank authority and same-owner preflight/CAS. Faults retain completed stores and close admission. Draw uses prior CPU SAT with current serviced art without advancing gameplay/animation/PLCs. Rewind captures actual owners, services and checkpoint/render state; fresh loads invalidate obsolete epochs. Sound/register intent is not audible FM/PCM/CDDA playback.

Controlled Body and natural Disc/Tunnel attachment remain deferred; Shadow/Hole never acquire control. Native Tamabboh World still fails its fixed blank-floor prerequisite. Native contact/timing/image parity, HUD/title/startup, audio, time travel, whole acts, remaining stages, special stages/Time Stones, menus/endings/saves and regional/viewport/donor/team breadth remain unfinished. Component rendering is not natural World-pixel or whole-game proof.

After renewed implementation authorization only, investigate `ObjSonic_TimeWarp.NotPPZ1`, native `src/Level/_Objects/Sonic/Main.asm:585–601`: ground velocity/timer path first writes 1. Establish actual timers, services and fault prefixes. Do not remove the guard, force period/direction 0 or select another object from the obsolete frontier.

## Locate evidence and resume

```bash
OPENGGF_ROOT=/absolute/path/to/OpenGGF
SCD_BUNDLE="$HOME/Downloads/Sonic-CD"
SCD_MEDIA_ROOT="$SCD_BUNDLE/media"
S2_ROM=/absolute/path/to/existing-S2-World-REV01.gen
NATIVE_GIT_DIR=/absolute/path/to/existing/scddisasm.git
I="$OPENGGF_ROOT/.worktrees/ai-nothing-to-see-here"
```

External `planning/implementation-plan.md` begins with the compact checkpoint; `README.md`/`media-inventory.json` index original media, patches and identities. `source/` holds exports; `research/sonic-hole-route-review.md` and FINAL/R2 originals retain evidence. Engine captures live under `native/engine-*/README.md` and `planning/sonic-cd-*-progress.html`; native contact, owner-ledger, PCM and regional title references are separate products. Original discs/BIOS/captures/frozen research remain external in Downloads.

Versioned references are the [implementation plan](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/architecture/plans/2026-10-23-sonic-cd-implementation.md), [design](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/architecture/designs/2026-10-23-sonic-cd-fourth-game.md) and [partial act matrix](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/architecture/validation/levels/scd-palmtree-panic-act1-present.md). Consult the [coverage backlog](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/status/level-test-coverage.md), [frontier log](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/status/trace-frontier-log.md) and [measurement hazards](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output) before interpreting checks. This controller frontier is not a native trace.

Retained tools: `tools/soniccd/{integration_packet,enum_contract_audit,java_declaration_census}.py`, `tools/disasm/disasm_citations.py`, and corresponding mirrored `.agents/skills/`/`.claude/skills/`. Packet apply needs pinned mappings/forward-inverse bytes and terminal writers; its terminal declaration is not proof. Multi-file apply is not transactional.

```bash
python3 -B "$I/tools/disasm/disasm_citations.py" lookup --git-dir "$NATIVE_GIT_DIR" --rev 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff --root src --path 'src/Level/_Objects/Sonic/Main.asm' --label ObjSonic_TimeWarp --context 2 --json
cd "$I"
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$I/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad' test -B
```

These recipes do not authorize execution during the pause. Use Java 25 and normal heaps. The two-class recipe is illustrative; it excludes separate EGL/guards/shared consumers. Authenticate S2 REV01 CRC32 `7B905383`; verify fresh identities/source/properties/skips. Reuse matching active jobs; queue tests, use change-based smoke, never release-only full-suite here. Acknowledge consumed diagnostics; keep no raw-log archive/pass cache.

## Delivery and cleanup

Code be5 was pushed nonforce only to `feature/ai-nothing-to-see-here`; `git ls-remote` verified that exact remote tip. Normal commit/push hooks and the release-tree audit passed. No main branch push/switch/merge, other-branch push, PR, tag or release occurred. The final handover is delivered in a documentation-only descendant of that stable code commit. The original discs, BIOS, captures and research custody remain external; no binary-file changes or original-media/capture extensions were found across the introduced history.

The canonical optional CD disassembly is now registered. Initialize `docs/scddisasm` explicitly only when source research needs it; engine/runtime asset loading remains independent and media-backed. Local submodule display settings hid its gitlink from ordinary diff/stat output; resource accounting used `--ignore-submodules=none` plus direct tree/index inspection.

The clean objects-source, next-baseline and delivery-validation worktrees and their fully merged local branches were removed after the verified code push. Requested checkout `ai-nothing-to-see-here` remains. Stale worktree metadata was checked/pruned; no task Maven or source worker remains live.

Three older worktrees are intentionally retained as `cleanup_pending`: `ai-sonic-cd-local` at 9598c663cacd9915768454e4abe5ada359cad305 has an ignored report and saves of unclear ownership; T3 `t3-7a3e44ae`/`soniccd-packet-integration` has unknown TraceChaser changes; `t3-3be36d39`/`disasm-native-path-lookup` preserves original unmerged tool history. Use `git worktree list --porcelain` to locate them; do not sweep their data or branches. Imported tool code is present on the delivery branch. FINAL/R2 author packets and useful external research remain preserved.

Further implementation requires renewed authorization. The existing 07:00 Europe/London digest is separate; 26 October was already delivered, and visual updates 00–07 stay queued. Do not restart validation or captures merely for the digest.

Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
