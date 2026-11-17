package com.openggf.game.soniccd.special;

import com.openggf.game.GameMode;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCdSpecialStageRewind {
    @Test
    void immutableArraysAndInPlaceRestore() throws Exception {
        try (var disc = CdSpecialNative.disc()) {
            var provider = new CdSpecialStageProvider(() -> disc);
            provider.initializeStage(0);
            var adapter = new CdSpecialStageRewindAdapter(provider);
            provider.session().stage().playSound(CdSpecialSound.JUMP);
            var state = adapter.capture();
            String saved = fingerprint(state);
            String liveSaved = liveFingerprint(provider);
            var session = provider.session();
            var sonic = session.stage().sonic();
            session.stage().image()[0] ^= 1;
            session.displayedFloor()[0] ^= 1;
            session.stage().floor().mapView()[0] ^= 1;
            session.screen().tiles[32] ^= 1;
            state.session().collected()[0] ^= 1;
            assertEquals(saved, fingerprint(state));
            adapter.restore(state);
            assertSame(session, provider.session());
            assertSame(sonic, session.stage().sonic());
            assertEquals(liveSaved, liveFingerprint(provider));
            session.stage().image()[0] ^= 1;
            session.stage().sonic().x++;
            session.screen().palette[0] ^= 1;
            assertEquals(saved, fingerprint(state));
            adapter.restore(state);
            assertEquals(liveSaved, liveFingerprint(provider));
            provider.initializeStage(0); // even the same stage number has a different owner
            assertThrows(IllegalStateException.class, () -> adapter.restore(state));
            assertThrows(IllegalStateException.class, adapter::resetForMissingSnapshot);
            // Live rewind keeps a keyframe every 60 frames, about 60 per minute of history.
            long bytes = retainedBytes(state);
            assertTrue(bytes < 200_000, "Snapshot retained bytes: " + bytes + " (budget 200000)");
        }
    }

    @ParameterizedTest
    @CsvSource({"0,false", "4,false", "0,true", "5,true"})
    void replayEveryFrameIncludingFadePacingJumpAndEffects(int stageId, boolean demo) throws Exception {
        TestEnvironment.resetAll();
        List<Integer> emitted = new ArrayList<>();
        com.openggf.game.GameServices.audio().setRequestObserver((kind, id) -> emitted.add(id));
        try (var disc = CdSpecialNative.disc()) {
            var provider = new CdSpecialStageProvider(() -> disc);
            provider.begin(new CdSpecialStageEntry(stageId,
                    demo ? CdSpecialStageFlags.DEMO : CdSpecialStageFlags.NORMAL, 0));
            var adapter = new CdSpecialStageRewindAdapter(provider);
            // Demo SS1 is the shipped successful UFO route. Normal inputs exercise both turn
            // directions and repeated jumps independently of the immutable demo stream.
            int frames = 1800;
            List<String> states = new ArrayList<>();
            List<byte[]> floors = new ArrayList<>();
            List<List<Integer>> sounds = new ArrayList<>();
            List<Integer> checkpoints = new ArrayList<>(List.of(0, 7, 24, 25, 26, 27, 300, 650, 1200, 1650));
            var snapshots = new java.util.LinkedHashMap<Integer, CdSpecialStageProvider.State>();
            boolean savedJump = false, savedUfo = false;
            boolean jumped = false, destroyed = false, lost = false;
            int previousRings = 0;
            for (int frame = 0; frame < frames; frame++) {
                if (jumped && !savedJump) { checkpoints.add(frame); savedJump = true; }
                if (destroyed && !savedUfo) { checkpoints.add(frame); savedUfo = true; }
                if (checkpoints.contains(frame)) snapshots.put(frame, adapter.capture());
                emitted.clear();
                advance(provider, pad(frame));
                var stage = provider.session().stage();
                jumped |= stage.sonic().routine == CdSpecialStageSonic.JUMP;
                destroyed |= stage.ufoCount() < 6;
                lost |= stage.rings() < previousRings;
                previousRings = stage.rings();
                sounds.add(List.copyOf(emitted));
                states.add(liveFingerprint(provider));
                floors.add(provider.session().displayedFloor().clone());
            }
            for (var checkpoint : snapshots.entrySet()) {
                adapter.restore(checkpoint.getValue());
                // Replay a bounded window from every phase; late checkpoints include live
                // effect state, UFO path cursors, and background wave accumulators.
                int end = Math.min(frames, checkpoint.getKey() + 180);
                for (int frame = checkpoint.getKey(); frame < end; frame++) {
                    emitted.clear();
                    advance(provider, pad(frame));
                    assertEquals(sounds.get(frame), emitted, "sounds frame " + frame);
                    assertEquals(states.get(frame), liveFingerprint(provider), "state frame " + frame);
                    assertArrayEquals(floors.get(frame), provider.session().displayedFloor(), "floor frame " + frame);
                }
            }
            if (stageId == 0 && demo) { assertTrue(destroyed); assertTrue(jumped); }
        } finally { TestEnvironment.resetAll(); }
    }

    @Test
    void hazardRingLossAndItsLiveEffectsReplay() throws Exception {
        TestEnvironment.resetAll();
        try (var disc = CdSpecialNative.disc()) {
            var provider = new CdSpecialStageProvider(() -> disc);
            provider.initializeStage(0);
            for (int i = 0; i < 310; i++) advance(provider, 0);
            var stage = provider.session().stage();
            // A bounded terrain fixture: use an actual disc hazard stamp, rather than
            // relying on a pad route accidentally crossing it with rings in hand.
            boolean found = false;
            for (int row = 26; row < 102 && !found; row++) {
                for (int col = 26; col < 102; col++) {
                    int x = col * 32 + 16, y = row * 32 + 16;
                    if ((stage.floor().stampAt(x, y) & 0xFF) == 6) {
                        stage.sonic().x = x << 16; stage.sonic().y = y << 16;
                        found = true; break;
                    }
                }
            }
            assertTrue(found, "disc has a hazard");
            stage.rings = 30;
            stage.sonic().routine = CdSpecialStageSonic.GROUND;
            stage.sonic().flags = 0;
            var adapter = new CdSpecialStageRewindAdapter(provider);
            var before = adapter.capture();
            List<String> expected = new ArrayList<>();
            CdSpecialStageProvider.State midEffects = null;
            for (int i = 0; i < 45; i++) {
                advance(provider, 0);
                expected.add(liveFingerprint(provider));
                if (i == 12) midEffects = adapter.capture();
            }
            assertTrue(stage.rings() < 30, "hazard loses rings");
            adapter.restore(before);
            for (int i = 0; i < 45; i++) {
                advance(provider, 0);
                assertEquals(expected.get(i), liveFingerprint(provider));
            }
            adapter.restore(midEffects);
            for (int i = 13; i < 45; i++) {
                advance(provider, 0);
                assertEquals(expected.get(i), liveFingerprint(provider));
            }
        } finally { TestEnvironment.resetAll(); }
    }

    private static int pad(int frame) {
        return (frame / 120 % 2 == 0 ? 8 : 4) | (frame % 75 < 9 ? 0x10 : 0);
    }

    private static void advance(CdSpecialStageProvider provider, int pad) {
        provider.handleInput(pad, 0);
        provider.update();
    }

    @Test
    void realLiveRewindContextRestoresCdProvider() throws Exception {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        try (var disc = CdSpecialNative.disc()) {
            var provider = new CdSpecialStageProvider(() -> disc);
            provider.initializeStage(0);
            var context = TestEnvironment.activeGameplayMode();
            context.registerSpecialStageAdapter(provider);
            var manager = new LiveRewindManager(config, com.openggf.game.GameServices.audio(), () -> GameMode.SPECIAL_STAGE);
            manager.bindOwner(context, false, GameMode.SPECIAL_STAGE);
            var input = new InputHandler();
            manager.recordExternalFrame(GameMode.SPECIAL_STAGE, false, input);
            assertNotNull(context.getRewindController());
            int first = provider.session().frameCount();
            provider.update();
            manager.recordExternalFrame(GameMode.SPECIAL_STAGE, false, input);
            context.getRewindController().seekTo(0);
            assertEquals(first, provider.session().frameCount());
            context.getPlaybackController().stepForwardOnce();
            assertEquals(first + 1, provider.session().frameCount());
        } finally {
            config.resetToDefaults();
            TestEnvironment.resetAll();
        }
    }

    // Independent of capture(): inspect every live special-package instance field.
    // Identity tracking on the current path terminates owner cycles while hashing shared values.
    private static String liveFingerprint(CdSpecialStageProvider provider) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        hashLive(digest, provider, new IdentityHashMap<>());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hashLive(MessageDigest digest, Object value, Map<Object, Integer> seen) throws Exception {
        if (value == null) { hash(digest, null); return; }
        Class<?> type = value.getClass();
        // The renderer's tile cache is a render cache, reset on restore.
        if (value instanceof com.openggf.game.soniccd.program.CdProgramScreenRenderer) return;
        // Immutable asset bundles and script tables must retain their loaded identities.
        if (value instanceof CdSpecialStageAssets || value instanceof CdSpecialAnimationScripts) {
            hashIdentity(digest, value);
            return;
        }
        // script() allocates each call: compare count, speed and frame contents by value.
        if (value instanceof CdSpecialAnimationScripts.Script script) {
            hash(digest, script);
            return;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            hash(digest, value);
            return;
        }
        Integer previous = seen.get(value);
        if (previous != null) { hash(digest, "cycle"); return; }
        seen.put(value, seen.size());
        hash(digest, type.getName());
        if (type.isArray()) {
            int length = Array.getLength(value);
            hash(digest, length);
            if (value instanceof byte[] bytes) digest.update(bytes);
            else if (type.componentType().isPrimitive()) {
                var bytes = java.nio.ByteBuffer.allocate(length * 8);
                for (int i = 0; i < length; i++) bytes.putLong(((Number) Array.get(value, i)).longValue());
                digest.update(bytes.array());
            } else for (int i = 0; i < length; i++) hashLive(digest, Array.get(value, i), seen);
        } else if (value instanceof Iterable<?> list) {
            for (Object entry : list) hashLive(digest, entry, seen);
        } else if (type.getPackageName().equals("com.openggf.game.soniccd.special")) {
            for (Class<?> owner = type; owner != Object.class; owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || excludedLiveField(field)) continue;
                    field.setAccessible(true);
                    hash(digest, field.getName());
                    if (immutableLiveField(field)) hashIdentity(digest, field.get(value));
                    else hashLive(digest, field.get(value), seen);
                }
            }
        } else {
            // Outside-package values (BitSet and screen sprite records) have value-based text;
            // do not reflect into host services or JDK implementation fields.
            hash(digest, value);
        }
        seen.remove(value);
    }

    private static boolean excludedLiveField(Field field) {
        String owner = field.getDeclaringClass().getSimpleName();
        String name = field.getName();
        return switch (owner) {
            // Host projection, disc supplier and exit callback belong to the engine.
            case "CdSpecialStageProvider" -> List.of("viewport", "disc", "onEnded").contains(name);
            // Per-update scratch: begin() clears before any read; updateShadows() queues after draw3D() and those entries are never drawn.
            case "CdSpecialSprites" -> name.equals("zBuffer");
            // dirty is the renderer upload cache, deliberately invalidated by restore().
            case "CdSpecialStageScreen" -> name.equals("dirty");
            default -> false;
        };
    }

    private static void hashIdentity(MessageDigest digest, Object value) throws Exception {
        if (value == null) { hash(digest, null); return; }
        hash(digest, value.getClass().getName());
        hash(digest, System.identityHashCode(value));
    }

    private static boolean immutableLiveField(Field field) {
        String name = field.getName();
        return switch (field.getDeclaringClass().getSimpleName()) {
            // Demo bytes are immutable disc data; demoCursor is inspected by value.
            case "CdSpecialStageSession" -> name.equals("demo");
            // Physics, animation and distance tables are immutable decoded ROM data.
            case "CdSpecialStage" -> List.of("sineTable", "walkAnimations", "distanceTable", "ufoDistanceFrames").contains(name);
            // Numeric glyph tables are immutable decoded ROM data.
            case "CdSpecialStageScreen" -> List.of("numUpper", "numLower").contains(name);
            // Stamp pixels, types and animation definitions are immutable ROM tables.
            case "CdStampFloor" -> List.of("stampSpace", "stampTypes", "hazardAnim", "fanAnim").contains(name);
            // The sprite program is immutable ROM bytes.
            case "CdSpecialSprites" -> name.equals("program");
            // UFO paths are decoded again on runtime time-UFO spawn, so hash their contents by value.
            case "Ufo" -> false;
            default -> false;
        };
    }

    // Fingerprint every nested state value, not Java record array identity/equality.
    private static String fingerprint(Object value) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        hash(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hash(MessageDigest digest, Object value) throws Exception {
        if (value == null) { digest.update((byte) 0); return; }
        if (value instanceof byte[] bytes) { digest.update(bytes); return; }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) hash(digest, Array.get(value, i));
        } else if (value instanceof Iterable<?> list) {
            for (Object entry : list) hash(digest, entry);
        } else if (value.getClass().isRecord()) {
            for (var component : value.getClass().getRecordComponents()) {
                if (component.getName().equals("owner") || component.getName().equals("scripts")) continue;
                hash(digest, component.getAccessor().invoke(value));
            }
        } else digest.update(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // Explicit layout estimate: 12-byte headers, 4-byte references, 8-byte alignment.
    // ROM tables and the live identity token are shared, so do not charge them per keyframe.
    private static long retainedBytes(Object value) throws Exception {
        if (value == null) return 0;
        if (value.getClass().isArray()) {
            int width = value instanceof byte[] ? 1 : value instanceof short[] ? 2 : 4;
            return align(16L + width * Array.getLength(value));
        }
        if (value instanceof List<?> list) {
            long bytes = 24 + align(16L + 4L * list.size());
            for (Object entry : list) bytes += retainedBytes(entry);
            return bytes;
        }
        if (!value.getClass().isRecord()) return 0;
        long own = 12, children = 0;
        for (var component : value.getClass().getRecordComponents()) {
            own += component.getType() == boolean.class ? 1 : 4;
            if (!component.getType().isPrimitive() && !List.of("owner", "scripts", "path").contains(component.getName()))
                children += retainedBytes(component.getAccessor().invoke(value));
        }
        return align(own) + children;
    }
    private static long align(long bytes) { return (bytes + 7) & ~7L; }
}
