package com.haishihua.deliverybot.navigation;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationMathTest {
    @Test
    void capsLargeDownwardApproachWithoutAddingHorizontalDrift() {
        Vector velocity = NavigationMath.precisionVelocity(new Vector(0, -124, 0), 0.23, 0.55);

        assertEquals(0.0, velocity.getX(), 0.0001);
        assertEquals(-0.55, velocity.getY(), 0.0001);
        assertEquals(0.0, velocity.getZ(), 0.0001);
    }

    @Test
    void capsLargeUpwardApproach() {
        Vector velocity = NavigationMath.precisionVelocity(new Vector(0, 124, 0), 0.23, 0.55);

        assertEquals(0.55, velocity.getY(), 0.0001);
    }

    @Test
    void createsExactDeliveryStandoffDistance() {
        Vector offset = NavigationMath.deliveryOffset(new Vector(4, 20, 0), new Vector(0, 0, 1), 3.0);

        assertEquals(3.0, offset.length(), 0.0001);
        assertEquals(0.0, offset.getY(), 0.0001);
    }

    @Test
    void highToLowRouteUsesDirectDiagonalDescent() {
        assertTrue(NavigationMath.shouldDirectlyDescend(158, 34, 1.8));
        assertFalse(NavigationMath.shouldDirectlyDescend(34, 158, 1.8));
    }
}
