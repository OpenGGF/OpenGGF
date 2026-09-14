package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Production-frame FBZ Act1 boundary fixture. Origin: 2026-09-14 FBZ completion.
 * Inputs: ROM, new external output directory, reviewed manifest. No native RAM
 * or trace rows are read. Every rendered frame includes state and GPU textures.
 */
public final class FbzBoundaryFixtureCaptureTool {
    private static final ObjectMapper JSON = new ObjectMapper();
    private FbzBoundaryFixtureCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("ROM OUTPUT MANIFEST [CHECKPOINT] required");
        String checkpoint = args.length == 4 ? args[3] : "fbz1-boundary-6-outdoor";
        Path rom = Path.of(args[0]), output = Path.of(args[1]);
        String romSha = java.util.HexFormat.of().withUpperCase().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(rom)));
        if (!romSha.equals("CFBF98C36C776677290A872547AC47C53D2761D6"))
            throw new IllegalArgumentException("Verified locked-on ROM required");
        Files.createDirectory(output);
        var manifest = FbzVisualManifest.load(Path.of(args[2]), FbzVisualCaptureTool.REVIEWED_MANIFEST_SHA256);
        var plan = new FbzVisualScenarioDriver(manifest).plan(checkpoint);
        if (!plan.strategy().equals("act1-bidirectional-boundary") && !plan.strategy().equals("act2-bidirectional-boundary"))
            throw new IllegalArgumentException("Only reviewed boundary recipes supported");
        var recipe = manifest.recipe(checkpoint);
        int normalStage = recipe.act()==1 ? 0 : 4;
        String axis = checkpoint.equals("fbz1-boundary-4-horizontal") ? "player_x" : "player_y";
        int reverseCoordinate = axis.equals("player_x") ? recipe.centreX() : recipe.centreY();
        int forwardCoordinate = -1;
        for (var node : JSON.readTree(Path.of(args[2]).toFile()).path("checkpoints"))
            if (node.path("id").asText().equals(checkpoint))
                forwardCoordinate = node.path("player").path(axis.equals("player_x") ? "x" : "y").asInt(-1);
        if (forwardCoordinate < 0) throw new IllegalArgumentException("Missing approved crossing coordinate");
        var port = new FbzGameServicesFixturePort();
        try (var session = new HiddenGlCaptureSession(FbzVisualCaptureMode.resolve("native-320",320,224,0))) {
            session.boot(rom, recipe.act()-1);
            for (int i = 0; i < 240; i++) {
                session.stepFrames(1);
                Map<String,Object> state = session.captureState().values();
                if (Boolean.TRUE.equals(state.get("title_card_complete"))
                        && Boolean.FALSE.equals(state.get("fade_active"))) break;
                if (i == 239) throw new IllegalStateException("Production title/fade did not clear");
            }
            // The independently reviewed native first-visible saved state begins
            // at LFC35. Select that same production clock through idle frames;
            // never assign it or import native gameplay state.
            int entryFrame=((Number)session.captureState().values().get("level_frame_counter")).intValue();
            if(entryFrame>35)throw new IllegalStateException("Missed reviewed first-visible LFC35");
            while(((Number)session.captureState().values().get("level_frame_counter")).intValue()<35)
                session.stepFrames(1);
            // Match the native declared-position camera prelude. The ordinary screen
            // event owns the FF->indoor layout copy (loc_527DA); setting its
            // flag to false first would falsely retain initial outdoor chunks.
            new FbzVisualFixture(port).applyVerified(new FbzVisualFixture.Mutation(Map.of(),
                    Map.of("player_x",recipe.centreX(),"player_y",recipe.centreY())));
            for (int i=1;i<=480;i++) {
                session.stepFrames(1);
                var cameraState = session.captureState().values();
                if (i>=80 && ((Number)cameraState.get("camera_x")).intValue()==recipe.centreX()-160
                        && ((Number)cameraState.get("camera_y")).intValue()==recipe.centreY()-96) break;
                if (i==480) {
                    sample(session,output,"camera-prerequisite",0);
                    throw new IllegalStateException("Ordinary tracker did not centre within480 gameplay frames");
                }
            }
            sample(session,output,"camera-prerequisite",0);
            var prerequisite = session.captureState().values();
            if (((Number)prerequisite.get("player_x")).intValue() != recipe.centreX()
                    || ((Number)prerequisite.get("player_y")).intValue() != recipe.centreY())
                throw new IllegalStateException("Player moved during ordinary camera setup; evidence retained");
            new FbzVisualFixture(port).applyVerified(plan.fixtureMutation());
            sample(session,output,"setup",0);
            boolean forward = cross(session,port,output,"forward",axis,forwardCoordinate,normalStage);
            session.stepFrames(1);sample(session,output,"forward-after",1);
            new FbzVisualFixture(port).applyVerified(new FbzVisualFixture.Mutation(
                    Map.of(),Map.of("events_routine_bg",normalStage,"foreground_outdoor",true,"background_outdoor",true)));
            boolean reverse = cross(session,port,output,"reverse",axis,reverseCoordinate,normalStage);
            session.stepFrames(1);sample(session,output,"after",1);
            JSON.writeValue(output.resolve("verdict.json").toFile(),Map.of(
                    "status","unaccepted-pending-native-comparison","checkpoint",checkpoint,"forward_redraw_complete",forward,
                    "reverse_redraw_complete",reverse,"rom_sha1",romSha,
                    "manifest_sha256",FbzVisualCaptureTool.REVIEWED_MANIFEST_SHA256));
        }
    }

    private static boolean cross(HiddenGlCaptureSession session,FbzGameServicesFixturePort port,
                                 Path output,String phase,String axis,int coordinate,int normalStage) throws Exception {
        port.write(axis,coordinate);
        boolean entered=false;
        for (int i=1;i<=40;i++) {
            session.stepFrames(1);sample(session,output,phase,i);
            int stage=((Number)session.captureState().values().get("events_routine_bg")).intValue();
            if(stage!=normalStage)entered=true;
            if(entered && stage==normalStage)return true;
        }
        return false;
    }

    private static void sample(HiddenGlCaptureSession session,Path output,String phase,int index) throws Exception {
        String stem=phase+"-"+String.format("%02d",index);
        var images=session.renderAndCapture();
        ScreenshotCapture.savePNG(images.nativeCrop(),output.resolve(stem+".png"));
        Map<String,Object> state=new LinkedHashMap<>(session.captureState().values());
        var player=GameServices.camera().getFocusedSprite();
        state.put("player_object_controlled",player.isObjectControlled());
        state.put("player_anim",player.getAnimationId());
        state.put("player_mapping_frame",player.getMappingFrame());
        state.put("player_anim_index",player.getAnimationFrameIndex());
        state.put("player_anim_timer",player.getAnimationTick());
        state.put("player_dead",player.getDead());
        state.put("player_air",player.getAir());
        state.put("sidekicks",GameServices.sprites().getSidekicks().stream().map(s -> Map.of(
                "x",s.getCentreX() & 0xFFFF,"y",s.getCentreY() & 0xFFFF,
                "object_controlled",s.isObjectControlled(),"dead",s.getDead())).toList());
        Files.write(output.resolve(stem+"-background-ring.rgba"),
                GameServices.level().captureBackgroundVdpPlane());
        state.put("gpu_sampling_state",session.captureGpuSamplingState());
        JSON.writeValue(output.resolve(stem+".json").toFile(),state);
        for(var entry:session.captureGpuTextures().entrySet())
            Files.write(output.resolve(stem+"-"+entry.getKey()),entry.getValue());
    }
}
