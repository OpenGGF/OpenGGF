package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2BreakIntoPieces {
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"47,false", "47,true", "50,false", "50,true"})
    void parentAtHigherSlotKeepsPieceZeroAndLeavesLowerHoleForUnrestrictedPopup(int id, boolean readyArt) throws Exception {
        var f = new Fixture();
        if (readyArt) f.readyArt(id);
        int parentSlot = 0x60;
        var parent = f.parent(id, parentSlot);
        f.reserveExcept(Set.of(parentSlot, 0x20));
        f.breakParent(parent);
        var first = f.at(parentSlot);
        assertNotNull(first, "BreakObjectToPieces uses a1=a0 even when no later slot is free");
        assertNotSame(parent, first, "the original slot now owns the first static mapping piece");
        assertEquals(id, first.getSpawn().objectId());
        assertInstanceOf(PointsObjectInstance.class, f.at(0x20),
                "SmashableObject_LoadPoints may use the lower hole after sibling admission fails");
        assertEquals(-1, f.objects.allocateDynamicSlot(), "the popup consumes the sole lower hole");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"47,false", "47,true", "50,false", "50,true"})
    void siblingsUseAscendingAfterCurrentHolesAndStopAtFirstFailure(int id, boolean readyArt) throws Exception {
        var f = new Fixture();
        if (readyArt) f.readyArt(id);
        var parent = f.parent(id, 0x60);
        f.reserveExcept(Set.of(0x60, 0x20, 0x62, 0x70));
        f.breakParent(parent);
        assertEquals(List.of(0x60, 0x62, 0x70), fragments(f).stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertInstanceOf(PointsObjectInstance.class, f.at(0x20));
        assertEquals(-1, f.objects.allocateDynamicSlot());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 4, 6, 8})
    void groundBreakUsesTheOddRomFrameAndContiguousVelocityPairs(int oldFrame) throws Exception {
        var f = new Fixture();
        var parent = f.parent(0x2F, 0x30, oldFrame);
        f.breakParent(parent);
        var rom = f.services.romReader();
        var expected = com.openggf.game.sonic2.S2SpriteDataLoader.loadStockMappingFrames(
                rom, 0x236FA).get(oldFrame + 1).pieces();
        int velocityBase = com.openggf.game.sonic2.Sonic2RomAddresses.address(rom, 0x234F2 + oldFrame * 4);
        var actual = fragments(f);
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            var state = actual.get(index).captureRewindState().compactGenericState();
            assertNotNull(state, "every fragment's logical mapping and motion are captured");
            assertEquals(expected.get(index), field(actual.get(index), "piece"));
            assertEquals((int) (short) rom.readU16BE(velocityBase + index * 4), field(actual.get(index), "xVelocity"));
            // Only a0 falls through now; after-current slots execute in the manager pass.
            int yVelocity = (short) rom.readU16BE(velocityBase + index * 4 + 2) + (index == 0 ? 0x18 : 0);
            assertEquals((int) (short) yVelocity, field(actual.get(index), "yVelocity"));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 7, 13})
    void blockUsesCpzOnlyInNativeChemicalPlantAndHtzMappingOtherwise(int nativeZone) throws Exception {
        var f = new Fixture();
        when(f.level.getZoneIndex()).thenReturn(nativeZone);
        f.breakParent(f.parent(0x32, 0x30));
        var expected = com.openggf.game.sonic2.S2SpriteDataLoader.loadStockMappingFrames(
                f.services.romReader(), nativeZone == 13 ? 0x23886 : 0x23852).getFirst().pieces();
        var actual = fragments(f);
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index), field(actual.get(index), "piece"));
            assertEquals(nativeZone == 13 ? 16 : 24, actual.get(index).getOnScreenHalfWidth());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0x2F, 0x32})
    void readyPresentationAndHeadlessBreaksHaveIdenticalLogicalFragments(int id) throws Exception {
        var headless = new Fixture();
        headless.breakParent(headless.parent(id, 0x30));
        var rendered = new Fixture();
        rendered.art = mock(ObjectRenderManager.class);
        var renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        String key = id == 0x2F ? com.openggf.game.sonic2.Sonic2ObjectArtKeys.SMASHABLE_GROUND
                : com.openggf.game.sonic2.Sonic2ObjectArtKeys.BREAKABLE_BLOCK;
        when(rendered.art.getRenderer(key)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);
        var mappings = com.openggf.game.sonic2.S2SpriteDataLoader.loadStockMappingFrames(
                rendered.services.romReader(), id == 0x2F ? 0x236FA : 0x23886);
        when(rendered.art.getSheet(key)).thenReturn(new ObjectSpriteSheet(new com.openggf.level.Pattern[0], mappings, 2, 1));
        rendered.breakParent(rendered.parent(id, 0x30));
        assertEquals(states(headless), states(rendered));
        for (var fragment : fragments(rendered)) fragment.appendRenderCommands(new java.util.ArrayList<>());
        verify(renderer, times(fragments(rendered).size())).drawPieces(anyList(), anyInt(), anyInt(), eq(false), eq(false));
    }

    @ParameterizedTest @ValueSource(ints = {0x2F, 0x32})
    void freshFragmentsRecreateAndRepeatTheirForwardMotion(int id) throws Exception {
        var f = new Fixture();
        var parent = f.parent(id, 0x30);
        f.breakParent(parent);
        f.objects.removeDynamicObject(parent);
        f.objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = new com.openggf.game.rewind.RewindRegistry();
        registry.register(f.objects.rewindSnapshottable());
        var saved = registry.capture();
        var before = states(f);
        var originals = fragments(f);
        originals.forEach(fragment -> fragment.update(1, f.player));
        var forward = states(f);
        for (var object : List.copyOf(f.objects.getActiveObjects())) f.objects.removeDynamicObject(object);
        registry.restore(saved);
        assertEquals(before, states(f));
        assertNotSame(originals.getFirst(), fragments(f).getFirst());
        fragments(f).forEach(fragment -> fragment.update(1, f.player));
        assertEquals(forward, states(f));
    }

    @org.junit.jupiter.api.Test
    void factoryIsNotInvokedForRejectedOrSubsequentPieces() throws Exception {
        var f = new Fixture();
        var parent = f.parent(0x32, 0x60);
        f.reserveExcept(Set.of(0x60, 0x62));
        var pieces = Sonic2BreakIntoPieces.readPieces(f.services, 0x23886, 0, 0x23698);
        var constructions = new java.util.concurrent.atomic.AtomicInteger();
        Sonic2BreakIntoPieces.replaceParent(parent, f.services, pieces.size(), index -> {
            constructions.incrementAndGet();
            return new Sonic2BreakPieceObjectInstance(parent.getSpawn(), pieces.get(index),
                    com.openggf.game.sonic2.Sonic2ObjectArtKeys.BREAKABLE_BLOCK,
                    0x18, 16, 0, false, false, 4, true);
        });
        assertEquals(2, constructions.get(), "a0 plus one admitted later piece; failed scan stops before construction");
        assertEquals(List.of(0x60, 0x62), fragments(f).stream().map(AbstractObjectInstance::getSlotIndex).toList());
    }

    @org.junit.jupiter.api.Test
    void realObjectPassRunsCurrentPieceThenEachAfterCurrentSiblingOnce() throws Exception {
        var f = new Fixture();
        var pieces = Sonic2BreakIntoPieces.readPieces(f.services, 0x23886, 0, 0x23698);
        f.objects.createDynamicObjectAtSlot(() -> new BreakDriver(f, pieces), 0x60);
        f.objects.update(0, f.player, List.of(), 0);
        var actual = fragments(f);
        assertEquals(pieces.size(), actual.size());
        for (int index = 0; index < actual.size(); index++) {
            assertEquals(0x60 + index, actual.get(index).getSlotIndex());
            assertEquals((128 * 256 + pieces.get(index).xVelocity()) >> 8, actual.get(index).getX());
            assertEquals((128 * 256 + pieces.get(index).yVelocity()) >> 8, actual.get(index).getY());
            assertEquals(false, field(actual.get(index), "firstUpdate"));
        }
        assertEquals(0x32, f.objects.objectIdInSlot(0x60), "retiring the replaced parent must not release piece zero");
    }

    private static final class BreakDriver extends AbstractObjectInstance {
        private final Fixture fixture;
        private final List<Sonic2BreakIntoPieces.Piece> pieces;
        BreakDriver(Fixture fixture, List<Sonic2BreakIntoPieces.Piece> pieces) {
            super(new ObjectSpawn(128, 128, 0x32, 0, 0, false, 0), "BreakDriver");
            this.fixture = fixture; this.pieces = pieces;
        }
        @Override public int getPriorityBucket() { return 4; }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
            boolean onScreen = isWithinSolidContactBounds();
            var first = Sonic2BreakIntoPieces.replaceParent(this, fixture.services, pieces.size(), index ->
                    new Sonic2BreakPieceObjectInstance(spawn, pieces.get(index),
                            com.openggf.game.sonic2.Sonic2ObjectArtKeys.BREAKABLE_BLOCK,
                            0x18, 16, 0, false, false, 4, onScreen));
            setDestroyed(true);
            first.update(vIntRunCount, player);
        }
    }

    @org.junit.jupiter.api.Test
    void deletingCurrentPieceReleasesItsSlotBeforeALaterObjectAllocates() throws Exception {
        var f = new Fixture();
        f.objects.createDynamicObjectAtSlot(() -> new BreakableBlockObjectInstance(
                new ObjectSpawn(256, 256, 0x32, 0, 0, false, 0), "OffscreenBreak") {
            @Override public boolean isWithinSolidContactBounds() { return false; }
            @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity actor) {
                f.checkpointParent = this;
                super.update(vIntRunCount, actor);
            }
        }, 0x60);
        BoxObjectInstance[] allocated = new BoxObjectInstance[1];
        f.objects.createDynamicObjectAtSlot(() -> new AbstractObjectInstance(
                new ObjectSpawn(256, 128, 0x41, 0, 0, false, 0), "LaterAllocator") {
            @Override public int getPriorityBucket() { return 4; }
            @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
            @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity actor) {
                allocated[0] = f.objects.createDynamicObjectWithReservedSlot(() -> new BoxObjectInstance(
                        new ObjectSpawn(256, 128, 0x41, 0, 0, false, 0), "Reused", 1, 1, 0, 0, 0, false));
            }
        }, 0x70);
        f.reserveExcept(Set.of(0x60, 0x70));
        f.objects.update(0, f.player, List.of(), 0);
        assertNotNull(allocated[0], "native DeleteObject frees a0 before the later allocator executes");
        assertEquals(0x60, allocated[0].getSlotIndex());
        assertEquals(0x41, f.objects.objectIdInSlot(0x60));
        assertEquals(-1, f.objects.allocateDynamicSlot(), "old-fragment cleanup must not release the reused slot twice");
        assertNotNull(f.currentPiece);
        assertTrue(f.currentPiece.isDestroyed());
        assertEquals(-1, f.currentPiece.getSlotIndex());
        assertEquals(255, f.currentPiece.getX(), "movement precedes the delete");
        assertEquals(254, f.currentPiece.getY());
        assertEquals(-0x200 + 0x18, field(f.currentPiece, "yVelocity"));
    }

    private static List<Sonic2BreakPieceObjectInstance> fragments(Fixture f) {
        return f.objects.getActiveObjects().stream().filter(object -> !object.isDestroyed())
                .filter(Sonic2BreakPieceObjectInstance.class::isInstance)
                .map(Sonic2BreakPieceObjectInstance.class::cast)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static List<com.openggf.game.rewind.schema.RewindObjectStateBlob> states(Fixture f) {
        return fragments(f).stream().map(fragment -> fragment.captureRewindState().compactGenericState()).toList();
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static final class Fixture {
        final Level level = mock(Level.class);
        final com.openggf.game.sonic2.Sonic2GameModule module = new com.openggf.game.sonic2.Sonic2GameModule();
        final com.openggf.game.solid.ObjectSolidExecutionContext solid = mock(com.openggf.game.solid.ObjectSolidExecutionContext.class);
        AbstractObjectInstance checkpointParent;
        final TestObjectServices services = new TestObjectServices() {
            @Override public Level currentLevel() { return level; }
            @Override public com.openggf.game.solid.ObjectSolidExecutionContext solidExecution() { return solid; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
            @Override public ObjectRenderManager renderManager() { return art; }
        };
        ObjectRenderManager art = mock(ObjectRenderManager.class);
        final ObjectManager objects;
        Sonic2BreakPieceObjectInstance currentPiece;
        final TestablePlayableSprite player;

        Fixture() throws Exception {
            TestEnvironment.configureGameModuleFixture(module);
            player = new TestablePlayableSprite("sonic", (short) 256, (short) 256);
            services.withGameModule(module);
            when(solid.resolveSolidNowAll()).thenAnswer(call -> {
                com.openggf.sprites.playable.PlayableNativeFloorReset.resetChain(player);
                player.setAir(false); player.setYSpeed((short) 0);
                var result = new com.openggf.game.solid.PlayerSolidContactResult(
                        com.openggf.game.solid.ContactKind.TOP, true, false, false, false,
                        com.openggf.game.solid.PreContactState.ZERO,
                        new com.openggf.game.solid.PostContactState((short)0,(short)0,false,true,false),0);
                return new com.openggf.game.solid.SolidCheckpointBatch(checkpointParent, java.util.Map.of(player,result));
            });
            when(level.getZoneIndex()).thenReturn(13); // Obj32_Init's native CPZ mapping branch.
            services.withRomReader(RomByteReader.fromRom(TestEnvironment.currentRom()))
                    .withGameState(new GameStateManager()).withCamera(new Camera());
            objects = new ObjectManager(List.of(), new Sonic2ObjectRegistry(), 0, null, null, null,
                    services.camera(), services) {
                @Override public void addDynamicObjectAtSlot(ObjectInstance object, int slot) {
                    super.addDynamicObjectAtSlot(object, slot);
                    if (slot == 0x60 && object instanceof Sonic2BreakPieceObjectInstance fragment)
                        currentPiece = fragment;
                }
            };
            services.withDirectObjectManager(objects);
            AbstractObjectInstance.updateCameraBounds(0, 0, 0x2000, 0x2000, 0);
            player.setAnimationId(Sonic2AnimationIds.ROLL);
            player.setTopSolidBit((byte) 0x0E);
        }

        void readyArt(int id) throws Exception {
            art = mock(ObjectRenderManager.class);
            var renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
            String key = id == 0x2F ? com.openggf.game.sonic2.Sonic2ObjectArtKeys.SMASHABLE_GROUND
                    : com.openggf.game.sonic2.Sonic2ObjectArtKeys.BREAKABLE_BLOCK;
            when(art.getRenderer(key)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            var mappings = com.openggf.game.sonic2.S2SpriteDataLoader.loadStockMappingFrames(
                    services.romReader(), id == 0x2F ? 0x236FA : 0x23886);
            when(art.getSheet(key)).thenReturn(new ObjectSpriteSheet(new com.openggf.level.Pattern[0], mappings, 2, 1));
        }

        AbstractObjectInstance parent(int id, int slot) { return parent(id, slot, 0); }

        AbstractObjectInstance parent(int id, int slot, int subtype) {
            return objects.createDynamicObjectAtSlot(() -> id == 0x2F
                    ? new SmashableGroundObjectInstance(new ObjectSpawn(256, 256, id, subtype, 0, false, 0), "Ground")
                    : new BreakableBlockObjectInstance(new ObjectSpawn(256, 256, id, subtype, 0, false, 0), "Block"), slot);
        }

        void reserveExcept(Set<Integer> holesAndParent) {
            for (int slot = 16; slot < 128; slot++)
                if (!holesAndParent.contains(slot)) assertTrue(objects.reserveDynamicSlot(slot));
        }

        void breakParent(AbstractObjectInstance parent) {
            checkpointParent = parent;
            parent.update(0, player);
        }

        AbstractObjectInstance at(int slot) {
            return objects.getActiveObjects().stream()
                    .filter(object -> !object.isDestroyed())
                    .filter(AbstractObjectInstance.class::isInstance)
                    .map(AbstractObjectInstance.class::cast)
                    .filter(object -> object.getSlotIndex() == slot).findFirst().orElse(null);
        }
    }
}
