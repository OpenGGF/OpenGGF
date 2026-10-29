package openggf.timeattack.mp;

import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRoomAccessView {
    private record Created(boolean privateRoom, boolean members, String password) { }
    private final List<Created> created = new ArrayList<>();
    private final FakeViewInput input = new FakeViewInput();
    private int cancelled;
    private final RoomAccessView view = new RoomAccessView(new RoomAccessView.Actions() {
        @Override public void create(boolean privateRoom, boolean members, String password) {
            created.add(new Created(privateRoom, members, password));
        }
        @Override public void cancelled() { cancelled++; }
    }, cue -> { });

    @Test
    void defaultPublicCreationHasNoCredentialAndMembersCanInvite() {
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        input.tapEnter(view::update);
        assertEquals(List.of(new Created(false, true, "")), created);
    }

    private void enterPrivatePassword(String password) {
        input.tapRight(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        input.commit(view::update, password);
        input.tapEnter(view::update);
    }

    @Test
    void privateCreationUsesExactUnicodeAndOptionalHostOnlySharing() {
        String secret = "  café密碼🙂 long secret  ";
        enterPrivatePassword(secret);
        input.tapDown(view::update);
        input.tapRight(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertEquals(List.of(new Created(true, false, secret)), created);
    }

    @Test
    void invalidPrivatePasswordCannotCreateAndCancelNeverCreates() {
        enterPrivatePassword("too short");
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertTrue(created.isEmpty());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(400);
        view.draw(canvas);
        assertTrue(canvas.shows("at least 15"));
        input.tapEscape(view::update);
        input.tapEnter(view::update);
        assertEquals(1, cancelled);
        assertTrue(created.isEmpty());
    }

    @Test
    void focusLossClearsTheCredentialWhileUpdatesArePaused() {
        enterPrivatePassword("a sufficiently long password");
        view.focusLost();
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertTrue(created.isEmpty());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(640);
        view.draw(canvas);
        assertTrue(canvas.shows("Not set"));
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 640, 960})
    void publicPrivateAndEditingLayoutsStayWithinTheViewportAndMaskTheSecret(int width) {
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
        view.draw(canvas);
        canvas.assertFillsOnScreen();
        String secret = "pāssword with spaces🙂";
        enterPrivatePassword(secret);
        view.draw(canvas);
        canvas.assertFillsOnScreen();
        assertFalse(canvas.shows(secret));
        assertFalse(canvas.shows("Not set"), "a set password shows only its fixed mask");
        input.tapEnter(view::update);
        assertTrue(view.isEditingText());
        view.draw(canvas);
        canvas.assertFillsOnScreen();
        assertFalse(canvas.shows(secret));
        view.close();
        assertFalse(view.isEditingText());
    }
}
