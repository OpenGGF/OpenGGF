package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Isolated, no-device live preview worker. One document then frame requests on stdin; JSONL on stdout. */
public final class StageplayPreviewTool {
    private StageplayPreviewTool() { }
    public static void main(String[] args) throws Exception {
        var output = System.out; System.setOut(System.err);
        var json = new ObjectMapper();
        var paths = new LinkedHashMap<String, Path>();
        for (int i=0;i<args.length;i+=2) {
            if (i+1==args.length || !List.of("s1","s2","s3k").contains(args[i])) throw new IllegalArgumentException("Expected game ROM pairs");
            paths.put(args[i],Path.of(args[i+1]));
        }
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String encoded = input.readLine();
            if (encoded == null || encoded.length() > StageplayStore.MAX_BYTES * 2) throw new IllegalArgumentException("Missing/big preview document");
            var document = StageplayStore.decode(Base64.getDecoder().decode(encoded));
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            try (var boot = new HeadlessGameBoot(document.width(),document.height(),document.width(),document.height(),320,224)) {
                boot.setBootToTitle(true);
                boot.boot(Objects.requireNonNull(paths.get(document.set().gameId()),"Supply the opening set ROM"),0,0);
                try (var library = SceneRomLibrary.open(paths);
                     var world = StageplayLiveWorld.adoptOffline(library,com.openggf.game.session.SessionManager.getCurrentGameplayMode());
                     var pixels = new GlReadPixelsGrabber(document.width(), document.height())) {
                    var audio=GameServices.audio();
                    int audioFrame=-1, rate=audio.outputSampleRate();
                    byte[] packet=new byte[0];
                    short[] pcm=new short[(rate/60+2)*2];
                    var audioWorld=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
                    String line;
                    try {
                    while ((line=input.readLine())!=null) {
                        try {
                            if (line.length()>1024) throw new IllegalArgumentException("Invalid preview request");
                            var request=json.readTree(line);
                            int frame=request.path("frame").intValue();
                            if (!request.path("frame").isInt()) throw new IllegalArgumentException("Preview frame must be an integer");
                            if (frame<0 || frame>=document.durationFrames()) throw new IllegalArgumentException("Frame outside scene");
                            var sample=world.resolve(document,StageplayTimeline.sample(document,frame));
                            var current=com.openggf.game.session.SessionManager.getCurrentGameplayMode();
                            if (current!=audioWorld) {
                                audio.endCaptureMode(); audio.beginCaptureMode(rate,60); audioWorld=current; audioFrame=-1;
                            }
                            if (audioFrame!=frame) {
                                // Authored music/voices stay with the editor; transport only native SFX from this no-device host.
                                audio.stopMusic(); HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                                int count=audio.drainCaptureFrame(pcm)*2;
                                var bytes=java.nio.ByteBuffer.allocate(count*2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                                bytes.asShortBuffer().put(pcm,0,count); packet=bytes.array(); audioFrame=frame;
                            }
                            var view = request.has("camera") ? json.treeToValue(request.get("camera"),StageplayTimeline.CameraPose.class) : sample.camera();
                            if (request.has("camera")) view = world.boundedCamera(view);
                            world.draw(sample,view);
                            if (!view.equals(sample.camera())) {
                                double shakeX=sample.camera().x()-sample.baseCamera().x(),shakeY=sample.camera().y()-sample.baseCamera().y();
                                sample=new StageplayTimeline.Frame(sample.frame(),sample.actors(),view,sample.captions(),sample.set(),sample.transition(),
                                        new StageplayTimeline.CameraPose(view.x()-shakeX,view.y()-shakeY,1),sample.dialogue(),sample.puppets(),sample.world());
                            }
                            output.println(json.writeValueAsString(Map.of("sample",sample,"rgba",Base64.getEncoder().encodeToString(pixels.grab()),"pcm",packet,"sampleRate",rate)));
                            output.flush();
                        } catch (Exception failure) {
                            output.println(json.writeValueAsString(Map.of("error",Objects.toString(failure.getMessage(),failure.getClass().getName())))); output.flush();
                        }
                    }
                    } finally { audio.endCaptureMode(); }
                }
            }
        }
    }
}
