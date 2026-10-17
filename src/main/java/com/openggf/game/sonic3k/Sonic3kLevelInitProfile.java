package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.SidekickSpawnOffset;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;

import java.util.List;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;

/**
 * Sonic 3&K level initialization profile.
 * <p>
 * Aligned to the S3K {@code Level:} routine at {@code sonic3k.asm:7505} (65 steps
 * across phases A-Q). The teardown steps undo the state set up by that routine.
 * <p>
 * S3K-specific post-load characteristics:
 * <ul>
 *   <li>Sidekick X offset: -32px (ROM: {@code $20}), not S2's -40px</li>
 * </ul>
 */
public class Sonic3kLevelInitProfile extends AbstractLevelInitProfile
        implements com.openggf.game.internal.QueuedPatternDmaPublication,
        com.openggf.game.internal.SpriteTablePublication,
        com.openggf.game.internal.InitialLevelScrollPreparation {
    private final Sonic3kLevelEventManager levelEventManager;
    private final Sonic3kFreshTitleCardSst freshTitleCardSst = new Sonic3kFreshTitleCardSst();

    Sonic3kFreshTitleCardSst freshTitleCardSst() { return freshTitleCardSst; }

    public Sonic3kLevelInitProfile(Sonic3kLevelEventManager levelEventManager) {
        this.levelEventManager = levelEventManager;
    }

    @Override
    public boolean publishesSpriteTable(com.openggf.game.resources.PlcLifecyclePhase phase) {
        // Shipped single-player VInt8/C/10 and Do_ControllerPal upload Sprite_table.
        // VInt0's single-player lag branch deliberately leaves VDP SAT untouched.
        // Special-stage tables and competition page flipping have separate scene owners;
        // the sanctuary results backdrop publishes its own table.
        return phase != null && switch (phase) {
            case ORDINARY_LEVEL, CREDITS_DEMO, LEVEL_TITLE_CARD, NORMAL_PAUSE,
                    PALETTE_FADE, CREDITS_DEMO_FADE -> true;
            default -> false;
        };
    }

    @Override
    public boolean updatesHudCounters(com.openggf.game.resources.PlcLifecyclePhase phase) {
        // VInt8/10 -> Do_Updates -> UpdateHUD. VIntC and fade handlers upload
        // the SAT but do not rewrite the numeric HUD tiles.
        return phase == com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == com.openggf.game.resources.PlcLifecyclePhase.CREDITS_DEMO
                || phase == com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE;
    }

    @Override
    public boolean advancesHudTimer(com.openggf.game.resources.PlcLifecyclePhase phase) {
        return updatesHudCounters(phase)
                && phase != com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE;
    }

    @Override
    public void serviceQueuedPatternDma(com.openggf.game.resources.PlcLifecyclePhase phase,
                                         boolean explicitDmaService,
                                         com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator,
                                         com.openggf.level.LevelManager levelManager) {
        // ROM: ordinary/credits LevelLoop arms VInt8 (7886 -> 701 -> 764),
        // title card loc_62CC arms C (7738 -> 797 -> 836), normal pause arms
        // 10 -> 8 (1551 -> 697). Fade12 and special1C reach DMA indirectly via
        // Do_ControllerPal (4908 -> 849 / 904 -> 929 -> 940). The tempting
        // inference that fade12 omits DMA is wrong: its callee owns the drain.
        // VInt0 lag (519 -> 567..647) omits it. VInt14's Sega loading loop is
        // not a level/title-card phase. Unsupported loops remain closed.
        boolean nativeHandler = phase != null && switch (phase) {
            case ORDINARY_LEVEL, CREDITS_DEMO, LEVEL_TITLE_CARD, NORMAL_PAUSE,
                    PALETTE_FADE, CREDITS_DEMO_FADE, SPECIAL_STAGE, SPECIAL_STAGE_PAUSE -> true;
            default -> false;
        };
        if (!explicitDmaService && !nativeHandler) return;
        // The caller's existing token guard guarantees exactly-once dispatch.
        recordVIntDma(phase, runtimeArtCoordinator, levelManager);
        if (levelManager != null
                && levelManager.getAnimatedPatternManager() instanceof Sonic3kLevelAnimationManager animator) {
            animator.publishAniPlcAtVBlank();
        }
        S3kRuntimeArtCoordinator.from(runtimeArtCoordinator).publishPatternModuleDma();
    }

    /**
     * Records this V-int's Process_DMA_Queue work before the queues publish: object entries already submitted, then
     * Animate_Tiles uploads, then KosM completions (LevelLoop order). Diagnostic only; it decides nothing.
     */
    private static void recordVIntDma(com.openggf.game.resources.PlcLifecyclePhase phase,
                                      com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator,
                                      com.openggf.level.LevelManager levelManager) {
        var art = S3kRuntimeArtCoordinator.from(runtimeArtCoordinator);
        var ledger = art.vintDmaLedger();
        var services = levelManager != null && levelManager.getObjectManager() != null
                ? levelManager.getObjectManager().getObjectServices() : null;
        if (!ledger.romBound() && services != null) {
            try {
                var reader = services.romReader();
                // An accepted image shorter than the pinned VInt_8 code prices nothing (ROM_UNSUPPORTED).
                ledger.bindRom(reader.size() < 0x6170 ? java.util.Optional.empty()
                        : com.openggf.game.sonic3k.timing.S3kVIntDma.VInt8Transfers.tryDecode(reader.slice(0, 0x6170)));
                // Byte-exact prefix through SpecialVInt_Array routine-0 rts ($4E80E), including all operands.
                ledger.bindVIntRom(reader.size() >= 0x4E810
                        && com.openggf.game.sonic3k.timing.S3kVIntInstructionCycles.romMatches(reader.slice(0, 0x4E810)));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        var animateTiles = new java.util.ArrayList<com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry>();
        if (levelManager != null && levelManager.getAnimatedPatternManager() instanceof Sonic3kLevelAnimationManager animator) {
            for (int[] upload : animator.pendingAniPlcTransfers()) {
                animateTiles.add(new com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry(
                        com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Producer.ANIMATE_TILES,
                        upload[1] / 2, upload[0] * 32));
            }
        }
        // VInt_8: tst.b (Hyper_Sonic_flash_timer).w selects CPU CRAM writes instead of the palette DMA.
        boolean hyperFlash = services != null
                && services.playerQuery().mainPlayerOrNull() instanceof com.openggf.sprites.playable.AbstractPlayableSprite player
                && player.getSuperStateController() instanceof Sonic3kSuperStateController controller
                && controller.hyperFlashStateLive();
        // The console region is the configured REGION, the same owner FrameRateResolver and the ending credits read.
        String configuredRegion = services != null && services.configuration() != null
                ? services.configuration().getString(com.openggf.configuration.SonicConfiguration.REGION) : null;
        var region = "PAL".equalsIgnoreCase(configuredRegion)
                ? com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.VideoRegion.PAL
                : "NTSC".equalsIgnoreCase(configuredRegion)
                        ? com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.VideoRegion.NTSC
                        : com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.VideoRegion.UNKNOWN;
        // Engine-owned inputs of the V-int instruction figure: the physical Kos queue and the level's Water_flag.
        Boolean levelHasWater = services != null && services.waterSystem() != null && levelManager != null
                ? services.waterSystem().hasWater(levelManager.getCurrentZone(), levelManager.getCurrentAct()) : null;
        var instructionInputs = new com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.InstructionInputs(
                !art.directQueue().decompressionsPending(), levelHasWater);
        ledger.serviceVInt(phase, region, hyperFlash, animateTiles, art.pendingKosModuleDmaEntries(),
                instructionInputs);
    }

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = buildCoreSteps(ctx);
        if (ctx.permitsInitialProcessSpritesRequest()) {
            // Level/loc_6068 and loc_60DA fill Nem_decomp_queue before
            // loc_62B6 installs Obj_TitleCard, with no intervening VBlank.
            // Decode, preview, restoration and seamless loads do not run Level.
            steps.add(1, ioStep("SubmitFreshLevelTitlePlcs",
                    "Level/loc_6068, loc_60DA: submit before the loc_62CC wait",
                    () -> GameServices.module().getGameService(Sonic3kLevelTitlePlcService.class)
                            .beginFreshZoneTitle(GameServices.level().getCurrentZone(),
                                    GameServices.level().getCurrentAct(),
                                    com.openggf.game.session.ActiveGameplayTeamResolver.resolveMainCharacterCode(
                                            GameServices.configuration()))));
        }
        steps.addFirst(new InitStep("PrepareFreshTitleCardSst",
                "Level/loc_62B6: fixed title owner precedes layout placement",
                () -> freshTitleCardSst.prepare(ctx)));
        if (ctx.permitsInitialProcessSpritesRequest()) {
            // Same fresh-Level gate: $610E clears $F700-$F7FF (the three DPLC previous-frame bytes) and $61C6 empties
            // DMA_queue, both before loc_62B6. Restoration, decode, preview and seamless continuation never run Level.
            steps.addFirst(new InitStep("ResetFreshLevelDmaQueue",
                    "Level/loc_60DE $610E clearRAM Tails_CPU_interact,$100; loc_61BE $61C6 DMA_queue reset",
                    () -> {
                        if (GameServices.runtimeArtCoordinator() instanceof S3kRuntimeArtCoordinator art) {
                            art.vintDmaLedger().resetAtFreshLevel();
                        }
                    }));
            // Level/loc_60DE clears Lag_frame_count ($F62A), $58 bytes:
            // RNG_seed at $F636 lies inside it, before any new sprite dispatch.
            // This includes ending/stage-return loads, but not preview, pure
            // decode/restoration or seamless in-place continuation.
            steps.addFirst(new InitStep("ClearFreshLevelRng",
                    "Level/loc_60DE: clearRAM Lag_frame_count,$58 includes RNG_seed",
                    () -> GameServices.rng().setSeed(0)));
        }
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(new InitStep("PublishNativeCpuSavedEntryFlag",
                    "Tails_Init/loc13A10: native fresh or saved entry eligibility",
                    () -> {
                        var level = GameServices.level();
                        var saved = level.getBigRingReturn();
                        boolean selectedSaved2 = saved != null
                                && level.getTransitions().isLastStarPostHitSet()
                                && saved.originZone() == level.getCurrentZone()
                                && saved.originAct() == level.getCurrentAct();
                        // Fresh/LevelSelect clears Last_star_post_hit (asm10039);
                        // real checkpoint/Saved2 return publishes its nonzero
                        // bank before Tails_Init copies it (asm26155). The
                        // existing captured flag must outlive bank consumption
                        // because the engine defers CPU routine zero until
                        // the following ordinary player pass.
                        if (ctx.hasCheckpoint() || selectedSaved2) level.setLastStarPostHit();
                        else level.clearLastStarPostHit();
                    }));
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep(ctx));
            steps.add(initLevelEventsStep());
            steps.add(spawnSidekickStep());
            steps.add(initZonePlayerStateStep(ctx));
            steps.add(requestInitialProcessSpritesStep(ctx));
            if (!isPreviewCapture(ctx)) {
                steps.add(requestTitleCardStep(ctx));
            }
        }
        steps.add(new InitStep("FinishFreshTitleCardSstInstallation",
                "End the load-only fixed-slot installation scope",
                freshTitleCardSst::finishInstallation));
        return List.copyOf(steps);
    }

    /**
     * ROM: SpawnLevelMainSprites zone-specific player state (sonic3k.asm:8132).
     * Runs after sidekick spawn so both main player and sidekicks exist.
     * Sets falling animation, airborne flag, and jumping for zone intros
     * (HCZ1, MGZ1, and LRZ1 non-Knuckles; SSZ has no corresponding branch).
     */
    protected InitStep initZonePlayerStateStep(LevelLoadContext ctx) {
        return new InitStep("InitZonePlayerState",
            "S3K: SpawnLevelMainSprites — zone-specific player animation/air state",
            () -> {
                // SpawnLevelMainSprites chooses intro scripts from the native
                // Current_zone_and_act word (sonic3k.asm:8143-8293). An authored
                // host's logical zone index does not identify a ROM script.
                if (!Sonic3kLevel.currentLevelHasStockRomZoneIdentity()) return;
                // Level loc_6460 clears Update_HUD_timer and Level_started_flag
                // before initial Process_Sprites for the $1701 hub. Existing
                // level/camera snapshots already own both captured values.
                if (Sonic3kLevelResourceProfile.isHpzSanctuary(
                        GameServices.level().getCurrentZone(), GameServices.level().getCurrentAct())) {
                    GameServices.level().getLevelGamestate().pauseTimer();
                    GameServices.camera().setLevelStarted(false);
                }
                levelEventManager.applyZonePlayerStateForLoad(
                        ctx.hasCheckpoint()
                                || GameServices.level().hasBigRingReturn()
                                || GameServices.level().isBonusStageReturn());
            });
    }

    private InitStep initCameraStep(LevelLoadContext ctx) {
        return new InitStep("InitCamera",
                "S3K Get_LevelSizeStart / HPZ special-stage hub camera",
                () -> {
                    GameServices.level().initCameraForLevel();
                    // Level loc_6404 sets Level_started_flag=1 for each fresh
                    // load, before scene initialization/loc_6460 can suppress
                    // it. In particular, a Saved2 origin return must not retain
                    // the sanctuary's zero. Later AIZ/ending scene owners and
                    // InitZonePlayerState retain their native clear authority.
                    GameServices.camera().setLevelStarted(true);
                    int zone = GameServices.level().getCurrentZone();
                    int act = GameServices.level().getCurrentAct();
                    Sonic3kLevelResourceProfile profile =
                            Sonic3kLevelResourceProfile.resolve(zone, act);
                    if (ctx.hasCheckpoint() || profile.eventKind()
                            != Sonic3kLevelResourceProfile.EventKind.HPZ_SPECIAL_STAGE_HUB) {
                        return;
                    }
                    Sonic3kLevelResourceProfile.CustomLevelResources resources =
                            profile.requireCustomResources();
                    var camera = GameServices.camera();
                    int cameraX = GameServices.level().sanctuaryReentryStage().isPresent()
                            ? hpzReturnCameraX(GameServices.level()
                            .sanctuaryReentryStage().getAsInt())
                            : resources.cameraX();
                    camera.setX((short) cameraX);
                    camera.setY((short) resources.cameraY());
                    camera.setXCopy((short) cameraX);
                    camera.setYCopy((short) resources.cameraY());
                });
    }

    static int hpzReturnCameraX(int stageIndex) {
        return S3kSanctuaryRuntimeState.resultsCameraX(stageIndex);
    }

    @Override
    public void prepareInitialLevelScroll() {
        var level = GameServices.level();
        // An authored host's logical index is not a native ROM script identity.
        // Only Level: fresh stock assembly admitted the owning setup token;
        // special/results/preview/restoration loops carry no such admission.
        if (!(level.getCurrentLevel() instanceof Sonic3kLevel loaded)
                || !loaded.hasStockRomZoneIdentity()) return;
        // Level loc_6310 (asm:7759-7760) calls DeformBgLayer before LevelSetup and
        // the initial Process_Sprites/Render_Sprites pair. Reproduce the real
        // dispatch once so its first published scroll is already initialized;
        // post-restore derived reconstruction then observes the same state.
        level.recomputeParallaxOnlyForCurrentFrame();
    }

    @Override
    public InitialProcessSpritesLifecycle initialProcessSpritesLifecycle() {
        return InitialProcessSpritesLifecycle.LOAD_THEN_PROCESS_ONCE;
    }

    /**
     * ROM fresh-level assembly creates players and zone state before the
     * initial Load_Sprites/Process_Sprites setup sequence
     * (docs/skdisasm/sonic3k.asm:7849-7855, 7889-7906).
     */
    private InitStep requestInitialProcessSpritesStep(LevelLoadContext ctx) {
        return new InitStep("RequestInitialProcessSprites",
                "S3K: arm post-load Load_Sprites then Process_Sprites setup",
                () -> {
                    // Fresh Level loc_60DE clears ring timer/frame before the
                    // first Process_Sprites; core-only seamless loads skip this step.
                    if (GameServices.level().getAnimatedPatternManager() instanceof Sonic3kLevelAnimationManager animator)
                        animator.resetFreshLevelRingAnimation();
                    ctx.requestInitialProcessSpritesFromProfile(initialProcessSpritesLifecycle());
                });
    }

    /** S3K sidekick: -32px X, +4px Y (ROM: {@code player_pos - $20}, {@code player_pos + 4}). */
    @Override
    public SidekickSpawnOffset sidekickSpawnOffset() {
        return new SidekickSpawnOffset(-32, 4);
    }

    @Override
    public boolean preserveFreshGroundedStatusUntilFirstDispatch() {
        // ROM fresh-start frame 0 is already routine 2. Grounded control
        // selects Wait before Player_AnglePos detaches at the platform edge;
        // Animate_Sonic then publishes mapping $BA without an air-gravity tick
        // (sonic3k.asm:24740-24771; AniSonic05).
        return true;
    }

    @Override
    protected InitStep spawnSidekickStep() {
        return new InitStep("SpawnSidekick",
            "S3K: SpawnLevelMainSprites_SpawnPlayers — Tails at player_pos - $20, +4 Y",
            () -> {
                SidekickSpawnOffset offset = sidekickSpawnOffset();
                GameServices.level().spawnSidekicks(offset.xOffset(), offset.yOffset());
            });
    }

    /** S3K: title card request follows the normal post-load path. */
    @Override
    protected InitStep requestTitleCardStep(LevelLoadContext ctx) {
        return new InitStep("RequestTitleCard",
            "S3K: Obj_TitleCard",
            () -> {
                if (ctx.getZone() == 0x0D && ctx.getAct() == 1) {
                    // Obj_TitleCardInit deletes immediately at $D01. A caller
                    // omitting animation must not manufacture the ordinary
                    // omitted-title owner and its four KosM jobs first. Reach
                    // the existing semantic-suppression boundary instead.
                    var endingPresentation = new LevelLoadContext();
                    endingPresentation.setShowTitleCard(true);
                    GameServices.level().requestTitleCardIfNeeded(endingPresentation);
                } else GameServices.level().requestTitleCardIfNeeded(ctx);
            });
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS3kLevelEvents",
                "Undoes S3K LevelSetupArray dispatch and per-zone event handlers (AIZ, HCZ, etc.)",
                levelEventManager::resetState);
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetAizIntroPhaseState",
                "Resets all AizPlaneIntroInstance static phase state (scroll, terrain swap, decompression)",
                AizPlaneIntroInstance::resetIntroPhaseState);
    }

    @Override
    protected List<StaticFixup> gameSpecificFixups() {
        return List.of(
                new StaticFixup("ResetAizIntroPhaseState",
                        "AIZ intro phase state persists across level loads",
                        AizPlaneIntroInstance::resetIntroPhaseState)
        );
    }
}
