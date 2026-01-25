package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

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

        event.setResult(LoginEvent.ComponentResult.denied(mm.deserialize(kickReason)));
    }
}
