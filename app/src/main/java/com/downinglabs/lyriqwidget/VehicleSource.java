package com.downinglabs.lyriqwidget;

/** A place a vehicle's battery can be read from. */
public interface VehicleSource {
    String SMARTCAR = "smartcar";
    String MANUAL = "manual";

    /** Fetches a fresh snapshot. Runs on a background thread. */
    BatterySnapshot fetch(Vehicle vehicle) throws Exception;

    static VehicleSource forVehicle(Vehicle vehicle) {
        return MANUAL.equals(vehicle.source()) ? new ManualSource() : new SmartcarSource();
    }
}
