package com.haishihua.deliverybot.crafting;

import org.bukkit.inventory.RecipeChoice;

public record IngredientNeed(RecipeChoice choice, int amount) {
}
