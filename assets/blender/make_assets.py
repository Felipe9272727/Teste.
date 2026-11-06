# Goose Skies v5 — Blender source-of-truth for the in-game 3D assets.
# Run: blender -b --python assets/blender/make_assets.py
# Generates a real .blend and material-colored triangle mesh header for the
# Goose native OpenGL renderer. No external model assets are downloaded.
import bpy
import math
import os
from mathutils import Vector

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "generated")
os.makedirs(OUT, exist_ok=True)

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)

def material(name, color, roughness=.52, metallic=0):
    m = bpy.data.materials.new(name=name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*color, 1)
        bsdf.inputs["Roughness"].default_value = roughness
        bsdf.inputs["Metallic"].default_value = metallic
    return m

chalk = material("Goose • warm ivory", (.93,.95,.94), .72)
cream = material("Goose • feather cream", (.83,.86,.83), .76)
feather = material("Goose • wing highlight", (.99,.99,.96), .55)
shadow = material("Goose • shaded feathers", (.69,.76,.75), .78)
bill = material("Beak • coral gold", (1.0,.48,.17), .45)
bill_top = material("Beak • orange highlight", (1.0,.65,.23), .43)
iris = material("Eye • dark slate", (.045,.07,.09), .18)
glint = material("Eye • small glint", (1,1,1), .10)
blush = material("Cheek • soft coral", (.95,.62,.52), .76)
foot = material("Foot • amber orange", (.95,.37,.11), .56)
pinewood = material("Pine • birch bark", (.40,.28,.20), .85)
pine1 = material("Pine • deep spruce", (.13,.40,.33), .79)
pine2 = material("Pine • seafoam needles", (.23,.54,.42), .72)
pine3 = material("Pine • light tips", (.35,.67,.50), .72)
rock = material("Rock • blue sandstone", (.48,.55,.54), .91)
wood = material("Cabin • warm timber", (.62,.41,.26), .73)
woodlight = material("Cabin • trim", (.88,.68,.43), .74)
roof = material("Cabin • terracotta", (.63,.29,.27), .75)
rooflight = material("Cabin • tile highlight", (.82,.43,.35), .75)
glass = material("Cabin • glazed sky", (.27,.62,.72), .24)
dark = material("Cabin • doorway", (.19,.25,.29), .76)

def assign(o, mat, group):
    o.data.materials.append(mat)
    o["goose_asset"] = group
    return o

def sphere(name, loc, scale, mat, group="goose", rings=12, segments=20):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segments, ring_count=rings, location=loc)
    o = bpy.context.object
    o.name = name
    o.scale = scale
    bpy.ops.object.shade_smooth()
    return assign(o, mat, group)

def cylinder(name, loc, radius, depth, mat, group="goose", vertices=12):
    bpy.ops.mesh.primitive_cylinder_add(
        vertices=vertices, radius=radius, depth=depth, location=loc)
    o = bpy.context.object
    o.name = name
    for poly in o.data.polygons:
        poly.use_smooth = poly.loop_total == 4
    return assign(o, mat, group)

def cone(name, loc, radius1, radius2, depth, mat, group="goose", vertices=12):
    bpy.ops.mesh.primitive_cone_add(
        vertices=vertices, radius1=radius1, radius2=radius2,
        depth=depth, location=loc)
    o = bpy.context.object
    o.name = name
    for poly in o.data.polygons:
        poly.use_smooth = poly.loop_total == 4
    return assign(o, mat, group)

def cube(name, loc, scale, mat, group="goose", bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.object
    o.name = name
    o.scale = scale
    assign(o, mat, group)
    if bevel:
        mod = o.modifiers.new("Soft corners", "BEVEL")
        mod.width = bevel
        mod.segments = 2
        o.modifiers.new("Weighted normals", "WEIGHTED_NORMAL")
    return o

def pipe(name, path, radii, mat, group="goose", steps=12):
    # A seamless, curved organic neck built from perpendicular ring sections.
    verts = []
    faces = []
    for j, p in enumerate(path):
        center = Vector(p)
        tangent = Vector(path[min(j+1, len(path)-1)]) - Vector(path[max(0,j-1)])
        tangent.normalize()
        u = tangent.cross(Vector((1,0,0))).normalized()
        v = tangent.cross(u).normalized()
        for k in range(steps):
            a = 2*math.pi*k/steps
            at = center + (u*math.cos(a) + v*math.sin(a))*radii[j]
            verts.append(tuple(at))
    for j in range(len(path)-1):
        for k in range(steps):
            t = j*steps+k
            n = j*steps+(k+1)%steps
            faces.append((t,n,n+steps,t+steps))
    faces.append(tuple(reversed(range(steps))))
    faces.append(tuple((len(path)-1)*steps+k for k in range(steps)))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    o = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(o)
    assign(o, mat, group)
    for poly in mesh.polygons:
        poly.use_smooth = True
    return o

# -------- Blender-sculpted goose --------
sphere("01 | Pear-shaped body", (0,0,0.02), (.73,.54,1.03), chalk)
sphere("02 | Breast and belly", (0,-.16,-.39), (.62,.44,.72), cream)
sphere("03 | Tail tuft", (0,.21,1.0), (.44,.31,.46), feather)

pipe("04 | Continuous elegant neck", [
    (0,.24,-.59),(0,.38,-.72),(0,.59,-.80),
    (0,.82,-.89),(0,1.06,-.98),(0,1.25,-1.03)
], [.25,.245,.215,.198,.217,.265], chalk, steps=18)
sphere("05 | Round head", (0,1.42,-1.055), (.43,.39,.43), chalk)
sphere("06 | Brow feather", (0,1.57,-1.26), (.35,.15,.27), feather)

# Beak, two separated upper/lower parts, styled as a short wedge.
pipe("07 | Golden upper beak", [
    (0,1.21,-1.37),(0,1.185,-1.52),(0,1.175,-1.70)
], [.205,.16,.070], bill_top, steps=16)
pipe("08 | Lower beak", [
    (0,1.125,-1.42),(0,1.115,-1.53),(0,1.12,-1.65)
], [.155,.12,.047], bill, steps=14)

for side in (-1,1):
    tag = "L" if side < 0 else "R"
    # Eyeballs are intentionally slightly forward and outward.
    sphere("09 | Eye socket "+tag,
           (.294*side,1.49,-1.255), (.145,.15,.105), cream)
    sphere("10 | Big ink eye "+tag,
           (.327*side,1.52,-1.304), (.103,.110,.065), iris, segments=24)
    sphere("11 | Eye catchlight "+tag,
           (.341*side,1.56,-1.365), (.035,.040,.023), glint, segments=12)
    sphere("12 | Peach cheek "+tag,
           (.325*side,1.32,-1.20), (.14,.095,.12), blush)
    # Rounded articulated wing plus five sculpted feather layers.
    sphere("13 | Wing silhouette "+tag,
           (.62*side,.12,.12), (.43,.27,.72), cream)
    for n in range(5):
        z = .22 + n*.145
        x = side*(.74+.035*n)
        sphere("14 | Flight feather %s-%d"%(tag,n),
               (x,.12-(n%2)*.018,z), (.25,.14,.40-n*.038),
               shadow if n>=4 else feather, segments=16)
    # Orange feet clearly visible under character.
    cylinder("15 | Shin "+tag,(.30*side,-.59,.30), .095,.41,foot,vertices=10)
    sphere("16 | Web foot "+tag,(.35*side,-.84,.06),(.235,.085,.32),foot)
    for t in range(3):
        sphere("17 | Toe %s-%d"%(tag,t),
               ((.21+t*.13)*side,-.87,-.16),
               (.09,.07,.27),foot,segments=12)
for i in (-1,0,1):
    sphere("18 | Long soft tail feather %d"%i,
           (.12*i,.18,1.17),(.17,.17,.40),feather)

# -------- Designed vegetation (readable at mobile resolutions) --------
cylinder("Pine trunk", (0,.69,0), .19, 1.38, pinewood, "pine", vertices=8)
cone("Pine lower crown", (0,1.82,0),1.16,.25,2.05,pine1,"pine",vertices=9)
cone("Pine middle crown", (0,2.60,0),.90,.21,1.91,pine2,"pine",vertices=9)
cone("Pine upper crown", (0,3.27,0),.66,.05,1.55,pine3,"pine",vertices=9)
for side in (-1,1):
    sphere("Pine pale tips %s"%side,(.70*side,1.47,.18),
           (.20,.15,.14),pine3,"pine",segments=10)

# -------- Designed house (visible door/window and roof shape) --------
cube("Cottage walls",(0,1.12,0),(3.45,2.24,3.2),wood,"cottage",bevel=.045)
cube("Corner beam L",(-1.60,1.14,-1.61),(.14,2.27,.14),woodlight,"cottage")
cube("Corner beam R",(1.60,1.14,-1.61),(.14,2.27,.14),woodlight,"cottage")
cube("Entrance", (0,.74,-1.64),(.85,1.45,.08),dark,"cottage")
cube("Door handle",(.30,.73,-1.74),(.08,.08,.065),bill_top,"cottage")
for sx in (-1,1):
    cube("Window glass %s"%sx,(1.14*sx,1.44,-1.66),(.59,.54,.055),
         glass,"cottage")
    cube("Window horizontal trim %s"%sx,(1.14*sx,1.44,-1.72),(.64,.055,.07),
         woodlight,"cottage")
    cube("Window vertical trim %s"%sx,(1.14*sx,1.44,-1.72),(.055,.59,.07),
         woodlight,"cottage")
# Pitched roof, two long sloped planar solid panels.
for sx in (-1,1):
    o = cube("Roof slope %s"%sx,(sx*.91,2.77,0),(2.38,.19,3.85),
             rooflight,"cottage",bevel=.05)
    o.rotation_euler[1] = .0
    o.rotation_euler[0] = 0
    o.rotation_euler[1] = sx*.0
    o.rotation_euler[2] = sx*.48
cube("Chimney",(.96,3.47,.55),(.40,.80,.47),rock,"cottage",bevel=.035)

# Make a clean showcase Blender file (lights/camera are excluded from runtime mesh).
bpy.ops.object.camera_add(location=(7,6,9))
camera = bpy.context.object
camera.name = "Preview • 3/4 studio"
direction = Vector((0,.52,0)) - camera.location
camera.rotation_euler = direction.to_track_quat("-Z","Y").to_euler()
bpy.context.scene.camera = camera
bpy.ops.object.light_add(type="AREA", location=(-3,6,5))
bpy.context.object.data.energy=1100
bpy.context.object.data.shape="DISK"
bpy.context.object.data.size=8
bpy.context.scene.render.engine="CYCLES"
bpy.context.scene.cycles.samples=24
bpy.context.scene.render.resolution_x=960
bpy.context.scene.render.resolution_y=720
bpy.context.scene.render.resolution_percentage=100
bpy.context.scene.world.color=(.28,.4,.5)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT,"GooseSkies_Character_Assets.blend"))

# Export evaluated, triangulated Blender meshes to a *static* C data header.
# The native Goose renderer streams these as VBOs, without C gameplay code.
objects = [o for o in bpy.context.scene.objects if
           o.type=="MESH" and "goose_asset" in o]
groups = ("goose","pine","cottage")
assets = []
deps = bpy.context.evaluated_depsgraph_get()
for group in groups:
    floats=[]
    count=0
    for obj in objects:
        if obj["goose_asset"]!=group: continue
        evaluated=obj.evaluated_get(deps)
        mesh=evaluated.to_mesh()
        mesh.calc_loop_triangles()
        matcol=(.92,.92,.92)
        if obj.active_material:
            matcol=tuple(obj.active_material.diffuse_color[:3])
        xform=obj.matrix_world
        normalmat=xform.to_3x3().inverted().transposed()
        for tri in mesh.loop_triangles:
            for li in tri.loops:
                loop=mesh.loops[li]
                co=xform @ mesh.vertices[loop.vertex_index].co
                normal=(normalmat @ loop.normal).normalized()
                floats.extend((co.x,co.y,co.z,
                               normal.x,normal.y,normal.z,
                               *matcol))
                count+=1
        evaluated.to_mesh_clear()
    assets.append((group,count,floats))
    print("EXPORT",group,"VERTICES",count,"FLOATS",len(floats))

path=os.path.join(OUT,"goose_assets.h")
with open(path,"w") as f:
    f.write("#pragma once\n/* Generated from the actual Blender .blend source. */\n")
    for index, (group,count,vals) in enumerate(assets):
        f.write("static const int goose_asset_count_%d = %d;\n"%(index,count))
        f.write("static const float goose_asset_%d[] = {\n"%index)
        for j in range(0,len(vals),9):
            f.write(" ".join("%.7ff,"%v for v in vals[j:j+9])+"\n")
        f.write("};\n")
    f.write("static const int goose_asset_counts[3] = {%s};\n"%
            ",".join(str(a[1]) for a in assets))
print("BLENDER DONE",path)
