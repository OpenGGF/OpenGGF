package com.openggf.stageplay.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.RomIdentity;
import com.openggf.game.session.EngineServices;
import com.openggf.mods.scene.SceneImage;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Process boundary for editor preview: the active editor/gameplay session never leaves its host. */
final class StageplayOfflinePreview implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private Process process;
    private StageplayProcessInput output;
    private BufferedWriter input;
    private StageplayDocument document;
    private Set<String> workerGames = Set.of();
    private StageplayTimeline.Frame sample;
    private SceneImage image;
    private com.openggf.audio.StreamedMusicPort.SfxPcm sound;
    private List<StageplayObjectInspection> objectInspection = List.of();
    private StageplayTimeline.CameraPose requestedCamera;
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();
    private final StageplayPreviewAssets.Source assets;

    StageplayOfflinePreview(StageplayPreviewAssets.Source assets) { this.assets = Objects.requireNonNull(assets); }

    StageplayTimeline.Frame resolve(StageplayDocument value, StageplayTimeline.Frame authoredFrame) {
        int frame = authoredFrame.frame();
        var camera = authoredFrame.world() == null ? null : authoredFrame.camera();
        try {
            if (!value.equals(document)) start(value);
            // Drawing an exact resolved response must not reinterpret its clipped camera
            // as a new raw draft. Otherwise clipping can change the unshaken origin twice.
            if (sample == authoredFrame || sample != null && sample.frame()==frame && Objects.equals(camera,requestedCamera)) return sample;
            var requestValue = JSON.createObjectNode().put("frame",frame);
            if (camera != null) requestValue.set("camera",JSON.valueToTree(camera));
            input.write(JSON.writeValueAsString(requestValue)); input.newLine(); input.flush();
            var request = CompletableFuture.supplyAsync(() -> {
                try { return output.readLine(20*1024*1024); } catch(IOException error) { throw new UncheckedIOException(error); }
            });
            String response=request.get(90,TimeUnit.SECONDS);
            if(response==null || response.length()>20*1024*1024) throw new IOException("Live preview worker exited or exceeded response limit: "+String.join(" | ",diagnostics));
            var tree=JSON.readTree(response);
            if(tree.has("error")) throw new IOException(tree.get("error").asText());
            var resolved=JSON.treeToValue(tree.get("sample"),StageplayTimeline.Frame.class);
            var inspections = tree.path("objectInspection");
            objectInspection = inspections.isArray() ? List.of(JSON.treeToValue(inspections, StageplayObjectInspection[].class)) : List.of();
            if(resolved.frame()!=frame) throw new IOException("Live preview returned a different frame");
            byte[] rgba=Base64.getDecoder().decode(tree.path("rgba").asText());
            if(rgba.length!=value.width()*value.height()*4) throw new IOException("Live preview pixel size mismatch");
            int[] argb=new int[value.width()*value.height()];
            for(int y=0;y<value.height();y++) for(int x=0;x<value.width();x++) {
                int at=((value.height()-1-y)*value.width()+x)*4;
                argb[y*value.width()+x]=(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|(rgba[at+2]&255);
            }
            if (image == null) image=SceneImage.streaming(value.width(),value.height());
            image.update(argb);
            byte[] encoded=Base64.getDecoder().decode(tree.path("pcm").asText());
            int rate=tree.path("sampleRate").intValue();
            if (rate<8000 || rate>192000 || encoded.length%4!=0 || encoded.length>rate/60*4+16)
                throw new IOException("Invalid native preview audio packet");
            short[] pcm=new short[encoded.length/2];
            java.nio.ByteBuffer.wrap(encoded).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            sound=new com.openggf.audio.StreamedMusicPort.SfxPcm(rate,2,pcm,1);
            sample=resolved; requestedCamera=camera; return sample;
        } catch(Exception failure) {
            close();
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Isolated LIVE preview failed: "+failure.getMessage(),failure);
        }
    }
    com.openggf.stageplay.StageplayGround.Report ground(StageplayDocument value, int frame,
            com.openggf.stageplay.StageplayGround.Batch batch, java.util.function.BooleanSupplier cancelled) throws IOException {
        long started = System.nanoTime();
        var basis = new com.openggf.stageplay.StageplayGround.SceneFrame(StageplayTimeline.sample(value,frame).set(),frame);
        boolean submitted = false, cancelledByCaller = false;
        try {
            if ((cancelledByCaller = cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            var donors = batch.queries().stream().map(q -> q.performer()).filter(p -> p instanceof com.openggf.stageplay.StageplayGround.Player)
                    .map(p -> ((com.openggf.stageplay.StageplayGround.Player)p).game()).collect(java.util.stream.Collectors.toSet());
            if (!value.equals(document) || !workerGames.containsAll(donors)) start(value,donors);
            var requestValue = JSON.createObjectNode().put("frame",frame);
            requestValue.set("ground",JSON.readTree(com.openggf.stageplay.StageplayGround.Json.encode(batch)));
            input.write(JSON.writeValueAsString(requestValue)); input.newLine(); input.flush(); submitted = true;
            var response = CompletableFuture.supplyAsync(() -> {
                try { return output.readLine(com.openggf.stageplay.StageplayGround.MAX_REPORT_CHARS + 1024); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            long deadline = started + (batch.maxMillis()+90000L)*1_000_000;
            String line;
            while (true) {
                if ((cancelledByCaller = cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (System.nanoTime() >= deadline) throw new IOException("Ground preview worker timed out");
                try { line = response.get(50,TimeUnit.MILLISECONDS); break; }
                catch (TimeoutException waiting) { /* Bounded wait with parent-side cancellation. */ }
            }
            if (line == null) throw new IOException("Ground preview worker exited");
            var tree = JSON.readTree(line);
            if (tree.has("error")) throw new IOException(tree.get("error").asText());
            var report = com.openggf.stageplay.StageplayGround.Json.decodeReport(tree.path("ground").toString());
            if (!report.basis().equals(basis) || !report.answers().stream().map(a -> a.id()).toList().equals(batch.queries().stream().map(q -> q.id()).toList()))
                throw new IOException("Ground preview response does not match the request");
            sample = null; requestedCamera = null; sound = null;
            return report;
        } catch (InterruptedException interrupted) {
            boolean wasInterrupted = !cancelledByCaller || Thread.currentThread().isInterrupted();
            close(); if (wasInterrupted) Thread.currentThread().interrupt();
            // A killed worker cannot publish its final counters; -1 explicitly means unobserved.
            return new com.openggf.stageplay.StageplayGround.Report(basis,batch.queries().stream().map(q ->
                    new com.openggf.stageplay.StageplayGround.Answer(q.id(),com.openggf.stageplay.StageplayGround.Outcome.INCOMPLETE,List.of(),List.of(),false)).toList(),
                    new com.openggf.stageplay.StageplayGround.Work(submitted ? -1 : 0,submitted ? -1 : 0,
                            (System.nanoTime()-started)/1_000_000,com.openggf.stageplay.StageplayGround.StopReason.CANCELLED));
        } catch (Exception failure) {
            close();
            throw new IOException("Isolated ground preview failed: "+failure.getMessage(),failure);
        }
    }
    long processId() { return process == null ? 0 : process.pid(); }
    SceneImage image() { return image; }
    List<StageplayObjectInspection> objectInspection() { return objectInspection; }
    com.openggf.audio.StreamedMusicPort.SfxPcm sound(int frame) { return sample!=null && sample.frame()==frame ? sound : null; }
    private void start(StageplayDocument value) throws IOException { start(value,Set.of()); }
    private void start(StageplayDocument value, Set<String> extraGames) throws IOException {
        close();
        var command=new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("--enable-native-access=ALL-UNNAMED"); command.add("-Xmx2g");
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("linux")) command.add("-Dopenggf.headless.gl=surfaceless-egl");
        else if(os.contains("mac")) command.add("-XstartOnFirstThread");
        command.add("-cp"); command.add(System.getProperty("java.class.path"));
        command.add("com.openggf.tools.StageplayPreviewTool");
        command.add("--rom-descriptor");
        var resolutions=new LinkedHashMap<String,com.openggf.data.RomImageCatalogue.Resolution>();
        var requiredGames = new LinkedHashSet<>(StageplayRomTransport.requiredGames(value)); requiredGames.addAll(extraGames);
        for(String game:requiredGames) {
            var identity=switch(game) { case "s1"->RomIdentity.S1; case "s2"->RomIdentity.S2; default->RomIdentity.S3K; };
            resolutions.put(game,EngineServices.current().roms().resolveLogicalRom(identity)
                    .orElseThrow(()->new IOException("LIVE preview needs the configured logical "+game+" ROM")));
        }
        String descriptor=StageplayRomTransport.encode(resolutions);
        String assetDescriptor=assets.encode(value);
        var builder=new ProcessBuilder(command); builder.environment().remove("DISPLAY"); builder.environment().remove("WAYLAND_DISPLAY");
        process=builder.start();
        Process owned=process;
        Thread.ofVirtual().name("stageplay-live-diagnostics").start(()-> {
            try(var reader=new BufferedReader(new InputStreamReader(owned.getErrorStream(),StandardCharsets.UTF_8))) {
                String line; while((line=reader.readLine())!=null) synchronized(diagnostics) {
                    if(diagnostics.size()==8) diagnostics.removeFirst(); diagnostics.addLast(line.substring(0,Math.min(512,line.length())));
                }
            } catch(IOException ignored) { }
        });
        output=new StageplayProcessInput(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
        input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        input.write(descriptor); input.newLine();
        input.write(Base64.getEncoder().encodeToString(StageplayStore.encode(value))); input.newLine();
        input.write(assetDescriptor); input.newLine(); input.flush(); document=value; workerGames=Set.copyOf(requiredGames);
    }
    @Override public void close() {
        if(process!=null) {
            var owned=process; process=null;
            try { owned.getOutputStream().close(); if(!owned.waitFor(2,TimeUnit.SECONDS)) { owned.destroy(); if(!owned.waitFor(2,TimeUnit.SECONDS)) owned.destroyForcibly(); } }
            catch(IOException ignored) { owned.destroyForcibly(); }
            catch(InterruptedException interrupted) { owned.destroyForcibly(); Thread.currentThread().interrupt(); }
        }
        document=null; workerGames=Set.of(); sample=null; image=null; sound=null; requestedCamera=null; objectInspection=List.of();
    }
}
