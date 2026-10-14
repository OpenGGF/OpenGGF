package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.ending.EndingKnucklesFlightObjectInstance;
import com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual two-fight controller prefix, then native ending owners through title. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKnucklesEndingLifecycleCapture {
    @ParameterizedTest
    @CsvSource({"320,0000000,-2","320,1111111,0","320,3333333,2",
            "800,0000000,-2","800,1111111,0","800,3333333,2"})
    void finalFightDepartsThroughBothCreditPartsAndTheActualTerminalWithPhaseReplay(
            int width,String emeralds,int progress) throws Exception {
        runEnding(width, emeralds, progress, false);
    }

    @ParameterizedTest
    @CsvSource({"0000000,-2", "1111111,0", "3333333,2"})
    void palFinalFightDepartsThroughBothCreditPartsAndTheActualTerminalWithPhaseReplay(
            String emeralds, int progress) throws Exception {
        runEnding(320, emeralds, progress, true);
    }

    private void runEnding(int width, String emeralds, int progress, boolean pal) throws Exception {
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of("src/test/resources/routes/s3k/ssz2-final-defeat.bk2"));
        assertEquals(8704,movie.getFrameCount());
        var settings=new GameplayCaptureSession.Settings(width,"knuckles","","off",null,null,null,
                emeralds,false,false,410766,null);
        var checked=new HashSet<String>();boolean creditsComplete=false,postSeen=false,start=false;
        String phase="fight";int flightFrame=-1;
        try(var session=new GameplayCaptureSession(settings)){
            GameServices.configuration().setSessionOverride(
                    com.openggf.configuration.SonicConfiguration.REGION, pal ? "PAL" : "NTSC");
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),10,1,settings);
            assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
            assertEquals(410766,GameServices.level().getObjectManager().getVblaCounter());
            // GameLoop admits input0 through the native production load owner.
            // The older HeadlessTestRunner fixture primes that pad externally;
            // skipping its production input0 loses the boundary and dies before
            // the first fight. Preserve the declared VInt and native setup pass.
            for(int frame=0;frame<60_000;frame++){
                var pad=frame<movie.getFrameCount()?movie.getFrame(frame):input(frame,start);
                session.step(pad);
                if(frame==0){
                    assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass());
                    assertEquals(410766,GameServices.level().getObjectManager().getVblaCounter(),
                            "native initial Process_Sprites owns no VInt of its own");
                    assertEquals(0x80,session.player().getCentreX());
                    assertEquals(0x6AE,session.player().getCentreY(),"native arrival slot published P1");
                }
                // Native Render_Sprites flags are owned by the post-camera pass;
                // draw every ending pass and bounded checkpoints of the incoming fight.
                if(frame>=8700||frame%32==0)session.renderFrame();
                if(session.loop().getCurrentGameMode()==GameMode.TITLE_SCREEN){
                    assertTrue(creditsComplete,"both native credits parts");assertTrue(postSeen,"actual terminal owner");
                    assertTrue(flightFrame>=8703,"the actual final-fight handoff allocated the flight");
                    var required=new HashSet<>(Set.of("final-defeat","flight-jump","plane-turn","small-plane",
                            "credits-one","credits-two","banner"));
                    if(progress<0)required.add("mecha-charge");
                    if(progress>0)required.add("s3-eyecatch");
                    assertEquals(required,checked);
                    assertEquals(progress!=0,start,"native Chaos-only Knuckles leaves directly after the banner");
                    System.out.println("S3K Knuckles width="+width+" progress="+progress+" title="+frame+
                            " flight="+flightFrame+" replay="+checked);return;
                }
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
                assertFalse(session.player().getDead(),"SSZ2 route died at input"+frame);
                assertFalse(session.loop().isPaused(),"native ending owns Start");
                var manager=GameServices.level().getObjectManager();
                var bosses=manager.activeObjectsOfType(SszMechaSonicObjectInstance.class);
                if(frame==3294){assertEquals(1,bosses.size());assertEquals(0,bosses.getFirst().getCollisionProperty(),
                        "matched maintained first-defeat prefix");}
                if(frame==4016){assertEquals(1,bosses.size());assertEquals(8,bosses.getFirst().getCollisionProperty(),
                        "matched maintained Super-entry prefix");assertEquals(2,bosses.getFirst().routineForTest());}
                if(frame==8156){assertEquals(1,bosses.size());assertEquals(0,bosses.getFirst().getCollisionProperty(),
                        "matched maintained final-defeat prefix");}
                var state=(SszZoneRuntimeState)GameServices.zoneRuntimeState();
                if (pal && state.ending().credits().active()) {
                    assertEquals((progress < 0 ? 0xF2 : 0xE2) << 8, state.ending().credits().speed(),
                            "production beginCredits selects the Knuckles PAL half");
                }
                var flight=manager.activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).stream().findFirst().orElse(null);
                var post=manager.activeObjectsOfType(S3kPostCreditsObjectInstance.class).stream().findFirst().orElse(null);
                if(flight!=null){if(flightFrame<0)flightFrame=frame;assertTrue(bosses.isEmpty(),"native Mecha deleted at admission");
                    if(flight.routine()!=0)assertEquals(progress,state.ending().progress());
                    else assertFalse(state.ending().initialized(),"allocation precedes the native progress writer");
                    phase="flight="+flight.routine();}
                if(state.ending().initialized())assertEquals(progress,state.ending().progress());
                creditsComplete|=state.ending().credits().complete();postSeen|=post!=null;
                String spot=frame==8156?"final-defeat":null;
                if(flight!=null){if(flight.routine()==4)spot="flight-jump";
                    if(flight.routine()==10)spot="plane-turn";if(flight.routine()==12)spot="small-plane";}
                var credits=state.ending().credits();
                if(credits.routine()==4&&credits.rowIndex()>0)spot=credits.partIndex()==0?"credits-one":"credits-two";
                if(post!=null){phase="post="+post.phase();if(post.phase()==1)spot="banner";
                    if(post.phase()==7&&!manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.ending.EndingMechaChargeObjectInstance.class).isEmpty())spot="mecha-charge";
                    if(post.phase()==6)spot="s3-eyecatch";
                    if(post.phase()>=4&&creditsComplete)start=true;}
                if(spot!=null&&checked.add(spot))replay(session,movie,frame,pad,spot);
                if(frame%5000==0)System.out.println("S3K Knuckles "+width+"/"+progress+" input="+frame+" "+phase+
                        " fg="+state.foregroundRoutine()+" credits="+credits.routine()+"/"+credits.partIndex()+"/"+credits.rowIndex());
            }
            fail("No Knuckles terminal/title: "+width+"/"+progress+" "+phase+" replay="+checked);
        }
    }
    private static Bk2FrameInput input(int frame,boolean start){return new Bk2FrameInput(frame,0,0,start,0,0,false,start?"Start":"neutral");}
    private static void replay(GameplayCaptureSession session,com.openggf.debug.playback.Bk2Movie movie,
                               int frame,Bk2FrameInput pad,String spot){
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();session.renderFrame();var saved=registry.capture();
        for(int n=1;n<=30;n++){session.step(frame+n<movie.getFrameCount()?movie.getFrame(frame+n):input(frame+n,false));session.renderFrame();}
        var expected=registry.capture();registry.restore(saved);same(saved,registry.capture(),spot+" restore");session.restoreInputHistory(pad);
        for(int n=1;n<=30;n++){session.step(frame+n<movie.getFrameCount()?movie.getFrame(frame+n):input(frame+n,false));session.renderFrame();}
        same(expected,registry.capture(),spot+" replay");registry.restore(saved);session.restoreInputHistory(pad);
    }
    private static void same(CompositeSnapshot a,CompositeSnapshot b,String label){
        assertEquals(a.entries().keySet(),b.entries().keySet(),label);
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),label+" "+key);
    }
}
