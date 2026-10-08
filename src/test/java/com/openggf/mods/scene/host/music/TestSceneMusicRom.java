package com.openggf.mods.scene.host.music;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.SceneMusicPart;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSceneMusicRom {
    @Test
    void excessivePcmBudgetIsRejectedBeforeReadingTheRom() {
        var audio = mock(AudioManager.class);
        when(audio.outputSampleRate()).thenReturn(192_000);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        try (var music = new ManagedSceneMusic(audio, ignored -> { reads.incrementAndGet(); return null; })) {
            assertThrows(IllegalArgumentException.class, () -> music.prepareAsync("s1", 0x81, 36_000));
            assertThrows(IllegalArgumentException.class, () -> music.prepare("s1", 0x81, 36_000));
            assertEquals(0, reads.get());
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void asynchronousFullAndPartJobsPublishWithoutAudioAndCloseCancelsTheWorker()
            throws Exception {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(8_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> rom)) {
            var cancelled = music.prepareAsync("s1", 0x81, 120);
            cancelled.cancel();
            assertEquals(com.openggf.mods.scene.SceneMusicPreparation.State.CANCELLED, cancelled.state());
            assertThrows(IllegalStateException.class, cancelled::prepared);
            var full = music.prepareAsync("s1", 0x81, 120);
            assertNotSame(cancelled, full, "the exact cancelled request can be retried");
            await(full);
            assertEquals(100, full.progressPercent());
            var song = full.prepared();
            assertFalse(song.notes().isEmpty());
            assertSame(song, music.prepareAsync("s1", 0x81, 120).prepared());
            var parts = List.of(new SceneMusicPart(0, 1, 0, false), new SceneMusicPart(8_000, 8, 0, false));
            var cancelledPart = music.preparePartAsync(song, parts);
            cancelledPart.cancel();
            var part = music.preparePartAsync(song, parts);
            assertNotSame(cancelledPart, part, "the exact cancelled selection can be retried");
            await(part);
            assertSame(song, part.prepared());
            var masked = retainedField(song, "masked");
            assertNotNull(masked);
            var player = music.start(song, parts, 0);
            assertSame(masked, retainedField(player, "masked"), "start reuses the prepared part mix");
            var other = List.of(new SceneMusicPart(0, 8, 0, false));
            var abandonedPart = music.preparePartAsync(song, other);
            abandonedPart.cancel();
            var synchronous = music.start(song, other, 0);
            assertFalse(synchronous.finished(), "cancelled part work cannot block the synchronous start contract");
            var unfinished = music.prepareAsync("s1", 0x81, 36_000);
            music.close();
            assertEquals(com.openggf.mods.scene.SceneMusicPreparation.State.CANCELLED, unfinished.state());
            assertTrue(player.finished());
            assertRetired(song);
            var worker = (java.util.concurrent.ThreadPoolExecutor) retainedField(music, "worker");
            assertTrue(worker.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS), "scene close terminates synthesis");
            assertThrows(IllegalStateException.class, () -> music.prepareAsync("s1", 0x81, 120));
        } finally { audio.destroy(); }
    }


    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1PartCuesRetainRealInstrumentAudioAndPauseOwnership() throws Exception { partCues("s1",new Sonic1AudioProfile(),0x81); }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2PartCuesRetainRealInstrumentAudioAndPauseOwnership() throws Exception { partCues("s2",new Sonic2AudioProfile(),0x81); }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kPartCuesRetainRealInstrumentAudioAndPauseOwnership() throws Exception { partCues("s3k",new Sonic3kAudioProfile(),1); }
    private void partCues(String game,GameAudioProfile profile,int id) throws Exception {
        var config=SonicConfigurationService.createStandalone();config.setConfigValue(SonicConfiguration.FPS,60);config.setConfigValue(SonicConfiguration.REGION,"NTSC");
        var audio=AudioManager.createStandalonePresentation(game,profile,config,PerformanceProfiler.getInstance(),new NoDeviceAudioSink(8000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom=GameServices.rom().getRom();
        try(var music=new ManagedSceneMusic(audio,ignored->rom)) {
            var song=music.prepare(game,id,180);
            var selections=List.of(new SceneMusicPart(0,1,0,false),new SceneMusicPart(0,2,0,false),
                    new SceneMusicPart(0,0,1 << song.notes().stream().filter(n->n.kind()==com.openggf.mods.scene.SceneNoteEvent.Kind.PSG).findFirst().orElseThrow().channel(),false),new SceneMusicPart(0,0,0,true));
            for(int role=0;role<selections.size();role++) {
                var part=selections.get(role);
                var attack=song.notes().stream().filter(n->switch(n.kind()) {
                    case FM -> (part.fmMask()&(1<<n.channel()))!=0;
                    case PSG -> (part.psgMask()&(1<<n.channel()))!=0;
                    case DAC -> part.dacMuted();
                }).findFirst().orElseThrow();
                long source=Math.min(song.lengthSamples()-1,attack.onsetSamples()+Math.min(64,attack.durationSamples()/4));
                var control=music.start(song,List.of(part),0);control.setPartAudible(false);
                short[] baseline=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)control).render(baseline,800);control.stop();
                var player=music.start(song,List.of(part),0);player.setPartAudible(false);
                assertTrue(player.cuePart(source,480,1,.85,.6,0));
                short[] result=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)player).render(result,800);
                assertFalse(Arrays.equals(baseline,result),game+" role "+role+" must use sounding ROM residual");
                double energy=0;for(int i=0;i<result.length;i++){double d=result[i]-baseline[i];energy+=d*d;}
                double rms=Math.sqrt(energy/result.length);assertTrue(rms>1,game+" role "+role+" cue RMS "+rms);
                System.out.println("Part cue ROM evidence: "+game+" role="+role+" RMS="+rms);

                // Pausing in the middle of an accepted cue must freeze its envelope and source.
                player.stop();
                player=music.start(song,List.of(part),0);player.setPartAudible(false);
                assertTrue(player.cuePart(source,480,1,.85,.6,0));
                short[] resumed=new short[1600], opening=new short[240];
                ((com.openggf.audio.presentation.ScenePcmSource)player).render(opening,120);
                System.arraycopy(opening,0,resumed,0,opening.length);
                player.pause();assertFalse(player.cuePart(source,480,1,1,.5,0));
                short[] silence=new short[80];((com.openggf.audio.presentation.ScenePcmSource)player).render(silence,40);assertArrayEquals(new short[80],silence);
                assertEquals(120,player.samplePosition());
                player.resume();
                short[] tail=new short[1360];((com.openggf.audio.presentation.ScenePcmSource)player).render(tail,680);
                System.arraycopy(tail,0,resumed,opening.length,tail.length);
                assertArrayEquals(result,resumed,"pause cannot advance the song, fade or cue pitch cursor");
                assertEquals(800,player.samplePosition());
                player.stop();assertFalse(player.cuePart(source,480,1,1,.5,0));
                var stopped=player;
                assertThrows(IllegalArgumentException.class,()->stopped.cuePart(-1,480,1,1,.5,0));

                var head=music.start(song,List.of(part),80);head.setPartAudible(false);
                assertFalse(head.cuePart(SceneMusicPlayer.PLAYHEAD,480,1,.85,.6,0),"lead-in has no playing note to choke");
                short[] prefix=new short[1760];((com.openggf.audio.presentation.ScenePcmSource)head).render(prefix,880);
                assertTrue(head.cuePart(SceneMusicPlayer.PLAYHEAD,480,1,.85,.6,0));
                short[] fromHead=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)head).render(fromHead,800);head.stop();
                var exact=music.start(song,List.of(part),80);exact.setPartAudible(false);
                ((com.openggf.audio.presentation.ScenePcmSource)exact).render(prefix,880);
                assertTrue(exact.cuePart(800,480,1,.85,.6,0));
                short[] fromSample=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)exact).render(fromSample,800);
                assertArrayEquals(fromSample,fromHead,"PLAYHEAD resolves the next buffer in song coordinates, excluding lead-in");
                exact.cuePart(800,480,1,.85,.6,0);exact.stop();
                var retry=music.start(song,List.of(part),0);retry.setPartAudible(false);
                short[] clean=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)retry).render(clean,800);
                assertArrayEquals(baseline,clean,"stopped cue cannot escape into a replacement player");retry.stop();

                var ending=music.start(song,List.of(part),0);ending.setPartAudible(false);
                int beforeEnd=(int)song.lengthSamples()-40;
                ((com.openggf.audio.presentation.ScenePcmSource)ending).render(new short[beforeEnd*2],beforeEnd);
                assertTrue(ending.cuePart(source,480,1,.85,.6,0));
                short[] end=new short[1600];((com.openggf.audio.presentation.ScenePcmSource)ending).render(end,800);
                for(int i=39;i<800;i++) {
                    assertEquals(0,end[i*2],"cue must finish inside the finite song taper");
                    assertEquals(0,end[i*2+1],"right cue channel must also finish inside the taper");
                }
                assertTrue(ending.finished());ending.stop();
                writeCueEvidence(game,role,baseline,result);
            }
        } finally {audio.destroy();}
    }
    private void writeCueEvidence(String game,int role,short[] backing,short[] withCue) throws Exception {
        String requested=System.getProperty("openggf.sitar.fumble.capture.dir");if(requested==null)return;
        var directory=java.nio.file.Path.of(requested).toAbsolutePath().normalize();
        if(directory.startsWith(java.nio.file.Path.of("").toAbsolutePath().normalize()))throw new IllegalArgumentException("durable audio evidence must be outside repo");
        java.nio.file.Files.createDirectories(directory);
        short[] demonstration=new short[backing.length+withCue.length];System.arraycopy(backing,0,demonstration,0,backing.length);System.arraycopy(withCue,0,demonstration,backing.length,withCue.length);
        byte[] bytes=new byte[demonstration.length*2];for(int i=0;i<demonstration.length;i++){bytes[i*2]=(byte)demonstration[i];bytes[i*2+1]=(byte)(demonstration[i]>>>8);}
        var format=new javax.sound.sampled.AudioFormat(8000,16,2,true,false);
        try(var stream=new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(bytes),format,demonstration.length/2)) {
            javax.sound.sampled.AudioSystem.write(stream,javax.sound.sampled.AudioFileFormat.Type.WAVE,directory.resolve(game+"-role-"+role+"-backing-then-fumble.wav").toFile());
        }
    }

    private static void await(com.openggf.mods.scene.SceneMusicPreparation job) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (job.state() == com.openggf.mods.scene.SceneMusicPreparation.State.PREPARING && System.nanoTime() < deadline) {
            assertTrue(job.progressPercent() >= 0 && job.progressPercent() <= 100);
            Thread.sleep(5);
        }
        assertEquals(com.openggf.mods.scene.SceneMusicPreparation.State.READY, job.state(), job.error());
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void fullSongsCanExtendBeyondNinetySecondsAndRemainFinite() throws IOException {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(8_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var rom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> rom)) {
            var song = music.prepare("s1", 0x81, 7_200);
            assertEquals(960_000, song.lengthSamples());
            assertTrue(song.notes().stream().anyMatch(note -> note.onsetSamples() > 900_000),
                    "the second minute must contain actual ROM events");
            assertThrows(IllegalArgumentException.class, () -> music.prepare("s1", 0x81, 36_001));
        } finally { audio.destroy(); }
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void failedSpeakerFreezesAudibleTimeAndCannotTurnThePerformanceIntoVirtualPlayback() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        var device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int i = 0; i < 10; i++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(150_000_000L);
            assertEquals(2_400, player.samplePosition());
            audio.replaceFailedPresentationSink(new IllegalStateException("lost speaker"));
            assertTrue(player.paused());
            assertEquals(1, player.underrunCount());
            for (int i = 0; i < 20; i++) audio.presentFrame(PresentationMode.FORWARD);
            now.set(500_000_000L);
            assertEquals(2_400, player.samplePosition(), "produced-ahead packets were never heard");
            assertEquals(2_400, player.samplePositionAt(now.get()));
            player.resume();
            assertTrue(player.paused(), "failed output cannot resume the stale song cursor");
            var retry = music.start(song, 1, 0, false, 0);
            assertTrue(retry.paused(), "retry remains interrupted until a sink is restored");
            assertEquals(0, retry.samplePosition());
            assertEquals(1, retry.underrunCount());
        } finally { audio.destroy(); }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void historicalInputUsesSurroundingAudibleObservationsAndNeverRunsBeyondTheFiniteSong() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        OpenAlPcmSink.Device device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int index = 0; index < 10; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(150_000_000);
            assertEquals(2_400, player.samplePosition());
            now.set(166_000_000);
            assertEquals(2_400, player.samplePosition());
            consumed.set(2_496); now.set(168_000_000);
            assertEquals(2_496, player.samplePosition());
            assertEquals(2_400, player.samplePositionAt(155_000_000),
                    "a late input inside the observed stall must not borrow samples played afterward");
            assertEquals(2_400, player.samplePositionAt(165_000_000));
            assertEquals(2_448, player.samplePositionAt(167_000_000),
                    "a running interval retains sample-rate timestamp precision");
            assertEquals(2_496, player.samplePositionAt(170_000_000),
                    "a future timestamp must not invent an unconsumed sample");
            for (int index = 10; index < 160; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(128_000); now.set(3_000_000_000L);
            assertEquals(song.lengthSamples(), player.samplePosition());
            assertEquals(song.lengthSamples(), player.samplePositionAt(now.get()),
                    "post-song silence must not extend the timestamp coordinate beyond finite EOF");
            assertEquals(song.lengthSamples(), player.samplePositionAt(now.get() + 1_000_000));

            player = music.start(song, 1, 0, false, 800);
            assertEquals(-800, player.samplePositionAt(now.get()));
            for (int index = 0; index < 2; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(480); now.set(3_010_000_000L);
            assertEquals(-320, player.samplePosition());
            consumed.set(960); now.set(3_020_000_000L);
            assertEquals(160, player.samplePosition());
            assertEquals(-80, player.samplePositionAt(3_015_000_000L),
                    "historical mapping retains signed positions across the lead-in boundary");
        } finally { audio.destroy(); }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void replacingOrClosingPreparationReleasesRomPcmWhileOpaqueMetadataRemainsUsable()
            throws IOException, ReflectiveOperationException {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom)) {
            var first = music.prepare("s1", 0x81, 120);
            var firstNotes = first.notes();
            var stopped = music.start(first, 1, 0, false, 0);
            assertNotNull(retainedField(stopped, "masked"));
            assertSame(first, music.prepare("s1", 0x81, 120), "cached retries keep the current preparation");
            assertFalse(stopped.finished());
            var playing = music.start(first, 8, 0, false, 0);
            assertTrue(stopped.finished());
            assertNull(retainedField(stopped, "masked"), "a stopped handle cannot retain its previous arrangement");
            var second = music.prepare("s1", 0x81, 121);
            assertTrue(playing.finished(), "replacing preparation stops its active performance");
            assertNull(retainedField(playing, "masked"));
            assertRetired(first);
            assertEquals(48_000, first.sampleRate());
            assertEquals(96_000, first.lengthSamples());
            assertSame(firstNotes, first.notes(), "retired note metadata remains useful for chart inspection");
            assertThrows(IllegalArgumentException.class, () -> music.start(first, 1, 0, false, 0));
            var last = music.start(second, 1, 0, false, 0);
            music.close();
            assertTrue(last.finished());
            assertNull(retainedField(last, "masked"));
            assertRetired(second);
            assertEquals(96_800, second.lengthSamples());
            assertFalse(second.notes().isEmpty());
        } finally { audio.destroy(); }
    }

    private static void assertRetired(Object prepared) throws ReflectiveOperationException {
        for (String field : List.of("full", "masked", "profile", "data", "dac", "parts"))
            assertNull(retainedField(prepared, field), "retired preparation retains " + field);
    }

    private static Object retainedField(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void timestampsResumeFromThePausedAudibleSampleInsteadOfExtrapolatingThroughThePause() throws IOException {
        AtomicLong now = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        AtomicLong starvation = new AtomicLong();
        OpenAlPcmSink.Device device = mock(OpenAlPcmSink.Device.class);
        when(device.initialize()).thenReturn(48_000);
        when(device.consumedStereoFrames()).thenAnswer(ignored -> consumed.get());
        when(device.underrunCount()).thenAnswer(ignored -> starvation.get());
        var sink = new OpenAlPcmSink(device, failure -> fail(failure), now::get, ignored -> { });
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation("s1", new Sonic1AudioProfile(), config,
                PerformanceProfiler.getInstance(), sink,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = new ManagedSceneMusic(audio, ignored -> actualRom, now::get)) {
            var song = music.prepare("s1", 0x81, 120);
            var player = music.start(song, 1, 0, false, 0);
            for (int index = 0; index < 10; index++) audio.presentFrame(PresentationMode.FORWARD);
            consumed.set(2_400); now.set(50_000_000);
            assertEquals(2_400, player.samplePosition());
            player.pause(); now.set(1_050_000_000);
            assertEquals(2_400, player.samplePosition());
            player.resume(); now.set(1_066_000_000); consumed.set(3_168);
            assertEquals(2_448, player.samplePositionAt(1_051_000_000),
                    "a tap 1ms after resume belongs 48 samples beyond the paused position");
            audio.pause();
            assertTrue(player.paused(), "focus pause must freeze the scene source too");
            now.set(2_066_000_000); audio.resume();
            assertFalse(player.paused());
            now.set(2_082_000_000); consumed.set(3_936);
            assertEquals(3_216, player.samplePositionAt(2_067_000_000),
                    "focus resume must restart the input timestamp anchor");
            for (int event = 0; event < 100; event++) {
                now.addAndGet(1_000);
                assertEquals(3_216, player.samplePositionAt(2_067_000_000),
                        "a large input batch must retain its prior audible-clock anchor");
            }
            player.pause(); audio.pause(); audio.resume();
            assertTrue(player.paused(), "regaining focus must preserve a manual performance pause");
            player.resume();
            assertFalse(player.paused());
            assertEquals(0, player.underrunCount());
            starvation.set(1);
            assertEquals(1, player.underrunCount(),
                    "speaker starvation must be observed even with unconsumed software packet remainder");
            assertEquals(1, player.underrunCount(), "the same stopped transition is counted only once");
        } finally { audio.destroy(); }
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillFiniteCaptureAndRestart() throws IOException { verify("s1", 0x81, new Sonic1AudioProfile()); }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void chemicalPlantFiniteCaptureAndRestart() throws IOException { verify("s2", 0x8C, new Sonic2AudioProfile()); }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void angelIslandFiniteCaptureAndRestart() throws IOException { verify("s3k", 0x01, new Sonic3kAudioProfile()); }

    private void verify(String game, int id, GameAudioProfile profile) throws IOException {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        var audio = AudioManager.createStandalonePresentation(game, profile, config,
                PerformanceProfiler.getInstance(), new NoDeviceAudioSink(48_000),
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        var actualRom = GameServices.rom().getRom();
        try (var music = SceneMusicFactory.create(audio, ignored -> actualRom);
             var capture = audio.beginLiveCaptureAudio(60)) {
            var song = music.prepare(game, id, 120);
            assertEquals(96_000, song.lengthSamples());
            assertSame(song, music.prepare(game, id, 120));
            assertFalse(song.notes().isEmpty());
            assertTrue(song.notes().stream().allMatch(note -> note.onsetSamples() + note.durationSamples() <= song.lengthSamples()));
            assertThrows(UnsupportedOperationException.class, () -> song.notes().clear());
            int fm = game.equals("s2") ? 0 : 1;
            int psg = game.equals("s2") ? 4 : 0;
            SceneMusicPlayer player = music.start(song, fm, psg, false, 800);
            assertEquals(-800, player.samplePosition());
            short[] packet = new short[1600];
            audio.presentFrame(PresentationMode.FORWARD);
            capture.drainPresentationFrame(packet);
            assertArrayEquals(new short[1600], packet);
            assertEquals(0, player.samplePosition());
            short[] first = new short[48_000 * 2];
            for (int frame = 0; frame < 60; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
                System.arraycopy(packet, 0, first, frame * 1600, 1600);
            }
            assertTrue(acVariance(first) > 1, "captured song must vary, not merely contain DC");
            player.pause();
            long paused = player.samplePosition();
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(paused, player.samplePosition());
            player.resume();
            player = music.start(song, fm, psg, false, 0);
            short[] restarted = new short[first.length];
            for (int frame = 0; frame < 60; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                capture.drainPresentationFrame(packet);
                System.arraycopy(packet, 0, restarted, frame * 1600, 1600);
            }
            assertArrayEquals(first, restarted);
            assertEquals(48_000, player.samplePosition());
            player.setPartAudible(false);
            short[] missedBacking = captureFrames(audio, capture, 60);
            assertTrue(acVariance(missedBacking) > 1, "backing must continue across musical rests when the performer misses");
            assertEquals(0, missedBacking[missedBacking.length - 2]);
            assertEquals(0, missedBacking[missedBacking.length - 1], "finite PCM must end at zero after its 5ms taper");
            assertTrue(player.finished());
            player.stop();
            if (game.equals("s2")) {
                player = music.start(song, fm, psg, false, 0);
                player.setPartAudible(false);
                short[] missed = new short[first.length];
                for (int frame = 0; frame < 60; frame++) {
                    audio.presentFrame(PresentationMode.FORWARD);
                    capture.drainPresentationFrame(packet);
                    System.arraycopy(packet, 0, missed, frame * 1600, 1600);
                }
                assertFalse(Arrays.equals(first, missed), "PSG3 hi-hat must mute its physical noise output");
                assertTrue(acVariance(missed) > 1, "FM and DAC backing must continue after a Synth miss");
            }
            if (game.equals("s1")) {
                player = music.start(song, 1, 0, false, 0);
                player.setPartAudible(false);
                short[] fixedPart = captureFrames(audio, capture, 120);
                player = music.start(song, List.of(new SceneMusicPart(0, 1, 0, false),
                        new SceneMusicPart(48_000, 8, 0, false)), 0);
                player.setPartAudible(false);
                short[] changingPart = captureFrames(audio, capture, 120);
                assertArrayEquals(Arrays.copyOf(fixedPart, 96_000), Arrays.copyOf(changingPart, 96_000),
                        "the opening must use the same selected FM part before its section boundary");
                assertFalse(Arrays.equals(Arrays.copyOfRange(fixedPart, 96_000, fixedPart.length),
                        Arrays.copyOfRange(changingPart, 96_000, changingPart.length)),
                        "muting follows the new musical source after the section boundary");
                assertThrows(IllegalArgumentException.class, () -> music.start(song,
                        List.of(new SceneMusicPart(1, 1, 0, false)), 0));
                assertThrows(IllegalArgumentException.class, () -> music.start(song,
                        List.of(new SceneMusicPart(0, 1, 0, false), new SceneMusicPart(0, 8, 0, false)), 0));
            }
        } finally { audio.destroy(); }
    }

    private static short[] captureFrames(AudioManager audio, com.openggf.audio.LiveCaptureAudioHandle capture, int frames) {
        short[] result = new short[frames * 1600];
        short[] packet = new short[1600];
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            capture.drainPresentationFrame(packet);
            System.arraycopy(packet, 0, result, frame * 1600, 1600);
        }
        return result;
    }

    private static double acVariance(short[] samples) {
        double mean = Arrays.stream(toInts(samples)).average().orElse(0);
        double sum = 0;
        for (short sample : samples) sum += (sample - mean) * (sample - mean);
        return sum / samples.length;
    }
    private static int[] toInts(short[] values) {
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }
}
