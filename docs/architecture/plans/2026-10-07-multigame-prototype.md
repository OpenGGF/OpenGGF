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
5. Complete walkthrough/examples, focused tests and combined category validation;
   assess actual destination, commit/push feature only, open and register PR.

No public creator API, campaign completion, linked rewind or portable checkpoint
promise is added by this prototype. Those remain later blueprint gates.
