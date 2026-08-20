package com.haishihua.deliverybot.crafting;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftPlannerTest {
    @Test
    void sortsAnImmutableRecipeChoiceListUsingAMutableCopy() {
        List<Material> immutableChoices = List.of(Material.COBBLESTONE, Material.DIORITE);

        List<Material> sorted = CraftPlanner.sortedChoices(
                immutableChoices,
                material -> material == Material.DIORITE ? 2 : 0
        );

        assertEquals(List.of(Material.DIORITE, Material.COBBLESTONE), sorted);
        assertEquals(List.of(Material.COBBLESTONE, Material.DIORITE), immutableChoices);
    }
}
