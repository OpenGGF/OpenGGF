# Player-art transfer plan (2026-10-31)

Base: Sol candidate's data model (one module-supplied plan, delete GameId and dead
API). Graft: Opus candidate's internal shape (keep the service's algorithms and
state in place; switch on the plan's transfer model). Rejected from Sol: the
journal + sealed per-model machine decomposition. Reason: it rewrites a 1,788-line
trace-critical service for no behaviour change; the invariant-2 violation is the
GameId branching and the Sonic constants in shared code, which the plan removes
alone. Rejected from Opus: putting the handoff on `DynamicArtDmaServiceModel`;
`GameRules.dynamicArtDmaService()` keeps deciding WHEN a V-blank services
player art, the plan decides WHAT the transfer is. Also rejected for this pass:
`bindPlayer` API and retiring `EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT` (scope).

## Data (game/resources, no game.sonic* imports)

```java
public record PlayerArtTransferPlan(Map<String, PlayerArtBank> banks, TransferModel model) {
    public enum TransferModel {
        /** Sonic_LoadGfx stages into a RAM buffer; a later V-int writes it (S1). */
        STAGED_FOR_VINT,
        /** The DPLC queues DMA; ProcessDMAQueue retires it at V-blank (S2, KiS2). */
        QUEUED_FOR_VBLANK
    }
    public PlayerArtBank bank(String owner) { return banks.get(owner); } // null = unsupported owner
}
/** One owner's bank in the loaded ROM revision. stagingRam < 0 = none. */
public record PlayerArtBank(int romArtBase, int vramDestination,
                            int stagingRamAddress, int stagingByteLength,
                            int convertedRamAddress /* -1 unless converted into RAM before DMA */) {}
```

Supplied by `GameModule.getGameService(PlayerArtTransferPlan.class)`:
- `Sonic1GameModule`: STAGED_FOR_VINT; "sonic" bank with ROM base already
  resolved for the loaded revision (REV00 via `Sonic1RomProfile`), F000, C800, 2E0.
- `Sonic2GameModule`: QUEUED_FOR_VBLANK; sonic/tails/tails-tails as today.
- `Kis2GameModule`: the S2 plan plus its converted Knuckles bank (replaces
  `PlayerArtTransferProfile`).
- S3K / standalone: no plan → no player-art audit (unchanged behaviour).

## Service

- Constructor `DynamicArtLifecycleService(PlayerArtTransferPlan plan)`; null plan = no audit.
- Every `GameId gameId` parameter is removed from `observePlayerDplc`,
  `primePlayerDplc`, `observeRamDplc`. `gameId == S1` becomes
  `model == STAGED_FOR_VINT`; `gameId == S2` becomes `model == QUEUED_FOR_VBLANK`.
- Renames: `prepareS1`→`stageForVint`, `s1Preparation`→`stagedPreparation`,
  `s1PreparationAfterPreMainLoopTail`→`stagedPreparationAfterPreMainLoopTail`,
  `pendingS2TransferIds`→`vblankQueuedTransferIds`, `flushS1Preparation*`→`flushStagedPreparation*`.
  Same for `RewindState` components (no migration: unpublished format).
- `flushStagedPreparation` reads staging RAM/VRAM/length from the prepared
  owner's bank, not `PLAYER_PROFILES.get(GameId.S1).get("sonic")`.
- Converted-bank path reads `bank.convertedRamAddress()`; the separate
  `ConvertedBank` parameter disappears from the public API.

## Deleted

`PLAYER_PROFILES`, `ProductionArtProfile`, `PlayerArtSourceAddressProfile`,
`PlayerArtTransferProfile`, `completePlayerDplc` (every production caller passes
S1, where it returns immediately), `DynamicArtDecisionOwner`'s `GameId` and
`ConvertedBank` fields, the GameId overloads.

## Proof

Behaviour-preserving refactor. Existing tests named in the Sol candidate stay
green with only signature migration; a guard asserts `com.openggf.game.resources`
imports no `com.openggf.game.sonic*` package and references no `GameId` constant.

## Provenance

Task: project quality review follow-up, 2026-10-31, integration branch
`feature/ai-weakness-pass`. Two architect candidates were produced on different
models: Opus (handoff property on `DynamicArtDmaServiceModel`, smaller) and
GPT-6.1 Sol (module-supplied plan plus a transfer journal and sealed per-model
state machines). Sol's candidate found consumers the Opus one missed: the
converted KiS2 Knuckles bank (`PlayerArtTransferProfile`), the REV00 source
remap (`PlayerArtSourceAddressProfile`), and the S2 special-stage RAM path.
Implemented as `refactor` on `feature/ai-dplc-transfer-plan` (`105af3db7`):
37 files, 426 insertions, 466 deletions; 738/739 focused tests (the one red,
`TestSonic2InterlacePixels`, is red identically at base `a7d7ca8fe`), the new
`TestPlayerArtTransferPlanGuard`, and four player-art trace-replay classes.
