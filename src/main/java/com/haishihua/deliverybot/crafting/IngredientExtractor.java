package com.haishihua.deliverybot.crafting;

import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IngredientExtractor {
    public List<IngredientNeed> extract(StonecuttingRecipe recipe, int crafts) {
        return List.of(new IngredientNeed(recipe.getInputChoice(), crafts));
    }

    public List<IngredientNeed> extract(CraftingRecipe recipe, int crafts) {
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getChoiceList().stream()
                    .map(choice -> new IngredientNeed(choice, crafts))
                    .toList();
        }
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, Integer> occurrences = new HashMap<>();
            for (String row : shaped.getShape()) {
                for (char symbol : row.toCharArray()) {
                    if (symbol != ' ') {
                        occurrences.merge(symbol, 1, Integer::sum);
                    }
                }
            }

            List<IngredientNeed> result = new ArrayList<>();
            for (Map.Entry<Character, RecipeChoice> entry : shaped.getChoiceMap().entrySet()) {
                Integer count = occurrences.get(entry.getKey());
                if (entry.getValue() != null && count != null && count > 0) {
                    result.add(new IngredientNeed(entry.getValue(), count * crafts));
                }
            }
            return result;
        }
        return List.of();
    }
}
