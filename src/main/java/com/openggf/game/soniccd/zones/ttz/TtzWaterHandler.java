package com.openggf.game.soniccd.zones.ttz;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.WaterSystem;
import com.openggf.physics.TrigLookupTable;

/**
 * Tidal Tempest's water height, one step per frame: {@code UpdateWater}, {@code WaterEvents} and the
 * act routines (docs/scddisasm pin 1ecc5f3a, {@code src/r4/main.asm:566-600} and {@code :612-774}; all
 * ten stage programs assemble the same {@code r4/main.asm}).
 *
 * <p>The native routine runs in V-blank before the frame's logic. In this order, per call:
 * <ol>
 *   <li>if the player's routine is below 6 ({@code cmpi.b #6,player_object+obj.routine}, :568-570) the
 *       act routine picks a target or assigns the base level, then the base level moves one
 *       {@code water_speed} toward the target (:612-633). The move itself is the shared water system's
 *       {@code DynamicWaterState.update}; while the player is dead this handler sets the state's speed
 *       to 0 so the base level holds, as the skipped {@code WaterEvents} does natively;</li>
 *   <li>the bob: {@code water_angle += 2}, {@code d0 = SineCosine(water_angle)}, {@code lsr.w #6},
 *       {@code ext.w}, i.e. a signed byte of the sine's low word shifted right by 6, added to the base
 *       level (:573-583). The shared system adds {@link #bob()} through the water provider.</li>
 * </ol>
 *
 * <p>Act routines, per the {@code act} and {@code time_zone} bytes (:635-774); {@code x} is the player's
 * centre X and {@code fgY} the foreground camera Y. Act 1 (:642-678): Past does nothing; Present targets
 * {@code $280}, or {@code $260} from X {@code $5E0}; Future targets {@code $1D0}, {@code $110} from X
 * {@code $920}, and {@code $240} with speed 3 from X {@code $1980}. Act 2 (:680-749) and Act 3 (:751-774)
 * follow the camera ({@code fgY + $E8}) outside their pump rooms and use the tables in the code below.
 * Shipped {@code FixBugs = 0}: {@code water_speed} is never lowered after it is raised, a step larger than
 * the remaining distance overshoots and the level then jitters around its target, exactly as the
 * unsigned compare and full-step add do natively.
 *
 * <p>Natively {@code UpdateWater} also runs in the lag V-blank ({@code VBlankLag}, :994-1022, the call at :1010), so
 * {@code water_angle} advances by two per V-blank, not per level frame: the engine frame runs this once, and a lag
 * V-blank runs it once more through {@code TtzZone.lagVBlankUpdatesWater} and the module's level-event lag hook.
 */
final class TtzWaterHandler implements DynamicWaterHandler {
    private final int act;                  // the native act byte, 0 for act 1
    private final int timeZone;             // 0 Past, 1 Present, 2 Future
    private final TtzWaterDataProvider.PlayerView player;
    private final TtzWaterDataProvider provider;
    private int speed = 1;                  // move.b #1,water_speed (r4/main.asm:209)
    private int angle;                      // water_angle, a byte

    TtzWaterHandler(CdStagePayload payload, TtzWaterDataProvider provider, TtzWaterDataProvider.PlayerView player) {
        this.act = payload.stage().actNumber() - 1;
        this.timeZone = timeZone(payload.stage().period());
        this.provider = provider;
        this.player = player;
    }

    /** STAGE_TIME: 0 Past, 1 Present, 2 Future (Level/Tidal Tempest/R41A Legacy.asm:6-9). */
    static int timeZone(CdPeriod period) {
        return switch (period) {
            case PAST -> 0;
            case PRESENT -> 1;
            case GOOD_FUTURE, BAD_FUTURE -> 2;
        };
    }

    @Override
    public void update(WaterSystem.DynamicWaterState state, int cameraX, int cameraY) {
        if (player.alive()) {
            events(state, cameraY & 0xFFFF);
            state.setSpeed(speed);
        } else {
            state.setSpeed(0);                                  // WaterEvents is skipped (main.asm:568-570)
        }
        angle = (angle + 2) & 0xFF;                             // addq.b #2,water_angle (:576)
        int sine = TrigLookupTable.sinHex(angle) & 0xFFFF;      // jsr SineCosine (math.asm:3-10)
        provider.setBob((byte) (sine >>> 6));                   // lsr.w #6,d0 ; ext.w d0 (:577-581)
    }

    /** The act routine (WaterEventsByAct, :635-640) and the target assignments it makes. */
    private void events(WaterSystem.DynamicWaterState state, int fgY) {
        int x = player.centreX();
        int y = player.centreY();
        switch (act) {
            case 0 -> act1(state, x);
            case 1 -> act2(state, x, y, fgY);
            default -> act3(state, x, fgY);
        }
    }

    /** WaterEventsAct1 (:642-678). */
    private void act1(WaterSystem.DynamicWaterState state, int x) {
        if (timeZone == 0) return;                              // :646-651 tst.b d0 ; bne.s ; rts
        int target;
        if (timeZone == 1) {                                    // WaterEventsAct1Present, :653-662
            target = x < 0x5E0 ? 0x280 : 0x260;
        } else {                                                // WaterEventsAct1Future, :664-678
            target = 0x1D0;
            if (x >= 0x920) {
                target = 0x110;
                if (x >= 0x1980) {
                    target = 0x240;
                    speed = 3;                                  // move.b #3,water_speed
                }
            }
        }
        state.setTarget(target);                                // WaterEventsAct1SetTarget, :670-678
    }

    /** WaterEventsAct2 (:680-749). */
    private void act2(WaterSystem.DynamicWaterState state, int x, int y, int fgY) {
        int d1 = (fgY + 0xE8) & 0xFFFF;                         // addi.w #$E8,d1
        boolean pumpRoom = x >= 0x200 && y >= 0x600 && y < 0x6C0 && x < 0x2A0;     // :687-698
        if (x < 0x200 || pumpRoom) {                            // WaterEventsAct2SetStatic
            state.setMeanDirect(d1);                            // move.w d1,static_water_y ; then target
            return;
        }
        if (timeZone == 0) {                                    // :699-708 past
            state.setMeanDirect(0x5B0);
            return;
        }
        if (timeZone == 1) {                                    // WaterEventsAct2Present, :710-732
            int target = 0x530;
            if (x >= 0xC00) {
                target = 0x780;
                if (x >= 0xDC0) {
                    target = 0x500;
                    if (x < 0x1380) {
                        if (y >= 0x400) return;                 // WaterEventsAct2PresentReturn: rts
                        target = 0x3C0;
                        if (x < 0x1100) return;                 // falls to the rts
                    }
                }
            }
            state.setTarget(target);
            return;
        }
        int target = 0x510;                                     // WaterEventsAct2Future, :733-744
        if (x >= 0x400) {
            target = 0x390;
            if (x >= 0x1600) {
                target = 0x410;
                if (x >= 0x1900) target = 0x500;
            }
        }
        state.setTarget(target);
    }

    /** WaterEventsAct3 (:751-774). */
    private void act3(WaterSystem.DynamicWaterState state, int x, int fgY) {
        if (x < 0x7C0) {
            state.setMeanDirect((fgY + 0xE8) & 0xFFFF);         // static_water_y = fgY + $E8 ; target = same
            return;
        }
        int target = 0x490;
        if (x >= 0xAF0) {
            speed = 2;                                          // move.b #2,water_speed
            target = 0x5C0;
        }
        state.setTarget(target);
    }
}
