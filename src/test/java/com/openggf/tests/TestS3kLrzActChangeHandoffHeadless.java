package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lava Reef act 1 miniboss defeat, the results screen and the seamless {@code $901} change,
 * through to a player who can walk in act 2.
 *
 * <p>The ROM chain is {@code loc_787E0} -&gt; {@code Obj_EndSignControl} -&gt;
 * {@code Obj_EndSignControlAwaitStart} (which calls {@code Restore_PlayerControl} as soon as
 * {@code Obj_Results}' {@code loc_2DD06} clears {@code _unkFAA8}) -&gt;
 * {@code Obj_EndSignControlDoStart}, which waits on {@code End_of_level_flag} and then calls
 * {@code Change_Act2Sizes} (sonic3k.asm:180420-180424, 180580-180596). {@code Change_Act2Sizes}
 * returns early only for Sandopolis ({@code cmpi.b #8,d0}) and Hydrocity
 * ({@code cmpi.b #$10,d0}, the zone shifted left by four), so Lava Reef runs it and gets act 2's
 * stored camera bounds plus {@code Make_LevelSizeObj}'s gradual workers.
 *
 * <p>Without that call the arena's {@code Camera_max_X_pos}, rebased to {@code $128} by
 * {@code loc_56CAA}, survives the change and pins the player against it: measured 2026-09-19 on
 * {@code inputs/lrz1-act-change-walk-v14.txt}, where a right-held player stayed at {@code x 296}
 * for 900 frames with {@code Ctrl_1_locked} clear and a ground speed that built and reset. The
 * native run is free by about 893 frames after its own change
 * ({@code s3k-sonic-tails-complete-emeralds/lrz} rows 25558 -&gt; ~26450).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzActChangeHandoffHeadless {

    /** {@code move.w #$2C00,d0} at {@code loc_56CAA} (sonic3k.asm:115361). */
    private static final int REBASE_X = 0x2C00;
    /** The measured arena right wall, and so the rebased {@code Camera_max_X_pos}. */
    private static final int REBASED_ARENA_MAX_X = 0x128;
    /** Native resumes play about 893 frames after the change; allow a generous margin. */
    private static final int HANDOFF_BUDGET_FRAMES = 1400;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void theActChangeEndsWithAPlayerWhoCanWalkInActTwo() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) 0x2C00, (short) 0x0600)
                .startPositionIsCentre()
                .withSkippedZoneIntro()
                .build();
        AbstractPlayableSprite player = fixture.sprite();

        // Check_CameraInRange's word_784E0 window ($610,$810,$2B00,$2D00) is satisfied where the
        // fixture starts, but loc_85CA4's arena lock only finishes once the player has walked into
        // the arena, exactly as the recorded route does before the fight.
        LrzMinibossInstance drill = null;
        for (int frame = 0; frame < 600 && drill == null; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            for (ObjectInstance object : fixture.runtime().getLevelManager().getObjectManager()
                    .getActiveObjects()) {
                if (object instanceof LrzMinibossInstance candidate
                        && candidate.isArenaGateComplete()) {
                    drill = candidate;
                    break;
                }
            }
        }
        assertTrue(drill != null, "precondition: the drill's arena gate completed");
        // Declared setup, not a route: the recorded run arrives at this arena with 355 rings, and
        // sub_7867C parks the drill on the player standing at the right wall. A ringless Sonic
        // dies there (measured at capture frame 932 of inputs/lrz1-miniboss-fight-v9.txt) and the
        // results screen this case is about would never run.
        player.setRingCount(355);
        // sub_7867C parks the drill where Player 1 stood at v 901, and the right wall leaves it
        // nowhere to put the player: measured, that slam is fatal. Backing off keeps the fight
        // survivable for an ordinary Sonic, which is all this case needs from it.
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepFrame(false, false, true, false, false);
        }

        // sub_78C14's final hit: collision_property reaches zero and loc_78C60 takes the defeat
        // branch on the object's own next dispatch. The offer is repeated because the shared touch
        // code only admits a hit while collision_flags is non-zero, which sub_78C14's own $20
        // invulnerability window closes.
        int changeFrame = -1;
        for (int frame = 0; frame < 2500 && changeFrame < 0; frame++) {
            if (!drill.isDestroyed() && drill.getState().hitCount > 0) {
                drill.getState().hitCount = 1;
                drill.onPlayerAttack(null, null);
            }
            fixture.stepFrame(false, false, false, false, false);
            if (S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                    .actIndex() == 1) {
                changeFrame = frame;
            }
        }
        assertTrue(changeFrame >= 0, "precondition: the defeat reached the seamless act change");
        assertEquals(1, S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().actIndex(), "loc_56CAA wrote $901");

        int xAtChange = player.getCentreX() & 0xFFFF;
        assertTrue(xAtChange < REBASE_X,
                "precondition: the player was rebased into act 2 coordinates (" + xAtChange + ")");

        int bestX = xAtChange;
        for (int frame = 0; frame < HANDOFF_BUDGET_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            bestX = Math.max(bestX, player.getCentreX() & 0xFFFF);
        }

        int maxX = fixture.camera().getMaxX() & 0xFFFF;
        assertTrue(maxX > REBASED_ARENA_MAX_X,
                "Change_Act2Sizes (sonic3k.asm:180580-180596) must replace the rebased arena "
                        + "right wall with act 2's own; Camera_max_X_pos is still " + maxX);
        assertTrue(bestX > xAtChange + 32,
                "a right-held player must walk in act 2 within " + HANDOFF_BUDGET_FRAMES
                        + " frames of the change; reached x " + bestX + " from " + xAtChange);
    }
}
