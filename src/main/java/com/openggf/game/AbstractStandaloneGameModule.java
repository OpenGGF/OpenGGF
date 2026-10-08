package com.openggf.game;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;

/**
 * Creator base for a no-ROM standalone module.
 *
 * <p>{@link #getGameId()} is fixed to {@link GameId#STANDALONE};
 * {@link #getIdentifier()} is also the title/save game code and must equal the
 * manifest owner. The optional {@link StandaloneGameSpec} constructor supplies
 * source-based games, registries, physics, launch initialization and save defaults.
 * No-argument subclasses retain the expert provider override path. Other providers
 * have no-ROM-safe neutral defaults; ROM-shaped creation methods deliberately throw.</p>
 */
@ModApi
public abstract class AbstractStandaloneGameModule implements GameModule {
    private final StandaloneGameSpec specification;
    public AbstractStandaloneGameModule() { specification = null; }
    protected AbstractStandaloneGameModule(StandaloneGameSpec specification) {
        this.specification = java.util.Objects.requireNonNull(specification, "specification");
    }
    private StandaloneGameSpec specification() {
        if (specification == null) throw new UnsupportedOperationException("Override this provider or construct with StandaloneGameSpec");
        return specification;
    }
    @Override public String getIdentifier() { return specification().owner(); }
    @Override public boolean supportsSidekick() { return specification == null ? GameModule.super.supportsSidekick() : specification.supportsSidekick(); }
    @Override public LevelInitProfile getLevelInitProfile() { return specification == null ? GameModule.super.getLevelInitProfile() : specification.levelInitProfile(); }
    @Override public com.openggf.game.save.SaveSnapshotProvider getSaveSnapshotProvider() { return specification == null ? GameModule.super.getSaveSnapshotProvider() : specification.saveSnapshotProvider(); }
    @Override public MusicReference getLevelMusicReference(int zone, int act) { return getZoneRegistry().getMusicReference(zone, act); }

    @Override public final GameId getGameId() { return GameId.STANDALONE; }
    @Override public final String getGameCode() { return getIdentifier(); }

    @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() {
        return PlayableCharacterRegistry.empty();
    }

    @Override public final Game createGame(Rom rom) {
        throw new UnsupportedOperationException("Standalone modules use GameDataSource");
    }

    @Override public final TouchResponseTable createTouchResponseTable(RomByteReader reader) {
        throw new UnsupportedOperationException("Standalone modules use GameDataSource");
    }

    @Override public Game createGame(GameDataSource source) { return specification().createGame(source); }
    @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) throws java.io.IOException {
        return specification().createTouchResponseTable();
    }
    @Override public ObjectRegistry createObjectRegistry() { return specification().createObjectRegistry(); }
    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() { return specification().placementEncoding(); }
    @Override public GameAudioProfile getAudioProfile() { return specification().audio(); }
    @Override public ZoneRegistry getZoneRegistry() { return specification().zoneRegistry(); }
    @Override public PhysicsProvider getPhysicsProvider() { return specification().physics(); }

    @Override public int getPlaneSwitcherObjectId() { return 0; }
    @Override public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F);
    }
    @Override public LevelEventProvider getLevelEventProvider() { return null; }
    @Override public RespawnState createRespawnState() { return new CheckpointState(); }
    @Override public LevelState createLevelState() { return new LevelGamestate(); }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return null; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return null; }
}
