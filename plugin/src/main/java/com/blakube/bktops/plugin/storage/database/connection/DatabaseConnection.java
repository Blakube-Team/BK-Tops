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
        DatabaseExecutors.init(config.getInt("pool.threads", 1));

        if (driver.equals("mysql")) {
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String database = config.getString("db-name", "bktops");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false" +
                    "&autoReconnect=true" +
                    "&allowPublicKeyRetrieval=true" +
                    "&characterEncoding=utf8";

            plugin.getLogger().info("Using MySQL database");
            dataSource = new HikariDataSource(buildConfig(config, url,
                    "com.mysql.cj.jdbc.Driver",
                    config.getString("username", "root"),
                    config.getString("password", "")));

        } else {
            File dbFile = new File(plugin.getDataFolder(), "data/bktops");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            plugin.getLogger().info("Using H2 database");
            plugin.getLogger().info("H2 path: " + dbFile.getAbsolutePath());

            boolean autoServer = config.getBoolean("h2.auto-server", false);
            try {
                dataSource = new HikariDataSource(
                        buildConfig(config, h2Url(dbFile, autoServer), "org.h2.Driver", null, null));
            } catch (RuntimeException ex) {
                if (!autoServer) throw ex;

                // AUTO_SERVER makes H2 open a TCP socket and resolve the local hostname. That
                // fails on hosts with no resolvable hostname or on network filesystems, and
                // surfaces as a generic H2 IO exception (error 90028). Retry without it.
                plugin.getLogger().warning("H2 could not start with auto-server enabled: " + rootCause(ex)
                        + ". Retrying with h2.auto-server disabled; set it to false in database.yml"
                        + " to silence this warning.");
                dataSource = new HikariDataSource(
                        buildConfig(config, h2Url(dbFile, false), "org.h2.Driver", null, null));
            }
        }

        plugin.getLogger().info("Database connection pool initialized");
    }

    @NotNull
    private static String h2Url(@NotNull File dbFile, boolean autoServer) {
        return "jdbc:h2:file:" + dbFile.getAbsolutePath()
                + ";MODE=MySQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + (autoServer ? ";AUTO_SERVER=TRUE" : "");
    }

    @NotNull
    private static HikariConfig buildConfig(@NotNull ConfigContainer config,
                                            @NotNull String url,
                                            @NotNull String driverClass,
                                            String username,
                                            String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("BK-Tops-Pool");
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setDriverClassName(driverClass);

        if (username != null) hikariConfig.setUsername(username);
        if (password != null) hikariConfig.setPassword(password);

        int threads = config.getInt("pool.threads", 1);
        hikariConfig.setMaximumPoolSize(config.getInt("pool.max-pool-size", threads));
        hikariConfig.setMinimumIdle(config.getInt("pool.minimum-idle", 1));
        hikariConfig.setConnectionTimeout(config.getInt("pool.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getInt("pool.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getInt("pool.max-lifetime", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getInt("pool.leak-detection-threshold", 30000));

        if (driverClass.startsWith("com.mysql")) {
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        return hikariConfig;
    }

    @NotNull
    private static String rootCause(@NotNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
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
