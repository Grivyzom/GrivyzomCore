package gc.grivyzom.grivyzomCore.utils;

import org.slf4j.Logger;

public class MessageUtils {

    // Códigos de color ANSI para consola
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    // Colores principales
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";

    // Colores brillantes
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_BLUE = "\u001B[94m";
    private static final String BRIGHT_PURPLE = "\u001B[95m";
    private static final String BRIGHT_CYAN = "\u001B[96m";

    // Prefijos del plugin
    private static final String PREFIX = BOLD + BRIGHT_PURPLE + "[" + BRIGHT_CYAN + "GrivyzomCore" + BRIGHT_PURPLE + "]" + RESET + " ";
    private static final String SUCCESS_PREFIX = PREFIX + BRIGHT_GREEN + "✓ " + RESET;
    private static final String ERROR_PREFIX = PREFIX + RED + "✗ " + RESET;
    private static final String WARNING_PREFIX = PREFIX + BRIGHT_YELLOW + "⚠ " + RESET;
    private static final String INFO_PREFIX = PREFIX + BRIGHT_BLUE + "ℹ " + RESET;

    /**
     * Envía el mensaje de inicio del plugin con arte ASCII
     */
    public static void sendStartupMessage(Logger logger) {
        logger.info("");
        logger.info(BRIGHT_PURPLE + "╔══════════════════════════════════════════════════════════════╗" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BOLD + BRIGHT_CYAN + "                        GrivyzomCore                          " + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + "                                                              " + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_GREEN + "  ██████  ██████  ██ ██    ██ ██    ██ ███████  ██████  ███    ███" + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_GREEN + " ██       ██   ██ ██ ██    ██  ██  ██       ██ ██    ██ ████  ████" + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_GREEN + " ██   ███ ██████  ██ ██    ██   ████   ███████ ██    ██ ██ ████ ██" + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_GREEN + " ██    ██ ██   ██ ██  ██  ██     ██         ██ ██    ██ ██  ██  ██" + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_GREEN + "  ██████  ██   ██ ██   ████      ██    ███████  ██████  ██      ██" + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + "                                                              " + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + BRIGHT_YELLOW + "                    Sistema Central del Network                  " + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "║" + RESET + WHITE + "                      Desarrollado por Francisco Fuentes             " + RESET + BRIGHT_PURPLE + "║" + RESET);
        logger.info(BRIGHT_PURPLE + "╚══════════════════════════════════════════════════════════════╝" + RESET);
        logger.info("");
    }

    /**
     * Envía un mensaje de éxito
     */
    public static void sendSuccessMessage(Logger logger, String message) {
        logger.info(SUCCESS_PREFIX + BRIGHT_GREEN + message + RESET);
    }

    /**
     * Envía un mensaje de error
     */
    public static void sendErrorMessage(Logger logger, String message) {
        logger.error(ERROR_PREFIX + RED + message + RESET);
    }

    /**
     * Envía un mensaje de advertencia
     */
    public static void sendWarningMessage(Logger logger, String message) {
        logger.warn(WARNING_PREFIX + BRIGHT_YELLOW + message + RESET);
    }

    /**
     * Envía un mensaje informativo
     */
    public static void sendInfoMessage(Logger logger, String message) {
        logger.info(INFO_PREFIX + BRIGHT_BLUE + message + RESET);
    }

    /**
     * Envía un mensaje de debug
     */
    public static void sendDebugMessage(Logger logger, String message) {
        logger.debug(PREFIX + CYAN + "[DEBUG] " + WHITE + message + RESET);
    }

    /**
     * Formatea un mensaje con colores para la consola
     */
    public static String formatMessage(String message, MessageType type) {
        return switch (type) {
            case SUCCESS -> SUCCESS_PREFIX + BRIGHT_GREEN + message + RESET;
            case ERROR -> ERROR_PREFIX + RED + message + RESET;
            case WARNING -> WARNING_PREFIX + BRIGHT_YELLOW + message + RESET;
            case INFO -> INFO_PREFIX + BRIGHT_BLUE + message + RESET;
            case DEBUG -> PREFIX + CYAN + "[DEBUG] " + WHITE + message + RESET;
        };
    }

    /**
     * Crea una línea separadora decorativa
     */
    public static void sendSeparator(Logger logger) {
        logger.info(BRIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
    }

    /**
     * Enum para tipos de mensajes
     */
    public enum MessageType {
        SUCCESS, ERROR, WARNING, INFO, DEBUG
    }
}