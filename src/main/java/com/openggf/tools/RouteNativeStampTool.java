package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.route.NativeFacts;
import com.openggf.route.QualificationStamp;
import com.openggf.route.ReplayScope;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Records a native check's outcome on a qualified route: the entry movie that reaches the route's
 * row 0, every observed lag VBlank as a native lag fact (stamped with the harness and the route's
 * content), and a {@code BIZHAWK} qualification stamp, passed or diverged at the first route row
 * that differed. The input is {@code tools/traces/route_native_check.py}'s {@code result.json}.
 * Native facts never reach the engine; the route's content (and its engine stamp) is unchanged.
 *
 * <p>Origin: shared route planner step 6 (2026-11-02), the SSZ1 Sonic+Tails cold routes.
 *
 * <pre>
 * java ... com.openggf.tools.RouteNativeStampTool ROUTE.route.json result.json
 * </pre>
 * The entry movie path in {@code result.json} is repository-relative and is read from the
 * working directory.
 */
public final class RouteNativeStampTool {
    private RouteNativeStampTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RouteNativeStampTool ROUTE.route.json result.json");
        Path manifest = Path.of(args[0]);
        JsonNode result = new ObjectMapper().readTree(Files.readString(Path.of(args[1])));
        RouteArtifact route = RouteFiles.read(manifest);
        if (result.path("format").asText().equals("openggf-native-goals/1")) {
            stampGoals(manifest,route,result);return;
        }
        String harness = result.get("harness").asText();
        String movie = result.get("entryMovie").asText();
        int prefix = result.get("prefixRows").asInt();
        if (result.get("routeRows").asInt() != route.tape().size()) {
            throw new IllegalArgumentException("the check played " + result.get("routeRows").asInt() + " rows; the route has "
                    + route.tape().size());
        }
        var entry = new NativeFacts.NativeEntry(movie, prefix, RouteFiles.tapeFromMovie(Path.of(movie), prefix).hash());
        var lag = new ArrayList<NativeFacts.LagFact>();
        for (JsonNode before : result.get("fillers")) {
            lag.add(new NativeFacts.LagFact(before.asInt(), harness, route.content()));
        }
        JsonNode verdict = result.get("verdict");
        int rows = route.tape().size();
        QualificationStamp stamp = switch (verdict.get(0).asText()) {
            case "agrees" -> verdict.get(1).asInt() == rows - 1
                    ? QualificationStamp.passed(ReplayScope.BIZHAWK, route.content(), harness, rows)
                    : QualificationStamp.diverged(ReplayScope.BIZHAWK, verdict.get(1).asInt() + 1,
                    "native rows ended after route row " + verdict.get(1).asInt(), route.content(), harness, rows);
            case "diverged" -> QualificationStamp.diverged(ReplayScope.BIZHAWK, verdict.get(1).asInt(),
                    verdict.get(2).asText() + " native " + verdict.get(3).asText() + " engine " + verdict.get(4).asText(),
                    route.content(), harness, rows);
            default -> throw new IllegalArgumentException("no native verdict to record: " + verdict);
        };
        RouteFiles.addStamp(manifest, stamp, new NativeFacts(Optional.of(entry), lag));
        System.out.println(manifest + ": " + stamp + ", " + lag.size() + " lag facts");
    }
    /** Named native observations, independently checked after a cold replay; never a full-state claim. */
    private static void stampGoals(Path manifest,RouteArtifact route,JsonNode result) throws Exception {
        var entry=route.manifest().nativeFacts().entry().orElseThrow(()->new IllegalArgumentException("native anchors required"));
        int expected=RouteFiles.nativeInputTape(route).size();
        if(result.path("nativeRows").asInt(-1)!=expected
                || !route.launch().rom().sha1().equalsIgnoreCase(result.path("romSha1").asText()))
            throw new IllegalArgumentException("native goal evidence launch/row identity differs");
        Path derived=Files.createTempFile("openggf-native-goal-identity-",".bk2");
        try {
            RouteFiles.exportBk2(route,derived);
            String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(derived)));
            if(!hash.equalsIgnoreCase(result.path("movieSha256").asText()))
                throw new IllegalArgumentException("native goal evidence is for another frozen movie");
        } finally {Files.deleteIfExists(derived);}
        var coverage=new ArrayList<String>();
        for(JsonNode check:result.path("coverage")) coverage.add(check.asText());
        String environment=result.path("harness").asText()+" movie "+result.path("movieSha256").asText()
                +"; accepted comparison boundary "+result.path("acceptedComparisonBoundary");
        QualificationStamp stamp=result.path("passed").asBoolean()
                ? QualificationStamp.passedChecks(ReplayScope.BIZHAWK,route.content(),environment,route.tape().size(),coverage)
                : new QualificationStamp(ReplayScope.BIZHAWK,false,java.util.OptionalInt.of(route.tape().size()-1),
                    "required native goals missing: "+result.path("missing"),route.content(),environment,route.tape().size(),coverage);
        RouteFiles.addStamp(manifest,stamp,route.manifest().nativeFacts());
        System.out.println(manifest+": "+stamp);
    }

}
