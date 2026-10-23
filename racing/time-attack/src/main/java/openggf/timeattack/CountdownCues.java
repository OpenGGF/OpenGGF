package openggf.timeattack;

import java.util.function.IntConsumer;

/**
 * Sounds for the 3-2-1-GO countdowns (a round's start and a fast restart), following the digit
 * on screen: one cue as each of 3, 2 and 1 appears, another when the run is released.
 *
 * <p>Sonic 3 &amp; Knuckles borrows Death Egg Zone's tunnel launcher, whose own countdown is the
 * same shape: {@code sfx_LaunchReady} when the player is grabbed and on each of the next two
 * 60-frame ticks, then {@code sfx_LaunchGo} on the third as it fires the player into the light
 * rings ({@code Obj_DEZTunnelLauncher_Main}, sonic3k.asm:94270-94291, and {@code sub_48370},
 * sonic3k.asm:94386-94393). The other games have no countdown sound of their own and stay silent.</p>
 */
final class CountdownCues {
    /** {@code sfx_LaunchReady} (sonic3k.constants.asm:1637). */
    static final int S3K_LAUNCH_READY = 0xA7;
    /** {@code sfx_LaunchGo} (sonic3k.constants.asm:1643). */
    static final int S3K_LAUNCH_GO = 0xAD;

    private IntConsumer player = id -> { };
    private int shownDigit;

    void setPlayer(IntConsumer player) {
        this.player = player == null ? id -> { } : player;
    }

    /** Forgets the digit on screen, so a reloaded level's countdown starts afresh. */
    void reset() {
        shownDigit = 0;
    }

    /**
     * Follows the digit now on screen (0 for none) for a run of {@code gameId}: a new 3, 2 or 1
     * plays the ready cue, and the first released step after the digits plays the go cue. A
     * countdown that ends without releasing the run (the player left) never says go.
     */
    void observe(String gameId, int digit, boolean released) {
        if (digit == 0 && shownDigit > 0 && !released) {
            return;
        }
        if (digit == shownDigit) {
            return;
        }
        int previous = shownDigit;
        shownDigit = digit;
        if (!"s3k".equals(gameId)) {
            return;
        }
        if (digit >= 1 && digit <= 3) {
            player.accept(S3K_LAUNCH_READY);
        } else if (digit == 0 && previous > 0) {
            player.accept(S3K_LAUNCH_GO);
        }
    }
}
