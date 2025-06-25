package gc.grivyzom.grivyzomCore.database;

import gc.grivyzom.grivyzomCore.config.ConfigManager;
import gc.grivyzom.grivyzomCore.config.DatabaseConfigManager;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    private final DatabaseConfigManager.DatabaseConfig dbConfig;
    private final Logger logger;
    private final ScheduledExecutorService executor;

    private Connection connection;
    private boolean isConnected = false;

    public DatabaseManager(DatabaseConfigManager.DatabaseConfig dbConfig, Logger logger) {
        this.dbConfig = dbConfig;
        this.logger = logger;
        this.executor = Executors.newScheduledThreadPool(2);
    }

    /**
     * Inicializa la conexión a la base de datos (SIN crear tablas)
     */
    public void initialize() {
        MessageUtils.sendInfoMessage(logger, "Iniciando conexión a la base de datos...");

        try {
            // Registrar el driver de MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establecer conexión
            connection = DriverManager.getConnection(
                    dbConfig.getJdbcUrl(),
                    dbConfig.getUsername(),
                    dbConfig.getPassword()
            );

            // Configurar la conexión
            connection.setAutoCommit(true);

            isConnected = true;
            MessageUtils.sendSuccessMessage(logger, "✅ Conexión a la base de datos establecida exitosamente");

            // Probar la conexión
            testConnection();

            // Iniciar monitoreo de conexión
            startConnectionMonitoring();

            MessageUtils.sendInfoMessage(logger, "🔍 Conexión verificada y monitoreo iniciado");

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al conectar con la base de datos: " + e.getMessage());
            throw new RuntimeException("No se pudo establecer la conexión a la base de datos", e);
        }
    }

    /**
     * Prueba la conexión a la base de datos
     */
    private void testConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT 1");
            if (rs.next()) {
                MessageUtils.sendSuccessMessage(logger, "🔗 Prueba de conexión exitosa");
            }
        }
    }

    private static final String CREATE_PLAYERS_TABLE = """
    CREATE TABLE IF NOT EXISTS grivyzom_players (
        uuid VARCHAR(36) PRIMARY KEY,
        username VARCHAR(16) NOT NULL,
        first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        is_online BOOLEAN DEFAULT FALSE,
        playtime BIGINT DEFAULT 0,
        last_ip VARCHAR(15),
        INDEX idx_username (username),
        INDEX idx_last_join (last_join),
        INDEX idx_is_online (is_online)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    """;

    private static final String CREATE_SERVERS_TABLE = """
    CREATE TABLE IF NOT EXISTS grivyzom_servers (
        server_id VARCHAR(32) PRIMARY KEY,
        server_name VARCHAR(64) NOT NULL,
        server_type VARCHAR(32) NOT NULL,
        ip_address VARCHAR(15),
        port INT,
        max_players INT DEFAULT 100,
        current_players INT DEFAULT 0,
        status ENUM('ONLINE', 'OFFLINE', 'MAINTENANCE') DEFAULT 'OFFLINE',
        last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_status (status),
        INDEX idx_server_type (server_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    """;

    private static final String CREATE_PLUGIN_DATA_TABLE = """
    CREATE TABLE IF NOT EXISTS grivyzom_plugin_data (
        id INT AUTO_INCREMENT PRIMARY KEY,
        plugin_name VARCHAR(64) NOT NULL,
        player_uuid VARCHAR(36),
        data_key VARCHAR(128) NOT NULL,
        data_value LONGTEXT,
        data_type ENUM('STRING', 'INT', 'LONG', 'DOUBLE', 'BOOLEAN', 'JSON') DEFAULT 'STRING',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY unique_plugin_player_key (plugin_name, player_uuid, data_key),
        INDEX idx_plugin_name (plugin_name),
        INDEX idx_player_uuid (player_uuid),
        FOREIGN KEY (player_uuid) REFERENCES grivyzom_players(uuid) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    """;

    public DatabaseManager(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        this.executor = Executors.newScheduledThreadPool(2);
    }

    /**
     * Inicializa la conexión a la base de datos
     */
    public void initialize() {
        MessageUtils.sendInfoMessage(logger, "Iniciando conexión a la base de datos...");

        try {
            ConfigManager.DatabaseConfig dbConfig = configManager.getDatabaseConfig();

            // Registrar el driver de MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establecer conexión
            connection = DriverManager.getConnection(
                dbConfig.getJdbcUrl(),
                dbConfig.getUsername(),
                dbConfig.getPassword()
            );

            // Configurar la conexión
            connection.setAutoCommit(true);

            isConnected = true;
            MessageUtils.sendSuccessMessage(logger, "Conexión a la base de datos establecida");

            // Crear tablas si no existen
            createTables();

            // Iniciar monitoreo de conexión
            startConnectionMonitoring();

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al conectar con la base de datos: " + e.getMessage());
            throw new RuntimeException("No se pudo establecer la conexión a la base de datos", e);
        }
    }

    /**
     * Crea las tablas necesarias en la base de datos
     */
    private void createTables() {
        try {
            MessageUtils.sendInfoMessage(logger, "Creando tablas de la base de datos...");

            executeUpdate(CREATE_PLAYERS_TABLE);
            executeUpdate(CREATE_SERVERS_TABLE);
            executeUpdate(CREATE_PLUGIN_DATA_TABLE);

            MessageUtils.sendSuccessMessage(logger, "Tablas de la base de datos creadas/verificadas correctamente");

        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al crear las tablas: " + e.getMessage());
            throw new RuntimeException("No se pudieron crear las tablas de la base de datos", e);
        }
    }

    /**
     * Inicia el monitoreo de la conexión a la base de datos
     */
    private void startConnectionMonitoring() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                if (!isConnectionValid()) {
                    MessageUtils.sendWarningMessage(logger, "Conexión a la base de datos perdida. Intentando reconectar...");
                    reconnect();
                }
            } catch (Exception e) {
                MessageUtils.sendErrorMessage(logger, "Error en el monitoreo de la base de datos: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        MessageUtils.sendInfoMessage(logger, "Monitoreo de conexión a la base de datos iniciado");
    }

    /**
     * Verifica si la conexión está activa
     */
    private boolean isConnectionValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Reconecta a la base de datos
     */
    private void reconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            ConfigManager.DatabaseConfig dbConfig = configManager.getDatabaseConfig();
            connection = DriverManager.getConnection(
                dbConfig.getJdbcUrl(),
                dbConfig.getUsername(),
                dbConfig.getPassword()
            );

            connection.setAutoCommit(true);
            isConnected = true;

            MessageUtils.sendSuccessMessage(logger, "Reconexión a la base de datos exitosa");

        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al reconectar a la base de datos: " + e.getMessage());
            isConnected = false;
        }
    }

    /**
     * Ejecuta una consulta de actualización (INSERT, UPDATE, DELETE)
     */
    public int executeUpdate(String sql, Object... parameters) throws SQLException {
        if (!isConnected || !isConnectionValid()) {
            throw new SQLException("No hay conexión activa a la base de datos");
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            return statement.executeUpdate();
        }
    }

    /**
     * Ejecuta una consulta de selección (SELECT)
     */
    public ResultSet executeQuery(String sql, Object... parameters) throws SQLException {
        if (!isConnected || !isConnectionValid()) {
            throw new SQLException("No hay conexión activa a la base de datos");
        }

        PreparedStatement statement = connection.prepareStatement(sql);
        setParameters(statement, parameters);
        return statement.executeQuery();
    }

    /**
     * Ejecuta una consulta de manera asíncrona
     */
    public CompletableFuture<ResultSet> executeQueryAsync(String sql, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeQuery(sql, parameters);
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger, "Error en consulta asíncrona: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Ejecuta una actualización de manera asíncrona
     */
    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeUpdate(sql, parameters);
            } catch (SQLException e) {
                MessageUtils.sendErrorMessage(logger, "Error en actualización asíncrona: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Establece los parámetros de un PreparedStatement
     */
    private void setParameters(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];
            if (param == null) {
                statement.setNull(i + 1, Types.NULL);
            } else if (param instanceof String) {
                statement.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                statement.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                statement.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                statement.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                statement.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof Timestamp) {
                statement.setTimestamp(i + 1, (Timestamp) param);
            } else {
                statement.setString(i + 1, param.toString());
            }
        }
    }

    /**
     * Ejecuta una transacción
     */
    public void executeTransaction(DatabaseTransaction transaction) throws SQLException {
        if (!isConnected || !isConnectionValid()) {
            throw new SQLException("No hay conexión activa a la base de datos");
        }

        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);
            transaction.execute(connection);
            connection.commit();

            if (configManager.isDebugMode()) {
                MessageUtils.sendDebugMessage(logger, "Transacción ejecutada exitosamente");
            }

        } catch (SQLException e) {
            connection.rollback();
            MessageUtils.sendErrorMessage(logger, "Error en transacción, rollback ejecutado: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Obtiene estadísticas de la base de datos
     */
    public DatabaseStats getStats() {
        try {
            DatabaseStats stats = new DatabaseStats();

            // Contar jugadores
            try (ResultSet rs = executeQuery("SELECT COUNT(*) FROM grivyzom_players")) {
                if (rs.next()) {
                    stats.totalPlayers = rs.getInt(1);
                }
            }

            // Contar jugadores online
            try (ResultSet rs = executeQuery("SELECT COUNT(*) FROM grivyzom_players WHERE is_online = TRUE")) {
                if (rs.next()) {
                    stats.onlinePlayers = rs.getInt(1);
                }
            }

            // Contar servidores
            try (ResultSet rs = executeQuery("SELECT COUNT(*) FROM grivyzom_servers")) {
                if (rs.next()) {
                    stats.totalServers = rs.getInt(1);
                }
            }

            // Contar datos de plugins
            try (ResultSet rs = executeQuery("SELECT COUNT(*) FROM grivyzom_plugin_data")) {
                if (rs.next()) {
                    stats.pluginDataEntries = rs.getInt(1);
                }
            }

            return stats;

        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al obtener estadísticas de la base de datos: " + e.getMessage());
            return new DatabaseStats();
        }
    }

    /**
     * Cierra la conexión a la base de datos
     */
    public void close() {
        try {
            isConnected = false;

            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }

            if (connection != null && !connection.isClosed()) {
                connection.close();
                MessageUtils.sendInfoMessage(logger, "Conexión a la base de datos cerrada");
            }

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al cerrar la conexión a la base de datos: " + e.getMessage());
        }
    }

    // Getters
    public boolean isConnected() {
        return isConnected && isConnectionValid();
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Interfaz funcional para transacciones
     */
    @FunctionalInterface
    public interface DatabaseTransaction {
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Clase para estadísticas de la base de datos
     */
    public static class DatabaseStats {
        public int totalPlayers = 0;
        public int onlinePlayers = 0;
        public int totalServers = 0;
        public int pluginDataEntries = 0;

        @Override
        public String toString() {
            return String.format(
                "DatabaseStats{totalPlayers=%d, onlinePlayers=%d, totalServers=%d, pluginDataEntries=%d}",
                totalPlayers, onlinePlayers, totalServers, pluginDataEntries
            );
        }
    }
}