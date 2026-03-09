package id.naturalsmp.naturalvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import id.naturalsmp.naturalvelocity.NaturalVelocity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HeadMotdCommand implements SimpleCommand {

    private final NaturalVelocity plugin;

    public HeadMotdCommand(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("naturalvelocity.admin")) {
            invocation.source().sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            invocation.source().sendMessage(Component.text("Usage: /headmotd <process|reload>", NamedTextColor.RED));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigAndIcon();
            invocation.source()
                    .sendMessage(Component.text("NaturalVelocity Config & HeadMOTD reloaded!", NamedTextColor.GREEN));
            return;
        }

        if (args[0].equalsIgnoreCase("process")) {
            if (!plugin.getConfig().getBoolean("head-motd.enabled", false)) {
                invocation.source()
                        .sendMessage(Component.text("Head MOTD is not enabled in velocity.toml!", NamedTextColor.RED));
                return;
            }

            int pct = 100;
            if (args.length > 1) {
                try {
                    pct = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }

            String fileName = plugin.getConfig().getString("head-motd.image-file", "motd.png");
            File imageFile = new File(plugin.getDataDirectory().toFile(), fileName);

            if (!imageFile.exists()) {
                invocation.source().sendMessage(
                        Component.text("Image file not found: " + imageFile.getAbsolutePath(), NamedTextColor.RED));
                return;
            }

            invocation.source().sendMessage(
                    Component.text("Starting Head MOTD processing for " + fileName + "...", NamedTextColor.YELLOW));

            plugin.getHeadImageProcessor().process(imageFile, pct).thenAccept(rows -> {
                plugin.getMotdUrls().clear();
                plugin.getMotdUrls().addAll(rows);

                List<String> rowStrings = new ArrayList<>();
                rows.forEach(urls -> rowStrings.add(String.join(",", urls)));
                plugin.getTextureCache().put("motd-mapping-cache", String.join(";", rowStrings));

                plugin.getJsonCacheManager().buildMotdCache(plugin.getMotdUrls());

                invocation.source().sendMessage(Component.text(
                        "✓ Head MOTD Processing Complete! Processed " + rows.size() + " rows.", NamedTextColor.GREEN));
            }).exceptionally(ex -> {
                invocation.source()
                        .sendMessage(Component.text("Failed to process MOTD: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
        }
    }
}
