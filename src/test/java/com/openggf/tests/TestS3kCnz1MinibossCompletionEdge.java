package com.openggf.tests;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileApplier;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzMinibossInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossScrollControlInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossTopInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CNZ1 miniboss completion edge from the authored ROM placement, driven only by
 * ordinary controller input.
 *
 * <p>Declared inlet: P1 starts at rest at the ROM centre of CNZ1 star post 3
 * ({@code Obj_StarPost} subtype 3 at {@code ($2CC0,$2A8)} in the S&amp;K Act 1
 * layout). The fixture places P1 there as a fresh act entry; it is not a
 * checkpoint respawn, a cold act route or a seeded post state. Nothing after the
 * inlet writes position, camera, rings, lives, clock, checkpoint or boss state.
 *
 * <p>The three maintained input resources are fixed held-button streams. They
 * were recovered once from the already successful retained runs, without trying
 * alternatives during this recovery. Tests never steer from observed boss state.
 * Native Knuckles, other presets, donors and roster shapes remain separate gaps.
 * Full-world snapshots include registered players, objects/children, terrain,
 * events, art and audio. This fixture does not drive live GameLoop history and
 * does not claim a restore across the CNZ1-to-CNZ2 owner replacement.
 *
 * <p>ROM owners: {@code Obj_CNZMiniboss} (outer {@code Camera_X_pos >= $31E0}
 * gate, {@code Obj_Wait} of {@code 2*60} frames, {@code Obj_CNZMinibossGo}),
 * {@code CNZMiniboss_CheckPlayerHit} (player hit opens the coil),
 * {@code CNZMinibossTop_CheckHitBase}/{@code CNZMiniboss_CheckTopHit} (the top
 * strikes the open coil; {@code $45} counts four real hits),
 * {@code CNZMiniboss_BossDefeated} ({@code Events_fg_5}),
 * {@code Obj_CNZMinibossScrollControl} ({@code loc_52042} wait/slow/snap path),
 * {@code CNZ1BGE_AfterBoss}/{@code CNZ1BGE_FGRefresh}/{@code CNZ1BGE_DoTransition},
 * {@code Obj_CNZMinibossEndGo} (end-sign control) and the results/title owners.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz1MinibossCompletionEdge {

    private static final int POST3_X = 0x2CC0;
    private static final int POST3_Y = 0x02A8;
    private static final int MINIBOSS_X = 0x32C0;
    private static final int MINIBOSS_Y = 0x020C;
    private static final int ARENA_GATE_X = 0x31E0;
    /** ROM Obj_CNZMiniboss: {@code move.w #2*60,$2E(a0)} before Obj_CNZMinibossGo. */
    private static final int ARENA_RELEASE_WAIT = 2 * 60;
    private static final int ROUTE_LIMIT = 9000;
    private static final int UP = AbstractPlayableSprite.INPUT_UP;
    private static final int DOWN = AbstractPlayableSprite.INPUT_DOWN;
    private static final int LEFT = AbstractPlayableSprite.INPUT_LEFT;
    private static final int RIGHT = AbstractPlayableSprite.INPUT_RIGHT;
    private static final int JUMP = AbstractPlayableSprite.INPUT_JUMP;

    private static final int ROUTINE_OPENING = 8;
    private static final int ROUTINE_WAIT_HIT = 0x0A;
    private static final int ROUTINE_CLOSING = 0x0C;

    record Row(String main, WidescreenAspect aspect, String followers) {
        @Override
        public String toString() {
            return main + "+" + (followers.isEmpty() ? "solo" : followers) + "@" + aspect.pixelWidth();
        }
    }

    static Stream<Row> rows() {
        return Stream.of(
                new Row("sonic", WidescreenAspect.NATIVE_4_3, "tails"),
                new Row("sonic", WidescreenAspect.NATIVE_4_3, ""),
                new Row("sonic", WidescreenAspect.SUPER_32_9, "tails"),
                new Row("sonic", WidescreenAspect.SUPER_32_9, ""),
                new Row("knuckles", WidescreenAspect.SUPER_32_9, ""));
    }

    /** Milestones measured on the authored live pass. */
    static final class Route {
        final Row row;
        final int[] inputs;
        final List<String> prints;
        final int spawn;
        final int gate;
        final int release;
        final int firstTop;
        final List<Integer> hits;
        final List<Integer> openings;
        final int kill;
        final Map<Integer, Integer> bgFirst;
        final int scrollControlGone;
        final int sign;
        final int results;
        final int load;
        final int apparent;
        final int titleComplete;
        final int control;

        Route(Row row, int[] inputs, List<String> prints, Map<String, Object> m) {
            this.row = row;
            this.inputs = inputs;
            this.prints = prints;
            spawn = (int) m.get("spawn");
            gate = (int) m.get("gate");
            release = (int) m.get("release");
            firstTop = (int) m.get("firstTop");
            @SuppressWarnings("unchecked") List<Integer> h = (List<Integer>) m.get("hits");
            hits = h;
            @SuppressWarnings("unchecked") List<Integer> o = (List<Integer>) m.get("openings");
            openings = o;
            kill = (int) m.get("kill");
            @SuppressWarnings("unchecked") Map<Integer, Integer> bg = (Map<Integer, Integer>) m.get("bgFirst");
            bgFirst = bg;
            scrollControlGone = (int) m.get("scrollControlGone");
            sign = (int) m.get("sign");
            results = (int) m.get("results");
            load = (int) m.get("load");
            apparent = (int) m.get("apparent");
            titleComplete = (int) m.get("titleComplete");
            control = (int) m.get("control");
        }

        @Override
        public String toString() {
            return row + " spawn=" + spawn + " gate=" + gate + " release=" + release
                    + " top=" + firstTop + " openings=" + openings + " hits=" + hits + " kill=" + kill
                    + " bg=" + bgFirst + " scrollGone=" + scrollControlGone + " sign=" + sign
                    + " results=" + results + " load=" + load + " apparent=" + apparent
                    + " title=" + titleComplete + " control=" + control;
        }
    }

    private static final Map<Row, Route> OBSERVED = new ConcurrentHashMap<>();

    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void saveConfiguration() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        }
    }

    @AfterEach
    void restoreConfiguration() {
        CrossGameFeatureProvider.getInstance().resetState();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "natural edge {0}")
    @MethodSource("rows")
    void placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row row) {
        Route route = observedRoute(row);
        System.out.println("CNZ1-EDGE " + route);

        // Arena: the gate arms on the first frame Camera_X_pos reaches $31E0 and
        // Obj_CNZMinibossGo runs after the ROM 2*60-frame Obj_Wait.
        assertTrue(route.spawn < route.gate, route + ": placement admitted before the arena gate");
        assertEquals(ARENA_RELEASE_WAIT, route.release - route.gate,
                route + ": Obj_Wait 2*60 between the $31E0 lock and Obj_CNZMinibossGo");
        assertTrue(route.firstTop > route.release, route + ": Obj_CNZMinibossInit creates the top only after release");

        // Four real hits ($45), each after an ordinary player hit opened the coil.
        assertEquals(4, route.hits.size(), route + ": four top-on-open-coil hits");
        int previous = route.release;
        for (int hit : route.hits) {
            int opener = previous;
            assertTrue(route.openings.stream().anyMatch(o -> o > opener && o < hit),
                    route + ": hit at " + hit + " needs a player-hit opening after " + opener);
            previous = hit;
        }
        assertEquals(route.hits.get(3), route.kill, route + ": the fourth $45 decrement is the killing hit");

        // Defeat -> scroll control slow path -> FG refresh -> transition, in ROM order.
        List<Integer> order = List.of(Sonic3kCNZEvents.BG_BOSS, Sonic3kCNZEvents.BG_AFTER_BOSS,
                Sonic3kCNZEvents.BG_FG_REFRESH, Sonic3kCNZEvents.BG_FG_REFRESH_2,
                Sonic3kCNZEvents.BG_DO_TRANSITION);
        int last = -1;
        for (int routine : order) {
            Integer first = route.bgFirst.get(routine);
            assertNotNull(first, route + ": Events_routine_bg never reached " + Integer.toHexString(routine));
            assertTrue(first > last, route + ": Events_routine_bg order at " + Integer.toHexString(routine));
            last = first;
        }
        assertTrue(route.bgFirst.get(Sonic3kCNZEvents.BG_AFTER_BOSS) > route.kill,
                route + ": AFTER_BOSS follows the real defeat signal");
        assertTrue(route.scrollControlGone > route.kill
                        && route.scrollControlGone <= route.bgFirst.get(Sonic3kCNZEvents.BG_FG_REFRESH),
                route + ": the production scroll control consumes the defeat and deletes at the FG handoff");
        assertTrue(route.sign > route.kill, route + ": end sign after defeat");
        assertTrue(route.results > route.sign, route + ": results after the end sign");
        assertTrue(route.load > route.results, route + ": CNZ2 reload while results own the screen");
        assertTrue(route.apparent > route.load, route + ": apparent act publishes after the reload");
        assertTrue(route.titleComplete >= route.apparent, route + ": CNZ2 title completes after publication");
        assertTrue(route.control > route.titleComplete, route + ": ordinary control in released CNZ2");
    }

    @ParameterizedTest(name = "replay/rewind {0}")
    @MethodSource("rows")
    void recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row row) {
        Route route = observedRoute(row);
        HeadlessTestFixture fixture = boot(row);
        var registry = fixture.gameplayMode().getRewindRegistry();
        TreeMap<Integer, String> checkpoints = new TreeMap<>();
        checkpoints.put(Math.max(12, route.spawn - 20), "prespawn");
        int attack = route.openings.get(0) - 12;
        checkpoints.put(attack, "active-attack");
        checkpoints.put(route.kill - 8, "killing-hit");
        checkpoints.put(route.bgFirst.get(Sonic3kCNZEvents.BG_AFTER_BOSS) + 10, "scroll-after-defeat");
        checkpoints.put(route.bgFirst.get(Sonic3kCNZEvents.BG_FG_REFRESH) + 1, "foreground-refresh");
        checkpoints.put(route.sign + 64, "end-sign");
        checkpoints.put(route.results + 1, "results-before-reload");
        checkpoints.put(route.load + 8, "loaded-cnz2-title-held");
        checkpoints.put(route.control, "released-cnz2");
        java.util.Set<Integer> visited = new java.util.TreeSet<>();
        int frame = 0;
        while (frame < route.inputs.length) {
            String label = checkpoints.get(frame);
            int window = "results-before-reload".equals(label) ? 4 : 45;
            if (label != null && frame + window <= route.inputs.length) {
                visited.add(frame);
                String context = route.row + " " + label + "@" + frame;
                if (label.equals("active-attack")) {
                    assertTrue(count(CnzMinibossTopInstance.class) == 1 && childCount() >= 2,
                            context + ": live top and coil children at the attack spot " + childSignature());
                }
                if (label.equals("prespawn")) {
                    assertEquals(0, count(CnzMinibossInstance.class), context + ": boss not yet admitted");
                }
                var before = registry.capture();
                String children = childSignature();
                for (int i = 0; i < window; i++) {
                    step(fixture, route.inputs[frame + i]);
                    assertEquals(route.prints.get(frame + i), print(fixture), context + " forward frame " + (frame + i));
                }
                var after = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(before);
                    fixture.runner().primeInputState(prime(route, frame));
                    TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                            context + " immediate restore " + cycle, window);
                    assertEquals(children, childSignature(),
                            context + " restore " + cycle + " recreates children exactly once");
                    for (int i = 0; i < window; i++) {
                        step(fixture, route.inputs[frame + i]);
                        assertEquals(route.prints.get(frame + i), print(fixture),
                                context + " replay " + cycle + " frame " + (frame + i));
                    }
                    TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                            context + " replay " + cycle, window);
                }
                frame += window;
                continue;
            }
            step(fixture, route.inputs[frame]);
            assertEquals(route.prints.get(frame), print(fixture), route.row + " open-loop replay frame " + frame);
            frame++;
        }
        assertEquals(checkpoints.keySet(), visited, "every declared world boundary was replayed twice");
        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(1, GameServices.level().getApparentAct());
    }

    static Stream<Row> gateRows() {
        return Stream.of(new Row("sonic", WidescreenAspect.NATIVE_4_3, ""),
                new Row("sonic", WidescreenAspect.SUPER_32_9, ""));
    }

    @ParameterizedTest(name = "dormant placed boss {0}")
    @MethodSource("gateRows")
    void earlyPlacedBossStaysDormantBeforeRomCameraGate(Row row) {
        HeadlessTestFixture fixture = boot(row);
        assertCensus();
        int[] input = readInput(row);
        boolean observed = false;
        for (int frame = 0; frame < 260; frame++) {
            step(fixture, input[frame]);
            assertTrue((fixture.camera().getX() & 0xFFFF) < ARENA_GATE_X);
            var boss = find(CnzMinibossInstance.class);
            if (boss.isPresent()) {
                observed = true;
                assertEquals(0, boss.get().getCurrentRoutine(), row + " frame " + frame
                        + ": ROM placement is dormant before Camera_X_pos >= $31E0");
                assertEquals(0, count(CnzMinibossTopInstance.class));
            }
        }
        assertTrue(observed, "production streamer admitted the real placement");
    }

    /** Inlet idle: the declared start alone never arms the arena or wakes the placed boss. */
    @Test
    void declaredInletWithoutInputNeverArmsTheArena() {
        Row row = new Row("sonic", WidescreenAspect.NATIVE_4_3, "tails");
        Route route = observedRoute(row);
        HeadlessTestFixture fixture = boot(row);
        for (int f = 0; f < route.release + 300; f++) {
            step(fixture, 0);
            assertFalse(cnz().isBossFlag(), "frame " + f + ": no Boss_flag without reaching $31E0");
            assertEquals(0, count(CnzMinibossTopInstance.class), "frame " + f + ": no top");
            find(CnzMinibossInstance.class).ifPresent(b -> assertEquals(0, b.getCurrentRoutine(),
                    "the placed boss stays dormant"));
        }
        assertTrue((fixture.camera().getX() & 0xFFFF) < ARENA_GATE_X);
    }

    /** A missing attack stream cannot create defeat or the outgoing edge. */
    @Test
    void truncatedAfterApproachNeverCompletesTheEdge() {
        Row row = new Row("sonic", WidescreenAspect.NATIVE_4_3, "");
        Route route = observedRoute(row);
        HeadlessTestFixture fixture = boot(row);
        int cut = route.firstTop;
        for (int frame = 0; frame < cut; frame++) step(fixture, route.inputs[frame]);
        for (int frame = cut; frame < route.inputs.length; frame++) {
            step(fixture, 0);
            final int at = frame;
            find(CnzMinibossInstance.class).ifPresent(b -> assertFalse(b.isDefeatedForChild(),
                    "frame " + at + ": no defeat without the attacking input tail"));
            assertEquals(0, count(S3kSignpostInstance.class), "no end sign without defeat");
            assertEquals(0, GameServices.level().getCurrentAct(), "no CNZ2 reload");
        }
    }

    private static int[] readInput(Row row) {
        String path = "/inputs/s3k/cnz1-miniboss/" + row.main() + "-" + row.aspect().pixelWidth() + ".txt";
        try (var in = TestS3kCnz1MinibossCompletionEdge.class.getResourceAsStream(path)) {
            assertNotNull(in, "maintained input " + path);
            List<Integer> frames = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.trim().split("\\s+");
                int count = Integer.parseInt(fields[0]);
                int mask = Integer.parseInt(fields[1], 16);
                assertTrue(count > 0 && count <= ROUTE_LIMIT && (mask & ~0x1F) == 0, line);
                for (int i = 0; i < count; i++) frames.add(mask);
                assertTrue(frames.size() <= ROUTE_LIMIT, "bounded maintained input");
            }
            return frames.stream().mapToInt(Integer::intValue).toArray();
        } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    private Route observedRoute(Row row) {
        Route cached = OBSERVED.get(row);
        if (cached != null) return cached;
        Route route = observe(row, readInput(row));
        OBSERVED.put(row, route);
        return route;
    }

    private Route observe(Row row, int[] fixedInput) {
        HeadlessTestFixture fixture = boot(row);
        assertCensus();
        AbstractPlayableSprite p = fixture.sprite();
        assertEquals(POST3_X, p.getCentreX() & 0xFFFF, "declared inlet x");
        assertSame(p, GameServices.camera().getFocusedSprite(), "current main owns focus at the inlet");
        int lives = GameServices.gameState().getLives();
        var startManager = GameServices.level().getObjectManager();
        List<Integer> inputs = new ArrayList<>();
        List<String> prints = new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        List<Integer> hits = new ArrayList<>();
        List<Integer> openings = new ArrayList<>();
        Map<Integer, Integer> bgFirst = new LinkedHashMap<>();
        CnzMinibossInstance owner = null;
        int lastHits = 4;
        int lastRoutine = -1;
        int spawn = -1, gate = -1, release = -1, firstTop = -1, kill = -1, scrollGone = -1;
        int sign = -1, results = -1, load = -1, apparent = -1, titleComplete = -1, control = -1;
        boolean scrollSeen = false;
        int minibossMusicAtBoot = minibossMusicPlays();
        int minibossMusic = -1;
        for (int f = 0; f < fixedInput.length; f++) {
            int mask = fixedInput[f];
            step(fixture, mask);
            inputs.add(mask);
            prints.add(print(fixture));
            String where = row + " frame " + f;
            assertFalse(p.getDead(), where + ": P1 died " + prints.get(f));
            assertEquals(lives, GameServices.gameState().getLives(), where + ": no hidden life change");
            assertSame(p, GameServices.sprites().getMainPlayable(), where + ": stable main");
            assertSame(p, GameServices.camera().getFocusedSprite(), where + ": main keeps focus");
            Sonic3kCNZEvents ev = load < 0 ? cnz() : null;
            Optional<CnzMinibossInstance> boss = find(CnzMinibossInstance.class);
            assertTrue(count(CnzMinibossInstance.class) <= 1, where + ": unique boss owner");
            assertTrue(count(CnzMinibossTopInstance.class) <= 1, where + ": unique top");
            assertTrue(count(CnzMinibossScrollControlInstance.class) <= 1, where + ": unique scroll control");
            assertTrue(count(S3kSignpostInstance.class) <= 1, where + ": unique end sign");
            assertTrue(count(S3kResultsScreenObjectInstance.class) <= 1, where + ": unique results owner");
            if (boss.isPresent() && spawn < 0) {
                spawn = f;
                owner = boss.get();
                ObjectSpawn s = owner.getSpawn();
                assertEquals(MINIBOSS_X, s.x(), where + ": placed x");
                assertEquals(MINIBOSS_Y, s.y(), where + ": placed y");
                assertEquals(Sonic3kObjectIds.CNZ_MINIBOSS, s.objectId(), where + ": placed id");
                assertTrue(startManager.getAllSpawns().contains(s), where + ": admitted from the layout spawn list");
            }
            if (boss.isPresent() && load < 0) {
                assertSame(owner, boss.get(), where + ": the admitted owner is never replaced in the act");
            }
            if (ev != null) {
                if (gate < 0 && ev.isBossFlag()) {
                    gate = f;
                    assertTrue((fixture.camera().getX() & 0xFFFF) >= ARENA_GATE_X, where + ": gate at $31E0");
                    assertEquals(ARENA_GATE_X, fixture.camera().getMinX() & 0xFFFF, where + ": arena min X");
                    assertEquals(ARENA_GATE_X + 0x80, fixture.camera().getMaxX() & 0xFFFF, where + ": arena max X");
                }
                if (release < 0 && ev.isMinibossStartReleased()) release = f;
                if (release < 0 && boss.isPresent()) {
                    assertEquals(0, boss.get().getCurrentRoutine(),
                            where + ": Obj_CNZMiniboss stays dormant until Obj_CNZMinibossGo");
                    assertEquals(0, count(CnzMinibossTopInstance.class), where + ": no top before release");
                    assertEquals(0, count(CnzMinibossScrollControlInstance.class),
                            where + ": no scroll control before release");
                }
                bgFirst.putIfAbsent(ev.getBackgroundRoutine(), f);
            }
            if (firstTop < 0 && count(CnzMinibossTopInstance.class) == 1) firstTop = f;
            if (count(CnzMinibossScrollControlInstance.class) == 1) scrollSeen = true;
            if (scrollSeen && scrollGone < 0 && kill >= 0 && count(CnzMinibossScrollControlInstance.class) == 0) {
                scrollGone = f;
            }
            if (boss.isPresent()) {
                int h = boss.get().getRemainingHits();
                if (h != lastHits) {
                    assertEquals(lastHits - 1, h, where + ": $45 decrements by one");
                    hits.add(f);
                    lastHits = h;
                }
                int r = boss.get().getCurrentRoutine();
                if (r == ROUTINE_OPENING && lastRoutine != ROUTINE_OPENING) openings.add(f);
                lastRoutine = r;
                if (kill < 0 && boss.get().isDefeatedForChild()) {
                    kill = f;
                    minibossMusic = minibossMusicPlays() - minibossMusicAtBoot;
                }
            }
            if (sign < 0 && count(S3kSignpostInstance.class) == 1) sign = f;
            if (results < 0 && count(S3kResultsScreenObjectInstance.class) == 1) results = f;
            if (load < 0 && GameServices.level().getCurrentAct() == 1) {
                load = f;
                assertNotSame(startManager, GameServices.level().getObjectManager(), where + ": CNZ2 object owner");
                assertEquals(0, count(CnzMinibossInstance.class) + count(CnzMinibossTopInstance.class)
                        + count(CnzMinibossScrollControlInstance.class) + childCount(),
                        where + ": no miniboss owner or child crosses the reload " + childSignature());
                assertEquals(1, count(S3kResultsScreenObjectInstance.class), where + ": results owner carried");
            }
            if (load >= 0 && apparent < 0 && GameServices.level().getApparentAct() == 1) apparent = f;
            if (apparent >= 0 && titleComplete < 0
                    && GameServices.module().getTitleCardProvider() instanceof Sonic3kTitleCardManager title
                    && title.isComplete()) {
                titleComplete = f;
            }
            if (titleComplete >= 0 && control < 0 && (mask & RIGHT) != 0 && p.getXSpeed() > 0
                    && !p.isObjectControlled() && !p.isControlLocked()) {
                control = f;
                assertEquals(0, count(S3kResultsScreenObjectInstance.class), where + ": results retired");
                assertEquals(1, GameServices.level().getCurrentAct());
                assertEquals(1, GameServices.level().getApparentAct());
                assertRoster(row, where);
            }
            if (control >= 0 && f >= control + 60) {
                assertEquals(1, minibossMusic, row + ": mus_Miniboss starts exactly once");
                m.put("spawn", spawn);
                m.put("gate", gate);
                m.put("release", release);
                m.put("firstTop", firstTop);
                m.put("hits", hits);
                m.put("openings", openings);
                m.put("kill", kill);
                m.put("bgFirst", bgFirst);
                m.put("scrollControlGone", scrollGone);
                m.put("sign", sign);
                m.put("results", results);
                m.put("load", load);
                m.put("apparent", apparent);
                m.put("titleComplete", titleComplete);
                m.put("control", control);
                return new Route(row,
                        inputs.stream().mapToInt(Integer::intValue).toArray(), prints, m);
            }
        }
        fail(row + ": fixed input did not complete the edge: spawn=" + spawn + " gate=" + gate
                + " release=" + release + " hits=" + hits + " kill=" + kill + " sign=" + sign
                + " results=" + results + " load=" + load + " apparent=" + apparent
                + " title=" + titleComplete + " last=" + prints.getLast());
        return null;
    }

    // ---- shared helpers ----

    HeadlessTestFixture boot(Row row) {
        // Each fixed-input pass is an independent fresh act assembly. Retaining
        // a completed previous world's title owner would carry a stale art lease.
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        config.clearSessionOverrides();
        new LaunchProfileApplier(config).apply(new LaunchProfile(true, "off", false,
                row.aspect().name(), row.main(), "none"), MasterTitleScreen.GameEntry.SONIC_3K);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) POST3_X, (short) POST3_Y)
                .startPositionIsCentre()
                .build();
        SozAcceptanceConfigurations.assertDisplay(row.aspect());
        assertEquals(row.main(), config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        return fixture;
    }

    /** ROM census of the S&amp;K CNZ1 layout: one CNZ_MINIBOSS and the post-3 inlet origin. */
    private static void assertCensus() {
        List<ObjectSpawn> spawns = GameServices.level().getObjectManager().getAllSpawns();
        List<ObjectSpawn> bosses = spawns.stream()
                .filter(s -> s.objectId() == Sonic3kObjectIds.CNZ_MINIBOSS).toList();
        assertEquals(1, bosses.size(), "CNZ1 places exactly one CNZ_MINIBOSS");
        assertEquals(MINIBOSS_X, bosses.get(0).x());
        assertEquals(MINIBOSS_Y, bosses.get(0).y());
        assertEquals(0, bosses.get(0).subtype());
        assertTrue(spawns.stream().anyMatch(s -> s.objectId() == Sonic3kObjectIds.STAR_POST
                        && s.x() == POST3_X && s.y() == POST3_Y && s.subtype() == 3),
                "the declared inlet is the placed star post 3 centre");
    }

    private static void assertRoster(Row row, String where) {
        var main = GameServices.sprites().getMainPlayable();
        assertEquals(row.main(), main.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT),
                where + ": configured main");
        long configured = GameServices.sprites().getRegisteredSidekicks().stream()
                .filter(s -> !com.openggf.sprites.managers.SpriteManagerInternalAccess
                        .usesNativeScriptedArt(GameServices.sprites(), s))
                .count();
        assertEquals(row.followers().isEmpty() ? 0 : row.followers().split(",").length, configured,
                where + ": configured follower roster retained " + GameServices.sprites().getRegisteredSidekicks());
    }

    private static void step(HeadlessTestFixture fixture, int mask) {
        fixture.stepFrame((mask & UP) != 0, (mask & DOWN) != 0, (mask & LEFT) != 0,
                (mask & RIGHT) != 0, (mask & JUMP) != 0);
    }

    private static Bk2FrameInput prime(Route route, int frame) {
        int mask = frame == 0 ? 0 : route.inputs[frame - 1];
        return new Bk2FrameInput(frame - 1, mask, (mask & JUMP) != 0 ? 0x01 : 0, false, "");
    }

    private static String print(HeadlessTestFixture fixture) {
        AbstractPlayableSprite p = GameServices.sprites().getMainPlayable();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("p=%04X,%04X v=%d,%d g=%d a=%s r=%s h=%s ring=%d c=%04X,%04X",
                p.getCentreX() & 0xFFFF, p.getCentreY() & 0xFFFF, p.getXSpeed(), p.getYSpeed(), p.getGSpeed(),
                p.getAir(), p.getRolling(), p.isHurt(), p.getRingCount(),
                fixture.camera().getX() & 0xFFFF, fixture.camera().getY() & 0xFFFF));
        for (var s : GameServices.sprites().getRegisteredSidekicks()) {
            sb.append(String.format(" s=%04X,%04X", s.getCentreX() & 0xFFFF, s.getCentreY() & 0xFFFF));
        }
        find(CnzMinibossInstance.class).ifPresent(b -> sb.append(String.format(" b=%04X,%04X/%X/%d/%s",
                b.getCentreX(), b.getCentreY(), b.getCurrentRoutine(), b.getRemainingHits(), b.isDefeatedForChild())));
        find(CnzMinibossTopInstance.class).ifPresent(t -> sb.append(String.format(" t=%04X,%04X", t.getX(), t.getY())));
        sb.append(' ').append(childSignature());
        sb.append(" act=").append(GameServices.level().getCurrentAct()).append('/')
                .append(GameServices.level().getApparentAct());
        if (GameServices.level().getCurrentAct() == 0 && GameServices.level().getCurrentZone() == Sonic3kZoneIds.ZONE_CNZ) {
            Sonic3kCNZEvents ev = cnz();
            sb.append(" bg=").append(Integer.toHexString(ev.getBackgroundRoutine()))
                    .append(" flag=").append(ev.isBossFlag()).append(" rel=").append(ev.isMinibossStartReleased());
        }
        return sb.toString();
    }

    /** Counts of every CNZ miniboss child class plus end-sign/results owners. */
    private static String childSignature() {
        var om = GameServices.level().getObjectManager();
        TreeMap<String, Integer> counts = new TreeMap<>();
        if (om != null) {
            for (ObjectInstance o : om.getActiveObjects()) {
                String name = o.getClass().getSimpleName();
                if (name.startsWith("CnzMiniboss") || name.startsWith("S3kBossExplosion")
                        || name.startsWith("S3kSignpost") || name.startsWith("S3kResults")
                        || name.startsWith("CnzResults")) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts.toString();
    }

    /** Live miniboss children (coil, sparks, debris, explosions), excluding the parent/top/scroll owners. */
    private static int childCount() {
        var om = GameServices.level().getObjectManager();
        int n = 0;
        if (om != null) {
            for (ObjectInstance o : om.getActiveObjects()) {
                String name = o.getClass().getSimpleName();
                if ((name.startsWith("CnzMiniboss") && !(o instanceof CnzMinibossInstance)
                        && !(o instanceof CnzMinibossScrollControlInstance))
                        || name.startsWith("S3kBossExplosion")) {
                    n++;
                }
            }
        }
        return n;
    }

    private static Sonic3kCNZEvents cnz() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getCnzEvents();
    }

    private static int count(Class<?> type) {
        var om = GameServices.level().getObjectManager();
        if (om == null) return 0;
        int n = 0;
        for (ObjectInstance o : om.getActiveObjects()) if (type.isInstance(o)) n++;
        return n;
    }

    private static <T> Optional<T> find(Class<T> type) {
        var om = GameServices.level().getObjectManager();
        if (om == null) return Optional.empty();
        for (ObjectInstance o : om.getActiveObjects()) {
            if (type.isInstance(o)) return Optional.of(type.cast(o));
        }
        return Optional.empty();
    }

    private static int minibossMusicPlays() {
        return (int) commandsOfType(AudioCommand.PlayMusic.class).stream()
                .filter(c -> c.musicId() == Sonic3kMusic.MINIBOSS.id).count();
    }

    private static <T extends AudioCommand> List<T> commandsOfType(Class<T> type) {
        return GameServices.audio().commandTimeline().entries().stream()
                .map(entry -> entry.command())
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }
}
