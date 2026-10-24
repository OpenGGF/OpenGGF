package echoes;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.*;
import com.openggf.level.objects.*;
import com.openggf.mods.code.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** ROM-backed production physics, objects, controlled frame and complete course checkpoint checks. */
@RequiresRom(SonicGame.SONIC_2)
public class FoundationTest {
    public static java.util.function.BiFunction<GgfMod, Path, GameModule> moduleFactory;
    static final class Visit implements AutoCloseable {
        SharedLevel base;
        HeadlessTestFixture fixture;
        RoomSession session;
        Visit() throws Exception {
            base = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "sonic,sonic,sonic,sonic,sonic");
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.WIDE_16_9.name());
            config.resolveDisplayAspect();
            Path jar = Path.of(EchoesMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            GameModule effective = moduleFactory.apply(new EchoesMod(), jar);
            SessionManager.clear(); GameModuleRegistry.setCurrent(effective); TestEnvironment.activeGameplayMode();
            fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            session = GameServices.module().getGameService(RoomSession.class);
            GameServices.level().getObjectManager().setRewindClassResolver(new RewindClassResolver() {
                @Override public Optional<Class<?>> resolve(String owner, String name) {
                    try { return Optional.of(Class.forName(name, true, FoundationTest.class.getClassLoader())); }
                    catch (ClassNotFoundException e) { return Optional.empty(); }
                }
                @Override public Optional<String> ownerOf(Class<?> type) {
                    return type.getName().startsWith("echoes.") ? Optional.of(EchoesMod.ID) : Optional.empty();
                }
            });
            // Held setup/title rows must complete before the first recording row.
            fixture.stepIdleFrames(180);
            assertEquals(5, GameServices.sprites().getSidekicks().size());
        }
        void row(boolean right, boolean left) { fixture.stepFrame(false, false, left, right, false); }
        void rows(int count, boolean right, boolean left) { for (int i=0;i<count;i++) row(right,left); }
        void commit() { session.command(RoomSession.Command.ECHO); row(false,false); finishRewind(); }
        void finishRewind() { while (session.rewindHold() > 0) row(false,false); }
        void solution(int loop) {
            for (var step : session.room().solution().get(loop)) {
                for (int i=0;i<step.frames();i++) fixture.stepFrame(step.buttons().contains("U"), step.buttons().contains("D"),
                        step.buttons().contains("L"), step.buttons().contains("R"), step.buttons().contains("J"));
            }
        }
        @Override public void close() { if (base != null) base.dispose(); }
    }
    @Test public void longSessionKeepsPerFrameWrappersStable() throws Exception {
        try (var v = new Visit()) {
            var module = GameServices.module();
            var events = module.getLevelEventProvider();
            var zones = module.getZoneRegistry();
            var gameOver = module.getGameOverFlowProvider();
            // Seven timed-out 600-frame loops: past the ~3400 frames that exhausted owner callback
            // identities when these were rebuilt on every call.
            v.rows(4200, false, true);
            assertTrue(v.session.loopCount() >= 6, "loops=" + v.session.loopCount());
            assertSame(events, module.getLevelEventProvider());
            assertSame(zones, module.getZoneRegistry());
            assertSame(gameOver, module.getGameOverFlowProvider());
        }
    }
    @Test public void zeroDriftAcrossThreeLoops() throws Exception {
        try (var v = new Visit()) {
            v.rows(20,true,false);
            v.fixture.stepFrame(false,false,false,true,true);
            v.rows(90,false,false); v.commit();
            var recording = v.session.recordings().getFirst();
            for (int loop=0;loop<3;loop++) {
                for (int frame=0;frame<recording.samples().size();frame++) {
                    // Different live inputs, same echo stream and identical restored world.
                    v.row(false,true);
                    var body = GameServices.sprites().getSidekicks().getFirst();
                    var expected = recording.samples().get(frame);
                    assertEquals(expected.x(),body.getCentreX(),"loop="+loop+" frame="+frame+" x");
                    assertEquals(expected.y(),body.getCentreY(),"loop="+loop+" frame="+frame+" y");
                    assertFalse(v.session.fractured(0));
                }
                v.fixture.sprite().setDead(true);
                v.row(false,false); v.finishRewind();
                assertEquals(1,v.session.recordings().size(),"death never commits the live attempt");
                assertEquals(0,v.session.frame());
            }
        }
    }
    @Test public void tutorialReferenceSolution() throws Exception {
        try (var v = new Visit()) {
            // Negative control: the released plate must close the gate before a solo runner arrives.
            v.rows(240,true,false);
            assertFalse(v.session.cleared());
            assertTrue(v.fixture.sprite().getCentreX() < 440,"closed gate must block the live body");
            v.session.command(RoomSession.Command.RESET); v.row(false,false);
            v.solution(0);
            assertEquals(0,v.fixture.sprite().getGSpeed(),"the reference echo stops on its plate");
            v.commit(); assertEquals(1,v.session.recordings().size());
            v.solution(1);
            assertTrue(v.session.cleared(),"live centre="+v.fixture.sprite().getCentreX()+","+v.fixture.sprite().getCentreY());
            assertFalse(v.session.fractured(0));
            assertEquals(1,v.session.lastClearEchoes());
            assertTrue(v.session.channel("cyan"),"the exhausted recording still holds the plate");
            var echo=GameServices.sprites().getSidekicks().getFirst();
            assertEquals(v.session.recordings().getFirst().at(100).x(),echo.getCentreX());
            assertNotSame(v.fixture.sprite().getSpriteRenderer().getRenderContext(),echo.getSpriteRenderer().getRenderContext());
        }
    }
    @Test public void disturbedEchoFractures() throws Exception {
        try (var v = new Visit()) {
            v.rows(20,true,false); v.rows(30,false,false); v.commit();
            v.row(false,true);
            var body = GameServices.sprites().getSidekicks().getFirst();
            NativePositionOps.writeXPosResetSubpixel(body,body.getCentreX()+64);
            v.row(false,true);
            assertTrue(v.session.fractured(0));
            assertTrue(body.isHidden());
            assertEquals(1,v.session.recordings().size());
            v.fixture.sprite().setDead(true); v.row(false,false); v.finishRewind();
            assertFalse(v.session.fractured(0),"fracture is loop-local");
            v.row(false,true);
            body=GameServices.sprites().getSidekicks().getFirst();
            body.setDead(true); v.row(false,true);
            assertTrue(v.session.fractured(0),"native death fractures as well as drift");
        }
    }
    @Test public void historySurvivesWorldRestore() throws Exception {
        try (var v = new Visit()) {
            int startX=v.fixture.sprite().getCentreX(), startY=v.fixture.sprite().getCentreY();
            v.rows(20,true,false); v.commit();
            assertEquals(1,v.session.loopCount()); assertEquals(1,v.session.recordings().size());
            assertEquals(0,v.session.frame());
            assertEquals(startX,v.fixture.sprite().getCentreX()); assertEquals(startY,v.fixture.sprite().getCentreY());
            assertFalse(v.session.allowsDebugRewind());
            v.rows(20,false,true); v.commit();
            assertEquals(2,v.session.loopCount()); assertEquals(2,v.session.recordings().size());
            for (int i=0;i<3;i++) { v.rows(20,true,false); v.commit(); }
            assertEquals(5,v.session.recordings().size(),"all five physical echo slots are supported");
            v.rows(80,false,true);
            for (int i=0;i<5;i++) {
                assertFalse(v.session.fractured(i),"slot="+i);
                assertFalse(GameServices.sprites().getSidekicks().get(i).isHidden());
            }
        }
    }
}
