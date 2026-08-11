package com.seamlessportals.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SeamlessPortalsPaper extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String CHANNEL = "seamlessportals:sync";
    private final Set<UUID> enhancedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        
        getLogger().info("Seamless Portals Paper integration enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        
        // Handle client handshake
        if (message.length > 0 && message[0] == 0x01) { // Handshake packet
            enhancedPlayers.add(player.getUniqueId());
            sendSyncData(player);
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        // When a portal is created, notify nearby enhanced players
        for (Player player : event.getWorld().getPlayers()) {
            if (enhancedPlayers.contains(player.getUniqueId())) {
                sendSyncData(player);
            }
        }
    }

    private void sendSyncData(Player player) {
        // Send minimal metadata about nearby portals
        // For this prototype, we just send a "connected" signal
        byte[] data = new byte[]{0x02}; // Sync packet
        player.sendPluginMessage(this, CHANNEL, data);
    }
}
