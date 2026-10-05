package slaytherobotnik.art;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Card illustrations as text, read from {@code art/cards.txt}. Each line gives a card id and
 * the layers drawn in its art window, back to front, separated by semicolons:
 *
 * <pre>
 * sonic:spin_dash: bg 902424; fx speed; hero sonic anim=0x9 -4,2; rom explosion 1 20,-4
 * </pre>
 *
 * Layers:
 * <ul>
 *   <li>{@code bg RRGGBB} the backdrop colour (default: by card type);</li>
 *   <li>{@code hero CHARACTER FRAME} a playable character's mapping frame, or
 *       {@code anim=ID} to play one of its animation scripts;</li>
 *   <li>{@code rom KEY FRAME} a frame of a ROM sprite named in {@link RomSprites}, or
 *       {@code anim=F1/F2/...@TICKS} to cycle frames;</li>
 *   <li>{@code icon NAME} a text-art icon from {@code icons.txt};</li>
 *   <li>{@code monitor FACE} an item monitor: a Map_Monitor frame number (3-10) or a short
 *       text such as {@code 1UP} painted on its screen;</li>
 *   <li>{@code fx NAME} a drawn effect: speed, burst, sparkle, rings, fire, water, zap, stars.</li>
 * </ul>
 * After the required words, a layer takes any of: {@code X,Y} (offset from the window's
 * centre, in pixels of the large card), {@code flip}, {@code xN} (scale, e.g. {@code x2} or
 * {@code x0.5}) and {@code aN} (opacity 0-1). Lines starting with {@code #} are comments.
 */
public final class CardRecipes {
    /** One drawn layer. {@code frames} holds one frame, or several to cycle every {@code ticks}. */
    public record Layer(String kind, String key, int[] frames, int ticks, boolean anim, float x, float y,
            boolean flip, float scale, float alpha) {
    }

    private final Map<String, List<Layer>> recipes = new HashMap<>();

    public CardRecipes(byte[] text) {
        String[] lines = new String(text, StandardCharsets.UTF_8).split("\r?\n");
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(": ");
            if (colon < 0) {
                throw new IllegalArgumentException("cards.txt line " + (n + 1) + ": expected 'card-id: layers'");
            }
            String id = line.substring(0, colon).strip();
            List<Layer> layers = new ArrayList<>();
            for (String part : line.substring(colon + 2).split(";")) {
                if (!part.isBlank()) {
                    layers.add(parseLayer(part.strip(), n + 1));
                }
            }
            recipes.put(id, List.copyOf(layers));
        }
    }

    /** The layers for a card, or null when it has no recipe. */
    public List<Layer> recipe(String cardId) {
        return recipes.get(cardId);
    }

    public Map<String, List<Layer>> all() {
        return Map.copyOf(recipes);
    }

    private static Layer parseLayer(String text, int line) {
        String[] t = text.split("\\s+");
        String kind = t[0];
        int required = switch (kind) {
            case "hero", "rom" -> 3;
            case "bg", "icon", "fx", "monitor" -> 2;
            default -> throw new IllegalArgumentException("cards.txt line " + line + ": unknown layer '" + kind + "'");
        };
        if (t.length < required) {
            throw new IllegalArgumentException("cards.txt line " + line + ": '" + text + "' is missing words");
        }
        String key = t[1];
        int[] frames = {0};
        int ticks = 8;
        boolean anim = false;
        if (required == 3) {
            String f = t[2];
            if (f.startsWith("anim=")) {
                anim = true;
                String spec = f.substring(5);
                int at = spec.indexOf('@');
                if (at >= 0) {
                    ticks = Integer.parseInt(spec.substring(at + 1));
                    spec = spec.substring(0, at);
                }
                String[] list = spec.split("/");
                frames = new int[list.length];
                for (int i = 0; i < list.length; i++) {
                    frames[i] = Integer.decode(list[i]);
                }
            } else {
                frames = new int[] {Integer.decode(f)};
            }
        }
        float x = 0;
        float y = 0;
        boolean flip = false;
        float scale = 1;
        float alpha = 1;
        for (int i = required; i < t.length; i++) {
            String w = t[i];
            if (w.equals("flip")) {
                flip = true;
            } else if (w.startsWith("x") && w.length() > 1 && Character.isDigit(w.charAt(1))) {
                scale = Float.parseFloat(w.substring(1));
            } else if (w.startsWith("a") && w.length() > 1 && Character.isDigit(w.charAt(1))) {
                alpha = Float.parseFloat(w.substring(1));
            } else if (w.contains(",")) {
                String[] xy = w.split(",");
                x = Float.parseFloat(xy[0]);
                y = Float.parseFloat(xy[1]);
            } else {
                throw new IllegalArgumentException("cards.txt line " + line + ": unknown word '" + w + "'");
            }
        }
        return new Layer(kind, key, frames, ticks, anim, x, y, flip, scale, alpha);
    }
}
