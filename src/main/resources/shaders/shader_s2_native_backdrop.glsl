#version 410 core

// S2 special-stage native backdrop. Source: the live native-shadow palette bank,
// already resolved from unfaded CRAM, displayed source-line fade and display profile.
// Origin: 2026-10-22 s2_chain_pacing. This shader supplies no ROM/game asset data.
uniform sampler2D Palette;
uniform int PaletteRow;
uniform vec4 DrawViewport;
uniform float LogicalWidth;
uniform vec2 H32Range;
out vec4 FragColor;

void main()
{
    float logicalX = (gl_FragCoord.x - DrawViewport.x) * LogicalWidth / DrawViewport.z;
    if (logicalX < H32Range.x || logicalX >= H32Range.y) {
        FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    // CPU submission validates the live texture identity and four-row bank bounds.
    // Color zero's palette alpha is intentionally ignored: VDP backdrop is opaque.
    FragColor = vec4(texelFetch(Palette, ivec2(0, PaletteRow), 0).rgb, 1.0);
}
