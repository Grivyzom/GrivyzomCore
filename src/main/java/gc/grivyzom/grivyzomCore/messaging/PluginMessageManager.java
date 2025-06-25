package gc.grivyzom.grivyzomCore.messaging;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gc.grivyzom.grivyzomCore.Main;
import gc.grivyzom.grivyzomCore.models.GrivyzomPlayer;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.io.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gestor de mensajería entre plugins para permitir comunicación
 * entre GrivyzomCore y otros plugins del network
 */
public class PluginMessageManager {

    private final ProxyServer server;
    private final Logger logger;

    // Canales de comunicación
    private static final MinecraftChannelIdentifier GRIVYZOM_CHANNEL =
            MinecraftChannelIdentifier.from("grivyzom:core");

    private static final MinecraftChannelIdentifier ECONOMY_CHANNEL =
            MinecraftChannelIdentifier.from("grivyzom:economy");

    private static final MinecraftChannelIdentifier RANKUP_CHANNEL =
            MinecraftChannelIdentifier.from("grivyzom:rankup");

    private static final MinecraftChannelIdentifier PVP_CHANNEL =
            MinecraftChannelIdentifier.from("grivyzom:pvp");

    // Tipos de mensajes
    private static final String GET_PLAYER_DATA = "GET_PLAYER_DATA";
    private static final String UPDATE_COINS = "UPDATE_COINS";
    private static final String UPDATE_GEMS = "UPDATE_GEMS";
    private static final String UPDATE_RANK = "UPDATE_RANK";
    private static final String PLAYER_DATA_RESPONSE = "PLAYER_DATA_RESPONSE";
    private static final String GET_TOP_PLAYERS = "GET_TOP_PLAYERS";
    private static final String SYNC_PLAYER_DATA = "SYNC_PLAYER_DATA";

    public PluginMessageManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /**
     * Registra los canales de mensajería
     */
    public void registerChannels() {
        server.getChannelRegistrar().register(GRIVYZOM_CHANNEL);
        server.getChannelRegistrar().register(ECONOMY_CHANNEL);
        server.getChannelRegistrar().register(RANKUP_CHANNEL);
        server.getChannelRegistrar().register(PVP_CHANNEL);

        // Registrar el listener de eventos
        server.getEventManager().register(Main.getInstance(), this);

        MessageUtils.sendSuccessMessage(logger, "Canales de mensajería registrados correctamente");
    }

    /**
     * Desregistra los canales de mensajería
     */
    public void unregisterChannels() {
        server.getChannelRegistrar().unregister(GRIVYZOM_CHANNEL);
        server.getChannelRegistrar().unregister(ECONOMY_CHANNEL);
        server.getChannelRegistrar().unregister(RANKUP_CHANNEL);
        server.getChannelRegistrar().unregister(PVP_CHANNEL);

        MessageUtils.sendInfoMessage(logger, "Canales de mensajería desregistrados");
    }

    /**
     * Maneja los mensajes recibidos de otros plugins
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        ServerConnection server = (ServerConnection) event.getSource();
        MinecraftChannelIdentifier identifier = event.getIdentifier();

        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
            DataInputStream input = new DataInputStream(stream);

            String messageType = input.readUTF();

            if (Main.getInstance().getConfigManager().isDebugMode()) {
                MessageUtils.sendDebugMessage(logger,
                        String.format("Mensaje recibido - Canal: %s, Tipo: %s, Servidor: %s",
                                identifier.getId(), messageType, server.getServerInfo().getName()));
            }

            // Procesar según el canal y tipo de mensaje
            if (identifier.equals(GRIVYZOM_CHANNEL) || identifier.equals(ECONOMY_CHANNEL)) {
                handleEconomyMessage(server, messageType, input);
            } else if (identifier.equals(RANKUP_CHANNEL)) {
                handleRankupMessage(server, messageType, input);
            } else if (identifier.equals(PVP_CHANNEL)) {
                handlePvPMessage(server, messageType, input);
            }

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger,
                    "Error al procesar mensaje de plugin: " + e.getMessage());
        }
    }

    /**
     * Maneja mensajes relacionados con economía
     */
    private void handleEconomyMessage(ServerConnection server, String messageType, DataInputStream input)
            throws IOException {

        switch (messageType) {
            case GET_PLAYER_DATA -> {
                String playerUuidStr = input.readUTF();
                UUID playerUuid = UUID.fromString(playerUuidStr);

                // Obtener datos del jugador de manera asíncrona
                Main.getInstance().getPlayerDataManager()
                        .getPlayer(playerUuid)
                        .thenAccept(playerOpt -> {
                            if (playerOpt.isPresent()) {
                                sendPlayerDataResponse(server, playerOpt.get());
                            } else {
                                MessageUtils.sendWarningMessage(logger,
                                        "Datos de jugador solicitados no encontrados: " + playerUuid);
                            }
                        });
            }

            case UPDATE_COINS -> {
                String playerUuidStr = input.readUTF();
                double newCoins = input.readDouble();
                UUID playerUuid = UUID.fromString(playerUuidStr);

                Main.getInstance().getPlayerDataManager()
                        .updatePlayerCoins(playerUuid, newCoins)
                        .thenAccept(success -> {
                            if (success && Main.getInstance().getConfigManager().isDebugMode()) {
                                MessageUtils.sendDebugMessage(logger,
                                        String.format("Monedas actualizadas para %s: %.2f", playerUuid, newCoins));
                            }
                        });
            }

            case UPDATE_GEMS -> {
                String playerUuidStr = input.readUTF();
                int newGems = input.readInt();
                UUID playerUuid = UUID.fromString(playerUuidStr);

                Main.getInstance().getPlayerDataManager()
                        .updatePlayerGems(playerUuid, newGems)
                        .thenAccept(success -> {
                            if (success && Main.getInstance().getConfigManager().isDebugMode()) {
                                MessageUtils.sendDebugMessage(logger,
                                        String.format("Gemas actualizadas para %s: %d", playerUuid, newGems));
                            }
                        });
            }

            case GET_TOP_PLAYERS -> {
                int limit = input.readInt();

                Main.getInstance().getPlayerDataManager()
                        .getTopPlayersByCoins(limit)
                        .thenAccept(topPlayers -> sendTopPlayersResponse(server, topPlayers));
            }
        }
    }

    /**
     * Maneja mensajes relacionados con rankup
     */
    private void handleRankupMessage(ServerConnection server, String messageType, DataInputStream input)
            throws IOException {

        switch (messageType) {
            case UPDATE_RANK -> {
                String playerUuidStr = input.readUTF();
                String newRank = input.readUTF();
                UUID playerUuid = UUID.fromString(playerUuidStr);

                // Actualizar rank en la base de datos
                CompletableFuture.runAsync(() -> {
                    try {
                        Main.getInstance().getDatabaseManager().executeUpdate(
                                "UPDATE grivyzom_players SET rank_id = ? WHERE uuid = ?",
                                newRank, playerUuid.toString()
                        );

                        // Actualizar cache si el jugador está online
                        Optional<Player> player = this.server.getPlayer(playerUuid);
                        if (player.isPresent()) {
                            Main.getInstance().getPlayerDataManager()
                                    .getPlayer(playerUuid)
                                    .thenAccept(playerOpt -> {
                                        if (playerOpt.isPresent()) {
                                            playerOpt.get().setRankId(newRank);
                                        }
                                    });
                        }

                        if (Main.getInstance().getConfigManager().isDebugMode()) {
                            MessageUtils.sendDebugMessage(logger,
                                    String.format("Rank actualizado para %s: %s", playerUuid, newRank));
                        }

                    } catch (Exception e) {
                        MessageUtils.sendErrorMessage(logger,
                                "Error al actualizar rank: " + e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Maneja mensajes relacionados con PvP
     */
    private void handlePvPMessage(ServerConnection server, String messageType, DataInputStream input)
            throws IOException {

        switch (messageType) {
            case SYNC_PLAYER_DATA -> {
                String playerUuidStr = input.readUTF();
                UUID playerUuid = UUID.fromString(playerUuidStr);

                // Sincronizar datos del jugador desde el cache
                Main.getInstance().getPlayerDataManager()
                        .getPlayer(playerUuid)
                        .thenAccept(playerOpt -> {
                            if (playerOpt.isPresent()) {
                                sendPlayerDataResponse(server, playerOpt.get());
                            }
                        });
            }
        }
    }

    /**
     * Envía respuesta con datos del jugador
     */
    private void sendPlayerDataResponse(ServerConnection server, GrivyzomPlayer player) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(stream);

            output.writeUTF(PLAYER_DATA_RESPONSE);
            output.writeUTF(player.getUuid().toString());
            output.writeUTF(player.getUsername());
            output.writeUTF(player.getDisplayName());
            output.writeDouble(player.getCoins());
            output.writeInt(player.getGems());
            output.writeUTF(player.getRankId());
            output.writeLong(player.getTotalPlaytime());
            output.writeBoolean(player.isOnline());

            if (player.getLastServer() != null) {
                output.writeUTF(player.getLastServer());
            } else {
                output.writeUTF("");
            }

            server.sendPluginMessage(GRIVYZOM_CHANNEL, stream.toByteArray());

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger,
                    "Error al enviar respuesta de datos del jugador: " + e.getMessage());
        }
    }

    /**
     * Envía respuesta con top de jugadores
     */
    private void sendTopPlayersResponse(ServerConnection server, java.util.List<GrivyzomPlayer> topPlayers) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(stream);

            output.writeUTF("TOP_PLAYERS_RESPONSE");
            output.writeInt(topPlayers.size());

            for (GrivyzomPlayer player : topPlayers) {
                output.writeUTF(player.getUuid().toString());
                output.writeUTF(player.getUsername());
                output.writeDouble(player.getCoins());
                output.writeInt(player.getGems());
                output.writeUTF(player.getRankId());
            }

            server.sendPluginMessage(ECONOMY_CHANNEL, stream.toByteArray());

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger,
                    "Error al enviar respuesta de top jugadores: " + e.getMessage());
        }
    }

    /**
     * Envía un mensaje a todos los servidores conectados
     */
    public void broadcastMessage(String messageType, String... data) {
        server.getAllServers().forEach(serverInfo -> {
            serverInfo.getPlayersConnected().stream().findFirst().ifPresent(player -> {
                try {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(stream);

                    output.writeUTF(messageType);
                    for (String datum : data) {
                        output.writeUTF(datum);
                    }

                    player.getCurrentServer().ifPresent(connection ->
                            connection.sendPluginMessage(GRIVYZOM_CHANNEL, stream.toByteArray()));

                } catch (IOException e) {
                    MessageUtils.sendErrorMessage(logger,
                            "Error al enviar mensaje broadcast: " + e.getMessage());
                }
            });
        });
    }

    /**
     * Notifica a todos los servidores sobre actualización de jugador
     */
    public void notifyPlayerUpdate(UUID playerUuid, String updateType) {
        broadcastMessage("PLAYER_UPDATE", playerUuid.toString(), updateType);
    }

    /**
     * Envía notificación de mantenimiento a todos los servidores
     */
    public void notifyMaintenance(boolean isStarting) {
        broadcastMessage("MAINTENANCE", isStarting ? "START" : "END");
    }
}