package com.haishihua.deliverybot.crafting;

import org.bukkit.Location;

import java.util.Optional;

public record Workstations(Optional<Location> stonecutter, Optional<Location> craftingTable) {
}
