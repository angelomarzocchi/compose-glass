package io.github.angelomarzocchi.glass

/**
 * The two AGSL programs glass is drawn with (API 33+). Both cut the same rounded rectangle and give
 * its edge the same quarter round, so what is behind the glass and what is engraved in it bend at
 * the same rim.
 *
 * Coordinates are the layer's pixels, y down. A shape is `l t r b`; its rim is [bevel] wide, and
 * inside that the glass is flat.
 */

/** The rounded box's distance field and the outward direction across its edge, shared by both. */
private const val Box = """
float box(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 boxNormal(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    float2 n = float2(0.0, 1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        n = normalize(q);
    } else if (q.x > q.y) {
        n = float2(1.0, 0.0);
    }
    return float2(p.x < 0.0 ? -n.x : n.x, p.y < 0.0 ? -n.y : n.y);
}
"""

/**
 * The glass itself, run on what is behind it ([content], drawn into the layer with [origin] of room
 * round the shape): bent by the rim so the edge magnifies — [falloff] is how far out towards the very
 * edge the bend is kept — split a little into its colours there, tinted the more glass there is to
 * look through, denser and a shade darker at the rim where it is thickest to look through ([edge]),
 * lit by the room — a highlight and a [hairline] on the side facing [keyLight] (and its far side, the
 * second value), a shade on the other, a whiter rim seen edge-on — and by two lights inside it: [lamp], under the finger, which the rim
 * carries further than the body; and [sel], the plate of the selected tab, whose light the rim
 * catches near it.
 *
 * On dark glass light adds up ([dark] 1); on pale glass it shows as colour instead ([dark] 0).
 */
internal const val GlassBodyShader = """
uniform shader content;
uniform float2 origin;
uniform float4 shape;
uniform float radius;
uniform float bevel;
uniform float refraction;
uniform float falloff;
uniform float dispersion;
uniform float4 tint;
uniform float edge;
uniform float rim;
uniform float specular;
uniform float2 hairline;
uniform float lineWidth;
uniform float3 keyLight;
uniform float4 lamp;
uniform float3 lampColor;
uniform float4 sel;
uniform float selOn;
uniform float3 selColor;
uniform float dark;
$Box
half4 main(float2 fc) {
    float2 q = fc - origin;
    float2 c = (shape.xy + shape.zw) * 0.5;
    float2 b = max((shape.zw - shape.xy) * 0.5, float2(1.0));
    float r = min(radius, min(b.x, b.y));
    float2 p = q - c;
    float d = box(p, b, r);
    if (d > 1.0) {
        return half4(0.0);
    }
    float2 g = boxNormal(p, b, r);
    float depth = max(-d, 0.0);
    float u = clamp(1.0 - depth / max(bevel, 1.0), 0.0, 1.0);
    float h = sqrt(max(1.0 - u * u, 0.0));
    float slope = u / max(h, 0.12);
    float3 n = normalize(float3(g * slope, 1.0));
    float band = u * u;

    float2 shift = -g * refraction * pow(u, max(falloff, 1.0));
    float3 col = float3(
        content.eval(fc + shift * (1.0 + dispersion)).r,
        content.eval(fc + shift).g,
        content.eval(fc + shift * (1.0 - dispersion)).b
    );
    col = mix(col, tint.rgb, tint.a * (0.55 + 0.45 * h));
    // Seen through at the rim the slab is thickest: more of its body there, and a shade darker.
    col = mix(col, tint.rgb, edge * band * 0.45);
    col *= 1.0 - edge * band * band * 0.2;

    float3 l = normalize(keyLight);
    float2 lxy = normalize(l.xy + float2(0.0001, 0.0001));
    float facing = dot(g, lxy);
    col += band * rim * max(facing, 0.0) * 0.28;
    col *= 1.0 - band * rim * max(-facing, 0.0) * 0.22;
    float fresnel = pow(1.0 - n.z, 2.0);
    col = mix(col, float3(1.0), fresnel * rim * 0.32);
    float3 hv = normalize(l + float3(0.0, 0.0, 1.0));
    col += pow(max(dot(n, hv), 0.0), 80.0) * specular;
    // The edge's line of light lies wholly inside the shape, [lineWidth] wide and fading inwards, averaged
    // over the pixel across the edge: on a curve it keeps its width instead of breaking into steps, and
    // the silhouette's own smoothing does not cut half of it away. It never goes out: where the edge is
    // side-on to the light it dims to a share of both sides' brightness, not to nothing.
    float s = -d;
    float lw = max(lineWidth, 1.0);
    float line = 0.0;
    for (int i = 0; i < 4; i++) {
        float t = s + (float(i) - 1.5) * 0.25;
        line += t < 0.0 ? 0.0 : 1.0 - smoothstep(lw * 0.5, lw * 1.4, t);
    }
    line *= 0.25;
    float sideOn = mix(hairline.y, hairline.x, facing * 0.5 + 0.5) * (0.3 + 0.7 * abs(facing));
    // The line's share of the pixel, over the shape's share: the colour is weighted by the latter below.
    col += min(line / max(clamp(s + 0.5, 0.0, 1.0), 0.001), 1.0) * sideOn * 0.4;

    float dl = length(q - lamp.xy);
    float reach = max(lamp.w, 1.0);
    float glow = exp(-(dl * dl) / (reach * reach));
    float carried = band * exp(-dl / (reach * 2.2));
    float lampLight = lamp.z * (glow * 0.5 + carried * 1.0);

    float2 sc = (sel.xy + sel.zw) * 0.5;
    float2 sb = max((sel.zw - sel.xy) * 0.5, float2(0.5));
    float ds = box(q - sc, sb, min(sb.x, sb.y));
    float core = 1.0 - smoothstep(-2.0, 2.0, ds);
    float halo = exp(-max(ds, 0.0) / (max(bevel, 1.0) * 0.8));
    float caught = band * exp(-max(ds, 0.0) / (max(bevel, 1.0) * 2.2));
    float selLight = selOn * (core * 0.4 + halo * 0.15 + caught * 0.6);

    // Light screens what it falls on: it brightens the dark, and never burns the bright to white.
    float3 lit = clamp(lampColor * lampLight + selColor * selLight, 0.0, 1.0);
    float3 added = 1.0 - (1.0 - col) * (1.0 - lit);
    float dye = clamp(selOn * (core * 0.7 + halo * 0.12 + caught * 0.4), 0.0, 1.0);
    float3 dyed = mix(col, selColor, dye);
    dyed = 1.0 - (1.0 - dyed) * (1.0 - clamp(lampColor * lampLight * 0.6, 0.0, 1.0));
    col = mix(dyed, added, dark);

    float a = clamp(0.5 - d, 0.0, 1.0);
    return half4(half3(col * a), half(a));
}
"""

/**
 * What is engraved in the glass — icons, labels, whatever the surface holds — run on its own drawing
 * ([content]): bent by the rim like what is behind, but less, because it sits inside the glass
 * ([refraction] is already scaled by how deep); mirrored and squeezed into the rim, which is the
 * polished wall of a thick block seen from above ([reflection]): nearer the rim it shows what is
 * further in, so the image is squeezed and turned over, and a little soft; and lit from inside —
 * turned [selInk] in the selected plate's light and [lampInk] under the lamp, with a [glow] round it.
 */
internal const val GlassInkShader = """
uniform shader content;
uniform float4 shape;
uniform float radius;
uniform float bevel;
uniform float refraction;
uniform float reflection;
uniform float4 lamp;
uniform float3 lampColor;
uniform float3 lampInk;
uniform float4 sel;
uniform float selOn;
uniform float3 selInk;
uniform float glow;
$Box
half4 main(float2 fc) {
    float2 c = (shape.xy + shape.zw) * 0.5;
    float2 b = max((shape.zw - shape.xy) * 0.5, float2(1.0));
    float r = min(radius, min(b.x, b.y));
    float2 p = fc - c;
    float d = box(p, b, r);
    if (d > 0.5) {
        return half4(0.0);
    }
    float2 g = boxNormal(p, b, r);
    float depth = max(-d, 0.0);
    float bv = max(bevel, 1.0);
    float u = clamp(1.0 - depth / bv, 0.0, 1.0);
    // Only the outer half of the rim bends the engraving: a label that sits near the edge keeps its
    // shape, and only what reaches the steep part of the rim is drawn out along it.
    float outer = smoothstep(0.45, 1.0, u);
    float4 ink = float4(content.eval(fc - g * refraction * (outer * outer)));
    float4 ghost = float4(0.0);
    float mirror = 0.0;
    if (reflection > 0.0) {
        float2 along = float2(-g.y, g.x);
        float2 gp = fc - g * (mix(0.3, 2.2, u * sqrt(u)) * bv);
        ghost = (float4(content.eval(gp)) + float4(content.eval(gp + along * 1.5))
            + float4(content.eval(gp - along * 1.5)) + float4(content.eval(gp - g * 2.0))) * 0.25;
        mirror = reflection * smoothstep(0.2, 1.0, u) * 0.8;
    }

    float dl = length(fc - lamp.xy);
    float reach = max(lamp.w, 1.0);
    float lampLit = lamp.z * exp(-(dl * dl) / (reach * reach * 1.4));
    float2 sc = (sel.xy + sel.zw) * 0.5;
    float2 sb = max((sel.zw - sel.xy) * 0.5, float2(0.5));
    float ds = box(fc - sc, sb, min(sb.x, sb.y));
    float selLit = selOn * (1.0 - smoothstep(-3.0, 3.0, ds));

    float halo = 0.0;
    for (int i = 0; i < 8; i++) {
        float t = float(i) * 0.7853982;
        halo += float(content.eval(fc + float2(cos(t), sin(t)) * glow).a);
    }
    halo *= 0.125;

    float3 base = ink.a > 0.001 ? ink.rgb / ink.a : float3(0.0);
    float3 colour = mix(base, lampInk, lampLit * (1.0 - selLit));
    colour = mix(colour, selInk, selLit);
    float3 rgb = colour * ink.a;
    float alpha = ink.a;

    float shine = clamp(selLit * 0.45 + lampLit * 0.5, 0.0, 1.0);
    float glowA = halo * shine * (1.0 - alpha);
    float3 glowColour = mix(selInk, mix(lampInk, lampColor, 0.5), lampLit / max(selLit + lampLit, 0.001));
    rgb += glowColour * glowA;
    alpha += glowA;

    float3 ghostBase = ghost.a > 0.001 ? ghost.rgb / ghost.a : float3(0.0);
    float ga = ghost.a * mirror * (1.0 - alpha);
    rgb += ghostBase * ga;
    alpha += ga;

    float edge = clamp(0.5 - d, 0.0, 1.0);
    return half4(half3(rgb * edge), half(alpha * edge));
}
"""
