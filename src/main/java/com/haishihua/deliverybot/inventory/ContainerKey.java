package com.haishihua.deliverybot.inventory;

import org.bukkit.Location;

public record ContainerKey(String world, int x, int y, int z) {
    public static ContainerKey from(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new ContainerKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static ContainerKey parse(String value) {
        String[] parts = value.split(";", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid container location: " + value);
        }
        return new ContainerKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
