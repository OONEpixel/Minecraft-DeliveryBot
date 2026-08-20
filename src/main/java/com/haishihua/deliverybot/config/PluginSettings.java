package com.haishihua.deliverybot.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PluginSettings(
        String botName,
        List<String> requestPrefixes,
        int maxRequestAmount,
        int boxSlots,
        int requestCooldownSeconds,
        int warehouseScanRadius,
        int workstationRadius,
        int warehouseBulkRegisterRadius,
        int warehouseBulkRegisterMaxRadius,
        Set<String> allowedContainers,
        double walkMaxDistance,
        double walkMaxHeightDifference,
        double walkSpeed,
        int walkTimeoutTicks,
        double flightSpeed,
        double cruiseHeight,
        double arrivalDistance,
        double precisionApproachHorizontalDistance,
        double precisionApproachSpeed,
        int flightTimeoutTicks,
        int boostIntervalTicks,
        boolean teleportHomeOnFailure,
        int craftingMaxDepth,
        int craftingAnimationTicks,
        int dropIntervalTicks,
        double dropDistance,
        double deliveryPositionTolerance
) {
    public static PluginSettings load(FileConfiguration config) {
        List<String> prefixes = List.copyOf(config.getStringList("request.prefixes"));
        if (prefixes.isEmpty()) {
            prefixes = List.of("机器人");
        }

        Set<String> containers = new HashSet<>();
        for (String value : config.getStringList("warehouse.allowed-containers")) {
            containers.add(value.toUpperCase(Locale.ROOT));
        }

        return new PluginSettings(
                config.getString("bot.name", "DeliveryBot"),
                prefixes,
                Math.max(1, config.getInt("request.max-amount", 2304)),
                Math.max(1, config.getInt("request.box-slots", 27)),
                Math.max(0, config.getInt("request.cooldown-seconds", 5)),
                Math.max(1, config.getInt("warehouse.scan-radius", 32)),
                Math.max(1, config.getInt("warehouse.workstation-radius", 16)),
                Math.max(1, config.getInt("warehouse.bulk-register-radius", 12)),
                Math.max(1, config.getInt("warehouse.bulk-register-max-radius", 24)),
                Set.copyOf(containers),
                Math.max(2.0, config.getDouble("movement.walk-max-distance", 14.0)),
                Math.max(1.0, config.getDouble("movement.walk-max-height-difference", 3.0)),
                Math.max(0.08, config.getDouble("movement.walk-speed", 0.23)),
                Math.max(100, config.getInt("movement.walk-timeout-ticks", 400)),
                Math.max(0.2, config.getDouble("flight.speed", 1.15)),
                Math.max(2.0, config.getDouble("flight.cruise-height", 12.0)),
                Math.max(0.8, config.getDouble("flight.arrival-distance", 1.8)),
                Math.max(1.0, config.getDouble("flight.precision-approach-horizontal-distance", 6.0)),
                Math.max(0.15, config.getDouble("flight.precision-approach-speed", 0.55)),
                Math.max(100, config.getInt("flight.timeout-ticks", 1200)),
                Math.max(10, config.getInt("flight.boost-interval-ticks", 30)),
                config.getBoolean("flight.teleport-home-on-failure", true),
                Math.max(1, config.getInt("crafting.max-depth", 6)),
                Math.max(0, config.getInt("crafting.animation-ticks", 20)),
                Math.max(1, config.getInt("delivery.drop-interval-ticks", 4)),
                Math.max(1.5, config.getDouble("delivery.drop-distance", 3.0)),
                Math.max(0.25, config.getDouble("delivery.position-tolerance", 0.65))
        );
    }

    public boolean isAllowedContainer(Material material) {
        String name = material.name();
        return allowedContainers.contains(name)
                || (allowedContainers.contains("SHULKER_BOX") && name.endsWith("_SHULKER_BOX"));
    }
}
