# LevelManager Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drain concrete responsibilities from `LevelManager` so it behaves as a coordinator instead of owning playable art setup, dirty-region dispatch, water lifecycle, and checkpoint state.

**Architecture:** Keep `LevelManager` as the public compatibility facade and move cohesive behavior into package-local collaborators. Do not change level-load, render, mutation, or trace semantics; preserve existing method names used by callers.

**Tech Stack:** Java 21, Maven, JUnit 5, existing `com.openggf.level` package-private collaborator style.

---

## Requirements

- Reduce `LevelManager` size and direct responsibility count without changing public load/render APIs.
- Move playable sprite art initialization out of `LevelManager`.
- Move `MutableLevel` dirty-set and mutation-effect dispatch out of `LevelManager`.
- Move water provider loading, dynamic water advancement, playable underwater state, and checkpoint water restore out of `LevelManager`.
- Move checkpoint/respawn storage, checkpoint restore, and checkpoint rewind capture/restore out of `LevelManager`.
- Keep trace ghost pattern-bank reservation behavior available through `LevelManager.reserveSidekickPatternBank`.
- Leave seamless act-transition execution as the remaining documented follow-up because it is parity-heavy and cross-cuts the new checkpoint and water collaborators plus object, ring, camera, sidekick, and event state.

## Exploration Synthesis

- Player-art explorer found a clean seam around `initPlayerSpriteArt`, sidekick bank allocation, dust, Tails tails, palette contexts, and super-state setup.
- Transition explorer recommended a future `LevelActTransitionExecutor`, but warned that `executeActTransition` bypasses full `LevelInitProfile` by design and must preserve apparent/current act water semantics.
- Render/dirty explorer found rendering already lives in `LevelRenderer`; the actionable remaining render-adjacent seam was dirty-region and mutation-effect dispatch.
- Checkpoint/water extraction became safe after the initial split because transition reload code could keep calling `initWater(true)` and checkpoint restore facades while ownership moved underneath.

## Architecture Decision

- Add `LevelPlayableArtInitializer` in `com.openggf.level`.
- Add `LevelDirtyRegionDispatcher` in `com.openggf.level`.
- Add `LevelWaterCoordinator` in `com.openggf.level`.
- Add `LevelCheckpointCoordinator` in `com.openggf.level`.
- Keep `LevelManager` fields package-visible where existing collaborators already rely on that pattern.
- Add source guards to prevent the drained responsibilities from being reintroduced.
- Ratchet `LevelManager` effective source-line budget from 2771 to 2500.

## Implementation Plan

- [x] Add failing source guard for playable-art ownership.
- [x] Extract playable art initialization to `LevelPlayableArtInitializer`.
- [x] Preserve `refreshPlayableSpriteArt`, `computeSidekickBankOffsets`, and `reserveSidekickPatternBank` as facades.
- [x] Add failing source guard for dirty-region ownership.
- [x] Extract dirty-set and mutation-effect dispatch to `LevelDirtyRegionDispatcher`.
- [x] Add source guards for water lifecycle and checkpoint ownership.
- [x] Extract water lifecycle and playable-water state to `LevelWaterCoordinator`.
- [x] Extract checkpoint storage, restore, and rewind state to `LevelCheckpointCoordinator`.
- [x] Run compile/package verification.
- [ ] Future: extract `executeActTransition` and private transition helpers to `LevelActTransitionExecutor` with focused act-transition tests.

## Integration Report

- `LevelManager` now delegates playable art, dirty-region, water, and checkpoint work.
- New package-local collaborators own the drained behavior.
- Source guards enforce the new boundaries and lower the class-size budget.
- Existing unrelated baseline failures remain in rewind tests and unrelated source-size guards.

## End-to-End Review

- Behavioral changes were avoided; this is a pure ownership extraction.
- Main residual risk is constructor-time collaborator initialization in tests that use the deprecated throwing constructor; compile verification covered this.
- Seamless act transition is intentionally deferred to a follow-up because it has higher parity risk than the extracted seams and can now delegate checkpoint/water concerns instead of owning them directly.
