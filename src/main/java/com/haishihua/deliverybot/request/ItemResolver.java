package com.haishihua.deliverybot.request;

import org.bukkit.Material;

import java.util.Optional;

public interface ItemResolver {
    Optional<Material> resolve(String input);

    String display(Material material);

    default int maxStackSize(Material material) {
        return material.getMaxStackSize();
    }
}
