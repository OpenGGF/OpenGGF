package com.openggf.stageplay;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.route.StageplayRouteFootprint;
import java.io.IOException;
import java.util.*;

/** Pure questions, bounded terrain proposals and their strict unpublished wire format. */
public final class StageplayGround {
    private StageplayGround() {}
    public static final int MAX_QUERIES = 128, MAX_BAND = 1024, MAX_PLACEMENTS = 16, MAX_REFUSALS = 64;
    public static final int MAX_BATCH_CHARS = 65536, MAX_REPORT_CHARS = 4 * 1024 * 1024;
    public static final int DEFAULT_SCANS = 4_000_000, DEFAULT_TICKS = 6000;

    public enum ObjectPath {
        PRIMARY, ALTERNATE;
        public int index() { return ordinal(); }
        public static ObjectPath of(int path) {
            if (path < 0 || path > 1) throw new IllegalArgumentException("Object path must be 0 or 1");
            return values()[path];
        }
    }
    public sealed interface Performer permits Player, Visual {}
    public record Player(String game, String character, int path) implements Performer {
        public Player {
            if (!Set.of("s1", "s2", "s3k").contains(game) || character == null || character.isBlank()
                    || path < 0 || path > 1) throw new IllegalArgumentException("Invalid native player profile");
        }
    }
    public record Visual(int halfWidth, int height, int footOffset, ObjectPath path) implements Performer {
        public Visual { Objects.requireNonNull(path); new StageplayRouteFootprint(halfWidth, height, footOffset, path.index()); }
        public StageplayRouteFootprint footprint() { return new StageplayRouteFootprint(halfWidth, height, footOffset, path.index()); }
    }
    /** Inclusive native surface-row band; y in a placement is the authored centre/anchor. */
    public record Query(String id, int x, int top, int bottom, Performer performer, String cast) {
        public Query(String id, int x, int top, int bottom, Performer performer) { this(id,x,top,bottom,performer,""); }
        public Query {
            Objects.requireNonNull(cast);
            if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid ground query ID");
            Objects.requireNonNull(performer);
            if (x < 0 || x > Short.MAX_VALUE || top < Short.MIN_VALUE || bottom > Short.MAX_VALUE
                    || bottom < top || (long) bottom - top + 1 > MAX_BAND)
                throw new IllegalArgumentException("Ground query needs native coordinates and a band of at most 1024 rows");
        }
    }
    public record Batch(List<Query> queries, int maxMillis, int maxScans, int maxPreparationTicks) {
        public Batch(List<Query> queries, int maxMillis) { this(queries, maxMillis, DEFAULT_SCANS, DEFAULT_TICKS); }
        public Batch {
            queries = List.copyOf(queries);
            if (queries.isEmpty() || queries.size() > MAX_QUERIES || queries.stream().map(Query::id).distinct().count() != queries.size())
                throw new IllegalArgumentException("Ground batch needs 1..128 distinct query IDs");
            if (maxMillis < 1 || maxMillis > 60000 || maxScans < 1 || maxScans > 10_000_000
                    || maxPreparationTicks < 0 || maxPreparationTicks > 36000)
                throw new IllegalArgumentException("Invalid ground work budget");
        }
    }
    public enum Caveat { TERRAIN_ONLY, NO_EVENTS_OR_MUTATIONS, DEFAULT_WRAPPING, DESCRIBES_UNEDITED_DOCUMENT }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = EntryLayout.class, name = "ENTRY_LAYOUT"),
            @JsonSubTypes.Type(value = SceneFrame.class, name = "SCENE_FRAME")})
    public sealed interface Basis permits EntryLayout, SceneFrame {
        StageplayDocument.SetSource set();
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Set<Caveat> caveats();
    }
    public record EntryLayout(StageplayDocument.SetSource set) implements Basis {
        public EntryLayout { Objects.requireNonNull(set); }
        public Set<Caveat> caveats() { return EnumSet.of(Caveat.TERRAIN_ONLY, Caveat.NO_EVENTS_OR_MUTATIONS, Caveat.DEFAULT_WRAPPING); }
    }
    public record SceneFrame(StageplayDocument.SetSource set, int frame) implements Basis {
        public SceneFrame { Objects.requireNonNull(set); if (frame < 0) throw new IllegalArgumentException("Negative scene frame"); }
        public Set<Caveat> caveats() { return EnumSet.of(Caveat.TERRAIN_ONLY, Caveat.DESCRIBES_UNEDITED_DOCUMENT); }
    }
    public enum Outcome { FOUND, NONE_IN_BAND, INCOMPLETE, UNSUPPORTED, CONTEXT_UNAVAILABLE, BUDGET_EXCEEDED, CANCELLED }
    public enum Reason { GAP_UNDER_FOOTPRINT, OBSTRUCTED, UNSTABLE_SUPPORT, NO_GEOMETRIC_SUPPORT,
        UNSUITABLE_ORIENTATION, OUTSIDE_NATIVE_RANGE, PROFILE, LOOP_PLANE, REVERSE_GRAVITY, DUAL_PLANE,
        NON_STOCK_STATE, CONTEXT_UNAVAILABLE }
    public enum StopReason { NONE, SCAN_LIMIT, PREPARATION_LIMIT, TIME_LIMIT, CANCELLED }
    public enum Foot { LEFT, RIGHT }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = VisualSupport.class, name = "VISUAL_SUPPORT"),
            @JsonSubTypes.Type(value = PlayerTerrainProposal.class, name = "PLAYER_TERRAIN_PROPOSAL")})
    public sealed interface Evidence permits VisualSupport, PlayerTerrainProposal {}
    public record VisualSupport(int left, int centre, int right) implements Evidence {}
    public record PlayerTerrainProposal(Foot foot, int distance, int xRadius, int yRadius) implements Evidence {}
    public record Placement(int x, int y, int surfaceY, int angle, Evidence evidence) {
        public Placement { Objects.requireNonNull(evidence); if (angle < 0 || angle > 255) throw new IllegalArgumentException("Angle must be unsigned"); }
    }
    public record Refusal(int surfaceY, Reason reason, String detail) {
        public Refusal { Objects.requireNonNull(reason); Objects.requireNonNull(detail); detail = detail.substring(0,Math.min(256,detail.length())); }
    }
    public record Answer(String id, Outcome outcome, List<Placement> placements, List<Refusal> refusals, boolean truncated) {
        public Answer {
            Objects.requireNonNull(id); Objects.requireNonNull(outcome);
            placements = List.copyOf(placements); refusals = List.copyOf(refusals);
            if (placements.size() > MAX_PLACEMENTS || refusals.size() > MAX_REFUSALS)
                throw new IllegalArgumentException("Ground answer exceeds its bounds");
            if (outcome == Outcome.FOUND && placements.isEmpty() || outcome != Outcome.FOUND
                    && outcome != Outcome.INCOMPLETE && !placements.isEmpty()) throw new IllegalArgumentException("Inconsistent ground outcome");
            int previous = Integer.MIN_VALUE;
            for (var p : placements) { if (p.y() <= previous) throw new IllegalArgumentException("Placements must be distinct and top-first"); previous = p.y(); }
        }
        /** No selection claim when the search or returned list is incomplete. Ties choose smaller Y. */
        public Optional<Placement> nearest(double y) {
            if (outcome != Outcome.FOUND || truncated) return Optional.empty();
            return placements.stream().min(Comparator.comparingDouble((Placement p) -> Math.abs(p.y() - y)).thenComparingInt(Placement::y));
        }
    }
    public record Work(int nativeScans, int preparationTicks, long millis, StopReason stopped) {
        public Work { Objects.requireNonNull(stopped); }
    }
    public record Report(Basis basis, List<Answer> answers, Work work) {
        public Report { Objects.requireNonNull(basis); answers = List.copyOf(answers); Objects.requireNonNull(work); }
        public Answer answer(String id) { return answers.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(); }
    }
    /** Monotonic editor freshness without growing the pinned controller API. */
    public static long generation(StageplayController controller) { return controller.generation(); }

    public static Performer performerOf(StageplayDocument scene, String id, Visual visual) {
        var actor = scene.actors().stream().filter(a -> a.id().equals(id)).findFirst();
        if (actor.isPresent()) {
            if (visual != null) throw new IllegalArgumentException("Player cast cannot use an object footprint");
            var track = scene.puppets().stream().filter(t -> t.actorId().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Poser cast has no native standing profile"));
            return new Player(actor.get().gameId(), actor.get().character(), track.collisionPath());
        }
        if (scene.objectActors().stream().noneMatch(a -> a.id().equals(id))) throw new IllegalArgumentException("Unknown cast: " + id);
        return Objects.requireNonNull(visual, "Object cast needs an explicit visual footprint");
    }

    public static final class Json {
        private Json() {}
        private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        private static JsonNode read(String value, int bound) throws IOException {
            if (value.length() > bound) throw new IOException("Ground JSON exceeds its bound");
            return MAPPER.readTree(value);
        }
        private static void fields(JsonNode node, String... allowed) throws IOException {
            if (node == null || !node.isObject()) throw new IOException("Expected ground JSON object");
            var names = Set.of(allowed); var iterator = node.fieldNames();
            while (iterator.hasNext()) { String name = iterator.next(); if (!names.contains(name)) throw new IOException("Unknown ground field: " + name); }
        }
        private static int integer(JsonNode node, String name, Integer fallback) throws IOException {
            var value = node.get(name);
            if (value == null && fallback != null) return fallback;
            if (value == null || !value.isInt()) throw new IOException(name + " must be an integer");
            return value.intValue();
        }
        private static String string(JsonNode node, String name) throws IOException {
            var value = node.get(name);
            if (value == null || !value.isTextual()) throw new IOException(name + " must be text");
            return value.textValue();
        }
        public static Batch decodeBatch(String value, StageplayDocument scene) throws IOException {
            var root = read(value, MAX_BATCH_CHARS); fields(root, "maxMillis", "maxScans", "maxPreparationTicks", "queries");
            var array = root.get("queries");
            if (array == null || !array.isArray() || array.size() > MAX_QUERIES) throw new IOException("Expected at most 128 ground queries");
            var queries = new ArrayList<Query>();
            for (var q : array) {
                fields(q, "id", "x", "top", "bottom", "object", "player", "cast");
                Visual visual = null;
                if (q.has("object")) {
                    var o = q.get("object"); fields(o, "halfWidth", "height", "footOffset", "path");
                    visual = new Visual(integer(o,"halfWidth",null), integer(o,"height",null), integer(o,"footOffset",null), ObjectPath.of(integer(o,"path",0)));
                }
                Performer performer;
                if (q.has("cast")) {
                    if (scene == null || q.has("player")) throw new IOException("Cast queries require a scene and no explicit player");
                    performer = performerOf(scene, string(q,"cast"), visual);
                } else if (q.has("player") && visual == null) {
                    var player = q.get("player"); fields(player,"game","character","path");
                    performer = new Player(string(player,"game"),string(player,"character"),integer(player,"path",0));
                } else if (visual != null && !q.has("player")) performer = visual;
                else throw new IOException("A query needs exactly one player or object performer");
                queries.add(new Query(string(q,"id"),integer(q,"x",null),integer(q,"top",null),integer(q,"bottom",null),performer,q.has("cast") ? string(q,"cast") : ""));
            }
            return new Batch(queries,integer(root,"maxMillis",20000),integer(root,"maxScans",DEFAULT_SCANS),integer(root,"maxPreparationTicks",DEFAULT_TICKS));
        }
        public static String encode(Batch batch) throws IOException {
            var root = MAPPER.createObjectNode().put("maxMillis",batch.maxMillis()).put("maxScans",batch.maxScans()).put("maxPreparationTicks",batch.maxPreparationTicks());
            var array = root.putArray("queries");
            for (var q : batch.queries()) {
                var node = array.addObject().put("id",q.id()).put("x",q.x()).put("top",q.top()).put("bottom",q.bottom());
                if (!q.cast().isEmpty()) node.put("cast",q.cast());
                if (q.performer() instanceof Player p) { if (q.cast().isEmpty()) node.set("player",MAPPER.valueToTree(p)); }
                else { var v = (Visual) q.performer(); node.putObject("object").put("halfWidth",v.halfWidth()).put("height",v.height()).put("footOffset",v.footOffset()).put("path",v.path().index()); }
            }
            return MAPPER.writeValueAsString(root);
        }
        public static String encode(Report report) throws IOException { return MAPPER.writeValueAsString(report); }
        public static Report decodeReport(String value) throws IOException { return MAPPER.treeToValue(read(value, MAX_REPORT_CHARS), Report.class); }
    }
}
