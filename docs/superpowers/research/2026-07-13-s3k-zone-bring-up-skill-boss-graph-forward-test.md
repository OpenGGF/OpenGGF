# S3K Boss Graph and Publication Skill Forward Test

## Exact Evaluation Scenario

```text
Read only the current s3k-implement-boss and s3k-zone-bring-up skills plus the locked-on sonic3k.asm. Prepare a mandatory implementation and validation contract for Obj_FBZMiniboss, including exact full dynamic graph count, graph links, allocation-failure semantics, damage source, completion publication owner, player participation policy, and first-tick timing. Cite disassembly evidence. Do not read plans, inventory, research artifacts, tests, or production implementation. Do not edit or run a trace.
```

The evaluator was independently delegated with the pre-update skill bytes and raw disassembly only.

## Baseline -- Prior Skill Bytes

- `.agents/skills/s3k-implement-boss/SKILL.md`: `D2F45A300962ADC2CAF7F6ECAE24DCC3DC372E3EC93851DE52B37637EDDB95C9`
- `.agents/skills/s3k-zone-bring-up/SKILL.md`: `0DE30B1C6F1EFCA046863A8AE5CB291AD30C68F25BD682FDE99E9D6568B83188`

### Baseline raw result

The evaluator correctly derived root + seven direct children + two independently owned five-link tables = 18 steady mechanical objects, with a transient palette controller allowing 19. It also derived cyclic terminal/controller links, prefix-stop allocation, no rollback/retry, after-current same-sweep eligibility, and scripted terminal-child damage rather than player attack damage.

However, it said: `Root exclusively owns defeat/completion publication` and stopped at `Obj_EndSignControl` / `_unkFAA8`. It did not follow `Obj_LevelResultsCreate` to the later `Events_fg_5` write. It assigned one broad P1 targeting policy rather than distinguishing P1-only activation/attack capture, nearest-native-P1/P2 tracker selection, and all-eligible-player solid/hazard participation. It also judged the existing generic inventory/review language sufficient even though none of the eight checks below was a hard acceptance gate.

| ID | Baseline | Evidence |
|---|---|---|
| R37 | FAIL | Damage was derived, but the skills did not require negative player-hit/no-invented-shield tests. |
| R38 | FAIL | Exact 18/19 was derived ad hoc; the skills did not require multiplication of repeated tables per owner or separate steady/transient/peak counts. |
| R39 | FAIL | Cycles were derived ad hoc; the skills required parent/child relink but not forward/reverse/terminal/cross-link topology. |
| R40 | FAIL | Prefix stop was derived ad hoc; no every-ordinal, independent-table, no-healing/retry gate existed. |
| R41 | FAIL | Same-sweep timing was only medium-high-confidence inference; no primitive/search-direction/first-tick test gate existed. |
| R42 | FAIL | The output assigned completion to the root/sign and missed the results-owned `Events_fg_5` publication. |
| R43 | FAIL | The output collapsed distinct per-phase and per-operation participant policies. |
| R44 | FAIL | The generic graph round trip did not require cyclic topology plus preserved partial prefixes without reconstruction healing. |

Baseline verdict: **RED, 0/8**. The old skill can undercount a repeated graph as 13 rather than 18/19, accept the wrong damage model, or attribute `Events_fg_5` to boss defeat without violating an explicit gate.

## Updated Skill Expectations

The updated skills require all of the following before implementation dispatch:

1. Classify the real damage publisher/collision owner per phase and prove scripted damage with negative player-hit coverage.
2. Expand helper/table counts per owner and report the FBZ graph as 8 after the initial table, 13 after one arm, 18 after both arms, and 19 only while the transient controller is live.
3. Preserve both six-object cyclic arm graphs, ordered forward/reverse links, and controller/terminal cross-links.
4. Test each initial/arm table independently at every allocation-failure ordinal, preserving the allocated prefix without rollback, retry, healing, or duplicates.
5. Distinguish allocation primitive/search direction and prove same-sweep child first ticks plus next-root-tick damage consumption.
6. Follow boss -> sign/controller -> results and assign `Events_fg_5` to `Obj_LevelResultsCreate`, not the boss.
7. Assign participation per operation: P1-only plunger/attack decisions and captured target, nearest native P1/P2 tracker choice, all-eligible-player solid/hazard interactions, no extension-sidekick authority leak.
8. Prove the full graph and partial-prefix topology through a real `ObjectManager` capture/remove/diverge/restore cycle.

The independent updated-skill result and final hashes are recorded below after validation.

## First Forward Pass -- Refactor Required

The first updated-skill evaluator scored R37-R44 as PASS and correctly reported 8/13/18/19, scripted terminal damage, cyclic graphs, independent partial prefixes, same-sweep allocation, and the results-owned `Events_fg_5` chain. Its raw participation result nevertheless said activation was a camera/world gate plus all-native-player solid contact. That conflated the shared solid routine with the plunger's later explicit P1 standing-bit test at `sonic3k.asm:146904-146922`.

R43 therefore remained **FAIL** and the first forward verdict was **RED, 7/8**. The skills were refactored to require tracing every standing/status/control-bit activation gate to its exact native slot instead of inheriting the participation policy of generic solid handling.

## Independent Refactor Forward Result

```text
R37 PASS
R38 PASS
R39 PASS
R40 PASS
R41 PASS
R42 PASS
R43 PASS
R44 PASS

Verdict: GREEN.

Generic solid collision processes native P1 and active P2, but the plunger tests
literal status bit 3, the P1-standing bit. Only P1 standing activates the miniboss.
P2 or extension-sidekick standing may receive ordinary solid behavior but must not
set root $38.0.

Events_fg_5 is written by Obj_LevelResultsCreate, then consumed by
FBZ1BGE_Normal; neither the boss nor sign writes it.
```

The evaluator also reconfirmed exact 8/13/18/19 counts; terminal `$A` scripted damage and negative player-hit requirements; both cyclic arm graphs and propagated cross-links; every-ordinal independent prefix failures without healing; forward `AllocateObjectAfterCurrent` same-sweep first ticks; and the real boss -> controller -> sign -> results publication path.

Final verdict: **GREEN, 8/8**, after baseline **RED, 0/8** and first forward **RED, 7/8**.

## Final Validation and Provenance

- `.agents/skills/s3k-implement-boss/SKILL.md`: `4D6A7ABF20EB93E6F967380F1E8B01CDA723CDC416B7A69C50C5B541D8214095`
- `.claude/skills/s3k-implement-boss/SKILL.md`: `D2094B26F9E57DD6DE58900CCE28B80DD0038CC32A551553788D9585B73E93B4`
- Boss mirror semantic diff: only intentional `.agents` versus `.claude` cross-reference paths; the new gate is identical.
- `.agents/skills/s3k-zone-bring-up/SKILL.md`: `0F5306D2011CA498FE08449DB880C637557D176336877EC716039322AF169F0F`
- `.claude/skills/s3k-zone-bring-up/SKILL.md`: `0F5306D2011CA498FE08449DB880C637557D176336877EC716039322AF169F0F`
- Zone-bring-up mirrors: byte-identical.
- `quick_validate.py`: PASS for all four skill directories with UTF-8 mode.
- `git diff --check`: PASS for both skill trees and both evaluation artifacts.
