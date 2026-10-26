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
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService;
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

    public Sonic2CompetitionRuntimeModule(GameModule base, Sonic2NativeCompetitionHost host) throws IOException {
        super(base, "engine:s2-native-competition");
        if (base.getGameId() != GameId.S2) throw new IllegalArgumentException("S2 ROM module required");
        this.host = Objects.requireNonNull(host, "host");
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

    @Override public LevelInitProfile getLevelInitProfile() { return profile; }
    @Override public LevelEventProvider getLevelEventProvider() { return events; }
    void initializeBoundaryWords() { events.initializeBoundaryWords(); }
    TitleCardManager nativeTitle() { return nativeTitle; }
    void runNativeTitleSlots() { events.updateFixedInLevelObjectsBeforeDynamicObjects(); }
    Sonic2CompetitionZoneFeatures nativeFeatures() { return features; }
    Sonic2CompetitionLifecycle nativeLifecycle() { return lifecycle; }

    void clearCameraSample() { events.clearCameraSample(); }

    private void completeCameraPass(int slot, short oldX, short oldY, boolean frozen) {
        // A skipped/frozen camera call retains the previous native WORDs.
        if (frozen) return;
        var camera = host.session().camera(slot);
        // ScrollHoriz (s2.asm:18127-18131), local .doScroll, stores pixel delta << 8.
        // ScrollVerti (s2.asm:18285-18290), local .doScroll, rotates the 16.16 delta.
        // This canonical camera represents integer positions: its actual motion
        // maps to native 8.8 words, including zero. Arbitrary native fractional
        // state and other camera writers remain the root RAM owner's gate.
        lifecycle.recordCameraDisplacement(slot,
                (short) ((camera.getX() - oldX) << 8),
                (short) ((camera.getY() - oldY) << 8));
    }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return features; }
    @Override public RuntimeArtCoordinator createRuntimeArtCoordinator(HardwareTimingService timing) {
        if (runtimeArt != null) throw new IllegalStateException("Native S2 runtime already belongs to one world");
        runtimeArt = new Sonic2CompetitionArtRuntime(Objects.requireNonNullElse(
                super.createRuntimeArtCoordinator(timing), RuntimeArtCoordinator.NONE));
        return runtimeArt;
    }

    /** Cartridge convenience; session callers retain their actual data source. */
    public Game createGame(Rom rom) {
        return createGame(new com.openggf.game.RomDataSource(rom, "rom:s2-competition:explicit"));
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
        requireArtRuntime().install(rom, plc, host.session());
    }

    private Sonic2CompetitionArtRuntime requireArtRuntime() {
        return Objects.requireNonNull(runtimeArt, "Native world must create its stable art coordinator first");
    }

    Sonic2InterlaceVramService nativeVramOwner() { return requireArtRuntime().requireOwner(); }
    void bindNativeArtPlayers() { if (runtimeArt != null) runtimeArt.bindPlayers(host.session()); }

    @Override public <T> T getGameService(Class<T> type) {
        var owner = runtimeArt == null ? null : runtimeArt.ownerOrNull();
        if (type == Sonic2InterlaceVramService.class) return type.cast(owner);
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
    @Override public Level loadLevelOverride(int index) throws IOException { return host.decodeTerrain(); }
    @Override public boolean supportsSidekick() { return false; }
    @Override public TitleCardProvider getTitleCardProvider() { return null; }
}
