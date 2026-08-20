package com.haishihua.deliverybot.persistence;

import java.util.UUID;

public record BotRecord(UUID uuid, String name, LocationData home) {
}
