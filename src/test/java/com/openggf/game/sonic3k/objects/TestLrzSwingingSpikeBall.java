package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.ArrayList;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Obj_LRZSwingingSpikeBall} (sonic3k.asm:88698-88805, ROM {@code $43500}).
 *
 * <p>The geometry expectations below were computed from the ROM's own {@code SineTable}
 * ({@code Levels/Misc/sine.bin}) through {@code sub_43604}'s arithmetic, not read back from the
 * class: one link step is {@code sin >> 4} vertically and {@code cos >> 4} horizontally, the
 * {@code subtype & $F} links sit at one step each from the anchor outwards, and the ball sits one
 * step past the last link.
 */
class TestLrzSwingingSpikeBall {

    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "1,0", "1,1"})
    void chainAndBallRenderOnTheSameCircle(int act, int flags) {
        var chain = mock(PatternSpriteRenderer.class);
        var ballRenderer = mock(PatternSpriteRenderer.class);
        var manager = mock(ObjectRenderManager.class);
        when(chain.isReady()).thenReturn(true);
        when(ballRenderer.isReady()).thenReturn(true);
        when(manager.getRenderer(act == 0 ? Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL_CHAIN
                : Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL_CHAIN)).thenReturn(chain);
        when(manager.getRenderer(act == 0 ? Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL
                : Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL)).thenReturn(ballRenderer);
        var services = new StubObjectServices() {
            @Override public int currentAct() { return act; }
            @Override public ObjectRenderManager renderManager() { return manager; }
        };
        var object = ObjectConstructionContext.construct(services,
                () -> new LrzSwingingSpikeBallObjectInstance(
                        new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 3, flags, false, 0)));
        object.setServices(services);
        // Cardinal positions from sub_43604: 16px per link, ball one step beyond.
        int[][] directions = {{1,0}, {0,1}, {-1,0}, {0,-1}};
        for (int frame = 0; frame < 128; frame++) {
            object.update(frame, null);
            if (frame % 32 != 0) continue;
            int dx = directions[frame / 32][0];
            int dy = directions[frame / 32][1] * (flags == 0 ? 1 : -1);
            object.appendRenderCommands(new ArrayList<>());
            for (int link = 1; link <= 3; link++) {
                verify(chain).drawFrameIndex(1, BASE_X + dx * link * 16,
                        BASE_Y + dy * link * 16, false, false);
            }
            verify(ballRenderer).drawFrameIndex(0, BASE_X + dx * 64,
                    BASE_Y + dy * 64, false, false);
            clearInvocations(chain, ballRenderer);
        }
    }

    private static final int OBJECT_ID = Sonic3kObjectIds.MGZLBZ_SMASHING_PILLAR_ALT;
    private static final int BASE_X = 0x0800;
    private static final int BASE_Y = 0x0600;

    /** {@code andi.w #$F,d0 / move.w d0,mainspr_childsprites(a1)} (sonic3k.asm:88727-88729). */
    @Test
    void theLinkCountIsTheSubtypesLowNibble() {
        for (int subtype : new int[] {2, 3, 4}) {
            assertEquals(subtype, ball(subtype, false).linkCount(),
                    "the three subtypes Lava Reef places");
        }
    }

    /** {@code moveq #2,d0 / btst #0,status(a0) / neg.w d0} (sonic3k.asm:88745-88749). */
    @Test
    void theFlipFlagReversesTheSwing() {
        assertEquals(2, ball(3, false).angleStep());
        assertEquals(-2, ball(3, true).angleStep());
    }

    /**
     * At angle 0 the ROM's sine is 0 and its cosine {@code $100}, so a step is {@code $100 >> 4 =
     * 16} pixels horizontally and nothing vertically: the chain lies flat to the right and the ball
     * is {@code (links + 1) * 16} pixels away.
     */
    @Test
    void atAngleZeroTheChainLiesFlatAndTheBallIsOneStepPastTheLastLink() {
        LrzSwingingSpikeBallObjectInstance ball = ball(3, false);
        ball.update(1, null);

        assertEquals(BASE_X + 16, ball.linkX(0), "the first link is one step out");
        assertEquals(BASE_X + 32, ball.linkX(1));
        assertEquals(BASE_X + 48, ball.linkX(2));
        assertEquals(BASE_Y, ball.linkY(0), "sin(0) is zero, so nothing moves vertically");
        assertEquals(BASE_X + 64, ball.getCentreX(), "the ball is one step past link three");
        assertEquals(BASE_Y, ball.getCentreY());
    }

    /**
     * A quarter turn on: {@code sin($40)} is {@code $100} and {@code cos($40)} is 0, so the chain
     * hangs straight down. The angle advances by two a frame after the chain has been placed, so
     * the {@code $21}st update is the one that places it at {@code $40}.
     */
    @Test
    void aQuarterTurnOnTheChainHangsStraightDown() {
        LrzSwingingSpikeBallObjectInstance ball = ball(3, false);
        for (int frame = 1; frame <= 0x21; frame++) {
            ball.update(frame, null);
        }
        // loc_435E0 places the chain from the CURRENT angle and only then advances it, so the
        // update that placed the chain at $40 leaves the angle at $42.
        assertEquals(0x42, ball.angle() & 0xFF, "placed at $40, then stepped past it");
        assertEquals(BASE_X, ball.linkX(0), "cos($40) is zero");
        assertEquals(BASE_Y + 16, ball.linkY(0));
        assertEquals(BASE_Y + 64, ball.getCentreY(), "the ball hangs four steps down");
        assertEquals(BASE_X, ball.getCentreX());
    }

    /** {@code tst.b subtype(a0) / bpl} (sonic3k.asm:88773-88776): bit 7 starts one step further. */
    @Test
    void subtypeBitSevenStartsTheChainOneStepFurtherOut() {
        LrzSwingingSpikeBallObjectInstance plain = ball(3, false);
        LrzSwingingSpikeBallObjectInstance offset = ball(0x83, false);
        plain.update(1, null);
        offset.update(1, null);
        assertEquals(plain.linkX(0) + 16, offset.linkX(0), "every link moves out one step");
        assertEquals(plain.getCentreX() + 16, offset.getCentreX(), "and so does the ball");
    }

    /** {@code move.b #$9A,collision_flags(a0)} (sonic3k.asm:88707). */
    @Test
    void theBallIsHarmful() {
        assertEquals(0x9A, ball(3, false).getCollisionFlags());
    }

    private static LrzSwingingSpikeBallObjectInstance ball(int subtype, boolean flipped) {
        return new LrzSwingingSpikeBallObjectInstance(new ObjectSpawn(
                BASE_X, BASE_Y, OBJECT_ID, subtype, flipped ? 1 : 0, false, 0));
    }
}
