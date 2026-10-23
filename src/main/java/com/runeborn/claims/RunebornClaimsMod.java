package com.runeborn.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.event.api.OPACServerAddonRegisterEventContext;
import xaero.pac.common.event.api.v3.OPACServerAddonRegister;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Set;
import java.util.Comparator;
import java.time.Instant;

public final class RunebornClaimsMod implements ModInitializer {
    private static RunebornClaimsMod INSTANCE;
    public static final String MOD_ID = "runeborn_claims";
    private static final String META_KEY = "xaero.pac_max_claims";
    private static final String FORCELOAD_META_KEY = "xaero.pac_max_forceloads";
    private static final String HOME_PERMISSION_PREFIX = "essentialcommands.home.limit.";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_MARKET_LISTING_PRICE = 1_000_000_000;
    private static final long PARKOUR_COMPLETION_REWARD = 100L;
    private static final long PARKOUR_FIRST_PLACE_BONUS = 250L;
    private static final long PARKOUR_SECOND_PLACE_BONUS = 150L;
    private static final long PARKOUR_THIRD_PLACE_BONUS = 75L;
    private static final int SYSTEM_MARKET_PAGE_SIZE = 36;
    private static final int SYSTEM_MARKET_MAX_PRICE = 1_000_000_000;

    private final Map<UUID, Long> playSeconds = new HashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<Integer, MarketListing> marketListings = new HashMap<>();
    private final Map<String, SystemMarketItem> systemMarketItems = new LinkedHashMap<>();
    private final Map<UUID, SystemMarketMenuSession> systemMarketMenuSessions = new HashMap<>();
    private final Map<UUID, AbstractContainerMenu> runeForgeMenuSessions = new HashMap<>();
    private final Map<UUID, AbstractContainerMenu> runeSocketMenuSessions = new HashMap<>();
    private int nextMarketId = 1;
    private final Map<UUID, Long> lastActivityTick = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> lastAppliedClaims = new HashMap<>();
    private final Map<UUID, String> lastAppliedTiers = new HashMap<>();
    private final Map<UUID, ServerBossEvent> jobBossBars = new HashMap<>();
    // Persistent progress for every job a player has used.
    private final Map<UUID, Map<String, PlayerJob>> playerJobProfiles = new HashMap<>();

    private Config config;
    private long tickCounter;
    private MinecraftServer server;
    private Path dataFile;
    private Path configFile;
    private Path jobsDataFile;
    private Path economyDataFile;
    private Path marketDataFile;
    private Path transactionJournalFile;
    private Path systemMarketFile;
    private Path eventConfigFile;
    private Path eventInventoriesFile;
    private Path leaderboardFile;
    private LeaderboardData leaderboardData = new LeaderboardData();
    // Original inventories are persisted separately so an unexpected shutdown cannot permanently erase player items.
    private final Map<UUID, List<JsonElement>> eventInventoryBackups = new HashMap<>();
    private EventConfig eventConfig = new EventConfig();
    private final Map<UUID, EventParticipant> eventParticipants = new HashMap<>();
    private int eventCountdownTicks;
    private boolean eventRunning;
    private boolean towerRaceActive;
    private long eventStartTick;
    private boolean placementRewardsDistributed;
    private final Map<UUID, PlayerJob> playerJobs = new HashMap<>();

    @Override
    public void onInitialize() {
        INSTANCE = this;
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            // Preserve the vanilla Smithing Table: only sneak-right-click opens the Rune Forge.
            // A normal right-click is allowed to continue to vanilla interaction handling.
            if (level.getBlockState(hit.getBlockPos()).is(Blocks.SMITHING_TABLE) && serverPlayer.isShiftKeyDown()) {
                openRuneForge(serverPlayer);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // Runeborn controls who may create claims. OPAC remains responsible for
        // the actual claim storage and all other claim actions.
        OPACServerAddonRegister.EVENT.register(this::registerOpacAddons);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> {
            ServerPlayer player = handler.player;
            lastActivityTick.put(player.getUUID(), tickCounter);
            snapshots.put(player.getUUID(), PlayerSnapshot.from(player));
            if (playerJobs.containsKey(player.getUUID())) {
                showJobBossBar(player);
            }
            applyPlayerClaimLimit(minecraftServer, player, true);
            restoreEventInventoryIfSafe(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            EventParticipant participant = eventParticipants.get(newPlayer.getUUID());
            if (eventRunning && participant != null && participant.lastCheckpointLocation != null) {
                if (teleportToEventLocation(newPlayer, participant.lastCheckpointLocation)) {
                    newPlayer.sendSystemMessage(Component.literal("[Runeborn Events] Respawned at your last checkpoint."));
                } else {
                    LOGGER.warn("Could not return event participant {} to their last checkpoint after respawn.", newPlayer.getGameProfile().name());
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
            UUID uuid = handler.player.getUUID();
            RtpWarmupManager.cancel(handler.player);
            snapshots.remove(uuid);
            systemMarketMenuSessions.remove(uuid);
            runeForgeMenuSessions.remove(uuid);
            lastActivityTick.remove(uuid);
            lastAppliedClaims.remove(uuid);
            lastAppliedTiers.remove(uuid);
            removeJobBossBar(handler.player);
            saveData();
            saveEconomy();
            saveMarket();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        // Job activity hooks. These are server-side and do not require client mods.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                handleBlockBreakJobRewards(serverPlayer, state);
            }
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killedEntity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                handleHunterRewards(player, killedEntity);
                applyVerdantOnKill(player);
            }
        });

        // Rune effects are evaluated on the server. Socketed Rune metadata, not item names,
        // determines whether an effect is active.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(source.getEntity() instanceof ServerPlayer attacker) || target.level().isClientSide()) return;
            applyWeaponRuneHitEffects(attacker, target);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) return true;
            if (!"fall".equals(source.getMsgId())) return true;
            double chance = getBestVoidArmorChance(player);
            return chance <= 0 || Math.random() >= chance;
        });
    }

    private void registerOpacAddons(OPACServerAddonRegisterEventContext context) {
        context.getClaimActionListenerManagerAPI().register(new IClaimActionListenerAPI() {
            @Override
            public String getName() {
                return MOD_ID + "_admin_claims_only";
            }

            @Override
            public ClaimActionPermissionOverride overrideClaimingActionPermission(
                    UUID playerId,
                    Identifier dimension,
                    int chunkX,
                    int chunkZ,
                    ClaimingAction action,
                    IServerClaimsManagerAPI claimsManagerAPI,
                    ClaimActionPermissionOverride currentOverride,
                    MinecraftServer minecraftServer
            ) {
                if (action != ClaimingAction.CLAIM) {
                    return currentOverride;
                }

                ServerPlayer player = minecraftServer.getPlayerList().getPlayer(playerId);
                if (player == null || !minecraftServer.getPlayerList().isOp(player.nameAndId())) {
                    return new ClaimActionPermissionOverride(
                            ClaimActionPermissionOverrideType.FORBID,
                            Component.literal("Runeborn: Only admins can claim chunks.")
                    );
                }

                return currentOverride;
            }

            @Override
            public void handleSuccessfulClaimingAction(
                    UUID playerId,
                    Identifier dimension,
                    int chunkX,
                    int chunkZ,
                    ClaimingAction action,
                    IServerClaimsManagerAPI claimsManagerAPI,
                    MinecraftServer minecraftServer
            ) {
                // No post-action behavior required.
            }
        });
    }

    private void onServerTick(MinecraftServer minecraftServer) {
        if (server == null) {
            server = minecraftServer;
            Path world = minecraftServer.getWorldPath(LevelResource.ROOT);
            dataFile = world.resolve("data").resolve("runeborn-claim-progression.json");
            configFile = world.resolve("config").resolve("runeborn-claims").resolve("config.json");
            jobsDataFile = world.resolve("data").resolve("runeborn-jobs.json");
            economyDataFile = world.resolve("data").resolve("runeborn-economy.json");
            marketDataFile = world.resolve("data").resolve("runeborn-market.json");
            transactionJournalFile = world.resolve("data").resolve("runeborn-market-transaction.json");
            systemMarketFile = world.resolve("config").resolve("runeborn-claims").resolve("system-market.json");
            eventConfigFile = world.resolve("data").resolve("runeborn-events.json");
            eventInventoriesFile = world.resolve("data").resolve("runeborn-event-inventory-backups.json");
            leaderboardFile = world.resolve("data").resolve("runeborn-parkour-leaderboards.json");
            recoverMarketplaceTransaction();
            config = Config.loadOrCreate(configFile);
            loadData();
            loadJobs();
            loadEconomy();
            loadMarket();
            loadSystemMarket();
            loadEventConfig();
            loadEventInventoryBackups();
            loadLeaderboards();
            // A server restart clears in-memory event state. Restore any saved original inventory
            // to players who are already online before normal play continues.
            for (ServerPlayer onlinePlayer : minecraftServer.getPlayerList().getPlayers()) {
                restoreEventInventoryIfSafe(onlinePlayer);
            }
        }

        tickCounter++;
        RtpWarmupManager.tick(minecraftServer);
        tickEventCountdown(minecraftServer);
        tickParkourProgress(minecraftServer);

        if (config == null || !config.enabled || tickCounter % 20L != 0L) {
            return;
        }

        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            updateActivity(player);
            updateExplorerProgress(player);

            UUID uuid = player.getUUID();

            // If staff changes the player's LuckPerms rank, immediately synchronize OPAC limits.
            String currentTier = resolveTierForPlayer(player);
            if (!currentTier.equals(lastAppliedTiers.get(uuid))) {
                applyPlayerClaimLimit(minecraftServer, player, false);
            }

            long lastActive = lastActivityTick.getOrDefault(uuid, tickCounter);
            long afkTicks = tickCounter - lastActive;

            // We only award playtime while the player has been active recently.
            if (afkTicks <= config.afkTimeoutSeconds * 20L) {
                long next = playSeconds.getOrDefault(uuid, 0L) + 1L;
                playSeconds.put(uuid, next);

                // Claims can only change after a completed hour.
                if (next % 3600L == 0L) {
                    applyPlayerClaimLimit(minecraftServer, player, false);
                }
            }
        }

        if (tickCounter % (Math.max(1L, config.saveIntervalSeconds) * 20L) == 0L) {
            saveData();
            saveJobs();
            saveEconomy();
        }
    }

    private void updateActivity(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerSnapshot previous = snapshots.get(uuid);
        PlayerSnapshot current = PlayerSnapshot.from(player);

        if (previous == null || current.isMeaningfullyDifferent(previous, config)) {
            lastActivityTick.put(uuid, tickCounter);
        }

        snapshots.put(uuid, current);
    }

    private void applyPlayerClaimLimit(MinecraftServer minecraftServer, ServerPlayer player, boolean forceMessage) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getPlayerAdapter(ServerPlayer.class).getUser(player);
            String tier = resolveTier(user);
            long seconds = playSeconds.getOrDefault(player.getUUID(), 0L);
            int claims = config.calculateClaims(tier, seconds);
            int maximum = config.getMaxClaims(tier);

            user.transientData().clear(NodeMatcher.key(META_KEY));
            user.transientData().add(MetaNode.builder(META_KEY, Integer.toString(claims)).build());

            int forceloads = config.getMaxForceloads(tier);
            user.transientData().clear(NodeMatcher.key(FORCELOAD_META_KEY));
            user.transientData().add(MetaNode.builder(FORCELOAD_META_KEY, Integer.toString(forceloads)).build());

            int homes = config.getMaxHomes(tier);
            syncHomePermission(user, homes);

            lastAppliedClaims.put(player.getUUID(), claims);
            lastAppliedTiers.put(player.getUUID(), tier);

            if (forceMessage) {
                sendStatus(player, tier, seconds, claims, maximum);
            }
        } catch (IllegalStateException ex) {
            // LuckPerms may not be ready during very early startup.
        } catch (Exception ex) {
            LOGGER.warn("Runeborn Claims could not update {}: {}", player.getGameProfile().name(), ex.getMessage());
        }
    }

    private void syncHomePermission(User user, int homes) {
        for (Integer limit : config.homeLimits.values()) {
            user.transientData().clear(NodeMatcher.key(HOME_PERMISSION_PREFIX + limit));
        }
        user.transientData().add(PermissionNode.builder(HOME_PERMISSION_PREFIX + homes).build());
    }

    private String resolveTier(User user) {
        String group = user.getPrimaryGroup().toLowerCase();
        if (group.equals("admin")) return "admin";
        if (group.equals("ascendant")) return "ascendant";
        if (group.equals("runekeeper")) return "runekeeper";
        if (group.equals("wayfarer")) return "wayfarer";
        return "runeborn";
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rbforge")
                .executes(context -> openRuneForge(context.getSource())));
        dispatcher.register(Commands.literal("rbsocket")
                .executes(context -> openRuneSocket(context.getSource())));
        dispatcher.register(
                Commands.literal("rbrune")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> giveRuneMaterial(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                IntegerArgumentType.getInteger(context, "amount")
                                                        ))
                                                )
                                        )
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("rbclaims")
                        .executes(context -> showOwn(context.getSource()))
                        .then(Commands.literal("reload")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> reloadConfig(context.getSource())))
                        .then(Commands.literal("set")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("time", StringArgumentType.word())
                                                .executes(context -> setTime(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "time")
                                                )))))
        );

        dispatcher.register(
                Commands.literal("sethome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> forwardHomeCommand(context.getSource(), "set", StringArgumentType.getString(context, "name"))))
        );

        dispatcher.register(
                Commands.literal("delhome")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> forwardHomeCommand(context.getSource(), "delete", StringArgumentType.getString(context, "name"))))
        );

        dispatcher.register(
                Commands.literal("homes")
                        .executes(context -> forwardHomeList(context.getSource()))
        );

        dispatcher.register(
                Commands.literal("rbrank")
                        .executes(context -> showOwnRank(context.getSource()))
                        .then(Commands.literal("info")
                                .executes(context -> showOwnRank(context.getSource())))
                        .then(Commands.literal("player")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> showPlayerRank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player")
                                        ))))
        );

        dispatcher.register(
                Commands.literal("rbjobs")
                        .executes(context -> showJobs(context.getSource()))
                        .then(Commands.literal("list")
                                .executes(context -> showJobs(context.getSource())))
                        .then(Commands.literal("info")
                                .executes(context -> showActiveJobInfo(context.getSource()))
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .executes(context -> showJobInfo(context.getSource(), StringArgumentType.getString(context, "job")))))
                        .then(Commands.literal("join")
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .executes(context -> joinJob(context.getSource(), StringArgumentType.getString(context, "job")))))
                        .then(Commands.literal("leave")
                                .executes(context -> leaveJob(context.getSource())))
        );

        dispatcher.register(
                Commands.literal("rbbalance")
                        .executes(context -> showOwnBalance(context.getSource()))
                        .then(Commands.literal("player")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> showPlayerBalance(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player")
                                        ))))
        );

        dispatcher.register(
                Commands.literal("rbpay")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> payPlayer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        ))))
        );

        dispatcher.register(
                Commands.literal("rbmarket")
                        .executes(context -> listMarket(context.getSource()))
                        .then(Commands.literal("list")
                                .executes(context -> listMarket(context.getSource())))
                        .then(Commands.literal("sell")
                                .then(Commands.argument("price", IntegerArgumentType.integer(1, MAX_MARKET_LISTING_PRICE))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> createListing(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "price"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("buy")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> buyListing(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id")))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> cancelListing(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id")))))
        );

        dispatcher.register(
                Commands.literal("rbshop")
                        .executes(context -> openSystemMarket(context.getSource()))
                        .then(Commands.literal("reload")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> reloadSystemMarket(context.getSource())))
                        .then(Commands.literal("prices")
                                .executes(context -> showSystemMarketInfo(context.getSource())))
        );

        dispatcher.register(
                Commands.literal("rbtop")
                        .executes(context -> showLeaderboard(context.getSource(), false))
                        .then(Commands.literal("me")
                                .executes(context -> showLeaderboard(context.getSource(), true)))
                        .then(Commands.literal("season")
                                .executes(context -> showActiveLeaderboardSeason(context.getSource()))
                                .then(Commands.literal("set")
                                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> setLeaderboardSeason(context.getSource(), StringArgumentType.getString(context, "name"))))))
        );

        dispatcher.register(
                Commands.literal("rbevent")
                        .executes(context -> showEventStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> showEventStatus(context.getSource())))
                        .then(Commands.literal("create")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> createEvent(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("setlobby")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> setEventLocation(context.getSource(), true)))
                        .then(Commands.literal("setarena")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> setEventLocation(context.getSource(), false)))
                        .then(Commands.literal("checkpoint")
                                .then(Commands.literal("add")
                                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                        .executes(context -> addEventCheckpoint(context.getSource())))
                                .then(Commands.literal("clear")
                                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                        .executes(context -> clearEventCheckpoints(context.getSource())))
                                .then(Commands.literal("list")
                                        .executes(context -> listEventCheckpoints(context.getSource())))
                        )
                        .then(Commands.literal("finish")
                                .then(Commands.literal("set")
                                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                        .executes(context -> setEventFinish(context.getSource())))
                                .then(Commands.literal("clear")
                                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                        .executes(context -> clearEventFinish(context.getSource())))
                        )
                        .then(Commands.literal("join")
                                .executes(context -> joinEvent(context.getSource())))
                        .then(Commands.literal("leave")
                                .executes(context -> leaveEvent(context.getSource())))
                        .then(Commands.literal("start")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> startEvent(context.getSource())))
                        .then(Commands.literal("end")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .executes(context -> endEvent(context.getSource())))
        );

        dispatcher.register(
                Commands.literal("rbeconomy")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> modifyBalance(
                                                        context.getSource(), "set",
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "amount")
                                                )))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> modifyBalance(
                                                        context.getSource(), "add",
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "amount")
                                                )))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> modifyBalance(
                                                        context.getSource(), "take",
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "amount")
                                                )))))
        );
    }

    private int showEventStatus(CommandSourceStack source) {
        if (eventConfig.name == null || eventConfig.name.isBlank()) {
            source.sendSuccess(() -> Component.literal("No Runeborn event is configured. An admin can use /rbevent create <name>."), false);
            return 1;
        }
        String state = eventRunning ? "RUNNING" : eventCountdownTicks > 0 ? "COUNTDOWN" : "READY";
        int participants = eventParticipants.size();
        source.sendSuccess(() -> Component.literal("Runeborn event: " + eventConfig.name + " | Status: " + state
                + " | Participants: " + participants
                + " | Lobby: " + (eventConfig.lobby == null ? "not set" : "set")
                + " | Arena: " + (eventConfig.arena == null ? "not set" : "set")
                + " | Checkpoints: " + safeCheckpoints().size()
                + " | Finish: " + (eventConfig.finish == null ? "not set" : "set")), false);
        return 1;
    }

    private int createEvent(CommandSourceStack source, String name) {
        if (!eventParticipants.isEmpty() || eventRunning || eventCountdownTicks > 0) {
            source.sendFailure(Component.literal("End the current event and clear all participants before creating another one."));
            return 0;
        }
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 48) {
            source.sendFailure(Component.literal("Event name must be between 1 and 48 characters."));
            return 0;
        }
        eventConfig = new EventConfig();
        eventConfig.name = cleanName;
        saveEventConfig();
        source.sendSuccess(() -> Component.literal("Created Runeborn event '" + cleanName + "'. Set locations with /rbevent setlobby and /rbevent setarena."), true);
        return 1;
    }

    private int setEventLocation(CommandSourceStack source, boolean lobby) throws CommandSyntaxException {
        if (eventConfig.name == null || eventConfig.name.isBlank()) {
            source.sendFailure(Component.literal("Create an event first with /rbevent create <name>."));
            return 0;
        }
        if (!eventParticipants.isEmpty() || eventRunning || eventCountdownTicks > 0) {
            source.sendFailure(Component.literal("You cannot change event locations while an event has participants."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        EventLocation location = EventLocation.from(player);
        if (lobby) eventConfig.lobby = location; else eventConfig.arena = location;
        saveEventConfig();
        source.sendSuccess(() -> Component.literal((lobby ? "Event lobby" : "Event arena") + " saved at your current location and dimension."), true);
        return 1;
    }

    private int joinEvent(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (eventConfig.name == null || eventConfig.name.isBlank() || eventConfig.lobby == null || eventConfig.arena == null) {
            source.sendFailure(Component.literal("The event is not ready yet. An admin must create it and set both lobby and arena."));
            return 0;
        }
        if (eventRunning || eventCountdownTicks > 0) {
            source.sendFailure(Component.literal("This event has already started. You cannot join now."));
            return 0;
        }
        if (eventParticipants.containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("You are already registered for this event."));
            return 0;
        }
        EventParticipant participant = new EventParticipant(EventLocation.from(player));
        if (!teleportToEventLocation(player, eventConfig.lobby)) {
            source.sendFailure(Component.literal("Could not teleport you to the event lobby. Check that its dimension is available."));
            return 0;
        }
        eventParticipants.put(player.getUUID(), participant);
        broadcastEventMessage(source.getServer(), player.getGameProfile().name() + " joined " + eventConfig.name + " (" + eventParticipants.size() + " participants)." );
        source.sendSuccess(() -> Component.literal("You joined '" + eventConfig.name + "'. Use /rbevent leave to leave before it starts."), false);
        return 1;
    }

    private int leaveEvent(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EventParticipant participant = eventParticipants.get(player.getUUID());
        if (participant == null) {
            source.sendFailure(Component.literal("You are not registered for the current event."));
            return 0;
        }
        if (!teleportToEventLocation(player, participant.returnLocation)) {
            source.sendFailure(Component.literal("Could not return you to your previous location. Your event registration was kept; contact an admin."));
            return 0;
        }
        restoreEventInventory(player);
        eventParticipants.remove(player.getUUID());
        source.sendSuccess(() -> Component.literal("You left the event and returned to your previous location."), false);
        broadcastEventMessage(source.getServer(), player.getGameProfile().name() + " left " + eventConfig.name + ".");
        return 1;
    }

    private int startEvent(CommandSourceStack source) {
        if (eventConfig.name == null || eventConfig.name.isBlank() || eventConfig.lobby == null || eventConfig.arena == null) {
            source.sendFailure(Component.literal("Create an event and set both lobby and arena first."));
            return 0;
        }
        // Tower-race progression is independent of the legacy checkpoint/finish
        // configuration, which may still be present in existing event data.
        if (eventRunning || eventCountdownTicks > 0) {
            source.sendFailure(Component.literal("An event is already running or counting down."));
            return 0;
        }
        if (eventParticipants.isEmpty()) {
            source.sendFailure(Component.literal("No players have joined the event."));
            return 0;
        }
        // The tower race uses the enclosed lobby as its holding room, then gives
        // participants a five-second countdown before sending them to Tower 1.
        towerRaceActive = true;
        placementRewardsDistributed = false;
        eventCountdownTicks = 100;
        announceEvent(source.getServer(), "countdown", eventConfig.name + " starts in 5 seconds! Stay in the starting room. Participants: " + eventParticipants.size() + ".", Map.of("event", eventConfig.name, "seconds", "5", "participants", Integer.toString(eventParticipants.size())));
        return 1;
    }

    private void tickEventCountdown(MinecraftServer minecraftServer) {
        if (eventCountdownTicks <= 0) return;
        eventCountdownTicks--;
        if (eventCountdownTicks > 0 && eventCountdownTicks % 20 == 0) {
            int seconds = eventCountdownTicks / 20;
            int[] configuredCountdown = eventConfig.announcements == null || eventConfig.announcements.countdownSeconds == null
                    ? new int[]{5, 4, 3, 2, 1} : eventConfig.announcements.countdownSeconds;
            for (int configuredSecond : configuredCountdown) {
                if (seconds == configuredSecond) {
                    announceEvent(minecraftServer, "countdown", eventConfig.name + " starts in " + seconds + "...", Map.of("event", eventConfig.name, "seconds", Integer.toString(seconds), "participants", Integer.toString(eventParticipants.size())));
                    break;
                }
            }
        }
        if (eventCountdownTicks == 0) {
            List<UUID> failed = new ArrayList<>();
            for (Map.Entry<UUID, EventParticipant> entry : eventParticipants.entrySet()) {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayer(entry.getKey());
                // Keep everyone in the contained lobby for the entire countdown.
                // Actual teleportation to Tower 1 happens immediately afterward.
                if (player == null) failed.add(entry.getKey());
            }
            for (UUID uuid : failed) {
                eventParticipants.remove(uuid);
                ServerPlayer player = minecraftServer.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Could not teleport you to the event arena; you have been removed from the event."));
                }
            }
            if (eventParticipants.isEmpty()) {
                towerRaceActive = false;
                eventRunning = false;
                eventStartTick = 0L;
                announceEvent(minecraftServer, "cancelled", eventConfig.name + " was cancelled because no participants could be moved to Tower 1.", Map.of("event", eventConfig.name));
                return;
            }
            eventRunning = true;
            eventStartTick = tickCounter;
            // Clear hostile mobs from the course before participants begin.
            clearParkourHostileMobs(minecraftServer);
            for (Map.Entry<UUID, EventParticipant> entry : new ArrayList<>(eventParticipants.entrySet())) {
                EventParticipant participant = entry.getValue();
                participant.nextCheckpointIndex = 0;
                participant.finishedAtTick = -1L;
                participant.waitingForCheckpointExit = false;
                participant.waitingForCourseCheckpointExit = false;
                participant.lastCheckpointLocation = null;
                participant.towerStage = 0;
                ServerPlayer player = minecraftServer.getPlayerList().getPlayer(entry.getKey());
                if (player == null) {
                    eventParticipants.remove(entry.getKey());
                    continue;
                }
                if (!captureAndClearEventInventory(player)) {
                    eventParticipants.remove(entry.getKey());
                    teleportToEventLocation(player, participant.returnLocation);
                    player.sendSystemMessage(Component.literal("Your race inventory could not be safely backed up, so you were removed from the event. Your items were not cleared."));
                    continue;
                }
                if (!teleportToEventLocation(player, getTowerLocation(0))) {
                    restoreEventInventory(player);
                    eventParticipants.remove(entry.getKey());
                    teleportToEventLocation(player, participant.returnLocation);
                    player.sendSystemMessage(Component.literal("Could not teleport you to Tower 1; you have been removed from the event and your inventory was restored."));
                } else {
                    player.sendSystemMessage(Component.literal("The race has begun! Your original inventory is safely stored until the event ends. Item pickups are disabled during the race."));
                }
            }
            if (eventParticipants.isEmpty()) {
                eventRunning = false;
                towerRaceActive = false;
                eventStartTick = 0L;
                announceEvent(minecraftServer, "cancelled", eventConfig.name + " was cancelled because no participants could be moved to Tower 1.", Map.of("event", eventConfig.name));
                return;
            }
            announceEvent(minecraftServer, "started", eventConfig.name + " has started! Complete each tower, reach the central Runestone to teleport to the next tower, and return to the center after Tower 4 to finish. Participants: " + eventParticipants.size() + ".", Map.of("event", eventConfig.name, "participants", Integer.toString(eventParticipants.size())));
        }
    }

    private int endEvent(CommandSourceStack source) {
        if (eventConfig.name == null || eventConfig.name.isBlank()) {
            source.sendFailure(Component.literal("No event is configured."));
            return 0;
        }
        if (eventParticipants.isEmpty() && !eventRunning && eventCountdownTicks == 0) {
            source.sendFailure(Component.literal("There is no active event to end."));
            return 0;
        }
        eventCountdownTicks = 0;
        if (towerRaceActive && !placementRewardsDistributed) {
            distributeParkourPlacementRewards(source.getServer());
        }
        int returned = 0;
        for (Map.Entry<UUID, EventParticipant> entry : new ArrayList<>(eventParticipants.entrySet())) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                // Restore is independent of teleport success: never keep a player's items locked
                // away just because their return location could not be resolved.
                restoreEventInventory(player);
                if (teleportToEventLocation(player, entry.getValue().returnLocation)) returned++;
            }
        }
        eventParticipants.clear();
        eventRunning = false;
        towerRaceActive = false;
        eventStartTick = 0L;
        announceEvent(source.getServer(), "ended", eventConfig.name + " has ended. Returned " + returned + " online participant(s) to their previous locations.", Map.of("event", eventConfig.name, "returned", Integer.toString(returned)));
        return 1;
    }

    private boolean isActiveParkourParticipant(ServerPlayer player) {
        return towerRaceActive && eventRunning && eventParticipants.containsKey(player.getUUID());
    }

    /** Called by the ItemEntity mixin to prevent race participants from picking up dropped items. */
    public static boolean shouldBlockParkourItemPickup(ServerPlayer player) {
        return INSTANCE != null && INSTANCE.isActiveParkourParticipant(player);
    }

    /** Save every inventory slot, including armor and offhand, before clearing anything. */
    private boolean captureAndClearEventInventory(ServerPlayer player) {
        // Close any open container first so an item held on the cursor is returned to a slot
        // before the snapshot is taken.
        player.closeContainer();
        UUID uuid = player.getUUID();
        if (eventInventoryBackups.containsKey(uuid)) {
            // A previous interrupted event left a backup. Restore it first rather than overwrite it
            // with the temporary race inventory and lose the player's real items.
            if (!restoreEventInventory(player)) return false;
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<JsonElement> slots = new ArrayList<>();
        try {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                slots.add(stack.isEmpty() ? JsonNull.INSTANCE : ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow().deepCopy());
            }
        } catch (Exception ex) {
            LOGGER.error("Could not serialize inventory for parkour participant {}. Inventory left untouched.", player.getGameProfile().name(), ex);
            return false;
        }

        eventInventoryBackups.put(uuid, slots);
        if (!saveEventInventoryBackups()) {
            eventInventoryBackups.remove(uuid);
            LOGGER.error("Could not persist inventory backup for {}. Inventory left untouched.", player.getGameProfile().name());
            return false;
        }

        player.getInventory().clearContent();
        player.getInventory().setChanged();
        return true;
    }

    /**
     * Restore the saved slots exactly. The backup is only removed after the restored inventory has
     * been applied; if a crash happens before the backup file is updated, replaying this operation
     * clears and reapplies the same slots instead of duplicating items.
     */
    private boolean restoreEventInventory(ServerPlayer player) {
        UUID uuid = player.getUUID();
        List<JsonElement> slots = eventInventoryBackups.get(uuid);
        if (slots == null) return true;
        if (slots.size() != player.getInventory().getContainerSize()) {
            LOGGER.error("Cannot restore inventory for {}: backup has {} slots but current inventory has {}. Backup retained.",
                    player.getGameProfile().name(), slots.size(), player.getInventory().getContainerSize());
            player.sendSystemMessage(Component.literal("[Runeborn Events] Your inventory backup needs staff attention. Your backup has been retained; please contact an admin."));
            return false;
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<ItemStack> decoded = new ArrayList<>(slots.size());
        try {
            for (JsonElement element : slots) {
                if (element == null || element.isJsonNull()) decoded.add(ItemStack.EMPTY);
                else decoded.add(ItemStack.CODEC.parse(ops, element).getOrThrow());
            }
        } catch (Exception ex) {
            LOGGER.error("Could not decode inventory backup for {}. Backup retained.", player.getGameProfile().name(), ex);
            player.sendSystemMessage(Component.literal("[Runeborn Events] Your inventory could not be restored automatically. Your backup has been retained; please contact an admin."));
            return false;
        }

        player.getInventory().clearContent();
        for (int slot = 0; slot < decoded.size(); slot++) {
            player.getInventory().setItem(slot, decoded.get(slot));
        }
        player.getInventory().setChanged();

        eventInventoryBackups.remove(uuid);
        if (!saveEventInventoryBackups()) {
            // Keep the in-memory backup so a later retry can safely reapply the same exact slots.
            eventInventoryBackups.put(uuid, slots);
            LOGGER.error("Restored inventory for {}, but could not remove its backup file entry. Backup retained for safe retry.", player.getGameProfile().name());
            return false;
        }
        player.sendSystemMessage(Component.literal("[Runeborn Events] Your original inventory has been restored."));
        return true;
    }

    private void restoreEventInventoryIfSafe(ServerPlayer player) {
        if (eventInventoryBackups.containsKey(player.getUUID()) && !isActiveParkourParticipant(player)) {
            restoreEventInventory(player);
        }
    }

    private void loadEventInventoryBackups() {
        if (eventInventoriesFile == null || !Files.exists(eventInventoriesFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(eventInventoriesFile));
            if (!parsed.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    if (!entry.getValue().isJsonArray()) continue;
                    List<JsonElement> slots = new ArrayList<>();
                    for (JsonElement slot : entry.getValue().getAsJsonArray()) slots.add(slot == null ? JsonNull.INSTANCE : slot.deepCopy());
                    eventInventoryBackups.put(uuid, slots);
                } catch (Exception ex) {
                    LOGGER.warn("Skipping invalid parkour inventory backup for {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            LOGGER.info("Loaded {} saved parkour inventory backup(s).", eventInventoryBackups.size());
        } catch (Exception ex) {
            LOGGER.error("Could not load parkour inventory backups. The backup file has been left untouched.", ex);
        }
    }

    private boolean saveEventInventoryBackups() {
        if (eventInventoriesFile == null) return false;
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, List<JsonElement>> entry : eventInventoryBackups.entrySet()) {
            JsonArray slots = new JsonArray();
            for (JsonElement slot : entry.getValue()) slots.add(slot == null ? JsonNull.INSTANCE : slot.deepCopy());
            root.add(entry.getKey().toString(), slots);
        }
        try {
            Files.createDirectories(eventInventoriesFile.getParent());
            return writeStringAtomically(eventInventoriesFile, GSON.toJson(root), "Runeborn Event Inventory Backups");
        } catch (IOException ex) {
            LOGGER.error("Could not save parkour inventory backups.", ex);
            return false;
        }
    }

    private int addEventCheckpoint(CommandSourceStack source) throws CommandSyntaxException {
        if (!eventConfiguredForEditing(source)) return 0;
        ServerPlayer player = source.getPlayerOrException();
        if (eventConfig.checkpoints == null) eventConfig.checkpoints = new ArrayList<>();
        EventPoint point = EventPoint.from(player);
        if (!eventConfig.checkpoints.isEmpty()) {
            EventPoint previous = eventConfig.checkpoints.get(eventConfig.checkpoints.size() - 1);
            if (previous.sameBlock(point)) {
                source.sendFailure(Component.literal("This block is already the previous checkpoint. Move at least one block before adding another."));
                return 0;
            }
        }
        eventConfig.checkpoints.add(point);
        saveEventConfig();
        int number = eventConfig.checkpoints.size();
        source.sendSuccess(() -> Component.literal("Checkpoint #" + number + " saved at " + point.x + ", " + point.y + ", " + point.z + ". Players must pass checkpoints in order."), true);
        return 1;
    }

    private int clearEventCheckpoints(CommandSourceStack source) {
        if (!eventConfiguredForEditing(source)) return 0;
        eventConfig.checkpoints = new ArrayList<>();
        saveEventConfig();
        source.sendSuccess(() -> Component.literal("All parkour checkpoints cleared."), true);
        return 1;
    }

    private int listEventCheckpoints(CommandSourceStack source) {
        List<EventPoint> checkpoints = safeCheckpoints();
        if (checkpoints.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No checkpoints configured. An admin can stand on each checkpoint block and use /rbevent checkpoint add."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Parkour checkpoints: " + checkpoints.size()), false);
        for (int i = 0; i < checkpoints.size(); i++) {
            EventPoint point = checkpoints.get(i);
            source.sendSuccess(() -> Component.literal("#" + (checkpoints.indexOf(point) + 1) + ": " + point.dimension + " " + point.x + ", " + point.y + ", " + point.z), false);
        }
        return 1;
    }

    private int setEventFinish(CommandSourceStack source) throws CommandSyntaxException {
        if (!eventConfiguredForEditing(source)) return 0;
        ServerPlayer player = source.getPlayerOrException();
        eventConfig.finish = EventPoint.from(player);
        saveEventConfig();
        source.sendSuccess(() -> Component.literal("Parkour finish saved at " + eventConfig.finish.x + ", " + eventConfig.finish.y + ", " + eventConfig.finish.z + ". Players must complete every checkpoint first."), true);
        return 1;
    }

    private int clearEventFinish(CommandSourceStack source) {
        if (!eventConfiguredForEditing(source)) return 0;
        eventConfig.finish = null;
        saveEventConfig();
        source.sendSuccess(() -> Component.literal("Parkour finish cleared."), true);
        return 1;
    }

    private boolean eventConfiguredForEditing(CommandSourceStack source) {
        if (eventConfig.name == null || eventConfig.name.isBlank()) {
            source.sendFailure(Component.literal("Create an event first with /rbevent create <name>."));
            return false;
        }
        if (eventRunning || eventCountdownTicks > 0 || !eventParticipants.isEmpty()) {
            source.sendFailure(Component.literal("Parkour checkpoints can only be changed when no event participants are registered."));
            return false;
        }
        return true;
    }

    private List<EventPoint> safeCheckpoints() {
        if (eventConfig.checkpoints == null) eventConfig.checkpoints = new ArrayList<>();
        return eventConfig.checkpoints;
    }

    private void tickParkourProgress(MinecraftServer minecraftServer) {
        if (!eventRunning) return;
        tickTowerRaceProgress(minecraftServer);
        // The tower race owns progress while an event is running. The legacy
        // checkpoint course remains available when tower-race mode is not active.
        if (towerRaceActive || safeCheckpoints().isEmpty() || eventConfig.finish == null) return;
        for (Map.Entry<UUID, EventParticipant> entry : new ArrayList<>(eventParticipants.entrySet())) {
            EventParticipant participant = entry.getValue();
            if (participant.finishedAtTick >= 0L) continue;
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            if (participant.waitingForCheckpointExit) {
                EventPoint previous = safeCheckpoints().get(Math.max(0, participant.nextCheckpointIndex - 1));
                if (isNearPoint(player, previous, 2.25D)) continue;
                participant.waitingForCheckpointExit = false;
            }

            if (participant.nextCheckpointIndex < safeCheckpoints().size()) {
                EventPoint next = safeCheckpoints().get(participant.nextCheckpointIndex);
                if (isNearPoint(player, next, 2.25D)) {
                    participant.nextCheckpointIndex++;
                    participant.waitingForCheckpointExit = true;
                    int reached = participant.nextCheckpointIndex;
                    player.sendSystemMessage(Component.literal("[Runeborn Events] Checkpoint " + reached + "/" + safeCheckpoints().size() + " reached."));
                    broadcastEventMessage(minecraftServer, player.getGameProfile().name() + " reached checkpoint " + reached + "/" + safeCheckpoints().size() + ".");
                    continue;
                }
            }

            if (participant.nextCheckpointIndex >= safeCheckpoints().size() && isNearPoint(player, eventConfig.finish, 2.25D)) {
                participant.finishedAtTick = tickCounter;
                long elapsedTicks = Math.max(0L, participant.finishedAtTick - eventStartTick);
                String elapsed = formatEventTime(elapsedTicks);
                recordParkourFinish(player, elapsedTicks);
                broadcastEventMessage(minecraftServer, player.getGameProfile().name() + " finished " + eventConfig.name + " in " + elapsed + "!");
                boolean allFinished = true;
                for (EventParticipant other : eventParticipants.values()) {
                    if (other.finishedAtTick < 0L) { allFinished = false; break; }
                }
                if (allFinished) broadcastEventMessage(minecraftServer, "All participants have finished. An admin can use /rbevent end to return everyone.");
            } else if (participant.nextCheckpointIndex < safeCheckpoints().size() && isNearPoint(player, eventConfig.finish, 2.25D)) {
                player.sendSystemMessage(Component.literal("[Runeborn Events] You still need checkpoint " + (participant.nextCheckpointIndex + 1) + "/" + safeCheckpoints().size() + " before finishing."));
            }
        }
    }

    private void tickTowerRaceProgress(MinecraftServer minecraftServer) {
        if (!towerRaceActive) return;
        // Check once per second; this also removes hostile mobs that spawn after the race starts.
        if (tickCounter % 20L == 0L) {
            clearParkourHostileMobs(minecraftServer);
        }
        EventPoint center = new EventPoint(eventConfig.lobby.dimension, 896, 142, 365);
        for (Map.Entry<UUID, EventParticipant> entry : new ArrayList<>(eventParticipants.entrySet())) {
            EventParticipant participant = entry.getValue();
            if (participant.finishedAtTick >= 0L) continue;
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            // Recover participants who fall below the parkour's recovery threshold.
            // The course is built high above the ground, so Y=110 is the fall boundary
            // rather than the vanilla void boundary. Players without a checkpoint
            // restart at Tower 1; players with one return to their latest checkpoint.
            if (player.getY() < 110.0D) {
                EventLocation recoveryLocation = participant.lastCheckpointLocation != null
                        ? participant.lastCheckpointLocation
                        : getTowerLocation(0);
                if (teleportToEventLocation(player, recoveryLocation)) {
                    if (participant.lastCheckpointLocation != null) {
                        player.sendSystemMessage(Component.literal("[Runeborn Events] You fell! Returning you to your last checkpoint."));
                        participant.waitingForCourseCheckpointExit = true;
                    } else {
                        player.sendSystemMessage(Component.literal("[Runeborn Events] You fell before reaching a checkpoint. Returning you to the start."));
                        participant.waitingForCourseCheckpointExit = false;
                    }
                    continue;
                }
                LOGGER.warn("Could not recover event participant {} after falling below Y=110.", player.getGameProfile().name());
            }

            // Course checkpoints are independent from the central Runestone used to advance towers.
            List<EventPoint> checkpoints = safeCheckpoints();
            if (participant.waitingForCourseCheckpointExit) {
                if (participant.nextCheckpointIndex > 0
                        && isNearPoint(player, checkpoints.get(participant.nextCheckpointIndex - 1), 2.25D)) {
                    // Do not trigger the same checkpoint repeatedly while the player stands on it.
                } else {
                    participant.waitingForCourseCheckpointExit = false;
                }
            }
            if (!participant.waitingForCourseCheckpointExit && participant.nextCheckpointIndex < checkpoints.size()) {
                EventPoint nextCheckpoint = checkpoints.get(participant.nextCheckpointIndex);
                if (isNearPoint(player, nextCheckpoint, 2.25D)) {
                    participant.nextCheckpointIndex++;
                    participant.lastCheckpointLocation = new EventLocation(
                            nextCheckpoint.dimension, nextCheckpoint.x + 0.5D, nextCheckpoint.y, nextCheckpoint.z + 0.5D,
                            player.getYRot(), player.getXRot());
                    participant.waitingForCourseCheckpointExit = true;
                    int reached = participant.nextCheckpointIndex;
                    player.sendSystemMessage(Component.literal("[Runeborn Events] Checkpoint " + reached + "/" + checkpoints.size() + " reached! Your checkpoint is saved."));
                    broadcastEventMessage(minecraftServer, player.getGameProfile().name() + " reached checkpoint " + reached + "/" + checkpoints.size() + ".");
                }
            }

            if (participant.waitingForCheckpointExit) {
                if (isNearPoint(player, center, 2.25D)) continue;
                participant.waitingForCheckpointExit = false;
            }
            if (!isNearPoint(player, center, 2.25D)) continue;
            if (participant.towerStage >= 3) {
                participant.finishedAtTick = tickCounter;
                long elapsedTicks = Math.max(0L, tickCounter - eventStartTick);
                String elapsed = formatEventTime(elapsedTicks);
                recordParkourFinish(player, elapsedTicks);
                player.sendSystemMessage(Component.literal("[Runeborn Events] You completed all four trials in " + elapsed + "!"));
                awardParkourCompletionReward(player, participant);
                announceEvent(minecraftServer, "finish", player.getGameProfile().name() + " completed " + eventConfig.name + " in " + elapsed + "!", Map.of("event", eventConfig.name, "player", player.getGameProfile().name(), "time", elapsed));
                boolean allFinished = true;
                for (EventParticipant other : eventParticipants.values()) {
                    if (other.finishedAtTick < 0L) { allFinished = false; break; }
                }
                if (allFinished) {
                    distributeParkourPlacementRewards(minecraftServer);
                    broadcastEventMessage(minecraftServer, "All participants have finished. An admin can use /rbevent end to return everyone.");
                }
                continue;
            }
            int nextStage = participant.towerStage + 1;
            if (teleportToEventLocation(player, getTowerLocation(nextStage))) {
                participant.towerStage = nextStage;
                participant.waitingForCheckpointExit = true;
                String[] names = {"Tower 1 — The Initiation", "Tower 2 — The Broken Path", "Tower 3 — The Four Conduits", "Tower 4 — The Final Trial"};
                player.sendSystemMessage(Component.literal("[Runeborn Events] Stage complete! Teleporting you to " + names[nextStage] + "."));
                broadcastEventMessage(minecraftServer, player.getGameProfile().name() + " completed " + names[nextStage - 1] + " and advanced to " + names[nextStage] + ".");
            } else {
                player.sendSystemMessage(Component.literal("[Runeborn Events] Could not teleport you to the next tower. Please contact an admin; your stage has not advanced."));
            }
        }
    }

    private void awardParkourCompletionReward(ServerPlayer player, EventParticipant participant) {
        if (participant.completionRewardPaid) return;
        participant.completionRewardPaid = true;
        long balance = balances.getOrDefault(player.getUUID(), 0L);
        long newBalance = addCurrencySafely(balance, PARKOUR_COMPLETION_REWARD);
        balances.put(player.getUUID(), newBalance);
        saveEconomy();
        player.sendSystemMessage(Component.literal("[Runeborn Events] Completion reward: +" + PARKOUR_COMPLETION_REWARD
                + " Crowns. Placement bonuses are awarded when the race ends. Balance: " + newBalance + " Crowns."));
    }

    private void distributeParkourPlacementRewards(MinecraftServer minecraftServer) {
        if (placementRewardsDistributed) return;
        placementRewardsDistributed = true;
        List<Map.Entry<UUID, EventParticipant>> finishers = new ArrayList<>();
        for (Map.Entry<UUID, EventParticipant> entry : eventParticipants.entrySet()) {
            if (entry.getValue().finishedAtTick >= 0L) finishers.add(entry);
        }
        finishers.sort((a, b) -> Long.compare(a.getValue().finishedAtTick, b.getValue().finishedAtTick));

        int place = 0;
        long previousFinishTick = Long.MIN_VALUE;
        for (int i = 0; i < finishers.size(); i++) {
            Map.Entry<UUID, EventParticipant> entry = finishers.get(i);
            long finishTick = entry.getValue().finishedAtTick;
            if (i == 0 || finishTick != previousFinishTick) place = i + 1;
            previousFinishTick = finishTick;
            long bonus = switch (place) {
                case 1 -> PARKOUR_FIRST_PLACE_BONUS;
                case 2 -> PARKOUR_SECOND_PLACE_BONUS;
                case 3 -> PARKOUR_THIRD_PLACE_BONUS;
                default -> 0L;
            };
            if (bonus <= 0L || entry.getValue().placementBonusPaid) continue;
            entry.getValue().placementBonusPaid = true;
            UUID uuid = entry.getKey();
            long newBalance = addCurrencySafely(balances.getOrDefault(uuid, 0L), bonus);
            balances.put(uuid, newBalance);
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(uuid);
            String playerName = player != null ? player.getGameProfile().name() : uuid.toString();
            if (player != null) {
                player.sendSystemMessage(Component.literal("[Runeborn Events] You placed #" + place + "! Placement bonus: +" + bonus
                        + " Crowns. Balance: " + newBalance + " Crowns."));
            }
            broadcastEventMessage(minecraftServer, playerName + " placed #" + place + " and earned a " + bonus + " Crowns bonus.");
        }
        saveEconomy();
    }

    private long addCurrencySafely(long balance, long amount) {
        if (amount <= 0L) return balance;
        return balance > Long.MAX_VALUE - amount ? Long.MAX_VALUE : balance + amount;
    }

    private EventLocation getTowerLocation(int towerIndex) {
        // Verified safe landing locations supplied by the server owner.
        double[][] coords = {
                {876, 138, 386}, // Tower 1
                {916, 138, 345}, // Tower 2
                {876, 138, 345}, // Tower 3
                {916, 138, 385}  // Tower 4
        };
        int index = Math.max(0, Math.min(3, towerIndex));
        String dimension = eventConfig.lobby != null ? eventConfig.lobby.dimension : "minecraft:overworld";
        return new EventLocation(dimension, coords[index][0] + 0.5D, coords[index][1], coords[index][2] + 0.5D, 0.0F, 0.0F);
    }

    /**
     * Removes hostile mobs from the floating parkour course only. The bounded area covers
     * all four towers and the central Runestone while avoiding unrelated parts of the world.
     * This is called at race start and once per second while the race is active.
     */
    private void clearParkourHostileMobs(MinecraftServer minecraftServer) {
        if (eventConfig.lobby == null || eventConfig.lobby.dimension == null) return;
        try {
            ResourceKey<Level> dimensionKey = ResourceKey.create(
                    Registries.DIMENSION, Identifier.parse(eventConfig.lobby.dimension));
            ServerLevel courseLevel = minecraftServer.getLevel(dimensionKey);
            if (courseLevel == null) return;

            // Tower coordinates are around X=876-916, Z=345-386, Y=138+.
            AABB courseBounds = new AABB(840.0D, 80.0D, 310.0D, 950.0D, 240.0D, 420.0D);
            List<Monster> hostileMobs = courseLevel.getEntitiesOfClass(Monster.class, courseBounds);
            for (Monster mob : hostileMobs) {
                mob.discard();
            }
            if (!hostileMobs.isEmpty()) {
                LOGGER.debug("Removed {} hostile mob(s) from the Runeborn parkour course.", hostileMobs.size());
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not clear hostile mobs from the Runeborn parkour course: {}", ex.getMessage());
        }
    }

    private boolean isNearPoint(ServerPlayer player, EventPoint point, double distanceSquared) {
        if (player == null || point == null || !player.level().dimension().identifier().toString().equals(point.dimension)) return false;
        double cx = point.x + 0.5D, cy = point.y + 0.5D, cz = point.z + 0.5D;
        double dx = player.getX() - cx, dy = player.getY() - cy, dz = player.getZ() - cz;
        return dx * dx + dy * dy + dz * dz <= distanceSquared;
    }

    private String formatEventTime(long ticks) {
        long centiseconds = ticks * 5L;
        long minutes = centiseconds / 6000L;
        long seconds = (centiseconds / 100L) % 60L;
        long hundredths = centiseconds % 100L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    private boolean teleportToEventLocation(ServerPlayer player, EventLocation location) {
        if (player == null || location == null || server == null) return false;
        try {
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(location.dimension));
            ServerLevel targetLevel = server.getLevel(dimensionKey);
            if (targetLevel == null) return false;
            boolean teleported = player.teleportTo(
                    targetLevel, location.x, location.y, location.z, Set.of(), location.yaw, location.pitch, true);
            if (teleported) {
                // Clear residual fall state and momentum from the fall that triggered recovery.
                player.resetFallDistance();
                player.setDeltaMovement(Vec3.ZERO);
            }
            return teleported;
        } catch (Exception ex) {
            LOGGER.warn("Could not teleport event participant {}: {}", player.getGameProfile().name(), ex.getMessage());
            return false;
        }
    }

    private void broadcastEventMessage(MinecraftServer minecraftServer, String message) {
        minecraftServer.getPlayerList().broadcastSystemMessage(Component.literal("[Runeborn Events] " + message), false);
    }

    private void announceEvent(MinecraftServer minecraftServer, String kind, String fallback, Map<String, String> values) {
        AnnouncementSettings settings = eventConfig.announcements == null ? new AnnouncementSettings() : eventConfig.announcements;
        if (!settings.enabled) return;
        boolean enabled = switch (kind) {
            case "countdown" -> settings.countdownEnabled;
            case "started" -> settings.startedEnabled;
            case "finish" -> settings.finishEnabled;
            case "ended" -> settings.endedEnabled;
            case "cancelled" -> settings.cancelledEnabled;
            default -> true;
        };
        if (!enabled) return;
        String template = switch (kind) {
            case "countdown" -> settings.countdownMessage;
            case "started" -> settings.startedMessage;
            case "finish" -> settings.finishMessage;
            case "ended" -> settings.endedMessage;
            case "cancelled" -> settings.cancelledMessage;
            default -> null;
        };
        String message = template == null || template.isBlank() ? fallback : template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            message = message.replace("%" + value.getKey() + "%", value.getValue());
        }
        broadcastEventMessage(minecraftServer, message);
    }

    private void loadEventConfig() {
        if (eventConfigFile == null || !Files.exists(eventConfigFile)) return;
        try {
            EventConfig loaded = GSON.fromJson(Files.readString(eventConfigFile), EventConfig.class);
            if (loaded != null) {
                eventConfig = loaded;
                if (eventConfig.announcements == null) eventConfig.announcements = new AnnouncementSettings();
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not load Runeborn event configuration: {}", ex.getMessage());
        }
    }

    private void saveEventConfig() {
        if (eventConfigFile == null) return;
        try {
            Files.createDirectories(eventConfigFile.getParent());
            if (!writeStringAtomically(eventConfigFile, GSON.toJson(eventConfig), "Runeborn Events")) {
                LOGGER.error("Runeborn event configuration was not committed.");
            }
        } catch (IOException ex) {
            LOGGER.error("Could not save Runeborn event configuration: {}", ex.getMessage());
        }
    }

    private void loadLeaderboards() {
        if (leaderboardFile == null || !Files.exists(leaderboardFile)) return;
        try {
            LeaderboardData loaded = GSON.fromJson(Files.readString(leaderboardFile), LeaderboardData.class);
            if (loaded != null) leaderboardData = loaded;
            if (leaderboardData.activeSeason == null || leaderboardData.activeSeason.isBlank()) leaderboardData.activeSeason = "Season 1";
            if (leaderboardData.seasons == null) leaderboardData.seasons = new LinkedHashMap<>();
        } catch (Exception ex) {
            LOGGER.error("Could not load Runeborn parkour leaderboards; existing file was preserved: {}", ex.getMessage());
        }
    }

    private void saveLeaderboards() {
        if (leaderboardFile == null) return;
        try {
            Files.createDirectories(leaderboardFile.getParent());
            if (!writeStringAtomically(leaderboardFile, GSON.toJson(leaderboardData), "Runeborn parkour leaderboards")) {
                LOGGER.error("Runeborn leaderboard data was not committed.");
            }
        } catch (IOException ex) {
            LOGGER.error("Could not save Runeborn parkour leaderboards: {}", ex.getMessage());
        }
    }

    private Map<String, LeaderboardRecord> activeSeasonRecords() {
        if (leaderboardData.seasons == null) leaderboardData.seasons = new LinkedHashMap<>();
        return leaderboardData.seasons.computeIfAbsent(leaderboardData.activeSeason, ignored -> new LinkedHashMap<>());
    }

    private void recordParkourFinish(ServerPlayer player, long elapsedTicks) {
        String uuid = player.getUUID().toString();
        Map<String, LeaderboardRecord> records = activeSeasonRecords();
        LeaderboardRecord record = records.get(uuid);
        String now = Instant.now().toString();
        if (record == null) {
            record = new LeaderboardRecord();
            record.uuid = uuid;
            record.playerName = player.getGameProfile().name();
            record.bestTicks = elapsedTicks;
            record.bestDate = now;
            record.completions = 1;
            record.lastFinishDate = now;
            record.lastEvent = eventConfig.name;
            records.put(uuid, record);
        } else {
            record.playerName = player.getGameProfile().name();
            record.completions++;
            record.lastFinishDate = now;
            record.lastEvent = eventConfig.name;
            if (elapsedTicks < record.bestTicks) {
                record.bestTicks = elapsedTicks;
                record.bestDate = now;
            }
        }
        saveLeaderboards();
        player.sendSystemMessage(Component.literal("[Runeborn Leaderboard] Personal best: " + formatEventTime(record.bestTicks)
                + " | Season: " + leaderboardData.activeSeason + " | Rank: " + getRankForPlayer(player.getUUID()) + "."));
    }

    private int getRankForPlayer(UUID uuid) {
        List<LeaderboardRecord> sorted = new ArrayList<>(activeSeasonRecords().values());
        sorted.sort(Comparator.comparingLong(record -> record.bestTicks));
        int rank = 0;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardRecord record = sorted.get(i);
            if (i == 0 || record.bestTicks != previous) rank = i + 1;
            previous = record.bestTicks;
            if (uuid.toString().equals(record.uuid)) return rank;
        }
        return 0;
    }

    private int showLeaderboard(CommandSourceStack source, boolean ownOnly) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Map<String, LeaderboardRecord> records = activeSeasonRecords();
        if (ownOnly) {
            LeaderboardRecord record = records.get(player.getUUID().toString());
            if (record == null) {
                player.sendSystemMessage(Component.literal("[Runeborn Leaderboard] You have no completed runs recorded for " + leaderboardData.activeSeason + " yet."));
                return 1;
            }
            player.sendSystemMessage(Component.literal("[Runeborn Leaderboard] " + leaderboardData.activeSeason + " personal best: "
                    + formatEventTime(record.bestTicks) + " | Rank #" + getRankForPlayer(player.getUUID())
                    + " | Completions: " + record.completions + " | Best: " + record.bestDate));
            return 1;
        }
        List<LeaderboardRecord> sorted = new ArrayList<>(records.values());
        sorted.sort(Comparator.comparingLong(record -> record.bestTicks));
        player.sendSystemMessage(Component.literal("===== Runeborn Parkour Top 10 — " + leaderboardData.activeSeason + " ====="));
        if (sorted.isEmpty()) {
            player.sendSystemMessage(Component.literal("No completed runs recorded yet."));
            return 1;
        }
        int shown = Math.min(10, sorted.size());
        for (int i = 0; i < shown; i++) {
            LeaderboardRecord record = sorted.get(i);
            String bestDate = record.bestDate == null ? "date unknown" : record.bestDate.substring(0, Math.min(10, record.bestDate.length()));
            player.sendSystemMessage(Component.literal((i + 1) + ". " + record.playerName + " — " + formatEventTime(record.bestTicks)
                    + " | best: " + bestDate + " | " + record.completions + " completion(s)"));
        }
        return 1;
    }

    private int showActiveLeaderboardSeason(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Active Runeborn leaderboard season: " + leaderboardData.activeSeason
                + ". Use /rbtop to view the top 10 or /rbtop me for your personal best."), false);
        return 1;
    }

    private int setLeaderboardSeason(CommandSourceStack source, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank() || clean.length() > 48) {
            source.sendFailure(Component.literal("Season name must be between 1 and 48 characters."));
            return 0;
        }
        leaderboardData.activeSeason = clean;
        activeSeasonRecords();
        saveLeaderboards();
        source.sendSuccess(() -> Component.literal("Active leaderboard season set to '" + clean + "'. Existing season records were preserved."), true);
        return 1;
    }

    private static final class LeaderboardData {
        String activeSeason = "Season 1";
        Map<String, Map<String, LeaderboardRecord>> seasons = new LinkedHashMap<>();
    }

    private static final class LeaderboardRecord {
        String uuid;
        String playerName;
        long bestTicks;
        String bestDate;
        String lastFinishDate;
        String lastEvent;
        int completions;
    }

    private static final class EventConfig {
        String name;
        AnnouncementSettings announcements = new AnnouncementSettings();
        EventLocation lobby;
        EventLocation arena;
        List<EventPoint> checkpoints = new ArrayList<>();
        EventPoint finish;
    }

    private static final class AnnouncementSettings {
        boolean enabled = true;
        boolean countdownEnabled = true;
        boolean startedEnabled = true;
        boolean finishEnabled = true;
        boolean endedEnabled = true;
        boolean cancelledEnabled = true;
        int[] countdownSeconds = new int[]{5, 4, 3, 2, 1};
        String countdownMessage = "%event% starts in %seconds% seconds! Participants: %participants%.";
        String startedMessage = "%event% has started! Complete each tower and return to the central Runestone after Tower 4. Participants: %participants%.";
        String finishMessage = "%player% completed %event% in %time%!";
        String endedMessage = "%event% has ended. Returned %returned% online participant(s) to their previous locations.";
        String cancelledMessage = "%event% was cancelled.";
    }

    private static final class EventParticipant {
        final EventLocation returnLocation;
        int nextCheckpointIndex;
        int towerStage;
        long finishedAtTick = -1L;
        boolean completionRewardPaid;
        boolean placementBonusPaid;
        boolean waitingForCheckpointExit;
        boolean waitingForCourseCheckpointExit;
        EventLocation lastCheckpointLocation;
        EventParticipant(EventLocation returnLocation) { this.returnLocation = returnLocation; }
    }

    private static final class EventPoint {
        String dimension;
        int x;
        int y;
        int z;
        EventPoint() { }
        EventPoint(String dimension, int x, int y, int z) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; }
        static EventPoint from(ServerPlayer player) {
            BlockPos pos = player.blockPosition();
            return new EventPoint(player.level().dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        boolean sameBlock(EventPoint other) {
            return other != null && x == other.x && y == other.y && z == other.z && java.util.Objects.equals(dimension, other.dimension);
        }
    }

    private static final class EventLocation {
        String dimension;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        EventLocation() { }
        EventLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
        static EventLocation from(ServerPlayer player) {
            return new EventLocation(player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    private int forwardHomeCommand(CommandSourceStack source, String action, String homeName) {
        String command = "home " + action + " " + homeName;
        source.getServer().getCommands().performPrefixedCommand(source, command);
        return 1;
    }

    private int forwardHomeList(CommandSourceStack source) {
        source.getServer().getCommands().performPrefixedCommand(source, "home list");
        return 1;
    }

    private int showOwn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        long seconds = playSeconds.getOrDefault(uuid, 0L);
        String tier = resolveTierForPlayer(player);
        applyPlayerClaimLimit(server, player, false);
        int claims = config.calculateClaims(tier, seconds);
        int maximum = config.getMaxClaims(tier);

        sendStatus(source, player, tier, seconds, claims, maximum);
        return 1;
    }

    private int showOwnRank(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String tier = resolveTierForPlayer(player);
        sendRankStatus(source, player.getGameProfile().name(), tier);
        return 1;
    }

    private int showPlayerRank(CommandSourceStack source, String playerName) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player must be online for this command."));
            return 0;
        }

        String tier = resolveTierForPlayer(target);
        sendRankStatus(source, target.getGameProfile().name(), tier);
        return 1;
    }

    private void sendRankStatus(CommandSourceStack source, String playerName, String tier) {
        RankInfo rank = RankInfo.fromKey(tier);
        int baseClaims = config.baseClaims.getOrDefault(tier, config.baseClaims.get("runeborn"));
        int maxClaims = config.getMaxClaims(tier);
        int maxForceloads = config.getMaxForceloads(tier);
        int maxHomes = config.getMaxHomes(tier);
        int currentHomes = countPlayerHomes(server.getPlayerList().getPlayerByName(playerName));
        String message = "Runeborn Rank\n"
                + "Player: " + playerName + "\n"
                + "Rank: " + rank.displayName + "\n"
                + "Claims: " + baseClaims + " → " + maxClaims + "\n"
                + "Forceloads: " + maxForceloads + "\n"
                + "Homes: " + currentHomes + "\n"
                + "Max Homes: " + maxHomes;
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private int countPlayerHomes(ServerPlayer player) {
        if (player == null) return 0;

        // Essential Commands is the source of truth for homes.
        // We use its public PlayerData API through reflection so Runeborn Claims
        // does not need to compile against a specific Essential Commands JAR.
        try {
            Class<?> playerDataClass = Class.forName(
                    "com.fibermc.essentialcommands.playerdata.PlayerData"
            );
            java.lang.reflect.Method accessMethod = playerDataClass.getMethod(
                    "access", ServerPlayer.class
            );
            Object playerData = accessMethod.invoke(null, player);

            java.lang.reflect.Method getHomeNamesMethod = playerDataClass.getMethod(
                    "getHomeNames"
            );
            Object homeNames = getHomeNamesMethod.invoke(playerData);

            if (homeNames instanceof java.util.Set<?> names) {
                return names.size();
            }

            return 0;
        } catch (ClassNotFoundException ex) {
            LOGGER.debug("Essential Commands PlayerData API is unavailable; home count is 0.");
            return 0;
        } catch (Exception ex) {
            LOGGER.debug("Could not determine home count for {}: {}",
                    player.getGameProfile().name(), ex.getMessage());
            return 0;
        }
    }

    private int showJobs(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerJob current = playerJobs.get(player.getUUID());
        StringBuilder message = new StringBuilder("Runeborn Jobs\n");
        for (JobDefinition job : JobDefinition.ALL) {
            message.append("- ").append(job.id).append(" — ").append(job.displayName);
            if (current != null && current.jobId.equals(job.id)) message.append(" [ACTIVE]");
            message.append("\n");
        }
        message.append("Use /rbjobs info <job> for details.\n");
        message.append("Use /rbjobs join <job> to choose a job.");
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private int showActiveJobInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerJob current = playerJobs.get(player.getUUID());
        if (current == null) {
            source.sendFailure(Component.literal("You do not currently have a job. Use /rbjobs list."));
            return 0;
        }
        return showJobInfo(source, current.jobId);
    }

    private int showJobInfo(CommandSourceStack source, String id) {
        JobDefinition job = JobDefinition.find(id);
        if (job == null) {
            source.sendFailure(Component.literal("Unknown job. Use /rbjobs list."));
            return 0;
        }
        PlayerJob current = null;
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            Map<String, PlayerJob> profiles = playerJobProfiles.get(player.getUUID());
            if (profiles != null) current = profiles.get(job.id);
        }
        int level = current != null ? current.level : 1;
        long xp = current != null ? current.xp : 0;
        long next = xpRequiredForLevel(level);
        long percent = next <= 0 ? 0 : (xp * 100L) / next;
        String message = "Job: " + job.displayName + "\n"
                + "Description: " + job.description + "\n"
                + "Level: " + level + "\n"
                + "Progress: " + xp + " / " + next + " XP (" + percent + "%)\n"
                + progressBar(xp, next);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private String progressBar(long current, long maximum) {
        int width = 20;
        double ratio = maximum <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) current / (double) maximum));
        int filled = (int) Math.round(ratio * width);
        return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]";
    }

    private int joinJob(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JobDefinition job = JobDefinition.find(id);
        if (job == null) {
            source.sendFailure(Component.literal("Unknown job. Use /rbjobs list."));
            return 0;
        }
        PlayerJob current = playerJobs.get(player.getUUID());
        if (current != null && current.jobId.equals(job.id)) {
            source.sendFailure(Component.literal("You are already a " + job.displayName + "."));
            return 0;
        }
        Map<String, PlayerJob> profiles = playerJobProfiles.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        PlayerJob profile = profiles.get(job.id);
        if (profile == null) {
            profile = new PlayerJob(job.id, 1, 0);
            profiles.put(job.id, profile);
        }
        playerJobs.put(player.getUUID(), profile);
        showJobBossBar(player);
        saveJobs();
            saveEconomy();
        source.sendSuccess(() -> Component.literal("You joined the " + job.displayName + " job. Your previous progress for this job has been restored."), false);
        return 1;
    }

    private int leaveJob(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerJob current = playerJobs.remove(player.getUUID());
        if (current == null) {
            source.sendFailure(Component.literal("You do not currently have a job."));
            return 0;
        }
        removeJobBossBar(player);
        saveJobs();
            saveEconomy();
        source.sendSuccess(() -> Component.literal("You left the " + current.jobId + " job."), false);
        return 1;
    }

    private void handleBlockBreakJobRewards(ServerPlayer player, BlockState state) {
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null || player.isCreative()) return;

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        int xp = 0;
        long crowns = 0;

        if (job.jobId.equals("lumberjack") && (path.endsWith("_log") || path.endsWith("_wood")
                || path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_wood")))) {
            xp = config.getJobXp("lumberjack");
            crowns = config.getJobCrowns("lumberjack");
        } else if (job.jobId.equals("farmer") && isFarmHarvestBlock(path, state)) {
            xp = config.getJobXp("farmer");
            crowns = config.getJobCrowns("farmer");
        } else if (job.jobId.equals("miner") && isMiningBlock(path)) {
            xp = config.getJobXp("miner");
            crowns = getMinerCrowns(path);
        }

        if (xp > 0 && crowns > 0) awardJobActivity(player, xp, crowns);
    }

    private long getMinerCrowns(String path) {
        return switch (path) {
            case "coal_ore", "copper_ore", "nether_quartz_ore" -> 2L;
            case "iron_ore", "gold_ore", "lapis_ore" -> 4L;
            case "redstone_ore", "deepslate_redstone_ore", "diamond_ore", "emerald_ore" -> 8L;
            case "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore" -> 6L;
            case "ancient_debris" -> 25L;
            default -> 1L;
        };
    }

    private boolean isFarmHarvestBlock(String path, BlockState state) {
        return path.equals("wheat") || path.equals("carrots") || path.equals("potatoes")
                || path.equals("beetroots") || path.equals("nether_wart") || path.equals("cocoa")
                || path.equals("sweet_berry_bush") || path.equals("cave_vines")
                || path.equals("cave_vines_plant");
    }

    private boolean isMiningBlock(String path) {
        if (path.endsWith("_ore") || path.equals("ancient_debris")) return true;
        return path.equals("stone") || path.equals("cobblestone") || path.equals("deepslate")
                || path.equals("cobbled_deepslate") || path.equals("tuff") || path.equals("granite")
                || path.equals("diorite") || path.equals("andesite") || path.equals("netherrack")
                || path.equals("blackstone") || path.equals("basalt") || path.equals("smooth_basalt");
    }

    private void applyWeaponRuneHitEffects(ServerPlayer attacker, LivingEntity target) {
        ItemStack weapon = attacker.getMainHandItem();
        double emberPower = getBestSocketedRuneMultiplier(weapon, "Ember");
        if (emberPower > 0 && target.isAlive()) {
            // DamageSource.magic() has no player attacker, so this bonus cannot recursively
            // trigger the weapon-rune callback.
            float bonusDamage = (float) Math.min(2.0, 0.5 * emberPower);
            target.hurtServer((ServerLevel) target.level(), ((ServerLevel) target.level()).damageSources().magic(), bonusDamage);
        }

        double frostPower = getBestSocketedRuneMultiplier(weapon, "Frost");
        if (frostPower > 0 && target.isAlive()) {
            double chance = Math.min(0.35, 0.10 * frostPower);
            if (Math.random() < chance) {
                int duration = (int) Math.round(30 * frostPower);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0), attacker);
            }
        }
    }

    private double getBestVoidArmorChance(ServerPlayer player) {
        double bestMultiplier = 0;
        for (ItemStack armor : java.util.List.of(player.getItemBySlot(EquipmentSlot.FEET), player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.CHEST), player.getItemBySlot(EquipmentSlot.HEAD))) {
            bestMultiplier = Math.max(bestMultiplier, getBestSocketedRuneMultiplier(armor, "Void"));
        }
        if (bestMultiplier <= 0) return 0;
        return Math.min(0.30, 0.10 * bestMultiplier);
    }

    private double getBestSocketedRuneMultiplier(ItemStack stack, String family) {
        if (stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = getCustomTag(stack);
        int count = Math.max(0, Math.min(tag.getIntOr("runeborn_socket_count", 0), 2));
        double best = 0;
        for (int i = 0; i < count; i++) {
            String socketFamily = tag.getStringOr("runeborn_socket_" + i + "_family", "");
            String rarity = tag.getStringOr("runeborn_socket_" + i + "_rarity", "");
            if (!family.equals(socketFamily)) continue;
            double multiplier = switch (rarity) {
                case "Common" -> 1.0;
                case "Uncommon" -> 1.25;
                case "Rare" -> 1.5;
                case "Epic" -> 2.0;
                default -> 0.0;
            };
            best = Math.max(best, multiplier);
        }
        return best;
    }

    private void applyVerdantOnKill(ServerPlayer player) {
        double bestMultiplier = 0;
        bestMultiplier = Math.max(bestMultiplier, getBestSocketedRuneMultiplier(player.getMainHandItem(), "Verdant"));
        if (bestMultiplier <= 0) return;
        double chance = Math.min(0.30, 0.10 * bestMultiplier);
        if (Math.random() < chance) {
            float healing = (float) Math.min(3.0, 1.0 * bestMultiplier);
            player.heal(healing);
            player.sendSystemMessage(Component.literal("[Rune] Verdant restored " + String.format(java.util.Locale.ROOT, "%.1f", healing) + " health."), true);
        }
    }

    private void handleHunterRewards(ServerPlayer player, LivingEntity killedEntity) {
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null || !job.jobId.equals("hunter") || player.isCreative()) return;
        if (killedEntity instanceof ServerPlayer) return;
        awardJobActivity(player, config.getJobXp("hunter"), config.getJobCrowns("hunter"));
    }

    private void updateExplorerProgress(ServerPlayer player) {
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null || !job.jobId.equals("explorer") || player.isSpectator()) return;

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimension = player.level().dimension().identifier().toString();
        String key = dimension + ":" + chunkX + ":" + chunkZ;
        if (job.discoveredChunks == null) job.discoveredChunks = new java.util.HashSet<>();
        if (job.discoveredChunks.add(key)) {
            awardJobActivity(player, config.getJobXp("explorer"), config.getJobCrowns("explorer"));
        }
    }

    private void awardJobActivity(ServerPlayer player, long xp, long crowns) {
        if (xp <= 0 && crowns <= 0) return;
        if (xp > 0) addJobXp(player, xp);
        if (crowns > 0) {
            long oldBalance = balances.getOrDefault(player.getUUID(), 0L);
            long newBalance = oldBalance + crowns;
            if (newBalance < oldBalance) newBalance = Long.MAX_VALUE;
            balances.put(player.getUUID(), newBalance);
            player.sendSystemMessage(Component.literal("+" + crowns + " Crowns  •  Balance: " + newBalance), true);
        }
    }

    private void addJobXp(ServerPlayer player, long amount) {
        if (amount <= 0) return;
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null) return;

        job.xp += amount;
        boolean leveled = false;
        while (job.xp >= xpRequiredForLevel(job.level)) {
            job.xp -= xpRequiredForLevel(job.level);
            job.level++;
            leveled = true;
        }
        sendJobXpFeedback(player, job, amount);
        if (leveled) {
            player.sendSystemMessage(Component.literal("✦ " + capitalize(job.jobId) + " Level Up! Level " + job.level + ""), true);
        }
        updateJobBossBar(player);
    }

    private void sendJobXpFeedback(ServerPlayer player, PlayerJob job, long amount) {
        long required = xpRequiredForLevel(job.level);
        player.sendSystemMessage(Component.literal("+" + amount + " Job XP  •  " + capitalize(job.jobId) + " " + job.level + "  " + job.xp + " / " + required), true);
    }

    private void showJobBossBar(ServerPlayer player) {
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null) return;
        removeJobBossBar(player);
        ServerBossEvent bar = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal(jobTitle(job)),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(jobProgress(job));
        bar.addPlayer(player);
        jobBossBars.put(player.getUUID(), bar);
    }

    private void updateJobBossBar(ServerPlayer player) {
        PlayerJob job = playerJobs.get(player.getUUID());
        if (job == null) {
            removeJobBossBar(player);
            return;
        }
        ServerBossEvent bar = jobBossBars.get(player.getUUID());
        if (bar == null) {
            showJobBossBar(player);
            return;
        }
        bar.setName(Component.literal(jobTitle(job)));
        bar.setProgress(jobProgress(job));
    }

    private void removeJobBossBar(ServerPlayer player) {
        ServerBossEvent bar = jobBossBars.remove(player.getUUID());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    private float jobProgress(PlayerJob job) {
        long required = xpRequiredForLevel(job.level);
        return required <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, (float) job.xp / (float) required));
    }

    private String jobTitle(PlayerJob job) {
        JobDefinition definition = JobDefinition.find(job.jobId);
        String name = definition == null ? capitalize(job.jobId) : definition.displayName;
        return name + " — Level " + job.level + "  " + job.xp + " / " + xpRequiredForLevel(job.level) + " XP";
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private long xpRequiredForLevel(int level) {
        return Math.max(100L, 100L * Math.max(1, level));
    }

    private void sendStatus(ServerPlayer player, String tier, long seconds, int claims, int maximum) {
        sendStatus(null, player, tier, seconds, claims, maximum);
    }

    private void sendStatus(CommandSourceStack source, ServerPlayer player, String tier, long seconds, int claims, int maximum) {
        String nextClaim = config.nextClaimTime(tier, seconds);
        String status = "Runeborn Claims\n"
                + "Tier: " + displayTier(tier) + "\n"
                + "Playtime: " + formatTime(seconds) + "\n"
                + "Claims: " + claims + " / " + maximum + "\n"
                + "Next claim: " + nextClaim;

        if (source != null) {
            source.sendSuccess(() -> Component.literal(status), false);
        } else {
            player.sendSystemMessage(Component.literal(status));
        }
    }

    private String resolveTierForPlayer(ServerPlayer player) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(ServerPlayer.class).getUser(player);
        return resolveTier(user);
    }

    private int reloadConfig(CommandSourceStack source) {
        config = Config.loadOrCreate(configFile);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyPlayerClaimLimit(server, player, false);
        }
        source.sendSuccess(() -> Component.literal("Runeborn Claims config reloaded."), true);
        return 1;
    }

    private int setTime(CommandSourceStack source, String playerName, String time) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player must be online for this command."));
            return 0;
        }
        Long seconds = parseDuration(time);
        if (seconds == null) {
            source.sendFailure(Component.literal("Invalid time. Examples: 3600, 2h, 90m, 1d6h."));
            return 0;
        }
        playSeconds.put(target.getUUID(), seconds);
        applyPlayerClaimLimit(server, target, false);
        saveData();
        source.sendSuccess(() -> Component.literal("Set " + playerName + " playtime to " + formatTime(seconds) + "."), true);
        return 1;
    }

    private void loadData() {
        if (!Files.exists(dataFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(dataFile));
            if (!parsed.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                try {
                    playSeconds.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read Runeborn Claims data: {}", ex.getMessage());
        }
    }

    private void saveData() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            JsonObject root = new JsonObject();
            playSeconds.forEach((uuid, seconds) -> root.addProperty(uuid.toString(), seconds));
            Files.writeString(dataFile, GSON.toJson(root));
        } catch (IOException ex) {
            LOGGER.warn("Could not save Runeborn Claims data: {}", ex.getMessage());
        }
    }

    private int showOwnBalance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long balance = balances.getOrDefault(player.getUUID(), 0L);
        source.sendSuccess(() -> Component.literal("Runeborn Economy\nBalance: " + formatCurrency(balance)), false);
        return 1;
    }

    private int showPlayerBalance(CommandSourceStack source, String playerName) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player must be online for this command."));
            return 0;
        }
        long balance = balances.getOrDefault(target.getUUID(), 0L);
        source.sendSuccess(() -> Component.literal(target.getGameProfile().name() + " balance: " + formatCurrency(balance)), false);
        return 1;
    }

    private int payPlayer(CommandSourceStack source, String playerName, String amountText) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player must be online for this command."));
            return 0;
        }
        if (target.getUUID().equals(sender.getUUID())) {
            source.sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }
        Long amount = parsePositiveAmount(amountText);
        if (amount == null) {
            source.sendFailure(Component.literal("Invalid amount. Use a positive whole number."));
            return 0;
        }
        UUID senderId = sender.getUUID();
        UUID targetId = target.getUUID();
        long senderBalance = balances.getOrDefault(senderId, 0L);
        if (senderBalance < amount) {
            source.sendFailure(Component.literal("Insufficient funds. Your balance is " + formatCurrency(senderBalance) + "."));
            return 0;
        }
        balances.put(senderId, senderBalance - amount);
        balances.put(targetId, balances.getOrDefault(targetId, 0L) + amount);
        saveEconomy();
        source.sendSuccess(() -> Component.literal("Paid " + target.getGameProfile().name() + " " + formatCurrency(amount) + "."), false);
        target.sendSystemMessage(Component.literal("You received " + formatCurrency(amount) + " from " + sender.getGameProfile().name() + "."));
        return 1;
    }

    private int modifyBalance(CommandSourceStack source, String action, String playerName, String amountText) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player must be online for this command."));
            return 0;
        }
        Long amount = parseNonNegativeAmount(amountText);
        if (amount == null) {
            source.sendFailure(Component.literal("Invalid amount. Use a whole number of 0 or more."));
            return 0;
        }
        UUID uuid = target.getUUID();
        long current = balances.getOrDefault(uuid, 0L);
        long next;
        try {
            next = switch (action) {
                case "set" -> amount;
                case "add" -> Math.addExact(current, amount);
                case "take" -> Math.max(0L, current - amount);
                default -> current;
            };
        } catch (ArithmeticException ex) {
            source.sendFailure(Component.literal("Balance is too large."));
            return 0;
        }
        balances.put(uuid, next);
        saveEconomy();
        source.sendSuccess(() -> Component.literal("Set " + target.getGameProfile().name() + " balance to " + formatCurrency(next) + "."), true);
        target.sendSystemMessage(Component.literal("Your Runeborn balance is now " + formatCurrency(next) + "."));
        return 1;
    }

    private Long parsePositiveAmount(String value) {
        try {
            if (!value.matches("\\d+")) return null;
            long amount = Long.parseLong(value);
            return amount > 0L ? amount : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Long parseNonNegativeAmount(String value) {
        try {
            if (!value.matches("\\d+")) return null;
            return Long.parseLong(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatCurrency(long amount) {
        return amount + " Crowns";
    }

    private static final String DEFAULT_SYSTEM_MARKET_JSON = """
            {
              "version": 1,
              "items": [
                {"id":"minecraft:cobblestone","category":"blocks","buyPrice":2,"sellPrice":1,"enabled":true},
                {"id":"minecraft:stone","category":"blocks","buyPrice":3,"sellPrice":1,"enabled":true},
                {"id":"minecraft:dirt","category":"blocks","buyPrice":1,"sellPrice":0,"enabled":true},
                {"id":"minecraft:sand","category":"blocks","buyPrice":3,"sellPrice":1,"enabled":true},
                {"id":"minecraft:glass","category":"blocks","buyPrice":5,"sellPrice":2,"enabled":true},
                {"id":"minecraft:oak_log","category":"blocks","buyPrice":6,"sellPrice":2,"enabled":true},
                {"id":"minecraft:oak_planks","category":"blocks","buyPrice":2,"sellPrice":1,"enabled":true},
                {"id":"minecraft:torch","category":"blocks","buyPrice":3,"sellPrice":1,"enabled":true},
                {"id":"minecraft:obsidian","category":"blocks","buyPrice":100,"sellPrice":25,"enabled":true},
                {"id":"minecraft:coal","category":"ores","buyPrice":8,"sellPrice":3,"enabled":true},
                {"id":"minecraft:raw_iron","category":"ores","buyPrice":12,"sellPrice":4,"enabled":true},
                {"id":"minecraft:iron_ingot","category":"ores","buyPrice":20,"sellPrice":8,"enabled":true},
                {"id":"minecraft:copper_ingot","category":"ores","buyPrice":8,"sellPrice":3,"enabled":true},
                {"id":"minecraft:gold_ingot","category":"ores","buyPrice":35,"sellPrice":12,"enabled":true},
                {"id":"minecraft:redstone","category":"ores","buyPrice":6,"sellPrice":2,"enabled":true},
                {"id":"minecraft:lapis_lazuli","category":"ores","buyPrice":10,"sellPrice":3,"enabled":true},
                {"id":"minecraft:quartz","category":"ores","buyPrice":8,"sellPrice":2,"enabled":true},
                {"id":"minecraft:diamond","category":"ores","buyPrice":250,"sellPrice":75,"enabled":true},
                {"id":"minecraft:emerald","category":"ores","buyPrice":180,"sellPrice":50,"enabled":true},
                {"id":"minecraft:wheat","category":"farming","buyPrice":4,"sellPrice":1,"enabled":true},
                {"id":"minecraft:carrot","category":"farming","buyPrice":4,"sellPrice":1,"enabled":true},
                {"id":"minecraft:potato","category":"farming","buyPrice":4,"sellPrice":1,"enabled":true},
                {"id":"minecraft:sugar_cane","category":"farming","buyPrice":5,"sellPrice":2,"enabled":true},
                {"id":"minecraft:pumpkin","category":"farming","buyPrice":12,"sellPrice":4,"enabled":true},
                {"id":"minecraft:melon_slice","category":"farming","buyPrice":2,"sellPrice":1,"enabled":true},
                {"id":"minecraft:bread","category":"food","buyPrice":8,"sellPrice":3,"enabled":true},
                {"id":"minecraft:cooked_beef","category":"food","buyPrice":12,"sellPrice":4,"enabled":true},
                {"id":"minecraft:baked_potato","category":"food","buyPrice":6,"sellPrice":2,"enabled":true},
                {"id":"minecraft:golden_carrot","category":"food","buyPrice":50,"sellPrice":10,"enabled":true},
                {"id":"minecraft:bone","category":"mob_drops","buyPrice":5,"sellPrice":1,"enabled":true},
                {"id":"minecraft:string","category":"mob_drops","buyPrice":8,"sellPrice":2,"enabled":true},
                {"id":"minecraft:gunpowder","category":"mob_drops","buyPrice":12,"sellPrice":3,"enabled":true},
                {"id":"minecraft:slime_ball","category":"mob_drops","buyPrice":15,"sellPrice":4,"enabled":true},
                {"id":"minecraft:ender_pearl","category":"mob_drops","buyPrice":40,"sellPrice":10,"enabled":true},
                {"id":"minecraft:blaze_rod","category":"mob_drops","buyPrice":50,"sellPrice":12,"enabled":true},
                {"id":"minecraft:arrow","category":"utility","buyPrice":4,"sellPrice":1,"enabled":true},
                {"id":"minecraft:bucket","category":"utility","buyPrice":20,"sellPrice":6,"enabled":true},
                {"id":"minecraft:saddle","category":"utility","buyPrice":100,"sellPrice":25,"enabled":true},
                {"id":"minecraft:name_tag","category":"utility","buyPrice":150,"sellPrice":30,"enabled":true},
                {"id":"minecraft:lead","category":"utility","buyPrice":25,"sellPrice":7,"enabled":true}
              ]
            }
            """;

    private int openSystemMarket(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        openSystemMarket(player, "home", 0);
        return 1;
    }

    private int reloadSystemMarket(CommandSourceStack source) {
        loadSystemMarket();
        source.sendSuccess(() -> Component.literal("Runeborn system market reloaded. Loaded " + systemMarketItems.size() + " configured items."), true);
        return 1;
    }

    private int showSystemMarketInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("System market: " + systemMarketItems.size()
                + " configured items. Edit world/config/runeborn-claims/system-market.json, then run /rbshop reload.\n"
                + "In the shop: left-click buys 1; right-click sells 1; shift-click buys or sells up to 16."), false);
        return 1;
    }

    public static boolean handleSystemMarketMenuClick(AbstractContainerMenu menu, int slot, int button,
                                                       ContainerInput input, Player player) {
        return INSTANCE != null && INSTANCE.handleSystemMarketMenuClickInternal(menu, slot, button, input, player);
    }

    private boolean handleSystemMarketMenuClickInternal(AbstractContainerMenu menu, int slot, int button,
                                                         ContainerInput input, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        AbstractContainerMenu runeMenu = runeForgeMenuSessions.get(serverPlayer.getUUID());
        if (runeMenu == menu) return handleRuneForgeMenuClick(serverPlayer, menu, slot, button, input);
        AbstractContainerMenu socketMenu = runeSocketMenuSessions.get(serverPlayer.getUUID());
        if (socketMenu == menu) return handleRuneSocketMenuClick(serverPlayer, menu, slot, button, input);
        SystemMarketMenuSession session = systemMarketMenuSessions.get(serverPlayer.getUUID());
        if (session == null || session.menu != menu) return false;

        // All clicks in this menu are handled here, including player-inventory slots,
        // to prevent shop icons being moved or items being inserted into the GUI.
        if (slot < 0 || slot >= 54) return true;
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) return true;
        if (button != 0 && button != 1) return true;
        if (button == 1 && input == ContainerInput.QUICK_MOVE) {
            // Shift-right-click sells up to 16 of the selected item.
        }

        if ("home".equals(session.category) && slot >= 0 && slot <= 7) {
            if (input == ContainerInput.PICKUP && button == 0) {
                String category = switch (slot) {
                    case 0 -> "all";
                    case 1 -> "blocks";
                    case 2 -> "ores";
                    case 3 -> "farming";
                    case 4 -> "food";
                    case 5 -> "mob_drops";
                    case 6 -> "utility";
                    case 7 -> "player_trades";
                    default -> "all";
                };
                openSystemMarket(serverPlayer, category, 0);
            }
            return true;
        }
        if (!"home".equals(session.category) && slot == 47) {
            if (input == ContainerInput.PICKUP && button == 0) openSystemMarket(serverPlayer, "home", 0);
            return true;
        }
        if (!"home".equals(session.category) && slot == 48) {
            if (input == ContainerInput.PICKUP && button == 0) openSystemMarket(serverPlayer, "all", 0);
            return true;
        }
        if (slot == 45) {
            if (input == ContainerInput.PICKUP && button == 0 && session.page > 0) {
                openSystemMarket(serverPlayer, session.category, session.page - 1);
            }
            return true;
        }
        if (slot == 53) {
            if (input == ContainerInput.PICKUP && button == 0) {
                int pageCount = getSystemMarketPageCount(session.category);
                if (session.page + 1 < pageCount) openSystemMarket(serverPlayer, session.category, session.page + 1);
            }
            return true;
        }
        if (slot < 9 || slot > 44) return true;

        if ("player_trades".equals(session.category)) {
            List<MarketListing> listings = getPlayerTradeListings();
            int listingIndex = session.page * SYSTEM_MARKET_PAGE_SIZE + slot - 9;
            if (listingIndex < 0 || listingIndex >= listings.size()) return true;
            MarketListing listing = listings.get(listingIndex);
            if (button == 0 && input == ContainerInput.PICKUP) {
                try {
                    buyListing(serverPlayer.createCommandSourceStack(), listing.id);
                } catch (CommandSyntaxException ex) {
                    serverPlayer.sendSystemMessage(Component.literal("Could not process this trade. Try /rbmarket buy " + listing.id + "."));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.literal("Player trades: left-click a listing to buy it. Create listings with /rbmarket sell <price> <amount> while holding the item."));
            }
            openSystemMarket(serverPlayer, session.category, session.page);
            return true;
        }

        List<SystemMarketItem> items = getSystemMarketItemsForCategory(session.category);
        int itemIndex = session.page * SYSTEM_MARKET_PAGE_SIZE + slot - 9;
        if (itemIndex < 0 || itemIndex >= items.size()) return true;
        SystemMarketItem item = items.get(itemIndex);
        int quantity = input == ContainerInput.QUICK_MOVE ? 16 : 1;
        if (button == 0) {
            buySystemMarketItem(serverPlayer, item, quantity);
        } else {
            sellSystemMarketItem(serverPlayer, item, quantity);
        }
        openSystemMarket(serverPlayer, session.category, session.page);
        return true;
    }

    private int openRuneSocket(CommandSourceStack source) throws CommandSyntaxException {
        openRuneSocket(source.getPlayerOrException());
        return 1;
    }

    private void openRuneSocket(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(27);
        ItemStack equipment = player.getMainHandItem().copy();
        ItemStack rune = player.getOffhandItem().copy();

        if (equipment.isEmpty()) {
            setRuneForgeIcon(container, 11, "minecraft:barrier", "Equipment (main hand)", List.of("Hold a sword, axe, or armor piece", "in your main hand."));
        } else {
            int limit = getSocketLimit(equipment);
            updateSocketLore(equipment, limit);
            container.setItem(11, equipment);
        }

        setRuneForgeIcon(container, 13, "minecraft:smithing_table", "Insert Rune", List.of("Hold a genuine Runeborn Rune in your offhand", "Click here to permanently socket it."));

        if (rune.isEmpty() || !isRunebornRune(rune)) {
            setRuneForgeIcon(container, 15, "minecraft:barrier", "Rune (offhand)", List.of("Hold a genuine Runeborn Rune", "in your offhand."));
        } else {
            container.setItem(15, rune);
        }

        // Show installed Rune details as dedicated, non-interactive display slots.
        if (!equipment.isEmpty()) {
            CompoundTag tag = getCustomTag(equipment);
            int count = tag.getIntOr("runeborn_socket_count", 0);
            if (count == 0) {
                setRuneForgeIcon(container, 20, "minecraft:gray_dye", "Sockets: 0/" + getSocketLimit(equipment), List.of("No Runes installed."));
            } else {
                setRuneForgeIcon(container, 20, "minecraft:gray_dye", "Sockets: " + count + "/" + getSocketLimit(equipment), List.of("Installed Runes are shown below."));
                for (int i = 0; i < Math.min(count, 5); i++) {
                    String family = tag.getStringOr("runeborn_socket_" + i + "_family", "Unknown");
                    String rarity = tag.getStringOr("runeborn_socket_" + i + "_rarity", "Unknown");
                    String property = tag.getStringOr("runeborn_socket_" + i + "_property", "Unknown");
                    setRuneForgeIcon(container, 21 + i, "minecraft:echo_shard",
                            "Rune " + (i + 1) + ": " + rarity + " " + family,
                            List.of("Property: " + property, "Installed permanently."));
                }
            }
        }

        player.openMenu(new SimpleMenuProvider((syncId, inventory, menuPlayer) -> {
            ChestMenu menu = ChestMenu.threeRows(syncId, inventory, container);
            runeSocketMenuSessions.put(player.getUUID(), menu);
            return menu;
        }, Component.literal("Runeborn Equipment Sockets")));
    }

    private void updateSocketLore(ItemStack equipment, int socketLimit) {
        if (equipment.isEmpty() || socketLimit <= 0) return;
        CompoundTag tag = getCustomTag(equipment);
        int count = Math.max(0, Math.min(tag.getIntOr("runeborn_socket_count", 0), socketLimit));

        List<Component> lore = new ArrayList<>();
        ItemLore existingLore = equipment.get(DataComponents.LORE);
        if (existingLore != null) {
            for (Component line : existingLore.lines()) {
                String text = line.getString();
                if (text.startsWith("Runeborn Sockets:") || (text.startsWith("Rune ") && text.contains(":"))) {
                    continue;
                }
                lore.add(line);
            }
        }

        lore.add(Component.literal("Runeborn Sockets: " + count + "/" + socketLimit));
        for (int i = 0; i < count; i++) {
            String family = tag.getStringOr("runeborn_socket_" + i + "_family", "Unknown");
            String rarity = tag.getStringOr("runeborn_socket_" + i + "_rarity", "Unknown");
            String property = tag.getStringOr("runeborn_socket_" + i + "_property", "Unknown");
            lore.add(Component.literal("Rune " + (i + 1) + ": " + rarity + " " + family + " (" + property + ")"));
        }
        equipment.set(DataComponents.LORE, new ItemLore(lore));
    }

    private boolean handleRuneSocketMenuClick(ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        if (slot < 0 || slot >= 54) return true;
        if (slot == 13 && input == ContainerInput.PICKUP && button == 0) {
            socketRune(player);
            openRuneSocket(player);
        }
        return true;
    }

    private void socketRune(ServerPlayer player) {
        ItemStack equipment = player.getMainHandItem();
        ItemStack rune = player.getOffhandItem();
        int socketLimit = getSocketLimit(equipment);
        if (socketLimit == 0) {
            player.sendSystemMessage(Component.literal("[Rune Sockets] Hold a sword, axe, or armor piece in your main hand."));
            return;
        }
        if (!isRunebornRune(rune)) {
            player.sendSystemMessage(Component.literal("[Rune Sockets] Hold a genuine Runeborn Rune in your offhand."));
            return;
        }
        CompoundTag equipmentTag = getCustomTag(equipment);
        int socketCount = equipmentTag.getIntOr("runeborn_socket_count", 0);
        if (socketCount >= socketLimit) {
            player.sendSystemMessage(Component.literal("[Rune Sockets] This item has no empty sockets (" + socketCount + "/" + socketLimit + ")."));
            return;
        }
        CompoundTag runeTag = getCustomTag(rune);
        equipmentTag.putString("runeborn_socket_" + socketCount + "_id", runeTag.getStringOr("runeborn_rune_id", ""));
        equipmentTag.putString("runeborn_socket_" + socketCount + "_family", runeTag.getStringOr("runeborn_rune_family", ""));
        equipmentTag.putString("runeborn_socket_" + socketCount + "_rarity", runeTag.getStringOr("runeborn_rune_rarity", ""));
        equipmentTag.putString("runeborn_socket_" + socketCount + "_property", runeTag.getStringOr("runeborn_rune_property", ""));
        equipmentTag.putInt("runeborn_socket_count", socketCount + 1);
        equipment.set(DataComponents.CUSTOM_DATA, CustomData.of(equipmentTag));
        updateSocketLore(equipment, socketLimit);
        rune.shrink(1);
        player.sendSystemMessage(Component.literal("[Rune Sockets] Socketed " + runeTag.getStringOr("runeborn_rune_rarity", "Unknown") + " " + runeTag.getStringOr("runeborn_rune_family", "Unknown") + " Rune (" + (socketCount + 1) + "/" + socketLimit + "). Sockets are permanent in this version."));
    }

    private int getSocketLimit(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        // Classify vanilla equipment by its registered item identifier rather than
        // mapped implementation classes or tag constants that differ by MC version.
        // This keeps the check server-side and rejects unrelated items.
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        if (path.endsWith("_sword") || path.endsWith("_axe")) return 2;

        if (path.endsWith("_helmet")
                || path.endsWith("_chestplate")
                || path.endsWith("_leggings")
                || path.endsWith("_boots")) return 1;

        return 0;
    }

    private boolean isRunebornRune(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:echo_shard")).orElseThrow().value())) return false;
        CompoundTag tag = getCustomTag(stack);
        return "rune".equals(tag.getStringOr("runeborn_item_type", ""))
                && !tag.getStringOr("runeborn_rune_id", "").isBlank()
                && Set.of("Ember", "Frost", "Verdant", "Void").contains(tag.getStringOr("runeborn_rune_family", ""))
                && Set.of("Common", "Uncommon", "Rare", "Epic").contains(tag.getStringOr("runeborn_rune_rarity", ""));
    }

    private CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private int openRuneForge(CommandSourceStack source) throws CommandSyntaxException {
        openRuneForge(source.getPlayerOrException());
        return 1;
    }

    private void openRuneForge(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(27);
        setRuneForgeIcon(container, 11, "minecraft:amethyst_shard", "Rune Shard", List.of("A raw fragment of latent power."));
        setRuneForgeIcon(container, 13, "minecraft:smithing_table", "Process Rune Shards", List.of(
                "Costs 3 Rune Shards", "Click to generate one random Rune", "Rarity and affinity are rolled server-side."));
        setRuneForgeIcon(container, 15, "minecraft:echo_shard", "Possible Rune Affinities", List.of(
                "Ember - combat and fire", "Frost - defense and control", "Verdant - nature and survival", "Void - mobility and exploration"));
        player.openMenu(new SimpleMenuProvider((syncId, inventory, menuPlayer) -> {
            ChestMenu menu = ChestMenu.threeRows(syncId, inventory, container);
            runeForgeMenuSessions.put(player.getUUID(), menu);
            return menu;
        }, Component.literal("Runeborn Rune Forge")));
    }

    private boolean handleRuneForgeMenuClick(ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        if (slot < 0 || slot >= 27) return true;
        if (input == ContainerInput.PICKUP && button == 0 && slot == 13) {
            processRuneShards(player);
            openRuneForge(player);
        }
        return true;
    }

    private void processRuneShards(ServerPlayer player) {
        int shardCount = countRuneShards(player);
        if (shardCount < 3) {
            player.sendSystemMessage(Component.literal("[Rune Forge] You need 3 Rune Shards to process a Rune. Use exploration, combat, and crafting to acquire materials."));
            return;
        }
        if (!canFitInPlayerInventory(player, createRuneItem("Ember", "Common", "Might"))) {
            player.sendSystemMessage(Component.literal("[Rune Forge] Make room in your inventory first."));
            return;
        }
        consumeRuneShards(player, 3);
        String[] families = {"Ember", "Frost", "Verdant", "Void"};
        double roll = Math.random();
        String rarity = roll < 0.55 ? "Common" : roll < 0.82 ? "Uncommon" : roll < 0.96 ? "Rare" : "Epic";
        String family = families[(int) (Math.random() * families.length)];
        String property = switch (family) {
            case "Ember" -> new String[]{"Might", "Focus"}[(int) (Math.random() * 2)];
            case "Frost" -> new String[]{"Guard", "Resilience"}[(int) (Math.random() * 2)];
            case "Verdant" -> new String[]{"Vitality", "Fortune"}[(int) (Math.random() * 2)];
            default -> new String[]{"Swiftness", "Discovery"}[(int) (Math.random() * 2)];
        };
        ItemStack rune = createRuneItem(family, rarity, property);
        if (!player.getInventory().add(rune)) player.drop(rune, false);
        player.sendSystemMessage(Component.literal("[Rune Forge] You processed 3 Rune Shards into a " + rarity + " " + family + " Rune (" + property + "). Rune effects and socketing will be added in a later phase."));
    }

    private ItemStack createRuneItem(String family, String rarity, String property) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:echo_shard")).orElseThrow().value());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(rarity + " " + family + " Rune"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Affinity: " + family));
        lore.add(Component.literal("Property: " + property));
        lore.add(Component.literal("Rarity: " + rarity));
        lore.add(Component.literal("Runeborn Rune - Phase 1"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CompoundTag tag = new CompoundTag();
        tag.putString("runeborn_item_type", "rune");
        tag.putString("runeborn_rune_family", family);
        tag.putString("runeborn_rune_rarity", rarity);
        tag.putString("runeborn_rune_property", property);
        tag.putString("runeborn_rune_id", UUID.randomUUID().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private ItemStack createRuneShard(int amount) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:amethyst_shard")).orElseThrow().value(), amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Rune Shard"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("A raw fragment used by the Rune Forge."))));
        CompoundTag tag = new CompoundTag();
        tag.putString("runeborn_item_type", "rune_shard");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private boolean isRuneShard(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:amethyst_shard")).orElseThrow().value())) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && "rune_shard".equals(data.copyTag().getStringOr("runeborn_item_type", ""));
    }

    private int countRuneShards(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) if (isRuneShard(stack)) count += stack.getCount();
        return count;
    }

    private void consumeRuneShards(ServerPlayer player, int amount) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (amount <= 0) break;
            if (!isRuneShard(stack)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
    }

    private int giveRuneMaterial(CommandSourceStack source, String playerName, String type, int amount) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player is not online: " + playerName));
            return 0;
        }
        if (!"shard".equalsIgnoreCase(type)) {
            source.sendFailure(Component.literal("Supported type: shard. Usage: /rbrune give <player> shard <amount>"));
            return 0;
        }
        ItemStack stack = createRuneShard(amount);
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        source.sendSuccess(() -> Component.literal("Gave " + amount + " Rune Shard(s) to " + target.getGameProfile().name() + "."), true);
        return 1;
    }

    private void setRuneForgeIcon(SimpleContainer container, int slot, String itemId, String title, List<String> loreLines) {
        var itemReference = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElse(null);
        if (itemReference == null) return;
        ItemStack stack = new ItemStack(itemReference.value());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.literal(line));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        container.setItem(slot, stack);
    }

    private void openSystemMarket(ServerPlayer player, String category, int page) {
        if (systemMarketItems.isEmpty() && !"player_trades".equals(category) && !"home".equals(category)) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] The system shop has no valid items configured. Ask an admin to check system-market.json."));
            return;
        }
        List<SystemMarketItem> items = getSystemMarketItemsForCategory(category);
        int itemCount = "home".equals(category) ? 0 : ("player_trades".equals(category) ? getPlayerTradeListings().size() : items.size());
        int pageCount = Math.max(1, (itemCount + SYSTEM_MARKET_PAGE_SIZE - 1) / SYSTEM_MARKET_PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));
        SimpleContainer container = new SimpleContainer(54);

        if ("home".equals(category)) {
            setShopIcon(container, 0, "minecraft:chest", "All Items", List.of("Browse every configured item except building blocks."));
            setShopIcon(container, 1, "minecraft:bricks", "Building Blocks", List.of("Blocks and common building supplies."));
            setShopIcon(container, 2, "minecraft:iron_ingot", "Ores & Minerals", List.of("Metals, gems, and minerals."));
            setShopIcon(container, 3, "minecraft:wheat", "Farming", List.of("Crops and farm supplies."));
            setShopIcon(container, 4, "minecraft:bread", "Food", List.of("Food and basic provisions."));
            setShopIcon(container, 5, "minecraft:bone", "Mob Drops", List.of("Common monster drops."));
            setShopIcon(container, 6, "minecraft:compass", "Utility", List.of("Useful tools and supplies."));
            setShopIcon(container, 7, "minecraft:emerald", "Player Trades", List.of("Browse items listed by other players.", "Left-click a listing to buy it.", "List items with /rbmarket sell <price> <amount>."));
        }

        int start = safePage * SYSTEM_MARKET_PAGE_SIZE;
        if ("player_trades".equals(category)) {
            List<MarketListing> listings = getPlayerTradeListings();
            for (int i = 0; i < SYSTEM_MARKET_PAGE_SIZE && start + i < listings.size(); i++) {
                MarketListing listing = listings.get(start + i);
                ItemStack stack = decodeListingStack(listing);
                if (stack == null || stack.isEmpty()) continue;
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("Quantity: " + listing.amount));
                lore.add(Component.literal("Price: " + formatCurrency(listing.price)));
                lore.add(Component.literal("Seller: " + listing.sellerName));
                lore.add(Component.literal("Listing #" + listing.id));
                lore.add(Component.literal(listing.sellerId.equals(player.getUUID()) ? "Your listing (cannot buy)" : "Left-click to buy"));
                stack.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(9 + i, stack);
            }
        } else {
            for (int i = 0; i < SYSTEM_MARKET_PAGE_SIZE && start + i < items.size(); i++) {
                SystemMarketItem entry = items.get(start + i);
                List<String> lore = new ArrayList<>();
                lore.add(entry.buyPrice > 0 ? "Buy: " + entry.buyPrice + " Crowns each" : "Buying disabled");
                lore.add(entry.sellPrice > 0 ? "Sell: " + entry.sellPrice + " Crowns each" : "Selling disabled");
                lore.add("Left-click: buy 1");
                lore.add("Right-click: sell 1");
                lore.add("Shift-left: buy up to 16");
                lore.add("Shift-right: sell up to 16");
                if (!entry.enabled) lore.add("Currently disabled by an admin");
                setShopIcon(container, 9 + i, entry.id, getItemDisplayName(entry.item), lore);
            }
        }

        if (!"home".equals(category)) {
            setShopIcon(container, 45, "minecraft:arrow", "Previous Page", List.of("Page " + (safePage + 1) + " of " + pageCount));
            setShopIcon(container, 47, "minecraft:compass", "Categories", List.of("Return to the market categories."));
            setShopIcon(container, 48, "minecraft:chest", "All Items", List.of("Browse every configured item except building blocks."));
            setShopIcon(container, 53, "minecraft:arrow", "Next Page", List.of("Page " + (safePage + 1) + " of " + pageCount));
        }
        setShopIcon(container, 49, "minecraft:gold_ingot", "Your Balance", List.of(formatCurrency(balances.getOrDefault(player.getUUID(), 0L)),
                "Left-click an item to buy; right-click to sell."));

        String title = "Runeborn Shop: " + categoryDisplayName(category) + " (" + (safePage + 1) + "/" + pageCount + ")";
        player.openMenu(new SimpleMenuProvider((syncId, inventory, menuPlayer) -> {
            ChestMenu menu = ChestMenu.sixRows(syncId, inventory, container);
            systemMarketMenuSessions.put(player.getUUID(), new SystemMarketMenuSession(menu, category, safePage));
            return menu;
        }, Component.literal(title)));
    }

    private void setShopIcon(SimpleContainer container, int slot, String itemId, String title, List<String> loreLines) {
        try {
            var itemReference = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElse(null);
            if (itemReference == null) return;
            ItemStack stack = new ItemStack(itemReference.value());
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(Component.literal(line));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot, stack);
        } catch (Exception ex) {
            LOGGER.warn("Could not create system shop icon {}: {}", itemId, ex.getMessage());
        }
    }

    private String getItemDisplayName(net.minecraft.world.item.Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private List<SystemMarketItem> getSystemMarketItemsForCategory(String category) {
        List<SystemMarketItem> result = new ArrayList<>();
        for (SystemMarketItem item : systemMarketItems.values()) {
            // Blocks have their own category, so omit them from All Items to avoid duplicate entries.
            boolean include = "all".equals(category) ? !"blocks".equals(item.category) : item.category.equals(category);
            if (item.enabled && include) result.add(item);
        }
        return result;
    }

    private List<MarketListing> getPlayerTradeListings() {
        return marketListings.values().stream()
                .sorted((a, b) -> Integer.compare(a.id, b.id))
                .toList();
    }

    private int getSystemMarketPageCount(String category) {
        int size = "home".equals(category) ? 0 : ("player_trades".equals(category)
                ? getPlayerTradeListings().size()
                : getSystemMarketItemsForCategory(category).size());
        return Math.max(1, (size + SYSTEM_MARKET_PAGE_SIZE - 1) / SYSTEM_MARKET_PAGE_SIZE);
    }

    private String categoryDisplayName(String category) {
        return switch (category) {
            case "blocks" -> "Blocks";
            case "ores" -> "Ores";
            case "farming" -> "Farming";
            case "food" -> "Food";
            case "mob_drops" -> "Mob Drops";
            case "utility" -> "Utility";
            case "player_trades" -> "Player Trades";
            case "home" -> "Categories";
            default -> "All Items";
        };
    }

    private void buySystemMarketItem(ServerPlayer player, SystemMarketItem entry, int requestedAmount) {
        if (!entry.enabled || entry.buyPrice <= 0) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] This item cannot currently be purchased."));
            return;
        }
        int amount = Math.min(requestedAmount, new ItemStack(entry.item).getMaxStackSize());
        long cost;
        try {
            cost = Math.multiplyExact(entry.buyPrice, (long) amount);
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] That purchase is too large."));
            return;
        }
        long balance = balances.getOrDefault(player.getUUID(), 0L);
        if (balance < cost) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] Not enough Crowns. Cost: " + cost + "; balance: " + balance + "."));
            return;
        }
        ItemStack purchased = new ItemStack(entry.item, amount);
        if (!canFitInPlayerInventory(player, purchased)) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] Make room in your main inventory first."));
            return;
        }
        // Debit first so an unexpected crash cannot grant a free purchase.
        balances.put(player.getUUID(), balance - cost);
        saveEconomy();
        if (!player.getInventory().add(purchased)) {
            // Defensive rollback; capacity was checked before the debit.
            balances.put(player.getUUID(), balance);
            saveEconomy();
            player.sendSystemMessage(Component.literal("[Runeborn Market] Purchase cancelled because your inventory could not accept the item."));
            return;
        }
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("[Runeborn Market] Bought " + amount + "x " + getItemDisplayName(entry.item) + " for " + cost + " Crowns."));
    }

    private void sellSystemMarketItem(ServerPlayer player, SystemMarketItem entry, int requestedAmount) {
        if (!entry.enabled || entry.sellPrice <= 0) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] This item cannot currently be sold."));
            return;
        }
        int available = 0;
        int mainSlots = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < mainSlots; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == entry.item) available += stack.getCount();
        }
        int amount = Math.min(requestedAmount, available);
        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] You don't have any " + getItemDisplayName(entry.item) + " in your main inventory."));
            return;
        }
        long proceeds;
        try {
            proceeds = Math.multiplyExact(entry.sellPrice, (long) amount);
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(Component.literal("[Runeborn Market] That sale is too large."));
            return;
        }
        int remaining = amount;
        for (int slot = 0; slot < mainSlots && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != entry.item) continue;
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        long oldBalance = balances.getOrDefault(player.getUUID(), 0L);
        balances.put(player.getUUID(), addCurrencySafely(oldBalance, proceeds));
        saveEconomy();
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("[Runeborn Market] Sold " + amount + "x " + getItemDisplayName(entry.item) + " for " + proceeds + " Crowns."));
    }

    private void loadSystemMarket() {
        if (systemMarketFile == null) return;
        Map<String, SystemMarketItem> loadedItems = new LinkedHashMap<>();
        try {
            Files.createDirectories(systemMarketFile.getParent());
            if (!Files.exists(systemMarketFile)) {
                Files.writeString(systemMarketFile, DEFAULT_SYSTEM_MARKET_JSON, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                LOGGER.info("Created starter Runeborn system market catalogue at {}", systemMarketFile);
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(systemMarketFile));
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("items")
                    || !parsed.getAsJsonObject().get("items").isJsonArray()) {
                LOGGER.error("System market configuration is invalid. The existing file was preserved: {}", systemMarketFile);
                return;
            }
            for (JsonElement element : parsed.getAsJsonObject().getAsJsonArray("items")) {
                try {
                    JsonObject object = element.getAsJsonObject();
                    String id = object.get("id").getAsString();
                    String category = object.get("category").getAsString();
                    long buyPrice = object.get("buyPrice").getAsLong();
                    long sellPrice = object.get("sellPrice").getAsLong();
                    boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
                    if (buyPrice < 0 || sellPrice < 0 || buyPrice > SYSTEM_MARKET_MAX_PRICE || sellPrice > SYSTEM_MARKET_MAX_PRICE) {
                        LOGGER.warn("Skipping system market item {} because its price is outside the allowed range.", id);
                        continue;
                    }
                    if (!Set.of("blocks", "ores", "farming", "food", "mob_drops", "utility").contains(category)) {
                        LOGGER.warn("Skipping system market item {} because category '{}' is invalid.", id, category);
                        continue;
                    }
                    var itemReference = BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElse(null);
                    if (itemReference == null || itemReference.value() == net.minecraft.world.item.Items.AIR) {
                        LOGGER.warn("Skipping unknown system market item {}.", id);
                        continue;
                    }
                    loadedItems.put(id, new SystemMarketItem(id, category, itemReference.value(), buyPrice, sellPrice, enabled));
                } catch (Exception ex) {
                    LOGGER.warn("Skipping invalid entry in system market configuration: {}", ex.getMessage());
                }
            }
            if (loadedItems.isEmpty()) {
                LOGGER.error("System market catalogue contained no valid items; keeping the previously loaded catalogue.");
                return;
            }
            systemMarketItems.clear();
            systemMarketItems.putAll(loadedItems);
            LOGGER.info("Loaded {} items into the Runeborn system market.", systemMarketItems.size());
        } catch (Exception ex) {
            LOGGER.error("Could not load Runeborn system market configuration. Existing file was preserved: {}", ex.getMessage());
        }
    }

    private static final class SystemMarketItem {
        final String id;
        final String category;
        final net.minecraft.world.item.Item item;
        final long buyPrice;
        final long sellPrice;
        final boolean enabled;
        SystemMarketItem(String id, String category, net.minecraft.world.item.Item item, long buyPrice, long sellPrice, boolean enabled) {
            this.id = id;
            this.category = category;
            this.item = item;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.enabled = enabled;
        }
    }

    private static final class SystemMarketMenuSession {
        final AbstractContainerMenu menu;
        final String category;
        final int page;
        SystemMarketMenuSession(AbstractContainerMenu menu, String category, int page) {
            this.menu = menu;
            this.category = category;
            this.page = page;
        }
    }

    private int listMarket(CommandSourceStack source) {
        if (marketListings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Runeborn Market\nNo active listings."), false);
            return 1;
        }
        StringBuilder text = new StringBuilder("Runeborn Market — ").append(marketListings.size()).append(" active listing(s)\n");
        marketListings.values().stream().sorted((a, b) -> Integer.compare(a.id, b.id)).limit(20).forEach(l -> {
            ItemStack stack = decodeListingStack(l);
            String displayName = stack == null || stack.isEmpty() ? l.itemId : stack.getHoverName().getString();
            text.append("#").append(l.id).append(" — ").append(displayName).append(" x").append(l.amount)
                    .append(" — ").append(formatCurrency(l.price)).append(" — Seller: ").append(l.sellerName).append("\n");
        });
        if (marketListings.size() > 20) text.append("Showing first 20 listings; use the listing ID to buy or cancel.\n");
        text.append("Use /rbmarket buy <id> to purchase.");
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private int createListing(CommandSourceStack source, int price, int amount) throws CommandSyntaxException {
        ServerPlayer seller = source.getPlayerOrException();
        ItemStack held = seller.getMainHandItem();
        if (price <= 0 || price > MAX_MARKET_LISTING_PRICE) {
            source.sendFailure(Component.literal("Listing price must be between 1 and " + formatCurrency(MAX_MARKET_LISTING_PRICE) + "."));
            return 0;
        }
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell in your main hand."));
            return 0;
        }
        if (amount <= 0) {
            source.sendFailure(Component.literal("Listing quantity must be at least 1."));
            return 0;
        }
        if (amount > held.getCount()) {
            source.sendFailure(Component.literal("You only have " + held.getCount() + " of that item in your hand."));
            return 0;
        }
        if (amount > held.getMaxStackSize()) {
            source.sendFailure(Component.literal("A listing cannot exceed one valid item stack (maximum " + held.getMaxStackSize() + ")."));
            return 0;
        }
        if (nextMarketId <= 0 || nextMarketId == Integer.MAX_VALUE) {
            source.sendFailure(Component.literal("The marketplace has reached its listing ID limit. Contact an administrator."));
            LOGGER.error("Marketplace listing ID limit reached; refusing new listing.");
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ItemStack listedStack = held.copyWithCount(amount);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonElement itemData;
        try {
            itemData = ItemStack.CODEC.encodeStart(ops, listedStack).getOrThrow();
        } catch (Exception ex) {
            LOGGER.warn("Could not serialize marketplace item {}: {}", itemId, ex.getMessage());
            source.sendFailure(Component.literal("Could not save this item for the marketplace."));
            return 0;
        }
        held.shrink(amount);
        MarketListing listing = new MarketListing(nextMarketId++, seller.getUUID(), seller.getGameProfile().name(), itemId, amount, price, itemData);
        marketListings.put(listing.id, listing);
        saveMarket();
        source.sendSuccess(() -> Component.literal("Listed " + itemId + " x" + amount + " for " + formatCurrency(price) + ". Listing #" + listing.id), false);
        return 1;
    }

    private int buyListing(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer buyer = source.getPlayerOrException();
        MarketListing listing = marketListings.get(id);
        if (listing == null) {
            source.sendFailure(Component.literal("That market listing no longer exists."));
            return 0;
        }
        if (listing.sellerId.equals(buyer.getUUID())) {
            source.sendFailure(Component.literal("You cannot buy your own listing."));
            return 0;
        }
        long balance = balances.getOrDefault(buyer.getUUID(), 0L);
        if (balance < listing.price) {
            source.sendFailure(Component.literal("Insufficient funds. Your balance is " + formatCurrency(balance) + "."));
            return 0;
        }
        ItemStack stack = decodeListingStack(listing);
        if (stack == null || stack.isEmpty()) {
            source.sendFailure(Component.literal("The listed item is no longer available."));
            return 0;
        }
        if (!canFitInPlayerInventory(buyer, stack)) {
            source.sendFailure(Component.literal("Your inventory does not have enough space."));
            return 0;
        }

        // Validate the seller's resulting balance before touching the buyer's inventory.
        long sellerBalance = balances.getOrDefault(listing.sellerId, 0L);
        final long nextSellerBalance;
        try {
            nextSellerBalance = Math.addExact(sellerBalance, listing.price);
        } catch (ArithmeticException ex) {
            LOGGER.error("Marketplace transaction aborted: seller balance overflow for listing #{}.", id);
            source.sendFailure(Component.literal("The purchase could not be completed because the seller's balance is too large."));
            return 0;
        }

        // The capacity and balance checks above happen before changing any state.
        // The actual insertion then occurs before balances/listing state are mutated.
        if (!buyer.getInventory().add(stack) || !stack.isEmpty()) {
            LOGGER.error("Marketplace transaction aborted: item insertion unexpectedly failed for listing #{}.", id);
            source.sendFailure(Component.literal("The purchase could not be completed. Nothing was charged."));
            return 0;
        }

        balances.put(buyer.getUUID(), balance - listing.price);
        balances.put(listing.sellerId, nextSellerBalance);
        marketListings.remove(id);
        if (!commitMarketplaceTransaction()) {
            LOGGER.error("Marketplace transaction #{} changed in memory but could not be durably committed; journal recovery will retry on restart.", id);
            source.sendFailure(Component.literal("The purchase was processed, but saving its transaction failed. Please contact an administrator before retrying."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Bought " + listing.itemId + " x" + listing.amount + " for " + formatCurrency(listing.price) + "."), false);
        ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerId);
        if (seller != null) seller.sendSystemMessage(Component.literal("Your market listing #" + id + " sold for " + formatCurrency(listing.price) + "."));
        return 1;
    }

    private int cancelListing(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer seller = source.getPlayerOrException();
        MarketListing listing = marketListings.get(id);
        if (listing == null) {
            source.sendFailure(Component.literal("That market listing no longer exists."));
            return 0;
        }
        if (!listing.sellerId.equals(seller.getUUID()) && !Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            source.sendFailure(Component.literal("You can only cancel your own listings."));
            return 0;
        }
        ItemStack stack = decodeListingStack(listing);
        if (stack == null || stack.isEmpty()) {
            source.sendFailure(Component.literal("The listed item is no longer available."));
            return 0;
        }
        if (!canFitInPlayerInventory(seller, stack)) {
            source.sendFailure(Component.literal("Your inventory does not have enough space to return the items."));
            return 0;
        }
        if (!seller.getInventory().add(stack) || !stack.isEmpty()) {
            LOGGER.error("Marketplace cancellation aborted: item insertion unexpectedly failed for listing #{}.", id);
            source.sendFailure(Component.literal("The listing could not be cancelled because the items could not be returned."));
            return 0;
        }
        marketListings.remove(id);
        saveMarket();
        source.sendSuccess(() -> Component.literal("Cancelled market listing #" + id + ". Items returned."), false);
        return 1;
    }

    /**
     * Checks the main 36 inventory slots without mutating the player's real inventory.
     * The same stack/component matching rules are used by PlayerInventory when stacking
     * items, so a full inventory cannot cause a listing to be removed before the item fits.
     */
    private boolean canFitInPlayerInventory(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        final int mainInventorySlots = Math.min(36, player.getInventory().getContainerSize());

        for (int slot = 0; slot < mainInventorySlots && remaining > 0; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), remaining);
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int room = Math.max(0, existing.getMaxStackSize() - existing.getCount());
                remaining -= Math.min(room, remaining);
            }
        }
        return remaining <= 0;
    }

    private void loadMarket() {
        if (marketDataFile == null || !Files.exists(marketDataFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(marketDataFile)).getAsJsonObject();
            nextMarketId = Math.max(1, root.has("nextId") ? root.get("nextId").getAsInt() : 1);
            if (!root.has("listings") || !root.get("listings").isJsonObject()) return;
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("listings").entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    JsonElement itemData = o.has("itemData") ? o.get("itemData") : null;
                    MarketListing l = new MarketListing(
                            Integer.parseInt(e.getKey()), UUID.fromString(o.get("sellerId").getAsString()),
                            o.get("sellerName").getAsString(), o.get("itemId").getAsString(),
                            o.get("amount").getAsInt(), o.get("price").getAsLong(), itemData);
                    if (l.id <= 0 || l.amount <= 0 || l.price <= 0
                            || l.price > MAX_MARKET_LISTING_PRICE || l.itemId == null || l.itemId.isBlank()
                            || l.sellerName == null || l.sellerName.isBlank()) {
                        LOGGER.warn("Skipping invalid marketplace listing entry {} in saved data.", e.getKey());
                        continue;
                    }
                    marketListings.put(l.id, l);
                    nextMarketId = Math.max(nextMarketId, l.id == Integer.MAX_VALUE ? Integer.MAX_VALUE : l.id + 1);
                } catch (Exception ignored) { }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read Runeborn Market data: {}", ex.getMessage());
        }
    }

    private String serializeMarket() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextMarketId);
        JsonObject listings = new JsonObject();
        marketListings.forEach((id, l) -> {
            JsonObject o = new JsonObject();
            o.addProperty("sellerId", l.sellerId.toString());
            o.addProperty("sellerName", l.sellerName);
            o.addProperty("itemId", l.itemId);
            o.addProperty("amount", l.amount);
            o.addProperty("price", l.price);
            if (l.itemData != null) o.add("itemData", l.itemData.deepCopy());
            listings.add(String.valueOf(id), o);
        });
        root.add("listings", listings);
        return GSON.toJson(root);
    }

    private String serializeEconomy() {
        JsonObject root = new JsonObject();
        balances.forEach((uuid, balance) -> root.addProperty(uuid.toString(), Math.max(0L, balance)));
        return GSON.toJson(root);
    }

    /**
     * Commits the current marketplace/economy state as one recoverable logical transaction.
     * The journal contains the complete intended post-transaction contents of both files.
     */
    private boolean commitMarketplaceTransaction() {
        if (transactionJournalFile == null) return false;
        String marketJson = serializeMarket();
        String economyJson = serializeEconomy();
        JsonObject journal = new JsonObject();
        journal.addProperty("version", 1);
        journal.addProperty("market", marketJson);
        journal.addProperty("economy", economyJson);
        try {
            Files.createDirectories(transactionJournalFile.getParent());
            if (!writeStringAtomically(transactionJournalFile, GSON.toJson(journal), "Marketplace transaction journal")) return false;
            if (!writeStringAtomically(economyDataFile, economyJson, "Runeborn Economy")) return false;
            if (!writeStringAtomically(marketDataFile, marketJson, "Runeborn Market")) return false;
            Files.deleteIfExists(transactionJournalFile);
            return true;
        } catch (IOException ex) {
            LOGGER.error("Marketplace transaction journal remains for recovery: {}", ex.getMessage());
            return false;
        }
    }

    /** Recover a transaction if the server stopped after journaling but before both files were saved. */
    private void recoverMarketplaceTransaction() {
        if (transactionJournalFile == null || !Files.exists(transactionJournalFile)) return;
        try {
            JsonObject journal = JsonParser.parseString(Files.readString(transactionJournalFile)).getAsJsonObject();
            if (!journal.has("market") || !journal.has("economy")) {
                LOGGER.error("Marketplace transaction journal is invalid. It has been preserved for manual recovery: {}", transactionJournalFile);
                return;
            }
            String marketJson = journal.get("market").getAsString();
            String economyJson = journal.get("economy").getAsString();
            if (!writeStringAtomically(economyDataFile, economyJson, "Runeborn Economy recovery")
                    || !writeStringAtomically(marketDataFile, marketJson, "Runeborn Market recovery")) {
                LOGGER.error("Could not finish marketplace transaction recovery. Journal retained: {}", transactionJournalFile);
                return;
            }
            Files.deleteIfExists(transactionJournalFile);
            LOGGER.warn("Recovered an interrupted marketplace/economy file commit from the transaction journal.");
        } catch (Exception ex) {
            LOGGER.error("Could not recover marketplace transaction journal. Journal retained for manual recovery: {}", ex.getMessage());
        }
    }

    private void saveMarket() {
        if (marketDataFile == null) return;
        try {
            Files.createDirectories(marketDataFile.getParent());
            if (!writeStringAtomically(marketDataFile, serializeMarket(), "Runeborn Market")) {
                LOGGER.error("Runeborn Market data was not committed.");
            }
        } catch (IOException ex) {
            LOGGER.error("Could not prepare Runeborn Market data save: {}", ex.getMessage());
        }
    }

    private static final class MarketListing {
        final int id; final UUID sellerId; final String sellerName; final String itemId; final int amount; final long price; final JsonElement itemData;
        MarketListing(int id, UUID sellerId, String sellerName, String itemId, int amount, long price, JsonElement itemData) {
            this.id=id; this.sellerId=sellerId; this.sellerName=sellerName; this.itemId=itemId; this.amount=amount; this.price=price;
            this.itemData=itemData == null ? null : itemData.deepCopy();
        }
    }

    private ItemStack decodeListingStack(MarketListing listing) {
        if (listing.itemData != null) {
            try {
                RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
                ItemStack stack = ItemStack.CODEC.parse(ops, listing.itemData).getOrThrow();
                if (stack.isEmpty()) return null;
                stack.setCount(listing.amount);
                return stack;
            } catch (Exception ex) {
                LOGGER.warn("Could not deserialize marketplace item #{}: {}", listing.id, ex.getMessage());
                return null;
            }
        }

        // Backward compatibility for listings created before full ItemStack serialization.
        try {
            Identifier key = Identifier.parse(listing.itemId);
            var itemReference = BuiltInRegistries.ITEM.get(key).orElse(null);
            return itemReference == null ? null : new ItemStack(itemReference.value(), listing.amount);
        } catch (Exception ex) {
            return null;
        }
    }

    private void loadEconomy() {
        if (economyDataFile == null || !Files.exists(economyDataFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(economyDataFile));
            if (!parsed.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                try {
                    balances.put(UUID.fromString(entry.getKey()), Math.max(0L, entry.getValue().getAsLong()));
                } catch (Exception ignored) { }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read Runeborn Economy data: {}", ex.getMessage());
        }
    }

    private void saveEconomy() {
        if (economyDataFile == null) return;
        try {
            Files.createDirectories(economyDataFile.getParent());
            if (!writeStringAtomically(economyDataFile, serializeEconomy(), "Runeborn Economy")) {
                LOGGER.error("Runeborn Economy data was not committed.");
            }
        } catch (IOException ex) {
            LOGGER.error("Could not prepare Runeborn Economy data save: {}", ex.getMessage());
        }
    }

    /**
     * Writes a data file through a sibling temporary file and then replaces the live file.
     * This prevents a crash during JSON generation from leaving a partially-written file.
     */
    private boolean writeStringAtomically(Path target, String content, String label) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                LOGGER.warn("Atomic move unavailable for {} data; using safe replacement: {}", label, atomicFailure.getMessage());
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            LOGGER.error("Could not atomically save {} data: {}", label, ex.getMessage());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupEx) {
                LOGGER.warn("Could not clean temporary {} data file: {}", label, cleanupEx.getMessage());
            }
            return false;
        }
    }

    private void loadJobs() {
        if (jobsDataFile == null || !Files.exists(jobsDataFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(jobsDataFile));
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();

            // v0.8.2/v0.8.3 stored only the currently active job. Migrate it
            // into the per-job profile map so existing progress is preserved.
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonElement value = entry.getValue();
                    Map<String, PlayerJob> profiles = new HashMap<>();

                    if (value.isJsonObject() && value.getAsJsonObject().has("jobs")) {
                        JsonObject jobs = value.getAsJsonObject().getAsJsonObject("jobs");
                        for (Map.Entry<String, JsonElement> jobEntry : jobs.entrySet()) {
                            PlayerJob job = GSON.fromJson(jobEntry.getValue(), PlayerJob.class);
                            if (job != null && job.jobId != null && JobDefinition.find(job.jobId) != null) {
                                if (job.discoveredChunks == null) job.discoveredChunks = new java.util.HashSet<>();
                                profiles.put(job.jobId, job);
                            }
                        }
                        playerJobProfiles.put(uuid, profiles);
                        String activeId = value.getAsJsonObject().has("activeJob") && !value.getAsJsonObject().get("activeJob").isJsonNull()
                                ? value.getAsJsonObject().get("activeJob").getAsString() : null;
                        if (activeId != null && profiles.containsKey(activeId)) playerJobs.put(uuid, profiles.get(activeId));
                    } else {
                        PlayerJob legacy = GSON.fromJson(value, PlayerJob.class);
                        if (legacy != null && legacy.jobId != null && JobDefinition.find(legacy.jobId) != null) {
                            if (legacy.discoveredChunks == null) legacy.discoveredChunks = new java.util.HashSet<>();
                            profiles.put(legacy.jobId, legacy);
                            playerJobProfiles.put(uuid, profiles);
                            playerJobs.put(uuid, legacy);
                        }
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read Runeborn Jobs data: {}", ex.getMessage());
        }
    }

    private void saveJobs() {
        if (jobsDataFile == null) return;
        try {
            Files.createDirectories(jobsDataFile.getParent());
            JsonObject root = new JsonObject();
            playerJobProfiles.forEach((uuid, profiles) -> {
                JsonObject playerObject = new JsonObject();
                PlayerJob active = playerJobs.get(uuid);
                if (active != null) playerObject.addProperty("activeJob", active.jobId);
                else playerObject.add("activeJob", com.google.gson.JsonNull.INSTANCE);
                JsonObject jobs = new JsonObject();
                profiles.forEach((jobId, job) -> jobs.add(jobId, GSON.toJsonTree(job)));
                playerObject.add("jobs", jobs);
                root.add(uuid.toString(), playerObject);
            });
            Files.writeString(jobsDataFile, GSON.toJson(root));
        } catch (IOException ex) {
            LOGGER.warn("Could not save Runeborn Jobs data: {}", ex.getMessage());
        }
    }

    private static String displayTier(String tier) {
        return switch (tier) {
            case "wayfarer" -> "Wayfarer";
            case "runekeeper" -> "Rune Keeper";
            case "ascendant" -> "Ascendant";
            case "admin" -> "Admin";
            default -> "Runeborn";
        };
    }

    private static Long parseDuration(String value) {
        try {
            if (value.matches("\\d+")) return Long.parseLong(value);
            long total = 0L;
            String remaining = value.toLowerCase();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)(d|h|m|s)").matcher(remaining);
            int end = 0;
            while (matcher.find()) {
                long amount = Long.parseLong(matcher.group(1));
                switch (matcher.group(2)) {
                    case "d" -> total += amount * 86400L;
                    case "h" -> total += amount * 3600L;
                    case "m" -> total += amount * 60L;
                    case "s" -> total += amount;
                }
                end = matcher.end();
            }
            return end == remaining.length() ? total : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String formatTime(long seconds) {
        long d = seconds / 86400L;
        long h = (seconds % 86400L) / 3600L;
        long m = (seconds % 3600L) / 60L;
        return d > 0 ? String.format("%dd %02dh %02dm", d, h, m)
                : String.format("%02dh %02dm", h, m);
    }

    private static final class RankInfo {
        final String key;
        final String displayName;
        private RankInfo(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        static RankInfo fromKey(String key) {
            return switch (key) {
                case "wayfarer" -> new RankInfo("wayfarer", "Wayfarer");
                case "runekeeper" -> new RankInfo("runekeeper", "Rune Keeper");
                case "ascendant" -> new RankInfo("ascendant", "Ascendant");
                case "admin" -> new RankInfo("admin", "Admin");
                default -> new RankInfo("runeborn", "Runeborn");
            };
        }
    }

    private static final class PlayerJob {
        String jobId;
        int level;
        long xp;
        java.util.Set<String> discoveredChunks = new java.util.HashSet<>();
        PlayerJob(String jobId, int level, long xp) { this.jobId = jobId; this.level = level; this.xp = xp; }
    }

    private static final class JobDefinition {
        static final List<JobDefinition> ALL = List.of(
                new JobDefinition("miner", "Miner", "Earn job progress by mining ores and stone."),
                new JobDefinition("farmer", "Farmer", "Earn job progress by farming and harvesting crops."),
                new JobDefinition("lumberjack", "Lumberjack", "Earn job progress by cutting trees and logs."),
                new JobDefinition("fisher", "Fisher", "Earn job progress by fishing."),
                new JobDefinition("hunter", "Hunter", "Earn job progress by hunting hostile and passive mobs."),
                new JobDefinition("explorer", "Explorer", "Earn job progress by exploring the world."),
                new JobDefinition("blacksmith", "Blacksmith", "Earn job progress through future smithing and crafting activities."),
                new JobDefinition("arcanist", "Arcanist", "Earn job progress through future magical and enchanting activities.")
        );
        final String id;
        final String displayName;
        final String description;
        JobDefinition(String id, String displayName, String description) { this.id = id; this.displayName = displayName; this.description = description; }
        static JobDefinition find(String id) { for (JobDefinition job : ALL) if (job.id.equalsIgnoreCase(id)) return job; return null; }
    }

    private static final class PlayerSnapshot {
        final double x;
        final double y;
        final double z;
        final float xRot;
        final float yRot;

        private PlayerSnapshot(double x, double y, double z, float xRot, float yRot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.xRot = xRot;
            this.yRot = yRot;
        }

        static PlayerSnapshot from(ServerPlayer player) {
            return new PlayerSnapshot(
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot()
            );
        }

        boolean isMeaningfullyDifferent(PlayerSnapshot other, Config config) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;

            float yawDelta = angleDifference(yRot, other.yRot);
            float pitchDelta = angleDifference(xRot, other.xRot);

            return distanceSquared >= config.movementThresholdBlocks * config.movementThresholdBlocks
                    || Math.abs(yawDelta) >= config.lookThresholdDegrees
                    || Math.abs(pitchDelta) >= config.lookThresholdDegrees;
        }

        private static float angleDifference(float a, float b) {
            float diff = (a - b) % 360.0f;
            if (diff > 180.0f) diff -= 360.0f;
            if (diff < -180.0f) diff += 360.0f;
            return diff;
        }
    }

    private static final class Config {
        boolean enabled = true;
        long saveIntervalSeconds = 60;
        long afkTimeoutSeconds = 300;
        double movementThresholdBlocks = 0.10;
        float lookThresholdDegrees = 3.0f;

        Map<String, Integer> baseClaims = Map.of(
                "runeborn", 4,
                "wayfarer", 8,
                "runekeeper", 12,
                "ascendant", 16,
                "admin", 500
        );

        Map<String, Integer> maxClaims = Map.of(
                "runeborn", 20,
                "wayfarer", 40,
                "runekeeper", 60,
                "ascendant", 80,
                "admin", 500
        );

        Map<String, Integer> maxForceloads = Map.of(
                "runeborn", 1,
                "wayfarer", 2,
                "runekeeper", 3,
                "ascendant", 5,
                "admin", 20
        );

        Map<String, Integer> homeLimits = Map.of(
                "runeborn", 2,
                "wayfarer", 4,
                "runekeeper", 5,
                "ascendant", 7,
                "admin", 20
        );

        Map<String, Integer> jobXp = Map.of(
                "miner", 2,
                "farmer", 3,
                "lumberjack", 3,
                "fisher", 8,
                "hunter", 6,
                "explorer", 5,
                "blacksmith", 5,
                "arcanist", 5
        );

        Map<String, Long> jobCrowns = Map.of(
                "miner", 1L,
                "farmer", 2L,
                "lumberjack", 2L,
                "fisher", 5L,
                "hunter", 5L,
                "explorer", 10L,
                "blacksmith", 5L,
                "arcanist", 5L
        );

        int getMaxHomes(String tier) {
            String key = homeLimits.containsKey(tier) ? tier : "runeborn";
            return homeLimits.get(key);
        }

        int getJobXp(String job) {
            if (jobXp == null) return 0;
            return jobXp.getOrDefault(job, 0);
        }

        long getJobCrowns(String job) {
            if (jobCrowns == null) return 0L;
            return jobCrowns.getOrDefault(job, 0L);
        }

        static Config loadOrCreate(Path path) {
            try {
                Files.createDirectories(path.getParent());
                if (!Files.exists(path)) {
                    Config defaults = new Config();
                    Files.writeString(path, GSON.toJson(defaults));
                    return defaults;
                }
                Config loaded = GSON.fromJson(Files.readString(path), Config.class);
                if (loaded == null) loaded = new Config();
                if (loaded.jobXp == null) loaded.jobXp = new Config().jobXp;
                if (loaded.jobCrowns == null) loaded.jobCrowns = new Config().jobCrowns;
                return loaded;
            } catch (Exception ex) {
                return new Config();
            }
        }

        int calculateClaims(String tier, long seconds) {
            String key = baseClaims.containsKey(tier) ? tier : "runeborn";
            if (key.equals("admin")) return maxClaims.get("admin");
            long hours = seconds / 3600L;
            long calculated = baseClaims.get(key) + hours;
            return (int) Math.min(calculated, maxClaims.get(key));
        }

        int getMaxClaims(String tier) {
            String key = maxClaims.containsKey(tier) ? tier : "runeborn";
            return maxClaims.get(key);
        }

        int getMaxForceloads(String tier) {
            String key = maxForceloads.containsKey(tier) ? tier : "runeborn";
            return maxForceloads.get(key);
        }

        String nextClaimTime(String tier, long seconds) {
            int maximum = getMaxClaims(tier);
            int current = calculateClaims(tier, seconds);
            if (current >= maximum) return "MAX";

            long remainder = seconds % 3600L;
            long remaining = 3600L - remainder;
            long hours = remaining / 3600L;
            long minutes = (remaining % 3600L) / 60L;
            long secs = remaining % 60L;
            return String.format("%02dh %02dm %02ds", hours, minutes, secs);
        }
    }
}
