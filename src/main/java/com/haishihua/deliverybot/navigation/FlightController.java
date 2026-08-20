package com.haishihua.deliverybot.navigation;

import com.haishihua.deliverybot.bot.BotEquipmentService;
import com.haishihua.deliverybot.config.PluginSettings;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Chooses ground movement for short, safe routes and elytra flight for long routes. Walking
 * automatically promotes to flight when the target moves away, the ground becomes unsafe, or
 * progress stalls.
 */
public final class FlightController {
    private static final int WALK_STUCK_TICKS = 60;
    private static final int WALK_BLOCKED_TICKS = 12;
    private static final double WALK_PROGRESS_EPSILON = 0.08;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final BotEquipmentService equipment;
    private BukkitTask activeTask;
    private Consumer<FlightResult> activeCallback;
    private Player activeBot;
    private MovementMode mode = MovementMode.IDLE;

    public FlightController(JavaPlugin plugin, PluginSettings settings, BotEquipmentService equipment) {
        this.plugin = plugin;
        this.settings = settings;
        this.equipment = equipment;
    }

    public void travel(Player bot, Location target, Consumer<FlightResult> callback) {
        travel(bot, () -> target.clone(), settings.arrivalDistance(), callback);
    }

    public void travel(Player bot, Supplier<Location> targetSupplier, Consumer<FlightResult> callback) {
        travel(bot, targetSupplier, settings.arrivalDistance(), callback);
    }

    public void travel(
            Player bot,
            Supplier<Location> targetSupplier,
            double arrivalDistance,
            Consumer<FlightResult> callback
    ) {
        cancel(false);
        double effectiveArrivalDistance = Math.max(0.25, arrivalDistance);
        Location target = targetSupplier.get();
        if (target != null && canStartWalking(bot.getLocation(), target)) {
            startWalking(bot, targetSupplier, effectiveArrivalDistance, callback);
        } else {
            startFlying(bot, targetSupplier, effectiveArrivalDistance, callback);
        }
    }

    public void fly(Player bot, Location target, Consumer<FlightResult> callback) {
        fly(bot, () -> target.clone(), callback);
    }

    public void fly(Player bot, Supplier<Location> targetSupplier, Consumer<FlightResult> callback) {
        cancel(false);
        startFlying(bot, targetSupplier, settings.arrivalDistance(), callback);
    }

    public MovementMode mode() {
        return mode;
    }

    public boolean isMoving() {
        return activeTask != null;
    }

    private void startWalking(
            Player bot,
            Supplier<Location> targetSupplier,
            double arrivalDistance,
            Consumer<FlightResult> callback
    ) {
        activeCallback = callback;
        activeBot = bot;
        mode = MovementMode.WALKING;
        bot.setGliding(false);
        bot.setSprinting(true);

        activeTask = new BukkitRunnable() {
            private int ticks;
            private int stuckTicks;
            private int blockedTicks;
            private double bestDistance = Double.MAX_VALUE;

            @Override
            public void run() {
                if (!bot.isOnline() || !bot.isValid()) {
                    finish(FlightResult.FAILED);
                    return;
                }

                Location target = targetSupplier.get();
                Location current = bot.getLocation();
                if (!sameWorld(current, target)) {
                    finish(FlightResult.FAILED);
                    return;
                }

                double distance = current.distance(target);
                if (distance <= arrivalDistance) {
                    stopGroundMovement(bot);
                    finish(FlightResult.ARRIVED);
                    return;
                }

                if (++ticks > settings.walkTimeoutTicks()
                        || distance > settings.walkMaxDistance() * 1.25
                        || Math.abs(target.getY() - current.getY()) > settings.walkMaxHeightDifference()
                        || current.getBlock().isLiquid()) {
                    promoteToFlight();
                    return;
                }

                if (distance + WALK_PROGRESS_EPSILON < bestDistance) {
                    bestDistance = distance;
                    stuckTicks = 0;
                } else if (++stuckTicks > WALK_STUCK_TICKS) {
                    promoteToFlight();
                    return;
                }

                Vector horizontal = target.toVector().subtract(current.toVector()).setY(0);
                if (horizontal.lengthSquared() < 0.0001) {
                    promoteToFlight();
                    return;
                }
                horizontal.normalize();

                if (!hasSafeSupportAhead(current, horizontal)) {
                    promoteToFlight();
                    return;
                }

                face(bot, current, horizontal);
                RayTraceResult obstacle = current.getWorld().rayTraceBlocks(
                        current.clone().add(0, 0.25, 0),
                        horizontal,
                        0.85,
                        FluidCollisionMode.NEVER,
                        true
                );

                double verticalVelocity = bot.getVelocity().getY();
                if (obstacle != null) {
                    if (hasSupport(current) && hasJumpHeadroom(current)) {
                        verticalVelocity = Math.max(verticalVelocity, 0.42);
                    } else if (++blockedTicks > WALK_BLOCKED_TICKS) {
                        promoteToFlight();
                        return;
                    }
                } else {
                    blockedTicks = 0;
                }

                Vector velocity = horizontal.multiply(settings.walkSpeed());
                velocity.setY(verticalVelocity);
                bot.setVelocity(velocity);
            }

            private void promoteToFlight() {
                cancel();
                activeTask = null;
                activeCallback = null;
                activeBot = null;
                stopGroundMovement(bot);
                startFlying(bot, targetSupplier, arrivalDistance, callback);
            }

            private void finish(FlightResult result) {
                cancel();
                activeTask = null;
                activeCallback = null;
                activeBot = null;
                mode = MovementMode.IDLE;
                callback.accept(result);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startFlying(
            Player bot,
            Supplier<Location> targetSupplier,
            double arrivalDistance,
            Consumer<FlightResult> callback
    ) {
        activeCallback = callback;
        activeBot = bot;
        mode = MovementMode.FLYING;
        bot.setSprinting(false);

        activeTask = new BukkitRunnable() {
            private int ticks;
            private int stuckTicks;
            private double bestDistance = Double.MAX_VALUE;
            private double cruiseY = Double.NaN;
            private Boolean directDescent;

            @Override
            public void run() {
                if (!bot.isOnline() || !bot.isValid()) {
                    finish(FlightResult.FAILED);
                    return;
                }
                Location target = targetSupplier.get();
                Location current = bot.getLocation();
                if (!sameWorld(current, target)) {
                    finish(FlightResult.FAILED);
                    return;
                }

                double distance = current.distance(target);
                if (distance <= arrivalDistance) {
                    bot.setVelocity(new Vector());
                    bot.setGliding(false);
                    finish(FlightResult.ARRIVED);
                    return;
                }
                if (++ticks > settings.flightTimeoutTicks()) {
                    finish(FlightResult.FAILED);
                    return;
                }

                if (distance + 0.25 < bestDistance) {
                    bestDistance = distance;
                    stuckTicks = 0;
                } else {
                    stuckTicks++;
                }
                if (stuckTicks > 240) {
                    finish(FlightResult.FAILED);
                    return;
                }

                double horizontalDistance = Math.hypot(
                        target.getX() - current.getX(),
                        target.getZ() - current.getZ()
                );
                if (horizontalDistance <= settings.precisionApproachHorizontalDistance()) {
                    precisionApproach(bot, current, target);
                    return;
                }

                if (ticks <= 5 && !bot.isGliding()) {
                    bot.setVelocity(new Vector(0, 0.75, 0));
                    if (ticks > 1) {
                        bot.setGliding(true);
                    }
                    return;
                }

                bot.setGliding(true);
                Location waypoint = waypoint(current, target);
                Vector direction = waypoint.toVector().subtract(current.toVector());
                if (direction.lengthSquared() < 0.0001) {
                    direction = target.toVector().subtract(current.toVector());
                }
                direction.normalize();

                World world = current.getWorld();
                RayTraceResult collision = world.rayTraceBlocks(
                        bot.getEyeLocation(), direction, 3.0, FluidCollisionMode.NEVER, true
                );
                if (collision != null || stuckTicks > 80) {
                    direction.setY(Math.max(0.75, direction.getY()));
                    direction.normalize();
                }
                faceFlight(bot, current, direction);
                boolean descendingFast = direction.getY() <= -0.15;

                Vector desired = direction.multiply(settings.flightSpeed());
                Vector velocity = bot.getVelocity().multiply(0.20).add(desired.multiply(0.80));
                bot.setVelocity(velocity);

                if (!descendingFast && ticks % settings.boostIntervalTicks() == 0) {
                    try {
                        bot.fireworkBoost(equipment.rocket());
                    } catch (IllegalArgumentException ignored) {
                        // Leaves may briefly reject a boost during takeoff; steering continues via velocity.
                    }
                    world.spawnParticle(Particle.FIREWORK, bot.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                }
            }

            private Location waypoint(Location current, Location target) {
                if (directDescent == null) {
                    directDescent = NavigationMath.shouldDirectlyDescend(
                            current.getY(),
                            target.getY(),
                            settings.arrivalDistance()
                    );
                }
                if (directDescent) {
                    return target;
                }
                double horizontalSquared = square(target.getX() - current.getX())
                        + square(target.getZ() - current.getZ());
                if (horizontalSquared <= 36.0) {
                    return target;
                }
                if (Double.isNaN(cruiseY)) {
                    cruiseY = Math.max(target.getY(), current.getY()) + settings.cruiseHeight();
                } else {
                    cruiseY = Math.max(cruiseY, target.getY() + settings.cruiseHeight());
                }
                return new Location(target.getWorld(), target.getX(), cruiseY, target.getZ());
            }

            private double square(double value) {
                return value * value;
            }

            private void finish(FlightResult result) {
                cancel();
                activeTask = null;
                activeCallback = null;
                activeBot = null;
                mode = MovementMode.IDLE;
                callback.accept(result);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean canStartWalking(Location current, Location target) {
        return sameWorld(current, target)
                && current.distance(target) <= settings.walkMaxDistance()
                && Math.abs(target.getY() - current.getY()) <= settings.walkMaxHeightDifference()
                && !current.getBlock().isLiquid()
                && hasSupport(current);
    }

    private void precisionApproach(Player bot, Location current, Location target) {
        bot.setGliding(false);
        Vector delta = target.toVector().subtract(current.toVector());
        Vector horizontal = delta.clone().setY(0);
        if (horizontal.lengthSquared() > 0.0001) {
            face(bot, current, horizontal);
        }
        bot.setVelocity(NavigationMath.precisionVelocity(
                delta,
                settings.walkSpeed(),
                settings.precisionApproachSpeed()
        ));
    }

    private boolean sameWorld(Location current, Location target) {
        return target != null
                && current.getWorld() != null
                && target.getWorld() != null
                && current.getWorld() == target.getWorld();
    }

    private boolean hasSupport(Location location) {
        Block below = location.clone().subtract(0, 0.2, 0).getBlock();
        return !below.isPassable() && !below.isLiquid();
    }

    private boolean hasSafeSupportAhead(Location current, Vector direction) {
        Location probe = current.clone().add(direction.clone().multiply(0.75));
        for (int depth = 0; depth <= 2; depth++) {
            Block below = probe.clone().subtract(0, 0.2 + depth, 0).getBlock();
            if (below.isLiquid()) {
                return false;
            }
            if (!below.isPassable()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJumpHeadroom(Location current) {
        return current.clone().add(0, 1.0, 0).getBlock().isPassable()
                && current.clone().add(0, 2.0, 0).getBlock().isPassable();
    }

    private void face(Player bot, Location current, Vector direction) {
        Location facing = current.clone();
        facing.setDirection(direction);
        bot.setRotation(facing.getYaw(), 0.0f);
    }

    private void faceFlight(Player bot, Location current, Vector direction) {
        Location facing = current.clone();
        facing.setDirection(direction);
        bot.setRotation(facing.getYaw(), facing.getPitch());
    }

    private void stopGroundMovement(Player bot) {
        bot.setSprinting(false);
        Vector current = bot.getVelocity();
        bot.setVelocity(new Vector(0, current.getY(), 0));
    }

    public void cancel(boolean notify) {
        if (activeTask == null) {
            mode = MovementMode.IDLE;
            return;
        }
        activeTask.cancel();
        activeTask = null;
        Player bot = activeBot;
        activeBot = null;
        if (bot != null && bot.isOnline()) {
            bot.setSprinting(false);
            bot.setGliding(false);
        }
        mode = MovementMode.IDLE;
        Consumer<FlightResult> callback = activeCallback;
        activeCallback = null;
        if (notify && callback != null) {
            callback.accept(FlightResult.CANCELLED);
        }
    }
}
