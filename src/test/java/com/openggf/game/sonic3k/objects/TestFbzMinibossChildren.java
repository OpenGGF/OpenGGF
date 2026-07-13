package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzMinibossChildren {
    @Test
    void initialTableHasSevenExactStableRolesAndTwoIndependentFiveLinkArms() {
        assertArrayEquals(new String[] {
                "cover-left", "cover-right", "cover-centre", "plunger", "aimer", "arm-left", "arm-right"
        }, FbzMinibossInstance.initialRoleNames());
        assertEquals(18, FbzMinibossInstance.fullPersistentGraphSlots());
        assertEquals(19, FbzMinibossInstance.peakGraphSlots());
        assertEquals(5, FbzMinibossArmChild.LINK_COUNT);
    }

    @Test
    void plungerGivesAllPlayersSolidContactButOnlyNativeP1PublishesStartBit() {
        PlayableEntity p1 = mock(PlayableEntity.class);
        PlayableEntity p2 = mock(PlayableEntity.class);
        PlayableEntity extra = mock(PlayableEntity.class);
        QueryServices services = new QueryServices(p1, List.of(p2, extra));
        FbzMinibossInstance boss = boss(services);
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        plunger.setServices(services);

        plunger.onStandingContact(p2, true);
        plunger.onStandingContact(extra, true);
        plunger.update(0, p1);
        assertFalse(boss.isPlungerStarted());
        plunger.onStandingContact(p1, true);
        plunger.update(1, p1);
        assertTrue(boss.isPlungerStarted());
        plunger.onStandingContact(p1, false);
        plunger.update(2, p1);
        assertFalse(boss.isPlungerStarted(), "P1's cleared standing bit is visible immediately");
    }

    @Test
    void bodyAndPlungerUseS3kSolidObjectFullInclusiveFreshContactEdge() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);

        assertTrue(boss.usesInclusiveRightEdge(),
                "sub_6F786 calls SolidObjectFull, whose SolidObject_cont cmp/bhi accepts relX == width*2");
        assertTrue(plunger.usesInclusiveRightEdge(),
                "sub_6F796 calls SolidObjectFull, whose SolidObject_cont cmp/bhi accepts relX == width*2");
    }

    @Test
    void aimerUsesClosestNativePairWithP1TieAndLungeAlwaysCapturesP1() {
        PlayableEntity p1 = mock(PlayableEntity.class);
        PlayableEntity p2 = mock(PlayableEntity.class);
        PlayableEntity extra = mock(PlayableEntity.class);
        when(p1.getCentreX()).thenReturn((short) 0x2E00);
        when(p2.getCentreX()).thenReturn((short) 0x2E20);
        when(extra.getCentreX()).thenReturn((short) 0x2E10);
        QueryServices services = new QueryServices(p1, List.of(p2, extra));
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss(services));
        aimer.setServices(services);
        assertSame(p1, aimer.closestNativePlayer(0x2E10));
        assertSame(p1, aimer.captureOutwardLungeTarget());
    }

    @Test
    void interpolationUsesFiveEqualRomSegmentsAndTerminalClosesCycle() {
        assertArrayEquals(new int[] {20, 40, 60, 80, 100},
                FbzMinibossChainLink.interpolateFive(0, 100));
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossChainLink[] links = arm.createLinksForTest();
        assertEquals(5, links.length);
        assertSame(arm, links[0].previous());
        for (int i = 1; i < links.length; i++) assertSame(links[i - 1], links[i].previous());
        assertSame(links[0], arm.next());
        assertSame(arm, links[4].next());
    }

    @Test
    void coverAndAimerActivationAreSetupOnlyAndUseExactWaitCounts() {
        assertArrayEquals(new int[] {33, 33, 65}, FbzMinibossCoverChild.waitUpdates());
        assertEquals(65, FbzMinibossAimerChild.activationWaitUpdates());

        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        int startX = cover.getX();
        boss.activateFromNativeP1Plunger();
        cover.update(0, null);
        for (int frame = 1; frame <= 32; frame++) cover.update(frame, null);
        assertEquals(startX - 8, cover.getX());
        cover.update(33, null);
        assertEquals(startX - 9, cover.getX(), "MoveSprite2 runs before the 33rd --timer expiry");
        cover.update(34, null);
        assertEquals(startX - 9, cover.getX());
    }

    @Test
    void renderPrioritiesChangeAtTheExactChainDeploymentCallback() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss);
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossChainLink[] links = arm.createLinksForTest();

        assertEquals(2, cover.getPriorityBucket());
        assertEquals(5, plunger.getPriorityBucket());
        assertEquals(2, aimer.getPriorityBucket());
        assertEquals(6, arm.getPriorityBucket());
        assertEquals(5, links[0].getPriorityBucket());
        assertEquals(4, links[4].getPriorityBucket());

        for (FbzMinibossChainLink link : links) link.update(0, null);
        arm.setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        for (FbzMinibossChainLink link : links) link.update(1, null);
        for (int frame = 0; frame < 16; frame++) {
            for (FbzMinibossChainLink link : links) link.update(frame + 2, null);
        }
        assertEquals(3, links[0].getPriorityBucket());
        assertEquals(1, links[4].getPriorityBucket());
    }

    @Test
    void coverAndAimerDeleteOnTheDefeatReleaseConversionPass() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss);

        boss.setRootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE);
        cover.update(0, null);
        aimer.update(0, null);

        assertTrue(cover.isDestroyed(), "Child_Draw_Sprite2 deletes the cover in the conversion pass");
        assertTrue(aimer.isDestroyed(), "Child_Draw_Sprite2 deletes the aimer in the conversion pass");
    }

    @Test
    void rootAndPlungerReuseSolidParamsAndHotStateSwitchesDoNotAllocateEnumArrays() throws IOException {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        assertSame(boss.getSolidParams(), boss.getSolidParams());
        assertSame(plunger.getSolidParams(), plunger.getSolidParams());

        Path packageDir = Path.of("src/main/java/com/openggf/game/sonic3k/objects");
        for (String source : List.of("FbzMinibossInstance.java", "FbzMinibossArmChild.java",
                "FbzMinibossChainLink.java", "FbzMinibossAimerChild.java")) {
            String text = Files.readString(packageDir.resolve(source));
            assertFalse(text.contains(".values()["), source + " must not allocate enum arrays in update/render");
        }
    }

    private static FbzMinibossInstance boss(TestObjectServices services) {
        FbzMinibossInstance boss = new FbzMinibossInstance(new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        boss.setServices(services);
        return boss;
    }

    private static final class QueryServices extends TestObjectServices {
        private final ObjectPlayerQuery query;
        QueryServices(PlayableEntity p1, List<? extends PlayableEntity> sidekicks) {
            query = new ObjectPlayerQuery(() -> p1, () -> sidekicks);
        }
        @Override public ObjectPlayerQuery playerQuery() { return query; }
    }
}
