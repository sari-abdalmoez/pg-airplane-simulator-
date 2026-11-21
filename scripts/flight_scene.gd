extends Node3D

var hud: CanvasLayer
var status_label: Label
var stats_label: Label
var paused := false

func _ready() -> void:
    _build_world()
    _build_hud()
    var plane := CharacterBody3D.new()
    plane.name = "Aircraft"
    plane.position = Vector3(0, 2.2, 0)
    plane.set_script(load("res://scripts/flight_controller.gd"))
    add_child(plane)

func _process(_delta: float) -> void:
    if Input.is_action_just_pressed("pause"):
        paused = not paused
        get_tree().paused = paused
        status_label.text = "PAUSED — press ESC to resume" if paused else "FLIGHT ACTIVE"

func _build_world() -> void:
    var env := WorldEnvironment.new()
    var environment := Environment.new()
    environment.background_mode = Environment.BG_SKY
    var sky := Sky.new()
    var sky_mat := ProceduralSkyMaterial.new()
    sky_mat.sky_top_color = Color(0.13, 0.32, 0.55)
    sky_mat.sky_horizon_color = Color(0.72, 0.78, 0.82)
    sky.sky_material = sky_mat
    environment.sky = sky
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.environment = environment
    add_child(env)

    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-48, -28, 0)
    sun.light_energy = 1.15
    add_child(sun)

    var ground := StaticBody3D.new()
    ground.position = Vector3(0, -0.6, -80)
    add_child(ground)
    var ground_mesh := MeshInstance3D.new()
    var plane_mesh := PlaneMesh.new()
    plane_mesh.size = Vector2(1200, 1200)
    ground_mesh.mesh = plane_mesh
    var grass := StandardMaterial3D.new()
    grass.albedo_color = Color(0.18, 0.31, 0.19)
    ground_mesh.material_override = grass
    ground.add_child(ground_mesh)
    var collider := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = Vector3(1200, 1, 1200)
    collider.shape = shape
    ground.add_child(collider)

    var runway := MeshInstance3D.new()
    var runway_mesh := BoxMesh.new()
    runway_mesh.size = Vector3(32, 0.18, 520)
    runway.mesh = runway_mesh
    runway.position = Vector3(0, 0.02, -70)
    var asphalt := StandardMaterial3D.new()
    asphalt.albedo_color = Color(0.12, 0.13, 0.15)
    runway.material_override = asphalt
    add_child(runway)

    for z in range(-315, 190, 30):
        var stripe := MeshInstance3D.new()
        var stripe_mesh := BoxMesh.new()
        stripe_mesh.size = Vector3(0.65, 0.04, 12)
        stripe.mesh = stripe_mesh
        stripe.position = Vector3(0, 0.13, z)
        var white := StandardMaterial3D.new()
        white.albedo_color = Color(0.92, 0.92, 0.86)
        stripe.material_override = white
        add_child(stripe)

func _build_hud() -> void:
    hud = CanvasLayer.new()
    add_child(hud)
    var panel := PanelContainer.new()
    panel.position = Vector2(22, 22)
    panel.custom_minimum_size = Vector2(330, 150)
    hud.add_child(panel)
    var box := VBoxContainer.new()
    panel.add_child(box)
    status_label = Label.new()
    status_label.text = "FLIGHT ACTIVE"
    status_label.add_theme_color_override("font_color", Color(0.15, 0.7, 0.3))
    status_label.add_theme_font_size_override("font_size", 18)
    box.add_child(status_label)
    stats_label = Label.new()
    stats_label.text = "IAS  0 kt\nALT  0 ft\nTHR  0%"
    stats_label.add_theme_font_size_override("font_size", 16)
    box.add_child(stats_label)
    var help := Label.new()
    help.text = "W/S Pitch  •  A/D Roll  •  Q/E Rudder\nR/F Throttle  •  G Gear  •  C Camera\nI/O Engine  •  SPACE Brake  •  ESC Pause"
    help.position = Vector2(22, 625)
    help.add_theme_color_override("font_color", Color(1, 1, 1))
    hud.add_child(help)
    add_to_group("flight_hud")

func update_flight(speed: float, altitude: float, throttle: float, engine_on: bool, gear_down: bool) -> void:
    if is_instance_valid(stats_label):
        stats_label.text = "IAS  %03d kt\nALT  %05d ft\nTHR  %02d%%\nENGINE %s  |  GEAR %s" % [
            int(speed * 1.94384), int(altitude * 3.28084), int(throttle * 100),
            "ON" if engine_on else "OFF", "DOWN" if gear_down else "UP"
        ]

func _exit_tree() -> void:
    get_tree().paused = false
