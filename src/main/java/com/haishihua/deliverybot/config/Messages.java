package com.haishihua.deliverybot.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class Messages {
    private static final Component PREFIX = Component.text("[DeliveryBot] ", NamedTextColor.AQUA);

    private Messages() {
    }

    public static void info(CommandSender target, String text) {
        target.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void success(CommandSender target, String text) {
        target.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
    }

    public static void error(CommandSender target, String text) {
        target.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }
}
