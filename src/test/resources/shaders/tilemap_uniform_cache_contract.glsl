#version 330 core
// Test-only optional-uniform/relink contract; originated in the 2026-10-12 runtime performance task.
uniform float WorldOffsetX;
uniform float WorldOffsetY;
out vec4 FragColor;
void main() {
    FragColor = vec4(WorldOffsetX, WorldOffsetY, 0.0, 1.0);
}
