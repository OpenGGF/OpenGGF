package com.openggf.tools;

import com.openggf.control.InputActionMasks;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Compiles a compact, human-writable input script into BizHawk input-log frames.
 *
 * <p>Purpose: let an agent (or a person) author a Genesis controller sequence in a
 * few lines and get a real {@code Input Log.txt} back, so the same
 * {@code Bk2MovieLoader} that replays recorded movies also drives authored
 * gameplay captures. Originating task: the FBZ2 {@code $1DC0} squeeze capture
 * (2026-09-13); see {@code GameplayCaptureTool}.
 *
 * <p>Grammar, one instruction per line or separated by {@code ;}:
 * <pre>
 *   # comment
 *   60 R              hold Right for 60 frames
 *   30 D+R            hold Down and Right for 30 frames (separators: + or spaces)
 *   1 A               press A (jump) for one frame
 *   10 -              neutral for 10 frames ('-' or '.')
 *   4 R / L           P1 holds Right, P2 holds Left
 *   repeat 3          block repeated 3 times; nestable
 *     1 A
 *     1 -
 *   end
 *   repeat 2 { 1 A ; 1 - }   inline form
 * </pre>
 * Button tokens are case-insensitive: {@code U D L R A B C S} or the words
 * {@code up down left right a b c start jump} ({@code jump} is {@code A}).
 * Every frame line is a held state, exactly as BizHawk records it; a tap is a
 * one-frame press followed by a release.
 */
public final class InputScriptCompiler {

    private static final String P1_P2_SEPARATOR = "/";

    private InputScriptCompiler() {
    }

    /** Compiles script text into per-frame held states, frame indices starting at zero. */
    public static List<RecordedFrameInput> compile(String script) {
        Objects.requireNonNull(script, "script");
        List<Instruction> instructions = new ArrayList<>();
        Deque<List<Instruction>> blocks = new ArrayDeque<>();
        Deque<Integer> counts = new ArrayDeque<>();
        List<Instruction> current = instructions;
        int lineNumber = 0;
        for (String rawLine : script.split("\\r?\\n", -1)) {
            lineNumber++;
            for (String statement : splitStatements(stripComment(rawLine))) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.startsWith("repeat")) {
                    String rest = trimmed.substring("repeat".length()).trim();
                    boolean inline = rest.endsWith("}");
                    String countText = inline ? rest.substring(0, rest.indexOf('{')).trim() : rest;
                    int count = parseCount(countText, lineNumber, "repeat count");
                    if (inline) {
                        String body = rest.substring(rest.indexOf('{') + 1, rest.length() - 1);
                        List<Instruction> inner = compileBody(body, lineNumber);
                        current.add(new Repeat(count, inner));
                    } else {
                        blocks.push(current);
                        counts.push(count);
                        current = new ArrayList<>();
                    }
                    continue;
                }
                if (lower.equals("end")) {
                    if (blocks.isEmpty()) {
                        throw new IllegalArgumentException("Line " + lineNumber + ": 'end' without 'repeat'");
                    }
                    List<Instruction> body = current;
                    current = blocks.pop();
                    current.add(new Repeat(counts.pop(), body));
                    continue;
                }
                current.add(parseHold(trimmed, lineNumber));
            }
        }
        if (!blocks.isEmpty()) {
            throw new IllegalArgumentException("Unterminated 'repeat' block (missing 'end')");
        }
        List<RecordedFrameInput> frames = new ArrayList<>();
        expand(instructions, frames);
        return frames;
    }

    /** Compiles and formats as a BizHawk {@code Input Log.txt} body. */
    public static String compileToInputLog(String script) {
        return UserRecordingWriter.inputLogText(compile(script));
    }

    /** One-line run-length summary such as {@code 60xR, 30xD+R, 1xA, 10x-}. */
    public static String summarize(List<RecordedFrameInput> frames) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < frames.size()) {
            RecordedFrameInput first = frames.get(index);
            int run = 1;
            while (index + run < frames.size() && sameState(frames.get(index + run), first)) {
                run++;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(run).append('x').append(describe(first));
            index += run;
        }
        return out.toString();
    }

    private static List<Instruction> compileBody(String body, int lineNumber) {
        List<Instruction> inner = new ArrayList<>();
        for (String statement : splitStatements(body)) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                inner.add(parseHold(trimmed, lineNumber));
            }
        }
        return inner;
    }

    private static Hold parseHold(String statement, int lineNumber) {
        String[] parts = statement.trim().split("\\s+", 2);
        int count = parseCount(parts[0], lineNumber, "frame count");
        String buttons = parts.length > 1 ? parts[1].trim() : "-";
        String p1Text = buttons;
        String p2Text = "-";
        int lane = buttons.indexOf(P1_P2_SEPARATOR);
        if (lane >= 0) {
            p1Text = buttons.substring(0, lane).trim();
            p2Text = buttons.substring(lane + 1).trim();
        }
        Lane p1 = parseLane(p1Text.isEmpty() ? "-" : p1Text, lineNumber);
        Lane p2 = parseLane(p2Text.isEmpty() ? "-" : p2Text, lineNumber);
        return new Hold(count, p1, p2);
    }

    private static Lane parseLane(String text, int lineNumber) {
        int input = 0;
        int action = 0;
        boolean start = false;
        for (String token : text.split("[+\\s]+")) {
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("neutral") || t.equals("none")) {
                continue;
            }
            switch (t) {
                case "u", "up" -> input |= AbstractPlayableSprite.INPUT_UP;
                case "d", "down" -> input |= AbstractPlayableSprite.INPUT_DOWN;
                case "l", "left" -> input |= AbstractPlayableSprite.INPUT_LEFT;
                case "r", "right" -> input |= AbstractPlayableSprite.INPUT_RIGHT;
                case "a", "jump" -> action |= InputActionMasks.ACTION_A;
                case "b" -> action |= InputActionMasks.ACTION_B;
                case "c" -> action |= InputActionMasks.ACTION_C;
                case "s", "start" -> start = true;
                default -> throw new IllegalArgumentException(
                        "Line " + lineNumber + ": unknown button token '" + token + "'");
            }
        }
        if (action != 0) {
            input |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return new Lane(input, action, start);
    }

    private static int parseCount(String text, int lineNumber, String what) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) {
                throw new IllegalArgumentException("Line " + lineNumber + ": negative " + what);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + lineNumber + ": expected " + what + ", got '" + text + "'");
        }
    }

    private static void expand(List<Instruction> instructions, List<RecordedFrameInput> frames) {
        for (Instruction instruction : instructions) {
            if (instruction instanceof Hold hold) {
                for (int i = 0; i < hold.frames(); i++) {
                    frames.add(new RecordedFrameInput(frames.size(),
                            hold.p1().input(), hold.p1().action(), hold.p1().start(),
                            hold.p2().input(), hold.p2().action(), hold.p2().start()));
                }
            } else if (instruction instanceof Repeat repeat) {
                for (int i = 0; i < repeat.count(); i++) {
                    expand(repeat.body(), frames);
                }
            }
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static List<String> splitStatements(String text) {
        List<String> statements = new ArrayList<>();
        // Keep inline "repeat n { ... }" intact: split on ';' outside braces.
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            if (c == ';' && depth == 0) {
                statements.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        statements.add(current.toString());
        return statements;
    }

    private static boolean sameState(RecordedFrameInput a, RecordedFrameInput b) {
        return a.p1InputMask() == b.p1InputMask() && a.p1ActionMask() == b.p1ActionMask()
                && a.p1Start() == b.p1Start() && a.p2InputMask() == b.p2InputMask()
                && a.p2ActionMask() == b.p2ActionMask() && a.p2Start() == b.p2Start();
    }

    private static String describe(RecordedFrameInput frame) {
        String p1 = describeLane(frame.p1InputMask(), frame.p1ActionMask(), frame.p1Start());
        boolean p2Active = frame.p2InputMask() != 0 || frame.p2ActionMask() != 0 || frame.p2Start();
        return p2Active ? p1 + "/" + describeLane(frame.p2InputMask(), frame.p2ActionMask(), frame.p2Start()) : p1;
    }

    private static String describeLane(int input, int action, boolean start) {
        StringBuilder out = new StringBuilder();
        if ((input & AbstractPlayableSprite.INPUT_UP) != 0) out.append("U+");
        if ((input & AbstractPlayableSprite.INPUT_DOWN) != 0) out.append("D+");
        if ((input & AbstractPlayableSprite.INPUT_LEFT) != 0) out.append("L+");
        if ((input & AbstractPlayableSprite.INPUT_RIGHT) != 0) out.append("R+");
        if ((action & InputActionMasks.ACTION_A) != 0) out.append("A+");
        if ((action & InputActionMasks.ACTION_B) != 0) out.append("B+");
        if ((action & InputActionMasks.ACTION_C) != 0) out.append("C+");
        if (start) out.append("S+");
        if (out.length() == 0) {
            return "-";
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    private sealed interface Instruction permits Hold, Repeat {
    }

    private record Lane(int input, int action, boolean start) {
    }

    private record Hold(int frames, Lane p1, Lane p2) implements Instruction {
    }

    private record Repeat(int count, List<Instruction> body) implements Instruction {
    }
}
