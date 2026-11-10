extends RefCounted

var shaders: Dictionary = {}
var screen: StandardMaterial3D

func effect(kind: String, source: String) -> ShaderMaterial:
	if not shaders.has(kind):
		var shader := Shader.new()
		shader.code = source
		shaders[kind] = shader
	var instance := ShaderMaterial.new()
	instance.shader = shaders[kind]
	return instance

func terminal_screen() -> StandardMaterial3D:
	if screen != null:
		return screen
	var image := Image.new()
	image.load_svg_from_string("""<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128" viewBox="0 0 256 128">
	<rect width="256" height="128" fill="#102a33"/>
	<path d="M0 28H256M9 54H176M9 76H176M9 98H176M36 43V105M74 43V105M112 43V105M150 43V105" stroke="#214954" stroke-width="2"/>
	<path d="M10 78H39L52 68L63 85L75 50L87 94L100 75H122L135 61L146 80H177" fill="none" stroke="#75eebc" stroke-width="3"/>
	<rect x="190" y="44" width="54" height="62" rx="4" fill="#172530" stroke="#446878"/>
	<path d="M217 50L234 80H200Z" fill="#ffb86e"/>
	<path d="M217 59V70M217 73V76" stroke="#49252d" stroke-width="3"/>
	<path d="M204 90H230M209 96H225" stroke="#d48870" stroke-width="2"/>
	<path d="M13 14H75M81 14H95M209 15H239M14 115H59M66 115H87" stroke="#8ecbd1" stroke-width="4"/>
	</svg>""")
	image.generate_mipmaps()
	screen = StandardMaterial3D.new()
	screen.albedo_texture = ImageTexture.create_from_image(image)
	screen.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	return screen

func portal() -> ShaderMaterial:
	return effect("portal", """
shader_type spatial;
render_mode unshaded, cull_disabled;
uniform float clock;
uniform float crossing;
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float r = length(p);
	if (r > 1.0) { discard; }
	float angle = atan(p.y, p.x + 0.0001);
	float twist = 12.0 * r - 2.3 * clock;
	float tide = sin(4.0 * angle + twist + 1.1 * sin(angle * 3.0 - clock + 10.0 * r));
	float filament = pow(0.5 + 0.5 * sin(8.0 * angle + 1.7 * twist), 7.0);
	float throat = 1.0 - smoothstep(0.04, 0.28, r);
	float rim = smoothstep(0.80, 0.99, r);
	float wave = exp(-24.0 * abs(r - (0.12 + 0.86 * crossing))) * smoothstep(0.0, 0.12, crossing);
	vec3 ink = vec3(0.025, 0.06, 0.15);
	vec3 blue = vec3(0.14, 0.40, 0.77);
	vec3 mint = vec3(0.43, 1.0, 0.87);
	ALBEDO = mix(ink, blue, (0.5 + 0.5 * tide) * (1.0 - 0.65 * throat))
		+ mint * (filament * (0.21 + 0.29 * r) + rim * 0.56 + wave * 0.53)
		+ vec3(0.20, 0.11, 0.43) * throat;
}
""")

func hazard(toxic: bool) -> ShaderMaterial:
	var floor_effect := effect("hazard", """
shader_type spatial;
render_mode unshaded, cull_disabled;
uniform float clock;
uniform float armed = 1.0;
uniform bool toxic = true;
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float r = length(p);
	float ripple = sin(p.x * 12.0 + p.y * 7.0) * cos(p.y * 12.0);
	float edge = toxic ? 0.92 + ripple * 0.065 : 0.99;
	if (r > edge) { discard; }
	if (toxic) {
		float current = 0.5 + 0.5 * sin(23.0 * r - clock * 4.0 + ripple * 2.0);
		vec3 water = mix(vec3(0.055, 0.19, 0.09), vec3(0.36, 0.75, 0.12), current * 0.7);
		float foam = smoothstep(edge - 0.13, edge - 0.03, r) * 0.40;
		ALBEDO = water * (0.61 + armed * 0.39) + vec3(0.38, 0.64, 0.12) * foam;
	} else {
		float branch = p.y - sin(p.x * 16.0 + clock * 13.0) * 0.08 - sin(p.x * 35.0 - clock * 9.0) * 0.05;
		float arc = 1.0 - smoothstep(0.012, 0.066, abs(branch));
		float fork = 1.0 - smoothstep(0.011, 0.047, abs(branch - p.x * 0.63));
		float spark = 0.67 + 0.33 * sin(clock * 10.0 + p.x * 9.0);
		ALBEDO = vec3(0.095, 0.115, 0.15) + vec3(0.36, 0.68, 1.0)
			* (arc + 0.58 * fork) * (0.29 + armed * 0.71) * spark * (1.0 - 0.42 * r);
	}
}
""")
	floor_effect.set_shader_parameter("toxic", toxic)
	return floor_effect

func vapor() -> ShaderMaterial:
	return effect("vapor", """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
uniform float clock;
uniform vec3 tint : source_color = vec3(0.55, 0.92, 0.32);
void fragment() {
	vec2 p = vec2(UV.x * 2.0 - 1.0, 1.0 - UV.y);
	float bend = sin(p.y * 6.0 + clock * 1.8) * p.y * 0.32;
	float column = 1.0 - smoothstep(0.12, 0.67 + p.y * 0.24, abs(p.x - bend));
	float billow = 0.53 + 0.47 * sin(p.y * 18.0 - clock * 3.4 + cos(p.x * 8.0 + p.y * 4.0));
	ALBEDO = tint;
	ALPHA = column * smoothstep(0.0, 0.14, p.y) * (1.0 - smoothstep(0.27, 1.0, p.y))
		* (0.09 + 0.31 * billow);
}
""")

func grate(tint: Color) -> ShaderMaterial:
	var floor_effect := effect("grate", """
shader_type spatial;
render_mode unshaded;
uniform vec3 tint : source_color = vec3(1.0, 0.45, 0.2);
uniform float clock;
void fragment() {
	float channel = 0.24 + 0.76 * pow(0.5 + 0.5 * sin(UV.y * 38.0 - clock * 3.2 + sin(UV.x * 31.0)), 3.0);
	float slots = smoothstep(0.20, 0.35, abs(fract(UV.y * 9.0) - 0.5));
	ALBEDO = tint * channel * (0.74 - 0.46 * slots);
}
""")
	floor_effect.set_shader_parameter("tint", tint)
	return floor_effect

func waste_pool() -> ShaderMaterial:
	return effect("waste_pool", """
shader_type spatial;
render_mode unshaded;
uniform float clock;
void fragment() {
	vec2 p = UV * 12.0;
	vec2 cell = floor(p);
	float seed = fract(sin(dot(cell, vec2(17.7, 47.3))) * 921.7);
	float life = fract(clock * (0.62 + seed * 0.46) + seed);
	float radius = length(fract(p) - vec2(0.28 + seed * 0.44, 0.50));
	float bubble = (1.0 - smoothstep(0.024, 0.085, abs(radius - (0.07 + life * 0.36)))) * (1.0 - life);
	float flow = sin(p.x * 1.7 + cos(p.y * 2.1 - clock) - clock * 1.4) * 0.5 + 0.5;
	ALBEDO = mix(vec3(0.055, 0.20, 0.035), vec3(0.18, 0.51, 0.063), flow) + bubble * vec3(0.24, 0.42, 0.08);
}
""")
