package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lightning-shield attracted ring is a subsystem-backed SST: {@code Obj_Attracted_Ring}
 * (sonic3k.asm:35750-35787) moves in its own object slot and ends at {@code Draw_Sprite}.
 * The engine reaches that slot only through the ring manager's external slot dispatch
 * on the live {@link ObjectManager}. An object-manager-only reset (the trace-replay
 * prelude, the S3K title-only object system) used to drop that binding; the rings then
 * moved through the legacy late update but no slot ever reached {@code Draw_Sprite},
 * so they vanished from their placements instead of flying to the player.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAttractedRingNativeSlotBinding {
    private static final int ZONE_MGZ = 2;
    private static SharedLevel sharedLevel;
    private HeadlessTestFixture fixture;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_MGZ, 0);
    }

    @AfterAll
    static void dispose() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
    }

    @Test
    void attractedRingReachesDrawSpriteAfterOrdinaryLoad() {
        assertAttractedRingAdmitted();
    }

    @Test
    void attractedRingReachesDrawSpriteAfterObjectManagerOnlyReset() {
        ObjectManager objects = GameServices.level().getObjectManager();
        objects.reset(GameServices.camera().getX());
        assertAttractedRingAdmitted();
    }

    private void assertAttractedRingAdmitted() {
        AbstractPlayableSprite sonic = fixture.sprite();
        int startX = sonic.getCentreX();
        RingSpawn ring = GameServices.level().getCurrentLevel().getRings().stream()
                .filter(r -> r.x() > startX + 0x60)
                .min(Comparator.comparingInt(RingSpawn::x))
                .orElse(null);
        assertNotNull(ring, "MGZ1 has a placed ring ahead of the start");
        // Inside the 0x40 attraction box but outside the 6px touch box; centre the
        // camera so the ring placement window and the object range include both.
        NativePositionOps.writeXPosPreserveSubpixel(sonic, ring.x() - 0x30);
        NativePositionOps.writeYPosPreserveSubpixel(sonic, ring.y());
        GameServices.camera().setX((short) (ring.x() - 0xA0));
        GameServices.camera().setY((short) Math.max(0, ring.y() - 0x70));
        sonic.giveShield(ShieldType.LIGHTNING);
        for (int frame = 0; frame < 4; frame++) {
            fixture.stepIdleFrames(1);
            ObjectManager objects = GameServices.level().getObjectManager();
            boolean externalAdmission = NativeSpriteSubmissionAccess.admissions(objects).stream()
                    .anyMatch(a -> NativeSstSlotAccess.currentOwner(objects, a.slot()) == null);
            if (externalAdmission) return;
        }
        throw new AssertionError("no attracted-ring SST reached Draw_Sprite within 4 frames");
    }
}
