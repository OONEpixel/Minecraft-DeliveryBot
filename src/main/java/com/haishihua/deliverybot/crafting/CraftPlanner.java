package com.haishihua.deliverybot.crafting;

import com.haishihua.deliverybot.inventory.ItemStacks;
import com.haishihua.deliverybot.inventory.MutableStock;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class CraftPlanner {
    private final RecipeIndex recipes;
    private final int maxDepth;
    private final IngredientExtractor ingredientExtractor = new IngredientExtractor();

    public CraftPlanner(RecipeIndex recipes, int maxDepth) {
        this.recipes = recipes;
        this.maxDepth = maxDepth;
    }

    public Optional<CraftAttempt> plan(Material requested, int missing, MutableStock base, Workstations stations) {
        Branch initial = new Branch(base.copy(), new ArrayList<>(), new ArrayList<>());
        return produce(requested, missing, initial, stations, 0, EnumSet.noneOf(Material.class))
                .map(production -> new CraftAttempt(
                        production.branch().stock(),
                        production.result(),
                        ItemStacks.compact(production.branch().returnItems()),
                        production.branch().steps()
                ));
    }

    private Optional<Production> produce(
            Material requested,
            int amount,
            Branch base,
            Workstations stations,
            int depth,
            Set<Material> path
    ) {
        if (amount <= 0 || depth >= maxDepth || path.contains(requested)) {
            return Optional.empty();
        }

        Set<Material> nextPath = path.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(path);
        nextPath.add(requested);
        for (RecipeOption option : options(requested, stations)) {
            Recipe recipe = option.recipe();
            ItemStack result = recipe.getResult();
            int crafts = craftCount(result, amount);
            List<IngredientNeed> ingredients = ingredients(recipe, crafts);
            if (ingredients.isEmpty()) {
                continue;
            }

            Branch candidate = base.copy();
            Map<Material, Integer> consumedByRecipe = new EnumMap<>(Material.class);
            List<IngredientNeed> ordered = new ArrayList<>(ingredients);
            MutableStock stockForOrdering = candidate.stock();
            ordered.sort(Comparator.comparingInt(need -> stockForOrdering.total(need.choice()::test)));

            boolean valid = true;
            for (IngredientNeed ingredient : ordered) {
                Optional<Satisfaction> satisfaction = satisfy(
                        ingredient.choice(),
                        ingredient.amount(),
                        candidate,
                        stations,
                        depth + 1,
                        nextPath
                );
                if (satisfaction.isEmpty()) {
                    valid = false;
                    break;
                }
                candidate = satisfaction.get().branch();
                satisfaction.get().consumed().forEach(
                        (material, count) -> consumedByRecipe.merge(material, count, Integer::sum)
                );
            }
            if (!valid) {
                continue;
            }

            int produced = Math.multiplyExact(crafts, result.getAmount());
            int overflow = produced - amount;
            if (overflow > 0) {
                candidate.returnItems().addAll(ItemStacks.split(result, overflow));
            }
            candidate.returnItems().addAll(craftingRemainders(consumedByRecipe));
            candidate.steps().add(new CraftingStep(option.workstation(), describe(recipe), crafts));
            return Optional.of(new Production(candidate, result.clone()));
        }
        return Optional.empty();
    }

    private Optional<Satisfaction> satisfy(
            RecipeChoice choice,
            int amount,
            Branch base,
            Workstations stations,
            int depth,
            Set<Material> path
    ) {
        Branch withStock = base.copy();
        List<ItemStack> taken = withStock.stock().takeUpTo(choice::test, amount);
        Map<Material, Integer> consumed = new EnumMap<>(Material.class);
        int supplied = 0;
        for (ItemStack item : taken) {
            supplied += item.getAmount();
            consumed.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        int missing = amount - supplied;
        if (missing == 0) {
            return Optional.of(new Satisfaction(withStock, consumed));
        }

        List<Material> choices = sortedChoices(
                craftableChoices(choice),
                material -> optionCount(material, stations)
        );
        for (Material material : choices) {
            Optional<Production> production = produce(material, missing, withStock.copy(), stations, depth, path);
            if (production.isEmpty() || !choice.test(production.get().result())) {
                continue;
            }
            Map<Material, Integer> completedConsumption = new EnumMap<>(Material.class);
            completedConsumption.putAll(consumed);
            completedConsumption.merge(material, missing, Integer::sum);
            return Optional.of(new Satisfaction(production.get().branch(), completedConsumption));
        }
        return Optional.empty();
    }

    private List<RecipeOption> options(Material result, Workstations stations) {
        List<RecipeOption> resultOptions = new ArrayList<>();
        stations.stonecutter().ifPresent(location -> recipes.stonecutting(result)
                .forEach(recipe -> resultOptions.add(new RecipeOption(recipe, location))));
        stations.craftingTable().ifPresent(location -> recipes.crafting(result)
                .forEach(recipe -> resultOptions.add(new RecipeOption(recipe, location))));
        return resultOptions;
    }

    private int optionCount(Material material, Workstations stations) {
        int count = 0;
        if (stations.stonecutter().isPresent()) {
            count += recipes.stonecutting(material).size();
        }
        if (stations.craftingTable().isPresent()) {
            count += recipes.crafting(material).size();
        }
        return count;
    }

    private List<Material> craftableChoices(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices().stream().distinct().toList();
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().stream().map(ItemStack::getType).distinct().toList();
        }
        return List.of();
    }

    static List<Material> sortedChoices(List<Material> source, ToIntFunction<Material> optionCounter) {
        List<Material> choices = new ArrayList<>(source);
        choices.sort(Comparator
                .comparingInt(optionCounter)
                .reversed()
                .thenComparing(Material::name));
        return choices;
    }

    private List<IngredientNeed> ingredients(Recipe recipe, int crafts) {
        if (recipe instanceof StonecuttingRecipe stonecutting) {
            return ingredientExtractor.extract(stonecutting, crafts);
        }
        if (recipe instanceof CraftingRecipe crafting) {
            return ingredientExtractor.extract(crafting, crafts);
        }
        return List.of();
    }

    private int craftCount(ItemStack result, int missing) {
        return (missing + result.getAmount() - 1) / result.getAmount();
    }

    private String describe(Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey().toString() : recipe.getClass().getSimpleName();
    }

    private List<ItemStack> craftingRemainders(Map<Material, Integer> consumed) {
        List<ItemStack> result = new ArrayList<>();
        addRemainder(result, Material.BUCKET,
                consumed.getOrDefault(Material.MILK_BUCKET, 0)
                        + consumed.getOrDefault(Material.WATER_BUCKET, 0)
                        + consumed.getOrDefault(Material.LAVA_BUCKET, 0)
                        + consumed.getOrDefault(Material.POWDER_SNOW_BUCKET, 0));
        addRemainder(result, Material.GLASS_BOTTLE, consumed.getOrDefault(Material.HONEY_BOTTLE, 0));
        return result;
    }

    private void addRemainder(List<ItemStack> result, Material material, int amount) {
        if (amount > 0) {
            result.addAll(ItemStacks.split(new ItemStack(material), amount));
        }
    }

    private record RecipeOption(Recipe recipe, Location workstation) {
    }

    private record Production(Branch branch, ItemStack result) {
    }

    private record Satisfaction(Branch branch, Map<Material, Integer> consumed) {
    }

    private record Branch(MutableStock stock, List<ItemStack> returnItems, List<CraftingStep> steps) {
        private Branch copy() {
            return new Branch(
                    stock.copy(),
                    returnItems.stream().map(ItemStack::clone).collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                    new ArrayList<>(steps)
            );
        }
    }
}
