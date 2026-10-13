#version 410 core
// World-only picture transform; the native HUD is submitted after this pass.
uniform sampler2D World;
uniform vec4 Viewport;
uniform vec2 LogicalSize, Anchor, Scale, Shake;
uniform ivec2 Mirror;
uniform int Negative, Outline;
uniform vec4 Backdrop;
out vec4 FragColor;

void main() {
    vec2 uv = (gl_FragCoord.xy - Viewport.xy) / Viewport.zw;
    vec2 pixel = vec2(uv.x, 1.0 - uv.y) * LogicalSize;
    pixel = (pixel - Shake - Anchor) / Scale + Anchor;
    if (Mirror.x != 0) pixel.x = LogicalSize.x - pixel.x;
    if (Mirror.y != 0) pixel.y = LogicalSize.y - pixel.y;
    vec2 source = vec2(pixel.x / LogicalSize.x, 1.0 - pixel.y / LogicalSize.y);
    vec4 color = any(lessThan(source, vec2(0))) || any(greaterThan(source, vec2(1)))
            ? Backdrop : texture(World, source);
    if (Outline >= 0) {
        if (color.a < 0.004) discard;
        vec2 stepUV = 1.0 / LogicalSize;
        float edge = 0.0;
        for (int axis = 0; axis < 4; axis++) {
            vec2 delta = axis == 0 ? vec2(stepUV.x, 0) : axis == 1 ? vec2(-stepUV.x, 0)
                    : axis == 2 ? vec2(0, stepUV.y) : vec2(0, -stepUV.y);
            vec4 neighbor = texture(World, source + delta);
            edge = max(edge, neighbor.a < 0.004 || distance(color.rgb, neighbor.rgb) > 0.12 + float(Outline) * 0.01 ? 1.0 : 0.0);
        }
        color.rgb = mix(vec3(0.025, 0.09, 0.19), vec3(0.2, 0.85, 1.0), edge);
    }
    if (Negative != 0) color.rgb = vec3(1) - color.rgb;
    FragColor = color;
}
