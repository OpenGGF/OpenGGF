#version 410 core
// Filter the completed opaque world; UI and dialogue are submitted afterwards.
uniform sampler2D World;
uniform vec4 Viewport;
uniform vec2 SourceExtent;
uniform vec2 SourceOrigin;
out vec4 FragColor;

void main() {
    vec2 view = (gl_FragCoord.xy - Viewport.xy) / Viewport.zw;
    // Crop fractional source extents at the bottom/right without rounding zoom.
    vec2 source = vec2(SourceOrigin.x + view.x * SourceExtent.x,
                       1.0 - SourceOrigin.y - (1.0 - view.y) * SourceExtent.y);
    vec2 size = vec2(textureSize(World, 0));
    vec2 pixel = source * size;
    vec2 footprint = max(fwidth(pixel), vec2(0.00001));
    vec3 color;
    if (max(footprint.x, footprint.y) <= 1.0) {
        // Integrate a magnified output pixel's coverage of neighboring world
        // texels. Integer scales stay crisp and crossing an integer scale does
        // not switch filters for one frame. The source is already opaque.
        vec2 fraction = fract(pixel);
        vec2 halfPixel = footprint * 0.5;
        vec2 overlap = (max(fraction - (1.0 - halfPixel), vec2(0.0))
                       - max(halfPixel - fraction, vec2(0.0))) / footprint;
        vec2 samplePoint = (floor(pixel) + 0.5 + overlap) / size;
        color = textureLod(World, samplePoint, 0.0).rgb;
    } else {
        // Only this completed world texture has a mip chain. Native tile art,
        // sprite atlases and the subsequently drawn UI keep their own sampling.
        color = textureGrad(World, source, dFdx(source), dFdy(source)).rgb;
    }
    FragColor = vec4(color, 1.0);
}
