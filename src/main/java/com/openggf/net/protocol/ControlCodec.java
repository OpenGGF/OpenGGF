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

/** Envelope codec for control messages: {@code {"v":2,"token":...,"msg":{...}}}. */
public final class ControlCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    private static final Set<String> ENVELOPE_FIELDS = Set.of("v", "token", "msg");
    private static final Map<String, Class<? extends ControlMessage>> MESSAGE_TYPES =
            Arrays.stream(ControlMessage.class.getPermittedSubclasses())
                    .map(type -> type.asSubclass(ControlMessage.class))
                    .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    public record DecodedControl(String token, ControlMessage message) {
    }

    private ControlCodec() {
    }

    public static String encode(String tokenOrNull, ControlMessage message) {
        try {
            ObjectNode envelope = MAPPER.createObjectNode();
            envelope.put("v", Protocol.VERSION);
            if (tokenOrNull == null) {
                envelope.putNull("token");
            } else {
                envelope.put("token", tokenOrNull);
            }
            ObjectNode body = MAPPER.valueToTree(message);
            body.put("type", message.getClass().getSimpleName());
            envelope.set("msg", body);
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new ProtocolViolationException(
                    "failed to encode " + message.getClass().getSimpleName(), e);
        }
    }

    public static DecodedControl decode(String text) {
        return decode(text, Protocol.MAX_CONTROL_BYTES);
    }

    /** Decodes under an explicit transport cap; master tunnel wrappers use the larger cap. */
    public static DecodedControl decode(String text, int maxBytes) {
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
            JsonNode root = MAPPER.readTree(text);
            if (root == null || !root.isObject()) {
                throw new ProtocolViolationException("control envelope must be an object");
            }
            root.fieldNames().forEachRemaining(field -> {
                if (!ENVELOPE_FIELDS.contains(field)) {
                    throw new ProtocolViolationException(
                            "unsupported control envelope field " + field);
                }
            });
            JsonNode version = root.get("v");
            if (version == null || !version.isIntegralNumber()
                    || version.intValue() != Protocol.VERSION) {
                throw new ProtocolViolationException("unsupported protocol version " + version);
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
                            ? null : MESSAGE_TYPES.get(typeNode.textValue());
            if (messageType == null) {
                throw new ProtocolViolationException("unsupported control message type " + typeNode);
            }
            ObjectNode messageBody = ((ObjectNode) msg).deepCopy();
            messageBody.remove("type");
            ControlMessage message = MAPPER.treeToValue(messageBody, messageType);
            validateClientState(message);
            if (message instanceof ControlMessage.RelayGuestText relay
                    && relay.text().getBytes(StandardCharsets.UTF_8).length
                    > Protocol.MAX_CONTROL_BYTES) {
                throw new ProtocolViolationException("relay guest text exceeds inner frame cap");
            }
            return new DecodedControl(tokenValue, message);
        } catch (ProtocolViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolViolationException("undecodable control frame", e);
        }
    }

    private static void validateClientState(ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomState state -> validatePlayers(state.players());
            case ControlMessage.JoinAccepted accepted -> {
                requireText(accepted.sessionToken(), "session token");
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
        for (ControlMessage.PlayerInfo player : players) {
            if (player.slot() < 0 || player.slot() >= Protocol.MAX_PLAYERS_RELAY) {
                throw new ProtocolViolationException("invalid player slot");
            }
            requireText(player.fingerprint(), "player identity");
            requireText(player.displayName(), "player name");
            requireText(player.character(), "player character");
        }
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
