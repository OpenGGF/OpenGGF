package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import java.util.concurrent.CompletableFuture;

/** Bound to one authenticated room registration, never to a guest's master session. */
@com.openggf.game.ModApi
@FunctionalInterface
public interface RoomInviteService {
    CompletableFuture<ControlMessage.RoomInviteControlResult> control(
            String instanceId, long accessEpoch, long inviteEpoch,
            ControlMessage.InviteOperation operation, boolean membersCanInvite);
}
