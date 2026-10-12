# Mod audit remediation execution — 2026-10-12

Execution of the [remediation plan](../plans/2026-10-12-mod-audit-remediation.md).
The initial implementation base is `15f3cf371a49e9755568588b00b1f515225aaa4d`
on `develop`. Work is isolated in `feature/ai-mod-remediation` and three owned
worker trees; unrelated main-workspace files and dirty disassemblies are preserved.
This record distinguishes checks already observed from pending qualification.

## Initial evidence and checks

- Java is OpenJDK 21.0.12.1. Existing S1 REV01, S2 REV01 and locked-on S3K ROMs
  matched the CRC32/SHA-1 identities in repository guidance; no assets were copied
  or renamed.
- Root `python3 tools/testing/maven_queue.py -Dmse=off test-compile` passed
  at the initial base plus regression sources (1m14s). This is compilation,
  not a test-suite pass.
- Creator export regressions failed because Flappy's authored `CourseTest.java`
  and a fixture's test/resource/generator inputs were absent. Shared-builder
  regression rejected its missing output override. SDK-template checks failed
  because both generic/simple templates omitted `creator-tests`.
- After the export/builder/template changes,
  `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/modding -p test_creator_kit.py`
  passed 16 tests. This structural Python pass does not qualify gameplay; actual consumer results follow below.
- Initial Maven requests waited behind an unrelated Java migration run; later admission
  proceeded normally. No unrelated queue lock or process was changed.

## Ownership and outstanding work

Root owns Three Islands/Slay correctness, creator tooling, shared save/UI helpers,
combined API pinning and integration. Workers own Flappy/Sitar/Hello/Tower/Practice
save/input changes plus their catalogues; Egg/Starfall decoding/world/cache changes;
and Survivors settlement/catalogues/frame-control changes. The plan remains the
completion ledger; no wave is declared delivered by this initial record.

Subsequent sections will record actual failing/passing regression identities,
API decisions, integrated commits, broad selections, skips and baseline attribution.
Raw Maven logs and diagnostic directories are temporary and are not archived here.

## Observed implementation qualifications

Root checks are at `8c69d75a1` plus the recorded uncommitted implementation, not yet integrated:

- Queued lean `TestThreeIslandsExample,TestSlayTheRobotnikExample`: four wrapper tests
  passed, zero skips. Their authored suites were also launched through actual external
  creator-kit Jupiter: Three Islands 86 checks, Slay 132, all passing with no skips.
  This portable model qualification uses no ROM and does not certify pixels/audio.
- Queued `TestSlayTheRobotnikScene,TestThreeIslandsScene,TestModTestKit`, with all
  three verified absolute ROM properties: 27 tests passed, zero skips, 33.478 s Maven
  execution after 121 s admission wait.
- Queued lean `TestProjectScaffolder`: two tests passed, zero skips. An actual generated
  scene starter's packed-mod registration and testkit scene-state inspection passed two
  external Jupiter checks. The testkit-only `scene()` inspection helper is committed as
  `8c69d75a1`; callbacks continue through `tick`/`draw` and their owner boundary.
- Queued lean `TestModSaveSlot,TestModStorage`: 25 passed, zero skips. After actual
  Three Islands/Slay migrations plus the preserved replacement/counter edge regressions,
  adding their two example selectors passed 29 wrapper/storage checks, zero skips,
  1m19s Maven execution. New public API pins/gates remain pending at this checkpoint.
- Input/save worker's initial batch is `7947bd9a90f1ab985d89a3464f19732912daeec8`.
  World worker's initial batch is `51f16847c`: portable Egg 25/25 and Starfall 53/53;
  engine focused checks 64/64 plus corrected actual owned-close lifecycle 1/1, zero skips.
  These are local worker results, not a main-branch delivery claim.
- Survivors' production registry restore reproduced a doubled death settlement:
  bank 55 became 110. The bounded new lifecycle repair passes its death/retire/victory/
  failed-write/old-run checks; the first full domain run exposed two migration regressions
  caused by eager ID reservation writes. Reserving an ID no longer writes a file;
  the matched eight-test migration/settlement subset passed. Full repaired qualification
  and the related additive milestone replay check remain pending.
- Flappy/Tower/Sitar prepared-table checks measured 1,000 distinct lookup values before
  the change and one afterward, with pinned content/RNG hashes unchanged. Portable
  affected checks passed 120; normal engine selector passed 110, zero skips, including
  the ROM-backed complete Sitar song catalogue (513 s for that class).

These counts distinguish wrapper Jupiter tests from the nested authored checks.
No full ordinary/guard suite, complete graphics route or final artifact qualification
has been claimed by this checkpoint.

## Shared slot/presentation and Infinite checkpoint

The root implementation now includes P12's `ModSaveSlot` and P14's composition/
canvas scopes, with Three Islands and Slay profile/composition migrations and Sitar
canvas migration. Local commits have not yet been integrated into main `develop`.

- Queued lean `TestSceneArtHelpers,TestCreatorUi,TestRecordingCanvas,TestSlayTheRobotnikExample,TestThreeIslandsExample`:
  29 wrapper/helper tests passed, zero skips (1m23s). The first test compilation failed
  on a missing `assertThrows` import; it was corrected before the completed run.
- Queued normal `TestInfiniteSonic` plus the six common API gate classes with S1 REV01:
  312 tests, two failures confined to exact signature pins, 32 structural assumptions
  skipped. Infinite's 241 tests had no failures/errors; the stock native collision,
  course/rewind, top-ten/tie and owned-storage regressions ran. First compilation of
  the new derived trait cache rejected a missing required `RewindTransient.reason`;
  that annotation was corrected before this completed domain run.
- Signature generation was rerun after compilation, adding the 142 missing helper
  signatures. A prior attempt ran during compilation and used an older inventory;
  another lacked dependencies and was rejected. Neither was accepted as a valid pin.
- A combined pin/Sitar/Slay request was accidentally submitted to the lean lane,
  cancelled as incomplete, and rerun in the normal lane. The completed normal
  `TestModApiSignatureSurface,TestModApiPinPolicy,TestSitarHeroCareer,TestSitarHeroArcade,TestSlayTheRobotnikExample`
  with verified S3K: 63 tests passed, zero skips, 32.633s. Slay's nested authored suite
  passed134 checks, including targeted cards/monitors and full saved-state comparison
  at no/sparse/duplicate draw cadence.
- Creator Python safety/export checks remain16/16 after preferring an authored legacy
  check program's `main` when present, so its private checks are not silently omitted.
- Local integration merges: input/save/definitions `9d26f4d04`; world/camp/art diagnosis
  `94ce59bf6`; Survivors once-per-run settlement `1fbca056f`. Creator tooling checkpoint
  is `edec25cad`. Worker P08 Survivors `85796e996` is the next local integration.

Final matching SDK-only exports, graphics/pixel qualification, broad ordinary/guards,
remaining capabilities and main-branch delivery remain outstanding. Candidate version
and release status stay0.7.0/unpublished; no published API baseline was changed.
