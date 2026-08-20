package com.haishihua.deliverybot.request;

import org.bukkit.Material;

public record RequestSpec(Material material, int amount, int requestedUnits, RequestUnit unit) {
}
