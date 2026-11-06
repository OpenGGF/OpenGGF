package com.openggf.net.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Envelope codec for control messages. The envelope's {@code v} is {@link Protocol#VERSION}.
 *
 * <p>Each connection owner may retain one codec; instances are thread-safe once
 * constructed. The JSON implementation stays private to this typed protocol API.
 */
@com.openggf.game.ModApi
public final class ControlJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    private final Set<String> envelopeFields = Set.of("v", "token", "msg", "requestId");
    private final Map<String, Class<? extends ControlMessage>> messageTypes =
            Arrays.stream(ControlMessage.class.getPermittedSubclasses())
                    .map(type -> type.asSubclass(ControlMessage.class))
                    .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    private enum Direction { ANY, ROOM_CLIENT, ROOM_SERVER, MASTER_CLIENT, MASTER_SERVER }

    private static final Set<Class<?>> ROOM_CLIENT_TYPES = Set.of(
            ControlMessage.Hello.class, ControlMessage.AuthProof.class,
            ControlMessage.RoomAccessSubmit.class, ControlMessage.RoomPasswordChange.class,
            ControlMessage.RoomInviteRequest.class, ControlMessage.RoomInviteRotate.class,
            ControlMessage.RoomInviteSharing.class, ControlMessage.Chat.class,
            ControlMessage.QuickChat.class, ControlMessage.SetChatMode.class,
            ControlMessage.UpdateProfile.class, ControlMessage.SetReady.class,
            ControlMessage.SetReadyRule.class, ControlMessage.Ping.class,
            ControlMessage.SelectCharacter.class, ControlMessage.RoundConfigure.class,
            ControlMessage.AttemptStart.class, ControlMessage.AttemptReset.class,
            ControlMessage.TrackVote.class, ControlMessage.AttemptFinish.class,
            ControlMessage.StandingsPageRequest.class);

    private static final Set<Class<?>> ROOM_SERVER_TYPES = Set.of(
            ControlMessage.Welcome.class, ControlMessage.JoinAccepted.class,
            ControlMessage.JoinRejected.class, ControlMessage.Kick.class,
            ControlMessage.RoomState.class, ControlMessage.PlayerUpsert.class,
            ControlMessage.PlayerLeft.class, ControlMessage.PlayerLook.class,
            ControlMessage.PlayerReady.class, ControlMessage.ReadyReset.class,
            ControlMessage.ReadyRule.class, ControlMessage.ChatModeState.class,
            ControlMessage.QuickChatBroadcast.class, ControlMessage.ChatBroadcast.class,
            ControlMessage.Pong.class, ControlMessage.RoundStart.class,
            ControlMessage.RoundEnd.class, ControlMessage.StandingsDelta.class,
            ControlMessage.TrackVoteOffer.class, ControlMessage.TrackVoteTally.class,
            ControlMessage.TrackVoteResult.class, ControlMessage.RecordingRequest.class,
            ControlMessage.StandingsPage.class, ControlMessage.RankUpdate.class,
            ControlMessage.RoomAccessChallenge.class, ControlMessage.RoomAccessState.class,
            ControlMessage.RoomPasswordChanged.class, ControlMessage.RoomInviteInvalidated.class,
            ControlMessage.RoomInvite.class, ControlMessage.RoomInviteRejected.class);

    private static final Set<Class<?>> MASTER_CLIENT_TYPES = Set.of(
            ControlMessage.Hello.class, ControlMessage.AuthProof.class,
            ControlMessage.PowSolution.class, ControlMessage.RoomCreate.class,
            ControlMessage.RoomActivate.class,
            ControlMessage.RoomListRequest.class, ControlMessage.RoomJoinRequest.class,
            ControlMessage.RoomGrantRedeem.class, ControlMessage.RoomRouteRetire.class,
            ControlMessage.RoomRouteRetireAck.class, ControlMessage.RoomInviteResolve.class,
            ControlMessage.RoomInviteControl.class, ControlMessage.MasterRequestCancel.class,
            ControlMessage.RelayAttach.class, ControlMessage.Heartbeat.class,
            ControlMessage.RoomLeave.class, ControlMessage.RoomTrackUpdate.class,
            ControlMessage.RoomChatModeUpdate.class, ControlMessage.RelayGuestText.class,
            ControlMessage.RelayGuestClose.class);

    private static final Set<Class<?>> MASTER_SERVER_TYPES = Set.of(
            ControlMessage.Welcome.class, ControlMessage.PowChallenge.class,
            ControlMessage.JoinAccepted.class, ControlMessage.JoinRejected.class,
            ControlMessage.RoomCreated.class, ControlMessage.RoomActivated.class,
            ControlMessage.RoomCreateRejected.class,
            ControlMessage.RoomListResult.class, ControlMessage.RoomJoinResult.class,
            ControlMessage.RoomJoinRejected.class, ControlMessage.RoomGrantDecision.class,
            ControlMessage.RoomRouteRetireNotice.class, ControlMessage.RoomRouteRenewed.class,
            ControlMessage.RoomRouteRetired.class,
            ControlMessage.RoomRouteUnavailable.class, ControlMessage.RoomInviteRejected.class,
            ControlMessage.RoomInviteControlResult.class, ControlMessage.RelayGuestOpen.class,
            ControlMessage.RelayGuestText.class, ControlMessage.RelayGuestClose.class);

    @com.openggf.game.ModApi
    public record Decoded(String token, ControlMessage message, long requestId) {
        public Decoded {
            if (requestId < 0) throw new IllegalArgumentException("invalid request ID");
        }
        public Decoded(String token, ControlMessage message) { this(token, message, 0); }
        @Override
        public String toString() { return "Decoded[redacted]"; }
    }

    public ControlJsonCodec() {
        var secrets = new com.fasterxml.jackson.databind.module.SimpleModule();
        secrets.addSerializer(RoomPassword.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(RoomPassword value, com.fasterxml.jackson.core.JsonGenerator output,
                                  com.fasterxml.jackson.databind.SerializerProvider provider)
                    throws java.io.IOException {
                output.writeString(value.wireValue());
            }
        });
        secrets.addDeserializer(RoomPassword.class, new com.fasterxml.jackson.databind.JsonDeserializer<>() {
            @Override
            public RoomPassword deserialize(com.fasterxml.jackson.core.JsonParser input,
                                            com.fasterxml.jackson.databind.DeserializationContext context)
                    throws java.io.IOException {
                if (!input.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING)) {
                    throw new ProtocolViolationException("invalid room password");
                }
                return RoomPassword.of(input.getText());
            }
        });
        mapper.registerModule(secrets);
    }

    public String encode(String tokenOrNull, ControlMessage message) {
        return encode(tokenOrNull, message, 0);
    }

    public String encode(String tokenOrNull, ControlMessage message, long requestId) {
        if (requestId < 0) throw new IllegalArgumentException("invalid request ID");
        try {
            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("v", Protocol.VERSION);
            if (requestId != 0) envelope.put("requestId", requestId);
            if (tokenOrNull == null) {
                envelope.putNull("token");
            } else {
                envelope.put("token", tokenOrNull);
            }
            ObjectNode body = mapper.valueToTree(message);
            body.put("type", message.getClass().getSimpleName());
            envelope.set("msg", body);
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new ProtocolViolationException(
                    "failed to encode " + message.getClass().getSimpleName());
        }
    }

    public Decoded decode(String text) {
        return decode(text, Protocol.MAX_CONTROL_BYTES, Direction.ANY);
    }

    public Decoded decodeRoomClient(String text) {
        return decode(text, Protocol.MAX_CONTROL_BYTES, Direction.ROOM_CLIENT);
    }

    public Decoded decodeRoomServer(String text) {
        Decoded decoded = decode(text, Protocol.MAX_CONTROL_BYTES, Direction.ROOM_SERVER);
        requireRoomAdmission(decoded);
        return decoded;
    }

    public Decoded decodeMasterClient(String text, int maxBytes) {
        return decode(text, maxBytes, Direction.MASTER_CLIENT);
    }

    public Decoded decodeMasterServer(String text, int maxBytes) {
        return decode(text, maxBytes, Direction.MASTER_SERVER);
    }

    /** Room traffic cannot use the broker's slotless master-admission marker. */
    public Decoded decodeRoom(String text) {
        Decoded decoded = decode(text);
        requireRoomAdmission(decoded);
        return decoded;
    }

    private static void requireRoomAdmission(Decoded decoded) {
        if (decoded.message() instanceof ControlMessage.JoinAccepted accepted
                && (accepted.playerSlot() < 0 || accepted.room() == null)) {
            throw new ProtocolViolationException("invalid room admission");
        }
    }

    private Decoded decode(String text, int maxBytes, Direction direction) {
        if (text == null) {
            throw new ProtocolViolationException("control frame is null");
        }
        if (maxBytes < 1 || maxBytes > Protocol.MAX_MASTER_FRAME_BYTES) {
            throw new IllegalArgumentException("invalid control frame cap " + maxBytes);
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ProtocolViolationException(
                    "control frame exceeds " + maxBytes + " bytes");
        }
        try {
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject()) {
                throw new ProtocolViolationException("control envelope must be an object");
            }
            root.fieldNames().forEachRemaining(field -> {
                if (!envelopeFields.contains(field)) {
                    throw new ProtocolViolationException(
                            "unsupported control envelope field");
                }
            });
            JsonNode version = root.get("v");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() != Protocol.VERSION) {
                throw new ProtocolViolationException("unsupported protocol version");
            }
            JsonNode msg = root.get("msg");
            if (msg == null || !msg.isObject()) {
                throw new ProtocolViolationException("missing msg body");
            }
            JsonNode token = root.get("token");
            if (token == null || (!token.isNull() && !token.isTextual())) {
                throw new ProtocolViolationException("token must be a string or null");
            }
            String tokenValue = token.isNull() ? null : token.textValue();
            JsonNode typeNode = msg.get("type");
            Class<? extends ControlMessage> messageType =
                    typeNode == null || !typeNode.isTextual()
                            ? null : messageTypes.get(typeNode.textValue());
            if (messageType == null) {
                throw new ProtocolViolationException("unsupported control message type");
            }
            if (!allows(direction, messageType)) {
                throw new ProtocolViolationException("control message from wrong peer");
            }
            ObjectNode messageBody = ((ObjectNode) msg).deepCopy();
            messageBody.remove("type");
            validateChatWireTypes(messageType, messageBody);
            ControlMessage message = mapper.treeToValue(messageBody, messageType);
            validateClientState(message);
            validateSemantics(message);
            validateAuthorityShape(direction, message);
            if (message instanceof ControlMessage.RelayGuestText relay
                    && relay.text().getBytes(StandardCharsets.UTF_8).length
                    > Protocol.MAX_CONTROL_BYTES) {
                throw new ProtocolViolationException("relay guest text exceeds inner frame cap");
            }
            JsonNode request = root.get("requestId");
            if (request != null && (!request.isIntegralNumber() || !request.canConvertToLong()
                    || request.longValue() < 0)) {
                throw new ProtocolViolationException("invalid request ID");
            }
            return new Decoded(tokenValue, message, request == null ? 0 : request.longValue());
        } catch (ProtocolViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolViolationException("undecodable control frame");
        }
    }

    private static boolean allows(Direction direction, Class<?> type) {
        return switch (direction) {
            case ANY -> true;
            case ROOM_CLIENT -> ROOM_CLIENT_TYPES.contains(type);
            case ROOM_SERVER -> ROOM_SERVER_TYPES.contains(type);
            case MASTER_CLIENT -> MASTER_CLIENT_TYPES.contains(type);
            case MASTER_SERVER -> MASTER_SERVER_TYPES.contains(type)
                    || ROOM_SERVER_TYPES.contains(type);
        };
    }

    private static void validateAuthorityShape(Direction direction, ControlMessage message) {
        if (direction == Direction.ROOM_SERVER) {
            if (message instanceof ControlMessage.RoundStart start && start.launch() == null) {
                invalid("missing race launch");
            }
            if (message instanceof ControlMessage.JoinAccepted accepted
                    && accepted.round() != null
                    && ("COUNTDOWN".equals(accepted.round().phase())
                        || "RUNNING".equals(accepted.round().phase()))
                    && accepted.round().launch() == null) {
                invalid("missing active race launch");
            }
        }
        if (direction == Direction.MASTER_SERVER) {
            if (message instanceof ControlMessage.RoomCreated created) {
                requireText(created.instanceId(), "room instance");
            }
        }
    }

    /** Do not let Jackson coerce numbers/booleans into semantic IDs or sender labels. */
    private static void validateChatWireTypes(Class<? extends ControlMessage> type, JsonNode body) {
        if (type == ControlMessage.SetChatMode.class || type == ControlMessage.ChatModeState.class) {
            requireStringNode(body, "mode");
        } else if (type == ControlMessage.QuickChat.class) {
            requireStringNode(body, "phraseId");
        } else if (type == ControlMessage.QuickChatBroadcast.class) {
            requireStringNode(body, "displayName");
            requireStringNode(body, "phraseId");
            requireStringNode(body, "gameId");
            for (String field : new String[]{"slot", "zone", "act"}) {
                JsonNode value = body.get(field);
                if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                    throw new ProtocolViolationException("invalid " + field);
                }
            }
        } else if (type == ControlMessage.RoomChatModeUpdate.class) {
            requireStringNode(body, "roomId");
            requireStringNode(body, "mode");
        }
    }

    private static void requireStringNode(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            throw new ProtocolViolationException("invalid " + field);
        }
    }

    private static void validateClientState(ControlMessage message) {
        switch (message) {
            case ControlMessage.QuickChatBroadcast quick -> {
                validateSlot(quick.slot());
                requireText(quick.displayName(), "chat sender");
                requireText(quick.gameId(), "chat game");
                if (QuickChatPhrase.parse(quick.phraseId()) == null
                        || quick.zone() < 0 || quick.act() < 0) {
                    throw new ProtocolViolationException("invalid quick chat context");
                }
            }
            case ControlMessage.RoomState state -> validatePlayers(state.players());
            case ControlMessage.PlayerUpsert upsert -> validatePlayer(upsert.player());
            case ControlMessage.PlayerLeft left -> validateSlot(left.slot());
            case ControlMessage.JoinAccepted accepted -> {
                requireText(accepted.sessionToken(), "session token");
                boolean masterAdmission = accepted.playerSlot() == -1
                        && accepted.room() == null && accepted.round() == null;
                boolean roomAdmission = accepted.playerSlot() >= 0
                        && accepted.playerSlot() < Protocol.MAX_PLAYERS_RELAY
                        && accepted.room() != null && accepted.round() != null;
                if (!masterAdmission && !roomAdmission) {
                    throw new ProtocolViolationException("invalid join metadata");
                }
                if (accepted.round() != null) {
                    ControlMessage.RoundSnapshot snapshot = accepted.round();
                    if (snapshot.phase() == null
                            || !Set.of("LOBBY", "COUNTDOWN", "RUNNING", "ROUND_END", "VOTE")
                            .contains(snapshot.phase())) {
                        throw new ProtocolViolationException("invalid round phase");
                    }
                    if (("COUNTDOWN".equals(snapshot.phase())
                            || "RUNNING".equals(snapshot.phase()))
                            && snapshot.config() == null) {
                        throw new ProtocolViolationException("missing active round config");
                    }
                    if (snapshot.config() != null) {
                        validateRoundConfig(snapshot.config());
                    }
                    if (snapshot.launch() != null) {
                        validateRaceLaunch(snapshot.launch(), snapshot.config(),
                                snapshot.countdownEndsAtHubMillis(), snapshot.deadlineHubMillis());
                    }
                    validateStandings(snapshot.standings(), "snapshot standings");
                }
            }
            case ControlMessage.RoundStart start -> {
                if (start.config() == null) {
                    throw new ProtocolViolationException("missing round config");
                }
                validateRoundConfig(start.config());
                if (start.launch() != null) {
                    validateRaceLaunch(start.launch(), start.config(),
                            start.countdownEndsAtHubMillis(), start.deadlineHubMillis());
                }
            }
            case ControlMessage.RoundEnd end ->
                    validateStandings(end.finalStandings(), "final standings");
            case ControlMessage.StandingsDelta delta ->
                    validateStandings(delta.rows(), "standings rows");
            case ControlMessage.StandingsPage page ->
                    validateStandings(page.rows(), "standings page");
            default -> { }
        }
    }

    private static void validateSemantics(ControlMessage message) {
        switch (message) {
            case ControlMessage.Hello hello -> {
                if (hello.protocolVersion() != Protocol.VERSION) invalid("hello version");
                requireText(hello.pubKeyBase64(), "public key");
                if (hello.displayName() == null) invalid("display name");
                requireText(hello.determinismFingerprint(), "determinism fingerprint");
                validateAppearance(hello.appearance());
                if ((hello.grantReference() == null) != (hello.routeGeneration() == 0)
                        || hello.routeGeneration() < 0
                        || hello.grantReference() != null
                        && (hello.grantReference().isBlank()
                        || hello.grantReference().length() > 128)) invalid("join route");
            }
            case ControlMessage.Welcome welcome -> {
                if (welcome.protocolVersion() != Protocol.VERSION) invalid("welcome version");
                requireText(welcome.nonceBase64(), "nonce");
                requireText(welcome.serverId(), "server identity");
            }
            case ControlMessage.AuthProof proof -> requireText(proof.signatureBase64(), "proof");
            case ControlMessage.JoinAccepted accepted -> {
                if (accepted.room() != null) validateRoom(accepted.room());
                if (accepted.round() != null && (accepted.round().countdownEndsAtHubMillis() < 0
                        || accepted.round().deadlineHubMillis() < 0)) invalid("round snapshot");
            }
            case ControlMessage.JoinRejected rejected -> requireText(rejected.reason(), "join rejection");
            case ControlMessage.Kick kick -> requireText(kick.reason(), "kick reason");
            case ControlMessage.RoomState ignored -> { }
            case ControlMessage.PlayerUpsert ignored -> { }
            case ControlMessage.PlayerLeft ignored -> { }
            case ControlMessage.SelectCharacter select -> requireText(select.character(), "character");
            case ControlMessage.UpdateProfile update -> {
                requireText(update.displayName(), "display name");
                validateAppearance(update.appearance());
            }
            case ControlMessage.PlayerLook look -> {
                validateSlot(look.slot());
                validateAppearance(look.appearance());
            }
            case ControlMessage.SetReady ignored -> { }
            case ControlMessage.PlayerReady ready -> validateSlot(ready.slot());
            case ControlMessage.ReadyReset ignored -> { }
            case ControlMessage.SetReadyRule rule -> validateReadyRule(rule.rule());
            case ControlMessage.ReadyRule rule -> validateReadyRule(rule.rule());
            case ControlMessage.SetChatMode mode -> validateChatMode(mode.mode());
            case ControlMessage.ChatModeState mode -> validateChatMode(mode.mode());
            case ControlMessage.QuickChat quick -> validateQuickPhrase(quick.phraseId());
            case ControlMessage.QuickChatBroadcast ignored -> { }
            case ControlMessage.Chat chat -> validateChat(chat.text());
            case ControlMessage.ChatBroadcast chat -> {
                validateSlot(chat.slot());
                requireText(chat.displayName(), "chat sender");
                validateChat(chat.text());
            }
            case ControlMessage.Ping ignored -> { }
            case ControlMessage.Pong ignored -> { }
            case ControlMessage.RoundConfigure configure -> {
                if (configure.config() == null) invalid("round config");
                validateRoundConfig(configure.config());
                if (configure.planRevision() < 0) invalid("round plan revision");
            }
            case ControlMessage.RoundStart start -> {
                if (start.countdownEndsAtHubMillis() < 0
                        || start.deadlineHubMillis() <= start.countdownEndsAtHubMillis()) {
                    invalid("round deadline");
                }
            }
            case ControlMessage.RoundEnd ignored -> { }
            case ControlMessage.StandingsDelta ignored -> { }
            case ControlMessage.AttemptStart start -> validateAttempt(start.attemptId());
            case ControlMessage.AttemptFinish finish -> {
                validateAttempt(finish.attemptId());
                if (finish.timeFrames() <= 0 || finish.firstInputFrame() < 0
                        || finish.finishFrame() < finish.firstInputFrame()) invalid("attempt finish");
                validateHash(finish.inputRecordingHashHex());
                validateHash(finish.ghostStreamHashHex());
            }
            case ControlMessage.AttemptReset reset -> validateAttempt(reset.attemptId());
            case ControlMessage.TrackVote vote -> validateTrackKey(vote.trackKey());
            case ControlMessage.TrackVoteOffer offer -> {
                validateUniqueTrackKeys(offer.trackKeys(), 8);
                if (offer.voteEndsAtHubMillis() < 0) invalid("vote deadline");
            }
            case ControlMessage.TrackVoteTally tally -> {
                if (tally.counts() == null || tally.counts().size() > 8) invalid("vote tally");
                Set<String> keys = new java.util.HashSet<>();
                for (ControlMessage.VoteCount count : tally.counts()) {
                    if (count == null || count.votes() < 0 || !keys.add(count.trackKey())) invalid("vote tally");
                    validateTrackKey(count.trackKey());
                }
            }
            case ControlMessage.TrackVoteResult result -> {
                validateTrackKey(result.trackKey());
                validateRoundConfig(result.nextConfig());
                if (result.planRevision() <= 0 || !result.trackKey().equals(
                        result.nextConfig().gameId() + ":" + result.nextConfig().zone()
                                + ":" + result.nextConfig().act())) {
                    invalid("committed round plan");
                }
            }
            case ControlMessage.RecordingRequest request -> {
                validateAttempt(request.attemptId());
                validateHash(request.expectedHashHex());
                if (request.uploadUrl() != null && request.uploadUrl().isBlank()) invalid("upload URL");
            }
            case ControlMessage.RoomCreate create -> {
                validateRoom(create.room());
                validateRouting(create.routing());
                requireText(create.determinismFingerprint(), "determinism fingerprint");
                validateUniqueTrackKeys(create.voteTrackKeys(), 32);
                if (create.accessEpoch() < 0) invalid("access epoch");
                if ("DIRECT".equals(create.routing())) {
                    if (create.room().maxPlayers() > Protocol.MAX_PLAYERS_DIRECT
                            || create.directPort() < 1 || create.directPort() > 65535
                            || create.instanceId() == null || create.instanceId().isBlank()
                            || create.instanceId().length() > 128
                            || create.certificateSha256() == null
                            || !create.certificateSha256().matches("[0-9a-f]{64}")) {
                        invalid("direct room creation");
                    }
                } else if (create.directPort() != 0) invalid("relay room port");
            }
            case ControlMessage.RoomCreated created -> {
                requireText(created.roomId(), "room ID");
                if (created.accessEpoch() < 0 || created.inviteEpoch() < 0) invalid("room epochs");
            }
            case ControlMessage.RoomActivate activate -> {
                requireText(activate.roomId(), "room ID");
                requireText(activate.instanceId(), "room instance");
            }
            case ControlMessage.RoomActivated activated -> {
                requireText(activated.roomId(), "room ID");
                requireText(activated.instanceId(), "room instance");
            }
            case ControlMessage.MasterRequestCancel cancel -> {
                if (cancel.requestId() <= 0) invalid("request cancellation");
            }
            case ControlMessage.RoomCreateRejected rejected -> requireText(rejected.reason(), "create rejection");
            case ControlMessage.RoomListRequest request -> {
                if (request.page() < 0) invalid("room list page");
            }
            case ControlMessage.RoomListResult result -> {
                validatePage(result.page(), result.totalPages(), true);
                requireItems(result.rooms(), "room list");
                if (result.rooms().size() > Protocol.MAX_PLAYERS_RELAY) invalid("room list size");
                for (ControlMessage.RoomSummary room : result.rooms()) validateSummary(room);
            }
            case ControlMessage.RoomJoinRequest request -> requireText(request.roomId(), "room ID");
            case ControlMessage.RoomJoinResult result -> {
                requireText(result.roomId(), "room ID");
                requireText(result.instanceId(), "room instance");
                validateGrantRoute(result.grantReference(), result.routeGeneration());
                validateRouting(result.routing());
                requireText(result.hostServerId(), "host server ID");
                requireText(result.determinismFingerprint(), "determinism fingerprint");
                if (result.directPort() < 0 || result.directPort() > 65535) invalid("direct port");
                if ("DIRECT".equals(result.routing())
                        && (result.directPort() == 0 || result.directHost() == null
                        || result.directHost().isBlank()
                        || result.certificateSha256() == null
                        || !result.certificateSha256().matches("[0-9a-f]{64}"))) {
                    invalid("direct room route");
                }
            }
            case ControlMessage.RoomJoinRejected rejected -> requireText(rejected.reason(), "join rejection");
            case ControlMessage.RoomGrantRedeem request -> {
                requireText(request.roomId(), "room ID");
                requireText(request.instanceId(), "room instance");
                validateGrantRoute(request.grantReference(), request.routeGeneration());
                requireText(request.fingerprint(), "guest identity");
            }
            case ControlMessage.RoomGrantDecision decision -> {
                requireText(decision.roomId(), "room ID");
                requireText(decision.instanceId(), "room instance");
                validateGrantRoute(decision.grantReference(), decision.routeGeneration());
                requireText(decision.fingerprint(), "guest identity");
            }
            case ControlMessage.RoomRouteRetire retire -> {
                requireText(retire.roomId(), "room ID");
                requireText(retire.instanceId(), "room instance");
                validateGrantRoute(retire.grantReference(), retire.routeGeneration());
            }
            case ControlMessage.RoomRouteRetireNotice notice -> {
                requireText(notice.roomId(), "room ID");
                requireText(notice.instanceId(), "room instance");
                validateGrantRoute(notice.grantReference(), notice.routeGeneration());
                if (notice.retirementId() <= 0) invalid("retirement ID");
            }
            case ControlMessage.RoomRouteRetireAck ack -> {
                requireText(ack.roomId(), "room ID");
                requireText(ack.instanceId(), "room instance");
                validateGrantRoute(ack.grantReference(), ack.routeGeneration());
                if (ack.retirementId() <= 0) invalid("retirement ID");
            }
            case ControlMessage.RoomRouteRenewed renewed -> {
                if (renewed.join() == null || renewed.join().grantReference() == null
                        || renewed.join().routeGeneration() <= 0) invalid("renewed route");
            }
            case ControlMessage.RoomRouteRetired retired -> {
                requireText(retired.roomId(), "room ID");
                requireText(retired.instanceId(), "room instance");
                validateGrantRoute(retired.grantReference(), retired.routeGeneration());
            }
            case ControlMessage.RoomRouteUnavailable unavailable ->
                    requireText(unavailable.reason(), "route unavailable reason");
            case ControlMessage.RoomLeave leave -> requireText(leave.roomId(), "room ID");
            case ControlMessage.Heartbeat heartbeat -> {
                requireText(heartbeat.roomId(), "room ID");
                if (heartbeat.playerCount() < 0 || heartbeat.playerCount() > Protocol.MAX_PLAYERS_RELAY) {
                    invalid("player count");
                }
            }
            case ControlMessage.PowChallenge challenge -> {
                requireText(challenge.kind(), "proof of work kind");
                if (challenge.prefixBase64() == null) invalid("proof of work prefix");
                if (challenge.difficultyBits() < 0 || challenge.difficultyBits() > 256) invalid("difficulty");
            }
            case ControlMessage.PowSolution solution -> requireText(solution.kind(), "proof of work kind");
            case ControlMessage.RelayAttach attach -> {
                requireText(attach.roomId(), "room ID");
                validateGrantRoute(attach.grantReference(), attach.routeGeneration());
            }
            case ControlMessage.RelayGuestOpen open -> {
                validateGuest(open.guestId());
                requireText(open.roomId(), "tunnel room ID");
                requireText(open.instanceId(), "tunnel instance ID");
                requireText(open.fingerprint(), "tunnel fingerprint");
                validateGrantRoute(open.grantReference(), open.routeGeneration());
                if (open.registrationGeneration() < 1) invalid("tunnel registration generation");
            }
            case ControlMessage.RelayGuestClose close -> {
                validateGuest(close.guestId());
                requireText(close.reason(), "guest close reason");
            }
            case ControlMessage.RelayGuestText relay -> {
                validateGuest(relay.guestId());
                if (relay.text() == null) invalid("relay text");
            }
            case ControlMessage.StandingsPageRequest request -> {
                if (request.page() < 0) invalid("standings page");
            }
            case ControlMessage.StandingsPage page -> validatePage(page.page(), page.totalPages(), false);
            case ControlMessage.RankUpdate rank -> {
                if (rank.rank() < 1 || rank.bestTimeFrames() < 1) invalid("rank update");
            }
            case ControlMessage.RoomTrackUpdate update -> {
                requireText(update.roomId(), "room ID");
                validateTrack(update.zone(), update.act());
            }
            case ControlMessage.RoomAccessChallenge challenge -> {
                requireText(challenge.challengeId(), "access challenge");
                requireText(challenge.instanceId(), "room instance");
                if (challenge.accessEpoch() < 0 || challenge.accessPolicy() == null) invalid("access state");
            }
            case ControlMessage.RoomAccessSubmit submit -> {
                requireText(submit.challengeId(), "access challenge");
                if (submit.password() == null) invalid("room password");
            }
            case ControlMessage.RoomAccessState state -> {
                requireText(state.instanceId(), "room instance");
                if (state.accessPolicy() == null || state.accessEpoch() < 0
                        || state.inviteEpoch() < 0) invalid("access state");
            }
            case ControlMessage.RoomPasswordChange change -> {
                if (change.password() == null) invalid("room password");
            }
            case ControlMessage.RoomPasswordChanged changed -> {
                if (changed.accessEpoch() < 0) invalid("access epoch");
            }
            case ControlMessage.RoomInviteRequest request -> {
                if (request.disclosureId() < 0) invalid("disclosure ID");
            }
            case ControlMessage.RoomInviteInvalidated invalidated -> requireText(invalidated.instanceId(), "room instance");
            case ControlMessage.RoomInviteRotate ignored -> { }
            case ControlMessage.RoomInviteSharing ignored -> { }
            case ControlMessage.RoomInvite invite -> {
                requireText(invite.code(), "invite code");
                requireText(invite.instanceId(), "room instance");
                if (invite.accessEpoch() < 0 || invite.inviteEpoch() < 0
                        || invite.expiresAtMillis() < 0 || invite.disclosureId() < 0) invalid("invite");
            }
            case ControlMessage.RoomInviteRejected rejected -> {
                requireText(rejected.reason(), "invite rejection");
                if (rejected.retryAfterMillis() < 0) invalid("invite retry");
            }
            case ControlMessage.RoomInviteResolve resolve -> requireText(resolve.code(), "invite code");
            case ControlMessage.RoomInviteControl control -> {
                requireText(control.roomId(), "room ID");
                requireText(control.instanceId(), "room instance");
                if (control.operation() == null || control.accessEpoch() < 0
                        || control.inviteEpoch() < 0) invalid("invite control");
            }
            case ControlMessage.RoomInviteControlResult result -> {
                if (result.accessPolicy() == null) invalid("invite control result");
                if (result.invite() != null) validateSemantics(result.invite());
            }
            case ControlMessage.RoomChatModeUpdate update -> {
                requireText(update.roomId(), "room ID");
                validateChatMode(update.mode());
            }
        }
    }

    private static void invalid(String field) {
        throw new ProtocolViolationException("invalid " + field);
    }

    private static void validateAppearance(String appearance) {
        if (appearance == null) invalid("appearance");
        try {
            com.openggf.net.identity.PlayerAppearance.decode(appearance);
        } catch (IllegalArgumentException malformed) {
            invalid("appearance");
        }
    }

    private static void validateReadyRule(String rule) {
        if (rule == null) invalid("ready rule");
        try {
            com.openggf.net.protocol.ReadyRules.valueOf(rule);
        } catch (IllegalArgumentException unknown) {
            invalid("ready rule");
        }
    }

    private static void validateChatMode(String mode) {
        if (ChatMode.parse(mode) == null) invalid("chat mode");
    }

    private static void validateQuickPhrase(String phrase) {
        if (QuickChatPhrase.parse(phrase) == null) invalid("quick phrase");
    }

    private static void validateChat(String value) {
        if (value == null || value.length() > Protocol.MAX_CHAT_CHARS) invalid("chat text");
    }

    private static void validateAttempt(int attemptId) {
        if (attemptId < 0) invalid("attempt ID");
    }

    private static void validateHash(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) invalid("hash");
    }

    private static void validateTrackKey(String key) {
        if (key == null || !key.matches("[a-z0-9]{1,8}:[0-9]{1,3}:[0-9]{1,3}")) invalid("track key");
    }

    private static void validateUniqueTrackKeys(List<String> keys, int maxCount) {
        requireItems(keys, "vote tracks");
        if (keys.size() > maxCount) invalid("vote tracks");
        if (new java.util.HashSet<>(keys).size() != keys.size()) invalid("duplicate vote track");
        keys.forEach(ControlJsonCodec::validateTrackKey);
    }

    private static void validateGuest(int guestId) {
        if (guestId < 0) invalid("guest ID");
    }

    private static void validateGrantRoute(String reference, long generation) {
        if (reference == null || reference.isBlank() || reference.length() > 128
                || generation <= 0) invalid("grant route");
    }

    private static void validateRouting(String routing) {
        if (!"DIRECT".equals(routing) && !"RELAY".equals(routing)) invalid("routing");
    }

    private static void validatePage(int page, int totalPages, boolean list) {
        if (page < 0 || (totalPages < 0 && (!list || totalPages != Protocol.ROOM_LIST_RATE_LIMITED))) {
            invalid("page");
        }
    }

    private static void validateTrack(int zone, int act) {
        if (zone < 0 || zone > 999 || act < 0 || act > 999) invalid("track");
    }

    private static void validateRoom(ControlMessage.RoomDescriptor room) {
        if (room == null) invalid("room");
        requireText(room.name(), "room name");
        requireText(room.gameId(), "room game");
        validateTrack(room.zone(), room.act());
        if (room.maxPlayers() < 1 || room.maxPlayers() > Protocol.MAX_PLAYERS_RELAY
                || room.accessPolicy() == null) invalid("room capacity");
        validateCharacterPolicy(room.characterPolicy(), room.lockedCharacter());
        if ("LOCKED".equals(room.characterPolicy())) {
            requireText(room.lockedCharacter(), "locked character");
        }
        validateChatMode(room.chatMode());
    }

    private static void validateSummary(ControlMessage.RoomSummary summary) {
        if (summary == null) invalid("room summary");
        requireText(summary.roomId(), "room ID");
        requireText(summary.name(), "room name");
        requireText(summary.gameId(), "room game");
        validateTrack(summary.zone(), summary.act());
        if (summary.maxPlayers() < 1 || summary.maxPlayers() > Protocol.MAX_PLAYERS_RELAY
                || summary.playerCount() < 0 || summary.playerCount() > summary.maxPlayers()) {
            invalid("room summary capacity");
        }
        validateCharacterPolicy(summary.characterPolicy(), null);
        validateRouting(summary.routing());
        validateChatMode(summary.chatMode());
    }

    private static void validateCharacterPolicy(String policy, String lockedCharacter) {
        if (!"OPEN".equals(policy) && !"LOCKED".equals(policy)) invalid("character policy");
        if ("LOCKED".equals(policy) && lockedCharacter != null) requireText(lockedCharacter, "locked character");
    }

    private static void validatePlayers(java.util.List<ControlMessage.PlayerInfo> players) {
        requireItems(players, "players");
        if (players.size() > Protocol.MAX_PLAYERS_RELAY) {
            throw new ProtocolViolationException("too many players");
        }
        Set<Integer> slots = new java.util.HashSet<>();
        Set<String> identities = new java.util.HashSet<>();
        for (ControlMessage.PlayerInfo player : players) {
            validatePlayer(player);
            if (!slots.add(player.slot()) || !identities.add(player.fingerprint())) {
                throw new ProtocolViolationException("duplicate player slot or identity");
            }
        }
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= Protocol.MAX_PLAYERS_RELAY) {
            throw new ProtocolViolationException("invalid player slot");
        }
    }

    private static void validatePlayer(ControlMessage.PlayerInfo player) {
        if (player == null) {
            throw new ProtocolViolationException("missing player");
        }
        validateSlot(player.slot());
        requireText(player.fingerprint(), "player identity");
        requireText(player.displayName(), "player name");
        requireText(player.character(), "player character");
    }

    private static void validateRoundConfig(ControlMessage.RoundConfig config) {
        requireText(config.gameId(), "round game");
        validateTrack(config.zone(), config.act());
        if (config.windowSeconds() <= 0 || config.windowSeconds() > 3600
                || (!"OPEN".equals(config.characterPolicy())
                && !"LOCKED".equals(config.characterPolicy()))) {
            throw new ProtocolViolationException("invalid round config");
        }
        if ("LOCKED".equals(config.characterPolicy())) {
            requireText(config.lockedCharacter(), "locked character");
        }
    }

    private static void validateRaceLaunch(ControlMessage.RaceLaunch launch,
                                           ControlMessage.RoundConfig config,
                                           long countdown, long deadline) {
        if (config == null || launch.roundId() <= 0 || launch.planRevision() <= 0
                || !java.util.Objects.equals(config, launch.config())
                || launch.countdownEndsAtHubMillis() != countdown
                || launch.deadlineHubMillis() != deadline) {
            throw new ProtocolViolationException("invalid round launch");
        }
        validateSlot(launch.slot());
        requireText(launch.participantId(), "launch participant");
        requireText(launch.fingerprint(), "launch identity");
        requireText(launch.effectiveCharacter(), "launch character");
        if ("LOCKED".equals(config.characterPolicy())
                && !config.lockedCharacter().equals(launch.effectiveCharacter())) {
            throw new ProtocolViolationException("invalid locked launch character");
        }
    }

    private static void validateStandings(
            java.util.List<ControlMessage.StandingsRow> rows, String field) {
        requireItems(rows, field);
        for (ControlMessage.StandingsRow row : rows) {
            if (row.slot() < 0 || row.slot() >= Protocol.MAX_PLAYERS_RELAY
                    || row.bestTimeFrames() <= 0 || row.rank() <= 0) {
                throw new ProtocolViolationException("invalid " + field);
            }
            requireText(row.displayName(), "standing name");
            requireText(row.character(), "standing character");
            requireText(row.verifyState(), "standing verification state");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ProtocolViolationException("invalid " + field);
        }
    }

    private static void requireItems(java.util.List<?> items, String field) {
        if (items == null || items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ProtocolViolationException("invalid " + field);
        }
    }
}
