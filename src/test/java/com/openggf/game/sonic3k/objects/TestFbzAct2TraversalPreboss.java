package com.openggf.game.sonic3k.objects;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BlasterProjectileObjectInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.InputRun;
import com.openggf.tests.route.ObjectLifetimeFrames;
import com.openggf.tests.route.RecentFrameLog;
import com.openggf.tests.route.RouteSteering;
import com.openggf.tests.route.SidekickAudit;

import static org.junit.jupiter.api.Assertions.*;

/** Placement-backed, deterministic route contract through FBZ2's preboss handoff. */
@RequiresRom(SonicGame.SONIC_3K)
public class TestFbzAct2TraversalPreboss {
    /**
     * Ordinary P1 controller program from the native FBZ2 start. Each entry is
     * {@code frame-count:input-mask}; masks use the playable sprite's
     * up/down/left/right/jump bits. Bounded adaptive controllers use only those
     * same normal inputs, exact placed-object identity, and live collision
     * geometry at authored traversal gates; object/physics drift outside those
     * gates still fails the route.
     */
    private static final String PREBOSS_INPUT_PROGRAM =
            "43:8,433:0,103:8,7:0,8:2,4:12,4:10,20:0,9:2,6:12,2:2,124:0,18:4,45:0,19:4,1:8,33:18,11:8,239:0,52:8,8:18,133:8,14:0,66:2,6:12,49:2,2:a,53:8,10:0,4:10,9:14,5:4,73:0,14:4,7:14,6:4,6:0,24:8,4:0,16:8,8:18,25:8,205:8,14:4,7:0,6:4,30:0,9:8,18:18,28:0,8:4,4:0,3:8,11:0,8:2,25:12,5:2,42:0,11:4,11:14,32:4,29:8,4:0,21:4,6:0,5:2,6:12,1:2,29:0,166:4,9:14,18:4,14:8,51:0,12:8,19:0,11:8,11:0,15:4,262:0,42:8,13:0,29:8,13:18,6:0,19:4,5:0,232:8,8:4,1:8,210:0,41:4,4:8,1:4,10:0,237:4,20:14,224:4,5:8,1:4,80:0,130:4,318:0,92:8,12:4,21:0,4:8,27:0,4:4,45:0,1:8,119:0,299:8,31:4,3:8,1:4,235:0,40:8,20:18,273:8,45:4,26:0,1373:8,31:18,12:8,13:4,2:8,1:4,45:0,39:4,740:8,25:4,105:0,80:8,60:a,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,9:8,16:4,2:8,1:4,110:0,1:8,20:18,63:8,1:18,12:8,31:4,16:8,3:4,1:8,1:4,359:0,33:4,42:8,20:18,24:8,4:4,1:8,9:18,49:8,1:18,81:8,50:4,4:8,1:4,215:0,6:8,20:18,29:14,20:4,20:8,30:18,10:8,8:2,4:12,4:2,4:12,4:2,19:8,42:0,45:8,10:4,208:0,16:8,40:18,1600:0,1:18,1:8,15:18,37:8,12:18,49:8,66:0,1:14,38:4,45:8,4:4,1:8,1:4,206:0,117:4,5:8,1:4,20:0,30:18,583:0,343:4,5:8,1:4,218:0,190:0,8:4,17:18,14:14,20:4,22:14,196:4,6:8,1:4,1:8,1:4,234:0,31:18,24:8,9:4,2:8,1:4,192:0,35:8,1:18,42:8,4:18,12:8,17:4,98:8,20:18,100:8";
    // The source BK2 program is cadence evidence only up to the landing on the
    // $0A80 lower floor (run 80 ends its 262-frame neutral settle at frame
    // 2889 with 23 rings). Its remaining 12,121 frames, the former chain/lower-
    // door program and nine lower-backtrack cycles never changed a milestone,
    // ring or position band; they only spent the act's 36,000-frame time-over
    // budget. The live midpoint controller owns everything after the landing.
    private static final int NATIVE_START_RUN_COUNT = 81;
    private static final List<InputRun> NATIVE_START_TO_LOWER_FLOOR =
            parseInputProgram().subList(0, NATIVE_START_RUN_COUNT);
    // The BK2 prefix is replayed only until P1 lands on the $01EC ledge after
    // the $078C trap spring (rows 23527-23540). From there the ledge controller
    // owns the route to the $076C loop floor: the BK2 grabs the $0818 Obj72
    // chain (sub $1B, range $D8), is knocked off it by a TechnoSqueek at row
    // 23622, grabs the $0868 chain (sub $16, range $B0) at row 23926, releases
    // LEFT at its full extension (row 24022, y=$0264), breaks the $0810/$02F0
    // monitor with a jump from $0829 (row 24085), stands on the $0810 Obj78
    // launcher (row 24104) and lands from its $1000 throw on the loop floor at
    // row 24272. A wider viewport activates the mice in another phase, so the
    // $0818 chain would lower P1 into the $0810/$0290 spikes; the controller
    // goes straight to the $0868 chain instead.
    private static final int LEDGE_FLOOR_Y = 0x01EC;
    private static final int LEDGE_HANDOFF_MIN_X = 0x0790;
    private static final int LEDGE_HANDOFF_MAX_X = 0x07D0;
    private static final int LEDGE_CHAIN_X = 0x0868;
    private static final int LEDGE_CHAIN_JUMP_X = 0x085C;
    private static final int LEDGE_CHAIN_STILL_FRAMES = 4;
    private static final int LEDGE_CHAIN_GRAB_ATTEMPT_LIMIT = 4;
    // Obj_TechnoSqueek patrols the ledge at y=$01F8; a released short hop
    // (Sonic_JumpHeight caps y_vel at -$400) clears it without rising into a
    // chain's $90-$A8 grab band.
    private static final int LEDGE_SQUEEK_HOP_RANGE = 0x30;
    private static final int LEDGE_SQUEEK_HOP_HOLD = 4;
    private static final int LEDGE_LOWER_FLOOR_Y = 0x02EC;
    private static final int LEDGE_SPIKE_GAP_X = 0x0840;
    private static final int LEDGE_MONITOR_X = 0x0810;
    private static final int LEDGE_MONITOR_Y = 0x02F0;
    private static final int LEDGE_MONITOR_JUMP_X = 0x0829;
    private static final int LEDGE_MONITOR_JUMP_HOLD = 0x10;
    private static final int LEDGE_MONITOR_ATTEMPT_LIMIT = 4;
    private static final int LEDGE_LAUNCH_SPEED = 0x0800;
    private static final int LEDGE_LOOP_FLOOR_MIN_Y = 0x0760;
    private static final int LEDGE_LOOP_FLOOR_MAX_Y = 0x0790;
    private static final int LEDGE_LOOP_FLOOR_MIN_X = 0x09F0;
    private static final int LEDGE_LOOP_FLOOR_MAX_X = 0x0AD0;
    // acquireMidpointCar's upper-loop brake range is $0A40-$0A98 on the
    // $0740-$0780 floor band. Settle just right of its $0A40-$0A70 approach
    // facing LEFT: the native spindash releases toward the facing, and the
    // S1 donation assist (FbzS1DonationUpperLoopAssist) consumes itself on
    // any leftward LEFT-pressed ground motion inside the approach, so no
    // LEFT may be pressed there before the controller hands over.
    private static final int LEDGE_LOOP_STAND_X = 0x0A78;
    private static final int LEDGE_ROUTE_FRAME_LIMIT = 0x800;
    private static final int LEDGE_STAGE_LIMIT = 0x300;
    private static final int SPIKE_CORRIDOR_START_RUN = 27;
    private static final int SPIKE_CORRIDOR_END_RUN = 39;
    private static final int SPIKE_CORRIDOR_WAYPOINT_FRAMES = 122;
    private static final int LATE_BUTTON_DOOR_EXTENSION_FRAMES = 0x100;
    private static final int FLAME_TOUCH_RADIUS = 0x04;
    private static final int BLASTER_PROJECTILE_TOUCH_RADIUS = 0x04;
    private static final int MAGNETIC_BALL_TOUCH_RADIUS = 0x0C;
    private static final int MAGNETIC_PLATFORM_TOUCH_RADIUS_X = 0x14;
    private static final int MAGNETIC_PLATFORM_TOUCH_RADIUS_Y = 0x08;
    private static final int PLAYER_TOUCH_HALF_WIDTH = 0x08;
    private static final int MAGNETIC_PLATFORM_WAIT_LIMIT = 0x200;
    /**
     * Tails_CPU_Control loc_13DA6 (sonic3k.asm) steers a CPU sidekick at the
     * leader's Pos_table entry $44 bytes back: 17 four-byte frames. Each link
     * of a sidekick chain follows its own leader with the same delay.
     */
    private static final int SIDEKICK_FOLLOW_LAG_FRAMES = 0x11;
    private static final int SIDEKICK_FOLLOW_MAX_TRAIL = 0x400;
    private static final int SPIKE_BALL_CORRIDOR_SAFE_Y = 0x04C0;
    private static final int SPIKE_BALL_TOUCH_HALF_WIDTH = 0x18;
    private static final int SPIKE_BALL_HOLD_GAP = 0x48;
    private static final int SQUEEZE_OTHER_CAR_SLOPE_MARGIN = 0x04;
    // S3K host movement rules: DOWN below $100 crouches, at or above it rolls
    // (sonic3k.asm:23223), and the roll test reads ground_vel after the DOWN
    // frame's $C friction, so the S1 profile's ordinary run-up must reach
    // $10C before the Obj28 launch frontier; start it $18 behind the staging
    // spot.
    private static final int SQUEEZE_S1_ROLL_START_SPEED = 0x0110;
    private static final int SQUEEZE_S1_RUN_UP = 0x18;
    private static final int SQUEEZE_S1_WAIT_LIMIT = 0x400;
    private static final int TECHNO_SQUEEK_JUMP_LOOKAHEAD = 0x50;
    private static final int TECHNO_SQUEEK_JUMP_VERTICAL_RANGE = 0x18;
    private static final int TECHNO_SQUEEK_JUMP_MIN_SPEED = 0x80;
    private static final int TECHNO_SQUEEK_JUMP_ATTEMPT_LIMIT = 4;
    private static final int SIDEKICK_GATHER_RANGE = 0x40;
    private static final int SIDEKICK_GATHER_FRAME_LIMIT = 0x400;
    private static final int SHAFT_REGROUP_HOLD_X = 0x0948;
    private static final int SHAFT_REGROUP_CLEAR_X = 0x0905;
    private static final int SHAFT_REGROUP_MIN_Y = 0x0840;
    private static final int SHAFT_REGROUP_FRAME_LIMIT = 0x200;
    private static final int SHAFT_FLOOR_EAST_HANDOFF_X = 0x0B00;
    private static final int SHAFT_FLOOR_EAST_SPEED_CAP = 0x0400;
    // Obj74 ride: bounded by six AnPal_FBZ half-cycles ($100 frames each).
    private static final int MAGNETIC_PLATFORM_RIDE_LIMIT = 0x600;
    // A jump lasts about $3C frames; a hop between risen platforms adds a
    // short grounded run-up. Never launch inside a shorter polarity runway,
    // or both platforms fall while P1 is airborne over the ground hazard.
    private static final int MAGNETIC_PLATFORM_RIDE_MIN_RUNWAY = 0x60;
    // Adjacent FBZ2 Obj74 columns sit $C0 apart; hop only to the next one.
    private static final int MAGNETIC_PLATFORM_RIDE_REACH = 0xE0;
    private static final int MAGNETIC_PLATFORM_RIDE_LAUNCH_OFFSET = 0x08;
    private static final int MAGNETIC_PLATFORM_RIDE_LANDING_OFFSET = 0x04;
    private static final int MAGNETIC_PLATFORM_RIDE_ATTEMPT_LIMIT = 3;
    private static final int SQUEEZE_CORRIDOR_RECOVERY_LIMIT = 0x200;
    private static final int BLASTER_TOUCH_RADIUS_Y = 0x08;
    private static final int LATE_HAZARD_JUMP_LOOKAHEAD = 0x70;
    private static final int LATE_PROJECTILE_LOOKAHEAD = 0xA0;
    // Post-door corridor ($1718 door to the $1A40 descent): during ACTIVE
    // polarity the render-flag-2 Blasters ride the magnet to the ceiling and
    // the Obj73 balls rise off the floor, so the floor is clear. Leave the
    // late button between $27 and $6F frames into an ACTIVE half-cycle so the
    // spindash traversal fits before the next INACTIVE edge.
    private static final int POST_DOOR_CORRIDOR_MIN_RUNWAY = 0x90;
    // loc_3B18C lifts an Obj73 ball at $18/frame^2 (about $28 frames to the
    // ceiling) and the Blaster magnet rise adds $20/frame^2; do not leave
    // before both have cleared the floor band.
    private static final int POST_DOOR_CORRIDOR_MAX_RUNWAY = 0xD8;
    private static final int POST_DOOR_CORRIDOR_RUN_MIN_RUNWAY = 0xC0;
    private static final int POST_DOOR_CORRIDOR_RUN_JUMP_HOLD = 6;
    private static final int POST_DOOR_CORRIDOR_HOLD_LIMIT = 0x200;
    private static final int POST_DOOR_CORRIDOR_CHARGE_TAPS = 3;
    // The non-magnetic $1790 Blaster patrols $1770-$17B0 and fires $10 frames
    // after P1 enters $80. A spindash roll crosses that window and destroys
    // it on contact while its shot arcs above the rolling radius.
    private static final int POST_DOOR_CORRIDOR_HANDOFF_X = 0x1A40;
    private static final int TRIGGER7_EGRESS_MIN_RUNWAY = 0xC0;
    // Generic placed button/screw-door pair: Obj_Button publishes trigger bit
    // (subtype & $F); Obj_FBZScrewDoor with the same low nibble latches on it.
    private static final int LINKED_DOOR_LOOKAHEAD = 0x60;
    private static final int LINKED_DOOR_BUTTON_REACH = 0xA0;
    private static final int LINKED_DOOR_FRAME_LIMIT = 0x300;
    private static final int SQUEEZE_PROACTIVE_LOOKAHEAD = 0x100;
    // The $1D38 button top sits on the approach; a fast run hops off it
    // airborne where ordinary braking is unavailable. Keep the last $30
    // before the fence at walking speed so the fence stays reachable.
    private static final int SQUEEZE_APPROACH_SPEED_CAP = 0x180;
    private static final int SQUEEZE_APPROACH_MARGIN = 0x30;
    private static final int SQUEEZE_CHARGED_WAIT_LIMIT = 0x200;
    // The $208E ledge drops $104 onto the $2090/$20F0 spike pair; the gap
    // pilot must own the airborne brake from the first falling frame.
    private static final int LOWER_SPIKE_GAP_DROP_LIMIT = 0x120;
    private static final int LOWER_SPIKE_GAP_LEDGE_LOOKAHEAD = 0x80;
    private static final int LOWER_SPIKE_GAP_APPROACH_CAP = 0x100;
    private static final int LOWER_SPIKE_GAP_PLATFORM_WAIT_LIMIT = 0x200;
    private static final int TAIL_ORDINARY_RIGHT_FALLBACK_LIMIT = 0x1800;
    private static final List<InputRun> LATE_STARPOST_INPUT_RUNS = List.of(new InputRun(120, 0x4));
    // Lower $26C8 spider crane loop to the FBZ2 subboss arena. Every gate below
    // reads exact placed-object identity, live geometry or AnPal_FBZ polarity.
    private static final int LOWER_CRANE_X = 0x26C8;
    // loc_3D11E captures a grounded P1 whose y_pos lies in [$46+$34+5, +$19)
    // once the $40 descent completes; the $0900 floor puts P1 at $08EC.
    private static final int LOWER_CRANE_FLOOR_Y = 0x08EC;
    private static final int LOWER_CRANE_ROUTE_FRAME_LIMIT = 0x1400;
    private static final int LOWER_CRANE_ROUTE_STAGE_LIMIT = 0x400;
    private static final int LOWER_CRANE_WALK_CAP = 0x100;
    // The crane releases P1 at $2831; a hop from $2850 clears the $28A9
    // TechnoSqueek and arcs through the $2904 horizontal chain grab band
    // (sub_3A8B8: dx within the $30 range, dy in [0,$18) below $08A0).
    private static final int LOWER_CRANE_CHAIN_ONE_HOP_X = 0x2850;
    private static final int LOWER_CRANE_CHAIN_HOP_HOLD = 0x19;
    private static final int LOWER_CRANE_CHAIN_RELEASE_HOLD = 2;
    // Both horizontal chains meet the $2924 vertical link; the $29C0 chain
    // ($2990..$29F0 band) meets the $29E0 vertical link.
    private static final int LOWER_CRANE_CHAIN_ONE_VERTICAL_X = 0x2924;
    private static final int LOWER_CRANE_CHAIN_TWO_BAND_LEFT_X = 0x2990;
    private static final int LOWER_CRANE_CHAIN_TWO_VERTICAL_X = 0x29E0;
    private static final int LOWER_CRANE_CHAIN_STILL_FRAMES = 4;
    // Obj_Button trigger D at $2970 opens the Obj_FBZScrewDoor floor door at $2924.
    private static final int LOWER_CRANE_BUTTON_X = 0x2970;
    private static final int LOWER_CRANE_FLOOR_DOOR_X = 0x2924;
    private static final int LOWER_CRANE_BOTTOM_CORRIDOR_MIN_Y = 0x0B00;
    // Bottom corridor: the resting $2840 Obj74 column blocks at $2863; spindash
    // west only while both $0B70 columns hang raised with a full roll of runway.
    // The $0B00 ceiling stops their rise at $31, clear of a rolling P1's $0E radius.
    private static final int LOWER_CRANE_SPINDASH_HOLD_X = 0x2890;
    private static final int LOWER_CRANE_SPINDASH_MIN_RUNWAY = 0x80;
    private static final int LOWER_CRANE_COLUMN_CLEAR_RISE = 0x28;
    private static final int LOWER_CRANE_ROLL_STOP_MAX_X = 0x26A0;
    private static final int LOWER_CRANE_ELEVATOR_X = 0x2648;
    // Leave the rising $2640 car as soon as the $0A80 corridor floor is level;
    // the shaft cap at $0A40 crushes a rider who waits.
    private static final int LOWER_CRANE_ELEVATOR_EXIT_Y = 0x0A90;
    private static final int LOWER_CRANE_LAUNCHER_X = 0x2708;
    private static final int LOWER_CRANE_CAGE_LANDING_MAX_Y = 0x0800;
    // The $2800 step is $60 tall: exactly one full jump beside its face.
    private static final int LOWER_CRANE_STEP_JUMP_X = 0x27F0;
    private static final int LOWER_CRANE_STEP_JUMP_HOLD = 0x1E;
    private static final int LOWER_CRANE_STEP_TOP_MAX_Y = 0x0790;
    private static final int LOWER_CRANE_COLUMN_ONE_X = 0x2840;
    private static final int LOWER_CRANE_COLUMN_HOP_X = 0x2816;
    private static final int LOWER_CRANE_COLUMN_HOP_HOLD = 0x14;
    private static final int LOWER_CRANE_COLUMN_TOP_MAX_Y = 0x06A4;
    // Two hops from the raised columns must land before the INACTIVE edge
    // drops them onto the $2880/$28F0 spikes.
    private static final int LOWER_CRANE_COLUMN_JUMP_MIN_RUNWAY = 0x70;
    private static final int LOWER_CRANE_COLUMN_JUMP_HOLD = 0x0E;
    private static final int LOWER_CRANE_ARENA_CORRIDOR_MIN_X = 0x2900;
    private static final int LOWER_CRANE_ARENA_CORRIDOR_MAX_Y = 0x0680;
    private static final int LOWER_CRANE_PIT_JUMP_X = 0x2A60;
    private static final int LOWER_CRANE_PIT_JUMP_HOLD = 0x19;
    private static final int LOWER_CRANE_ARENA_ARRIVAL_X = 0x2BC0;
    // Subboss arena to the SOZ request: Obj_FBZ2Subboss, the boss-event plane
    // carrier, Obj_FBZEndBoss and the end capsule; every gate reads live object
    // state, never a frame count.
    private static final int ARENA_ROUTE_FRAME_LIMIT = 0x4000;
    private static final int ARENA_ROUTE_STAGE_LIMIT = 0x2000;
    // loc_6FE22 tracks P1 at $100 and fires the beam straight down at the end of
    // each cycle: keep at least $50 from the machine, running past it to the
    // far live side wall (Fbz2SubbossSolidSideChild, half-width $13).
    private static final int SUBBOSS_KEEP_AWAY = 0x50;
    private static final int SUBBOSS_WALL_HALF_WIDTH = 0x13;
    private static final int SUBBOSS_WALL_STAND_GAP = 0x0A;
    private static final int SUBBOSS_ARENA_LEFT_WALL_X = 0x2A90;
    private static final int SUBBOSS_ARENA_RIGHT_WALL_X = 0x2BF0;
    private static final int ARENA_EXIT_HOP_MIN_X = 0x2BF0;
    private static final int ARENA_EXIT_HOP_MAX_X = 0x2C40;
    private static final int ARENA_EXIT_HOP_HOLD = 0x14;
    // The plane carrier (Obj_FBZEndBossEventControl, top-solid $4C0) lifts P1
    // through the ship interior; the ship column stays open at these offsets
    // from the controller, shifting once the controller climbs past $0380.
    private static final int PLANE_CONTROLLER_ORIGIN_X = 0x31C0;
    private static final int PLANE_CONTROLLER_ORIGIN_Y = 0x0690;
    private static final int PLANE_RIDE_LOWER_OFFSET_X = -0x123;
    private static final int PLANE_RIDE_UPPER_OFFSET_X = -0x167;
    private static final int PLANE_RIDE_OFFSET_SWITCH_Y = 0x0380;
    // Obj_FBZEndBoss arena after the $45C rebase: walls at $2E6C/$2F84.
    private static final int END_BOSS_ARENA_LEFT_STAND_X = 0x2E74;
    private static final int END_BOSS_ARENA_RIGHT_STAND_X = 0x2F7C;
    // Strike the pod with a charged spindash jump released from the wait point
    // ($78 out, or the arena wall) as the pod rises through [$0660,$0690]: at
    // $800 P1 reaches the $20 hitbox from above while it tops out, and the
    // side rebound returns P1 to the wall for the next cycle. During attack
    // rounds the arms creep $8 toward P1 (word_709FC), so stand at the wall
    // uncharged and cross with the same launch before the pod closes to $80.
    private static final int END_BOSS_WAIT_DISTANCE = 0x78;
    private static final int END_BOSS_CROSS_ROOM = 0x80;
    private static final int END_BOSS_POD_WINDOW_MIN_Y = 0x0660;
    private static final int END_BOSS_POD_WINDOW_MAX_Y = 0x0690;
    private static final int END_BOSS_LAUNCH_HOLD = 0x10;
    private static final int END_BOSS_CHARGE_TAPS = 3;
    private static final int END_CAPSULE_X = 0x307C;
    // The capsule is a $2B/$18 solid: approach from beside its west face and
    // hop onto the top, where the $1B-wide button child carries P1.
    private static final int END_CAPSULE_APPROACH_X = 0x3038;
    private static final int END_CAPSULE_JUMP_HOLD = 0x19;


    private static boolean postLauncherRecoveryComplete(
            boolean playerAirborne, int playerX, int clearX) {
        return !playerAirborne || playerX >= clearX;
    }

    @Test
    void postLauncherRecoveryRelinquishesLeftOwnershipWhenGrounded() {
        assertTrue(postLauncherRecoveryComplete(false, 0x08FC, 0x08FD),
                "grounded recovery cannot retain the airborne LEFT stage");
        assertFalse(postLauncherRecoveryComplete(true, 0x08FC, 0x08FD),
                "airborne recovery below the existing clear edge still owns LEFT");
        assertTrue(postLauncherRecoveryComplete(true, 0x08FD, 0x08FD),
                "the existing airborne world-X completion remains valid");
    }

    @Test
    void a80Subtype16DownwardPathSwitchSelectsCollisionPathCD() throws IOException {
        ObjectSpawn switcher = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.x() == 0x0A80 && spawn.y() == 0x0630)
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.PATH_SWAP)
                .filter(spawn -> spawn.subtype() == 0x16)
                .findFirst().orElseThrow();
        assertTrue(ObjectManager.isPlaneSwitcherHorizontal(switcher.subtype()));
        assertEquals(0, ObjectManager.decodePlaneSwitcherPath(switcher.subtype(), 1),
                "crossing downward selects native path 0");
        var config = new Sonic3kGameModule().getPlaneSwitcherConfig();
        assertEquals(0x0C, config.getPath0TopSolidBit() & 0xFF);
        assertEquals(0x0D, config.getPath0LrbSolidBit() & 0xFF);
    }

    @Test
    void act2ContainsExactlyTheEightNativeElevatorSubtypesAndTwelveControllers() throws IOException {
        var elevators = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.FBZ_ELEVATOR)
                .toList();
        assertEquals(12, elevators.size());
        assertEquals(Set.of(0x0F, 0x1E, 0x24, 0x25, 0x32, 0x37, 0x3B, 0x4B),
                elevators.stream().map(ObjectSpawn::subtype).collect(Collectors.toSet()));
        assertEquals(Map.of(0x0F, 1L, 0x1E, 1L, 0x24, 2L, 0x25, 1L,
                        0x32, 1L, 0x37, 3L, 0x3B, 1L, 0x4B, 2L),
                elevators.stream().collect(Collectors.groupingBy(
                        ObjectSpawn::subtype, Collectors.counting())));
    }

    @Test
    void everyElevatorPlacementResolvesToTheRealControllerFactory() throws IOException {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_FBZ; }
        };
        TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.FBZ_ELEVATOR)
                .forEach(spawn -> assertInstanceOf(
                        FbzElevatorObjectInstance.class, registry.create(spawn)));
    }

    @Test
    void ordinaryPlacementRouteTo2b30HasNoMechanicalPlaceholder() throws IOException {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_FBZ; }
        };
        var unresolved = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.x() < 0x2B30)
                .filter(spawn -> spawn.objectId() != 0xCF)
                .filter(spawn -> registry.create(spawn) instanceof PlaceholderObjectInstance)
                .map(spawn -> String.format("$%04X id=$%02X subtype=$%02X",
                        spawn.x(), spawn.objectId(), spawn.subtype()))
                .toList();
        assertTrue(unresolved.isEmpty(), () -> "ordinary preboss placeholders: " + unresolved);
    }

    public static void assertLateNativeStarpostRestartMaterializesAndExecutesLowerMagneticSection() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        ObjectSpawn checkpoint = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == GameServices.module().getCheckpointObjectId())
                .filter(spawn -> (spawn.subtype() & 0x7F) == 5)
                .findFirst().orElseThrow();
        CheckpointState state = (CheckpointState) GameServices.level().getCheckpointState();
        state.restoreFromSaved(checkpoint.x(), checkpoint.y(),
                checkpoint.x() - 0xA0, checkpoint.y() - 0x60, checkpoint.subtype() & 0x7F);
        GameServices.level().respawnPlayer();

        assertTrue(state.isActive());
        assertEquals(5, state.getLastCheckpointIndex());
        assertEquals(checkpoint.x(), fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(checkpoint.y(), fixture.sprite().getCentreY() & 0xFFFF);
        assertEquals(checkpoint.x() - 0xA0, state.getSavedCameraX());
        assertEquals(checkpoint.y() - 0x60, state.getSavedCameraY());
        assertEquals(checkpoint.x() - 0xA0, fixture.camera().getX() & 0xFFFF);
        assertEquals(Math.min(state.getSavedCameraY(), fixture.camera().getMaxY() & 0xFFFF),
                fixture.camera().getY() & 0xFFFF,
                "checkpoint restart camera must use the game-owned FBZ2 vertical clamp");
        LateStarpostMilestones milestones = new LateStarpostMilestones();
        FixedInputRunner runner = new FixedInputRunner(
                fixture, GameServices.level().getObjectManager(), milestones::observe);
        runner.run(LATE_STARPOST_INPUT_RUNS, (frame, player) ->
                assertFalse(player.isHurt() || player.getDead(),
                        () -> "fixed checkpoint section took damage at frame " + frame.frames()));

        assertTrue(milestones.platformObserved,
                "late starpost did not materialize the lower $2840 platform");
        assertTrue(milestones.platformExecuted,
                "lower $2840 platform did not survive into its next execution pass");
        assertTrue(milestones.chainObserved,
                "lower $2840 platform did not allocate its real chain child");
        assertTrue(milestones.chainExecuted,
                "lower magnetic chain did not survive into its next execution pass");
    }

    /**
     * Runs the fixed native-start route through every traversal stage, both bosses,
     * the capsule/results handoff, and the production request for Sandopolis Act 0.
     */
    public static void assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones() {
        runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(fixture -> { });
    }

    public static void assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(
            Consumer<HeadlessTestFixture> startAssertion) {
        runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(startAssertion);
    }

    public static RouteCompletionEvidence
            runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(
                    Consumer<HeadlessTestFixture> startAssertion) {
        return runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(
                startAssertion, null);
    }

    public static RouteCompletionEvidence
            runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(
                    Consumer<HeadlessTestFixture> startAssertion, String donorCode) {
        HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1);
        if (donorCode != null && !donorCode.equals("off")) {
            fixtureBuilder.withCrossGameDonation(donorCode);
        }
        HeadlessTestFixture fixture = fixtureBuilder.build();
        startAssertion.accept(fixture);
        ObjectManager objects = GameServices.level().getObjectManager();
        Set<Class<?>> encounteredFamilies = new LinkedHashSet<>();
        Set<Class<?>> executedFamilies = new LinkedHashSet<>();
        RouteMilestones milestones = new RouteMilestones();
        FixedInputRunner runner = new FixedInputRunner(fixture, objects,
                (active, previous, player, manager) -> observeActiveObjects(
                        active, previous, encounteredFamilies, executedFamilies,
                        milestones, player, manager));
        FrameCheck alive = (frame, player) ->
                assertFalse(player.getDead(),
                        () -> routeEvidence(fixture, frame.frames(), milestones)
                                + frame.recentDiagnostic());

        boolean ledgeReached = runner.runUntil(NATIVE_START_TO_LOWER_FLOOR, alive, () -> {
            AbstractPlayableSprite player = fixture.sprite();
            int x = player.getCentreX() & 0xFFFF;
            return !player.getAir() && (player.getCentreY() & 0xFFFF) == LEDGE_FLOOR_Y
                    && x >= LEDGE_HANDOFF_MIN_X && x <= LEDGE_HANDOFF_MAX_X;
        });
        assertTrue(ledgeReached, () -> "BK2 prefix never landed on the $01EC ledge: "
                + routeEvidence(fixture, runner.frames(), milestones) + runner.recentDiagnostic());
        runner.runLedgeToLoopFloor(alive);
        // The placed $09C8/$0498 spider crane is an upper optional branch.
        // This mandatory run is already on the authored lower route here;
        // exact $E5 capture/move/release and sidekick-authority coverage lives
        // in FbzCompatibilityInteractionProbe for every compatibility row.
        // The BK2's own player reaches this landing with a single ring
        // (fbz_completerun rows 23622 and 23796 lose the rings on the $0818
        // chain descent; row 24081 lands at $082A/$02EC with rings=1), so the
        // floor here is only the ROM's: not ringless. Later hazards are gated
        // on live geometry rather than on a ring buffer.
        int midpointRingFloor = GameServices.level().getLevelGamestate().getRings();
        assertTrue(midpointRingFloor >= 1,
                () -> "lower-floor landing lost the route's safety rings: "
                        + routeEvidence(fixture, runner.frames(), milestones));
        List<InputRun> midpointApproach = new java.util.ArrayList<>();
        // Runs 0-26 climb from the $0A80 lower floor to the $06AC ledge, stand
        // on the $0948 button for the $0918 screw door and board the $08C0
        // car; the live midpoint controller owns the car itself.
        List<InputRun> elevatorSubbossBossAndExit = List.of(
                new InputRun(100, 0x8),
                new InputRun(120, 0x0),
                new InputRun(30, 0x18), new InputRun(30, 0x8),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(30, 0x18), new InputRun(30, 0x8),
                new InputRun(20, 0x8), new InputRun(20, 0x18),
                new InputRun(40, 0x8), new InputRun(20, 0x18),
                new InputRun(30, 0x4),
                new InputRun(20, 0x8),
                new InputRun(30, 0x14),
                new InputRun(30, 0x4),
                new InputRun(40, 0x8),
                new InputRun(1, 0x4), new InputRun(3, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(2, 0x4), new InputRun(40, 0x4),
                new InputRun(20, 0x18), new InputRun(60, 0x8),
                new InputRun(1, 0x8),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(30, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(55, 0x4),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(69, 0x4),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(40, 0x4),
                new InputRun(20, 0x14), new InputRun(60, 0x4),
                new InputRun(400, 0x8),
                new InputRun(20, 0x18), new InputRun(200, 0x8),
                // Bounded allowance for the common-route subtype-$24 button:
                // the native profile reaches it 34 door-motion frames later
                // than the donated profiles after the earlier real-object
                // waypoints. The controller, not this neutral source mask,
                // owns the exact trigger wait and subsequent RIGHT release.
                new InputRun(1000 + LATE_BUTTON_DOOR_EXTENSION_FRAMES, 0x0));
        FrameCheck tailSafety = (frame, player) -> {
            assertFalse(player.getDead(),
                    () -> routeEvidence(fixture, frame.frames(), milestones)
                            + frame.recentDiagnostic());
            // Same ROM floor as the lower-floor landing: the BK2 runs long
            // stretches of act 2 on one or two rings, so only a ringless P1
            // (one hit from Kill_Character) is a route defect here.
            assertTrue(GameServices.level().getLevelGamestate().getRings() >= 1,
                    () -> routeEvidence(fixture, frame.frames(), milestones)
                            + " subboss=" + objects.activeObjectsOfType(Fbz2SubbossInstance.class)
                            .stream().map(boss -> boss.phaseName() + "/" + boss.cyclesRemaining())
                            .toList()
                            + " laser=" + objects.activeObjectsOfType(Fbz2SubbossLaserChild.class)
                            .stream().map(laser -> laser.getX() + ":" + laser.getY()).toList()
                            + " boss=" + objects.activeObjectsOfType(FbzEndBossInstance.class)
                            .stream().map(boss -> boss.phase() + "/" + boss.getCollisionProperty())
                            .toList()
                            + frame.recentDiagnostic());
        };
        List<InputRun> spikeCorridor = elevatorSubbossBossAndExit.subList(
                SPIKE_CORRIDOR_START_RUN, SPIKE_CORRIDOR_END_RUN);
        assertEquals(SPIKE_CORRIDOR_WAYPOINT_FRAMES,
                spikeCorridor.stream().mapToInt(InputRun::frames).sum(),
                "spike-corridor waypoint must replace only the proven hazard segment");
        // The ledge controller ends grounded inside acquireMidpointCar's
        // upper-loop approach, so the BK2's 27 loop-climb runs are not consumed.
        FrameCheck midpointApproachSafety = (frame, player) -> {
            tailSafety.afterFrame(frame, player);
            // The corrected cage/spike path legitimately collects more rings
            // (the complete-run trace carries $17 here). No approach damage
            // may reduce the actual entry floor before exact car ownership.
            assertTrue(GameServices.level().getLevelGamestate().getRings()
                            >= midpointRingFloor,
                    () -> routeEvidence(fixture, frame.frames(), milestones)
                            + " midpointRingFloor=" + midpointRingFloor);
        };
        runner.rideMidpointCarThroughSpikeCorridor(
                midpointApproach, SPIKE_CORRIDOR_WAYPOINT_FRAMES,
                midpointApproachSafety);
        StopCondition sozRequested = () -> GameServices.level().getRequestedZone()
                == Sonic3kZoneIds.ZONE_SOZ && GameServices.level().getRequestedAct() == 0;
        boolean forcedExitRequested = runner.runUntilWithDescendingElevatorCorridor(
                elevatorSubbossBossAndExit.subList(
                        SPIKE_CORRIDOR_END_RUN, elevatorSubbossBossAndExit.size()),
                tailSafety, sozRequested);
        if (!forcedExitRequested && runner.lowerCraneApproachReached) {
            forcedExitRequested = runner.runLowerCraneRouteToSubboss(tailSafety, sozRequested);
            if (!forcedExitRequested) {
                forcedExitRequested = runner.runArenaToExit(tailSafety, sozRequested);
            }
        }
        assertTrue(milestones.spiderControl, "the $26C8 spider crane never carried P1");
        assertTrue(executedFamilies.containsAll(encounteredFamilies),
                () -> "encountered family missed its next execution pass: "
                        + difference(encounteredFamilies, executedFamilies));
        assertTrue(milestones.cageCapture, "opening wire cage never captured P1");
        assertTrue(milestones.elevatorRide, "no real $E2 car carried P1");
        assertTrue(milestones.launcherRide, "floor launcher never received a standing P1 contact");
        assertTrue(milestones.chainControl, "chain-link transport never controlled P1");
        assertTrue(executedFamilies.contains(FbzScrewDoorObjectInstance.class),
                "placed screw-door family never executed on the route");
        assertTrue(fixture.camera().getX() >= 0x2B30,
                "route has not reached the native FBZ2 subboss event: "
                        + routeEvidence(fixture, runner.frames(), milestones)
                        + runner.recentDiagnostic());
        assertTrue(forcedExitRequested,
                "boss-owned forced exit never requested SOZ act 0: "
                        + routeEvidence(fixture, runner.frames(), milestones));
        assertEquals(Sonic3kZoneIds.ZONE_SOZ, GameServices.level().getRequestedZone());
        assertEquals(0, GameServices.level().getRequestedAct());
        for (String key : List.of(Sonic3kObjectArtKeys.FBZ_EXIT_DOOR,
                Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY,
                Sonic3kObjectArtKeys.FBZ_EXIT_HALL)) {
            var renderer=GameServices.level().getObjectRenderManager().getRenderer(key);
            assertNotNull(renderer,"post-capsule PLC did not publish "+key);
            assertTrue(renderer.isReady(),"post-capsule PLC consumer was not cached: "+key);
        }
        return milestones.evidence(runner.frames(), forcedExitRequested);
    }

    private static void observeActiveObjects(
            Set<ObjectInstance> active, Set<ObjectInstance> previousFrame,
            Set<Class<?>> encounteredFamilies, Set<Class<?>> executedFamilies,
            RouteMilestones milestones, AbstractPlayableSprite player, ObjectManager objects) {
        milestones.observeFrame(active, previousFrame, player, objects);
        for (ObjectInstance object : active) {
            boolean excluded = object.getSpawn() != null
                    && (object.getSpawn().x() >= 0x2B30
                    || object.getSpawn().objectId() == 0);
            assertTrue(!(object instanceof PlaceholderObjectInstance) || excluded,
                    () -> "mechanical placeholder entered fixed FBZ2 route: "
                            + object.getClass().getName() + " spawn=" + object.getSpawn());
            encounteredFamilies.add(object.getClass());
            if (previousFrame.contains(object)) executedFamilies.add(object.getClass());
            if (object instanceof FbzWireCageObjectInstance cage && cage.heldByParticipant(0)) {
                milestones.cageCapture = true;
            }
            if (object instanceof FbzEggPrisonFragmentInstance
                    || object instanceof FbzEggPrisonExplosionController) {
                milestones.prisonOpened = true;
            }
        }

        ObjectInstance riding = objects.getRidingObject(player);
        milestones.elevatorRide |= riding instanceof FbzElevatorObjectInstance.Car;
        milestones.launcherRide |= riding instanceof FbzDezPlayerLauncherObjectInstance;
        boolean onFlamethrower = riding instanceof FbzFlamethrowerObjectInstance flame
                && flame.mappingFrame() == 2;
        milestones.flamethrowerRide |= onFlamethrower;
        milestones.magneticPlatformRide |= riding instanceof FbzMagneticPlatformObjectInstance;
        if (!player.isObjectControlled()) return;
        Object exactOwner = player.getLatchedSolidObjectInstance();
        milestones.chainControl |= exactOwner instanceof FbzChainLinkObjectInstance chain
                && active.contains(chain)
                && chain.stateForParticipant(0).grabbed();
        int playerX = player.getCentreX() & 0xFFFF;
        milestones.spiderControl |= active.stream().anyMatch(object ->
                object instanceof FbzSpiderCraneObjectInstance crane
                        && Math.abs(crane.getX() - playerX) < 0x100
                        && switch (crane.stateName()) {
                            case "CAPTURE", "RETRACT", "TRAVEL" -> true;
                            default -> false;
                        });
    }

    private static List<InputRun> parseInputProgram() {
        List<InputRun> runs = InputProgram.parse(PREBOSS_INPUT_PROGRAM);
        // The corrected stationary-cage $42 control state keeps native forward
        // movement active.  The complete-run BK2 likewise holds right across
        // the comparable $078C-$07C5 cage-exit approach; retain that direction
        // only long enough to clear the exit, then restore the authored wait.
        InputRun postCage = runs.remove(42);
        if (!postCage.equals(new InputRun(205, 0x08))) {
            throw new IllegalStateException("post-cage route segment moved: " + postCage);
        }
        runs.add(42, new InputRun(30, 0x08));
        runs.add(43, new InputRun(175, 0x00));
        // The placed spikes are centred at $0810 and $0870. Opposite steering
        // trials land symmetrically on those hazards ($080A/$0875), proving the
        // authored safe gap is the $0840 midpoint. Preserve the 27-frame jump
        // cadence without horizontal steering so the arc remains in that gap.
        if (!runs.get(48).equals(new InputRun(9, 0x08))
                || !runs.get(49).equals(new InputRun(18, 0x18))) {
            throw new IllegalStateException("post-cage spike approach moved");
        }
        runs.set(48, new InputRun(9, 0x10));
        runs.set(49, new InputRun(18, 0x00));
        // The later second approach reaches x=$0868 without lateral speed.
        // Match BK2 $5DD6-$5DE2 exactly: seven left+jump frames, six left
        // frames, then neutral for the remainder of the original 30-frame run.
        if (!runs.get(56).equals(new InputRun(25, 0x12))
                || !runs.get(57).equals(new InputRun(5, 0x02))) {
            throw new IllegalStateException("second spike approach moved");
        }
        runs.remove(57);
        runs.remove(56);
        runs.add(56, new InputRun(7, 0x14));
        // The native-spawn approach has more leftward momentum than the
        // seamless-run BK2 at this point. Keep its authoritative jump edge,
        // but neutralize the remaining 23 frames to land in the measured gap.
        runs.add(57, new InputRun(23, 0x00));
        return List.copyOf(runs);
    }

    private static Set<Class<?>> difference(Set<Class<?>> expected, Set<Class<?>> actual) {
        Set<Class<?>> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }

    private static String routeEvidence(
            HeadlessTestFixture fixture, int frames, RouteMilestones milestones) {
        AbstractPlayableSprite player = fixture.sprite();
        return "frame=" + frames
                + " camera=($" + Integer.toHexString(fixture.camera().getX() & 0xFFFF)
                + ",$" + Integer.toHexString(fixture.camera().getY() & 0xFFFF) + ')'
                + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ')'
                + " speed=($" + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                + ",$" + Integer.toHexString(player.getYSpeed() & 0xFFFF) + ')'
                + " rings=" + GameServices.level().getLevelGamestate().getRings()
                + " hurt=" + player.isHurt() + " dead=" + player.getDead()
                + " nearby=" + GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> object.getSpawn() != null)
                .filter(object -> Math.abs(object.getX() - player.getCentreX()) <= 0x80
                        && Math.abs(object.getY() - player.getCentreY()) <= 0x80)
                .map(object -> object.getClass().getSimpleName() + "@($"
                        + Integer.toHexString(object.getX() & 0xFFFF) + ", $"
                        + Integer.toHexString(object.getY() & 0xFFFF) + ")")
                .toList()
                + " milestones=" + milestones;
    }

    @FunctionalInterface
    private interface FrameObserver {
        void observe(Set<ObjectInstance> active, Set<ObjectInstance> previous,
                     AbstractPlayableSprite player, ObjectManager objects);
    }

    @FunctionalInterface
    private interface FrameCheck {
        void afterFrame(FixedInputRunner runner, AbstractPlayableSprite player);
    }

    @FunctionalInterface
    private interface StopCondition {
        boolean reached();
    }

    /**
     * Executes immutable fixed-input runs while reusing exactly two identity sets
     * for current/previous object-lifetime observations across every frame.
     */
    private static final class FixedInputRunner {
        private static final StopCondition NEVER_STOP = () -> false;

        private final HeadlessTestFixture fixture;
        private final ObjectManager objects;
        private final FrameObserver observer;
        private final ObjectLifetimeFrames lifetime;
        private final RecentFrameLog recentLog = new RecentFrameLog(30);
        private String lastControllerDiagnostic = " controller=unobserved";
        private int frames;
        private boolean s1UpperCarEgressCompleted;
        private boolean s1UpperAssistObserved;
        private FbzWireCageObjectInstance horizontalCageTarget;
        private boolean horizontalCageWasHeld;
        private boolean horizontalCageEgressJumpActive;
        private boolean earlySpikeGapRecovery;
        private boolean lowerCraneApproachReached;
        private int earlySpikeRollDirection;
        private int earlySpikeRollClearX;

        private FixedInputRunner(
                HeadlessTestFixture fixture, ObjectManager objects, FrameObserver observer) {
            this.fixture = fixture;
            this.objects = objects;
            this.observer = observer;
            // The fixture has already loaded its initial placement window.
            // Seed it so frame one cannot report those existing objects as
            // genuine absent-to-active placement transitions.
            this.lifetime = new ObjectLifetimeFrames(objects);
        }

        private void run(List<InputRun> runs, FrameCheck check) {
            assertFalse(runUntil(runs, check, NEVER_STOP), "non-stopping input run stopped");
        }

        private boolean runUntil(
                List<InputRun> runs, FrameCheck check, StopCondition stopCondition) {
            for (InputRun run : runs) {
                for (int i = 0; i < run.frames(); i++) {
                    lifetime.beginFrame(objects);
                    AbstractPlayableSprite player = fixture.sprite();
                    observer.observe(lifetime.active(), lifetime.previous(), player, objects);
                    int mask = run.mask();
                    s1UpperAssistObserved |= GameServices.zoneRuntimeRegistry()
                            .currentAs(FbzZoneRuntimeState.class)
                            .map(runtime -> runtime.s1DonationUpperLoopAssistState()
                                    == FbzZoneRuntimeState.S1DonationUpperLoopAssistState.CONSUMED)
                            .orElse(false);
                    int playerX = player.getCentreX() & 0xFFFF;
                    int playerY = player.getCentreY() & 0xFFFF;
                    Sonic3kSpikeObjectInstance earlyLeftSpike = objects.activeObjectsOfType(
                                    Sonic3kSpikeObjectInstance.class).stream()
                            .filter(spike -> spike.getSpawn().x() == 0x0810
                                    && spike.getSpawn().y() == 0x0290)
                            .findFirst().orElse(null);
                    Sonic3kSpikeObjectInstance earlyRightSpike = objects.activeObjectsOfType(
                                    Sonic3kSpikeObjectInstance.class).stream()
                            .filter(spike -> spike.getSpawn().x() == 0x0870
                                    && spike.getSpawn().y() == 0x0290)
                            .findFirst().orElse(null);
                    if (earlyLeftSpike != null && earlyRightSpike != null) {
                        int gapX = (earlyLeftSpike.getX() + earlyRightSpike.getX()) / 2;
                        int spikeTop = earlyLeftSpike.getY()
                                - earlyLeftSpike.getSolidParams().airHalfHeight();
                        ObjectInstance riding = player.getLatchedSolidObjectInstance();
                        boolean chainThreat = riding instanceof FbzChainLinkObjectInstance chain
                                && !chain.horizontalMode() && player.isObjectControlled()
                                && playerX >= earlyLeftSpike.getX() - earlyLeftSpike.getSolidParams().halfWidth()
                                && playerX <= earlyRightSpike.getX() + earlyRightSpike.getSolidParams().halfWidth()
                                && playerY < spikeTop
                                && !player.getInvulnerable()
                                && GameServices.level().getLevelGamestate().getRings() == 0
                                && playerY + player.getStandYRadius() + 2 >= spikeTop - 1;
                        if (player.getSpindash() && !player.getAir()
                                && (riding == earlyLeftSpike || riding == earlyRightSpike)
                                && GameServices.level().getLevelGamestate().getRings() == 0) {
                            int direction = player.getDirection() == com.openggf.physics.Direction.LEFT ? -1 : 1;
                            Sonic3kSpikeObjectInstance spike = (Sonic3kSpikeObjectInstance) riding;
                            int clearX = spike.getX() + direction * (spike.getSolidParams().halfWidth() + 1);
                            short[] speeds = player.getGameRules().playerCapability().spindashSpeedTable();
                            int releaseSpeed = speeds[Math.min(player.getSpindashCounter() >> 8, speeds.length - 1)];
                            int distance = Math.abs(clearX - playerX) << 8;
                            int releaseLead = 2 + (distance + releaseSpeed - 1) / releaseSpeed;
                            if (player.getInvulnerableFrames() <= releaseLead) {
                                earlySpikeRollDirection = direction;
                                earlySpikeRollClearX = clearX;
                            }
                        }
                        if (earlySpikeRollDirection != 0) {
                            // Tails_Spindash releases the charged ground speed on
                            // the neutral frame; rolling displacement starts next
                            // frame. A jump here would freeze air steering through
                            // the ROM rolling-jump flag, so let that roll clear the
                            // current spike's live inclusive horizontal edge first.
                            mask = 0;
                            if ((playerX - earlySpikeRollClearX) * earlySpikeRollDirection >= 0) {
                                earlySpikeRollDirection = 0;
                            } else if (!player.getAir() && !player.getRolling() && !player.getSpindash()) {
                                // Solid contact can stop the released roll before
                                // the edge. Wait for native unrolling before the
                                // jump so ordinary air steering remains available.
                                earlySpikeRollDirection = 0;
                                earlySpikeGapRecovery = true;
                                mask = AbstractPlayableSprite.INPUT_JUMP
                                        | (playerX < gapX ? AbstractPlayableSprite.INPUT_RIGHT
                                        : AbstractPlayableSprite.INPUT_LEFT);
                            }
                        } else if (chainThreat) {
                            // Obj_FBZChainLink loc_3A860 moves a held chain by
                            // its initialized two pixels; loc_3A910 releases on
                            // a normal jump edge with directional +/-$200 X speed.
                            // Preserve the authored route unless the next carried
                            // step would put unprotected, zero-ring P1 on a spike.
                            earlySpikeGapRecovery = true;
                            mask = AbstractPlayableSprite.INPUT_JUMP
                                    | (playerX < gapX ? AbstractPlayableSprite.INPUT_RIGHT
                                    : AbstractPlayableSprite.INPUT_LEFT);
                        } else if (earlySpikeGapRecovery) {
                            if (playerY - player.getYRadius() > earlyLeftSpike.getY()
                                    + earlyLeftSpike.getSolidParams().groundHalfHeight()) {
                                earlySpikeGapRecovery = false;
                            } else {
                                // Sonic_ChgJumpDir applies twice run acceleration.
                                // Aim using the distance needed to brake that live
                                // lateral speed, rather than a viewport/frame timer.
                                int velocity = player.getXSpeed();
                                int airAcceleration = 2 * player.getRunAccel();
                                int projectedX = playerX + velocity * Math.abs(velocity)
                                        / (2 * airAcceleration * 256);
                                mask = projectedX < gapX ? AbstractPlayableSprite.INPUT_RIGHT
                                        : projectedX > gapX ? AbstractPlayableSprite.INPUT_LEFT : 0;
                            }
                        }
                    }
                    if (!s1UpperCarEgressCompleted && s1UpperAssistObserved
                            && playerX >= 0x0880 && playerX <= 0x0940
                            && playerY >= 0x0830 && playerY <= 0x0890) {
                        // S1 donation compatibility: after the typed upper-loop
                        // assist has supplied the native launch, reproduce BK2
                        // $62C4+ with ordinary RIGHT across the exact $08C0-car
                        // egress. This prevents later source-cadence neutral from
                        // leaving P1 against the $0880/$0900 spike pair.
                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                        if (playerX >= 0x0940) s1UpperCarEgressCompleted = true;
                    }
                    if (horizontalCageTarget == null && player.getYSpeed() >= 0
                            && !player.isObjectControlled()) {
                        horizontalCageTarget = objects.activeObjectsOfType(
                                        FbzWireCageObjectInstance.class).stream()
                                .filter(cage -> !cage.isDestroyed())
                                .filter(cage -> !cage.verticalMode())
                                .filter(cage -> cage.getSpawn().x() == 0x0EC0)
                                .filter(cage -> cage.getSpawn().y() == 0x0B80)
                                .filter(cage -> cage.getSpawn().subtype() == 0x18)
                                .filter(cage -> playerX
                                        >= cage.getSpawn().x() - cage.rangePixels() - 0x100)
                                .filter(cage -> playerX
                                        <= cage.getSpawn().x() + cage.rangePixels())
                                .filter(cage -> playerY >= cage.getSpawn().y() - 0x180)
                                .filter(cage -> playerY <= cage.getSpawn().y() + 0x3C)
                                .min(java.util.Comparator.comparingInt(cage ->
                                        Math.abs(cage.getSpawn().x() - playerX)))
                                .orElse(null);
                    }
                    if (horizontalCageTarget != null) {
                        int cageX = horizontalCageTarget.getSpawn().x();
                        int cageY = horizontalCageTarget.getSpawn().y();
                        int leftEdge = cageX - horizontalCageTarget.rangePixels();
                        int cooldownBandBottom = cageY - 0x38;
                        int surfaceY = cageY + 0x3C;
                        boolean heldByTarget = player.isOnObject()
                                && player.getLatchedSolidObjectInstance()
                                == horizontalCageTarget;
                        horizontalCageWasHeld |= heldByTarget;
                        if ((horizontalCageWasHeld && !heldByTarget)
                                || player.isObjectControlled()
                                || playerY > surfaceY + player.getYRadius()) {
                            horizontalCageTarget = null;
                            horizontalCageWasHeld = false;
                        } else if (playerY < cooldownBandBottom) {
                            // Obj_FBZWireCage writes a 40-frame cooldown when
                            // P1 is inside its horizontal width at y-$50..-$39.
                            // Stay just outside the immutable left edge until
                            // below that ROM band, then enter for the real
                            // -$10..-$01 landing check.
                            if (playerX >= leftEdge - 0x08 || player.getXSpeed() > 0x0100) {
                                mask = AbstractPlayableSprite.INPUT_LEFT;
                            } else if (playerX < leftEdge - 0x20) {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else {
                                mask = 0;
                            }
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                            int rightEdge = cageX + horizontalCageTarget.rangePixels();
                            if (heldByTarget && playerX >= rightEdge - 0x28) {
                                // The adjacent placed $0FC0 platform begins
                                // only $15 beyond this cage's authored right
                                // range. Take an ordinary jump edge while the
                                // exact cage still owns the standing contact.
                                horizontalCageEgressJumpActive = true;
                                mask |= AbstractPlayableSprite.INPUT_JUMP;
                            }
                        }
                    }
                    if (horizontalCageEgressJumpActive) {
                        if (player.getAir()) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        } else if (horizontalCageTarget == null) {
                            horizontalCageEgressJumpActive = false;
                        }
                    }
                    InputProgram.step(fixture, mask);
                    frames++;
                    recordRecentFrame(mask, player);
                    if (stopCondition.reached()) {
                        lifetime.endFrame();
                        return true;
                    }
                    check.afterFrame(this, player);
                    lifetime.endFrame();
                }
            }
            return false;
        }

        /**
         * Consumes the existing tail masks while retaining P1 on the exact
         * descending $E2/$37 car through the $0B80/$0C00 spike shaft. The
         * override arms only from real ride and screw-door state and never
         * inserts a frame or mutates player/object state.
         */
        private boolean runUntilWithDescendingElevatorCorridor(
                List<InputRun> runs, FrameCheck check, StopCondition stopCondition) {
            final int targetX = 0x0BC0;
            final int preEgressPlayerX = 0x0BC4;
            // $3B is the car's ROM SolidObjectTopSloped2 half-width. Waiting
            // one pixel before its live left edge leaves a one-frame ordinary
            // intercept instead of spending the vertical catch window running
            // across empty floor from the screw door.
            final int preAcquisitionPlayerX = targetX - 0x3B - 1;
            final int safeMinX = 0x0B9C;
            final int safeMaxX = 0x0BE4;
            final int spikeEnvelopeMinY = 0x08DD;
            final int egressStartY = 0x09F0;
            final int squashEscapeBandMaxX = 0x0B94;
            final int leftJumpGateX = 0x0B57;
            final int lowerPathLandingMaxX = 0x0AD6;
            final int lowerPathLandingMinY = 0x0A34;
            final int spindashBrakeStartX = 0x0980;
            final int stableLowerLoopMaxX = 0x0810;
            final int stableLowerLoopMinY = 0x09EC;
            final int risingCarTargetX = 0x06C9;
            final int risingCarSafeMinX = 0x069C;
            final int risingCarSafeMaxX = 0x06E4;
            final int risingCarApproachMaxX = 0x0730;
            final int risingCarExitY = 0x08BB;
            final int risingCarJumpX = 0x06E7;
            final int risingCarSafeExitX = 0x0730;
            final int flamethrowerLandingY = 0x04EC;
            final int flamethrowerJumpX = 0x08C8;
            final int flamethrowerJumpMaxSpeed = 0x02E8;
            final int launcherRecoveryClearX = 0x08FD;
            final int spiderCraneX = 0x09C8;
            final int magneticCorridorExitX = 0x0E00;
            final int postDoorPathSwitchX = 0x1300;
            final int postDoorRunUpMinX = 0x1200;
            final int postDoorWallX = 0x1290;
            final int authoredSpindashChargeFrames = 27;
            final int carriedHandoffY = 0x0A2B;
            final int releasedHandoffY = 0x0A2C;
            final int frameLimit = 0x300;
            FbzElevatorObjectInstance.Car retainedCar = null;
            FbzElevatorObjectInstance.Car risingCar = null;
            FbzElevatorObjectInstance.Car descendingCar = null;
            FbzSpiderCraneObjectInstance retainedSpiderCrane = null;
            int carryStartY = 0;
            int carCarryStartY = 0;
            int lastRetainedPlayerY = 0;
            int controlledFrames = 0;
            boolean carryReachedHandoff = false;
            boolean descendingDoorOpened = false;
            boolean descendingButtonLive = false;
            boolean descendingControllerActiveLast = false;
            boolean naturallyReleased = false;
            boolean lowerPathJumpStarted = false;
            boolean lowerPathLandingReached = false;
            boolean ordinaryRollRequested = false;
            boolean spindashApproachArmed = false;
            boolean spindashCrouchSetup = false;
            boolean spindashReleased = false;
            boolean lowerLoopLaunchCommitted = false;
            int spindashChargeFrame = -1;
            int spindashReleaseSpeed = 0;
            boolean completed = false;
            boolean risingCarRideCompleted = false;
            boolean risingCarExitStarted = false;
            boolean risingCarExitJumpPressed = false;
            boolean risingCarAcquisitionArmed = false;
            boolean midpointCarEgressActive = true;
            boolean shaftRegroupActive = false;
            boolean shaftFloorEastActive = false;
            int shaftRegroupFrames = 0;
            boolean missedDescendingCarSpikeJumpActive = false;
            int postLauncherRouteStage = 0;
            int squeekJumpAttempts = 0;
            int lateCorridorJumpHold = 0;
            int springTowerStage = 0;
            boolean spikeBallCorridorCommitted = false;
            int spikeBallCorridorCommittedCount = 0;
            final int springTowerMinX = 0x0700;
            final int springTowerMaxX = 0x07C0;
            final int springTowerMaxY = 0x0890;
            final int springWaitX = 0x0795;
            final int springLaunchSpeed = -0x0E00;
            final int springApexDriftMaxY = 0x0520;
            final int springLauncherX = 0x078A;
            int flamethrowerJumpHoldFrames = 0;
            int flamethrowerLeftJumpHoldFrames = 0;
            boolean screwDoorRecoveryArmed = false;
            boolean screwDoorRecoveryCompleted = false;
            boolean postDoorPathSwitchReached = false;
            int postDoorRunUpPhase = 0;
            int lateButtonDoorStage = 0;
            int trigger0DoorStage = 0;
            int descendingDoorStage = 0;
            boolean descendingButtonBrakeReady = false;
            int trigger7DoorStage = 0;
            int trigger7LandingAttempts = 0;
            boolean trigger7EgressJumpStarted = false;
            boolean trigger7RightHandoffCommitted = false;
            int trigger7HandoffPlayerX = -1;
            int trigger7HandoffPlayerY = -1;
            int trigger7HandoffXSpeed = 0;
            boolean trigger7LandingReached = false;
            boolean trigger7TurnReached = false;
            boolean earlyFlamethrowerJumpActive = false;
            boolean lowerLauncherAcquisitionJumpActive = false;
            int lateHazardJumpAttempts = 0;
            boolean lateHazardJumpActive = false;
            ObjectSpawn magneticPlatformHazardPlacement = null;
            FbzMagneticPlatformObjectInstance magneticPlatformHazardTarget = null;
            boolean magneticPlatformHazardArmed = false;
            boolean magneticPlatformVerticalClearanceObserved = false;
            boolean magneticPlatformCurrentVerticalClearanceObserved = false;
            boolean magneticPlatformHazardCleared = false;
            int magneticPlatformHazardClearances = 0;
            int magneticPlatformLiveBindings = 0;
            int magneticPlatformHazardRingsAtArm = -1;
            int magneticPlatformSafetyRings = -1;
            int magneticPlatformHazardWaitFrames = 0;
            long magneticPlatformPreviousLiveYFixed = Long.MIN_VALUE;
            boolean magneticPlatformCrossingCommitted = false;
            int magneticPlatformCrossingBudget = 0;
            int magneticPlatformCrossingFrames = 0;
            Set<Integer> magneticPlatformEncounteredLayoutIndices = new LinkedHashSet<>();
            Set<Integer> magneticPlatformClearedLayoutIndices = new LinkedHashSet<>();
            boolean magneticPlatformRideMode = false;
            int magneticPlatformRideStage = 0;
            int magneticPlatformRideFrames = 0;
            int magneticPlatformRideAttempts = 0;
            boolean magneticPlatformRideJumpStarted = false;
            boolean magneticPlatformRideSettled = false;
            FbzMagneticPlatformObjectInstance magneticPlatformRideHopTarget = null;
            Sonic3kSpikeObjectInstance magneticPlatformRideBlockingSpike = null;
            Sonic3kInvisibleBlockObjectInstance squeezeCorridorTarget = null;
            FbzElevatorObjectInstance.Car squeezeCorridorSupport = null;
            boolean squeezeCorridorSafetyArmed = false;
            boolean squeezeCorridorRollRequested = false;
            int squeezeCorridorStage = 0;
            boolean squeezeCorridorSpindashCapable = false;
            int squeezeCorridorLaunchSpeed = 0;
            boolean squeezeCorridorAssistConsumed = false;
            boolean squeezeCorridorAssistEverConsumed = false;
            boolean squeezeCorridorEntered = false;
            boolean squeezeCorridorSupportAcquired = false;
            boolean squeezeCorridorSupportExited = false;
            int squeezeCorridorS1RollFrames = 0;
            int squeezeCorridorS1WaitFrames = 0;
            int squeezeCorridorBlockSlot = -1;
            int squeezeCorridorSupportSlot = -1;
            boolean squeezeCorridorCarBeforeBlock = false;
            int squeezeCorridorSafetyRings = -1;
            int squeezeCorridorLiveBindings = 0;
            int squeezeCorridorClearances = 0;
            Sonic3kSpikeObjectInstance landingGapRight = null;
            int landingGapX = -1;
            boolean landingGapJump = false;
            boolean squeezeCorridorTentativeBindingRecorded = false;
            Set<Integer> squeezeCorridorEncounteredLayoutIndices = new LinkedHashSet<>();
            Set<Integer> squeezeCorridorClearedLayoutIndices = new LinkedHashSet<>();
            Map<Integer, Integer> squeezeCorridorMinimumGaps = new LinkedHashMap<>();
            Map<Integer, Integer> squeezeCorridorRollEntrySpeeds = new LinkedHashMap<>();
            Map<Integer, String> squeezeCorridorSupportEvidence = new LinkedHashMap<>();
            String squeezeCorridorLastCandidateEvidence = "none";
            List<String> squeezeCorridorAbortEvidence = new java.util.ArrayList<>();
            boolean squeezeCorridorRecoveryActive = false;
            int squeezeCorridorRecoveryFrontierX = -1;
            int squeezeCorridorRecoveryHoldX = -1;
            int squeezeCorridorRecoveryFrames = 0;
            int lowCeilingRetreatFrames = 0;
            BlasterProjectileObjectInstance lowCeilingProjectile = null;
            IdentityHashMap<BlasterProjectileObjectInstance, Integer> projectileLastX =
                    new IdentityHashMap<>();
            FbzScrewDoorObjectInstance blockingScrewDoor = null;
            FbzScrewDoorObjectInstance lateButtonDoor = null;
            Sonic3kButtonObjectInstance lateButton = null;
            int lateCorridorStage = 0;
            int lateCorridorHoldFrames = 0;
            boolean lateCorridorRollObserved = false;
            FbzScrewDoorObjectInstance linkedDoor = null;
            Sonic3kButtonObjectInstance linkedButton = null;
            int linkedDoorStage = 0;
            int linkedDoorFrames = 0;
            Set<Integer> linkedDoorsPassed = new LinkedHashSet<>();
            boolean squeezeApproachBrake = false;
            int landingGapWaitFrames = 0;
            boolean squeezeChargedWaitingForCar = false;
            int squeezeChargedWaitFrames = 0;
            Sonic3kInvisibleBlockObjectInstance squeezeAheadBlock = null;
            int lateCorridorChargeTaps = 0;
            boolean lateButtonControllerSeen = false;
            FbzScrewDoorObjectInstance trigger0Door = null;
            Sonic3kButtonObjectInstance trigger0Button = null;
            FbzScrewDoorObjectInstance trigger7Door = null;
            Sonic3kButtonObjectInstance trigger7Button = null;
            FbzFlamethrowerObjectInstance trigger7Flamethrower = null;

            List<InputRun> boundedControllerRuns = new java.util.ArrayList<>(runs);
            // The source program is cadence evidence, not the completion
            // oracle. If it expires after the real door/hazard stages, keep
            // this same stateful controller alive under ordinary RIGHT only.
            // The fallback is bounded below the observed time-over frontier;
            // the real SOZ request still terminates it immediately.
            boundedControllerRuns.add(new InputRun(
                    TAIL_ORDINARY_RIGHT_FALLBACK_LIMIT,
                    AbstractPlayableSprite.INPUT_RIGHT));
            for (InputRun run : boundedControllerRuns) {
                for (int i = 0; i < run.frames(); i++) {
                    lifetime.beginFrame(objects);
                    AbstractPlayableSprite player = fixture.sprite();
                    if (completed && !magneticPlatformRideMode && squeezeCorridorTarget == null
                            && lowerCraneApproachReached(player)) {
                        // The source program and its RIGHT fallback end beside
                        // the $26C8 crane; hand the live route to the crane
                        // controller before this frame is observed or stepped.
                        lowerCraneApproachReached = true;
                        return false;
                    }
                    observer.observe(lifetime.active(), lifetime.previous(), player, objects);

                    int currentPlayerY = player.getCentreY() & 0xFFFF;
                    descendingCar = objects.activeObjectsOfType(
                                            FbzElevatorObjectInstance.Car.class).stream()
                                    .filter(car -> car.getCentreX() == targetX)
                                    .filter(car -> car.yVelocity() == 1)
                                    // The controller emits a car every $60
                                    // frames. Select the nearest live support
                                    // below P1, not allocation/slot order.
                                    .filter(car -> car.getCentreY() >= currentPlayerY)
                                    .min(Comparator.comparingInt(
                                            car -> car.getCentreY() - currentPlayerY))
                                    .orElse(null);
                    FbzScrewDoorObjectInstance descendingDoor =
                            objects.activeObjectsOfType(
                                            FbzScrewDoorObjectInstance.class).stream()
                                    .filter(door -> !door.isDestroyed())
                                    .filter(door -> door.getSpawn().x() == 0x0B68)
                                    .filter(door -> door.getSpawn().y() == 0x08DE)
                                    .filter(door -> door.getSpawn().subtype() == 0x12)
                                    .filter(door -> door.triggerIndex() == 2)
                                    .findFirst().orElse(null);
                    Sonic3kButtonObjectInstance descendingDoorButton =
                            objects.activeObjectsOfType(
                                            Sonic3kButtonObjectInstance.class).stream()
                                    .filter(button -> !button.isDestroyed())
                                    .filter(button -> button.getSpawn().x() == 0x0B38)
                                    .filter(button -> button.getSpawn().y() == 0x08FA)
                                    .filter(button -> button.getSpawn().subtype() == 0x22)
                                    .findFirst().orElse(null);
                    descendingButtonLive = descendingDoorButton != null;
                    descendingDoorOpened = descendingDoor != null
                            && descendingDoor.getY() <= 0x089E;
                    boolean descendingDoorLatched = descendingDoor != null
                            && descendingDoor.getY() < descendingDoor.getSpawn().y();
                    if (retainedCar == null && !completed) {
                        Object owner = player.getLatchedSolidObjectInstance();
                        int playerX = player.getCentreX() & 0xFFFF;
                        if (descendingDoorLatched && player.isOnObject()
                                && owner instanceof FbzElevatorObjectInstance.Car car
                                && car.getCentreX() == targetX
                                && car.yVelocity() == 1
                                && playerX >= safeMinX && playerX <= safeMaxX) {
                            retainedCar = car;
                            carryStartY = player.getCentreY() & 0xFFFF;
                            carCarryStartY = car.getCentreY();
                        }
                    }

                    int playerXBefore = player.getCentreX() & 0xFFFF;
                    int playerYBefore = player.getCentreY() & 0xFFFF;
                    // $1300 is the authored post-door path-switch threshold.
                    // Latch it from P1's world position before resolving the
                    // later $16D8/$1718 button-door pair; waiting for the
                    // generic fallback branch below can leave this false while
                    // source cadence has already carried P1 behind the door.
                    if (playerXBefore >= postDoorPathSwitchX) {
                        postDoorPathSwitchReached = true;
                    }
                    boolean descendingDoorClearsPlayer = descendingDoor != null
                            && descendingDoor.getY()
                            + descendingDoor.getSolidParams().airHalfHeight()
                            <= playerYBefore - player.getYRadius();
                    if (!risingCarRideCompleted && !risingCarExitStarted
                            && risingCar == null
                            && player.isOnObject()
                            && player.getLatchedSolidObjectInstance()
                            instanceof FbzElevatorObjectInstance.Car car
                            && car.getCentreX() == 0x06C0 && car.yVelocity() == -1) {
                        risingCar = car;
                        risingCarAcquisitionArmed = false;
                    }
                    boolean spindashEnabled = player.getGameRules() != null
                            && player.getGameRules().playerCapability() != null
                            && player.getGameRules().playerCapability().spindashEnabled();
                    boolean injectLowerPathJump = naturallyReleased
                            && !lowerPathJumpStarted && !player.getAir()
                            && playerXBefore <= leftJumpGateX;
                    boolean injectOrdinaryRoll = lowerPathLandingReached
                            && !spindashEnabled
                            && !ordinaryRollRequested && !player.getAir()
                            && player.getXSpeed() < 0;
                    if (lowerPathLandingReached && spindashEnabled && !spindashReleased
                            && !player.getAir() && playerXBefore <= spindashBrakeStartX) {
                        spindashApproachArmed = true;
                    }
                    boolean spindashChargePressed = false;
                    boolean spindashReleasedThisFrame = false;
                    boolean exactRisingCarActive = lifetime.active().stream().anyMatch(object ->
                            object instanceof FbzElevatorObjectInstance.Car car
                                    && car.getCentreX() == 0x06C0
                                    && car.yVelocity() == -1);
                    boolean acquireRisingCar = !risingCarRideCompleted
                            && !risingCarExitStarted && risingCar == null
                            && playerXBefore <= risingCarApproachMaxX
                            && playerXBefore >= 0x0660
                            && exactRisingCarActive;
                    risingCarAcquisitionArmed |= acquireRisingCar;
                    if (completed && risingCarRideCompleted
                            && postLauncherRouteStage == 0 && !player.getAir()
                            && playerYBefore == flamethrowerLandingY
                            && playerXBefore >= 0x0780 && playerXBefore <= 0x0920) {
                        postLauncherRouteStage = 1;
                    }
                    if (postLauncherRouteStage >= 10 && !screwDoorRecoveryCompleted
                            && !screwDoorRecoveryArmed && !player.getAir()) {
                        blockingScrewDoor = objects.activeObjectsOfType(
                                        FbzScrewDoorObjectInstance.class).stream()
                                .filter(door -> door.getY() == 0x04C8)
                                .filter(door -> playerXBefore >= door.getX() - 0x60)
                                .filter(door -> playerXBefore <= door.getX())
                                .findFirst().orElse(null);
                        if (blockingScrewDoor != null) {
                            screwDoorRecoveryArmed = true;
                        }
                    }
                    if (trigger0DoorStage < 4) {
                        List<Sonic3kButtonObjectInstance> liveButtons =
                                objects.activeObjectsOfType(
                                                Sonic3kButtonObjectInstance.class).stream()
                                        .filter(button -> !button.isDestroyed())
                                        .filter(button -> button.getSpawn().x() == 0x0748)
                                        .filter(button -> button.getSpawn().y() == 0x09FA)
                                        .filter(button -> button.getSpawn().subtype() == 0x20)
                                        .toList();
                        assertTrue(liveButtons.size() <= 1,
                                "duplicate live placed $0748 subtype-$20 buttons");
                        trigger0Button = liveButtons.isEmpty() ? null : liveButtons.getFirst();

                        List<FbzScrewDoorObjectInstance> liveDoors =
                                objects.activeObjectsOfType(
                                                FbzScrewDoorObjectInstance.class).stream()
                                        .filter(door -> !door.isDestroyed())
                                        .filter(door -> door.getSpawn().x() == 0x0718)
                                        .filter(door -> door.getSpawn().y() == 0x09DE)
                                        .filter(door -> door.getSpawn().subtype() == 0x10)
                                        .filter(door -> door.triggerIndex() == 0)
                                        .toList();
                        assertTrue(liveDoors.size() <= 1,
                                "duplicate live placed $0718 subtype-$10 doors");
                        trigger0Door = liveDoors.isEmpty() ? null : liveDoors.getFirst();
                        if (trigger0DoorStage > 0) {
                            assertEquals(1, liveButtons.size(),
                                    "trigger-0 controller lost its live placed button");
                            assertEquals(1, liveDoors.size(),
                                    "trigger-0 controller lost its live linked door");
                        }
                    }
                    if (postDoorPathSwitchReached && lateButtonDoorStage < 4) {
                        // Both objects are ordinary cullable placements. Resolve
                        // the current live instances from immutable placement
                        // identity every frame; retaining a destroyed door here
                        // would observe its frozen pre-opening Y after respawn.
                        List<Sonic3kButtonObjectInstance> liveButtons =
                                objects.activeObjectsOfType(
                                                Sonic3kButtonObjectInstance.class).stream()
                                        .filter(button -> !button.isDestroyed())
                                        .filter(button -> button.getSpawn().x() == 0x16D8)
                                        .filter(button -> button.getSpawn().y() == 0x05FA)
                                        .filter(button -> button.getSpawn().subtype() == 0x24)
                                        .toList();
                        assertTrue(liveButtons.size() <= 1,
                                "duplicate live placed $16D8 subtype-$24 buttons");
                        lateButton = liveButtons.isEmpty() ? null : liveButtons.getFirst();

                        List<FbzScrewDoorObjectInstance> liveDoors =
                                objects.activeObjectsOfType(
                                                FbzScrewDoorObjectInstance.class).stream()
                                        .filter(door -> !door.isDestroyed())
                                        .filter(door -> door.getSpawn().x() == 0x1718)
                                        .filter(door -> door.getSpawn().y() == 0x05DE)
                                        .filter(door -> door.getSpawn().subtype() == 0x14)
                                        .filter(door -> door.triggerIndex() == 4)
                                        .toList();
                        assertTrue(liveDoors.size() <= 1,
                                "duplicate live placed $1718 subtype-$14 doors");
                        lateButtonDoor = liveDoors.isEmpty() ? null : liveDoors.getFirst();
                        if (!lateButtonControllerSeen && lateButton != null
                                && lateButtonDoor != null) {
                            lateButtonControllerSeen = true;
                            int firstBlockingLeftEdge = lateButtonDoor.getX()
                                    - lateButtonDoor.getSolidParams().halfWidth()
                                    - player.getXRadius();
                            assertTrue(playerXBefore <= firstBlockingLeftEdge,
                                    () -> waypointDiagnostic(
                                            "late-button-first-seen-behind-door",
                                            firstBlockingLeftEdge));
                        }
                        if (lateButtonDoorStage > 0 && lateButtonDoorStage < 3) {
                            assertEquals(1, liveButtons.size(),
                                    "button-stage controller lost its live placed button");
                            assertEquals(1, liveDoors.size(),
                                    "button-stage controller lost its live linked door");
                        }
                    }
                    if (postDoorPathSwitchReached && trigger7DoorStage < 4) {
                        List<Sonic3kButtonObjectInstance> liveButtons =
                                objects.activeObjectsOfType(
                                                Sonic3kButtonObjectInstance.class).stream()
                                        .filter(button -> !button.isDestroyed())
                                        .filter(button -> button.getSpawn().x() == 0x1B28)
                                        .filter(button -> button.getSpawn().y() == 0x05F1)
                                        .filter(button -> button.getSpawn().subtype() == 0x27)
                                        .toList();
                        assertTrue(liveButtons.size() <= 1,
                                "duplicate live placed $1B28 subtype-$27 buttons");
                        trigger7Button = liveButtons.isEmpty() ? null : liveButtons.getFirst();

                        List<FbzScrewDoorObjectInstance> liveDoors =
                                objects.activeObjectsOfType(
                                                FbzScrewDoorObjectInstance.class).stream()
                                        .filter(door -> !door.isDestroyed())
                                        .filter(door -> door.getSpawn().x() == 0x1AC0)
                                        .filter(door -> door.getSpawn().y() == 0x0648)
                                        .filter(door -> door.getSpawn().subtype() == 0x57)
                                        .filter(door -> door.triggerIndex() == 7)
                                        .toList();
                        assertTrue(liveDoors.size() <= 1,
                                "duplicate live placed $1AC0 subtype-$57 doors");
                        trigger7Door = liveDoors.isEmpty() ? null : liveDoors.getFirst();

                        List<FbzFlamethrowerObjectInstance> liveFlamethrowers =
                                objects.activeObjectsOfType(
                                                FbzFlamethrowerObjectInstance.class).stream()
                                        .filter(flame -> !flame.isDestroyed())
                                        .filter(flame -> flame.getSpawn().x() == 0x1B28)
                                        .filter(flame -> flame.getSpawn().y() == 0x05F8)
                                        .filter(flame -> flame.getSpawn().subtype() == 0x02)
                                        .toList();
                        assertTrue(liveFlamethrowers.size() <= 1,
                                "duplicate live placed $1B28 subtype-$02 flamethrowers");
                        trigger7Flamethrower = liveFlamethrowers.isEmpty()
                                ? null : liveFlamethrowers.getFirst();
                        if (trigger7DoorStage > 0 && trigger7DoorStage < 3) {
                            assertEquals(1, liveButtons.size(),
                                    "trigger-7 controller lost its live placed button");
                            assertEquals(1, liveDoors.size(),
                                    "trigger-7 controller lost its live linked door");
                            assertEquals(1, liveFlamethrowers.size(),
                                    "trigger-7 controller lost its overlapping flamethrower");
                        }
                    }
                    int playerTouchRadiusY = Math.max(1, player.getYRadius() - 3);
                    int playerTouchTop = playerYBefore - playerTouchRadiusY;
                    int playerTouchBottom = playerYBefore + playerTouchRadiusY;
                    int playerTouchLeft = playerXBefore - PLAYER_TOUCH_HALF_WIDTH;
                    int playerTouchRight = playerXBefore + PLAYER_TOUCH_HALF_WIDTH;
                    if (magneticPlatformHazardPlacement == null && !player.getAir()
                            && !player.isObjectControlled() && !player.isOnObject()) {
                        int brakingLookahead = RouteSteering.ordinaryBrakeDistancePixels(player) + 0x10;
                        // The immutable layout position is the earliest state
                        // available before S3K Load_Sprites materializes Obj74.
                        // Its initial touch box is conservative: crossing is
                        // forbidden until the bound live object has moved above
                        // P1's exact touch top.
                        magneticPlatformHazardPlacement = objects.getAllSpawns().stream()
                                .filter(spawn -> spawn.layoutIndex() >= 0)
                                .filter(spawn -> spawn.objectId()
                                        == Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM)
                                .filter(spawn -> spawn.x()
                                        - MAGNETIC_PLATFORM_TOUCH_RADIUS_X >= playerTouchRight)
                                .filter(spawn -> spawn.x()
                                        - MAGNETIC_PLATFORM_TOUCH_RADIUS_X - playerTouchRight
                                        <= brakingLookahead)
                                .filter(spawn -> spawn.y()
                                        + MAGNETIC_PLATFORM_TOUCH_RADIUS_Y >= playerTouchTop)
                                .filter(spawn -> spawn.y()
                                        - MAGNETIC_PLATFORM_TOUCH_RADIUS_Y <= playerTouchBottom)
                                .min(Comparator.comparingInt(spawn -> spawn.x()
                                        - MAGNETIC_PLATFORM_TOUCH_RADIUS_X - playerTouchRight))
                                .orElse(null);
                        if (magneticPlatformHazardPlacement != null) {
                            int rings = GameServices.level().getLevelGamestate().getRings();
                            if (!magneticPlatformHazardArmed) {
                                magneticPlatformSafetyRings = rings;
                            }
                            magneticPlatformHazardArmed = true;
                            magneticPlatformEncounteredLayoutIndices.add(
                                    magneticPlatformHazardPlacement.layoutIndex());
                            magneticPlatformCurrentVerticalClearanceObserved = false;
                            magneticPlatformHazardWaitFrames = 0;
                            magneticPlatformPreviousLiveYFixed = Long.MIN_VALUE;
                            magneticPlatformCrossingCommitted = false;
                            magneticPlatformCrossingBudget = 0;
                            magneticPlatformCrossingFrames = 0;
                            magneticPlatformHazardRingsAtArm = rings;
                        }
                    }
                    if (magneticPlatformHazardPlacement != null
                            && magneticPlatformHazardTarget == null) {
                        // Obj74 rewrites getSpawn() as it moves. Bind by the
                        // immutable layout ordinal carried through that rewrite,
                        // never by value-equality with the authored placement.
                        int targetLayoutIndex = magneticPlatformHazardPlacement.layoutIndex();
                        magneticPlatformHazardTarget = objects.activeObjectsOfType(
                                        FbzMagneticPlatformObjectInstance.class).stream()
                                .filter(platform -> !platform.isDestroyed())
                                .filter(platform -> platform.getSpawn().layoutIndex()
                                        == targetLayoutIndex)
                                .findFirst().orElse(null);
                        if (magneticPlatformHazardTarget != null) {
                            magneticPlatformLiveBindings++;
                            assertEquals(0x0D,
                                    magneticPlatformHazardTarget.getCollisionFlags() & 0x3F,
                                    "live Obj74 hazard changed Touch_Sizes index");
                        }
                    }
                    boolean linkedDoorLatched = linkedDoor != null
                            && linkedDoor.getY() < linkedDoor.getSpawn().y();
                    if (squeezeCorridorTarget == null && !squeezeCorridorRecoveryActive
                            && !player.getAir()
                            && (linkedDoorStage == 0 || linkedDoorLatched)) {
                        // A live normal Obj28 ahead over this floor is a squeeze
                        // corridor whose car may not be live yet. Never walk in
                        // on the ordinary fallback: hold at the geometry fence
                        // (the abort hold) until an episode binds.
                        Sonic3kInvisibleBlockObjectInstance aheadBlock =
                                objects.activeObjectsOfType(
                                                Sonic3kInvisibleBlockObjectInstance.class).stream()
                                        .filter(block -> FbzMovingSqueezeTraversal
                                                .isLiveNormalInvisibleSolid(block, player))
                                        .filter(block -> !squeezeCorridorClearedLayoutIndices
                                                .contains(block.getSpawn().layoutIndex()))
                                        .filter(block -> squeezeBlockLeft(block)
                                                > playerRightEdge(player))
                                        .filter(block -> squeezeBlockLeft(block)
                                                - playerRightEdge(player)
                                                <= SQUEEZE_PROACTIVE_LOOKAHEAD)
                                        .filter(block -> block.getY() < playerYBefore)
                                        .filter(block -> playerYBefore + player.getYRadius()
                                                - (block.getY() + block.getSolidParams()
                                                .airHalfHeight()) <= 0x60)
                                        .min(Comparator.comparingInt(block ->
                                                squeezeBlockLeft(block)))
                                        .orElse(null);
                        squeezeAheadBlock = aheadBlock;
                        squeezeApproachBrake = aheadBlock != null
                                && player.getGSpeed() > SQUEEZE_APPROACH_SPEED_CAP
                                && squeezeRecoveryStoppingRightEdge(player)
                                >= squeezeBlockLeft(aheadBlock) - SQUEEZE_APPROACH_MARGIN;
                        if (aheadBlock == null) squeezeAheadBlock = null;
                        boolean doorBeforeBlock = aheadBlock != null
                                && findLinkedBlockingDoor(objects, player, playerXBefore,
                                        playerYBefore, linkedDoorsPassed) != null;
                        if (aheadBlock != null && !squeezeApproachBrake && !doorBeforeBlock) {
                            if (linkedDoorStage > 0) {
                                // The latched door is crossed by the squeeze
                                // launch itself; retire the door controller.
                                linkedDoorsPassed.add(linkedDoor.getSpawn().layoutIndex());
                                linkedDoorStage = 0;
                                linkedDoor = null;
                                linkedButton = null;
                            }
                            squeezeCorridorRecoveryActive = true;
                            squeezeCorridorRecoveryFrontierX = squeezeBlockLeft(aheadBlock);
                            squeezeCorridorRecoveryHoldX = squeezeCorridorRecoveryFrontierX
                                    - (player.getStandXRadius() & 0xFFFF) - 0x08;
                            squeezeCorridorRecoveryFrames = 0;
                        }
                    }
                    if (squeezeAheadBlock != null
                            && (squeezeAheadBlock.isDestroyed() || squeezeCorridorStage > 0
                            || squeezeCorridorClearedLayoutIndices.contains(
                                    squeezeAheadBlock.getSpawn().layoutIndex())
                            || playerRightEdge(player) >= squeezeBlockLeft(squeezeAheadBlock))) {
                        squeezeAheadBlock = null;
                        squeezeApproachBrake = false;
                    } else if (squeezeAheadBlock != null && !player.getAir()) {
                        // Bang-bang speed limit on the approach: the recovery
                        // hold steers RIGHT toward its fence, so re-evaluate the
                        // ordinary stopping edge every grounded frame.
                        squeezeApproachBrake = player.getGSpeed() > SQUEEZE_APPROACH_SPEED_CAP
                                && squeezeRecoveryStoppingRightEdge(player)
                                >= squeezeBlockLeft(squeezeAheadBlock) - SQUEEZE_APPROACH_MARGIN;
                    }
                    if (squeezeCorridorTarget == null) {
                        var squeezeEpisode =
                                squeezeCorridorRecoveryActive
                                        ? findSqueezeRecoveryEpisode(objects, player,
                                                squeezeCorridorRecoveryFrontierX)
                                        : FbzMovingSqueezeTraversal.findEpisode(objects, player);
                        if (squeezeEpisode.isPresent()) {
                            FbzMovingSqueezeTraversal.Episode episode =
                                    squeezeEpisode.orElseThrow();
                            FbzMovingSqueezeTraversal.Projection nativeProjection =
                                    FbzMovingSqueezeTraversal.project(episode, player,
                                            FbzMovingSqueezeTraversal.NATIVE_RELEASE_SPEED);
                            int episodeBlockLeft = squeezeBlockLeft(episode.block());
                            boolean recoveryBindingValid = !squeezeCorridorRecoveryActive
                                    || (hasSqueezeLaunchControl(player)
                                    && episodeBlockLeft == squeezeCorridorRecoveryFrontierX
                                    && FbzMovingSqueezeTraversal.beforeLaunchFrontier(
                                            episode, player)
                                    && nativeProjection.clears());
                            if (!recoveryBindingValid) {
                                squeezeCorridorLastCandidateEvidence =
                                        "recovery-rejected-blockLeft=" + episodeBlockLeft
                                                + ",frontier="
                                                + squeezeCorridorRecoveryFrontierX
                                                + ",ordinaryTerrain="
                                                + hasSqueezeLaunchControl(player)
                                                + ",beforeFrontier="
                                                + FbzMovingSqueezeTraversal
                                                .beforeLaunchFrontier(episode, player)
                                                + ",nativeProjection=" + nativeProjection;
                                squeezeEpisode = java.util.Optional.empty();
                            }
                        }
                        if (squeezeEpisode.isPresent()) {
                            FbzMovingSqueezeTraversal.Episode episode =
                                    squeezeEpisode.orElseThrow();
                            FbzMovingSqueezeTraversal.Projection nativeProjection =
                                    FbzMovingSqueezeTraversal.project(episode, player,
                                            FbzMovingSqueezeTraversal.NATIVE_RELEASE_SPEED);
                            squeezeCorridorRecoveryActive = false;
                            squeezeCorridorRecoveryFrontierX = -1;
                            squeezeCorridorRecoveryHoldX = -1;
                            squeezeCorridorRecoveryFrames = 0;
                            squeezeCorridorTarget = episode.block();
                            squeezeCorridorSupport = episode.car();
                            squeezeCorridorBlockSlot = episode.block().getSlotIndex();
                            squeezeCorridorSupportSlot = episode.car().getSlotIndex();
                            squeezeCorridorCarBeforeBlock = episode.carBeforeBlock();
                            squeezeCorridorSafetyArmed = true;
                            squeezeCorridorRollRequested = false;
                            squeezeCorridorStage = 0;
                            squeezeCorridorSpindashCapable = player.getGameRules() != null
                                    && player.getGameRules().playerCapability() != null
                                    && player.getGameRules().playerCapability().spindashEnabled();
                            squeezeCorridorLaunchSpeed = 0;
                            squeezeCorridorAssistConsumed = false;
                            squeezeCorridorEntered = false;
                            squeezeCorridorSupportAcquired = false;
                            squeezeCorridorSupportExited = false;
                            squeezeCorridorS1RollFrames = 0;
                            squeezeCorridorS1WaitFrames = 0;
                            squeezeCorridorLiveBindings++;
                            squeezeCorridorTentativeBindingRecorded = true;
                            int layoutIndex = episode.block().getSpawn().layoutIndex();
                            squeezeCorridorEncounteredLayoutIndices.add(layoutIndex);
                            squeezeCorridorMinimumGaps.put(layoutIndex,
                                    nativeProjection.minimumGap());
                            squeezeCorridorSupportEvidence.put(layoutIndex,
                                    "blockSlot=" + episode.block().getSlotIndex()
                                            + ",carSlot=" + episode.car().getSlotIndex()
                                            + ",order=" + (episode.carBeforeBlock()
                                            ? "car-first" : "block-first")
                                            + ",nativeProjection=" + nativeProjection);
                            squeezeCorridorLastCandidateEvidence = "layout=" + layoutIndex
                                    + ",state=" + ordinaryRideControlEvidence(player)
                                    + ",launchAuthority="
                                    + squeezeLaunchAuthorityEvidence(
                                            player, episode.car())
                                    + ",nativeProjection=" + nativeProjection;
                            if (squeezeCorridorSafetyRings < 0) {
                                squeezeCorridorSafetyRings =
                                        GameServices.level().getLevelGamestate().getRings();
                            }
                        }
                    }
                    // Stages 6 and 7 are the S1 profile's pre-roll wait and
                    // run-up: still tentative, so the bound car may expire and
                    // rebind exactly as during a native charged hold.
                    if (squeezeCorridorTarget != null
                            && (squeezeCorridorStage < 4 || squeezeCorridorStage >= 6)) {
                        FbzMovingSqueezeTraversal.Episode tentativeEpisode =
                                new FbzMovingSqueezeTraversal.Episode(
                                        squeezeCorridorTarget,
                                        squeezeCorridorSupport);
                        boolean launchAuthorityLost =
                                !FbzMovingSqueezeTraversal
                                        .hasLaunchFloorAuthority(player);
                        boolean episodeActive = FbzMovingSqueezeTraversal.isActive(
                                tentativeEpisode, player);
                        squeezeChargedWaitingForCar = false;
                        if (!launchAuthorityLost && !episodeActive
                                && (squeezeCorridorStage == 3 || squeezeCorridorStage == 6)) {
                            // Sonic_Spindash cannot be cancelled: dropping DOWN
                            // launches. The bound car expired while charged, so
                            // rebind the next live upward car of this same Obj28
                            // or keep the charge held until one appears.
                            Sonic3kInvisibleBlockObjectInstance chargedBlock =
                                    squeezeCorridorTarget;
                            FbzElevatorObjectInstance.Car nextCar = objects
                                    .activeObjectsOfType(FbzElevatorObjectInstance.Car.class)
                                    .stream()
                                    .filter(car -> FbzMovingSqueezeTraversal.isActive(
                                            new FbzMovingSqueezeTraversal.Episode(
                                                    chargedBlock, car), player))
                                    .min(Comparator.comparingInt(car ->
                                            Math.abs(car.getCentreY() - playerYBefore)))
                                    .orElse(null);
                            if (nextCar != null) {
                                squeezeCorridorSupport = nextCar;
                                squeezeCorridorSupportSlot = nextCar.getSlotIndex();
                                episodeActive = true;
                            } else {
                                squeezeChargedWaitingForCar = true;
                                squeezeChargedWaitFrames++;
                                int chargedWait = squeezeChargedWaitFrames;
                                int chargedFrontier = squeezeBlockLeft(chargedBlock);
                                assertTrue(chargedWait <= SQUEEZE_CHARGED_WAIT_LIMIT,
                                        () -> waypointDiagnostic(
                                                "obj28-charged-wait-for-car-limit",
                                                chargedFrontier)
                                                + " chargedWait=" + chargedWait);
                            }
                        }
                        if (!squeezeChargedWaitingForCar
                                && (launchAuthorityLost || !episodeActive)) {
                            int abortedLayout = squeezeCorridorTarget
                                    .getSpawn().layoutIndex();
                            int abortedFrontierX =
                                    squeezeBlockLeft(squeezeCorridorTarget);
                            int abortedHoldX = abortedFrontierX
                                    - (player.getStandXRadius() & 0xFFFF) - 0x08;
                            int recoveryStoppingEdge =
                                    squeezeRecoveryStoppingRightEdge(player);
                            assertTrue(playerRightEdge(player) < abortedFrontierX,
                                    "Obj28 authority abort crossed its geometry fence");
                            assertTrue(recoveryStoppingEdge < abortedFrontierX,
                                    "Obj28 authority abort cannot stop before its geometry fence"
                                            + " stoppingEdge=" + recoveryStoppingEdge
                                            + " frontier=" + abortedFrontierX);
                            String abortEvidence =
                                    "aborted-prelaunch-layout=" + abortedLayout
                                            + ",playerOnObject="
                                            + player.isOnObject()
                                            + ",playerAir=" + player.getAir()
                                            + ",stage=" + squeezeCorridorStage
                                            + ",launchAuthorityLost="
                                            + launchAuthorityLost
                                            + ",episodeActive=" + episodeActive
                                            + ",latchedSame="
                                            + (player.getLatchedSolidObjectInstance()
                                            == squeezeCorridorSupport)
                                            + ",carTimer="
                                            + squeezeCorridorSupport.travelTimer()
                                            + ",frontier=" + abortedFrontierX
                                            + ",hold=" + abortedHoldX
                                            + ",stoppingEdge=" + recoveryStoppingEdge;
                            abortEvidence += ",authority="
                                    + squeezeLaunchAuthorityEvidence(
                                            player, squeezeCorridorSupport);
                            squeezeCorridorAbortEvidence.add(abortEvidence);
                            squeezeCorridorLastCandidateEvidence = abortEvidence;
                            assertFalse(squeezeCorridorClearedLayoutIndices
                                            .contains(abortedLayout),
                                    "Obj28 prelaunch abort targeted completed evidence");
                            assertTrue(squeezeCorridorTentativeBindingRecorded,
                                    "Obj28 abort lacked a tentative binding");
                            assertTrue(squeezeCorridorLiveBindings > 0,
                                    "Obj28 tentative binding count underflow");
                            squeezeCorridorEncounteredLayoutIndices
                                    .remove(abortedLayout);
                            squeezeCorridorMinimumGaps.remove(abortedLayout);
                            squeezeCorridorRollEntrySpeeds.remove(abortedLayout);
                            squeezeCorridorSupportEvidence.remove(abortedLayout);
                            squeezeCorridorLiveBindings--;
                            squeezeCorridorTentativeBindingRecorded = false;
                            squeezeCorridorSafetyArmed = false;
                            squeezeCorridorSafetyRings = -1;
                            squeezeCorridorTarget = null;
                            squeezeCorridorSupport = null;
                            squeezeCorridorBlockSlot = -1;
                            squeezeCorridorSupportSlot = -1;
                            squeezeCorridorCarBeforeBlock = false;
                            squeezeCorridorRollRequested = false;
                            squeezeCorridorStage = 0;
                            squeezeCorridorSpindashCapable = false;
                            squeezeCorridorLaunchSpeed = 0;
                            squeezeCorridorAssistConsumed = false;
                            squeezeCorridorEntered = false;
                            squeezeCorridorSupportAcquired = false;
                            squeezeCorridorSupportExited = false;
                            squeezeCorridorRecoveryActive = true;
                            squeezeCorridorRecoveryFrontierX = abortedFrontierX;
                            squeezeCorridorRecoveryHoldX = abortedHoldX;
                            squeezeCorridorRecoveryFrames = 0;
                        }
                    }
                    boolean magneticPlatformHazardControllerActive =
                            magneticPlatformHazardPlacement != null;
                    boolean squeezeCorridorControllerActive =
                            squeezeCorridorTarget != null;
                    int mask;
                    String maskOwner = "main-route";
                    boolean trigger0ControllerActive = trigger0Button != null
                            && trigger0Door != null && trigger0DoorStage < 4
                            && (trigger0DoorStage > 0
                            || (playerXBefore >= 0x0700 && playerXBefore <= 0x0780
                            && playerYBefore >= 0x09B0 && playerYBefore <= 0x0A20));
                    if (retainedCar != null) descendingDoorStage = 4;
                    boolean descendingDoorControllerActive = descendingDoorStage < 4
                            && descendingDoor != null
                            && (descendingDoorStage >= 2 || descendingDoorButton != null)
                            && (descendingDoorStage > 0
                            || (playerXBefore >= 0x0B00 && playerXBefore <= 0x0B80
                            && playerYBefore >= 0x08B0 && playerYBefore <= 0x0930));
                    descendingControllerActiveLast = descendingDoorControllerActive;
                    int descendingCarDeltaY = Integer.MIN_VALUE;
                    int descendingCarLandingMinDeltaY = Integer.MAX_VALUE;
                    int descendingCarLandingMaxDeltaY = Integer.MIN_VALUE;
                    if (descendingCar != null) {
                        int halfWidth = descendingCar.getSolidParams().halfWidth();
                        int sampleIndex = (preEgressPlayerX - descendingCar.getCentreX()
                                + halfWidth) >>> 1;
                        int slopeSample = descendingCar.getSlopeData()[sampleIndex] & 0xFF;
                        int slopeOffset = slopeSample - descendingCar.getSlopeBaseline();
                        descendingCarDeltaY = descendingCar.getCentreY() - playerYBefore;
                        // SolidObjectTopSloped2's sampled relY must remain in
                        // [0, groundHalfHeight+yRadius]. Resolve that interval
                        // at the intended $0BC4 landing X, where byte_3CAD0 is
                        // $1A and its $10 baseline supplies a $0A offset.
                        descendingCarLandingMinDeltaY = slopeOffset + 4;
                        descendingCarLandingMaxDeltaY = descendingCarLandingMinDeltaY
                                + descendingCar.getSolidParams().groundHalfHeight()
                                + player.getYRadius();
                    }
                    boolean acquireDescendingCar = retainedCar == null && !completed
                            && descendingDoorLatched && descendingCar != null
                            && !player.getAir()
                            && playerXBefore >= 0x0B40 && playerXBefore <= 0x0C20
                            && playerYBefore >= 0x0850 && playerYBefore <= 0x0900
                            && descendingCarDeltaY >= descendingCarLandingMinDeltaY
                            && descendingCarDeltaY <= descendingCarLandingMaxDeltaY;
                    if (magneticPlatformHazardControllerActive && !magneticPlatformRideMode
                            && !magneticPlatformCrossingCommitted) {
                        // Live geometry decides the traversal kind: a placed
                        // spike blocking the flat approach at P1's own level
                        // makes the underpass impossible, so the route must
                        // jump onto the resting platform and ride it.
                        Sonic3kSpikeObjectInstance blocking =
                                magneticPlatformRideBlockingSpike(objects, player,
                                        playerXBefore, playerYBefore,
                                        magneticPlatformHazardPlacement.x());
                        if (blocking != null) {
                            magneticPlatformRideMode = true;
                            magneticPlatformRideStage = 0;
                            magneticPlatformRideFrames = 0;
                            magneticPlatformRideAttempts = 0;
                            magneticPlatformRideJumpStarted = false;
                            magneticPlatformRideHopTarget = null;
                            magneticPlatformRideBlockingSpike = blocking;
                        }
                    }
                    if (magneticPlatformHazardControllerActive && magneticPlatformRideMode) {
                        FbzMagneticPlatformObjectInstance target =
                                magneticPlatformHazardTarget;
                        magneticPlatformRideFrames++;
                        int rideFrames = magneticPlatformRideFrames;
                        int rideStage = magneticPlatformRideStage;
                        int ridePlacementX = magneticPlatformHazardPlacement.x();
                        assertTrue(rideFrames <= MAGNETIC_PLATFORM_RIDE_LIMIT,
                                () -> waypointDiagnostic("obj74-ride-frame-limit",
                                        ridePlacementX)
                                        + " stage=" + rideStage
                                        + " target=" + objectPosition(target));
                        if (target == null) {
                            maskOwner = "obj74-ride-await-live";
                            mask = 0;
                        } else {
                            assertFalse(target.isDestroyed(),
                                    "live Obj74 ride target was destroyed mid-episode");
                            FbzZoneRuntimeState rideRuntime =
                                    GameServices.zoneRuntimeRegistry()
                                            .currentAs(FbzZoneRuntimeState.class)
                                            .orElseThrow();
                            int rideRunway = 0xFF - rideRuntime.magneticTimerPhase();
                            boolean rideActive = rideRuntime.magneticPolarity()
                                    == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
                            boolean standingOnTarget = player.isOnObject()
                                    && player.getLatchedSolidObjectInstance() == target;
                            boolean targetResting = target.displacement() == 0
                                    && !rideActive;
                            boolean targetRaised =
                                    target.displacement() == target.maximumRise();
                            int landingX = target.getX()
                                    - MAGNETIC_PLATFORM_RIDE_LANDING_OFFSET;
                            switch (magneticPlatformRideStage) {
                                case 0 -> {
                                    // Grounded hold at the blocking spike until
                                    // the platform rests on its floor with a
                                    // full jump of INACTIVE runway left.
                                    maskOwner = "obj74-ride-hold";
                                    if (!player.getAir() && !player.isOnObject()
                                            && targetResting
                                            && rideRunway > MAGNETIC_PLATFORM_RIDE_MIN_RUNWAY) {
                                        magneticPlatformRideAttempts++;
                                        int attempts = magneticPlatformRideAttempts;
                                        assertTrue(attempts <= MAGNETIC_PLATFORM_RIDE_ATTEMPT_LIMIT,
                                                () -> waypointDiagnostic(
                                                        "obj74-ride-approach-attempts", landingX)
                                                        + " attempts=" + attempts);
                                        magneticPlatformRideStage = 1;
                                        mask = AbstractPlayableSprite.INPUT_JUMP
                                                | AbstractPlayableSprite.INPUT_RIGHT;
                                    } else {
                                        mask = player.getGSpeed() > 0x80
                                                ? AbstractPlayableSprite.INPUT_LEFT : 0;
                                    }
                                }
                                case 1 -> {
                                    // Airborne approach onto the resting platform.
                                    maskOwner = "obj74-ride-approach";
                                    if (standingOnTarget) {
                                        magneticPlatformRideStage = 2;
                                        magneticPlatformCurrentVerticalClearanceObserved = true;
                                        mask = 0;
                                    } else if (player.getAir()) {
                                        mask = (player.getYSpeed() < 0
                                                ? AbstractPlayableSprite.INPUT_JUMP : 0)
                                                | RouteSteering.steerMask(player, landingX, 2);
                                    } else {
                                        assertFalse(player.isOnObject(),
                                                () -> waypointDiagnostic(
                                                        "obj74-ride-approach-landed-elsewhere",
                                                        landingX));
                                        magneticPlatformRideStage = 0;
                                        mask = 0;
                                    }
                                }
                                case 2 -> {
                                    // Ride: stay centred through the rise, then
                                    // hop to the next risen column or exit right.
                                    maskOwner = "obj74-ride";
                                    if (!standingOnTarget) {
                                        // loc_3B3C0 drops the platform at $58/frame,
                                        // faster than P1 gravity; P1 separates and
                                        // re-lands on the resting platform.
                                        assertTrue(player.getAir(),
                                                () -> waypointDiagnostic(
                                                        "obj74-ride-lost-support", landingX));
                                        mask = RouteSteering.steerMask(player, landingX, 2);
                                    } else {
                                        // Hops between columns happen at rest
                                        // level: the raised columns share their
                                        // height with the horizontal Obj72 chain
                                        // links, which would grab an airborne P1.
                                        // Only the last column is ridden to its
                                        // top for the exit onto the far floor.
                                        ObjectSpawn nextPlacement =
                                                nextMagneticPlatformPlacement(objects, target);
                                        FbzMagneticPlatformObjectInstance next =
                                                nextPlacement == null ? null
                                                        : liveMagneticPlatform(objects,
                                                                nextPlacement.layoutIndex());
                                        boolean runwayOk =
                                                rideRunway > MAGNETIC_PLATFORM_RIDE_MIN_RUNWAY;
                                        if (nextPlacement == null && targetRaised
                                                && rideActive && runwayOk) {
                                            magneticPlatformRideStage = 4;
                                            magneticPlatformRideJumpStarted = false;
                                            magneticPlatformRideSettled = false;
                                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                                        } else if (next != null && targetResting
                                                && next.displacement() == 0
                                                && next.getY() == target.getY() && runwayOk) {
                                            magneticPlatformRideHopTarget = next;
                                            magneticPlatformRideStage = 3;
                                            magneticPlatformRideJumpStarted = false;
                                            magneticPlatformRideSettled = false;
                                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                                        } else {
                                            mask = RouteSteering.steerMask(player, target.getX(), 3);
                                        }
                                    }
                                }
                                case 3, 4 -> {
                                    FbzMagneticPlatformObjectInstance hop =
                                            magneticPlatformRideHopTarget;
                                    boolean hopping = magneticPlatformRideStage == 3;
                                    maskOwner = hopping ? "obj74-ride-hop" : "obj74-ride-exit";
                                    if (!magneticPlatformRideJumpStarted) {
                                        assertTrue(standingOnTarget,
                                                () -> waypointDiagnostic(
                                                        "obj74-ride-run-up-lost-support",
                                                        target.getX()));
                                        if (!magneticPlatformRideSettled) {
                                            // Brake the landing inertia at the
                                            // column centre so every launch
                                            // leaves from the same short run-up.
                                            magneticPlatformRideSettled =
                                                    Math.abs(player.getGSpeed()) <= 0x40
                                                    && Math.abs(playerXBefore - target.getX()) <= 2;
                                            mask = magneticPlatformRideSettled
                                                    ? AbstractPlayableSprite.INPUT_RIGHT
                                                    : RouteSteering.steerMask(player, target.getX(), 2);
                                        } else if (playerXBefore >= target.getX()
                                                + MAGNETIC_PLATFORM_RIDE_LAUNCH_OFFSET) {
                                            magneticPlatformRideJumpStarted = true;
                                            mask = AbstractPlayableSprite.INPUT_JUMP
                                                    | AbstractPlayableSprite.INPUT_RIGHT;
                                        } else {
                                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                                        }
                                    } else if (hopping && player.isOnObject()
                                            && player.getLatchedSolidObjectInstance() == hop) {
                                        // Landed on the next column: the previous
                                        // one is cleared and the new one bound.
                                        magneticPlatformClearedLayoutIndices.add(
                                                magneticPlatformHazardPlacement.layoutIndex());
                                        magneticPlatformHazardClearances++;
                                        magneticPlatformHazardCleared = true;
                                        magneticPlatformHazardPlacement = hop.getSpawn();
                                        magneticPlatformHazardTarget = hop;
                                        magneticPlatformEncounteredLayoutIndices.add(
                                                hop.getSpawn().layoutIndex());
                                        magneticPlatformLiveBindings++;
                                        assertEquals(0x0D, hop.getCollisionFlags() & 0x3F,
                                                "live Obj74 hop target changed Touch_Sizes index");
                                        magneticPlatformRideHopTarget = null;
                                        magneticPlatformRideJumpStarted = false;
                                        magneticPlatformRideStage = 2;
                                        mask = 0;
                                    } else if (player.getAir()) {
                                        int jumpHold = player.getYSpeed() < 0
                                                ? AbstractPlayableSprite.INPUT_JUMP : 0;
                                        mask = jumpHold | (hopping
                                                ? RouteSteering.steerMask(player, hop.getX()
                                                        - MAGNETIC_PLATFORM_RIDE_LANDING_OFFSET, 2)
                                                : AbstractPlayableSprite.INPUT_RIGHT);
                                    } else {
                                        assertFalse(hopping,
                                                () -> waypointDiagnostic("obj74-ride-hop-missed",
                                                        hop.getX()));
                                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                                    }
                                }
                                default -> throw new IllegalStateException(
                                        "Obj74 ride stage " + magneticPlatformRideStage);
                            }
                        }
                    } else if (magneticPlatformHazardControllerActive) {
                        FbzMagneticPlatformObjectInstance target =
                                magneticPlatformHazardTarget;
                        if (target != null) {
                            assertFalse(target.isDestroyed(),
                                    "live Obj74 target was destroyed before underpass clearance");
                            assertTrue(target.getCollisionFlags() != 0,
                                    "live Obj74 target disabled collision before underpass clearance");
                        }
                        int objectX = target != null
                                ? target.getX() : magneticPlatformHazardPlacement.x();
                        int objectY = target != null
                                ? target.getY() : magneticPlatformHazardPlacement.y();
                        int objectTouchLeft = objectX
                                - MAGNETIC_PLATFORM_TOUCH_RADIUS_X;
                        int objectTouchRight = objectX
                                + MAGNETIC_PLATFORM_TOUCH_RADIUS_X;
                        int objectTouchBottom = objectY
                                + MAGNETIC_PLATFORM_TOUCH_RADIUS_Y;
                        boolean verticalTouchOverlap = objectTouchBottom >= playerTouchTop
                                && objectY - MAGNETIC_PLATFORM_TOUCH_RADIUS_Y
                                <= playerTouchBottom;
                        long objectYFixed = target != null
                                ? magneticPlatformYFixed(target) : Long.MIN_VALUE;
                        boolean upwardMotionObserved = target != null
                                && magneticPlatformPreviousLiveYFixed != Long.MIN_VALUE
                                && objectYFixed < magneticPlatformPreviousLiveYFixed;
                        if (target != null) {
                            magneticPlatformPreviousLiveYFixed = objectYFixed;
                        }
                        FbzZoneRuntimeState magneticRuntime =
                                GameServices.zoneRuntimeRegistry()
                                        .currentAs(FbzZoneRuntimeState.class)
                                        .orElseThrow();
                        int magneticRunway = 0xFF - magneticRuntime.magneticTimerPhase();
                        boolean activePolarity = magneticRuntime.magneticPolarity()
                                == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
                        boolean ordinaryGroundControl =
                                hasOrdinaryFlatGroundControl(player);
                        // The runway must also outlast the CPU sidekicks that
                        // trail P1 under the column: a follower still beneath
                        // it when polarity drops is crushed onto the Obj6B
                        // hurt block placed under every Obj74 column.
                        int proposedCrossingBudget = Math.max(
                                ordinaryRightCrossingBudget(
                                        player, objectTouchRight - playerTouchLeft + 1),
                                sidekickFollowAllowance(objectTouchRight));
                        boolean raisedClearance = !verticalTouchOverlap
                                && objectTouchBottom < playerTouchTop;
                        boolean canCommitCrossing = target != null
                                && upwardMotionObserved
                                && raisedClearance
                                && activePolarity
                                && target.lastMagneticActive()
                                && ordinaryGroundControl
                                && magneticRunway > proposedCrossingBudget;
                        if (!magneticPlatformCrossingCommitted && canCommitCrossing) {
                            magneticPlatformCrossingCommitted = true;
                            magneticPlatformCrossingBudget = proposedCrossingBudget;
                            magneticPlatformCrossingFrames = 0;
                            magneticPlatformVerticalClearanceObserved = true;
                            magneticPlatformCurrentVerticalClearanceObserved = true;
                        }
                        magneticPlatformHazardWaitFrames++;
                        assertTrue(magneticPlatformHazardWaitFrames
                                        <= MAGNETIC_PLATFORM_WAIT_LIMIT,
                                () -> waypointDiagnostic(
                                        "obj74-live-geometry-wait-limit", objectX)
                                        + " target=" + objectPosition(target));
                        maskOwner = "obj74-live-geometry-wait";
                        if (!magneticPlatformCrossingCommitted) {
                            assertTrue(playerTouchRight < objectTouchLeft,
                                    () -> waypointDiagnostic(
                                            "obj74-overlap-crossed-whole-player-left",
                                            objectTouchLeft)
                                            + " target=" + objectPosition(target));
                            int safeHoldCentre = objectTouchLeft
                                    - PLAYER_TOUCH_HALF_WIDTH - 0x08;
                            mask = player.getGSpeed() > 0
                                    ? AbstractPlayableSprite.INPUT_LEFT
                                    : RouteSteering.steerMask(player, safeHoldCentre, 2);
                        } else {
                            int remainingBudget = magneticPlatformCrossingBudget
                                    - magneticPlatformCrossingFrames;
                            assertTrue(ordinaryGroundControl,
                                    "Obj74 crossing lost ordinary grounded control");
                            assertTrue(activePolarity && target.lastMagneticActive(),
                                    "Obj74 crossing lost active sampled polarity");
                            assertTrue(raisedClearance,
                                    "Obj74 crossing lost live bottom clearance");
                            assertTrue(magneticRunway > remainingBudget,
                                    () -> waypointDiagnostic(
                                            "obj74-crossing-runway-exhausted", objectX)
                                            + " runway=" + magneticRunway
                                            + " remainingBudget=" + remainingBudget);
                            maskOwner = "obj74-live-crossing";
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    } else if (squeezeApproachBrake && squeezeCorridorStage == 0
                            && !squeezeCorridorRollRequested) {
                        // An Obj28 squeeze lies within the ordinary braking
                        // distance ahead. Shed the run speed before any episode
                        // binds so the launch fence stays reachable.
                        maskOwner = "obj28-approach-brake";
                        mask = player.getGSpeed() > SQUEEZE_APPROACH_SPEED_CAP
                                ? AbstractPlayableSprite.INPUT_LEFT : 0;
                    } else if (squeezeChargedWaitingForCar) {
                        maskOwner = "obj28-native-charged-wait-for-car";
                        mask = AbstractPlayableSprite.INPUT_DOWN;
                    } else if (squeezeCorridorRecoveryActive) {
                        assertNull(squeezeCorridorTarget,
                                "Obj28 recovery retained tentative block authority");
                        assertNull(squeezeCorridorSupport,
                                "Obj28 recovery retained tentative car authority");
                        assertFalse(squeezeCorridorTentativeBindingRecorded,
                                "Obj28 recovery retained tentative binding evidence");
                        assertTrue(squeezeCorridorRecoveryFrontierX >= 0
                                        && squeezeCorridorRecoveryHoldX >= 0,
                                "Obj28 recovery lost its geometry-only safety fence");
                        squeezeCorridorRecoveryFrames++;
                        assertTrue(squeezeCorridorRecoveryFrames
                                        <= SQUEEZE_CORRIDOR_RECOVERY_LIMIT,
                                waypointDiagnostic(
                                        "obj28-terrain-recovery-timeout",
                                        squeezeCorridorRecoveryFrontierX)
                                        + " recoveryFrames="
                                        + squeezeCorridorRecoveryFrames
                                        + " evidence="
                                        + squeezeCorridorLastCandidateEvidence);
                        int recoveryRightEdge = playerRightEdge(player);
                        int recoveryStoppingEdge =
                                squeezeRecoveryStoppingRightEdge(player);
                        assertTrue(recoveryRightEdge < squeezeCorridorRecoveryFrontierX,
                                "Obj28 recovery crossed its geometry fence");
                        assertTrue(recoveryStoppingEdge
                                        < squeezeCorridorRecoveryFrontierX,
                                waypointDiagnostic(
                                        "obj28-recovery-cannot-stop-before-frontier",
                                        squeezeCorridorRecoveryFrontierX)
                                        + " stoppingEdge=" + recoveryStoppingEdge
                                        + " hold=" + squeezeCorridorRecoveryHoldX);
                        boolean ordinaryTerrain =
                                hasSqueezeLaunchControl(player);
                        if (!ordinaryTerrain) {
                            maskOwner = "obj28-recovery-nonterrain-brake";
                            mask = Math.max(player.getGSpeed(), player.getXSpeed()) > 0
                                    ? AbstractPlayableSprite.INPUT_LEFT : 0;
                        } else {
                            maskOwner = "obj28-recovery-terrain-hold";
                            mask = RouteSteering.steerMask(player,
                                    squeezeCorridorRecoveryHoldX, 2);
                            if (mask == 0 && player.getGSpeed() == 0
                                    && recoveryStoppingEdge + 1
                                    < squeezeCorridorRecoveryFrontierX) {
                                // A single ordinary RIGHT sample gives the
                                // fresh episode finder positive ground motion;
                                // it cannot itself cross the retained fence.
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        }
                    } else if (squeezeCorridorControllerActive) {
                        Sonic3kInvisibleBlockObjectInstance target = squeezeCorridorTarget;
                        FbzMovingSqueezeTraversal.Episode episode =
                                new FbzMovingSqueezeTraversal.Episode(
                                        target, squeezeCorridorSupport);
                        String squeezeAuthorityEvidence =
                                "normal Obj28 squeeze target lost live solid authority"
                                        + " frame=" + frames
                                        + " player=($"
                                        + Integer.toHexString(player.getCentreX() & 0xFFFF)
                                        + ",$"
                                        + Integer.toHexString(player.getCentreY() & 0xFFFF)
                                        + ") speed=($"
                                        + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                                        + ",$"
                                        + Integer.toHexString(player.getYSpeed() & 0xFFFF)
                                        + ") g=$"
                                        + Integer.toHexString(player.getGSpeed() & 0xFFFF)
                                        + " air=" + player.getAir()
                                        + " gap="
                                        + FbzMovingSqueezeTraversal.currentGap(
                                                episode, player)
                                        + " playerOnObject=" + player.isOnObject()
                                        + " latchedSame="
                                        + (player.getLatchedSolidObjectInstance()
                                        == squeezeCorridorSupport)
                                        + " blockDestroyed=" + target.isDestroyed()
                                        + " blockSkip="
                                        + target.isSkipSolidContactThisFrame()
                                        + " blockSolid=" + target.isSolidFor(player)
                                        + " carDestroyed="
                                        + squeezeCorridorSupport.isDestroyed()
                                        + " carYVel="
                                        + squeezeCorridorSupport.yVelocity()
                                        + " carTimer="
                                        + squeezeCorridorSupport.travelTimer()
                                        + " evidence="
                                        + squeezeCorridorLastCandidateEvidence;
                        assertTrue(FbzMovingSqueezeTraversal.isActive(episode, player),
                                squeezeAuthorityEvidence);
                        assertEquals(squeezeCorridorBlockSlot, target.getSlotIndex(),
                                "Obj28 squeeze target changed SST slot");
                        assertNotNull(squeezeCorridorSupport,
                                "Obj28 squeeze lost its retained support identity");
                        assertEquals(squeezeCorridorSupportSlot,
                                squeezeCorridorSupport.getSlotIndex(),
                                "Obj28 squeeze support changed SST slot");
                        assertEquals(squeezeCorridorCarBeforeBlock,
                                squeezeCorridorSupportSlot < squeezeCorridorBlockSlot,
                                "Obj28/Car SST execution order changed");
                        int layoutIndex = target.getSpawn().layoutIndex();
                        int blockLeft = target.getX()
                                + target.getSolidParams().offsetX()
                                - target.getSolidParams().halfWidth();
                        int stagingX = blockLeft
                                - (player.getStandXRadius() & 0xFFFF) - 0x08;
                        if (!squeezeCorridorSpindashCapable && squeezeCorridorStage == 6) {
                            // S1 profile: no charge to hold, so stand at the
                            // staging spot until the native-speed projection
                            // clears and no other car of the column spans the
                            // rolling height (the same gate the native release
                            // uses), then take the ordinary run-up into the roll.
                            maskOwner = "obj28-s1-wait-for-clear-car";
                            int rollCentreY = playerYBefore + (player.getStandYRadius() & 0xFFFF)
                                    - (player.getRollYRadius() & 0xFFFF);
                            int rollTop = rollCentreY - (player.getRollYRadius() & 0xFFFF);
                            int rollBottom = rollCentreY + (player.getRollYRadius() & 0xFFFF);
                            FbzElevatorObjectInstance.Car boundSupport = squeezeCorridorSupport;
                            boolean otherCarBlocksRoll = objects.activeObjectsOfType(
                                            FbzElevatorObjectInstance.Car.class).stream()
                                    .filter(car -> car != boundSupport
                                            && car.getCentreX() == boundSupport.getCentreX())
                                    .anyMatch(car -> car.getCentreY()
                                            + car.getSolidParams().groundHalfHeight()
                                            + SQUEEZE_OTHER_CAR_SLOPE_MARGIN >= rollTop
                                            && car.getCentreY()
                                            - car.getSolidParams().airHalfHeight()
                                            - SQUEEZE_OTHER_CAR_SLOPE_MARGIN <= rollBottom);
                            // FbzS1DonationSqueezeAssist only fires for the pair
                            // FbzMovingSqueezeTraversal.findEpisode selects: a
                            // native-speed projection that clears with a real
                            // danger edge ahead of P1, unique among the column's
                            // cars. Wait for exactly that car and bind it.
                            Sonic3kInvisibleBlockObjectInstance s1Block = target;
                            List<FbzElevatorObjectInstance.Car> genuineCandidates =
                                    objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)
                                            .stream()
                                            .filter(car -> car.getCentreX() == boundSupport.getCentreX())
                                            .filter(car -> {
                                                FbzMovingSqueezeTraversal.Projection candidate =
                                                        FbzMovingSqueezeTraversal.project(
                                                                new FbzMovingSqueezeTraversal.Episode(
                                                                        s1Block, car),
                                                                player,
                                                                FbzMovingSqueezeTraversal.NATIVE_RELEASE_SPEED);
                                                if (!candidate.clears()) return false;
                                                if (candidate.dangerEdge() > playerXBefore) return true;
                                                // The finder also accepts a pair whose
                                                // ordinary-speed roll would be crushed;
                                                // evaluate it at the roll-start speed.
                                                return FbzMovingSqueezeTraversal.project(
                                                        new FbzMovingSqueezeTraversal.Episode(s1Block, car),
                                                        player, SQUEEZE_S1_ROLL_START_SPEED)
                                                        .crushOverlapObserved();
                                            })
                                            .toList();
                            squeezeCorridorS1WaitFrames++;
                            int s1WaitFrames = squeezeCorridorS1WaitFrames;
                            assertTrue(s1WaitFrames <= SQUEEZE_S1_WAIT_LIMIT,
                                    () -> waypointDiagnostic("obj28-s1-no-genuine-squeeze", blockLeft)
                                            + " waitFrames=" + s1WaitFrames
                                            + " candidates=" + genuineCandidates.size()
                                            + " otherCarBlocks=" + otherCarBlocksRoll);
                            if (genuineCandidates.size() == 1 && !otherCarBlocksRoll
                                    && !player.getAir()) {
                                FbzElevatorObjectInstance.Car genuine = genuineCandidates.get(0);
                                if (genuine != squeezeCorridorSupport) {
                                    squeezeCorridorSupport = genuine;
                                    squeezeCorridorSupportSlot = genuine.getSlotIndex();
                                    squeezeCorridorCarBeforeBlock =
                                            squeezeCorridorSupportSlot < squeezeCorridorBlockSlot;
                                }
                                squeezeCorridorStage = 7;
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else {
                                mask = RouteSteering.steerMask(player, stagingX - SQUEEZE_S1_RUN_UP, 2);
                            }
                        } else if (!squeezeCorridorSpindashCapable && squeezeCorridorStage == 7) {
                            // Sonic_Roll needs |ground_vel| >= $80 and neither
                            // LEFT nor RIGHT held: run up, then DOWN alone starts
                            // the roll; RIGHT+DOWN while rolling is the production
                            // assist's activation input (checked in stage 4).
                            maskOwner = "obj28-s1-ordinary-roll";
                            assertTrue(FbzMovingSqueezeTraversal.beforeLaunchFrontier(
                                            episode, player),
                                    () -> waypointDiagnostic(
                                            "obj28-s1-roll-missed-launch-frontier", blockLeft));
                            if (player.getRolling()) {
                                squeezeCorridorStage = 4;
                                squeezeCorridorRollRequested = true;
                                squeezeCorridorS1RollFrames++;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_DOWN;
                            } else if (player.getGSpeed() >= SQUEEZE_S1_ROLL_START_SPEED) {
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                            } else {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (squeezeCorridorStage == 0) {
                            String terrainControlEvidence =
                                    ordinaryRideControlEvidence(player)
                                            + ",latch=" + objectPosition(
                                            player.getLatchedSolidObjectInstance())
                                            + ",selectedCar=" + objectPosition(
                                            squeezeCorridorSupport)
                                            + ",pair="
                                            + squeezeCorridorLastCandidateEvidence;
                            assertTrue(hasSqueezeLaunchControl(player),
                                    "Obj28 native staging lost ordinary terrain control: "
                                            + terrainControlEvidence);
                            int delta = playerXBefore - stagingX;
                            if (!squeezeCorridorSpindashCapable) {
                                delta = playerXBefore - (stagingX - SQUEEZE_S1_RUN_UP);
                            }
                            if (delta > 2) {
                                mask = AbstractPlayableSprite.INPUT_LEFT;
                            } else if (delta < -2) {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else if (player.getGSpeed() > 0x80) {
                                mask = AbstractPlayableSprite.INPUT_LEFT;
                            } else if (player.getGSpeed() < -0x80) {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else if (squeezeCorridorSpindashCapable) {
                                squeezeCorridorStage = 1;
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                            } else {
                                squeezeCorridorStage = 6;
                                mask = 0;
                            }
                            maskOwner = "obj28-native-stage";
                        } else if (squeezeCorridorStage == 1) {
                            assertFalse(player.getRolling() || player.getSpindash(),
                                    "Obj28 native crouch stage entered roll early");
                            if (player.getCrouching()) {
                                squeezeCorridorStage = 2;
                                mask = AbstractPlayableSprite.INPUT_DOWN
                                        | AbstractPlayableSprite.INPUT_JUMP;
                                maskOwner = "obj28-native-charge";
                            } else {
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                                maskOwner = "obj28-native-crouch";
                            }
                        } else if (squeezeCorridorStage == 2) {
                            assertTrue(player.getSpindash(),
                                    "Obj28 native charge input did not enter spindash");
                            squeezeCorridorStage = 3;
                            maskOwner = "obj28-native-charge-hold";
                            mask = AbstractPlayableSprite.INPUT_DOWN;
                        } else if (squeezeCorridorStage == 3) {
                            assertTrue(player.getSpindash(),
                                    "Obj28 native hold lost spindash state");
                            int releaseSpeed = projectedSpindashReleaseSpeed(player);
                            FbzMovingSqueezeTraversal.Projection projection =
                                    FbzMovingSqueezeTraversal.project(
                                            episode, player, releaseSpeed);
                            // The projection models the bound support car
                            // only. Another car of the same column whose solid
                            // box (ROM SolidObjectTopSloped2: $3B by $10, plus
                            // its slope catch range) spans P1's rolling height
                            // stops the release at its $3B side edge instead,
                            // unrolling P1 on the ledge; hold the charge until
                            // that car has risen clear.
                            int rollTop = playerYBefore - (player.getRollYRadius() & 0xFFFF);
                            int rollBottom = playerYBefore + (player.getRollYRadius() & 0xFFFF);
                            FbzElevatorObjectInstance.Car boundSupport = squeezeCorridorSupport;
                            boolean otherCarBlocksRoll = objects.activeObjectsOfType(
                                            FbzElevatorObjectInstance.Car.class).stream()
                                    .filter(car -> car != boundSupport
                                            && car.getCentreX() == boundSupport.getCentreX())
                                    .anyMatch(car -> car.getCentreY()
                                            + car.getSolidParams().groundHalfHeight()
                                            + SQUEEZE_OTHER_CAR_SLOPE_MARGIN >= rollTop
                                            && car.getCentreY()
                                            - car.getSolidParams().airHalfHeight()
                                            - SQUEEZE_OTHER_CAR_SLOPE_MARGIN <= rollBottom);
                            if (projection.clears() && !otherCarBlocksRoll) {
                                squeezeCorridorStage = 4;
                                squeezeCorridorRollRequested = true;
                                squeezeCorridorLaunchSpeed = releaseSpeed;
                                squeezeCorridorRollEntrySpeeds.put(
                                        layoutIndex, releaseSpeed);
                                squeezeCorridorMinimumGaps.merge(layoutIndex,
                                        projection.minimumGap(), Math::min);
                                maskOwner = "obj28-native-release";
                                mask = 0;
                            } else {
                                maskOwner = "obj28-native-wait-for-release";
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                            }
                        } else {
                            assertSqueezePosture(player, squeezeCorridorSupportAcquired, target);
                            assertFalse(player.getSpindash(), "Obj28 release remained charged");
                            if (playerXBefore >= blockLeft) {
                                squeezeCorridorEntered = true;
                            }
                            if (player.isOnObject()
                                    && player.getLatchedSolidObjectInstance()
                                    == squeezeCorridorSupport) {
                                squeezeCorridorSupportAcquired = true;
                                if (!squeezeCorridorSupportExited) {
                                    assertTrue(FbzMovingSqueezeTraversal.isLiveUpwardCar(
                                                    squeezeCorridorSupport),
                                            "Obj28 crossing lost live upward car support");
                                    int projectedSpeed = Math.max(1, player.getGSpeed());
                                    FbzMovingSqueezeTraversal.Projection projection =
                                            FbzMovingSqueezeTraversal.project(
                                                    episode, player, projectedSpeed);
                                    // This predictor models rolling friction;
                                    // standing after RideObject_SetRide is checked
                                    // against actual clearance by assertSqueezePosture.
                                    assertTrue(!player.getRolling() || projection.clears(),
                                            () -> waypointDiagnostic(
                                                    "obj28-moving-support-revalidation",
                                                    projection.dangerEdge())
                                                    + " projection=" + projection);
                                    squeezeCorridorMinimumGaps.merge(layoutIndex,
                                            projection.minimumGap(), Math::min);
                                }
                            } else if (squeezeCorridorSupportAcquired) {
                                squeezeCorridorSupportExited = true;
                            }
                            maskOwner = squeezeCorridorSpindashCapable
                                    ? "obj28-native-neutral-crossing"
                                    : "obj28-s1-assisted-crossing";
                            if (!squeezeCorridorSpindashCapable && player.getRolling()) {
                                squeezeCorridorS1RollFrames++;
                            }
                            mask = squeezeCorridorSpindashCapable ? 0
                                    : AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_DOWN;
                        }
                    } else if (midpointCarEgressActive) {
                        // Complete-run BK2 $62C4-$6310 walks RIGHT from the
                        // exact $08C0 car after the fixed protected segment.
                        // Retain that ordinary egress until P1 has cleared the
                        // adjacent $0880/$0900 spike pair; donor cadence must
                        // not reinterpret the next source run as LEFT here.
                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                        if (playerXBefore >= 0x0940 || playerYBefore >= 0x0890) {
                            midpointCarEgressActive = false;
                            shaftRegroupActive = true;
                        }
                    } else if (shaftRegroupActive) {
                        // The $08C0 cars keep descending through the shaft
                        // floor every 96 frames, so anything standing inside
                        // the car's $3B half-width there is crushed. A CPU
                        // sidekick chain lags P1 by 17 frames per link
                        // (Tails_CPU_Control loc_13DA6) and a lost member
                        // flies back in to P1's position (loc_13B50), so hold
                        // just right of the column until every sidekick has
                        // either walked clear of it or landed here from its
                        // respawn. Solo rows pass straight through.
                        List<AbstractPlayableSprite> regroupSidekicks =
                                GameServices.sprites().getSidekicks();
                        boolean sidekickInsideShaft = regroupSidekicks.stream()
                                .anyMatch(sidekick -> !sidekick.getDead()
                                        && (SidekickAudit.awaitingRespawn(sidekick)
                                        || ((sidekick.getCentreX() & 0xFFFF)
                                        <= SHAFT_REGROUP_CLEAR_X
                                        && (sidekick.getCentreY() & 0xFFFF)
                                        >= SHAFT_REGROUP_MIN_Y)));
                        shaftRegroupFrames++;
                        if (!sidekickInsideShaft
                                || shaftRegroupFrames > SHAFT_REGROUP_FRAME_LIMIT) {
                            shaftRegroupActive = false;
                            shaftFloorEastActive = true;
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (player.getAir()) {
                            mask = 0;
                        } else {
                            mask = RouteSteering.steerMask(player, SHAFT_REGROUP_HOLD_X, 3);
                        }
                    } else if (shaftFloorEastActive && !descendingDoorControllerActive) {
                        // BK2 rows 25398-25478 run RIGHT from the shaft floor
                        // down the $0A2C slope to the $0B38 button and stop at
                        // the closed $0B68 door. A shifted car phase lets the
                        // authored RIGHT/JUMP cadence carry P1 over the button
                        // airborne into the $0C00 spikes, so cap the run to a
                        // speed the door stage can brake inside its $0B00-$0B80
                        // envelope; the stage then owns the button and door.
                        maskOwner = "shaft-floor-east";
                        if (playerXBefore >= SHAFT_FLOOR_EAST_HANDOFF_X) {
                            shaftFloorEastActive = false;
                        }
                        mask = player.getGSpeed() < SHAFT_FLOOR_EAST_SPEED_CAP
                                ? AbstractPlayableSprite.INPUT_RIGHT : 0;
                    } else if (descendingDoorControllerActive
                            && descendingDoorStage >= 2) {
                        if (!descendingDoorClearsPlayer) {
                            // The button may leave the forward placement window
                            // after the door routine latches. It is no longer an
                            // authority dependency. Walk into the live door only
                            // until its bottom clears P1's top; waiting for its
                            // full $40 travel misses the periodic car below.
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (descendingDoorStage == 3) {
                            // The selected car was inside the sampled intercept
                            // envelope when this stage committed. Preserve the
                            // ordinary crossing input; re-entering the waiting
                            // branch would steer P1 away from the same car.
                            mask = RouteSteering.steerMask(playerXBefore, preEgressPlayerX, 2);
                        } else if (Math.abs(playerXBefore - preAcquisitionPlayerX) > 2) {
                            mask = RouteSteering.steerMask(player, preAcquisitionPlayerX, 2);
                        } else if (acquireDescendingCar) {
                            descendingDoorStage = 3;
                            mask = RouteSteering.steerMask(playerXBefore, preEgressPlayerX, 2);
                        } else {
                            // Hold on the authored floor until the nearest live
                            // downward car enters its actual slope catch band.
                            mask = 0;
                        }
                    } else if (descendingDoorControllerActive) {
                        int buttonX = descendingDoorButton.getX();
                        int surfaceFeetY = descendingDoorButton.getY()
                                - descendingDoorButton.getSolidParams().airHalfHeight();
                        int playerFeetY = playerYBefore + player.getYRadius();
                        boolean withinButtonX = Math.abs(playerXBefore - buttonX)
                                <= descendingDoorButton.getSolidParams().halfWidth();
                        boolean exactButtonFeet = playerFeetY == surfaceFeetY
                                || playerFeetY == surfaceFeetY - 1;
                        boolean exactButtonOwner = !player.getAir()
                                && player.isOnObject()
                                && player.getLatchedSolidObjectInstance()
                                == descendingDoorButton;
                        boolean triggerHeld = Sonic3kLevelTriggerManager.testBit(2, 0);
                        boolean doorOpeningLatched = descendingDoor.getY()
                                < descendingDoor.getSpawn().y();
                        if (descendingDoorStage == 0 && !descendingButtonBrakeReady) {
                            // Shed the incoming source velocity before the
                            // momentary width. This remains ordinary opposite-
                            // direction braking and prevents a many-cycle
                            // projected-steering oscillation around the button.
                            if (Math.abs(player.getGSpeed()) <= 0x80) {
                                descendingButtonBrakeReady = true;
                                mask = RouteSteering.steerMask(player, buttonX, 2);
                            } else {
                                mask = player.getGSpeed() > 0
                                        ? AbstractPlayableSprite.INPUT_LEFT
                                        : AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (descendingDoorStage == 0 && !triggerHeld) {
                            // Brake on projected ordinary motion so the exact
                            // subtype-$22 momentary button is held, rather than
                            // merely crossed for one frame at $0200+ speed.
                            mask = RouteSteering.steerMask(player, buttonX, 2);
                        } else if (descendingDoorStage == 0) {
                            // Obj_Button publishes one shared trigger bit from
                            // the whole standing mask, so a configured sidekick
                            // may hold subtype $22 before P1 arrives. Accept that
                            // shared authority as the late button does; keep P1
                            // steering onto the button so the bit survives if the
                            // sidekick steps off before the door latches.
                            boolean p1HoldsButton = withinButtonX && exactButtonFeet
                                    && exactButtonOwner;
                            if (p1HoldsButton && Math.abs(player.getGSpeed()) > 0x80) {
                                mask = player.getGSpeed() > 0
                                        ? AbstractPlayableSprite.INPUT_LEFT
                                        : AbstractPlayableSprite.INPUT_RIGHT;
                            } else {
                                descendingDoorStage = 1;
                                mask = p1HoldsButton ? 0 : RouteSteering.steerMask(player, buttonX, 2);
                            }
                        } else if (descendingDoorStage == 1 && !doorOpeningLatched) {
                            assertTrue(triggerHeld,
                                    () -> waypointDiagnostic(
                                            "descending-door-released-before-latch", buttonX));
                            mask = exactButtonOwner ? 0 : RouteSteering.steerMask(player, buttonX, 2);
                        } else {
                            descendingDoorStage = 2;
                            // The door routine is now latched independently of
                            // the momentary button. Relinquish it immediately
                            // and walk into the still-solid door; its own live
                            // geometry gates progress until -$40 completes.
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    } else if (acquireDescendingCar) {
                        // Wait over the exact live $0BC0 downward car only
                        // while its top is within the real one-contact vertical
                        // envelope. This is ordinary steering driven by door,
                        // car identity and geometry for every movement profile.
                        mask = RouteSteering.steerMask(playerXBefore, preEgressPlayerX, 2);
                    } else if (retainedCar == null && !completed
                            && (missedDescendingCarSpikeJumpActive
                            || (!player.getAir()
                            && playerXBefore >= 0x0BA0 && playerXBefore <= 0x0C20
                            && playerYBefore >= 0x0A20 && playerYBefore <= 0x0A40
                            && objects.activeObjectsOfType(
                                            Sonic3kSpikeObjectInstance.class).stream()
                                    .anyMatch(spike -> !spike.isDestroyed()
                                            && spike.getSpawn().x() == 0x0C00
                                            && spike.getSpawn().y() == 0x0A18)))) {
                        // If ordinary donor velocity misses the live $0BC0 car,
                        // the authored lower floor remains passable: jump the
                        // exact placed $0C00/$0A18 spike, using no profile or
                        // frame identity. Hold only the normal variable jump.
                        missedDescendingCarSpikeJumpActive = playerXBefore <= 0x0C30;
                        mask = AbstractPlayableSprite.INPUT_RIGHT
                                | (!player.getAir() || player.getYSpeed() < 0
                                ? AbstractPlayableSprite.INPUT_JUMP : 0);
                    } else if (trigger0ControllerActive) {
                        int buttonX = trigger0Button.getX();
                        int maintainedRideFeetY = trigger0Button.getY()
                                - trigger0Button.getSolidParams().airHalfHeight();
                        int playerFeetY = playerYBefore + player.getYRadius();
                        boolean withinButtonX = Math.abs(playerXBefore - buttonX)
                                <= trigger0Button.getSolidParams().halfWidth();
                        boolean exactButtonFeet = playerFeetY == maintainedRideFeetY
                                || playerFeetY == maintainedRideFeetY - 1;
                        boolean exactButtonOwner = !player.getAir()
                                && player.isOnObject()
                                && player.getLatchedSolidObjectInstance() == trigger0Button;
                        boolean triggerHeld = Sonic3kLevelTriggerManager.testBit(0, 0);
                        int trigger0DoorX = trigger0Door.getX();
                        boolean doorOpeningLatched = trigger0Door.getY()
                                < trigger0Door.getSpawn().y();
                        int doorRightEdge = trigger0Door.getX()
                                + trigger0Door.nativeWidth() + 0x0B;
                        int doorBottom = trigger0Door.getY()
                                + trigger0Door.nativeHeight() + 1;
                        boolean doorStillOverlapsPlayer = playerXBefore <= doorRightEdge
                                && doorBottom >= playerYBefore - player.getYRadius();

                        if (trigger0DoorStage == 0
                                && !(withinButtonX && exactButtonFeet && exactButtonOwner)) {
                            // Approach direction may differ after an earlier
                            // geometry wait. Brake/steer relative to the exact
                            // live top-solid subtype-$20 button; no jump or
                            // injected contact asserts trigger byte 0.
                            mask = RouteSteering.steerMask(player, buttonX, 2);
                        } else if (trigger0DoorStage == 0) {
                            assertTrue(withinButtonX && exactButtonFeet && exactButtonOwner,
                                    () -> waypointDiagnostic(
                                            "trigger-0-without-native-button-top", buttonX));
                            assertTrue(triggerHeld, () -> waypointDiagnostic(
                                    "trigger-0-owner-without-trigger", buttonX));
                            trigger0DoorStage = 1;
                            // BK2 $675A jumps LEFT immediately after the button
                            // has asserted the linked momentary trigger.
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (trigger0DoorStage == 1 && !doorOpeningLatched) {
                            assertTrue(triggerHeld && withinButtonX
                                            && exactButtonFeet && exactButtonOwner,
                                    () -> waypointDiagnostic(
                                            "trigger-0-released-before-door-latched", buttonX));
                            mask = 0;
                        } else if (trigger0DoorStage == 1) {
                            assertTrue(doorOpeningLatched,
                                    () -> waypointDiagnostic(
                                            "trigger-0-door-did-not-latch", trigger0DoorX));
                            trigger0DoorStage = 2;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | (player.getAir() && player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        } else if (trigger0DoorStage == 2
                                && (player.getAir() || playerXBefore > doorRightEdge)) {
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | (player.getAir() && player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        } else if (trigger0DoorStage == 2) {
                            trigger0DoorStage = 3;
                            // At $072B/$09EC P1 remains against the same live
                            // door while its half-speed vertical opening clears
                            // the overlap. Neutral is the native wait.
                            mask = 0;
                        } else if (player.getPushing() || doorStillOverlapsPlayer) {
                            assertTrue(doorOpeningLatched,
                                    () -> waypointDiagnostic(
                                            "trigger-0-door-lost-opening-latch", trigger0DoorX));
                            mask = 0;
                        } else {
                            trigger0DoorStage = 4;
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    } else if (risingCarExitStarted) {
                        // Once the exact physical corridor owner is active it
                        // preempts every generic route stage. This route loops
                        // back through lower world X after later stages have
                        // latched, so stage ordering cannot own the collision.
                        if (!risingCarExitJumpPressed && !player.getAir()
                                && playerXBefore >= risingCarJumpX) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                            risingCarExitJumpPressed = true;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (player.getAir() && player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        }
                    } else if (risingCar != null && playerYBefore <= risingCarExitY) {
                        risingCar = null;
                        risingCarExitStarted = true;
                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                    } else if (risingCar != null) {
                        mask = RouteSteering.steerMask(player, risingCarTargetX, 2);
                    } else if (risingCarAcquisitionArmed) {
                        mask = RouteSteering.steerMask(player, risingCarTargetX, 2);
                    } else if (completed && risingCarRideCompleted
                            && postLauncherRouteStage == 0 && springTowerStage < 3
                            && playerXBefore >= springTowerMinX
                            && playerXBefore <= springTowerMaxX
                            && playerYBefore <= springTowerMaxY) {
                        // Complete-run BK2 rows 27102-27432: after the rising
                        // car exit P1 walks RIGHT to the $0795 wall over the
                        // $0790 Obj3D retracting spring and waits neutral. The
                        // spring's 128-frame cycle (four extend frames, 60
                        // hold, four retract, 60 hold) launches P1 at $F000
                        // whenever it next extends, a second spring at
                        // $06BB relaunches, and a short LEFT from the apex
                        // (rows 27352-27376) drifts P1 onto the $078A vertical
                        // launcher that lands at $086C/$04EC. Wide viewports
                        // reach the wall in a different spring phase, so the
                        // wait is gated on the live launch, not on cadence.
                        if (springTowerStage == 0) {
                            if (player.getAir() && player.getYSpeed() <= springLaunchSpeed) {
                                springTowerStage = 1;
                                mask = 0;
                            } else if (!player.getAir()) {
                                mask = RouteSteering.steerMask(player, springWaitX, 2);
                            } else {
                                mask = 0;
                            }
                        } else if (springTowerStage == 1) {
                            if (player.getAir() && playerYBefore < springApexDriftMaxY
                                    && player.getYSpeed() >= 0) {
                                springTowerStage = 2;
                                mask = AbstractPlayableSprite.INPUT_LEFT;
                            } else {
                                mask = 0;
                            }
                        } else if (!player.getAir() || playerXBefore <= springLauncherX) {
                            springTowerStage = 3;
                            mask = 0;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    } else if (postLauncherRouteStage == 1) {
                        // BK2 $6B30 lands from the vertical launcher at
                        // $086C/$04EC, already to the right of the live $0800
                        // flamethrower, then holds RIGHT to $08C8.
                        if (playerXBefore < flamethrowerJumpX) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (player.getGSpeed() > flamethrowerJumpMaxSpeed) {
                            // Donation profiles can retain more horizontal
                            // speed at this landing. Brake with ordinary input
                            // to the BK2's exact $02E8 jump-edge speed before
                            // committing to the live badnik corridor.
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        } else {
                            postLauncherRouteStage = 2;
                            flamethrowerJumpHoldFrames++;
                            mask = AbstractPlayableSprite.INPUT_JUMP;
                        }
                    } else if (postLauncherRouteStage == 2) {
                        // BK2 $6B71-$6B75: exactly five neutral JUMP frames.
                        if (flamethrowerJumpHoldFrames < 5) {
                            flamethrowerJumpHoldFrames++;
                            mask = AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            postLauncherRouteStage = 3;
                            flamethrowerLeftJumpHoldFrames++;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        }
                    } else if (postLauncherRouteStage == 3) {
                        // BK2 $6B76-$6B77: two LEFT+JUMP frames.
                        if (flamethrowerLeftJumpHoldFrames < 2) {
                            flamethrowerLeftJumpHoldFrames++;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            postLauncherRouteStage = 4;
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    } else if (postLauncherRouteStage == 4) {
                        // BK2 changes back to RIGHT at $6B8C/$08FD, just before
                        // the landing. Do not extend LEFT to a donor-dependent
                        // grounded frame beside the live TechnoSqueek family.
                        if (postLauncherRecoveryComplete(
                                player.getAir(), playerXBefore, launcherRecoveryClearX)) {
                            postLauncherRouteStage = 5;
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    } else if (postLauncherRouteStage == 5) {
                        // BK2 $6B8C-$6BC8 runs to the crane approach waypoint.
                        TechnoSqueekBadnikInstance nextSqueek = objects.activeObjectsOfType(
                                        TechnoSqueekBadnikInstance.class).stream()
                                .filter(squeek -> squeek.getX() >= playerXBefore)
                                .filter(squeek -> squeek.getX() - playerXBefore <= 0x48)
                                .filter(squeek -> Math.abs(squeek.getY() - playerYBefore) <= 0x40)
                                .min(java.util.Comparator.comparingInt(
                                        TechnoSqueekBadnikInstance::getX))
                                .orElse(null);
                        mask = AbstractPlayableSprite.INPUT_RIGHT
                                | (nextSqueek != null && !player.getAir()
                                ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        if (playerXBefore >= 0x0994) postLauncherRouteStage = 6;
                    } else if (postLauncherRouteStage == 6) {
                        // BK2 $6BC9-$6C06 coasts to rest at $09FD.
                        mask = 0;
                        if (!player.getAir() && player.getGSpeed() == 0) {
                            postLauncherRouteStage = 7;
                        }
                    } else if (postLauncherRouteStage == 7) {
                        // BK2 $6C07-$6C79 returns to the real $09C8 spider
                        // crane, then waits at its capture coordinate.
                        mask = playerXBefore > spiderCraneX
                                ? AbstractPlayableSprite.INPUT_LEFT : 0;
                        if (retainedSpiderCrane == null) {
                            retainedSpiderCrane = objects.activeObjectsOfType(
                                            FbzSpiderCraneObjectInstance.class).stream()
                                    .filter(candidate -> candidate.getX() == spiderCraneX)
                                    .findFirst().orElse(null);
                        }
                        if (retainedSpiderCrane != null
                                && (retainedSpiderCrane.stateName().equals("CAPTURE")
                                || retainedSpiderCrane.stateName().equals("RETRACT")
                                || retainedSpiderCrane.stateName().equals("TRAVEL"))) {
                            postLauncherRouteStage = 8;
                        }
                    } else if (postLauncherRouteStage == 8) {
                        // Control is ROM-locked while the real crane retracts
                        // and travels. The input is intentionally neutral.
                        mask = 0;
                        if (retainedSpiderCrane != null
                                && retainedSpiderCrane.stateName().equals("INERT")) {
                            postLauncherRouteStage = 9;
                            // The release already occurred in the prior object
                            // update. Resume the BK2's RIGHT input immediately
                            // instead of leaving P1 idle among the live mines.
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    } else if (postLauncherRouteStage == 9) {
                        // The BK2 holds RIGHT after the crane release with the
                        // Obj73 balls already lifted. Rows on another global
                        // phase reach the $0C20-$0D60 balls while they rest on
                        // the floor or are falling: gate the corridor on live
                        // polarity runway covering P1 and every trailing CPU
                        // sidekick, with every ball ahead risen clear.
                        List<FbzMagneticSpikeBallObjectInstance> ballsAhead =
                                objects.activeObjectsOfType(
                                                FbzMagneticSpikeBallObjectInstance.class).stream()
                                        .filter(ball -> ball.kind()
                                                == FbzMagneticSpikeBallObjectInstance.Kind.BALL)
                                        .filter(ball -> ball.getX() + SPIKE_BALL_TOUCH_HALF_WIDTH
                                                >= playerXBefore - PLAYER_TOUCH_HALF_WIDTH)
                                        .filter(ball -> ball.getX() <= magneticCorridorExitX)
                                        .sorted(java.util.Comparator.comparingInt(
                                                FbzMagneticSpikeBallObjectInstance::getX))
                                        .toList();
                        FbzZoneRuntimeState ballRuntime = GameServices.zoneRuntimeRegistry()
                                .currentAs(FbzZoneRuntimeState.class).orElseThrow();
                        boolean ballPolarityActive = ballRuntime.magneticPolarity()
                                == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
                        int ballRunway = 0xFF - ballRuntime.magneticTimerPhase();
                        boolean ballsRaised = ballsAhead.stream().allMatch(ball ->
                                ball.rising() && ball.getY() <= SPIKE_BALL_CORRIDOR_SAFE_Y);
                        int lastBallTouchRight = ballsAhead.isEmpty() ? -1
                                : ballsAhead.get(ballsAhead.size() - 1).getX()
                                + SPIKE_BALL_TOUCH_HALF_WIDTH;
                        int ballCrossingBudget = ballsAhead.isEmpty() ? 0 : Math.max(
                                ordinaryRightCrossingBudget(player,
                                        lastBallTouchRight
                                                - (playerXBefore - PLAYER_TOUCH_HALF_WIDTH) + 1),
                                sidekickFollowAllowance(lastBallTouchRight));
                        int lastMineHazardX = -1;
                        for (ObjectInstance object : lifetime.active()) {
                            if ((object instanceof FbzMineObjectInstance
                                    || object instanceof ExplosionObjectInstance)
                                    && object.getX() >= 0x0B00 && object.getX() <= 0x0BB0
                                    && Math.abs(object.getY() - flamethrowerLandingY) <= 0x20) {
                                lastMineHazardX = Math.max(lastMineHazardX, object.getX());
                            }
                        }
                        boolean insideMineEnvelope = lastMineHazardX >= 0
                                && playerXBefore <= lastMineHazardX + 0x30;
                        // Balls spawn as the camera reaches them, so a commit
                        // covers only the balls that existed; a newly placed
                        // ball ahead reopens the gate for the remaining crossing.
                        if (ballsAhead.size() > spikeBallCorridorCommittedCount) {
                            spikeBallCorridorCommitted = false;
                        }
                        spikeBallCorridorCommittedCount = ballsAhead.size();
                        if (!spikeBallCorridorCommitted && ballsAhead.isEmpty()) {
                            spikeBallCorridorCommitted = true;
                        } else if (!spikeBallCorridorCommitted && ballsRaised
                                && ballPolarityActive && ballRunway > ballCrossingBudget) {
                            spikeBallCorridorCommitted = true;
                        }
                        if (insideMineEnvelope || spikeBallCorridorCommitted) {
                            // Keep the BK2's RIGHT traversal until every real
                            // mine/explosion envelope is behind P1. Waiting for
                            // magnetic polarity inside this corridor is lethal.
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else {
                            int holdX = ballsAhead.get(0).getX() - SPIKE_BALL_HOLD_GAP;
                            mask = playerXBefore > holdX && player.getGSpeed() > 0
                                    ? AbstractPlayableSprite.INPUT_LEFT
                                    : RouteSteering.steerMask(player, holdX, 4);
                        }
                        if (playerXBefore >= magneticCorridorExitX) {
                            postLauncherRouteStage = 10;
                        }
                    } else if (screwDoorRecoveryArmed) {
                        int doorRightEdge = blockingScrewDoor.getX()
                                + blockingScrewDoor.nativeWidth() + 0x0B;
                        if (playerXBefore > doorRightEdge) {
                            screwDoorRecoveryArmed = false;
                            screwDoorRecoveryCompleted = true;
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else {
                            // The fully displaced $43 screw door leaves its
                            // exact solid edge at $0F75. A normal run-jump clears
                            // it; this intentionally exercises no spindash or
                            // donation-specific production assist.
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (!player.getAir() || player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        }
                    } else if (screwDoorRecoveryCompleted
                            && !risingCarExitStarted
                            && risingCar == null
                            && !risingCarAcquisitionArmed) {
                        // All three profiles converge safely at $1114/$0568,
                        // but the consumed tail is in its final neutral run;
                        // there is no active object at that coordinate. Resume
                        // ordinary RIGHT movement toward the next authored
                        // $02/$0A path switch at $1280/$0640.
                        if (postDoorPathSwitchReached && lateButtonDoorStage == 3
                                && lateCorridorStage < 5) {
                            FbzZoneRuntimeState corridorRuntime =
                                    GameServices.zoneRuntimeRegistry()
                                            .currentAs(FbzZoneRuntimeState.class)
                                            .orElseThrow();
                            boolean lateDoorClearsPlayer = lateButtonDoor == null
                                    || lateButtonDoor.getY()
                                    + lateButtonDoor.getSolidParams().airHalfHeight()
                                    < playerYBefore - player.getYRadius();
                            int corridorRunway = 0xFF - corridorRuntime.magneticTimerPhase();
                            // Without a spindash (S1 donation) the crossing is an
                            // ordinary run from rest, about $C0 frames to pass
                            // the last magnetic hazard, so it needs almost the
                            // whole ACTIVE half-cycle after the hazards have risen.
                            int corridorMinRunway = spindashEnabled
                                    ? POST_DOOR_CORRIDOR_MIN_RUNWAY
                                    : POST_DOOR_CORRIDOR_RUN_MIN_RUNWAY;
                            boolean corridorGateOpen = lateDoorClearsPlayer
                                    && corridorRuntime.magneticPolarity()
                                    == Sonic3kFBZEvents.MagneticPolarity.ACTIVE
                                    && corridorRunway >= corridorMinRunway
                                    && corridorRunway <= POST_DOOR_CORRIDOR_MAX_RUNWAY;
                            int holdX = lateButton != null ? lateButton.getX() : playerXBefore;
                            switch (lateCorridorStage) {
                                case 0 -> {
                                    maskOwner = "late-corridor-polarity-hold";
                                    lateCorridorHoldFrames++;
                                    int holdFrames = lateCorridorHoldFrames;
                                    assertTrue(holdFrames <= POST_DOOR_CORRIDOR_HOLD_LIMIT,
                                            () -> waypointDiagnostic(
                                                    "late-corridor-polarity-hold-limit", holdX)
                                                    + " holdFrames=" + holdFrames);
                                    if (corridorGateOpen && !spindashEnabled
                                            && !player.getAir()) {
                                        lateCorridorStage = 4;
                                        lateCorridorRollObserved = true;
                                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                                    } else if (corridorGateOpen && !player.getAir()
                                            && Math.abs(player.getGSpeed()) <= 0x80
                                            && player.getDirection()
                                            != com.openggf.physics.Direction.LEFT) {
                                        lateCorridorStage = 1;
                                        mask = AbstractPlayableSprite.INPUT_DOWN;
                                    } else if (corridorGateOpen && !player.getAir()
                                            && Math.abs(player.getGSpeed()) <= 0x80) {
                                        // Sonic_Spindash releases along the facing
                                        // direction; face RIGHT before crouching.
                                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                                    } else {
                                        mask = player.getAir() ? 0
                                                : RouteSteering.steerMask(player, holdX, 2);
                                    }
                                }
                                case 1 -> {
                                    maskOwner = "late-corridor-spindash-crouch";
                                    if (player.getCrouching()) {
                                        lateCorridorStage = 2;
                                        lateCorridorChargeTaps = 1;
                                        mask = AbstractPlayableSprite.INPUT_DOWN
                                                | AbstractPlayableSprite.INPUT_JUMP;
                                    } else {
                                        mask = AbstractPlayableSprite.INPUT_DOWN;
                                    }
                                }
                                case 2 -> {
                                    // Sonic_Spindash adds $100 per JUMP edge up
                                    // to $C00; three edges leave enough rolling
                                    // speed after loc_3B462-style friction to
                                    // reach the descent inside one half-cycle.
                                    maskOwner = "late-corridor-spindash-hold";
                                    assertTrue(player.getSpindash(),
                                            "late corridor charge input did not enter spindash");
                                    if (lateCorridorChargeTaps < POST_DOOR_CORRIDOR_CHARGE_TAPS
                                            && (lateCorridorHoldFrames & 1) == 0) {
                                        lateCorridorChargeTaps++;
                                        mask = AbstractPlayableSprite.INPUT_DOWN
                                                | AbstractPlayableSprite.INPUT_JUMP;
                                    } else if (lateCorridorChargeTaps
                                            < POST_DOOR_CORRIDOR_CHARGE_TAPS) {
                                        mask = AbstractPlayableSprite.INPUT_DOWN;
                                    } else {
                                        lateCorridorStage = 3;
                                        mask = AbstractPlayableSprite.INPUT_DOWN;
                                    }
                                    lateCorridorHoldFrames++;
                                }
                                case 3 -> {
                                    maskOwner = "late-corridor-spindash-release";
                                    assertTrue(player.getSpindash(),
                                            "late corridor hold lost spindash state");
                                    lateCorridorStage = 4;
                                    mask = 0;
                                }
                                case 4 -> {
                                    maskOwner = "late-corridor-roll";
                                    lateCorridorRollObserved |= player.getRolling();
                                    mask = AbstractPlayableSprite.INPUT_RIGHT;
                                    if (!spindashEnabled) {
                                        // S1 profile: run. A roll would bleed the
                                        // speed the half-cycle budget needs, so the
                                        // non-magnetic $1790 floor Blaster is taken
                                        // with a short jump-roll (Sonic_JumpHeight's
                                        // released -$400 cap keeps the arc under the
                                        // lifted magnetic hazards) that lands at
                                        // running speed.
                                        maskOwner = "late-corridor-run";
                                        boolean floorBlasterAhead = objects.activeObjectsOfType(
                                                        BlasterBadnikInstance.class).stream()
                                                .filter(blaster -> !blaster.isDestroyed())
                                                .filter(blaster -> blaster.getCollisionFlags() != 0)
                                                .filter(blaster -> blaster.getX() >= playerXBefore)
                                                .filter(blaster -> blaster.getX() - playerXBefore <= 0x50)
                                                .anyMatch(blaster -> Math.abs(blaster.getY() - playerYBefore)
                                                        <= 0x10);
                                        if (lateCorridorJumpHold > 0) {
                                            lateCorridorJumpHold--;
                                            mask |= AbstractPlayableSprite.INPUT_JUMP;
                                        } else if (floorBlasterAhead && !player.getAir()) {
                                            lateCorridorJumpHold = POST_DOOR_CORRIDOR_RUN_JUMP_HOLD;
                                            mask |= AbstractPlayableSprite.INPUT_JUMP;
                                        }
                                        if (playerXBefore >= POST_DOOR_CORRIDOR_HANDOFF_X) {
                                            lateCorridorStage = 5;
                                            lateButtonDoorStage = 4;
                                        }
                                    } else if (playerXBefore >= POST_DOOR_CORRIDOR_HANDOFF_X) {
                                        lateCorridorStage = 5;
                                        lateButtonDoorStage = 4;
                                    } else if (lateCorridorRollObserved && !player.getAir()
                                            && !player.getRolling() && !player.getSpindash()) {
                                        if (Math.abs(player.getGSpeed()) >= 0x80) {
                                            // Player_TouchFloor unrolled P1 after the
                                            // drop off the button ledge; Sonic_Roll on
                                            // DOWN resumes the roll at the live speed.
                                            mask = AbstractPlayableSprite.INPUT_DOWN;
                                        } else {
                                            // The roll ended inside the corridor:
                                            // re-arm the gate and charge again.
                                            lateCorridorStage = 0;
                                            lateCorridorHoldFrames = 0;
                                            lateCorridorRollObserved = false;
                                        }
                                    }
                                }
                                default -> throw new IllegalStateException(
                                        "late corridor stage " + lateCorridorStage);
                            }
                        } else if (postDoorPathSwitchReached && lateButtonDoorStage == 3) {
                            maskOwner = "late-button-stage-3-egress";
                            if (lateButtonDoor == null) {
                                assertTrue(playerXBefore > 0x172B,
                                        () -> waypointDiagnostic(
                                                "late-door-culled-before-egress", 0x1718));
                                lateButtonDoorStage = 4;
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else {
                                int doorRightEdge = lateButtonDoor.getX()
                                        + lateButtonDoor.getSolidParams().halfWidth();
                                int doorBottom = lateButtonDoor.getY()
                                        + lateButtonDoor.getSolidParams().airHalfHeight();
                                boolean doorStillOverlapsPlayer = doorBottom
                                        >= playerYBefore - player.getYRadius();
                                if (playerXBefore > doorRightEdge
                                        && !doorStillOverlapsPlayer) {
                                    lateButtonDoorStage = 4;
                                }
                                // The latched door remains the sole live gate:
                                // ordinary RIGHT either waits against its exact
                                // solid edge or crosses as soon as its bottom
                                // clears P1's top. Source cadence cannot pull P1
                                // back behind a successfully pressed button.
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (postDoorPathSwitchReached && trigger7DoorStage == 3) {
                            maskOwner = "trigger-7-stage-3-egress";
                            // The door has latched and P1 has left the authored
                            // activation surface. loc_3CD4C gates each new pair
                            // on the parent's previous-render bounds, and
                            // loc_3CF90 keeps every child collision-enabled until
                            // its own animation expires (sonic3k.asm:80714-80733,
                            // 80915-80934). Commit the turn only after production
                            // can no longer emit and the final live wave is gone.
                            boolean sourceCanEmit = trigger7Flamethrower != null
                                    && trigger7Flamethrower.isWithinSolidContactBounds();
                            boolean liveCollisionFlames = objects.activeObjectsOfType(
                                            FbzFlameObjectInstance.class).stream()
                                    .filter(flame -> !flame.isDestroyed())
                                    .anyMatch(flame -> flame.getCollisionFlags() != 0);
                            if (!trigger7RightHandoffCommitted
                                    && !sourceCanEmit && !liveCollisionFlames) {
                                trigger7RightHandoffCommitted = true;
                                trigger7HandoffPlayerX = playerXBefore;
                                trigger7HandoffPlayerY = playerYBefore;
                                trigger7HandoffXSpeed = player.getXSpeed();
                            }
                            if (trigger7RightHandoffCommitted && !player.getAir()
                                    && playerYBefore >= 0x0700) {
                                trigger7LandingReached = true;
                                FbzScrewDoorObjectInstance lowerDoor =
                                        objects.activeObjectsOfType(
                                                        FbzScrewDoorObjectInstance.class)
                                                .stream()
                                                .filter(door -> !door.isDestroyed())
                                                .filter(door -> door.getSpawn().x() == 0x1A68)
                                                .filter(door -> door.getSpawn().y() == 0x071E)
                                                .filter(door -> door.getSpawn().subtype() == 0x16)
                                                .findFirst().orElse(null);
                                assertNotNull(lowerDoor,
                                        "trigger-7 turn lost live trigger-6 lower door");
                                int lowerDoorRightContact = lowerDoor.getX()
                                        + lowerDoor.getSolidParams().halfWidth()
                                        + player.getXRadius();
                                assertTrue(playerXBefore >= lowerDoorRightContact,
                                        () -> waypointDiagnostic(
                                                "trigger-7-turn-crossed-lower-door",
                                                lowerDoorRightContact));
                                trigger7TurnReached = true;
                                trigger7DoorStage = 4;
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else if (!trigger7EgressJumpStarted && !player.getAir()) {
                                trigger7EgressJumpStarted = true;
                                mask = AbstractPlayableSprite.INPUT_LEFT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            } else if (player.getAir()) {
                                if (!trigger7RightHandoffCommitted) {
                                    mask = AbstractPlayableSprite.INPUT_LEFT
                                            | (player.getYSpeed() < 0
                                            ? AbstractPlayableSprite.INPUT_JUMP : 0);
                                } else {
                                    mask = AbstractPlayableSprite.INPUT_RIGHT;
                                }
                            } else {
                                mask = trigger7RightHandoffCommitted
                                        ? AbstractPlayableSprite.INPUT_RIGHT
                                        : AbstractPlayableSprite.INPUT_LEFT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            }
                        } else if (postDoorPathSwitchReached
                                && trigger7Button != null
                                && trigger7Door != null
                                && trigger7Flamethrower != null
                                && trigger7DoorStage < 3) {
                            maskOwner = "trigger-7-button-contact";
                            int buttonX = trigger7Button.getX();
                            int surfaceFeetY = trigger7Button.getY()
                                    - trigger7Button.getSolidParams().airHalfHeight();
                            int playerFeetY = (player.getCentreY() & 0xFFFF)
                                    + player.getYRadius();
                            boolean withinButtonX = Math.abs(playerXBefore - buttonX)
                                    <= trigger7Button.getSolidParams().halfWidth();
                            boolean exactSurfaceFeet = playerFeetY == surfaceFeetY
                                    || playerFeetY == surfaceFeetY - 1;
                            Object surfaceOwner = player.getLatchedSolidObjectInstance();
                            boolean exactSurfaceOwner = !player.getAir()
                                    && player.isOnObject()
                                    && (surfaceOwner == trigger7Button
                                    || surfaceOwner == trigger7Flamethrower);
                            boolean triggerHeld = Sonic3kLevelTriggerManager.testBit(7, 0);
                            boolean doorOpeningLatched = trigger7Door.getX()
                                    < trigger7Door.getSpawn().x();
                            boolean flameCrossesAscentColumn = objects.activeObjectsOfType(
                                            FbzFlameObjectInstance.class).stream()
                                    .filter(flame -> !flame.isDestroyed())
                                    .filter(flame -> flame.getCollisionFlags() != 0)
                                    .filter(flame -> flame.getY() + FLAME_TOUCH_RADIUS
                                            >= surfaceFeetY - player.getYRadius())
                                    .filter(flame -> flame.getY() - FLAME_TOUCH_RADIUS
                                            <= playerYBefore + player.getYRadius())
                                    .anyMatch(flame -> crossesProjectedColumn(
                                            flame, playerXBefore,
                                            player.getXRadius() + FLAME_TOUCH_RADIUS, 12));
                            boolean liveCollisionFlames = objects.activeObjectsOfType(
                                            FbzFlameObjectInstance.class).stream()
                                    .filter(flame -> !flame.isDestroyed())
                                    .anyMatch(flame -> flame.getCollisionFlags() != 0);
                            if (trigger7DoorStage == 2 && doorOpeningLatched) {
                                // On mapping_frame 2, the four-frame loc_3CD4C
                                // cadence reaches the standing checkpoint but
                                // emits no pair. Leave on that exact suppressed
                                // slot only after the old children have expired,
                                // buying the full ROM-owned interval before a
                                // new pair can move.
                                boolean suppressedCadencePhase =
                                        ((GameServices.level().getFrameCounter() + 1) & 3) == 0;
                                // The magnetic $19B0 Blaster below the egress
                                // rides the magnet to the ceiling only during
                                // ACTIVE polarity; leave the button inside a
                                // fresh ACTIVE half-cycle so the whole left
                                // egress and turn stay above its shots.
                                FbzZoneRuntimeState egressRuntime =
                                        GameServices.zoneRuntimeRegistry()
                                                .currentAs(FbzZoneRuntimeState.class)
                                                .orElseThrow();
                                boolean egressPolarityOpen = egressRuntime.magneticPolarity()
                                        == Sonic3kFBZEvents.MagneticPolarity.ACTIVE
                                        && 0xFF - egressRuntime.magneticTimerPhase()
                                        >= TRIGGER7_EGRESS_MIN_RUNWAY;
                                if (player.getGSpeed() > 0x0100) {
                                    mask = AbstractPlayableSprite.INPUT_LEFT;
                                } else if (exactSurfaceOwner
                                        && trigger7Flamethrower.mappingFrame() == 2
                                        && !liveCollisionFlames
                                        && suppressedCadencePhase
                                        && egressPolarityOpen) {
                                    trigger7DoorStage = 3;
                                    trigger7EgressJumpStarted = true;
                                    mask = AbstractPlayableSprite.INPUT_LEFT
                                            | AbstractPlayableSprite.INPUT_JUMP;
                                } else {
                                    mask = 0;
                                }
                            } else if (trigger7DoorStage == 2) {
                                assertTrue(triggerHeld && withinButtonX
                                                && exactSurfaceFeet && exactSurfaceOwner,
                                        () -> waypointDiagnostic(
                                                "trigger-7-released-before-door-latched", buttonX));
                                mask = 0;
                            } else if (triggerHeld) {
                                assertTrue(withinButtonX && exactSurfaceFeet && exactSurfaceOwner,
                                        () -> waypointDiagnostic(
                                                "trigger-7-without-native-surface", buttonX));
                                trigger7DoorStage = 2;
                                mask = 0;
                            } else if (trigger7DoorStage == 1 && player.getAir()) {
                                mask = RouteSteering.steerMask(player, buttonX, 2)
                                        | (player.getYSpeed() < 0
                                        ? AbstractPlayableSprite.INPUT_JUMP : 0);
                            } else if (trigger7DoorStage == 1) {
                                trigger7DoorStage = 4;
                                mask = 0;
                            } else {
                                int jumpStartX = buttonX - 0x38;
                                if (trigger7DoorStage == 0 && playerXBefore < jumpStartX) {
                                    mask = AbstractPlayableSprite.INPUT_RIGHT;
                                } else if (flameCrossesAscentColumn) {
                                    // Touch_Sizes[$18] is 4x4. Use the live
                                    // flame's ROM-derived x velocity to keep
                                    // the ordinary jump edge neutral until no
                                    // current collision-enabled flame can cross
                                    // P1's ascent column during the measured
                                    // twelve-frame rise to the shared surface.
                                    mask = player.getGSpeed() > 0
                                            ? AbstractPlayableSprite.INPUT_LEFT : 0;
                                } else {
                                    trigger7LandingAttempts++;
                                    assertTrue(trigger7LandingAttempts <= 6,
                                            () -> waypointDiagnostic(
                                                    "trigger-7-landing-attempt-limit", buttonX));
                                    trigger7DoorStage = 1;
                                    int direction = RouteSteering.steerMask(player, buttonX, 2);
                                    if (direction == 0 && playerXBefore < buttonX) {
                                        direction = AbstractPlayableSprite.INPUT_RIGHT;
                                    }
                                    mask = direction | AbstractPlayableSprite.INPUT_JUMP;
                                }
                            }
                        } else if (postDoorPathSwitchReached
                                && lateButton != null && lateButtonDoor != null
                                && lateButtonDoorStage < 3) {
                            maskOwner = "late-button-contact";
                            int buttonX = lateButton.getX();
                            boolean buttonTriggerHeld =
                                    Sonic3kLevelTriggerManager.testBit(4, 0);
                            boolean doorOpeningLatched = lateButtonDoor.getY()
                                    < lateButtonDoor.getSpawn().y();
                            if (lateButtonDoorStage == 2 && doorOpeningLatched) {
                                // Obj_FBZScrewDoor samples Level_trigger_array
                                // only to enter its opening routine. That
                                // routine is latched and continues to the native
                                // $80 displacement after the momentary subtype
                                // $24 button clears, so release immediately and
                                // let the still-solid live door gate RIGHT travel.
                                lateButtonDoorStage = 3;
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            } else if (lateButtonDoorStage == 2) {
                                // Obj_Button publishes one shared trigger bit
                                // from the whole standing mask. Any configured
                                // participant may own that contact; the linked
                                // door consumes only the shared bit, then
                                // latches its opening routine independently.
                                assertTrue(buttonTriggerHeld,
                                        () -> waypointDiagnostic(
                                                "late-button-shared-trigger-released-before-door-latched",
                                                buttonX));
                                mask = 0;
                            } else if (buttonTriggerHeld) {
                                lateButtonDoorStage = 2;
                                mask = 0;
                            } else {
                                int liveDoorX = lateButtonDoor.getX();
                                int doorBlockingLeftEdge = liveDoorX
                                        - lateButtonDoor.getSolidParams().halfWidth()
                                        - player.getXRadius();
                                assertTrue(playerXBefore <= doorBlockingLeftEdge,
                                        () -> waypointDiagnostic(
                                                "late-button-controller-started-behind-door",
                                                liveDoorX));
                                // The live subtype-$24 button top is seven
                                // pixels above this floor. Grounded projected
                                // steering reaches its native SolidObjectTop
                                // contact directly and brakes before the still-
                                // solid $1718 door; no jump/contact injection is
                                // required to assert trigger 4.
                                mask = RouteSteering.steerMask(player, buttonX, 2);
                            }
                        } else if (postDoorPathSwitchReached
                                && (linkedDoorStage > 0
                                || (!player.getAir() && (linkedDoor = findLinkedBlockingDoor(
                                        objects, player, playerXBefore, playerYBefore,
                                        linkedDoorsPassed)) != null))) {
                            maskOwner = "linked-button-door";
                            if (linkedDoorStage == 0) {
                                linkedButton = findLinkedButton(objects, linkedDoor,
                                        playerXBefore, playerYBefore);
                                int foundDoorX = linkedDoor.getX();
                                assertNotNull(linkedButton, () -> waypointDiagnostic(
                                        "linked-door-without-button", foundDoorX));
                                linkedDoorStage = 1;
                                linkedDoorFrames = 0;
                            }
                            linkedDoorFrames++;
                            int doorFrames = linkedDoorFrames;
                            int doorStage = linkedDoorStage;
                            FbzScrewDoorObjectInstance door = linkedDoor;
                            Sonic3kButtonObjectInstance button = linkedButton;
                            assertTrue(doorFrames <= LINKED_DOOR_FRAME_LIMIT,
                                    () -> waypointDiagnostic("linked-door-frame-limit", door.getX())
                                            + " stage=" + doorStage
                                            + " button=" + objectPosition(button));
                            boolean doorLatched = door.getY() < door.getSpawn().y();
                            boolean doorClearsPlayer = door.getY()
                                    + door.getSolidParams().airHalfHeight()
                                    < playerYBefore - player.getYRadius();
                            int doorRightEdge = door.getX() + door.getSolidParams().halfWidth();
                            if (linkedDoorStage == 1) {
                                boolean p1OnButton = !player.getAir() && player.isOnObject()
                                        && player.getLatchedSolidObjectInstance() == button;
                                if (doorLatched) {
                                    // The door routine is latched independently of
                                    // the momentary button; walk into its live edge.
                                    linkedDoorStage = 2;
                                    mask = AbstractPlayableSprite.INPUT_RIGHT;
                                } else if (p1OnButton) {
                                    mask = Math.abs(player.getGSpeed()) > 0x80
                                            ? (player.getGSpeed() > 0
                                            ? AbstractPlayableSprite.INPUT_LEFT
                                            : AbstractPlayableSprite.INPUT_RIGHT) : 0;
                                } else {
                                    mask = RouteSteering.steerMask(player, button.getX(), 2);
                                }
                            } else {
                                if (playerXBefore > doorRightEdge && doorClearsPlayer) {
                                    linkedDoorsPassed.add(door.getSpawn().layoutIndex());
                                    linkedDoorStage = 0;
                                    linkedDoor = null;
                                    linkedButton = null;
                                }
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (postDoorPathSwitchReached) {
                            maskOwner = "post-door-live-hazards";
                            // Touch_Sizes indices $18/$1A give the live Blaster
                            // primary projectile a 4x4 radius and magnetic balls
                            // a $C x $C radius. React only to current collision-
                            // enabled instances ahead of P1 in this authored
                            // corridor; no donor/profile/frame state participates.
                            BlasterProjectileObjectInstance projectileThreat =
                                    objects.activeObjectsOfType(
                                                    BlasterProjectileObjectInstance.class).stream()
                                            .filter(projectile -> !projectile.isDestroyed())
                                            .filter(projectile -> projectile.getCollisionFlags() != 0)
                                            .filter(projectile -> projectile.getX() - playerXBefore
                                                    >= -(BLASTER_PROJECTILE_TOUCH_RADIUS
                                                    + player.getXRadius()))
                                            .filter(projectile -> projectile.getX() - playerXBefore
                                                    <= LATE_PROJECTILE_LOOKAHEAD)
                                            .filter(projectile -> projectile.getY()
                                                    + BLASTER_PROJECTILE_TOUCH_RADIUS
                                                    >= playerYBefore - player.getYRadius() - 0x40)
                                            .filter(projectile -> projectile.getY()
                                                    - BLASTER_PROJECTILE_TOUCH_RADIUS
                                                    <= playerYBefore + player.getYRadius() + 0x10)
                                            .min(java.util.Comparator.comparingInt(projectile ->
                                                    Math.abs(projectile.getX() - playerXBefore)))
                                            .orElse(null);
                            Integer previousProjectileX = projectileThreat == null ? null
                                    : projectileLastX.put(
                                            projectileThreat, projectileThreat.getX());
                            boolean projectileApproaching = projectileThreat != null
                                    && (previousProjectileX == null
                                    || projectileThreat.getX() < previousProjectileX);
                            var ceiling = ObjectTerrainUtils.checkCeilingDist(
                                    playerXBefore, playerYBefore, player.getYRadius());
                            boolean lowCeilingHeadroom = ceiling.foundSurface()
                                    && ceiling.distance() <= 0x20;
                            if (lowCeilingProjectile == null && projectileApproaching
                                    && lowCeilingHeadroom && !player.getAir()) {
                                lowCeilingProjectile = projectileThreat;
                            }
                            FbzMagneticSpikeBallObjectInstance ballThreat =
                                    objects.activeObjectsOfType(
                                                    FbzMagneticSpikeBallObjectInstance.class).stream()
                                            .filter(ball -> !ball.isDestroyed())
                                            .filter(ball -> ball.getCollisionFlags() != 0)
                                            .filter(ball -> ball.getX() >= playerXBefore)
                                            .filter(ball -> ball.getX() - playerXBefore
                                                    <= LATE_HAZARD_JUMP_LOOKAHEAD)
                                            .filter(ball -> Math.abs(ball.getY() - playerYBefore)
                                                    <= MAGNETIC_BALL_TOUCH_RADIUS
                                                    + player.getYRadius())
                                            .min(java.util.Comparator.comparingInt(ball ->
                                                    ball.getX() - playerXBefore))
                                            .orElse(null);
                            BlasterBadnikInstance blasterThreat =
                                    objects.activeObjectsOfType(BlasterBadnikInstance.class).stream()
                                            .filter(blaster -> !blaster.isDestroyed())
                                            .filter(blaster -> blaster.getCollisionFlags() != 0)
                                            .filter(blaster -> blaster.getX() >= playerXBefore)
                                            .filter(blaster -> blaster.getX() - playerXBefore <= 0x60)
                                            .filter(blaster -> Math.abs(blaster.getY() - playerYBefore)
                                                    <= BLASTER_TOUCH_RADIUS_Y + player.getYRadius())
                                            .min(java.util.Comparator.comparingInt(blaster ->
                                                    blaster.getX() - playerXBefore))
                                            .orElse(null);
                            boolean insideLateHazardCorridor = playerXBefore >= 0x16F0
                                    && playerXBefore <= 0x18D0;
                            // Obj_TechnoSqueek patrols its rail on its own
                            // timer, so a wider viewport meets it in another
                            // phase where the BK2's walk-off from the $1F40 car
                            // (rows 31668-31688) passed. A jump-roll destroys it.
                            // Only a mouse at P1's own body height while P1
                            // runs at it matters; one patrolling a wall or
                            // ceiling rail above an Obj74 ride is not on the
                            // path and must not spend the ride's neutral wait.
                            boolean squeekThreat = !player.getAir()
                                    && player.getGSpeed() > TECHNO_SQUEEK_JUMP_MIN_SPEED
                                    && squeekJumpAttempts < TECHNO_SQUEEK_JUMP_ATTEMPT_LIMIT
                                    && objects.activeObjectsOfType(
                                                    TechnoSqueekBadnikInstance.class).stream()
                                            .filter(squeek -> !squeek.isDestroyed())
                                            .filter(squeek -> squeek.getCollisionFlags() != 0)
                                            .filter(squeek -> squeek.getX() >= playerXBefore)
                                            .filter(squeek -> squeek.getX() - playerXBefore
                                                    <= TECHNO_SQUEEK_JUMP_LOOKAHEAD)
                                            .anyMatch(squeek -> Math.abs(
                                                    squeek.getY() - playerYBefore)
                                                    <= TECHNO_SQUEEK_JUMP_VERTICAL_RANGE);
                            if (squeekThreat) {
                                squeekJumpAttempts++;
                            }
                            if (lowCeilingProjectile != null
                                    && (lowCeilingProjectile.isDestroyed()
                                    || lowCeilingProjectile.getCollisionFlags() == 0
                                    || lowCeilingProjectile.getX()
                                    + BLASTER_PROJECTILE_TOUCH_RADIUS
                                    < playerXBefore - player.getXRadius())) {
                                lowCeilingProjectile = null;
                            }
                            boolean retreatingForLowCeilingProjectile =
                                    insideLateHazardCorridor && lowCeilingProjectile != null;
                            boolean groundedThreat = insideLateHazardCorridor && !player.getAir()
                                    && ((!lowCeilingHeadroom && projectileThreat != null)
                                    || ballThreat != null
                                    || blasterThreat != null);
                            if (squeekThreat) {
                                lateHazardJumpActive = true;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            } else if (retreatingForLowCeilingProjectile) {
                                // A jump here reaches the measured ceiling before
                                // clearing Touch_Sizes[$18]. Brake/retreat only
                                // until the left-moving live shot is fully behind
                                // P1's own left touch edge, then resume RIGHT.
                                BlasterProjectileObjectInstance retreatProjectile =
                                        lowCeilingProjectile;
                                lowCeilingRetreatFrames++;
                                assertTrue(lowCeilingRetreatFrames <= 0x100,
                                        () -> waypointDiagnostic(
                                                "low-ceiling-projectile-retreat-limit",
                                                retreatProjectile.getX())
                                                + " projectile="
                                                + objectPosition(retreatProjectile)
                                                + " ceilingDistance=" + ceiling.distance());
                                lateHazardJumpActive = false;
                                mask = player.getGSpeed() > -0x100
                                        ? AbstractPlayableSprite.INPUT_LEFT : 0;
                            } else if (groundedThreat) {
                                lateHazardJumpAttempts++;
                                assertTrue(lateHazardJumpAttempts <= 8,
                                        () -> waypointDiagnostic(
                                                "late-hazard-jump-attempt-limit", playerXBefore)
                                                + " projectile=" + objectPosition(projectileThreat)
                                                + " ball=" + objectPosition(ballThreat)
                                                + " blaster=" + objectPosition(blasterThreat));
                                lateHazardJumpActive = true;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            } else if (lateHazardJumpActive && player.getAir()) {
                                // Preserve the ordinary variable jump only on
                                // ascent; airborne steering remains normal RIGHT.
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | (player.getYSpeed() < 0
                                        ? AbstractPlayableSprite.INPUT_JUMP : 0);
                            } else {
                                if (!player.getAir()) lateHazardJumpActive = false;
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (postDoorRunUpPhase == 0 && !player.getAir()
                                && player.getGSpeed() == 0
                                && playerXBefore >= postDoorWallX) {
                            // The first approach converges at $12A0/$066C. A
                            // repeated jump from rest cannot follow this terrain;
                            // take an ordinary run-up through the real path
                            // switch before proving any spindash-only gap.
                            postDoorRunUpPhase = 1;
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else {
                            if (postDoorRunUpPhase == 1) {
                                mask = AbstractPlayableSprite.INPUT_LEFT;
                                if (playerXBefore <= postDoorRunUpMinX) {
                                    postDoorRunUpPhase = 2;
                                }
                            } else {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        }
                    } else if (retainedCar == null) {
                        mask = run.mask();
                    } else if (injectLowerPathJump) {
                        mask = AbstractPlayableSprite.INPUT_LEFT
                                | AbstractPlayableSprite.INPUT_JUMP;
                        lowerPathJumpStarted = true;
                    } else if (lowerPathJumpStarted && player.getAir()
                            && player.getYSpeed() < 0) {
                        // BK2 $6555-$6561 holds left+jump until the low ceiling
                        // ends the ascent, then continues left to the lower path.
                        mask = AbstractPlayableSprite.INPUT_LEFT
                                | AbstractPlayableSprite.INPUT_JUMP;
                    } else if (injectOrdinaryRoll) {
                        // S1 donation retains run-to-roll even though it has no
                        // spindash. Test that native mechanic before adding a
                        // donation-specific lower-loop workaround.
                        mask = AbstractPlayableSprite.INPUT_DOWN;
                        ordinaryRollRequested = true;
                    } else if (spindashApproachArmed && spindashChargeFrame < 0
                            && player.getGSpeed() != 0) {
                        // BK2 brakes the authored left run near $095A/$0A6B
                        // before crouching for the lower-loop spindash. Once
                        // inside one input quantum, neutral friction settles
                        // the slope residue instead of oscillating +/-$80.
                        mask = Math.abs(player.getGSpeed()) <= 0x80
                                ? 0
                                : player.getGSpeed() < 0
                                ? AbstractPlayableSprite.INPUT_RIGHT
                                : AbstractPlayableSprite.INPUT_LEFT;
                    } else if (spindashApproachArmed && !spindashCrouchSetup) {
                        // The BK2 is already crouched before its first $12
                        // charge edge. Supply that preceding DOWN-only frame.
                        mask = AbstractPlayableSprite.INPUT_DOWN;
                        spindashCrouchSetup = true;
                    } else if (spindashApproachArmed
                            && spindashChargeFrame < authoredSpindashChargeFrames - 1) {
                        // Complete-run BK2 $6627-$6641 alternates one-frame
                        // DOWN+JUMP and DOWN inputs.  Every jump frame is a new
                        // press edge; held jump would charge only once.
                        spindashChargeFrame++;
                        spindashChargePressed = (spindashChargeFrame & 1) == 0;
                        mask = AbstractPlayableSprite.INPUT_DOWN
                                | (spindashChargePressed
                                ? AbstractPlayableSprite.INPUT_JUMP : 0);
                    } else if (spindashApproachArmed) {
                        // Complete-run BK2 $6642 releases DOWN with no
                        // directional input.  Neutral is load-bearing: the
                        // loop terrain rotates the $F500 ground speed up and
                        // over the prison while held LEFT instead drives P1
                        // into its exact $0873 solid edge at ground level.
                        mask = 0;
                        spindashApproachArmed = false;
                        spindashReleased = true;
                        lowerLoopLaunchCommitted = true;
                        spindashReleasedThisFrame = true;
                    } else if (lowerLoopLaunchCommitted) {
                        // BK2 $6643-$6698 remains neutral until P1 has
                        // traversed the loop and landed at $080C/$09EC. Donated
                        // movement can settle a few pixels to the right; use an
                        // ordinary LEFT input on the same authored floor until
                        // it reaches the native stable interval.
                        mask = !player.getAir()
                                && playerYBefore >= stableLowerLoopMinY
                                && playerXBefore > stableLowerLoopMaxX
                                ? AbstractPlayableSprite.INPUT_LEFT : 0;
                    } else if (naturallyReleased) {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else if ((player.getCentreY() & 0xFFFF) >= egressStartY) {
                        // Complete-run BK2 $6512 begins the authored left exit
                        // at y=$09F0 while P1 remains on this exact car.
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else {
                        // Complete-run BK2 keeps P1 at $BC4 on the $BC0 car
                        // before beginning the authored left exit at y=$09F0.
                        mask = RouteSteering.steerMask(player.getCentreX(), preEgressPlayerX, 2);
                    }
                    FbzDezPlayerLauncherObjectInstance lowerLauncher =
                            objects.activeObjectsOfType(
                                            FbzDezPlayerLauncherObjectInstance.class).stream()
                                    .filter(launcher -> !launcher.isDestroyed())
                                    .filter(launcher -> launcher.getSpawn().x() == 0x11D0)
                                    .filter(launcher -> launcher.getSpawn().y() == 0x0B80)
                                    .filter(launcher -> launcher.getSpawn().subtype() == 0x00)
                                    .findFirst().orElse(null);
                    if (!lowerLauncherAcquisitionJumpActive && lowerLauncher != null
                            && !player.getAir()
                            && playerXBefore >= lowerLauncher.getSpawn().x() - 0xC0
                            && playerXBefore < lowerLauncher.getSpawn().x() - 0x18
                            && playerYBefore >= lowerLauncher.getSpawn().y() - 0x50
                            && playerYBefore <= lowerLauncher.getSpawn().y() + 0x10) {
                        // Build an ordinary run-jump from the real lower ledge
                        // toward the exact subtype-$00 launcher. Without this
                        // edge the native+Tails cadence reaches its 16-pixel
                        // top width about ten pixels too far left and falls.
                        lowerLauncherAcquisitionJumpActive = true;
                    }
                    if (lowerLauncherAcquisitionJumpActive && lowerLauncher != null) {
                        boolean standingOnLauncher = player.isOnObject()
                                && player.getLatchedSolidObjectInstance() == lowerLauncher;
                        if (standingOnLauncher
                                || (!player.getAir()
                                && playerXBefore > lowerLauncher.getSpawn().x())) {
                            lowerLauncherAcquisitionJumpActive = false;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (!player.getAir() || player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        }
                    }
                    FbzFlamethrowerObjectInstance earlyFlamethrower =
                            objects.activeObjectsOfType(
                                            FbzFlamethrowerObjectInstance.class).stream()
                                    .filter(flame -> !flame.isDestroyed())
                                    .filter(flame -> flame.getSpawn().x() == 0x1278)
                                    .filter(flame -> flame.getSpawn().y() == 0x0978)
                                    .findFirst().orElse(null);
                    if (!earlyFlamethrowerJumpActive && earlyFlamethrower != null
                            && !player.getAir()
                            && playerXBefore >= earlyFlamethrower.getSpawn().x() - 0x78
                            && playerXBefore < earlyFlamethrower.getSpawn().x()
                            && Math.abs(playerYBefore
                            - earlyFlamethrower.getSpawn().y()) <= 0x48) {
                        // Shared ordinary jump over the exact live placed
                        // $1278/$0978 hazard; no donor or frame identity.
                        earlyFlamethrowerJumpActive = true;
                    }
                    if (earlyFlamethrowerJumpActive) {
                        maskOwner = "early-flamethrower-override";
                        mask = AbstractPlayableSprite.INPUT_RIGHT
                                | (player.getAir() && player.getYSpeed() >= 0
                                ? 0 : AbstractPlayableSprite.INPUT_JUMP);
                        if (playerXBefore >= 0x12A0
                                || (!player.getAir() && playerXBefore > 0x1278)) {
                            earlyFlamethrowerJumpActive = false;
                        }
                    }
                    boolean exactLowerSpikeAhead = objects.activeObjectsOfType(
                                    Sonic3kSpikeObjectInstance.class).stream()
                            .anyMatch(spike -> !spike.isDestroyed()
                                    && spike.getSpawn().x() == 0x0C00
                                    && spike.getSpawn().y() == 0x0A18);
                    if ((missedDescendingCarSpikeJumpActive
                            || (!player.getAir() && exactLowerSpikeAhead
                            && playerXBefore >= 0x0BA0 && playerXBefore <= 0x0C20
                            && playerYBefore >= 0x0A20 && playerYBefore <= 0x0A40))) {
                        maskOwner = "lower-spike-override";
                        missedDescendingCarSpikeJumpActive = playerXBefore <= 0x0C30;
                        mask = AbstractPlayableSprite.INPUT_RIGHT
                                | (!player.getAir() || player.getYSpeed() < 0
                                ? AbstractPlayableSprite.INPUT_JUMP : 0);
                    }
                    if (squeezeCorridorClearances > 0 && player.isOnObject()
                            && player.getLatchedSolidObjectInstance()
                            instanceof FbzElevatorObjectInstance.Car descendingSupport
                            && descendingSupport.yVelocity() > 0) {
                        boolean spikeWallAhead = objects.activeObjectsOfType(
                                        Sonic3kSpikeObjectInstance.class).stream()
                                .anyMatch(spike -> !spike.isDestroyed()
                                        && spike.getX() > descendingSupport.getX()
                                        && spike.getX() - descendingSupport.getX() <= 0x80
                                        && spike.getY() + spike.getSolidParams().airHalfHeight()
                                        >= playerYBefore - player.getYRadius()
                                        && spike.getY() - spike.getSolidParams().airHalfHeight()
                                        <= playerYBefore + player.getYRadius());
                        if (spikeWallAhead) {
                            // Ride the live descending platform below the spike
                            // wall before walking off its right edge. Ordinary
                            // steering preserves native platform/solid ownership.
                            maskOwner = "descending-car-spike-wall-wait";
                            mask = RouteSteering.steerMask(player, descendingSupport.getX(), 2);
                        }
                    }
                    if (landingGapRight != null
                            && (magneticPlatformHazardControllerActive
                            || landingGapRight.isDestroyed()
                            || !objects.getActiveObjects().contains(landingGapRight)
                            || playerYBefore - player.getYRadius() > landingGapRight.getY()
                                    + landingGapRight.getSolidParams().airHalfHeight())) {
                        // A completed descent or another input owner ends this
                        // episode. Never resume steering toward an old spike row.
                        landingGapRight = null;
                        landingGapX = -1;
                        landingGapJump = false;
                    }
                    if (squeezeCorridorClearances > 0 && landingGapRight == null
                            && !magneticPlatformHazardControllerActive
                            && player.getAir() && player.getYSpeed() > 0) {
                        var spikes = objects.activeObjectsOfType(Sonic3kSpikeObjectInstance.class);
                        for (var leftSpike : spikes) {
                            for (var rightSpike : spikes) {
                                // Spike solid half-width already includes the
                                // native $0B player contact margin. Do not add
                                // the player's radius again to this centre gap.
                                int gapLeft = leftSpike.getX() + leftSpike.getSolidParams().halfWidth();
                                int gapRight = rightSpike.getX() - rightSpike.getSolidParams().halfWidth();
                                if (leftSpike.isDestroyed() || rightSpike.isDestroyed()
                                        || leftSpike.getY() != rightSpike.getY()
                                        || gapRight - gapLeft <= 0
                                        || rightSpike.getX() - leftSpike.getX() > 0x80
                                        || playerXBefore < leftSpike.getX() - leftSpike.getSolidParams().halfWidth()
                                        || playerXBefore > rightSpike.getX() + rightSpike.getSolidParams().halfWidth()
                                        || rightSpike.getY() - playerYBefore < player.getYRadius()
                                        || rightSpike.getY() - playerYBefore
                                        > LOWER_SPIKE_GAP_DROP_LIMIT) continue;
                                landingGapRight = rightSpike;
                                landingGapX = (gapLeft + gapRight) / 2;
                            }
                        }
                    }
                    // A committed magnetic underpass retains sole input
                    // ownership; the landing pilot cannot inject a jump into it.
                    if (landingGapRight != null && !magneticPlatformHazardControllerActive) {
                        maskOwner = "lower-spike-gap-landing";
                        // The gap may hold an Obj74 column. Its raised top sits
                        // under a wall that blocks the exit jump, so wait on it
                        // (loc_3B3C0 drops it once polarity clears) and leave
                        // only from its resting floor position.
                        FbzMagneticPlatformObjectInstance gapPlatform =
                                !player.getAir() && player.isOnObject()
                                        && player.getLatchedSolidObjectInstance()
                                        instanceof FbzMagneticPlatformObjectInstance platform
                                        ? platform : null;
                        boolean gapPlatformMoving = gapPlatform != null
                                && gapPlatform.displacement() != 0;
                        if (gapPlatformMoving) {
                            landingGapWaitFrames++;
                            int gapWait = landingGapWaitFrames;
                            int gapCentreX = landingGapX;
                            assertTrue(gapWait <= LOWER_SPIKE_GAP_PLATFORM_WAIT_LIMIT,
                                    () -> waypointDiagnostic(
                                            "lower-spike-gap-platform-wait-limit", gapCentreX)
                                            + " waitFrames=" + gapWait);
                        }
                        if (!landingGapJump && !player.getAir() && !gapPlatformMoving
                                && Math.abs(player.getXSpeed()) < 0x20) landingGapJump = true;
                        if (gapPlatformMoving) {
                            mask = RouteSteering.steerMask(player, gapPlatform.getX(), 3);
                        } else if (landingGapJump) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (!player.getAir() || player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                            if (playerXBefore > landingGapRight.getX()
                                    + landingGapRight.getSolidParams().halfWidth()) {
                                landingGapRight = null;
                                landingGapJump = false;
                            }
                        } else {
                            // Sonic_ChgJumpDir uses twice run acceleration.
                            // Brake live lateral inertia before the landing;
                            // jumping with retained leftward speed misses the exit.
                            int velocity = player.getXSpeed();
                            int acceleration = Math.max(1, 2 * player.getRunAccel());
                            int projectedX = playerXBefore
                                    + velocity * Math.abs(velocity) / (2 * acceleration * 256);
                            mask = RouteSteering.steerMask(projectedX, landingGapX, 1);
                        }
                    } else if (squeezeCorridorClearances > 0 && landingGapRight == null
                            && !magneticPlatformHazardControllerActive
                            && !player.getAir() && player.getGSpeed() > LOWER_SPIKE_GAP_APPROACH_CAP
                            && "post-door-live-hazards".equals(maskOwner)) {
                        // A spike-pair gap below a ledge ahead: cap the run-off
                        // speed so the airborne gap pilot can still brake into
                        // the gap centre during the drop.
                        var spikes = objects.activeObjectsOfType(Sonic3kSpikeObjectInstance.class);
                        boolean gapLedgeAhead = false;
                        for (var leftSpike : spikes) {
                            for (var rightSpike : spikes) {
                                int gapLeft = leftSpike.getX() + leftSpike.getSolidParams().halfWidth();
                                int gapRight = rightSpike.getX() - rightSpike.getSolidParams().halfWidth();
                                int leftEdge = leftSpike.getX() - leftSpike.getSolidParams().halfWidth();
                                if (leftSpike.isDestroyed() || rightSpike.isDestroyed()
                                        || leftSpike.getY() != rightSpike.getY()
                                        || gapRight - gapLeft <= 0
                                        || rightSpike.getX() - leftSpike.getX() > 0x80
                                        || leftEdge < playerXBefore
                                        || leftEdge - playerXBefore > LOWER_SPIKE_GAP_LEDGE_LOOKAHEAD
                                        || rightSpike.getY() - playerYBefore < player.getYRadius()
                                        || rightSpike.getY() - playerYBefore
                                        > LOWER_SPIKE_GAP_DROP_LIMIT) continue;
                                gapLedgeAhead = true;
                            }
                        }
                        if (gapLedgeAhead) {
                            maskOwner = "lower-spike-gap-approach-brake";
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    }
                    lastControllerDiagnostic = " controller={postSwitch="
                            + postDoorPathSwitchReached
                            + ",lateStage=" + lateButtonDoorStage
                            + ",lateButtonLive=" + (lateButton != null)
                            + ",lateDoorLive=" + (lateButtonDoor != null)
                            + ",trigger7Stage=" + trigger7DoorStage
                            + ",trigger7RightHandoff=" + trigger7RightHandoffCommitted
                            + ",trigger7HandoffPlayer=($"
                            + Integer.toHexString(trigger7HandoffPlayerX)
                            + ",$" + Integer.toHexString(trigger7HandoffPlayerY) + ")"
                            + ",trigger7HandoffXSpeed=$"
                            + Integer.toHexString(trigger7HandoffXSpeed & 0xFFFF)
                            + ",trigger7Landing=" + trigger7LandingReached
                            + ",trigger7Turn=" + trigger7TurnReached
                            + ",trigger7ButtonLive=" + (trigger7Button != null)
                            + ",trigger7DoorLive=" + (trigger7Door != null)
                            + ",obj74Armed=" + magneticPlatformHazardArmed
                            + ",obj74VerticalClear="
                            + magneticPlatformVerticalClearanceObserved
                            + ",obj74Cleared=" + magneticPlatformHazardCleared
                            + ",obj74Clearances=" + magneticPlatformHazardClearances
                            + ",obj74Bindings=" + magneticPlatformLiveBindings
                            + ",obj74Encountered="
                            + magneticPlatformEncounteredLayoutIndices
                            + ",obj74ClearedLayouts="
                            + magneticPlatformClearedLayoutIndices
                            + ",obj74Crossing=" + magneticPlatformCrossingCommitted
                            + ",obj74CrossBudget=" + magneticPlatformCrossingBudget
                            + ",obj74CrossFrames=" + magneticPlatformCrossingFrames
                            + ",obj74YFixed="
                            + (magneticPlatformHazardTarget == null ? "none" : "$"
                            + Long.toHexString(magneticPlatformYFixed(
                                    magneticPlatformHazardTarget)))
                            + ",obj74WaitFrames=" + magneticPlatformHazardWaitFrames
                            + ",obj74Ride=" + magneticPlatformRideMode
                            + ",obj74RideStage=" + magneticPlatformRideStage
                            + ",obj74RideFrames=" + magneticPlatformRideFrames
                            + ",obj74RideAttempts=" + magneticPlatformRideAttempts
                            + ",obj74RideJump=" + magneticPlatformRideJumpStarted
                            + ",obj74RideSettled=" + magneticPlatformRideSettled
                            + ",obj74RideHop=" + objectPosition(magneticPlatformRideHopTarget)
                            + ",obj74RideSpike=" + objectPosition(magneticPlatformRideBlockingSpike)
                            + ",obj28Target=" + objectPosition(squeezeCorridorTarget)
                            + ",obj28Support=" + objectPosition(squeezeCorridorSupport)
                            + ",obj28RollRequested=" + squeezeCorridorRollRequested
                            + ",obj28Stage=" + squeezeCorridorStage
                            + ",obj28SpindashCapable="
                            + squeezeCorridorSpindashCapable
                            + ",obj28LaunchSpeed=$"
                            + Integer.toHexString(squeezeCorridorLaunchSpeed)
                            + ",obj28AssistConsumed="
                            + squeezeCorridorAssistConsumed
                            + ",obj28Entered=" + squeezeCorridorEntered
                            + ",obj28SupportAcquired="
                            + squeezeCorridorSupportAcquired
                            + ",obj28SupportExited=" + squeezeCorridorSupportExited
                            + ",obj28Bindings=" + squeezeCorridorLiveBindings
                            + ",obj28Clearances=" + squeezeCorridorClearances
                            + ",obj28Encountered="
                            + squeezeCorridorEncounteredLayoutIndices
                            + ",obj28Cleared="
                            + squeezeCorridorClearedLayoutIndices
                            + ",obj28MinimumGaps=" + squeezeCorridorMinimumGaps
                            + ",obj28RollEntrySpeeds="
                            + squeezeCorridorRollEntrySpeeds
                            + ",obj28SupportEvidence="
                            + squeezeCorridorSupportEvidence
                            + ",obj28TentativeBinding="
                            + squeezeCorridorTentativeBindingRecorded
                            + ",obj28AbortEvidence="
                            + squeezeCorridorAbortEvidence
                            + ",obj28RecoveryActive="
                            + squeezeCorridorRecoveryActive
                            + ",obj28RecoveryFrontier="
                            + squeezeCorridorRecoveryFrontierX
                            + ",obj28RecoveryHold="
                            + squeezeCorridorRecoveryHoldX
                            + ",obj28RecoveryFrames="
                            + squeezeCorridorRecoveryFrames
                            + ",obj28Ahead=" + objectPosition(squeezeAheadBlock)
                            + ",obj28Brake=" + squeezeApproachBrake
                            + ",obj28LastCandidate="
                            + squeezeCorridorLastCandidateEvidence
                            + ",owner=" + maskOwner + ",mask=$"
                            + Integer.toHexString(mask) + '}';
                    if ("late-button-stage-3-egress".equals(maskOwner)) {
                        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, mask,
                                waypointDiagnostic(
                                        "late-door-egress-mask-override-stage-"
                                                + lateButtonDoorStage,
                                        lateButtonDoor == null
                                                ? 0x1718 : lateButtonDoor.getX()));
                    }
                    if (trigger7DoorStage == 3 && trigger7RightHandoffCommitted) {
                        assertEquals(0, mask & AbstractPlayableSprite.INPUT_JUMP,
                                waypointDiagnostic(
                                        "trigger-7-egress-rearmed-released-jump", 0x1AC0));
                    }
                    if ("trigger-7-stage-3-egress".equals(maskOwner)
                            && trigger7RightHandoffCommitted) {
                        assertEquals(0, mask & AbstractPlayableSprite.INPUT_LEFT,
                                waypointDiagnostic(
                                        "trigger-7-egress-reversed-after-right",
                                        trigger7HandoffPlayerX));
                    }
                    if (trigger7DoorStage == 3 && trigger7RightHandoffCommitted
                            && lateButtonDoor != null && !player.getAir()) {
                        int respawnedDoorRightEdge = lateButtonDoor.getX()
                                + lateButtonDoor.getSolidParams().halfWidth();
                        boolean verticalDoorOverlap = Math.abs(
                                playerYBefore - lateButtonDoor.getY())
                                <= lateButtonDoor.getSolidParams().airHalfHeight()
                                + player.getYRadius();
                        assertFalse(verticalDoorOverlap
                                        && playerXBefore <= respawnedDoorRightEdge,
                                waypointDiagnostic(
                                        "trigger-7-overshot-authored-landing",
                                        respawnedDoorRightEdge)
                                        + " handoffPlayer=($"
                                        + Integer.toHexString(trigger7HandoffPlayerX)
                                        + ",$" + Integer.toHexString(trigger7HandoffPlayerY)
                                        + ") handoffXSpeed=$"
                                        + Integer.toHexString(trigger7HandoffXSpeed & 0xFFFF));
                    }
                    InputProgram.step(fixture, mask);
                    frames++;
                    recordRecentFrame(mask, player);
                    if (magneticPlatformHazardArmed) {
                        assertFalse(player.getDead() || player.isHurt(),
                                () -> waypointDiagnostic(
                                        "obj74-continuous-safety-hurt", player.getCentreX()));
                        int currentRings =
                                GameServices.level().getLevelGamestate().getRings();
                        int priorRings = magneticPlatformSafetyRings;
                        assertTrue(currentRings >= priorRings,
                                () -> waypointDiagnostic(
                                        "obj74-continuous-safety-ring-loss",
                                        player.getCentreX())
                                        + " priorRings=" + priorRings
                                        + " currentRings=" + currentRings);
                        magneticPlatformSafetyRings = currentRings;
                    }
                    if (squeezeCorridorSafetyArmed) {
                        assertFalse(player.getDead() || player.isHurt(),
                                () -> waypointDiagnostic(
                                        "obj28-continuous-safety-hurt",
                                        player.getCentreX()));
                        int currentRings =
                                GameServices.level().getLevelGamestate().getRings();
                        int priorRings = squeezeCorridorSafetyRings;
                        assertTrue(currentRings >= priorRings,
                                () -> waypointDiagnostic(
                                        "obj28-continuous-safety-ring-loss",
                                        player.getCentreX())
                                        + " priorRings=" + priorRings
                                        + " currentRings=" + currentRings);
                        squeezeCorridorSafetyRings = currentRings;
                    }
                    if (squeezeCorridorTarget != null) {
                        Sonic3kInvisibleBlockObjectInstance target =
                                squeezeCorridorTarget;
                        assertTrue(FbzMovingSqueezeTraversal.isLiveNormalInvisibleSolid(
                                        target, player),
                                "normal Obj28 squeeze target lost post-step solid authority");
                        int layoutIndex = target.getSpawn().layoutIndex();
                        int postPlayerX = player.getCentreX() & 0xFFFF;
                        int blockLeft = target.getX()
                                + target.getSolidParams().offsetX()
                                - target.getSolidParams().halfWidth();
                        int blockRight = target.getX()
                                + target.getSolidParams().offsetX()
                                + target.getSolidParams().halfWidth();
                        if (squeezeCorridorStage == 2 || squeezeCorridorStage == 3) {
                            assertTrue(player.getSpindash(),
                                    "Obj28 native charge/hold did not retain spindash");
                        } else if (squeezeCorridorStage == 4) {
                            boolean landedOnSelectedCar = player.isOnObject()
                                    && player.getLatchedSolidObjectInstance() == squeezeCorridorSupport;
                            assertSqueezePosture(player,
                                    squeezeCorridorSupportAcquired || landedOnSelectedCar, target);
                            assertFalse(player.getSpindash(),
                                    "Obj28 post-launch remained in spindash state");
                            if (!squeezeCorridorSpindashCapable) {
                                FbzZoneRuntimeState runtime =
                                        GameServices.zoneRuntimeRegistry()
                                                .currentAs(FbzZoneRuntimeState.class)
                                                .orElseThrow();
                                squeezeCorridorAssistConsumed =
                                        runtime.s1DonationSqueezeAssistState()
                                                == FbzZoneRuntimeState
                                                .S1DonationSqueezeAssistState.CONSUMED;
                                squeezeCorridorAssistEverConsumed |=
                                        squeezeCorridorAssistConsumed;
                                // The assist reads RIGHT+DOWN on a frame P1 is
                                // already rolling, one frame after DOWN began it,
                                // and by design stays armed when the ordinary
                                // roll's own projection already clears the pair.
                                boolean ownRollClears = squeezeCorridorSupport != null
                                        && FbzMovingSqueezeTraversal.project(
                                                new FbzMovingSqueezeTraversal.Episode(
                                                        target, squeezeCorridorSupport),
                                                player, player.getGSpeed()).clears();
                                int s1RollFrames = squeezeCorridorS1RollFrames;
                                FbzElevatorObjectInstance.Car s1Support = squeezeCorridorSupport;
                                assertTrue(squeezeCorridorAssistConsumed
                                                || squeezeCorridorS1RollFrames < 2
                                                || ownRollClears,
                                        () -> "S1 Obj28 roll did not consume production assist"
                                                + " rollFrames=" + s1RollFrames
                                                + " g=$" + Integer.toHexString(player.getGSpeed() & 0xFFFF)
                                                + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                                                + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ")"
                                                + " rolling=" + player.getRolling()
                                                + " support=" + objectPosition(s1Support));
                                squeezeCorridorLaunchSpeed = Math.max(
                                        squeezeCorridorLaunchSpeed, player.getGSpeed());
                                squeezeCorridorRollEntrySpeeds.put(
                                        layoutIndex, squeezeCorridorLaunchSpeed);
                            }
                        }
                        if (squeezeCorridorStage == 4) {
                            if (player.isOnObject()
                                    && player.getLatchedSolidObjectInstance()
                                    == squeezeCorridorSupport) {
                                squeezeCorridorSupportAcquired = true;
                                assertTrue(FbzMovingSqueezeTraversal.isLiveUpwardCar(
                                                squeezeCorridorSupport),
                                        "Obj28 post-step lost live upward car support");
                                FbzMovingSqueezeTraversal.Projection projection =
                                        FbzMovingSqueezeTraversal.project(
                                                new FbzMovingSqueezeTraversal.Episode(
                                                        target, squeezeCorridorSupport),
                                                player, Math.max(1, player.getGSpeed()));
                                // The rolling predictor does not model native
                                // unrolling; assertSqueezePosture checks that
                                // standing clearance against the live block.
                                assertTrue(!player.getRolling() || projection.clears(),
                                        () -> waypointDiagnostic(
                                                "obj28-post-step-projection-unsafe",
                                                projection.dangerEdge())
                                                + " projection=" + projection);
                                if (projection.minimumGap() != Integer.MAX_VALUE) {
                                    squeezeCorridorMinimumGaps.merge(layoutIndex,
                                            projection.minimumGap(), Math::min);
                                }
                            } else if (squeezeCorridorSupportAcquired) {
                                squeezeCorridorSupportExited = true;
                            }
                        }
                        if (postPlayerX >= blockLeft) {
                            squeezeCorridorEntered = true;
                        }
                        if (postPlayerX > blockRight) {
                            assertTrue(squeezeCorridorEntered,
                                    "Obj28 corridor cleared without evidenced entry");
                            assertTrue(squeezeCorridorSupportAcquired,
                                    "Obj28 corridor cleared without acquiring its exact car");
                            assertTrue(squeezeCorridorSupportExited,
                                    "Obj28 corridor cleared before exiting its exact car");
                            squeezeCorridorClearances++;
                            squeezeCorridorClearedLayoutIndices.add(layoutIndex);
                            squeezeCorridorTentativeBindingRecorded = false;
                            squeezeCorridorTarget = null;
                            squeezeCorridorSupport = null;
                            squeezeCorridorBlockSlot = -1;
                            squeezeCorridorSupportSlot = -1;
                            squeezeCorridorCarBeforeBlock = false;
                            squeezeCorridorRollRequested = false;
                            squeezeCorridorStage = 0;
                            squeezeCorridorSpindashCapable = false;
                            squeezeCorridorLaunchSpeed = 0;
                            squeezeCorridorAssistConsumed = false;
                            squeezeCorridorEntered = false;
                            squeezeCorridorSupportAcquired = false;
                            squeezeCorridorSupportExited = false;
                        }
                    }
                    if (magneticPlatformHazardPlacement != null && magneticPlatformRideMode) {
                        ObjectSpawn hazardPlacement = magneticPlatformHazardPlacement;
                        FbzMagneticPlatformObjectInstance target =
                                magneticPlatformHazardTarget;
                        assertFalse(player.getDead() || player.isHurt(),
                                () -> waypointDiagnostic("obj74-ride-hurt", hazardPlacement.x())
                                        + " target=" + objectPosition(target));
                        int ringsAtArm = magneticPlatformHazardRingsAtArm;
                        assertTrue(GameServices.level().getLevelGamestate().getRings()
                                        >= ringsAtArm,
                                () -> waypointDiagnostic("obj74-ride-ring-loss",
                                        hazardPlacement.x())
                                        + " ringsAtArm=" + ringsAtArm);
                        if (magneticPlatformRideStage == 4 && target != null
                                && magneticPlatformRideJumpStarted) {
                            int postPlayerTouchLeft = (player.getCentreX() & 0xFFFF)
                                    - PLAYER_TOUCH_HALF_WIDTH;
                            int postObjectTouchRight = target.getX()
                                    + MAGNETIC_PLATFORM_TOUCH_RADIUS_X;
                            if (postPlayerTouchLeft > postObjectTouchRight) {
                                magneticPlatformHazardCleared = true;
                                magneticPlatformHazardClearances++;
                                magneticPlatformClearedLayoutIndices.add(
                                        hazardPlacement.layoutIndex());
                                magneticPlatformHazardPlacement = null;
                                magneticPlatformHazardTarget = null;
                                magneticPlatformPreviousLiveYFixed = Long.MIN_VALUE;
                                magneticPlatformCrossingCommitted = false;
                                magneticPlatformCrossingBudget = 0;
                                magneticPlatformCrossingFrames = 0;
                                magneticPlatformRideMode = false;
                                magneticPlatformRideStage = 0;
                                magneticPlatformRideJumpStarted = false;
                                magneticPlatformRideHopTarget = null;
                                magneticPlatformRideBlockingSpike = null;
                            }
                        }
                    } else if (magneticPlatformHazardPlacement != null) {
                        ObjectSpawn hazardPlacement = magneticPlatformHazardPlacement;
                        FbzMagneticPlatformObjectInstance target =
                                magneticPlatformHazardTarget;
                        int postPlayerX = player.getCentreX() & 0xFFFF;
                        int postPlayerY = player.getCentreY() & 0xFFFF;
                        int postPlayerTouchRadiusY = Math.max(
                                1, player.getYRadius() - 3);
                        int postPlayerTouchTop = postPlayerY - postPlayerTouchRadiusY;
                        int postPlayerTouchBottom = postPlayerY + postPlayerTouchRadiusY;
                        int postPlayerTouchLeft = postPlayerX - PLAYER_TOUCH_HALF_WIDTH;
                        int postPlayerTouchRight = postPlayerX + PLAYER_TOUCH_HALF_WIDTH;
                        assertFalse(player.getDead() || player.isHurt(),
                                () -> waypointDiagnostic(
                                        "obj74-underpass-hurt",
                                        hazardPlacement.x())
                                        + " target=" + objectPosition(target));
                        int ringsAtArm = magneticPlatformHazardRingsAtArm;
                        assertTrue(GameServices.level().getLevelGamestate().getRings()
                                        >= ringsAtArm,
                                () -> waypointDiagnostic(
                                        "obj74-underpass-ring-loss",
                                        hazardPlacement.x())
                                        + " ringsAtArm=" + ringsAtArm);
                        int postObjectX = target != null
                                ? target.getX() : hazardPlacement.x();
                        int postObjectTouchLeft = postObjectX
                                - MAGNETIC_PLATFORM_TOUCH_RADIUS_X;
                        int postObjectTouchRight = postObjectX
                                + MAGNETIC_PLATFORM_TOUCH_RADIUS_X;
                        boolean postCrossed = postPlayerTouchLeft > postObjectTouchRight;
                        if (magneticPlatformCrossingCommitted) {
                            magneticPlatformCrossingFrames++;
                            int crossingBudget = magneticPlatformCrossingBudget;
                            int crossingFrames = magneticPlatformCrossingFrames;
                            assertTrue(crossingFrames <= crossingBudget,
                                    () -> waypointDiagnostic(
                                            "obj74-crossing-budget-exceeded", postObjectX)
                                            + " budget=" + crossingBudget
                                            + " used=" + crossingFrames);
                            if (!postCrossed) {
                                assertNotNull(target,
                                        "committed Obj74 crossing lost its live target");
                                FbzZoneRuntimeState postRuntime =
                                        GameServices.zoneRuntimeRegistry()
                                                .currentAs(FbzZoneRuntimeState.class)
                                                .orElseThrow();
                                int postRunway = 0xFF - postRuntime.magneticTimerPhase();
                                int remainingBudget = crossingBudget - crossingFrames;
                                int postObjectTouchBottom = target.getY()
                                        + MAGNETIC_PLATFORM_TOUCH_RADIUS_Y;
                                assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE,
                                        postRuntime.magneticPolarity(),
                                        "Obj74 crossing polarity changed before exact clearance");
                                assertTrue(target.lastMagneticActive(),
                                        "Obj74 stopped sampling active polarity during crossing");
                                assertTrue(hasOrdinaryFlatGroundControl(player),
                                        () -> waypointDiagnostic("obj74-crossing-lost-flat-control", postObjectX));
                                assertTrue(postObjectTouchBottom < postPlayerTouchTop,
                                        "Obj74 live bottom re-entered P1 touch box during crossing");
                                assertTrue(postRunway > remainingBudget,
                                        () -> waypointDiagnostic(
                                                "obj74-post-crossing-runway-exhausted",
                                                postObjectX)
                                                + " runway=" + postRunway
                                                + " remainingBudget=" + remainingBudget);
                            }
                        }
                        if (target == null) {
                            assertTrue(postPlayerTouchRight < postObjectTouchLeft,
                                    () -> waypointDiagnostic(
                                            "obj74-preload-whole-player-left",
                                            postObjectTouchLeft)
                                            + " target=" + objectPosition(target));
                        } else {
                            int postObjectTouchTop = target.getY()
                                    - MAGNETIC_PLATFORM_TOUCH_RADIUS_Y;
                            int postObjectTouchBottom = target.getY()
                                    + MAGNETIC_PLATFORM_TOUCH_RADIUS_Y;
                            boolean postVerticalTouchOverlap =
                                    postObjectTouchBottom >= postPlayerTouchTop
                                            && postObjectTouchTop <= postPlayerTouchBottom;
                            if (postVerticalTouchOverlap) {
                                assertTrue(postPlayerTouchRight < postObjectTouchLeft,
                                        () -> waypointDiagnostic(
                                                "obj74-post-step-whole-player-left",
                                                postObjectTouchLeft)
                                                + " target=" + objectPosition(target));
                            } else if (postPlayerTouchRight >= postObjectTouchLeft) {
                                assertTrue(postObjectTouchBottom < postPlayerTouchTop,
                                        () -> waypointDiagnostic(
                                                "obj74-post-step-wrong-vertical-side",
                                                target.getX())
                                                + " target=" + objectPosition(target));
                                assertTrue(magneticPlatformCurrentVerticalClearanceObserved,
                                        "Obj74 crossing lacked upward-motion clearance proof");
                            }
                            if (postCrossed) {
                                assertTrue(magneticPlatformCurrentVerticalClearanceObserved,
                                        "Obj74 underpass crossed before vertical clearance");
                                magneticPlatformHazardCleared = true;
                                magneticPlatformHazardClearances++;
                                magneticPlatformClearedLayoutIndices.add(
                                        hazardPlacement.layoutIndex());
                                magneticPlatformHazardPlacement = null;
                                magneticPlatformHazardTarget = null;
                                magneticPlatformPreviousLiveYFixed = Long.MIN_VALUE;
                                magneticPlatformCrossingCommitted = false;
                                magneticPlatformCrossingBudget = 0;
                                magneticPlatformCrossingFrames = 0;
                            }
                        }
                    }
                    if (stopCondition.reached()) {
                        assertTrue(trigger7RightHandoffCommitted,
                                "successful route never retired trigger-7 flame emission");
                        assertEquals(4, trigger7DoorStage,
                                "successful route did not complete trigger-7 egress");
                        assertTrue(trigger7LandingReached,
                                "successful route lacked trigger-7 landing evidence");
                        assertTrue(trigger7TurnReached,
                                "successful route lacked one-way trigger-7 turn evidence");
                        assertTrue(magneticPlatformHazardArmed,
                                "successful route never armed the live Obj74 hazard response");
                        assertTrue(magneticPlatformVerticalClearanceObserved,
                                "successful route never observed Obj74 above P1 touch-top");
                        assertTrue(magneticPlatformHazardCleared,
                                "successful route never cleared the live Obj74 touch-right edge");
                        assertTrue(magneticPlatformHazardClearances > 0,
                                "successful route lacked an evidenced Obj74 clearance episode");
                        assertTrue(magneticPlatformEncounteredLayoutIndices.size() >= 2,
                                "successful route did not encounter repeated Obj74 hazards");
                        assertEquals(magneticPlatformEncounteredLayoutIndices,
                                magneticPlatformClearedLayoutIndices,
                                "not every dynamically eligible Obj74 placement was cleared");
                        assertEquals(magneticPlatformEncounteredLayoutIndices.size(),
                                magneticPlatformLiveBindings,
                                "not every Obj74 hazard episode bound exactly one live instance");
                        assertEquals(magneticPlatformClearedLayoutIndices.size(),
                                magneticPlatformHazardClearances,
                                "Obj74 clearance count disagrees with cleared layout identities");
                        assertNull(magneticPlatformHazardPlacement,
                                "route completed with an unresolved Obj74 placement");
                        assertNull(magneticPlatformHazardTarget,
                                "route completed with an unresolved live Obj74 target");
                        assertTrue(squeezeCorridorSafetyArmed,
                                "successful route never armed a normal Obj28 squeeze response");
                        assertTrue(squeezeCorridorEncounteredLayoutIndices.size() >= 1,
                                "successful route encountered no dynamic Obj28 squeeze episode");
                        assertEquals(squeezeCorridorEncounteredLayoutIndices,
                                squeezeCorridorClearedLayoutIndices,
                                "not every dynamically eligible Obj28 squeeze was cleared");
                        assertEquals(squeezeCorridorEncounteredLayoutIndices.size(),
                                squeezeCorridorLiveBindings,
                                "not every Obj28 squeeze bound exactly one live instance");
                        assertEquals(squeezeCorridorClearedLayoutIndices.size(),
                                squeezeCorridorClearances,
                                "Obj28 squeeze clearance count disagrees with layout evidence");
                        assertEquals(squeezeCorridorEncounteredLayoutIndices,
                                squeezeCorridorMinimumGaps.keySet(),
                                "Obj28 squeeze episodes lacked retained corridor geometry");
                        assertEquals(squeezeCorridorEncounteredLayoutIndices,
                                squeezeCorridorRollEntrySpeeds.keySet(),
                                "Obj28 squeeze episodes lacked retained roll-entry speed");
                        assertEquals(squeezeCorridorEncounteredLayoutIndices,
                                squeezeCorridorSupportEvidence.keySet(),
                                "Obj28 squeeze episodes lacked retained moving-support evidence");
                        assertNull(squeezeCorridorTarget,
                                "route completed with an unresolved Obj28 squeeze target");
                        assertNull(squeezeCorridorSupport,
                                "route completed with an unresolved Obj28 moving support");
                        assertEquals(-1, squeezeCorridorBlockSlot,
                                "route completed with a retained Obj28 slot identity");
                        assertEquals(-1, squeezeCorridorSupportSlot,
                                "route completed with a retained car slot identity");
                        assertFalse(squeezeCorridorRollRequested,
                                "route completed with an unresolved Obj28 roll request");
                        assertFalse(squeezeCorridorEntered,
                                "route completed inside an Obj28 squeeze envelope");
                        assertFalse(squeezeCorridorTentativeBindingRecorded,
                                "route completed with a tentative Obj28 binding");
                        assertFalse(squeezeCorridorRecoveryActive,
                                "route completed with unresolved Obj28 terrain recovery");
                        assertEquals(-1, squeezeCorridorRecoveryFrontierX,
                                "route completed with retained Obj28 recovery frontier");
                        assertEquals(-1, squeezeCorridorRecoveryHoldX,
                                "route completed with retained Obj28 recovery hold target");
                        boolean routeSpindashCapable = player.getGameRules() != null
                                && player.getGameRules().playerCapability() != null
                                && player.getGameRules().playerCapability().spindashEnabled();
                        assertEquals(!routeSpindashCapable,
                                squeezeCorridorAssistEverConsumed,
                                "Obj28 S1 assist consumption disagrees with route capability");
                        assertTrue(completed, () -> waypointDiagnostic(
                                "descending-elevator-not-complete-at-stop", targetX));
                        lifetime.endFrame();
                        return true;
                    }
                    check.afterFrame(this, player);

                    if (retainedCar != null) {
                        controlledFrames++;
                        int playerX = player.getCentreX() & 0xFFFF;
                        int playerY = player.getCentreY() & 0xFFFF;
                        String diagnostic = waypointDiagnostic(
                                "descending-elevator-corridor", targetX);
                        boolean stillOnRetainedCar = player.isOnObject()
                                && player.getLatchedSolidObjectInstance() == retainedCar;
                        assertEquals(1, retainedCar.yVelocity(), diagnostic);
                        assertFalse(player.getDead() || player.isHurt(), diagnostic);
                        if (!spindashEnabled) {
                            assertFalse(player.getSpindash(),
                                    () -> diagnostic + " no-spindash route used spindash");
                        }
                        if (spindashChargePressed) {
                            assertTrue(player.getSpindash(),
                                    () -> diagnostic + " authored DOWN+JUMP did not charge spindash");
                        }
                        if (spindashReleasedThisFrame) {
                            spindashReleaseSpeed = player.getGSpeed();
                            assertTrue(player.getGSpeed() <= -0x0B00,
                                    () -> diagnostic + " lower-loop spindash release speed=$"
                                            + Integer.toHexString(player.getGSpeed() & 0xFFFF));
                        }
                        if (injectLowerPathJump) {
                            assertTrue(player.getAir(),
                                    () -> diagnostic + " authored left-jump edge did not launch");
                        }
                        if (injectOrdinaryRoll) {
                            assertTrue(player.getRolling(),
                                    () -> diagnostic + " ordinary run-to-roll input did not roll");
                        }
                        if (!spindashEnabled && ordinaryRollRequested
                                && player.getGSpeed() <= -0x0B00) {
                            // The S1-donation production assist supplies the
                            // same launch floor from ordinary LEFT input. Once
                            // supplied, mirror the BK2's neutral traversal.
                            lowerLoopLaunchCommitted = true;
                        }
                        if (!naturallyReleased && playerY >= spikeEnvelopeMinY
                                && playerY < egressStartY) {
                            assertTrue(playerX >= safeMinX && playerX <= safeMaxX,
                                    () -> diagnostic + " left safe centre interval");
                        }
                        assertTrue(controlledFrames <= frameLimit,
                                diagnostic + " frameLimit=" + frameLimit
                                        + " spindashRelease=$"
                                        + Integer.toHexString(spindashReleaseSpeed & 0xFFFF));
                        if (stillOnRetainedCar && playerY >= carriedHandoffY) {
                            carryReachedHandoff = true;
                        }
                        if (stillOnRetainedCar) {
                            lastRetainedPlayerY = playerY;
                        }
                        if (!stillOnRetainedCar && !naturallyReleased) {
                            // The byte_3CAD0 slope can advance the integer
                            // player centre directly from $A2A to $A2C. That is
                            // still a carry through the $A2B boundary when the
                            // same car remains latched through the prior frame.
                            assertTrue((carryReachedHandoff
                                            || lastRetainedPlayerY >= carriedHandoffY - 1)
                                            && playerY >= releasedHandoffY,
                                    () -> diagnostic + " released before authored handoff");
                            assertTrue(playerX <= squashEscapeBandMaxX,
                                    () -> diagnostic + " missed ROM left squash escape band");
                            int requiredCarry = carriedHandoffY - carryStartY;
                            int carCarry = retainedCar.getCentreY() - carCarryStartY;
                            // byte_3CAD0 ranges from $10..$21. Steering from
                            // the right slope to the midpoint can lower P1 by
                            // up to $11 independently of the car's +1/frame
                            // centre motion (the five-team route observes $05).
                            assertTrue(carCarry > 0 && carCarry + 0x11 >= requiredCarry,
                                    diagnostic + " carryStart=player:$"
                                            + Integer.toHexString(carryStartY) + "/car:$"
                                            + Integer.toHexString(carCarryStartY));
                            naturallyReleased = true;
                        } else if (naturallyReleased && lowerPathJumpStarted
                                && !stillOnRetainedCar && !player.getAir()
                                && playerX <= lowerPathLandingMaxX
                                && playerY >= lowerPathLandingMinY) {
                            lowerPathLandingReached = true;
                        }
                        if (naturallyReleased && lowerPathLandingReached
                                && (ordinaryRollRequested || spindashReleased)
                                && !stillOnRetainedCar && !player.getAir()
                                && playerX <= stableLowerLoopMaxX
                                && playerY >= stableLowerLoopMinY) {
                            assertFalse(player.getDead() || player.isHurt(), diagnostic);
                            assertFalse(player.isOnObject()
                                            && player.getLatchedSolidObjectInstance() == retainedCar,
                                    () -> diagnostic + " still attached after bounded egress");
                            assertEquals(1, retainedCar.yVelocity(),
                                    diagnostic + " car stopped descending during egress");
                            retainedCar = null;
                            completed = true;
                        } else if (!naturallyReleased) {
                            assertSame(retainedCar, player.getLatchedSolidObjectInstance(),
                                    diagnostic + " retained-car identity");
                        }
                    }
                    if (!risingCarRideCompleted) {
                        int playerX = player.getCentreX() & 0xFFFF;
                        int playerY = player.getCentreY() & 0xFFFF;
                        Object owner = player.getLatchedSolidObjectInstance();
                        if (!risingCarExitStarted && risingCar == null && player.isOnObject()
                                && owner instanceof FbzElevatorObjectInstance.Car car
                                && car.getCentreX() == 0x06C0 && car.yVelocity() == -1) {
                            risingCar = car;
                            risingCarAcquisitionArmed = false;
                        }
                        if (risingCar != null) {
                            String diagnostic = waypointDiagnostic(
                                    "rising-elevator-corridor", 0x06C0);
                            assertFalse(player.getDead() || player.isHurt(), diagnostic);
                            if (playerY <= 0x0930) {
                                assertTrue(playerX >= risingCarSafeMinX
                                                && playerX <= risingCarSafeMaxX,
                                        () -> diagnostic + " left safe centre interval");
                            }
                            assertSame(risingCar, owner,
                                    diagnostic + " retained-car identity");
                            assertEquals(-1, risingCar.yVelocity(),
                                    diagnostic + " car stopped rising");
                        }
                        if (risingCarExitStarted
                                && playerX >= risingCarSafeExitX) {
                            assertFalse(player.getDead() || player.isHurt(),
                                    () -> waypointDiagnostic(
                                            "rising-elevator-exit", risingCarSafeExitX));
                            risingCarExitStarted = false;
                            risingCarRideCompleted = true;
                        }
                    }
                    lifetime.endFrame();
                }
            }
            assertTrue(completed, waypointDiagnostic(
                    "descending-elevator-never-completed", targetX)
                    + " retained=" + (retainedCar != null)
                    + " naturallyReleased=" + naturallyReleased
                    + " lowerPathLanding=" + lowerPathLandingReached
                    + " lowerLoopCommitted=" + lowerLoopLaunchCommitted
                    + " descendingDoorOpened=" + descendingDoorOpened
                    + " descendingDoorStage=" + descendingDoorStage
                    + " descendingButtonBrakeReady=" + descendingButtonBrakeReady
                    + " descendingButtonLive=" + descendingButtonLive
                    + " descendingControllerActive=" + descendingControllerActiveLast
                    + " descendingCar=" + (descendingCar == null ? "null"
                    : objectPosition(descendingCar) + "/vy=" + descendingCar.yVelocity()));
            return false;
        }

        private FbzSpiderCraneObjectInstance lowerSpiderCrane() {
            return objects.activeObjectsOfType(FbzSpiderCraneObjectInstance.class).stream()
                    .filter(candidate -> !candidate.isDestroyed())
                    .filter(candidate -> candidate.getSpawn().x() == LOWER_CRANE_X)
                    .filter(candidate -> candidate.stateName().equals("WAIT")
                            || candidate.stateName().equals("DESCEND"))
                    .findFirst().orElse(null);
        }

        private boolean lowerCraneApproachReached(AbstractPlayableSprite player) {
            if (player.getAir() || player.isObjectControlled() || player.isOnObject()) return false;
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            return Math.abs(y - LOWER_CRANE_FLOOR_Y) <= 4
                    && x >= LOWER_CRANE_X - 0x10 && x <= LOWER_CRANE_X + 0x60
                    && lowerSpiderCrane() != null;
        }

        private static boolean craneHolding(FbzSpiderCraneObjectInstance crane) {
            return switch (crane.stateName()) {
                case "CAPTURE", "RETRACT", "TRAVEL" -> true;
                default -> false;
            };
        }

        private FbzMagneticPlatformObjectInstance magneticColumn(int spawnX, int spawnY) {
            return objects.activeObjectsOfType(FbzMagneticPlatformObjectInstance.class).stream()
                    .filter(column -> !column.isDestroyed())
                    .filter(column -> column.getX() == spawnX)
                    .filter(column -> column.getY() + column.displacement() == spawnY)
                    .findFirst().orElse(null);
        }

        /** Walks toward {@code targetX} at a capped speed and brakes inside the tolerance. */
        private static int steerMaskWalking(AbstractPlayableSprite player, int targetX,
                                            int tolerance) {
            return RouteSteering.walkMask(player, targetX, tolerance, LOWER_CRANE_WALK_CAP);
        }

        private boolean technoSqueekAhead(AbstractPlayableSprite player) {
            int playerX = player.getCentreX() & 0xFFFF;
            int playerY = player.getCentreY() & 0xFFFF;
            return objects.activeObjectsOfType(TechnoSqueekBadnikInstance.class).stream()
                    .filter(squeek -> !squeek.isDestroyed())
                    .filter(squeek -> squeek.getX() >= playerX)
                    .filter(squeek -> squeek.getX() - playerX <= 0x48)
                    .anyMatch(squeek -> Math.abs(squeek.getY() - playerY) <= 0x40);
        }

        /** Live TechnoSqueek within {@code range} ahead of P1 at ledge height. */
        private boolean technoSqueekWithin(AbstractPlayableSprite player, int range) {
            int playerX = player.getCentreX() & 0xFFFF;
            int playerY = player.getCentreY() & 0xFFFF;
            return objects.activeObjectsOfType(TechnoSqueekBadnikInstance.class).stream()
                    .filter(squeek -> !squeek.isDestroyed())
                    .filter(squeek -> squeek.getCollisionFlags() != 0)
                    .filter(squeek -> squeek.getX() >= playerX - 0x08)
                    .filter(squeek -> squeek.getX() - playerX <= range)
                    .anyMatch(squeek -> Math.abs(squeek.getY() - playerY) <= 0x20);
        }

        /**
         * $01EC ledge to the $076C loop floor (see the LEDGE_* constants):
         * hop the mice, grab the $0868 chain, ride it down, drop LEFT onto
         * the $02EC floor, break the $0810 monitor, stand on the $0810
         * launcher and settle from its throw inside the upper-loop approach.
         */
        private void runLedgeToLoopFloor(FrameCheck check) {
            int stage = 0;
            int stageFrames = 0;
            int hold = 0;
            int chainStillFrames = 0;
            int lastChainY = -1;
            int grabAttempts = 0;
            int monitorAttempts = 0;
            for (int frame = 0; frame < LEDGE_ROUTE_FRAME_LIMIT; frame++) {
                AbstractPlayableSprite player = fixture.sprite();
                int x = player.getCentreX() & 0xFFFF;
                int y = player.getCentreY() & 0xFFFF;
                boolean grounded = !player.getAir();
                Object latch = player.getLatchedSolidObjectInstance();
                FbzChainLinkObjectInstance chain = player.isOnObject()
                        && latch instanceof FbzChainLinkObjectInstance link ? link : null;
                int mask = 0;
                String owner;
                int stageBefore = stage;
                switch (stage) {
                    case 0 -> {
                        owner = "ledge-east";
                        if (chain != null && chain.getSpawn().x() != LEDGE_CHAIN_X) {
                            // A hop rose into another link's grab band: the
                            // directional jump release (loc_3A8F8) leaves it
                            // with a re-grab cooldown.
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                            hold = 0;
                        } else if (chain != null) {
                            stage = 2;
                        } else if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && x >= LEDGE_CHAIN_JUMP_X) {
                            stage = 1;
                            mask = AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && technoSqueekWithin(player, LEDGE_SQUEEK_HOP_RANGE)) {
                            hold = LEDGE_SQUEEK_HOP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    }
                    case 1 -> {
                        owner = "ledge-chain-grab";
                        if (chain != null && chain.getSpawn().x() == LEDGE_CHAIN_X) {
                            stage = 2;
                        } else if (chain != null) {
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && stageFrames > 2) {
                            grabAttempts++;
                            int attempts = grabAttempts;
                            assertTrue(attempts < LEDGE_CHAIN_GRAB_ATTEMPT_LIMIT,
                                    () -> waypointDiagnostic("ledge-chain-grab-attempts", LEDGE_CHAIN_X)
                                            + " attempts=" + attempts);
                            stage = 0;
                            mask = x > LEDGE_CHAIN_JUMP_X ? AbstractPlayableSprite.INPUT_LEFT : 0;
                        } else {
                            // Full variable jump, air-steered onto the link column.
                            mask = (player.getYSpeed() < 0 ? AbstractPlayableSprite.INPUT_JUMP : 0)
                                    | (x < LEDGE_CHAIN_X - 2 ? AbstractPlayableSprite.INPUT_RIGHT
                                    : x > LEDGE_CHAIN_X + 2 ? AbstractPlayableSprite.INPUT_LEFT : 0);
                        }
                    }
                    case 2 -> {
                        owner = "ledge-chain-hang";
                        if (chain == null) {
                            stage = 3;
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        } else {
                            // loc_3A860 extends the link 2px/frame to its range;
                            // release LEFT once it rests at full extension.
                            chainStillFrames = y == lastChainY ? chainStillFrames + 1 : 0;
                            lastChainY = y;
                            if (chainStillFrames >= LEDGE_CHAIN_STILL_FRAMES) {
                                hold = 1;
                                stage = 3;
                                mask = AbstractPlayableSprite.INPUT_LEFT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            }
                        }
                    }
                    case 3 -> {
                        owner = "ledge-chain-drop";
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && y >= LEDGE_LOWER_FLOOR_Y - 8) {
                            stage = 4;
                        } else {
                            // The $0810/$0870 spikes flank the landing; aim the
                            // -$200 release drift at the gap midpoint using the
                            // distance Sonic_ChgJumpDir needs to brake it.
                            int velocity = player.getXSpeed();
                            int airAcceleration = Math.max(1, 2 * player.getRunAccel());
                            int projectedX = x + velocity * Math.abs(velocity)
                                    / (2 * airAcceleration * 256);
                            mask = projectedX > LEDGE_SPIKE_GAP_X + 2
                                    ? AbstractPlayableSprite.INPUT_LEFT
                                    : projectedX < LEDGE_SPIKE_GAP_X - 2
                                    ? AbstractPlayableSprite.INPUT_RIGHT : 0;
                        }
                    }
                    case 4 -> {
                        owner = "ledge-monitor";
                        boolean monitorLive = objects.activeObjectsOfType(
                                        Sonic3kMonitorObjectInstance.class).stream()
                                .filter(monitor -> !monitor.isDestroyed())
                                .anyMatch(monitor -> monitor.getSpawn().x() == LEDGE_MONITOR_X
                                        && monitor.getSpawn().y() == LEDGE_MONITOR_Y);
                        if (!monitorLive) {
                            stage = 5;
                        } else if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | (player.getYSpeed() < 0 ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        } else if (Math.abs(x - LEDGE_MONITOR_JUMP_X) <= 2
                                && Math.abs(player.getGSpeed()) <= 0x80) {
                            monitorAttempts++;
                            int attempts = monitorAttempts;
                            assertTrue(attempts <= LEDGE_MONITOR_ATTEMPT_LIMIT,
                                    () -> waypointDiagnostic("ledge-monitor-attempts", LEDGE_MONITOR_X)
                                            + " attempts=" + attempts);
                            hold = LEDGE_MONITOR_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_LEFT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = steerMaskWalking(player, LEDGE_MONITOR_JUMP_X, 2);
                        }
                    }
                    case 5 -> {
                        owner = "ledge-launcher";
                        if (player.getGSpeed() >= LEDGE_LAUNCH_SPEED
                                || player.getXSpeed() >= LEDGE_LAUNCH_SPEED) {
                            stage = 6;
                        } else if (player.isOnObject()
                                && latch instanceof FbzDezPlayerLauncherObjectInstance) {
                            // loc_3B9AC starts the throw from the standing bits
                            // of the previous object pass; stand still on it.
                            mask = 0;
                        } else if (grounded) {
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        }
                    }
                    case 6 -> {
                        owner = "ledge-launch-flight";
                        if (grounded && y >= LEDGE_LOOP_FLOOR_MIN_Y && y <= LEDGE_LOOP_FLOOR_MAX_Y
                                && x >= LEDGE_LOOP_FLOOR_MIN_X && x <= LEDGE_LOOP_FLOOR_MAX_X) {
                            stage = 7;
                        }
                    }
                    default -> {
                        owner = "loop-floor-settle";
                        if (!grounded) {
                            mask = 0;
                        } else if (Math.abs(player.getGSpeed()) > 0x80) {
                            mask = player.getGSpeed() > 0
                                    ? AbstractPlayableSprite.INPUT_LEFT
                                    : AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (Math.abs(x - LEDGE_LOOP_STAND_X) > 4) {
                            mask = steerMaskWalking(player, LEDGE_LOOP_STAND_X, 4);
                        } else if (player.getDirection() != com.openggf.physics.Direction.LEFT) {
                            mask = AbstractPlayableSprite.INPUT_LEFT;
                        } else {
                            return;
                        }
                    }
                }
                stageFrames = stage == stageBefore ? stageFrames + 1 : 0;
                int stageNow = stage;
                int stageFramesNow = stageFrames;
                String ownerNow = owner;
                assertTrue(stageFrames <= LEDGE_STAGE_LIMIT,
                        () -> waypointDiagnostic("ledge-stage-limit", x)
                                + " stage=" + stageNow + " owner=" + ownerNow
                                + " frames=" + stageFramesNow);
                lastControllerDiagnostic = " controller={owner=" + owner + ",stage=" + stage + "}";
                AbstractPlayableSprite after = stepCheckedFrame(mask, check);
                assertFalse(after.getDead(),
                        () -> waypointDiagnostic("ledge-route-death", x) + " owner=" + ownerNow);
            }
            fail(waypointDiagnostic("ledge-route-frame-limit", LEDGE_LOOP_STAND_X)
                    + " frameLimit=" + LEDGE_ROUTE_FRAME_LIMIT + " stage=" + stage);
        }

        /**
         * Lower $26C8 spider crane loop: the crane carries P1 over the $2708
         * pipe; the $2904/$2944 chains hand over to the $2924 vertical link,
         * the $29C0 chain to the $29E0 link; the $2970 button opens the $2924
         * floor door onto the bottom corridor; a spindash under the raised
         * $27C0/$2840 columns reaches the rising $2640 car; the $2708 launcher
         * throws P1 into the $2780 vertical cage; the $2800 step and the
         * raised $2840/$28C0 columns reach the $0680 corridor beside the
         * $2B40 subboss. Returns true only when {@code stop} is reached.
         */
        private boolean runLowerCraneRouteToSubboss(FrameCheck check, StopCondition stop) {
            FbzSpiderCraneObjectInstance crane = lowerSpiderCrane();
            assertNotNull(crane, () -> waypointDiagnostic("lower-crane-missing", LOWER_CRANE_X));
            int stage = 0;
            int stageFrames = 0;
            int hold = 0;
            int chargeTaps = 0;
            int stepAttempts = 0;
            int hopAttempts = 0;
            int columnJumps = 0;
            int lastChainY = -1;
            int chainStillFrames = 0;
            boolean pitJumped = false;
            FbzMagneticPlatformObjectInstance column = null;
            for (int frame = 0; frame < LOWER_CRANE_ROUTE_FRAME_LIMIT; frame++) {
                if (stop.reached()) return true;
                AbstractPlayableSprite player = fixture.sprite();
                int x = player.getCentreX() & 0xFFFF;
                int y = player.getCentreY() & 0xFFFF;
                boolean grounded = !player.getAir();
                Object latch = player.getLatchedSolidObjectInstance();
                FbzChainLinkObjectInstance chain = player.isObjectControlled()
                        && latch instanceof FbzChainLinkObjectInstance link ? link : null;
                FbzZoneRuntimeState magnetic = GameServices.zoneRuntimeRegistry()
                        .currentAs(FbzZoneRuntimeState.class).orElseThrow();
                boolean activePolarity = magnetic.magneticPolarity()
                        == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
                int runway = 0xFF - magnetic.magneticTimerPhase();
                int mask = 0;
                String owner;
                int before = stage;
                switch (stage) {
                    case 0 -> {
                        // Stand inside loc_3D0F6's $20 window until loc_3D11E captures.
                        owner = "crane-approach";
                        mask = x > LOWER_CRANE_X + 0x04 ? AbstractPlayableSprite.INPUT_LEFT
                                : x < LOWER_CRANE_X - 0x08 ? AbstractPlayableSprite.INPUT_RIGHT : 0;
                        if (player.isObjectControlled() && craneHolding(crane)) stage = 1;
                    }
                    case 1 -> {
                        // loc_3D1B2..loc_3D2D2 own P1 until the $B0 travel decelerates.
                        owner = "crane-ride";
                        if (crane.stateName().equals("INERT") && !player.isObjectControlled()
                                && grounded) {
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        owner = "crane-corridor-east";
                        if (chain != null) {
                            stage = 3;
                        } else if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && (x >= LOWER_CRANE_CHAIN_ONE_HOP_X
                                || technoSqueekAhead(player))) {
                            hold = LOWER_CRANE_CHAIN_HOP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    }
                    case 3, 4 -> {
                        int verticalX = stage == 3 ? LOWER_CRANE_CHAIN_ONE_VERTICAL_X
                                : LOWER_CRANE_CHAIN_TWO_VERTICAL_X;
                        owner = stage == 3 ? "chain-ladder-one" : "chain-ladder-two";
                        if (chain == null) {
                            if (hold > 0) {
                                hold--;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            } else if (grounded && x >= (stage == 3
                                    ? LOWER_CRANE_CHAIN_ONE_HOP_X
                                    : LOWER_CRANE_CHAIN_TWO_BAND_LEFT_X)) {
                                hold = LOWER_CRANE_CHAIN_HOP_HOLD;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                            } else {
                                mask = AbstractPlayableSprite.INPUT_RIGHT;
                            }
                        } else if (chain.horizontalMode()) {
                            // sub_3AA7E hand steps are $20 apart; the vertical link
                            // transfers when the hand rests on its column.
                            mask = x < verticalX ? AbstractPlayableSprite.INPUT_RIGHT
                                    : x > verticalX ? AbstractPlayableSprite.INPUT_LEFT : 0;
                            chainStillFrames = 0;
                            lastChainY = -1;
                        } else {
                            assertEquals(verticalX, chain.getSpawn().x(),
                                    () -> waypointDiagnostic("chain-ladder-wrong-vertical-link",
                                            verticalX));
                            // loc_3A860 extends 2px/frame to $2E; a directional jump
                            // release (loc_3A8F8) carries P1 clear of the re-grab window.
                            chainStillFrames = y == lastChainY ? chainStillFrames + 1 : 0;
                            lastChainY = y;
                            if (chainStillFrames >= LOWER_CRANE_CHAIN_STILL_FRAMES) {
                                hold = LOWER_CRANE_CHAIN_RELEASE_HOLD - 1;
                                mask = AbstractPlayableSprite.INPUT_RIGHT
                                        | AbstractPlayableSprite.INPUT_JUMP;
                                chainStillFrames = 0;
                                lastChainY = -1;
                                stage++;
                            }
                        }
                    }
                    case 5 -> {
                        owner = "trigger-d-button";
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (player.isOnObject()
                                && latch instanceof Sonic3kButtonObjectInstance button
                                && button.getSpawn().x() == LOWER_CRANE_BUTTON_X) {
                            stage = 6;
                        } else {
                            mask = steerMaskWalking(player, LOWER_CRANE_BUTTON_X, 4);
                        }
                    }
                    case 6 -> {
                        // The opened Obj_FBZScrewDoor no longer supports the floor walk.
                        owner = "floor-door-drop";
                        if (grounded && y >= LOWER_CRANE_BOTTOM_CORRIDOR_MIN_Y) {
                            stage = 7;
                        } else if (grounded) {
                            mask = steerMaskWalking(player, LOWER_CRANE_FLOOR_DOOR_X, 4);
                        }
                    }
                    case 7 -> {
                        owner = "bottom-corridor-west";
                        FbzMagneticPlatformObjectInstance near = magneticColumn(0x2840, 0x0B70);
                        FbzMagneticPlatformObjectInstance far = magneticColumn(0x27C0, 0x0B70);
                        boolean columnsRaised = near != null && far != null
                                && near.displacement() >= LOWER_CRANE_COLUMN_CLEAR_RISE
                                && far.displacement() >= LOWER_CRANE_COLUMN_CLEAR_RISE;
                        // Hold east of the $2840/$0B94 Obj6B hurt block (sub
                        // $41: half-width $28 plus the $B solid margin reaches
                        // $2873). P1's $13 radius stands on the floor 1px above
                        // its top face, but a CPU sidekick landing from flight
                        // with Tails' $F radius meets the face and
                        // Kill_Character runs.
                        if (!grounded) {
                            mask = 0;
                        } else if (Math.abs(x - LOWER_CRANE_SPINDASH_HOLD_X) > 4
                                && !(columnsRaised && activePolarity
                                && runway >= LOWER_CRANE_SPINDASH_MIN_RUNWAY)) {
                            mask = steerMaskWalking(player, LOWER_CRANE_SPINDASH_HOLD_X, 4);
                        } else if (columnsRaised && activePolarity
                                && runway >= LOWER_CRANE_SPINDASH_MIN_RUNWAY
                                && Math.abs(player.getGSpeed()) <= 0x80) {
                            assertTrue(player.getGameRules().playerCapability().spindashEnabled(),
                                    "bottom corridor roll needs the native spindash");
                            stage = 8;
                            chargeTaps = 0;
                            mask = AbstractPlayableSprite.INPUT_DOWN;
                        } else {
                            mask = player.getGSpeed() < -0x80
                                    ? AbstractPlayableSprite.INPUT_RIGHT : 0;
                        }
                    }
                    case 8 -> {
                        // Sonic_Spindash: crouch, then JUMP edges add $100 each.
                        owner = "bottom-spindash";
                        if (!player.getCrouching() && !player.getSpindash()) {
                            mask = AbstractPlayableSprite.INPUT_DOWN;
                        } else if (chargeTaps < POST_DOOR_CORRIDOR_CHARGE_TAPS) {
                            boolean tap = (stageFrames & 1) == 0;
                            if (tap) chargeTaps++;
                            mask = AbstractPlayableSprite.INPUT_DOWN
                                    | (tap ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        } else {
                            assertTrue(player.getSpindash(),
                                    () -> waypointDiagnostic("bottom-spindash-not-charged", x));
                            stage = 9;
                        }
                    }
                    case 9 -> {
                        // The roll passes under both raised columns and destroys the $2700 Blaster.
                        owner = "bottom-roll-west";
                        assertFalse(player.isHurt(),
                                () -> waypointDiagnostic("bottom-roll-hurt", x));
                        if (grounded && !player.getRolling() && !player.getSpindash()
                                && (x <= LOWER_CRANE_ROLL_STOP_MAX_X
                                || Math.abs(player.getGSpeed()) < 0x80)) {
                            stage = 10;
                        }
                    }
                    case 10 -> {
                        owner = "elevator-board";
                        if (player.isOnObject()
                                && latch instanceof FbzElevatorObjectInstance.Car car
                                && car.yVelocity() == -1) {
                            stage = 11;
                        } else if (grounded) {
                            mask = steerMaskWalking(player, LOWER_CRANE_ELEVATOR_X, 4);
                        }
                    }
                    case 11 -> {
                        owner = "elevator-ride";
                        assertTrue(player.isOnObject()
                                        && latch instanceof FbzElevatorObjectInstance.Car,
                                () -> waypointDiagnostic("elevator-ride-lost-car", x));
                        if (y <= LOWER_CRANE_ELEVATOR_EXIT_Y) stage = 12;
                    }
                    case 12 -> {
                        owner = "elevator-exit";
                        if (player.isObjectControlled()
                                && latch instanceof FbzWireCageObjectInstance) {
                            stage = 14;
                        } else if (latch instanceof FbzDezPlayerLauncherObjectInstance) {
                            stage = 13;
                        } else if (grounded && x > LOWER_CRANE_LAUNCHER_X + 0x30) {
                            mask = steerMaskWalking(player, LOWER_CRANE_LAUNCHER_X, 4);
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    }
                    case 13 -> {
                        // sub_3B9D8 carries the standing rider right; the throw enters
                        // the $2780 vertical Obj_FBZWireCage window.
                        owner = "launcher-throw";
                        if (player.isObjectControlled()
                                && latch instanceof FbzWireCageObjectInstance) {
                            stage = 14;
                        } else if (grounded && y <= LOWER_CRANE_CAGE_LANDING_MAX_Y) {
                            stage = 15;
                        } else if (grounded && !player.isOnObject()
                                && x > LOWER_CRANE_LAUNCHER_X + 0x40
                                && y >= LOWER_CRANE_ELEVATOR_EXIT_Y - 0x30) {
                            stage = 12;
                        }
                    }
                    case 14 -> {
                        owner = "cage-lift";
                        if (!player.isObjectControlled() && grounded
                                && y <= LOWER_CRANE_CAGE_LANDING_MAX_Y) {
                            stage = 15;
                        }
                    }
                    case 15 -> {
                        owner = "step-up";
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (y <= LOWER_CRANE_STEP_TOP_MAX_Y) {
                            stage = 16;
                        } else if (Math.abs(x - LOWER_CRANE_STEP_JUMP_X) <= 4
                                && player.getGSpeed() == 0) {
                            stepAttempts++;
                            assertTrue(stepAttempts <= 3,
                                    () -> waypointDiagnostic("step-up-failed", x));
                            hold = LOWER_CRANE_STEP_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = steerMaskWalking(player, LOWER_CRANE_STEP_JUMP_X, 4);
                        }
                    }
                    case 16 -> {
                        owner = "column-hop";
                        column = magneticColumn(LOWER_CRANE_COLUMN_ONE_X, 0x0790);
                        boolean onColumn = column != null && latch == column
                                && player.isOnObject();
                        if (onColumn) {
                            stage = 17;
                        } else if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = 0;
                        } else if (column != null && column.displacement() == 0
                                && !column.lastMagneticActive()
                                && Math.abs(x - LOWER_CRANE_COLUMN_HOP_X) <= 2
                                && player.getGSpeed() == 0) {
                            hopAttempts++;
                            assertTrue(hopAttempts <= 3,
                                    () -> waypointDiagnostic("column-hop-failed", x));
                            hold = LOWER_CRANE_COLUMN_HOP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = steerMaskWalking(player, LOWER_CRANE_COLUMN_HOP_X, 2);
                        }
                    }
                    case 17 -> {
                        // Ride loc_3B450's ACTIVE rise; leave from the top with the
                        // half-cycle runway both hops need.
                        owner = "column-ride";
                        boolean onColumn = column != null && latch == column
                                && player.isOnObject();
                        if (!onColumn && grounded && y > 0x0700) {
                            stage = 16;
                        } else if (onColumn && activePolarity
                                && y <= LOWER_CRANE_COLUMN_TOP_MAX_Y
                                && runway >= LOWER_CRANE_COLUMN_JUMP_MIN_RUNWAY) {
                            columnJumps = 1;
                            hold = LOWER_CRANE_COLUMN_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                            stage = 18;
                        } else if (onColumn) {
                            mask = steerMaskWalking(player, LOWER_CRANE_COLUMN_ONE_X, 4);
                        }
                    }
                    case 18 -> {
                        owner = "column-jumps";
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else if (x >= LOWER_CRANE_ARENA_CORRIDOR_MIN_X
                                && y <= LOWER_CRANE_ARENA_CORRIDOR_MAX_Y) {
                            stage = 19;
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        } else {
                            assertTrue(y <= 0x0700,
                                    () -> waypointDiagnostic("column-jump-fell-short", x));
                            columnJumps++;
                            assertTrue(columnJumps <= 3,
                                    () -> waypointDiagnostic("column-jumps-exhausted", x));
                            hold = LOWER_CRANE_COLUMN_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        }
                    }
                    case 19 -> {
                        owner = "arena-corridor";
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && !pitJumped && x >= LOWER_CRANE_PIT_JUMP_X) {
                            pitJumped = true;
                            hold = LOWER_CRANE_PIT_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                        if (grounded && x >= LOWER_CRANE_ARENA_ARRIVAL_X) {
                            lastControllerDiagnostic = " craneRoute={arrived,frame=" + frame + '}';
                            return false;
                        }
                    }
                    default -> throw new IllegalStateException("stage " + stage);
                }
                if (stage != before) stageFrames = 0; else stageFrames++;
                lastControllerDiagnostic = " craneRoute={stage=" + stage
                        + ",owner=" + owner + ",hold=" + hold
                        + ",stageFrames=" + stageFrames
                        + ",crane=" + crane.stateName()
                        + ",chain=" + (chain == null ? "none" : objectPosition(chain))
                        + ",latch=" + (latch == null ? "none" : latch.getClass().getSimpleName())
                        + ",column=" + objectPosition(column)
                        + ",polarity=" + (activePolarity ? "ACTIVE" : "INACTIVE")
                        + ",runway=$" + Integer.toHexString(runway)
                        + ",mask=$" + Integer.toHexString(mask) + '}';
                assertTrue(stageFrames <= LOWER_CRANE_ROUTE_STAGE_LIMIT,
                        () -> waypointDiagnostic("lower-crane-route-stalled-" + owner, x));
                stepCheckedFrame(mask, check);
            }
            fail(waypointDiagnostic("lower-crane-route-frame-limit", LOWER_CRANE_ARENA_ARRIVAL_X));
            return false;
        }

        /**
         * From the subboss arena wall to the SOZ request: survive the seven
         * Obj_FBZ2Subboss laser cycles by running past the machine to the far
         * live side wall, hop the arena exit, ride the boss-event plane carrier
         * at the open ship-column offsets, cross the Obj_FBZEndBoss pod with a
         * run-up while it rises so each strike lands from above, stand on the
         * end capsule button, and leave the exit walk to the boss-owned forced
         * input. Returns true only when {@code stop} is reached.
         */
        private boolean runArenaToExit(FrameCheck check, StopCondition stop) {
            int stage = 0;
            int stageFrames = 0;
            int hold = 0;
            int direction = 0;
            int lastPodY = -1;
            int chargeStep = 0;
            boolean podWasVulnerable = false;
            int podHits = 0;
            for (int frame = 0; frame < ARENA_ROUTE_FRAME_LIMIT; frame++) {
                if (stop.reached()) return true;
                AbstractPlayableSprite player = fixture.sprite();
                int x = player.getCentreX() & 0xFFFF;
                int y = player.getCentreY() & 0xFFFF;
                boolean grounded = !player.getAir();
                int mask = 0;
                String owner;
                int before = stage;
                switch (stage) {
                    case 0 -> {
                        owner = "subboss-dodge";
                        Fbz2SubbossInstance subboss = objects.activeObjectsOfType(
                                Fbz2SubbossInstance.class).stream().findFirst().orElse(null);
                        boolean cleared = subboss == null
                                || subboss.phaseName().startsWith("DEFEAT")
                                || subboss.phaseName().startsWith("RELEASE");
                        if (cleared) {
                            stage = 1;
                        } else {
                            int machineX = subboss.getX();
                            int leftWall = SUBBOSS_ARENA_LEFT_WALL_X;
                            int rightWall = SUBBOSS_ARENA_RIGHT_WALL_X;
                            for (Fbz2SubbossSolidSideChild wall : objects.activeObjectsOfType(
                                    Fbz2SubbossSolidSideChild.class)) {
                                if (wall.getX() < machineX) leftWall = Math.max(leftWall, wall.getX());
                                else rightWall = Math.min(rightWall, wall.getX());
                            }
                            int leftStand = leftWall + SUBBOSS_WALL_HALF_WIDTH + SUBBOSS_WALL_STAND_GAP;
                            int rightStand = rightWall - SUBBOSS_WALL_HALF_WIDTH - SUBBOSS_WALL_STAND_GAP;
                            if (direction == 0 && Math.abs(machineX - x) < SUBBOSS_KEEP_AWAY) {
                                direction = machineX - leftStand > rightStand - machineX
                                        ? AbstractPlayableSprite.INPUT_LEFT
                                        : AbstractPlayableSprite.INPUT_RIGHT;
                            }
                            if (direction == AbstractPlayableSprite.INPUT_RIGHT && x >= rightStand) direction = 0;
                            if (direction == AbstractPlayableSprite.INPUT_LEFT && x <= leftStand) direction = 0;
                            mask = direction;
                        }
                    }
                    case 1 -> {
                        // The arena's east lip needs one hop; then ride the carrier.
                        owner = "arena-exit";
                        FbzEndBossEventControlInstance carrier = objects.activeObjectsOfType(
                                FbzEndBossEventControlInstance.class).stream().findFirst().orElse(null);
                        boolean carrierMoving = carrier != null
                                && (carrier.getX() != PLANE_CONTROLLER_ORIGIN_X
                                || carrier.getY() != PLANE_CONTROLLER_ORIGIN_Y);
                        if (carrierMoving) {
                            stage = 2;
                        } else if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (grounded && x >= ARENA_EXIT_HOP_MIN_X && x < ARENA_EXIT_HOP_MAX_X
                                && Math.abs(player.getGSpeed()) < 0x80) {
                            hold = ARENA_EXIT_HOP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT;
                        }
                    }
                    case 2 -> {
                        owner = "plane-ride";
                        if (!objects.activeObjectsOfType(FbzEndBossInstance.class).isEmpty()) {
                            stage = 3;
                        } else {
                            FbzEndBossEventControlInstance carrier = objects.activeObjectsOfType(
                                    FbzEndBossEventControlInstance.class).stream().findFirst().orElse(null);
                            assertNotNull(carrier, () -> waypointDiagnostic("plane-carrier-missing", x));
                            int carrierX = carrier.getX() & 0xFFFF;
                            int carrierY = carrier.getY() & 0xFFFF;
                            int targetX = carrierX + (carrierY > PLANE_RIDE_OFFSET_SWITCH_Y
                                    ? PLANE_RIDE_LOWER_OFFSET_X : PLANE_RIDE_UPPER_OFFSET_X);
                            int delta = targetX - x;
                            mask = grounded ? RouteSteering.walkMask(player, targetX, 4,
                                    Math.abs(delta) > 0x60 ? 0x600 : 0x180) : 0;
                        }
                    }
                    case 3 -> {
                        owner = "end-boss";
                        FbzEndBossInstance boss = objects.activeObjectsOfType(
                                FbzEndBossInstance.class).stream().findFirst().orElse(null);
                        assertNotNull(boss, () -> waypointDiagnostic("end-boss-missing", x));
                        if (boss.phase().ordinal() >= FbzEndBossInstance.Phase.DEFEAT_RECENTER.ordinal()) {
                            stage = 4;
                            break;
                        }
                        int podX = boss.getX() & 0xFFFF;
                        int podY = boss.getY() & 0xFFFF;
                        boolean vulnerable = boss.getCollisionFlags() != 0;
                        if (podWasVulnerable && !vulnerable) podHits++;
                        podWasVulnerable = vulnerable;
                        boolean rising = lastPodY >= 0 && podY < lastPodY;
                        lastPodY = podY;
                        boolean rotation = boss.phase() == FbzEndBossInstance.Phase.ROTATION;
                        boolean fighting = rotation || boss.phase() == FbzEndBossInstance.Phase.ATTACK;
                        int side = x < podX ? 1 : -1;
                        int toward = side > 0 ? AbstractPlayableSprite.INPUT_RIGHT
                                : AbstractPlayableSprite.INPUT_LEFT;
                        if (hold > 0) {
                            // Release frame first (Sonic_Spindash leaves the crouch),
                            // then hold JUMP with the launch direction.
                            hold--;
                            mask = (hold == END_BOSS_LAUNCH_HOLD - 1 ? 0 : AbstractPlayableSprite.INPUT_JUMP)
                                    | direction;
                        } else if (!grounded) {
                            mask = direction;
                        } else if (!fighting) {
                            // Wait at the west wall while the boss descends and opens.
                            chargeStep = 0;
                            mask = RouteSteering.walkMask(player, END_BOSS_ARENA_LEFT_STAND_X, 6, 0x180);
                        } else {
                            boolean windowOpen = vulnerable && rising
                                    && podY >= END_BOSS_POD_WINDOW_MIN_Y
                                    && podY <= END_BOSS_POD_WINDOW_MAX_Y;
                            int wallX = side > 0 ? END_BOSS_ARENA_LEFT_STAND_X
                                    : END_BOSS_ARENA_RIGHT_STAND_X;
                            int room = Math.abs(podX - wallX);
                            boolean crossNow = !rotation && room < END_BOSS_CROSS_ROOM;
                            int waitX = !rotation ? wallX : side > 0
                                    ? Math.max(wallX, podX - END_BOSS_WAIT_DISTANCE)
                                    : Math.min(wallX, podX + END_BOSS_WAIT_DISTANCE);
                            if (chargeStep == 0) {
                                mask = RouteSteering.walkMask(player, waitX, 6, 0x300);
                                if (Math.abs(x - waitX) <= 6 && player.getGSpeed() == 0
                                        && (rotation || crossNow)) {
                                    chargeStep = 1;
                                }
                            } else if (chargeStep == 1) {
                                // Sonic_Spindash releases along the facing direction.
                                mask = toward;
                                chargeStep = 2;
                            } else if (chargeStep == 2) {
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                                if (player.getCrouching()) chargeStep = 3;
                            } else if (chargeStep < 3 + END_BOSS_CHARGE_TAPS * 2) {
                                boolean tap = ((chargeStep - 3) & 1) == 0;
                                mask = AbstractPlayableSprite.INPUT_DOWN
                                        | (tap ? AbstractPlayableSprite.INPUT_JUMP : 0);
                                chargeStep++;
                            } else if (!player.getSpindash()) {
                                chargeStep = 0;
                            } else if (windowOpen || crossNow) {
                                hold = END_BOSS_LAUNCH_HOLD;
                                direction = toward;
                                chargeStep = 0;
                                mask = 0;
                            } else {
                                mask = AbstractPlayableSprite.INPUT_DOWN;
                            }
                        }
                    }
                    case 4 -> {
                        owner = "end-capsule";
                        boolean capsuleLive = !objects.activeObjectsOfType(
                                FbzEndEggCapsuleInstance.class).isEmpty();
                        if (hold > 0) {
                            hold--;
                            mask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (!grounded) {
                            mask = 0;
                        } else if (player.isOnObject()
                                && player.getLatchedSolidObjectInstance()
                                instanceof FbzEndEggCapsuleButtonInstance) {
                            stage = 5;
                        } else if (capsuleLive && Math.abs(x - END_CAPSULE_APPROACH_X) <= 4
                                && player.getGSpeed() == 0) {
                            hold = END_CAPSULE_JUMP_HOLD;
                            mask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
                        } else if (capsuleLive) {
                            mask = RouteSteering.walkMask(player, END_CAPSULE_APPROACH_X, 4, 0x180);
                        }
                    }
                    case 5 -> {
                        // loc_7092A: the boss owns the forced RIGHT walk to the exit.
                        owner = "forced-exit";
                        mask = 0;
                    }
                    default -> throw new IllegalStateException("stage " + stage);
                }
                if (stage != before) stageFrames = 0; else stageFrames++;
                lastControllerDiagnostic = " arenaRoute={stage=" + stage + ",owner=" + owner
                        + ",hold=" + hold + ",direction=" + direction + ",chargeStep=" + chargeStep
                        + ",podHits=" + podHits + ",stageFrames=" + stageFrames
                        + ",mask=$" + Integer.toHexString(mask) + '}';
                assertTrue(stageFrames <= ARENA_ROUTE_STAGE_LIMIT,
                        () -> waypointDiagnostic("arena-route-stalled-" + owner, x));
                stepCheckedFrame(mask, check);
            }
            fail(waypointDiagnostic("arena-route-frame-limit", END_CAPSULE_X));
            return false;
        }

        /**
         * Consumes the ordinary approach only until P1 owns the real $08C0
         * elevator car, then holds that identity for exactly the fixed segment
         * being replaced. This prevents a donor profile that reaches the car
         * early from consuming the remaining approach cadence and overshooting
         * into the later wall loop. The two subtype-$40 spike columns have
         * observed native contact thresholds $089B and $08E5, leaving the
         * inclusive safe centre interval $089C..$08E4.
         */
        private void rideMidpointCarThroughSpikeCorridor(
                List<InputRun> approachRuns, int frameLimit, FrameCheck check) {
            final int targetX = 0x08C0;
            final int safeMinX = 0x089C;
            final int safeMaxX = 0x08E4;
            final int authoredCorridorStartY = 0x07BC;
            final int approachEnvelope = 0x60;
            final int acquisitionFrameLimit = 0x800;
            final int alignmentFrameLimit = 0x100;
            boolean rideObserved = runUntilMidpointApproach(
                    approachRuns, check, targetX, approachEnvelope);
            if (!ownsExactCar(fixture.sprite(), targetX)) {
                rideObserved = acquireMidpointCar(
                        targetX, safeMinX, safeMaxX, acquisitionFrameLimit, check);
            }

            int alignmentFrames = 0;
            while (Math.abs((fixture.sprite().getCentreX() & 0xFFFF) - targetX) > 2
                    || Math.abs(fixture.sprite().getXSpeed()) > 0x80) {
                AbstractPlayableSprite before = fixture.sprite();
                AbstractPlayableSprite player = stepCheckedFrame(
                        RouteSteering.steerMask(before, targetX, 2), check);
                alignmentFrames++;
                String diagnostic = waypointDiagnostic("spike-corridor-alignment", targetX);
                assertFalse(player.getDead() || player.isHurt(), diagnostic);
                assertTrue(ownsExactCar(player, targetX),
                        () -> diagnostic + " lost exact midpoint car during bounded alignment");
                if ((player.getCentreY() & 0xFFFF) >= authoredCorridorStartY) {
                    int playerX = player.getCentreX() & 0xFFFF;
                    assertTrue(playerX >= safeMinX && playerX <= safeMaxX,
                            () -> diagnostic + " entered protected Y outside safe interval");
                }
                assertTrue(alignmentFrames <= alignmentFrameLimit,
                        () -> diagnostic + " frameLimit=" + alignmentFrameLimit);
            }

            // Complete-run BK2 $624A-$62C3 is the exact 122-frame protected
            // segment: P1 begins at y=$07BC and RIGHT starts at $62C4/y=$0836.
            // Donated movement profiles can acquire the same car earlier, so
            // retain it neutrally until the authored vertical phase before
            // consuming those fixed frames.
            while ((fixture.sprite().getCentreY() & 0xFFFF) < authoredCorridorStartY) {
                AbstractPlayableSprite before = fixture.sprite();
                AbstractPlayableSprite player = stepCheckedFrame(
                        RouteSteering.steerMask(before, targetX, 2), check);
                int playerX = player.getCentreX() & 0xFFFF;
                String diagnostic = waypointDiagnostic("spike-corridor-phase", targetX);
                assertFalse(player.getDead() || player.isHurt(), diagnostic);
                assertTrue(playerX >= safeMinX && playerX <= safeMaxX,
                        () -> diagnostic + " left safe centre interval");
                Object owner = player.getLatchedSolidObjectInstance();
                assertTrue(player.isOnObject()
                                && owner instanceof FbzElevatorObjectInstance.Car car
                                && car.getCentreX() == targetX,
                        () -> diagnostic + " lost authored midpoint car");
            }
            assertEquals(authoredCorridorStartY,
                    fixture.sprite().getCentreY() & 0xFFFF,
                    () -> waypointDiagnostic("spike-corridor-phase-start", targetX));

            for (int i = 0; i < frameLimit; i++) {
                AbstractPlayableSprite before = fixture.sprite();
                AbstractPlayableSprite player = stepCheckedFrame(
                        RouteSteering.steerMask(before, targetX, 2), check);
                int playerX = player.getCentreX() & 0xFFFF;
                String diagnostic = waypointDiagnostic("spike-corridor", targetX);
                assertFalse(player.getDead() || player.isHurt(), diagnostic);
                assertTrue(playerX >= safeMinX && playerX <= safeMaxX,
                        () -> diagnostic + " left safe centre interval");

                Object owner = player.getLatchedSolidObjectInstance();
                if (player.isOnObject()
                        && owner instanceof FbzElevatorObjectInstance.Car car
                        && car.getCentreX() == targetX) {
                    rideObserved = true;
                }
            }

            AbstractPlayableSprite player = fixture.sprite();
            Object owner = player.getLatchedSolidObjectInstance();
            assertTrue(rideObserved, () -> waypointDiagnostic(
                    "spike-corridor-no-car-ride", targetX));
            assertTrue(player.isOnObject()
                            && owner instanceof FbzElevatorObjectInstance.Car car
                            && car.getCentreX() == targetX,
                    () -> waypointDiagnostic("spike-corridor-exit", targetX));
            assertTrue(Math.abs((player.getCentreX() & 0xFFFF) - targetX) <= 2,
                    () -> waypointDiagnostic("spike-corridor-not-centred", targetX));
        }

        /**
         * Consumes the pre-midpoint source masks while owning the real rising
         * $06C0-car hazard in the phase where it physically occurs. The caller
         * includes the preceding lower-backtrack cycles so donated profiles
         * cannot pass the $08C0 approach before this controller is active.
         */
        private boolean runUntilMidpointApproach(
                List<InputRun> approachRuns, FrameCheck check,
                int midpointX, int approachEnvelope) {
            final int risingCarTargetX = 0x06C9;
            final int risingCarApproachMinX = 0x0660;
            final int risingCarApproachMaxX = 0x0730;
            final int risingCarExitY = 0x08BB;
            final int risingCarJumpX = 0x06E7;
            final int risingCarSafeMinX = 0x069C;
            final int risingCarSafeMaxX = 0x06E4;
            FbzElevatorObjectInstance.Car risingCar = null;
            boolean risingAcquisitionArmed = false;
            boolean risingExitStarted = false;
            boolean risingExitJumpPressed = false;

            for (InputRun run : approachRuns) {
                for (int frame = 0; frame < run.frames(); frame++) {
                    AbstractPlayableSprite player = fixture.sprite();
                    int playerX = player.getCentreX() & 0xFFFF;
                    int playerY = player.getCentreY() & 0xFFFF;
                    boolean exactMidpointCarApproach =
                            objects.activeObjectsOfType(
                                            FbzElevatorObjectInstance.Car.class).stream()
                                    .filter(car -> car.getCentreX() == midpointX)
                                    .filter(car -> car.yVelocity() == 1)
                                    .anyMatch(car -> Math.abs(
                                            car.getCentreY() - playerY) <= 0x100)
                                    && playerX >= midpointX - approachEnvelope
                                    && playerX <= midpointX + 0xC0;
                    if (ownsExactCar(player, midpointX) || exactMidpointCarApproach) {
                        return true;
                    }

                    Object owner = player.getLatchedSolidObjectInstance();
                    if (!risingExitStarted && player.isOnObject()
                            && owner instanceof FbzElevatorObjectInstance.Car car
                            && car.getCentreX() == 0x06C0 && car.yVelocity() == -1) {
                        risingCar = car;
                        risingAcquisitionArmed = false;
                    }
                    boolean exactRisingCarActive = objects.activeObjectsOfType(
                                    FbzElevatorObjectInstance.Car.class).stream()
                            .anyMatch(car -> car.getCentreX() == 0x06C0
                                    && car.yVelocity() == -1);
                    risingAcquisitionArmed |= !risingExitStarted && risingCar == null
                            && playerX >= risingCarApproachMinX
                            && playerX <= risingCarApproachMaxX
                            && exactRisingCarActive;

                    int mask;
                    if (risingExitStarted) {
                        if (!risingExitJumpPressed && !player.getAir()
                                && playerX >= risingCarJumpX) {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | AbstractPlayableSprite.INPUT_JUMP;
                            risingExitJumpPressed = true;
                        } else {
                            mask = AbstractPlayableSprite.INPUT_RIGHT
                                    | (player.getAir() && player.getYSpeed() < 0
                                    ? AbstractPlayableSprite.INPUT_JUMP : 0);
                        }
                        if (playerX >= risingCarApproachMaxX) {
                            risingExitStarted = false;
                        }
                    } else if (risingCar != null
                            && (player.getCentreY() & 0xFFFF) <= risingCarExitY) {
                        risingCar = null;
                        risingExitStarted = true;
                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                    } else if (risingCar != null || risingAcquisitionArmed) {
                        mask = RouteSteering.steerMask(player, risingCarTargetX, 2);
                    } else {
                        mask = run.mask();
                    }
                    AbstractPlayableSprite after = stepCheckedFrame(mask, check);
                    if (risingCar != null) {
                        int afterX = after.getCentreX() & 0xFFFF;
                        int afterY = after.getCentreY() & 0xFFFF;
                        String diagnostic = waypointDiagnostic(
                                "rising-elevator-approach", 0x06C0);
                        assertFalse(after.getDead() || after.isHurt(), diagnostic);
                        if (afterY <= 0x0930) {
                            assertTrue(afterX >= risingCarSafeMinX
                                            && afterX <= risingCarSafeMaxX,
                                    () -> diagnostic + " left safe centre interval");
                        }
                        assertTrue(after.isOnObject()
                                        && after.getLatchedSolidObjectInstance() == risingCar,
                                () -> diagnostic + " lost retained-car identity");
                        assertEquals(-1, risingCar.yVelocity(),
                                diagnostic + " car stopped rising");
                    }
                }
            }
            return false;
        }

        /**
         * Donated movement profiles can reach the fixed corridor with a
         * different approach velocity. Brake/reverse using ordinary inputs
         * until P1 owns the real $08C0 car inside the unchanged safe interval;
         * the exact 122-frame corridor begins only after that acquisition.
         */
        private boolean acquireMidpointCar(int targetX, int safeMinX, int safeMaxX,
                                           int frameLimit, FrameCheck check) {
            boolean stagedInsideSafeEdge = false;
            final int upperLoopMinX = 0x0A40;
            final int upperLoopMaxX = 0x0A70;
            final int upperLoopMinY = 0x0740;
            final int upperLoopMaxY = 0x0780;
            final int nativeUpperReleaseSpeed = -0x0800;
            int upperLoopSpindashStage = 0;
            int upperLoopStageFrames = 0;
            boolean upperLoopLaunchCommitted = false;
            int upperLoopEgressStage = 0;
            int upperLoopJumpFrames = 0;
            int upperLoopJumpAttempts = 0;
            final int nativeStandSpotX = 0x092B;
            int standSpotFrames = 0;
            final int nativeJumpSpotX = 0x0911;
            final int nativeWalkPhaseMin = -0x19;
            final int nativeWalkPhaseMax = -0x0F;
            StringBuilder upperLoopS1ApproachLog = new StringBuilder();
            for (int frame = 0; frame < frameLimit; frame++) {
                AbstractPlayableSprite before = fixture.sprite();
                int beforeX = before.getCentreX() & 0xFFFF;
                int beforeY = before.getCentreY() & 0xFFFF;
                if (beforeX <= safeMinX) {
                    stagedInsideSafeEdge = true;
                }
                boolean spindashEnabled = before.getGameRules() != null
                        && before.getGameRules().playerCapability() != null
                        && before.getGameRules().playerCapability().spindashEnabled();
                boolean inUpperLoopApproach = beforeX >= upperLoopMinX
                        && beforeX <= upperLoopMaxX
                        && beforeY >= upperLoopMinY && beforeY <= upperLoopMaxY;
                boolean inUpperLoopBrakeRange = beforeX >= upperLoopMinX
                        && beforeX <= 0x0A98
                        && beforeY >= upperLoopMinY && beforeY <= upperLoopMaxY;
                boolean upperS1AssistConsumed = GameServices.zoneRuntimeRegistry()
                        .currentAs(FbzZoneRuntimeState.class)
                        .map(runtime -> runtime.s1DonationUpperLoopAssistState()
                                == FbzZoneRuntimeState.S1DonationUpperLoopAssistState.CONSUMED)
                        .orElse(false);
                if (!spindashEnabled && inUpperLoopApproach && !before.getAir()) {
                    upperLoopS1ApproachLog.append(String.format(
                            " f%d:%04X/%04X g=%04X mode=%s",
                            frames, beforeX, beforeY,
                            before.getGSpeed() & 0xFFFF, before.getGroundMode()));
                }
                FbzElevatorObjectInstance.Car liveMidpointCar =
                        objects.activeObjectsOfType(
                                        FbzElevatorObjectInstance.Car.class).stream()
                                .filter(car -> car.getCentreX() == targetX)
                                .filter(car -> car.yVelocity() == 1)
                                .findFirst().orElse(null);
                boolean liveMidpointCarReachable = liveMidpointCar != null
                        && liveMidpointCar.getCentreY() >= beforeY
                        && liveMidpointCar.getCentreY() - beforeY <= 0x100;
                boolean alreadyPastUpperLoop = !upperLoopLaunchCommitted
                        && beforeY <= 0x06D0
                        && beforeX >= 0x08E0 && beforeX <= 0x0980
                        && liveMidpointCarReachable;
                int mask;
                if (alreadyPastUpperLoop) {
                    // The uninterrupted native+Tails row can enter this
                    // controller at the measured post-loop $092B/$06AC car
                    // approach: its source cadence already executed the upper
                    // launch before runUntilMidpointApproach observed the live
                    // $08C0 car. Preserve that geometry as controller phase,
                    // then use the same ordinary run-up/retry stages as rows
                    // which entered the narrow launch envelope here.
                    upperLoopLaunchCommitted = true;
                    upperLoopEgressStage = 4;
                    mask = AbstractPlayableSprite.INPUT_RIGHT;
                } else if (!upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 0 && inUpperLoopBrakeRange
                        && !before.getAir()) {
                    if (beforeX > 0x0A52 && before.getGSpeed() < -0x80) {
                        mask = AbstractPlayableSprite.INPUT_RIGHT;
                    } else if (beforeX < 0x0A48 && before.getGSpeed() > 0x80) {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else if (!inUpperLoopApproach) {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else if (Math.abs(before.getGSpeed()) > 0x80) {
                        mask = before.getGSpeed() < 0
                                ? AbstractPlayableSprite.INPUT_RIGHT
                                : AbstractPlayableSprite.INPUT_LEFT;
                    } else {
                        upperLoopSpindashStage = 1;
                        upperLoopStageFrames = 1;
                        mask = AbstractPlayableSprite.INPUT_DOWN;
                    }
                } else if (!upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 1) {
                    // Complete-run BK2 $604F-$6053: five DOWN frames.
                    mask = AbstractPlayableSprite.INPUT_DOWN;
                    if (++upperLoopStageFrames >= 5) {
                        upperLoopSpindashStage = 2;
                        upperLoopStageFrames = 0;
                    }
                } else if (!upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 2) {
                    // Complete-run BK2 $6054-$6059: six DOWN+JUMP frames.
                    mask = AbstractPlayableSprite.INPUT_DOWN
                            | AbstractPlayableSprite.INPUT_JUMP;
                    if (++upperLoopStageFrames >= 6) {
                        upperLoopSpindashStage = 3;
                        upperLoopStageFrames = 0;
                    }
                } else if (!upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 3) {
                    // Complete-run BK2 $605A retains DOWN for one frame.
                    mask = AbstractPlayableSprite.INPUT_DOWN;
                    upperLoopSpindashStage = 4;
                } else if (!upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 4) {
                    // Complete-run BK2 $605B releases neutral at ground speed
                    // $F800. The loop traversal remains neutral after release.
                    mask = 0;
                    upperLoopLaunchCommitted = true;
                } else if (!upperLoopLaunchCommitted && !spindashEnabled
                        && upperS1AssistConsumed) {
                    // The event update follows the eligible ordinary LEFT
                    // request and can carry P1 outside the narrow envelope in
                    // that same frame. Commit the native neutral traversal on
                    // the next input frame; the typed runtime evidence proves
                    // that production, rather than the harness, supplied it.
                    upperLoopLaunchCommitted = true;
                    mask = 0;
                } else if (!upperLoopLaunchCommitted && !spindashEnabled
                        && inUpperLoopApproach && !before.getAir()) {
                    // S1 donation compatibility is production-owned: ordinary
                    // LEFT is the only activation input. The zone event supplies
                    // the independently serialized $F800 speed floor.
                    mask = AbstractPlayableSprite.INPUT_LEFT;
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 0) {
                    // Complete-run BK2 remains neutral around the left wall
                    // ($0A2A/$075D) and over the loop apex. LEFT begins only
                    // after the neutral launch reaches about $0A0E/$06C7.
                    if (beforeY <= 0x06D0) {
                        upperLoopEgressStage = 1;
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else {
                        mask = 0;
                    }
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 1) {
                    // Complete-run BK2 rows 24858-24861 walk LEFT to $0911
                    // before the single jump. Hold that native jump spot
                    // (stage 4) instead of walking off the ledge: a walk-off
                    // that misses the car lands on the $0880 spike-column top,
                    // and a CPU sidekick following it there is hurt by the
                    // spike side, drops onto the car that has already passed,
                    // and is later crushed on the shaft floor by P1's car.
                    if (!before.getAir() && beforeX <= 0x0930
                            && before.getGSpeed() > -0x0180) {
                        upperLoopEgressStage = 4;
                        mask = RouteSteering.steerMask(before, nativeStandSpotX, 2);
                    } else {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    }
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 2) {
                    // Complete-run BK2 rows 24862-24870: nine LEFT+JUMP frames
                    // (the released short hop peaks at y=$068E), then LEFT
                    // until P1 lands on the car at row 24882 ($08D9/$06A0).
                    mask = AbstractPlayableSprite.INPUT_LEFT
                            | AbstractPlayableSprite.INPUT_JUMP;
                    upperLoopJumpFrames++;
                    if (upperLoopJumpFrames >= 9) {
                        upperLoopEgressStage = 3;
                        upperLoopJumpFrames = 0;
                    }
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 3) {
                    if (!before.getAir() && !ownsExactCar(before, targetX)) {
                        // Landed on the ledge again: re-arm the phase wait.
                        upperLoopJumpAttempts++;
                        assertTrue(upperLoopJumpAttempts < 4,
                                () -> waypointDiagnostic(
                                        "upper-loop-jump-attempt-limit", 0x095A));
                        upperLoopEgressStage = 4;
                        mask = RouteSteering.steerMask(before, nativeStandSpotX, 2);
                    } else {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    }
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 4) {
                    // Complete-run BK2 rows 24792-24861: P1 stands at $092B,
                    // then walks LEFT from rest for the ~33 frames that reach
                    // $0911 at g=$FE74 and jumps there. Wait standing at $092B
                    // for the car phase that puts the descending $08C0 car
                    // centre $08-$12 below P1's centre at the jump frame (row
                    // 24882 lands P1 at y=$06A0 on the car top $06B4, centre
                    // $06C8, twenty frames after the jump: centre $06B4 versus
                    // P1 $06AC at the jump). A car four pixels higher than the
                    // native phase meets P1's $13 radius at its $3B side edge
                    // instead of its top; cars are $60 apart at 1px/frame, so
                    // a missed phase returns within 96 frames.
                    boolean atStandSpot = !before.getAir()
                            && Math.abs(beforeX - nativeStandSpotX) <= 2
                            && Math.abs(before.getGSpeed()) <= 0x0040;
                    final int standY = beforeY;
                    boolean carInWalkPhase = objects.activeObjectsOfType(
                                    FbzElevatorObjectInstance.Car.class).stream()
                            .filter(car -> car.getCentreX() == targetX)
                            .filter(car -> car.yVelocity() == 1)
                            .mapToInt(car -> car.getCentreY() - standY)
                            .anyMatch(phase -> phase >= nativeWalkPhaseMin
                                    && phase <= nativeWalkPhaseMax);
                    // A CPU sidekick left behind the upper loop (it cannot
                    // spindash) only rejoins by Tails_CPU_Control's fly-in
                    // respawn; if P1 jumps first that respawn lands inside the
                    // $08C0 car column and the next car crushes it. Stand at
                    // the native spot until every sidekick is grounded beside
                    // P1, then take the next matching car phase.
                    if (atStandSpot) {
                        standSpotFrames++;
                    }
                    boolean sidekicksGathered = sidekicksGatheredBeside(beforeX)
                            || standSpotFrames > SIDEKICK_GATHER_FRAME_LIMIT;
                    if (atStandSpot && carInWalkPhase && sidekicksGathered) {
                        upperLoopEgressStage = 6;
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else if (before.getAir()) {
                        mask = beforeX < nativeStandSpotX
                                ? AbstractPlayableSprite.INPUT_RIGHT
                                : AbstractPlayableSprite.INPUT_LEFT;
                    } else {
                        mask = RouteSteering.steerMask(before, nativeStandSpotX, 2);
                    }
                } else if (upperLoopLaunchCommitted && upperLoopEgressStage == 6) {
                    // Native walk-up: LEFT until $0911, then the jump.
                    if (before.getAir()) {
                        upperLoopEgressStage = 3;
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    } else if (beforeX <= nativeJumpSpotX) {
                        upperLoopEgressStage = 2;
                        upperLoopJumpFrames = 1;
                        mask = AbstractPlayableSprite.INPUT_LEFT
                                | AbstractPlayableSprite.INPUT_JUMP;
                    } else {
                        mask = AbstractPlayableSprite.INPUT_LEFT;
                    }
                } else {
                    int steeringTarget = stagedInsideSafeEdge ? targetX : safeMinX - 0x10;
                    mask = RouteSteering.steerMask(before, steeringTarget, 2);
                }
                boolean exactUpperLoopBrake = !upperLoopLaunchCommitted
                        && spindashEnabled && upperLoopSpindashStage == 0
                        && inUpperLoopBrakeRange && !before.getAir()
                        && before.getGSpeed() < -0x80;
                boolean exactUpperLoopRunUp = upperLoopLaunchCommitted
                        && upperLoopEgressStage == 4;
                assertFalse(beforeX > safeMaxX
                                && (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0
                                && !exactUpperLoopBrake && !exactUpperLoopRunUp,
                        () -> waypointDiagnostic("spike-corridor-wrong-way-input", targetX));
                AbstractPlayableSprite player = stepCheckedFrame(
                        mask, check);
                String diagnostic = waypointDiagnostic("spike-corridor-acquisition", targetX);
                assertFalse(player.getDead() || player.isHurt(), diagnostic);
                if (upperLoopSpindashStage >= 3 && spindashEnabled
                        && !upperLoopLaunchCommitted) {
                    assertTrue(player.getSpindash(),
                            () -> diagnostic + " native upper-loop input did not arm spindash");
                }
                if (upperLoopLaunchCommitted && spindashEnabled
                        && upperLoopSpindashStage == 4) {
                    assertTrue(player.getGSpeed() <= nativeUpperReleaseSpeed,
                            () -> diagnostic + " upper-loop release speed=$"
                                    + Integer.toHexString(player.getGSpeed() & 0xFFFF));
                    upperLoopSpindashStage = 5;
                }
                int playerX = player.getCentreX() & 0xFFFF;
                Object owner = player.getLatchedSolidObjectInstance();
                if (playerX >= safeMinX && playerX <= safeMaxX
                        && player.isOnObject()
                        && owner instanceof FbzElevatorObjectInstance.Car car
                        && car.getCentreX() == targetX) {
                    return true;
                }
            }
            fail(waypointDiagnostic("spike-corridor-car-not-acquired", targetX)
                    + " frameLimit=" + frameLimit
                    + " upperLaunch=" + upperLoopLaunchCommitted
                    + " upperSpindashStage=" + upperLoopSpindashStage
                    + " upperEgressStage=" + upperLoopEgressStage
                    + " upperJumpFrames=" + upperLoopJumpFrames
                    + " upperJumpAttempts=" + upperLoopJumpAttempts
                    + " s1Approach=" + upperLoopS1ApproachLog);
            return false;
        }

        private static boolean ownsExactCar(AbstractPlayableSprite player, int targetX) {
            Object owner = player.getLatchedSolidObjectInstance();
            return player.isOnObject()
                    && owner instanceof FbzElevatorObjectInstance.Car car
                    && car.getCentreX() == targetX;
        }

        private AbstractPlayableSprite stepCheckedFrame(int mask, FrameCheck check) {
            lifetime.beginFrame(objects);
            AbstractPlayableSprite player = fixture.sprite();
            observer.observe(lifetime.active(), lifetime.previous(), player, objects);
            InputProgram.step(fixture, mask);
            frames++;
            recordRecentFrame(mask, player);
            check.afterFrame(this, player);
            lifetime.endFrame();
            return player;
        }

        private static void assertSqueezePosture(AbstractPlayableSprite player,
                                                  boolean carAcquired,
                                                  Sonic3kInvisibleBlockObjectInstance block) {
            // RideObject_SetRide calls Player_TouchFloor for an airborne landing.
            // The retail FBZ run unrolls on the car; requiring rolling for the
            // whole crossing contradicts that native landing contract.
            if (!carAcquired) {
                assertTrue(player.getRolling(), () -> "squeeze release must roll until car landing"
                        + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                        + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF)
                        + ") speed=($" + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                        + ",$" + Integer.toHexString(player.getYSpeed() & 0xFFFF)
                        + ") g=$" + Integer.toHexString(player.getGSpeed() & 0xFFFF)
                        + " air=" + player.getAir() + " onObject=" + player.isOnObject()
                        + " latch=" + objectPosition(player.getLatchedSolidObjectInstance())
                        + " block=" + objectPosition(block));
            }
            assertEquals(player.getRolling() ? player.getRollYRadius() : player.getStandYRadius(),
                    player.getYRadius(), "squeeze posture must retain its native radius");
            int x = player.getCentreX() & 0xffff;
            int right = block.getX() + block.getSolidParams().offsetX()
                    + block.getSolidParams().halfWidth();
            if (x >= squeezeBlockLeft(block) && x <= right) {
                int required = player.getStandYRadius() + player.getYRadius() - 4;
                int feet = (player.getCentreY() & 0xffff) + player.getYRadius();
                int bottom = block.getY() + block.getSolidParams().offsetY()
                        + block.getSolidParams().airHalfHeight();
                assertTrue(feet - bottom >= required,
                        "squeeze must clear the live posture, including standing after landing");
            }
        }

        private static java.util.Optional<FbzMovingSqueezeTraversal.Episode> findSqueezeRecoveryEpisode(
                ObjectManager objects, AbstractPlayableSprite player, int frontierX) {
            if (!hasSqueezeLaunchControl(player)) return java.util.Optional.empty();
            FbzMovingSqueezeTraversal.Episode candidate = null;
            for (var block : objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)) {
                if (squeezeBlockLeft(block) != frontierX) continue;
                for (var car : objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)) {
                    var episode = new FbzMovingSqueezeTraversal.Episode(block, car);
                    if (!FbzMovingSqueezeTraversal.beforeLaunchFrontier(episode, player)
                            || !FbzMovingSqueezeTraversal.project(episode, player,
                                    FbzMovingSqueezeTraversal.NATIVE_RELEASE_SPEED).clears()) continue;
                    if (candidate != null) return java.util.Optional.empty();
                    candidate = episode;
                }
            }
            return java.util.Optional.ofNullable(candidate);
        }

        private static int squeezeBlockLeft(
                Sonic3kInvisibleBlockObjectInstance block) {
            return block.getX() + block.getSolidParams().offsetX()
                    - block.getSolidParams().halfWidth();
        }

        private static int playerRightEdge(AbstractPlayableSprite player) {
            return (player.getCentreX() & 0xFFFF)
                    + (player.getStandXRadius() & 0xFFFF);
        }

        private static int squeezeRecoveryStoppingRightEdge(
                AbstractPlayableSprite player) {
            int speed = Math.max(0,
                    Math.max(player.getGSpeed(), player.getXSpeed()));
            int deceleration = Math.max(1,
                    player.getEffectiveRunDecel() & 0xFFFF);
            long frames = (speed + (long) deceleration - 1) / deceleration;
            long fixedDistance = frames
                    * (2L * speed - (frames - 1) * deceleration) / 2;
            int distance = (int) ((fixedDistance + 0xFF) >> 8);
            return playerRightEdge(player) + distance;
        }

        private static boolean hasOrdinaryFlatUpwardCarRideControl(
                AbstractPlayableSprite player) {
            return !player.getAir()
                    && player.getGroundMode() == GroundMode.GROUND
                    && (player.getAngle() & 0xFF) == 0
                    && !player.isSliding()
                    && !player.getRolling()
                    && !player.getSpindash()
                    && !player.getCrouching()
                    && !player.isControlLocked()
                    && !player.isObjectControlled()
                    && player.isOnObject()
                    && player.getLatchedSolidObjectInstance()
                    instanceof FbzElevatorObjectInstance.Car car
                    && FbzMovingSqueezeTraversal.isLiveUpwardCar(car)
                    && player.getMoveLockTimer() == 0
                    && !player.getPushing();
        }

        private static String ordinaryRideControlEvidence(
                AbstractPlayableSprite player) {
            return "air=" + player.getAir()
                    + "/mode=" + player.getGroundMode()
                    + "/angle=$" + Integer.toHexString(player.getAngle() & 0xFF)
                    + "/sliding=" + player.isSliding()
                    + "/rolling=" + player.getRolling()
                    + "/spindash=" + player.getSpindash()
                    + "/crouching=" + player.getCrouching()
                    + "/controlLocked=" + player.isControlLocked()
                    + "/objectControlled=" + player.isObjectControlled()
                    + "/onObject=" + player.isOnObject()
                    + "/moveLock=" + player.getMoveLockTimer()
                    + "/pushing=" + player.getPushing();
        }

        private static String squeezeLaunchAuthorityEvidence(
                AbstractPlayableSprite player,
                FbzElevatorObjectInstance.Car selectedCar) {
            ObjectInstance latched = player.getLatchedSolidObjectInstance();
            StringBuilder evidence = new StringBuilder()
                    .append("predicate=")
                    .append(FbzMovingSqueezeTraversal
                            .hasLaunchFloorAuthority(player))
                    .append("/centre=($")
                    .append(Integer.toHexString(player.getCentreX() & 0xFFFF))
                    .append(",$ ")
                    .append(Integer.toHexString(player.getCentreY() & 0xFFFF))
                    .append(")/radius=")
                    .append(player.getYRadius() & 0xFFFF)
                    .append("/standRadius=")
                    .append(player.getStandYRadius() & 0xFFFF)
                    .append("/rollRadius=")
                    .append(player.getRollYRadius() & 0xFFFF)
                    .append("/feet=$")
                    .append(Integer.toHexString((player.getCentreY() & 0xFFFF)
                            + (player.getYRadius() & 0xFFFF)))
                    .append("/air=").append(player.getAir())
                    .append("/groundMode=").append(player.getGroundMode())
                    .append("/angle=$")
                    .append(Integer.toHexString(player.getAngle() & 0xFF))
                    .append("/rolling=").append(player.getRolling())
                    .append("/spindash=").append(player.getSpindash())
                    .append("/crouching=").append(player.getCrouching())
                    .append("/sliding=").append(player.isSliding())
                    .append("/objectControlled=").append(player.isObjectControlled())
                    .append("/controlLocked=").append(player.isControlLocked())
                    .append("/moveLock=").append(player.getMoveLockTimer())
                    .append("/pushing=").append(player.getPushing())
                    .append("/onObject=").append(player.isOnObject())
                    .append("/latchedType=")
                    .append(latched == null ? "none" : latched.getClass().getName())
                    .append("/latchedIsButton=")
                    .append(latched != null
                            && latched.getClass() == Sonic3kButtonObjectInstance.class)
                    .append("/latchedIsSelectedCar=").append(latched == selectedCar);
            if (latched != null
                    && latched.getClass() == Sonic3kButtonObjectInstance.class) {
                Sonic3kButtonObjectInstance button =
                        (Sonic3kButtonObjectInstance) latched;
                var params = button.getSolidParams();
                int anchorX = button.getX() + params.offsetX();
                int surfaceY = button.getY() + params.offsetY()
                        - params.groundHalfHeight();
                int liveFeetY = (player.getCentreY() & 0xFFFF)
                        + (player.getYRadius() & 0xFFFF);
                evidence.append("/buttonPos=($")
                        .append(Integer.toHexString(button.getX() & 0xFFFF))
                        .append(",$ ")
                        .append(Integer.toHexString(button.getY() & 0xFFFF))
                        .append(")/buttonOffset=(")
                        .append(params.offsetX()).append(',')
                        .append(params.offsetY()).append(')')
                        .append("/buttonHalfWidth=").append(params.halfWidth())
                        .append("/buttonGroundHalfHeight=")
                        .append(params.groundHalfHeight())
                        .append("/buttonBounds=[$")
                        .append(Integer.toHexString(anchorX - params.halfWidth()))
                        .append(",$ ")
                        .append(Integer.toHexString(anchorX + params.halfWidth()))
                        .append("]/buttonSurface=$")
                        .append(Integer.toHexString(surfaceY))
                        .append("/feetDelta=").append(liveFeetY - surfaceY)
                        .append("/buttonDestroyed=").append(button.isDestroyed())
                        .append("/buttonSkip=")
                        .append(button.isSkipSolidContactThisFrame())
                        .append("/buttonSolid=").append(button.isSolidFor(player));
            }
            return evidence.toString();
        }

        private static int projectedSpindashReleaseSpeed(
                AbstractPlayableSprite player) {
            var capability = player.getGameRules().playerCapability();
            short[] table = player.isSuperSonic()
                    ? capability.superSpindashSpeedTable()
                    : capability.spindashSpeedTable();
            if (table == null || table.length == 0) {
                return FbzMovingSqueezeTraversal.NATIVE_RELEASE_SPEED;
            }
            int index = Math.min((player.getSpindashCounter() >> 8) & 0xFF,
                    table.length - 1);
            return table[index] & 0xFFFF;
        }

        private static boolean hasOrdinaryFlatGroundControl(AbstractPlayableSprite player) {
            return !player.getAir()
                    && player.getGroundMode() == GroundMode.GROUND
                    && (player.getAngle() & 0xFF) == 0
                    && !player.isSliding()
                    && !player.getRolling()
                    && !player.getSpindash()
                    && !player.getCrouching()
                    && !player.isControlLocked()
                    && !player.isObjectControlled()
                    && !player.isOnObject()
                    && player.getMoveLockTimer() == 0
                    && !player.getPushing();
        }

        /**
         * A live placed spike between P1 and the Obj74 column, overlapping P1's
         * standing height, blocks the flat underpass approach. Its solid box is
         * the ROM Obj_Spikes SolidObject envelope; no route identity is used.
         */
        private static Sonic3kSpikeObjectInstance magneticPlatformRideBlockingSpike(
                ObjectManager objects, AbstractPlayableSprite player,
                int playerX, int playerY, int platformX) {
            return objects.activeObjectsOfType(Sonic3kSpikeObjectInstance.class).stream()
                    .filter(spike -> !spike.isDestroyed())
                    .filter(spike -> spike.getX() > playerX && spike.getX() < platformX)
                    .filter(spike -> Math.abs(spike.getY() - playerY)
                            <= spike.getSolidParams().airHalfHeight() + player.getYRadius())
                    .min(Comparator.comparingInt(spike -> spike.getX() - playerX))
                    .orElse(null);
        }

        /** The next authored Obj74 column within one hop, by immutable layout placement. */
        private static ObjectSpawn nextMagneticPlatformPlacement(
                ObjectManager objects, FbzMagneticPlatformObjectInstance current) {
            int currentX = current.getX();
            int restY = current.getSpawn().y() + current.displacement();
            return objects.getAllSpawns().stream()
                    .filter(spawn -> spawn.layoutIndex() >= 0)
                    .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.FBZ_MAGNETIC_PLATFORM)
                    .filter(spawn -> spawn.layoutIndex() != current.getSpawn().layoutIndex())
                    .filter(spawn -> spawn.x() > currentX
                            && spawn.x() - currentX <= MAGNETIC_PLATFORM_RIDE_REACH)
                    .filter(spawn -> spawn.y() == restY)
                    .min(Comparator.comparingInt(spawn -> spawn.x() - currentX))
                    .orElse(null);
        }

        private static FbzMagneticPlatformObjectInstance liveMagneticPlatform(
                ObjectManager objects, int layoutIndex) {
            return objects.activeObjectsOfType(FbzMagneticPlatformObjectInstance.class).stream()
                    .filter(platform -> !platform.isDestroyed())
                    .filter(platform -> platform.getSpawn().layoutIndex() == layoutIndex)
                    .findFirst().orElse(null);
        }

        private static FbzScrewDoorObjectInstance findLinkedBlockingDoor(
                ObjectManager objects, AbstractPlayableSprite player,
                int playerX, int playerY, Set<Integer> passed) {
            return objects.activeObjectsOfType(FbzScrewDoorObjectInstance.class).stream()
                    .filter(door -> !door.isDestroyed())
                    .filter(door -> !passed.contains(door.getSpawn().layoutIndex()))
                    .filter(door -> door.getY() >= door.getSpawn().y())
                    .filter(door -> door.getX() > playerX
                            && door.getX() - playerX <= LINKED_DOOR_LOOKAHEAD)
                    .filter(door -> Math.abs(door.getY() - playerY)
                            <= door.getSolidParams().airHalfHeight() + player.getYRadius())
                    .filter(door -> findLinkedButton(objects, door, playerX, playerY) != null)
                    .min(Comparator.comparingInt(door -> door.getX() - playerX))
                    .orElse(null);
        }

        private static Sonic3kButtonObjectInstance findLinkedButton(
                ObjectManager objects, FbzScrewDoorObjectInstance door,
                int playerX, int playerY) {
            return objects.activeObjectsOfType(Sonic3kButtonObjectInstance.class).stream()
                    .filter(button -> !button.isDestroyed())
                    .filter(button -> (button.getSpawn().subtype() & 0xF) == door.triggerIndex())
                    .filter(button -> Math.abs(button.getX() - playerX) <= LINKED_DOOR_BUTTON_REACH)
                    .filter(button -> Math.abs(button.getY() - playerY) <= 0x40)
                    .min(Comparator.comparingInt(button -> Math.abs(button.getX() - playerX)))
                    .orElse(null);
        }

        private static boolean hasSqueezeLaunchControl(
                AbstractPlayableSprite player) {
            return FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player)
                    && !player.getRolling()
                    && !player.getSpindash()
                    && !player.getCrouching();
        }

        private static long magneticPlatformYFixed(
                FbzMagneticPlatformObjectInstance platform) {
            return ((long) platform.getY() << 16)
                    | (platform.yFraction() & 0xFFFFL);
        }

        private static int ordinaryRightCrossingBudget(
                AbstractPlayableSprite player, int distancePixels) {
            // Conservative ordinary-run model; the clamp keeps S1 donation's
            // always-cap ground rule honest and S3K can only cross sooner.
            return RouteSteering.ordinaryRightCrossingBudget(
                    player, distancePixels, MAGNETIC_PLATFORM_WAIT_LIMIT);
        }

        /** See {@link SidekickAudit#followAllowance}: trailing CPU chain crossing budget. */
        private static int sidekickFollowAllowance(int objectTouchRight) {
            return SidekickAudit.followAllowance(objectTouchRight, PLAYER_TOUCH_HALF_WIDTH,
                    SIDEKICK_FOLLOW_MAX_TRAIL, SIDEKICK_FOLLOW_LAG_FRAMES,
                    MAGNETIC_PLATFORM_WAIT_LIMIT);
        }

        /** Every live CPU sidekick is grounded within {@code SIDEKICK_GATHER_RANGE} of P1. */
        private static boolean sidekicksGatheredBeside(int playerX) {
            return SidekickAudit.gatheredBeside(playerX, SIDEKICK_GATHER_RANGE);
        }

        private static boolean crossesProjectedColumn(
                FbzFlameObjectInstance flame, int playerX,
                int combinedRadius, int frameLimit) {
            for (int frame = 0; frame <= frameLimit; frame++) {
                int projectedX = flame.getX()
                        + (int) (((long) flame.xVelocity() * frame) >> 8);
                if (Math.abs(projectedX - playerX) <= combinedRadius) return true;
            }
            return false;
        }

        private String waypointDiagnostic(String name, int targetX) {
            AbstractPlayableSprite player = fixture.sprite();
            return name + " frame=" + frames
                    + " target=$" + Integer.toHexString(targetX)
                    + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                    + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ")"
                    + " speed=($" + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                    + ",$" + Integer.toHexString(player.getYSpeed() & 0xFFFF) + ")"
                    + " air=" + player.getAir() + " onObject=" + player.isOnObject()
                    + " hurt=" + player.isHurt() + " dead=" + player.getDead()
                    + recentDiagnostic();
        }

        private static String objectPosition(ObjectInstance object) {
            return object == null ? "none"
                    : object.getClass().getSimpleName() + "@($"
                    + Integer.toHexString(object.getX()) + ",$"
                    + Integer.toHexString(object.getY()) + ")";
        }

        private int frames() {
            return frames;
        }

        private void recordRecentFrame(int mask, AbstractPlayableSprite player) {
            recentLog.record(frames, mask, player);
        }

        private String recentDiagnostic() {
            return lastControllerDiagnostic + " recent=" + recentLog;
        }
    }

    private static final class LateStarpostMilestones {
        private boolean platformObserved;
        private boolean platformExecuted;
        private boolean chainObserved;
        private boolean chainExecuted;

        private void observe(
                Set<ObjectInstance> active, Set<ObjectInstance> previous,
                AbstractPlayableSprite player, ObjectManager objects) {
            for (ObjectInstance object : active) {
                boolean lowerPlatform = object instanceof FbzMagneticPlatformObjectInstance platform
                        && platform.getX() == 0x2840 && platform.getY() >= 0x0A00;
                boolean lowerChain = object instanceof FbzMagneticPlatformChainObjectInstance chain
                        && chain.parentMember() != null
                        && chain.parentMember().getX() == 0x2840
                        && chain.parentMember().getY() >= 0x0A00;
                if (lowerPlatform) {
                    platformObserved = true;
                    platformExecuted |= previous.contains(object);
                }
                if (lowerChain) {
                    chainObserved = true;
                    chainExecuted |= previous.contains(object);
                }
            }
        }
    }

    private static final class RouteMilestones {
        private boolean cageCapture;
        private boolean prisonOpened;
        private boolean elevatorRide;
        private boolean launcherRide;
        private boolean flamethrowerRide;
        private boolean magneticPlatformRide;
        private boolean chainControl;
        private boolean spiderControl;
        private boolean nonPersistentSpawn;
        private boolean nonPersistentDespawn;
        private int maxPlacedSpawnScreenX = Integer.MIN_VALUE;
        private int minPlacedDespawnScreenX = Integer.MAX_VALUE;
        private boolean placementStateSeeded;
        private final Set<ObjectSpawn> observedInactivePlacements =
                new java.util.HashSet<>();
        private final Map<ObjectSpawn, Integer> genuineSpawnScreenX =
                new java.util.HashMap<>();
        private final Map<ObjectSpawn, ObjectSpawn> canonicalPlacements =
                new java.util.HashMap<>();
        private boolean hazardObserved;
        private boolean exactArenaLock;
        private boolean bossCombat;
        private boolean bossDefeat;
        private boolean bossArenaPlayerContained = true;
        private boolean capsuleObserved;
        private boolean capsuleCameraRelease;
        private boolean exitCameraRelease;
        private boolean unsafeFall;
        private boolean spindashObserved;
        private boolean s1DonationUpperLoopAssistConsumed;
        private boolean s1DonationLowerLoopAssistConsumed;
        private int minScreenX = Integer.MAX_VALUE;
        private int maxScreenX = Integer.MIN_VALUE;
        private int minPlayerX = Integer.MAX_VALUE;
        private int maxPlayerX = Integer.MIN_VALUE;
        /**
         * CPU sidekick deaths are shipped behaviour (the BK2's own Tails dies
         * at rows 35308, 38697 and 39721 and respawns); the contract lives in
         * {@link SidekickAudit}. The excused window is the boss-event plane
         * carrier lift, and the sampled milestone is Obj_FBZEndBoss allocation.
         */
        private static final int SIDEKICK_RESPAWN_LIMIT = 0x100;
        private final SidekickAudit sidekicks = new SidekickAudit(SIDEKICK_RESPAWN_LIMIT);

        private void observeFrame(Set<ObjectInstance> active, Set<ObjectInstance> previous,
                                  AbstractPlayableSprite player, ObjectManager objects) {
            int playerX = player.getCentreX() & 0xFFFF;
            int cameraX = GameServices.camera().getX() & 0xFFFF;
            int viewportWidth = GameServices.camera().getWidth() & 0xFFFF;
            int placementLoadAhead = Math.max(0x280, viewportWidth + 0x80);
            int screenX = playerX - cameraX;
            minScreenX = Math.min(minScreenX, screenX);
            maxScreenX = Math.max(maxScreenX, screenX);
            minPlayerX = Math.min(minPlayerX, playerX);
            maxPlayerX = Math.max(maxPlayerX, playerX);
            unsafeFall |= player.getDead();
            spindashObserved |= player.getSpindash();
            boolean bossLive = !objects.activeObjectsOfType(FbzEndBossInstance.class).isEmpty();
            boolean carrierLifting = !bossLive && objects.activeObjectsOfType(
                            FbzEndBossEventControlInstance.class).stream()
                    .anyMatch(carrier -> carrier.getX() != PLANE_CONTROLLER_ORIGIN_X
                            || carrier.getY() != PLANE_CONTROLLER_ORIGIN_Y);
            sidekicks.observe(player, carrierLifting, bossLive, cameraX);
            GameServices.zoneRuntimeRegistry().currentAs(FbzZoneRuntimeState.class)
                    .ifPresent(runtime -> {
                        s1DonationUpperLoopAssistConsumed |=
                                runtime.s1DonationUpperLoopAssistState()
                                        == FbzZoneRuntimeState.S1DonationUpperLoopAssistState.CONSUMED;
                        s1DonationLowerLoopAssistConsumed |=
                                runtime.s1DonationLowerLoopAssistState()
                                        == FbzZoneRuntimeState.S1DonationLowerLoopAssistState.CONSUMED;
                    });
            exactArenaLock |= (GameServices.camera().getMinX() & 0xFFFF) == 0x32B8
                    && (GameServices.camera().getMaxX() & 0xFFFF) == 0x32B8;
            capsuleCameraRelease |= (GameServices.camera().getMaxXTarget() & 0xFFFF) == 0x2FDC;
            exitCameraRelease |= (GameServices.camera().getMaxXTarget() & 0xFFFF) == 0x3738;

            if (!placementStateSeeded) {
                Set<ObjectSpawn> initiallyActive = new java.util.HashSet<>();
                initiallyActive.addAll(objects.getActiveSpawns());
                for (ObjectSpawn spawn : objects.getAllSpawns()) {
                    canonicalPlacements.put(spawn, spawn);
                    if (!initiallyActive.contains(spawn)) {
                        observedInactivePlacements.add(spawn);
                    }
                }
                placementStateSeeded = true;
            }

            for (ObjectInstance object : active) {
                ObjectSpawn canonicalSpawn = canonicalPlacements.get(object.getSpawn());
                if (!object.isPersistent() && canonicalSpawn != null
                        && !object.isDestroyed()
                        && !objects.isRemembered(canonicalSpawn)
                        && !previous.contains(object)
                        && observedInactivePlacements.contains(canonicalSpawn)) {
                    int placementScreenX = canonicalSpawn.x() - cameraX;
                    if (placementScreenX >= placementLoadAhead - 0x80
                            && placementScreenX < placementLoadAhead + 0x80) {
                        genuineSpawnScreenX.put(canonicalSpawn, placementScreenX);
                    }
                }
                hazardObserved |= object instanceof FbzFlamethrowerObjectInstance
                        || object instanceof FbzEndBossFlameChild;
                capsuleObserved |= object instanceof FbzEndEggCapsuleInstance;
                if (object instanceof FbzEndBossInstance boss) {
                    boolean combat = switch (boss.phase()) {
                        case OPENING_ROTATION, ATTACK, ROTATION -> true;
                        default -> false;
                    };
                    boolean defeat = switch (boss.phase()) {
                        case DEFEAT_RECENTER, DEFEAT_EXPLOSIONS, DEFEAT_HIDE_WAIT,
                                DEFEAT_CAPSULE_DELAY, CAPSULE_WAIT, EXIT_READY -> true;
                        default -> false;
                    };
                    bossCombat |= combat;
                    bossDefeat |= defeat;
                    if (combat) {
                        int left = GameServices.camera().getMinX() & 0xFFFF;
                        int right = (GameServices.camera().getMaxX() & 0xFFFF) + 320 - 24;
                        bossArenaPlayerContained &= playerX >= left + 16 && playerX <= right;
                    }
                }
            }
            for (ObjectInstance object : previous) {
                ObjectSpawn canonicalSpawn = canonicalPlacements.get(object.getSpawn());
                if (!object.isPersistent() && canonicalSpawn != null
                        && !active.contains(object)
                        && !object.isDestroyed()
                        && !objects.isRemembered(canonicalSpawn)) {
                    observedInactivePlacements.add(canonicalSpawn);
                    Integer spawnScreenX = genuineSpawnScreenX.remove(canonicalSpawn);
                    int despawnScreenX = canonicalSpawn.x() - cameraX;
                    if (spawnScreenX != null
                            && despawnScreenX >= -0x180 && despawnScreenX <= -0x80) {
                        // Only a canonical absent -> active -> absent placed
                        // lifecycle contributes. Both edges use immutable ROM
                        // placement X, never a moving instance's mutable X.
                        nonPersistentSpawn = true;
                        nonPersistentDespawn = true;
                        maxPlacedSpawnScreenX = Math.max(
                                maxPlacedSpawnScreenX, spawnScreenX);
                        minPlacedDespawnScreenX = Math.min(
                                minPlacedDespawnScreenX, despawnScreenX);
                    }
                }
            }
        }

        private RouteCompletionEvidence evidence(int frames, boolean forcedExit) {
            return new RouteCompletionEvidence(frames, cageCapture, prisonOpened,
                    elevatorRide, launcherRide, flamethrowerRide, magneticPlatformRide,
                    chainControl, spiderControl, nonPersistentSpawn,
                    nonPersistentDespawn, maxPlacedSpawnScreenX,
                    minPlacedDespawnScreenX, hazardObserved, exactArenaLock, bossCombat,
                    bossDefeat, bossArenaPlayerContained, capsuleObserved,
                    capsuleCameraRelease, exitCameraRelease, forcedExit, unsafeFall,
                    spindashObserved, s1DonationUpperLoopAssistConsumed,
                    s1DonationLowerLoopAssistConsumed,
                    minScreenX, maxScreenX, minPlayerX, maxPlayerX,
                    sidekicks.auditFrames(), sidekicks.identityOrderPreserved(),
                    sidekicks.respawnedAfterEveryDeath(), sidekicks.deaths(),
                    sidekicks.longestDeadStreak(),
                    SidekickAudit.allAliveNow(),
                    sidekicks.controllerEveryFrame(),
                    sidekicks.leaderChainEveryFrame(), sidekicks.diedDuringExcusedWindow(),
                    sidekicks.aliveAtMilestone(), sidekicks.deathEvidence());
        }

        @Override public String toString() {
            return "{cage=" + cageCapture + ",prison=" + prisonOpened
                    + ",elevator=" + elevatorRide
                    + ",launcher=" + launcherRide + ",flame=" + flamethrowerRide
                    + ",magnetic=" + magneticPlatformRide
                    + ",chain=" + chainControl + ",spider=" + spiderControl + '}';
        }
    }

    public record RouteCompletionEvidence(
            int frames,
            boolean cageCapture,
            boolean prisonOpened,
            boolean elevatorRide,
            boolean launcherRide,
            boolean flamethrowerRide,
            boolean magneticPlatformRide,
            boolean chainControl,
            boolean spiderControl,
            boolean nonPersistentSpawn,
            boolean nonPersistentDespawn,
            int maxPlacedSpawnScreenX,
            int minPlacedDespawnScreenX,
            boolean hazardObserved,
            boolean exactArenaLock,
            boolean bossCombat,
            boolean bossDefeat,
            boolean bossArenaPlayerContained,
            boolean capsuleObserved,
            boolean capsuleCameraRelease,
            boolean exitCameraRelease,
            boolean forcedExit,
            boolean unsafeFall,
            boolean spindashObserved,
            boolean s1DonationUpperLoopAssistConsumed,
            boolean s1DonationLowerLoopAssistConsumed,
            int minScreenX,
            int maxScreenX,
            int minPlayerX,
            int maxPlayerX,
            int sidekickAuditFrames,
            boolean sidekickIdentityOrderPreserved,
            boolean sidekickRespawnedAfterEveryDeath,
            int sidekickDeaths,
            int sidekickLongestDeadStreak,
            boolean sidekickAliveAtExit,
            boolean sidekickControllerEveryFrame,
            boolean sidekickLeaderChainEveryFrame,
            boolean sidekickDiedOnCarrier,
            boolean sidekickAliveAtBossEntry,
            String sidekickDeathEvidence) { }
}
