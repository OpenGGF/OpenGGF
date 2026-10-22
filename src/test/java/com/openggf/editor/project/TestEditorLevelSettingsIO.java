package com.openggf.editor.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameId;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ZoneKey;
import com.openggf.game.modzone.ModLevelSettings;
import com.openggf.game.modzone.ModLevelSettingsJson;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.mods.code.ModZoneLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Persistence and launch-policy checks use actual project/export codecs without a ROM or Engine boot. */
class TestEditorLevelSettingsIO {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @Test
    void completeSettingsSurviveProjectSaveOpenAndImmutableMetadataEdits() throws Exception {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        var team = new GameplayLaunchTeam(CharacterKey.mod("example", "runner"),
                List.of(CharacterKey.TAILS, CharacterKey.mod("example", "buddy")));
        var settings = new ModLevelSettings(new ModLevelSettings.StockScrollSource(0, 1), -48, team);
        var metadata = TestEditorProjects.metadata(GameId.S2, source).withSettings(settings);
        var document = EditorProjectDocument.create(metadata, source);
        Path file = directory.resolve("settings.ogfproject");
        var store = new EditorProjectStore();
        store.save(file, document);
        var reopened = store.open(file);
        assertEquals(settings, reopened.metadata().settings());
        assertFalse(reopened.isModified());
        assertEquals(team, ModLevelSettingsJson.parse(ModLevelSettingsJson.toJson(settings)).team());
        assertEquals(settings, reopened.metadata().withName("Renamed").settings());
        assertEquals(settings, reopened.metadata().withStart(80, 80).settings());
        assertEquals(settings, reopened.metadata().withBounds(0, 511, 0, 511).settings());
        reopened.setMetadata(reopened.metadata().withSettings(settings.withWaterHeight(32)));
        assertTrue(reopened.isModified(), "settings participate in the document savepoint");
        store.save(file, reopened);
        assertEquals(Integer.valueOf(32), store.open(file).metadata().settings().waterHeight());
    }

    @Test
    void defaultsAreExplicitAndUnknownNestedProjectSettingsAreRejectedAfterChecksumValidation() throws Exception {
        assertEquals(ModLevelSettings.DEFAULT, ModLevelSettingsJson.parse(null));
        assertEquals(ModLevelSettings.DEFAULT, ModLevelSettingsJson.parse(JSON.readTree("null")));
        assertEquals(ModLevelSettings.DEFAULT, ModLevelSettingsJson.parse(JSON.readTree("{}")));
        assertEquals(ModLevelSettings.DEFAULT, ModLevelSettingsJson.parse(ModLevelSettingsJson.toJson(ModLevelSettings.DEFAULT)));
        var document = document(ModLevelSettings.DEFAULT);
        Path file = directory.resolve("strict-settings.ogfproject");
        var store = new EditorProjectStore();
        store.save(file, document);
        ObjectNode envelope = (ObjectNode) JSON.readTree(file.toFile());
        ((ObjectNode) envelope.path("payload").path("metadata").path("settings")).put("uncheckedWaterMode", "lava");
        TestEditorProjectValidation.resign(envelope);
        byte[] invalid = JSON.writeValueAsBytes(envelope);
        Files.write(file, invalid);
        IOException failure = assertThrows(IOException.class, () -> store.open(file));
        assertTrue(failure.getMessage().contains("Unknown settings field"), failure.getMessage());
        assertArrayEquals(invalid, Files.readAllBytes(file), "failed open does not rewrite authored settings");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]", "{\"unknown\":1}", "{\"background\":false}",
            "{\"background\":{\"zone\":0,\"act\":0,\"speed\":2}}",
            "{\"background\":{\"zone\":\"0\",\"act\":0}}",
            "{\"background\":{\"zone\":0}}",
            "{\"background\":{\"zone\":-1,\"act\":0}}",
            "{\"background\":{\"zone\":0,\"act\":256}}",
            "{\"waterHeight\":\"100\"}", "{\"waterHeight\":1.5}", "{\"waterHeight\":32768}",
            "{\"waterHeight\":-32769}", "{\"waterHeight\":4294967296}",
            "{\"team\":[]}", "{\"team\":{\"main\":\"sonic\",\"sidekicks\":\"tails\"}}",
            "{\"team\":{\"main\":false,\"sidekicks\":[]}}",
            "{\"team\":{\"main\":\"unqualified-unknown\",\"sidekicks\":[]}}",
            "{\"team\":{\"main\":\"sonic\",\"sidekicks\":[1]}}",
            "{\"team\":{\"main\":\"sonic\"}}",
            "{\"team\":{\"main\":\"sonic\",\"sidekicks\":[],\"fallback\":true}}"
    })
    void settingsJsonRejectsUnknownFieldsCoercionsInvalidRangesAndCharacterShapes(String json) throws Exception {
        assertThrows(IOException.class, () -> ModLevelSettingsJson.parse(JSON.readTree(json)), json);
    }

    @Test
    void settingsJsonBoundsTeamLengthWithoutDiscardingValidMemberOrder() throws Exception {
        var node = ModLevelSettingsJson.toJson(new ModLevelSettings(null, 0,
                new GameplayLaunchTeam(CharacterKey.SONIC, java.util.Collections.nCopies(16, CharacterKey.TAILS))));
        assertEquals(16, ModLevelSettingsJson.parse(node).team().sidekicks().size());
        ((com.fasterxml.jackson.databind.node.ArrayNode) node.path("team").path("sidekicks")).add("knuckles");
        assertThrows(IOException.class, () -> ModLevelSettingsJson.parse(node));
    }

    @Test
    void bakedSettingsRoundTripThroughExportParserAndHostDataTransfer() throws Exception {
        var settings = new ModLevelSettings(new ModLevelSettings.StockScrollSource(0, 1), 192,
                new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS)));
        var document = document(settings);
        Path output = directory.resolve("baked");
        var request = EditorProjectRuntimeModule.request(document, output, 64, 64);
        var exporter = new FullLevelExporter(new ModLevelExportStagingValidator(new Sonic2GameModule()));
        exporter.export(document.level(), request);
        assertEquals(ModLevelSettingsJson.toJson(settings), JSON.readTree(output.resolve("level.json").toFile()).get("settings"));
        try (var assets = ModAssetRoot.directory(directory, output, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            assertEquals(settings, parsed.settings());
            var hostData = ModZoneLoader.prepareHostData(parsed);
            assertEquals(settings, hostData.settings());
            assertEquals(document.level().getBlockCount(), hostData.blockCount());
            assertEquals(request.startX(), parsed.start().x());
        }
        ObjectNode invalid = (ObjectNode) JSON.readTree(output.resolve("level.json").toFile());
        ((ObjectNode) invalid.get("settings")).put("unexpected", true);
        Files.write(output.resolve("level.json"), JSON.writeValueAsBytes(invalid));
        try (var assets = ModAssetRoot.directory(directory, output, ModInputLimits.production(), DirectoryAccess.TEST)) {
            assertThrows(IOException.class, () -> ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json")));
        }
    }

    @Test
    void reopenedRequiredTeamPreparesExactNoSaveLaunchAndUnrestrictedProjectHasNoOverride() throws Exception {
        var team = new GameplayLaunchTeam(CharacterKey.KNUCKLES, List.of(CharacterKey.TAILS, CharacterKey.SONIC));
        var settings = new ModLevelSettings(new ModLevelSettings.StockScrollSource(0, 0), 176, team);
        var store = new EditorProjectStore();
        Path file = directory.resolve("required-team.ogfproject");
        store.save(file, document(settings));
        var reopened = store.open(file);
        var runtime = EditorProjectRuntimeModule.prepare(new Sonic2GameModule(), reopened, 64, 64);
        var context = runtime.launchContext();
        assertNotNull(context);
        assertEquals("s2", context.gameCode());
        assertTrue(context.activeSlot().isEmpty(), "editor playtest cannot write a stock save slot");
        assertEquals("knuckles", context.selectedTeam().mainCharacter());
        assertEquals(List.of("tails", "sonic"), context.selectedTeam().sidekicks());
        assertEquals(0, context.startZone()); assertEquals(0, context.startAct());
        assertEquals(team, runtime.getGameplayPolicyProvider().launchTeam(ZoneKey.mod("openggf-editor", "project")).orElseThrow());
        var inherited = EditorProjectRuntimeModule.prepare(new Sonic2GameModule(), document(ModLevelSettings.DEFAULT), 64, 64);
        assertNull(inherited.launchContext());
        assertTrue(inherited.getGameplayPolicyProvider().launchTeam(ZoneKey.mod("openggf-editor", "project")).isEmpty());
    }

    @Test
    void missingRequiredCharacterIsRejectedBeforePreparingPlaytestWithoutFallback() {
        CharacterKey missing = CharacterKey.mod("absent-owner", "runner");
        var settings = new ModLevelSettings(null, null, new GameplayLaunchTeam(missing, List.of(CharacterKey.TAILS)));
        var document = document(settings);
        var failure = assertThrows(GameplayTeamBootstrap.UnavailableRequiredCharacter.class,
                () -> EditorProjectRuntimeModule.prepare(new Sonic2GameModule(), document, 64, 64));
        assertEquals(missing, failure.key());
        assertEquals(settings, document.metadata().settings(), "rejecting a launch retains the author-requested team");
    }

    static EditorProjectDocument document(ModLevelSettings settings) {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        var document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source).withSettings(settings), source);
        document.level().replaceSpawnsPersisted(List.of(), List.of(), Map.of());
        useGenesisPalette(document);
        return document;
    }
    static void useGenesisPalette(EditorProjectDocument document) {
        // The complete-project fixture deliberately includes arbitrary RGB; baked settings
        // tests need independently valid Genesis art to reach the settings transfer.
        for (int line = 0; line < document.level().getPaletteCount(); line++) for (int index = 0; index < 16; index++) {
            var color = document.level().getPalette(line).getColor(index);
            color.r = (byte) com.openggf.editor.palette.EditorPaletteEditing.componentFromStep((Byte.toUnsignedInt(color.r) * 7 + 127) / 255);
            color.g = (byte) com.openggf.editor.palette.EditorPaletteEditing.componentFromStep((Byte.toUnsignedInt(color.g) * 7 + 127) / 255);
            color.b = (byte) com.openggf.editor.palette.EditorPaletteEditing.componentFromStep((Byte.toUnsignedInt(color.b) * 7 + 127) / 255);
        }
    }

}
