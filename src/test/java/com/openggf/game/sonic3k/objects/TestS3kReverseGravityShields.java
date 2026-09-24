package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.ShieldType;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All four shields are drawn upside down while {@code Reverse_gravity_flag} is set.
 *
 * <p>{@code Obj_InstaShield_Main} (sonic3k.asm:34590-34597), {@code Obj_FireShield_Main}
 * (:34662-34669), {@code Obj_LightningShield_Main} (:34743-34750) and
 * {@code Obj_BubbleShield_Main} (:34907-34914) share one shape:
 *
 * <pre>
 *   move.b  status(a2),status(a0)   ; inherit the player's status
 *   andi.b  #1,status(a0)           ; keep ONLY the orientation bit
 *   tst.b   (Reverse_gravity_flag).w
 *   beq.s   .normalgravity
 *   ori.b   #2,status(a0)
 * </pre>
 *
 * <p>The {@code andi.b #1} has already cleared bit 1 when the {@code ori.b #2} runs, so
 * despite the ROM's own comment ("on if off beforehand and vice versa") this is a
 * <em>set</em>, not a toggle: the shield's Y-flip simply <strong>equals the flag</strong>
 * every frame. That is the same net effect {@code AbstractPlayableSprite.renderVFlipForDraw}
 * writes for the player, and porting the comment instead of the code would alternate the
 * sprite every frame.
 *
 * <p>Asserted through {@code shieldRenderVFlip()} — the expression each shield's draw hands
 * to {@code drawFrame}'s {@code vFlip} argument — so this measures the drawn value rather
 * than restating the helper.
 */
class TestS3kReverseGravityShields {

    @Test
    void everyShieldsDrawnFlipFollowsTheFlag() {
        GameStateManager gameState = new GameStateManager();
        ObjectServices services = new FlagCarryingServices(gameState);

        // InstaShieldObjectInstance loads its art from its constructor, so it needs the
        // services in scope while it is built; the other three are given them afterwards.
        InstaShieldObjectInstance insta = ObjectConstructionContext.construct(
                services, () -> new InstaShieldObjectInstance(testPlayer()));
        FireShieldObjectInstance fire = new FireShieldObjectInstance(testPlayer());
        LightningShieldObjectInstance lightning = new LightningShieldObjectInstance(testPlayer());
        BubbleShieldObjectInstance bubble = new BubbleShieldObjectInstance(testPlayer());
        insta.setServices(services);
        fire.setServices(services);
        lightning.setServices(services);
        bubble.setServices(services);

        gameState.setReverseGravityActive(false);
        assertFalse(insta.shieldRenderVFlip(), "no flag, no mirror (Obj_InstaShield_Main)");
        assertFalse(fire.shieldRenderVFlip(), "no flag, no mirror (Obj_FireShield_Main)");
        assertFalse(lightning.shieldRenderVFlip(), "no flag, no mirror (Obj_LightningShield_Main)");
        assertFalse(bubble.shieldRenderVFlip(), "no flag, no mirror (Obj_BubbleShield_Main)");

        gameState.setReverseGravityActive(true);
        assertTrue(insta.shieldRenderVFlip(), "ori.b #2,status(a0) at sonic3k.asm:34596");
        assertTrue(fire.shieldRenderVFlip(), "ori.b #2,status(a0) at sonic3k.asm:34668");
        assertTrue(lightning.shieldRenderVFlip(), "ori.b #2,status(a0) at sonic3k.asm:34749");
        assertTrue(bubble.shieldRenderVFlip(), "ori.b #2,status(a0) at sonic3k.asm:34913");

        // A set, not a toggle: the ROM's andi.b #1 clears bit 1 first, so a second frame
        // under the same flag draws the same way rather than flipping back.
        assertTrue(fire.shieldRenderVFlip(), "the mirror is the flag, not an XOR of last frame");

        gameState.setReverseGravityActive(false);
        assertFalse(bubble.shieldRenderVFlip(), "and the mirror goes away with the flag");
    }

    private static TestablePlayableSprite testPlayer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setShieldStateForTest(true, ShieldType.BASIC);
        return player;
    }

    /** {@link StubObjectServices} returns a null {@code gameState()}; these shields need one. */
    private static final class FlagCarryingServices extends StubObjectServices {
        private final GameStateManager gameState;

        private FlagCarryingServices(GameStateManager gameState) {
            this.gameState = gameState;
        }

        @Override
        public GameStateManager gameState() {
            return gameState;
        }
    }
}
