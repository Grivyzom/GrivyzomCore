package gc.grivyzom.grivyzomCore.managers;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import gc.grivyzom.grivyzomCore.database.DatabaseManager;
import gc.grivyzom.grivyzomCore.models.GrivyzomPlayer;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    // Cache de jugadores en memoria
    private final Map<UUID, GrivyzomPlayer> playerCache = new ConcurrentHashMap<>();

    // Queries SQL
    private static final String SELECT_PLAYER = """
            SELECT uuid, username, display_name, first_join, last_join, last_server, 
                   total_playtime, coins, gems, rank_id, permissions, data, is_online 
            FROM grivyzom_players WHERE uuid = ?
            """;

    private static final String INSERT_PLAYER = """
            INSERT INTO grivyzom_players (uuid, username, display_name, first_join, last_join, is_online) 
            VALUES (?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
                username = VALUES(username), 
                display_name = VALUES(display_name), 
                last_join = VALUES(last_join), 
                is_online = VALUES(is_online)
            """;

    private static final String UPDATE_PLAYER_ONLINE_STATUS = """
            UPDATE grivyzom_players SET is_online = ?, last_join = ? WHERE uuid = ?
            """;

    private static final String UPDATE_PLAYER_SERVER = """
            UPDATE grivyzom_players SET last_server = ? WHERE uuid = ?
            """;

    private static final String UPDATE_PLAYER_DATA = """
            UPDATE grivyzom_players SET coins = ?, gems = ?, rank_id = ?, 
                   permissions = ?, data = ?, total_playtime = ? WHERE uuid = ?
            """;

    public PlayerDataManager(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /**
     * Maneja el evento de login del jugador
     */
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();

        CompletableFuture.runAsync(() -> {
            try {
                GrivyzomPlayer grivyzomPlayer = loadOrCreatePlayer(player);
                playerCache.put(player.getUniqueId(), grivyzomPlayer);

                // Actualizar estado online
                updatePlayerOnlineStatus(player.getUniqueId(), true);

                MessageUtils.sendInfoMessage(logger,
                        String.format("Jugador %s (%s) conectado y cargado",
                                player.getUsername(), player.getUniqueId()));

            } catch (Exception e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al cargar datos del jugador %s: %s",
                                player.getUsername(), e.getMessage()));
            }
        });
    }

    /**
     * Maneja el evento de desconexión del jugador
     */
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        CompletableFuture.runAsync(() -> {
            try {
                // Guardar datos antes de desconectar
                GrivyzomPlayer grivyzomPlayer = playerCache.get(player.getUniqueId());
                if (grivyzomPlayer != null) {
                    savePlayerData(grivyzomPlayer);
                }

                // Actualizar estado offline
                updatePlayerOnlineStatus(player.getUniqueId(), false);

                // Remover del cache
                playerCache.remove(player.getUniqueId());

                MessageUtils.sendInfoMessage(logger,
                        String.format("Jugador %s desconectado y guardado", player.getUsername()));

            } catch (Exception e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al guardar datos del jugador %s: %s",
                                player.getUsername(), e.getMessage()));
            }
        });
    }

    /**
     * Maneja el cambio de servidor del jugador
     */
    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        CompletableFuture.runAsync(() -> {
            try {
                updatePlayerServer(player.getUniqueId(), serverName);

                GrivyzomPlayer grivyzomPlayer = playerCache.get(player.getUniqueId());
                if (grivyzomPlayer != null) {
                    grivyzomPlayer.setLastServer(serverName);
                }

            } catch (Exception e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al actualizar servidor del jugador %s: %s",
                                player.getUsername(), e.getMessage()));
            }
        });
    }

    /**
     * Carga o crea un jugador desde la base de datos
     */
    private GrivyzomPlayer loadOrCreatePlayer(Player player) throws SQLException {
        try (ResultSet rs = databaseManager.executeQuery(SELECT_PLAYER, player.getUniqueId().toString())) {

            if (rs.next()) {
                // Jugador existe, cargar datos
                return GrivyzomPlayer.fromResultSet(rs);
            } else {
                // Jugador nuevo, crear entrada
                Timestamp now = new Timestamp(System.currentTimeMillis());

                databaseManager.executeUpdate(INSERT_PLAYER,
                        player.getUniqueId().toString(),
                        player.getUsername(),
                        player.getUsername(), // display_name por defecto
                        now, // first_join
                        now, // last_join
                        true  // is_online
                );

                // Crear objeto GrivyzomPlayer
                GrivyzomPlayer newPlayer = new GrivyzomPlayer(
                        player.getUniqueId(),
                        player.getUsername(),
                        player.getUsername(),
                        now,
                        now,
                        null, // last_server
                        0L,   // total_playtime
                        0.0,  // coins
                        0,    // gems
                        "default", // rank_id
                        null, // permissions
                        null, // data
                        true  // is_online
                );

                MessageUtils.sendInfoMessage(logger,
                        String.format("Nuevo jugador registrado: %s", player.getUsername()));

                return newPlayer;
            }
        }
    }

    /**
     * Actualiza el estado online del jugador
     */
    private void updatePlayerOnlineStatus(UUID uuid, boolean online) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        databaseManager.executeUpdate(UPDATE_PLAYER_ONLINE_STATUS, online, now, uuid.toString());
    }

    /**
     * Actualiza el servidor del jugador
     */
    private void updatePlayerServer(UUID uuid, String serverName) throws SQLException {
        databaseManager.executeUpdate(UPDATE_PLAYER_SERVER, serverName, uuid.toString());
    }

    /**
     * Guarda los datos del jugador en la base de datos
     */
    public void savePlayerData(GrivyzomPlayer player) throws SQLException {
        databaseManager.executeUpdate(UPDATE_PLAYER_DATA,
                player.getCoins(),
                player.getGems(),
                player.getRankId(),
                player.getPermissions(),
                player.getData(),
                player.getTotalPlaytime(),
                player.getUuid().toString()
        );
    }

    /**
     * Guarda todos los datos de jugadores online
     */
    public CompletableFuture<Void> saveAllOnlinePlayers() {
        return CompletableFuture.runAsync(() -> {
            int saved = 0;
            int errors = 0;

            for (GrivyzomPlayer player : playerCache.values()) {
                try {
                    savePlayerData(player);
                    saved++;
                } catch (SQLException e) {
                    errors++;
                    MessageUtils.sendErrorMessage(logger,
                            String.format("Error al guardar jugador %s: %s",
                                    player.getUsername(), e.getMessage()));
                }
            }

            MessageUtils.sendInfoMessage(logger,
                    String.format("Guardado automático completado: %d guardados, %d errores", saved, errors));
        });
    }

    /**
     * Obtiene un jugador del cache o de la base de datos
     */
    public CompletableFuture<Optional<GrivyzomPlayer>> getPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Primero buscar en cache
            GrivyzomPlayer cached = playerCache.get(uuid);
            if (cached != null) {
                return Optional.of(cached);
            }

            // Si no está en cache, buscar en base de datos
            try (ResultSet rs = databaseManager.executeQuery(SELECT_PLAYER, uuid.toString())) {
                if (rs.next()) {
                    GrivyzomPlayer player = GrivyzomPlayer.fromResultSet(rs);
                    return Optional.of(player);
                }
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al obtener jugador %s: %s", uuid, e.getMessage()));
            }

            return Optional.empty();
        });
    }

    /**
     * Obtiene un jugador por nombre de usuario
     */
    public CompletableFuture<Optional<GrivyzomPlayer>> getPlayerByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            // Buscar en cache primero
            for (GrivyzomPlayer player : playerCache.values()) {
                if (player.getUsername().equalsIgnoreCase(username)) {
                    return Optional.of(player);
                }
            }

            // Buscar en base de datos
            try (ResultSet rs = databaseManager.executeQuery(
                    SELECT_PLAYER.replace("WHERE uuid = ?", "WHERE username = ?"),
                    username)) {

                if (rs.next()) {
                    GrivyzomPlayer player = GrivyzomPlayer.fromResultSet(rs);
                    return Optional.of(player);
                }
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al obtener jugador por nombre %s: %s", username, e.getMessage()));
            }

            return Optional.empty();
        });
    }

    /**
     * Actualiza las monedas de un jugador
     */
    public CompletableFuture<Boolean> updatePlayerCoins(UUID uuid, double coins) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                databaseManager.executeUpdate(
                        "UPDATE grivyzom_players SET coins = ? WHERE uuid = ?",
                        coins, uuid.toString()
                );

                // Actualizar cache si está presente
                GrivyzomPlayer cached = playerCache.get(uuid);
                if (cached != null) {
                    cached.setCoins(coins);
                }

                return true;
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al actualizar monedas del jugador %s: %s", uuid, e.getMessage()));
                return false;
            }
        });
    }

    /**
     * Actualiza las gemas de un jugador
     */
    public CompletableFuture<Boolean> updatePlayerGems(UUID uuid, int gems) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                databaseManager.executeUpdate(
                        "UPDATE grivyzom_players SET gems = ? WHERE uuid = ?",
                        gems, uuid.toString()
                );

                // Actualizar cache si está presente
                GrivyzomPlayer cached = playerCache.get(uuid);
                if (cached != null) {
                    cached.setGems(gems);
                }

                return true;
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger,
                        String.format("Error al actualizar gemas del jugador %s: %s", uuid, e.getMessage()));
                return false;
            }
        });
    }

    /**
     * Obtiene el top de jugadores por monedas
     */
    public CompletableFuture<java.util.List<GrivyzomPlayer>> getTopPlayersByCoins(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<GrivyzomPlayer> topPlayers = new java.util.ArrayList<>();

            try (ResultSet rs = databaseManager.executeQuery(
                    SELECT_PLAYER.replace("WHERE uuid = ?", "") +
                            " ORDER BY coins DESC LIMIT ?", limit)) {

                while (rs.next()) {
                    topPlayers.add(GrivyzomPlayer.fromResultSet(rs));
                }
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger,
                        "Error al obtener top de jugadores por monedas: " + e.getMessage());
            }

            return topPlayers;
        });
    }

    /**
     * Obtiene estadísticas de jugadores
     */
    public PlayerStats getPlayerStats() {
        return new PlayerStats(
                playerCache.size(),
                playerCache.values().stream().mapToDouble(GrivyzomPlayer::getCoins).sum(),
                playerCache.values().stream().mapToInt(GrivyzomPlayer::getGems).sum()
        );
    }

    // Getters
    public Map<UUID, GrivyzomPlayer> getPlayerCache() {
        return new ConcurrentHashMap<>(playerCache);
    }

    /**
     * Clase para estadísticas de jugadores
     */
    public static class PlayerStats {
        private final int onlinePlayers;
        private final double totalCoins;
        private final int totalGems;

        public PlayerStats(int onlinePlayers, double totalCoins, int totalGems) {
            this.onlinePlayers = onlinePlayers;
            this.totalCoins = totalCoins;
            this.totalGems = totalGems;
        }

        public int getOnlinePlayers() { return onlinePlayers; }
        public double getTotalCoins() { return totalCoins; }
        public int getTotalGems() { return totalGems; }

        @Override
        public String toString() {
            return String.format(
                    "PlayerStats{onlinePlayers=%d, totalCoins=%.2f, totalGems=%d}",
                    onlinePlayers, totalCoins, totalGems
            );
        }
    }
}