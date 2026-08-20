package com.haishihua.deliverybot.navigation;

public enum MovementMode {
    IDLE,
    WALKING,
    FLYING;

    public String displayName() {
        return switch (this) {
            case IDLE -> "待机";
            case WALKING -> "步行";
            case FLYING -> "飞行";
        };
    }
}
