package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelState;
import com.openggf.game.OscillationManager;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kCheatFlags;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.LevelAnimationInternalAccess;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.rings.SpillAnimationState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Concrete miniature competition world composition. The outer loop owns input,
 * general timers and the PLC token; this owner supplies the native actor pass,
 * real ROM terrain, SST objects, per-human banks, and session transitions.
 * Opening this host does not advertise or activate a title-menu destination.
 */
public final class S3kCompetitionNativeHost implements S3kCompetitionScreen.Host,
        S3kCompetitionSession.Host, S3kCompetitionLevelInitProfile.Host,
        NativeCompetitionRoster, CompetitionArenaObjectInstance.NativeContext, S3kCompetitionDamage.Context {
    /** Render-only boundary; the native page producer owns admitted-pointer visibility writeback. */
    public interface Views {
        void drawArena(GameplayModeContext context, NativeCompetitionRoster roster, S3kCompetitionScroll scroll);
        void drawMenu(S3kCompetitionMenuScene.Scene scene);
        void prepareNativeSpritePages(
                GameplayModeContext context, NativeCompetitionRoster roster, S3kCompetitionScroll scroll);
        void publishNativeSpritePages(PlcLifecyclePhase phase, GameplayModeContext context);
        default void prepareNativeSpritePages(GameplayModeContext context, NativeCompetitionRoster roster,
                S3kCompetitionScroll scroll, S3kCompetitionSpritePages.Pair pair) {
            prepareNativeSpritePages(context, roster, scroll);
        }
        default void publishNativeSpritePages(PlcLifecyclePhase phase, GameplayModeContext context,
                S3kCompetitionSpritePages.Pair pair) { publishNativeSpritePages(phase, context); }
        default void drawArena(GameplayModeContext context, NativeCompetitionRoster roster,
                S3kCompetitionScroll scroll, S3kCompetitionPlanes planes, S3kCompetitionSpritePages.Pair pair) {
            drawArena(context, roster, scroll);
        }
        void closeArena();
        void setClearColor();
    }

    /**
     * Object-art-only initialization while its shared bridge is integrating.
     * It must not admit campaign playable DPLCs or an ordinary HUD.
     */
    public interface Binding {
        void initializeObjectArt(LevelManager level);
    }

    private final EngineContext engine;
    private final GameModule rootModule;
    private final InputHandler input;
    private final Supplier<PlcLifecycleFrame> frameSource;
    private final Consumer<GameplayModeContext> publishMode;
    private final S3kCompetitionRecords records;
    private final Consumer<byte[]> saveRecords;
    private final Views views;
    private final Binding binding;
    private final Rom rom;
    private final S3kCompetitionResources resources;
    private final S3kCompetitionAssets assets;
    private final S3kCompetitionArtProvider art;
    private final S3kCompetitionMenuScene menus;
    private final S3kMiniatureAnimation animator;
    private final S3kCompetitionScreen screen;
    private final Camera[] cameras;
    private final GameStateManager[] gameStates = {new GameStateManager(), new GameStateManager()};
    private final LevelState[] levelStates = {new NativeLevelState(0), new NativeLevelState(1)};
    private List<S3kCompetitionPlayer> players = List.of();
    private S3kCompetitionSession session;
    private NativeCompetitionWorld world;
    private S3kCompetitionSession.Arena selectedArena;
    private S3kCompetitionScroll scroll;
    private S3kCompetitionTerrain terrain;
    private S3kCompetitionPatternAnimator patterns;
    private S3kCompetitionPaletteAnimator palettes;
    private final S3kCompetitionPause pause = new S3kCompetitionPause();
    private S3kCompetitionPlanes planes;
    private S3kCompetitionSpritePages spritePages;
    // Actual player-slot Draw_Sprite calls awaiting the canonical object queue reset.
    // This synchronous attempt state is always drained or cleared before a frame checkpoint.
    private final List<NativeSpriteSubmissionAccess.Admission> pendingActorDraws = new ArrayList<>();
    private int newRecordIndex = -1;
    private boolean stepping, closeRequested, closing;

    /** Production composition receives its admitted iteration through the shared native loop scope. */
    public S3kCompetitionNativeHost(EngineContext engine, GameModule rootModule, InputHandler input,
            Consumer<GameplayModeContext> publishMode, S3kCompetitionRecords records,
            Consumer<byte[]> saveRecords, Views views, Binding binding) throws IOException {
        this(engine, rootModule, input, null, publishMode, records, saveRecords, views, binding);
    }

    /** Compatibility composition for callers which already own an explicit outer PLC token. */
    public S3kCompetitionNativeHost(EngineContext engine, GameModule rootModule, InputHandler input,
            Supplier<PlcLifecycleFrame> frameSource, Consumer<GameplayModeContext> publishMode,
            S3kCompetitionRecords records, Consumer<byte[]> saveRecords, Views views, Binding binding) throws IOException {
        this.engine = Objects.requireNonNull(engine); this.rootModule = Objects.requireNonNull(rootModule);
        this.input = Objects.requireNonNull(input); this.frameSource = frameSource;
        this.publishMode = Objects.requireNonNull(publishMode); this.records = Objects.requireNonNull(records);
        this.saveRecords = Objects.requireNonNull(saveRecords); this.views = Objects.requireNonNull(views);
        this.binding = Objects.requireNonNull(binding); rom = engine.roms().getRom();
        S3kCompetitionRuntimeModule.requireS3kModule(rootModule, "S3K module required");
        resources = S3kCompetitionResources.identify(rom);
        assets = new S3kCompetitionAssets(rom,resources); art = new S3kCompetitionArtProvider(assets);
        spritePages = new S3kCompetitionSpritePages(art);
        menus = new S3kCompetitionMenuScene(rom, assets); animator = S3kMiniatureAnimation.fromRom(rom,resources);
        cameras = new Camera[]{new Camera(engine.configuration()), new Camera(engine.configuration())};
        screen = new S3kCompetitionScreen(this);
    }

    public S3kCompetitionScreen screen() { return screen; }
    public NativeCompetitionWorld world() { return world; }
    public S3kCompetitionSession session() { return session; }
    public S3kCompetitionScroll scroll() { return scroll; }
    public S3kCompetitionResources resources() { return resources; }
    public S3kCompetitionAssets assets() { return assets; }
    public S3kCompetitionArtProvider artProvider() { return art; }
    public S3kCompetitionTerrain terrain() { return terrain; }
    public S3kCompetitionPlanes planes() { return planes; }
    public S3kCompetitionSpritePages spritePages() { return spritePages; }
    public S3kCompetitionPaletteAnimator palettes() { return palettes; }
    public S3kCompetitionPause pause() { return pause; }
    public int newRecordIndex() { return newRecordIndex; }
    @Override public List<S3kCompetitionPlayer> players() { return players; }
    @Override public AbstractPlayableSprite player(int slot) {
        return slot >= 0 && slot < players.size() ? players.get(slot).sprite() : null;
    }
    @Override public Camera camera(int slot) { return cameras[slot]; }
    @Override public GameStateManager gameState(int slot) { return gameStates[slot]; }
    @Override public LevelState levelState(int slot) { return levelStates[slot]; }

    @Override public void prepareRestore(GameplayModeContext context, Set<String> capturedCodes) {
        if (NativeCompetitionRoster.from(context.getWorldSession()) != this)
            throw new IllegalArgumentException("Restore context does not own this native competition roster");
        var sprites = context.getSpriteManager();
        for (var participant : players) {
            String code = "s3k-competition-p" + (participant.slot() + 1);
            if (!capturedCodes.contains(code) || sprites.getSprite(code) != null) continue;
            try {
                // Recreate construction identity only. The canonical sprite owner
                // overlays captured RAM, then bindRoster reconnects these models
                // and the current fixed tail/dust owners before object restore.
                var replacement = new S3kCompetitionPlayer(participant.slot(), participant.character(), 0, 0,
                        S3kCompetitionPlayer.readSpeeds(rom, participant.character(),resources));
                replacement.setAnimator(animator);
                sprites.addSprite(replacement.sprite());
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot reconstruct the ROM-backed native human", failure);
            }
        }
    }

    @Override public S3kCompetitionSession openArena(S3kCompetitionScreen.Launch launch) {
        if (stepping) throw new IllegalStateException("Native arena loads must occur after the current world step");
        Objects.requireNonNull(launch);
        try {
            selectedArena = launch.config().arena();
            var definition = S3kCompetitionArenaDefinition.load(rom, selectedArena,resources);
            boolean continuation = world != null && session != null
                    && session.config().mode() == S3kCompetitionSession.Mode.GRAND_PRIX
                    && launch.config().mode() == S3kCompetitionSession.Mode.GRAND_PRIX
                    && players.size() == launch.characters().size();
            if (!continuation) {
                closeArena();
                selectedArena = launch.config().arena();
                players = definition.createPlayers(rom, launch.characters(),resources);
                players.forEach(p -> p.setAnimator(animator));
                session = new S3kCompetitionSession(rom, launch.config(), this,resources);
                var profile = new S3kCompetitionLevelInitProfile(rom, () -> selectedArena, this::players, this,resources);
                var source = Objects.requireNonNull(rootModule.getScrollHandlerProvider(), "S3K scroll constants");
                var scrollProvider = new S3kCompetitionScrollProvider(source.getZoneConstants(), this::scroll,
                        this::players, this::mirrorCamera);
                var runtime = new S3kCompetitionRuntimeModule(rootModule, profile, art, scrollProvider,
                        new S3kCompetitionZoneFeatures(this::planes));
                world = NativeCompetitionWorld.open(rootModule, runtime, this, session, engine, publishMode);
            } else {
                for (int slot = 0; slot < players.size(); slot++) if (players.get(slot).character() != launch.characters().get(slot))
                    throw new IllegalArgumentException("Grand Prix continuation cannot change its native humans");
            }
            world.load(selectedArena.zoneId(), 0);
            return session;
        } catch (IOException failure) {
            closeArena(); throw new UncheckedIOException("Cannot load the ROM-backed native competition arena", failure);
        } catch (RuntimeException | Error failure) {
            closeArena(); throw failure;
        }
    }

    @Override public void prepareLoad(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        try {
            var initial = arena.createPlayers(rom, supplied.stream().map(S3kCompetitionPlayer::character).toList(),resources);
            for (int slot = 0; slot < supplied.size(); slot++) {
                supplied.get(slot).restore(initial.get(slot).capture()); supplied.get(slot).setAnimator(animator);
            }
            rebindPlayers(supplied); session.enterArena(arena.arena()); newRecordIndex = -1;
            pause.reset(); syncPauseFlags();
            world.context().getLevelManager().resetFrameCounter();
            world.context().getSpriteManager().setFrameCounter(0);
            world.context().getRng().setSeed(0); OscillationManager.reset();
            // Core parallax construction may query its handler before the later
            // camera-mirror step. Native BackgroundInit runs exactly once here.
            scroll = new S3kCompetitionScroll(rom, arena.arena(),0, S3kCompetitionSession.TOTAL_LAPS,resources);
            scroll.initializeFromPlayers(players.getFirst(), players.size() == 2 ? players.get(1) : null);
            terrain = null; patterns = null; palettes = null; planes = null;
            spritePages = new S3kCompetitionSpritePages(art); closeRequested = false;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public void installArtAndPalette(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        try {
            binding.initializeObjectArt(level());
            // A direct world switch may retain an outgoing campaign FROM_BLACK
            // presentation. Native competition fades its actual 64-word RAM bank.
            engine.graphics().clearPaletteFadePresentation();
            palettes = new S3kCompetitionPaletteAnimator(rom, context.getLevel(),
                    world.context().getPaletteOwnershipRegistry(), engine.graphics(),resources);
            palettes.prepareInitialPalette(assets.arenaPalette(arena.arena()));
            patterns = new S3kCompetitionPatternAnimator(rom, context.getLevel(), arena.arena(), this::scroll, engine.graphics(),resources);
            patterns.setAfterTilePass(this::preparePages);
            LevelAnimationInternalAccess.installNativeAnimatedPatternManager(world.context(), patterns);
            LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(world.context(), palettes);
            planes = new S3kCompetitionPlanes(rom, arena.arena(), level(),resources);
            planes.initialize();
            var registry = world.context().getRewindRegistry();
            registry.deregister(planes.key()); registry.register(planes);
            registry.deregister(spritePages.key()); registry.register(spritePages);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public void bindSuppliedParticipants(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        if (supplied.size() != players.size()) throw new IllegalStateException("Native load roster changed");
        level().resetLevelGamestate(levelStates[0]);
        terrain = new S3kCompetitionTerrain(level(), scroll.profile().yWrapMask(), () -> session,
                () -> scroll.view(0).camera().y());
        var registry = world.context().getRewindRegistry();
        registry.deregister(pause.key());
        registry.register(new RewindSnapshottable<S3kCompetitionPause.State>() {
            @Override public String key() { return pause.key(); }
            @Override public S3kCompetitionPause.State capture() { return pause.capture(); }
            @Override public void restore(S3kCompetitionPause.State value) { pause.restore(value); syncPauseFlags(); }
            @Override public void resetForMissingSnapshot() { pause.reset(); syncPauseFlags(); }
        });
        // Level's cleared RAM contains all three native shared ring-animation fields.
        level().getRingManager().getSpillAnimationState().restore(new int[]{0,0,0});
        registry.deregister(records.key()); registry.register(records);
        registry.deregister("s3k.competition.new-record");
        registry.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "s3k.competition.new-record"; }
            @Override public Integer capture() { return newRecordIndex; }
            @Override public void restore(Integer value) { newRecordIndex = value; }
        });
        registry.deregister("s3k.competition.game-state-2");
        registry.register(new RewindSnapshottable<GameStateSnapshot>() {
            @Override public String key() { return "s3k.competition.game-state-2"; }
            @Override public GameStateSnapshot capture() { return gameStates[1].capture(); }
            @Override public void restore(GameStateSnapshot value) { gameStates[1].restore(value); }
        });
    }

    @Override public void initializeCamerasAndScroll(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        var bounds = scroll.profile().bounds();
        for (Camera camera : cameras) {
            camera.setMinX((short) bounds.minimumX()); camera.setMaxX((short) bounds.maximumX());
            camera.setMinY((short) bounds.minimumY()); camera.setMaxY((short) bounds.maximumY());
        }
        mirrorCamera(scroll.view(0)); mirrorCamera(scroll.view(1));
        // Publish the already initialized native handler for LF0 snapshots.
        // Its init is intentionally inert: no second native scroll/camera pass.
        world.context().getParallaxManager().initZone(arena.arena().zoneId(), 0,
                cameras[0].getX(), cameras[0].getY());
    }
    private void mirrorCamera(S3kCompetitionScroll.View view) {
        Camera camera = cameras[view.slot()];
        camera.setX((short) view.camera().x()); camera.setY((short) view.camera().y());
        camera.setXCopy((short) view.camera().x()); camera.setYCopy((short) view.camera().y());
    }

    @Override public void allocateInitialPlacements(LevelLoadContext context, S3kCompetitionArenaDefinition arena) {
        var objects = objects();
        ObjectManagerInternalAccess.enableNativeCompetitionUpfrontPlacement(objects, 0x800);
        objects.reset(cameras[0].getX()); // Policy is installed before its one source-order allocation pass.
        // Object reset also clears the ring owner's external-slot bindings. This
        // fresh-load reset lets that owner reinstall its independent dispatcher.
        level().getRingManager().reset(cameras[0].getX());
        bindActorDrawPrefix(objects);
        var registry = world.context().getRewindRegistry();
        var adapter = ObjectManagerInternalAccess.nativeCompetitionUpfrontPlacementRewindAdapter(objects);
        registry.deregister(adapter.key()); registry.register(adapter);
        var pointers = ObjectManagerInternalAccess.nativeCompetitionCollisionPointersRewindAdapter(objects);
        registry.deregister(pointers.key()); registry.register(pointers);
        NativeSpriteSubmissionAccess.registerRewind(objects, registry);
        // SpawnLevelMainSprites writes Dust/Dust_P2 before the first Process_Sprites.
        for (var player : players) {
            int slot = player.slot();
            ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects,
                    S3kMiniatureDust.P1_FIXED_SLOT + slot, () -> new S3kMiniatureDust(slot));
        }
    }

    @Override public void processInitialNativeSprites(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        var objects = objects();
        try (var scope = objects.beginInitialProcessSprites(cameras[0].getX(), player(0),
                player(1) == null ? List.of() : List.of(player(1)))) {
            objects.freezeInitialCollisionResponseReadView(); objects.resetInitialCollisionResponseBuild();
            nativePlayerPass(LogicalInputSnapshot.neutral(), false);
            // The initial scope reset its queue BEFORE the miniature pass.
            drainActorDraws();
            objects.processInitialDynamicSlots(scope);
            session.afterFrame(); objects.finishInitialProcessSprites(scope);
        } finally {
            pendingActorDraws.clear();
        }
    }
    @Override public void prepareInitialViews(LevelLoadContext context, S3kCompetitionArenaDefinition arena,
            List<S3kCompetitionPlayer> supplied) {
        preparePages();
        // loc_6468 has Render_Sprites followed by Animate_Tiles. No common
        // initial-token executor is admitted by the miniature load profile.
        patterns.runInitialLevelAnimationPass();
        // loc_665A clears all 64 current words only after the initial sprite/tile pass.
        palettes.armLevelFade();
    }

    @Override public void stepArena(LogicalInputSnapshot logical, S3kCompetitionSession expectedSession) {
        if (session == null || expectedSession != session || world == null)
            throw new IllegalStateException("Native world/session has been retired");
        if (stepping) throw new IllegalStateException("Native competition cannot step recursively");
        stepping = true;
        try {
            var wait = pause.beginWait(gameStates[0].getLives());
            syncPauseFlags();
            if (wait == S3kCompetitionPause.Wait.PAUSE_WAIT) {
                if (frameSource == null) world.pauseVBlank();
                else world.pauseVBlank(Objects.requireNonNull(frameSource.get(), "Outer PLC token"));
                acceptNativePauseSample(logical);
                return;
            }
            if (frameSource == null) world.step(input, () -> nativePlayerPass(logical, true));
            else world.step(input, Objects.requireNonNull(frameSource.get(), "Outer PLC token"),
                    () -> nativePlayerPass(logical, true));
            session.afterFrame();
            switch (pause.completeBody()) {
                case TIME_ATTACK_SELECTION -> screen.requestTimeAttackSelection();
                case TITLE_SCREEN -> screen.requestTitleScreen();
                case NONE -> { }
            }
        } finally {
            // A player or object failure must not replay a partial actor pass later.
            pendingActorDraws.clear();
            stepping = false;
            if (closeRequested) closeArena();
        }
    }

    private void acceptNativePauseSample(LogicalInputSnapshot logical) {
        var p1 = nativeInput(logical.player1());
        var cheats = rootModule.getGameService(Sonic3kCheatFlags.class);
        pause.acceptSample(p1.held(), p1.pressed(), cheats != null && cheats.isSlowMotionEnabled(),
                session.config().mode() == S3kCompetitionSession.Mode.TIME_ATTACK);
        syncPauseFlags();
    }
    private void syncPauseFlags() {
        for (var state : gameStates) state.setGamePaused(pause.gamePaused());
    }
    private void nativePlayerPass(LogicalInputSnapshot logical, boolean counted) {
        pendingActorDraws.clear();
        if (counted) {
            acceptNativePauseSample(logical);
            world.context().getSpriteManager().setFrameCounter(level().getFrameCounter());
        }
        session.beforeFrame(level().getFrameCounter());
        var profile = scroll.profile();
        var bounds = new S3kCompetitionPlayer.Bounds(profile.xWrapMask(), profile.yWrapMask(),
                profile.bounds().minimumY() == -0x100, profile.bounds().minimumY());
        for (S3kCompetitionPlayer player : players) {
            // Level clears the absent P2 SST, leaving character_id=0. Sonic
            // still compares that byte and toggles its duplicate-view bit.
            int partner = players.size() == 2 ? players.get(player.slot() ^ 1).character().ordinal() : 0;
            if (player.beginNativeActorPass(partner) && player.character() == S3kCompetitionPlayer.Character.TAILS)
                ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects(), 96 + player.slot(), () -> new S3kMiniatureTail(player.slot()));
            PlayerInputState buttons = player.slot() == 0 ? logical.player1() : logical.player2();
            int dispatchedRoutine = player.routine();
            player.tick(nativeInput(buttons), terrain, bounds, session.levelFrameCounter());
            if (player.bodyVisible()) pendingActorDraws.add(new NativeSpriteSubmissionAccess.Admission(player.slot(), 2));
            scroll.recordPosition(player);
            // loc_12302/loc_123AA return after Draw_Sprite. A hurt landing or
            // death respawn to routine2 does not enter TouchResponse this pass.
            if (dispatchedRoutine != 2 || !player.touchEnabled()) continue;
            // Resolve native SST pointers anew for P2: P1 may have replaced a
            // fixed owner whose address was in the previous collision list.
            for (ObjectInstance object : ObjectManagerInternalAccess.previousCollisionResponseObjects(objects())) {
                boolean consumed = object instanceof CompetitionArenaObjectInstance arena && arena.touchNativePlayer(this, player)
                        || object instanceof CompetitionItemObjectInstance item && item.touchNativePlayer(player)
                        || object instanceof S3kCompetitionRingLoss ring && ring.touchNativePlayer(player);
                if (consumed) break;
            }
        }
    }
    private void bindActorDrawPrefix(ObjectManager manager) {
        NativeSpriteSubmissionAccess.bindBeforePassPrefix(manager, this::drainActorDraws);
    }
    private void drainActorDraws() {
        var calls = List.copyOf(pendingActorDraws);
        pendingActorDraws.clear(); // Clear before emission, including a failed queue callback.
        if (calls.isEmpty()) return;
        var manager = objects();
        for (var call : calls) NativeSpriteSubmissionAccess.submitSpilling(manager, call.slot(), call.bucket());
    }
    private static S3kCompetitionPlayer.Input nativeInput(PlayerInputState input) {
        return new S3kCompetitionPlayer.Input((input.heldMask() & 15)
                | InputActionMasks.toMegaDriveButtonBits(input.actionHeldMask()) | (input.startHeld() ? 0x80 : 0),
                (input.pressedMask() & 15) | InputActionMasks.toMegaDriveButtonBits(input.actionPressedMask())
                        | (input.startPressed() ? 0x80 : 0));
    }

    private void preparePages() {
        var completed = spritePages.prepare(objects(), players, scroll);
        views.prepareNativeSpritePages(world.context(), this, scroll, completed);
    }
    @Override public void publishNativeSpritePages(PlcLifecyclePhase phase, LevelManager levelManager) {
        if (world == null || levelManager != level()) throw new IllegalStateException("Retired native SAT publication owner");
        views.publishNativeSpritePages(phase, world.context(), spritePages.publish(phase));
    }
    @Override public void drawArena() {
        requireWorld(); views.drawArena(world.context(), this, scroll, planes, spritePages.uploaded());
    }
    @Override public void drawMenu(S3kCompetitionScreen.MenuView view) {
        try { views.drawMenu(menus.compose(view, records, newRecordIndex)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public void setClearColor() { views.setClearColor(); }
    @Override public void requestArena(S3kCompetitionSession.Arena arena) { screen.requestArena(arena); }
    @Override public void requestResults(S3kCompetitionSession.Result result) { screen.requestResults(result); }
    @Override public void saveTimeAttackRecord(S3kCompetitionSession.PlayerResult result, S3kCompetitionSession.Arena arena) {
        newRecordIndex = records.insert(arena, S3kCompetitionRecords.packCentiseconds(result.totalCentiseconds()), result.character());
        if (newRecordIndex >= 0) saveRecords.accept(records.encode());
    }
    @Override public void closeArena() {
        if (stepping) { closeRequested = true; return; }
        if (closing) return;
        closing = true;
        try {
            if (world != null) {
                var retired = world; world = null;
                if (SessionManager.getCurrentGameplayMode() == retired.context()) SessionManager.closeGameplaySession();
                views.closeArena();
            }
            resetNativeLoadState();
        } finally { closing = false; }
    }
    @Override public void resetNativeLoadState() {
        pendingActorDraws.clear();
        terrain = null; patterns = null; palettes = null; planes = null; scroll = null; closeRequested = false;
    }
    @Override public void rebindPlayers(List<S3kCompetitionPlayer> restored) {
        pendingActorDraws.clear();
        if (world != null && level().getObjectManager() != null) bindActorDrawPrefix(level().getObjectManager());
        players = List.copyOf(restored);
        players.forEach(player -> {
            player.setAnimator(animator);
            int slot = player.slot();
            player.setNativeDustWrites(new S3kCompetitionPlayer.NativeDustWrites() {
                @Override public void setAnimation(int value) {
                    var dust = currentDust(slot); if (dust != null) dust.setAnimation(value);
                }
                @Override public void startSkid() {
                    var dust = currentDust(slot); if (dust != null) dust.startSkid();
                }
            });
        });
        // Shared SpriteManager restore binds before objects, and the shared
        // camera adapter replaces actor references without resetting camera RAM.
    }
    private S3kMiniatureDust currentDust(int parentSlot) {
        // Resolve the physical a6 address after object recreation; never retain its retired owner.
        int slot = S3kMiniatureDust.P1_FIXED_SLOT + parentSlot;
        return objects().getActiveObjects().stream()
                .filter(object -> object instanceof S3kMiniatureDust dust && !dust.isDestroyed()
                        && dust.getSlotIndex() == slot)
                .map(object -> (S3kMiniatureDust) object).findFirst().orElse(null);
    }
    @Override public List<CompetitionItemObjectInstance> itemWheels() {
        return objects().getActiveObjects().stream().filter(o -> o instanceof CompetitionItemObjectInstance)
                .map(o -> (CompetitionItemObjectInstance) o).filter(o -> o.kind() == CompetitionItemObjectInstance.Kind.WHEEL)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }
    @Override public AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
            Supplier<? extends AbstractObjectInstance> factory) {
        ObjectManager manager = objects();
        return installReserved(manager,manager.allocateSlotAfter(parent.getSlotIndex()),factory);
    }
    @Override public AbstractObjectInstance allocateFree(Supplier<? extends AbstractObjectInstance> factory) {
        ObjectManager manager = objects();
        return installReserved(manager,manager.allocateDynamicSlot(),factory);
    }
    private AbstractObjectInstance installReserved(ObjectManager manager,int slot,
            Supplier<? extends AbstractObjectInstance> factory) {
        if (slot < 0) return null;
        try {
            var object = ObjectConstructionContext.with(manager.getObjectServices(), slot,
                    () -> Objects.requireNonNull(factory.get(), "Native allocated object"));
            manager.addDynamicObjectAtSlot(object, slot);
            return object;
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }
    @Override public AbstractObjectInstance overwriteProjectile(Supplier<? extends AbstractObjectInstance> factory) {
        return ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects(), 94, factory);
    }
    @Override public CompetitionArenaObjectInstance.NativeContext nativeObjectContext() { return this; }
    @Override public S3kCompetitionSession.ItemTerrain itemTerrain() { return Objects.requireNonNull(terrain); }
    @Override public int xWrapMask() { return scroll.profile().xWrapMask(); }
    @Override public int projectileYWrapMask() { return scroll.profile().yWrapMask(); }
    @Override public boolean playerHighPriority(int slot) { return players.get(slot).highPriority(); }
    @Override public int oscillation(int offset) { return OscillationManager.getByte(offset - 2); }
    @Override public int zoneId() { return session.arena().zoneId(); }
    @Override public int levelFrameCounter() { return session.levelFrameCounter(); }
    @Override public int trigger(int index) { return session.trigger(index); }
    @Override public void setTrigger(int index, int value) { session.setTrigger(index, value); }
    @Override public com.openggf.sprites.animation.SpriteAnimationSet animations(String key) { return assets.animations(key); }
    @Override public int oscillationByte(int offset) { return oscillation(offset); }
    @Override public int floorDistance(int x, int bottomY) { return terrain.floorDistance(x, bottomY); }
    @Override public int randomWord() { return world.context().getRng().nextWord(); }
    @Override public int bottomY() { return cameras[0].getMaxY(); }
    @Override public int wrapYMask() { return cameras[0].getMinY() == -0x100 ? projectileYWrapMask() : -1; }
    @Override public void playMusic(int nativeMusicId) {
        var profile = resources == S3kCompetitionResources.ORIGINAL_S3
                ? com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.SONIC_3
                : com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.LOCKED_ON;
        engine.audio().playMusic(profile.musicId(nativeMusicId));
    }
    void stopVisitMusic() { engine.audio().stopMusic(); }
    @Override public void playSfx(int id) { engine.audio().playSfx(id); }
    @Override public void playSfx(Sonic3kSfx sound) { engine.audio().playSfx(sound.id); }
    @Override public void hurt(S3kCompetitionPlayer player, int sourceX) {
        S3kCompetitionDamage.contact(session,this,player,sourceX);
    }
    @Override public SpillAnimationState spillAnimation() {
        return Objects.requireNonNull(level().getRingManager(),"Native shared ring clock").getSpillAnimationState();
    }
    @Override public void awardLife(int slot) {
        var state=gameStates[slot]; int nativeLives=(state.getLives()+1)&0xFF;
        while(state.getLives()>nativeLives) state.loseLife();
        while(state.getLives()<nativeLives) state.addLife();
    }
    @Override public void playExtraLifeMusic() { playMusic(Sonic3kMusic.EXTRA_LIFE.id); }
    @Override public int paletteColor(int line, int color) {
        return Objects.requireNonNull(palettes, "Native palette RAM").readCurrentColor(line, color);
    }
    @Override public void setPaletteColor(int line, int color, int nativeColor) {
        Objects.requireNonNull(palettes, "Native palette RAM").writeCurrentColor(line, color, nativeColor);
    }
    @Override public List<CompetitionArenaObjectInstance.RockDebrisPiece> rockDebris() { return assets.rockDebris(); }
    private void requireWorld() { if (world == null) throw new IllegalStateException("No active native competition world"); }
    private LevelManager level() { requireWorld(); return world.context().getLevelManager(); }
    private ObjectManager objects() { return Objects.requireNonNull(level().getObjectManager(), "Native object RAM"); }

    /** Native rings and lap clock; none of the campaign life-award or timer owners run. */
    private final class NativeLevelState implements LevelState {
        private final int slot;
        private NativeLevelState(int slot) { this.slot = slot; }
        @Override public void update() { } // Executable goal-owned HUD SSTs own the clock.
        @Override public int getRings() { return session == null ? 0 : session.rings(slot); }
        @Override public void addRings(int amount) { setRings(getRings() + amount); }
        @Override public void setRings(int rings) { requireSession().setNativeRings(slot, rings); }
        @Override public int getRingExtraLifeFlags() { return session==null?0:session.damage().extraLifeFlags(slot); }
        @Override public void setRingExtraLifeFlags(int flags) { requireSession().damage().setExtraLifeFlags(slot,flags); }
        @Override public boolean isTimeOver() { return false; } // Native timer saturation never invokes campaign death.
        @Override public String getDisplayTime() {
            int time = session == null ? 0 : session.timeCentiseconds(slot);
            return "%d:%02d".formatted(time / 6000, time / 100 % 60);
        }
        @Override public boolean shouldFlashTimer() { return false; }
        @Override public boolean getFlashCycle() { return session == null || (session.levelFrameCounter() & 8) != 0; }
        @Override public void pauseTimer() { requireSession().setNativeTimerRunning(slot, false); }
        @Override public void resumeTimer() { requireSession().setNativeTimerRunning(slot, true); }
        @Override public boolean isTimerPaused() { return session == null || session.nativeTimerFlag(slot) == 0; }
        @Override public int getElapsedSeconds() { return session == null ? 0 : session.timeCentiseconds(slot) / 100; }
        @Override public long getTimerFrames() { return session == null ? 0 : session.nativeLapTimerFrames(slot); }
        @Override public void setTimerFrames(long frames) {
            if (frames < 0 || frames > 36000) throw new IllegalArgumentException("Native lap timer exceeds ten-minute saturation");
            requireSession().setNativeLapTimerFrames(slot, (int) frames);
        }
        private S3kCompetitionSession requireSession() { return Objects.requireNonNull(session, "Native session bank"); }
    }
}
