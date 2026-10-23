package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.internal.QueuedPatternDmaPublication;
import com.openggf.game.internal.SpriteTablePublication;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.level.LevelManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native competition branch of Level:, retaining the ordinary ROM geometry/resource pipeline.
 *
 * <p>Obj_TitleCardInit deletes immediately for zones $E..$12, before queueing title archives
 * (sonic3k.asm:62108-62118). SpawnLevelMainSprites and loc_1B720 supply miniature player slots
 * and the complete x&lt;$800 placement list before the initial Process_Sprites/Render_Sprites
 * pair (sonic3k.asm:7881-7887,37449-37471). No campaign player, checkpoint, CPU or title owner
 * is created by this profile. The host owns the real supplied roster and shared managers.
 */
public final class S3kCompetitionLevelInitProfile extends AbstractLevelInitProfile
        implements SpriteTablePublication, QueuedPatternDmaPublication {
    /** Internal composition boundary implemented by the native competition world. */
    public interface Host {
        /** Level/loc_60DE clears native counters, RNG and pending publication before fresh assembly. */
        void prepareLoad(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                         List<S3kCompetitionPlayer> players);
        /** Native art/palettes and pattern-animation owner; retain the palette owner's rewind adapter. */
        void installArtAndPalette(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                                  List<S3kCompetitionPlayer> players);
        /** Bind these same human bodies and native LevelStates; never spawn/reset campaign actors. */
        void bindSuppliedParticipants(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                                      List<S3kCompetitionPlayer> players);
        /** Get_LevelSizeStart/DeformBgLayer, for both native cameras before the initial SST pass. */
        void initializeCamerasAndScroll(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                                        List<S3kCompetitionPlayer> players);
        /** loc_1B720: allocate the supplied ordered placements once, stopping on native full RAM. */
        void allocateInitialPlacements(LevelLoadContext context, S3kCompetitionArenaDefinition arena);
        /** Exactly one uncounted native player/object pass, in SST order, through the real world. */
        void processInitialNativeSprites(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                                         List<S3kCompetitionPlayer> players);
        /** Render_Sprites prepares the native page pair; it does not manufacture a VBlank. */
        void prepareInitialViews(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
                                 List<S3kCompetitionPlayer> players);
        /**
         * VInt8/10/A/C flip a completed page then upload the front. LAG uploads that front
         * without flipping; PALETTE_FADE's Do_ControllerPal uploads fixed Sprite_table.
         */
        void publishNativeSpritePages(PlcLifecyclePhase phase, LevelManager levelManager);
        void resetNativeLoadState();
    }

    private final Rom rom;
    private final Supplier<Arena> arenaSource;
    private final Supplier<List<S3kCompetitionPlayer>> playerSource;
    private final Host host;

    public S3kCompetitionLevelInitProfile(Rom rom, Arena arena,
            Supplier<List<S3kCompetitionPlayer>> playerSource, Host host) {
        this(rom, () -> arena, playerSource, host);
        Objects.requireNonNull(arena, "arena");
    }

    public S3kCompetitionLevelInitProfile(Rom rom, Supplier<Arena> arenaSource,
            Supplier<List<S3kCompetitionPlayer>> playerSource, Host host) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.arenaSource = Objects.requireNonNull(arenaSource, "arenaSource");
        this.playerSource = Objects.requireNonNull(playerSource, "playerSource");
        this.host = Objects.requireNonNull(host, "host");
    }

    private static List<S3kCompetitionPlayer> currentPlayers(List<S3kCompetitionPlayer> supplied) {
        var players = List.copyOf(supplied);
        if (players.isEmpty() || players.size() > 2)
            throw new IllegalArgumentException("One or two supplied native participants required");
        for (int slot = 0; slot < players.size(); slot++) {
            if (players.get(slot).slot() != slot)
                throw new IllegalArgumentException("Native participants must retain P1/P2 order");
        }
        return players;
    }

    @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
        Objects.requireNonNull(context, "context");
        boolean assemble = context.permitsInitialProcessSpritesRequest();
        var attempt = new LoadAttempt();
        var steps = new ArrayList<InitStep>();
        steps.add(ioStep("LoadCompetitionArenaDefinition", "Get_LevelSizeStart / loc_1B720", () -> {
            // World policy binds the restored roster before profile execution. Resolve once
            // per attempt: a later GP load may now own different miniature model identities.
            attempt.players = currentPlayers(playerSource.get());
            attempt.arena = S3kCompetitionArenaDefinition.load(rom,
                    Objects.requireNonNull(arenaSource.get(), "selected competition arena"));
            context.setRom(rom);
            context.setZone(attempt.arena.arena().zoneId()); context.setAct(0);
            // The native title object deletes before title PLCs. There is no campaign title
            // admission owner to hold a fresh-level runtime-art handoff open here.
            context.setShowTitleCard(false);
            context.setTitleCardRequiredInHeadlessMode(false);
            context.setQueueFreshLevelRuntimeArt(false);
            if (assemble) host.prepareLoad(context, attempt.arena, attempt.players);
        }));
        int artSteps = 0;
        int playerSteps = 0;
        for (InitStep core : buildCoreSteps(context)) {
            switch (core.name()) {
                case "InitArt" -> {
                    artSteps++;
                    steps.add(new InitStep("InitCompetitionArtAndPalette", "Level/loc_60B4: PLC6; Pal_Level_2P",
                            () -> host.installArtAndPalette(context, attempt.arena, attempt.players)));
                }
                case "InitPlayerAndCheckpoint" -> {
                    playerSteps++;
                    if (assemble) steps.add(new InitStep("BindCompetitionParticipants", "SpawnLevelMainSprites_SpawnPlayers",
                            () -> host.bindSuppliedParticipants(context, attempt.arena, attempt.players)));
                }
                default -> {
                    steps.add(core);
                    if (core.name().equals("LoadLevelData")) steps.add(new InitStep("VerifyCompetitionArenaIdentity",
                            "SpriteLocPtrs uses the selected native act-zero arena", () -> {
                        if (context.getLevel() == null
                                || context.getLevel().getZoneIndex() != attempt.arena.arena().zoneId())
                            throw new IllegalStateException("Decoded level does not match the selected native arena");
                    }));
                }
            }
        }
        if (artSteps != 1 || playerSteps != 1)
            throw new IllegalStateException("Core load profile changed its native art/player replacement boundaries");
        if (assemble) {
            steps.add(new InitStep("InitCompetitionCamerasAndScroll", "Get_LevelSizeStart / DeformBgLayer",
                    () -> host.initializeCamerasAndScroll(context, attempt.arena, attempt.players)));
            steps.add(new InitStep("LoadAllCompetitionPlacements", "Load_Sprites / loc_1B720: x < $800",
                    () -> host.allocateInitialPlacements(context, attempt.arena)));
            steps.add(new InitStep("ProcessInitialCompetitionSprites", "loc_6468: Process_Sprites",
                    () -> host.processInitialNativeSprites(context, attempt.arena, attempt.players)));
            steps.add(new InitStep("PrepareInitialCompetitionViews", "loc_6468: Render_Sprites",
                    () -> host.prepareInitialViews(context, attempt.arena, attempt.players)));
        }
        // The host just performed the miniature prelude. Requesting the common setup token would
        // dispatch campaign player routines a second time through InitialProcessSpritesExecutor.
        return List.copyOf(steps);
    }

    /** The shared single SAT is not the native competition's page pair. */
    @Override public boolean publishesSpriteTable(PlcLifecyclePhase phase) { return false; }
    /** Competition numeric HUD and timers are executable goal-owned SST records. */
    @Override public boolean updatesHudCounters(PlcLifecyclePhase phase) { return false; }
    @Override public boolean advancesHudTimer(PlcLifecyclePhase phase) { return false; }

    @Override public void serviceQueuedPatternDma(PlcLifecyclePhase phase, boolean explicitDmaService,
            RuntimeArtCoordinator runtimeArtCoordinator, LevelManager levelManager) {
        // Competition arenas have no Water_flag. VInt_0_NoWater republishes the retained
        // front page even on lag, without flipping it or draining DMA (sonic3k.asm:618-650).
        // VInt8/10/A/C flip pending pages; fade Do_ControllerPal uploads fixed Sprite_table
        // instead (sonic3k.asm:927-943). The host owns those actual native page identities.
        boolean levelHandler = phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == PlcLifecyclePhase.NORMAL_PAUSE || phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
        boolean dmaHandler = levelHandler || phase == PlcLifecyclePhase.PALETTE_FADE;
        if (dmaHandler || phase == PlcLifecyclePhase.LAG) host.publishNativeSpritePages(phase, levelManager);
        if (!explicitDmaService && !dmaHandler) return;
        if (levelManager != null
                && levelManager.getAnimatedPatternManager() instanceof S3kCompetitionPatternAnimator animation)
            animation.publishAtVBlank();
        S3kRuntimeArtCoordinator.from(runtimeArtCoordinator).publishPatternModuleDma();
    }

    @Override protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetCompetitionLoadState", "Undoes the native competition world assembly",
                host::resetNativeLoadState);
    }
    @Override protected InitStep perTestLeadStep() { return levelEventTeardownStep(); }

    private static final class LoadAttempt {
        private S3kCompetitionArenaDefinition arena;
        private List<S3kCompetitionPlayer> players;
    }
}
