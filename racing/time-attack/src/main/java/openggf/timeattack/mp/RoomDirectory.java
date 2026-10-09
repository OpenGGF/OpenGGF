package openggf.timeattack.mp;

import openggf.racing.client.MasterClient;
import openggf.racing.protocol.ControlMessage;

import java.util.concurrent.CompletableFuture;

/** The room listing the server browser pages through: an admitted master connection. */
public interface RoomDirectory {
    boolean isOpen();

    /** One page of rooms for {@code gameId}; completes on a network thread. */
    CompletableFuture<ControlMessage.RoomListResult> listRooms(String gameId, int page);

    static RoomDirectory of(MasterClient master) {
        return new RoomDirectory() {
            @Override public boolean isOpen() { return master.isOpen(); }
            @Override public CompletableFuture<ControlMessage.RoomListResult> listRooms(String gameId, int page) {
                return master.listRooms(gameId, page);
            }
        };
    }
}
