# Trace Frontier Advancement Prompt

Reusable prompt for future trace replay work. Paste this into a fresh agent
session when the goal is to move one or more trace replay frontiers forward
without masking ROM parity bugs.

---

You are advancing OpenGGF trace replay parity. Your job is to make the engine
natively reproduce more of the recorded ROM behavior, one verified frontier at a
time.

## Required Reading

Before changing code:

1. Read `AGENTS.md`.
2. Read `.agents/skills/trace-replay-bug-fixing/SKILL.md`.
3. If you touch Sonic 2 objects or disassembly, read:
   - `.agents/skills/s2-implement-object/SKILL.md`
   - `.agents/skills/s2disasm-guide/SKILL.md`
4. If the target is S3K, read the matching S3K zone/object/disassembly skills.

Apply the trace replay invariant throughout: trace data is read-only comparison
and diagnostic input. The engine must not hydrate or sync state from trace rows
inside the replay loop.

## Mission

Advance the highest-priority failing trace frontier while keeping the engine
accurate to the original game and aligned with the current architecture.

Current priority:

1. Advance SCZ first if it still has an actionable frontier.
2. Then advance the nearest failing frame among the other S2 level-select traces.
3. Prefer a small, disassembly-backed engine fix over broad speculative work.

Known recent frontier examples:

- SCZ: frame 55, player `x` expected `0x017F`, actual `0x017E`.
- WFZ: frame 207, player `y` expected `0x04B2`, actual `0x04B1`.
- HTZ: frame 308, `tails_y` expected `0x0477`, actual `0x0476`.
- OOZ: frame 230, `y_speed` expected `0x0000`, actual `0x05B0`.
- ARZ: frame 303, `tails_y` expected `0x0363`, actual `0x0362`.
- CNZ: frame 201, `tails_rolling` expected `1`, actual `0`.
- CPZ: frame 339, `tails_g_speed` expected `0x042D`, actual `0x0000`.
- MCZ: frame 216, `tails_air` expected `0`, actual `1`.

Reconfirm the current failures before coding; these frames may have moved.

## Execution Loop

1. Pick one target trace and run its focused test.
2. Read the first failing frame from:
   - `target/trace-reports/<game>_<zone>_report.json`
   - `target/trace-reports/<game>_<zone>_context.txt`
3. Classify the failure before editing:
   - bootstrap or route prelude,
   - camera or scroll,
   - terrain physics,
   - object spawn, routine, or placement,
   - solid object contact, riding, carry, or release,
   - sidekick AI or following history,
   - zone event, mutation, palette, PLC, or animation state.
4. Locate the ROM routine in the relevant disassembly and compare it to the
   engine path. Cite source file and line numbers in notes, commits, and any
   code comment that exists only to explain a ROM quirk.
5. Classify the implementation surface before coding:
   - game-local object/zone/event code,
   - shared object/solid/camera/scroll infrastructure,
   - shared physics/collision/player movement,
   - trace recorder/parser/reporting diagnostics only.
6. For any shared or plausibly shared behavior, verify the corresponding ROM
   behavior across Sonic 1, Sonic 2, and Sonic 3&K before choosing the code
   shape. If the games differ, introduce or reuse an explicit feature flag or
   game-specific provider path; do not let an S2 fix silently change S1/S3K.
7. Fix the engine path that should have produced the ROM value.
8. Run the focused test again.
9. Run cross-game regression checks proportional to the touched surface. Shared
   physics/collision changes require S1, S2, and S3K coverage; game-local object
   fixes require at least the target trace plus nearby shared-object/trace tests
   that exercise the same manager path.
10. Record whether the frontier passed, moved later, or stayed at the same frame,
   and record which cross-game checks were run or why a narrower scope was safe.
11. If the target moved, decide whether to continue on the same trace or switch
   to the next highest-priority trace based on route impact and fix scope.

Do not treat a moved frontier as a fully solved trace unless the trace test
passes. Report the exact new first failing frame.

## Rules

- Do not change trace data just to make a test pass.
- Do not add per-frame trace-to-engine hydration, sync, reseeding, tolerance
  bands, or elastic comparison windows.
- Diagnostic reseeding is allowed only as uncommitted local investigation.
- If the trace lacks enough ROM-side state to diagnose the bug, extend the Lua
  recorder and Java parser as diagnostics only, bump script/schema metadata as
  needed, regenerate the affected trace, and keep the new data out of engine
  mutation paths.
- Use disassembly-backed constants and behavior. Do not infer ROM rules from
  engine convenience alone.
- For shared physics, collision, sidekick, object, camera, or scroll code,
  check whether S1, S2, and S3K share the behavior. Gate real per-game
  divergence with `PhysicsFeatureSet` or an equivalent existing feature flag,
  not game-name checks.
- Never assume a shared engine path is safe because one game's trace improves.
  Before landing shared behavior changes, verify whether Sonic 1, Sonic 2, and
  Sonic 3&K use the same ROM rule and run tests that can catch regressions in
  every involved game.
- Keep object code on `ObjectServices`; keep non-object runtime code on
  `GameServices` or explicit mode-context dependencies.
- Prefer runtime-owned shared frameworks when relevant:
  `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`,
  `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`,
  `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and
  `AdvancedRenderModeController`.
- For ROM `x_pos` and `y_pos`, use engine centre-coordinate APIs
  (`getCentreX`, `setCentreX`, `getCentreY`, `setCentreY`) unless the code is
  explicitly about sprite bounds or render extents.
- For gameplay-affecting scroll, logic-frame code must produce the state used
  by headless replay and rendering. The render pass should consume scroll state,
  not be the only place that mutates it.
- For moving solids, preserve ROM ordering: routine transition, timer update,
  object motion, embedded `SolidObject` call placement, standing-bit refresh,
  carry, and release.

## Subagent Strategy

Use subagents when the work naturally splits into independent tracks:

- one explorer to classify the failing frame and summarize report context,
- one explorer to locate and summarize the ROM routine,
- one worker to implement a bounded fix in a disjoint file set,
- one reviewer to check for trace invariant violations and architecture drift.

Do not delegate the immediate blocker if your next local step depends entirely
on that answer. Continue local work on a non-overlapping task while agents run.

Ask agents to suggest skill improvements only when they find a repeated failure
pattern that future agents are likely to repeat. If skill edits are warranted,
update both `.agents/skills/...` and `.claude/skills/...` mirrors in the same
logical change, and use the commit trailer `Skills: updated`.

## Skill Self-Improvement Feedback Loop

When a frontier advancement commits a fix that touches code under
`src/main/java/com/openggf/game/sonic{1,2,3k}/`, the loop closes back into
the implement-object skill catalogue so future implementations don't
repeat the same bug class. Execute step 10 of the
`trace-replay-bug-fixing/SKILL.md` workflow:

1. After landing the fix, classify the root cause. Is it:
   - A class of bug that could occur in any not-yet-implemented object?
     (Touch-response gating, multi-frame init collapse, per-player state,
     character-dependent offsets, premature non-solid transition, gravity
     order, centre-vs-top-left coordinates, per-game post-event flow.)
   - Or a one-off specific to this object's quirks?
2. If a class of bug, open the relevant `rom-pitfalls.md`:
   - `.agents/skills/s2-implement-object/rom-pitfalls.md` (canonical) +
     `.claude/skills/s2-implement-object/rom-pitfalls.md` (mirror).
   - `.agents/skills/s3k-implement-object/rom-pitfalls.md` (canonical) +
     `.claude/skills/s3k-implement-object/rom-pitfalls.md` (mirror).
3. Decide: matches an existing P-numbered entry (append commit hash to
   the entry's originating-commit list) or genuinely new (append a new
   P-numbered entry following the template).
4. If the pattern is cross-game (ROM convention shared between S2 and
   S3K), copy the entry to the other game's pitfalls file with the
   matching disasm citation.
5. Commit the catalogue update on its own (or folded into the same
   commit as the engine fix when the diff is small). Use the
   `Skills: updated` trailer.

The catalogue grows by accretion. Read `rom-pitfalls.md` at Phase 1.5 of
the corresponding `*-implement-object/SKILL.md` before writing object
code; check each entry against the object you're porting.

## Verification

Use focused commands while iterating. Examples:

```powershell
mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2SczLevelSelectTraceReplay" test -DfailIfNoTests=false
mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2WfzLevelSelectTraceReplay" test -DfailIfNoTests=false
mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2HtzLevelSelectTraceReplay" test -DfailIfNoTests=false
mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay" test -DfailIfNoTests=false
```

When touching shared infrastructure, run a broader trace gate:

```powershell
mvn -q "-Dtest=*TraceReplay" test -DfailIfNoTests=false
```

When touching shared physics/collision/player movement, include cross-game
coverage. Prefer existing focused tests first, then broaden:

```powershell
mvn -q "-Dtest=*Physics*,*Collision*,*Sensor*,*Sonic1*,*Sonic2*,*Sonic3k*" test -DfailIfNoTests=false -DfailIfNoSpecifiedTests=false
mvn -q "-Dtest=*TraceReplay" test -DfailIfNoTests=false -DfailIfNoSpecifiedTests=false
```

If the touched code is shared but only one game has a current trace for the
specific behavior, document the disassembly comparison for the other games and
run the closest available unit or integration tests for those games.

If Maven's quiet/silent output hides the useful failure, rerun with
`-Dmse=off`.

## Reporting

For every completed iteration, report:

- target trace and command run,
- old first failing frame and field,
- fix summary,
- disassembly evidence used,
- new first failing frame, or that the trace now passes,
- shared/non-shared behavior classification,
- cross-game verification performed for S1, S2, and S3K, including skipped
  checks with the reason,
- any tests not run and why.

If committing, keep commits logically small and follow the branch documentation
trailers in `AGENTS.md`. Do not use `--no-verify`.

## Stop Conditions

Stop and plan instead of forcing a local fix when:

- the failing frame requires several unimplemented objects or a whole zone
  subsystem,
- recorder diagnostics and parser changes are needed before root cause is
  knowable,
- the likely fix crosses shared physics/collision behavior for multiple games,
- the required change would fight the service architecture or runtime-owned
  framework stack,
- multiple agents should implement independent staged work.

The right stopping point is a clear frontier movement, a passing trace, or a
specific implementation plan with verified blockers. Do not land exploratory
hacks.
