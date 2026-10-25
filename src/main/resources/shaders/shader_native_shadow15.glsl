#version 410 core

// Constrained native S2 special-stage palette-3/color-15 operator. Runtime admission
// validates that its immutable source contains only 0 and 15. Color 14 highlight,
// general shadow/highlight intensity, and ordinary palette colors are not enabled.
uniform sampler2D IndexedColorTexture;
uniform sampler2D TilePriorityTexture;
uniform sampler2D ShadowBackdropTexture;
uniform vec2 ShadowBackdropOrigin;
uniform vec2 ShadowBackdropSize;
uniform int TilePriorityAvailable;
uniform int TileOcclusionPaletteMask;
uniform vec2 ScreenSize;
uniform vec2 ViewportOffset;
uniform int GhostMode;
uniform float GhostAlpha;
uniform float PresentationOpacity = 1.0;
in vec2 v_texCoord;
out vec4 FragColor;

void main() {
    float index = texture(IndexedColorTexture, v_texCoord).r * 255.0;
    if (index < 0.5) discard;

    vec2 screenCoord = (gl_FragCoord.xy - ViewportOffset) / ScreenSize;
    vec4 terrain = TilePriorityAvailable == 1 ? texture(TilePriorityTexture, screenCoord) : vec4(0.0);
    int paletteLine = int(round(terrain.g * 3.0));
    bool hidden = terrain.r > 0.5 && (TileOcclusionPaletteMask & (1 << paletteLine)) != 0;
    // An opaque winning operator still claims coverage when high terrain hides it.
    // Selecting alpha zero retains that terrain without discarding native ownership.
    float strength = clamp(PresentationOpacity, 0.0, 1.0);
    if (GhostMode == 1) strength *= clamp(GhostAlpha, 0.0, 1.0);
    vec2 backdropCoord = (gl_FragCoord.xy - ShadowBackdropOrigin) / ShadowBackdropSize;
    vec3 shadowColor = texture(ShadowBackdropTexture, backdropCoord).rgb;
    FragColor = vec4(shadowColor, hidden ? 0.0 : strength);
}
