package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileApplier;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostStarChild;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MGZ1 star post 1: physical activation, physical hazard death and the real checkpoint
 * respawn through the production {@link GameLoop}, with full-world replay around each
 * boundary.
 *
 * <p>Independent ROM contracts (sonic3k.asm). {@code Obj_StarPost} (:61559) tests
 * Player 1 only ({@code sub_2D028}): it activates when {@code x - post.x + 8} is in
 * {@code [0,$10)} and {@code y - post.y + $40} in {@code [0,$68)}, allocates the
 * routine-6 twirl child with {@code $36 = $20} (it orbits until the counter goes
 * negative, then sets the parent's anim 2 and deletes itself, {@code loc_2D10A}),
 * and {@code sub_2D164} writes {@code Last_star_post_hit = subtype} and
 * {@code Saved_X_pos/Saved_Y_pos} = the post's own position. A reloaded post whose
 * subtype is at most {@code Last_star_post_hit} starts used ({@code loc_2CFC0} →
 * {@code loc_2D008}) and never re-saves. The death is physical: the placed
 * {@code Obj_MGZSwingingSpikeBall} (collision {@code $8F}, hurt type) or upright
 * {@code Obj_Spikes} ({@code loc_24090} hurts only a standing player through
 * {@code sub_24280}) reaches {@code HurtCharacter}, which with no rings and no shield
 * takes {@code Kill_Character}. No checkpoint, life, ring, clock, position or death
 * state is written by this test; the approach inlet is a declared position 48 px
 * left of the unactivated post (not a cold route).
 */
@RequiresRom(SonicGame.SONIC_3K)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestS3kMgz1PhysicalCheckpointLifecycle {
    // Independent placement oracle: skdisasm Levels/MGZ/Object Pos/1.bin (1-S3.bin agrees).
    private static final List<List<Integer>> AUTHORED_POSTS = List.of(
            List.of(1, 0x1180, 0x0AF4), List.of(2, 0x1E98, 0x03B4), List.of(3, 0x2AE0, 0x0E72));
    private static final int POST_INDEX = 1, POST_X = 0x1180, POST_Y = 0x0AF4;
    private static final int SPIKES_X = 0x1310, SPIKES_Y = 0x0B1D;
    private static final int BALL_X = 0x1400, BALL_Y = 0x0B34;
    private static final int INLET_X = POST_X - 48, INLET_Y = POST_Y;
    // ROM Touch_Sizes (sonic3k.asm:20749), half-width/half-height per collision size index.
    private static final int[][] TOUCH_SIZES = {{4,4},{0x14,0x14},{0xC,0x14},{0x14,0xC},{4,0x10},{0xC,0x12},
            {0x10,0x10},{6,6},{0x18,0xC},{0xC,0x10},{0x10,8},{8,8},{0x14,0x10},{0x14,8},{0xE,0xE},{0x18,0x18},
            {0x28,0x10},{0x10,0x18},{8,0x10},{0x20,0x70},{0x40,0x20},{0x80,0x20},{0x20,0x20},{8,8},{4,4},{0x20,8},
            {0xC,0xC},{8,4},{0x18,4},{0x28,4},{4,8},{4,0x18},{4,0x28},{0x18,0x18},{0x18,0x18},{0xC,0x18},{0x48,8},
            {0x18,0x28},{0x10,4},{0x20,2},{0x10,0x1C},{0xC,0x24},{0x10,2},{4,0x40},{0x18,0x40},{0x20,0x10},
            {0x1C,0x14},{0x10,2},{0x10,1},{2,8},{0x10,0x40},{0xC,4},{8,0xC},{0x28,0x20},{0x40,2},{0x60,2},{0x28,0x28},
            {0,0},{0,0},{0,0},{0,0},{0,0},{0,0},{0,0}};
    private static final TestS3kCanonicalEntryLifecycle.Act MGZ1 =
            new TestS3kCanonicalEntryLifecycle.Act("MGZ1", 2, 0);

    @Order(0)
    @Test
    void romPlacementsSupplyThePostAndTheHazardsOfThisProduct() {
        HeadlessTestFixture.builder().withZoneAndAct(MGZ1.zone(), MGZ1.act()).build();
        var spawns = GameServices.level().getCurrentLevel().getObjects();
        var posts = spawns.stream().filter(spawn -> spawn.objectId() == Sonic3kObjectIds.STAR_POST)
                .map(spawn -> List.of(spawn.subtype() & 0x7F, spawn.x(), spawn.y()))
                .sorted(Comparator.comparingInt(List::getFirst)).toList();
        assertEquals(AUTHORED_POSTS, posts, "MGZ1 physical post inventory");
        assertTrue(spawns.stream().anyMatch(spawn -> spawn.objectId() == Sonic3kObjectIds.SPIKES
                && spawn.x() == SPIKES_X && spawn.y() == SPIKES_Y && spawn.subtype() == 0), "upright spikes");
        assertTrue(spawns.stream().anyMatch(spawn -> spawn.objectId() == Sonic3kObjectIds.MGZ_SWINGING_SPIKE_BALL
                && spawn.x() == BALL_X && spawn.y() == BALL_Y && spawn.subtype() == 0), "swinging spike ball");
    }

    @Order(1)
    @Test
    void nativeSonicActivatesThePostDiesOnAHazardAndRespawnsThereTwice() {
        run(new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", ""));
    }

    /** Threshold just before the window: the same inlet stopping short never saves; resuming does. */
    @Order(2)
    @Test
    void anApproachThatStopsShortOfTheWindowLeavesThePostUnused() {
        var product = new Product(new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", ""));
        try {
            product.land();
            for (int frame = 0; frame < 16; frame++) product.step(AbstractPlayableSprite.INPUT_RIGHT, false);
            for (int frame = 0; frame < 90; frame++) product.step(0, false);
            var p1 = GameServices.sprites().getMainPlayable();
            assertEquals(0, p1.getGSpeed(), "the stopped approach has settled");
            assertTrue(p1.getCentreX() < POST_X - 8, "stopped left of the ROM window: x=" + p1.getCentreX());
            assertFalse(product.checkpoint().isActive(), "no contact, no save");
            assertTrue(product.twirls().isEmpty(), "no activation child");
            int frames = 0;
            while (!product.checkpoint().isActive() && frames++ < 60) product.step(AbstractPlayableSprite.INPUT_RIGHT, false);
            assertTrue(product.checkpoint().isActive(), "the same inlet saves once it reaches the window");
            assertTrue(p1.getCentreX() >= POST_X - 8 && p1.getCentreX() < POST_X + 8, "activated inside the window");
        } finally {
            product.close();
        }
    }

    /** The native pair: P1 alone owns the post ({@code sub_2D028} reads Player_1). */
    @Order(3)
    @Test
    void nativeSonicAndTailsKeepThePhysicalCheckpointLifecycle() {
        run(new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "tails"));
    }

    /**
     * Every supported solo product of the MGZ1 entry/restart matrix: the five production
     * presets × off/S1/S2 main support (30 rows). Larger rosters are an open obligation: with
     * followers registered, P1 under the swinging ball's sweep was not killed within the bound
     * in 25 of the 110 team rows (MGZ1 matrix), so they are not claimed here.
     */
    static java.util.stream.Stream<CanonicalEntryMatrixSupport.Row> soloProducts() {
        return CanonicalEntryMatrixSupport.configurations().filter(row -> row.followers().isEmpty());
    }

    @Order(4)
    @ParameterizedTest(name = "physical post, hazard death and respawn twice {0}")
    @MethodSource("soloProducts")
    void supportedSoloProductsKeepThePhysicalCheckpointLifecycle(CanonicalEntryMatrixSupport.Row row) {
        run(row);
    }

    private void run(CanonicalEntryMatrixSupport.Row row) {
        var product = new Product(row);
        try {
            product.verify();
        } finally {
            product.close();
        }
    }

    /** One supported configuration from the declared inlet through two real deaths. */
    private static final class Product {
        private final CanonicalEntryMatrixSupport.Row row;
        private final CanonicalEntryMatrixSupport entry = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() { return MGZ1; }
        };
        private final HeadlessTestFixture fixture;
        private final InputHandler input = new InputHandler();
        private final GameLoop loop;
        private Bk2FrameInput previous = pad(0, false);
        private int lives;

        Product(CanonicalEntryMatrixSupport.Row row) {
            this.row = row;
            entry.saveConfiguration();
            fixture = boot();
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(previous, previous));
            loop = new GameLoop(input);
            loop.setGameplayMode(fixture.gameplayMode());
            loop.setGameMode(GameMode.LEVEL);
            assertTrue(loop.ownsGameplayFadeLifecycle(), "production logical iteration owns fade service");
            lives = GameServices.gameState().getLives();
        }

        /** {@link CanonicalEntryMatrixSupport#boot} with the declared positioned inlet. */
        private HeadlessTestFixture boot() {
            var config = entry.config;
            config.clearSessionOverrides();
            new LaunchProfileApplier(config).apply(new LaunchProfile(true, row.donor(), false,
                    row.aspect().name(), row.main(), "none"), MasterTitleScreen.GameEntry.SONIC_3K);
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            if (!row.donor().equals("off")) {
                var rom = row.donor().equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                        : RomTestUtils.ensureSonic2RomAvailable();
                assertNotNull(rom, "required donor ROM " + row.donor());
                config.setSessionOverride(row.donor().equals("s1") ? SonicConfiguration.SONIC_1_ROM
                        : SonicConfiguration.SONIC_2_ROM, rom.getAbsolutePath());
            }
            CrossGameFeatureProvider.getInstance().resetState();
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var builder = HeadlessTestFixture.builder().withZoneAndAct(MGZ1.zone(), MGZ1.act())
                    .startPosition((short) INLET_X, (short) INLET_Y).startPositionIsCentre()
                    .withFreshLevelStartLifecycle();
            if (!row.donor().equals("off")) builder.withCrossGameDonation(row.donor());
            return builder.build();
        }

        void close() {
            try {
                loop.closePresence();
            } finally {
                entry.restoreConfiguration();
            }
        }

        static Bk2FrameInput pad(int direction, boolean jump) {
            return new Bk2FrameInput(0, direction | (jump ? AbstractPlayableSprite.INPUT_JUMP : 0),
                    jump ? InputActionMasks.ACTION_A : 0, false, "");
        }

        void step(int direction, boolean jump) {
            stepThroughLoad(direction, jump);
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode(), this::label);
        }

        /** One production logical iteration; reload, title and fade modes are allowed. */
        void stepThroughLoad(int direction, boolean jump) {
            var current = pad(direction, jump);
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
            previous = current;
            loop.step();
            GameServices.graphics().runPendingRenderThreadTasks();
        }

        void alive(int direction, boolean jump) {
            step(direction, jump);
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), this::label);
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden P1 restart");
            entry.assertOwners(row);
        }

        CheckpointState checkpoint() {
            return assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
        }

        List<Sonic3kStarPostStarChild> twirls() {
            return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostStarChild.class);
        }

        Sonic3kStarPostObjectInstance post() {
            var posts = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class)
                    .stream().filter(post -> post.getSpawn().x() == POST_X && post.getSpawn().y() == POST_Y).toList();
            assertEquals(1, posts.size(), "the ROM-placed post 1 is live exactly once");
            assertEquals(POST_INDEX, posts.getFirst().getCheckpointIndex());
            return posts.getFirst();
        }

        /** The declared inlet drops onto the floor; no input until P1 stands. */
        void land() {
            assertFalse(checkpoint().isActive(), "fresh MGZ1 has no saved post");
            int frames = 0;
            while (GameServices.sprites().getMainPlayable().getAir() && frames++ < 60) alive(0, false);
            assertFalse(GameServices.sprites().getMainPlayable().getAir(), this::label);
            assertFalse(GameServices.sprites().getMainPlayable().isControlLocked(), this::label);
            assertFalse(checkpoint().isActive());
            post();
        }

        void verify() {
            land();
            assertEquals(row.aspect().pixelWidth(), GameServices.camera().getWidth() & 0xFFFF, "configured viewport");
            var registry = fixture.gameplayMode().getRewindRegistry();

            // Spot 1: before activation -> through the contact frame.
            var beforeInput = previous;
            var before = registry.capture();
            int approach = 0;
            while (!checkpoint().isActive() && approach++ < 120) alive(AbstractPlayableSprite.INPUT_RIGHT, false);
            assertTrue(checkpoint().isActive(), () -> "ordinary movement reaches the post: " + label());
            assertActivated();
            alive(AbstractPlayableSprite.INPUT_RIGHT, false);
            var contact = registry.capture();
            var contactInput = previous;
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                previous = beforeInput;
                same(before, registry.capture(), "before activation restore " + cycle);
                assertFalse(checkpoint().isActive(), "restored pre-contact world has no saved post");
                for (int frame = 0; frame <= approach; frame++) alive(AbstractPlayableSprite.INPUT_RIGHT, false);
                same(contact, registry.capture(), "activation replay " + cycle);
                assertActivated();
            }
            previous = contactInput;

            // Spot 2: while the twirl child orbits -> after it retires.
            assertEquals(1, twirls().size(), "the routine-6 child is still orbiting");
            var active = registry.capture();
            for (int frame = 0; frame < 40; frame++) alive(AbstractPlayableSprite.INPUT_RIGHT, false);
            assertTrue(twirls().isEmpty(), "the $20 countdown has retired the twirl child");
            var settled = registry.capture();
            var settledInput = previous;
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(active);
                previous = contactInput;
                same(active, registry.capture(), "active post restore " + cycle);
                assertEquals(1, twirls().size(), "restored orbit child");
                for (int frame = 0; frame < 40; frame++) alive(AbstractPlayableSprite.INPUT_RIGHT, false);
                same(settled, registry.capture(), "active post replay " + cycle);
                assertTrue(twirls().isEmpty());
                assertSaved();
            }
            previous = settledInput;

            for (int death = 0; death < 2; death++) {
                dieOnAHazardAndRespawn(death);
            }
        }

        private void assertActivated() {
            assertSaved();
            post();
            assertEquals(1, twirls().size(), "one routine-6 orbit child per activation");
            var p1 = GameServices.sprites().getMainPlayable();
            assertTrue(p1.getCentreX() >= POST_X - 8 && p1.getCentreX() < POST_X + 8, "inside the ROM X window");
        }

        private void assertSaved() {
            var state = checkpoint();
            assertTrue(state.isActive());
            assertEquals(POST_INDEX, state.getLastCheckpointIndex(), "Last_star_post_hit = subtype");
            assertEquals(POST_X, state.getSavedX(), "Saved_X_pos = post x");
            assertEquals(POST_Y, state.getSavedY(), "Saved_Y_pos = post y");
        }

        /**
         * Ordinary inputs toward the placed hazards: run right and take one running jump over
         * the upright spikes (early enough to pass above the rings over them), then stand on the
         * floor under the swinging ball's anchor until its sweep reaches P1. A ring-bounce onto
         * the ledge above (y below $AF0) walks left off it.
         */
        private void dieOnAHazardAndRespawn(int death) {
            var oldManager = GameServices.level().getObjectManager();
            var oldRuntime = GameServices.zoneRuntimeRegistry().current();
            var history = fixture.gameplayMode().getRewindController();
            assertNotNull(history, "production live history is enabled");
            long outgoing = history.currentFrame();
            int direction;
            String hazard = null;
            int jumpHeld = 0;
            boolean lastJump = false;
            for (int frame = 0; frame < 2400 && hazard == null; frame++) {
                var p1 = GameServices.sprites().getMainPlayable();
                int x = p1.getCentreX();
                if (x < SPIKES_X + 0x20) {
                    direction = AbstractPlayableSprite.INPUT_RIGHT;
                    // A new press needs a released frame before it (jumpHeld reached 0 last frame).
                    if (jumpHeld == 0 && !lastJump && !p1.getAir() && x >= SPIKES_X - 0x80) jumpHeld = 12;
                } else if (p1.getCentreY() < 0xAF0) {
                    direction = AbstractPlayableSprite.INPUT_LEFT;
                } else if (x < BALL_X - 0x18) {
                    direction = AbstractPlayableSprite.INPUT_RIGHT;
                } else if (x > BALL_X + 0x18) {
                    direction = AbstractPlayableSprite.INPUT_LEFT;
                } else {
                    direction = 0;
                }
                boolean jump = jumpHeld > 0;
                if (jumpHeld > 0) jumpHeld--;
                lastJump = jump;
                step(direction, jump);
                var current = fixture.gameplayMode().getRewindController();
                if (current != null) outgoing = Math.max(outgoing, current.currentFrame());
                if (GameServices.sprites().getMainPlayable().getDead()) hazard = hazard(death);
                else {
                    assertEquals(lives, GameServices.gameState().getLives(), "no hidden P1 restart");
                    assertSame(oldManager, GameServices.level().getObjectManager());
                }
            }
            assertNotNull(hazard, () -> "a placed hazard must kill P1: " + label());
            System.out.println("MGZ1 physical post " + row + ": " + hazard);
            assertSaved();
            int elapsed = 0;
            while (GameServices.level().getObjectManager() == oldManager && elapsed++ < 1800) {
                stepThroughLoad(0, false);
                var current = fixture.gameplayMode().getRewindController();
                if (current != null && GameServices.level().getObjectManager() == oldManager)
                    outgoing = Math.max(outgoing, current.currentFrame());
            }
            assertNotSame(oldManager, GameServices.level().getObjectManager(), () -> "death reload: " + label());
            assertNotSame(oldRuntime, GameServices.zoneRuntimeRegistry().current(), "replacement runtime owner");
            assertEquals(MGZ1.zone(), GameServices.level().getCurrentZone());
            assertEquals(MGZ1.act(), GameServices.level().getCurrentAct());
            assertEquals(lives - 1, GameServices.gameState().getLives(), "one actual life per death");
            lives--;
            assertSaved();
            var p1 = GameServices.sprites().getMainPlayable();
            assertEquals(POST_X, p1.getCentreX() & 0xFFFF, "respawn at the post's own Saved_X_pos");
            assertEquals(POST_Y, p1.getCentreY() & 0xFFFF, "respawn at the post's own Saved_Y_pos");
            assertTrue(outgoing > 10, "the outgoing world recorded live history");
            var incoming = fixture.gameplayMode().getRewindController();
            assertTrue(incoming == null || incoming.currentFrame() < outgoing, "the reload retires outgoing history");
            boolean released = false;
            for (int frame = 0; frame < 1800 && !released; frame++) {
                stepThroughLoad(0, false);
                var title = GameServices.module().getTitleCardProvider();
                released = loop.getCurrentGameMode() == GameMode.LEVEL
                        && (title == null || title.isComplete())
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()
                        && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                        && !GameServices.sprites().getMainPlayable().isControlLocked();
            }
            assertTrue(released, () -> "title/fade/assembly release: " + label());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(lives, GameServices.gameState().getLives(), "release hides no restart");
            entry.assertOwners(row);
            assertEquals(row.aspect().pixelWidth(), GameServices.camera().getWidth() & 0xFFFF);
            post();
            assertTrue(twirls().isEmpty(), "the reloaded post starts used: no new activation");
            // Spot 3: the released incoming world, two forward/reverse cycles.
            CheckpointIncomingReplay.verify(fixture, loop, input, POST_INDEX, () -> {
                entry.assertOwners(row);
                assertEquals(row.aspect().pixelWidth(), GameServices.camera().getWidth() & 0xFFFF);
                assertSaved();
            });
            previous = pad(0, false);
            // The used post never re-saves when P1 passes it again.
            for (int frame = 0; frame < 30; frame++) {
                alive(AbstractPlayableSprite.INPUT_RIGHT, false);
                assertTrue(twirls().isEmpty(), "a used post allocates no second orbit child");
            }
        }

        /**
         * Names the placed object whose harmful touch region P1 overlapped on the death frame:
         * an enemy ({@code $00-$3F}) or hurt ({@code $80-$BF}) collision type, measured with the
         * ROM {@code Touch_Sizes} entry against P1's {@code Touch_Process} box (8 wide,
         * {@code y_radius-3} high) plus 6 px for motion after the touch phase, or standing on the
         * upright spikes ({@code loc_24090}: solid width {@code $10+$B}).
         */
        private String hazard(int death) {
            var p1 = GameServices.sprites().getMainPlayable();
            assertEquals(0, p1.getRingCount(), "HurtCharacter kills only with no rings");
            int px = p1.getCentreX(), py = p1.getCentreY(), ph = p1.getYRadius() - 3;
            var nearby = new StringBuilder();
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (!(object instanceof TouchResponseProvider touch)) continue;
                var regions = touch.getMultiTouchRegions() != null ? List.of(touch.getMultiTouchRegions())
                        : List.of(new TouchResponseProvider.TouchRegion(object.getX(), object.getY(),
                                touch.getCollisionFlags()));
                for (var region : regions) {
                    int flags = region.collisionFlags() & 0xFF;
                    int category = flags & 0xC0;
                    if (Math.abs(region.x() - px) < 0x60 && Math.abs(region.y() - py) < 0x60) {
                        nearby.append(' ').append(object.getClass().getSimpleName()).append('@')
                                .append(region.x()).append(',').append(region.y()).append(" flags=")
                                .append(Integer.toHexString(flags));
                    }
                    if (flags == 0 || category == 0x40 || category == 0xC0) continue;
                    int[] size = TOUCH_SIZES[flags & 0x3F];
                    if (Math.abs(region.x() - px) < size[0] + 8 + 6 && Math.abs(region.y() - py) < size[1] + ph + 6) {
                        var spawn = ((com.openggf.level.objects.AbstractObjectInstance) object).getSpawn();
                        return object.getClass().getSimpleName() + " placed (" + spawn.x() + "," + spawn.y()
                                + ") flags " + Integer.toHexString(flags) + " death " + death + " at " + px + "," + py;
                    }
                }
            }
            if (Math.abs(px - SPIKES_X) < 0x10 + 0x0B && py < SPIKES_Y) {
                return "upright spikes death " + death + " at " + px + "," + py;
            }
            return fail("P1 died away from every placed harmful touch region: " + label() + " nearby:" + nearby);
        }

        private void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
            TestS3kCanonicalEntryLifecycle.assertSameWorld(expected, actual, MGZ1 + "/" + row + " " + where, 40);
        }

        private String label() {
            var p1 = GameServices.sprites().getMainPlayable();
            var state = GameServices.level().getCheckpointState();
            return MGZ1 + "/" + row + " mode=" + loop.getCurrentGameMode()
                    + " p1=" + p1.getCentreX() + "," + p1.getCentreY() + " v=" + p1.getXSpeed() + "," + p1.getYSpeed()
                    + " air=" + p1.getAir() + " rings=" + p1.getRingCount() + " hurt=" + p1.isHurt()
                    + " dead=" + p1.getDead() + " lives=" + GameServices.gameState().getLives()
                    + " post=" + (state == null ? "none" : state.isActive() + "/" + state.getLastCheckpointIndex())
                    + " camera=" + GameServices.camera().getX() + "," + GameServices.camera().getY();
        }
    }
}
