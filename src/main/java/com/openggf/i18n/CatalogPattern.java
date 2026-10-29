package com.openggf.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.MessagePattern;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One maintained ICU parser owns both authoring validation and runtime grammar. */
final class CatalogPattern {
    enum Kind { TEXT, NUMBER }
    private final MessageFormat formatter;
    private final Map<String, Kind> arguments;
    private final boolean isolate;
    private final java.util.Set<String> selectors = new java.util.HashSet<>();
    private final Map<String, java.util.Set<String>> selectValues = new LinkedHashMap<>();

    CatalogPattern(String pattern, JsonNode metadata, ULocale locale, Map<String, Kind> contract) {
        if (pattern.length() > 100_000) throw new IllegalArgumentException("Message is too long");
        MessagePattern parsed = new MessagePattern(pattern);
        Map<String, Kind> found = new LinkedHashMap<>();
        for (int i = 0; i < parsed.countParts(); i++) {
            var part = parsed.getPart(i);
            if (part.getType() != MessagePattern.Part.Type.ARG_START) continue;
            var namePart = parsed.getPart(i + 1);
            if (namePart.getType() != MessagePattern.Part.Type.ARG_NAME)
                throw new IllegalArgumentException("Use named message arguments");
            String name = parsed.getSubstring(namePart);
            if (part.getArgType() == MessagePattern.ArgType.SELECT) {
                selectors.add(name);
                var values = selectValues.computeIfAbsent(name, ignored -> new java.util.HashSet<>());
                for (int j = i + 2, end = parsed.getLimitPartIndex(i); j < end; j++) {
                    var selector = parsed.getPart(j);
                    if (selector.getType() == MessagePattern.Part.Type.MSG_START) j = parsed.getLimitPartIndex(j);
                    else if (selector.getType() == MessagePattern.Part.Type.ARG_SELECTOR) values.add(parsed.getSubstring(selector));
                }
            }
            Kind kind = switch (part.getArgType()) {
                case NONE, SELECT -> Kind.TEXT;
                case PLURAL, SELECTORDINAL -> Kind.NUMBER;
                case SIMPLE -> {
                    String type = parsed.getSubstring(parsed.getPart(i + 2));
                    if (!type.equals("number")) throw new IllegalArgumentException("Unsupported formatter " + type);
                    yield Kind.NUMBER;
                }
                default -> throw new IllegalArgumentException("Unsupported argument " + part.getArgType());
            };
            if (part.getArgType() == MessagePattern.ArgType.NONE) {
                String type = metadata.path("placeholders").path(name).path("type").asText();
                if (isNumeric(type)) kind = Kind.NUMBER;
                if (contract != null && contract.containsKey(name)) kind = contract.get(name);
            }
            Kind previous = found.putIfAbsent(name, kind);
            if (previous != null && previous != kind) throw new IllegalArgumentException("Inconsistent argument " + name);
            if (part.getArgType() == MessagePattern.ArgType.PLURAL
                    || part.getArgType() == MessagePattern.ArgType.SELECTORDINAL) {
                var type = part.getArgType() == MessagePattern.ArgType.SELECTORDINAL
                        ? PluralRules.PluralType.ORDINAL : PluralRules.PluralType.CARDINAL;
                var categories = PluralRules.forLocale(locale, type).getKeywords();
                int end = parsed.getLimitPartIndex(i);
                for (int j = i + 2; j < end; j++) {
                    var selector = parsed.getPart(j);
                    if (selector.getType() == MessagePattern.Part.Type.MSG_START) j = parsed.getLimitPartIndex(j);
                    else if (selector.getType() == MessagePattern.Part.Type.ARG_SELECTOR) {
                        String keyword = parsed.getSubstring(selector);
                        if (!keyword.startsWith("=") && !categories.contains(keyword))
                            throw new IllegalArgumentException("Invalid plural category for " + locale + ": " + keyword);
                    }
                }
            }
        }
        if (contract != null && !contract.equals(found))
            throw new IllegalArgumentException("Expected arguments " + contract + ", got " + found);
        JsonNode schema = metadata.path("placeholders");
        if (!schema.isMissingNode()) {
            if (!schema.isObject()) throw new IllegalArgumentException("placeholders must be an object");
            var names = new java.util.HashSet<String>();
            schema.fieldNames().forEachRemaining(names::add);
            if (!names.equals(found.keySet())) throw new IllegalArgumentException("Placeholder metadata differs from message");
            schema.fields().forEachRemaining(entry -> {
                String type = entry.getValue().path("type").asText("String");
                if (!isNumeric(type) && !type.equals("String") && !type.equals("string"))
                    throw new IllegalArgumentException("Unsupported placeholder type " + type);
                if (found.get(entry.getKey()) != (isNumeric(type) ? Kind.NUMBER : Kind.TEXT))
                    throw new IllegalArgumentException("Placeholder type differs: " + entry.getKey());
            });
        }
        arguments = Map.copyOf(found);
        formatter = new MessageFormat(pattern, locale);
        isolate = locale.isRightToLeft();
    }

    private static boolean containsRightToLeft(String value) {
        return value.codePoints().anyMatch(codePoint -> {
            byte direction = Character.getDirectionality(codePoint);
            return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
        });
    }

    private static boolean isNumeric(String type) {
        return switch (type) { case "int", "long", "double", "num", "number", "Number" -> true; default -> false; };
    }

    Map<String, Kind> arguments() { return arguments; }

    void validateTranslationOf(CatalogPattern source) {
        if (!source.selectValues.equals(selectValues))
            throw new IllegalArgumentException("Semantic select keys differ: expected " + source.selectValues + ", got " + selectValues);
    }

    synchronized String format(Map<String, ?> values) {
        Objects.requireNonNull(values, "arguments");
        if (!values.keySet().equals(arguments.keySet()))
            throw new IllegalArgumentException("Expected arguments " + arguments.keySet() + ", got " + values.keySet());
        Map<String, Object> safe = new LinkedHashMap<>();
        arguments.forEach((name, kind) -> {
            Object value = Objects.requireNonNull(values.get(name), name);
            if (kind == Kind.NUMBER && !(value instanceof Number)) throw new IllegalArgumentException("Numeric argument required: " + name);
            if (kind == Kind.TEXT && !(value instanceof CharSequence)) throw new IllegalArgumentException("Text argument required: " + name);
            safe.put(name, kind == Kind.TEXT && (isolate || containsRightToLeft(value.toString())) && !selectors.contains(name) && !value.toString().isEmpty()
                    ? "\u2068" + value + "\u2069" : value);
        });
        return formatter.format(safe);
    }
}
