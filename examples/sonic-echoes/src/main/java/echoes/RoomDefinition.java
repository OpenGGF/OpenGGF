package echoes;

import java.io.*;
import java.util.*;

/** Immutable authoring data. Coordinates are native centre coordinates; camera values are origins. */
public record RoomDefinition(String id, String name, int zone, int act, int left, int right,
        int top, int bottom, int spawnX, int spawnY, int loopFrames, int par, int echoCap,
        List<Placement> objects, List<List<Step>> solution) {
    public record Placement(String kind, int x, int y, int width, int height, String channels, int latch) { }
    public record Step(int frames, String buttons) { }
    public RoomDefinition {
        objects = List.copyOf(objects);
        solution = solution.stream().map(List::copyOf).toList();
        if (echoCap < 1 || echoCap > 5 || loopFrames < 1 || right - left < 384 || top >= bottom || objects.size() > 254)
            throw new IllegalArgumentException("Invalid room bounds/cap/clock: " + id);
        for (var o : objects) {
            if (!Set.of("plate", "gate", "rift", "shard", "native").contains(o.kind()) || o.width() < 1 || o.height() < 1)
                throw new IllegalArgumentException("Invalid placement: " + o);
        }
    }
    public static RoomDefinition load(String id) {
        if (!id.matches("[0-9]+-[0-9]+")) throw new IllegalArgumentException("Invalid room ID");
        Properties p = new Properties();
        try (var stream = RoomDefinition.class.getResourceAsStream("/rooms/" + id + ".properties")) {
            if (stream == null) throw new IllegalArgumentException("Unknown room: " + id);
            p.load(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        var objects = new ArrayList<Placement>();
        for (int i = 0; p.containsKey("object." + i); i++) {
            String[] a = p.getProperty("object." + i).split(",", -1);
            if (a.length != 8) throw new IllegalArgumentException("Expected kind,x,y,width,height,channels,latch,reserved");
            if (!a[7].equals("0")) throw new IllegalArgumentException("Reserved placement field must be zero");
            objects.add(new Placement(a[0], Integer.decode(a[1]), Integer.decode(a[2]), Integer.decode(a[3]),
                    Integer.decode(a[4]), a[5], Integer.decode(a[6])));
        }
        var solutions = new ArrayList<List<Step>>();
        for (int i = 0; p.containsKey("solution." + i); i++) {
            var steps = new ArrayList<Step>();
            for (String part : p.getProperty("solution." + i).split(",")) {
                String[] a = part.trim().split(":");
                int frames = Integer.parseInt(a[0]);
                if (frames < 1 || !a[1].matches("[-RLUDJ]+")) throw new IllegalArgumentException("Invalid input step");
                steps.add(new Step(frames, a[1]));
            }
            solutions.add(steps);
        }
        return new RoomDefinition(id, p.getProperty("name"), n(p,"zone"), n(p,"act"), n(p,"left"), n(p,"right"),
                n(p,"top"), n(p,"bottom"), n(p,"spawnX"), n(p,"spawnY"), n(p,"loopFrames"), n(p,"par"), n(p,"echoCap"), objects, solutions);
    }
    /** Optional presentation-only hint; old room resources remain valid. */
    public String hint() {
        Properties p = new Properties();
        try (var stream = RoomDefinition.class.getResourceAsStream("/rooms/" + id + ".properties")) {
            if (stream != null) p.load(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
        return p.getProperty("hint", "");
    }
    /** Optional independent star route. Empty means the ordinary solution earns the shard star. */
    public List<List<Step>> shardSolution() { return authoredLoops("shardSolution"); }
    /** Negative example used by the Paradox teaching room, never a playback position source. */
    public List<List<Step>> naiveSolution() { return authoredLoops("naive"); }
    private List<List<Step>> authoredLoops(String prefix) {
        Properties p = new Properties();
        try (var stream = RoomDefinition.class.getResourceAsStream("/rooms/" + id + ".properties")) {
            if (stream == null) return List.of();
            p.load(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        var loops = new ArrayList<List<Step>>();
        for (int i=0; p.containsKey(prefix+"."+i); i++) {
            var steps = new ArrayList<Step>();
            for (String part : p.getProperty(prefix+"."+i).split(",")) {
                String[] a = part.trim().split(":");
                int frames = Integer.parseInt(a[0]);
                if (frames < 1 || !a[1].matches("[-RLUDJ]+")) throw new IllegalArgumentException("Invalid input step");
                steps.add(new Step(frames,a[1]));
            }
            loops.add(List.copyOf(steps));
        }
        return List.copyOf(loops);
    }
    private static int n(Properties p, String key) { return Integer.decode(Objects.requireNonNull(p.getProperty(key), key)); }
}
