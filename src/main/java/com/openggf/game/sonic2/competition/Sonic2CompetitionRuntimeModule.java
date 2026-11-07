package com.openggf.game.sonic2.competition;

import com.openggf.game.Game;
import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.Sonic2GameOverFlowProvider;
import com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.Level;
import com.openggf.game.sonic2.titlecard.TitleCardManager;

import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/** S2-scoped native load composition. Installing this module does not advertise a mode. */
public final class Sonic2CompetitionRuntimeModule extends DelegatingGameModule {
    private final Sonic2NativeCompetitionHost host;
    private final Sonic2CompetitionLevelInitProfile profile;
    private final Sonic2CompetitionLevelEvents events;
    private final TitleCardManager nativeTitle;
    private final Sonic2CompetitionZoneFeatures features;
    private Sonic2CompetitionArtRuntime runtimeArt;
    private final Sonic2CompetitionLifecycle lifecycle;
    private final Sonic2CompetitionScrollSourceAdapter scroll;
    private Sonic2CompetitionVideoFrame videoFrame;

    public Sonic2CompetitionRuntimeModule(GameModule base, Sonic2NativeCompetitionHost host) throws IOException {
        super(base, "engine:s2-native-competition");
        if (base.getGameId() != GameId.S2) throw new IllegalArgumentException("S2 ROM module required");
        this.host = Objects.requireNonNull(host, "host");
        scroll = new Sonic2CompetitionScrollSourceAdapter(host.rom(), base.getScrollHandlerProvider().getZoneConstants());
        this.profile = new Sonic2CompetitionLevelInitProfile(host);
        if (!(base.getTitleCardProvider() instanceof TitleCardManager title))
            throw new IllegalArgumentException("S2 native title object owner required");
        nativeTitle = title;
        features = new Sonic2CompetitionZoneFeatures(host);
        this.events = new Sonic2CompetitionLevelEvents(host.session(), nativeTitle,
                host::deformNativeEntry, this::completeCameraPass);
        lifecycle = new Sonic2CompetitionLifecycle(host.rom(), host.session(), (slot, timeOver) -> {
            var context = host.context();
            var flow = context.getWorldSession().getGameModule().getGameOverFlowProvider();
            if (!(flow instanceof Sonic2GameOverFlowProvider nativeFlow))
                throw new IllegalStateException("Native S2 requires its actual fixed-card flow provider");
            nativeFlow.beginNativeGameOverCard(context.getObjectManager().getObjectServices(), slot, timeOver);
        }, host::checkpointEnvironment);
    }

    @Override public com.openggf.game.ScrollHandlerProvider getScrollHandlerProvider() { return scroll; }
    @Override public com.openggf.level.objects.ObjectRegistry createObjectRegistry() {
        return new com.openggf.game.sonic2.competition.state.Sonic2LevelOnlyObjectRegistry(super.createObjectRegistry());
    }

    @Override public LevelInitProfile getLevelInitProfile() { return profile; }
    @Override public LevelEventProvider getLevelEventProvider() { return events; }
    void initializeBoundaryWords() {
        events.initializeBoundaryWords();
        installDisplayArtOwners();
        initializeDisplay();
    }
    private void installDisplayArtOwners() {
        var context = host.context();
        var fields = nativeVramOwner().fields();
        // Ordinary S2 animation construction primes 1P script destinations. Rebuild
        // the derived field table once, then retire BOTH halves of the combined owner.
        fields.rebuild();
        var patterns = fields;
        try {
            var palettes = com.openggf.game.sonic2.Sonic2PaletteCycleInternalAccess.create(host.rom(),
                    context.getLevelManager().getCurrentLevel(), host.profile().romZone());
            com.openggf.level.LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context, patterns);
            com.openggf.level.LevelAnimationInternalAccess.installNativeAnimatedPaletteManager(context, palettes);
            context.getAnimatedTileChannelGraph().clear();
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private void initializeDisplay() {
        var context = host.context();
        var session = host.session();
        scroll.initialize(host.profile(), session);
        scroll.applyLoadedLayerDefinition(host.nativeArt());
        var planes = new Sonic2CompetitionPlanes();
        var planeScroll = new Sonic2CompetitionPlaneScroll();
        planeScroll.initialize(session.camera(0).getX(), session.camera(0).getY(),
                session.camera(1).getX(), session.camera(1).getY());
        var registers = new Sonic2CompetitionViewRegisters(session.camera(0).capture(), session.camera(1).capture());
        var draw = new Sonic2CompetitionPlaneDraw(host.nativeArt().workRamBytes());
        // C1 has terrain planes only during the title card. A2-S owns the live
        // Obj34 fill/reveal hook, without a draw-time plane reconstruction.
        planes.apply(draw.initialForeground(new Sonic2CompetitionPlaneDraw.CameraCopy(
                session.camera(0).getX(), session.camera(0).getY()),
                new Sonic2CompetitionPlaneDraw.CameraCopy(session.camera(1).getX(), session.camera(1).getY())));
        var background = scroll.owner().view(0).background();
        planes.apply(draw.initialBackground(host.profile().romZone(),
                new Sonic2CompetitionPlaneDraw.CameraCopy(background.x() >>> 16, background.y() >>> 16)));
        try {
            var pages = new Sonic2CompetitionSpritePages(new Sonic2CompetitionSatBuilder(
                    com.openggf.data.RomByteReader.fromRom(host.rom())));
            videoFrame = new Sonic2CompetitionVideoFrame(nativeVramOwner(), planeScroll, planes, draw,
                    pages, registers, this::displaySample);
            videoFrame.bindSpriteSource(com.openggf.data.RomByteReader.fromRom(host.rom()));
            videoFrame.bindDisplayState(new Sonic2CompetitionDisplayState() {
                // B supplies the Teleport_flag owner at integration. Never infer it from a timer.
                @Override public int teleportTimer() { return session.teleportTimer(); }
                @Override public boolean gamePaused() { return host.nativePause().gamePaused(); }
            });
            nativeVramOwner().bindVideoFrame(videoFrame);
            var registry = context.getRewindRegistry();
            for (var owner : List.<RewindSnapshottable<?>>of(planes, planeScroll, pages, registers)) {
                registry.deregister(owner.key()); registry.register(owner);
            }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private Sonic2CompetitionVideoFrame.Sample displaySample() {
        var first = scroll.owner().view(0); var second = scroll.owner().view(1);
        var level = host.context().getLevelManager().getCurrentLevel();
        var words = new ArrayList<Integer>(64);
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) {
            var rgb = level.getPalette(line).getColor(color);
            int r = ((rgb.r & 255) * 7 + 127) / 255;
            int g = ((rgb.g & 255) * 7 + 127) / 255;
            int b = ((rgb.b & 255) * 7 + 127) / 255;
            words.add((r << 1) | (g << 5) | (b << 9));
        }
        // Retain Level's explicit post-deform clears until the next real
        // completed deformation. The lead supplies the missing initial clock/seed.
        boolean cleared = videoFrame.planeScroll().postLoadScrollClear();
        var retained = videoFrame.registers().capture();
        int foreground1 = cleared ? retained.foreground1() : first.foregroundVScroll();
        int foreground2 = cleared ? retained.foreground2() : second.foregroundVScroll();
        int[] horizontal = cleared ? retained.horizontal().stream().mapToInt(Integer::intValue).toArray()
                : first.horizontalScroll();
        return new Sonic2CompetitionVideoFrame.Sample(
                (foreground1 << 16) | (first.backgroundVScroll() & 65535),
                (foreground2 << 16) | (second.backgroundVScroll() & 65535),
                horizontal, host.session().camera(0).capture(), host.session().camera(1).capture(),
                words, videoFrame.displayState());
    }
    TitleCardManager nativeTitle() { return nativeTitle; }
    void runNativeTitleSlots() { events.updateFixedInLevelObjectsBeforeDynamicObjects(); }
    Sonic2CompetitionZoneFeatures nativeFeatures() { return features; }
    Sonic2CompetitionLifecycle nativeLifecycle() { return lifecycle; }

    void clearCameraSample() { events.clearCameraSample(); }

    private void completeCameraPass(int slot, short oldX, short oldY, boolean frozen) {
        var camera = host.session().camera(slot);
        if (videoFrame != null) videoFrame.planeScroll().completeCameraPass(slot, oldX, oldY,
                camera.getX(), camera.getY(), frozen);
        // The ROM skips both deformation handlers when Scroll_lock is set.
        if (slot == 1 && !frozen) scroll.completeCameraPass(host.session(), host.context().getObjectManager().getVblaCounter());
        // A skipped/frozen camera call retains the previous native WORDs.
        if (frozen) return;
        // ScrollHoriz (s2.asm:18127-18131), local .doScroll, stores pixel delta << 8.
        // ScrollVerti (s2.asm:18285-18290), local .doScroll, rotates the 16.16 delta.
        // This canonical camera represents integer positions: its actual motion
        // maps to native 8.8 words, including zero. Arbitrary native fractional
        // state and other camera writers remain the root RAM owner's gate.
        lifecycle.recordCameraDisplacement(slot,
                (short) ((camera.getX() - oldX) << 8),
                (short) ((camera.getY() - oldY) << 8), !host.session().teleportClock().active(), true);
    }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return features; }
    @Override public RuntimeArtCoordinator createRuntimeArtCoordinator(HardwareTimingService timing) {
        if (runtimeArt != null) throw new IllegalStateException("Native S2 runtime already belongs to one world");
        runtimeArt = new Sonic2CompetitionArtRuntime(Objects.requireNonNullElse(
                super.createRuntimeArtCoordinator(timing), RuntimeArtCoordinator.NONE));
        return runtimeArt;
    }

    @Override public Game createGame(Rom rom) {
        requireArtRuntime();
        var game = super.createGame(rom);
        installNativeArt(rom);
        return game;
    }

    @Override public Game createGame(GameDataSource source) {
        requireArtRuntime();
        var game = super.createGame(source);
        installNativeArt(source.rom().orElseThrow(() -> new IllegalArgumentException("Native S2 requires its ROM")));
        return game;
    }

    private void installNativeArt(Rom rom) {
        // createGame replaces the base module's PLC every act. Fetch that fresh
        // service directly, before selecting this act's physical VRAM owner.
        var plc = Objects.requireNonNull(super.getGameService(Sonic2PlcService.class), "fresh S2 PLC service");
        videoFrame = null;
        requireArtRuntime().install(rom, plc, host.session());
    }

    private Sonic2CompetitionArtRuntime requireArtRuntime() {
        return Objects.requireNonNull(runtimeArt, "Native world must create its stable art coordinator first");
    }

    Sonic2CompetitionArtResidency nativeVramOwner() { return requireArtRuntime().requireOwner(); }
    void bindNativeArtPlayers() { if (runtimeArt != null) runtimeArt.bindPlayers(host.session()); }

    @Override public <T> T getGameService(Class<T> type) {
        var owner = runtimeArt == null ? null : runtimeArt.ownerOrNull();
        if (type == Sonic2CompetitionVideoFrame.class) return type.cast(videoFrame);
        if (type == Sonic2CompetitionArtResidency.class) return type.cast(owner);
        if (type == Sonic2PlcService.class) return type.cast(owner == null ? null : owner.plcService());
        if (type == PlcLifecycleService.class) return type.cast(owner == null ? null : owner.plcLifecycle());
        if (type == Sonic2CompetitionLifecycle.class || type == NativePlayerDeathPass.class)
            return type.cast(lifecycle);
        return super.getGameService(type);
    }

    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        // The stable art binding captures the actual PLC and physical VRAM as
        // one owner. Keep the production music/prize adapters, never a second FIFO.
        var adapters = new ArrayList<RewindSnapshottable<?>>();
        super.rewindAdapters().stream().filter(adapter -> !(adapter instanceof Sonic2PlcService))
                .forEach(adapters::add);
        adapters.add(lifecycle);
        return List.copyOf(adapters);
    }
    @Override public Level loadLevelOverride(int index) throws IOException {
        var terrain = (Sonic2CompetitionTerrain) host.decodeTerrain();
        terrain.bindFieldPatterns(nativeVramOwner().fields());
        return terrain;
    }
    @Override public boolean supportsSidekick() { return false; }
    @Override public TitleCardProvider getTitleCardProvider() { return null; }
}
