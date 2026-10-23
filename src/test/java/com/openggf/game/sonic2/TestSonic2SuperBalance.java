package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent object/cliff geometry through the ordinary playable dispatch and
 * ROM-loaded animation scripts. No trace rows or recorded gameplay state.
 *
 * <p>SuperSonic_BalanceOnObjRight/Left always face the edge and write anim 6
 * (s2.asm:36712-36729), reached from both the object and terrain branches.
 * REV01 SuperSonicAniData $1B7C6 + entry 6 points to $1B82A:
 * {@code 09 C2 C3 C4 C3 C5 C6 C7 C6 FF}. Ordinary Sonic retains all four
 * states (Sonic_BalanceOnObjRight/Left, s2.asm:36615-36647).
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SuperBalance {
    private static final int PLATFORM_X = 0x400;
    private static final int PLATFORM_Y = 0x100;
    private static final int HALF_WIDTH = 0x20;
    private static final int PLAYER_Y = PLATFORM_Y - 0x10 - 0x13;

    private record Run(HeadlessTestFixture fixture, Sonic2SuperStateController controller) {
        AbstractPlayableSprite player() { return fixture.sprite(); }
    }

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    private Run boot(boolean superForm) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withFreshLevelStartLifecycle().build();
        var player = fixture.sprite();
        var controller = assertInstanceOf(Sonic2SuperStateController.class,
                player.getSuperStateController());
        assertNotNull(player.getAnimationSet(), "ordinary animation scripts come from the ROM");
        if (superForm) {
            var normalScripts = player.getAnimationSet();
            GameServices.gameState().configureSpecialStageProgress(7, 7);
            for (int i = 0; i < 7; i++) GameServices.gameState().markEmeraldCollected(i);
            player.setRingCount(100);
            player.setAir(true);
            player.setJumping(true);
            player.setYSpeed((short) 0x28);
            controller.checkTransformationBeforeMove();
            assertEquals(SuperState.TRANSFORMING, controller.getState());
            for (int i = 0; i < 80 && controller.getState() != SuperState.SUPER; i++) {
                fixture.stepFrame(false, false, false, false, false);
            }
            assertEquals(SuperState.SUPER, controller.getState(), "production palette releases the transform");
            assertNotSame(normalScripts, player.getAnimationSet(), "Super scripts come from the ROM");
        }
        return new Run(fixture, controller);
    }

    private static Stream<Arguments> edgeCases() {
        return Stream.of(false, true).flatMap(superForm ->
                Stream.of(false, true).flatMap(leftEdge ->
                        Stream.of(false, true).flatMap(precarious ->
                                Stream.of(false, true).map(toward ->
                                        Arguments.of(superForm, leftEdge, precarious, toward)))));
    }

    @ParameterizedTest(name = "Super={0}, left={1}, precarious={2}, toward={3}")
    @MethodSource("edgeCases")
    void objectEdgesDispatchTheNativeFormSpecificBalance(
            boolean superForm, boolean leftEdge, boolean precarious, boolean toward) throws Exception {
        var run = boot(superForm);
        arrangeObjectEdge(run, leftEdge, precarious, toward);
        tick(run, 1);
        assertBalance(run, superForm, leftEdge, precarious, toward);
    }

    @ParameterizedTest(name = "Super={0}, left={1}, precarious={2}, toward={3}")
    @MethodSource("edgeCases")
    void terrainEdgesUseTheSameNativeSuperBranch(
            boolean superForm, boolean leftEdge, boolean precarious, boolean toward) throws Exception {
        var run = boot(superForm);
        var player = run.player();
        int inset = precarious ? 7 : 3;
        int cliffX = PLATFORM_X + (leftEdge ? inset : -inset);
        arrangePlayer(player, PLATFORM_X, facing(leftEdge, toward));
        player.setGroundSensors(new Sensor[] {
                new CliffSensor(player, (byte) -9, cliffX, leftEdge),
                new CliffSensor(player, (byte) 9, cliffX, leftEdge)
        });
        // The first real AnglePos pass publishes the prior-probe angle bytes.
        // The next movement dispatch consumes them; no private latch seeding.
        tick(run, 1);
        assertFalse(player.getAir(), "one foot remains supported by the independent cliff");
        player.setDirection(facing(leftEdge, toward));
        restartAnimation(player);
        tick(run, 2);
        assertBalance(run, superForm, leftEdge, precarious, toward);
    }

    @Test
    void superProfileAndBalancePhaseSurviveWorldRewindAndRevertToOrdinarySonic() throws Exception {
        var run = boot(true);
        arrangeObjectEdge(run, false, true, false);
        var registry = run.fixture().gameplayMode().getRewindRegistry();
        var before = registry.capture();
        tick(run, 1);
        assertBalance(run, true, false, true, false);
        for (int frame = 2; frame <= 15; frame++) tick(run, frame);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertEquals(PhysicsProfile.SONIC_2_SUPER_SONIC, run.player().getPhysicsProfile());
            assertSnapshot(before, registry.capture(), "restored before");
            for (int frame = 1; frame <= 15; frame++) tick(run, frame);
            assertSnapshot(after, registry.capture(), "replayed balance script");
        }
        run.controller().debugDeactivate();
        assertEquals(SuperState.NORMAL, run.controller().getState());
        assertEquals(PhysicsProfile.SONIC_2_SONIC, run.player().getPhysicsProfile());
        // The recreated platform must still own the restored riding contact.
        assertInstanceOf(StablePlatform.class,
                GameServices.level().getObjectManager().getRidingObject(run.player()));
        run.player().setDirection(Direction.LEFT);
        restartAnimation(run.player());
        tick(run, 16);
        assertBalance(run, false, false, true, false);
    }

    private static void arrangeObjectEdge(Run run, boolean leftEdge, boolean precarious, boolean toward) {
        var platform = new StablePlatform(new ObjectSpawn(
                PLATFORM_X, PLATFORM_Y, 0xFE, 0, 0, false, PLATFORM_Y));
        var objects = GameServices.level().getObjectManager();
        objects.addDynamicObject(platform);
        // d1 = playerX + width - objectX. Native ordinary boundaries are
        // d1 < 2 / d1 >= 2*width-2; precarious is d1 < -4 / d1 >= 2*width+4.
        int d1 = leftEdge ? (precarious ? -5 : 1)
                : 2 * HALF_WIDTH + (precarious ? 4 : -2);
        arrangePlayer(run.player(), PLATFORM_X - HALF_WIDTH + d1, facing(leftEdge, toward));
        objects.forceRidingObjectForBootstrap(run.player(), platform);
    }

    private static void arrangePlayer(AbstractPlayableSprite player, int x, Direction direction) {
        GameServices.level().getObjectManager().clearRidingObject(player);
        player.setCentreX((short) x);
        player.setCentreY((short) PLAYER_Y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAngle((byte) 0);
        player.setGroundMode(GroundMode.GROUND);
        player.setAir(false);
        player.setOnObject(false);
        player.setRolling(false);
        player.setJumping(false);
        player.setRollingJump(false);
        player.setPushing(false);
        player.setBalanceState(0);
        player.setDirection(direction);
        restartAnimation(player);
    }

    private static void restartAnimation(AbstractPlayableSprite player) {
        player.setAnimationId(0);
        player.getAnimationManager().publishPreviousAnimationId(0);
        player.setAnimationFrameIndex(0);
        player.setAnimationTick(0);
    }

    private static Direction facing(boolean leftEdge, boolean toward) {
        return leftEdge == toward ? Direction.LEFT : Direction.RIGHT;
    }

    private static void tick(Run run, int levelFrameCounter) {
        var player = run.player();
        player.setDirectionalInputPressed(false, false, false, false);
        player.setJumpInputPressed(false);
        player.captureOnObjectAtFrameStart();
        SpriteManager.tickPlayablePhysics(player, false, false, false, false,
                false, false, false, false, GameServices.level(), levelFrameCounter);
    }

    private static void assertBalance(Run run, boolean superForm, boolean leftEdge,
                                      boolean precarious, boolean toward) {
        int state = superForm ? 1 : (toward ? (precarious ? 2 : 1) : (precarious ? 4 : 3));
        int animation = superForm ? 6 : (toward ? (precarious ? 12 : 6) : (precarious ? 30 : 29));
        int mapping = superForm ? 0xC2 : (toward ? (precarious ? 0xC8 : 0xCC)
                : (precarious ? 0xCF : 0xD0));
        assertEquals(state, run.player().getBalanceState(), "native balance tier");
        assertEquals(animation, run.player().getAnimationId(), "native anim byte");
        assertEquals(mapping, run.player().getMappingFrame(), "ROM script's first mapping frame");
        assertEquals(facing(leftEdge, superForm || toward || precarious), run.player().getDirection(),
                "Super always faces toward the edge; normal safe away-facing pose retains facing");
    }

    private static void assertSnapshot(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }

    /** A fixed half-plane: real movement selects/probes sensors, with no scripted results. */
    private static final class CliffSensor extends Sensor {
        private final int cliffX;
        private final boolean leftEdge;

        private CliffSensor(AbstractPlayableSprite player, byte x, int cliffX, boolean leftEdge) {
            super(player, Direction.DOWN, x, (byte) 19, true);
            this.cliffX = cliffX;
            this.leftEdge = leftEdge;
        }

        @Override protected SensorResult doScan(short dx, short dy) {
            int probeX = sprite.getCentreX() + x + dx;
            boolean supported = leftEdge ? probeX >= cliffX : probeX <= cliffX;
            return supported ? new SensorResult((byte) 0, (byte) 0, 1, Direction.DOWN) : null;
        }
    }

    /** Recreated by the same registered object-manager adapter as production dynamic solids. */
    public static final class StablePlatform extends AbstractObjectInstance
            implements SolidObjectProvider, SpawnRewindRecreatable {
        private static final SolidObjectParams PARAMS = new SolidObjectParams(HALF_WIDTH, 0x10, 0x10);

        public StablePlatform(ObjectSpawn spawn) { super(spawn, "SuperBalanceTestPlatform"); }
        @Override public int getX() { return getSpawn().x(); }
        @Override public int getY() { return getSpawn().y(); }
        @Override public SolidObjectParams getSolidParams() { return PARAMS; }
        @Override public int getBalanceWidthPixels() { return HALF_WIDTH; }
        @Override public boolean isTopSolidOnly() { return true; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
