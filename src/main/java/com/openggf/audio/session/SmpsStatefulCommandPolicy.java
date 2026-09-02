package com.openggf.audio.session;

import java.util.Objects;

/**
 * Host-owned extension point for stateful SMPS commands.
 *
 * <p>Programs describe content; they do not choose host operations.  The
 * session installs this policy from its profile configuration so a future
 * command implementation can consume an immutable projection and return a
 * host operation without inspecting a game name or donor source.
 */
public interface SmpsStatefulCommandPolicy {
    SmpsStatefulCommandPolicy NONE = new SmpsStatefulCommandPolicy() {
        @Override
        public Identity identity() {
            return Identity.NONE;
        }
    };

    Identity identity();

    /**
     * Phase 1 deliberately has no stateful-command behavior.  The operation
     * type exists to keep a future host policy out of donor transitions.
     */
    default SmpsStatefulCommandOperation prepare(
            SmpsStatefulCommandOperation.Input input) {
        return SmpsStatefulCommandOperation.none(
                Objects.requireNonNull(input, "input"));
    }

    record Identity(String value) {
        public static final Identity NONE = new Identity("none");

        public Identity {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "stateful-command policy identity is required");
            }
        }
    }
}
