package com.openggf.mods.scene;

/**
 * Keyboard key codes for {@link SceneContext#keyDown} and {@link SceneContext#keyPressed}, so a
 * scene never writes raw numbers. They are the engine's (GLFW's) codes for the key's position on
 * a US layout: {@code A} is the key labelled A there, whatever the player's layout prints on it.
 * Letters {@code A..Z} and digits {@code DIGIT_0..DIGIT_9} are consecutive, so
 * {@code DIGIT_1 + i} is the key for digit {@code 1 + i}.
 *
 * <p>Holding Escape returns to the master title from any scene; a short press reaches the scene
 * like any other key.
 */
@com.openggf.game.ModApi
public final class SceneKeys {
    public static final int A = 65;
    public static final int B = 66;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int G = 71;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int T = 84;
    public static final int U = 85;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;
    public static final int DIGIT_0 = 48;
    public static final int DIGIT_1 = 49;
    public static final int DIGIT_2 = 50;
    public static final int DIGIT_3 = 51;
    public static final int DIGIT_4 = 52;
    public static final int DIGIT_5 = 53;
    public static final int DIGIT_6 = 54;
    public static final int DIGIT_7 = 55;
    public static final int DIGIT_8 = 56;
    public static final int DIGIT_9 = 57;
    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int SEMICOLON = 59;
    public static final int EQUAL = 61;
    public static final int LEFT_BRACKET = 91;
    public static final int BACKSLASH = 92;
    public static final int RIGHT_BRACKET = 93;
    public static final int GRAVE_ACCENT = 96;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F4 = 293;
    public static final int F5 = 294;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;
    public static final int F11 = 300;
    public static final int F12 = 301;
    public static final int KP_ENTER = 335;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;

    private SceneKeys() {
    }
}
