package com.haishihua.deliverybot.inventory;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

public record ContainerAccess(ContainerKey key, Location location, Inventory inventory) {
}
