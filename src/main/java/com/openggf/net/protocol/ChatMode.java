package com.openggf.net.protocol;

/** Authoritative room communication capabilities, ordered from most to least permissive. */
@com.openggf.game.ModApi
public enum ChatMode {
    FULL_CHAT("Full Chat", true, true),
    QUICK_CHAT("Quick Chat", false, true),
    OFF("Off", false, false);

    private final String label;
    private final boolean text;
    private final boolean quickChat;

    ChatMode(String label, boolean text, boolean quickChat) {
        this.label = label;
        this.text = text;
        this.quickChat = quickChat;
    }

    public String label() {
        return label;
    }

    public boolean allowsText() {
        return text;
    }

    public boolean allowsQuickChat() {
        return quickChat;
    }

    /** The capabilities allowed by both this choice and the server's ceiling. */
    public ChatMode intersect(ChatMode limit) {
        java.util.Objects.requireNonNull(limit, "limit");
        if (!quickChat || !limit.quickChat) return OFF;
        return text && limit.text ? FULL_CHAT : QUICK_CHAT;
    }

    /** The exact wire ID, or null for an unknown, missing or noncanonical ID. */
    public static ChatMode parse(String name) {
        if (name == null) return null;
        for (ChatMode mode : values()) {
            if (mode.name().equals(name)) return mode;
        }
        return null;
    }
}
