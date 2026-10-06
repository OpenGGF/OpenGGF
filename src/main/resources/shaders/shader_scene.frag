#version 410 core

// Flash mixes the texel towards a solid colour (keeping its alpha); the tint then multiplies.
uniform sampler2D Texture;

in vec2 v_uv;
in vec4 v_color;
in vec4 v_flash;
out vec4 FragColor;

void main()
{
    vec4 texel = texture(Texture, v_uv);
    vec3 rgb = mix(texel.rgb, v_flash.rgb, v_flash.a);
    FragColor = vec4(rgb, texel.a) * v_color;
    if (FragColor.a < 0.004) discard;
}
