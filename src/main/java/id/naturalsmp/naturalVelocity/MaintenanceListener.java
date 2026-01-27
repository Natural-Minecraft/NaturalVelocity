package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MaintenanceListener {

    private final NaturalVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MaintenanceListener(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!plugin.isMaintenanceActive())
            return;

        Player player = event.getPlayer();
        if (player.hasPermission("naturalsmp.maintenance.bypass") ||
                plugin.getWhitelistedPlayers().contains(player.getUsername().toLowerCase())) {
            return;
        }

        String kickReason = plugin.getConfig().getString("maintenance.kick-reason",
                "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP MAINTENANCE</bold></gradient>\n\n<gray>Server sedang dalam perbaikan.\nMohon kembali lagi nanti!");

        Component component = parse(kickReason);

        // Flatten the component to legacy format for maximum compatibility during Login
        // state
        // This prevents DecoderException on some clients (like Optifine)
        Component flattened = LegacyComponentSerializer.legacySection()
                .deserialize(LegacyComponentSerializer.legacySection().serialize(component));

        event.setResult(LoginEvent.ComponentResult.denied(flattened));
    }

    private Component parse(String text) {
        if (text == null)
            return Component.empty();
        // 1. Support &#RRGGBB by converting to MiniMessage <#RRGGBB>
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        // 2. Support legacy & codes
        processed = processed.replace("&", "§");

        if (processed.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(processed);
        }
        return mm.deserialize(processed);
    }
}
