package com.openggf.net.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Envelope codec for control messages: {@code {"v":6,"token":...,"msg":{...}}}.
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
        return decode(text, Protocol.MAX_CONTROL_BYTES);
    }

    /** Room traffic cannot use the broker's slotless master-admission marker. */
    public Decoded decodeRoom(String text) {
        Decoded decoded = decode(text);
        if (decoded.message() instanceof ControlMessage.JoinAccepted accepted
                && (accepted.playerSlot() < 0 || accepted.room() == null)) {
            throw new ProtocolViolationException("invalid room admission");
        }
        return decoded;
    }

    /** Decodes under an explicit transport cap; master tunnel wrappers use the larger cap. */
    public Decoded decode(String text, int maxBytes) {
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
            ObjectNode messageBody = ((ObjectNode) msg).deepCopy();
            messageBody.remove("type");
            ControlMessage message = mapper.treeToValue(messageBody, messageType);
            validateClientState(message);
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

    private static void validateClientState(ControlMessage message) {
        switch (message) {
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
                    validateStandings(snapshot.standings(), "snapshot standings");
                }
            }
            case ControlMessage.RoundStart start -> {
                if (start.config() == null) {
                    throw new ProtocolViolationException("missing round config");
                }
                validateRoundConfig(start.config());
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
        if (config.zone() < 0 || config.act() < 0 || config.windowSeconds() <= 0
                || (!"OPEN".equals(config.characterPolicy())
                && !"LOCKED".equals(config.characterPolicy()))) {
            throw new ProtocolViolationException("invalid round config");
        }
        if ("LOCKED".equals(config.characterPolicy())) {
            requireText(config.lockedCharacter(), "locked character");
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
