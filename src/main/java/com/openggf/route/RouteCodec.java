package com.openggf.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The canonical text codec for route value types: manifests, launches, goal programs and whole
 * artifacts for transport between processes. Parsing is strict: unknown keys, missing keys and
 * wrong types are rejected.
 */
public final class RouteCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private RouteCodec() {
    }

    /** The manifest text that binds {@code scriptName} beside it. */
    public static String formatManifest(RouteManifest manifest, String scriptName) {
        ObjectNode root = manifestNode(manifest);
        ObjectNode tape = (ObjectNode) root.get("tape");
        tape.put("script", scriptName);
        tape.put("rows", manifest.rowCount());
        tape.put("sha256", manifest.tapeHash().sha256());
        return text(root);
    }

    /** One self-contained text for an in-memory artifact (its rows inline), for handing to a worker. */
    public static String formatTransport(RouteArtifact artifact) {
        ObjectNode root = manifestNode(artifact.manifest());
        ObjectNode tape = (ObjectNode) root.get("tape");
        tape.put("inline", RouteScript.write(artifact.tape(), java.util.List.of()));
        tape.put("rows", artifact.tape().size());
        tape.put("sha256", artifact.tape().hash().sha256());
        return text(root);
    }

    /** Parses {@link #formatTransport}; rows and hash must agree. */
    public static RouteArtifact parseTransport(String text) throws IOException {
        JsonNode root = COMPACT.readTree(text);
        RouteManifest manifest = manifest(root, true);
        InputTape tape = RouteScript.read(text(root.get("tape"), "inline"));
        if (tape.size() != manifest.rowCount() || !tape.hash().equals(manifest.tapeHash())) {
            throw new IOException("inline rows (" + tape.size() + ", " + tape.hash().shortForm()
                    + ") disagree with the declared " + manifest.rowCount() + ", " + manifest.tapeHash().shortForm());
        }
        return new RouteArtifact(manifest, tape);
    }

    public static String formatLaunch(LaunchContract launch) {
        return text(launch(launch));
    }

    public static LaunchContract parseLaunch(String text) throws IOException {
        return launch(COMPACT.readTree(text));
    }

    public static String formatGoals(GoalProgram goals) {
        return text(goals(goals));
    }

    public static GoalProgram parseGoals(String text) throws IOException {
        return goals(COMPACT.readTree(text));
    }

    private static String text(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node) + "\n";
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ObjectNode manifestNode(RouteManifest manifest) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("format", RouteManifest.FORMAT);
        if (manifest.draft()) root.put("draft", true);
        root.set("launch", launch(manifest.launch()));
        if (!manifest.checks().isEmpty()) root.set("checks", goals(manifest.checks()));
        root.putObject("tape");
        ObjectNode provenance = root.putObject("provenance");
        provenance.put("producer", manifest.provenance().producer());
        provenance.put("engine", manifest.provenance().engine());
        provenance.put("source", manifest.provenance().source());
        ArrayNode notes = provenance.putArray("notes");
        manifest.provenance().notes().forEach(notes::add);
        ArrayNode stamps = root.putArray("qualification");
        for (QualificationStamp stamp : manifest.qualification()) {
            ObjectNode node = stamps.addObject();
            node.put("scope", stamp.scope().name());
            node.put("passed", stamp.passed());
            stamp.divergedRow().ifPresent(row -> node.put("divergedRow", row));
            if (!stamp.reason().isEmpty()) node.put("reason", stamp.reason());
            node.put("content", stamp.content().sha256());
            node.put("environment", stamp.environment());
            node.put("rows", stamp.rows());
            if(!stamp.coverage().isEmpty()) {
                ArrayNode coverage=node.putArray("coverage");stamp.coverage().forEach(coverage::add);
            }
        }
        ObjectNode nativeNode = root.putObject("native");
        manifest.nativeFacts().entry().ifPresent(entry -> {
            ObjectNode node = nativeNode.putObject("entry");
            node.put("movie", entry.movie());
            node.put("prefixRows", entry.prefixRows());
            node.put("inputHash", entry.inputHash().sha256());
            if (!entry.anchorBoundary().isEmpty()) {
                node.put("engineAnchorRows", entry.engineAnchorRows());
                node.put("anchorBoundary", entry.anchorBoundary());
            }
        });
        if (!manifest.nativeFacts().exportBlocker().isEmpty())
            nativeNode.put("exportBlocker", manifest.nativeFacts().exportBlocker());
        if(!manifest.nativeFacts().inlets().isEmpty()) {
            ArrayNode inlets=nativeNode.putArray("inlets");
            for(var inlet:manifest.nativeFacts().inlets()) {
                var n=inlets.addObject();var a=inlet.anchor();
                n.put("engineFromRows",inlet.engineFromRows()).put("movie",a.movie())
                    .put("prefixRows",a.prefixRows()).put("inputHash",a.inputHash().sha256())
                    .put("engineAnchorRows",a.engineAnchorRows()).put("anchorBoundary",a.anchorBoundary());
            }
        }
        ArrayNode lag = nativeNode.putArray("lag");
        for (NativeFacts.LagFact fact : manifest.nativeFacts().lag()) {
            ObjectNode node = lag.addObject();
            node.put("beforeRow", fact.beforeRow());
            node.put("harness", fact.harness());
            node.put("content", fact.content().sha256());
        }
        return root;
    }

    /** A parsed manifest plus the script file name it binds. */
    public record ParsedManifest(RouteManifest manifest, String scriptName) { }

    /** Parses manifest text that binds a script file beside it. */
    public static ParsedManifest parseManifest(String text) throws IOException {
        JsonNode root = COMPACT.readTree(text);
        RouteManifest manifest = manifest(root, false);
        return new ParsedManifest(manifest, text(root.get("tape"), "script"));
    }

    private static RouteManifest manifest(JsonNode root, boolean inline) throws IOException {
        object(root, "manifest", Set.of("format", "draft", "launch", "checks", "tape", "provenance", "qualification",
                "native"), Set.of("format", "launch", "tape"));
        if (!RouteManifest.FORMAT.equals(text(root, "format"))) {
            throw new IOException("unsupported route format: " + root.get("format"));
        }
        LaunchContract launch = launch(root.get("launch"));
        JsonNode tape = root.get("tape");
        String rowsKey = inline ? "inline" : "script";
        object(tape, "tape", Set.of(rowsKey, "rows", "sha256"), Set.of(rowsKey, "rows", "sha256"));
        GoalProgram checks = root.has("checks") ? goals(root.get("checks")) : GoalProgram.NONE;
        boolean draft = root.has("draft") && bool(root, "draft");
        Provenance provenance = new Provenance("unknown", "", "", List.of());
        if (root.has("provenance")) {
            JsonNode node = root.get("provenance");
            object(node, "provenance", Set.of("producer", "engine", "source", "notes"), Set.of("producer"));
            var notes = new ArrayList<String>();
            if (node.has("notes")) for (JsonNode note : array(node.get("notes"), "notes")) notes.add(note.asText());
            provenance = new Provenance(text(node, "producer"), optionalText(node, "engine"),
                    optionalText(node, "source"), notes);
        }
        var stamps = new ArrayList<QualificationStamp>();
        if (root.has("qualification")) {
            for (JsonNode node : array(root.get("qualification"), "qualification")) {
                object(node, "qualification stamp", Set.of("scope", "passed", "divergedRow", "reason", "content",
                        "environment", "rows", "coverage"), Set.of("scope", "passed", "content", "environment", "rows"));
                var coverage=new ArrayList<String>();
                if(node.has("coverage")) for(JsonNode c:array(node.get("coverage"),"coverage")) {
                    if(!c.isTextual())throw new IOException("qualification coverage must name checks");
                    coverage.add(c.asText());
                }
                stamps.add(new QualificationStamp(ReplayScope.valueOf(text(node, "scope")), bool(node, "passed"),
                        node.has("divergedRow") ? java.util.OptionalInt.of(integer(node, "divergedRow"))
                                : java.util.OptionalInt.empty(), optionalText(node, "reason"),
                        new ContentHash(text(node, "content")), text(node, "environment"), integer(node, "rows"),coverage));
            }
        }
        NativeFacts facts = NativeFacts.NONE;
        if (root.has("native")) {
            JsonNode node = root.get("native");
            object(node, "native", Set.of("entry", "lag", "exportBlocker", "inlets"), Set.of());
            Optional<NativeFacts.NativeEntry> entry = Optional.empty();
            if (node.has("entry")) {
                JsonNode e = node.get("entry");
                object(e, "native entry", Set.of("movie", "prefixRows", "inputHash", "engineAnchorRows", "anchorBoundary"), Set.of("movie", "prefixRows", "inputHash"));
                if (e.has("engineAnchorRows") != e.has("anchorBoundary"))
                    throw new IOException("native entry needs both engineAnchorRows and anchorBoundary");
                entry = Optional.of(new NativeFacts.NativeEntry(text(e, "movie"), integer(e, "prefixRows"),
                        new ContentHash(text(e, "inputHash")), e.has("engineAnchorRows") ? integer(e, "engineAnchorRows") : 0,
                        optionalText(e, "anchorBoundary")));
            }
            var lag = new ArrayList<NativeFacts.LagFact>();
            if (node.has("lag")) {
                for (JsonNode l : array(node.get("lag"), "lag")) {
                    object(l, "lag fact", Set.of("beforeRow", "harness", "content"), Set.of("beforeRow", "harness", "content"));
                    lag.add(new NativeFacts.LagFact(integer(l, "beforeRow"), text(l, "harness"),
                            new ContentHash(text(l, "content"))));
                }
            }
            var inlets=new ArrayList<NativeFacts.NativeInlet>();
            if(node.has("inlets")) for(var n:array(node.get("inlets"),"native inlets")) {
                var fields=Set.of("engineFromRows","movie","prefixRows","inputHash","engineAnchorRows","anchorBoundary");
                object(n,"native inlet",fields,fields);
                inlets.add(new NativeFacts.NativeInlet(integer(n,"engineFromRows"),new NativeFacts.NativeEntry(
                    text(n,"movie"),integer(n,"prefixRows"),new ContentHash(text(n,"inputHash")),
                    integer(n,"engineAnchorRows"),text(n,"anchorBoundary"))));
            }
            facts = new NativeFacts(entry, lag, optionalText(node, "exportBlocker"),inlets);
        }
        return new RouteManifest(launch, new ContentHash(text(tape, "sha256")), integer(tape, "rows"), checks, draft,
                provenance, stamps, facts);
    }

    static ContentHash contentHash(LaunchContract launch, GoalProgram checks, InputTape tape) {
        try {
            String canonical = COMPACT.writeValueAsString(launch(launch)) + "\n"
                    + COMPACT.writeValueAsString(goals(checks)) + "\n" + tape.hash().sha256() + "\n" + tape.size();
            return ContentHash.of(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ObjectNode launch(LaunchContract launch) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode rom = node.putObject("rom");
        rom.put("game", launch.rom().game());
        rom.put("crc32", launch.rom().crc32Hex());
        rom.put("sha1", launch.rom().sha1());
        ObjectNode inlet = node.putObject("inlet");
        switch (launch.inlet()) {
            case Inlet.Title ignored -> inlet.put("kind", "title");
            case Inlet.ColdLevel cold -> {
                inlet.put("kind", "cold-level");
                inlet.put("zone", cold.level().zone());
                inlet.put("act", cold.level().act());
                inlet.put("showTitleCard", cold.showTitleCard());
            }
            case Inlet.Scene scene -> {
                inlet.put("kind", "scene");
                inlet.put("sceneSha256", scene.sceneSha256());
                inlet.put("startFrame", scene.startFrame());
            }
        }
        ObjectNode roster = node.putObject("roster");
        roster.put("main", launch.roster().main());
        roster.put("sidekick", launch.roster().sidekick());
        node.put("width", launch.width());
        node.put("donor", launch.donor());
        ObjectNode initial = node.putObject("initial");
        initial.put("vIntStart", launch.initial().vIntStart());
        Seeds seeds = launch.initial().seeds();
        if (!seeds.none()) {
            ObjectNode s = initial.putObject("seeds");
            if (seeds.startX() != null) { s.put("startX", seeds.startX()); s.put("startY", seeds.startY()); }
            if (seeds.starPost()) s.put("starPost", true);
            if (seeds.rings() != null) s.put("rings", seeds.rings());
            if (seeds.emeralds() != null) s.put("emeralds", seeds.emeralds());
            if (seeds.reverseGravity()) s.put("reverseGravity", true);
            if (seeds.endOfLevel()) s.put("endOfLevel", true);
            if (seeds.cameraXSub() != null) s.put("cameraXSub", seeds.cameraXSub());
        }
        return node;
    }

    private static LaunchContract launch(JsonNode node) throws IOException {
        if (node == null) throw new IOException("launch must be an object");
        object(node, "launch", Set.of("rom", "inlet", "roster", "width", "donor", "initial"),
                Set.of("rom", "inlet", "roster", "width", "initial"));
        JsonNode rom = node.get("rom");
        object(rom, "rom", Set.of("game", "crc32", "sha1"), Set.of("game", "crc32", "sha1"));
        String game = text(rom, "game");
        var identity = new RomIdentity(game, Long.parseLong(text(rom, "crc32"), 16), text(rom, "sha1"));
        JsonNode inlet = node.get("inlet");
        Inlet parsedInlet = switch (text(inlet, "kind")) {
            case "title" -> {
                object(inlet, "inlet", Set.of("kind"), Set.of("kind"));
                yield new Inlet.Title();
            }
            case "cold-level" -> {
                object(inlet, "inlet", Set.of("kind", "zone", "act", "showTitleCard"), Set.of("kind", "zone", "act"));
                yield new Inlet.ColdLevel(new LevelId(game, integer(inlet, "zone"), integer(inlet, "act")),
                        inlet.has("showTitleCard") && bool(inlet, "showTitleCard"));
            }
            case "scene" -> {
                object(inlet, "inlet", Set.of("kind", "sceneSha256", "startFrame"), Set.of("kind", "sceneSha256", "startFrame"));
                yield new Inlet.Scene(text(inlet, "sceneSha256"), integer(inlet, "startFrame"));
            }
            default -> throw new IOException("unknown inlet kind: " + inlet.get("kind"));
        };
        JsonNode roster = node.get("roster");
        object(roster, "roster", Set.of("main", "sidekick"), Set.of("main"));
        JsonNode initial = node.get("initial");
        object(initial, "initial", Set.of("vIntStart", "seeds"), Set.of("vIntStart"));
        Seeds seeds = Seeds.NONE;
        if (initial.has("seeds")) {
            JsonNode s = initial.get("seeds");
            object(s, "seeds", Set.of("startX", "startY", "starPost", "rings", "emeralds", "reverseGravity",
                    "endOfLevel", "cameraXSub"), Set.of());
            seeds = new Seeds(optionalInt(s, "startX"), optionalInt(s, "startY"), s.has("starPost") && bool(s, "starPost"),
                    optionalInt(s, "rings"), s.has("emeralds") ? text(s, "emeralds") : null,
                    s.has("reverseGravity") && bool(s, "reverseGravity"), s.has("endOfLevel") && bool(s, "endOfLevel"),
                    optionalInt(s, "cameraXSub"));
        }
        return new LaunchContract(identity, parsedInlet, new Roster(text(roster, "main"), optionalText(roster, "sidekick")),
                integer(node, "width"), node.has("donor") ? text(node, "donor") : "off",
                new InitialState(integer(initial, "vIntStart"), seeds));
    }

    private static ObjectNode goals(GoalProgram program) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set("guard", guard(program.routeGuard()));
        if (program.startRow() != 0) node.put("startRow", program.startRow());
        ArrayNode legs = node.putArray("legs");
        for (Leg leg : program.legs()) {
            ObjectNode l = legs.addObject();
            l.put("id", leg.id());
            l.put("maxRows", leg.maxRows());
            l.set("guard", guard(leg.guard()));
            ObjectNode goal = l.putObject("goal");
            switch (leg.goal()) {
                case RouteGoal.Achieve achieve -> {
                    goal.put("kind", "achieve");
                    goal.set("condition", predicate(achieve.condition()));
                }
                case RouteGoal.Wait wait -> { goal.put("kind", "wait"); goal.put("rows", wait.rows()); }
                case RouteGoal.EnterBoundary ignored -> goal.put("kind", "enter-boundary");
            }
        }
        return node;
    }

    private static GoalProgram goals(JsonNode node) throws IOException {
        object(node, "checks", Set.of("guard", "legs", "startRow"), Set.of("guard", "legs"));
        var legs = new ArrayList<Leg>();
        for (JsonNode l : array(node.get("legs"), "legs")) {
            object(l, "leg", Set.of("id", "maxRows", "guard", "goal"), Set.of("id", "maxRows", "guard", "goal"));
            JsonNode goal = l.get("goal");
            RouteGoal parsed = switch (text(goal, "kind")) {
                case "achieve" -> {
                    object(goal, "goal", Set.of("kind", "condition"), Set.of("kind", "condition"));
                    yield new RouteGoal.Achieve(predicate(goal.get("condition")));
                }
                case "wait" -> {
                    object(goal, "goal", Set.of("kind", "rows"), Set.of("kind", "rows"));
                    yield new RouteGoal.Wait(integer(goal, "rows"));
                }
                case "enter-boundary" -> {
                    object(goal, "goal", Set.of("kind"), Set.of("kind"));
                    yield new RouteGoal.EnterBoundary();
                }
                default -> throw new IOException("unknown goal kind: " + goal.get("kind"));
            };
            legs.add(new Leg(text(l, "id"), parsed, guard(l.get("guard")), integer(l, "maxRows")));
        }
        return new GoalProgram(guard(node.get("guard")), legs, node.has("startRow") ? integer(node, "startRow") : 0);
    }

    private static ObjectNode guard(Guard guard) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("allowHurt", guard.allowHurt());
        node.put("allowRingLoss", guard.allowRingLoss());
        node.put("allowDeath", guard.allowDeath());
        node.put("allowBoundary", guard.allowBoundary());
        guard.holds().ifPresent(holds -> node.set("holds", predicate(holds)));
        return node;
    }

    private static Guard guard(JsonNode node) throws IOException {
        Set<String> keys = Set.of("allowHurt", "allowRingLoss", "allowDeath", "allowBoundary");
        object(node, "guard", Set.of("allowHurt", "allowRingLoss", "allowDeath", "allowBoundary", "holds"), keys);
        return new Guard(bool(node, "allowHurt"), bool(node, "allowRingLoss"), bool(node, "allowDeath"),
                bool(node, "allowBoundary"), node.has("holds") ? java.util.Optional.of(predicate(node.get("holds")))
                        : java.util.Optional.empty());
    }

    private static ObjectNode predicate(SamplePredicate predicate) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (predicate) {
            case SamplePredicate.FactsEqual equal -> {
                node.put("kind","facts-equal");node.put("left",equal.left());node.put("right",equal.right());
            }
            case SamplePredicate.FactEquals fact -> {
                node.put("kind", "fact-equals");
                node.put("key", fact.key());
                node.put("value", fact.value());
            }
            case SamplePredicate.PlayerIn in -> {
                node.put("kind", "player-in");
                node.put("player", in.player().name());
                ArrayNode region = node.putArray("region");
                region.add(in.region().left()).add(in.region().top()).add(in.region().right()).add(in.region().bottom());
            }
            case SamplePredicate.Settled settled -> {
                node.put("kind", "settled");
                node.put("player", settled.player().name());
                node.put("maxSpeed", settled.maxSpeed());
            }
            case SamplePredicate.LevelIs level -> {
                node.put("kind", "level-is");
                node.put("zone", level.zone());
                node.put("act", level.act());
            }
            case SamplePredicate.All all -> {
                node.put("kind", "all");
                ArrayNode clauses = node.putArray("clauses");
                for (SamplePredicate clause : all.clauses()) clauses.add(predicate(clause));
            }
            case SamplePredicate.Riding riding -> {
                node.put("kind", "riding");
                node.put("player", riding.player().name());
                node.set("target", target(riding.target()));
            }
            case SamplePredicate.Attached attached -> {
                node.put("kind", "attached");
                node.put("player", attached.player().name());
                node.set("target", target(attached.target()));
                node.put("interaction", attached.kind().name());
            }
            case SamplePredicate.PhaseIs phase -> {
                node.put("kind", "phase-is");
                node.set("target", target(phase.target()));
                node.put("phase", phase.phase());
            }
            case SamplePredicate.Defeated defeated -> {
                node.put("kind", "defeated");
                node.set("target", target(defeated.target()));
            }
        }
        return node;
    }

    private static ObjectNode target(ObjectTarget target) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (target) {
            case ObjectTarget.Placement p -> {
                node.put("kind", "placement");
                node.put("zone", p.key().zone());
                node.put("act", p.key().act());
                node.put("layoutIndex", p.key().layoutIndex());
                node.put("objectId", p.key().objectId());
                node.put("subtype", p.key().subtype());
            }
            case ObjectTarget.Instance i -> {
                node.put("kind", "instance");
                node.put("epoch", i.key().epoch());
                node.put("slot", i.key().slot());
                node.put("generation", i.key().generation());
                node.put("spawnId", i.key().spawnId());
                node.put("dynamicId", i.key().dynamicId());
                node.put("refKind", i.key().kind());
            }
            case ObjectTarget.Typed t -> {
                node.put("kind", "typed");
                node.put("typeKey", t.typeKey());
                node.put("capability", t.capability().name());
                t.region().ifPresent(r -> node.putArray("region").add(r.left()).add(r.top()).add(r.right()).add(r.bottom()));
            }
        }
        return node;
    }

    private static ObjectTarget target(JsonNode node) throws IOException {
        return switch (text(node, "kind")) {
            case "placement" -> {
                Set<String> keys = Set.of("kind", "zone", "act", "layoutIndex", "objectId", "subtype");
                object(node, "target", keys, keys);
                yield ObjectTarget.placement(integer(node, "zone"), integer(node, "act"), integer(node, "layoutIndex"),
                        integer(node, "objectId"), integer(node, "subtype"));
            }
            case "instance" -> {
                Set<String> keys = Set.of("kind", "epoch", "slot", "generation", "spawnId", "dynamicId", "refKind");
                object(node, "target", keys, keys);
                if (!node.get("epoch").isIntegralNumber()) throw new IOException("epoch must be an integer");
                yield new ObjectTarget.Instance(new InstanceKey(node.get("epoch").asLong(), integer(node, "slot"),
                        integer(node, "generation"), integer(node, "spawnId"), integer(node, "dynamicId"),
                        text(node, "refKind")));
            }
            case "typed" -> {
                object(node, "target", Set.of("kind", "typeKey", "capability", "region"), Set.of("kind", "typeKey", "capability"));
                java.util.Optional<Region> region = java.util.Optional.empty();
                if (node.has("region")) {
                    ArrayNode r = array(node.get("region"), "region");
                    if (r.size() != 4) throw new IOException("a region is [left, top, right, bottom]");
                    region = java.util.Optional.of(new Region(r.get(0).asInt(), r.get(1).asInt(), r.get(2).asInt(), r.get(3).asInt()));
                }
                yield new ObjectTarget.Typed(text(node, "typeKey"), Capability.valueOf(text(node, "capability")), region);
            }
            default -> throw new IOException("unknown target kind: " + node.get("kind"));
        };
    }

    private static SamplePredicate predicate(JsonNode node) throws IOException {
        return switch (text(node, "kind")) {
            case "facts-equal" -> {
                object(node,"condition",Set.of("kind","left","right"),Set.of("kind","left","right"));
                yield new SamplePredicate.FactsEqual(text(node,"left"),text(node,"right"));
            }
            case "fact-equals" -> {
                object(node, "condition", Set.of("kind", "key", "value"), Set.of("kind", "key", "value"));
                yield new SamplePredicate.FactEquals(text(node, "key"), integer(node, "value"));
            }
            case "player-in" -> {
                object(node, "condition", Set.of("kind", "player", "region"), Set.of("kind", "player", "region"));
                ArrayNode r = array(node.get("region"), "region");
                if (r.size() != 4) throw new IOException("a region is [left, top, right, bottom]");
                yield new SamplePredicate.PlayerIn(PlayerSlot.valueOf(text(node, "player")),
                        new Region(r.get(0).asInt(), r.get(1).asInt(), r.get(2).asInt(), r.get(3).asInt()));
            }
            case "settled" -> {
                object(node, "condition", Set.of("kind", "player", "maxSpeed"), Set.of("kind", "player", "maxSpeed"));
                yield new SamplePredicate.Settled(PlayerSlot.valueOf(text(node, "player")), integer(node, "maxSpeed"));
            }
            case "level-is" -> {
                object(node, "condition", Set.of("kind", "zone", "act"), Set.of("kind", "zone", "act"));
                yield new SamplePredicate.LevelIs(integer(node, "zone"), integer(node, "act"));
            }
            case "all" -> {
                object(node, "condition", Set.of("kind", "clauses"), Set.of("kind", "clauses"));
                var clauses = new ArrayList<SamplePredicate>();
                for (JsonNode clause : array(node.get("clauses"), "clauses")) clauses.add(predicate(clause));
                yield new SamplePredicate.All(clauses);
            }
            case "riding" -> {
                object(node, "condition", Set.of("kind", "player", "target"), Set.of("kind", "player", "target"));
                yield new SamplePredicate.Riding(PlayerSlot.valueOf(text(node, "player")), target(node.get("target")));
            }
            case "attached" -> {
                Set<String> keys = Set.of("kind", "player", "target", "interaction");
                object(node, "condition", keys, keys);
                yield new SamplePredicate.Attached(PlayerSlot.valueOf(text(node, "player")), target(node.get("target")),
                        InteractionKind.valueOf(text(node, "interaction")));
            }
            case "phase-is" -> {
                object(node, "condition", Set.of("kind", "target", "phase"), Set.of("kind", "target", "phase"));
                yield new SamplePredicate.PhaseIs(target(node.get("target")), text(node, "phase"));
            }
            case "defeated" -> {
                object(node, "condition", Set.of("kind", "target"), Set.of("kind", "target"));
                yield new SamplePredicate.Defeated(target(node.get("target")));
            }
            default -> throw new IOException("unknown condition kind: " + node.get("kind"));
        };
    }

    private static void object(JsonNode node, String what, Set<String> allowed, Set<String> required) throws IOException {
        if (node == null || !node.isObject()) throw new IOException(what + " must be an object");
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.contains(name)) throw new IOException("unknown " + what + " key: " + name);
        }
        for (String name : required) {
            if (!node.has(name) || node.get(name).isNull()) throw new IOException(what + " is missing " + name);
        }
    }

    private static ArrayNode array(JsonNode node, String what) throws IOException {
        if (!(node instanceof ArrayNode array)) throw new IOException(what + " must be an array");
        return array;
    }

    private static String text(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) throw new IOException(key + " must be a string");
        return value.asText();
    }

    private static String optionalText(JsonNode node, String key) throws IOException {
        return node.has(key) ? text(node, key) : "";
    }

    private static int integer(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) throw new IOException(key + " must be an integer");
        return value.asInt();
    }

    private static Integer optionalInt(JsonNode node, String key) throws IOException {
        return node.has(key) ? integer(node, key) : null;
    }

    private static boolean bool(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.isBoolean()) throw new IOException(key + " must be a boolean");
        return value.asBoolean();
    }
}
