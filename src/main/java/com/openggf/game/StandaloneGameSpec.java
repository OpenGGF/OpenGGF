package com.openggf.game;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.RomByteReader;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.level.Level;
import com.openggf.level.LevelDescriptor;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/** Optional typed standalone assembly. Expert modules can still override every provider. */
@ModApi
public final class StandaloneGameSpec {
    @ModApi
    public record Act(int levelIndex, Level level, int startX, int startY, MusicReference music)
            implements LevelDescriptor {
        public Act {
            if (levelIndex < 0 || startX < 0 || startX > 65535 || startY < 0 || startY > 65535)
                throw new IllegalArgumentException("Invalid standalone act descriptor");
            Objects.requireNonNull(level, "level"); Objects.requireNonNull(music, "music");
        }
    }
    private record Zone(String name, List<Act> acts) { }
    private final String owner;
    private final List<Zone> zones;
    private final boolean sidekicks;
    private final Supplier<ObjectRegistry> objects;
    private final PhysicsProvider physics;
    private final GameAudioProfile audio;
    private final ObjectPlacementEncoding placements;
    private final LevelInitProfile init;
    private final SaveSnapshotProvider saves;
    private final byte[] touchResponses;

    private StandaloneGameSpec(Builder b) {
        owner=b.owner; zones=List.copyOf(b.zones); sidekicks=b.sidekicks; objects=b.objects;
        physics=b.physics; audio=b.audio; placements=b.placements; init=b.init;
        saves=b.saves == null ? defaultSaves() : b.saves; touchResponses=b.touchResponses.clone();
        if (zones.isEmpty()) throw new IllegalArgumentException("Standalone game needs at least one zone");
        Set<Integer> indices=new HashSet<>();
        for (Zone z : zones) for (Act a : z.acts()) {
            if (!indices.add(a.levelIndex())) throw new IllegalArgumentException("Duplicate standalone level index: " + a.levelIndex());
        }
    }
    public static Builder builder(String owner) { return new Builder(owner); }
    public AbstractStandaloneGameModule module() { return new AbstractStandaloneGameModule(this) { }; }
    public String owner() { return owner; }
    public boolean supportsSidekick() { return sidekicks; }
    public PhysicsProvider physics() { return physics; }
    public GameAudioProfile audio() { return audio; }
    public LevelInitProfile levelInitProfile() { return init; }
    public SaveSnapshotProvider saveSnapshotProvider() { return saves; }
    public ObjectPlacementEncoding placementEncoding() { return placements; }
    public ObjectRegistry createObjectRegistry() { return Objects.requireNonNull(objects.get(), "Object registry factory returned null"); }
    public TouchResponseTable createTouchResponseTable() {
        return new TouchResponseTable(RomByteReader.fromBytes(touchResponses.clone()), 0, touchResponses.length / 2);
    }
    public Game createGame(GameDataSource source) {
        return new ModGame(owner, source) {
            @Override public Level loadLevel(int index) throws IOException {
                for (Zone zone : zones) for (Act act : zone.acts()) if (act.levelIndex()==index) return act.level();
                throw new IOException("Unknown standalone level index: " + index);
            }
            @Override public int getMusicId(int index) { return -1; }
        };
    }
    public ZoneRegistry zoneRegistry() {
        return new ZoneRegistry() {
            private Zone zone(int z) { return zones.get(z); }
            private Act act(int z, int a) { return zone(z).acts().get(a); }
            @Override public int getZoneCount() { return zones.size(); }
            @Override public int getActCount(int z) { return zone(z).acts().size(); }
            @Override public String getZoneName(int z) { return zone(z).name(); }
            @Override public int[] getStartPosition(int z, int a) { Act act=act(z,a); return new int[]{act.startX(),act.startY()}; }
            @Override public List<LevelDescriptor> getLevelDataForZone(int z) { return List.copyOf(zone(z).acts()); }
            @Override public List<List<LevelDescriptor>> getAllZones() { return java.util.stream.IntStream.range(0,zones.size()).mapToObj(this::getLevelDataForZone).toList(); }
            @Override public int getMusicId(int z, int a) { act(z,a); return -1; }
            @Override public MusicReference getMusicReference(int z, int a) { return act(z,a).music(); }
        };
    }
    private SaveSnapshotProvider defaultSaves() {
        return (reason, context) -> {
            var save=context.saveSessionContext();
            int zone=context.hasLiveGameplayState() ? context.levelManager().getCurrentZone() : save.startZone();
            int act=context.hasLiveGameplayState() ? context.levelManager().getCurrentAct() : save.startAct();
            if (zone < 0 || zone >= zones.size() || act < 0 || act >= zones.get(zone).acts().size()) { zone=0; act=0; }
            return Map.of("zone",zone,"act",act,"mainCharacter",save.selectedTeam().mainCharacter(),
                    "sidekicks",save.selectedTeam().sidekicks(),"clear",false);
        };
    }
    @ModApi
    public static final class Builder {
        private final String owner;
        private final List<Zone> zones=new ArrayList<>();
        private boolean sidekicks;
        private Supplier<ObjectRegistry> objects=EmptyObjects::new;
        private PhysicsProvider physics=PhysicsProvider.fixed(PhysicsProfile.SONIC_2_SONIC,
                PhysicsModifiers.STANDARD, com.openggf.game.rules.GameRules.SONIC_2);
        private GameAudioProfile audio=GameAudioProfile.silentNative();
        private ObjectPlacementEncoding placements=(x,y,id,subtype,flags,tracked,index) -> new ObjectSpawn(x,y,id,subtype,flags,tracked,y,index);
        private LevelInitProfile init=new CoreInit();
        private SaveSnapshotProvider saves;
        private byte[] touchResponses=new byte[0];
        private Builder(String owner) { this.owner=ModKeySyntax.requireManifestId(owner); }
        public Builder zone(String name, Act... acts) {
            Objects.requireNonNull(name,"name"); List<Act> copy=List.of(acts);
            if (name.isBlank() || copy.isEmpty()) throw new IllegalArgumentException("Zone needs a name and acts");
            zones.add(new Zone(name,copy)); return this;
        }
        public Builder supportsSidekick(boolean supported) { sidekicks=supported; return this; }
        public Builder objects(Supplier<ObjectRegistry> factory) { objects=Objects.requireNonNull(factory); return this; }
        public Builder physics(PhysicsProvider provider) { physics=Objects.requireNonNull(provider); return this; }
        public Builder audio(GameAudioProfile profile) { audio=Objects.requireNonNull(profile); return this; }
        public Builder placementEncoding(ObjectPlacementEncoding encoding) { placements=Objects.requireNonNull(encoding); return this; }
        public Builder levelInitProfile(LevelInitProfile profile) { init=Objects.requireNonNull(profile); return this; }
        public Builder saveSnapshotProvider(SaveSnapshotProvider provider) { saves=Objects.requireNonNull(provider); return this; }
        public Builder touchResponses(byte... dimensions) {
            if ((dimensions.length & 1)!=0) throw new IllegalArgumentException("Touch dimensions need width/height pairs");
            touchResponses=dimensions.clone(); return this;
        }
        public StandaloneGameSpec build() { return new StandaloneGameSpec(this); }
    }
    private static final class EmptyObjects implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) { throw new IllegalArgumentException("No standalone objects registered"); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "Standalone object"; }
    }
    private static final class CoreInit extends AbstractLevelInitProfile {
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) {
            List<InitStep> steps=buildCoreSteps(context);
            if (context.isIncludePostLoadAssembly()) {
                steps.add(restoreCheckpointStep(context)); steps.add(spawnPlayerStep(context));
                steps.add(resetPlayerStateStep(context)); steps.add(initCameraStep()); steps.add(initLevelEventsStep());
                if (!isPreviewCapture(context)) steps.add(requestTitleCardStep(context));
            }
            return List.copyOf(steps);
        }
        @Override protected InitStep levelEventTeardownStep() { return new InitStep("ResetStandaloneLevelEvents","standalone",()->{}); }
        @Override protected InitStep perTestLeadStep() { return new InitStep("ResetStandaloneLevelEvents","standalone",()->{}); }
    }
}
