package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.HardwareBoundaryDispatch;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingBoundaryObserver;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Obj_RobotnikHeadInit's actual character_id branch, resident art and global KosM lifetime. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczRobotnikHeadArtContract {
    private static final int SOURCE = Sonic3kConstants.ART_KOSM_EGG_ROBO_HEAD_ADDR;
    private static final int TILE = Sonic3kConstants.ART_TILE_ROBOTNIK_SHIP;
    private static final int VRAM = TILE * 32;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void nativeHeadDataAndHcz2RegistryUseTheLockedOnSharedShipBank() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        RomByteReader reader = RomByteReader.fromRom(rig.rom);
        assertArrayEquals(new byte[]{0x0F, 0, 1, (byte) 0xFC},
                rig.rom.readBytes(Sonic3kConstants.ANI_RAW_EGG_ROBO_HEAD_ADDR, 4));
        assertEquals(0x400, reader.readU16BE(SOURCE));
        assertEquals("12479274979954cf89bac77b8fc1b9337f9013bb",
                sha1(rig.rom.readBytes(SOURCE, 0x1E2)));
        assertEquals("6b66fa56d221f51f70f85049ef05240798567a7f",
                sha1(rig.rom.readBytes(Sonic3kConstants.MAP_EGG_ROBO_HEAD_ADDR, 0x28)));
        var plan = Sonic3kPlcArtRegistry.getPlan(Sonic3kZoneIds.ZONE_HCZ, 1);
        var entry = plan.levelArt().stream()
                .filter(candidate -> Sonic3kObjectArtKeys.EGG_ROBO_HEAD.equals(candidate.key()))
                .findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.MAP_EGG_ROBO_HEAD_ADDR, entry.mappingAddr());
        assertEquals(TILE, entry.artTileBase());
        assertEquals(0, entry.palette());
        assertEquals(4, entry.mappingFrameCount());
        assertFalse(plan.standaloneArt().stream()
                .anyMatch(candidate -> Sonic3kObjectArtKeys.EGG_ROBO_HEAD.equals(candidate.key())));
        assertFalse(Sonic3kPlcArtRegistry.getPlan(Sonic3kZoneIds.ZONE_HCZ, 0).levelArt().stream()
                .anyMatch(candidate -> Sonic3kObjectArtKeys.EGG_ROBO_HEAD.equals(candidate.key())));
    }

    @Test
    void sessionP1KnucklesSelectsEggRoboOnceDespiteSonicDefaultAndOtherDispatchPlayer() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        rig.head.update(0, rig.sidekick);
        assertTrue(flag(rig.head, "eggRobo"));
        assertEquals(2, word(rig.head, "routine"));
        assertEquals(0, word(rig.head, "mappingFrame"), "INIT does not fall into Animate_Raw");
        assertEquals(0, word(rig.head, "animCounter"));
        HardwareWorkHandle submitted = rig.onlyModule();
        var descriptor = rig.coordinator.moduleQueue().descriptor(submitted);
        assertEquals(SOURCE, descriptor.sourceAddress());
        assertTrue(descriptor.compressedLength() > 2 && descriptor.compressedLength() <= 0x1E2,
                "the job's inspected terminator stays inside the pinned native archive span");
        assertEquals(VRAM, descriptor.destinationAddress());
        assertEquals(0x400, descriptor.destinationLength());
        assertEquals(1, descriptor.moduleCount());
        assertFalse(rig.coordinator.moduleQueue().isReady(submitted));
        rig.head.appendRenderCommands(new ArrayList<>());
        verify(rig.eggRenderer).drawFrameIndexForcedPriority(eq(0), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), eq(0), anyBoolean());
        verifyNoInteractions(rig.shipRenderer);

        // Selection is native object state, not a fresh per-frame team/config lookup.
        when(rig.main.characterKey()).thenReturn(CharacterKey.SONIC);
        rig.head.update(1, rig.sidekick);
        assertEquals(1, word(rig.head, "mappingFrame"));
        assertEquals(0x0F, word(rig.head, "animCounter"));
        for (int frame = 2; frame <= 16; frame++) rig.head.update(frame, rig.sidekick);
        assertEquals(1, word(rig.head, "mappingFrame"));
        assertEquals(0, word(rig.head, "animCounter"));
        rig.head.update(17, rig.sidekick);
        assertEquals(0, word(rig.head, "mappingFrame"));
        assertEquals(0x0F, word(rig.head, "animCounter"));
        assertEquals(submitted, rig.onlyModule());
        assertEquals(1, rig.physical.queuedArchiveCount());
    }

    @Test
    void sonicP1DoesNotSelectKnucklesDefaultSidekickOrDispatchArgument() throws Exception {
        assertOriginalHead(CharacterKey.SONIC);
    }

    @Test
    void tailsP1DoesNotMistakePlayerModeOrdinalTwoForNativeCharacterIdTwo() throws Exception {
        assertEquals(2, PlayerCharacter.TAILS_ALONE.ordinal());
        assertOriginalHead(CharacterKey.TAILS);
    }

    private static void assertOriginalHead(CharacterKey identity) throws Exception {
        Rig rig = new Rig(identity, "knuckles");
        rig.head.update(0, rig.sidekick);
        assertFalse(flag(rig.head, "eggRobo"));
        assertTrue(rig.timing.pendingHandles().isEmpty());
        assertTrue(rig.physical.isIdle());
        assertEquals(0, word(rig.head, "mappingFrame"));
        rig.head.update(1, rig.sidekick);
        assertEquals(1, word(rig.head, "mappingFrame"));
        assertEquals(5, word(rig.head, "animCounter"));
        rig.head.appendRenderCommands(new ArrayList<>());
        verify(rig.shipRenderer).drawFrameIndexForcedPriority(eq(1), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), eq(0), anyBoolean());
        verifyNoInteractions(rig.eggRenderer);
    }

    @Test
    void freshHcz2ProviderBindsAllThirtyTwoActualResidentPatternsAndNativeMappings() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        assertTrue(rig.level.getPatternCount() >= 0x7E0,
                "fresh character PLC's life icon at $7D4 reserves the intervening resident bank");
        var sheet = rig.provider.getSheet(Sonic3kObjectArtKeys.EGG_ROBO_HEAD);
        assertNotNull(sheet);
        assertNotNull(rig.provider.getRenderer(Sonic3kObjectArtKeys.EGG_ROBO_HEAD));
        assertEquals(32, sheet.getPatterns().length);
        assertEquals(4, sheet.getFrameCount());
        for (int tile = 0; tile < 32; tile++) {
            assertSame(rig.level.getPattern(TILE + tile), sheet.getPatterns()[tile],
                    "head sheet must retain resident tile $" + Integer.toHexString(TILE + tile));
        }
        for (int frame = 0; frame < 4; frame++) {
            var pieces = sheet.getFrame(frame).pieces();
            assertEquals(1, pieces.size());
            var piece = pieces.getFirst();
            assertEquals(frame * 8, piece.tileIndex());
            assertEquals(4, piece.widthTiles());
            assertEquals(2, piece.heightTiles());
            assertEquals(-0x10, piece.xOffset());
            assertEquals(-8, piece.yOffset());
        }
        assertTrue(rig.provider.getAffectedRendererKeys(List.of(new Sonic3kPlcLoader.TileRange(TILE, 32)))
                .contains(Sonic3kObjectArtKeys.EGG_ROBO_HEAD));
    }

    @Test
    void actualGlobalJobOutlivesHeadAndPublishesResidentBytesOnlyAtExistingVintDrain() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        byte[] before = rig.residentBytes();
        rig.head.update(0, null);
        HardwareWorkHandle handle = rig.onlyModule();
        var unserviced = rig.physical.capture();
        for (int dispatch = 0; dispatch < 12; dispatch++) {
            rig.head.appendRenderCommands(new ArrayList<>());
            rig.physical.processNativeFrame();
        }
        assertEquals(unserviced, rig.physical.capture());
        assertFalse(rig.coordinator.moduleQueue().isReady(handle));
        assertArrayEquals(before, rig.residentBytes());
        rig.manager.removeDynamicObject(rig.head);
        rig.drain();
        assertTrue(rig.timing.pendingHandles().isEmpty(), "global owner retains and claims the deleted head's job");
        assertArrayEquals(before, rig.residentBytes(), "completed parent is not native VRAM publication");
        assertFalse(rig.coordinator.pendingKosModuleDmaEntries().isEmpty());
        rig.coordinator.publishPatternModuleDma();
        assertArrayEquals(rig.expectedArt(), rig.residentBytes());
        assertTrue(rig.coordinator.pendingKosModuleDmaEntries().isEmpty());
        var sheet = rig.provider.getSheet(Sonic3kObjectArtKeys.EGG_ROBO_HEAD);
        for (int tile = 0; tile < 32; tile++) assertSame(rig.level.getPattern(TILE + tile), sheet.getPatterns()[tile]);
    }

    @Test
    void recreatedSelectionAndGlobalPendingPresentedDmaReplayInBothDirectionsTwice() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        ObjectRefId headId = rig.manager.captureIdentityContext().requireIdentityTable().idFor(rig.head);
        CompositeSnapshot beforeInit = rig.rewind.capture();
        rig.head.update(0, null);
        CompositeSnapshot submitted = rig.rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.removeObjects();
            rig.rewind.restore(beforeInit);
            rig.same(beforeInit);
            var restored = rig.resolve(headId);
            assertNotSame(rig.head, restored);
            restored.update(0, null);
            rig.same(submitted);
            rig.removeObjects();
            rig.rewind.restore(submitted);
            rig.same(submitted); // Pure recreation cannot submit the archive again.
            assertTrue(flag(rig.resolve(headId), "eggRobo"));
            rig.onlyModule();
        }
        rig.drain();
        CompositeSnapshot queuedDma = rig.rewind.capture();
        rig.coordinator.publishPatternModuleDma();
        CompositeSnapshot presented = rig.rewind.capture();
        assertArrayEquals(rig.expectedArt(), rig.residentBytes());
        for (int cycle = 0; cycle < 2; cycle++) {
            rig.rewind.restore(queuedDma);
            rig.same(queuedDma);
            rig.coordinator.publishPatternModuleDma();
            rig.same(presented);
            rig.rewind.restore(submitted);
            rig.same(submitted);
            rig.drain();
            rig.same(queuedDma);
        }
    }

    @Test
    void fullTimedFifoFailsTheInitSubmissionWithoutInventingPhysicalAdmission() throws Exception {
        Rig rig = new Rig(CharacterKey.KNUCKLES, "sonic");
        for (int index = 0; index < 4; index++) rig.coordinator.moduleQueue().queue(rig.rom, SOURCE, TILE);
        var timingBefore = rig.timing.capture();
        var physicalBefore = rig.physical.capture();
        assertThrows(IllegalStateException.class, () -> rig.head.update(0, null));
        assertTrue(RewindSnapshotDiff.diffKey(rig.timing.key(), timingBefore, rig.timing.capture()).isEmpty());
        assertEquals(physicalBefore, rig.physical.capture());
        assertTrue(rig.physical.isIdle());
    }

    private static final class Rig {
        final Rom rom;
        final Sonic3kLevel level;
        final Sonic3kObjectArtProvider provider;
        final HardwareTimingService timing = new HardwareTimingService();
        final S3kRuntimeArtCoordinator coordinator = new S3kRuntimeArtCoordinator(timing);
        final KosinskiModuleQueue physical = new KosinskiModuleQueue();
        final RewindRegistry rewind = new RewindRegistry();
        final AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        final AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        final PatternSpriteRenderer shipRenderer = mock(PatternSpriteRenderer.class);
        final PatternSpriteRenderer eggRenderer = mock(PatternSpriteRenderer.class);
        final ObjectManager manager;
        final HczEndBossRobotnikHead head;

        Rig(CharacterKey identity, String configuredDefault) throws Exception {
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1).build();
            rom = TestEnvironment.objectServices().rom();
            level = (Sonic3kLevel) GameServices.level().getCurrentLevel();
            provider = (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
            var config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, configuredDefault);
            when(main.characterKey()).thenReturn(identity);
            when(sidekick.characterKey()).thenReturn(CharacterKey.KNUCKLES);
            when(sidekick.isCpuControlled()).thenReturn(true);
            SpriteManager sprites = mock(SpriteManager.class);
            when(sprites.getSprite(identity.persisted())).thenReturn(main);
            when(sprites.getAllSprites()).thenReturn(List.of(sidekick, main));
            WorldSession session = mock(WorldSession.class);
            when(session.getSaveSessionContext()).thenReturn(SaveSessionContext.noSave("s3k",
                    new SelectedTeam(identity.persisted(), List.of("knuckles")), 1, 1));
            when(shipRenderer.isReady()).thenReturn(true);
            when(eggRenderer.isReady()).thenReturn(true);
            var renderManager = new ObjectRenderManager(null) {
                @Override public PatternSpriteRenderer getRenderer(String key) {
                    if (Sonic3kObjectArtKeys.EGG_ROBO_HEAD.equals(key)) return eggRenderer;
                    return Sonic3kObjectArtKeys.ROBOTNIK_SHIP.equals(key) ? shipRenderer : null;
                }
            };
            TestObjectServices services = new TestObjectServices() {
                @Override public HardwareTimingService hardwareTiming() { return timing; }
                @Override public S3kRuntimeArtCoordinator runtimeArtCoordinator() { return coordinator; }
                @Override public Level currentLevel() { return level; }
                @Override public LevelManager levelManager() { return GameServices.level(); }
                @Override public ObjectRenderManager renderManager() { return renderManager; }
            }.withRom(rom).withRomReader(RomByteReader.fromRom(rom)).withConfiguration(config)
                    .withSpriteManager(sprites).withWorldSession(session).withSidekicks(List.of(sidekick))
                    .withKosinskiModuleQueue(physical).withGraphicsManager(GameServices.graphics())
                    .withIsolatedObjectManager();
            manager = services.objectManager();
            var boss = manager.createDynamicObject(() -> new HczEndBossInstance(
                    new ObjectSpawn(0x4000, 0x738, 0x9A, 0, 0, false, 0x738)));
            var ship = manager.createDynamicObject(() -> new HczEndBossRobotnikShip(boss));
            ship.update(0, null);
            head = manager.activeObjectsOfType(HczEndBossRobotnikHead.class).getFirst();
            rewind.register(timing);
            coordinator.registerRewindAdapters(rewind);
            rewind.register(physical);
            rewind.register(manager.rewindSnapshottable());
        }

        HardwareWorkHandle onlyModule() {
            var handles = timing.pendingHandles();
            assertEquals(1, handles.size());
            assertEquals(HardwareWorkKind.KOS_MODULE_QUEUE, handles.getFirst().kind());
            return handles.getFirst();
        }

        void drain() {
            for (int loop = 0; loop < 10000 && !physical.isIdle(); loop++) {
                HardwareBoundaryDispatch.serviceBoundary(HardwareServiceBoundary.POST_OBJECTS,
                        coordinator, timing, HardwareTimingBoundaryObserver.NO_OP);
                HardwareBoundaryDispatch.serviceBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP,
                        coordinator, timing, HardwareTimingBoundaryObserver.NO_OP);
                physical.processNativeFrame();
            }
            assertTrue(physical.isIdle(), "real module/direct FIFO must finish within the bounded service loop");
            assertTrue(timing.pendingHandles().isEmpty());
        }

        byte[] residentBytes() {
            byte[] bytes = new byte[0x400];
            for (int offset = 0; offset < bytes.length; offset++) {
                var pattern = level.getPattern(TILE + offset / 32);
                int pixel = (offset % 32) * 2;
                bytes[offset] = (byte) (pattern.getPixel(pixel % 8, pixel / 8) << 4
                        | pattern.getPixel((pixel + 1) % 8, (pixel + 1) / 8));
            }
            return bytes;
        }

        byte[] expectedArt() throws Exception {
            return KosinskiReader.decompressModuled(rom.readBytes(SOURCE, 0x1E2), 0);
        }

        void removeObjects() {
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        }

        HczEndBossRobotnikHead resolve(ObjectRefId identity) {
            return (HczEndBossRobotnikHead) manager.captureIdentityContext().requireIdentityTable()
                    .resolveObject(identity, true);
        }

        void same(CompositeSnapshot expected) {
            var actual = rewind.capture();
            assertEquals(expected.entries().keySet(), actual.entries().keySet());
            for (String key : expected.entries().keySet()) {
                assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                        () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
            }
        }
    }

    private static int word(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static boolean flag(Object target, String name) throws Exception {
        return field(target, name).getBoolean(target);
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }
}
