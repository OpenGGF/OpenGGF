package echoes;

/** Selects authored room data before the host requests its stock zone/act. No retained frame handles. */
public final class RoomLauncher {
    private final RoomSession session;
    public RoomLauncher(RoomSession session) { this.session = session; }
    public RoomDefinition launch(String roomId) {
        var room = RoomDefinition.load(roomId);
        session.select(room);
        return room;
    }
}
