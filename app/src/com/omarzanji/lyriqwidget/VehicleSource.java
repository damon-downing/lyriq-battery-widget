package com.omarzanji.lyriqwidget;

/** A place the widget can read the car's battery from. */
public interface VehicleSource {
    String SMARTCAR = "smartcar";
    String HOME_ASSISTANT = "ha";
    String MANUAL = "manual";

    /** Fetches a fresh snapshot. Runs on a background thread. */
    BatterySnapshot fetch(Prefs prefs) throws Exception;

    static VehicleSource forPrefs(Prefs prefs) {
        switch (prefs.source()) {
            case SMARTCAR:
                return new SmartcarSource();
            case HOME_ASSISTANT:
                return new HomeAssistantSource();
            default:
                return new ManualSource();
        }
    }
}
