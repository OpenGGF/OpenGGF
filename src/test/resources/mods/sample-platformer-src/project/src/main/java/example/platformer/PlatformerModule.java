package example.platformer;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.game.save.SaveReason;
import com.openggf.level.Level;
import com.openggf.level.LevelDescriptor;
import com.openggf.level.ModLevel;
import com.openggf.level.objects.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clones {@code SampleStandaloneModule}'s structure: one zone, one act,
 * {@code loadLevel(0x400)} only, namespaced {@code zone-theme} music, no sidekick support,
 * the {@code zone/act/mainCharacter/sidekicks/clear} save-snapshot map, a silent
 * {@link GameAudioProfile}, and the same registry/init-profile inner-class shape.
 */
public final class PlatformerModule extends AbstractStandaloneGameModule {
    private final String owner;
    private final Level level;
    // Duplicated from BoltCharacter#defineSpeeds(): author classes may not hold non-primitive
    // static state, so this design-constants literal cannot be shared through a static field
    // (mirrors how SampleStandaloneModule's profile field duplicates SampleCharacter's).
    private final PhysicsProfile profile = new PhysicsProfile(
            (short) 0x20, (short) 0x80, (short) 0x20, (short) 0x480, (short) 0x780,
            (short) 0x20, (short) 0x14, (short) 0x50, (short) 0x20,
            (short) 0x80, (short) 0x80, (short) 0x1000, (short) 28, (short) 38,
            (short) 9, (short) 19, (short) 7, (short) 14, false, (short) 2);
    private final LevelInitProfile levelInitProfile = new PlatformerInitProfile();

    public PlatformerModule(String owner, Level level) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override public String getIdentifier() { return owner; }
    @Override public boolean supportsSidekick() { return false; }
    @Override public LevelInitProfile getLevelInitProfile() { return levelInitProfile; }
    @Override public Game createGame(GameDataSource source) { return new PlatformerGame(owner, source, level); }
    @Override public TouchResponseTable createTouchResponseTable(GameDataSource source) {
        return new TouchResponseTable(RomByteReader.fromBytes(new byte[] { 0, 0, 8, 8 }), 0, 2);
    }
    @Override public ObjectRegistry createObjectRegistry() { return new PlatformerRegistry(); }
    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() {
        return (x, y, objectId, subtype, flags, tracked, placement) ->
                new ObjectSpawn(x, y, objectId, subtype, flags, tracked, y, placement);
    }
    @Override public GameAudioProfile getAudioProfile() { return new SilentNativeAudioProfile(); }
    @Override public ZoneRegistry getZoneRegistry() { return new PlatformerZones(owner); }

    @Override public PhysicsProvider getPhysicsProvider() {
        return new PhysicsProvider() {
            @Override public PhysicsProfile getProfile(String characterType) { return profile; }
            @Override public PhysicsModifiers getModifiers() { return PhysicsModifiers.STANDARD; }
            @Override public GameRules getRules() { return GameRules.SONIC_2; }
        };
    }
    @Override public MusicReference getLevelMusicReference(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) throw new IllegalArgumentException("Unknown zone/act");
        return MusicReference.namespaced(owner, "zone-theme");
    }
    @Override public com.openggf.game.save.SaveSnapshotProvider getSaveSnapshotProvider() {
        return (reason, context) -> {
            boolean live = context.hasLiveGameplayState();
            var save = context.saveSessionContext();
            int zone = live ? context.levelManager().getCurrentZone() : save.startZone();
            int act = live ? context.levelManager().getCurrentAct() : save.startAct();
            if (zone >= getZoneRegistry().getZoneCount()) { zone = 0; act = 0; }
            return Map.of("zone", zone,
                    "act", act,
                    "mainCharacter", save.selectedTeam().mainCharacter(),
                    "sidekicks", save.selectedTeam().sidekicks(), "clear", false);
        };
    }

    private static final class PlatformerGame extends ModGame {
        private final Level level;
        private PlatformerGame(String owner, GameDataSource source, Level level) {
            super(owner, source); this.level = level;
        }
        @Override public Level loadLevel(int levelIndex) throws IOException {
            if (levelIndex != 0x400) throw new IOException("Unknown standalone level index: " + levelIndex);
            return level;
        }
        @Override public int getMusicId(int levelIndex) { return -1; }
    }

    private static final class PlatformerZones implements ZoneRegistry {
        private final String owner;
        private PlatformerZones(String owner) { this.owner = owner; }
        @Override public int getZoneCount() { return 1; }
        @Override public int getActCount(int zoneIndex) { requireZone(zoneIndex); return 1; }
        @Override public String getZoneName(int zoneIndex) { requireZone(zoneIndex); return "BOLT PLAINS"; }
        @Override public int[] getStartPosition(int zoneIndex, int actIndex) {
            require(zoneIndex, actIndex); return new int[] { 64, 160 };
        }
        @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
            requireZone(zoneIndex); return List.of(descriptor());
        }
        @Override public List<List<LevelDescriptor>> getAllZones() { return List.of(List.of(descriptor())); }
        @Override public int getMusicId(int zoneIndex, int actIndex) { require(zoneIndex, actIndex); return -1; }
        @Override public MusicReference getMusicReference(int zoneIndex, int actIndex) {
            require(zoneIndex, actIndex); return MusicReference.namespaced(owner, "zone-theme");
        }
        private static LevelDescriptor descriptor() { return new LevelDescriptor() {
            @Override public int levelIndex() { return 0x400; }
            @Override public int startX() { return 64; }
            @Override public int startY() { return 160; }
        }; }
        private static void requireZone(int zone) { if (zone != 0) throw new IllegalArgumentException("zone"); }
        private static void require(int zone, int act) {
            requireZone(zone); if (act != 0) throw new IllegalArgumentException("act");
        }
    }

    private static final class PlatformerRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) {
            return switch (String.valueOf(spawn.objectKey())) {
                case "sample-platformer:zapbug" -> new ZapBug(spawn);
                case "sample-platformer:springpad" -> new SpringPad(spawn);
                default -> throw new IllegalArgumentException("Unknown standalone object: " + spawn.objectKey());
            };
        }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "Standalone object"; }
        @Override public boolean hasObjectKey(String key) {
            return "sample-platformer:zapbug".equals(key) || "sample-platformer:springpad".equals(key);
        }
        @Override public List<String> browsableObjectKeys() {
            return List.of("sample-platformer:zapbug", "sample-platformer:springpad");
        }
    }

    private static final class PlatformerInitProfile extends AbstractLevelInitProfile {
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
            List<InitStep> steps = buildCoreSteps(context);
            if (context.isIncludePostLoadAssembly()) {
                steps.add(restoreCheckpointStep(context));
                steps.add(spawnPlayerStep(context));
                steps.add(resetPlayerStateStep(context));
                steps.add(initCameraStep());
                steps.add(initLevelEventsStep());
                if (!isPreviewCapture(context)) steps.add(requestTitleCardStep(context));
            }
            return List.copyOf(steps);
        }

        @Override protected InitStep levelEventTeardownStep() {
            return new InitStep("ResetPlatformerLevelEvents", "standalone", () -> { });
        }

        @Override protected InitStep perTestLeadStep() {
            return new InitStep("ResetPlatformerLevelEvents", "standalone", () -> { });
        }
    }

    private static final class SilentNativeAudioProfile implements GameAudioProfile {
        @Override public SmpsLoader createSmpsLoader(Rom rom) { return null; }
        @Override public SmpsSequencerConfig getSequencerConfig() { return null; }
        @Override public int getSpeedShoesOnCommandId() { return -1; }
        @Override public int getSpeedShoesOffCommandId() { return -1; }
        @Override public int getInvincibilityMusicId() { return -1; }
        @Override public int getExtraLifeMusicId() { return -1; }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
        @Override public Map<GameMusic, Integer> getMusicMap() { return Map.of(); }
    }
}
