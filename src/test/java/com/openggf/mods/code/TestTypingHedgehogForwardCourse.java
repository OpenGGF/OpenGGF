package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.openggf.mods.code.ForwardRails.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A forward (format 2) rail through the packaged mod's real Course: the authored entry rows, the
 * native spindash countdown, a fight before a measured stop whose loaded badniks join it, the
 * victory restore with the same bodies converted again, and the run on to the endpoint. The rail is
 * a short pilot authored here on S2 EHZ2 with the shared machinery (zero-length fight); the course's
 * native state where the fight ends and at the endpoint must equal the authoring run's, whatever the
 * fight took. Origin: rails lane, rails v2 step 3, 2026-10-10.
 */
@Tag("slow-suite")
@RequiresRom(SonicGame.SONIC_2)
class TestTypingHedgehogForwardCourse {
    static final Pilot EHZ2 = new Pilot("ehz2", SonicGame.SONIC_2, "s2", 0, 1, "tails", "s2-ehz2.rail");
    static final int ENTRY = 60, CLEANUP = 2, RUN_ON = 300, SOAK = 600;
    static final String MOD = "typing-of-the-hedgehog";

    @TempDir static Path temp;
    static ExampleModHarness harness;

    @AfterAll static void close() throws Exception {
        if (harness != null) harness.close();
        GameServices.configuration().clearSessionOverrides();
    }

    /** The authored pilot: its text, the stand and endpoint rows, and the authoring run's state at each. */
    record Pilot1(String text, int standRow, int endpointRow, int poolSize, Row atStand, Row afterFirst, Row atEndpoint) { }

    /** Authors the pilot: entry, countdown, a run to the first stop with loaded badniks on screen, the fight, the run on. */
    static Pilot1 author(Handoff handoff) throws Exception {
        try (World w = new World(EHZ2, Map.of())) {
            int release = ENTRY + COUNTDOWN_ROWS;
            w.driver.program = row -> row < ENTRY ? 0 : row < release ? countdownMask(row - ENTRY, true) : RIGHT;
            while (w.driver.row <= release) w.advance();
            CourseCheckpoint afterRelease = w.course.capture();
            int afterReleaseRow = w.driver.row, afterReleasePrevious = w.driver.previousMask;
            for (int run = 0; run <= 600; run += 10) {
                w.course.restore(afterRelease);
                w.driver.row = afterReleaseRow;
                w.driver.previousMask = afterReleasePrevious;
                int runEnd = afterReleaseRow + run;
                int[] stillRows = {0};
                w.driver.program = row -> row < runEnd ? RIGHT : sonic().getXSpeed() > 0 ? LEFT : 0;
                boolean bad = false;
                for (int i = 0; i < run + 400 && stillRows[0] < 12; i++) {
                    w.advance();
                    var s = sonic();
                    if (s.isHurt() || s.getDead()) { bad = true; break; }
                    stillRows[0] = w.driver.row > runEnd && s.getGSpeed() == 0 && s.getXSpeed() == 0 && !s.getAir() ? stillRows[0] + 1 : 0;
                }
                var s = sonic();
                if (bad || stillRows[0] < 12 || s.isOnObject() || Math.abs(signedAngle(s.getAngle())) > 4 || !geometry().flat()) continue;
                List<?> pool = handoff.pool();
                if (pool.isEmpty()) continue;
                int standRow = w.driver.row;
                CourseCheckpoint stop = w.course.capture();
                int stopPrevious = w.driver.previousMask;
                Row atStand = Row.observe(w.course);
                // The fight's world: the conversion, then Sonic held for the soak.
                w.driver.before = () -> handoff.convert(pool);
                w.driver.hold = true;
                for (int i = 0; i < SOAK && !bad; i++) {
                    w.advance();
                    bad = sonic().getDead();
                }
                w.driver.hold = false;
                if (bad) continue;
                // The zero-length fight: restore the stop, convert, the quiet cleanup rows, then run on.
                w.course.restore(stop);
                w.driver.row = standRow;
                w.driver.previousMask = stopPrevious;
                w.driver.before = () -> handoff.convert(pool);
                int endpoint = standRow + CLEANUP + RUN_ON;
                w.driver.program = row -> row < standRow + CLEANUP ? 0 : RIGHT;
                w.advance();
                Row afterFirst = Row.observe(w.course);
                while (w.driver.row < endpoint && !bad) {
                    w.advance();
                    bad = sonic().isHurt() || sonic().getDead() || GameServices.level().getCurrentAct() != EHZ2.act();
                }
                if (bad) continue;
                Row atEndpoint = Row.observe(w.course);
                int[] masks = w.driver.played(endpoint);
                StringBuilder pools = new StringBuilder();
                for (Object b : pool) pools.append(' ').append(token(b));
                String text = "format 2\npolicy forward-v1\ncombat live-restore-v1\ncourse pilot\nlaunch native-spindash\n"
                        + "profile width=400\ngame s2\nzone 0\nact 1\nteam sonic,tails\nrom 7B905383\nrom-sha1 "
                        + w.course.romSha1() + "\nengine test\ncountdown " + ENTRY + "\nstand 1 " + standRow + " " + CLEANUP
                        + pools + "\nendpoint boss-arena " + endpoint + "\nvalidated " + endpoint + "\nprogram "
                        + TypingHedgehogRailBuilderProbe.rle(masks, endpoint) + "\n";
                return new Pilot1(text, standRow, endpoint, pool.size(), atStand, afterFirst, atEndpoint);
            }
        }
        return fail("no stop with loaded badniks on screen after the release");
    }

    @Test void aForwardRailRunsItsCountdownFightAndRestoreThroughTheRealCourse() throws Exception {
        harness = buildHarness(temp, "author", Map.of());
        Pilot1 pilot = author(new Handoff(harness, "s2"));
        harness.close();
        harness = buildHarness(temp, "course", Map.of("s2-ehz2.rail", pilot.text()));
        GameServices.configuration().clearSessionOverrides();

        TypingHedgehogRailBuilderProbe.resetModule();
        var config = GameServices.configuration();
        applyProfile(config, EHZ2, Map.of());
        GameServices.graphics().setProjectionWidth(WIDTH);
        GameModule effective = harness.apply(GameServices.module());
        Path storage = temp.resolve("work-course").resolve("storage").resolve("mods").resolve(MOD);
        Files.createDirectories(storage);
        Files.writeString(storage.resolve("intent.txt"), "mode=ARCADE\ngame=s2\nzone=0\nact=1\ndifficulty=EASY\n"
                + "capitals=false\npunctuation=false\ndictionaries=standard\nseed=7\nbossStars=1\nnoFail=true\nslowMotion=false\n");
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(EHZ2.zone(), EHZ2.act()).build();
        GameServices.level().getObjectManager().initVblaCounter(0);
        var course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        var input = new InputHandler();

        List<String> phases = new ArrayList<>();
        Row atStand = null, afterFight = null, atEndpoint = null;
        int loaded = -1, countdownRows = 0, fightNative = 0;
        for (int i = 0; i < 40_000 && atEndpoint == null; i++) {
            Object view = view();
            String phase = (String) get(view, "phase");
            int railRow = (int) get(view, "railRow");
            assertNull(get(view, "fault"), "rail fault: " + get(view, "fault"));
            if (phases.isEmpty() || !phases.getLast().equals(phase)) phases.add(phase);
            if (phase.equals("COUNTDOWN")) countdownRows++;
            if (phase.equals("RUN") && railRow == pilot.standRow() && atStand == null) atStand = Row.observe(course);
            // The victory restore plays the stand row itself on the same step: the first run row after the fight.
            if (phase.equals("RUN") && railRow == pilot.standRow() + 1 && afterFight == null) afterFight = Row.observe(course);
            if (phase.equals("RUN") && railRow == pilot.endpointRow()) atEndpoint = Row.observe(course);
            if (phase.equals("STAND")) {
                loaded = Math.max(loaded, (int) get(view, "loaded"));
                fightNative = Math.max(fightNative, (int) get(view, "fightNativeRows"));
                typeNext(view, input);
            }
            if (atEndpoint != null) break;
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }
        System.out.println("FORWARD|pilot stand row " + pilot.standRow() + " pool " + pilot.poolSize() + " endpoint "
                + pilot.endpointRow() + "; course phases " + phases + ", loaded " + loaded + ", fight native rows "
                + fightNative + ", countdown rows " + countdownRows);
        assertNotNull(atEndpoint, "the course reached its endpoint; phases " + phases);
        assertTrue(phases.containsAll(List.of("ENTRY", "COUNTDOWN", "RUN", "STAND", "STAND_CLEAR")), "phases " + phases);
        assertTrue(countdownRows >= COUNTDOWN_ROWS, "the countdown played its native rows: " + countdownRows);
        assertEquals(pilot.poolSize(), loaded, "the loaded badniks joined the fight");
        assertTrue(fightNative > 0, "the fight's world lived");
        assertEquals(List.of(), pilot.atStand().differences(atStand), "the course reaches the stand as authored");
        assertNotNull(afterFight, "the run went on after the fight; phases " + phases);
        assertEquals(List.of(), pilot.afterFirst().differences(afterFight), "the victory restore returns to the stand as authored");
        assertEquals(List.of(), pilot.atEndpoint().differences(atEndpoint), "the run on after the fight is the authored run");
    }

    private static void typeNext(Object view, InputHandler input) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> targets = (List<Object>) get(view, "targets");
        Object target = targets.stream().filter(t -> {
            try { return (boolean) get(t, "locked"); } catch (Exception e) { return false; }
        }).findFirst().orElse(targets.stream().filter(t -> {
            try { return (boolean) get(t, "typeable"); } catch (Exception e) { return false; }
        }).findFirst().orElse(null));
        if (target == null) return;
        String typed = (String) get(target, "typed");
        int progress = (int) get(target, "progress");
        if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
    }

    private static Object view() throws Exception {
        Class<?> type = harness.loader().loadClass("typinghog.play.Course");
        Object course = GameModuleRegistry.getCurrent().getGameService(type);
        return course.getClass().getMethod("view").invoke(course);
    }

    private static Object get(Object record, String component) throws Exception {
        Method method = record.getClass().getMethod(component);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
