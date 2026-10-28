package echoes;

import com.openggf.mods.ModStorage;
import com.openggf.game.TitleScreenProvider;
import com.openggf.control.InputHandler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Save safety, catalogue rules, native launch selection and frozen-world presentation. */
public class PresentationTest {
    static final class Memory implements ModStorage {
        final Map<String,String> data=new HashMap<>();
        boolean fail; int writes;
        public Optional<String> read(String name) { return Optional.ofNullable(data.get(name)); }
        public boolean write(String name,String text) { writes++;if(fail)return false;data.put(name,text);return true; }
        public Optional<byte[]> readBytes(String name) { return read(name).map(s->s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        public boolean writeBytes(String name,byte[] bytes) { return write(name,new String(bytes,java.nio.charset.StandardCharsets.UTF_8)); }
        public boolean delete(String name) { return data.remove(name)!=null; }
        public List<String> list() { return data.keySet().stream().sorted().toList(); }
    }
    static RoomDefinition room(String id) {
        var r=RoomFixtures.load("1-1");
        return new RoomDefinition(id,r.name(),r.zone(),r.act(),r.left(),r.right(),r.top(),r.bottom(),
                r.spawnX(),r.spawnY(),r.loopFrames(),r.par(),r.echoCap(),r.objects(),r.solution(),r.hint(),r.shardSolution(),r.naiveSolution());
    }
    @Test public void saveRoundTripAndRetry() {
        var storage=new Memory();var progress=new EchoProgress(storage).load();
        storage.fail=true; assertTrue(progress.record(room("1-1"),1,420,3));
        assertEquals(1,storage.writes); storage.fail=false;
        for(int i=0;i<60;i++) progress.tick();
        assertEquals(2,storage.writes);
        var loaded=new EchoProgress(storage).load();
        assertEquals(new EchoProgress.Best(1,420,7),loaded.best("1-1"));
        loaded.record(room("1-1"),2,300,0);
        assertEquals(new EchoProgress.Best(1,300,7),new EchoProgress(storage).load().best("1-1"));
    }
    @Test public void corruptSaveIsProtected() {
        var storage=new Memory(); var good=new EchoProgress(storage).load();good.record(room("1-1"),1,120,3);
        String generation=storage.data.get("progress.txt");
        // Even a partially valid generation must not leak partially parsed records.
        String bad=generation+"2-1=broken\n";storage.data.put("progress.txt",bad);
        var fallback=new EchoProgress(storage).load(); assertEquals(0,fallback.totalStars());
        int writes=storage.writes;fallback.record(room("1-1"),0,10,3);
        for(int i=0;i<180;i++) fallback.tick();
        assertEquals(writes,storage.writes);assertEquals(bad,storage.data.get("progress.txt"));
        storage.data.put("progress.txt",generation);
        assertEquals(7,new EchoProgress(storage).load().best("1-1").stars());
    }
    @Test public void unlockRules() {
        var p=new EchoProgress(new Memory()).load();
        assertTrue(p.unlocked("1-1"));assertFalse(p.unlocked("1-2"));assertFalse(p.unlocked("2-1"));
        p.record(room("1-1"),1,100,3);assertTrue(p.unlocked("1-2"));assertTrue(p.unlocked("2-1"));assertFalse(p.unlocked("3-1"));
        p.record(room("1-2"),1,100,3);p.record(room("1-3"),1,100,3);assertTrue(p.unlocked("3-1"));
        p.record(room("3-3"),1,100,3);assertFalse(p.unlocked("3-4"));
        for(String id:List.of("1-4","2-1","2-2","2-3")) p.record(room(id),1,100,3);
        assertEquals(24,p.totalStars());assertTrue(p.unlocked("3-4"));
    }
    static final class Title implements TitleScreenProvider {
        public void initialize() { } public void update(InputHandler i) { } public void draw() { }
        public void setClearColor() { } public void reset() { } public State getState() { return State.ACTIVE; }
        public boolean isExiting() { return false; } public boolean isActive() { return true; }
    }
    @Test public void catalogueNavigationAndLaunch() {
        var p=new EchoProgress(new Memory()).load();var s=new RoomSession(p,RoomFixtures.catalogue());var hub=new EchoHub(new Title(),s,new RoomLauncher(s));
        var input=new InputHandler();
        for(int i=0;i<100;i++) hub.update(input);
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,0,0,true,true),null));
        hub.update(input);assertTrue(hub.hubOpen());assertFalse(hub.isExiting(),"first Start opens hub");
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,2,2,false,false),null));
        hub.update(input);assertFalse(hub.hubOpen(),"B backs out, despite stock C-back policy");
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0,0,4,4,false,false),null));
        hub.update(input);assertTrue(hub.hubOpen());assertFalse(hub.isExiting());
        hub.update(input);assertTrue(hub.isExiting(),"C accepts instead of cancelling");
        hub=new EchoHub(new Title(),s,new RoomLauncher(s));
        assertEquals("1-1",hub.selectedId());hub.navigate(1,0);assertEquals("1-2",hub.selectedId());assertFalse(hub.launchSelected());
        hub.navigate(-1,0);assertTrue(hub.launchSelected());assertEquals("1-1",s.room().id());
        for(int world=1;world<=3;world++) for(int n=1;n<=4;n++) p.record(room(world+"-"+n),1,100,3);
        hub=new EchoHub(new Title(),s,new RoomLauncher(s));
        hub.navigate(1,0);hub.navigate(1,0);assertEquals("2-1",hub.selectedId());
        for(int world=2;world<=3;world++) {
            String id=world+"-1";
            if(hub.available(id)) {
                assertTrue(hub.launchSelected());var expected=RoomFixtures.load(id);
                assertEquals(id,s.room().id());assertEquals(expected.zone(),hub.startZoneIndex());assertEquals(expected.act(),hub.startActIndex());
            } else assertFalse(hub.launchSelected(),"missing resource is coming soon");
            hub.navigate(1,0);hub.navigate(1,0);
        }
    }
    @Test public void idleClockHintAndExit() throws Exception {
        try(var v=new FoundationTest.Visit()) {
            v.rows(30,false,false);
            assertEquals(0,v.session.frame());assertEquals(v.session.room().loopFrames(),v.session.remainingFrames());
            v.session.command(RoomSession.Command.HINT);v.row(false,false);assertTrue(v.session.hintVisible());
            v.session.command(RoomSession.Command.HINT);v.row(false,false);assertFalse(v.session.hintVisible());
            assertEquals(0,v.session.frame(),"thinking and hints consume no loop time");
            v.session.command(RoomSession.Command.EXIT);v.row(false,false);
            assertTrue(v.session.consumeTitleRequest());
            var hub=new EchoHub(new Title(),v.session,new RoomLauncher(v.session));hub.initialize();
            assertTrue(hub.hubOpen());assertEquals("1-2",hub.selectedId());
        }
    }
    @Test public void rewindHoldsTwelveFramesThenRestores() throws Exception {
        try(var v=new FoundationTest.Visit()) {
            int start=v.fixture.sprite().getCentreX();v.rows(40,true,false);
            int end=v.fixture.sprite().getCentreX(),frame=v.session.frame();
            v.session.command(RoomSession.Command.ECHO);v.row(false,false);
            assertEquals(12,v.session.rewindHold());assertEquals(1,v.session.recordings().size());
            for(int i=0;i<11;i++) {
                v.row(false,false);assertEquals(end,v.fixture.sprite().getCentreX());assertEquals(frame,v.session.frame());
            }
            v.row(false,false);assertEquals(0,v.session.rewindHold());assertEquals(0,v.session.frame());assertEquals(start,v.fixture.sprite().getCentreX());
            assertTrue(v.session.loopLabel()>0);
            v.rows(40,false,true);assertFalse(v.session.fractured(0));
        }
    }
    @Test public void clearRecordsStarsAndReturnsToNextCard() throws Exception {
        try(var v=new FoundationTest.Visit()) {
            v.solution(0);v.commit();v.solution(1);assertTrue(v.session.cleared());
            var best=v.session.progress().best("1-1");assertNotNull(best);assertEquals(3,best.stars()&3);assertEquals(v.session.clearStars(),best.stars());
            int x=v.fixture.sprite().getCentreX();v.rows(75,true,false);assertEquals(x,v.fixture.sprite().getCentreX());assertTrue(v.session.clearAge()>=72);
            var actions=new RoomActions(v.session);var confirm=new InputHandler();
            actions.handleInput(confirm); // New input context consumes held edges.
            confirm.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                    com.openggf.control.PlayerInputState.of(0,0,0,0,true,true),null));
            assertTrue(actions.handleInput(confirm),"results must own Start instead of pausing");v.row(false,false);
            var hub=new EchoHub(new Title(),v.session,new RoomLauncher(v.session));
            hub.initialize();assertTrue(hub.hubOpen());assertEquals("1-2",hub.selectedId());
        }
    }
}
