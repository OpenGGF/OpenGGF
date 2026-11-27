package com.openggf.mods.mutators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Engine-internal requested settings that a development capture host lays over the saved
 * preferences when a world opens. They are ordinary requests: the world's LAUNCH boundary
 * admits them, the same validation applies, and nothing is written to the player's saved
 * preferences. Creator code never supplies or sees these values.
 *
 * <p>Syntax of one request: {@code <id>[:<option>=<value>[,<option>=<value>...]]}, where
 * {@code <id>} is a mutator's local id, unique across the loaded owners, or
 * {@code owner/local} where two owners share a local id.
 * Slider values are integers, checkboxes take {@code true}/{@code false} (also on/off, yes/no),
 * choices take a declared token. Naming a mutator enables it.
 */
public record MutatorLaunchRequests(List<Request> requests) {
    private static final int MAX_REQUESTS = 32;

    public MutatorLaunchRequests {
        requests = List.copyOf(requests);
        if (requests.size() > MAX_REQUESTS) throw new IllegalArgumentException("At most 32 mutator requests");
        var seen = new java.util.HashSet<String>();
        for (var request : requests)
            if (!seen.add(request.id())) throw new IllegalArgumentException("Mutator requested twice: " + request.id());
    }

    /** One named mutator and its raw option text. */
    public record Request(String id, Map<String, String> options) {
        public Request {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || id.length() > 128) throw new IllegalArgumentException("Invalid mutator id: " + id);
            options = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(options));
            if (options.size() > 32) throw new IllegalArgumentException("Too many options for " + id);
        }
    }

    /** Parses repeated command-line specifications; syntax errors name the bad text. */
    public static MutatorLaunchRequests parse(List<String> specs) {
        var parsed = new ArrayList<Request>();
        for (String spec : specs) parsed.add(parseOne(spec));
        return new MutatorLaunchRequests(parsed);
    }

    static Request parseOne(String spec) {
        Objects.requireNonNull(spec, "spec");
        String text = spec.strip();
        int split = text.indexOf(':');
        String id = split < 0 ? text : text.substring(0, split), list = split < 0 ? "" : text.substring(split + 1);
        if (split >= 0 && list.isBlank()) throw new IllegalArgumentException("--mutator has ':' but no options: " + spec);
        if (id.isBlank()) throw new IllegalArgumentException("--mutator needs an id: " + spec);
        var options = new LinkedHashMap<String, String>();
        if (!list.isBlank()) {
            for (String pair : list.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0 || equals == pair.length() - 1)
                    throw new IllegalArgumentException("Expected option=value in --mutator " + spec + ": " + pair);
                String key = pair.substring(0, equals).strip(), value = pair.substring(equals + 1).strip();
                if (options.putIfAbsent(key, value) != null)
                    throw new IllegalArgumentException("Option given twice in --mutator " + spec + ": " + key);
            }
        }
        return new Request(id.strip(), options);
    }

    /**
     * Returns {@code requested} with each named mutator enabled and its options converted by
     * the definition's own widget types. Unknown ids/options and invalid values are rejected.
     */
    public Map<String, MutatorSessionState.Configuration> applyTo(List<OwnedMutator> definitions,
            Map<String, MutatorSessionState.Configuration> requested) {
        var result = new LinkedHashMap<>(requested);
        for (var request : requests) {
            OwnedMutator owned = resolve(definitions, request.id());
            var definition = owned.definition();
            var base = result.get(owned.key());
            var options = new LinkedHashMap<>(base == null ? definition.defaults() : base.options());
            for (var entry : request.options().entrySet()) {
                MutatorOption option;
                try { option = definition.option(entry.getKey()); }
                catch (IllegalArgumentException unknown) {
                    throw new IllegalArgumentException("Unknown option '" + entry.getKey() + "' for mutator "
                            + definition.localId() + "; options: " + definition.options().stream().map(MutatorOption::id).toList());
                }
                Object value = convert(option, entry.getValue(), definition.localId());
                option.validate(value);
                options.put(option.id(), value);
            }
            result.put(owned.key(), new MutatorSessionState.Configuration(true, options));
        }
        return Map.copyOf(result);
    }

    private static OwnedMutator resolve(List<OwnedMutator> definitions, String id) {
        String key = id.replace('/', ':');
        var exact = definitions.stream().filter(owned -> owned.key().equals(key)).toList();
        if (exact.size() == 1) return exact.getFirst();
        var local = definitions.stream().filter(owned -> owned.definition().localId().equals(id)).toList();
        if (local.size() == 1) return local.getFirst();
        if (local.size() > 1) throw new IllegalArgumentException("Mutator id " + id + " is ambiguous; use owner/local");
        throw new IllegalArgumentException("Unknown mutator " + id + "; available: "
                + definitions.stream().map(owned -> owned.definition().localId()).toList());
    }

    private static Object convert(MutatorOption option, String raw, String mutator) {
        return switch (option) {
            case MutatorOption.IntegerSlider slider -> {
                try { yield Integer.parseInt(raw); }
                catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException(mutator + "." + slider.id() + " expects an integer "
                            + slider.minimum() + ".." + slider.maximum() + ": " + raw);
                }
            }
            case MutatorOption.Checkbox checkbox -> switch (raw.toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes", "1" -> Boolean.TRUE;
                case "false", "off", "no", "0" -> Boolean.FALSE;
                default -> throw new IllegalArgumentException(mutator + "." + checkbox.id() + " expects true or false: " + raw);
            };
            case MutatorOption.Choice choice -> {
                if (!choice.tokens().contains(raw))
                    throw new IllegalArgumentException(mutator + "." + choice.id() + " expects one of " + choice.tokens() + ": " + raw);
                yield raw;
            }
        };
    }
}
