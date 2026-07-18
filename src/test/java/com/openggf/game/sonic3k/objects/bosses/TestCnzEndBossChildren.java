package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCnzEndBossChildren {

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
    void armMultiDelayFrameThreeSurvivesAngleFrameSelection() throws Exception {
        CnzEndBossInstance boss = boss();
        CnzEndBossArmChild arm = new CnzEndBossArmChild(boss, 0);
        arm.setServices(new StubObjectServices());
        setBossRoutine(boss, CnzEndBossInstance.Routine.ALIGN);

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
