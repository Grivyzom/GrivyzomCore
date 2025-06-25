package gc.grivyzom.grivyzomCore.database;

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

            // Mostrar información de la base de datos
            showDatabaseInfo();

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

    /**
     * Muestra información detallada de la base de datos
     */
    private void showDatabaseInfo() {
        try {
            DatabaseInfo info = getDatabaseInfo();
            if (info != null) {
                MessageUtils.sendSeparator(logger);
                MessageUtils.sendInfoMessage(logger, "📊 Información de la Base de Datos:");
                MessageUtils.sendInfoMessage(logger, "  🗃️ Motor: " + info.getProductName() + " " + info.getProductVersion());
                MessageUtils.sendInfoMessage(logger, "  🔌 Driver: " + info.getDriverName() + " " + info.getDriverVersion());
                MessageUtils.sendInfoMessage(logger, "  🌐 Conexión: " + info.getHost() + ":" + info.getPort() + "/" + info.getDatabase());
                MessageUtils.sendSeparator(logger);
            }
        } catch (Exception e) {
            MessageUtils.sendWarningMessage(logger, "No se pudo obtener información detallada de la base de datos");
        }
    }

    /**
     * Inicia el monitoreo de la conexión a la base de datos
     */
    private void startConnectionMonitoring() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                if (!isConnectionValid()) {
                    MessageUtils.sendWarningMessage(logger, "⚠ Conexión a la base de datos perdida. Intentando reconectar...");
                    reconnect();
                }
            } catch (Exception e) {
                MessageUtils.sendErrorMessage(logger, "Error en el monitoreo de la base de datos: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        MessageUtils.sendInfoMessage(logger, "📊 Monitoreo de conexión a la base de datos iniciado");
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

            connection = DriverManager.getConnection(
                    dbConfig.getJdbcUrl(),
                    dbConfig.getUsername(),
                    dbConfig.getPassword()
            );

            connection.setAutoCommit(true);
            isConnected = true;

            MessageUtils.sendSuccessMessage(logger, "🔄 Reconexión a la base de datos exitosa");

        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "❌ Error al reconectar a la base de datos: " + e.getMessage());
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

            MessageUtils.sendDebugMessage(logger, "Transacción ejecutada exitosamente");

        } catch (SQLException e) {
            connection.rollback();
            MessageUtils.sendErrorMessage(logger, "Error en transacción, rollback ejecutado: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Obtiene información básica de la base de datos
     */
    public DatabaseInfo getDatabaseInfo() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            return new DatabaseInfo(
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    metaData.getDriverName(),
                    metaData.getDriverVersion(),
                    dbConfig.getHost(),
                    dbConfig.getPort(),
                    dbConfig.getDatabase()
            );
        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al obtener información de la base de datos: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verifica si una tabla existe en la base de datos
     */
    public boolean tableExists(String tableName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"});
            return tables.next();
        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al verificar existencia de tabla: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el número de tablas en la base de datos
     */
    public int getTableCount() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            int count = 0;
            while (tables.next()) {
                count++;
            }
            return count;
        } catch (SQLException e) {
            MessageUtils.sendErrorMessage(logger, "Error al contar tablas: " + e.getMessage());
            return 0;
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
                MessageUtils.sendInfoMessage(logger, "🔌 Conexión a la base de datos cerrada correctamente");
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

    public DatabaseConfigManager.DatabaseConfig getDbConfig() {
        return dbConfig;
    }

    /**
     * Interfaz funcional para transacciones
     */
    @FunctionalInterface
    public interface DatabaseTransaction {
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Clase para información de la base de datos
     */
    public static class DatabaseInfo {
        private final String productName;
        private final String productVersion;
        private final String driverName;
        private final String driverVersion;
        private final String host;
        private final int port;
        private final String database;

        public DatabaseInfo(String productName, String productVersion, String driverName,
                            String driverVersion, String host, int port, String database) {
            this.productName = productName;
            this.productVersion = productVersion;
            this.driverName = driverName;
            this.driverVersion = driverVersion;
            this.host = host;
            this.port = port;
            this.database = database;
        }

        // Getters
        public String getProductName() { return productName; }
        public String getProductVersion() { return productVersion; }
        public String getDriverName() { return driverName; }
        public String getDriverVersion() { return driverVersion; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }

        @Override
        public String toString() {
            return String.format(
                    "DatabaseInfo{product='%s %s', driver='%s %s', connection='%s:%d/%s'}",
                    productName, productVersion, driverName, driverVersion, host, port, database
            );
        }
    }
}