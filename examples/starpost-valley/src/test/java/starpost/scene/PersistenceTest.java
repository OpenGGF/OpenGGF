package starpost.scene;

import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Production persistence owners with refused/throwing stores and no ROM prerequisites. */
class PersistenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"garbage", "future"})
    void rejectedPrimarySurvivesAnUnauthorizedCheckpointAndSceneExit(String kind) throws Exception {
        Store store = new Store();
        String original = kind.equals("future") ? SaveCodec.encode(Game.fresh(new Catalog(), 3, "tails"))
                .replace("version=1", "version=9") : "garbage";
        store.files.put(SaveCodec.FILE, original);
        Shell shell = shell(store);
        assertNull(shell.load());
        shell.game = shell.newGame(7, "sonic");
        assertDoesNotThrow(shell::save);
        assertTrue(original.equals(store.files.get(SaveCodec.FILE)), "a rejected primary has no implicit replacement authority");
        scene(shell).exit(shell.ctx);
        assertTrue(original.equals(store.files.get(SaveCodec.FILE)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedBedtimeRetriesTheExactCheckpointWithoutRepeatingCalendarOrRng(boolean throwing) {
        Store store = new Store();
        Shell shell = shell(store);
        shell.game = shell.newGame(17, "knuckles");
        store.failures = 1;
        store.throwing = throwing;
        DayEndScreen bedtime = new DayEndScreen(false);
        assertDoesNotThrow(() -> bedtime.enter(shell));
        String checkpoint = SaveCodec.encode(shell.game);
        int day = shell.game.calendar.dayNumber();
        shell.game.rings += 7;
        long rng = shell.game.rng.snapshot();
        for (int i = 0; i < 120; i++) shell.update();
        assertTrue(checkpoint.equals(store.files.get(SaveCodec.FILE)), "retry must publish the accepted bedtime checkpoint");
        assertEquals(day, shell.game.calendar.dayNumber());
        assertEquals(rng, shell.game.rng.snapshot());
        assertEquals(2, store.attempts.stream().filter(a -> a.name.equals(SaveCodec.FILE)).count());
    }

    @Test
    void reenteringTheSameBedtimeCannotRepeatItsPayoutOrCalendarTransition() {
        Store store = new Store();
        Shell shell = shell(store);
        shell.game = shell.newGame(29, "sonic");
        DayEndScreen bedtime = new DayEndScreen(false);
        bedtime.enter(shell);
        String accepted = SaveCodec.encode(shell.game);
        int day = shell.game.calendar.dayNumber();
        bedtime.enter(shell);
        assertEquals(day, shell.game.calendar.dayNumber());
        assertTrue(accepted.equals(SaveCodec.encode(shell.game)));
        assertEquals(1, store.attempts.stream().filter(a -> a.name.equals(SaveCodec.FILE)).count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusedOptionsRemainActiveRetryAndShowAnHonestNotice(boolean throwing) throws Exception {
        Store store = new Store();
        Shell shell = shell(store);
        store.failures = 1;
        store.throwing = throwing;
        shell.settings.music = false;
        assertDoesNotThrow(() -> shell.settings.save());
        List<String> text = new ArrayList<>();
        shell.draw(canvas(text));
        assertTrue(String.join(" ", text).toLowerCase().contains("save"), "publication refusal must be visible");
        assertFalse(String.join(" ", text).contains("SAVED"));
        for (int i = 0; i < 120; i++) shell.update();
        assertFalse(Settings.load(store).music);
        assertEquals(2, store.attempts.stream().filter(a -> a.name.equals(Settings.FILE)).count());
        scene(shell).exit(shell.ctx);
    }

    @Test
    void overTargetCapsuleCountRejectsTheWholeFileAndPreservesItsBytes() throws Exception {
        Store store = new Store();
        Shell original = shell(store);
        String edited = SaveCodec.encode(original.newGame(41, "sonic")) + "s.capsule.vault1.rings=100000\n";
        store.files.put(SaveCodec.FILE, edited);
        Shell shell = shell(store);
        Game loaded = shell.load();
        if (loaded != null) {
            shell.game = loaded;
            int wallet = loaded.rings;
            CapsuleMenu menu = vaultMenu(shell);
            shell.in.confirm = true; menu.update(shell); shell.in.consume();
            assertEquals(wallet, loaded.rings, "an edited full bundle must not mint wallet rings through the actual menu");
        }
        assertNull(loaded, "a known bundle count must be bounded by that bundle's target");
        shell.game = shell.newGame(42, "tails");
        shell.save();
        scene(shell).exit(shell.ctx);
        assertTrue(edited.equals(store.files.get(SaveCodec.FILE)), "rejected delivery data must retain its original bytes");
    }

    @Test
    void completedVaultMenuAndDirectDeliveryTakeNothingAndRewardExactlyOnce() {
        Store store = new Store();
        Shell shell = shell(store);
        shell.game = shell.newGame(43, "sonic");
        var capsule = shell.game.section(starpost.core.Capsule.class);
        var chambers = capsule.chambers(shell.catalog);
        var vault = chambers.stream().filter(c -> c.id().equals("vault")).findFirst().orElseThrow();
        shell.game.rings = 42500;
        CapsuleMenu menu = new CapsuleMenu();
        for (int i = 0; i < chambers.indexOf(vault); i++) {
            shell.in.downPressed = true; menu.update(shell); shell.in.consume();
        }
        shell.in.confirm = true; menu.update(shell); shell.in.consume(); // enter the Vault's bundles
        for (int i = 0; i < vault.bundles().size(); i++) {
            shell.in.confirm = true; menu.update(shell); shell.in.consume();
            if (i + 1 < vault.bundles().size()) {
                shell.in.downPressed = true; menu.update(shell); shell.in.consume();
            }
        }
        assertEquals(0, shell.game.rings);
        assertTrue(shell.game.flags.contains("farm_open"));
        assertEquals(starpost.core.Farm.COLUMNS, shell.game.farm.open());
        String complete = SaveCodec.encode(shell.game);
        shell.in.confirm = true; menu.update(shell); shell.in.consume();
        assertTrue(complete.equals(SaveCodec.encode(shell.game)), "confirming a full menu bundle must not mutate game state");
        for (var bundle : vault.bundles()) assertEquals(0, capsule.deliver(shell.game, vault, bundle));
        assertTrue(complete.equals(SaveCodec.encode(shell.game)), "completed direct delivery cannot grant another reward");
    }

    @Test
    void newGameRequiresFinalFarmerConfirmationBeforeReplacingFutureBytes() throws Exception {
        Store store = new Store();
        String future = SaveCodec.encode(Game.fresh(new Catalog(), 51, "tails")).replace("version=1", "version=9");
        store.files.put(SaveCodec.FILE, future);
        Shell shell = shell(store);
        TitleScreen title = new TitleScreen(); title.enter(shell);
        shell.in.confirm = true; title.update(shell);
        assertInstanceOf(ConfirmMenu.class, overlay(shell));
        shell.in.back = true; overlay(shell).update(shell);
        assertFalse(shell.hasOverlay());
        assertTrue(future.equals(store.files.get(SaveCodec.FILE)));
        shell.in.confirm = true; title.update(shell);
        shell.in.confirm = true; overlay(shell).update(shell);
        assertInstanceOf(FarmerSelect.class, overlay(shell));
        shell.in.back = true; overlay(shell).update(shell);
        assertNull(shell.game, "cancelling farmer selection grants no replacement");
        scene(shell).exit(shell.ctx);
        assertTrue(future.equals(store.files.get(SaveCodec.FILE)));
        shell = shell(store); title = new TitleScreen(); title.enter(shell);
        shell.in.confirm = true; title.update(shell);
        shell.in.confirm = true; overlay(shell).update(shell);
        shell.in.confirm = true; overlay(shell).update(shell);
        assertNotNull(shell.game);
        assertTrue(future.equals(store.files.get(SaveCodec.FILE)), "new farms first save at bedtime");
        new DayEndScreen(false).enter(shell);
        assertNotNull(shell.load());
        assertFalse(future.equals(store.files.get(SaveCodec.FILE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"music=broken\n", "music=0\nversion=9\n"})
    void rejectedOptionsRequireTheirOwnExplicitReplacement(String rejected) throws Exception {
        Store store = new Store(); store.files.put(Settings.FILE, rejected);
        Shell shell = shell(store);
        OptionsMenu options = new OptionsMenu(false);
        shell.push(options);
        shell.in.confirm = true; options.update(shell); shell.in.consume();
        assertFalse(shell.settings.music, "accepted choices remain live while rejected bytes are preserved");
        assertTrue(rejected.equals(store.files.get(Settings.FILE)));
        scene(shell).exit(shell.ctx);
        assertTrue(rejected.equals(store.files.get(Settings.FILE)));
        shell = shell(store); options = new OptionsMenu(false); shell.push(options);
        shell.in.confirm = true; options.update(shell); shell.in.consume();
        for (int i = 0; i < 3; i++) {
            shell.in.downPressed = true; options.update(shell); shell.in.consume();
        }
        shell.in.confirm = true; options.update(shell);
        assertInstanceOf(ConfirmMenu.class, overlay(shell));
        shell.in.back = true; overlay(shell).update(shell);
        assertTrue(rejected.equals(store.files.get(Settings.FILE)));
        shell.in.confirm = true; options.update(shell);
        shell.in.confirm = true; overlay(shell).update(shell);
        assertFalse(Settings.load(store).music);
        assertFalse(shell.settings.replacementRequired());
        shell.in.confirm = true; options.update(shell); // removed replacement row clamps to ENERGY
        assertTrue(shell.settings.stamina);
    }

    @Test
    void exitFlushesBothOriginalPendingCheckpointsWithoutSavingMutableDaytimeState() throws Exception {
        Store store = new Store(); Shell shell = shell(store);
        shell.game = shell.newGame(61, "knuckles");
        store.failures = 2;
        new DayEndScreen(false).enter(shell);
        String checkpoint = SaveCodec.encode(shell.game);
        shell.settings.music = false; assertFalse(shell.settings.save());
        shell.game.rings += 13;
        long rng = shell.game.rng.snapshot(); int day = shell.game.calendar.dayNumber();
        scene(shell).exit(shell.ctx);
        assertTrue(checkpoint.equals(store.files.get(SaveCodec.FILE)));
        assertFalse(Settings.load(store).music);
        assertEquals(rng, shell.game.rng.snapshot()); assertEquals(day, shell.game.calendar.dayNumber());
        assertFalse(shell.farmSavePending());
    }

    @Test
    void pendingContinueReconstructsTheAcceptedBedtimeAndNewGameCancelsItsRetry() {
        Store store = new Store(); Shell shell = shell(store);
        shell.game = shell.newGame(71, "sonic"); assertTrue(shell.save());
        store.failures = 10;
        new DayEndScreen(false).enter(shell);
        String checkpoint = SaveCodec.encode(shell.game);
        shell.game.rings += 17;
        Game continued = shell.load();
        assertNotSame(shell.game, continued);
        assertTrue(checkpoint.equals(SaveCodec.encode(continued)), "continue uses the accepted checkpoint while publication is pending");
        shell.beginNewGame(72, "tails");
        assertFalse(shell.farmSavePending());
        int writes = store.attempts.size();
        shell.flushPendingSaves();
        assertEquals(writes, store.attempts.size(), "new game cannot publish its predecessor's retained checkpoint");
    }

    @Test
    void permanentRefusalHasBoundedRetryCadenceAndNeverClaimsSuccess() throws Exception {
        Store store = new Store(); Shell shell = shell(store);
        shell.game = shell.newGame(81, "sonic"); store.failures = Integer.MAX_VALUE;
        new DayEndScreen(false).enter(shell);
        for (int i = 0; i < 119; i++) shell.update();
        assertEquals(2, store.attempts.size(), "one initial attempt and one retry per sixty owner ticks");
        List<String> text = new ArrayList<>(); shell.draw(canvas(text));
        assertTrue(text.stream().anyMatch(t -> t.contains("SAVE PENDING")));
        assertTrue(text.stream().noneMatch(t -> t.contains("SAVED")));
        scene(shell).exit(shell.ctx);
        assertEquals(3, store.attempts.size());
        assertTrue(shell.farmSavePending());
    }

    @Test
    void lateCorruptionRemainsPreservedAfterAnEarlierValidPublication() throws Exception {
        Store store = new Store(); Shell shell = shell(store);
        shell.game = shell.newGame(101, "sonic"); assertTrue(shell.save());
        store.files.put(SaveCodec.FILE, "late corruption");
        shell.save();
        assertTrue("late corruption".equals(store.files.get(SaveCodec.FILE)),
                "later corrupt primary bytes require explicit replacement when there is no recovery generation");
        scene(shell).exit(shell.ctx);
        assertTrue("late corruption".equals(store.files.get(SaveCodec.FILE)));
        shell = shell(store);
        shell.beginNewGame(102, "tails");
        assertTrue(shell.save(), "the explicit player replacement command grants the new checkpoint authority");
        assertNotNull(shell.load());
    }

    @Test
    void exitClosesPreparationFailureOwnerEvenWhenNoShellWasPublished() throws Exception {
        StarpostScene scene = new StarpostScene();
        var failures = new starpost.art.AssetFailures("starpost-valley", () -> 0L);
        failures.load("unavailable", () -> null);
        var field = StarpostScene.class.getDeclaredField("artFailures"); field.setAccessible(true); field.set(scene, failures);
        scene.exit(shell(new Store()).ctx);
        assertTrue(failures.failures().isEmpty());
        assertThrows(IllegalStateException.class, () -> failures.load("late", () -> "asset"));
    }

    private static Screen overlay(Shell shell) throws Exception {
        var field = Shell.class.getDeclaredField("overlays"); field.setAccessible(true);
        return (Screen) ((java.util.Deque<?>) field.get(shell)).peek();
    }

    private static CapsuleMenu vaultMenu(Shell shell) {
        var chambers = shell.game.section(starpost.core.Capsule.class).chambers(shell.catalog);
        var vault = chambers.stream().filter(c -> c.id().equals("vault")).findFirst().orElseThrow();
        CapsuleMenu menu = new CapsuleMenu();
        for (int i = 0; i < chambers.indexOf(vault); i++) {
            shell.in.downPressed = true; menu.update(shell); shell.in.consume();
        }
        shell.in.confirm = true; menu.update(shell); shell.in.consume();
        return menu;
    }

    static Shell shell(Store store) {
        SceneAudio audio = (SceneAudio) Proxy.newProxyInstance(SceneAudio.class.getClassLoader(),
                new Class<?>[]{SceneAudio.class}, (p, m, a) -> m.getReturnType() == boolean.class ? false : null);
        SceneContext context = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[]{SceneContext.class}, (p, m, a) -> switch (m.getName()) {
                    case "ownerModId" -> "starpost-valley";
                    case "storage" -> store;
                    case "audio" -> audio;
                    case "mouse" -> com.openggf.mods.scene.SceneMouse.none();
                    case "width" -> 400;
                    case "height" -> 224;
                    case "ticks" -> 0L;
                    default -> m.getReturnType() == boolean.class ? false : null;
                });
        return new Shell(context, null, new Catalog());
    }

    static StarpostScene scene(Shell shell) throws Exception {
        StarpostScene scene = new StarpostScene();
        var field = StarpostScene.class.getDeclaredField("shell");
        field.setAccessible(true);
        field.set(scene, shell);
        return scene;
    }

    static SceneCanvas canvas(List<String> text) {
        return (SceneCanvas) Proxy.newProxyInstance(SceneCanvas.class.getClassLoader(),
                new Class<?>[]{SceneCanvas.class}, (p, m, a) -> switch (m.getName()) {
                    case "width" -> 400;
                    case "height" -> 224;
                    case "textWidth" -> ((String) a[0]).length() * 6;
                    case "text" -> { text.add((String) a[0]); yield null; }
                    default -> null;
                });
    }

    static final class Store implements SceneStorage {
        record Attempt(String name, String text) {}
        final Map<String, String> files = new HashMap<>();
        final List<Attempt> attempts = new ArrayList<>();
        int failures;
        boolean throwing;
        public Optional<String> read(String name) { return Optional.ofNullable(files.get(name)); }
        public boolean write(String name, String text) {
            attempts.add(new Attempt(name, text));
            if (failures-- > 0) {
                if (throwing) throw new UncheckedIOException(new IOException("storage unavailable"));
                return false;
            }
            files.put(name, text);
            return true;
        }
        public Optional<byte[]> readBytes(String name) { return Optional.empty(); }
        public boolean writeBytes(String name, byte[] bytes) { throw new AssertionError("text only"); }
        public boolean delete(String name) { return files.remove(name) != null; }
        public List<String> list() { return files.keySet().stream().sorted().toList(); }
    }
}
