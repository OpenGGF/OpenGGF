package com.openggf.stageplay.route;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.route.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.StageplayCompetitionRouteHosts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Purpose: uncut engine video of a frozen S2 Competition route, one frame per committed row.
 * Inputs: supplied S2 REV01, -Ds2video.route (resource name) and -Ds2video.out (directory);
 * -Ds2video.audio=true also writes the engine's own mix as audio.pcm (s16le stereo, one
 * presented outer audio frame per row; rate in audio-rate.txt).
 * Origin: 2026-11-08 PR #270 parity video request. Explicit Debug selector only.
 */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class DebugSonic2CompetitionRouteVideo {
    @org.junit.jupiter.api.BeforeEach
    void retireAnnotationFixtureBeforeColdBoot() { com.openggf.tests.TestEnvironment.resetEngineOnly(); }

    private static int f(RouteSample s,String k){return s.facts().getOrDefault(k,-1);}
    @Test void renderEveryRow() throws Exception {
        String name=System.getProperty("s2video.route","ehz-tied-act-wins-special");
        Path out=Path.of(System.getProperty("s2video.out","target/s2video")).toAbsolutePath();
        Files.createDirectories(out);
        String routeFile=System.getProperty("s2video.routeFile");
        var route=RouteFiles.read(routeFile == null ? Path.of(Objects.requireNonNull(getClass().getResource(
                "/routes/s2/competition/"+name+".route.json")).toURI()) : Path.of(routeFile));
        var config=GameServices.configuration();
        boolean limit=config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,true);
        var ffmpeg=Boolean.getBoolean("s2video.clockOnly") ? null : new ProcessBuilder("ffmpeg","-y","-loglevel","error","-f","rawvideo","-pix_fmt","argb",
                "-s","320x448","-r","60","-i","-","-c:v","ffv1","-level","3",out.resolve("engine.mkv").toString())
                .redirectErrorStream(true).redirectOutput(out.resolve("ffmpeg.log").toFile()).start();
        try(var video=new BufferedOutputStream(ffmpeg == null ? OutputStream.nullOutputStream() : ffmpeg.getOutputStream(),1<<20);
            var csv=Files.newBufferedWriter(out.resolve("engine-frames.csv"))) {
            csv.write("row,mode,phase,lfc,p1x,p1y,p2x,p2y,p1rings,p2rings,p1total,p2total,p1time,p2time,vint,titleState,titleCard\n");
            var hosts=new StageplayCompetitionRouteHosts(Path.of(System.getProperty("sonic2.rom.path")));
            byte[] frame=new byte[320*448*4];
            var audio=Boolean.getBoolean("s2video.audio") ? GameServices.audio() : null;
            var pcmOut=audio == null ? null : new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(out.resolve("audio.pcm")),1<<20));
            short[][] pcm={null};
            var result=new RouteReplay(hosts).replay(route,Cancellation.NONE,(row,sample)->{
                try {
                    if (audio != null) {
                        if (pcm[0] == null) {
                            int rate=audio.outputSampleRate();
                            audio.beginCaptureMode(rate,audio.presentationFrameRate());
                            Files.writeString(out.resolve("audio-rate.txt"),rate+"\n");
                            pcm[0]=new short[(rate/50+2)*2];
                        }
                        com.openggf.tools.HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                        int frames=audio.drainCaptureFrame(pcm[0]);
                        for(int i=0;i<frames*2;i++) pcmOut.writeShort(Short.reverseBytes(pcm[0][i]));
                    }
                    if (ffmpeg != null) {
                    var image=hosts.renderAnyCurrent();
                    int[] px=image.pixels();
                    for(int i=0;i<px.length;i++){int v=px[i]|0xff000000;frame[i*4]=(byte)(v>>>24);frame[i*4+1]=(byte)(v>>>16);frame[i*4+2]=(byte)(v>>>8);frame[i*4+3]=(byte)v;}
                    video.write(frame);
                    }
                    csv.write(row+","+hosts.currentGameMode()+","+sample.facts().getOrDefault("competition.phase",-1)+","
                            +sample.clocks().levelFrameCounter()+","+sample.p1().centreX()+","+sample.p1().centreY()+","
                            +sample.p2().map(PlayerView::centreX).orElse(0)+","+sample.p2().map(PlayerView::centreY).orElse(0)
                            +","+f(sample,"competition.p1.rings")+","+f(sample,"competition.p2.rings")+","+f(sample,"competition.p1.totalRings")+","+f(sample,"competition.p2.totalRings")+","+f(sample,"competition.p1.time")+","+f(sample,"competition.p2.time")+","+sample.clocks().vIntRunCount()+","+f(sample,"title.state")+","+f(sample,"competition.titleCard")+"\n");
                } catch(IOException e) { throw new UncheckedIOException(e); }
            });
            if (audio != null) { pcmOut.close(); if (pcm[0] != null) audio.endCaptureMode(); }
            Files.writeString(out.resolve("result.txt"),result.toString());
            assertInstanceOf(ReplayResult.Passed.class,result,result.toString());
        } finally {
            config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,limit);
            if (ffmpeg != null) assertEquals(0,ffmpeg.waitFor());
        }
    }
}
