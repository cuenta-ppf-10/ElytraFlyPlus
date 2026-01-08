package kassuk.addon.blackout.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kassuk.addon.blackout.modules.ElytraFlyPlus;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ElytraCommand extends Command {
    
    public ElytraCommand() {
        super("efly", "Control ElytraFly+ pathfinding.", "elytrafly", "ef");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .efly goto <x> <y> <z>
        builder.then(literal("goto")
            .then(argument("x", IntegerArgumentType.integer())
                .then(argument("y", IntegerArgumentType.integer())
                    .then(argument("z", IntegerArgumentType.integer())
                        .executes(context -> {
                            int x = IntegerArgumentType.getInteger(context, "x");
                            int y = IntegerArgumentType.getInteger(context, "y");
                            int z = IntegerArgumentType.getInteger(context, "z");

                            ElytraFlyPlus module = Modules.get().get(ElytraFlyPlus.class);
                            if (module != null) {
                                module.setDestination(x, y, z);
                                
                                // Auto-activate if not active
                                if (!module.isActive()) {
                                    module.toggle();
                                    info("ElytraFly+ activated");
                                }
                            } else {
                                error("ElytraFly+ module not found!");
                            }

                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
        );

        // .efly clear
        builder.then(literal("clear")
            .executes(context -> {
                ElytraFlyPlus module = Modules.get().get(ElytraFlyPlus.class);
                if (module != null) {
                    module.clearDestination();
                } else {
                    error("ElytraFly+ module not found!");
                }

                return SINGLE_SUCCESS;
            })
        );

        // .efly here
        builder.then(literal("here")
            .executes(context -> {
                if (mc.player == null) {
                    error("Player is null!");
                    return SINGLE_SUCCESS;
                }

                int x = (int) mc.player.getX();
                int y = (int) mc.player.getY();
                int z = (int) mc.player.getZ();

                info("Current position: " + x + ", " + y + ", " + z);

                return SINGLE_SUCCESS;
            })
        );

        // .efly status
        builder.then(literal("status")
            .executes(context -> {
                ElytraFlyPlus module = Modules.get().get(ElytraFlyPlus.class);
                if (module != null) {
                    if (module.isActive()) {
                        info("ElytraFly+ is ACTIVE - Mode: " + module.getMode());
                        if (module.hasDestination()) {
                            info("Destination: " + module.getDestinationString());
                        } else {
                            warning("No destination set");
                        }
                    } else {
                        info("ElytraFly+ is INACTIVE");
                    }
                } else {
                    error("ElytraFly+ module not found!");
                }

                return SINGLE_SUCCESS;
            })
        );

        // .efly help
        builder.then(literal("help")
            .executes(context -> {
                info("--- ElytraFly+ Commands ---");
                info("  .efly goto <x> <y> <z> - Set destination and start flying");
                info("  .efly clear - Clear destination");
                info("  .efly here - Show current coordinates");
                info("  .efly status - Show module status");
                info("  .efly help - Show this help");

                return SINGLE_SUCCESS;
            })
        );
    }
}