package com.runeborn.claims;

import com.fibermc.essentialcommands.ECPerms;
import com.fibermc.essentialcommands.EssentialCommands;
import com.fibermc.essentialcommands.playerdata.PlayerData;
import com.fibermc.essentialcommands.text.ECText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adds an RTP-only warmup without changing Essential Commands' TPA/home teleport behavior. */
public final class RtpWarmupManager {
    private static final int WARMUP_TICKS = 100; // 5 seconds
    private static final double MOVEMENT_EPSILON = 0.10D;

    private static final Map<UUID, PendingRtp> PENDING = new HashMap<>();
    private static final Set<UUID> BYPASS_ONCE = new HashSet<>();

    private RtpWarmupManager() {}

    /**
     * Called at the very start of Essential Commands' /rtp command.
     * Returns true when the original command should continue immediately.
     */
    public static boolean handleRtpCommand(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();

        // Internal re-run after a successful warmup: let Essential Commands execute normally.
        if (BYPASS_ONCE.remove(uuid)) {
            return true;
        }

        // Essential Commands performs its cooldown check inside RandomTeleportCommand.run().
        // This mixin executes before that method, so we must check the same cooldown first.
        if (EssentialCommands.CONFIG.RTP_COOLDOWN > 0
                && !ECPerms.check(player.createCommandSourceStack(), ECPerms.Registry.bypass_randomteleport_cooldown)) {
            int now = server.getTickCount();
            PlayerData data = PlayerData.access(player);
            int nextAllowedTick = data.getTimeUsedRtp() + EssentialCommands.CONFIG.RTP_COOLDOWN * 20;
            int remainingTicks = nextAllowedTick - now;

            if (remainingTicks > 0) {
                ECText text = ECText.access(player);
                Component seconds = text.accent(String.format("%.1f", remainingTicks / 20.0D));
                data.sendError("cmd.rtp.error.cooldown", seconds);
                return false;
            }
        }

        if (PENDING.containsKey(uuid)) {
            player.sendSystemMessage(Component.literal(
                    "RTP warmup already active. Stay still."
            ));
            return false;
        }

        PENDING.put(uuid, new PendingRtp(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                server.getTickCount()
        ));

        player.sendSystemMessage(Component.literal(
                "RTP warmup started. Stay still for 5 seconds."
        ));
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }

        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, PendingRtp>> iterator = PENDING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRtp> entry = iterator.next();
            UUID uuid = entry.getKey();
            PendingRtp pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            if (player == null) {
                iterator.remove();
                continue;
            }

            double dx = player.getX() - pending.x;
            double dy = player.getY() - pending.y;
            double dz = player.getZ() - pending.z;
            if ((dx * dx + dy * dy + dz * dz) > (MOVEMENT_EPSILON * MOVEMENT_EPSILON)) {
                iterator.remove();
                player.sendSystemMessage(Component.literal(
                        "RTP cancelled because you moved."
                ));
                continue;
            }

            if (now - pending.startTick < WARMUP_TICKS) {
                continue;
            }

            iterator.remove();
            BYPASS_ONCE.add(uuid);
            player.sendSystemMessage(Component.literal("RTP ready."));
            server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(uuid);
                if (current == null) {
                    BYPASS_ONCE.remove(uuid);
                    return;
                }
                server.getCommands().performPrefixedCommand(current.createCommandSourceStack(), "rtp");
            });
        }
    }

    public static void cancel(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PENDING.remove(uuid);
        BYPASS_ONCE.remove(uuid);
    }

    private record PendingRtp(double x, double y, double z, float yaw, float pitch, int startTick) {}
}
