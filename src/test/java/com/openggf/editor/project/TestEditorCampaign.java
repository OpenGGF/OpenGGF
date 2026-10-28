package com.openggf.editor.project;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.code.ModCampaignDefinition;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.tools.modsdk.ModJarValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorCampaign {
    @TempDir Path directory;

    @Test void projectReferencesAreOrderedUniqueBoundedAndRelative() {
        var current = directory.resolve("first.ogfproject");
        assertEquals(List.of(current), EditorCampaign.EMPTY.resolve(current));
        var campaign = new EditorCampaign(List.of("first.ogfproject", "acts/second.ogfproject"));
        assertEquals(List.of(current, directory.resolve("acts/second.ogfproject")), campaign.resolve(current));
        for (String invalid : List.of("../other.ogfproject", "/other.ogfproject", "other\\act.ogfproject", "act.json"))
            assertThrows(IllegalArgumentException.class, () -> new EditorCampaign(List.of(invalid)));
        assertThrows(IllegalArgumentException.class, () -> new EditorCampaign(List.of("first.ogfproject", "first.ogfproject")));
    }

    @ParameterizedTest @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void savedProjectsPackageAndAutomaticallyRegisterWithoutJavaOrCodeTrust(GameId game) throws Exception {
        Path first = save("first", game, null), second = save("second", game, null);
        var host = GameModuleRegistry.createStockModule(game);
        var result = new EditorCampaignExportService().export(List.of(first, second), request("campaign"), host);
        assertEquals(2, result.levels());
        assertTrue(new ModJarValidator().validate(result.outputJar()).valid());
        try (var zip = new ZipFile(result.outputJar().toFile())) {
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().endsWith(".class")));
            assertNotNull(zip.getEntry("campaign.json"));
            assertNotNull(zip.getEntry("levels/act-2/level.json"));
        }
        var entries = new DefaultModRepositoryScanner().scan(directory.toAbsolutePath());
        var descriptor = assertInstanceOf(ModDescriptor.class, entries.getFirst());
        assertFalse(descriptor.containsCode());
        assertNull(descriptor.manifest().entrypoint());
        assertEquals("campaign.json", descriptor.manifest().campaign());
        try (var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(
                new EffectiveModCatalog(List.of(descriptor)), Set.of())) {
            assertEquals(List.of("authored-campaign"), runtime.owners());
            var patches = runtime.newRegistrationPlan();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            assertEquals(1, patches.registrations().size());
            var context = new PatchContext(rom -> { throw new IOException("Registration must not read ROM art"); },
                    SonicConfigurationService.getInstance());
            var effective = patches.registrations().getFirst().patch().apply(host, context);
            var zones = effective.getZoneRegistry();
            int zone = zones.resolveZoneKey(ZoneKey.mod("authored-campaign", "campaign")).orElseThrow();
            assertEquals(2, zones.getActCount(zone));
            assertEquals(new ZoneProgressionPlan.Successor(zone, 1),
                    zones.progressionPlan().next(zones.progressionTopology(), zone, 0));
            assertEquals(ZoneProgressionPlan.Credits.INSTANCE,
                    zones.progressionPlan().next(zones.progressionTopology(), zone, 1));
            assertEquals(zone, effective.getDataSelectHostProfile().newGameDestination().zone());
            var acts = zones.getLevelDataForZone(zone);
            assertNotEquals(acts.getFirst().levelIndex(), acts.getLast().levelIndex());
            assertNotNull(zones.modZoneRuntimeContribution(acts.getLast().levelIndex()));
            assertTrue(runtime.registrationPlans().get("authored-campaign").preparedZones().stream()
                    .allMatch(com.openggf.mods.code.PreparedModZone::terminalCampaign));
        }
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".editor-campaign-")));
        }
    }

    @Test void invalidSequencesAndHostOrTeamChangesNeverPublishAPackage() throws Exception {
        Path first = save("first", GameId.S2, null), wrongHost = save("other-host", GameId.S1, null);
        var service = new EditorCampaignExportService();
        var host = GameModuleRegistry.createStockModule(GameId.S2);
        assertThrows(IOException.class, () -> service.export(List.of(first, first), request("duplicate"), host));
        assertThrows(IOException.class, () -> service.export(List.of(first, wrongHost), request("mixed-host"), host));
        Path wrongTeam = save("other-team", GameId.S2, new GameplayLaunchTeam(CharacterKey.SONIC, List.of()));
        assertTrue(assertThrows(IOException.class, () -> service.export(
                List.of(first, wrongTeam), request("mixed-team"), host)).getMessage().contains("same required team"));
        assertFalse(Files.exists(directory.resolve("duplicate.jar")));
        assertFalse(Files.exists(directory.resolve("mixed-host.jar")));
        assertFalse(Files.exists(directory.resolve("mixed-team.jar")));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".editor-campaign-")));
        }
    }

    @Test void trustedHostGizmosRemainDataOnlyAndKeepTheirAuthoredProperties() throws Exception {
        var properties = new com.openggf.level.objects.authored.AuthoredObjectProperties.MusicZone(
                32, 32, com.openggf.game.MusicReference.stock(0x81), true);
        Path project = save("gizmo", GameId.S2, null, properties);
        var result = new EditorCampaignExportService().export(List.of(project), request("gizmo"),
                GameModuleRegistry.createStockModule(GameId.S2));
        try (var assets = ModAssetRoot.jar(directory, result.outputJar())) {
            var campaign = ModCampaignDefinition.read(assets, "campaign.json", "s2");
            var definition = campaign.validateLevels(assets).getFirst();
            assertEquals(properties, definition.objects().getFirst().authoredProperties());
        }
    }

    @Test void strictCampaignReaderRejectsCyclesDuplicatesTraversalUnknownFieldsAndMixedHosts() throws Exception {
        String valid = """
                {"name":"Test", "game":"s2", "localKey":"campaign", "levels":[
                  {"id":"first", "level":"levels/first/level.json", "next":"second"},
                  {"id":"second", "level":"levels/second/level.json", "next":null}]}
                """;
        assertEquals(2, parse(valid).levels().size());
        for (String invalid : List.of(
                valid.replace("\"next\":null", "\"next\":\"first\""),
                valid.replace("\"id\":\"second\"", "\"id\":\"first\""),
                valid.replace("levels/second/level.json", "levels/first/level.json"),
                valid.replace("levels/second/level.json", "../second/level.json"),
                valid.replace("\"game\":\"s2\"", "\"game\":\"s1\""),
                valid.replace("\"game\":\"s2\"", "\"unknown\":true,\"game\":\"s2\""),
                valid.replace("\"next\":null", "\"next\":null,\"next\":null"))) {
            assertThrows(IOException.class, () -> parse(invalid));
        }
    }

    private ModCampaignDefinition parse(String json) throws Exception {
        Path root = Files.createTempDirectory(directory, "parse-");
        Files.writeString(root.resolve("campaign.json"), json, StandardCharsets.UTF_8);
        try (var assets = ModAssetRoot.directory(directory, root, ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            return ModCampaignDefinition.read(assets, "campaign.json", "s2");
        }
    }

    private EditorCampaignExportService.Request request(String output) {
        return new EditorCampaignExportService.Request(directory.resolve(output + ".jar"),
                "authored-campaign", "Authored Campaign", "1.0.0", "Author", "campaign");
    }

    private Path save(String name, GameId game, GameplayLaunchTeam team) throws Exception {
        return save(name, game, team, null);
    }

    private Path save(String name, GameId game, GameplayLaunchTeam team,
            com.openggf.level.objects.authored.AuthoredObjectProperties gizmo) throws Exception {
        var source = new Fixture(game, gizmo);
        var metadata = new EditorProjectMetadata(game, name, 0, 0, "fixture", 16, 16,
                source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                new FullLevelExporter.ExportMusic.Stock(0x81), game == GameId.S3K ? ModObjectZoneSet.S3KL : null)
                .withSettings(com.openggf.game.modzone.ModLevelSettings.DEFAULT.withTeam(team));
        Path file = directory.resolve(name + ".ogfproject");
        new EditorProjectStore().save(file, EditorProjectDocument.create(metadata, source));
        return file;
    }

    private static final class Fixture extends AbstractLevel {
        private final int side;
        Fixture(GameId game) {
            this(game, null);
        }
        Fixture(GameId game, com.openggf.level.objects.authored.AuthoredObjectProperties gizmo) {
            super(0x40);
            side = game == GameId.S1 ? 16 : 8;
            patterns = new Pattern[] {new Pattern()}; patternCount = 1;
            patterns[0].setPixel(0, 0, (byte) 1);
            chunks = new Chunk[] {new Chunk()}; chunkCount = 1;
            chunks[0].restoreState(new int[] {0x4000, 0x4000, 0x4000, 0x4000, 0, 0});
            blocks = new Block[] {new Block(side)}; blockCount = 1;
            solidTiles = new SolidTile[] {new SolidTile(0, new byte[16], new byte[16], (byte) 0)}; solidTileCount = 1;
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            palettes[2].setColor(1, new Palette.Color((byte) 36, (byte) 146, (byte) 255));
            map = new com.openggf.level.Map(2, 2, 2);
            objects = gizmo == null ? List.of() : List.of(new com.openggf.level.objects.ObjectSpawn(
                    16, 16, 0, 0, 0, false, 16, 0, "openggf", gizmo.objectKey(), gizmo));
            rings = List.of();
            minX = 0; minY = 0; maxX = side * 16 * 2 - 1; maxY = side * 16 * 2 - 1;
        }
        @Override public int getChunksPerBlockSide() { return side; }
        @Override public int getBlockPixelSize() { return side * 16; }
    }
}
