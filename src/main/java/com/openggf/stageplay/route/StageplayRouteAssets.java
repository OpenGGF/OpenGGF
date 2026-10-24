package com.openggf.stageplay.route;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayAssets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Route-only immutable union of terrain, dialogue and voice dependencies; no child filesystem asset access. */
public final class StageplayRouteAssets {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    public static final int MAX_DESCRIPTOR_CHARS = (MAX_BYTES + 2) / 3 * 4 + 256 * (1024 * 6 + 16) + 128;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(MAX_DESCRIPTOR_CHARS)
                    .maxStringLength((MAX_BYTES+2)/3*4).maxNameLength(1024).maxNestingDepth(4).maxTokenCount(2048).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private StageplayRouteAssets() { }

    public static String capture(StageplayDocument document,Path root) throws IOException {
        var result=JSON.createObjectNode().put("version",1); var values=result.putObject("assets");
        long total=0;
        for(var entry:paths(document).entrySet()) {
            Path realRoot=root.toRealPath(),file=realRoot.resolve(entry.getKey()).toRealPath();
            if(!file.startsWith(realRoot) || !Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Route asset escapes scene directory: "+entry.getKey());
            int maximum=entry.getValue();
            if(Files.size(file)>maximum) throw new IOException("Route asset exceeds its encoded byte budget: "+entry.getKey());
            byte[] bytes;
            try(var input=Files.newInputStream(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(maximum+1); }
            total+=bytes.length;
            if(bytes.length==0 || bytes.length>maximum || total>MAX_BYTES) throw new IOException("Route assets exceed per-file or aggregate 64 MiB budget");
            values.put(entry.getKey(),Base64.getEncoder().encodeToString(bytes));
        }
        String encoded=JSON.writeValueAsString(result);
        if(encoded.length()>MAX_DESCRIPTOR_CHARS) throw new IOException("Route asset descriptor exceeds bound");
        return encoded;
    }
    public static StageplayAssets.Reader decode(String encoded,StageplayDocument document) throws IOException {
        return decode(encoded,document,MAX_BYTES);
    }
    static StageplayAssets.Reader decode(String encoded,StageplayDocument document,int totalLimit) throws IOException {
        if(totalLimit<1 || totalLimit>MAX_BYTES) throw new IllegalArgumentException("Invalid route asset budget");
        if(encoded==null || encoded.length()>MAX_DESCRIPTOR_CHARS) throw new IOException("Missing or oversized route asset descriptor");
        var tree=JSON.readTree(encoded);
        if(tree==null || !tree.isObject() || tree.size()!=2 || !tree.path("version").isInt()
                || tree.path("version").intValue()!=1 || !tree.path("assets").isObject()) throw new IOException("Invalid route asset descriptor");
        var expected=paths(document); var values=tree.path("assets");
        if(values.size()!=expected.size()) throw new IOException("Route asset snapshots do not match declared dependencies");
        long total=0;var bytes=new LinkedHashMap<String,byte[]>();
        for(var entry:expected.entrySet()) {
            var value=values.get(entry.getKey()); int maximum=entry.getValue();
            if(value==null || !value.isTextual() || value.textValue().length()>(maximum+2L)/3*4)
                throw new IOException("Missing or oversized route asset: "+entry.getKey());
            byte[] decoded;
            try { decoded=Base64.getDecoder().decode(value.textValue()); }
            catch(IllegalArgumentException failure) { throw new IOException("Invalid route asset encoding",failure); }
            total+=decoded.length;
            if(decoded.length==0 || decoded.length>maximum || total>totalLimit) throw new IOException("Route assets exceed per-file or aggregate 64 MiB budget");
            bytes.put(entry.getKey(),decoded);
        }
        return entry -> {
            byte[] value=bytes.get(entry);
            if(value==null) throw new IOException("Asset was not supplied to route worker: "+entry);
            return value.clone();
        };
    }
    private static Map<String,Integer> paths(StageplayDocument document) throws IOException {
        var paths=new LinkedHashMap<String,Integer>();
        if(!document.set().levelProject().isEmpty()) paths.put(document.set().levelProject(),AuthoredLevelResources.MAX_ENCODED_BYTES);
        document.sceneChanges().stream().map(c->c.set().levelProject()).filter(p->!p.isEmpty()).forEach(p->paths.putIfAbsent(p,AuthoredLevelResources.MAX_ENCODED_BYTES));
        StageplayAssets.imagePaths(document).forEach(p->paths.putIfAbsent(p,(int)StageplayAssets.MAX_ENCODED_BYTES));
        document.audio().stream().map(c->c.asset()).filter(p->!p.isEmpty()).forEach(p->paths.putIfAbsent(p,(int)StageplayAssets.MAX_ENCODED_BYTES));
        if(paths.size()>StageplayAssets.MAX_ASSETS) throw new IOException("Route has more than 256 declared assets");
        for(String path:paths.keySet()) ModAssetRoot.requireNormalizedEntry(path);
        return paths;
    }
}
