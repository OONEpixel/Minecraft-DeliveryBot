package com.haishihua.deliverybot.delivery;

import java.util.List;

public record CargoManifest(List<Integer> deliverySlots, List<Integer> returnSlots) {
    public CargoManifest {
        deliverySlots = List.copyOf(deliverySlots);
        returnSlots = List.copyOf(returnSlots);
    }
}
