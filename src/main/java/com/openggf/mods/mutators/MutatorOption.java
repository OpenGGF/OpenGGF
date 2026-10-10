package com.openggf.mods.mutators;

import com.openggf.game.ModKeySyntax;

import java.util.List;
import java.util.Objects;

/** Common host widgets with stable, bounded serialized values and an explicit edit scope. */
@com.openggf.game.ModApi
public sealed interface MutatorOption permits MutatorOption.IntegerSlider,
        MutatorOption.Checkbox, MutatorOption.Choice {
    String id();
    String label();
    String help();
    MutatorScope editScope();
    Object defaultValue();
    /** Rejects the wrong type, unknown enum token or value outside this schema. */
    void validate(Object value);

    @com.openggf.game.ModApi
    record IntegerSlider(String id, String label, String help, MutatorScope editScope,
                         Integer defaultValue, int minimum, int maximum, int step,
                         String unit) implements MutatorOption {
        public IntegerSlider {
            metadata(id, label, help, editScope);
            Objects.requireNonNull(unit, "unit");
            if (unit.length() > 32 || minimum > maximum || step < 1
                    || (long) maximum - minimum > 1_000_000L
                    || ((long) maximum - minimum) % step != 0) {
                throw new IllegalArgumentException("Invalid bounded slider schema");
            }
            checkInteger(defaultValue, minimum, maximum, step);
        }
        @Override public void validate(Object value) {
            checkInteger(value, minimum, maximum, step);
        }
    }

    @com.openggf.game.ModApi
    record Checkbox(String id, String label, String help, MutatorScope editScope,
                    Boolean defaultValue) implements MutatorOption {
        public Checkbox {
            metadata(id, label, help, editScope);
            Objects.requireNonNull(defaultValue, "defaultValue");
        }
        @Override public void validate(Object value) {
            if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected a checkbox value");
        }
    }

    @com.openggf.game.ModApi
    record Choice(String id, String label, String help, MutatorScope editScope,
                  String defaultValue, List<String> tokens) implements MutatorOption {
        public Choice {
            metadata(id, label, help, editScope);
            tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
            if (tokens.isEmpty() || tokens.size() > 16 || tokens.stream().distinct().count() != tokens.size()) {
                throw new IllegalArgumentException("A choice needs 1..16 distinct tokens");
            }
            tokens.forEach(ModKeySyntax::requireLocalName);
            if (!tokens.contains(defaultValue)) throw new IllegalArgumentException("Unknown default choice");
        }
        @Override public void validate(Object value) {
            if (!(value instanceof String token) || !tokens.contains(token)) {
                throw new IllegalArgumentException("Unknown choice token");
            }
        }
    }

    private static void metadata(String id, String label, String help, MutatorScope scope) {
        ModKeySyntax.requireLocalName(id);
        if (Objects.requireNonNull(label, "label").isBlank() || label.length() > 80
                || Objects.requireNonNull(help, "help").length() > 512) {
            throw new IllegalArgumentException("Invalid option text");
        }
        Objects.requireNonNull(scope, "editScope");
    }

    private static void checkInteger(Object value, int minimum, int maximum, int step) {
        if (!(value instanceof Integer integer) || integer < minimum || integer > maximum
                || ((long) integer - minimum) % step != 0) {
            throw new IllegalArgumentException("Expected a bounded integer slider value");
        }
    }
}
