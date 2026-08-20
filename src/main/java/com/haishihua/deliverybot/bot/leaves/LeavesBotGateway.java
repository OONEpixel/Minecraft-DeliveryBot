package com.haishihua.deliverybot.bot.leaves;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.regex.Pattern;

public final class LeavesBotGateway {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public boolean createAtSender(CommandSender sender, String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            return false;
        }
        return Bukkit.dispatchCommand(sender, "bot create " + name);
    }

    public boolean validName(String name) {
        return VALID_NAME.matcher(name).matches();
    }
}
