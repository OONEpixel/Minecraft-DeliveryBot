package com.haishihua.deliverybot.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryRequest(
        UUID requester,
        List<RequestSpec> items,
        Instant createdAt
) {
    public DeliveryRequest {
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Delivery request must contain at least one item");
        }
    }

    public DeliveryRequest(UUID requester, RequestSpec spec) {
        this(requester, List.of(spec), Instant.now());
    }

    public DeliveryRequest(UUID requester, org.bukkit.Material material, int amount) {
        this(requester, new RequestSpec(material, amount, amount, RequestUnit.ITEM));
    }
}
