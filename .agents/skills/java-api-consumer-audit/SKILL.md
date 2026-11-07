---
name: java-api-consumer-audit
description: Use when a Java source handoff removes or changes written methods and explicitly selected consumers may retain stale calls, before costly queued compilation; also for bounded known-owner missing-method hints. Not Java compilation or API acceptance.
---

Use [java_api_consumer_audit.py](../../../tools/soniccd/java_api_consumer_audit.py)
for advisory name/arity evidence. It includes package-private written methods.
Use declaration census for inventories alone, import preflight for imports and
enum contract audit for enum constants/counts. This tool adds consumer references;
it never edits sources, approves an API or launches Java/Maven.

Choose one explicit qualified owner, an exact before-source Git blob at a full
immutable commit, an independently pinned FINAL after-owner, and up to 16 explicit
caller files. Git callers use that same revision. FINAL callers are independent;
there is no destination mapping, checkout scan, fallback or union of versions.

```bash
python3 -B tools/soniccd/java_api_consumer_audit.py \
  --git-dir "$CANONICAL_GIT_METADATA" --revision "$FULL_COMMIT_SHA1" \
  --owner p.Assets --source src/main/java/p/Assets.java \
  --after "$FINAL_OWNER_JAVA" --after-sha256 "$SUPPLIED_SHA256" \
  --after-bytes "$SUPPLIED_BYTES" --after-mode 0444 \
  --caller src/test/java/p/Consumer.java --json
```

Repeat `--caller` for immutable files, or use
`--caller-final /canonical/Consumer.java SUPPLIED_SHA256 SUPPLIED_BYTES 0444`.
Pins must come from the trusted handoff; do not mint pins for unknown sources to
make verification pass. FINAL files must be regular, canonical, no-follow and
without hardlink aliases. Both bytes and modes are checked before and after.

Read `delta`, each caller's `references`/provenance, `advisory_hits`,
`known_foreign_references`, `unsupported`, `omissions` and `observation_limits`
together. Human output is the default; `--json` preserves structured rows.

- Exit **0**: ready bounded observations with no advisory hits.
- Exit **1**: review candidates in supported observations.
- Exit **2**: invalid input, pins, dependency or initial read.
- Exit **3**: incomplete evidence, drift, unsupported binding or exhausted bound;
  candidates can still be present. Omitted rows are never a clean empty result.

A removed `originalImage/0` on R31 must retain unresolved `var`/chained-factory
calls as candidates. A directly declared foreign R13 receiver is separate;
unresolved R13 factory results remain unknown, never attributed to R31.
Simple written parameters/variables, exact `new` constructors and class spellings
supply only lexical provenance. Block/header shadows, generics, inheritance,
external/generated members and unsupported grammar stay uncertain. Receiver and
context strings are normalized lexer tokens, with literals redacted.

Same-arity signature changes retain overload candidates; the tool never binds
argument types. Method references always have unknown overload arity. Optional
`--check-member getPatterns` inventories an absent written name on the selected
after-owner: `var geometry = new SonicCdLevelGeometry(...)` can support a receiver
hint, but inherited/base members remain unknown and the result is incomplete.

Defaults/ceilings: 1 MiB/file, 4 MiB selected bytes, 100,000 aggregate tokens,
5,000 declarations/file, 4,096 context tokens, depth 32, 256 delta/reference rows,
30 seconds for Git/parse checkpoints, 1 MiB output. `--max-*` only lowers bounds;
output requires 512 bytes for an incomplete diagnostic. Git argv is fixed and
filters are never run. Selected sources and HEAD metadata are rechecked
non-atomically; restored transient changes can escape detection. Zero candidates
never proves consumer completeness or Java certification. Actual required queued
compile/test evidence remains owned by the integration task.

Dependencies are exact hash-pinned sibling preflight/census/enum tools. Review
changed helpers and renew safety checks before updating pins; never bypass them.
Run [focused tests](../../../tools/soniccd/test_java_api_consumer_audit.py) and the
unchanged sibling import/census/enum safety suites with owned `TMPDIR` and
`PYTHONDONTWRITEBYTECODE=1`. Regenerate the Claude mirror with
`python3 tools/git/sync_skills.py` and check it with `--check`.
