package com.openggf.stageplay.route;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/** Strict bounded JSON records for the one-attempt, logic-only route subprocess. */
public final class StageplayRouteProtocol {
    public static final int MAX_REQUEST_CHARS = 16 * 1024;
    public static final int MAX_RESULT_CHARS = 16 * 1024 * 1024;
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxDocumentLength(MAX_RESULT_CHARS)
                                                    .maxStringLength(StageplayStore.MAX_BYTES * 2)
                                                    .maxNestingDepth(24)
                                                    .maxNumberLength(32)
                                                    .build())
                                    .build())
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .build();

    private StageplayRouteProtocol() {}

    public record Envelope(
            int version, String fingerprint, String root, StageplayRouteRequest request) {}

    private record Response(
            int version,
            StageplayRouteResult.Status status,
            String message,
            String fingerprint,
            StageplayRouteRequest request,
            String candidate,
            List<StageplayDocument.PuppetCommand> commands,
            List<StageplayDocument.ObjectCommand> objectCommands,
            List<StageplayRoutePoint> path,
            int expandedNodes,
            int simulatedTicks,
            boolean nativeVerified) {}

    public static String encodeRequest(StageplayRouteRequest request) throws IOException {
        return JSON.writeValueAsString(request);
    }

    public static String envelope(StageplayRouteInputs.Snapshot snapshot) throws IOException {
        return JSON.writeValueAsString(
                new Envelope(
                        1, snapshot.fingerprint(), snapshot.root().toString(), snapshot.request()));
    }

    public static Envelope decodeEnvelope(String line) throws IOException {
        if (line == null || line.length() > MAX_REQUEST_CHARS)
            throw new IOException("Missing or oversized route request");
        Envelope value = JSON.readValue(line, Envelope.class);
        if (value.version() != 1
                || value.fingerprint() == null
                || !value.fingerprint().matches("[0-9a-f]{64}")
                || value.root() == null
                || value.root().length() > 4096
                || !java.nio.file.Path.of(value.root()).isAbsolute()
                || value.request() == null) throw new IOException("Invalid route request envelope");
        return value;
    }

    public static String encodeResult(StageplayRouteResult result) throws IOException {
        String candidate =
                result.candidate() == null
                        ? ""
                        : Base64.getEncoder()
                                .encodeToString(StageplayStore.encode(result.candidate()));
        String line =
                JSON.writeValueAsString(
                        new Response(
                                1,
                                result.status(),
                                result.message(),
                                result.fingerprint(),
                                result.request(),
                                candidate,
                                result.commands(),
                                result.objectCommands(),
                                result.path(),
                                result.expandedNodes(),
                                result.simulatedTicks(),
                                result.nativeVerified()));
        if (line.length() > MAX_RESULT_CHARS)
            throw new IOException("Route response exceeds 16 MiB");
        return line;
    }

    public static StageplayRouteResult decodeResult(String line) throws IOException {
        if (line == null || line.length() > MAX_RESULT_CHARS)
            throw new IOException("Missing or oversized route response");
        var response = JSON.readValue(line, Response.class);
        if (response.version() != 1 || response.candidate() == null)
            throw new IOException("Invalid route response version/candidate");
        StageplayDocument candidate = null;
        if (!response.candidate().isEmpty()) {
            try {
                candidate = StageplayStore.decode(Base64.getDecoder().decode(response.candidate()));
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid route candidate encoding", error);
            }
        }
        return new StageplayRouteResult(
                response.status(),
                response.message(),
                response.fingerprint(),
                response.request(),
                candidate,
                response.commands(),
                response.objectCommands(),
                response.path(),
                response.expandedNodes(),
                response.simulatedTicks(),
                response.nativeVerified());
    }

    /**
     * A worker cannot silently edit poses, sets, another performer or unrelated command intervals.
     */
    public static void validatePatch(
            StageplayDocument source,
            StageplayRouteRequest request,
            String fingerprint,
            StageplayRouteResult result)
            throws IOException {
        if (result==null) throw new IOException("Route worker returned no result");
        if (!fingerprint.equals(result.fingerprint()) || !request.equals(result.request()))
            throw new IOException("Stale route result binding");
        boolean player = source.actors().stream().anyMatch(a -> a.id().equals(request.actorId()));
        boolean success =
                result.status() == StageplayRouteResult.Status.VERIFIED
                        || result.status() == StageplayRouteResult.Status.VISUAL_TERRAIN;
        if (!success) {
            if (result.candidate() != null
                    || !result.commands().isEmpty()
                    || !result.objectCommands().isEmpty())
                throw new IOException("Failed route returned an applicable command patch");
            return;
        }
        if (result.candidate() == null
                || player != (result.status() == StageplayRouteResult.Status.VERIFIED)
                || player != result.nativeVerified()
                || player && !result.objectCommands().isEmpty()
                || !player && !result.commands().isEmpty())
            throw new IOException("Route verification kind does not match performer");
        StageplayDocument stripped = result.candidate();
        if (player) {
            var original =
                    source.puppets().stream()
                            .filter(p -> p.actorId().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow(() -> new IOException("Route performer is not a Puppet"));
            var changed =
                    stripped.puppets().stream()
                            .filter(p -> p.actorId().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow(() -> new IOException("Route removed performer track"));
            validateCommandInterval(
                    original.commands(), changed.commands(), result.commands(), request);
            var restored =
                    new StageplayDocument.PuppetTrack(
                            changed.actorId(),
                            changed.snapToFloor(),
                            changed.collisionPath(),
                            original.commands());
            stripped =
                    stripped.withPuppets(
                            stripped.puppets().stream()
                                    .map(p -> p.actorId().equals(request.actorId()) ? restored : p)
                                    .toList());
        } else {
            var original =
                    source.objectActors().stream()
                            .filter(a -> a.id().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow(() -> new IOException("Unknown object route performer"));
            var changed =
                    stripped.objectActors().stream()
                            .filter(a -> a.id().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow(() -> new IOException("Route removed object performer"));
            validateObjectInterval(
                    original.commands(), changed.commands(), result.objectCommands(), request);
            var restored =
                    new StageplayDocument.ObjectActor(
                            changed.id(),
                            changed.name(),
                            changed.gameId(),
                            changed.zone(),
                            changed.act(),
                            changed.objectId(),
                            changed.subtype(),
                            changed.layoutIndex(),
                            changed.keys(),
                            original.commands(),
                            changed.sourceKind());
            stripped =
                    stripped.withObjectActors(
                            stripped.objectActors().stream()
                                    .map(a -> a.id().equals(request.actorId()) ? restored : a)
                                    .toList());
        }
        if (!source.equals(stripped))
            throw new IOException("Route candidate edits data outside the requested command track");
    }

    private static void validateCommandInterval(
            List<StageplayDocument.PuppetCommand> old,
            List<StageplayDocument.PuppetCommand> actual,
            List<StageplayDocument.PuppetCommand> proposed,
            StageplayRouteRequest request)
            throws IOException {
        var expected =
                new java.util.ArrayList<>(
                        old.stream()
                                .filter(
                                        c ->
                                                c.frame() < request.startFrame()
                                                        || c.frame()
                                                                >= request.startFrame()
                                                                        + request.maxFrames())
                                .toList());
        for (var command : proposed) {
            if (command.frame() < request.startFrame()
                    || command.frame() >= request.startFrame() + request.maxFrames())
                throw new IOException("Route command exceeds requested interval");
            expected.add(command);
        }
        expected.sort(java.util.Comparator.comparingInt(StageplayDocument.PuppetCommand::frame));
        if (!expected.equals(actual))
            throw new IOException("Route changed unrelated performer commands");
    }

    private static void validateObjectInterval(
            List<StageplayDocument.ObjectCommand> old,
            List<StageplayDocument.ObjectCommand> actual,
            List<StageplayDocument.ObjectCommand> proposed,
            StageplayRouteRequest request)
            throws IOException {
        var expected =
                new java.util.ArrayList<>(
                        old.stream()
                                .filter(
                                        c ->
                                                c.frame() < request.startFrame()
                                                        || c.frame()
                                                                >= request.startFrame()
                                                                        + request.maxFrames())
                                .toList());
        for (var command : proposed) {
            if (command.frame() < request.startFrame()
                    || command.frame() >= request.startFrame() + request.maxFrames())
                throw new IOException("Object route command exceeds requested interval");
            expected.add(command);
        }
        expected.sort(java.util.Comparator.comparingInt(StageplayDocument.ObjectCommand::frame));
        if (!expected.equals(actual))
            throw new IOException("Route changed unrelated object commands");
    }
}
