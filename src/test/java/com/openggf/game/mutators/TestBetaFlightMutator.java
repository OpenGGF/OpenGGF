package com.openggf.game.mutators;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native S3K AIZ1 Knuckles through real movement owners. The expected flight values come from the
 * Sonic 3 CES 12/22/93 cartridge's leftover Knuckles object (see {@link BetaFlightRuntime}).
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestBetaFlightMutator {
    private static final String KEY = "test-mutators:beta-flight";
    @TempDir Path temp;
    private String oldRoot;
    private ModFaultBoundary faults;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        oldRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("player").toString());
        faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        if (oldRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", oldRoot);
    }

    private enum Session { STOCK, REGISTERED_OFF, ENABLED, FIXED_STEERING }

    /** Opens S3K with Knuckles alone; the Lab-like module is absent, registered off, or enabled. */
    private WorldSession open(Session kind) {
        SessionManager.clear();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var s3k = new Sonic3kGameModule();
        if (kind == Session.STOCK) {
            GameModuleRegistry.setCurrent(s3k);
            SessionManager.openGameplaySession(s3k, s3k, null);
            TestEnvironment.activeGameplayMode();
            return SessionManager.getCurrentWorldSession();
        }
        var definition = new MutatorDefinition("beta-flight", "Beta Flight", "", MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.BETA_FLIGHT),
                options -> List.of(new MutatorPolicy.BetaFlight(kind == Session.FIXED_STEERING)));
        var catalog = new MutatorCatalog(List.of(new OwnedMutator("test-mutators", definition)), faults);
        var module = new DelegatingGameModule(s3k, "test-mutators:beta-flight-fixture") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                if (type == MutatorCatalog.class) return type.cast(catalog);
                if (type == MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int zone, int act) { return Set.of(MutatorCapability.BETA_FLIGHT); }
                    public boolean supportsPlayer(String key, boolean leader) { return leader; }
                    public boolean supportsPlayer(MutatorCapability capability, String key, boolean leader) {
                        return leader && key.equals("knuckles");
                    }
                });
                return super.getGameService(type);
            }
        };
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(s3k, module, null);
        TestEnvironment.activeGameplayMode();
        var world = SessionManager.getCurrentWorldSession();
        if (kind == Session.ENABLED || kind == Session.FIXED_STEERING) {
            var state = MutatorWorldAccess.state(world);
            state.requestEnabled(KEY, true);
            assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        }
        return world;
    }

    private static HeadlessTestFixture settledAiz1() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(60);
        assertEquals("knuckles", fixture.sprite().characterKey().persisted());
        assertFalse(fixture.sprite().getAir(), "Knuckles must start the probe standing");
        return fixture;
    }

    private record Frame(int x, int y, int xVel, int yVel, int gVel, int flag, int anim, boolean air,
                         Direction facing) {
        static Frame of(AbstractPlayableSprite s) {
            return new Frame(s.getCentreX(), s.getCentreY(), s.getXSpeed(), s.getYSpeed(), s.getGSpeed(),
                    s.getDoubleJumpFlag(), s.getAnimationId(), s.getAir(), s.getDirection());
        }
    }

    /** Run right, jump, release, re-press and hold the air move, release, then settle. */
    private static List<Frame> runAirMoveRoute(HeadlessTestFixture fixture) {
        var frames = new ArrayList<Frame>();
        var knuckles = fixture.sprite();
        for (int i = 0; i < 20; i++) { fixture.stepFrame(false, false, false, true, false); frames.add(Frame.of(knuckles)); }
        fixture.stepFrame(false, false, false, true, true); frames.add(Frame.of(knuckles));
        for (int i = 0; i < 6; i++) { fixture.stepFrame(false, false, false, true, false); frames.add(Frame.of(knuckles)); }
        for (int i = 0; i < 24; i++) { fixture.stepFrame(false, false, false, true, true); frames.add(Frame.of(knuckles)); }
        for (int i = 0; i < 90; i++) { fixture.stepFrame(false, false, false, false, false); frames.add(Frame.of(knuckles)); }
        return frames;
    }

    @Test
    void disabledLeavesNativeGlideUnchanged() {
        open(Session.STOCK);
        var stock = runAirMoveRoute(settledAiz1());
        assertTrue(stock.stream().anyMatch(f -> f.flag() == 1 && f.anim() == 0x20),
                "The probe must actually reach the native glide");

        open(Session.REGISTERED_OFF);
        var registeredOff = runAirMoveRoute(settledAiz1());
        assertEquals(stock, registeredOff, "A registered but disabled mutator must not change the glide");

        // Break the comparison on purpose: the enabled mutator must diverge on the same route.
        open(Session.ENABLED);
        var enabled = runAirMoveRoute(settledAiz1());
        assertNotEquals(stock, enabled, "The comparison must be able to see the mutator");
    }

    @Test
    void entryLaunchesAtLeastFourHundredAndKeepsYVelocity() {
        var world = open(Session.ENABLED);
        var fixture = settledAiz1();
        var knuckles = fixture.sprite();
        fixture.stepFrame(false, false, false, false, true);
        for (int i = 0; i < 6; i++) fixture.stepFrame(false, false, false, false, false);
        assertTrue(knuckles.getAir());
        assertEquals(0, knuckles.getDoubleJumpFlag());
        fixture.stepFrame(false, false, false, false, true);
        var runtime = WorldSessionPolicyAccess.getService(world, BetaFlightRuntime.class);
        assertTrue(runtime.owns(knuckles), "The press enters beta flight, not the glide");
        assertEquals(1, knuckles.getDoubleJumpFlag());
        // Standing start: ground_vel was zero, so $400 toward the facing (right). The rest of the
        // entry frame is the ordinary air path, whose ChgJumpDir apex drag may then trim x_vel.
        assertEquals(0x400, knuckles.getGSpeed());
        assertEquals(BetaFlightRuntime.FLIGHT_ANIMATION, knuckles.getAnimationId());
        assertNotEquals(10, knuckles.getXRadius(), "Beta flight keeps standing radii, unlike the glide's 10x10");
    }

    @Test
    void flapsWithoutATimerAndSteeringOnlyTurns() {
        var world = open(Session.ENABLED);
        var fixture = settledAiz1();
        var knuckles = fixture.sprite();
        var runtime = WorldSessionPolicyAccess.getService(world, BetaFlightRuntime.class);
        fixture.stepFrame(false, false, false, false, true);
        for (int i = 0; i < 6; i++) fixture.stepFrame(false, false, false, false, false);
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(runtime.owns(knuckles));

        // Descend: +8 per frame, no other gravity on the flight path.
        int before = knuckles.getYSpeed();
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(before + 0x08, knuckles.getYSpeed());
        assertEquals(1, knuckles.getDoubleJumpFlag());

        // Hold left: facing flips and ground_vel moves, but x_vel keeps its launch value.
        int xVel = knuckles.getXSpeed();
        int gVel = knuckles.getGSpeed();
        fixture.stepFrame(false, false, true, false, false);
        assertTrue(runtime.owns(knuckles));
        assertEquals(Direction.LEFT, knuckles.getDirection());
        assertEquals(xVel, knuckles.getXSpeed(), "CES steering writes ground_vel, not x_vel");
        assertEquals(gVel - 2 * knuckles.getRunAccel(), knuckles.getGSpeed());

        // A press with y_vel >= -$100 starts a flap that lifts by $20 per frame.
        while (knuckles.getYSpeed() < BetaFlightRuntime.FLAP_SPEED_LIMIT) {
            fixture.stepFrame(false, false, false, false, false);
        }
        fixture.stepFrame(false, false, false, false, true);
        assertEquals(2, knuckles.getDoubleJumpFlag(), "The press frame starts the flap");
        int flapStart = knuckles.getYSpeed();
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(3, knuckles.getDoubleJumpFlag());
        assertTrue(knuckles.getYSpeed() == flapStart - BetaFlightRuntime.FLAP_ACCELERATION
                || knuckles.getYSpeed() == 0, "Flap lift is $20 unless the camera top stops it");

        // Keep flapping for longer than Tails' 8-second limit: beta flight never tires.
        for (int frame = 0; frame < 600; frame++) {
            fixture.stepFrame(false, false, false, false, frame % 8 == 0);
            assertTrue(knuckles.getAir() && runtime.owns(knuckles), "Still flying at frame " + frame);
            assertEquals(BetaFlightRuntime.FLIGHT_ANIMATION, knuckles.getAnimationId());
        }

        // Stop flapping: landing clears the flag, ends ownership and releases the pose.
        for (int i = 0; i < 900 && knuckles.getAir(); i++) fixture.stepFrame(false, false, false, false, false);
        assertFalse(knuckles.getAir(), "Knuckles must land");
        assertEquals(0, knuckles.getDoubleJumpFlag());
        assertFalse(runtime.owns(knuckles));
        assertNotEquals(BetaFlightRuntime.FLIGHT_ANIMATION, knuckles.getForcedAnimationId());
    }

    @Test
    void fixedSteeringAcceleratesXVelocityInFlight() {
        var world = open(Session.FIXED_STEERING);
        var fixture = settledAiz1();
        var knuckles = fixture.sprite();
        var runtime = WorldSessionPolicyAccess.getService(world, BetaFlightRuntime.class);
        fixture.stepFrame(false, false, false, false, true);
        for (int i = 0; i < 6; i++) fixture.stepFrame(false, false, false, false, false);
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(runtime.owns(knuckles));
        fixture.stepFrame(false, false, false, false, false);

        // Hold left: the CES arithmetic now acts on x_vel, and ground_vel is left alone.
        int xVel = knuckles.getXSpeed();
        int gVel = knuckles.getGSpeed();
        fixture.stepFrame(false, false, true, false, false);
        assertTrue(runtime.owns(knuckles));
        assertEquals(Direction.LEFT, knuckles.getDirection());
        assertEquals(xVel - 2 * knuckles.getRunAccel(), knuckles.getXSpeed(), "Fixed steering writes x_vel");
        assertEquals(gVel, knuckles.getGSpeed());

        // Holding right accelerates the $400 launch up to the top-speed cap, which the cartridge never reaches.
        for (int i = 0; i < 40; i++) fixture.stepFrame(false, false, false, true, i % 8 == 0);
        assertTrue(runtime.owns(knuckles), "Still flying");
        assertEquals(Direction.RIGHT, knuckles.getDirection());
        assertEquals(knuckles.getMax(), knuckles.getXSpeed());
    }

    @Test
    void ownershipIsCapturedForRewind() {
        var runtime = new BetaFlightRuntime(() -> BetaFlightRuntime.Policy.STOCK);
        assertEquals(new BetaFlightRuntime.Snapshot(false), runtime.capture());
        runtime.restore(new BetaFlightRuntime.Snapshot(true));
        assertEquals(new BetaFlightRuntime.Snapshot(true), runtime.capture());
        runtime.resetForMissingSnapshot();
        assertEquals(new BetaFlightRuntime.Snapshot(false), runtime.capture());
        assertEquals("beta-flight", runtime.key());
    }
}
