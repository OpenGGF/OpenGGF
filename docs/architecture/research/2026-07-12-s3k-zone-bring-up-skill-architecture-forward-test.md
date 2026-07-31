# S3K Zone Bring-Up Skill Retained-Plane Forward Test

## Evaluation scenario

A fresh read-only agent received only the updated `s3k-zone-bring-up` skill and the same retained Plane-B, target-palette, Plane-A mutation, widescreen, and mid-transition rewind scenario used for the baseline.

## Exact evaluation prompt

```text
Read only <repo>\.worktrees\fbz-complete-execution\.agents\skills\s3k-zone-bring-up\SKILL.md. Do not read other repo docs or code. Answer this evaluation scenario only: A new S3K zone uses staged Plane-B row/column redraws, target-vs-normal palette writes during an overlay fade, Plane-A runtime mutations, and rewind mid-transition. From the skill alone, state the mandatory architecture and validation checks an implementer must perform for retained VDP tilemap state, palette visibility, dirty invalidation, widescreen, act scoping, and real runtime rewind. Be concrete. Then score whether the skill explicitly requires each: (A) distinguish full CPU layout cache from fixed 64x32 VDP ring and preserve native plane size under widescreen; (B) layer-aware dirty invalidation so Plane-A writes cannot rebuild retained Plane B; (C) target-palette writes must have a real visible fade consumer/materialization, not dead metadata; (D) retained state must be act/mode scoped and survive the next ensure/render; (E) real ROM-backed tall-cache capture→restore→reconcile→next-render test rather than synthetic helper-only tests. Mark PASS only if explicit, not inferred.
```

## Raw agent output

```text
From the skill alone, the implementer must:

- Model the CPU-side level/layout cache separately from the retained VDP nametable. The retained Plane-B nametable is a fixed native 64x32-cell ring; widescreen may enlarge the viewport, but must not enlarge this plane or alter native row/column update cadence.
- Keep delayed redraw position, direction, mode, and act gating in typed zone runtime state. Continue the ROM's ordinary row feed while staged redraws run.
- Route Plane-A block/tile mutations through the zone layout mutation pipeline and make dirty invalidation plane-aware: Plane-A mutation must not rebuild or erase retained Plane B; actual Plane-B, layout, or geometry invalidation must exit retained-authoritative mode and rebuild appropriately.
- Prove retained writes/restored bytes survive the production sequence: next real tilemap ensure, window preparation, upload, and render. A no-op manager/test double is insufficient.
- Scope retained-plane ownership and rewind payloads to only applicable acts and modes. Capture exact native ring bytes, or prove deterministic reconstruction of both staged and ordinary writes.
- Treat Target_palette and live Normal_palette as different state surfaces. A target write is incomplete unless the real fade lifecycle consumes it, or the same colors are safely materialized while the screen is opaque. Validate the first visible frame and rewind ownership of both target and normal palettes; registry metadata alone is explicitly rejected.
- Validate rewind with a ROM-backed tall-cache integration test: capture -> mutate -> restore -> reconcile -> next render. It must prove fixed-ring behavior and widescreen/native-plane semantics against the real manager; synthetic helper-only snapshot tests do not qualify.
- More generally, give new runtime state deterministic reset, checkpoint/death restore, session reload, and rewind capture/relink behavior; run both rewind coverage guards plus focused capture/restore round trips.

A PASS: Explicitly distinguishes the full CPU cache from a fixed 64x32 VDP ring and says widescreen must not expand the native plane or alter cadence.
B PASS: Explicitly requires layer-aware dirty invalidation and states Plane-A mutations must not rebuild/erase retained Plane B.
C PASS: Explicitly requires a production fade consumer or safe opaque-screen materialization; dead registry metadata is rejected.
D PASS: Explicitly requires act/mode-scoped retained ownership and rewind payloads, and explicitly tests survival through the next real ensure/render path.
E PASS: Explicitly mandates a ROM-backed tall-cache capture -> mutate -> restore -> reconcile -> next-render test and rejects synthetic helper-only coverage.
```

## GREEN result

| Assertion | Result | Forward-test evidence |
|---|---|---|
| R13 | PASS | Explicitly distinguishes the full CPU cache from the fixed 64x32 VDP ring and preserves native size/cadence under widescreen. |
| R14 | PASS | Explicitly requires layer-aware invalidation and protects retained Plane B from Plane-A mutations. |
| R15 | PASS | Requires a real fade consumer or opaque-screen materialization and rejects dead target metadata. |
| R16 | PASS | Requires act/mode scoping and survival through real ensure, window preparation, upload, and render. |
| R17 | PASS | Requires a ROM-backed tall-cache capture -> mutate -> restore -> reconcile -> next-render test and rejects synthetic-only coverage. |

Overall: **GREEN, 5/5** for R13-R17.

Structural validation:

- `.agents` and `.claude` mirrors are byte-identical.
- Both skills pass `skill-creator/scripts/quick_validate.py` with UTF-8 mode.
- `git diff --check` passes.
