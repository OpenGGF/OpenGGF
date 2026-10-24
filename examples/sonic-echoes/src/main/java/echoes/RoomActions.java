package echoes;

import com.openggf.control.*;
import com.openggf.game.LevelInputOverlay;

/** Physical verbs stay separate from Genesis A/B/C and Start. */
final class RoomActions implements LevelInputOverlay {
    private final RoomSession session;
    private final ActionReducer reducer;
    private boolean reset = true;
    private RoomDefinition inputRoom;
    RoomActions(RoomSession session) {
        this.session = session;
        var map = new ActionMap();
        bind(map, "echo", 69, 3); // E / Y
        bind(map, "undo", 85, 5); // U / RB
        bind(map, "reset", 82, 9); // R / left stick click
        bind(map, "hint", 72, 4); // H / LB
        bind(map, "exit", 81, 6); // Q / Back
        reducer = new ActionReducer(map);
    }
    private static void bind(ActionMap map, String name, int key, int button) {
        map.bind(name, new PhysicalBinding('K', -1, key, 1), new PhysicalBinding('B', 0, button, 1));
    }
    @Override public boolean handleInput(InputHandler input) {
        var sample = input.capturePhysicalInput();
        if (inputRoom != session.room()) { inputRoom=session.room();reset=true; }
        if (reset) { reducer.reset(sample); reset = false; return session.cleared(); }
        if (sample.droppedEvents() > 0) { reducer.reset(sample); return session.cleared(); }
        if (session.cleared() && session.clearAge() >= 72 && ((input.logical().player1().actionPressedMask() & 5) != 0 || input.logical().menuStart() || input.isKeyPressed(257)))
            session.command(RoomSession.Command.EXIT);
        for (var frame : reducer.accept(sample)) {
            if (frame.pressed("hint")) session.command(RoomSession.Command.HINT);
            if (frame.pressed("exit")) session.command(RoomSession.Command.EXIT);
            else if (frame.pressed("reset")) session.command(RoomSession.Command.RESET);
            else if (frame.pressed("undo")) session.command(RoomSession.Command.UNDO);
            else if (frame.pressed("echo")) session.command(RoomSession.Command.ECHO);
        }
        return session.cleared(); // Results own Start so confirmation cannot toggle host pause.
    }
}
