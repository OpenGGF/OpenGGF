package com.openggf;

import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.*;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPhase3SharedFoundation {
    @TempDir Path temp;

    @Test
    void characterKeysRoundTripOwnerAndKeepBuiltinPersistence() {
        assertEquals("sonic", CharacterKey.SONIC.persisted());
        assertEquals("tails", CharacterKey.TAILS.persisted());
        assertEquals("knuckles", CharacterKey.KNUCKLES.persisted());
        CharacterKey first = CharacterKey.mod("owner-a", "runner");
        CharacterKey second = CharacterKey.mod("owner-b", "runner");
        assertNotEquals(first, second);
        assertEquals(first, CharacterKey.parsePersisted("owner-a:runner"));
        assertEquals("owner-a", first.ownerModId().orElseThrow());
    }

    @Test
    void immutableRegistryRetainsDuplicateLocalNamesAcrossOwnersAndReportsDisabledFallback() {
        CharacterDefinition a = definition(CharacterKey.mod("owner-a", "runner"), "A");
        CharacterDefinition b = definition(CharacterKey.mod("owner-b", "runner"), "B");
        PlayableCharacterRegistry empty = PlayableCharacterRegistry.empty();
        PlayableCharacterRegistry registry = empty.register(a.key(), a).register(b.key(), b);
        assertTrue(empty.definitions().isEmpty());
        assertSame(a, registry.find(a.key()).orElseThrow());
        assertSame(b, registry.find(b.key()).orElseThrow());
        var disabled = registry.resolve(a.key(), Set.of("owner-b"), CharacterKey.SONIC);
        assertEquals(CharacterKey.SONIC, disabled.resolvedKey());
        assertEquals(PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER, disabled.fallbackReason());
    }

    @Test
    void delegatingModuleForwardsRegistryAndContentPatchDecoratesTransactionally() {
        GameModule base = mock(GameModule.class);
        when(base.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        CharacterDefinition character = definition(CharacterKey.mod("owner-a", "runner"), "Runner");
        ModRegistrationPlan plan = ModRegistrationPlan.characterOnly("owner-a", "s2", Map.of(character.key(), character));
        GameModule decorated = new ModBackedGamePatch(plan, new com.openggf.mods.code.ModFaultBoundary(
                Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> {}))
                .apply(base, mock(com.openggf.game.patch.PatchContext.class));
        assertEquals(List.of("owner-a:runner"), new ModBackedGamePatch(plan,
                new com.openggf.mods.code.ModFaultBoundary(Map.of(),
                        new com.openggf.mods.ModRuntimeFindingStore(),
                        owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> {}))
                .providedMainCharacters());
        assertTrue(base.getPlayableCharacterRegistry().definitions().isEmpty());
        CharacterDefinition installed = decorated.getPlayableCharacterRegistry()
                .find(character.key()).orElseThrow();
        assertEquals(character.key(), installed.key());
        assertEquals(character.displayName(), installed.displayName());
        assertEquals(character.behavesLike(), installed.behavesLike());
        assertEquals(character.secondaryAbility(), installed.secondaryAbility());
        assertNotSame(character.spriteFactory(), installed.spriteFactory(),
                "Creator callbacks must be wrapped by the owner fault boundary");
        GameModule forwarding = new DelegatingGameModule(decorated, "test") {};
        assertSame(decorated.getPlayableCharacterRegistry(), forwarding.getPlayableCharacterRegistry());
    }

    @Test
    void modAssetDataSourceUsesBoundedJarAndDirectorySnapshots() throws Exception {
        Path root = Files.createDirectory(temp.resolve("mods"));
        Path dir = Files.createDirectory(root.resolve("dev"));
        Files.writeString(dir.resolve("asset.bin"), "directory");
        try (ModAssetRoot assets = ModAssetRoot.snapshotDirectory(root, dir, ModInputLimits.production(), DirectoryAccess.TEST)) {
            GameDataSource source = new ModAssetDataSource("owner-a", assets);
            assertEquals("directory", new String(source.openAsset("asset.bin").readAllBytes()));
            assertTrue(source.identity().matches("mod:owner-a:[0-9a-f]{64}"));
            assertThrows(IllegalArgumentException.class, () -> source.openAsset("../escape"));
        }

        Path jar = root.resolve("packed.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("asset.bin")); zip.write("jar".getBytes()); zip.closeEntry();
        }
        try (ModAssetRoot assets = ModAssetRoot.jar(root, jar, ModInputLimits.production())) {
            GameDataSource source = new ModAssetDataSource("owner-a", assets);
            assertEquals("jar", new String(source.openAsset("asset.bin").readAllBytes()));
            assertTrue(source.rom().isEmpty());
        }
    }

    @Test
    void modDataSourcePreservesRootCumulativeReadBudget() throws Exception {
        Path root = Files.createDirectory(temp.resolve("budget-root"));
        Path dir = Files.createDirectory(root.resolve("dev"));
        Files.writeString(dir.resolve("a.bin"), "1234");
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAssetBytes(4)
                .maxModValidationBytes(6).build();
        try (ModAssetRoot assets = ModAssetRoot.snapshotDirectory(root, dir, limits, DirectoryAccess.TEST)) {
            GameDataSource source = new ModAssetDataSource("owner-a", assets);
            assertEquals(4, source.openAsset("a.bin").readAllBytes().length);
            assertThrows(java.io.IOException.class, () -> source.openAsset("a.bin"));
        }
    }

    @Test
    void standaloneBaseRequiresOnlyDocumentedSourceProvidersAndNeverExposesARom() throws Exception {
        AbstractStandaloneGameModule module = new AbstractStandaloneGameModule() {
            @Override public String getIdentifier() { return "standalone-test"; }
            @Override public com.openggf.data.Game createGame(GameDataSource source) {
                return new ModGame("standalone-test", source) {
                    @Override public com.openggf.level.Level loadLevel(int levelIdx) { return null; }
                    @Override public int getMusicId(int levelIdx) { return 0; }
                };
            }
            @Override public com.openggf.level.objects.TouchResponseTable createTouchResponseTable(GameDataSource source) {
                return new com.openggf.level.objects.TouchResponseTable(new com.openggf.data.RomByteReader(new byte[]{0, 0}), 0, 1);
            }
            @Override public com.openggf.level.objects.ObjectRegistry createObjectRegistry() { return mock(com.openggf.level.objects.ObjectRegistry.class); }
            @Override public com.openggf.level.objects.ObjectPlacementEncoding getObjectPlacementEncoding() { return mock(com.openggf.level.objects.ObjectPlacementEncoding.class); }
            @Override public com.openggf.audio.GameAudioProfile getAudioProfile() { return mock(com.openggf.audio.GameAudioProfile.class); }
            @Override public ZoneRegistry getZoneRegistry() { return mock(ZoneRegistry.class); }
            @Override public PhysicsProvider getPhysicsProvider() { return mock(PhysicsProvider.class); }
        };
        GameDataSource source = missingSource("standalone-assets");
        assertNull(module.createGame(source).getRom());
        assertThrows(UnsupportedOperationException.class, () -> module.createGame(mock(com.openggf.data.Rom.class)));
        assertNull(module.getObjectArtProvider());
        assertNull(module.getLevelEventProvider());
    }

    @Test
    void selectedTeamOwnerKeysRoundTripThroughRealSaveManager() throws Exception {
        var team = new com.openggf.game.save.SelectedTeam("owner-a:runner", List.of("owner-b:buddy"));
        com.openggf.game.save.SaveManager saves = new com.openggf.game.save.SaveManager(temp.resolve("saves"));
        saves.writeSlot("s2", 1, Map.of("mainCharacter", team.mainCharacter(), "sidekicks", team.sidekicks()));
        Map<String, Object> payload = saves.readSlotSummary("s2", 1).payload();
        var restored = new com.openggf.game.save.SelectedTeam((String) payload.get("mainCharacter"),
                ((List<?>) payload.get("sidekicks")).stream().map(String.class::cast).toList());
        assertEquals(team, restored);
        assertEquals(CharacterKey.mod("owner-a", "runner"), CharacterKey.parsePersisted(restored.mainCharacter()));
    }

    @Test
    void immutableSourceIdentityChangesWhenSamePathContentsChange() throws Exception {
        Path root = Files.createDirectory(temp.resolve("identity-root"));
        Path dir = Files.createDirectory(root.resolve("dev"));
        Path asset = dir.resolve("asset.bin");
        Files.writeString(asset, "first");
        String first;
        try (var assets = ModAssetRoot.snapshotDirectory(root, dir, ModInputLimits.production(), DirectoryAccess.TEST)) {
            first = new ModAssetDataSource("owner-a", assets).identity();
        }
        Files.writeString(asset, "second");
        try (var assets = ModAssetRoot.snapshotDirectory(root, dir, ModInputLimits.production(), DirectoryAccess.TEST)) {
            assertNotEquals(first, new ModAssetDataSource("owner-a", assets).identity());
        }
    }

    @Test
    void romSourceRequiresCallerSuppliedStableIdentity() {
        var rom = mock(com.openggf.data.Rom.class);
        assertEquals("rom:sha256:abc", new RomDataSource(rom, "rom:sha256:abc").identity());
        assertFalse(java.util.Arrays.stream(RomDataSource.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.equals(constructor.getParameterTypes(),
                        new Class<?>[]{com.openggf.data.Rom.class})));
    }

    private static CharacterDefinition definition(CharacterKey key, String name) {
        return new CharacterDefinition(key, name, (code, x, y) -> null, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false, code -> null);
    }

    private static GameDataSource missingSource(String identity) {
        return new GameDataSource() {
            @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                return java.util.Optional.empty();
            }
            @Override public java.io.InputStream openAsset(String normalizedPath)
                    throws java.io.IOException {
                throw new java.io.IOException("fixture has no named assets");
            }
            @Override public String identity() { return identity; }
        };
    }
}
