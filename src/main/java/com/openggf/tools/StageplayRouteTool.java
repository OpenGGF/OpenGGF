package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.route.*;
import com.openggf.stageplay.runtime.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Isolated logic-only collision-route attempt for editors and the user CLI; no graphics/device boot.
 * Inputs: canonical Stageplay scene, typed bounded request, exact logical ROM windows and confined
 * content-snapshotted project/image/voice assets. Outputs: typed diagnostics and a command-only patch.
 * Origin: 2026-10-22 Stageplay complete-cast task.
 */
public final class StageplayRouteTool {
    private StageplayRouteTool() { }
    public static void main(String[] args) {
        PrintStream output = System.out; System.setOut(System.err);
        int code = execute(args,output);
        if (code != 0) System.exit(code);
    }
    public static int execute(String[] args, PrintStream output) {
        try {
            if (Arrays.equals(args,new String[]{"--worker"})) {
                var result = worker();
                output.println(StageplayRouteProtocol.encodeResult(result)); output.flush();
                return 0;
            }
            var flags = flags(args);
            Path destination=Path.of(required(flags,"out")).toAbsolutePath().normalize();
            if(java.nio.file.Files.exists(destination,java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Choose a new route output; existing files are never overwritten");
            Path scene = Path.of(required(flags,"scene")).toAbsolutePath().normalize();
            var document = StageplayStore.read(scene);
            String actor = required(flags,"actor"); int frame = integer(flags,"frame",0);
            int end = document.sceneChanges().stream().filter(c -> c.frame()>frame).mapToInt(c -> c.frame()).min().orElse(document.durationFrames());
            StageplayRouteFootprint footprint = null;
            if (flags.containsKey("half-width") || flags.containsKey("height") || flags.containsKey("foot-offset") || flags.containsKey("collision-path")) {
                footprint = new StageplayRouteFootprint(Integer.parseInt(required(flags,"half-width")),
                        Integer.parseInt(required(flags,"height")),Integer.parseInt(required(flags,"foot-offset")),integer(flags,"collision-path",0));
            }
            var request = new StageplayRouteRequest(actor,frame,Double.parseDouble(required(flags,"x")),Double.parseDouble(required(flags,"y")),
                    integer(flags,"frames",Math.min(300,end-frame)),integer(flags,"nodes",1024),integer(flags,"ticks",120000),integer(flags,"millis",60000),
                    decimal(flags,"urgency",1),footprint,decimal(flags,"speed",1),
                    new com.openggf.stageplay.StageplayDocument.RouteMotion(
                            com.openggf.stageplay.StageplayDocument.StartAction.valueOf(flags.getOrDefault("start","WALK").toUpperCase(Locale.ROOT)),
                            com.openggf.stageplay.StageplayDocument.TravelSpeed.valueOf(flags.getOrDefault("travel","WALK").toUpperCase(Locale.ROOT)),
                            com.openggf.stageplay.StageplayDocument.EndingAction.valueOf(flags.getOrDefault("ending","GENTLE_STOP").toUpperCase(Locale.ROOT)),
                            com.openggf.stageplay.StageplayDocument.EndingFacing.valueOf(flags.getOrDefault("facing","INHERIT").toUpperCase(Locale.ROOT))));
            String unavailable=StageplayRouteContract.unavailableReason(document,request);
            if(!unavailable.isEmpty()) {
                var unsupported=StageplayRouteResult.failure(StageplayRouteResult.Status.UNSUPPORTED,unavailable,"0".repeat(64),request,0,0);
                output.println(new ObjectMapper().writeValueAsString(Map.of("ok",false,"error",unsupported.status()+": "+unsupported.message(),"result",new ObjectMapper().readTree(StageplayRouteProtocol.encodeResult(unsupported)))));
                return 2;
            }
            var resolutions = new LinkedHashMap<String,RomImageCatalogue.Resolution>();
            for (String game : StageplayRomTransport.requiredGames(document)) {
                var image = PhysicalImage.probe(Path.of(required(flags,game+"-rom")));
                RomIdentity identity = switch(game) { case "s1" -> RomIdentity.S1; case "s2" -> RomIdentity.S2; default -> RomIdentity.S3K; };
                if (image.size()>64L*1024*1024) throw new IOException("Route ROM exceeds 64 MiB");
                resolutions.put(game,new RomImageCatalogue.Resolution(identity,List.of(new RomImageCatalogue.Part(identity,image,0,Math.toIntExact(image.size()))),
                        RomImageCatalogue.Source.EXPLICIT_KEY));
            }
            var snapshot = StageplayRouteInputs.capture(document,request,scene.getParent(),resolutions);
            var result = plan(snapshot);
            StageplayRouteProtocol.validatePatch(document,request,snapshot.fingerprint(),result);
            if (result.success()) {
                // Native cold verification is valid for exactly these disk inputs, including project contents.
                StageplayRouteInputs.Snapshot fresh;
                try { fresh=StageplayRouteInputs.capture(document,request,scene.getParent(),resolutions); }
                catch(IOException failure) {
                    result=StageplayRouteResult.failure(StageplayRouteResult.Status.STALE_INPUT,failure.getMessage(),snapshot.fingerprint(),request,result.expandedNodes(),result.simulatedTicks());
                    output.println(new ObjectMapper().writeValueAsString(Map.of("ok",false,"error",result.status()+": "+result.message(),"result",new ObjectMapper().readTree(StageplayRouteProtocol.encodeResult(result)))));
                    return 2;
                }
                if (!fresh.fingerprint().equals(snapshot.fingerprint()))
                    result = StageplayRouteResult.failure(StageplayRouteResult.Status.STALE_INPUT,"Route inputs changed during planning; plan again",snapshot.fingerprint(),request,result.expandedNodes(),result.simulatedTicks());
                else {
                    Path out = Path.of(required(flags,"out")).toAbsolutePath().normalize();
                    boolean relative = !document.set().levelProject().isEmpty()
                            || document.sceneChanges().stream().anyMatch(c -> !c.set().levelProject().isEmpty())
                            || document.audio().stream().anyMatch(c -> !c.asset().isEmpty())
                            || document.dialogueSkins().stream().anyMatch(c -> !c.decorationAsset().isEmpty())
                            || document.dialogue().stream().anyMatch(c -> !c.portraitAsset().isEmpty());
                    if (relative && !scene.getParent().toRealPath().equals(out.getParent().toRealPath()))
                        throw new IOException("Write routed scenes with relative assets beside the source scene");
                    StageplayTool.publish(out,StageplayStore.encode(result.candidate()));
                }
            }
            var json = new ObjectMapper();
            var report = new LinkedHashMap<String,Object>();
            report.put("ok",result.success());
            if (!result.success()) report.put("error",result.status()+": "+result.message());
            report.put("result",json.readTree(StageplayRouteProtocol.encodeResult(result)));
            output.println(json.writeValueAsString(report));
            return result.success()?0:2;
        } catch (Exception failure) {
            if(Arrays.equals(args,new String[]{"--worker"})) System.err.println("Route worker: "+Objects.toString(failure.getMessage(),failure.getClass().getSimpleName()));
            try { output.println(new ObjectMapper().writeValueAsString(Map.of("ok",false,"error",Objects.toString(failure.getMessage(),failure.getClass().getSimpleName())))); }
            catch(IOException impossible) { throw new UncheckedIOException(impossible); }
            return 2;
        }
    }
    private static StageplayRouteResult worker() throws IOException {
        try (var input = new StageplayProcessInput(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String roms = input.readLine(StageplayRomTransport.MAX_DESCRIPTOR_CHARS);
            String encoded = input.readLine(StageplayStore.MAX_BYTES*2);
            if (encoded==null) throw new IOException("Missing route source document");
            var document = StageplayStore.decode(Base64.getDecoder().decode(encoded));
            String assets = input.readLine(StageplayRouteAssets.MAX_DESCRIPTOR_CHARS);
            var request = StageplayRouteProtocol.decodeEnvelope(input.readLine(StageplayRouteProtocol.MAX_REQUEST_CHARS));
            if (input.readLine(0)!=null) throw new IOException("Trailing route request records");
            String fingerprint = StageplayRouteInputs.fingerprint(document,request.request(),Path.of(request.root()),roms,assets);
            if (!fingerprint.equals(request.fingerprint())) throw new IOException("Route request content fingerprint mismatch");
            return plan(new StageplayRouteInputs.Snapshot(document,request.request(),Path.of(request.root()),roms,assets,fingerprint));
        }
    }
    /** Caller must be an unoccupied disposable process; this cannot replace an editor or gameplay session. */
    public static StageplayRouteResult plan(StageplayRouteInputs.Snapshot snapshot) throws IOException {
        if (com.openggf.game.session.SessionManager.getCurrentWorldSession()!=null)
            throw new IOException("Route planning requires an isolated logic worker; the active world must not be replaced");
        String actual=StageplayRouteInputs.fingerprint(snapshot.document(),snapshot.request(),snapshot.root(),snapshot.romDescriptor(),snapshot.assetDescriptor());
        if(!actual.equals(snapshot.fingerprint())) throw new IOException("Route snapshot fingerprint does not match its contents");
        try (var library = SceneRomLibrary.openReaders(StageplayRomTransport.open(snapshot.romDescriptor()),snapshot.document().set().gameId());
             var resources = new StageplayResources(library,false)) {
            resources.setAssetReader(StageplayRouteAssets.decode(snapshot.assetDescriptor(),snapshot.document()));
            var result = StageplayRoutePlanner.plan(resources,library,snapshot.document(),snapshot.request(),snapshot.fingerprint(),Thread.currentThread()::isInterrupted);
            StageplayRouteProtocol.validatePatch(snapshot.document(),snapshot.request(),snapshot.fingerprint(),result);
            return result;
        }
    }
    private static Map<String,String> flags(String[] args) throws IOException {
        Set<String> allowed = Set.of("start","travel","ending","facing","scene","actor","frame","x","y","out","s1-rom","s2-rom","s3k-rom","frames","nodes","ticks","millis","urgency","speed","half-width","height","foot-offset","collision-path");
        var result = new LinkedHashMap<String,String>();
        for(int i=0;i<args.length;i+=2) {
            if(i+1==args.length || !args[i].startsWith("--") || !allowed.contains(args[i].substring(2))
                    || result.put(args[i].substring(2),args[i+1])!=null) throw new IOException("Expected known unique --flag value pairs");
        }
        required(result,"out"); return result;
    }
    private static String required(Map<String,String> flags,String key) throws IOException {
        String value=flags.get(key); if(value==null || value.isBlank()) throw new IOException("--"+key+" is required"); return value;
    }
    private static int integer(Map<String,String> flags,String key,int fallback) { return flags.containsKey(key)?Integer.parseInt(flags.get(key)):fallback; }
    private static double decimal(Map<String,String> flags,String key,double fallback) { return flags.containsKey(key)?Double.parseDouble(flags.get(key)):fallback; }
}
