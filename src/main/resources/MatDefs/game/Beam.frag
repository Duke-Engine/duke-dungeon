#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform vec4 m_Color;
uniform float m_Time;
uniform float m_Tiles;
#ifdef HAS_COLORMAP
uniform sampler2D m_ColorMap;
#endif

varying vec2 texCoord;

void main() {
    // Across the ribbon: 0 at its edges, 1 down its middle.
    float across = 1.0 - abs(texCoord.x * 2.0 - 1.0);
    float glow = across * across;                 // a soft halo to the edges
    float core = pow(across, 10.0) * 1.6;         // a thin white-hot line down the middle

    // Along it: bright pulses running from the hands to where the beams meet.
    float along = texCoord.y - m_Time;
    float pulse = 0.75 + 0.25 * sin(along * 6.2832 * 1.5 - m_Time * 4.0);
    pulse += 0.35 * pow(max(0.0, sin(along * 6.2832 * 0.5 - m_Time * 2.0)), 12.0);

    float picture = 1.0;
#ifdef HAS_COLORMAP
    picture = 0.55 + 0.45 * texture2D(m_ColorMap, texCoord).r;
#endif

    vec3 light = m_Color.rgb * (glow * pulse * picture) + vec3(core * pulse);
    gl_FragColor = vec4(light, 1.0);
}
