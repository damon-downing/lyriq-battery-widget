package com.downinglabs.lyriqwidget;

/** A place a vehicle's battery can be read from. */
public interface VehicleSource {
    String SMARTCAR = "smartcar";
    String HOME_ASSISTANT = "ha";
    String MANUAL = "manual"; // TEMP demo source — see ManualSource.java

    /** Fetches a fresh snapshot. Runs on a background thread. */
    BatterySnapshot fetch(Vehicle vehicle) throws Exception;

    static VehicleSource forVehicle(Vehicle vehicle) {
        switch (vehicle.source()) {
            case HOME_ASSISTANT: return new HomeAssistantSource();
            case MANUAL: return new ManualSource();
            default: return new SmartcarSource();
        }
    }
}
