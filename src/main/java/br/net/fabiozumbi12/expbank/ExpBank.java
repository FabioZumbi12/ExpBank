package br.net.fabiozumbi12.expbank;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.GuiceObjectMapperFactory;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.world.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.reflect.TypeToken.of;

@Plugin(
        id = "expbank",
        name = "ExpBank",
        version = "1.1",
        description = "Plugin to store player experiences on signs",
        authors = {
                "FabioZumbi12"
        }
)
public class ExpBank {

    @Inject
    public GuiceObjectMapperFactory factory;
    @Inject
    private Logger logger;
    @Inject
    private Game game;
    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;
    private ConfigCategory root;
    private ConfigurationLoader<CommentedConfigurationNode> cfgLoader;

    private ConfigurationNode offRoot;
    private ConfigurationLoader<CommentedConfigurationNode> offlineLoader;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdir();
        }
        cfgLoader = HoconConfigurationLoader.builder().setFile(new File(configDir.toFile(), "config.conf")).build();
        offlineLoader = HoconConfigurationLoader.builder().setFile(new File(configDir.toFile(), "offline.conf")).build();
        initConfig();

        CommandSpec give = CommandSpec.builder()
                .description(Text.of("Command to give experience to online and offline players"))
                .arguments(
                        GenericArguments.firstParsing(GenericArguments.player(Text.of("player")), GenericArguments.string(Text.of("offline-player"))),
                        GenericArguments.integer(Text.of("level"))
                )
                .permission("expbank.command.give")
                .executor((sender, args) -> {
                    int level = args.<Integer>getOne("level").get();

                    if (args.hasAny("player")){
                        Player p = args.<Player>getOne("player").get();
                        int player_level = p.get(Keys.EXPERIENCE_LEVEL).get();
                        p.offer(Keys.EXPERIENCE_LEVEL, level + player_level);
                        sender.sendMessage(toText(root.strings.command_give
                                .replace("{value}", String.valueOf(level))
                                .replace("{player}", p.getName())));
                    } else
                    if (args.hasAny("offline-player")){
                        String p = args.<String>getOne("offline-player").get();
                        setOfflineLevel(p, level);
                        offRoot.getNode("offline", p).setValue(level);
                        sender.sendMessage(toText(root.strings.command_give
                                .replace("{value}", String.valueOf(level))
                                .replace("{player}", p)));
                    }
                    return CommandResult.success();
                }).build();

        CommandSpec reload = CommandSpec.builder()
                .description(Text.of("Reload command for ExpBank"))
                .permission("expbank.command.reload")
                .executor((sender, args) -> {

                    initConfig();
                    sender.sendMessage(toText(root.strings.command_reload));
                    return CommandResult.success();

                }).build();

        Sponge.getCommandManager().register(this, CommandSpec.builder()
                .description(Text.of("Main command for ExpBank"))
                .permission("expbank.command.base")
                .executor((sender, args) -> {

                    sender.sendMessage(toText("&a[ExpBank] Developed by FabioZumbi12"));
                    List<String> cmds = Stream.of("reload", "give").filter(cmd-> sender.hasPermission("expbank.command."+cmd)).collect(Collectors.toList());
                    if (!cmds.isEmpty()){
                        sender.sendMessage(toText("&6- Available commands: " + Arrays.toString(cmds.toArray())));
                    }
                    return CommandResult.success();

                })
                .child(reload, "reload")
                .child(give, "give")
                .build(), "expbank");


        logger.info("Experience Bank enabled!");
    }

    private void initConfig() {
        try {
            ConfigurationNode configRoot = cfgLoader.load(ConfigurationOptions.defaults().setObjectMapperFactory(factory).setShouldCopyDefaults(true));
            root = configRoot.getValue(of(ConfigCategory.class), new ConfigCategory());
            cfgLoader.save(configRoot);

            offRoot = offlineLoader.load(ConfigurationOptions.defaults().setObjectMapperFactory(factory).setShouldCopyDefaults(true));
            offlineLoader.save(offRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Listener(order = Order.LATE)
    public void onPlayerJoin(ClientConnectionEvent.Join e) {
        Player p = e.getTargetEntity();
        ConfigurationNode playerNode = offRoot.getNode("offline", p.getName());
        if (!playerNode.isVirtual()) {
            int player_level = p.get(Keys.EXPERIENCE_LEVEL).get() + playerNode.getInt(0);
            p.offer(Keys.EXPERIENCE_LEVEL, player_level);
            if (root.message_onjoin) p.sendMessage(toText(root.strings.other_sign_break
                    .replace("{value}", String.valueOf(playerNode.getInt(0)))));
            playerNode.setValue(null);
            try {
                offlineLoader.save(offRoot);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Listener(order = Order.LATE)
    public void onSignPlace(ChangeSignEvent e, @First Player p) {
        if (e.getText().get(0).get().toPlain().equalsIgnoreCase(root.sign_prefix)) {
            if (!p.hasPermission("expbank.use")) {
                p.sendMessage(toText(root.strings.no_permission));
                e.setCancelled(true);
                return;
            }
            Location attached = e.getTargetTile().getLocation().getBlockRelative(e.getTargetTile().getLocation().get(Keys.DIRECTION).get().getOpposite());
            if (attached.hasTileEntity()) {
                e.setCancelled(true);
                p.sendMessage(toText(root.strings.cant_place));
                return;
            }
            e.getText().setElement(0, Text.of(root.sign_prefix));
            e.getText().setElement(1, Text.of(p.getName()));
            e.getText().setElement(2, Text.of(0));
            e.getText().setElement(3, Text.of());
            p.sendMessage(toText(root.strings.bank_created));
        }
    }

    @Listener(order = Order.FIRST)
    public void onSignBreak(ChangeBlockEvent.Break e) {
        List<Transaction<BlockSnapshot>> bt = e.getTransactions().stream().filter(bl ->
                bl.getOriginal().getState().getType().equals(BlockTypes.STANDING_SIGN) ||
                        bl.getOriginal().getState().getType().equals(BlockTypes.WALL_SIGN)).collect(Collectors.toList());

        for (Transaction<BlockSnapshot> b : bt) {
            List<String> lines = getWorldSignLines(b.getOriginal());
            if (lines.get(0).equalsIgnoreCase(root.sign_prefix)) {
                if (lines.get(1).isEmpty() || lines.get(2).isEmpty())
                    continue;

                String playerName = lines.get(1);
                String sign_level = lines.get(2);

                if (e.getCause().first(Living.class).isPresent() && e.getCause().first(Living.class).get() instanceof Player) {
                    Player p = (Player) e.getCause().first(Living.class).get();
                    if (lines.get(1).equalsIgnoreCase(p.getName()) || p.hasPermission("expbank.others")) {
                        if (game.getServer().getPlayer(playerName).isPresent()) {
                            int player_level = game.getServer().getPlayer(playerName).get().get(Keys.EXPERIENCE_LEVEL).get() + Integer.parseInt(sign_level);
                            game.getServer().getPlayer(playerName).get().offer(Keys.EXPERIENCE_LEVEL, player_level);
                            if (game.getServer().getPlayer(playerName).get().equals(p)) {
                                p.sendMessage(toText(root.strings.exp_removed.replace("{value}", sign_level)));
                            } else {
                                p.sendMessage(toText(root.strings.exp_removed_other
                                        .replace("{value}", sign_level)
                                        .replace("{other}", game.getServer().getPlayer(playerName).get().getName())));
                            }
                        } else {
                            setOfflineLevel(lines.get(1), sign_level);
                        }
                    }
                } else {
                    if (game.getServer().getPlayer(playerName).isPresent()) {
                        int player_level = game.getServer().getPlayer(playerName).get().get(Keys.EXPERIENCE_LEVEL).get() + Integer.parseInt(sign_level);
                        game.getServer().getPlayer(playerName).get().offer(Keys.EXPERIENCE_LEVEL, player_level);
                        game.getServer().getPlayer(playerName).get().sendMessage(toText(root.strings.other_sign_break.replace("{value}", sign_level)));
                    } else {
                        setOfflineLevel(lines.get(1), sign_level);
                    }
                }
            }
        }
    }

    @Listener(order = Order.LATE)
    public void onInteractSign(InteractBlockEvent e, @First Player p) {
        BlockSnapshot b = e.getTargetBlock();
        if (b.getState().getType().equals(BlockTypes.WALL_SIGN) || b.getState().getType().equals(BlockTypes.STANDING_SIGN)) {
            if (!b.getLocation().get().hasTileEntity()) return;

            Sign s = (Sign) b.getLocation().get().getTileEntity().get();
            if (s.lines().get(0).toPlain().equalsIgnoreCase(root.sign_prefix)) {
                if (s.lines().get(1).isEmpty() || s.lines().get(2).isEmpty())
                    return;

                if (!p.hasPermission("expbank.use")) {
                    p.sendMessage(toText(root.strings.no_permission));
                    return;
                }
                if (s.lines().get(1).toPlain().equalsIgnoreCase(p.getName())) {

                    int player_level = p.get(Keys.EXPERIENCE_LEVEL).get();
                    int sign_level = Integer.parseInt(s.lines().get(2).toPlain());
                    int sign_diff = Integer.parseInt(s.lines().get(2).toPlain());
                    boolean sneaking = p.get(Keys.IS_SNEAKING).get() && root.shift_once;

                    // Put exp
                    if (e instanceof InteractBlockEvent.Secondary) {
                        if (player_level > 0) {
                            if (sneaking || (player_level - root.level_per_interaction < 0)) {
                                sign_level += player_level;
                                player_level = 0;
                            } else {
                                sign_level += root.level_per_interaction;
                                player_level -= root.level_per_interaction;
                            }

                            if (!sneaking) {
                                if (sign_level > root.max_level) {
                                    p.sendMessage(toText(root.strings.max_deposit.replace("{value}", String.valueOf(root.max_level))));
                                    return;
                                }
                            }

                            if (sign_level > root.max_level) {
                                player_level += sign_level - root.max_level;
                                sign_level = root.max_level;
                                if (sign_level == sign_diff) {
                                    p.sendMessage(toText(root.strings.max_deposit.replace("{value}", String.valueOf(root.max_level))));
                                    return;
                                }
                            }
                        }
                        p.sendMessage(toText(root.strings.exp_added.replace("{value}", String.valueOf(sign_level - sign_diff))));

                    } else
                        // Takeout exp
                        if (e instanceof InteractBlockEvent.Primary) {

                            if (sneaking || (sign_level - root.level_per_interaction < 0)) {
                                player_level += sign_level;
                                sign_level = 0;
                            } else {
                                sign_level -= root.level_per_interaction;
                                player_level += root.level_per_interaction;
                            }
                            p.sendMessage(toText(root.strings.exp_removed.replace("{value}", String.valueOf(sign_diff - sign_level))));
                        }

                    // Set sign and player exp
                    s.offer(Keys.SIGN_LINES, Arrays.asList(toText(root.sign_prefix), s.lines().get(1), Text.of(sign_level)));
                    p.offer(Keys.EXPERIENCE_LEVEL, player_level);
                } else {
                    e.setCancelled(true);
                    p.sendMessage(toText(root.strings.bank_not_yours));
                }
            }
        }
    }

    private void setOfflineLevel(String player, String level) {
        setOfflineLevel(player, Integer.parseInt(level));
    }

    private void setOfflineLevel(String player, int level) {
        ConfigurationNode node = offRoot.getNode("offline", player);
        node.setValue(node.getInt(0) + level);
        try {
            offlineLoader.save(offRoot);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private List<String> getWorldSignLines(BlockSnapshot block) {
        DataView dv = block.toContainer().getView(DataQuery.of("UnsafeData")).get();
        return new ArrayList<>(Arrays.asList(
                TextSerializers.JSON.deserialize(dv.getString(DataQuery.of("Text1")).get()).toPlain(),
                TextSerializers.JSON.deserialize(dv.getString(DataQuery.of("Text2")).get()).toPlain(),
                TextSerializers.JSON.deserialize(dv.getString(DataQuery.of("Text3")).get()).toPlain(),
                TextSerializers.JSON.deserialize(dv.getString(DataQuery.of("Text4")).get()).toPlain()
        ));
    }

    private Text toText(String str) {
        return TextSerializers.FORMATTING_CODE.deserialize(str);
    }
}
