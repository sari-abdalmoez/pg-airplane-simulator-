extends Control

var page := "home"
var title_label: Label
var info_label: Label
var body: VBoxContainer

func _ready() -> void:
    _build_ui()

func _build_ui() -> void:
    var bg := ColorRect.new()
    bg.color = Color(0.035, 0.075, 0.13)
    bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(bg)

    var margin := MarginContainer.new()
    margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    margin.add_theme_constant_override("margin_left", 54)
    margin.add_theme_constant_override("margin_right", 54)
    margin.add_theme_constant_override("margin_top", 42)
    margin.add_theme_constant_override("margin_bottom", 42)
    add_child(margin)

    var layout := HBoxContainer.new()
    layout.add_theme_constant_override("separation", 48)
    margin.add_child(layout)

    var left := VBoxContainer.new()
    left.custom_minimum_size.x = 360
    left.add_theme_constant_override("separation", 18)
    layout.add_child(left)

    title_label = Label.new()
    title_label.text = "SARI\nFLIGHT SIMULATOR"
    title_label.add_theme_font_size_override("font_size", 38)
    title_label.add_theme_color_override("font_color", Color(0.92, 0.96, 1))
    left.add_child(title_label)

    var subtitle := Label.new()
    subtitle.text = "FLIGHT • EXPLORE • MASTER"
    subtitle.add_theme_color_override("font_color", Color(0.3, 0.78, 0.95))
    left.add_child(subtitle)

    var spacer := Control.new()
    spacer.custom_minimum_size.y = 18
    left.add_child(spacer)

    _add_button(left, "START FLIGHT", _start)
    _add_button(left, "SELECT AIRCRAFT", _aircraft)
    _add_button(left, "SELECT AIRPORT", _airport)
    _add_button(left, "SETTINGS", _settings)
    _add_button(left, "QUIT", _quit)

    var right := PanelContainer.new()
    right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    right.size_flags_vertical = Control.SIZE_EXPAND_FILL
    var style := StyleBoxFlat.new()
    style.bg_color = Color(0.07, 0.14, 0.22, 0.96)
    style.set_corner_radius_all(18)
    right.add_theme_stylebox_override("panel", style)
    layout.add_child(right)

    body = VBoxContainer.new()
    body.add_theme_constant_override("separation", 20)
    body.size_flags_vertical = Control.SIZE_EXPAND_FILL
    right.add_child(body)
    info_label = Label.new()
    info_label.size_flags_vertical = Control.SIZE_EXPAND_FILL
    info_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    info_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    info_label.add_theme_font_size_override("font_size", 23)
    info_label.add_theme_color_override("font_color", Color(0.83, 0.91, 0.98))
    body.add_child(info_label)
    _show_home()

func _add_button(parent: VBoxContainer, text: String, action: Callable) -> void:
    var button := Button.new()
    button.text = text
    button.custom_minimum_size = Vector2(300, 54)
    button.add_theme_font_size_override("font_size", 17)
    button.pressed.connect(action)
    parent.add_child(button)

func _clear_body() -> void:
    for child in body.get_children():
        child.queue_free()

func _show_home() -> void:
    _clear_body()
    info_label.text = "WELCOME TO THE SKIES\n\nYour flight begins here.\n\nAircraft: %s\nAirport: %s\n\nUse W/S to pitch, A/D to roll,\nR/F for throttle and C to change camera." % [
        GameManager.aircraft_cfg().name, GameManager.airport_cfg().name
    ]

func _aircraft() -> void:
    GameManager.selected_aircraft = (GameManager.selected_aircraft + 1) % GameManager.aircraft_db.size()
    _show_home()

func _airport() -> void:
    GameManager.selected_airport = (GameManager.selected_airport + 1) % GameManager.airport_db.size()
    _show_home()

func _settings() -> void:
    _clear_body()
    info_label.text = "SETTINGS\n\nQuality: %s\nMaster volume: %d%%\n\nUse the buttons on the left to return." % [
        str(GameManager.settings.quality), int(GameManager.settings.master_volume * 100)
    ]

func _start() -> void:
    GameManager.start_flight()

func _quit() -> void:
    get_tree().quit()
