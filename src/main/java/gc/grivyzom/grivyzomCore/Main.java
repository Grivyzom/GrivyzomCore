package gc.grivyzom.grivyzomCore;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import gc.grivyzom.grivyzomCore.config.ConfigManager;
import gc.grivyzom.grivyzomCore.config.DatabaseConfigManager;
import gc.grivyzom.grivyzomCore.database.DatabaseManager;
//import gc.grivyzom.grivyzomCore.managers.PlayerDataManager;
//import gc.grivyzom.grivyzomCore.messaging.PluginMessageManager;
import gc.grivyzom.grivyzomCore.utils.MessageUtils;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "grivyzomcore",
        name = "GrivyzomCore",
        version = "0.1-SNAPSHOT",
        url = "www.grivyzom.com",
        authors = {"Francisco Fuentes"},
        description = "Plugin central para la administración de usuarios del network Grivyzom"
)
public class Main {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    @Inject
    private @DataDirectory Path dataDirectory;

    private ConfigManager configManager;
    private DatabaseConfigManager databaseConfigManager;
    private DatabaseManager databaseManager;
    //private PlayerDataManager playerDataManager;
    //private PluginMessageManager pluginMessageManager;

    private static Main instance;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        MessageUtils.sendStartupMessage(logger);

        try {
            // Inicializar configuración principal
            configManager = new ConfigManager(dataDirectory, logger);
            configManager.loadConfig();

            // Inicializar configuración de base de datos
            databaseConfigManager = new DatabaseConfigManager(dataDirectory, logger);
            databaseConfigManager.loadDatabaseConfig();

            // Mostrar información de la base de datos
            databaseConfigManager.printDatabaseInfo();

            // Inicializar conexión a base de datos (SIN crear tablas)
            databaseManager = new DatabaseManager(databaseConfigManager.createDatabaseConfig(), logger);
            databaseManager.initialize();

            // TODO: Inicializar gestores cuando estén las tablas creadas
            //playerDataManager = new PlayerDataManager(databaseManager, logger);
            //pluginMessageManager = new PluginMessageManager(server, logger);

            // TODO: Registrar eventos y canales de mensajería
            //server.getEventManager().register(this, playerDataManager);
            //pluginMessageManager.registerChannels();

            MessageUtils.sendSuccessMessage(logger, "Plugin inicializado correctamente");
            MessageUtils.sendInfoMessage(logger, "Versión: 0.1-SNAPSHOT");
            MessageUtils.sendInfoMessage(logger, "Autor: Francisco Fuentes");

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error durante la inicialización: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        MessageUtils.sendInfoMessage(logger, "Cerrando GrivyzomCore...");

        try {
            if (databaseManager != null) {
                databaseManager.close();
            }

            // TODO: Desregistrar canales cuando esté implementado
            //if (pluginMessageManager != null) {
            //    pluginMessageManager.unregisterChannels();
            //}

            MessageUtils.sendSuccessMessage(logger, "Plugin cerrado correctamente");

        } catch (Exception e) {
            MessageUtils.sendErrorMessage(logger, "Error durante el cierre: " + e.getMessage());
        }
    }

    // Getters para acceso desde otros plugins
    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseConfigManager getDatabaseConfigManager() {
        return databaseConfigManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    // TODO: Descomentar cuando estén implementados
    /*
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PluginMessageManager getPluginMessageManager() {
        return pluginMessageManager;
    }
    */

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }
}