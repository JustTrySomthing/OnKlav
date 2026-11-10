extends RefCounted

# Bake each sculpted prop to a shared solid mesh and an optional glow mesh.
# Actors keep their existing transforms and hitboxes.
var models: Dictionary = {}
var shapes: Dictionary = {}
var solid: SurfaceTool
var glow: SurfaceTool
var organic_finish := false

func model(name: String) -> Array[Mesh]:
	if models.has(name):
		return models[name]
	solid = SurfaceTool.new()
	glow = SurfaceTool.new()
	solid.begin(Mesh.PRIMITIVE_TRIANGLES)
	glow.begin(Mesh.PRIMITIVE_TRIANGLES)
	organic_finish = name in ["spitter","spore_sacs","spitter_jaw","spitter_foreleg","rupture_pod","pressure_roots"]
	match name:
		"spitter": spitter()
		"spore_sacs": spore_sacs()
		"spitter_jaw": spitter_jaw()
		"spitter_foreleg": spitter_foreleg()
		"nail_slide": nail_slide()
		"breaker_action": breaker_action()
		"nailgun": weapon(false)
		"sporebreaker": weapon(true)
		"arc_lance": arc_lance()
		"grav_mortar": grav_mortar()
		"triple_nails": triple_nails()
		"biogel": biogel()
		"cache": cache()
		"sample": sample()
		"dock": dock()
		"canister": canister()
		"conveyor_specimen": conveyor_specimen()
		"conveyor_drive": conveyor_drive()
		"pressure_manifold": pressure_manifold()
		"pressure_roots": pressure_roots()
		"pressure_valve": pressure_valve()
		"rupture_pod": rupture_pod()
		"culture_vent": culture_vent()
		"culture_shutter": culture_shutter()
		"culture_cassette": culture_cassette()
		"furnace_pump": furnace_pump()
		"pump_core": pump_core()
		"furnace_nozzle": furnace_nozzle()
		"furnace_iris": furnace_iris()
		"king_armor": king_armor()
		"throne_root": throne_root()
		"king_crown": king_crown()
		"king_scars": king_scars()
		"king_plate": king_plate()
		"king_core": king_core()
	var result: Array[Mesh] = [bake(solid, false)]
	if name not in ["dock","spore_sacs","spitter_jaw","culture_shutter","furnace_pump","furnace_iris","king_armor","throne_root","king_plate","spitter_foreleg","nail_slide","breaker_action"]:
		result.append(bake(glow, true))
	models[name] = result
	return result

func bake(builder: SurfaceTool, luminous: bool) -> Mesh:
	var finish := StandardMaterial3D.new()
	finish.vertex_color_use_as_albedo = true
	finish.vertex_color_is_srgb = true
	finish.roughness = .48
	if luminous:
		finish.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	builder.set_material(finish)
	builder.index()
	return builder.commit()

func piece(shape: Mesh, at: Vector3, size: Vector3, color: Color,
		rotation := Basis.IDENTITY, luminous := false) -> void:
	var arrays := shape.surface_get_arrays(0)
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	var builder := glow if luminous else solid
	for index in indices:
		var point := at + rotation * (vertices[index] * size)
		var tint := color
		if organic_finish and not luminous:
			# Broad pigment mottling is baked into the hide with no runtime shader.
			var marbling := sin(point.x*28.0 + sin(point.y*22.0)) * cos(point.z*21.0 - point.y*19.0)
			tint = color * (.90 + point.y*.14 + marbling*.095)
			tint.a = 1.0
		builder.set_color(tint)
		builder.set_normal((rotation * (normals[index] / size)).normalized())
		builder.add_vertex(point)

func oval(at: Vector3, size: Vector3, color: Color, rotation := Basis.IDENTITY,
		luminous := false) -> void:
	if not shapes.has("oval"):
		var shape := SphereMesh.new()
		shape.radial_segments = 12
		shape.rings = 6
		shapes["oval"] = shape
	piece(shapes["oval"], at, size, color, rotation, luminous)

func tube(at: Vector3, size: Vector3, color: Color, rotation := Basis.IDENTITY,
		taper := 1.0, luminous := false) -> void:
	if not shapes.has(taper):
		var shape := CylinderMesh.new()
		shape.top_radius = .5 * taper
		shape.bottom_radius = .5
		shape.height = 1
		shape.radial_segments = 12
		shape.rings = 1
		shapes[taper] = shape
	piece(shapes[taper], at, size, color, rotation, luminous)

func band(at: Vector3, size: Vector3, color: Color, rotation := Basis.IDENTITY) -> void:
	if not shapes.has("band"):
		var shape := TorusMesh.new()
		shape.inner_radius = .38
		shape.outer_radius = .50
		shape.rings = 20
		shape.ring_segments = 8
		shapes["band"] = shape
	piece(shapes["band"], at, size, color, rotation)

func beveled_case(at: Vector3, size: Vector3, color: Color) -> void:
	if not shapes.has("case"):
		var builder := SurfaceTool.new()
		builder.begin(Mesh.PRIMITIVE_TRIANGLES)
		builder.set_smooth_group(-1)
		var corners := [Vector2(-.38,-.50), Vector2(.38,-.50), Vector2(.50,-.38),
			Vector2(.50,.38), Vector2(.38,.50), Vector2(-.38,.50),
			Vector2(-.50,.38), Vector2(-.50,-.38)]
		var levels := [Vector2(-.50,.83), Vector2(-.37,1), Vector2(.37,1), Vector2(.50,.83)]
		for row in range(levels.size() - 1):
			for side in corners.size():
				for corner in [Vector2i(0,0), Vector2i(0,1), Vector2i(1,0),
						Vector2i(0,1), Vector2i(1,1), Vector2i(1,0)]:
					var p: Vector2 = corners[(side + corner.y) % corners.size()]
					var layer: Vector2 = levels[row + corner.x]
					builder.add_vertex(Vector3(p.x*layer.y, layer.x, p.y*layer.y))
		for side in corners.size():
			var a: Vector2 = corners[side] * .83
			var b: Vector2 = corners[(side + 1) % corners.size()] * .83
			for p in [Vector3(0,.5,0),Vector3(a.x,.5,a.y),Vector3(b.x,.5,b.y),
					Vector3(0,-.5,0),Vector3(b.x,-.5,b.y),Vector3(a.x,-.5,a.y)]:
				builder.add_vertex(p)
		builder.generate_normals()
		builder.index()
		shapes["case"] = builder.commit()
	piece(shapes["case"], at, size, color)

func spitter() -> void:
	var hide_color := Color("#376a62")
	var mantle := Color("#548e75")
	var ridge := Color("#745a83")
	var gum := Color("#ae6b86")
	var throat := Color("#291b37")
	var horn := Color("#efd09c")
	var eye := Color("#e1ff93")
	var forward := Basis(Vector3.RIGHT, PI*.5)
	# The body hunches over the snout, with an armored back and wide walking feet.
	oval(Vector3(0,.43,.14),Vector3(1.28,.83,1.14),hide_color)
	oval(Vector3(0,.66,.28),Vector3(1.23,1.00,1.04),mantle,Basis(Vector3.RIGHT,.29))
	oval(Vector3(0,.52,-.34),Vector3(.95,.64,.87),ridge)
	for side in [-1,1]:
		var lean := Basis(Vector3.FORWARD, side*.44)
		oval(Vector3(side*.46,.16,.50),Vector3(.46,.31,.51),hide_color)
		oval(Vector3(side*.30,.80,-.60),Vector3(.31,.26,.24),throat,lean)
		oval(Vector3(side*.31,.80,-.705),Vector3(.16,.098,.05),eye,lean,true)
		oval(Vector3(side*.30,.88,-.66),Vector3(.34,.12,.17),mantle,lean)
	for plate in range(4):
		var height := .32 - .034*plate
		tube(Vector3(0,1.07-.058*plate,.06+.18*plate),Vector3(.26,height,.20),
			ridge,Basis(Vector3.RIGHT,.38),.12)
	# A dark inset and thick folded lip make the mouth read as a bore from the side.
	oval(Vector3(0,.59,-.66),Vector3(.61,.45,.23),throat)
	band(Vector3(0,.59,-.77),Vector3(.69,1.00,.48),gum,forward)
	for side in [-1,1]:
		oval(Vector3(side*.30,.58,-.73),Vector3(.17,.32,.16),ridge)
		tube(Vector3(side*.17,.71,-.79),Vector3(.10,.16,.105),horn,
			Basis(Vector3.FORWARD,PI+side*.38),.10)

func spore_sacs() -> void:
	# Geometry is centered around the pressure chamber so swelling stays attached.
	for side in [-1,1]:
		var lean := Basis(Vector3.FORWARD, side*.44)
		oval(Vector3(side*.47,0,0),Vector3(.39,.65,.43),Color("#745a83"),lean)
		oval(Vector3(side*.56,.02,0),Vector3(.28,.47,.34),Color("#bac96c"),lean)
		for rib in range(3):
			oval(Vector3(side*(.60-.025*rib),-.09+.11*rib,-.07),
				Vector3(.11,.052,.35),Color("#548e75"),lean)

func spitter_jaw() -> void:
	oval(Vector3(0,0,-.076),Vector3(.49,.13,.21),Color("#9d6580"))
	for side in [-1,1]:
		tube(Vector3(side*.15,.035,-.10),Vector3(.082,.115,.083),Color("#efd09c"),
			Basis(Vector3.FORWARD,-side*.28),.10)

func biogel() -> void:
	var steel := Color("#253847")
	var ivory := Color("#e4f4e9")
	var aqua := Color("#59f6bf")
	tube(Vector3(0,.36,0),Vector3(.35,.43,.35),Color("#2c9f81"))
	tube(Vector3(0,.62,0),Vector3(.44,.13,.44),ivory)
	tube(Vector3(0,.72,0),Vector3(.29,.14,.29),steel,Basis.IDENTITY,.86)
	tube(Vector3(0,.16,0),Vector3(.44,.13,.44),steel)
	tube(Vector3(0,.55,0),Vector3(.47,.07,.47),steel)
	for side in [-1,1]:
		beveled_case(Vector3(side*.22,.35,0),Vector3(.16,.43,.38),ivory)
		beveled_case(Vector3(0,.37,side*.177),Vector3(.32,.32,.05),steel)
		var cross := Vector3(0,.37,side*.207)
		for size in [Vector3(.22,.063,.028), Vector3(.063,.22,.028)]:
			piece(box(),cross,size,aqua,Basis.IDENTITY,true)

func box() -> Mesh:
	if not shapes.has("box"):
		shapes["box"] = BoxMesh.new()
	return shapes["box"]

func cache() -> void:
	var armor := Color("#87623a")
	var alloy := Color("#dca559")
	var dark := Color("#282b38")
	beveled_case(Vector3(0,.32,0),Vector3(.94,.40,.65),armor)
	beveled_case(Vector3(0,.52,0),Vector3(.97,.17,.68),alloy)
	beveled_case(Vector3(0,.43,0),Vector3(.96,.066,.66),dark)
	for side in [-1,1]:
		beveled_case(Vector3(side*.34,.33,0),Vector3(.15,.50,.69),dark)
		beveled_case(Vector3(side*.31,.39,-.34),Vector3(.12,.17,.075),alloy)
		beveled_case(Vector3(side*.22,.16,.18),Vector3(.20,.12,.17),dark)
	band(Vector3(0,.63,0),Vector3(.37,.58,.28),dark,Basis(Vector3.RIGHT, PI*.5))
	beveled_case(Vector3(0,.405,-.355),Vector3(.26,.19,.07),dark)
	piece(box(),Vector3(0,.41,-.397),Vector3(.14,.064,.026),Color("#ffdc86"),Basis.IDENTITY,true)

func sample() -> void:
	var metal := Color("#788caa")
	var dark := Color("#2a3850")
	var liquid := Color("#ffb84f")
	tube(Vector3.ZERO,Vector3(.27,.54,.27),liquid)
	for y in [-.20,.20]:
		tube(Vector3(0,y,0),Vector3(.295,.042,.295),Color("#ffd080"),Basis.IDENTITY,1.0,true)
	for side in [-1,1]:
		tube(Vector3(0,side*.30,0),Vector3(.59,.15,.59),metal)
		tube(Vector3(0,side*.40,0),Vector3(.42,.11,.42),dark)
	for i in 3:
		var angle := TAU*i/3.0
		var spoke := Vector3(cos(angle),0,sin(angle))
		beveled_case(spoke*.23,Vector3(.108,.60,.108),metal)
		oval(spoke*.207,Vector3(.092,.24,.092),Color("#ffd080"),Basis.IDENTITY,true)

func dock() -> void:
	beveled_case(Vector3(0,.13,0),Vector3(1.04,.25,.86),Color("#344050"))
	beveled_case(Vector3(0,.25,0),Vector3(.88,.12,.74),Color("#647a8a"))
	band(Vector3(0,.32,0),Vector3(.66,1.0,.66),Color("#b08755"))
	for side in [-1,1]:
		beveled_case(Vector3(side*.40,.23,0),Vector3(.17,.26,.38),Color("#253040"))

func canister() -> void:
	var body_color := Color("#59684f")
	var seal := Color("#414747")
	var hazard := Color("#e1aa49")
	var face := Basis(Vector3.RIGHT, PI*.5)
	tube(Vector3(0,.60,0),Vector3(.61,.87,.61),body_color)
	for y in [.14,.99]:
		tube(Vector3(0,y,0),Vector3(.74,.15,.74),seal)
		band(Vector3(0,y+.055,0),Vector3(.78,.73,.78),hazard)
	tube(Vector3(0,1.085,0),Vector3(.32,.14,.32),seal)
	band(Vector3(0,1.16,0),Vector3(.28,.8,.28),hazard)
	for side in [-1,1]:
		var turn := face if side == -1 else Basis(Vector3.RIGHT,-PI*.5)
		tube(Vector3(0,.58,side*.29),Vector3(.36,.12,.36),seal,turn)
		band(Vector3(0,.58,side*.35),Vector3(.31,.60,.31),hazard,turn)
		oval(Vector3(0,.58,side*.362),Vector3(.20,.20,.04),Color("#c12430"),Basis.IDENTITY,true)

func weapon(heavy: bool) -> void:
	var steel := Color("#687988")
	var shell := Color("#915447") if heavy else Color("#3d8583")
	var dark := Color("#293747")
	var grip := Color("#343039")
	var grip_hand := Color("#88716a")
	var light := Color("#ffc678") if heavy else Color("#73efcf")
	var bore := .11
	var forward := Basis(Vector3.RIGHT,PI*.5)
	beveled_case(Vector3(0,-.025,-.12),Vector3(.20,.19,.48),dark)
	beveled_case(Vector3(0,.055,-.19),Vector3(.22,.115,.43),shell)
	beveled_case(Vector3(0,.114,-.25),Vector3(.079,.045,.36),steel)
	beveled_case(Vector3(0,-.145,.048),Vector3(.105,.17,.14),grip)
	oval(Vector3(0,-.148,.09),Vector3(.15,.15,.18),grip_hand)
	oval(Vector3(-.109,-.093,-.22),Vector3(.113,.107,.22),grip_hand)
	if heavy:
		# Reagent reservoir and a broad fan nozzle give it a separate profile.
		beveled_case(Vector3(0,-.05,-.35),Vector3(.27,.13,.27),Color("#432655"))
		tube(Vector3(.005,.158,-.34),Vector3(.139,.295,.139),Color("#b742ec"),forward,.90,true)
		for z in [-.49,-.20]:
			band(Vector3(.005,.158,z),Vector3(.172,.55,.172),steel,forward)
		for z in [-.43,-.36,-.28]:
			oval(Vector3(.045,.183,z),Vector3(.023,.024,.033),Color("#edd1ff"),Basis.IDENTITY,true)
		beveled_case(Vector3(0,.016,-.555),Vector3(.27,.10,.17),steel)
		for x in [-.075,0,.075]:
			tube(Vector3(x,.018,-.641),Vector3(.060,.034,.060),Color("#df89ff"),forward,.78,true)
	else:
		tube(Vector3(0,.02,-.42),Vector3(bore,.32,bore),steel,forward)
		band(Vector3(0,.02,-.568),Vector3(bore*1.23,.58,bore*1.23),dark,forward)
		tube(Vector3(0,.02,-.570),Vector3(bore*.69,.022,bore*.69),Color("#161925"),forward)
		beveled_case(Vector3(.10,-.105,-.23),Vector3(.10,.13,.24),Color("#436966"))
		for z in [-.31,-.24,-.17]:
			piece(box(),Vector3(.153,-.09,z),Vector3(.013,.025,.031),light,Basis.IDENTITY,true)
	for side in [-1,1]:
		beveled_case(Vector3(side*.108,.015,-.16),Vector3(.044,.101,.27),steel)
		for i in 4:
			piece(box(),Vector3(side*.133,.024,-.235+i*.042),Vector3(.012,.051,.018),dark)
		beveled_case(Vector3(side*.065,.112,-.061),Vector3(.045,.035,.052),dark)
		piece(box(),Vector3(side*.065,.132,-.061),Vector3(.025,.013,.027),light,Basis.IDENTITY,true)
	beveled_case(Vector3(0,.13,-.46),Vector3(.025,.050,.036),steel)
	piece(box(),Vector3(0,.158,-.46),Vector3(.016,.013,.023),light,Basis.IDENTITY,true)

func conveyor_specimen() -> void:
	var bracket := Color("#77888c")
	var shell := Color("#384953")
	var glass_rim := Color("#7b9ea1")
	beveled_case(Vector3(0,.063,0),Vector3(.79,.12,.68),shell)
	beveled_case(Vector3(0,.131,0),Vector3(.59,.077,.53),bracket)
	for y in [.19,.59]:
		tube(Vector3(0,y,0),Vector3(.56,.105,.52),shell)
		band(Vector3(0,y+.031,0),Vector3(.57,.59,.55),glass_rim)
	for side in [-1,1]:
		beveled_case(Vector3(side*.247,.38,0),Vector3(.065,.43,.12),bracket)
		beveled_case(Vector3(0,.10,side*.276),Vector3(.31,.07,.041),Color("#f1bb66"))
	oval(Vector3(0,.392,0),Vector3(.33,.38,.32),Color("#9b74a0"),Basis(Vector3.RIGHT,.39))
	oval(Vector3(.024,.394,-.145),Vector3(.15,.18,.055),Color("#bdf084"),Basis.IDENTITY,true)
	for i in 3:
		oval(Vector3(-.077+i*.075,.277+i*.054,.117),Vector3(.091,.103,.087),Color("#abc875"))

func pressure_roots() -> void:
	for i in 6:
		var x := (i-2.5)*.36
		var lean := Basis(Vector3.FORWARD,.29 if i%2==0 else -.29)
		oval(Vector3(x,.14,0),Vector3(.36,.28,.34),Color("#665044"))
		tube(Vector3(x,.43,0),Vector3(.26,.74,.24),Color("#976957"),lean,.18)
		tube(Vector3(x+(-.092 if i%2==0 else .092),.75,0),Vector3(.065,.27,.063),
			Color("#edac70"),lean,.08,true)

func pressure_valve() -> void:
	var forward := Basis(Vector3.RIGHT,PI*.5)
	oval(Vector3.ZERO,Vector3(.62,.65,.47),Color("#765243"))
	band(Vector3(0,0,.175),Vector3(.57,.57,.60),Color("#be8b54"),forward)
	oval(Vector3(0,0,.202),Vector3(.31,.34,.19),Color("#ffc675"),Basis.IDENTITY,true)

func rupture_pod() -> void:
	var skin := Color("#72506d")
	var ridge := Color("#9d6e81")
	var core := Color("#e69fd1")
	oval(Vector3(0,-.07,0),Vector3(1.75,1.67,.90),skin)
	for i in 6:
		var angle := i*TAU/6.0
		var radial := Vector3(cos(angle),sin(angle),0)
		oval(radial*.59+Vector3(0,0,.185),Vector3(.36,.75,.46),ridge,
			Basis(Vector3.BACK,angle-PI*.5))
		oval(radial*.52+Vector3(0,0,.399),Vector3(.14,.18,.09),core,Basis.IDENTITY,true)
	oval(Vector3(0,.015,.435),Vector3(.58,.69,.23),Color("#4f3d56"))
	oval(Vector3(0,.04,.522),Vector3(.24,.42,.091),core,Basis.IDENTITY,true)

func culture_vent() -> void:
	var forward := Basis(Vector3.RIGHT,PI*.5)
	var alloy := Color("#687d86")
	var gasket := Color("#263940")
	beveled_case(Vector3(0,0,-.07),Vector3(.68,.68,.23),alloy)
	tube(Vector3(0,0,.09),Vector3(.56,.18,.56),gasket,forward)
	band(Vector3(0,0,.186),Vector3(.57,.62,.57),alloy,forward)
	for row in 3:
		piece(box(),Vector3(0,(row-1)*.117,.187),Vector3(.43,.052,.06),alloy)
		piece(box(),Vector3(0,(row-1)*.117-.03,.172),Vector3(.29,.025,.03),Color("#afe677"),Basis.IDENTITY,true)
	for side in [-1,1]:
		beveled_case(Vector3(side*.27,-.25,.101),Vector3(.10,.10,.086),Color("#b19569"))

func culture_shutter() -> void:
	var forward := Basis(Vector3.RIGHT,PI*.5)
	tube(Vector3(0,0,.237),Vector3(.49,.089,.49),Color("#53676e"),forward)
	band(Vector3(0,0,.28),Vector3(.48,.49,.48),Color("#9baca7"),forward)
	for x in [-.12,.12]:
		piece(box(),Vector3(x,0,.29),Vector3(.047,.27,.034),Color("#344b54"))

func culture_cassette() -> void:
	var bracket := Color("#658189")
	var dark := Color("#2a434c")
	for y in [-.48,.46]:
		beveled_case(Vector3(0,y,0),Vector3(.63,.15,.63),dark)
		tube(Vector3(0,y,0),Vector3(.57,.13,.57),bracket)
	for side in [-1,1]:
		beveled_case(Vector3(0,0,side*.244),Vector3(.14,.96,.085),bracket)
		oval(Vector3(.06,side*.18,.04),Vector3(.32,.38,.33),Color("#83b6a1"),Basis(Vector3.RIGHT,side*.40))
	for i in 3:
		oval(Vector3(-.109,i*.18-.19,-.153),Vector3(.073,.090,.063),Color("#b8e9ae"),Basis.IDENTITY,true)

func furnace_pump() -> void:
	var alloy := Color("#77716a")
	var dark := Color("#423b42")
	beveled_case(Vector3(0,.13,0),Vector3(.99,.25,.83),dark)
	tube(Vector3(0,.35,0),Vector3(.73,.36,.67),alloy)
	band(Vector3(0,.52,0),Vector3(.78,.72,.70),Color("#b17a56"))
	for side in [-1,1]:
		var pipe_axis := Basis(Vector3.BACK,PI*.5)
		tube(Vector3(side*.42,.30,0),Vector3(.22,.28,.22),alloy,pipe_axis)
		band(Vector3(side*.50,.30,0),Vector3(.26,.53,.26),dark,pipe_axis)

func pump_core() -> void:
	var amber := Color("#ffc080")
	tube(Vector3.ZERO,Vector3(.28,.57,.28),Color("#997264"))
	for y in [-.26,.26]:
		band(Vector3(0,y,0),Vector3(.39,.58,.39),Color("#aa9583"))
	for i in 3:
		var a := i*TAU/3.0
		oval(Vector3(cos(a)*.146,0,sin(a)*.146),Vector3(.11,.40,.11),amber,Basis.IDENTITY,true)

func furnace_nozzle() -> void:
	var alloy := Color("#77605d")
	var forward := Basis(Vector3.RIGHT,PI*.5)
	beveled_case(Vector3(0,-.19,-.02),Vector3(.98,.55,.78),Color("#3f363a"))
	tube(Vector3(0,0,.12),Vector3(.71,.43,.71),alloy,forward,.78)
	band(Vector3(0,0,.347),Vector3(.64,.68,.64),Color("#a17d61"),forward)
	oval(Vector3(0,0,.331),Vector3(.38,.38,.085),Color("#d55332"),Basis.IDENTITY,true)
	for x in [-.38,.38]:
		tube(Vector3(x,.07,-.15),Vector3(.17,.58,.17),alloy)

func furnace_iris() -> void:
	var forward := Basis(Vector3.RIGHT,PI*.5)
	tube(Vector3.ZERO,Vector3(.41,.068,.41),Color("#4b4043"),forward)
	for angle in [0.0,TAU/3.0,TAU*2.0/3.0]:
		var at := Vector3(.094,0,.038).rotated(Vector3.BACK,angle)
		piece(box(),at,Vector3(.16,.026,.028),Color("#a88b71"),Basis(Vector3.BACK,angle))

func king_armor() -> void:
	var bark := Color("#704453")
	var ridges := Color("#a47562")
	for side in [-1,1]:
		var tilt := Basis(Vector3.BACK,side*.46)
		oval(Vector3(side*.48,.67,.03),Vector3(.51,.49,.61),bark,tilt)
		oval(Vector3(side*.59,.55,-.08),Vector3(.34,.37,.42),ridges,tilt)
		for layer in 3:
			var point := Vector3(side*(.45+layer*.075),.83-layer*.065,-.15-layer*.021)
			oval(point,Vector3(.26,.11,.30),ridges.darkened(layer*.095),tilt)
		for claw in 3:
			var point := Vector3(side*(.41+claw*.09),.15,-.34+claw*.048)
			tube(point,Vector3(.09,.17,.09),ridges,Basis(Vector3.RIGHT,-PI*.38),.11)

func king_crown() -> void:
	var bronze := Color("#ac7651")
	var horn := Color("#d6aa75")
	band(Vector3(0,.03,0),Vector3(1.21,.76,1.02),bronze)
	for i in 7:
		var a := i*TAU/7.0
		var side := Vector3(cos(a),0,sin(a))
		var outward := Basis(Vector3.UP.cross(side),.30)
		var height := .56 if i%2==0 else .39
		tube(side*.52+Vector3.UP*height*.41,Vector3(.19,height,.17),horn,outward,.09)
		oval(side*.55+Vector3.UP*.096,Vector3(.11,.15,.11),Color("#e6a375"),Basis.IDENTITY,true)

func king_scars() -> void:
	var wound := Color("#4a283a")
	var glow_color := Color("#ff865f")
	for side in [-1,1]:
		var tilt := Basis(Vector3.BACK,side*.37)
		oval(Vector3(side*.49,.74,-.279),Vector3(.24,.30,.08),wound,tilt)
		oval(Vector3(side*.49,.76,-.322),Vector3(.088,.23,.031),glow_color,tilt,true)

func king_plate() -> void:
	beveled_case(Vector3.ZERO,Vector3(.27,.33,.13),Color("#705052"))
	oval(Vector3(0,.01,-.068),Vector3(.10,.22,.04),Color("#b38264"))

func king_core() -> void:
	oval(Vector3.ZERO,Vector3(.33,.28,.13),Color("#563748"))
	oval(Vector3(0,0,-.075),Vector3(.22,.22,.09),Color("#ffb867"),Basis.IDENTITY,true)
	oval(Vector3(0,0,-.122),Vector3(.10,.13,.05),Color("#fff3bc"),Basis.IDENTITY,true)


func spitter_foreleg() -> void:
	oval(Vector3(0,-.09,.01),Vector3(.36,.68,.41),Color("#376a62"),Basis(Vector3.FORWARD,.38))
	oval(Vector3(.05,-.37,-.18),Vector3(.41,.29,.63),Color("#548e75"))
	for toe in [-1,1]:
		tube(Vector3(.05+toe*.10,-.42,-.49),Vector3(.10,.18,.10),Color("#efd09c"),
			Basis(Vector3.RIGHT,-PI*.42),.12)

func nail_slide() -> void:
	beveled_case(Vector3(0,.039,-.045),Vector3(.061,.040,.225),Color("#a9babf"))
	for i in 3:
		piece(box(),Vector3(0,.062,-.081+i*.052),Vector3(.052,.013,.014),Color("#455962"))

func breaker_action() -> void:
	beveled_case(Vector3(0,.061,-.036),Vector3(.102,.067,.225),Color("#805655"))
	for side in [-1,1]:
		piece(box(),Vector3(side*.035,.099,-.065),Vector3(.026,.017,.113),Color("#bc9d72"))

func conveyor_drive() -> void:
	var face := Basis(Vector3.RIGHT,PI*.5)
	var steel := Color("#879692")
	var dark := Color("#353b46")
	beveled_case(Vector3(0,0,.05),Vector3(.72,.45,.23),dark)
	tube(Vector3(0,0,.17),Vector3(.35,.18,.35),steel,face)
	band(Vector3(0,0,.263),Vector3(.36,.62,.36),Color("#b2a488"),face)
	tube(Vector3(0,0,.278),Vector3(.13,.075,.13),dark,face)
	for side in [-1,1]:
		for y in [-.15,.15]:
			tube(Vector3(side*.27,y,.17),Vector3(.062,.045,.062),steel,face)
		piece(box(),Vector3(side*.275,0,.184),Vector3(.084,.035,.018),Color("#fdc077"),Basis.IDENTITY,true)

func pressure_manifold() -> void:
	var bronze := Color("#916952")
	var iron := Color("#484b54")
	var horizontal := Basis(Vector3.BACK,PI*.5)
	beveled_case(Vector3(0,-.18,0),Vector3(.90,.26,.54),iron)
	tube(Vector3(0,.02,0),Vector3(.26,.74,.26),bronze,horizontal)
	for x in [-.30,0.0,.30]:
		band(Vector3(x,.02,0),Vector3(.31,.70,.31),iron,horizontal)
		tube(Vector3(x,.14,0),Vector3(.14,.23,.14),bronze)
		oval(Vector3(x,.24,.037),Vector3(.086,.068,.079),Color("#e5a660"),Basis.IDENTITY,true)


func throne_root() -> void:
	var bark := Color("#634553")
	var growth := Color("#95645f")
	for i in 6:
		var t := i/5.0
		var tilt := Basis(Vector3.BACK,-.37-.37*t)
		oval(Vector3(-.69*t,-.36*t,0),Vector3(.56-.24*t,.53-.17*t,.48-.18*t),bark,tilt)
		oval(Vector3(-.66*t,-.36*t,.18-.07*t),Vector3(.20,.14,.13),growth,tilt)
	for i in 3:
		tube(Vector3(-.55-i*.18,-.38-i*.09,.015),Vector3(.18,.40,.17),growth,
			Basis(Vector3.BACK,PI+.32+i*.22),.10)

func arc_lance() -> void:
	var alloy := Color("#7797ad")
	var ceramic := Color("#263b53")
	var cyan := Color("#6bf5ff")
	var forward := Basis(Vector3.RIGHT,PI*.5)
	beveled_case(Vector3(0,.002,-.08),Vector3(.21,.19,.38),ceramic)
	beveled_case(Vector3(0,-.12,.06),Vector3(.11,.16,.16),Color("#342f46"))
	tube(Vector3(0,.065,-.27),Vector3(.165,.29,.165),Color("#405a92"),forward)
	for z in [-.36,-.30,-.24,-.18]:
		tube(Vector3(0,.065,z),Vector3(.174,.023,.174),cyan,forward,1.0,true)
	for side in [-1,1]:
		var x: float = side*.134
		beveled_case(Vector3(x,.053,-.45),Vector3(.053,.099,.34),alloy)
		beveled_case(Vector3(x,.075,-.595),Vector3(.05,.066,.155),ceramic)
		piece(box(),Vector3(x,.105,-.47),Vector3(.032,.014,.28),cyan,Basis.IDENTITY,true)
		for z in [-.38,-.48,-.57]:
			band(Vector3(x,.055,z),Vector3(.074,.48,.081),Color("#c4cdd4"),forward)
		oval(Vector3(x,.065,-.684),Vector3(.052,.06,.073),Color("#e3ffff"),Basis.IDENTITY,true)
	oval(Vector3(0,.069,-.456),Vector3(.079,.081,.115),Color("#d4f6ff"),Basis.IDENTITY,true)

func grav_mortar() -> void:
	var hull := Color("#584663")
	var copper := Color("#cb9476")
	var dark := Color("#272233")
	var violet := Color("#e998ff")
	var forward := Basis(Vector3.RIGHT,PI*.5)
	beveled_case(Vector3(0,-.032,-.12),Vector3(.30,.23,.43),hull)
	beveled_case(Vector3(0,-.15,.06),Vector3(.13,.16,.16),dark)
	tube(Vector3(0,.045,-.39),Vector3(.31,.42,.31),dark,forward)
	for z in [-.24,-.40,-.57]:
		band(Vector3(0,.045,z),Vector3(.36,.66,.36),copper,forward)
	tube(Vector3(0,.045,-.606),Vector3(.262,.073,.262),hull,forward)
	tube(Vector3(0,.045,-.645),Vector3(.205,.012,.205),violet,forward,1.0,true)
	tube(Vector3(0,.045,-.655),Vector3(.118,.014,.118),dark,forward)
	for side in [-1,1]:
		var x: float = side*.16
		beveled_case(Vector3(x,-.042,-.29),Vector3(.094,.155,.32),hull)
		for z in [-.39,-.31,-.23]:
			oval(Vector3(x+side*.043,-.028,z),Vector3(.025,.065,.048),violet,Basis.IDENTITY,true)

func triple_nails() -> void:
	var metal := Color("#d1dbe0")
	var brass := Color("#d1a453")
	for x in [-.19,0,.19]:
		tube(Vector3(x,.02,0),Vector3(.062,.49,.062),metal)
		tube(Vector3(x,-.267,0),Vector3(.062,.13,.062),metal,Basis(Vector3.RIGHT,PI),.05)
		tube(Vector3(x,.267,0),Vector3(.136,.049,.136),metal)
	beveled_case(Vector3.ZERO,Vector3(.59,.105,.123),brass)
	piece(box(),Vector3(0,0,-.066),Vector3(.44,.035,.015),Color("#ffdf8d"),Basis.IDENTITY,true)
