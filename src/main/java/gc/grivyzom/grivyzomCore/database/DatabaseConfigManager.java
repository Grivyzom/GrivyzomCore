package gc.grivyzom.grivyzomCore.config;

import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestor específico para la configuración de base de datos usando YAML
 */
public class DatabaseConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private final File databaseConfigFile;
    private Map<String, Object> databaseConfig;

    // Configuración por defecto para database.yml
    private static final String DEFAULT_DATABASE_CONFIG = """
            # ═══════════════════════════════════════════════════════════════
            #                    GrivyzomCore Database Configuration
            # ═══════════════════════════════════════════════════════════════
            
            # Configuración de conexión MySQL
            connection:
              host: "localhost"
              port: 3306
              database: "grivyzom_network"
              username: "grivyzom_user"
              password: "your_password_here"
              
            # Configuración del pool de conexiones
            pool:
              max_pool_size: 10
              min_idle: 5
              connection_timeout: 30000
              idle_timeout: 600000
              max_lifetime: 1800000
              
            # Configuraciones adicionales de conexión
            options:
              use_ssl: false
              server_timezone: "UTC"
              allow_public_key_retrieval: true
              use_unicode: true
              character_encoding: "utf8mb4"
              
            # Configuraciones de rendimiento
            performance:
              cache_prep_stmts: true
              prep_stmt_cache_size: 250
              prep_stmt_cache_sql_limit: 2048
              use_server_prep_stmts: true
            """;

    public DatabaseConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.databaseConfigFile = dataDirectory.resolve("database.yml").toFile();
    }

    /**
     * Carga la configuración de base de datos
     */
    public void loadDatabaseConfig() {
        try {
            // Crear directorio si no existe
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                MessageUtils.sendInfoMessage(logger, "Directorio de datos creado: " + dataDirectory);
            }

            // Crear archivo de configuración si no existe
            if (!databaseConfigFile.exists()) {
                createDefaultDatabaseConfig();
                MessageUtils.sendInfoMessage(logger, "Archivo database.yml creado con valores por defecto");
            }

            // Cargar configuración usando SnakeYAML
            Yaml yaml = new Yaml();
            try (FileInputStream inputStream = new FileInputStream(databaseConfigFile)) {
                databaseConfig = yaml.load(inputStream);
            }

            MessageUtils.sendSuccessMessage(logger, "Configuración de base de datos cargada desde database.yml");

            // Validar configuración
            validateDatabaseConfig();

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al cargar la configuración de base de datos: " + e.getMessage());
            throw new RuntimeException("No se pudo cargar la configuración de base de datos", e);
        }
    }

    /**
     * Crea el archivo database.yml por defecto
     */
    private void createDefaultDatabaseConfig() throws IOException {
        try (FileWriter writer = new FileWriter(databaseConfigFile)) {
            writer.write(DEFAULT_DATABASE_CONFIG);
        }
    }

    /**
     * Valida la configuración de base de datos
     */
    private void validateDatabaseConfig() {
        boolean hasErrors = false;

        // Validar configuración de conexión
        if (getConnectionString("host").isEmpty()) {
            MessageUtils.sendErrorMessage(logger, "La configuración 'connection.host' no puede estar vacía");
            hasErrors = true;
        }

        if (getConnectionString("database").isEmpty()) {
            MessageUtils.sendErrorMessage(logger, "La configuración 'connection.database' no puede estar vacía");
            hasErrors = true;
        }

        if (getConnectionString("username").isEmpty()) {
            MessageUtils.sendErrorMessage(logger, "La configuración 'connection.username' no puede estar vacía");
            hasErrors = true;
        }

        // Validar que la contraseña no sea la por defecto
        if (getConnectionString("password").equals("your_password_here")) {
            MessageUtils.sendWarningMessage(logger, "⚠ Por favor, cambia la contraseña por defecto en database.yml");
        }

        // Validar configuraciones numéricas
        if (getConnectionInt("port") <= 0 || getConnectionInt("port") > 65535) {
            MessageUtils.sendErrorMessage(logger, "El puerto debe estar entre 1 y 65535");
            hasErrors = true;
        }

        if (getPoolInt("max_pool_size") <= 0) {
            MessageUtils.sendErrorMessage(logger, "max_pool_size debe ser mayor que 0");
            hasErrors = true;
        }

        if (hasErrors) {
            throw new RuntimeException("La configuración de base de datos tiene errores críticos");
        }

        MessageUtils.sendSuccessMessage(logger, "Configuración de base de datos validada correctamente");
    }

    /**
     * Recarga la configuración de base de datos
     */
    public void reloadDatabaseConfig() {
        try {
            Yaml yaml = new Yaml();
            try (FileInputStream inputStream = new FileInputStream(databaseConfigFile)) {
                databaseConfig = yaml.load(inputStream);
            }
            validateDatabaseConfig();
            MessageUtils.sendSuccessMessage(logger, "Configuración de base de datos recargada");
        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al recargar la configuración de base de datos: " + e.getMessage());
        }
    }

    // Métodos para obtener valores de conexión
    @SuppressWarnings("unchecked")
    private Map<String, Object> getConnectionConfig() {
        return (Map<String, Object>) databaseConfig.getOrDefault("connection", new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPoolConfig() {
        return (Map<String, Object>) databaseConfig.getOrDefault("pool", new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOptionsConfig() {
        return (Map<String, Object>) databaseConfig.getOrDefault("options", new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPerformanceConfig() {
        return (Map<String, Object>) databaseConfig.getOrDefault("performance", new LinkedHashMap<>());
    }

    public String getConnectionString(String key) {
        return (String) getConnectionConfig().getOrDefault(key, "");
    }

    public int getConnectionInt(String key) {
        Object value = getConnectionConfig().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public int getPoolInt(String key) {
        Object value = getPoolConfig().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public long getPoolLong(String key) {
        Object value = getPoolConfig().get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    public boolean getOptionsBoolean(String key) {
        Object value = getOptionsConfig().get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    public String getOptionsString(String key) {
        return (String) getOptionsConfig().getOrDefault(key, "");
    }

    public boolean getPerformanceBoolean(String key) {
        Object value = getPerformanceConfig().get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    public int getPerformanceInt(String key) {
        Object value = getPerformanceConfig().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * Construye la URL de conexión JDBC con todas las opciones
     */
    public String buildJdbcUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://")
                .append(getConnectionString("host"))
                .append(":")
                .append(getConnectionInt("port"))
                .append("/")
                .append(getConnectionString("database"));

        // Añadir parámetros de conexión
        url.append("?useSSL=").append(getOptionsBoolean("use_ssl"));
        url.append("&serverTimezone=").append(getOptionsString("server_timezone"));
        url.append("&allowPublicKeyRetrieval=").append(getOptionsBoolean("allow_public_key_retrieval"));
        url.append("&useUnicode=").append(getOptionsBoolean("use_unicode"));
        url.append("&characterEncoding=").append(getOptionsString("character_encoding"));

        // Añadir parámetros de rendimiento
        url.append("&cachePrepStmts=").append(getPerformanceBoolean("cache_prep_stmts"));
        url.append("&prepStmtCacheSize=").append(getPerformanceInt("prep_stmt_cache_size"));
        url.append("&prepStmtCacheSqlLimit=").append(getPerformanceInt("prep_stmt_cache_sql_limit"));
        url.append("&useServerPrepStmts=").append(getPerformanceBoolean("use_server_prep_stmts"));

        return url.toString();
    }

    /**
     * Crea un objeto DatabaseConfig con toda la información
     */
    public DatabaseConfig createDatabaseConfig() {
        return new DatabaseConfig(
                getConnectionString("host"),
                getConnectionInt("port"),
                getConnectionString("database"),
                getConnectionString("username"),
                getConnectionString("password"),
                getPoolInt("max_pool_size"),
                getPoolInt("min_idle"),
                getPoolLong("connection_timeout"),
                getPoolLong("idle_timeout"),
                getPoolLong("max_lifetime"),
                buildJdbcUrl()
        );
    }

    /**
     * Muestra información de la configuración actual
     */
    public void printDatabaseInfo() {
        MessageUtils.sendSeparator(logger);
        MessageUtils.sendInfoMessage(logger, "📊 Información de Base de Datos:");
        MessageUtils.sendInfoMessage(logger, "  🏠 Host: " + getConnectionString("host") + ":" + getConnectionInt("port"));
        MessageUtils.sendInfoMessage(logger, "  🗄️ Base de datos: " + getConnectionString("database"));
        MessageUtils.sendInfoMessage(logger, "  👤 Usuario: " + getConnectionString("username"));
        MessageUtils.sendInfoMessage(logger, "  🔗 Pool máximo: " + getPoolInt("max_pool_size") + " conexiones");
        MessageUtils.sendInfoMessage(logger, "  ⚙️ SSL: " + (getOptionsBoolean("use_ssl") ? "Habilitado" : "Deshabilitado"));
        MessageUtils.sendSeparator(logger);
    }

    /**
     * Clase para encapsular toda la configuración de la base de datos
     */
    public static class DatabaseConfig {
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;
        private final int maxPoolSize;
        private final int minIdle;
        private final long connectionTimeout;
        private final long idleTimeout;
        private final long maxLifetime;
        private final String jdbcUrl;

        public DatabaseConfig(String host, int port, String database, String username,
                              String password, int maxPoolSize, int minIdle,
                              long connectionTimeout, long idleTimeout, long maxLifetime, String jdbcUrl) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
            this.maxPoolSize = maxPoolSize;
            this.minIdle = minIdle;
            this.connectionTimeout = connectionTimeout;
            this.idleTimeout = idleTimeout;
            this.maxLifetime = maxLifetime;
            this.jdbcUrl = jdbcUrl;
        }

        // Getters
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getMinIdle() { return minIdle; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public long getIdleTimeout() { return idleTimeout; }
        public long getMaxLifetime() { return maxLifetime; }
        public String getJdbcUrl() { return jdbcUrl; }
    }
}