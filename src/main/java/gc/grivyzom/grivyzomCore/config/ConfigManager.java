package gc.grivyzom.grivyzomCore.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private final File configFile;
    private Toml config;

    // Configuración por defecto (SIN configuración de base de datos)
    private static final String DEFAULT_CONFIG = """
            # ═══════════════════════════════════════════════════════════════
            #                        GrivyzomCore Configuration
            # ═══════════════════════════════════════════════════════════════
            
            [plugin]
            # Configuraciones generales del plugin
            debug_mode = false
            auto_save_interval = 300  # segundos
            language = "es"
            
            [messaging]
            # Sistema de mensajería entre plugins
            enable_plugin_messaging = true
            message_timeout = 5000  # milisegundos
            
            [cache]
            # Configuración del sistema de caché
            enable_cache = true
            cache_size = 1000
            cache_expire_time = 3600  # segundos
            
            [security]
            # Configuraciones de seguridad
            enable_encryption = true
            api_key = "change_this_api_key"
            allowed_plugins = ["GrivyzomEconomy", "GrivyzomRankup", "GrivyzomPvP"]
            """;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("config.toml").toFile();
    }

    /**
     * Carga o crea la configuración
     */
    public void loadConfig() {
        try {
            // Crear directorio si no existe
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                MessageUtils.sendInfoMessage(logger, "Directorio de datos creado: " + dataDirectory);
            }

            // Crear archivo de configuración si no existe
            if (!configFile.exists()) {
                createDefaultConfig();
                MessageUtils.sendInfoMessage(logger, "Archivo de configuración creado con valores por defecto");
            }

            // Cargar configuración
            config = new Toml().read(configFile);
            MessageUtils.sendSuccessMessage(logger, "Configuración cargada correctamente");

            // Validar configuración
            validateConfig();

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al cargar la configuración: " + e.getMessage());
            throw new RuntimeException("No se pudo cargar la configuración", e);
        }
    }

    /**
     * Crea el archivo de configuración por defecto
     */
    private void createDefaultConfig() throws IOException {
        Files.writeString(configFile.toPath(), DEFAULT_CONFIG);
    }

    /**
     * Valida la configuración cargada
     */
    private void validateConfig() {
        boolean hasErrors = false;

        // Validar configuración de seguridad
        if (getString("security.api_key").equals("change_this_api_key")) {
            MessageUtils.sendWarningMessage(logger, "⚠ Por favor, cambia la API key por defecto en la configuración");
        }

        if (hasErrors) {
            throw new RuntimeException("La configuración tiene errores críticos");
        }

        MessageUtils.sendSuccessMessage(logger, "Configuración validada correctamente");
    }

    /**
     * Recarga la configuración desde el archivo
     */
    public void reloadConfig() {
        try {
            config = new Toml().read(configFile);
            validateConfig();
            MessageUtils.sendSuccessMessage(logger, "Configuración recargada");
        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error al recargar la configuración: " + e.getMessage());
        }
    }

    /**
     * Guarda la configuración actual al archivo
     */
    public void saveConfig() {
        try {
            TomlWriter writer = new TomlWriter();
            writer.write(config, configFile);
            MessageUtils.sendSuccessMessage(logger, "Configuración guardada");
        } catch (IOException e) {
            MessageUtils.sendErrorMessage(logger, "Error al guardar la configuración: " + e.getMessage());
        }
    }

    // Métodos para obtener valores de configuración

    public String getString(String key) {
        return config.getString(key, "");
    }

    public String getString(String key, String defaultValue) {
        return config.getString(key, defaultValue);
    }

    public int getInt(String key) {
        return config.getLong(key, 0L).intValue();
    }

    public int getInt(String key, int defaultValue) {
        return config.getLong(key, (long) defaultValue).intValue();
    }

    public long getLong(String key) {
        return config.getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        return config.getLong(key, defaultValue);
    }

    public boolean getBoolean(String key) {
        return config.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return config.getBoolean(key, defaultValue);
    }

    public java.util.List<String> getStringList(String key) {
        return config.getList(key, java.util.Collections.emptyList());
    }

    // Métodos específicos para configuraciones comunes (SIN base de datos)

    public boolean isDebugMode() {
        return getBoolean("plugin.debug_mode");
    }

    public int getAutoSaveInterval() {
        return getInt("plugin.auto_save_interval");
    }

    public String getLanguage() {
        return getString("plugin.language", "es");
    }

    public boolean isPluginMessagingEnabled() {
        return getBoolean("messaging.enable_plugin_messaging");
    }

    public int getMessageTimeout() {
        return getInt("messaging.message_timeout");
    }

    public boolean isCacheEnabled() {
        return getBoolean("cache.enable_cache");
    }

    public int getCacheSize() {
        return getInt("cache.cache_size");
    }

    public int getCacheExpireTime() {
        return getInt("cache.cache_expire_time");
    }

    public boolean isEncryptionEnabled() {
        return getBoolean("security.enable_encryption");
    }

    public String getApiKey() {
        return getString("security.api_key");
    }

    public java.util.List<String> getAllowedPlugins() {
        return getStringList("security.allowed_plugins");
    }
}