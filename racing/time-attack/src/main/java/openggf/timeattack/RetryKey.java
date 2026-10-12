package openggf.timeattack;

import java.util.Locale;

/** Names and codes for the GLFW keyboard positions used by SceneKeys and legacy config files. */
final class RetryKey {
    private RetryKey() { }

    static int parse(String value) {
        String name = value.strip().toUpperCase(Locale.ROOT);
        if (name.startsWith("GLFW_KEY_")) name = name.substring(9);
        else if (name.startsWith("KEY_")) name = name.substring(4);
        if (name.length() == 1 && Character.isLetterOrDigit(name.charAt(0))) return name.charAt(0);
        if (name.matches("F(?:[1-9]|1[0-9]|2[0-5])")) return 289 + Integer.parseInt(name.substring(1));
        if (name.matches("KP_[0-9]")) return 320 + name.charAt(3) - '0';
        int code = switch (name) {
            case "SPACE" -> 32; case "APOSTROPHE" -> 39; case "COMMA" -> 44;
            case "MINUS" -> 45; case "PERIOD" -> 46; case "SLASH" -> 47;
            case "SEMICOLON" -> 59; case "EQUAL" -> 61; case "LEFT_BRACKET" -> 91;
            case "BACKSLASH" -> 92; case "RIGHT_BRACKET" -> 93; case "GRAVE_ACCENT" -> 96;
            case "WORLD_1", "#" -> 161; case "WORLD_2" -> 162;
            case "ESCAPE" -> 256; case "ENTER" -> 257; case "TAB" -> 258;
            case "BACKSPACE" -> 259; case "INSERT" -> 260; case "DELETE" -> 261;
            case "RIGHT" -> 262; case "LEFT" -> 263; case "DOWN" -> 264; case "UP" -> 265;
            case "PAGE_UP" -> 266; case "PAGE_DOWN" -> 267; case "HOME" -> 268; case "END" -> 269;
            case "CAPS_LOCK" -> 280; case "SCROLL_LOCK" -> 281; case "NUM_LOCK" -> 282;
            case "PRINT_SCREEN" -> 283; case "PAUSE" -> 284;
            case "KP_DECIMAL" -> 330; case "KP_DIVIDE" -> 331; case "KP_MULTIPLY" -> 332;
            case "KP_SUBTRACT" -> 333; case "KP_ADD" -> 334; case "KP_ENTER" -> 335; case "KP_EQUAL" -> 336;
            case "LEFT_SHIFT" -> 340; case "LEFT_CONTROL" -> 341; case "LEFT_ALT" -> 342;
            case "LEFT_SUPER" -> 343; case "RIGHT_SHIFT" -> 344; case "RIGHT_CONTROL" -> 345;
            case "RIGHT_ALT" -> 346; case "RIGHT_SUPER" -> 347; case "MENU" -> 348;
            default -> Integer.parseInt(name);
        };
        if (!supported(code)) throw new IllegalArgumentException("Unsupported retry key: " + value);
        return code;
    }

    static boolean supported(int code) {
        return code == 32 || code == 39 || (code >= 44 && code <= 57) || code == 59 || code == 61
                || (code >= 65 && code <= 93) || code == 96 || code == 161 || code == 162
                || (code >= 256 && code <= 269) || (code >= 280 && code <= 284)
                || (code >= 290 && code <= 314) || (code >= 320 && code <= 336)
                || (code >= 340 && code <= 348);
    }

    static String label(int code) {
        if ((code >= 65 && code <= 90) || (code >= 48 && code <= 57)) return Character.toString((char) code);
        if (code >= 290 && code <= 314) return "F" + (code - 289);
        return switch (code) {
            case 32 -> "SPACE"; case 256 -> "ESCAPE"; case 257 -> "ENTER"; case 258 -> "TAB";
            case 259 -> "BACKSPACE"; case 260 -> "INSERT"; case 261 -> "DELETE";
            case 262 -> "RIGHT"; case 263 -> "LEFT"; case 264 -> "DOWN"; case 265 -> "UP";
            case 266 -> "PAGE_UP"; case 267 -> "PAGE_DOWN"; case 268 -> "HOME"; case 269 -> "END";
            default -> "KEY " + code;
        };
    }
}
