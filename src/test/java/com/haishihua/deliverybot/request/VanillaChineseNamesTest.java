package com.haishihua.deliverybot.request;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaChineseNamesTest {
    @Test
    void loadsOfficialNamesForPaper121Items() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("vanilla-zh-cn.yml");
        assertNotNull(stream);

        Map<Material, String> names;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            names = VanillaChineseNames.load(reader);
        }

        assertTrue(names.size() >= 1000, "expected broad Minecraft 1.21 item coverage");
        assertEquals("潜影盒", names.get(Material.SHULKER_BOX));
        assertEquals("白色潜影盒", names.get(Material.WHITE_SHULKER_BOX));
        assertEquals("烟花火箭", names.get(Material.FIREWORK_ROCKET));
        assertEquals("小麦", names.get(Material.WHEAT));
    }
}
