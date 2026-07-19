package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCnzEndBossChildren {

    @Test
    void postFieldWindDownConsumesExactFfWaitBeforeDescent() throws Exception {
        CnzEndBossInstance boss = boss();
        boss.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> null, List::of)));
        setBossRoutine(boss, CnzEndBossInstance.Routine.CHARGE);
        setBoolean(boss, "magneticFieldActive", true);
        field(boss, "routineTimer").setInt(boss, 0);

        boss.update(0, null);

        assertEquals(CnzEndBossInstance.Routine.WIND_DOWN, boss.nativeRoutine(),
                "loc_6E650 must enter the dedicated parent bit-7 wind-down state");
        for (int frame = 0; frame < 255; frame++) {
            boss.update(frame + 1, null);
        }
        assertEquals(CnzEndBossInstance.Routine.WIND_DOWN, boss.nativeRoutine(),
                "Obj_Wait with $2E=$FF remains active for 255 decrement frames");

        boss.update(256, null);

        assertEquals(CnzEndBossInstance.Routine.DESCEND, boss.nativeRoutine(),
                "the 256th wind-down update must dispatch loc_6E66C");
    }

    @Test
    void exactFloorContactRunsMagnetBounceCallback() throws Exception {
        CnzEndBossInstance boss = boss();
        CnzEndBossMagnetChild magnet = magnet(boss, playerAt(boss.getCentreX()));
        field(magnet, "centreY").setInt(magnet, 0x0300);
        field(magnet, "yVelocity").setInt(magnet, 0x0100);

        magnet.resolveFloorContact(0);

        assertEquals(0x0300, magnet.getCentreY());
        assertEquals(-0x80, magnet.yVelocityForTest());
        assertFalse(magnet.isLanded());
    }

    @Test
    void descentBottomSignalsImmediateMagnetReattach() throws Exception {
        CnzEndBossInstance boss = boss();
        boss.setServices(new StubObjectServices());
        CnzEndBossMagnetChild magnet = magnet(boss, playerAt(boss.getCentreX()));
        magnet.beginDrop();
        setBoolean(magnet, "landed", true);
        field(magnet, "centreY").setInt(magnet, boss.getCentreY() + 0x10);
        field(boss, "magnetChild").set(boss, magnet);
        setBossRoutine(boss, CnzEndBossInstance.Routine.DESCEND);

        boss.update(0, null);

        assertEquals(CnzEndBossInstance.Routine.ASCEND, boss.nativeRoutine());
        assertFalse(magnet.isReleasedForTest(),
                "loc_6E69C bit 3 must make loc_6E920 return the landed magnet to follow mode");
        assertEquals(boss.getCentreY() + 0x14, magnet.getCentreY(),
                "reattachment occurs at descent bottom, not after the later ascent");
    }

    @Test
    void alignFacingUsesPreMoveComparisonOnFinalPixel() throws Exception {
        CnzEndBossInstance boss = boss();
        boss.setServices(new StubObjectServices());
        CnzEndBossMagnetChild magnet = magnet(boss, playerAt(boss.getCentreX()));
        field(magnet, "centreX").setInt(magnet, boss.getCentreX() + 1);
        field(boss, "magnetChild").set(boss, magnet);
        setBossRoutine(boss, CnzEndBossInstance.Routine.ALIGN);

        boss.update(0, null);

        assertEquals(magnet.getCentreX(), boss.getCentreX());
        assertEquals(true, boss.facingRight(),
                "loc_6E5D8 sets render bit 0 from the pre-move comparison on the final pixel");
    }

    @Test
    void magnetDropTargetsClosestNativePlayerAndMovesBeforeApplyingGravity() {
        CnzEndBossInstance boss = boss();
        PlayableEntity main = playerAt(boss.getCentreX() - 0x40);
        PlayableEntity sidekick = playerAt(boss.getCentreX() + 0x20);
        CnzEndBossMagnetChild magnet = magnet(boss, main, sidekick);

        magnet.beginDrop();

        assertEquals(0x100, magnet.xVelocityForTest(),
                "loc_6E87E must aim the released magnet toward the closest native player");
        int startX = magnet.getCentreX();
        int startY = magnet.getCentreY();
        magnet.update(0, main);

        assertEquals(startX + 1, magnet.getCentreX(), "MoveSprite applies x_vel on the release frame");
        assertEquals(startY, magnet.getCentreY(), "MoveSprite moves with the old zero y_vel first");
        assertEquals(0x38, magnet.yVelocityForTest(), "MoveSprite adds $38 gravity after movement");
        magnet.update(1, main);
        assertEquals(startX + 2, magnet.getCentreX(),
                "horizontal drop velocity must persist through later fall/bounce updates");
        assertEquals(0x70, magnet.yVelocityForTest());
    }

    @Test
    void magnetRemainsHazardousWhileDockedAndLanded() throws Exception {
        CnzEndBossInstance boss = boss();
        PlayableEntity main = playerAt(boss.getCentreX());
        CnzEndBossMagnetChild magnet = magnet(boss, main);

        assertEquals(0x8B, magnet.getCollisionFlags(), "ObjDat3_6ED9C installs collision at init");
        magnet.beginDrop();
        setBoolean(magnet, "landed", true);
        assertEquals(0x8B, magnet.getCollisionFlags(),
                "sub_6ED22 clears collision only when the parent enters defeat");
        setBossRoutine(boss, CnzEndBossInstance.Routine.DEFEATED);
        assertEquals(0, magnet.getCollisionFlags());
    }

    @Test
    void magnetUsesExactBitThreeMultiDelayScriptAndResetsAtDescent() throws Exception {
        CnzEndBossInstance boss = boss();
        PlayableEntity main = playerAt(boss.getCentreX());
        CnzEndBossMagnetChild magnet = magnet(boss, main);
        magnet.beginDrop();
        setBoolean(magnet, "landed", true);
        setBossRoutine(boss, CnzEndBossInstance.Routine.CHARGE);

        int[] expected = {5, 4, 5, 4, 4, 4, 4, 4, 5};
        for (int frame : expected) {
            magnet.update(0, main);
            assertEquals(frame, magnet.frameForTest());
        }

        setBossRoutine(boss, CnzEndBossInstance.Routine.DESCEND);
        magnet.update(0, main);
        assertEquals(4, magnet.frameForTest(),
                "loc_6E910 resets the magnet head when parent bit 3 clears");
    }

    @Test
    void defeatScatterUnlinksExpiredMagnetFromRewindGraph() throws Exception {
        CnzEndBossInstance boss = boss();
        CnzEndBossMagnetChild magnet = magnet(boss, playerAt(boss.getCentreX()));
        field(boss, "magnetChild").set(boss, magnet);

        magnet.beginDefeatScatter();

        assertEquals(true, magnet.isDestroyed());
        assertNull(field(boss, "magnetChild").get(boss),
                "an expired native child must not remain in the captured boss graph");
    }

    @Test
    void nativeBossChildrenIgnoreGenericOffscreenCulling() {
        CnzEndBossInstance boss = boss();
        CnzEndBossMagnetChild magnet = magnet(boss, playerAt(boss.getCentreX()));
        CnzEndBossArmChild arm = new CnzEndBossArmChild(boss, 0);

        assertEquals(true, magnet.isPersistent());
        assertEquals(true, arm.isPersistent());
        assertEquals(magnet.getCentreX(), magnet.getMultiTouchRegions()[0].x());
        assertEquals(magnet.getCentreY(), magnet.getMultiTouchRegions()[0].y());
        assertEquals(arm.getCentreX(), arm.getMultiTouchRegions()[0].x());
        assertEquals(arm.getCentreY(), arm.getMultiTouchRegions()[0].y());
    }

    @Test
    void repeatedArmSubtypesProduceQuarterTurnPhases() {
        CnzEndBossInstance boss = boss();
        for (int childIndex = 0; childIndex < 4; childIndex++) {
            CnzEndBossArmChild arm = new CnzEndBossArmChild(boss, childIndex << 6);
            assertEquals(childIndex << 6, arm.angleForTest());
            assertEquals(childIndex << 1, arm.getSpawn().subtype());
        }
    }

    @Test
    void bossTouchResponseStartsOnlyAfterNativeRoutineZeroSetup() throws Exception {
        CnzEndBossInstance boss = boss();

        assertEquals(0, boss.getCollisionFlags(),
                "the camera-gate wrapper does not call Draw_And_Touch_Sprite");
        setBoolean(boss, "startupComplete", true);
        assertEquals(0x06, boss.getCollisionFlags(),
                "loc_6E4F2 installs ObjDat_CNZEndBoss collision response 6");
        var region = boss.getMultiTouchRegions()[0];
        assertEquals(boss.getCentreX(), region.x());
        assertEquals(boss.getCentreY(), region.y());
    }

    @Test
    void armMultiDelayFrameThreeSurvivesAngleFrameSelection() throws Exception {
        CnzEndBossInstance boss = boss();
        CnzEndBossArmChild arm = new CnzEndBossArmChild(boss, 0);
        arm.setServices(new StubObjectServices());
        setBossRoutine(boss, CnzEndBossInstance.Routine.CHARGE);

        int[] expected = {3, 1, 3, 1, 1, 1, 1, 1, 3};
        for (int frame : expected) {
            arm.update(0, null);
            assertEquals(frame, arm.frameForTest(),
                    "sub_6EBF0 must preserve byte_6EE0E frame 3 and delay each pair by delay+1");
        }
    }

    private static CnzEndBossInstance boss() {
        return new CnzEndBossInstance(new com.openggf.level.objects.ObjectSpawn(
                0x4740, 0x0240, 0xA7, 0, 0, false, 0));
    }

    private static CnzEndBossMagnetChild magnet(CnzEndBossInstance boss, PlayableEntity main,
                                                 PlayableEntity... sidekicks) {
        CnzEndBossMagnetChild magnet = new CnzEndBossMagnetChild(boss);
        magnet.setServices(new StubObjectServices().withPlayerQuery(
                new ObjectPlayerQuery(() -> main, () -> List.of(sidekicks))));
        return magnet;
    }

    private static PlayableEntity playerAt(int x) {
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) x);
        return player;
    }

    private static void setBossRoutine(CnzEndBossInstance boss, CnzEndBossInstance.Routine routine)
            throws Exception {
        field(boss, "routine").set(boss, routine);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        field(target, name).setBoolean(target, value);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
