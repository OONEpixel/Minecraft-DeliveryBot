package com.haishihua.deliverybot.request;

import com.haishihua.deliverybot.config.PluginSettings;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestParserTest {
    private RequestParser parser;

    @BeforeEach
    void setUp() {
        PluginSettings settings = new PluginSettings(
                "DeliveryBot",
                List.of("机器人", "@快递员"),
                2304,
                27,
                5,
                32,
                16,
                12,
                24,
                Set.of("CHEST"),
                14,
                3,
                0.23,
                400,
                1.15,
                12,
                1.8,
                6,
                0.55,
                1200,
                30,
                true,
                6,
                20,
                4,
                3,
                0.65
        );
        Map<String, Material> aliases = Map.of(
                "石砖", Material.STONE_BRICKS,
                "橡木木板", Material.OAK_PLANKS,
                "安山岩", Material.ANDESITE,
                "磨制安山岩", Material.POLISHED_ANDESITE,
                "闪长岩", Material.DIORITE,
                "火把", Material.TORCH,
                "钻石", Material.DIAMOND
        );
        parser = new RequestParser(settings, new ItemResolver() {
            @Override
            public Optional<Material> resolve(String input) {
                Material material = aliases.get(input);
                if (material != null) {
                    return Optional.of(material);
                }
                return Optional.ofNullable(Material.matchMaterial(input.replace("minecraft:", "").toUpperCase()));
            }

            @Override
            public String display(Material material) {
                return material.name();
            }

            @Override
            public int maxStackSize(Material material) {
                return 64;
            }
        });
    }

    @Test
    void parsesChineseDeliveryRequest() {
        ParseResult result = parser.parse("机器人 送我 64 石砖");

        assertTrue(result.successful());
        assertEquals(Material.STONE_BRICKS, result.request().material());
        assertEquals(64, result.request().amount());
    }

    @Test
    void parsesNamespacedMaterialAndDefaultAmount() {
        ParseResult result = parser.parse("@快递员 给我 minecraft:diamond");

        assertTrue(result.successful());
        assertEquals(Material.DIAMOND, result.request().material());
        assertEquals(1, result.request().amount());
    }

    @Test
    void ignoresOrdinaryChat() {
        ParseResult result = parser.parse("今天去挖钻石吗？");

        assertFalse(result.addressed());
    }

    @Test
    void rejectsOutOfRangeAmount() {
        ParseResult result = parser.parse("机器人 送我 2305 石砖");

        assertTrue(result.addressed());
        assertFalse(result.successful());
    }

    @Test
    void expandsOneBoxToTwentySevenFullStacks() {
        ParseResult result = parser.parse("机器人 给我一盒石砖");

        assertTrue(result.successful());
        assertEquals(1728, result.request().amount());
        assertEquals(1, result.request().requestedUnits());
        assertEquals(RequestUnit.BOX, result.request().unit());
    }

    @Test
    void expandsStackUnitButKeepsSingleItemExact() {
        ParseResult stack = parser.parse("机器人 给我1组石砖");
        ParseResult single = parser.parse("机器人 给我单个石砖");

        assertEquals(64, stack.request().amount());
        assertEquals(RequestUnit.STACK, stack.request().unit());
        assertEquals(1, single.request().amount());
        assertEquals(RequestUnit.ITEM, single.request().unit());
    }

    @Test
    void parsesMultipleItemsAsOneOrder() {
        ParseResult result = parser.parse("机器人 给我128个安山岩，128个闪长岩");

        assertTrue(result.successful());
        assertEquals(2, result.requests().size());
        assertEquals(Material.ANDESITE, result.requests().get(0).material());
        assertEquals(128, result.requests().get(0).amount());
        assertEquals(Material.DIORITE, result.requests().get(1).material());
        assertEquals(128, result.requests().get(1).amount());
    }

    @Test
    void supportsNaturalLanguageSeparatorsAndMixedUnits() {
        ParseResult result = parser.parse("机器人 给我一盒石砖还有2组火把和单个钻石");

        assertTrue(result.successful());
        assertEquals(3, result.requests().size());
        assertEquals(RequestUnit.BOX, result.requests().get(0).unit());
        assertEquals(128, result.requests().get(1).amount());
        assertEquals(1, result.requests().get(2).amount());
    }

    @Test
    void parsesFourStacksOfPolishedAndesite() {
        ParseResult result = parser.parse("机器人 给我4组磨制安山岩");

        assertTrue(result.successful());
        assertEquals(Material.POLISHED_ANDESITE, result.request().material());
        assertEquals(256, result.request().amount());
        assertEquals(RequestUnit.STACK, result.request().unit());
    }
}
