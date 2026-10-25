package openggf.timeattack.ui;

import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestViewInput {
    @Test
    void sceneContextForwardsThePhysicalSnapshotAndDetectsControllerConfirmation() {
        SceneContext context = mock(SceneContext.class);
        PhysicalInput physical = physical(button(0, PhysicalGamepad.BUTTON_X));
        when(context.physicalInput()).thenReturn(physical);
        when(context.buttonPressed(SceneButtons.START | SceneButtons.A | SceneButtons.C)).thenReturn(true);
        ViewInput input = ViewInput.of(context);
        assertSame(physical, input.physicalInput());
        assertTrue(input.controllerAccept());
    }

    @Test
    void connectedPadsAndGameplayButtonsDoNotImplyControllerMenuActivation() {
        FakeViewInput input = new FakeViewInput();
        input.accept = true;
        input.physical = physical();
        assertFalse(input.controllerAccept());
        input.physical = physical(button(0, PhysicalGamepad.BUTTON_A));
        assertFalse(input.controllerAccept());
        input.physical = physical(button(4, PhysicalGamepad.BUTTON_X));
        assertFalse(input.controllerAccept(), "the second pad cannot own the first player's chooser");
        input.physical = physical(button(0, PhysicalGamepad.BUTTON_B));
        assertTrue(input.controllerAccept());
        input.accept = false;
        assertFalse(input.controllerAccept(), "a raw transition cannot bypass logical controller enablement");
    }

    @Test
    void keyboardActivationWinsIfBothDevicesArePressedTogether() {
        FakeViewInput input = new FakeViewInput();
        input.accept = true;
        input.physical = physical(button(0, PhysicalGamepad.BUTTON_START));
        input.pressed.add(SceneKeys.ENTER);
        assertFalse(input.controllerAccept());
        input.pressed.clear();
        input.physical = physical(button(0, PhysicalGamepad.BUTTON_START), new PhysicalInputEvent(2, 0,
                PhysicalInputEvent.Kind.KEY, PhysicalInputEvent.KEYBOARD_DEVICE, SceneKeys.Z, 1));
        assertFalse(input.controllerAccept(), "mapped keyboard confirm keys also take precedence");
    }

    private static PhysicalInputEvent button(int device, int code) {
        return new PhysicalInputEvent(1, 0, PhysicalInputEvent.Kind.BUTTON, device, code, 1);
    }

    private static PhysicalInput physical(PhysicalInputEvent... events) {
        return new PhysicalInput(0, List.of(), List.of(
                new PhysicalGamepad(0, "Primary", new boolean[15], new float[6]),
                new PhysicalGamepad(4, "Secondary", new boolean[15], new float[6])), List.of(events), 0);
    }
}
