# Handover Follow-ups Integration

## Baseline and inputs

- Main workspace branch: `develop`
- Updated baseline: `2c64e09d4925cd6d9628ea59ac9874b2e26e6829`
- Development branch: `bugfix/ai-handover-followups`
- Candidate at first comparison: `1ebb5d929`
- JVM: OpenJDK 21.0.11
- ROM inputs: verified S1 REV01, S2 REV01, and locked-on S3K images supplied outside
  the repository
- Remote synchronization: `git fetch origin` followed by
  `git pull --ff-only origin develop`; `develop` and `origin/develop` were already aligned

The same command was used in both workspaces:

```text
mvn -Dmse=off \
  -Dsonic1.rom.path=<verified-s1> \
  -Dsonic2.rom.path=<verified-s2> \
  -Ds3k.rom.path=<verified-s3k> \
  test
```

## Pre-merge regression comparison

| Revision | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `develop` `2c64e09d4` | 14,054 | 27 | 8 | 31 | expected red baseline |
| candidate `1ebb5d929` | 14,072 | 27 | 7 | 31 | expected red baseline plus one isolation finding |
| candidate after isolation correction | 14,072 | 26 | 7 | 31 | no new or worsened baseline failure |

The candidate added 18 focused contract cases. Relative to reports written by these exact
suite invocations, two baseline failures became green:

- `TestHardwareTimingReplayPort#schemaTwoAdmitsDirectPreEdgeBeforeIndependentModulePostEdge`
  now follows the production loop-tail order fixed on 2026-08-01.
- `TestMhzMushroomParachuteObjectInstance#fallingPlayerInGrabWindowIsCarriedAtRomOffsetAndParachuteStartsFalling`
  no longer inherits the persistent swing-vine owner from an earlier test.

One otherwise-passing method failed only in the first development suite:
`Sonic1SpecialStageResultsScreenTest#testRingBonusTalliesIntoScoreAndCompletes`. The
unchanged baseline and candidate both pass the complete four-method class alone. The test
polls session-owned S1 PLC readiness but lacked the repository's standard singleton reset,
so a reused fork could inherit unrelated outstanding PLC work. The amended design and plan
scope the correction to class-level `SingletonResetExtension` plus `@FullReset`, because the
lighter per-test profile does not clear the PLC service. Production result timing and PLC
behavior remain unchanged. The results class and lifecycle guard pass together, 12/12, and
the repeated full suite passes the method.

The final candidate failure set is a strict subset of the baseline set. It removes
`TestHardwareTimingReplayPort#schemaTwoAdmitsDirectPreEdgeBeforeIndependentModulePostEdge`
and `TestMhzMushroomParachuteObjectInstance#fallingPlayerInGrabWindowIsCarriedAtRomOffsetAndParachuteStartsFalling`;
every remaining failure and error has the same method identity as the baseline.

The suite also rewrites `docs/status/rewind-round-trip-gaps.md` as generated probe output.
That rewrite is not an intended deliverable and must be discarded before integration.

## Integration completion

Pending independent end-to-end review, merge, post-merge suite, push, and worktree cleanup.
This section will be completed on merged `develop` with exact commits, conflicts, regression
comparison, and cleanup state.
