package gc.grivyzom.grivyzomCore.messaging;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gc.grivyzom.grivyzomCore.Main;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.io.*;

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

    // Tipos de mensajes básicos
    private static final String PING = "PING";
    private static final String PONG = "PONG";
    private static final String STATUS_REQUEST = "STATUS_REQUEST";
    private static final String STATUS_RESPONSE = "STATUS_RESPONSE";

    public PluginMessageManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /**
     * Registra los canales de mensajería
     */
    public void registerChannels() {
        try {
            server.getChannelRegistrar().register(GRIVYZOM_CHANNEL);
            server.getChannelRegistrar().register(ECONOMY_CHANNEL);
            server.getChannelRegistrar().register(RANKUP_CHANNEL);
            server.getChannelRegistrar().register(PVP_CHANNEL);

            // Registrar el listener de eventos
            server.getEventManager().register(Main.getInstance(), this);

            MessageUtils.sendSuccessMessage(logger, "📡 Canales de mensajería registrados correctamente");
            MessageUtils.sendInfoMessage(logger, "  🔗 grivyzom:core - Canal principal");
            MessageUtils.sendInfoMessage(logger, "  💰 grivyzom:economy - Canal de economía");
            MessageUtils.sendInfoMessage(logger, "  📈 grivyzom:rankup - Canal de rangos");
            MessageUtils.sendInfoMessage(logger, "  ⚔️ grivyzom:pvp - Canal de PvP");

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al registrar canales de mensajería: " + e.getMessage());
        }
    }

    /**
     * Desregistra los canales de mensajería
     */
    public void unregisterChannels() {
        try {
            server.getChannelRegistrar().unregister(GRIVYZOM_CHANNEL);
            server.getChannelRegistrar().unregister(ECONOMY_CHANNEL);
            server.getChannelRegistrar().unregister(RANKUP_CHANNEL);
            server.getChannelRegistrar().unregister(PVP_CHANNEL);

            MessageUtils.sendInfoMessage(logger, "📡 Canales de mensajería desregistrados");

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al desregistrar canales: " + e.getMessage());
        }
    }

    /**
     * Maneja los mensajes recibidos de otros plugins
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        ServerConnection serverConnection = (ServerConnection) event.getSource();
        MinecraftChannelIdentifier identifier = event.getIdentifier();

        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
            DataInputStream input = new DataInputStream(stream);

            String messageType = input.readUTF();

            // Log de debug si está habilitado
            if (Main.getInstance().getConfigManager().isDebugMode()) {
                MessageUtils.sendDebugMessage(logger,
                        String.format("📨 Mensaje recibido - Canal: %s, Tipo: %s, Servidor: %s",
                                identifier.getId(), messageType, serverConnection.getServerInfo().getName()));
            }

            // Procesar según el canal y tipo de mensaje
            handleMessage(serverConnection, identifier, messageType, input);

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger,
                    "❌ Error al procesar mensaje de plugin: " + e.getMessage());
        }
    }

    /**
     * Maneja los diferentes tipos de mensajes según el canal
     */
    private void handleMessage(ServerConnection serverConnection, MinecraftChannelIdentifier identifier,
                               String messageType, DataInputStream input) throws IOException {

        switch (messageType) {
            case PING -> handlePing(serverConnection, identifier);
            case STATUS_REQUEST -> handleStatusRequest(serverConnection, identifier);
            default -> {
                if (Main.getInstance().getConfigManager().isDebugMode()) {
                    MessageUtils.sendDebugMessage(logger,
                            "⚠️ Mensaje no reconocido: " + messageType + " en canal: " + identifier.getId());
                }
            }
        }
    }

    /**
     * Maneja mensajes PING (responde con PONG)
     */
    private void handlePing(ServerConnection serverConnection, MinecraftChannelIdentifier channel) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(stream);

            output.writeUTF(PONG);
            output.writeUTF("GrivyzomCore");
            output.writeLong(System.currentTimeMillis());

            serverConnection.sendPluginMessage(channel, stream.toByteArray());

            if (Main.getInstance().getConfigManager().isDebugMode()) {
                MessageUtils.sendDebugMessage(logger,
                        "🏓 PONG enviado a " + serverConnection.getServerInfo().getName());
            }

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al enviar PONG: " + e.getMessage());
        }
    }

    /**
     * Maneja solicitudes de estado del sistema
     */
    private void handleStatusRequest(ServerConnection serverConnection, MinecraftChannelIdentifier channel) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(stream);

            output.writeUTF(STATUS_RESPONSE);
            output.writeBoolean(Main.getInstance().getDatabaseManager().isConnected());
            output.writeLong(System.currentTimeMillis());
            output.writeInt(server.getPlayerCount());
            output.writeString("GrivyzomCore v0.1-SNAPSHOT");

            serverConnection.sendPluginMessage(channel, stream.toByteArray());

            MessageUtils.sendDebugMessage(logger,
                    "📊 Estado del sistema enviado a " + serverConnection.getServerInfo().getName());

        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al enviar estado: " + e.getMessage());
        }
    }

    /**
     * Envía un mensaje PING a todos los servidores conectados
     */
    public void pingAllServers() {
        server.getAllServers().forEach(serverInfo -> {
            serverInfo.getPlayersConnected().stream().findFirst().ifPresent(player -> {
                player.getCurrentServer().ifPresent(connection -> {
                    try {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        DataOutputStream output = new DataOutputStream(stream);

                        output.writeUTF(PING);
                        output.writeLong(System.currentTimeMillis());

                        connection.sendPluginMessage(GRIVYZOM_CHANNEL, stream.toByteArray());

                    } catch (IOException e) {
                        MessageUtils.sendErrorMessage(logger,
                                "❌ Error al enviar PING a " + serverInfo.getName() + ": " + e.getMessage());
                    }
                });
            });
        });

        MessageUtils.sendDebugMessage(logger, "🏓 PING enviado a todos los servidores");
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
                            "❌ Error al enviar mensaje broadcast: " + e.getMessage());
                }
            });
        });

        if (Main.getInstance().getConfigManager().isDebugMode()) {
            MessageUtils.sendDebugMessage(logger, "📡 Mensaje broadcast enviado: " + messageType);
        }
    }

    /**
     * Envía notificación de mantenimiento a todos los servidores
     */
    public void notifyMaintenance(boolean isStarting) {
        String status = isStarting ? "START" : "END";
        broadcastMessage("MAINTENANCE", status, String.valueOf(System.currentTimeMillis()));

        MessageUtils.sendInfoMessage(logger,
                "🔧 Notificación de mantenimiento enviada: " + (isStarting ? "INICIANDO" : "FINALIZANDO"));
    }

    /**
     * Solicita el estado de todos los servidores
     */
    public void requestStatusFromAllServers() {
        server.getAllServers().forEach(serverInfo -> {
            serverInfo.getPlayersConnected().stream().findFirst().ifPresent(player -> {
                player.getCurrentServer().ifPresent(connection -> {
                    try {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        DataOutputStream output = new DataOutputStream(stream);

                        output.writeUTF(STATUS_REQUEST);
                        output.writeLong(System.currentTimeMillis());

                        connection.sendPluginMessage(GRIVYZOM_CHANNEL, stream.toByteArray());

                    } catch (IOException e) {
                        MessageUtils.sendErrorMessage(logger,
                                "❌ Error al solicitar estado de " + serverInfo.getName() + ": " + e.getMessage());
                    }
                });
            });
        });

        MessageUtils.sendDebugMessage(logger, "📊 Solicitud de estado enviada a todos los servidores");
    }

    /**
     * Obtiene estadísticas de los canales registrados
     */
    public ChannelStats getChannelStats() {
        return new ChannelStats(
                server.getChannelRegistrar().getChannelsForPlugin(Main.getInstance()).size(),
                server.getAllServers().size(),
                server.getPlayerCount()
        );
    }

    /**
     * Clase para estadísticas de canales
     */
    public static class ChannelStats {
        private final int registeredChannels;
        private final int connectedServers;
        private final int totalPlayers;

        public ChannelStats(int registeredChannels, int connectedServers, int totalPlayers) {
            this.registeredChannels = registeredChannels;
            this.connectedServers = connectedServers;
            this.totalPlayers = totalPlayers;
        }

        public int getRegisteredChannels() { return registeredChannels; }
        public int getConnectedServers() { return connectedServers; }
        public int getTotalPlayers() { return totalPlayers; }

        @Override
        public String toString() {
            return String.format(
                    "ChannelStats{channels=%d, servers=%d, players=%d}",
                    registeredChannels, connectedServers, totalPlayers
            );
        }
    }
}