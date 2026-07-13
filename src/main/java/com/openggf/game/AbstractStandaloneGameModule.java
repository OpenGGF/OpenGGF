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
 * manifest owner. Creators implement source-based game and touch-table creation,
 * object registry/placement encoding, audio profile, zone registry, and physics
 * provider. All other providers have no-ROM-safe neutral defaults. ROM-shaped
 * creation methods deliberately throw.</p>
 */
@ModApi
public abstract class AbstractStandaloneGameModule implements GameModule {
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

    @Override public abstract Game createGame(GameDataSource source);
    @Override public abstract TouchResponseTable createTouchResponseTable(GameDataSource source)
            throws java.io.IOException;
    @Override public abstract ObjectRegistry createObjectRegistry();
    @Override public abstract ObjectPlacementEncoding getObjectPlacementEncoding();
    @Override public abstract GameAudioProfile getAudioProfile();
    @Override public abstract ZoneRegistry getZoneRegistry();
    @Override public abstract PhysicsProvider getPhysicsProvider();

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
