"""Procedural Cadillac LYRIQ for the battery widget.

Builds a lofted body from the side profile traced over a photo (same 400x150 design
space CarRenderer uses), a tumblehome greenhouse, ten-spoke wheels, studio lights and a
3/4 front camera, then renders two layers that the app composites at runtime:

  body_diff.png   diffuse shading of the paint with a WHITE base colour (multiply by paint)
  body_gloss.png  glossy/clearcoat reflections of the paint (add on top)
  rest.png        everything else — glass, wheels, lamps, ground shadow — with the paint
                  surfaces held out (transparent) so the tinted body shows through

Run:  Blender -b -P lyriq_model.py -- OUT_DIR WIDTH HEIGHT SAMPLES
"""
import bpy, bmesh, math, sys, os
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "/tmp/lyriq"
W = int(argv[1]) if len(argv) > 1 else 1400
H = int(argv[2]) if len(argv) > 2 else 640
SAMPLES = int(argv[3]) if len(argv) > 3 else 96
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- dimensions (m)
LENGTH, WIDTH, HEIGHT, WHEELBASE = 4.996, 1.977, 1.623, 3.094
TIRE_R, TIRE_W, TRACK = 0.375, 0.27, 1.66
ARCH_R = 0.455
HALF_W = 0.965

# Design-space (400x150) → metres. x: 18..387 spans the length; y: 22 → roof, 132 → sill.
def X(dx): return (dx - 202.5) / 369.0 * LENGTH
def Z(dy): return 1.62 - (dy - 22) * (1.42 / 110.0)

def interp(points, x):
    """Catmull-Rom spline through (x, y) points sorted by x; clamped outside."""
    if x <= points[0][0]: return points[0][1]
    if x >= points[-1][0]: return points[-1][1]
    for i in range(len(points) - 1):
        x1, y1 = points[i]; x2, y2 = points[i + 1]
        if x <= x2:
            x0, y0 = points[max(i - 1, 0)]; x3, y3 = points[min(i + 2, len(points) - 1)]
            t = (x - x1) / (x2 - x1)
            # tangents scaled to the segment length so uneven spacing stays smooth
            m1 = (y2 - y0) / (x2 - x0) * (x2 - x1) if x2 != x0 else 0.0
            m2 = (y3 - y1) / (x3 - x1) * (x2 - x1) if x3 != x1 else 0.0
            t2, t3 = t * t, t * t * t
            return (2 * t3 - 3 * t2 + 1) * y1 + (t3 - 2 * t2 + t) * m1 + (-2 * t3 + 3 * t2) * y2 + (t3 - t2) * m2
    return points[-1][1]

# Lower body top surface: nose → hood → beltline → rear deck (design coords)
LOWER_TOP = [(18, 78), (22, 70), (30, 66), (45, 62), (60, 61), (90, 61), (120, 60), (148, 59),
             (158, 64), (180, 64), (220, 63), (260, 62), (300, 61), (330, 59), (354, 57),
             (368, 58), (375, 61), (382, 66), (386, 78), (387, 96)]
LOWER_TOP = [(X(a), Z(b)) for a, b in LOWER_TOP]
# Sill / bottom of the body
Z_BOT = Z(132)
# Greenhouse: beltline (bottom of glass) and roof line
BELT = [(150, 60), (156, 66), (170, 68), (220, 66), (260, 64), (300, 62), (330, 59), (354, 56), (372, 55), (386, 52)]
ROOF = [(148, 59), (160, 49), (176, 36), (196, 29), (212, 24), (236, 22), (260, 24), (290, 26),
        (318, 33), (342, 42), (356, 47), (370, 48), (384, 47), (387, 51)]
BELT = [(X(a), Z(b)) for a, b in BELT]
ROOF = [(X(a), Z(b)) for a, b in ROOF]

def plan_halfwidth(x):
    """Half-width of the lower body along the length, rounded at both ends."""
    xn, xt = X(18), X(387)
    pts = [(xn, 0.50), (xn + 0.04, 0.66), (xn + 0.15, 0.80), (xn + 0.5, 0.90), (xn + 1.2, HALF_W),
           (0.0, HALF_W), (xt - 1.0, HALF_W), (xt - 0.5, 0.93), (xt - 0.15, 0.87), (xt - 0.04, 0.78), (xt, 0.66)]
    return interp(pts, x)

# ---------------------------------------------------------------- scene reset
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = W, H
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.image_settings.color_depth = '8'
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'

def mat(name, **kw):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    for k, v in kw.items():
        bsdf.inputs[k].default_value = v
    return m

PAINT = mat("Paint", **{"Base Color": (1, 1, 1, 1), "Metallic": 0.15, "Roughness": 0.32,
                        "Coat Weight": 1.0, "Coat Roughness": 0.03})
GLASS = mat("Glass", **{"Base Color": (0.02, 0.025, 0.03, 1), "Metallic": 0.25, "Roughness": 0.12,
                        "Coat Weight": 1.0, "Coat Roughness": 0.02})
TRIM = mat("Trim", **{"Base Color": (0.015, 0.016, 0.018, 1), "Roughness": 0.35})
TIRE = mat("Tire", **{"Base Color": (0.02, 0.02, 0.022, 1), "Roughness": 0.7})
RIM = mat("Rim", **{"Base Color": (0.78, 0.8, 0.83, 1), "Metallic": 1.0, "Roughness": 0.22})
SPOKE = mat("Spoke", **{"Base Color": (0.12, 0.13, 0.15, 1), "Metallic": 0.9, "Roughness": 0.35})
CHROME = mat("Chrome", **{"Base Color": (0.9, 0.92, 0.95, 1), "Metallic": 1.0, "Roughness": 0.08})

def emissive(name, color, strength):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial"); em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = color; em.inputs["Strength"].default_value = strength
    nt.links.new(em.outputs[0], out.inputs[0]); return m

DRL = emissive("DRL", (1.0, 0.93, 0.75, 1), 14.0)
TAIL = emissive("Tail", (1.0, 0.05, 0.03, 1), 10.0)

def new_object(name, verts, faces, material, smooth=True):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    ob.data.materials.append(material)
    if smooth:
        for p in me.polygons: p.use_smooth = True
    return ob

def loft(name, stations, ring_fn, material, n_ring=48):
    """stations: list of x; ring_fn(x, theta) -> (y, z). Closed ring per station, capped."""
    verts, faces = [], []
    for x in stations:
        for i in range(n_ring):
            th = 2 * math.pi * i / n_ring
            y, z = ring_fn(x, th)
            verts.append((x, y, z))
    S = len(stations)
    for s in range(S - 1):
        for i in range(n_ring):
            a = s * n_ring + i; b = s * n_ring + (i + 1) % n_ring
            faces.append((a, b, b + n_ring, a + n_ring))
    faces.append(tuple(range(n_ring))[::-1])
    faces.append(tuple(range((S - 1) * n_ring, S * n_ring)))
    return new_object(name, verts, faces, material)

def superellipse(th, a, b, n):
    c, s = math.cos(th), math.sin(th)
    return (math.copysign(abs(c) ** (2 / n), c) * a, math.copysign(abs(s) ** (2 / n), s) * b)


def split_by_height(ob, hi_name, hi_mat, lo_name, lo_mat):
    """Separate ob by material index, then name/material the two halves by their mean height
    (Blender does not guarantee which half keeps the original object)."""
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True); bpy.context.view_layer.objects.active = ob
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.separate(type='MATERIAL'); bpy.ops.object.mode_set(mode='OBJECT')
    parts = list(bpy.context.selected_objects)
    def mean_z(o): return sum(v.co.z for v in o.data.vertices) / max(1, len(o.data.vertices))
    parts.sort(key=mean_z)
    lo, hi = parts[0], parts[-1]
    lo.name, hi.name = lo_name, hi_name
    for o, m in ((lo, lo_mat), (hi, hi_mat)):
        o.data.materials.clear(); o.data.materials.append(m)
    return hi, lo

# ---------------------------------------------------------------- lower body
def body_ring(x, th):
    zt = interp(LOWER_TOP, x)
    w = plan_halfwidth(x)
    h = (zt - Z_BOT) / 2; zc = Z_BOT + h
    y, z = superellipse(th, w, h, 4.6)
    # tumblehome above the shoulder and a tucked-in sill below
    if z > 0: y *= 1 - 0.10 * (z / h) ** 2.5
    else:     y *= 1 - 0.05 * (-z / h) ** 3
    z = zc + z
    # wheel wells: lift the outer skin over an arch around each axle
    for ax in (-WHEELBASE / 2, WHEELBASE / 2):
        d = x - ax
        if abs(d) < ARCH_R and abs(y) > 0.50:
            z = max(z, TIRE_R + math.sqrt(ARCH_R * ARCH_R - d * d))
    return (y, z)

xs = [X(18)] + [X(18) + (X(387) - X(18)) * (i / 90.0) for i in range(1, 90)] + [X(387)]
body = loft("Body", xs, body_ring, PAINT, 72)
bm = bmesh.new(); bm.from_mesh(body.data)
body.data.materials.append(TRIM)
for f in bm.faces:
    cm = f.calc_center_median()
    f.material_index = 1 if (cm.z < Z_BOT + 0.11 and abs(cm.y) > 0.45 and X(40) < cm.x < X(372)) else 0
bm.to_mesh(body.data); bm.free()
body, sill = split_by_height(body, "Body", PAINT, "Sill", TRIM)
sub = body.modifiers.new("subd", 'SUBSURF'); sub.levels = sub.render_levels = 2

# ---------------------------------------------------------------- greenhouse (glass + roof)
def cabin_ring(x, th):
    zb = interp(BELT, x) - 0.04
    zr = interp(ROOF, x)
    wb = plan_halfwidth(x) * (0.93 - 0.10 * max(0.0, (x - 0.6) / 1.9))
    h = (zr - zb) / 2; zc = zb + h
    y, z = superellipse(th, wb, h, 4.0)
    if z > 0: y *= 1 - 0.25 * (z / h) ** 1.6   # tumblehome up to the roof
    return (y, zc + z)

cx0, cx1 = X(146), X(386)
cxs = [cx0 + (cx1 - cx0) * (i / 60.0) for i in range(61)]
cabin = loft("Cabin", cxs, cabin_ring, GLASS, 48)
sub = cabin.modifiers.new("subd", 'SUBSURF'); sub.levels = sub.render_levels = 2
# split the top of the cabin off as a painted roof
bm = bmesh.new(); bm.from_mesh(cabin.data)
cabin.data.materials.append(PAINT)
for f in bm.faces:
    zr = interp(ROOF, f.calc_center_median().x)
    f.material_index = 1 if f.calc_center_median().z > zr - 0.03 else 0
bm.to_mesh(cabin.data); bm.free()
roof, cabin = split_by_height(cabin, "Roof", PAINT, "Cabin", GLASS)

# ---------------------------------------------------------------- wheel wells + wheels
def cylinder(name, r, depth, loc, rot, material, verts=64):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    ob = bpy.context.active_object; ob.name = name
    ob.data.materials.append(material)
    for p in ob.data.polygons: p.use_smooth = True
    return ob

AXLES = [-WHEELBASE / 2, WHEELBASE / 2]
for ax in AXLES:
    for side in (-1, 1):
        yc = side * TRACK / 2
        # arch cut
        # dark liner inside the well
        cylinder("Liner", TIRE_R + 0.06, 0.34, (ax, side * (TRACK / 2 - 0.08), TIRE_R + 0.01), (math.pi / 2, 0, 0), TRIM)
        # tire
        tire = cylinder("Tire", TIRE_R, TIRE_W, (ax, yc, TIRE_R), (math.pi / 2, 0, 0), TIRE, 96)
        bev = tire.modifiers.new("bevel", 'BEVEL'); bev.width = 0.035; bev.segments = 4
        # rim: bright lip, dark dish, ten spokes, chrome cap
        rim_y = yc + side * (TIRE_W / 2 + 0.012)
        cylinder("RimLip", TIRE_R * 0.66, 0.03, (ax, rim_y, TIRE_R), (math.pi / 2, 0, 0), RIM, 96)
        cylinder("RimDish", TIRE_R * 0.64, 0.06, (ax, rim_y - side * 0.045, TIRE_R), (math.pi / 2, 0, 0), SPOKE, 96)
        for i in range(10):
            a = 2 * math.pi * i / 10
            bpy.ops.mesh.primitive_cube_add(size=1, location=(ax + math.cos(a) * TIRE_R * 0.34, rim_y + side * 0.012, TIRE_R + math.sin(a) * TIRE_R * 0.34))
            sp = bpy.context.active_object; sp.name = "Spoke"
            sp.scale = (TIRE_R * 0.60, 0.02, 0.028); sp.rotation_euler = (0, -a, 0)
            sp.data.materials.append(RIM)
        cylinder("Hub", TIRE_R * 0.11, 0.04, (ax, rim_y + side * 0.01, TIRE_R), (math.pi / 2, 0, 0), CHROME, 32)

# ---------------------------------------------------------------- lamps, mirrors, trim
def box(name, loc, scale, material, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    ob = bpy.context.active_object; ob.name = name; ob.scale = scale
    ob.data.materials.append(material); return ob

nose_x = X(18)
for side in (-1, 1):
    # vertical LED blade wrapping the front corner
    box("DRL", (nose_x + 0.02, side * (plan_halfwidth(nose_x + 0.08) - 0.03), Z(92)), (0.02, 0.035, Z(74) - Z(110)), DRL)
    box("DRLtop", (nose_x + 0.14, side * (plan_halfwidth(nose_x + 0.14) - 0.02), Z(70)), (0.2, 0.025, 0.02), DRL)
    # thin vertical tail lamp on the rear corner
    box("Tail", (X(387) - 0.02, side * (plan_halfwidth(X(386)) - 0.03), Z(98)), (0.03, 0.05, Z(80) - Z(116)), TAIL)
    # mirror
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.5, segments=24, ring_count=12, location=(X(158), side * (HALF_W + 0.09), Z(64)))
    mir = bpy.context.active_object; mir.name = "Mirror"; mir.scale = (0.16, 0.2, 0.09); mir.data.materials.append(PAINT)
    for p in mir.data.polygons: p.use_smooth = True
    # chrome sill strip and flush handles
    box("Sill", (X(236), side * (plan_halfwidth(X(236)) + 0.004), Z(112)), (X(330) - X(142), 0.01, 0.018), CHROME)
    box("Handle", (X(191), side * (plan_halfwidth(X(191)) + 0.004), Z(74)), (0.17, 0.01, 0.025), CHROME)
    box("Handle", (X(283), side * (plan_halfwidth(X(283)) + 0.004), Z(70)), (0.17, 0.01, 0.025), CHROME)
# black-crystal grille panel on the nose
box("Grille", (nose_x + 0.045, 0, Z(94)), (0.03, plan_halfwidth(nose_x + 0.06) * 1.05, Z(78) - Z(110)), TRIM)

# ---------------------------------------------------------------- ground, lights, camera
bpy.ops.mesh.primitive_plane_add(size=30, location=(0, 0, 0))
ground = bpy.context.active_object; ground.name = "Ground"; ground.is_shadow_catcher = True
ground.data.materials.append(mat("Ground", **{"Base Color": (0.5, 0.5, 0.5, 1)}))

def area(name, loc, size, energy, target=(0, 0, 0.7), color=(1, 1, 1)):
    bpy.ops.object.light_add(type='AREA', location=loc)
    l = bpy.context.active_object; l.name = name
    l.data.energy = energy; l.data.size = size; l.data.color = color
    d = Vector(target) - Vector(loc); l.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return l

area("Key", (-6, -7, 5.5), 6, 170)
area("Fill", (5, -7, 2.5), 8, 70, color=(0.85, 0.9, 1.0))
area("Rim", (3, 6, 4), 5, 150)
area("Top", (0, -1, 6), 14, 130)   # big softbox for the horizon-style reflection on the shoulder
world = bpy.data.worlds.new("World"); scene.world = world; world.use_nodes = True
bg = world.node_tree.nodes["Background"]; bg.inputs[0].default_value = (0.05, 0.055, 0.06, 1); bg.inputs[1].default_value = 1.0

bpy.ops.object.camera_add(location=(-8.2, -12.8, 1.55))
cam = bpy.context.active_object; scene.camera = cam
cam.data.lens = 95; cam.data.sensor_width = 36
d = Vector((-0.05, 0.0, 0.72)) - cam.location; cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

# ---------------------------------------------------------------- render passes
PAINT_OBJS = [o for o in scene.objects if o.type == 'MESH' and o.data.materials and o.data.materials[0] == PAINT]
NON_PAINT = [o for o in scene.objects if o.type == 'MESH' and o not in PAINT_OBJS]

bsdf = PAINT.node_tree.nodes["Principled BSDF"]
def set_paint(base, coat, metallic, rough):
    bsdf.inputs["Base Color"].default_value = base
    bsdf.inputs["Coat Weight"].default_value = coat
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough

# Pass A: paint only (other parts hidden so they neither occlude nor leak into the layers)
for o in NON_PAINT: o.hide_render = True
ground.visible_camera = False  # floor stays for bounce light but must not add shadow alpha here
# A1: pure white diffuse -> multiply by the paint colour at runtime
set_paint((1, 1, 1, 1), 0.0, 0.0, 0.9)
scene.render.filepath = os.path.join(OUT, "body_diff")
bpy.ops.render.render(write_still=True)
# A2: black base under a clearcoat -> only the reflections, added at runtime
set_paint((0, 0, 0, 1), 1.0, 0.0, 0.3)
scene.render.filepath = os.path.join(OUT, "body_gloss")
bpy.ops.render.render(write_still=True)
set_paint((1, 1, 1, 1), 1.0, 0.15, 0.32)

# Pass B: everything else, paint held out
for o in NON_PAINT: o.hide_render = False
ground.visible_camera = True
for o in PAINT_OBJS: o.is_holdout = True
scene.render.filepath = os.path.join(OUT, "rest")
bpy.ops.render.render(write_still=True)

# Preview composite with a red paint, for eyeballing
for o in PAINT_OBJS:
    o.is_holdout = False
PAINT.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.42, 0.02, 0.05, 1)
scene.render.filepath = os.path.join(OUT, "preview_red")
bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "lyriq.blend"))
print("DONE", OUT)
