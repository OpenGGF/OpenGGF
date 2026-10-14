package secondstrike.stages;

import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.GumballItemObjectInstance;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.lwjgl.opengl.GL11.*;
import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.CELL_RED;

/**
 * Supplementary native stage playback for Second Strike, originating in the October 2026 prototype.
 * Inputs: blue|gumball, matching packaged mod (or stock for a bounded positive control), original S3K ROM, fresh isolated output/user.dir,
 * bounded outer-frame count, optional authored BK2/input log, optional PNG stride (default 2).
 * Uses production HeadlessGameBoot/GameLoop through GameplayCaptureSession, ordinary pad input,
 * and one declared native entry request. No position/reward/clock/HP/terrain seeds or debug completion.
 * State is written before images and offline PCM. This direct-entry cell does not certify campaign entry.
 * Run one cell per JVM with explicit surfaceless EGL, no display variables, and null host audio.
 */
public final class NativeStagePlayback {
    private static final int[][] FIRST_BOARD_GATES = {
            {2,8},{3,11},{2,20},{3,23},{28,8},{29,11},{28,20},{29,23},{10,3},{21,2},{10,28},{21,29}
    };
    private NativeStagePlayback() { }

    public static void main(String[] args) throws Exception {
        if (args.length<5 || args.length>7) throw new IllegalArgumentException(
                "blue|gumball packagedMod originalS3kRom freshOutput maxOuterFrames [inputLog|default] [pngStride]");
        String stage=args[0];
        if (!Set.of("blue","gumball").contains(stage)) throw new IllegalArgumentException("Unsupported cell: "+stage);
        Path mod="stock".equals(args[1])?null:Path.of(args[1]).toRealPath();
        Path rom=Path.of(args[2]).toRealPath(),out=Path.of(args[3]).toAbsolutePath().normalize();
        int frames=Integer.parseInt(args[4]),stride=args.length==7?Integer.parseInt(args[6]):2;
        if (frames<60 || frames>7200 || stride<1 || stride>6) throw new IllegalArgumentException("Unbounded capture request");
        if (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl"))
                || present("DISPLAY") || present("WAYLAND_DISPLAY"))
            throw new IllegalStateException("Explicit surfaceless EGL with DISPLAY/WAYLAND_DISPLAY unset is required");
        if (!"null".equals(System.getenv("ALSOFT_DRIVERS")))
            throw new IllegalStateException("Launch with ALSOFT_DRIVERS=null; capture must not use a physical audio device");
        if (!Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(out))
            throw new IllegalStateException("Fresh isolated user.dir must equal output directory");
        Files.createDirectories(out);
        try(var files=Files.list(out)) {
            if(files.anyMatch(path->!Set.of("config.yaml","runtime","saves","empty-roms").contains(path.getFileName().toString())))
                throw new IllegalArgumentException("Fresh capture output required; existing evidence is preserved");
        }
        Path input=out.resolve("input.bk2");
        if(args.length>=6 && !"default".equals(args[5])) Files.copy(Path.of(args[5]).toRealPath(),input);
        else {
            String script=stage.equals("blue")
                    ? "1 U; 30 -; 1 A; 24 -; 1 R; 23 -; 1 L; 1200 -"
                    : "2400 -"; // Native center spring -> 1UP collision -> depleted-floor exit, without gameplay seeds.
            Files.writeString(out.resolve("input.script"),script+"\n");
            InputLogAuthorTool.author(script,input,"s3k");
        }
        Bk2Movie movie=new Bk2MovieLoader().loadMovieOrInputLog(input);
        Files.createDirectories(out.resolve("frames"));
        Files.writeString(out.resolve("setup.txt"),"Direct native stage-entry request from intro-skipped AIZ1 boot; title-card presentation omitted through native owner retirement. Supplementary stage playback; campaign entry unproven.\n"
                +"mod="+(mod==null?"stock-positive-control":mod)+"\nmod_sha256="+(mod==null?"none":sha256(mod))+"\nrom="+rom+"\nrom_sha256="+sha256(rom)+"\n"
                +"stage="+stage+"\nmax_outer_frames="+frames+"\npng_stride="+stride+"\ninput_origin=interactive stage boundary\n");
        run(stage,mod,rom,out,frames,stride,movie);
    }

    private static boolean present(String variable) { String value=System.getenv(variable);return value!=null&&!value.isBlank(); }
    private static String sha256(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }

    private static void run(String stage,Path mod,Path rom,Path out,int frames,int stride,Bk2Movie movie) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null);
        int stageFrame=-1,returnedAt=-1,last=-1,maxGates=0,maxSprings=0,minSprings=Integer.MAX_VALUE;
        boolean requested=false,seenStage=false,seenResults=false,sawTurn=false,sawJump=false;
        boolean initializedBoardObserved=false;
        int initialEmeralds=0,initialLives=0,initialRings=0;
        try(var session=new GameplayCaptureSession(settings);
            var rows=new PrintWriter(Files.newBufferedWriter(out.resolve("state.csv")));
            var objects=new PrintWriter(Files.newBufferedWriter(out.resolve("stage-objects.csv")))) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.CONTROLLER_ENABLED,false);
            if(mod!=null)session.applyMod(mod);
            session.enableAudio();session.boot(rom,0,0,settings);
            initialEmeralds=GameServices.gameState().getEmeraldCount();initialLives=GameServices.gameState().getLives();
            initialRings=GameServices.camera().getFocusedSprite().getRingCount();
            rows.println("frame,phase,stage_frame,mode,input,input_mask,action_mask,x,y,xvel,yvel,air,hurt,dead,rings,lives,shield,emeralds,ss_stage,ss_x,ss_y,ss_angle,ss_turning,ss_jumping,ss_mapping_frame,ss_fade_timer,ss_started,ss_blues,ss_rings,ss_finished,ss_emerald,added_red_gates,spring_live,spring_solid,rep_live,bonus_complete,bonus_reward_rings,bonus_reward_lives,bonus_reward_shield,fade,pcm_stereo_frames");
            objects.println("frame,type,x,y,subtype,destroyed,solid_for_player");
            try(LiveCaptureAudioHandle audio=GameServices.audio().beginLiveCaptureAudio(60);
                OutputStream pcm=Files.newOutputStream(out.resolve("audio.pcm"))) {
                short[] samples=new short[audio.maxStereoFramesPerPacket()*2];
                for(int frame=0;frame<frames;frame++) {
                    GameMode before=session.loop().getCurrentGameMode();
                    if(!requested && levelReady(session)) {
                        requested=true;
                        if(stage.equals("blue")) GameServices.level().requestSpecialStageEntry(SpecialStageEntryRequest.ordinary());
                        else GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
                    }
                    boolean interactive=interactive(session,stage);
                    if(interactive && stageFrame<0)stageFrame=0;
                    Bk2FrameInput input=interactive && stageFrame<movie.getFrameCount() ? movie.getFrame(stageFrame) : null;
                    session.step(input);
                    session.loop().presentOuterFrame(false,false); // One normal outer SMPS boundary; null host audio backend.
                    GameMode mode=session.loop().getCurrentGameMode();
                    if(stage.equals("blue") ? mode==GameMode.SPECIAL_STAGE
                            : mode==GameMode.BONUS_STAGE && bonus()!=null && bonus().getActiveType()==BonusStageType.GUMBALL)seenStage=true;
                    if(mode==GameMode.SPECIAL_STAGE_RESULTS)seenResults=true;
                    var special=session.loop().getActiveSpecialStageProvider() instanceof Sonic3kSpecialStageProvider p?p:null;
                    var comparison=special==null?null:special.getManager().captureComparisonState();
                    int gates=0;
                    if(special!=null && mode==GameMode.SPECIAL_STAGE && special.isInitialized()) {
                        for(int[] gate:FIRST_BOARD_GATES)if(special.getManager().getGrid().getCell(gate[0],gate[1])==CELL_RED)gates++;
                        if(!initializedBoardObserved) {
                            initializedBoardObserved=true;
                            Files.writeString(out.resolve("first-board.txt"),"stage="+special.getManager().getCurrentStage()+"\nadded_red_gates="+gates+"\nblues="+comparison.spheresLeft()+"\n");
                            if(special.getManager().getCurrentStage()!=0||(mod!=null&&gates!=12))
                                throw new IllegalStateException("Expected the packaged first Chaos board with twelve red gates");
                        }
                    }
                    maxGates=Math.max(maxGates,gates);
                    sawTurn|=comparison!=null&&comparison.turning()!=0;sawJump|=comparison!=null&&comparison.jumping()!=0;
                    var bonus=bonus();var reward=bonus==null?BonusStageProvider.BonusStageRewards.none():bonus.getRewards();
                    var player=GameServices.camera().getFocusedSprite();
                    int springs=0,solidSprings=0,rep=0;
                    var manager=GameServices.level().getObjectManager();
                    if(manager!=null && mode==GameMode.BONUS_STAGE)for(var object:manager.getActiveObjects()) {
                        String type=object.getClass().getSimpleName();
                        boolean spring=type.equals("GumballSpringChild");
                        boolean solid=object instanceof SolidObjectProvider provider&&player!=null&&provider.isSolidFor(player);
                        if(spring&&!object.isDestroyed()){springs++;if(solid)solidSprings++;}
                        if(object instanceof GumballItemObjectInstance&&!object.isDestroyed()&&object.getSpawn().subtype()==1)rep++;
                        if(spring||object instanceof GumballItemObjectInstance)
                            objects.println(frame+","+type+","+object.getX()+","+object.getY()+","+object.getSpawn().subtype()+","+object.isDestroyed()+","+solid);
                    }
                    if(mode==GameMode.BONUS_STAGE&&interactive){maxSprings=Math.max(maxSprings,springs);minSprings=Math.min(minSprings,springs);}
                    String phase=!requested?"native-level-boot":!seenStage?"native-entry-request":
                            mode==GameMode.LEVEL?"native-return":interactive?"native-stage-pad":"native-stage-transition";
                    if(seenStage && mode==GameMode.LEVEL && levelReady(session)&&returnedAt<0)returnedAt=frame;
                    rows.println(csv(frame,phase,stageFrame,mode,input==null?"-":input.rawLine(),input==null?0:input.p1InputMask(),input==null?0:input.p1ActionMask(),
                            player==null?"":player.getCentreX()&0xffff,player==null?"":player.getCentreY()&0xffff,
                            player==null?"":player.getXSpeed(),player==null?"":player.getYSpeed(),player==null?"":player.getAir(),
                            player==null?"":player.isHurt(),player==null?"":player.getDead(),player==null?"":player.getRingCount(),GameServices.gameState().getLives(),
                            player==null?"":player.getShieldType(),GameServices.gameState().getEmeraldCount(),special==null?"":special.getManager().getCurrentStage(),
                            comparison==null?"":comparison.playerX(),comparison==null?"":comparison.playerY(),comparison==null?"":comparison.angle(),
                            comparison==null?"":comparison.turning(),comparison==null?"":comparison.jumping(),special==null?"":special.getManager().getPlayer().getMappingFrame(),
                            comparison==null?"":comparison.fadeTimer(),comparison==null?"":comparison.started(),
                            comparison==null?"":comparison.spheresLeft(),comparison==null?"":comparison.ringsCollected(),comparison==null?"":comparison.finished(),
                            comparison==null?"":comparison.emeraldCollected(),gates,springs,solidSprings,rep,bonus==null?"":bonus.isStageComplete(),
                            reward.rings(),reward.lives(),reward.shield()||reward.fireShield()||reward.lightningShield()||reward.bubbleShield(),
                            GameServices.graphics().getFadeManager().isActive(),audio.totalStereoFrames()));
                    rows.flush();objects.flush(); // Observation boundary precedes PNG/PCM output.
                    if(rows.checkError()||objects.checkError())throw new IllegalStateException("Observation write failed");
                    if(frame%stride==0 || mode!=before) {
                        ScreenshotCapture.savePNG(render(session),out.resolve("frames").resolve(String.format(Locale.ROOT,"%05d.png",frame)));
                    }
                    int count=audio.drainPresentationFrame(samples)*2;
                    for(int i=0;i<count;i++){pcm.write(samples[i]&255);pcm.write((samples[i]>>>8)&255);}
                    if(interactive)stageFrame++;
                    last=frame;
                    if(returnedAt>=0&&frame>=returnedAt+120)break;
                }
                Files.writeString(out.resolve("summary.txt"),"direct_entry_supplementary=true\nrequested="+requested+"\nseen_stage="+seenStage
                        +"\ncomplete="+(requested&&seenStage&&returnedAt>=0)
                        +"\nseen_results="+seenResults+"\nreturned_frame="+returnedAt+"\nlast_frame="+last+"\npad_stage_frames="+stageFrame
                        +"\nobserved_turn="+sawTurn+"\nobserved_jump="+sawJump+"\nmax_added_red_gates="+maxGates
                        +"\nmax_live_springs="+maxSprings+"\nmin_live_springs="+(minSprings==Integer.MAX_VALUE?-1:minSprings)
                        +"\ninitial_emeralds="+initialEmeralds+"\nfinal_emeralds="+GameServices.gameState().getEmeraldCount()
                        +"\ninitial_lives="+initialLives+"\nfinal_lives="+GameServices.gameState().getLives()
                        +"\ninitial_rings="+initialRings+"\nfinal_rings="+GameServices.camera().getFocusedSprite().getRingCount()
                        +"\npcm_stereo_frames="+audio.totalStereoFrames()+"\nsample_rate="+audio.sampleRate()+"\n");
            }
        }
        Path raw=out.resolve("audio.pcm");
        var summary=Files.readString(out.resolve("summary.txt"));
        int rate=Integer.parseInt(summary.lines().filter(line->line.startsWith("sample_rate=")).findFirst().orElseThrow().substring(12));
        try(var stream=new AudioInputStream(Files.newInputStream(raw),new AudioFormat(rate,16,2,true,false),Files.size(raw)/4)) {
            AudioSystem.write(stream,AudioFileFormat.Type.WAVE,out.resolve("audio.wav").toFile());
        }
        Files.delete(raw);
        System.out.println(summary);
        if (!summary.lines().anyMatch("complete=true"::equals))
            throw new IllegalStateException("Incomplete direct stage route: native request, intended stage and ready return are required; see summary.txt");
    }

    private static boolean levelReady(GameplayCaptureSession session) {
        var level=GameServices.level();var player=GameServices.camera().getFocusedSprite();
        return session.loop().getCurrentGameMode()==GameMode.LEVEL&&player!=null&&!player.getDead()&&!player.isControlLocked()
                &&!level.hasPendingInitialProcessSpritesPass()&&!level.isTitleCardRequested()&&!GameServices.graphics().getFadeManager().isActive();
    }
    private static BonusStageProvider bonus() {
        var gameplay=SessionManager.getCurrentGameplayMode();return gameplay==null?null:gameplay.getActiveBonusStageProvider();
    }
    private static boolean interactive(GameplayCaptureSession session,String stage) {
        if(GameServices.graphics().getFadeManager().isActive())return false;
        if(stage.equals("blue")) {
            var provider=session.loop().getActiveSpecialStageProvider();
            var owner=NativeStagePacingOwners.special(provider);
            return session.loop().getCurrentGameMode()==GameMode.SPECIAL_STAGE&&owner!=null&&owner.pacingState().interactive();
        }
        var provider=bonus();return session.loop().getCurrentGameMode()==GameMode.BONUS_STAGE&&provider!=null
                &&provider.getActiveType()==BonusStageType.GUMBALL&&!provider.isStageComplete()
                &&!GameServices.level().hasPendingInitialProcessSpritesPass()&&!GameServices.level().isTitleCardRequested();
    }
    private static RgbaImage render(GameplayCaptureSession session) {
        if(session.loop().getCurrentGameMode()!=GameMode.SPECIAL_STAGE)return session.render();
        var graphics=GameServices.graphics();graphics.runPendingRenderThreadTasks();
        var provider=session.loop().getActiveSpecialStageProvider();provider.setClearColor();glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        // Match Engine's native special-stage screen-space render owner; no gameplay state is changed.
        GameServices.camera().setX((short)0);GameServices.camera().setY((short)0);
        provider.setSpecialStageViewport(SpecialStageViewport.nativeViewport());provider.draw();graphics.flushScreenSpace();
        graphics.flush(); // Engine's outer draw boundary also drains deferred world/pattern commands.
        if(graphics.getUiRenderPipeline()!=null)graphics.getUiRenderPipeline().renderFadePass();glFinish();
        return ScreenshotCapture.captureFramebuffer(session.width(),GameplayCaptureSession.HEIGHT);
    }
    private static String csv(Object... values) {
        return String.join(",",java.util.Arrays.stream(values).map(value->"\""+String.valueOf(value).replace("\"","\"\"")+"\"").toList());
    }
}
