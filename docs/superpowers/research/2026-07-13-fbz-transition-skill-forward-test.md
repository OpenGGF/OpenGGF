# FBZ Transition Skill Forward Test

## RED pressure baseline

The pre-revision bring-up skill allowed a locally green first pass while independent review found these observable gaps:

- An ordinary SST could satisfy the generic offset policy while its inherited hook changed no native position.
- Rewind coverage inspected a helper boundary, not the live rewind/keyframe path; nested `LEVEL_LOAD` could expose a mid-reload state.
- The event flag could be consumed outside the native background-event state and continue through the wrong post-reload tail.
- Camera “current bound” setters also changed targets, silently defeating the intended current/target/full-bound restore.
- PLC and palette checks stopped at selector helpers rather than observing production scheduler and palette side effects.
- Later bound workers used convenient cumulative math/allocation instead of the disassembly's field widths, update order, and primitive.
- More than one phase could publish completion.
- Compatibility toggled configuration and directly invoked the event instead of traversing the owning results-to-worker route; no fresh native-off route followed the matrix.

Baseline pressure checklist: require a concrete ordinary SST offset, a real live-rewind boundary sequence, exact event-state/tail dispatch, independent camera current/target assertions, production PLC/palette observation, literal worker arithmetic/allocation, a sole publisher, and the full owning route for every compatibility cell plus a fresh native-off rerun.

## Unbiased evaluation prompt

Both evaluations receive the same request, without the checklist or expected findings:

> Use the s3k-zone-bring-up skill at the supplied path to independently review the current FBZ Act 1-to-2 seamless transition. Inspect the production diff, focused tests, and locked-on disassembly as needed. Decide whether the implementation/test evidence passes the skill's seamless-transition gate. Return a compact PASS/RED report with concrete evidence. Do not edit files, run traces, use prior review conclusions, or ask for expected findings.

## Results

- Baseline skill (433 lines): **RED, 6/8 rubric items enforced**. The fresh evaluator caught the no-op ordinary-SST offset, incomplete camera-state preservation, helper-only rewind and PLC/palette evidence, non-native worker/title cadence, and direct-call compatibility matrix. It did not require exact native event-state/tail dispatch or a sole-publisher proof.
- Revised skill, first pass: **REFACTOR, 7/8 rubric items enforced**. The fresh evaluator applied the new owner/pipeline requirements but left literal worker arithmetic/allocation `NOT ASSESSED`. The rubric now requires an explicit PASS/RED for all eight items and treats `NOT ASSESSED` as RED.
- Revised skill, second fresh pass: **GREEN, 8/8 rubric items explicitly assessed**. The evaluator returned concrete-family RED, event-state/tail RED, camera-field RED, live-rewind RED, production PLC/palette RED, literal worker arithmetic/allocation RED, sole-publication PASS, and full-route compatibility RED. `GREEN` here means the review skill rejected every false-green omission and allowed no unassessed category; the candidate implementation correctly remained RED.

The eight scored items are: concrete family/native position mutation; exact event state and post-reload tail; independent camera current/copy/bounds/targets; real live-rewind path with nested-boundary suppression; production PLC/palette observation; literal worker arithmetic/allocation; sole completion publisher; and full owning-route compatibility with a fresh native-off rerun.
