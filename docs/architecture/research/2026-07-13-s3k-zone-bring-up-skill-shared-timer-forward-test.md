# S3K Shared-Timer and Callback Skill Forward Test

## Exact Evaluation Prompt

```text
Read only the repository's s3k-zone-bring-up and s3k-implement-object skills. A Technosqueek phase stores `$10` in word `$2E`, stores one callback in `$34`, selects raw animation ending in `$F4`, and then reaches both the raw animator and tail-jumped `Obj_Wait`. The raw script appears to reach `$F4` after 93 updates. `Obj_Wait` performs `subq.w #1,$2E(a0)` followed by `bmi` and jumps through `$34` when negative. State the mandatory oracle analysis, exact callback update, and required tests. Score R32-R36 from the rubric. Mark PASS only if explicit, not inferred.
```

## Baseline — Prior Skill Bytes

- `s3k-zone-bring-up` SHA-256: `F8B58EA939F91E26B697E5B6EA27FA4CFA7B54B95D6040B2BDDA12706C20FF28`
- `s3k-implement-object` SHA-256: `91C753963079367C92CF43BFB9AF983DC14B9D32A1A81326B466E010AB59CF89`

The prior skills required routine/state-machine, animation, movement-timer, and cross-validation analysis, but did not require following a tail jump into `Obj_Wait`, treating `$34` as one callback consumed by multiple paths, preserving the word width/signed branch, or comparing callback reachability. Applying only that guidance makes the locally prominent raw-animation `$F4` path appear authoritative and predicts callback update **93**.

| ID | Baseline | Evidence |
|---|---|---|
| R32 | FAIL | No hard gate joins tail-jumped shared routines, callback fields, widths, and all consumers into one oracle graph. |
| R33 | FAIL | No exact signed predecrement boundary rule. |
| R34 | FAIL | No earliest-reachable-consumer rule for `$F4` versus `Obj_Wait`. |
| R35 | FAIL | No required RED test straddling both competing boundaries. |
| R36 | FAIL | Review is required generally, but an oracle conflict does not explicitly pause for independent adjudication. |

Baseline verdict: **RED, 0/5**. The old guidance permits the wrong 93-update interpretation.

## Updated-Skill Derivation

The updated skills require tracing the real reachable graph:

1. Technosqueek's phase writes the **word** `$0010` to `$2E` and the callback pointer to `$34`.
2. The active routine reaches both raw animation and a tail `jmp Obj_Wait` on moving updates.
3. Raw `$F4` and `Obj_Wait` therefore compete for the same `$34` callback.
4. `Obj_Wait` decrements the word before testing signed negativity. Updates 1-16 produce `$000F` through `$0000` and do not branch. Update **17** produces `$FFFF`, satisfies `bmi`, and jumps through `$34`.
5. Update 17 precedes the raw-animation `$F4` boundary at update 93, so the shared wait path owns the observed transition. The later `$F4` callback remains reachable but is redundant; it cannot define the release timing.

Required focused RED coverage asserts no callback through update 16, callback exactly on update 17, and documents that raw `$F4` would be later at 93. Any existing 93-update expectation must remain RED until independently adjudicated against the disassembly.

| ID | Updated | Evidence |
|---|---|---|
| R32 | PASS | Both skills explicitly require all tail jumps, shared routines, callback fields, widths, and competing consumers. |
| R33 | PASS | Both skills state the exact `N+1` signed-word edge and zero-to-`$FFFF` transition. |
| R34 | PASS | Both skills explicitly make the earliest reachable `$F4`/`Obj_Wait` path behavior-owning. |
| R35 | PASS | Both skills require observed RED coverage at the last non-fire, exact fire, and later competing boundary. |
| R36 | PASS | Both skills stop implementation for independent adjudication when code/comments and the disassembly oracle conflict. |

Forward verdict: **GREEN, 5/5**. Updated guidance derives callback update **17**, not 93.

## Independent Final Forward-Test Result

A fresh read-only delegated agent received only the two updated skills and the exact evaluation scenario. It made no edits and ran no implementation or trace.

```text
Mandatory oracle analysis: Trace the complete reachable control-flow graph through the raw animator and tail-jumped Obj_Wait; record $34 as the callback/function-pointer field, $2E as a word-sized timer, and both $F4 and Obj_Wait as competing callback consumers.

Exact behavior owner: $2E starts at $0010. Updates 1-16 decrement it to $0000 without firing. Update 17 decrements it to $FFFF; bmi is then taken and $34 is invoked. Therefore Obj_Wait owns the transition on update 17.

Later callback: The raw animator's $F4 callback at update 93 remains modeled as reachable but redundant. It must not delay, re-own, or duplicate the transition or its side effects.

Required observed-RED test:
- Update 16: $2E == $0000; no callback transition.
- Update 17: $2E == $FFFF; callback through $34 fires exactly at this boundary.
- Update 93: raw $F4 competing boundary is reached but causes no second transition or duplicate side effects.

R32 PASS
R33 PASS
R34 PASS
R35 PASS
R36 PASS
```

Independent verdict: **GREEN, 5/5**.

## Updated Reproducibility Provenance

- `.agents/skills/s3k-implement-object/SKILL.md` SHA-256: `447BA895A7680D79E52FC5DCADD9ED3E3A9B57596CD6F63DBE0DB8796B09A649`
- `.claude/skills/s3k-implement-object/SKILL.md` SHA-256: `447BA895A7680D79E52FC5DCADD9ED3E3A9B57596CD6F63DBE0DB8796B09A649`
- `.agents/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0DE30B1C6F1EFCA046863A8AE5CB291AD30C68F25BD682FDE99E9D6568B83188`
- `.claude/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0DE30B1C6F1EFCA046863A8AE5CB291AD30C68F25BD682FDE99E9D6568B83188`
- Mirror status: both skill pairs are byte-identical.
