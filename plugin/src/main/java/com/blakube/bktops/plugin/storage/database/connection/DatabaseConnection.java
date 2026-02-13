package com.blakube.bktops.plugin.storage.database.connection;

import com.blakube.bktops.api.config.ConfigContainer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConnection {

    private static HikariDataSource dataSource;
    private static String driver;

    private DatabaseConnection() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void init(@NotNull JavaPlugin plugin, @NotNull ConfigContainer config) {
        driver = config.getString("driver", "h2").toLowerCase();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("BK-Tops-Pool");

        if (driver.equals("mysql")) {
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String database = config.getString("db-name", "bktops");
            String username = config.getString("username", "root");
            String password = config.getString("password", "");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false" +
                    "&autoReconnect=true" +
                    "&allowPublicKeyRetrieval=true" +
                    "&characterEncoding=utf8";

            hikariConfig.setJdbcUrl(url);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            plugin.getLogger().info("Using MySQL database");

        } else {
            File dataFolder = plugin.getDataFolder();
            File dbFile = new File(dataFolder, "data/bktops");

            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            String url = "jdbc:h2:file:" + dbFile.getAbsolutePath()
                    + ";MODE=MySQL"
                    + ";DATABASE_TO_LOWER=TRUE"
                    + ";AUTO_SERVER=TRUE";

            hikariConfig.setJdbcUrl(url);
            hikariConfig.setDriverClassName("org.h2.Driver");

            plugin.getLogger().info("Using H2 database");
            plugin.getLogger().info("H2 path: " + dbFile.getAbsolutePath());
        }

        hikariConfig.setMaximumPoolSize(config.getInt("max-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("minimum-idle", 2));
        hikariConfig.setConnectionTimeout(config.getInt("connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getInt("idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getInt("max-lifetime", 1800000));

        hikariConfig.setLeakDetectionThreshold(0);

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hikariConfig);

        plugin.getLogger().info("Database connection pool initialized");
    }

    @NotNull
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseConnection not initialized");
        }
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @NotNull
    public static String getDriver() {
        return driver;
    }
}
