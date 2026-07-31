# S3K Boss Runtime Skill Forward Test

## Baseline prompt

```text
Read ONLY <repo>\.worktrees\fbz-complete-execution\.claude\skills\s3k-zone-bring-up\SKILL.md. Do not use tools beyond that one file; do not inspect code/disassembly/research/other skills. Scenario: an S3K boss uses installed callback pointers with return/fallthrough/tail-jump distinctions, signed byte/word timers and asymmetric sibling delays, unsigned byte-angle endpoint reflection, nonconventional trig axes, phase-mutated render priority/collision/solidity/visibility, a parent-required cyclic RewindRecreatable graph, and Obj_EndSignControl defeat flow. State the mandatory implementation/tests that this skill explicitly requires for those runtime details, exact sign/results publication/allocation lifecycle, and executable multi-sidekick/widescreen/donation/reset compatibility. Then list anything the skill does not explicitly require. Do not edit files or run trace.
```

The fresh evaluator read the untouched pre-update `.claude` mirror at SHA-256
`0F5306D2011CA498FE08449DB880C637557D176336877EC716039322AF169F0F`.

## Baseline raw result

```text
From the skill alone, these are explicit requirements.

Mandatory implementation and tests:

- Use locked-on disassembly behavior and test-first delivery for every behavior: focused RED, minimum disassembly-backed GREEN, regressions, then spec/code review.
- Before implementing shared timers/callbacks, trace:
  - Every tail-jumped shared routine.
  - Every callback/function-pointer field.
  - Field width.
  - Every competing consumer.
  - The real entry state and earliest reachable path when raw animation `$F4` and `Obj_Wait` share a callback.
- For native signed word predecrement, preserve the exact `subq.w #1` / `bmi` boundary: starting at `N`, it fires on update `N+1` after zero becomes `$FFFF`.
- Add focused tests for the last non-firing update, exact firing update, and the competing consumer’s later boundary. If oracle, code, and comments disagree, stop for independent disassembly adjudication.
- Capture each child’s role, phase, radius, delay, and special flags in recreation metadata; a shared parent spawn is insufficient.
- For the real `ObjectManager` graph, test capture → remove/diverge → restore and prove:
  - Exact child count/configuration.
  - Parent relinking despite recreation order.
  - No duplicate respawn/reconstruction.
  - Child lifetime follows the restored parent.
- Inventory every forward, reverse, cyclic, terminal, and cross-link.
- For every allocation site, specify:
  - Allocation primitive and search direction.
  - Partial-prefix behavior.
  - Rollback and retry behavior.
  - Independence from sibling tables.
  - Same-sweep first-tick eligibility.
- Test allocation failure at every ordinal, plus a real-manager rewind round trip proving exact cyclic topology, no missing-suffix healing, and no duplicate reconstruction.
- Run both rewind coverage guards and focused capture/restore round trips. New runtime/object state must deterministically support reset, checkpoint/death restoration, session reload, and rewind capture/relink.
- For the boss:
  - Classify damage source and collision-bearing object in every phase.
  - Add negative player-hit coverage where damage is scripted through child/self-impact, proving player attacks neither reduce health nor invoke an invented shield path.
  - Classify participant policy separately for activation, targeting, contact/hazard, damage authority, forced movement, and completion.
  - Tie standing/status/control-bit activation to the exact native slot and preserve P1-only, nearest-native-P1/P2, and all-player behavior distinctly.
- Trace defeat publication through the entire reachable chain:
  - Boss → sign/controller → results → global/event write → transition consumer.
  - Do not assign an event flag to the boss merely because the eventual defeat flow publishes it.
- Broad completion coverage must include boss phase behavior, rendering/collision/solidity/visibility consequences, transitions, reset/checkpoint/session/rewind, and all reachable dynamic objects, though the skill does not prescribe a specific assertion matrix for those individual phase mutations.

Compatibility audits must be executable, not a generic “route passed” claim:

- Multi-sidekick:
  - Test 0–3 sidekicks and duplicate-character banks.
  - Explicitly exercise shared mutable boss/zone/event state with at least three characters.
  - Cover participant policy, solids/hazards, carriers, forced movement, bosses, transitions, and full-route completion.
- Widescreen:
  - Test every supported viewport width.
  - Record world-coordinate event thresholds, camera locks/releases, spawning/culling, hazards, boss-arena containment, screen-edge transitions, premature activation, and unsafe falls/deaths for every width.
- Cross-game donation:
  - Test donation off and every external donor supported by the host.
  - Complete mandatory routes and identify mechanics blocked by unavailable abilities.
  - Any workaround must be an explicit semantic capability/profile rule with blocked route and rationale documented.
  - Preserve native behavior with donation off.
- After compatibility changes, rerun the strict locked-on native suite with donation and extension modes disabled.
- Reset/lifecycle compatibility must cover deterministic reset, death/restart, checkpoint, session reload, and rewind where those lifecycles cross the relevant behavior.

Not explicitly required by this skill:

- It does not explicitly require preserving or individually testing callback `return` versus fallthrough versus tail-jump control-flow distinctions; it only explicitly calls out tracing tail-jumped routines, callback fields, widths, and consumers.
- It gives an exact signed-word predecrement rule, but no equivalent signed-byte rule, mixed byte/word conversion rule, or explicit test for asymmetric sibling-delay values.
- It does not mention unsigned byte-angle masking, endpoint reflection, overshoot behavior, angle wraparound, or nonconventional trig-axis conventions.
- It does not prescribe a per-phase matrix for render priority, collision category, solidity, and visibility, nor exact ordering of those mutations relative to callbacks.
- It does not name or require the `RewindRecreatable` interface specifically, a particular parent-required constructor/API, or a concrete recreation algorithm; it requires the observable real-manager topology/relink behavior.
- It does not name `Obj_EndSignControl`, its exact fields/offsets, exact result values, publication frame ordering, or a dedicated end-to-end sign/results test. It requires tracing the generic sign/controller/results/global/transition chain and test-first coverage of implemented behavior.
- It does not prescribe exact viewport widths, donor identities, character configurations, route fixtures, commands, or a compatibility test harness.
- It does not specify exact reset call order or exact reset-state values for this boss family.
- It requires late comparison-only complete-run replay for full zone completion, but it does not make trace execution the focused oracle for these individual runtime details.
```

| ID | Baseline | Evidence |
|---|---|---|
| R45 | FAIL | Callback control transfer, signed-byte/asymmetric timing, unsigned angle endpoints, and helper axis contracts were all explicitly listed as missing. |
| R46 | FAIL | Only broad consequences were required; there was no per-transition render/collision/solidity/visibility matrix or native flicker movement gate. |
| R47 | FAIL | Observable graph restoration existed, but no parent-free shell, reference-free phase 1, or settled captured-prefix reconstruction algorithm was required. |
| R48 | FAIL | The generic publication chain existed, but not the three distinct end-control moments, converted-slot lifetime, allocation-success publication, or required real handoff test. |
| R49 | FAIL | Broad compatibility routes were executable, but the skill did not require owning-feature-boundary authority/lock tests or pristine object-manager/session reset with no static/graph leakage. |

Baseline verdict: **RED, 0/5**.

## Forward prompt

```text
Read ONLY <repo>\.worktrees\fbz-complete-execution\.agents\skills\s3k-zone-bring-up\SKILL.md. Do not use tools beyond that one file; do not inspect code/disassembly/research/other skills. Scenario: an S3K boss uses installed callback pointers with return/fallthrough/tail-jump distinctions, signed byte/word timers and asymmetric sibling delays, unsigned byte-angle endpoint reflection, nonconventional trig axes, phase-mutated render priority/collision/solidity/visibility, a parent-required cyclic RewindRecreatable graph, and Obj_EndSignControl defeat flow. State the mandatory implementation/tests that this skill explicitly requires for those runtime details, exact sign/results publication/allocation lifecycle, and executable multi-sidekick/widescreen/donation/reset compatibility. Then list anything the skill does not explicitly require. Do not edit files or run trace.
```

The forward evaluator was a fresh read-only agent with no baseline context. Both
updated mirrors are byte-identical at SHA-256
`0A0F82F1C53B2CFE5C8AA1085A8740707227ECD5ADFDC2DE114B38DCC76B8ACA`.

## Forward raw result

```text
Mandatory requirements stated by `s3k-zone-bring-up`:

- Model every installed callback/routine pointer as a distinct update state. Preserve whether installation returns, falls through, tail-jumps to movement/wait, or invokes the callback during the same sweep.
- Trace all tail-jumped shared routines, callback fields, field widths, and competing consumers before assigning timer/animation semantics.
- Test signed byte/word timers at the last non-firing call and exact firing call, including asymmetric sibling initial delays. For `subq.w #1` + `bmi`, firing is update `N+1`, after `0` becomes `$FFFF`. Where raw animation `$F4` competes with `Obj_Wait`, test both from the real entry state and implement the earliest reachable path; also test the competing consumer’s later boundary.
- Port byte-angle comparisons as unsigned 8-bit operations and preserve endpoint-visible reflection. Derive axes from the called helper’s register contract rather than assuming conventional sine/cosine screen axes.
- Treat render priority, collision flags, solidity, and visibility as routine-owned mutable state. Test callback transitions, defeat clearing, and converted-slot state. A hidden frame must remain solid if the native object is still a solid provider. Defeat flicker must run the native move/gravity/delete helper and toggle actual draw eligibility.
- Classify damage source and collision-bearing object for every boss phase. If damage is scripted through child/self-impact, add negative coverage proving player attacks neither reduce health nor trigger an invented shield path.

For the cyclic `RewindRecreatable` family:

- Capture every role, phase, radius, delay, and special flag in recreation metadata; a shared parent spawn is insufficient.
- Provide a parent-free probe/recreation shell if generic probing cannot construct the live signature.
- Do not resolve parent/sibling links during phase 1. Restore role/family scalars first, clear stale links, then relink in `afterRewindRestoreSettled`.
- Rebuild only the captured contiguous prefix and close a cycle only when the real terminal role exists.
- Inventory forward, reverse, cyclic, terminal, and cross-links, plus direct, steady, transient, and peak live counts.
- For every allocation site, record the actual primitive/search direction, partial-prefix behavior, rollback, retry, sibling-table independence, and same-sweep first-tick eligibility.
- Test allocation failure at every ordinal.
- Use a real `ObjectManager` capture → remove/diverge → restore test proving exact child count/configuration, recreation-order-independent parent relinking, exact cyclic topology, no missing-suffix healing, no duplicates, and child lifetime following the restored parent.
- New state must also have deterministic reset, checkpoint/death restore, session reload, and rewind capture/relink coverage, including both rewind coverage guards and focused round-trip tests.

For `Obj_EndSignControl` publication and allocation:

- Trace the complete reachable publication chain: boss → sign/controller → results → global/event write → transition consumer.
- Do not assign a boss-local event flag merely because the defeat chain eventually publishes one.
- Preserve three distinct moments:

  1. Immediate “end of level in effect” write.
  2. Later sign allocation.
  3. Still-later results/act-complete publication.

- Preserve the exact native allocation primitive, such as `AllocateObject` versus after-current `CreateChild6`.
- Check allocation success before publishing that the child exists.
- Keep the converted boss slot alive and non-solid only for the native controller’s actual lifetime.
- Execute the real sign/results handoff in a route test; a synthetic boss flag or substitute controller is explicitly insufficient.
- Separately classify activation, targeting, contact/hazard, damage authority, forced movement, and completion participation. Preserve P1-only, nearest-native-P1/P2, and all-player behavior distinctly.

Executable compatibility requirements:

- Key participant state by playable identity, never list position or a fixed native-slot array. Preserve native P1/P2 branch order and use allocation-free player iteration in hot paths.
- Test 0–3 sidekicks, duplicate-character banks, and shared mutable boss/event state with at least three characters. Cover solids/hazards, carriers, forced movement, bosses, transitions, and full-route completion.
- At the boss boundary, prove extra sidekicks cannot acquire native-only activation authority.
- Test every supported widescreen width, recording world-coordinate thresholds, boss containment, premature activation, unsafe falls/deaths, and camera lock/release behavior. Boss camera locks must remain world-coordinate constants.
- Test donation disabled and every supported external donor. Complete mandatory routes, identify unavailable-ability blockers, and only add documented semantic capability/profile workarounds. Donation-neutral traversal must not assume Spindash.
- Prove an object-manager/session reset recreates a pristine family without static control-bit or graph leakage.
- Rerun the exact native locked-on route after extension cases, with donation and extension modes disabled.

The skill does not explicitly specify:

- The concrete timer values, sibling delays, angle endpoints, trig equations, priority values, collision bits, health values, or phase constants for this particular boss.
- The exact boss-specific child count, graph shape, allocation primitive, allocation order, global/event field, results object, or transition target; it requires those to be derived and inventoried from the locked-on implementation.
- Specific supported viewport widths or donor identities; it says to test every one supported by the engine.
- A required Java class layout beyond the named framework and rewind contracts.
- A trace replay as the focused proof for these individual mechanics. It requires RED/GREEN focused tests, real `ObjectManager` rewind tests, and the real route handoff; a late comparison-only complete-run trace remains a separate complete-zone requirement.
- Compatibility-route testing beyond 0–3 sidekicks, although the underlying participant-state design must scale to the configured count.
```

| ID | Forward | Evidence |
|---|---|---|
| R45 | PASS | The output requires callback-state/control-transfer modeling, exact byte/word and asymmetric boundaries, unsigned endpoint reflection, and the helper's real axis contract. |
| R46 | PASS | The output requires transition/defeat/converted-slot state tests, collision independent of hidden render frames, and native flicker movement plus draw eligibility. |
| R47 | PASS | The output requires the conditional parent-free shell, scalar-only phase 1, stale-link clearing, settled phase 2, captured prefixes, and terminal-dependent cycle closure. |
| R48 | PASS | The output separates all three publication moments and requires exact allocation semantics/success, converted-slot lifetime, and the real route handoff. |
| R49 | PASS | The output requires owning boss-boundary authority/lock/donation tests, pristine manager/session reset, and the exact native rerun. |

Forward verdict: **GREEN, 5/5**, after baseline **RED, 0/5**.
