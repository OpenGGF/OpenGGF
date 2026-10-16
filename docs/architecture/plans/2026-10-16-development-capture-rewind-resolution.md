# Development capture owned-class rewind resolution

## Boundary and decision

Origin: Second Strike CNZ committed-shot replay prerequisite, 2026-10-16.
The pre-fix physical witness stopped restoring a required creator-owned native
Clamer Spring. This prerequisite changes development capture resolver publication,
not Clamer recreation, optional-reference policy, or native gameplay.

`DevelopmentPatchLoader.PreparedDecorator` retains one actual jar loader and fresh
registration transactions/boundaries. Its resolver admits the exact manifest owner
only after successful decoration, and rejects that owner after closure, including
classes already loaded. Ownerless lookup retains engine behavior. Correctly owned
native classes resolve through the accepted owner's loader and its ordinary parent
delegation; failed owned lookup never retries as ownerless.

`GameplayCaptureSession` installs the prepared resolver after deterministic external
content disable and successful decoration, before gameplay managers attach. The
capture-local scope restores its predecessor only while it still owns the installed
resolver. GameplaySessionFactory/LevelManager already propagate that resolver across
manager replacement; their source and snapshot schema are unchanged.

Normal close retains the existing order: world/session retirement, resolver scope
restoration, then jar loader closure. Exceptional session-retirement behavior was not
changed or certified. No shared allocation, native actor, Mod API inventory, or
signature changes belong to this prerequisite.

## Matched evidence

Input composition: root `247fd43810bce7e6f5dc9d3372dd7893cea8ab2d`, locally merged
without content differences as `370131518064bfc72ef220479684f273a7385ef0` while
preserving the helper's prior accepted history.

Before production edits, exact focused selector `TestDevelopmentPatchRewindResolver`
ran two component cases. The development case failed restoring required
`ClamerObjectInstance$ClamerSpringChild`, owner `capture-fixture`, object identity
`slot=-1, spawn=165, dynamic=1`. Its pre-restore resolver answer was false. The
matched accepted-loader `ModRuntime`/`ModClassResolver` control resolved the same
owner/type/identity and restored successfully. Result: 2 tests, 1 failure, no errors
or skips. This isolates resolver sufficiency for that component graph; the control
uses a real runtime accepted-loader map, not a complete launcher trust/catalogue flow.

After the fix, the same exact class ran four cases: required Spring graph restoration
through the real capture-local scope and two GameplaySessionFactory manager rebuilds;
accepted-owner runtime control; missing/wrong/closed owner rejection including cached
classes; and failed-decoration/newer-resolver close isolation. Result: 4 passed,
zero failures/errors/skips. Destructive restoration checks the required Spring's
identity and backlink to its restored native parent.

Commands (all through the worktree queue; `S3K_ROM_PATH` denotes the existing locked-on ROM absolute path):

```sh
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestDevelopmentPatchRewindResolver' test
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSecondStrikeEastCnzClamerCommittedReplay#committedClamerShotRestoresAndReplaysExactly' -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$S3K_ROM_PATH" test
```

The immutable physical B witness came from packet16, SHA256
`be780a2ce20372a5b68ec5ce7919be91e5e017308b90b23edad1ce4f6fea7c32`,
test blob `d59d766d69c7d5ab5008f3803278721362cfad20`; no controller or actor/timer
changes were made. One execution passed, zero failures/errors/skips. It captured
the committed shot at frame 126, with zero rings and native Spring contact. Immediate
restored tuple and class/slot multiset matched. All 60 forward/replay tuples matched,
and final snapshot differences were zero keys/entries. No hurt/death was observed;
final player X was 5707 and the existing B predicate passed. Maven execution was
26.692 seconds, test execution 1.988 seconds; the queue reported a 26-second wait on admission.

## Coverage limits and disposition

This proves the component resolver boundary and the declared CNZ B committed-shot
window. It is not a whole-act route certificate, exhaustive creator graph audit,
full-suite pass, exceptional teardown proof, or verification of a newer concurrent
root composition. No A calibration, native state writes, resolver fallback relaxation,
API/SDK sweeps, or broad engine checks were introduced. Consumed raw logs/XML/capture
diagnostics are temporary; preserve this bounded result and committed test inputs.
