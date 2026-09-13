package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.InputRun;
import com.openggf.tests.route.ObjectLifetimeFrames;
import com.openggf.tests.route.RecentFrameLog;
import com.openggf.tests.route.RouteSteering;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIZ act 1 route pilot on the shared route primitives (second-zone pilot of
 * the technique in docs/architecture/research/2026-09-13-live-state-route-controllers.md).
 * The authored fallback is the {@code aiz1_to_hcz_fullrun} fixture's act-1
 * pad program (rows 0-5495); live-state stages take over wherever the
 * engine's object phase differs from the recording.
 *
 * <p>Measured so far (2026-09-13, native Sonic+Tails, 320px):
 * <ul>
 * <li>The engine's intro is 289 frames shorter than the fixture: rows 0-288
 *     are the plane flight before the player object exists, while the
 *     session starts with P1 at $0040/$0420 under object control. Every
 *     global-oscillator object is therefore met in another phase, and the
 *     Knuckles cutscene keeps the pad locked after object control ends
 *     ({@code isControlLocked}), so the handover waits for both.</li>
 * <li>With the intro gate, the program tracks the recording to within four
 *     frames until the $1870 Obj_FloatingPlatform, whose oscillator phase
 *     decides whether P1 leaves the $02BC ledge at its end ($1848) or from
 *     the platform's edge ($1890). The LEDGE stages climb to the ledge,
 *     build speed with one short RIGHT hop, and regulate the run-off to
 *     about $460 when the platform is level so the arc clears the $1930
 *     wall onto the $1948 spring.</li>
 * <li>Next unowned hazard: the $1DE0 Obj_AIZ giant ride vine (rows
 *     2662-3058), also oscillator-phased; the program alone drops P1 into
 *     the $04B0 lower area beneath it. Vines, collapsing bridges, the
 *     miniboss and the fire transition remain to be gated.</li>
 * </ul>
 * Opt-in while the route is incomplete: run with
 * {@code -Dopenggf.aiz1.pilot=true}.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.aiz1.pilot", matches = "true")
class TestS3kAiz1RoutePilot {

    /** "frames:hexMask" runs, fixture rows 0-5495 (148 runs). */
    private static final String ACT1_INPUT_PROGRAM =
            "161:0,8:4,32:0,10:10,1217:0,252:8,2:0,22:4,5:0,10:10,12:18,3:10,7:0,12:8,14:0,4:4,7:0,5:8,"
            + "9:0,13:2,6:12,5:2,7:12,4:2,4:12,13:2,25:0,34:8,2:0,25:4,10:14,31:4,4:0,27:8,17:0,14:4,48:0"
            + ",43:4,18:0,5:8,20:0,6:4,25:14,20:4,8:0,20:8,11:0,57:8,157:0,43:8,3:A,16:2,9:0,8:8,12:18,16"
            + ":8,6:0,13:4,4:0,3:8,10:18,4:8,13:0,8:4,29:0,22:8,62:0,53:8,6:18,103:8,5:18,87:8,4:18,26:8,"
            + "26:0,9:2,7:12,3:2,83:0,1:8,5:18,86:8,7:18,27:8,17:0,9:8,17:18,7:8,55:0,4:8,5:18,19:0,14:8,"
            + "6:0,20:4,3:0,10:8,24:0,57:8,4:0,24:4,12:14,16:4,15:0,20:8,44:0,6:8,36:0,31:8,18:0,10:4,5:0"
            + ",39:8,4:0,23:4,8:0,43:8,17:18,214:8,68:0,554:8,9:0,58:4,32:0,18:8,116:0,11:8,9:0,5:8,17:18"
            + ",22:8,17:0,8:8,8:0,23:8,8:18,12:8,22:0,9:8,9:18,24:8,31:0,32:8,2:0,42:4,10:0,31:8,143:0";

    /** Program run that starts at fixture row 1428, the first player-driven input. */
    private static final int PROGRAM_RUN_AFTER_INTRO = 5;
    /** Program run that starts at fixture row 2314, neutral after the ledge run-off. */
    private static final int PROGRAM_RUN_AFTER_LEDGE = 48;
    private static final int LEDGE_PLATFORM_SPAWN_X = 0x1870;
    private static final int LEDGE_MIN_X = 0x1780;
    private static final int LEDGE_MAX_X = 0x1850;
    private static final int LEDGE_STAND_Y = 0x02BC;
    private static final int LEDGE_RUNUP_X = 0x17D0;
    private static final int LEDGE_RUNUP_GROUND_FRAMES = 20;
    private static final int LEDGE_JUMP_HOLD = 6;
    private static final int LEDGE_RUN_OFF_X = 0x1848;
    private static final int LEDGE_PLATFORM_DROP_SPEED = 0x0460;
    private static final int FRAME_LIMIT = 9000;

    @Test
    void act1RouteReachesTheAct2Reload() {
        configureNativeSonicTails();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build();
        Aiz1RouteRunner runner = new Aiz1RouteRunner(fixture, InputProgram.parse(ACT1_INPUT_PROGRAM));
        runner.run();
    }

    private static void configureNativeSonicTails() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        CrossGameFeatureProvider.getInstance().resetState();
        configuration.clearSessionOverrides();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        configuration.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        configuration.resolveDisplayAspect();
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "off");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private enum Stage { INTRO, PROGRAM, LEDGE_CLIMB, LEDGE_RUNUP, LEDGE_JUMP, LEDGE_RUN_OFF }

    private static final class Aiz1RouteRunner {
        private final HeadlessTestFixture fixture;
        private final ObjectManager objects;
        private final List<InputRun> program;
        private final ObjectLifetimeFrames lifetime;
        private final RecentFrameLog recentLog = new RecentFrameLog(30);
        private int frames;
        private int runIndex;
        private int runFrame;
        private Stage stage = Stage.INTRO;
        private boolean introControlSeen;
        private int hold;
        private boolean ledgeDone;
        private boolean ledgeJumped;
        private int reloadFrame = -1;
        private String lastState = "";

        Aiz1RouteRunner(HeadlessTestFixture fixture, List<InputRun> program) {
            this.fixture = fixture;
            this.objects = GameServices.level().getObjectManager();
            this.program = program;
            this.lifetime = new ObjectLifetimeFrames(objects);
        }

        void run() {
            while (frames < FRAME_LIMIT) {
                lifetime.beginFrame(objects);
                AbstractPlayableSprite player = fixture.sprite();
                int x = player.getCentreX() & 0xFFFF;
                int y = player.getCentreY() & 0xFFFF;
                int act = GameServices.level().getCurrentAct();
                if (act == 1 && reloadFrame < 0) reloadFrame = frames;
                int mask = decide(player, x, y);
                log(player, x, y, act, mask);
                assertFalse(player.getDead(), () -> "P1 died: " + diagnostic(player));
                InputProgram.step(fixture, mask);
                recentLog.record(frames, mask, player);
                frames++;
                lifetime.endFrame();
                if (stage == Stage.PROGRAM && runIndex >= program.size()) break;
                if (reloadFrame >= 0 && frames - reloadFrame > 300) break;
            }
            AbstractPlayableSprite player = fixture.sprite();
            System.out.printf("AIZTL end f=%d stage=%s run=%d p=(%04X,%04X) act=%d reloadFrame=%d%n",
                    frames, stage, runIndex, player.getCentreX() & 0xFFFF,
                    player.getCentreY() & 0xFFFF, GameServices.level().getCurrentAct(), reloadFrame);
            assertTrue(reloadFrame >= 0, () -> "act-2 reload never observed: " + diagnostic(player));
        }

        private int decide(AbstractPlayableSprite player, int x, int y) {
            boolean grounded = !player.getAir();
            Object latch = player.getLatchedSolidObjectInstance();
            boolean onLedgePlatform = player.isOnObject()
                    && latch instanceof FloatingPlatformObjectInstance platform
                    && platform.getSpawn().x() == LEDGE_PLATFORM_SPAWN_X;
            switch (stage) {
                case INTRO -> {
                    // Obj_AIZ intro: P1 is object-controlled through the plane
                    // run-in and the Knuckles punch; hand over on the first
                    // grounded, uncontrolled, unhurt frame after that control.
                    // The Knuckles cutscene keeps the pad locked
                    // (Ctrl_1_locked) after object control ends, so the
                    // handover also waits for the lock and move-lock timer.
                    introControlSeen |= player.isObjectControlled();
                    if (introControlSeen && !player.isObjectControlled() && grounded
                            && !player.isHurt() && !player.isControlLocked()
                            && player.getMoveLockTimer() == 0) {
                        stage = Stage.PROGRAM;
                        runIndex = PROGRAM_RUN_AFTER_INTRO;
                        runFrame = 0;
                    }
                    return 0;
                }
                case PROGRAM -> {
                    if (!ledgeDone && onLedgePlatform && y <= 0x0320) {
                        // The $1870 Obj_FloatingPlatform rides the global
                        // oscillator, so the recording's phase never holds;
                        // climb to the fixed $02BC ledge and build the
                        // run-off speed there instead.
                        stage = Stage.LEDGE_CLIMB;
                        hold = 25;
                        return INPUT_LEFT | INPUT_JUMP;
                    }
                    return programMask();
                }
                case LEDGE_CLIMB -> {
                    if (grounded && y <= LEDGE_STAND_Y + 4 && x >= LEDGE_MIN_X && x <= LEDGE_MAX_X) {
                        stage = Stage.LEDGE_RUNUP;
                        return RouteSteering.walkMask(player, LEDGE_RUNUP_X, 8, 0x0200);
                    }
                    if (hold > 0) { hold--; return INPUT_LEFT | INPUT_JUMP; }
                    return INPUT_LEFT;
                }
                case LEDGE_RUNUP -> {
                    int walk = RouteSteering.walkMask(player, LEDGE_RUNUP_X, 8, 0x0200);
                    if (walk == 0 && grounded && Math.abs(player.getGSpeed()) <= 0x40) {
                        stage = Stage.LEDGE_JUMP;
                        hold = LEDGE_RUNUP_GROUND_FRAMES;
                        return INPUT_RIGHT;
                    }
                    return walk;
                }
                case LEDGE_JUMP -> {
                    // Ground run-up, then one short hop with RIGHT held
                    // through the arc: air acceleration builds the ~$600 the
                    // recording carried off the ledge at row 2314, which is
                    // what clears the $1930 wall onto the $1948 spring. A
                    // full-height jump instead clears the wall onto the
                    // upper path, so the hold is short.
                    if (hold > 0) { hold--; return INPUT_RIGHT; }
                    if (!ledgeJumped && grounded) {
                        ledgeJumped = true;
                        hold = -LEDGE_JUMP_HOLD;
                        return INPUT_RIGHT | INPUT_JUMP;
                    }
                    if (hold < 0) { hold++; return INPUT_RIGHT | INPUT_JUMP; }
                    if (!grounded) return INPUT_RIGHT;
                    stage = Stage.LEDGE_RUN_OFF;
                    return INPUT_RIGHT;
                }
                case LEDGE_RUN_OFF -> {
                    if (!grounded && x >= LEDGE_RUN_OFF_X - 8) {
                        ledgeDone = true;
                        stage = Stage.PROGRAM;
                        runIndex = PROGRAM_RUN_AFTER_LEDGE;
                        runFrame = 0;
                        return 0;
                    }
                    if (onLedgePlatform) {
                        // The oscillating platform is level with the ledge in
                        // this phase, so the drop starts from its right edge
                        // ($18B0) instead of the ledge end ($1848): a $98 px
                        // fall of $C1 onto the $1948 spring needs about $3A0,
                        // not the recording's $600.
                        int g = player.getGSpeed();
                        if (g > LEDGE_PLATFORM_DROP_SPEED + 0x20) return INPUT_LEFT;
                        if (g < LEDGE_PLATFORM_DROP_SPEED - 0x20) return INPUT_RIGHT;
                        return 0;
                    }
                    return INPUT_RIGHT;
                }
                default -> throw new IllegalStateException(stage.name());
            }
        }

        private int programMask() {
            if (runIndex >= program.size()) return 0;
            InputRun run = program.get(runIndex);
            int mask = run.mask();
            runFrame++;
            if (runFrame >= run.frames()) {
                runIndex++;
                runFrame = 0;
            }
            return mask;
        }

        private void log(AbstractPlayableSprite player, int x, int y, int act, int mask) {
            String state = stage + " act=" + act + " ctrl=" + player.isObjectControlled()
                    + " air=" + player.getAir() + " hurt=" + player.isHurt();
            if (frames % 100 == 0 || !state.equals(lastState)) {
                System.out.printf("AIZTL f=%d %s run=%d mask=%02X p=(%04X,%04X) v=(%04X,%04X) g=%04X rings=%d %s%n",
                        frames, stage, runIndex, mask, x, y, player.getXSpeed() & 0xFFFF,
                        player.getYSpeed() & 0xFFFF, player.getGSpeed() & 0xFFFF,
                        GameServices.level().getLevelGamestate().getRings(), nearby(x, y));
                lastState = state;
            }
        }

        private String nearby(int px, int py) {
            return objects.getActiveObjects().stream()
                    .filter(o -> o.getSpawn() != null && !o.isDestroyed())
                    .filter(o -> Math.abs(o.getX() - px) <= 0xA0 && Math.abs(o.getY() - py) <= 0xA0)
                    .map(o -> o.getClass().getSimpleName().replace("ObjectInstance", "").replace("Instance", "")
                            + "@" + Integer.toHexString(o.getX()) + "," + Integer.toHexString(o.getY()))
                    .toList().toString();
        }

        private String diagnostic(AbstractPlayableSprite player) {
            return "frame=" + frames + " stage=" + stage + " run=" + runIndex
                    + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                    + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ")"
                    + " recent=" + recentLog;
        }
    }
}
