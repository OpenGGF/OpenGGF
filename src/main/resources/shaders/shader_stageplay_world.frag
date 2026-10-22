#version 410 core
// Filter the completed opaque world; UI and dialogue are submitted afterwards.
uniform sampler2D World;
uniform vec4 Viewport;
uniform vec2 SourceExtent;
out vec4 FragColor;

void main() {
    vec2 view = (gl_FragCoord.xy - Viewport.xy) / Viewport.zw;
    // Crop fractional source extents at the bottom/right without rounding zoom.
    vec2 source = vec2(view.x * SourceExtent.x, 1.0 - (1.0 - view.y) * SourceExtent.y);
    FragColor = vec4(texture(World, source).rgb, 1.0);
}
