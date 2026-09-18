package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameStateManager;
import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.TestablePlayableSprite;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestMadmoleBadnikInstance {

    @BeforeEach
    void setUp() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void registryCreatesMadmoleForSklSlot8cInMhz() {
        Sonic3kObjectRegistry registry = new MhzRegistry();

        ObjectInstance instance = registry.create(new ObjectSpawn(0x120, 0x100,
                Sonic3kObjectIds.MADMOLE, 0, 0, false, 0));

        assertInstanceOf(MadmoleBadnikInstance.class, instance);
    }

    @Test
    void wakingCapAllocatesSeparateBodyAfterItsOwnSlot() {
        ObjectManager objectManager = mock(ObjectManager.class);
        CapturingServices services = new CapturingServices(objectManager);
        MadmoleBadnikInstance madmole = madmole(services);
        madmole.setSlotIndex(17);
        TestablePlayableSprite player = player(0x120, 0x100);

        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);

        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager).addDynamicObjectAfterSlot(captor.capture(), eq(17));
        MadmoleBadnikInstance.MadmoleBodyChild body =
                assertInstanceOf(MadmoleBadnikInstance.MadmoleBodyChild.class, captor.getValue(),
                        "loc_8D5BE CreateChild1_Normal allocates the body with AllocateObjectAfterCurrent");
        assertEquals(0x120, body.getX(), "ChildObjDat_8D9C0 x offset is 0");
        assertEquals(0x110, body.getY(), "ChildObjDat_8D9C0 y offset is +$10");
        assertEquals(true, madmole.isBodyBusy(), "loc_8D5BE sets $38 bit 1 before allocating the body");
        assertEquals("WAIT_FOR_BODY", madmole.getStateName(), "loc_8D5BE sets parent routine 4");
        assertEquals(false, madmole.isDestroyed());
    }

    @Test
    void usesRomRenderBoundsAndSideDrillPriorityFromObjectData() {
        MadmoleBadnikInstance madmole = madmole();
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild();

        assertEquals(0x18, madmole.getOnScreenHalfWidth(),
                "ObjDat_Madmole width_pixels byte is $18");
        assertEquals(0x04, madmole.getOnScreenHalfHeight(),
                "ObjDat_Madmole height_pixels byte is $04");
        assertEquals(5, madmole.getPriorityBucket(),
                "ObjDat_Madmole priority word $280 maps to render bucket 5");
        assertEquals(0x08, child.getOnScreenHalfWidth(),
                "word_8D9BA side drill width_pixels byte is $08");
        assertEquals(0x08, child.getOnScreenHalfHeight(),
                "word_8D9BA side drill height_pixels byte is $08");
        assertEquals(5, child.getPriorityBucket(),
                "word_8D9BA side drill priority word $280 maps to render bucket 5");
    }

    @Test
    void buriedCapUsesRomMappingFrameAndHasNoTouchCollision() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x1C0, 0x100);

        advancePastWaitOffscreenInit(madmole, player);

        assertEquals(0x0D, mappingFrameOf(madmole),
                "ObjDat_Madmole stores mapping_frame=$0D for the buried cap");
        assertEquals(0, madmole.getCollisionFlags(),
                "ObjDat_Madmole's final byte is collision_flags=0; the cap is solid-only via SolidObjectFull");
    }

    @Test
    void bodyUsesRomChildObjectDataMetadataWhileCapKeepsNoCollision() {
        Rig rig = wake();

        assertEquals(0x0C, rig.body.getOnScreenHalfWidth(), "word_8D9B4 body width_pixels byte is $0C");
        assertEquals(0x0C, rig.body.getOnScreenHalfHeight(), "word_8D9B4 body height_pixels byte is $0C");
        assertEquals(0x0B, rig.body.getCollisionFlags(), "word_8D9B4 body collision byte is $0B");
        assertEquals(5, rig.body.getPriorityBucket(), "word_8D9B4 priority word $280 maps to render bucket 5");
        assertEquals(0, rig.madmole.getCollisionFlags(), "the cap never gains the body's $0B collision");
    }

    @Test
    void capAndBodyRenderFromTheirOwnObjects() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MADMOLE)).thenReturn(renderer);
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withLevelManager(levelManager);
        Rig rig = wake(services);

        step(rig, 3);
        rig.body.appendRenderCommands(new ArrayList<>());
        rig.madmole.appendRenderCommands(new ArrayList<>());

        InOrder inOrder = inOrder(renderer);
        // The creation pass rose y+$10 -> y+$F (loc_8D620->loc_8D636); pass 3 rises to y+$E.
        inOrder.verify(renderer).drawFrameIndex(0, 0x120, 0x10E, false, false);
        inOrder.verify(renderer).drawFrameIndex(0x0D, 0x120, 0x100, false, false);
    }

    @Test
    void exposesRomSolidObjectFullCapDimensions() {
        MadmoleBadnikInstance madmole = madmole();

        SolidObjectProvider solid = assertInstanceOf(SolidObjectProvider.class, madmole,
                "Obj_Madmole calls SolidObjectFull with d1=$1F,d2=4,d3=5 after its routine");
        SolidObjectParams params = solid.getSolidParams();

        assertEquals(0x1F, params.halfWidth());
        assertEquals(4, params.airHalfHeight());
        assertEquals(5, params.groundHalfHeight());
        assertEquals(0, params.offsetX());
        assertEquals(0, params.offsetY());
    }

    @Test
    void objWaitOffscreenSuppressesRangeDetectionUntilSetupRuns() {
        ObjectManager objectManager = mock(ObjectManager.class);
        CapturingServices services = new CapturingServices(objectManager);
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x100, 0x100);

        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 0xC0, 0);
        madmole.update(0, player);
        assertEquals("WAIT_FOR_PLAYER", madmole.getStateName());

        putMadmoleOnScreen();
        madmole.update(1, player);
        madmole.update(2, player);
        verify(objectManager, never()).addDynamicObjectAfterCurrent(org.mockito.ArgumentMatchers.any());
        assertEquals("WAIT_FOR_PLAYER", madmole.getStateName(),
                "Obj_WaitOffscreen and loc_8D5A6 setup each return before range detection");

        madmole.update(3, player);
        assertEquals("WAIT_FOR_BODY", madmole.getStateName());
        MadmoleBadnikInstance.MadmoleBodyChild body = capturedBody(objectManager);
        body.setServices(services);
        body.update(3, player);
        assertEquals(0x10F, body.getY(),
                "loc_8D620 falls through to loc_8D636, so the body rises one pixel on its creation pass");
        assertEquals(-0x100, body.getYVelocity());
        assertEquals(0x100, madmole.getY(), "the cap never moves");
    }

    @Test
    void staysBuriedUntilPlayerIsWithinRomA0Range() {
        ObjectManager objectManager = mock(ObjectManager.class);
        CapturingServices services = new CapturingServices(objectManager);
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x1C0, 0x100);

        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);
        assertEquals("WAIT_FOR_PLAYER", madmole.getStateName());

        player.setCentreX((short) 0x1BF);
        madmole.update(3, player);
        assertEquals("WAIT_FOR_BODY", madmole.getStateName());
        MadmoleBadnikInstance.MadmoleBodyChild body = capturedBody(objectManager);
        body.setServices(services);
        body.update(3, player);
        assertEquals(0x1E, body.getTimer(),
                "loc_8D620 sets $2E=$1F then loc_8D636's subq.w #1 leaves $1E on the creation pass");
    }

    @Test
    void nativeP2InsideRomRangeWakesMadmoleWhenP1IsTooFar() {
        TestablePlayableSprite sidekick = player(0x121, 0x100);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withSidekicks(List.of(sidekick));
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite sonic = player(0x1C0, 0x100);

        advancePastWaitOffscreenInit(madmole, sonic);
        madmole.update(2, sonic);

        assertEquals("WAIT_FOR_BODY", madmole.getStateName(),
                "loc_8D5B0 uses Find_SonicTails before testing d2<$A0");
    }

    @Test
    void deadPlayerInsideRomRangeStillWakesMadmole() {
        MadmoleBadnikInstance madmole = madmole(new CapturingServices(mock(ObjectManager.class)));
        TestablePlayableSprite player = player(0x121, 0x100);
        player.setDead(true);

        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);

        assertEquals("WAIT_FOR_BODY", madmole.getStateName(),
                "loc_8D5B0 only checks Find_SonicTails distance d2<$A0; it does not gate on player death");
    }

    @Test
    void bodyRisesPausesDrillsSinksThenCapCoolsDown() {
        Rig rig = wake();

        int frame = stepWhileBody(rig, 3, "RISING");
        assertEquals("PAUSING", rig.body.getStateName());
        assertEquals(0x0F0, rig.body.getY());
        assertEquals(0x1F, rig.body.getTimer(), "loc_8D648 sets $2E=$1F when the body finishes rising");

        frame = stepWhileBody(rig, frame, "PAUSING");
        assertEquals("DRILLING", rig.body.getStateName());

        frame = stepWhileBody(rig, frame, "DRILLING");
        assertEquals("SINKING", rig.body.getStateName());
        assertEquals(0x100, rig.body.getYVelocity());

        while (!rig.body.isDeletePending()) {
            step(rig, frame++);
            assertEquals("WAIT_FOR_BODY", rig.madmole.getStateName());
        }
        step(rig, frame++);
        assertEquals("COOLDOWN", rig.madmole.getStateName(),
                "the cap runs before the body, so it sees the cleared busy bit one pass later");
        assertEquals(60, rig.madmole.getTimer());
        assertEquals(true, rig.body.isDestroyed());

        for (int i = 0; i < 60; i++) {
            rig.madmole.update(frame++, rig.player);
        }
        assertEquals("COOLDOWN", rig.madmole.getStateName(),
                "Obj_Wait subq.w/bmi leaves timer value 0 waiting");
        assertEquals(0, rig.madmole.getTimer());
        rig.madmole.update(frame, rig.player);
        assertEquals("WAIT_FOR_PLAYER", rig.madmole.getStateName(),
                "Obj_Wait jumps through $34 = loc_8D5FA only once the word timer underflows");
    }

    @Test
    void goDeleteSpriteKeepsBodyDrawnAndTouchableUntilTheNextPass() {
        Rig rig = wake();
        int frame = stepWhileBody(rig, 3, "RISING");
        frame = stepWhileBody(rig, frame, "PAUSING");
        frame = stepWhileBody(rig, frame, "DRILLING");
        while (rig.body.getTimer() > 0) {
            step(rig, frame++);
        }
        assertEquals(0x10F, rig.body.getY());

        step(rig, frame++);

        assertEquals(true, rig.body.isDeletePending());
        assertEquals(0x110, rig.body.getY(), "loc_8D6CA moves before Obj_Wait calls loc_8D6D6");
        assertEquals(0x0B, rig.body.getCollisionFlags(),
                "loc_8D602 still calls Child_DrawTouch_Sprite after Go_Delete_Sprite returns");
        assertEquals(false, rig.body.isDestroyed(), "Go_Delete_Sprite does not free the slot this pass");
        assertEquals(false, rig.madmole.isBodyBusy(), "loc_8D6D6 clears $38 bit 1 on parent3");
        assertEquals("WAIT_FOR_BODY", rig.madmole.getStateName());

        step(rig, frame);

        assertEquals(true, rig.body.isDestroyed(), "Delete_Current_Sprite runs on the next pass");
        assertEquals("COOLDOWN", rig.madmole.getStateName());
    }

    @Test
    void attackStartupUsesRomRawAnimationBeforeSideDrillFrames() {
        Rig rig = wake();
        int frame = stepWhileBody(rig, 3, "RISING");
        frame = stepWhileBody(rig, frame, "PAUSING");
        assertEquals("DRILLING", rig.body.getStateName());

        step(rig, frame);

        assertEquals(1, rig.body.getMappingFrame(),
                "loc_8D67A runs Animate_Raw over byte_8D9D8; fresh anim_frame=0 advances to frame byte 1");
    }

    @Test
    void sideDrillPhasePlaysSpikeMoveAndUsesRomRawFrameDelay() {
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        Rig rig = wake(services);
        int frame = stepWhileBody(rig, 3, "RISING");
        frame = stepWhileBody(rig, frame, "PAUSING");

        while (services.soundIds.isEmpty()) {
            step(rig, frame++);
        }
        assertEquals(List.of(Sonic3kSfx.SPIKE_MOVE.id), services.soundIds,
                "loc_8D680 plays sfx_SpikeMove when byte_8D9D8 reaches its $F4 callback");
        assertEquals(2, rig.body.getMappingFrame(),
                "the $F4 callback swaps to byte_8D9DD but does not animate the new script until the next frame");

        step(rig, frame++);
        assertEquals(3, rig.body.getMappingFrame(), "byte_8D9DD's first animated frame after the $F4 swap is 3");
        int threeHold = 1;
        while (rig.body.getMappingFrame() == 3) {
            step(rig, frame++);
            if (rig.body.getMappingFrame() == 3) {
                threeHold++;
            }
        }
        assertEquals(3, threeHold,
                "byte_8D9DD uses Animate_Raw delay 2, so the displayed frame 3 is held three engine frames");
        assertEquals(4, rig.body.getMappingFrame());
    }

    @Test
    void sideDrillCallbackSpawnsCollisionChildFromBodyAtRomFacingOffset() {
        ObjectManager objectManager = mock(ObjectManager.class);
        Rig rig = wake(new CapturingServices(objectManager));
        rig.body.setSlotIndex(21);

        for (int frame = 3; frame <= 0x49; frame++) {
            step(rig, frame);
        }

        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager).addDynamicObjectAfterSlot(captor.capture(), eq(21));
        MadmoleBadnikInstance.SideDrillChild child =
                assertInstanceOf(MadmoleBadnikInstance.SideDrillChild.class, captor.getValue(),
                        "loc_8D680 runs CreateChild1_Normal from the body, so the drill follows the body's slot");
        assertEquals(0x120 - 0x0E, child.getX(),
                "ChildObjDat_8D9C8 uses x offset -$E when the body is facing left");
        assertEquals(0x0F0 - 0x0C, child.getY(),
                "ChildObjDat_8D9C8/8D9D0 use y offset -$C from the raised body");

        TouchResponseProvider touch = assertInstanceOf(TouchResponseProvider.class, child);
        assertEquals(0xD8, touch.getCollisionFlags(),
                "word_8D9BA gives the side drill child collision byte $D8");
    }

    @Test
    void sideDrillChildUsesContinuousS3kSpecialPropertyTouchProfile() {
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild();

        assertEquals(0xD8, child.getCollisionFlags(),
                "word_8D9BA gives the side drill child collision byte $D8");
        assertEquals(TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                child.getTouchResponseProfile().categoryDecodeMode(),
                "$D8 is routed through S3K Touch_Special and collision_property, not generic boss handling");
        assertEquals(true, child.getTouchResponseProfile().continuousCallbacks(),
                "sub_8D8E6/sub_8D94A clear and poll collision_property every active side-drill frame");
    }

    @Test
    void sideDrillChildUsesRomRawAnimationScript() {
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild();

        child.update(0x48, player(0x100, 0x100));
        assertEquals(5, sideChildMappingFrameOf(child),
                "loc_8D746 only sets up word_8D9BA and loc_8D89E; Animate_Raw does not run until the next frame");
        child.update(0x49, player(0x100, 0x100));
        assertEquals(6, sideChildMappingFrameOf(child),
                "routine 2/4 runs Animate_Raw over byte_8D9E7 after the setup frame");
        child.update(0x50, player(0x100, 0x100));
        assertEquals(6, sideChildMappingFrameOf(child));
        child.update(0x51, player(0x100, 0x100));
        assertEquals(6, sideChildMappingFrameOf(child));
        child.update(0x52, player(0x100, 0x100));
        assertEquals(7, sideChildMappingFrameOf(child),
                "byte_8D9E7 uses delay 2 before advancing from frame 6 to frame 7");
    }

    @Test
    void sideDrillChildInitializesRomStraightSlideFromRandomNumber() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 1);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(rng);

        child.update(0x48, player(0x100, 0x100));

        assertEquals(-0x600, sideChildIntField(child, "xVelocity"),
                "loc_8D89E selects word_8D8DE[0] when tst.b Random_Number is non-negative");
        assertEquals(0, sideChildIntField(child, "yVelocity"));
        assertEquals(0x120 - 0x0E, child.getX(),
                "loc_8D746/8D89E initializes velocity only; MoveSprite2 starts on the next side-child frame");

        child.update(0x49, player(0x100, 0x100));
        assertEquals(0x120 - 0x0E - 6, child.getX());
        assertEquals(0x0F0 - 0x0C, child.getY());
    }

    @Test
    void sideDrillChildInitializesRomArcingDrillFromRandomNumber() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(rng);

        child.update(0x48, player(0x100, 0x100));

        assertEquals(-0x380, sideChildIntField(child, "xVelocity"),
                "loc_8D89E selects word_8D8DE[1] when tst.b Random_Number is negative");
        assertEquals(0x200, sideChildIntField(child, "yVelocity"));

        child.update(0x49, player(0x100, 0x100));
        assertEquals(0x120 - 0x0E - 4, child.getX());
        assertEquals(0x0F0 - 0x0C + 2, child.getY(),
                "loc_8D778 uses MoveSprite_LightGravity, moving with old y_vel before gravity is applied");
        assertEquals(0x200 + 0x20, sideChildIntField(child, "yVelocity"),
                "MoveSprite_LightGravity (sonic3k.asm:178357) applies moveq #$20 gravity, not the $38 object default");
    }

    @Test
    void straightSideDrillTouchLaunchesPlayerWithRomFlipperResponse() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 1);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        services.soundIds.clear();

        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child,
                "loc_8D768 polls sub_8D8E6 for collision_property on the straight drill branch");
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);

        assertEquals(List.of(), services.soundIds,
                "TouchResponse only writes collision_property; the later side-drill SST slot owns sub_8D8E6");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(false, player.getAir());

        child.update(0x49, player);

        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds);
        assertEquals(-0xC00, player.getXSpeed(),
                "sub_8D8E6 doubles the side-drill x_vel into player x_vel");
        assertEquals(-0xC00, player.getGSpeed());
        assertEquals(-0x200, player.getYSpeed());
        assertEquals(true, player.getAir());
        assertEquals(0x1A, player.getAnimationId(),
                "sub_8D8E6 writes anim=$1A on the straight side-drill flipper response");
    }

    @Test
    void straightSideDrillStillLaunchesPlayerDuringPostHitInvulnerabilityTimer() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 1);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        player.setInvulnerableFrames(0x78);
        player.setInvincibleFrames(0);
        child.update(0x48, player);
        services.soundIds.clear();

        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);

        assertEquals(List.of(), services.soundIds,
                "post-hit invulnerability still permits collision_property, but not an inline player mutation");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());

        child.update(0x49, player);

        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds,
                "sub_8D8E6 checks Status_Invincible in status_secondary, not invulnerable_time");
        assertEquals(-0xC00, player.getXSpeed());
        assertEquals(-0x200, player.getYSpeed());
    }

    @Test
    void arcingSideDrillTouchCapturesAndCarriesPlayerAtRomOffset() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        services.soundIds.clear();

        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child,
                "loc_8D778 polls sub_8D94A for collision_property on the arcing branch");
        // The player TouchResponse pass only records collision_property; sub_8D94A
        // runs during the side drill's own update (loc_8D778), so the grab applies
        // on the frame the arm executes, not inside the touch callback.
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player);

        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds);
        assertEquals(true, player.getAir());
        assertEquals(true, player.isObjectControlled(),
                "sub_8D94A sets object_control(a1)=1 so the side drill owns player movement");
        assertEquals(true, player.isObjectControlAllowsCpu(),
                "object_control=1 is a native bits 0-6 state, not the signed bit-7 full-control state");
        assertEquals(true, player.isObjectControlSuppressesMovement(),
                "object_control=1 suppresses normal movement while the arcing drill carries the player");
        assertEquals(0x1A, player.getAnimationId());
        assertEquals(0, child.getPriorityBucket(),
                "sub_8D94A writes priority(a0)=0 when the arcing side drill captures a player");

        // loc_8D778 (routine 4) still moves the arm on the grab frame without
        // carrying; the carry (loc_8D7A8, routine 8) starts the next frame and pins
        // the player to the arm's pre-move coordinates before MoveSprite runs.
        int armXBeforeCarry = child.getX();
        int armYBeforeCarry = child.getY();
        child.update(0x4A, player);

        assertEquals(armXBeforeCarry - 8, player.getCentreX(),
                "loc_8D7A8 pins the captured player to the pre-move x_pos(a0)-8 while x_vel is negative");
        assertEquals(armYBeforeCarry + 8, player.getCentreY(),
                "loc_8D7A8 pins the captured player to the pre-move y_pos(a0)+8");
    }

    @Test
    void arcingSideDrillCarryPreservesCapturedPlayerSubpixels() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        // Give the CPU the ROM's frozen carry subpixels (Player_2 x_sub/y_sub).
        player.setSubpixelRaw(0xF600, 0x2E00);
        child.update(0x48, player);

        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player); // sub_8D94A captures (grab frame, no carry yet)

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));

            child.update(0x4A, player); // loc_8D7A8 carry: move.w to x_pos/y_pos only
        }

        assertEquals(0xF600, player.getXSubpixelRaw(),
                "loc_8D7D4 writes x_pos(a1) with move.w, leaving the captured player's x_sub untouched");
        assertEquals(0x2E00, player.getYSubpixelRaw(),
                "loc_8D7D4 writes y_pos(a1) with move.w, leaving the captured player's y_sub untouched");
    }

    @Test
    void arcingSideDrillIgnoresRepeatedTouchPollsAfterCapture() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        services.soundIds.clear();

        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child,
                "loc_8D778 polls sub_8D94A only until it captures a player and switches to loc_8D7A8");
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player);
        // Once captured, the player is object-controlled, so a later TouchResponse
        // poll is ignored (sub_8D94A's tst.b object_control(a2) guard) and no second
        // capture / flipper is queued.
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x4A);
        child.update(0x4A, player);

        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds,
                "after sub_8D94A sets routine=8, the side drill carries the captured player instead of re-running capture");
    }

    @Test
    void arcingSideDrillFloorImpactReboundsWhileBelowRomReleaseVelocity() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player);
        // Drop the capture flipper so the assertion below only sees the rebound one.
        services.soundIds.clear();

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));

            // Run the arc until ObjHitFloor_DoRoutine invokes $34(a0) = loc_8D846.
            for (int frame = 0x4A; frame < 0x4A + 40 && services.soundIds.isEmpty(); frame++) {
                child.update(frame, player);
            }
        }

        assertEquals(-0x500, sideChildIntField(child, "yVelocity"),
                "ObjHitFloor_DoRoutine's $34(a0) hook loc_8D846 resets y_vel to -$500 while y_vel<$A00");
        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds);
        assertEquals(true, player.isObjectControlled(),
                "loc_8D846's below-threshold branch rebounds the drill without releasing the captured player");
    }

    @Test
    void arcingSideDrillFloorImpactReleasesPlayerAtRomThresholdVelocity() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));

            // ROM routine 4 (loc_8D778) still owns the capture frame; the carrying
            // routine loc_8D7A8 with its $34(a0) = loc_8D846 hook runs the frame after.
            child.update(0x49, player);
            setSideChildIntField(child, "yVelocity", 0xA00);
            child.update(0x4A, player);
        }

        assertEquals(false, player.isObjectControlled(),
                "loc_8D846's threshold branch clears object_control and releases the captured player");
        assertEquals(-0x300, player.getYSpeed());
        assertEquals(-0x380, player.getXSpeed());
        assertEquals(-0x200, sideChildIntField(child, "yVelocity"));
    }

    @Test
    void releasedArcingSideDrillCannotImmediatelyRecapturePlayer() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));

            child.update(0x49, player);
            setSideChildIntField(child, "yVelocity", 0xA00);
            child.update(0x4A, player);
            listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x4B);
            child.update(0x4B, player);
        }

        assertEquals(false, player.isObjectControlled(),
                "loc_8D834 switches the arcing side drill to routine 6, so continuous touch polling cannot recapture Sonic");
        assertEquals(-0x200, sideChildIntField(child, "yVelocity"),
                "post-release routine 6 uses MoveSprite2; it does not keep applying light gravity");
    }

    @Test
    void arcingSideDrillWallImpactReleasesPlayerBeforeTerrainClippingLoop() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player);
        services.soundIds.clear();

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(-2, (byte) 0, 0));

            // loc_8D7A8's ObjCheckWallDist runs during the carry routine, i.e. the
            // frame after sub_8D94A captured the player.
            child.update(0x4A, player);
            terrain.verify(() -> ObjectTerrainUtils.checkLeftWallDist(anyInt(), anyInt()));
        }

        assertEquals(false, player.isObjectControlled(),
                "loc_8D7A8 clears object_control when ObjCheckLeftWallDist reports a blocked carry path");
        assertEquals(true, player.getAir());
        assertEquals(0x380, player.getXSpeed(),
                "loc_8D820 negates the drill's x_vel into Sonic's x_vel on wall impact");
        assertEquals(0x1C0, sideChildIntField(child, "xVelocity"),
                "loc_8D820 halves the reversed velocity before switching the drill to routine 6");

        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x4B);
        child.update(0x4B, player);
        assertEquals(false, player.isObjectControlled(),
                "after loc_8D834 switches to routine 6, continuous touch polling must not recapture Sonic");
    }

    @Test
    void offscreenArcingSideDrillDeletesAndReleasesCapturedPlayerLikeRomWrapper() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        child.update(0x49, player);
        assertEquals(true, player.isObjectControlled());

        AbstractObjectInstance.updateCameraBounds(0, 0, 0x100, 0x100, 0);
        setSideChildIntField(child, "currentX", 0x0500);
        setSideChildIntField(child, "currentY", 0x0500);
        child.update(0x4A, player);

        assertEquals(true, child.isDestroyed(),
                "loc_8D6E6 deletes the side drill when its custom camera window test fails");
        assertEquals(false, player.isObjectControlled(),
                "loc_8D724 clears object_control on the captured player before deleting the offscreen drill");
        assertEquals(true, player.getAir(),
                "loc_8D724 also leaves Status_InAir set on the released player");
    }


    @Test
    void defeatingBodyLeavesSolidCapStumpThatNeverReEmerges() {
        ObjectManager objectManager = mock(ObjectManager.class);
        Rig rig = wake(new CapturingServices(objectManager));
        for (int frame = 3; frame <= 0x42; frame++) {
            step(rig, frame);
        }
        assertEquals("DRILLING", rig.body.getStateName());

        TouchResponseResult result = new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY);
        rig.body.onPlayerAttack(rig.player, result);

        assertEquals(true, rig.body.isDestroyed(), "EnemyDefeated converts the body's own slot to an explosion");
        assertEquals(false, rig.madmole.isDestroyed(), "the cap keeps its own slot");
        assertEquals(true, rig.madmole.isBodyBusy(),
                "only loc_8D6D6 clears $38 bit 1, and the defeated body never reaches it");

        for (int frame = 0x43; frame <= 0x140; frame++) {
            rig.madmole.update(frame, rig.player);
        }
        assertEquals("WAIT_FOR_BODY", rig.madmole.getStateName(),
                "loc_8D5D4 waits forever: the cap is a permanent solid stump");
        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager, atLeastOnce()).addDynamicObjectAfterCurrent(captor.capture());
        assertEquals(1, captor.getAllValues().stream()
                        .filter(MadmoleBadnikInstance.MadmoleBodyChild.class::isInstance).count(),
                "the cap never allocates a second body");
        assertEquals(0, rig.madmole.getSolidParams().offsetY());
    }

    @Test
    void capDeletionSendsBodyThroughChildDrawTouchGoDelete() {
        Rig rig = wake();
        step(rig, 3);
        assertEquals(0x0B, rig.body.getCollisionFlags());

        // Sprite_CheckDelete removed the cap in slot order before the body runs.
        rig.madmole.onUnload();
        rig.body.update(4, rig.player);

        assertEquals(0, rig.body.getCollisionFlags(),
                "Child_DrawTouch_Sprite branches to Go_Delete_Sprite before Add_SpriteToCollisionResponseList");
        assertEquals(false, rig.body.isDestroyed());
        assertEquals(true, rig.body.isDeletePending());

        rig.body.update(5, rig.player);
        assertEquals(true, rig.body.isDestroyed(), "Delete_Current_Sprite frees the body on the next pass");
    }

    @Test
    void bodyIgnoresTheGenericOutOfRangeUnload() {
        Rig rig = wake();
        assertEquals(true, rig.body.usesCustomOutOfRangeCheck());
        assertEquals(false, rig.body.isCustomOutOfRange(0x7000),
                "loc_8D602 never calls Sprite_CheckDelete; it follows the cap's status bit instead");
    }

    private static final class Rig {
        final MadmoleBadnikInstance madmole;
        final MadmoleBadnikInstance.MadmoleBodyChild body;
        final TestablePlayableSprite player;

        Rig(MadmoleBadnikInstance madmole, MadmoleBadnikInstance.MadmoleBodyChild body,
                TestablePlayableSprite player) {
            this.madmole = madmole;
            this.body = body;
            this.player = player;
        }
    }

    private static Rig wake() {
        return wake(new CapturingServices(mock(ObjectManager.class)));
    }

    /** Wakes the cap on pass 2 and runs the body's same-pass creation update. */
    private static Rig wake(CapturingServices services) {
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);
        MadmoleBadnikInstance.MadmoleBodyChild body = capturedBody(services.objectManager);
        body.setServices(services);
        body.update(2, player);
        return new Rig(madmole, body, player);
    }

    private static MadmoleBadnikInstance.MadmoleBodyChild capturedBody(ObjectManager objectManager) {
        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager, atLeastOnce()).addDynamicObjectAfterCurrent(captor.capture());
        return captor.getAllValues().stream()
                .filter(MadmoleBadnikInstance.MadmoleBodyChild.class::isInstance)
                .map(MadmoleBadnikInstance.MadmoleBodyChild.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** One object pass: the cap's lower slot runs before the body. */
    private static void step(Rig rig, int frame) {
        rig.madmole.update(frame, rig.player);
        rig.body.update(frame, rig.player);
    }

    private static int stepWhileBody(Rig rig, int startFrame, String state) {
        int frame = startFrame;
        for (int guard = 0; guard < 1000 && rig.body.getStateName().equals(state); guard++) {
            step(rig, frame++);
        }
        return frame;
    }

    private static MadmoleBadnikInstance madmole() {
        return madmole(new TestObjectServices().withGameState(mock(GameStateManager.class)));
    }

    private static MadmoleBadnikInstance madmole(TestObjectServices services) {
        MadmoleBadnikInstance madmole = new MadmoleBadnikInstance(new ObjectSpawn(
                0x120, 0x100, Sonic3kObjectIds.MADMOLE, 0, 0, false, 0));
        madmole.setServices(services.withGameState(mock(GameStateManager.class)));
        return madmole;
    }

    private static TestablePlayableSprite player(int x, int y) {
        return new TestablePlayableSprite("sonic", (short) x, (short) y);
    }

    private static void advancePastWaitOffscreenInit(MadmoleBadnikInstance madmole,
            TestablePlayableSprite player) {
        putMadmoleOnScreen();
        madmole.update(0, player);
        madmole.update(1, player);
    }

    private static void putMadmoleOnScreen() {
        AbstractObjectInstance.updateCameraBounds(0x80, 0x80, 0x1C0, 0x160, 0);
    }

    private static int mappingFrameOf(MadmoleBadnikInstance madmole) {
        try {
            Field field = AbstractS3kBadnikInstance.class.getDeclaredField("mappingFrame");
            field.setAccessible(true);
            return field.getInt(madmole);
        } catch (ReflectiveOperationException e) {
            fail(e);
            return -1;
        }
    }

    private static MadmoleBadnikInstance.SideDrillChild spawnedSideDrillChild() {
        return spawnedSideDrillChild(new GameRng(GameRng.Flavour.S3K, 1));
    }

    private static MadmoleBadnikInstance.SideDrillChild spawnedSideDrillChild(GameRng rng) {
        ObjectManager objectManager = mock(ObjectManager.class);
        CapturingServices services = new CapturingServices(objectManager);
        services.withRng(rng);
        return spawnedSideDrillChild(services);
    }

    private static MadmoleBadnikInstance.SideDrillChild spawnedSideDrillChild(CapturingServices services) {
        Rig rig = wake(services);
        for (int frame = 3; frame <= 0x49; frame++) {
            step(rig, frame);
        }
        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(services.objectManager, atLeastOnce()).addDynamicObjectAfterCurrent(captor.capture());
        MadmoleBadnikInstance.SideDrillChild child = captor.getAllValues().stream()
                .filter(MadmoleBadnikInstance.SideDrillChild.class::isInstance)
                .map(MadmoleBadnikInstance.SideDrillChild.class::cast)
                .findFirst()
                .orElseThrow();
        child.setServices(services);
        return child;
    }

    private static int sideChildMappingFrameOf(MadmoleBadnikInstance.SideDrillChild child) {
        return sideChildIntField(child, "mappingFrame");
    }

    private static int sideChildIntField(MadmoleBadnikInstance.SideDrillChild child, String fieldName) {
        try {
            Field field = MadmoleBadnikInstance.SideDrillChild.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(child);
        } catch (ReflectiveOperationException e) {
            fail(e);
            return -1;
        }
    }

    private static void setSideChildIntField(MadmoleBadnikInstance.SideDrillChild child, String fieldName, int value) {
        try {
            Field field = MadmoleBadnikInstance.SideDrillChild.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(child, value);
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }

    private static final class MhzRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_MHZ;
        }
    }

    private static final class CapturingServices extends TestObjectServices {
        private final List<Integer> soundIds = new ArrayList<>();
        private final ObjectManager objectManager;

        private CapturingServices() {
            this(null);
        }

        private CapturingServices(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        @Override
        public void playSfx(int soundId) {
            soundIds.add(soundId);
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }
}
