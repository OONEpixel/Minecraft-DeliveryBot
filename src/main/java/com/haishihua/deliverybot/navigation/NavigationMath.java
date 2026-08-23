package com.haishihua.deliverybot.navigation;

import org.bukkit.util.Vector;

public final class NavigationMath {
    private NavigationMath() {
    }

    public static boolean shouldDirectlyDescend(double currentY, double targetY, double arrivalDistance) {
        return targetY + arrivalDistance < currentY;
    }

    public static boolean shouldBoost(
            double distance,
            double horizontalSpeed,
            double targetVerticalDirection,
            int ticksSinceBoost,
            int minimumInterval
    ) {
        if (distance < 12.0 || targetVerticalDirection < -0.15 || ticksSinceBoost < minimumInterval) {
            return false;
        }
        return horizontalSpeed < 0.72 || targetVerticalDirection > 0.12;
    }

    public static boolean shouldUseLandingWater(
            boolean enabled,
            boolean nether,
            double descendedDistance,
            double verticalVelocity,
            double groundDistance,
            double triggerFallDistance
    ) {
        return enabled
                && !nether
                && descendedDistance >= triggerFallDistance
                && verticalVelocity <= -0.42
                && groundDistance > 0.4
                && groundDistance <= 3.25;
    }

    public static Vector precisionVelocity(Vector delta, double horizontalSpeedLimit, double verticalSpeedLimit) {
        Vector horizontal = delta.clone().setY(0);
        double horizontalDistance = horizontal.length();
        Vector velocity = new Vector();
        if (horizontalDistance > 0.001) {
            double horizontalSpeed = Math.min(horizontalSpeedLimit, horizontalDistance * 0.20);
            velocity.add(horizontal.normalize().multiply(horizontalSpeed));
        }
        velocity.setY(clamp(delta.getY() * 0.18, -verticalSpeedLimit, verticalSpeedLimit));
        return velocity;
    }

    public static Vector deliveryOffset(Vector awayFromPlayer, Vector fallback, double distance) {
        Vector direction = awayFromPlayer.clone().setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = fallback.clone().setY(0).multiply(-1);
        }
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(1, 0, 0);
        }
        return direction.normalize().multiply(distance);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
