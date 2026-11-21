extends CharacterBody3D

@export var max_speed := 125.0
@export var acceleration := 17.0
@export var turn_rate := 1.15
@export var lift_factor := 0.34
@export var gravity_strength := 9.8

var throttle := 0.0
var engine_on := true
var gear_down := true
var camera_mode := 0
var pitch_input := 0.0
var roll_input := 0.0
var yaw_input := 0.0
var speed := 0.0
var altitude := 0.0
var vertical_speed := 0.0
var aircraft_mesh: Node3D

func _ready() -> void:
    throttle = 0.35
    _build_aircraft()

func _physics_process(delta: float) -> void:
    _read_input(delta)
    var cfg: Dictionary = GameManager.aircraft_cfg()
    max_speed = float(cfg.get("speed", 82.0)) * 1.5
    if engine_on:
        speed = move_toward(speed, throttle * max_speed, acceleration * delta)
    else:
        speed = move_toward(speed, 0.0, acceleration * 0.35 * delta)

    rotate_object_local(Vector3.UP, yaw_input * turn_rate * delta)
    rotate_object_local(Vector3.RIGHT, pitch_input * turn_rate * delta)
    rotate_object_local(Vector3.FORWARD, roll_input * turn_rate * 1.4 * delta)

    var forward := -global_transform.basis.z
    var lift := maxf(0.0, speed - 12.0) * lift_factor
    vertical_speed += (lift - gravity_strength) * delta
    vertical_speed = clampf(vertical_speed, -28.0, 22.0)
    if global_position.y <= 2.0 and vertical_speed < 0.0:
        vertical_speed = 0.0
        global_position.y = 2.0
    velocity = forward * speed + Vector3.UP * vertical_speed
    if Input.is_action_pressed("brake") and global_position.y <= 2.1:
        velocity *= 0.72
    move_and_slide()
    altitude = global_position.y
    _update_hud_values()

func _read_input(delta: float) -> void:
    pitch_input = Input.get_action_strength("pitch_down") - Input.get_action_strength("pitch_up")
    roll_input = Input.get_action_strength("roll_right") - Input.get_action_strength("roll_left")
    yaw_input = Input.get_action_strength("rud_right") - Input.get_action_strength("rud_left")
    if Input.is_action_pressed("thr_up"):
        throttle = minf(1.0, throttle + delta * 0.35)
    if Input.is_action_pressed("thr_down"):
        throttle = maxf(0.0, throttle - delta * 0.35)
    if Input.is_action_just_pressed("engine_start"):
        engine_on = true
    if Input.is_action_just_pressed("engine_stop"):
        engine_on = false
    if Input.is_action_just_pressed("gear"):
        gear_down = not gear_down
    if Input.is_action_just_pressed("camera"):
        camera_mode = (camera_mode + 1) % 2
        var cam := get_node_or_null("CameraRig/Camera3D")
        if cam:
            cam.position = Vector3(0, 2.8, 9) if camera_mode == 0 else Vector3(0, 1.1, -3.2)

func _update_hud_values() -> void:
    var hud := get_tree().get_first_node_in_group("flight_hud")
    if hud and hud.has_method("update_flight"):
        hud.update_flight(speed, altitude, throttle, engine_on, gear_down)

func _build_aircraft() -> void:
    aircraft_mesh = Node3D.new()
    aircraft_mesh.name = "AircraftVisual"
    add_child(aircraft_mesh)
    var fuselage := _mesh_box(Vector3(0.8, 0.65, 7.0), Color(0.82, 0.86, 0.9))
    aircraft_mesh.add_child(fuselage)
    var wing := _mesh_box(Vector3(12.0, 0.22, 1.45), Color(0.12, 0.38, 0.58))
    wing.position = Vector3(0, 0.0, -0.2)
    aircraft_mesh.add_child(wing)
    var tail := _mesh_box(Vector3(3.6, 0.18, 0.9), Color(0.12, 0.38, 0.58))
    tail.position = Vector3(0, 0.7, 2.7)
    aircraft_mesh.add_child(tail)
    var fin := _mesh_box(Vector3(0.18, 1.4, 1.0), Color(0.9, 0.34, 0.12))
    fin.position = Vector3(0, 1.0, 2.8)
    aircraft_mesh.add_child(fin)
    var rig := Node3D.new()
    rig.name = "CameraRig"
    add_child(rig)
    var camera := Camera3D.new()
    camera.position = Vector3(0, 2.8, 9)
    camera.rotation_degrees.x = -9
    camera.current = true
    rig.add_child(camera)

func _mesh_box(size: Vector3, color: Color) -> MeshInstance3D:
    var mesh := MeshInstance3D.new()
    var box := BoxMesh.new()
    box.size = size
    mesh.mesh = box
    var mat := StandardMaterial3D.new()
    mat.albedo_color = color
    mat.metallic = 0.18
    mat.roughness = 0.42
    mesh.material_override = mat
    return mesh
