package com.openggf.editor.project;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameModule;
import com.openggf.game.MusicReference;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.level.Level;
import com.openggf.level.LevelDescriptor;
import com.openggf.level.MutableLevel;
import com.openggf.mods.code.ModLevelExportStagingValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** One immutable authored act; every load constructs fresh native gameplay data. */
public final class EditorProjectRuntimeModule extends DelegatingGameModule {
    private static final String OWNER = "openggf-editor";
    private static final ZoneKey KEY = ZoneKey.mod(OWNER, "project");
    public static final int LEVEL_INDEX = 0x400;
    public static final int DATA_ZONE_INDEX = 0x40;
    private final ModZoneLevelData data;
    private final ZoneRegistry zones;

    public static EditorProjectRuntimeModule prepare(GameModule base, EditorProjectDocument document,
                                                     int spawnX, int spawnY) throws IOException {
        Objects.requireNonNull(base); Objects.requireNonNull(document);
        var metadata = document.metadata();
        if (base.getGameId() != metadata.gameId())
            throw new IOException("Project game does not match the active host");
        metadata.validateAgainst(document.level());
        for (var object : document.level().getObjects()) {
            if (object.objectKey() != null && !base.createObjectRegistry().hasObjectKey(object.objectKey()))
                throw new IOException("Project requires unavailable object " + object.objectKey()
                        + ". Enable its dependency before playtesting; the saved object is retained.");
        }
        EditorProjectSpawnValidator.validate(document.level(), spawnX, spawnY);
        var request = request(document, Path.of("exports", "editor", "playtest"), spawnX, spawnY);
        var exporter = new FullLevelExporter(new ModLevelExportStagingValidator(base));
        ModZoneLevelData data = exporter.prepareHostData(MutableLevel.snapshot(document.snapshot()), request);
        base.getModZoneAdapter().validate(OWNER, data);
        return new EditorProjectRuntimeModule(base, metadata, data, spawnX, spawnY);
    }

    public static FullLevelExporter.ExportRequest request(EditorProjectDocument document, Path output,
                                                           int startX, int startY) {
        var m = document.metadata();
        return new FullLevelExporter.ExportRequest(output, m.name(), DATA_ZONE_INDEX, LEVEL_INDEX,
                startX, startY, m.music(), m.gameId(), m.objectZoneSet());
    }

    private EditorProjectRuntimeModule(GameModule base, EditorProjectMetadata metadata,
                                       ModZoneLevelData data, int startX, int startY) {
        super(base, "editor-project");
        this.data = data;
        var descriptor = new ProjectAct(LEVEL_INDEX, startX, startY);
        var contribution = new ModZoneRuntimeContribution(OWNER, "project", data,
                base.getModZoneAdapter().runtimeProfile(OWNER, data));
        MusicReference music = metadata.music() instanceof FullLevelExporter.ExportMusic.Stock stock
                ? MusicReference.stock(stock.id())
                : MusicReference.namespaced(((FullLevelExporter.ExportMusic.Track) metadata.music()).owner(),
                        ((FullLevelExporter.ExportMusic.Track) metadata.music()).localName());
        zones = new ZoneRegistry() {
            @Override public int getZoneCount() { return 1; }
            @Override public int getActCount(int zone) { check(zone, 0); return 1; }
            @Override public String getZoneName(int zone) { check(zone, 0); return metadata.name(); }
            @Override public int[] getStartPosition(int zone, int act) { check(zone, act); return new int[]{startX, startY}; }
            @Override public List<LevelDescriptor> getLevelDataForZone(int zone) {
                return zone == 0 ? List.of(descriptor) : List.of();
            }
            @Override public List<List<LevelDescriptor>> getAllZones() { return List.of(List.of(descriptor)); }
            @Override public int getMusicId(int zone, int act) {
                check(zone, act); return music instanceof MusicReference.Stock stock ? stock.musicId() : -1;
            }
            @Override public MusicReference getMusicReference(int zone, int act) { check(zone, act); return music; }
            @Override public ZoneKey zoneKey(int zone) { check(zone, 0); return KEY; }
            @Override public OptionalInt resolveZoneKey(ZoneKey key) { return KEY.equals(key) ? OptionalInt.of(0) : OptionalInt.empty(); }
            @Override public ModZoneRuntimeContribution modZoneRuntimeContribution(int index) {
                return index == LEVEL_INDEX ? contribution : null;
            }
            @Override public ZoneProgressionPlan.ZoneTopology progressionTopology() {
                return ZoneProgressionPlan.ZoneTopology.of(List.of(new ZoneProgressionPlan.ZoneMetadata(
                        1, ZoneProgressionPlan.Completion.TERMINAL)));
            }
            private void check(int zone, int act) {
                if (zone != 0 || act != 0) throw new IllegalArgumentException("An editor playtest owns exactly one act");
            }
        };
    }

    @Override public ZoneRegistry getZoneRegistry() { return zones; }
    // This act owns a flat authored layout. Its logical zone zero is not the
    // base game's campaign zone zero and must not initialize stock cutscenes.
    @Override public com.openggf.game.LevelEventProvider getLevelEventProvider() { return null; }
    @Override public MusicReference getLevelMusicReference(int zone, int act) { return zones.getMusicReference(zone, act); }
    @Override public Level loadLevelOverride(int index) throws IOException {
        if (index != LEVEL_INDEX) throw new IOException("An editor playtest cannot load a stock campaign level");
        return base().getModZoneAdapter().load(OWNER, data);
    }
    @Override public int getRemappedFeatureZone(int zone, int act, int levelZone) { return DATA_ZONE_INDEX; }
    @Override public int getRemappedFeatureAct(int zone, int act, int levelZone) { return 0; }
    @Override public int getRomAct(int zone, int act, int levelZone) { return 0; }
    private record ProjectAct(int levelIndex, int startX, int startY) implements LevelDescriptor { }
}
