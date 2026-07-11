package com.openggf.net.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Envelope codec for control messages: {@code {"v":1,"token":...,"msg":{...}}}. */
public final class ControlCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
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
            String tokenValue = token == null || token.isNull() ? null : token.asText();
            JsonNode typeNode = msg.get("type");
            Class<? extends ControlMessage> messageType =
                    typeNode == null ? null : MESSAGE_TYPES.get(typeNode.asText());
            if (messageType == null) {
                throw new ProtocolViolationException("unsupported control message type " + typeNode);
            }
            ControlMessage message = MAPPER.treeToValue(msg, messageType);
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
}
