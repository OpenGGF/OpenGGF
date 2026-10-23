package com.openggf.editor.palette;

import com.openggf.editor.EditorCommand;
import com.openggf.level.Level;
import com.openggf.level.Palette;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;

/** Editor-owned palette edits using the host's cell ownership and texture refresh policies. */
public final class EditorPaletteEditing {
    private EditorPaletteEditing() { }

    /** Converts one native three-bit component to its exact stored RGB byte value. */
    public static int componentFromStep(int step) {
        if (step < 0 || step > 7) throw new IllegalArgumentException("Palette component step must be 0..7");
        return (step * 255 + 3) / 7;
    }

    /** Rejects arbitrary RGB instead of silently changing its value during export. */
    public static int stepFromComponent(int component) {
        if (component < 0 || component > 255)
            throw new IllegalArgumentException("Palette RGB component must be 0..255");
        int step = (component * 7 + 127) / 255;
        if (componentFromStep(step) != component)
            throw new IllegalArgumentException("Palette color must be exactly Genesis-representable");
        return step;
    }

    /**
     * Prepares one cell edit for the normal editor history. RGB values are unsigned bytes;
     * ownership comes from the selected host. The callback refreshes the changed palette line
     * after both apply and undo. Prior project RGB is retained exactly, including legacy values
     * that are not exportable, and no mutable Color reference is retained or published twice.
     */
    public static EditorCommand command(Level level, int line, int color, int red, int green, int blue,
            BiPredicate<Integer, Integer> editableCell, IntConsumer refreshLine) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(editableCell, "editableCell");
        Objects.requireNonNull(refreshLine, "refreshLine");
        if (line < 0 || line >= 4 || line >= level.getPaletteCount() || color < 0 || color >= 16
                || level.getPalette(line) == null || color >= level.getPalette(line).getColorCount())
            throw new IllegalArgumentException("Palette cell is outside the authored bank");
        if (!editableCell.test(line, color))
            throw new IllegalArgumentException("Palette cell belongs to the host: line " + line + ", color " + color);
        stepFromComponent(red);
        stepFromComponent(green);
        stepFromComponent(blue);
        Rgb before = Rgb.capture(level.getPalette(line).getColor(color));
        Rgb after = new Rgb((byte) red, (byte) green, (byte) blue);
        return new EditorCommand() {
            @Override public void apply() { replace(after); }
            @Override public void undo() { replace(before); }

            private void replace(Rgb value) {
                Palette palette = level.getPalette(line);
                Rgb previous = Rgb.capture(palette.getColor(color));
                palette.setColor(color, value.copy());
                try { refreshLine.accept(line); }
                catch (RuntimeException failure) {
                    palette.setColor(color, previous.copy());
                    try { refreshLine.accept(line); }
                    catch (RuntimeException restoreFailure) {
                        if (restoreFailure != failure) failure.addSuppressed(restoreFailure);
                    }
                    throw failure;
                }
            }
        };
    }

    private record Rgb(byte red, byte green, byte blue) {
        static Rgb capture(Palette.Color color) { return new Rgb(color.r, color.g, color.b); }
        Palette.Color copy() { return new Palette.Color(red, green, blue); }
    }
}
