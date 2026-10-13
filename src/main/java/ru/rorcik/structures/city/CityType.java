package ru.rorcik.structures.city;

import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.core.zone.ZoneRules;
import java.util.Set;

public enum CityType {
    PLAINS(
            "plains",
            Set.of(TemperatureZone.COOL, TemperatureZone.NEUTRAL, TemperatureZone.WARM),
            64,   
            5000, 
            200   
    ),
    DESERT(
            "desert",
            Set.of(TemperatureZone.HOT, TemperatureZone.EXTREME_HOT),
            -1,   
            1500,
            100
    );

    public final String id;
    public final Set<TemperatureZone> allowedZones;
    public final int targetHeight;
    public final int minDistance;
    public final int estimatedRadius;

    CityType(String id, Set<TemperatureZone> allowedZones, int targetHeight, int minDistance, int estimatedRadius) {
        this.id = id;
        this.allowedZones = allowedZones;
        this.targetHeight = targetHeight;
        this.minDistance = minDistance;
        this.estimatedRadius = estimatedRadius;
    }

    public ZoneRules toZoneRules() {
        return new ZoneRules(this.id, this.minDistance, this.estimatedRadius);
    }
}