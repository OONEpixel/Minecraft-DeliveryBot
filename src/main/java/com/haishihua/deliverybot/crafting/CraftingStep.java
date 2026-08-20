package com.haishihua.deliverybot.crafting;

import org.bukkit.Location;

public record CraftingStep(Location workstation, String recipeDescription, int crafts) {
    public CraftingStep {
        workstation = workstation.clone();
    }

    @Override
    public Location workstation() {
        return workstation.clone();
    }
}
