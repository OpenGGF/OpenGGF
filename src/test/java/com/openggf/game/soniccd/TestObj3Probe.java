package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Diagnostic, not a regression test: replays a rehost fixture and prints the engine's player and the live objects near it
 * on chosen rows, to set beside the trace's {@code object_near} events. Origin: objects-3 lane, item 5 of its brief
 * (first diverging rows whose frontier label is "unhosted object"). Runs only with
 * {@code -Dobj3.probe=<payload>:<firstRow>:<lastRow>[,...]} (rows are trace frame numbers, decimal) and reads the fixture
 * from {@code -Dsoniccd.fixtures.root} (default: the media root's rehost-fixtures). Output lines start {@code [obj3-probe]}.
 */
class TestObj3Probe {
    @TempDir
    Path tempDir;

    @Test
    void probe() throws Exception {
        String spec = System.getProperty("obj3.probe", "");
        Assumptions.assumeFalse(spec.isBlank(), "diagnostic only: set -Dobj3.probe=<payload>:<from>:<to>");
        String configured = System.getProperty("soniccd.fixtures.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : CdTestMedia.nativeFile("rehost-fixtures/README.md").getParent();
        for (String one : spec.split(",")) {
            String[] part = one.trim().split(":");
            CdStagePayload payload = CdStagePayload.valueOf(part[0]);
            int from = Integer.parseInt(part[1]);
            int to = Integer.parseInt(part[2]);
            // An optional fourth part names the fixtures root for this entry (a path under ~/Downloads/Sonic-CD/native).
            Path entryRoot = part.length > 3 ? CdTestMedia.nativeFile(part[3] + "/" + payload.name() + "/trace/physics.csv")
                    .getParent().getParent().getParent() : root;
            java.util.Set<String> dump = java.util.Set.of(System.getProperty("obj3.dump", "").split(","));
            TestEnvironment.resetAll();
            EngineContext previous = EngineServices.current();
            try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
                var fixture = CdFixtureReplay.Fixture.under(entryRoot, payload);
                CdFixtureReplay.replay(disc, tempDir.resolve(payload.name()), fixture, to, false, -1, -1,
                        new CdFixtureReplay.Hooks() {
                            @Override
                            public void row(int row, AbstractPlayableSprite sonic) {
                                if (row < from || row > to) return;
                                StringBuilder line = new StringBuilder("[obj3-probe] " + payload + " row " + row
                                        + " player x=" + Integer.toHexString(sonic.getCentreX())
                                        + " y=" + Integer.toHexString(sonic.getCentreY())
                                        + " xs=" + Integer.toHexString(sonic.getXSpeed() & 0xFFFF)
                                        + " ys=" + Integer.toHexString(sonic.getYSpeed() & 0xFFFF)
                                        + " air=" + (sonic.getAir() ? 1 : 0) + " onObj=" + (sonic.isOnObject() ? 1 : 0) + " |");
                                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                    if (o.isDestroyed() || o.getSpawn() == null) continue;
                                    if (Math.abs(o.getX() - sonic.getCentreX()) > 0x90
                                            || Math.abs(o.getY() - sonic.getCentreY()) > 0x70) continue;
                                    if (!dump.isEmpty() && dump.contains(o.getClass().getSimpleName())) {
                                        System.out.println("[obj3-dump] " + payload + " row " + row + " " + describe(o)
                                                + " remembered=" + GameServices.level().getObjectManager().isRemembered(o.getSpawn())
                                                + " destroyed=" + o.isDestroyed());
                                    }
                                    line.append(' ').append(o.getClass().getSimpleName())
                                            .append("#").append(Integer.toHexString(o.getSpawn().objectId()))
                                            .append("s").append(o instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -1)
                                            .append('@').append(Integer.toHexString(o.getX()))
                                            .append(',').append(Integer.toHexString(o.getY()));
                                }
                                if (Boolean.getBoolean("obj8.slots")) {
                                    // Every dynamic slot, to set beside the trace's slot_dump events (levelFrames = the engine's counter).
                                    StringBuilder slots = new StringBuilder("[obj8-slots] " + payload + " lf "
                                            + GameServices.level().getFrameCounter() + ":");
                                    java.util.TreeMap<Integer, String> bySlot = new java.util.TreeMap<>();
                                    for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                        if (o instanceof com.openggf.level.objects.AbstractObjectInstance a && !a.isDestroyed()
                                                && a.getSlotIndex() >= 32 && o.getSpawn() != null) {
                                            bySlot.put(a.getSlotIndex(), String.format("%02X@%X,%X", o.getSpawn().objectId(), o.getX(), o.getY()));
                                        }
                                    }
                                    bySlot.forEach((slot, text) -> slots.append(' ').append(slot).append('=').append(text));
                                    System.out.println(slots);
                                }
                                if (Boolean.getBoolean("obj6.water")) {
                                    var water = GameServices.waterOrNull();
                                    var lm = GameServices.level();
                                    if (water != null && lm != null) {
                                        line.append(" | water gameplay=" + Integer.toHexString(water.getGameplayWaterLevelY(lm.getFeatureZoneId(), lm.getFeatureActId()))
                                                + " base=" + Integer.toHexString(water.getWaterLevelY(lm.getFeatureZoneId(), lm.getFeatureActId())));
                                    }
                                }
                                String floor = System.getProperty("obj6.floor", "");
                                if (!floor.isBlank()) {
                                    String[] f = floor.split(":");
                                    var r = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(Integer.parseInt(f[0], 16),
                                            Integer.parseInt(f[1], 16), Integer.parseInt(f[2], 16));
                                    line.append(" | floor(" + floor + ")=" + Integer.toHexString(r.distance() & 0xFF) + " hit=" + r.hasCollision());
                                }
                                System.out.println(line);
                            }
                        });
            } finally {
                GroundSensor.setLevelManager(null);
                SessionManager.clear();
                GameModuleRegistry.reset();
                if (previous != null) EngineServices.configure(previous);
                TestEnvironment.resetAll();
            }
        }
    }

    /**
     * Diagnostic: replays a recorded time-travel movie ({@code -Dobj3.warp=<dir under native/rehost-timetravel>}) and prints the
     * engine's time-travel state whenever it changes, with the player, for the rows from {@code obj3.warp.from} (default 0).
     * Origin: objects-6 lane, failure of TestSonicCdTimeTravel.recordedPalmtreePanicWarpsMatchNativeAsFarAsTheyReplay.
     */
    @Test
    void warp() throws Exception {
        String name = System.getProperty("obj3.warp", "");
        Assumptions.assumeFalse(name.isBlank(), "diagnostic only: set -Dobj3.warp=present-to-future");
        int from = Integer.getInteger("obj3.warp.from", 0);
        Path dir = CdTestMedia.nativeFile("rehost-timetravel/" + name + "/probe-frames.csv").getParent();
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R11A, dir.resolve("trace"), dir.resolve("movie.bk2"));
        TestEnvironment.resetAll();
        EngineContext previous = EngineServices.current();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            String[] last = {""};
            CdFixtureReplay.replay(disc, tempDir.resolve("warp"), fixture, Integer.MAX_VALUE, false, -1, -1,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(GameModuleRegistry.getCurrent());
                            String state = "warping=" + tt.warping() + " dir=" + tt.warpDirection() + " timer=" + Integer.toHexString(tt.warpTimer())
                                    + " ready=" + tt.warpReady() + " pending=" + (tt.pendingWarp() != null) + " zone=" + tt.timeZone();
                            if (row >= from && !state.equals(last[0])) {
                                System.out.println("[obj6-warp] row " + row + " " + state + " player x=" + Integer.toHexString(sonic.getCentreX())
                                        + " y=" + Integer.toHexString(sonic.getCentreY()));
                            }
                            last[0] = state;
                        }
                    });
        } finally {
            GroundSensor.setLevelManager(null);
            SessionManager.clear();
            GameModuleRegistry.reset();
            if (previous != null) EngineServices.configure(previous);
            TestEnvironment.resetAll();
        }
    }

    /** The object's int/boolean/long fields up its class chain, as {@code name=value}. */
    private static String describe(Object o) {
        StringBuilder text = new StringBuilder(o.getClass().getSimpleName()).append(':');
        for (Class<?> c = o.getClass(); c != null && c != Object.class
                && !c.getName().startsWith("com.openggf.level.objects.Abstract"); c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t != int.class && t != boolean.class && t != long.class && t != short.class && t != byte.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    text.append(' ').append(f.getName()).append('=')
                            .append(v instanceof Integer i ? Integer.toHexString(i) : v);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return text.toString();
    }

    /**
     * Lists the layout's object IDs the stage's registry leaves as {@code PlaceholderObjectInstance} (unhosted), with
     * their counts: {@code -Dobj3.census=R11A,R11B,...}.
     */
    @Test
    void census() throws Exception {
        String spec = System.getProperty("obj3.census", "");
        Assumptions.assumeFalse(spec.isBlank(), "diagnostic only: set -Dobj3.census=<payload>[,...]");
        for (String name : spec.split(",")) {
            TestEnvironment.resetAll();
            EngineContext previous = EngineServices.current();
            try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
                CdStagePayload payload = CdStagePayload.valueOf(name.trim());
                CdFixtureReplay.boot(tempDir.resolve("c" + name.trim()), disc, payload);
                var registry = GameModuleRegistry.getCurrent().createObjectRegistry();
                java.util.TreeMap<Integer, Integer> missing = new java.util.TreeMap<>();
                java.util.TreeMap<Integer, Integer> hosted = new java.util.TreeMap<>();
                for (var spawn : GameServices.level().getCurrentLevel().getObjects()) {
                    boolean placeholder;
                    try {
                        placeholder = registry.create(spawn) instanceof com.openggf.level.objects.PlaceholderObjectInstance;
                    } catch (RuntimeException e) {
                        placeholder = false;
                    }
                    (placeholder ? missing : hosted).merge(spawn.objectId(), 1, Integer::sum);
                }
                String ids = System.getProperty("obj3.census.ids", "");
                if (!ids.isBlank()) {
                    var wanted = java.util.Arrays.stream(ids.split(",")).map(t -> Integer.parseInt(t.trim(), 16)).toList();
                    java.util.TreeMap<String, Integer> kinds = new java.util.TreeMap<>();
                    for (var spawn : GameServices.level().getCurrentLevel().getObjects()) {
                        if (wanted.contains(spawn.objectId())) {
                            kinds.merge(Integer.toHexString(spawn.objectId()) + "/" + Integer.toHexString(spawn.subtype())
                                    + "/" + Integer.toHexString(spawn.respawnTracked() ? 1 : 0), 1, Integer::sum);
                        }
                    }
                    System.out.println("[obj3-census-subtypes] " + name.trim() + " " + kinds);
                }
                System.out.println("[obj3-census] " + name.trim() + " unhosted " + hex(missing) + " hosted " + hex(hosted));
            } finally {
                GroundSensor.setLevelManager(null);
                SessionManager.clear();
                GameModuleRegistry.reset();
                if (previous != null) EngineServices.configure(previous);
                TestEnvironment.resetAll();
            }
        }
    }

    private static String hex(java.util.Map<Integer, Integer> counts) {
        StringBuilder text = new StringBuilder("[");
        counts.forEach((id, n) -> text.append(Integer.toHexString(id)).append('x').append(n).append(' '));
        return text.append(']').toString();
    }

    /** Boots a stage and steps idle frames, then dumps the objects of {@code -Dobj3.dump}: {@code -Dobj3.idle=<payload>:<frames>}. */
    @Test
    void idle() throws Exception {
        String spec = System.getProperty("obj3.idle", "");
        Assumptions.assumeFalse(spec.isBlank(), "diagnostic only: set -Dobj3.idle=<payload>:<frames>");
        String[] part = spec.split(":");
        CdStagePayload payload = CdStagePayload.valueOf(part[0]);
        java.util.Set<String> dump = java.util.Set.of(System.getProperty("obj3.dump", "").split(","));
        TestEnvironment.resetAll();
        EngineContext previous = EngineServices.current();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(tempDir.resolve("idle"), disc, payload);
            var runner = new com.openggf.tests.HeadlessTestRunner(sonic);
            int frames = Integer.parseInt(part[1]);
            if (Boolean.getBoolean("obj3.slots")) {
                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                    if (o instanceof com.openggf.level.objects.AbstractObjectInstance a && a.getSlotIndex() >= 0 && a.getSlotIndex() < 42) {
                        System.out.println("[obj3-slots] " + payload + " slot " + a.getSlotIndex() + " exec " + a.getExecutionSlotIndex() + " "
                                + o.getClass().getSimpleName() + " id=" + (o.getSpawn() == null ? -1 : Integer.toHexString(o.getSpawn().objectId()))
                                + " x=" + Integer.toHexString(o.getX()) + " y=" + Integer.toHexString(o.getY()));
                    }
                }
            }
            String poke = System.getProperty("obj3.poke", "");
            if (!poke.isBlank()) {
                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                    if (!o.getClass().getSimpleName().equals(poke)) continue;
                    try {
                        o.update(0, sonic);
                        System.out.println("[obj3-poke] updated " + describe(o));
                    } catch (Throwable t) {
                        System.out.println("[obj3-poke] threw " + t);
                        t.printStackTrace(System.out);
                    }
                }
            }
            for (int f = 0; f <= frames; f += Math.max(1, frames / 4)) {
                if (f > 0) runner.stepIdleFrames(Math.max(1, frames / 4));
                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                    if (dump.contains(o.getClass().getSimpleName())) {
                        System.out.println("[obj3-idle] " + payload + " +" + f + " " + describe(o));
                    }
                }
            }
        } finally {
            GroundSensor.setLevelManager(null);
            SessionManager.clear();
            GameModuleRegistry.reset();
            if (previous != null) EngineServices.configure(previous);
            TestEnvironment.resetAll();
        }
    }
}
