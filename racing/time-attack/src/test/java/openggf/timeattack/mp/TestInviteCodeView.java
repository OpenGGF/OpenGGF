package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneMouse;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestInviteCodeView {
    private final AtomicLong now = new AtomicLong(1_000);
    private final AtomicReference<InviteCodeView.Snapshot> snapshot = new AtomicReference<>(
            new InviteCodeView.Snapshot("7K3 M9X", 61_000, 1, true, true, true, ""));
    private final List<InviteCodeView.Action> actions = new ArrayList<>();
    private final FakeViewInput input = new FakeViewInput();
    private final InviteCodeView view = new InviteCodeView(snapshot::get, actions::add, cue -> { }, now::get);

    private void update(openggf.timeattack.ui.ViewInput input) { view.update(input, SceneMouse.none()); }

    private List<String> pixels(int width) {
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
        view.draw(canvas);
        canvas.assertFillsOnScreen();
        return canvas.fills.stream().filter(fill -> fill[0] >= 24 && fill[0] < width - 100
                && fill[1] >= 38 && fill[1] < 70).map(java.util.Arrays::toString).toList();
    }

    @Test
    void explicitRevealRehidesOnTimeoutNavigationAndFocus() {
        List<String> hidden = pixels(320);
        input.tapEnter(this::update);
        assertNotEquals(hidden, pixels(320));
        now.addAndGet(10_001);
        input.idle(this::update);
        assertEquals(hidden, pixels(320));
        input.tapEnter(this::update);
        view.hide();
        assertEquals(hidden, pixels(320));
        input.tapEnter(this::update);
        input.tapDown(this::update);
        assertTrue(actions.contains(InviteCodeView.Action.HIDE));
    }

    @Test
    void copyDoesNotRevealAndMembersCannotUseOwnerActions() {
        snapshot.set(new InviteCodeView.Snapshot("7K3 M9X", 61_000, 1, true, false, true, ""));
        input.idle(this::update);
        input.tapDown(this::update);
        List<String> hidden = pixels(400);
        input.tapEnter(this::update);
        assertEquals(hidden, pixels(400));
        assertEquals(InviteCodeView.Action.COPY, actions.getLast());
        input.tapDown(this::update);
        input.tapEnter(this::update);
        assertTrue(view.closed());
        assertFalse(actions.contains(InviteCodeView.Action.ROTATE));
        assertFalse(actions.contains(InviteCodeView.Action.TOGGLE_SHARING));
    }

    @Test
    void pendingDisclosureCannotReappearAfterHideOrPermissionChange() {
        snapshot.set(new InviteCodeView.Snapshot(null, 0, 1, true, false, true, ""));
        input.tapEnter(this::update);
        assertEquals(InviteCodeView.Action.FETCH, actions.getLast());
        view.hide();
        snapshot.set(new InviteCodeView.Snapshot("7K3 M9X", 61_000, 1, true, false, true, ""));
        input.idle(this::update);
        List<String> hidden = pixels(320);
        input.tapEnter(this::update);
        snapshot.set(new InviteCodeView.Snapshot("7K3 M9X", 61_000, 2, false, false, false, ""));
        input.idle(this::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(400);
        view.draw(canvas);
        assertTrue(canvas.joined().contains("Only the host can invite"));
        snapshot.set(new InviteCodeView.Snapshot("7K3 M9X", 61_000, 1, true, false, true, ""));
        input.idle(this::update);
        assertEquals(hidden, pixels(320));
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 640, 960})
    void codeAndManualLayoutsFitAndTheCompleteManualInviteIsOnlyDrawnAfterReveal(int width) {
        pixels(width);
        input.tapEnter(this::update);
        pixels(width);
        String manual = "HOST_IP:27900#" + "aB1_".repeat(21) + "YZ";
        snapshot.set(new InviteCodeView.Snapshot(manual, Long.MAX_VALUE, 2, true, true, true, "", true, true));
        input.idle(this::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
        view.draw(canvas);
        assertFalse(canvas.joined().contains("HOST_IP"));
        input.tapEnter(this::update);
        view.draw(canvas);
        canvas.assertFillsOnScreen();
        assertTrue(canvas.joined().replace("\n", "").contains(manual), "the full pinned address is wrapped without truncation");
        view.hide();
        view.draw(canvas);
        assertFalse(canvas.joined().contains("HOST_IP"));
    }

    @Test
    void theRealMouseEyeRequestsAReveal() throws Exception {
        pixels(320);
        Constructor<SceneMouse> constructor = SceneMouse.class.getDeclaredConstructor(int.class, int.class,
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                boolean.class, boolean.class, int.class, boolean.class);
        constructor.setAccessible(true);
        SceneMouse click = constructor.newInstance(250, 60, true, true, true, true, false, false, false, false, 0, true);
        List<String> hidden = pixels(320);
        view.update(input, click);
        assertNotEquals(hidden, pixels(320));
    }
}
