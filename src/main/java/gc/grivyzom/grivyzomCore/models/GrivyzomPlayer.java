package gc.grivyzom.grivyzomCore.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Modelo que representa un jugador en el sistema GrivyzomCore
 */
public class GrivyzomPlayer {

    private final UUID uuid;
    private String username;
    private String displayName;
    private final Timestamp firstJoin;
    private Timestamp lastJoin;
    private String lastServer;
    private long totalPlaytime;
    private double coins;
    private int gems;
    private String rankId;
    private String permissions;
    private String data;
    private boolean isOnline;

    // Constructor completo
    public GrivyzomPlayer(UUID uuid, String username, String displayName,
                          Timestamp firstJoin, Timestamp lastJoin, String lastServer,
                          long totalPlaytime, double coins, int gems, String rankId,
                          String permissions, String data, boolean isOnline) {
        this.uuid = uuid;
        this.username = username;
        this.displayName = displayName;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.lastServer = lastServer;
        this.totalPlaytime = totalPlaytime;
        this.coins = coins;
        this.gems = gems;
        this.rankId = rankId;
        this.permissions = permissions;
        this.data = data;
        this.isOnline = isOnline;
    }

    /**
     * Crea un GrivyzomPlayer desde un ResultSet de la base de datos
     */
    public static GrivyzomPlayer fromResultSet(ResultSet rs) throws SQLException {
        return new GrivyzomPlayer(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getTimestamp("first_join"),
                rs.getTimestamp("last_join"),
                rs.getString("last_server"),
                rs.getLong("total_playtime"),
                rs.getDouble("coins"),
                rs.getInt("gems"),
                rs.getString("rank_id"),
                rs.getString("permissions"),
                rs.getString("data"),
                rs.getBoolean("is_online")
        );
    }

    /**
     * Añade monedas al jugador
     */
    public void addCoins(double amount) {
        this.coins += amount;
    }

    /**
     * Remueve monedas del jugador
     */
    public boolean removeCoins(double amount) {
        if (this.coins >= amount) {
            this.coins -= amount;
            return true;
        }
        return false;
    }

    /**
     * Añade gemas al jugador
     */
    public void addGems(int amount) {
        this.gems += amount;
    }

    /**
     * Remueve gemas del jugador
     */
    public boolean removeGems(int amount) {
        if (this.gems >= amount) {
            this.gems -= amount;
            return true;
        }
        return false;
    }

    /**
     * Añade tiempo de juego
     */
    public void addPlaytime(long milliseconds) {
        this.totalPlaytime += milliseconds;
    }

    /**
     * Obtiene el tiempo de juego en formato legible
     */
    public String getFormattedPlaytime() {
        long totalSeconds = totalPlaytime / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Obtiene el tiempo desde la primera conexión
     */
    public String getTimeSinceFirstJoin() {
        long diffMs = System.currentTimeMillis() - firstJoin.getTime();
        long diffDays = diffMs / (24 * 60 * 60 * 1000);

        if (diffDays > 0) {
            return diffDays + " días";
        } else {
            long diffHours = diffMs / (60 * 60 * 1000);
            return diffHours + " horas";
        }
    }

    /**
     * Obtiene el tiempo desde la última conexión
     */
    public String getTimeSinceLastJoin() {
        if (isOnline) {
            return "Conectado";
        }

        long diffMs = System.currentTimeMillis() - lastJoin.getTime();
        long diffDays = diffMs / (24 * 60 * 60 * 1000);

        if (diffDays > 0) {
            return "Hace " + diffDays + " días";
        } else {
            long diffHours = diffMs / (60 * 60 * 1000);
            if (diffHours > 0) {
                return "Hace " + diffHours + " horas";
            } else {
                long diffMinutes = diffMs / (60 * 1000);
                return "Hace " + diffMinutes + " minutos";
            }
        }
    }

    /**
     * Verifica si el jugador tiene suficientes monedas
     */
    public boolean hasEnoughCoins(double amount) {
        return this.coins >= amount;
    }

    /**
     * Verifica si el jugador tiene suficientes gemas
     */
    public boolean hasEnoughGems(int amount) {
        return this.gems >= amount;
    }

    /**
     * Actualiza la hora de última conexión
     */
    public void updateLastJoin() {
        this.lastJoin = new Timestamp(System.currentTimeMillis());
    }

    // Getters y Setters
    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Timestamp getFirstJoin() {
        return firstJoin;
    }

    public Timestamp getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(Timestamp lastJoin) {
        this.lastJoin = lastJoin;
    }

    public String getLastServer() {
        return lastServer;
    }

    public void setLastServer(String lastServer) {
        this.lastServer = lastServer;
    }

    public long getTotalPlaytime() {
        return totalPlaytime;
    }

    public void setTotalPlaytime(long totalPlaytime) {
        this.totalPlaytime = totalPlaytime;
    }

    public double getCoins() {
        return coins;
    }

    public void setCoins(double coins) {
        this.coins = coins;
    }

    public int getGems() {
        return gems;
    }

    public void setGems(int gems) {
        this.gems = gems;
    }

    public String getRankId() {
        return rankId;
    }

    public void setRankId(String rankId) {
        this.rankId = rankId;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    @Override
    public String toString() {
        return String.format(
                "GrivyzomPlayer{uuid=%s, username='%s', displayName='%s', " +
                        "coins=%.2f, gems=%d, rankId='%s', isOnline=%s, lastServer='%s'}",
                uuid, username, displayName, coins, gems, rankId, isOnline, lastServer
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GrivyzomPlayer that = (GrivyzomPlayer) obj;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}