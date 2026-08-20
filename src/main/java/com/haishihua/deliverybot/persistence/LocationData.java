package com.haishihua.deliverybot.persistence;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

public record LocationData(String world, double x, double y, double z, float yaw, float pitch) {
    public static LocationData from(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new LocationData(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public Optional<Location> resolve() {
        World resolved = Bukkit.getWorld(world);
        if (resolved == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(resolved, x, y, z, yaw, pitch));
    }
}
