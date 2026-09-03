package com.downinglabs.lyriqwidget;

/** A place a vehicle's battery can be read from. */
public interface VehicleSource {
    String SMARTCAR = "smartcar";
    String HOME_ASSISTANT = "ha";

    /** Fetches a fresh snapshot. Runs on a background thread. */
    BatterySnapshot fetch(Vehicle vehicle) throws Exception;

    static VehicleSource forVehicle(Vehicle vehicle) {
        return HOME_ASSISTANT.equals(vehicle.source()) ? new HomeAssistantSource() : new SmartcarSource();
    }
}
