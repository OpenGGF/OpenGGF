# Multigame prototype implementation

Accepted design: [process-host blueprint](../designs/2026-10-07-multigame-blueprint.md).
Original integration base: `6d817a9d74f135714f3da59ab9aab156cc09473e`.
Delivery: `feature/ai-multigame-prototype` PR against develop; no main-tree edits.

1. Prove exclusive live held-input admission and one production iteration, with
   per-worker native polling histories. Input child owns the small engine seam.
2. Boot isolated production workers, native GPU rendering and production final PCM.
   Worker child owns boot/media; host owns bounded versioned process transport.
3. Compose native triptych, title/how-to-play, ROM validation, all-ready start,
   focus audio, pause/restart/fault/exit and controller input.
4. Verify deterministic solo/shared and duplicate-game media, stale generations,
   common-control witness, sibling isolation, teardown, resource budgets and
   observed audiovisual output. Independently review engine ownership boundaries.
5. Complete walkthrough/examples, focused tests and combined category validation
   against actual published develop; prepare a clean verified implementation
   handoff and reconcile owned children/processes.
6. Hold feature push/PR for the user's root-owned Opus polish integration and
   separate promo-video task, then retain one feature PR against develop.

Steps 1–4 are implemented and observed. The composed package/audio/API focus
passed 218 tests with zero skips; native host/device and seven-cell isolation
checks passed. Nine required trace methods ran: six passed and the three S3K
assertions literally matched the shared clean 2fc baseline. The final combined
ordinary run matched all 28 inherited failures and 62 skips. Its two structural
guard regressions were repaired by extraction into the existing driver owner
and an exact JDK import. The repair passed 39 affected tests, package/SDK/compiled
API verification and all 672 fresh guard tests without skips. Rebuilt-package
GPU/device/common-input and seven-cell isolation/replay observations passed;
see the [evidence record](../validation/2026-10-07-multigame-prototype.md).
Step 5 is complete at the clean implementation handoff; step 6 is root-owned.
The documented intermittent default-WM startup and pane footer presentation
remain explicit stability/polish opportunities for the next stage.

No public creator API, campaign completion, linked rewind or portable checkpoint
promise is added by this prototype. Those remain later blueprint gates.
