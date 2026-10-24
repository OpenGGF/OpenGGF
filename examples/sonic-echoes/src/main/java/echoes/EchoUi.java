package echoes;

import com.openggf.mods.ui.*;
import java.util.Locale;

/** Original mod bitmap face shared with the Survivors example; logical pixel presentation. */
final class EchoUi {
    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:!?-+/%()'><x*=";
    // Seven 5-bit rows per glyph, top to bottom.
    static final String GLYPHS =
            "01110100011000111111100011000110001"   // A
            + "11110100011000111110100011000111110" // B
            + "01111100001000010000100001000001111" // C
            + "11110100011000110001100011000111110" // D
            + "11111100001000011110100001000011111" // E
            + "11111100001000011110100001000010000" // F
            + "01111100001000010011100011000101111" // G
            + "10001100011000111111100011000110001" // H
            + "11111001000010000100001000010011111" // I
            + "00111000100001000010100101001001100" // J
            + "10001100101010011000101001001010001" // K
            + "10000100001000010000100001000011111" // L
            + "10001110111010110101100011000110001" // M
            + "10001110011010110011100011000110001" // N
            + "01110100011000110001100011000101110" // O
            + "11110100011000111110100001000010000" // P
            + "01110100011000110001101011001001101" // Q
            + "11110100011000111110101001001010001" // R
            + "01111100001000001110000010000111110" // S
            + "11111001000010000100001000010000100" // T
            + "10001100011000110001100011000101110" // U
            + "10001100011000110001100010101000100" // V
            + "10001100011000110101101011101110001" // W
            + "10001100010101000100010101000110001" // X
            + "10001100010101000100001000010000100" // Y
            + "11111000010001000100010001000011111" // Z
            + "01110100111010110101101011100101110" // 0
            + "00100011000010000100001000010001110" // 1
            + "01110100010000100110010001000011111" // 2
            + "11110000010000101110000010000111110" // 3
            + "00010001100101010010111110001000010" // 4
            + "11111100001111000001000011000101110" // 5
            + "00110010001000011110100011000101110" // 6
            + "11111000010001000100010000100001000" // 7
            + "01110100011000101110100011000101110" // 8
            + "01110100011000101111000010001001100" // 9
            + "00000000000000000000000000110001100" // .
            + "00000000000000000000011000010001000" // ,
            + "00000011000110000000011000110000000" // :
            + "00100001000010000100001000000000100" // !
            + "01110100010000100110001000000000100" // ?
            + "00000000000000011111000000000000000" // -
            + "00000001000010011111001000010000000" // +
            + "00001000100001000100010000100010000" // /
            + "11001110100001000100010000101110011" // %
            + "00010001000100001000010000010000010" // (
            + "01000001000001000010000100010001000" // )
            + "00100001000100000000000000000000000" // '
            + "01000001000001000001000100010001000" // >
            + "00010001000100010000010000010000010" // <
            + "00000000001000101010001000101010001" // x (times)
            + "00000101010111011111011101010100000" // *
            + "00000000001111100000111110000000000"; // =


    final BitmapFont font = BitmapFont.binary(CHARS, GLYPHS, 5, 7, 6);
    static int colour(int slot) { return switch(slot) {
        case 0 -> 0xFF60E8FF; case 1 -> 0xFFFF70D0; case 2 -> 0xFFFFD030;
        case 3 -> 0xFFA0F060; default -> 0xFFC090FF; }; }
    void text(PixelCanvas c, String text, int x, int y, int scale, int colour) {
        String upper=text.toUpperCase(Locale.ROOT);
        font.draw(c,upper,x+1,y+1,scale,0xD0081028);
        font.draw(c,upper,x,y,scale,colour);
    }
    void centre(PixelCanvas c,String text,int y,int scale,int colour) {
        text(c,text,(c.width()-font.width(text,scale))/2,y,scale,colour);
    }
    void panel(PixelCanvas c,int x,int y,int w,int h,int accent) {
        c.fill(x,y,w,h,0xF00A1535); c.fill(x,y,w,1,accent); c.fill(x,y,2,h,accent);
    }
    // Nine nine-pixel rows of a five-point star, top to bottom; 'o' is the outline, 'x' the fill.
    static final String STAR =
            "....o...." + "...oxo..." + "oooxxxooo" + "oxxxxxxxo" + ".oxxxxxo."
            + "..oxxxo.." + ".oxxoxxo." + ".oxo.oxo." + ".oo...oo.";

    void star(PixelCanvas c,int x,int y,int scale,boolean earned) {
        int fill=earned?0xFFFFD030:0xFF34405C, edge=earned?0xFFA05810:0xFF6878A0;
        for(int i=0;i<STAR.length();i++) {
            char p=STAR.charAt(i);
            if(p!='.') c.fill(x+i%9*scale,y+i/9*scale,scale,scale,p=='x'?fill:edge);
        }
    }
    static String time(int frames) {
        int hundredths=Math.max(0,frames)*100/60;
        return "%d:%02d.%02d".formatted(hundredths/6000,hundredths/100%60,hundredths%100);
    }
}
