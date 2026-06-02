package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameStateManager;
import com.openggf.game.GameRng;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    void activeBodyUsesRomChildObjectDataMetadata() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);

        advanceToRising(madmole, player);

        assertEquals(0x0C, madmole.getOnScreenHalfWidth(),
                "word_8D9B4 body-child width_pixels byte is $0C once ChildObjDat_8D9C0 is active");
        assertEquals(0x0C, madmole.getOnScreenHalfHeight(),
                "word_8D9B4 body-child height_pixels byte is $0C once ChildObjDat_8D9C0 is active");
        assertEquals(0x0B, madmole.getCollisionFlags(),
                "word_8D9B4 body-child collision byte is $0B");
        assertEquals(5, madmole.getPriorityBucket(),
                "word_8D9B4 body-child priority word $280 maps to render bucket 5");
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
    void objWaitOffscreenSuppressesRangeDetectionAndCollisionUntilSetupRuns() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);

        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 0xC0, 0);
        madmole.update(0, player);

        assertEquals("BURIED", madmole.getStateName());
        assertEquals(0x100, madmole.getY());
        assertEquals(0, madmole.getCollisionFlags());

        putMadmoleOnScreen();
        madmole.update(1, player);

        assertEquals("BURIED", madmole.getStateName(),
                "Obj_WaitOffscreen restores Obj_Madmole and returns before SetUp_ObjAttributes");
        assertEquals(0, madmole.getCollisionFlags());

        madmole.update(2, player);

        assertEquals("BURIED", madmole.getStateName(),
                "loc_8D5A6 setup returns before range detection can run");
        assertEquals(0, madmole.getCollisionFlags(),
                "ObjDat_Madmole has mapping_frame=$0D and collision_flags=0");

        madmole.update(3, player);

        assertEquals("RISING", madmole.getStateName());
        assertEquals(0x110, madmole.getY());
        assertEquals(-0x100, madmole.getYVelocity());
    }

    @Test
    void solidCapStaysAtParentPositionWhileBodyChildRises() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);
        advanceToRising(madmole, player);

        for (int frame = 3; frame <= 0x22; frame++) {
            madmole.update(frame, player);
        }

        assertEquals("PAUSING", madmole.getStateName());
        assertEquals(0x0F0, madmole.getY(),
                "ChildObjDat_8D9C0 starts the body child at parent y+$10 before loc_8D636 rises for $20 pixels");

        SolidObjectParams params = madmole.getSolidParams();
        assertEquals(0x10, params.offsetY(),
                "parent Obj_Madmole keeps SolidObjectFull anchored at its original y_pos while the body child rises");
        assertEquals(0x100, madmole.getY() + params.offsetY());
    }

    @Test
    void staysBuriedUntilPlayerIsWithinRomA0Range() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x1C0, 0x100);

        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);

        assertEquals("BURIED", madmole.getStateName());
        assertEquals(0x120, madmole.getX());
        assertEquals(0x100, madmole.getY());

        player.setCentreX((short) 0x1BF);
        madmole.update(3, player);

        assertEquals("RISING", madmole.getStateName());
        assertEquals(-0x100, madmole.getYVelocity());
        assertEquals(0x1F, madmole.getTimer());
    }

    @Test
    void nativeP2InsideRomRangeWakesMadmoleWhenP1IsTooFar() {
        TestablePlayableSprite sidekick = player(0x121, 0x100);
        MadmoleBadnikInstance madmole = madmole(new TestObjectServices()
                .withSidekicks(List.of(sidekick)));
        TestablePlayableSprite sonic = player(0x1C0, 0x100);

        advancePastWaitOffscreenInit(madmole, sonic);
        madmole.update(2, sonic);

        assertEquals("RISING", madmole.getStateName(),
                "loc_8D5B0 uses Find_SonicTails before testing d2<$A0");
        assertEquals(-0x100, madmole.getYVelocity());
    }

    @Test
    void deadPlayerInsideRomRangeStillWakesMadmole() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x121, 0x100);
        player.setDead(true);

        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);

        assertEquals("RISING", madmole.getStateName(),
                "loc_8D5B0 only checks Find_SonicTails distance d2<$A0; it does not gate on player death");
        assertEquals(-0x100, madmole.getYVelocity());
    }

    @Test
    void risesPausesDrillsThenSinksBeforeCooldown() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);

        advanceToRising(madmole, player);

        for (int frame = 3; frame <= 0x22; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("PAUSING", madmole.getStateName());
        assertEquals(0x0F0, madmole.getY());
        assertEquals(0x1F, madmole.getTimer());

        for (int frame = 0x23; frame <= 0x42; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("DRILLING", madmole.getStateName());

        for (int frame = 0x43; frame <= 0x5F; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("SINKING", madmole.getStateName());
        assertEquals(0x100, madmole.getYVelocity());

        for (int frame = 0x60; frame <= 0x7F; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("COOLDOWN", madmole.getStateName());
        assertEquals(60, madmole.getTimer());

        for (int frame = 0x80; frame <= 0xBB; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("COOLDOWN", madmole.getStateName(),
                "Obj_Wait decrements $2E and returns while the timer is still zero");
        assertEquals(0, madmole.getTimer());

        madmole.update(0xBC, player);
        assertEquals("BURIED", madmole.getStateName());
        assertEquals(0x100, madmole.getY());
    }

    @Test
    void cooldownExitsOnlyAfterObjWaitTimerUnderflowsSigned() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);

        advanceToRising(madmole, player);
        for (int frame = 3; frame <= 0x7F; frame++) {
            madmole.update(frame, player);
        }

        assertEquals("COOLDOWN", madmole.getStateName());
        assertEquals(60, madmole.getTimer());

        for (int frame = 0x80; frame < 0x80 + 60; frame++) {
            madmole.update(frame, player);
        }

        assertEquals("COOLDOWN", madmole.getStateName(),
                "Madmole routine 6 calls Obj_Wait, whose subq.w/bmi leaves timer value 0 waiting");
        assertEquals(0, madmole.getTimer());

        madmole.update(0x80 + 60, player);

        assertEquals("BURIED", madmole.getStateName(),
                "Obj_Wait jumps through $34 only once the word timer has underflowed negative");
    }

    @Test
    void attackStartupUsesRomRawAnimationBeforeSideDrillFrames() {
        MadmoleBadnikInstance madmole = madmole();
        TestablePlayableSprite player = player(0x100, 0x100);
        advanceToRising(madmole, player);

        for (int frame = 3; frame <= 0x42; frame++) {
            madmole.update(frame, player);
        }
        assertEquals("DRILLING", madmole.getStateName());

        madmole.update(0x43, player);

        assertEquals(1, mappingFrameOf(madmole),
                "loc_8D67A runs Animate_Raw over byte_8D9D8; fresh anim_frame=0 advances to frame byte 1");
    }

    @Test
    void sideDrillPhasePlaysSpikeMoveAndUsesRomRawFrameDelay() {
        CapturingServices services = new CapturingServices();
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        advanceToRising(madmole, player);

        for (int frame = 3; frame <= 0x48; frame++) {
            madmole.update(frame, player);
        }
        assertEquals(2, mappingFrameOf(madmole));

        madmole.update(0x49, player);
        assertEquals(List.of(Sonic3kSfx.SPIKE_MOVE.id), services.soundIds,
                "loc_8D680 plays sfx_SpikeMove when byte_8D9D8 reaches its $F4 callback");
        assertEquals(2, mappingFrameOf(madmole),
                "the $F4 callback swaps to byte_8D9DD but does not animate the new script until the next frame");

        madmole.update(0x4A, player);
        assertEquals(3, mappingFrameOf(madmole));
        madmole.update(0x4B, player);
        assertEquals(3, mappingFrameOf(madmole));
        madmole.update(0x52, player);
        assertEquals(3, mappingFrameOf(madmole));
        madmole.update(0x53, player);
        assertEquals(4, mappingFrameOf(madmole),
                "byte_8D9DD uses Animate_Raw delay 2, so frame 3 is held before advancing to frame 4");
    }

    @Test
    void sideDrillCallbackSpawnsCollisionChildAtRomFacingOffset() {
        ObjectManager objectManager = mock(ObjectManager.class);
        CapturingServices services = new CapturingServices(objectManager);
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        advanceToRising(madmole, player);

        for (int frame = 3; frame <= 0x49; frame++) {
            madmole.update(frame, player);
        }

        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager).addDynamicObjectAfterCurrent(captor.capture());
        MadmoleBadnikInstance.SideDrillChild child =
                assertInstanceOf(MadmoleBadnikInstance.SideDrillChild.class, captor.getValue());
        assertEquals(0x120 - 0x0E, child.getX(),
                "ChildObjDat_8D9C8 uses x offset -$E when the body is facing left");
        assertEquals(0x0F0 - 0x0C, child.getY(),
                "ChildObjDat_8D9C8/8D9D0 use y offset -$C from the raised body child");

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
        assertEquals(0x200 + 0x38, sideChildIntField(child, "yVelocity"));
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
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);

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

        child.update(0x49, player);

        assertEquals(child.getX() - 8, player.getCentreX(),
                "loc_8D7A8 pins the captured player to x_pos(a0)-8 while x_vel is negative");
        assertEquals(child.getY() + 8, player.getCentreY(),
                "loc_8D7A8 pins the captured player to y_pos(a0)+8");
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
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x4A);

        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds,
                "after sub_8D94A sets routine=8, the side drill carries the captured player instead of re-running capture");
    }

    @Test
    void arcingSideDrillRawCallbackReboundsWhileBelowRomReleaseVelocity() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        services.soundIds.clear();

        for (int frame = 0x49; frame <= 0x5E; frame++) {
            child.update(frame, player);
        }

        assertEquals(-0x500, sideChildIntField(child, "yVelocity"),
                "byte_8D9E7's $FC callback loc_8D846 resets y_vel to -$500 while y_vel<$A00");
        assertEquals(List.of(Sonic3kSfx.FLIPPER.id), services.soundIds);
        assertEquals(true, player.isObjectControlled(),
                "loc_8D846's below-threshold branch rebounds the drill without releasing the captured player");
    }

    @Test
    void arcingSideDrillRawCallbackReleasesPlayerAtRomThresholdVelocity() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 4);
        CapturingServices services = new CapturingServices(mock(ObjectManager.class));
        services.withRng(rng);
        MadmoleBadnikInstance.SideDrillChild child = spawnedSideDrillChild(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        child.update(0x48, player);
        TouchResponseListener listener = assertInstanceOf(TouchResponseListener.class, child);
        listener.onTouchResponse(player, new TouchResponseResult(0x18, 0x18, 0x08, TouchCategory.ENEMY), 0x49);
        setSideChildIntField(child, "yVelocity", 0xA00);
        setSideChildIntField(child, "animFrame", 7);
        setSideChildIntField(child, "animTimer", 0);

        child.update(0x49, player);

        assertEquals(false, player.isObjectControlled(),
                "loc_8D846's threshold branch clears object_control and releases the captured player");
        assertEquals(-0x300, player.getYSpeed());
        assertEquals(-0x380, player.getXSpeed());
        assertEquals(-0x200, sideChildIntField(child, "yVelocity"));
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
        assertEquals(true, player.isObjectControlled());

        AbstractObjectInstance.updateCameraBounds(0, 0, 0x100, 0x100, 0);
        setSideChildIntField(child, "currentX", 0x0500);
        setSideChildIntField(child, "currentY", 0x0500);
        child.update(0x49, player);

        assertEquals(true, child.isDestroyed(),
                "loc_8D6E6 deletes the side drill when its custom camera window test fails");
        assertEquals(false, player.isObjectControlled(),
                "loc_8D724 clears object_control on the captured player before deleting the offscreen drill");
        assertEquals(true, player.getAir(),
                "loc_8D724 also leaves Status_InAir set on the released player");
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

    private static void advanceToRising(MadmoleBadnikInstance madmole,
            TestablePlayableSprite player) {
        advancePastWaitOffscreenInit(madmole, player);
        madmole.update(2, player);
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
        ObjectManager objectManager = services.objectManager;
        MadmoleBadnikInstance madmole = madmole(services);
        TestablePlayableSprite player = player(0x100, 0x100);
        advanceToRising(madmole, player);
        for (int frame = 3; frame <= 0x49; frame++) {
            madmole.update(frame, player);
        }
        ArgumentCaptor<ObjectInstance> captor = ArgumentCaptor.forClass(ObjectInstance.class);
        verify(objectManager).addDynamicObjectAfterCurrent(captor.capture());
        MadmoleBadnikInstance.SideDrillChild child =
                assertInstanceOf(MadmoleBadnikInstance.SideDrillChild.class, captor.getValue());
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
