package com.openggf.tools;

// Act Practice menu pictures through the real Engine startup: the example's exploded classes
// (examples/build_example.py act-practice) load as the development mod (-Dggfmod.dev.modDir, as
// `ggfmod run` does) and open from Extras. Real keyboard events and a scripted Xbox-style pad
// (a GamepadStateSource swapped into the capture's InputHandler) drive the menu.
// States named "*-induced-*" call the production scene's resumed(...) with a reason the script could
// not reach in a short capture (an act completed, a failed load) or set the host's save notice; the
// drawing is the production scene, the state is induced and labelled as such.
// Usage (cwd = an empty, owned working directory):
//   env -u DISPLAY -u WAYLAND_DISPLAY java -Dopenggf.headless.gl=surfaceless-egl -cp <target/classes:deps> \
//     src/test/java/com/openggf/tools/ActPracticeDesignCaptureTool.java <out> <rom-dir> \
//     target/examples/act-practice/classes <logical-width> <extras-entry-index>
// Build the example first (python3 examples/build_example.py act-practice --skip-engine after a
// package build); PRACTICE_PRE_ESC=1 with an empty owned ROM directory pictures the no-games page.
// Inputs: output directory, original ROM directory, example classes, logical width, Extras index.
// Origin: all-menus-final-families-20261028 ModStates.java (base efae20d69); adapted for
// time-attack-practice-menu-fixes-20261029 (base 9999bcbdf).
import com.openggf.tools.TimeAttackVideoCaptureTool;
import com.openggf.mods.run.RunEndReason;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class ActPracticeDesignCaptureTool {
    static TimeAttackVideoCaptureTool t;
    static final boolean[] padButtons = new boolean[15];
    static boolean padInstalled;
    /** The scripted pad's name, which selects its family's prompt labels. */
    static volatile String padName = "Xbox Controller";

    public static void main(String[] a) throws Exception {
        if (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl"))
                || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Use surfaceless EGL with DISPLAY and WAYLAND_DISPLAY unset");
        System.setProperty("ggfmod.dev.modDir", Path.of(a[2]).toAbsolutePath().toString());
        int logical = Integer.parseInt(a[3]);
        int entry = Integer.parseInt(a[4]);
        try (var tool = new TimeAttackVideoCaptureTool(Path.of(a[0]), Path.of(a[1]), logical, 224, logical)) {
            t = tool;
            t.frame();
            t.frames(350);
            t.tap(GLFW_KEY_ENTER);
            for (int i = 0; i < 240; i++) t.frame();
            t.frames(60);
            t.png("00-title.png");
            // Without ROMs the title opens on onboarding (Add your games); Back reaches the hub.
            if (System.getenv("PRACTICE_PRE_ESC") != null) { t.tap(GLFW_KEY_ESCAPE); t.frames(60); t.png("00-hub.png"); }
            t.tap(GLFW_KEY_UP); t.tap(GLFW_KEY_RIGHT); t.tap(GLFW_KEY_RIGHT); t.tap(GLFW_KEY_ENTER);
            t.frames(30);
            t.png("00-extras.png");
            for (int i = 0; i < entry; i++) { t.tap(GLFW_KEY_DOWN); t.frames(4); }
            t.tap(GLFW_KEY_ENTER);
            t.frames(90);
            Object scene = t.openScene();
            System.out.println("scene " + (scene == null ? null : scene.getClass().getName()));
            if (scene == null || !scene.getClass().getName().equals("practice.PracticeScene"))
                throw new IllegalStateException("Extras did not open Act Practice: " + scene);
            List<?> games = (List<?>) field(scene, "games");
            System.out.println("games " + games);
            shot("p01-choice");
            if (games.isEmpty()) {
                padTap(com.openggf.control.PhysicalGamepad.BUTTON_DPAD_DOWN);
                shot("p02-empty-pad");
                return;
            }
            tap(GLFW_KEY_RIGHT);
            shot("p02-choice-next");
            tap(GLFW_KEY_RIGHT);
            shot("p03-choice-third");
            tap(GLFW_KEY_RIGHT);
            padTap(com.openggf.control.PhysicalGamepad.BUTTON_DPAD_LEFT);
            padTap(com.openggf.control.PhysicalGamepad.BUTTON_DPAD_RIGHT);
            shot("p04-choice-pad");
            padName = "Sony DualSense Wireless Controller";
            padTap(com.openggf.control.PhysicalGamepad.BUTTON_DPAD_LEFT);
            padTap(com.openggf.control.PhysicalGamepad.BUTTON_DPAD_RIGHT);
            shot("p04b-choice-pad-playstation");
            padName = "Xbox Controller";
            tap(GLFW_KEY_LEFT); tap(GLFW_KEY_RIGHT);
            Object host = field(scene, "host");
            set(host, "game", games.get(0).toString()); // as prepare() would for the chosen game
            set(host, "lastTimeFrames", 2712);
            set(host, "bestFrames", 2712);
            resumed(scene, RunEndReason.ACT_COMPLETED);
            shot("p05-induced-act-complete");
            resumed(scene, RunEndReason.LOAD_FAILED);
            shot("p06-induced-load-failed");
            resumed(scene, RunEndReason.ACT_COMPLETED);
            set(host, "saveNotice", "Best not saved; retrying");
            shot("p07-induced-save-notice");
            set(host, "saveNotice", "");
            resumed(scene, RunEndReason.LEFT);
            // A real launch of the chosen first act; Right held for a second so the timer runs.
            tap(GLFW_KEY_ENTER);
            for (int i = 0; i < 400 && t.engine().getCurrentGameMode() == com.openggf.game.GameMode.MOD_SCENE; i++) t.frame();
            t.frames(200);
            t.key(GLFW_KEY_RIGHT, true); t.frames(70); t.key(GLFW_KEY_RIGHT, false);
            System.out.println("launched mode " + t.engine().getCurrentGameMode());
            shot("p08-in-run-timer");
        }
    }

    static void resumed(Object scene, RunEndReason reason) throws Exception {
        Method m = scene.getClass().getMethod("resumed", com.openggf.mods.scene.SceneContext.class, RunEndReason.class);
        m.invoke(scene, null, reason);
        t.frames(4);
    }

    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static void tap(int key) throws Exception { t.tap(key); t.frames(4); }

    static void shot(String name) throws Exception {
        t.frames(2);
        t.png(name + ".png");
        System.out.println("shot " + name);
    }

    static void padTap(int button) throws Exception {
        if (!padInstalled) {
            var managerField = com.openggf.control.InputHandler.class.getDeclaredField("gamepadInputManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(t.input());
            var sourceField = com.openggf.control.GamepadInputManager.class.getDeclaredField("stateSource");
            sourceField.setAccessible(true);
            sourceField.set(manager, (com.openggf.control.GamepadStateSource) () -> List.of(
                    com.openggf.control.GamepadStateSource.DeviceState.connected(0, padName,
                            padButtons.clone(), 0f, 0f)));
            padInstalled = true;
            t.frames(2);
        }
        padButtons[button] = true;
        t.frame();
        padButtons[button] = false;
        t.frames(4);
    }
}
