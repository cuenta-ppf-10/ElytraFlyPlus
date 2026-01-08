package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.concurrent.*;

public class ElytraFlyPlus extends BlackOutModule {
    public ElytraFlyPlus() {
        super(BlackOut.BLACKOUT, "Elytra Fly+", "Better efly with Baritone pathfinding.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgPathfinding = settings.createGroup("Pathfinding");

    //--------------------General--------------------//
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Mode")
        .description("Flight mode.")
        .defaultValue(Mode.Wasp)
        .build()
    );

    private final Setting<Boolean> stopWater = sgGeneral.add(new BoolSetting.Builder()
        .name("Stop Water")
        .description("Doesn't modify movement while in water.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopLava = sgGeneral.add(new BoolSetting.Builder()
        .name("Stop Lava")
        .description("Doesn't modify movement while in lava.")
        .defaultValue(true)
        .build()
    );

    //--------------------Speed (Wasp/Control/Constantiam)--------------------//
    private final Setting<Double> horizontal = sgSpeed.add(new DoubleSetting.Builder()
        .name("Horizontal Speed")
        .description("How many blocks to move each tick horizontally.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> up = sgSpeed.add(new DoubleSetting.Builder()
        .name("Up Speed")
        .description("How many blocks to move up each tick.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> speed = sgSpeed.add(new DoubleSetting.Builder()
        .name("Speed")
        .description("How many blocks to move each tick.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Double> upMultiplier = sgSpeed.add(new DoubleSetting.Builder()
        .name("Up Multiplier")
        .description("How many times faster should we fly up.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Double> down = sgSpeed.add(new DoubleSetting.Builder()
        .name("Down Speed")
        .description("How many blocks to move down each tick.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Control || mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Boolean> smartFall = sgSpeed.add(new BoolSetting.Builder()
        .name("Smart Fall")
        .description("Only falls down when looking down.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> fallSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("Fall Speed")
        .description("How many blocks to fall down each tick.")
        .defaultValue(0.01)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> mode.get() == Mode.Control || mode.get() == Mode.Wasp)
        .build()
    );

    private final Setting<Double> constSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("Const Speed")
        .description("Maximum speed for constantiam mode.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    private final Setting<Double> constAcceleration = sgSpeed.add(new DoubleSetting.Builder()
        .name("Const Acceleration")
        .description("Acceleration for constantiam mode.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    private final Setting<Boolean> constStop = sgSpeed.add(new BoolSetting.Builder()
        .name("Const Stop")
        .description("Stops movement when no input.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Constantiam)
        .build()
    );

    //--------------------Pathfinding--------------------//
    private final Setting<Double> pathSpeed = sgPathfinding.add(new DoubleSetting.Builder()
        .name("Flight Speed")
        .description("Speed when following path.")
        .defaultValue(1.5)
        .min(0)
        .sliderRange(0, 5)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Integer> pathLookahead = sgPathfinding.add(new IntSetting.Builder()
        .name("Lookahead")
        .description("How many blocks ahead to check for obstacles.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 50)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Double> minAvoidance = sgPathfinding.add(new DoubleSetting.Builder()
        .name("Min Avoidance")
        .description("Minimum clearance from obstacles.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 5)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Integer> simulationTicks = sgPathfinding.add(new IntSetting.Builder()
        .name("Simulation Ticks")
        .description("How many ticks to simulate ahead.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 40)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Double> targetHeight = sgPathfinding.add(new DoubleSetting.Builder()
        .name("Target Height")
        .description("Preferred flying height.")
        .defaultValue(120)
        .min(0)
        .sliderRange(0, 256)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Boolean> avoidTerrain = sgPathfinding.add(new BoolSetting.Builder()
        .name("Avoid Terrain")
        .description("Dynamically avoid terrain.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Boolean> recalcPath = sgPathfinding.add(new BoolSetting.Builder()
        .name("Dynamic Recalc")
        .description("Recalculate path when blocked.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Pathfind)
        .build()
    );

    private final Setting<Integer> recalcInterval = sgPathfinding.add(new IntSetting.Builder()
        .name("Recalc Interval")
        .description("Ticks between path recalculations.")
        .defaultValue(40)
        .min(10)
        .sliderRange(10, 100)
        .visible(() -> mode.get() == Mode.Pathfind && recalcPath.get())
        .build()
    );

    // State variables
    private boolean moving;
    private float yaw;
    private float pitch;
    private float p;
    private double velocity;
    private int activeFor;

    // Pathfinding state
    private BlockPos destination = null;
    private List<Vec3d> currentPath = new ArrayList<>();
    private int currentPathNode = 0;
    private int ticksSinceRecalc = 0;
    private ExecutorService pathfindingExecutor;
    private Future<List<Vec3d>> pathCalculation;
    private Vec3d targetNode = null;

    @Override
    public void onActivate() {
        activeFor = 0;
        pathfindingExecutor = Executors.newSingleThreadExecutor();
        
        if (mode.get() == Mode.Pathfind && destination == null) {
            ChatUtils.error("No destination set! Use .efly goto <x> <y> <z>");
        }
    }

    @Override
    public void onDeactivate() {
        if (pathfindingExecutor != null) {
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }
        currentPath.clear();
        targetNode = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mode.get() != Mode.Pathfind) return;

        ticksSinceRecalc++;

        // Check if path calculation is done
        if (pathCalculation != null && pathCalculation.isDone()) {
            try {
                currentPath = pathCalculation.get();
                currentPathNode = 0;
                ticksSinceRecalc = 0;
                if (!currentPath.isEmpty()) {
                    ChatUtils.info("Path calculated: " + currentPath.size() + " nodes");
                }
            } catch (Exception e) {
                ChatUtils.error("Path calculation failed: " + e.getMessage());
            }
            pathCalculation = null;
        }

        // Recalculate if needed
        if (recalcPath.get() && ticksSinceRecalc >= recalcInterval.get() && pathCalculation == null) {
            calculatePath();
        }

        // Update current node
        if (!currentPath.isEmpty()) {
            updateCurrentNode();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMove(PlayerMoveEvent event) {
        if (!active()) return;

        activeFor++;
        if (activeFor < 5) return;

        switch (mode.get()) {
            case Wasp -> waspTick(event);
            case Control -> controlTick(event);
            case Constantiam -> constantiamTick(event);
            case Pathfind -> pathfindTick(event);
        }
    }

    // PATHFINDING MODE - Similar to Baritone
    private void pathfindTick(PlayerMoveEvent event) {
        if (!mc.player.isFallFlying() || destination == null) return;

        Vec3d playerPos = mc.player.getPos();

        // If no path, calculate one
        if (currentPath.isEmpty() && pathCalculation == null) {
            calculatePath();
            return;
        }

        // If still calculating, fly towards destination
        if (pathCalculation != null || targetNode == null) {
            flyTowards(event, Vec3d.ofCenter(destination));
            return;
        }

        // Check if reached destination
        if (playerPos.distanceTo(Vec3d.ofCenter(destination)) < 5) {
            ChatUtils.info("Reached destination!");
            ((IVec3d) event.movement).set(0, -0.1, 0);
            return;
        }

        // Follow the path
        Vec3d targetPos = targetNode;
        
        // Check if path ahead is clear
        if (avoidTerrain.get() && !isPathClear(playerPos, targetPos)) {
            // Obstacle detected, try to go around
            targetPos = findAvoidancePoint(playerPos, targetPos);
            
            if (recalcPath.get() && ticksSinceRecalc > 10) {
                calculatePath(); // Recalc immediately if blocked
            }
        }

        flyTowards(event, targetPos);
    }

    private void flyTowards(PlayerMoveEvent event, Vec3d target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d direction = target.subtract(playerPos).normalize();

        // Calculate yaw and pitch
        double targetYaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        
        // Height management
        double heightDiff = target.y - playerPos.y;
        float targetPitch = (float) MathHelper.clamp(heightDiff / 10.0, -45, 45);

        // Smooth interpolation
        this.yaw = (float) lerpAngle(this.yaw, targetYaw, 0.2);
        this.pitch = (float) MathHelper.lerp(0.2, this.pitch, targetPitch);

        // Calculate movement
        double cos = Math.cos(Math.toRadians(this.yaw + 90));
        double sin = Math.sin(Math.toRadians(this.yaw + 90));

        double speedMult = pathSpeed.get();
        double distToTarget = playerPos.distanceTo(target);
        
        // Slow down near waypoints
        if (distToTarget < 10) {
            speedMult *= Math.max(0.3, distToTarget / 10.0);
        }

        double x = cos * speedMult;
        double z = sin * speedMult;
        double y;

        if (Math.abs(heightDiff) > 3) {
            y = MathHelper.clamp(heightDiff / 8.0, -speedMult * 0.5, speedMult * 0.5);
        } else {
            y = -0.02; // Slight descent
        }

        ((IVec3d) event.movement).set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void calculatePath() {
        if (destination == null || pathfindingExecutor == null) return;

        Vec3d start = mc.player.getPos();
        BlockPos startPos = new BlockPos((int)start.x, (int)start.y, (int)start.z);

        pathCalculation = pathfindingExecutor.submit(() -> 
            computePath(startPos, destination)
        );
    }

    private List<Vec3d> computePath(BlockPos start, BlockPos end) {
        List<Vec3d> path = new ArrayList<>();
        
        Vec3d current = Vec3d.ofCenter(start);
        Vec3d target = Vec3d.ofCenter(end);
        
        // Simple waypoint generation with obstacle avoidance
        double totalDist = current.distanceTo(target);
        int segments = (int) Math.ceil(totalDist / pathLookahead.get());
        
        for (int i = 1; i <= segments; i++) {
            double progress = (double) i / segments;
            Vec3d waypoint = current.lerp(target, progress);
            
            // Adjust height based on terrain
            if (avoidTerrain.get()) {
                waypoint = adjustForTerrain(waypoint);
            } else {
                waypoint = new Vec3d(waypoint.x, targetHeight.get(), waypoint.z);
            }
            
            path.add(waypoint);
        }
        
        // Always end at exact destination
        path.add(target);
        
        return path;
    }

    private Vec3d adjustForTerrain(Vec3d point) {
        BlockPos checkPos = new BlockPos((int)point.x, (int)point.y, (int)point.z);
        
        // Scan downwards to find ground
        int groundLevel = (int)point.y;
        for (int y = (int)point.y; y > Math.max(point.y - 50, mc.world.getBottomY()); y--) {
            BlockPos pos = new BlockPos((int)point.x, y, (int)point.z);
            if (!isPassable(pos)) {
                groundLevel = y;
                break;
            }
        }
        
        // Fly at target height above ground
        double desiredHeight = Math.max(groundLevel + targetHeight.get(), targetHeight.get());
        
        return new Vec3d(point.x, desiredHeight, point.z);
    }

    private void updateCurrentNode() {
        Vec3d playerPos = mc.player.getPos();
        
        // Find closest node ahead of us
        double closestDist = Double.MAX_VALUE;
        int closestNode = currentPathNode;
        
        for (int i = currentPathNode; i < Math.min(currentPathNode + 10, currentPath.size()); i++) {
            double dist = playerPos.distanceTo(currentPath.get(i));
            if (dist < closestDist) {
                closestDist = dist;
                closestNode = i;
            }
        }
        
        currentPathNode = closestNode;
        
        // Move to next node if close enough
        if (closestDist < 5 && currentPathNode < currentPath.size() - 1) {
            currentPathNode++;
        }
        
        // Set target node (look ahead)
        int lookAheadNode = Math.min(currentPathNode + 3, currentPath.size() - 1);
        targetNode = currentPath.get(lookAheadNode);
    }

    private boolean isPathClear(Vec3d from, Vec3d to) {
        // Raytrace to check for obstacles
        RaycastContext context = new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        );
        
        BlockHitResult result = mc.world.raycast(context);
        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3d findAvoidancePoint(Vec3d from, Vec3d to) {
        // Try to find a clear path by going higher
        Vec3d elevated = new Vec3d(to.x, to.y + 10, to.z);
        
        if (isPathClear(from, elevated)) {
            return elevated;
        }
        
        // Try lateral avoidance
        Vec3d direction = to.subtract(from).normalize();
        Vec3d perpendicular = new Vec3d(-direction.z, 0, direction.x).multiply(5);
        
        Vec3d left = to.add(perpendicular);
        if (isPathClear(from, left)) {
            return left;
        }
        
        Vec3d right = to.subtract(perpendicular);
        if (isPathClear(from, right)) {
            return right;
        }
        
        // Default to going up
        return elevated;
    }

    private boolean isPassable(BlockPos pos) {
        if (!mc.world.isInBuildLimit(pos)) return true;
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || !state.isSolidBlock(mc.world, pos);
    }

    private double lerpAngle(double from, double to, double delta) {
        double diff = ((to - from) % 360 + 540) % 360 - 180;
        return from + diff * delta;
    }

    // Command methods
    public void setDestination(int x, int y, int z) {
        destination = new BlockPos(x, y, z);
        currentPath.clear();
        currentPathNode = 0;
        ticksSinceRecalc = 0;
        ChatUtils.info("Destination set to: " + x + ", " + y + ", " + z);
        
        if (mode.get() == Mode.Pathfind) {
            calculatePath();
        }
    }

    public void clearDestination() {
        destination = null;
        currentPath.clear();
        targetNode = null;
        ChatUtils.info("Destination cleared");
    }

    public boolean hasDestination() {
        return destination != null;
    }

    public String getDestinationString() {
        if (destination == null) return "None";
        return destination.getX() + ", " + destination.getY() + ", " + destination.getZ();
    }

    public String getMode() {
        return mode.get().toString();
    }

    // ORIGINAL MODES (Wasp, Control, Constantiam) - Unchanged
    private void constantiamTick(PlayerMoveEvent event) {
        Vec3d motion = getMotion(mc.player.getVelocity());
        if (motion != null) {
            ((IVec3d) event.movement).set(motion.getX(), motion.getY(), motion.getZ());
        }
    }

    private Vec3d getMotion(Vec3d velocity) {
        if (mc.player.input.movementForward == 0) {
            if (constStop.get()) return new Vec3d(0, 0, 0);
            return null;
        }

        boolean forward = mc.player.input.movementForward > 0;
        double yaw = Math.toRadians(mc.player.getYaw() + (forward ? 90 : -90));

        double x = Math.cos(yaw);
        double z = Math.sin(yaw);
        double maxAcc = calcAcceleration(velocity.x, velocity.z, x, z);
        double delta = MathHelper.clamp(MathHelper.getLerpProgress(velocity.horizontalLength(), 0, 0.5), 0, 1);

        double acc = Math.min(maxAcc, constAcceleration.get() / 20 * (0.1 + delta * 0.9));
        return new Vec3d(velocity.getX() + x * acc, velocity.getY(), velocity.getZ() + z * acc);
    }

    private double calcAcceleration(double vx, double vz, double x, double z) {
        double xz = x * x + z * z;
        return (Math.sqrt(xz * constSpeed.get() * constSpeed.get() - x * x * vz * vz - z * z * vx * vx + 2 * x * z * vx * vz) - x * vx - z * vz) / xz;
    }

    private void waspTick(PlayerMoveEvent event) {
        if (!mc.player.isFallFlying()) return;

        updateWaspMovement();
        pitch = mc.player.getPitch();

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double x = moving ? cos * horizontal.get() : 0;
        double y = -fallSpeed.get();
        double z = moving ? sin * horizontal.get() : 0;

        if (smartFall.get()) {
            y *= Math.abs(Math.sin(Math.toRadians(pitch)));
        }

        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            y = -down.get();
        }
        if (!mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed()) {
            y = up.get();
        }

        ((IVec3d) event.movement).set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void updateWaspMovement() {
        float yaw = mc.player.getYaw();

        float f = mc.player.input.movementForward;
        float s = mc.player.input.movementSideways;

        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        this.yaw = yaw;
    }

    private void controlTick(PlayerMoveEvent event) {
        if (!mc.player.isFallFlying()) return;

        updateControlMovement();
        pitch = 0;

        boolean movingUp = false;

        if (!mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed() && velocity > speed.get() * 0.4) {
            p = (float) Math.min(p + 0.1 * (1 - p) * (1 - p) * (1 - p), 1f);
            pitch = Math.max(Math.max(p, 0) * -90, -90);
            movingUp = true;
            moving = false;
        } else {
            velocity = speed.get();
            p = -0.2f;
        }

        velocity = moving ? speed.get() : Math.min(velocity + Math.sin(Math.toRadians(pitch)) * 0.08, speed.get());

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double x = moving && !movingUp ? cos * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * cos : 0;
        double y = pitch < 0 ? velocity * upMultiplier.get() * -Math.sin(Math.toRadians(pitch)) * velocity : -fallSpeed.get();
        double z = moving && !movingUp ? sin * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * sin : 0;

        y *= Math.abs(Math.sin(Math.toRadians(movingUp ? pitch : mc.player.getPitch())));

        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            y = -down.get();
        }

        ((IVec3d) event.movement).set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void updateControlMovement() {
        float yaw = mc.player.getYaw();

        float f = mc.player.input.movementForward;
        float s = mc.player.input.movementSideways;

        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        this.yaw = yaw;
    }

    public boolean active() {
        if (stopWater.get() && mc.player.isTouchingWater()) {
            activeFor = 0;
            return false;
        }
        if (stopLava.get() && mc.player.isInLava()) {
            activeFor = 0;
            return false;
        }
        return mc.player.isFallFlying();
    }

    public enum Mode {
        Wasp,
        Control,
        Constantiam,
        Pathfind
    }
}