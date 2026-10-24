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
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** ROM-backed production physics, objects, controlled frame and complete course checkpoint checks. */
@RequiresRom(SonicGame.SONIC_2)
public class RoomSolutionsTest {
    public static java.util.function.BiFunction<GgfMod, Path, GameModule> moduleFactory;
    static final class Visit implements AutoCloseable {
        SharedLevel base;
        HeadlessTestFixture fixture;
        RoomSession session;
        final Set<String> defeated=new HashSet<>();
        final boolean[] launch=new boolean[6], launching=new boolean[6];
        final int[] launchCount=new int[6];
        Visit(String id) throws Exception {
            var room=RoomDefinition.load(id);
            base = SharedLevel.load(SonicGame.SONIC_2, room.zone(), room.act());
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "sonic,sonic,sonic,sonic,sonic");
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.WIDE_16_9.name());
            config.resolveDisplayAspect();
            Path jar = Path.of(EchoesMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            GameModule effective = moduleFactory.apply(new EchoesMod(), jar);
            SessionManager.clear(); GameModuleRegistry.setCurrent(effective); TestEnvironment.activeGameplayMode();
            effective.getGameService(RoomSession.class).select(room);
            fixture = HeadlessTestFixture.builder().withZoneAndAct(room.zone(), room.act()).build();
            session = GameServices.module().getGameService(RoomSession.class);
            GameServices.level().getObjectManager().setRewindClassResolver(new RewindClassResolver() {
                @Override public Optional<Class<?>> resolve(String owner, String name) {
                    try { return Optional.of(Class.forName(name, true, RoomSolutionsTest.class.getClassLoader())); }
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
        void row(boolean right, boolean left) { tick(right?"R":left?"L":"-"); }
        void tick(String b) {
            var objects=List.copyOf(GameServices.level().getObjectManager().getActiveObjects());
            fixture.stepFrame(b.contains("U"),b.contains("D"),b.contains("L"),b.contains("R"),b.contains("J"));
            for(var o:objects) if(o instanceof AbstractBadnikInstance && o.isDestroyed()) defeated.add(o.getClass().getSimpleName());
            var bodies=new ArrayList<com.openggf.sprites.playable.AbstractPlayableSprite>();
            bodies.add(fixture.sprite()); bodies.addAll(GameServices.sprites().getSidekicks());
            for(int i=0;i<bodies.size();i++) {
                boolean now=bodies.get(i).getYSpeed() < -2000
                        && bodies.get(i).getAnimationId()==com.openggf.game.sonic2.constants.Sonic2AnimationIds.SPRING.id();
                if(now) launch[i]=true;
                if(now&&!launching[i]) launchCount[i]++;
                launching[i]=now;
            }
        }
        void rows(int count, boolean right, boolean left) { for (int i=0;i<count;i++) row(right,left); }
        void commit() { session.command(RoomSession.Command.ECHO); row(false,false); for(int i=0;i<24;i++) row(false,false); }
        void solution(int loop) { steps(session.room().solution().get(loop)); }
        void steps(List<RoomDefinition.Step> steps) {
            for (var step : steps) {
                for (int i=0;i<step.frames();i++) tick(step.buttons());
            }
        }
        @Override public void close() { if (base != null) base.dispose(); }
    }
    public void verify(String id) throws Exception {
        try(var v=new Visit(id)) {
            String probe=System.getProperty("echoes.probe");
            if(probe!=null) {
                for(String loop:probe.split(";")) {
                    for(String segment:loop.split(",")) {
                        var a=segment.split(":"); int count=Integer.parseInt(a[0]); String b=a[1];
                        for(int i=0;i<count;i++) {
                            v.tick(b);
                            if(i%10==0 || i==count-1) report(v);
                        }
                    }
                    if(!v.session.cleared()) v.commit();
                }
                return;
            }
            var room=v.session.room();
            assertEquals(3,room.objects().stream().filter(o->o.kind().equals("shard")).count(),id+" shard count");
            assertTrue(room.loopFrames()>=480&&room.loopFrames()<=1200,id+" 8–20 second clock");
            assertTrue(room.echoCap()>=room.par()&&room.echoCap()<=5,id+" cap");
            assertFalse(room.hint().isBlank(),id+" hint");
            v.rows(Math.min(400,v.session.room().loopFrames()-1),true,false);
            assertFalse(v.session.cleared(),id+" solo straight run");
            v.session.command(RoomSession.Command.RESET); v.row(false,false); v.rows(24,false,false);
            v.steps(room.solution().getLast());
            assertFalse(v.session.cleared(),id+" final route with zero echoes");
            v.session.command(RoomSession.Command.RESET); v.row(false,false); v.rows(24,false,false);
            if(id.equals("3-1")) {
                v.solution(0); v.commit();
                for(var loop:v.session.room().naiveSolution()) v.steps(loop);
                assertTrue(v.session.fractured(0),"opening the recorded obstacle early must fracture the runner");
                v.session.command(RoomSession.Command.RESET); v.row(false,false); v.rows(24,false,false);
            }
            for(int loop=0;loop<v.session.room().solution().size();loop++) {
                if(loop==v.session.room().solution().size()-1) {
                    v.defeated.clear(); Arrays.fill(v.launch,false); Arrays.fill(v.launching,false); Arrays.fill(v.launchCount,0);
                }
                v.solution(loop);
                if(loop<v.session.room().solution().size()-1) {
                    assertFalse(v.session.cleared(),id+" cleared prematurely loop "+loop);
                    v.commit();
                }
            }
            report(v);
            assertTrue(v.session.cleared(),id+" reference solution");
            assertTrue(v.session.lastClearEchoes()<=v.session.room().par(),id+" par");
            for(int i=0;i<v.session.recordings().size();i++) assertFalse(v.session.fractured(i),id+" safe solution fractured slot "+i);
            assertTrue(v.session.lastClearShards()<3,id+" the shard star must need an optional detour");
            if(id.startsWith("2-")) assertTrue(v.launch[0],id+" native seesaw launch for live Sonic");
            if(id.equals("2-2")) assertTrue(v.launchCount[0]>=2,"both chained native seesaws launch the live body");
            if(id.equals("3-2")) assertTrue(v.launch[2],"the second echo must hit the native spring");
            if(id.equals("1-4")) assertTrue(v.defeated.contains("BuzzerBadnikInstance"),"echo must defeat the native Buzzer");
            if(id.equals("3-4")) assertTrue(v.defeated.contains("SpinyBadnikInstance"),"echo must defeat the native Spiny");
        }
        verifyShardRoute(id);
        if(id.equals("3-3")) {
            try(var v=new Visit(id)) {
                for(int i=0;i<3;i++) { v.solution(i); v.commit(); }
                v.steps(List.of(new RoomDefinition.Step(1,"U"),new RoomDefinition.Step(110,"-"),new RoomDefinition.Step(200,"R")));
                assertFalse(v.session.cleared(),"ten frames late misses Clockwork's latch window");
            }
        }
    }
    private void verifyShardRoute(String id) throws Exception {
        try(var v=new Visit(id)) {
            var loops=v.session.room().shardSolution();
            assertFalse(loops.isEmpty(),id+" has a separate star route");
            for(int i=0;i<loops.size();i++) {
                v.steps(loops.get(i));
                if(i<loops.size()-1) {
                    assertFalse(v.session.cleared(),id+" premature shard clear "+i);
                    v.commit();
                }
            }
            report(v);
            assertTrue(v.session.cleared(),id+" shard route clears");
            assertEquals(3,v.session.lastClearShards(),id+" three shards in one final loop");
            assertTrue(v.session.lastClearEchoes()<=v.session.room().echoCap(),id+" star route cap");
            for(int i=0;i<v.session.recordings().size();i++) assertFalse(v.session.fractured(i),id+" star route fractured slot "+i);
        }
    }
    static void report(Visit v) {
        var p=v.fixture.sprite();
        var bodies=GameServices.sprites().getSidekicks();
        String echo="";
        for(var o:GameServices.level().getObjectManager().getActiveObjects())
            if(o.getClass().getSimpleName().contains("BuzzerBadnik") || o.getClass().getSimpleName().contains("Spiny")) echo+=" "+o.getClass().getSimpleName()+"="+o.getX()+","+o.getY()+",dead="+o.isDestroyed();
        for(int i=0;i<v.session.recordings().size();i++) {
            var b=bodies.get(i); echo+=" e"+i+"="+b.getCentreX()+","+b.getCentreY()+","+b.getYSpeed()+",roll="+b.getRolling()+",fract="+v.session.fractured(i);
        }
        echo+=" channels="+v.session.channel("cyan")+","+v.session.channel("magenta")+","+v.session.channel("gold");
        System.out.println("ROOM "+v.session.room().id()+" f="+v.session.frame()+" p="+p.getCentreX()+","+p.getCentreY()+" v="+p.getXSpeed()+","+p.getYSpeed()+" g="+p.getGSpeed()+" air="+p.getAir()+" roll="+p.getRolling()+" clear="+v.session.cleared()+" shards="+v.session.lastClearShards()+" defeated="+v.defeated+echo);
    }
}
