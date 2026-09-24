#version 410 core
// Presentation adaptation: native games have no widescreen side mask.
// Independent integer hash per pixel AND gameplay frame; never translate the field.
uniform vec4 Viewport;
uniform vec2 LogicalSize;
uniform float ActiveWidth;
uniform float Intensity;
uniform uint NoiseFrame;
out vec4 FragColor;
uint hash(uint v) {
    v ^= v >> 16; v *= 0x7feb352du;
    v ^= v >> 15; v *= 0x846ca68bu;
    return v ^ (v >> 16);
}
void main() {
    vec2 pixel = floor((gl_FragCoord.xy - Viewport.xy) * LogicalSize / Viewport.zw);
    float left = floor((LogicalSize.x - ActiveWidth) * 0.5);
    float right = left + ActiveWidth;
    if (pixel.x >= left && pixel.x < right) discard;
    float distance = max(left - pixel.x, pixel.x - (right - 1.0));
    float alpha = smoothstep(0.0, 12.0, distance) * Intensity;
    uint seed = hash(uint(pixel.x)) ^ hash(uint(pixel.y) + 0x9e3779b9u) ^ hash(NoiseFrame + 0x85ebca6bu);
    float grain = float(hash(seed) & 0x00ffffffu) / 16777215.0;
    vec3 color = ((13.0 + 35.0 * grain) / 255.0) * vec3(0.94, 0.98, 1.06);
    FragColor = vec4(color, alpha);
}
