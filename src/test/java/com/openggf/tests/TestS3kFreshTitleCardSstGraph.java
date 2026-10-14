package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardOwnerObjectInstance;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardElementObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level/loc_62B6 writes Obj_TitleCard at Dynamic_object_RAM+5 (absolute SST8).
 * Obj_TitleCardCreate allocates the four ObjArray_TtlCard entries after it,
 * before loc_6468 Load_Sprites can allocate ordinary placed objects.
 * This is a production load contract, independent of trace SST comparison rows.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshTitleCardSstGraph {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"0,1,320", "0,1,800", "1,0,320", "1,0,800"})
    void freshTitleGraphPrecedesPlacementAndSurvivesOmittedPresentation(
            int zone, int act, int width) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = SonicConfigurationService.getInstance();
        configureViewportPreset(width);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var level = GameServices.level();
        assertEquals(width, fixture.camera().getWidth(), "actual preset before native load");
        level.loadZoneAndActAtFreshTitleCardBoundary(zone,act);
        assertEquals(width, fixture.camera().getWidth(), "actual preset after native load");
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertTitleSlot(8, "FreshTitleCardOwner", 0, 0);
        // Literal ObjArray_TtlCard rest positions, reached before the LevelLoop.
        assertTitleSlot(9, "FreshTitleCardElement", 0x120, 0xE0);
        assertTitleSlot(10, "FreshTitleCardElement", 0x17C, 0x100);
        assertTitleSlot(11, "FreshTitleCardElement", 0x184, 0x120);
        assertTitleSlot(12, "FreshTitleCardElement", 0xE0, 0xC0);
        assertTrue(level.skipPendingInitialTitleCardPresentation());
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        // Omitting the visual loop does not free the owner or card pieces.
        assertTitleSlot(8, "FreshTitleCardOwner", 0, 0);
        for (int tick=0;tick<0x16;tick++) fixture.stepIdleFrames(1);
        assertTitleSlot(9, "FreshTitleCardElement", 0x120, 0xE0);
        assertTitleSlot(12, "FreshTitleCardElement", 0xE0, 0xC0);
        fixture.stepIdleFrames(1);
        assertTitleSlot(12, "FreshTitleCardElement", 0xE0, 0xA0);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(WidescreenAspect.class)
    void realChildRetirementThenOwnerDispatchRecreatesAndReplaysTheExactGraph(WidescreenAspect aspect)
            throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        int width=aspect.pixelWidth();
        configureViewportPreset(width);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var level=GameServices.level();
        assertEquals(width, fixture.camera().getWidth(), "actual preset before native load");
        level.loadZoneAndActAtFreshTitleCardBoundary(0,1);
        assertEquals(width, fixture.camera().getWidth(), "actual preset after native load");
        assertTrue(level.skipPendingInitialTitleCardPresentation());
        var objects=level.getObjectManager();
        // A separate native parent proves that restoration follows parent2,
        // rather than rebinding a child to the closest currently live title.
        var other=objects.createDynamicObjectAtSlot(() ->
                new S3kFreshTitleCardOwnerObjectInstance(
                        new ObjectSpawn(0,0,0,0,0,false,0)),70);
        assertNotNull(other);
        other.createNativeChildren(0,1);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        fixture.stepIdleFrames(26);
        assertTitleSlot(8,"FreshTitleCardOwner",0,0);
        assertTitleSlot(9,"FreshTitleCardElement",0x160,0xE0);
        var registry=fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before=registry.capture();
        var oldOwner=owner(8);
        int retirementSteps=0;
        while (elementsFor(8)>0 && retirementSteps<64) {
            fixture.stepIdleFrames(1);
            retirementSteps++;
        }
        assertEquals(0,elementsFor(8),"the actual four higher-slot children retire");
        assertNotNull(owner(8),"lower-slot owner returned before the final child decremented $30");
        fixture.stepIdleFrames(1);
        assertNull(owner(8),"only the next owner dispatch reaches Delete_Current_Sprite");
        CompositeSnapshot after=registry.capture();
        for (int cycle=0;cycle<2;cycle++) {
            registry.restore(before);
            assertNotSame(oldOwner,owner(8),"pure recreation replaces the retired owner");
            assertSnapshots(before,registry.capture());
            assertEquals(4,elementsFor(70),"independent parent2 graph remains untouched");
            fixture.stepIdleFrames(retirementSteps+1);
            assertSnapshots(after,registry.capture());
            assertNull(owner(8));
            assertNotNull(owner(70));
        }
    }

    @Test
    void internalEndingAdmissionDoesNotManufactureTheOrdinaryTitleGraph() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        GameServices.level().loadZoneAndActAtFreshTitleCardBoundary(0x0D,1);
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).isEmpty(),
                "Obj_TitleCardInit deletes at D01; SST8 belongs to the native ending scene");
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kFreshTitleCardElementObjectInstance.class).isEmpty());
    }

    private static void configureViewportPreset(int width) {
        WidescreenAspect aspect=java.util.Arrays.stream(WidescreenAspect.values())
                .filter(candidate -> candidate.pixelWidth()==width).findFirst().orElseThrow();
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,aspect.name());
        config.resolveDisplayAspect();
        assertEquals(width,config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width,GameServices.camera().getWidth());
    }

    private static S3kFreshTitleCardOwnerObjectInstance owner(int slot) {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).stream()
                .filter(owner -> !owner.isDestroyed() && owner.getSlotIndex()==slot)
                .findFirst().orElse(null);
    }

    private static long elementsFor(int parentSlot) throws Exception {
        var parentField=S3kFreshTitleCardElementObjectInstance.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        long result=0;
        for (var child:GameServices.level().getObjectManager()
                .activeObjectsOfType(S3kFreshTitleCardElementObjectInstance.class)) {
            if (child.isDestroyed()) continue;
            var parent=(S3kFreshTitleCardOwnerObjectInstance)parentField.get(child);
            assertNotNull(parent,"every restored child has its exact native parent2");
            if (parent.getSlotIndex()==parentSlot) result++;
        }
        return result;
    }

    private static void assertSnapshots(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for (String key:expected.entries().keySet()) {
            var differences=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(differences.isEmpty(), () -> key+": "+differences);
        }
    }

    private static void assertTitleSlot(int slot, String name, int x, int y) {
        List<ObjectInstance> occupants = GameServices.level().getObjectManager().getActiveObjects()
                .stream().filter(object -> !object.isDestroyed())
                .filter(object -> object instanceof AbstractObjectInstance abstractObject
                        && abstractObject.getSlotIndex()==slot).toList();
        assertEquals(1, occupants.size(), "absolute native SST"+slot);
        ObjectInstance object = occupants.getFirst();
        assertEquals(name, object.getName(), "absolute native SST"+slot);
        assertEquals(x, object.getX());
        assertEquals(y, object.getY());
    }
}
