package com.openggf.game.sonic2.competition.view;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.graphics.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Purpose: comparison-only first-frontier diagnostic for native two-controller movies.
 * Inputs: -Ds2comp.refs=/absolute/native-refs (optional; clean skip when absent).
 * Origin: lane F, 2026-11-07; production host/anchor from A2-S C2 capture.
 * CSV values never enter gameplay. Raw clocks are reported separately because the
 * headless boot does not replay the cold frontend prefix. No subpixels are recorded.
 */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class DebugSonic2CompetitionDivergence {
    record Route(String movie, String csv, int zone, int anchor) { }
    static java.util.stream.Stream<Route> routes() {
        return java.util.stream.Stream.of(
            new Route("ehz-finish","ehz-finish-cold",0,1329),
            new Route("ehz-zone","ehz-zone-first",0,1329),
            new Route("ehz-flow","ehz-flow-first",0,1329),
            new Route("ehz","ehz-first",0,1329),
            new Route("ehz-route","ehz-route-first",0,1329),
            new Route("ehz-flowers","ehz-flowers-cold",0,1329),
            new Route("ehz-monitor","ehz-monitor-first",0,1329),
            new Route("ehz-monitor-high","ehz-monitor-high-first",0,1329),
            new Route("cnz-flow","cnz-flow-first",2,1337),
            new Route("mcz-route","mcz-route-first",1,1324),
            new Route("cnz-route","cnz-route-first",2,1337)).filter(r -> System.getProperty("s2comp.route",r.movie()).equals(r.movie()));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routes")
    void compare(Route route) throws Exception {
        Path refs=Path.of(System.getProperty("s2comp.refs","/tmp/s2comp-analysis-20261107/native-refs"));
        Path csv=refs.resolve(route.csv()+"/state.csv"), bk2=refs.resolve(route.movie()+".bk2");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(csv)&&Files.isRegularFile(bk2),"external native references absent");
        var nativeRows=readRows(csv);var movie=new Bk2MovieLoader().load(bk2);
        // ehz-zone extends the identical finish movie through physical14651.
        // Use its independently observed cold prefix only after proving every
        // controller row in the shared prefix is identical; never synthesize state.
        if(route.movie().equals("ehz-zone")) {
            var prefixMovie=new Bk2MovieLoader().load(refs.resolve("ehz-finish.bk2"));
            for(int i=0;i<Math.min(prefixMovie.getFrameCount(),movie.getFrameCount());i++)
                assertEquals(prefixMovie.getFrame(i),movie.getFrame(i),"continuation input prefix row "+i);
            var prefixRows=readRows(refs.resolve("ehz-finish-cold/state.csv"));
            int start=nativeRows.firstKey();prefixRows.forEach((frame,row)->{if(frame<start)nativeRows.put(frame,row);});
        }
        int nativeEnd=Math.min(nativeRows.lastKey(),movie.getFrameCount());
        var nativeLag=com.openggf.tests.NativeReplayLagBoundary.first(nativeRows,route.anchor()+1,nativeEnd,"lfc",
                (before,after)->before.get("mode")==12 && after.get("mode")==12
                        && before.get("two_player")==1 && after.get("two_player")==1
                        && before.get("zoneact").equals(after.get("zoneact")));
        int comparisonEnd=nativeLag==null?nativeEnd:nativeLag.frame()-1;
        Path out=Path.of(System.getProperty("s2comp.out","target/s2comp-divergence"));Files.createDirectories(out);
        var report=new StringBuilder("route="+route+"\nCSV="+csv+" movie="+bk2+"\n");
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var services=EngineServices.current();var root=SessionManager.getCurrentWorldSession().rootGameModule();
        var config=GameServices.configuration();boolean limits=config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,true);
        int compared=0,lastCompared=-1;String first=null, firstClock=null;int remaining=-1;
        var window=new ArrayDeque<String>();
        var lifetimes=new StringBuilder("frame,slot,layout,x,y\n");
        var flow=new StringBuilder("frame,loserTimeLeft\n");
        try(var gl=PresentationOffscreenTestContext.openConfigured(320,224,PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            AutoCloseable cleanup=()->{try{SessionManager.closeGameplaySession();}finally{services.graphics().cleanup();}};
            var views=new Sonic2CompetitionViews(services.graphics())) {
            var graphics=services.graphics();graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0,0,320,224);glViewport(0,0,320,224);
            var host=Sonic2NativeCompetitionHost.production(root,services,ignored->{},views,false,true,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess());
            host.clearMenuCameraRam();
            var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical,()->false);
            var session=new Sonic2CompetitionSession();
            if(route.zone()!=0)session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(route.zone()==1?8:2,route.zone()==1?8:2,0,0,false,false),PlayerInputState.neutral()),true);
            session.updateMenu(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()),true);
            host.loadAct(session,session.romZone(),0);
            if(Boolean.getBoolean("s2comp.instrument")) services.debugOverlay().setEnabled(com.openggf.debug.DebugOverlayToggle.TOUCH_RESPONSE,true);
            // Lead U-2 ruling: stop BEFORE the unsupported native lag input.
            // No loop selection, skipping, timing input or re-alignment occurs.
            for(int physical=route.anchor()+1;physical<=comparisonEnd;physical++) {
                var context=host.context();input.setLogicalOverride(at(movie,physical));
                LevelFrameStep.updateTimers(LevelFrameContext.from(context));
                context.plcFrameLifecycle().runLogicalIteration(context::updateFade,frame->{
                    var result=new LevelFrameResult[1];
                    NativeCompetitionIterationAccess.runAdmitted(frame,()->result[0]=host.step(input,frame));return result[0];
                });
                if (Boolean.getBoolean("s2comp.lifetimes"))
                    for (var object : context.getObjectManager().getActiveObjects())
                        if (object instanceof com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance body)
                            lifetimes.append(physical).append(',').append(body.getSlotIndex()).append(',')
                                .append(body.getSpawn().layoutIndex()).append(',').append(body.getX()).append(',')
                                .append(body.getY()).append('\n');
                if (Boolean.getBoolean("s2comp.lifetimes")) flow.append(physical).append(',').append(session.loserTimeLeft()).append('\n');
                var expected=nativeRows.get(physical);if(expected==null)continue;
                if(expected.get("mode")!=12 || expected.get("two_player")!=1) {
                    report.append("native left ordinary gameplay at ").append(physical).append("; suffix requires production frontend transition\n");break;
                }
                var actual=sample(host.context(),session);compared++;lastCompared=physical;
                String diff=difference(actual,expected,new HashSet<>(Arrays.asList(System.getProperty("s2comp.exclude","vint").split(","))));
                String clock=difference(actual,expected,Set.of("p1_x","p1_y","p2_x","p2_y","p1_vx","p1_vy","p2_vx","p2_vy","p1_camx","p1_camy","p2_camx","p2_camy","p1_rings","p2_rings","p1_lives","p2_lives","p1_score","p2_score","lfc"));
                if(firstClock==null&&!clock.isEmpty())firstClock="frame="+physical+" "+clock;
                String row="frame="+physical+" actual="+actual+" native="+expected+"\n";
                if(Boolean.getBoolean("s2comp.instrument")) {
                    var manager=context.getObjectManager();var debug=manager.getTouchResponseDebugState();
                    row+="touch actor="+debug.getPlayerX()+","+debug.getPlayerY()+" hits="+debug.getHits()+"\n";
                    for(var object:manager.getActiveObjects()) {
                        if(java.util.stream.IntStream.range(0,2).anyMatch(i -> Math.abs(object.getX()-session.player(i).getCentreX())<128
                                && Math.abs(object.getY()-session.player(i).getCentreY())<128))
                            row+="nearby="+object.getClass().getSimpleName()+" spawn="+object.getSpawn()+" x="+object.getX()+" y="+object.getY()+" details="+object.traceDebugDetails()+"\n";
                    }
                }
                window.addLast(row);if(first==null&&window.size()>7)window.removeFirst();
                if(first==null&&!diff.isEmpty()){first="frame="+physical+" "+diff;remaining=3;}
                else if(remaining>=0&&--remaining==0)break;
            }
            String result=first==null?"exact through "+lastCompared:first;
            if(first==null && nativeLag!=null && lastCompared==comparisonEnd)
                result+="; native lag at "+nativeLag.frame()+" (unsupported: BuildSprites_2P overrun)";
            report.append("native lag boundary=").append(nativeLag==null?"none through "+nativeEnd:nativeLag).append("\n");
            report.append("compared=").append(compared).append(" first=").append(result)
                .append("\nfirst raw clock mismatch=").append(firstClock).append("\n");
            window.forEach(report::append);
        } finally {config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,limits);TestEnvironment.resetAll();Files.writeString(out.resolve(route.movie()+".txt"),report);
            if(Boolean.getBoolean("s2comp.lifetimes")) { Files.writeString(out.resolve(route.movie()+"-lifetimes.csv"),lifetimes);
                Files.writeString(out.resolve(route.movie()+"-flow.csv"),flow); }}
        String result=first==null?"exact through "+lastCompared:first;
        if(first==null && nativeLag!=null && lastCompared==comparisonEnd)
            result+="; native lag at "+nativeLag.frame()+" (unsupported: BuildSprites_2P overrun)";
        System.out.println("FRONTIER "+route.movie()+" compared="+compared+" "+result+" rawClock="+firstClock);
    }
    static TreeMap<Integer,Map<String,Long>> readRows(Path csv) throws Exception {
        var lines=Files.readAllLines(csv);String[] keys=lines.getFirst().split(",");var rows=new TreeMap<Integer,Map<String,Long>>();
        for(String line:lines.subList(1,lines.size())){String[] cells=line.split(",");if(cells.length!=keys.length)throw new IllegalArgumentException("Malformed row: "+line);
            var row=new LinkedHashMap<String,Long>();for(int i=0;i<keys.length;i++)row.put(keys[i],Long.parseLong(cells[i]));
            if(rows.put(Math.toIntExact(row.get("frame")),row)!=null)throw new IllegalArgumentException("duplicate frame");}
        return rows;
    }
    static String difference(Map<String,Long> actual,Map<String,Long> expected,Set<String> excluded){
        var differences=new ArrayList<String>();actual.forEach((field,value)->{if(!excluded.contains(field)&&expected.containsKey(field)&&!value.equals(expected.get(field)))
            differences.add(field+" engine="+value+" native="+expected.get(field));});return String.join("; ",differences);
    }
    static Map<String,Long> sample(GameplayModeContext context,Sonic2CompetitionSession session){
        var result=new LinkedHashMap<String,Long>();
        for(int i=0;i<2;i++){String p="p"+(i+1)+"_";var player=session.player(i);var camera=session.camera(i);
            result.put(p+"x",(long)(player.getCentreX()&0xffff));result.put(p+"y",(long)(player.getCentreY()&0xffff));
            result.put(p+"vx",(long)player.getXSpeed());result.put(p+"vy",(long)player.getYSpeed());
            result.put(p+"camx",(long)(camera.getX()&0xffff));result.put(p+"camy",(long)(camera.getY()&0xffff));
            result.put(p+"rings",(long)session.levelState(i).getRings());result.put(p+"lives",(long)session.gameState(i).getLives());
            result.put(p+"score",(long)session.gameState(i).getScore()/10);}
        result.put("lfc",(long)(context.getLevelManager().getFrameCounter()&0xffff));
        result.put("vint",(long)context.getObjectManager().getVblaCounter());return result;
    }
    private static LogicalInputSnapshot at(Bk2Movie movie,int physical){
        int index=physical-1;return RecordedInputSnapshots.fromBk2(movie.getFrame(index),index==0?null:movie.getFrame(index-1));
    }
}
