package com.openggf.sprites.playable;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.PoweredEffects;
import com.openggf.game.StageSpriteSet;
import com.openggf.game.StrikeTrailStyle;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Creator powered-form particles: emission rules, one-shot bursts, budget and lifetime. */
class TestPoweredFormEffects {
    private static final PoweredEffects.Particle DOT = new PoweredEffects.Particle(List.of(
            new StageSpriteSet.Frame(new CharacterPresentation.Image(1, 1, new byte[] {5}), 0, 0)), 2);

    private static PoweredEffects.Emitter emitter(PoweredEffects.Trigger trigger, int count, int interval, int life) {
        return new PoweredEffects.Emitter(trigger, List.of(0), count, interval, PoweredEffects.Spread.SCATTER,
                -6, 0, 4, -0x80, 0, 0x40, 0, life);
    }

    private static AbstractPlayableSprite runner(int groundSpeed) {
        var player = mock(AbstractPlayableSprite.class);
        when(player.getGSpeed()).thenReturn((short) groundSpeed);
        when(player.getDirection()).thenReturn(Direction.RIGHT);
        when(player.getCentreX()).thenReturn((short) 100);
        when(player.getCentreY()).thenReturn((short) 100);
        return player;
    }

    @Test
    void fastEmitterShedsOnlyWhilePoweredAndFastThenParticlesExpire() {
        var effects = new PoweredEffects(null, List.of(DOT),
                List.of(emitter(PoweredEffects.Trigger.FAST, 1, 2, 10)));
        var particles = new PoweredFormEffects();
        var slow = runner(0x300);
        for (int i = 0; i < 10; i++) particles.tick(slow, effects, true);
        assertEquals(0, particles.liveCount(), "below 6 px/frame nothing is shed");
        var fast = runner(0x700);
        for (int i = 0; i < 10; i++) particles.tick(fast, effects, true);
        assertEquals(5, particles.liveCount(), "one particle every second tick");
        for (int i = 0; i < 10; i++) particles.tick(fast, effects, false);
        assertEquals(0, particles.liveCount(), "a reverted form sheds nothing and old particles expire");
    }

    @Test
    void oneShotTriggersBurstAndRespectTheLiveBudget() {
        var effects = new PoweredEffects(null, List.of(DOT),
                List.of(emitter(PoweredEffects.Trigger.TRANSFORM, 32, 1, 60),
                        emitter(PoweredEffects.Trigger.STRIKE_HIT, 12, 1, 60)));
        var particles = new PoweredFormEffects();
        var player = runner(0);
        particles.fire(player, effects, PoweredEffects.Trigger.TRANSFORM);
        assertEquals(32, particles.liveCount());
        particles.fire(player, effects, PoweredEffects.Trigger.TRANSFORM);
        assertEquals(PoweredEffects.MAX_LIVE_PARTICLES, particles.liveCount());
        particles.tick(player, effects, true);
        assertEquals(PoweredEffects.MAX_LIVE_PARTICLES, particles.liveCount(), "continuous rules never fire one-shots");
        particles.clear();
        assertEquals(0, particles.liveCount(), "a rewind restore discards presentation particles");
        particles.fire(player, null, PoweredEffects.Trigger.TRANSFORM);
        assertEquals(0, particles.liveCount());
    }

    @Test
    void creatorRecordsRejectInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new PoweredEffects(null, List.of(DOT),
                List.of(new PoweredEffects.Emitter(PoweredEffects.Trigger.FAST, List.of(1), 1, 1,
                        PoweredEffects.Spread.RING, 0, 0, 0, 0, 0, 0, 0, 10))));
        assertThrows(IllegalArgumentException.class, () -> new PoweredEffects.Particle(List.of(
                new StageSpriteSet.Frame(new CharacterPresentation.Image(40, 1, new byte[40]), 0, 0)), 1));
        var front = List.of(new StrikeTrailStyle.FrontLayer(0xFFFFFF, 14, 3, 255, 4));
        assertThrows(IllegalArgumentException.class, () -> new StrikeTrailStyle(List.of(0xFFFFFF), front, -1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new StrikeTrailStyle(List.of(1, 2, 3), front, 0x1000000, 100));
        assertDoesNotThrow(() -> new StrikeTrailStyle(List.of(1, 2, 3), front, -1, 100));
        assertEquals(-1, new StrikeTrailStyle(List.of(1, 2, 3), front, -1, 100).echoPhaseSlot());
        assertThrows(IllegalArgumentException.class, () -> new StrikeTrailStyle(List.of(1, 2, 3), front, -1, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new StrikeTrailStyle(List.of(1, 2, 3), front, -1, 100, 16));
        assertEquals(4, new StrikeTrailStyle(List.of(1, 2, 3), front, -1, 100, 4).echoPhaseSlot());
        assertTrue(new PoweredEffects(null, List.of(), List.of()).strikePalette().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new PoweredEffects(null, List.of(), List.of(), List.of(0x000, 0xEEE)));
        var row = new java.util.ArrayList<>(java.util.Collections.nCopies(16, 0x0CE));
        assertEquals(16, new PoweredEffects(null, List.of(), List.of(), row).strikePalette().size());
        row.set(3, 0x0CF);
        assertThrows(IllegalArgumentException.class, () -> new PoweredEffects(null, List.of(), List.of(), row),
                "odd Mega Drive colour nibbles are rejected");
    }
}
