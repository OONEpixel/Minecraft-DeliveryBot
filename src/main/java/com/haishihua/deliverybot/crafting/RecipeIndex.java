package com.haishihua.deliverybot.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RecipeIndex {
    private final Map<Material, List<StonecuttingRecipe>> stonecutting = new EnumMap<>(Material.class);
    private final Map<Material, List<CraftingRecipe>> crafting = new EnumMap<>(Material.class);

    public void reload() {
        stonecutting.clear();
        crafting.clear();

        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            index(iterator.next());
        }

        sortRecipes();
    }

    private void index(Recipe recipe) {
        if (recipe.getResult().getType().isAir() || recipe.getResult().getAmount() <= 0) {
            return;
        }
        Material result = recipe.getResult().getType();
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            stonecutting.computeIfAbsent(result, ignored -> new ArrayList<>()).add(stonecuttingRecipe);
        } else if (recipe instanceof CraftingRecipe craftingRecipe) {
            crafting.computeIfAbsent(result, ignored -> new ArrayList<>()).add(craftingRecipe);
        }
    }

    private void sortRecipes() {
        Comparator<Recipe> byKey = Comparator.comparing((Recipe recipe) -> recipe instanceof Keyed keyed
                ? keyed.getKey().toString()
                : recipe.getResult().getType().name());
        stonecutting.values().forEach(list -> list.sort(byKey));
        crafting.values().forEach(list -> list.sort(byKey));
    }

    public List<StonecuttingRecipe> stonecutting(Material result) {
        return stonecutting.getOrDefault(result, List.of());
    }

    public List<CraftingRecipe> crafting(Material result) {
        return crafting.getOrDefault(result, List.of());
    }
}
