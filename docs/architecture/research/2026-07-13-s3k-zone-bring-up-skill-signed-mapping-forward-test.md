# S3K Zone Bring-Up Skill Signed-Mapping Forward Test

## Exact evaluation prompt

```text
Read only C:\Users\farre\IdeaProjects\sonic-engine\.worktrees\fbz-complete-execution\.agents\skills\s3k-zone-bring-up\SKILL.md. Do not inspect other repo files or code. Evaluation scenario: an S3K zone adds an object mapping table whose dc.w frame offsets include backward references to earlier/shared frame data, and the loader currently treats each relative word as unsigned. State the mandatory art-loader architecture and regression checks the skill requires. Then explicitly score PASS or FAIL for each: (A) relative dc.w mapping offsets must be decoded as signed 16-bit values; (B) backward/shared-frame references must be audited; (C) when the first frame pointer cannot delimit the offset table, the caller/loader must supply an explicit verified frame count; (D) a real-ROM table-shape test must catch zero-extension or runaway/OOM behavior without memory-limit workarounds or object-specific special cases; (E) the full ROM art crawler and PatternSpriteRenderer corruption guard must run. Mark PASS only when the skill states the requirement explicitly, not when it could be inferred. Do not edit files.
```

## Baseline raw output

```text
The requested skill does not explicitly define the signed mapping-table loader architecture or regression checks.

- A — FAIL: No signed 16-bit dc.w offset requirement.
- B — FAIL: No backward/shared-frame reference audit.
- C — FAIL: No explicit verified frame-count fallback.
- D — FAIL: No real-ROM table-shape/runaway/OOM regression requirement.
- E — FAIL: No full ROM art crawler or PatternSpriteRenderer corruption-guard requirement.

It only provides broader guidance around s3k-plc-system, AnimatedTileChannelGraph, ROM-backed art handoffs, inventory, and test-first coverage.
```

The old guidance could therefore allow a loader to zero-extend a negative frame pointer, miss valid backward/shared frame data, infer an invalid table length, and run away into oversized allocation or OOM before a generic art check diagnosed the cause.

## Updated-skill raw output

```text
Mandatory loader architecture is explicit—decode every relative mapping-table dc.w as a signed 16-bit displacement from the table base; explicitly audit negative/backward and shared-frame pointers; if the first frame pointer cannot prove table length, require a disassembly-verified explicit frame count. Fix the generic decoder or verified table metadata, never heap/memory workarounds or object-specific address special cases. Regression gate: real-ROM table-shape test with exact frame count plus representative piece count/dimensions/tile indices and a backward/shared reference when present; malformed offsets must fail boundedly; run the complete ROM-conditional S3K art crawler and TestPatternSpriteRendererCorruptionGuard.

A PASS — signed 16-bit dc.w displacement explicitly stated.
B PASS — negative/backward and shared-frame audit explicitly stated.
C PASS — disassembly-verified explicit frame count explicitly required when first pointer cannot establish length.
D PASS — real-ROM table-shape/runaway-OOM coverage and prohibitions on memory workarounds/object-specific special cases explicitly stated.
E PASS — complete ROM-conditional S3K art crawler and TestPatternSpriteRendererCorruptionGuard explicitly required.
Overall: 5/5 PASS.
```

## Verdict

| Assertion | Baseline | Updated | Rubric |
|---|---|---|---|
| Signed relative `dc.w` decoding | FAIL | PASS | R27 |
| Backward/shared-frame audit | FAIL | PASS | R28 |
| Explicit verified frame count | FAIL | PASS | R29 |
| Real-ROM bounded table-shape regression | FAIL | PASS | R30 |
| Full art crawler and corruption guard | FAIL | PASS | R31 |

Overall: **RED, 0/5 → GREEN, 5/5**.
