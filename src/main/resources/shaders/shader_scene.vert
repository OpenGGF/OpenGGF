#version 410 core

// Mod scene canvas: textured quads with a multiply tint and a flash colour.
layout(location = 0) in vec2 VertexPos;
layout(location = 1) in vec2 VertexUv;
layout(location = 2) in vec4 VertexColor;
layout(location = 3) in vec4 VertexFlash;

uniform mat4 ProjectionMatrix;

out vec2 v_uv;
out vec4 v_color;
out vec4 v_flash;

void main()
{
    gl_Position = ProjectionMatrix * vec4(VertexPos, 0.0, 1.0);
    v_uv = VertexUv;
    v_color = VertexColor;
    v_flash = VertexFlash;
}
