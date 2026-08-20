package com.haishihua.deliverybot.request;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.Reader;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class VanillaChineseNames {
    private VanillaChineseNames() {
    }

    public static Map<Material, String> load(Reader reader) {
        YamlConfiguration vanilla = YamlConfiguration.loadConfiguration(reader);
        Map<Material, String> result = new EnumMap<>(Material.class);
        for (String entry : vanilla.getStringList("names")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            Material material = Material.matchMaterial(entry.substring(0, separator));
            if (material == null) {
                continue;
            }
            result.putIfAbsent(material, entry.substring(separator + 1));
        }
        return Collections.unmodifiableMap(result);
    }
}
