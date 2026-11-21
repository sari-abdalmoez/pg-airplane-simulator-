extends Node

const SETTINGS_PATH := "user://sari_settings.cfg"

var selected_aircraft := 0
var selected_airport := 0
var settings := {
    "master_volume": 0.8,
    "music_volume": 0.55,
    "effects_volume": 0.8,
    "quality": "medium",
    "muted": false
}

var aircraft_db := [
    {"name":"S-100 KESTREL RG", "speed":82.0, "lift":1.0, "thrust":34.0, "description":"Light single-engine trainer"},
    {"name":"S-200 MERIDIAN", "speed":112.0, "lift":1.15, "thrust":43.0, "description":"High-performance touring aircraft"}
]
var airport_db := [
    {"name":"SARI INTERNATIONAL", "runway_length":1800.0, "elevation":1120.0},
    {"name":"HIGHLAND RIDGE", "runway_length":1250.0, "elevation":1640.0}
]

func _ready() -> void:
    load_settings()

func aircraft_cfg() -> Dictionary:
    return aircraft_db[clampi(selected_aircraft, 0, aircraft_db.size() - 1)]

func airport_cfg() -> Dictionary:
    return airport_db[clampi(selected_airport, 0, airport_db.size() - 1)]

func quality_params() -> Dictionary:
    match str(settings.get("quality", "medium")):
        "low":
            return {"trees": 80, "terrain_resolution": 48, "shadows": false}
        "high":
            return {"trees": 420, "terrain_resolution": 120, "shadows": true}
        _:
            return {"trees": 200, "terrain_resolution": 80, "shadows": true}

func load_settings() -> void:
    var cfg := ConfigFile.new()
    if cfg.load(SETTINGS_PATH) == OK:
        for key in settings:
            settings[key] = cfg.get_value("settings", key, settings[key])

func save_settings() -> void:
    var cfg := ConfigFile.new()
    for key in settings:
        cfg.set_value("settings", key, settings[key])
    cfg.save(SETTINGS_PATH)

func set_setting(key: String, value: Variant) -> void:
    if settings.has(key):
        settings[key] = value
        save_settings()

func start_flight() -> void:
    get_tree().change_scene_to_file("res://scenes/flight_scene.tscn")

func goto_menu() -> void:
    get_tree().change_scene_to_file("res://scenes/main_menu.tscn")
