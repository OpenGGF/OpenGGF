package openggf.timeattack;

import openggf.timeattack.ghost.GhostFileCodec;
import openggf.timeattack.ghost.GhostHeader;
import openggf.timeattack.ghost.GhostRecording;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Best-run persistence. The atomic ghost file selects a verified input sidecar by content hash. */
public final class GhostStore {
    public static final int MAX_SCAN_ENTRIES=4096, MAX_SCAN_SIDECARS=512, MAX_SCAN_DEPTH=6;
    public enum SaveStatus { SAVED, NOT_BEST, REJECTED_PRIMARY, FAILED }
    public record SaveResult(SaveStatus status,String notice) {
        public boolean saved() { return status==SaveStatus.SAVED; }
    }
    @FunctionalInterface interface Publisher { void move(Path from,Path to) throws IOException; }
    private final Path root;
    private final Publisher publisher;
    public GhostStore(Path root) { this(root,(from,to)->Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)); }
    GhostStore(Path root,Publisher publisher) { this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();this.publisher=Objects.requireNonNull(publisher); }

    public Optional<GhostRecording> loadBest(String gameId,int zone,int act,String character) throws IOException {
        Path best=bestPath(gameId,zone,act,character);
        IOException rejected=null;
        for(Path path:List.of(best,stemSuffix(best,"-prev1"),stemSuffix(best,"-prev2"))) {
            if(!Files.exists(path))continue;
            try { return Optional.of(readMatching(path,gameId,zone,act,character)); }
            catch(IOException invalid) { if(rejected==null)rejected=invalid; }
        }
        if(rejected!=null)throw rejected;
        return Optional.empty();
    }

    /** Visible outcome for callers; rejected primaries never acquire overwrite authority from recovery. */
    public SaveResult saveBest(GhostRecording candidate,AttemptInputRecording inputs) {
        try { return saveChecked(candidate,inputs); }
        catch(RejectedPrimary rejected) { return new SaveResult(SaveStatus.REJECTED_PRIMARY,"EXISTING RECORD PRESERVED"); }
        catch(IOException | IllegalArgumentException | UncheckedIOException failed) { return new SaveResult(SaveStatus.FAILED,"SAVE NOT CONFIRMED"); }
    }
    /** Compatibility path: invalid input binding remains an argument error and IO rejection stays checked. */
    public boolean saveIfBest(GhostRecording candidate,AttemptInputRecording inputs) throws IOException {
        return saveChecked(candidate,inputs).saved();
    }

    private synchronized SaveResult saveChecked(GhostRecording candidate,AttemptInputRecording inputs) throws IOException {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(inputs,"inputs");
        GhostHeader h=candidate.header();
        Path best=bestPath(h.gameId(),h.zone(),h.act(),h.character());
        if(inputs.frameCount()>AttemptInputRecording.MAX_FRAMES)throw new IOException("Attempt recording exceeds frame limit");
        byte[] encoded=inputs.encode();
        AttemptInputRecording checked=AttemptInputRecording.decodeChecked(encoded);
        if(!Arrays.equals(h.inputRecordingHash(),checked.sha256()))throw new IllegalArgumentException("ghost header inputRecordingHash does not match the supplied input recording");
        if(!sameRoute(h,checked.start().gameId(),checked.start().zone(),checked.start().act(),checked.start().character()))
            throw new IllegalArgumentException("ghost and input recording routes differ");
        GhostRecording existing=null;
        if(Files.exists(best)) {
            try { existing=readMatching(best,h.gameId(),h.zone(),h.act(),h.character()); }
            catch(IOException rejected) { throw new RejectedPrimary(rejected); }
        } else existing=loadBest(h.gameId(),h.zone(),h.act(),h.character()).orElse(null);
        if(existing!=null && existing.header().finalTimeFrames()<=h.finalTimeFrames())return new SaveResult(SaveStatus.NOT_BEST,"");
        Files.createDirectories(best.getParent());
        Path staged=Files.createTempFile(best.getParent(),".ghost-",".tmp");
        boolean published=false;
        try {
            // Complete codec validation and round-trip happen before any primary/history mutation.
            GhostFileCodec.write(candidate,staged);
            GhostRecording verified=GhostFileCodec.read(staged);
            if(!h.equals(verified.header()) || !Arrays.equals(candidate.frameData(),verified.frameData()))
                throw new IOException("Ghost staging verification failed");
            putInputs(best,h,encoded);
            Optional<byte[]> previousInputs=preserveInputs(best);
            Path prev1=stemSuffix(best,"-prev1"),prev2=stemSuffix(best,"-prev2");
            Optional<byte[]> olderInputs=preserveInputs(prev1);
            copyIfExists(prev1,prev2);
            if(olderInputs.isPresent())atomicBytes(olderInputs.orElseThrow(),sibling(prev2,".ggfinputs"));
            copyIfExists(best,prev1);
            if(previousInputs.isPresent())atomicBytes(previousInputs.orElseThrow(),sibling(prev1,".ggfinputs"));
            // One atomic publication authority selects the already-verified immutable hash sidecar.
            // The legacy filename alias can lag publication; it is not the pair's authority.
            publisher.move(staged,best);
            published=true;
            String notice="";
            try { atomicBytes(encoded,sibling(best,".ggfinputs")); }
            catch(IOException aliasFailed) { notice="RECORD SAVED - COPY PENDING"; }
            return new SaveResult(SaveStatus.SAVED,notice);
        } finally {
            try { Files.deleteIfExists(staged); }
            catch(IOException cleanup) { if(!published)throw cleanup; }
            pruneInputs(best);
        }
    }

    public List<Path> listImports(String gameId) throws IOException {
        GhostFileCodec.validateRoute(gameId,0,0,"sonic");
        Path dir=root.resolve(gameId).resolve("import");
        if(!Files.isDirectory(dir))return List.of();
        try(Stream<Path> files=Files.list(dir)) {
            var imports=new ArrayList<Path>();var iterator=files.iterator();int entries=0;
            while(iterator.hasNext()) {
                if(++entries>MAX_SCAN_ENTRIES)throw new IOException("Import scan entry limit exceeded");
                Path path=iterator.next();
                if(path.getFileName().toString().endsWith(".ggfghost") && Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))imports.add(path);
            }
            imports.sort(Comparator.naturalOrder());return List.copyOf(imports);
        } catch(UncheckedIOException failure) { throw failure.getCause(); }
    }

    /** Lazy bounded lookup includes legacy aliases and retained hash-addressed sidecars. */
    public Optional<byte[]> findInputRecording(String hashHex) throws IOException {
        if(hashHex==null || !hashHex.matches("[a-fA-F0-9]{64}"))return Optional.empty();
        if(!Files.isDirectory(root))return Optional.empty();
        try(Stream<Path> files=Files.walk(root,MAX_SCAN_DEPTH)) {
            var iterator=files.iterator();int entries=0,sidecars=0;
            boolean legacyLimit=false,hashFileRead=false;
            while(iterator.hasNext()) {
                if(++entries>MAX_SCAN_ENTRIES)throw new IOException("Recording scan entry limit exceeded");
                Path file=iterator.next();
                if(!file.getFileName().toString().endsWith(".ggfinputs") || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                String name=file.getFileName().toString();
                if(name.matches("[a-fA-F0-9]{64}\\.ggfinputs")) {
                    if(!name.equalsIgnoreCase(hashHex+".ggfinputs") || hashFileRead)continue;
                    hashFileRead=true;
                } else if(++sidecars>MAX_SCAN_SIDECARS) {
                    // Reserve hash-directed lookup even when the bounded legacy decode budget is exhausted.
                    legacyLimit=true;continue;
                }
                try {
                    byte[] encoded=readInputs(file);
                    if(HexFormat.of().formatHex(AttemptInputRecording.decodeChecked(encoded).sha256()).equalsIgnoreCase(hashHex))return Optional.of(encoded);
                } catch(IOException | IllegalArgumentException invalid) { /* A rejected file remains untouched; keep looking. */ }
            }
            if(legacyLimit)throw new IOException("Recording sidecar scan limit exceeded");
            return Optional.empty();
        } catch(UncheckedIOException failure) { throw failure.getCause(); }
    }

    private Path bestPath(String gameId,int zone,int act,String character) throws IOException {
        GhostFileCodec.validateRoute(gameId,zone,act,character);
        // Built-in filenames remain compatible; canonical mod identities need filename escaping.
        String safe=java.net.URLEncoder.encode(character,java.nio.charset.StandardCharsets.UTF_8);
        return root.resolve(gameId).resolve(zone+"-"+act+"-"+safe+".ggfghost");
    }
    private GhostRecording readMatching(Path file,String game,int zone,int act,String character) throws IOException {
        GhostRecording recording=GhostFileCodec.read(file);
        if(!sameRoute(recording.header(),game,zone,act,character))throw new IOException("Ghost route does not match its saved identity");
        return recording;
    }
    private static boolean sameRoute(GhostHeader h,String game,int zone,int act,String character) {
        return h.gameId().equals(game) && h.zone()==zone && h.act()==act && h.character().equals(character);
    }
    private Path inputDirectory(Path best) { return best.resolveSibling(stem(best)+".recordings"); }
    private Path inputPath(Path best,GhostHeader h) { return inputDirectory(best).resolve(HexFormat.of().formatHex(h.inputRecordingHash())+".ggfinputs"); }
    private void putInputs(Path best,GhostHeader h,byte[] encoded) throws IOException {
        Path target=inputPath(best,h);
        if(Files.exists(target)) {
            if(!Arrays.equals(readInputs(target),encoded))throw new IOException("Existing hash sidecar is rejected; preserved");
        } else atomicBytes(encoded,target);
    }
    private Optional<byte[]> preserveInputs(Path ghost) throws IOException {
        if(!Files.exists(ghost))return Optional.empty();
        GhostHeader h=GhostFileCodec.read(ghost).header();
        String name=stem(ghost).replaceFirst("-prev[12]$","");
        Path primary=ghost.resolveSibling(name+".ggfghost");
        Path committed=inputPath(primary,h);
        // A legacy alias may lag publication; prefer the immutable sidecar selected by the ghost hash.
        Path alias=Files.exists(committed) ? committed : sibling(ghost,".ggfinputs");
        if(!Files.exists(alias))return Optional.empty();
        byte[] encoded=readInputs(alias);
        if(!Arrays.equals(AttemptInputRecording.decodeChecked(encoded).sha256(),h.inputRecordingHash()))throw new IOException("Existing input sidecar does not match ghost; preserved");
        // All three retained ghosts share the route's hash directory, even before history moves.
        putInputs(primary,h,encoded);
        return Optional.of(encoded);
    }
    private void pruneInputs(Path best) {
        // Bounded housekeeping cannot turn an already published record into a failed save.
        try {
            Set<String> retained=new HashSet<>();
            for(Path ghost:List.of(best,stemSuffix(best,"-prev1"),stemSuffix(best,"-prev2")))
                if(Files.exists(ghost))retained.add(HexFormat.of().formatHex(GhostFileCodec.read(ghost).header().inputRecordingHash())+".ggfinputs");
            try(Stream<Path> files=Files.list(inputDirectory(best))) {
                var iterator=files.iterator();int count=0;
                while(iterator.hasNext() && ++count<=MAX_SCAN_ENTRIES) {
                    Path file=iterator.next();String name=file.getFileName().toString();
                    if(name.matches("[a-f0-9]{64}\\.ggfinputs") && !retained.contains(name))Files.deleteIfExists(file);
                }
            }
        } catch(IOException | UncheckedIOException ignored) { /* Retry housekeeping on the next successful publication. */ }
    }
    private static byte[] readInputs(Path file) throws IOException {
        if(Files.size(file)>AttemptInputRecording.MAX_ENCODED_BYTES)throw new IOException("Recording sidecar exceeds byte limit");
        try(var input=Files.newInputStream(file)) {
            byte[] encoded=input.readNBytes(AttemptInputRecording.MAX_ENCODED_BYTES+1);
            if(encoded.length>AttemptInputRecording.MAX_ENCODED_BYTES)throw new IOException("Recording sidecar exceeds byte limit");
            AttemptInputRecording.decodeChecked(encoded);return encoded;
        }
    }
    private void atomicBytes(byte[] encoded,Path target) throws IOException {
        Files.createDirectories(target.getParent());Path staged=Files.createTempFile(target.getParent(),".inputs-",".tmp");
        try { Files.write(staged,encoded);publisher.move(staged,target); }
        finally { Files.deleteIfExists(staged); }
    }
    private void copyIfExists(Path from,Path to) throws IOException {
        if(!Files.exists(from))return;
        Path staged=Files.createTempFile(to.getParent(),".history-",".tmp");
        try { Files.copy(from,staged,StandardCopyOption.REPLACE_EXISTING);publisher.move(staged,to); }
        finally { Files.deleteIfExists(staged); }
    }
    private static String stem(Path file) { String name=file.getFileName().toString();return name.substring(0,name.length()-".ggfghost".length()); }
    private static Path stemSuffix(Path file,String suffix) { return file.resolveSibling(stem(file)+suffix+".ggfghost"); }
    private static Path sibling(Path file,String extension) { return file.resolveSibling(stem(file)+extension); }
    private static final class RejectedPrimary extends IOException { private RejectedPrimary(IOException cause) { super("Existing ghost primary rejected and preserved",cause); } }
}
