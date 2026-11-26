package com.example.myproject;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.Properties;
import java.util.logging.Logger;

/** Daily totals only: no IP addresses, user agents, cookies, or search terms. */
final class PageViewStore {
    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Logger LOG = Logger.getLogger(PageViewStore.class.getName());
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    private final Clock clock;
    private volatile boolean initialized;
    private volatile Instant lastFailure;

    PageViewStore(Connections connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    static PageViewStore fromEnvironment() {
        String value = System.getenv("ANALYTICS_DATABASE_URL");
        if (value == null || value.isBlank()) value = System.getenv("DATABASE_URL");
        if (value == null || value.isBlank()) return new PageViewStore(null, Clock.systemUTC());
        try {
            ConnectionSettings settings = parsePostgres(value);
            return new PageViewStore(() -> DriverManager.getConnection(settings.url(), settings.properties()), Clock.systemUTC());
        } catch (RuntimeException invalid) {
            // Never log the URL: it contains database credentials.
            LOG.warning("Analytics PostgreSQL URL is invalid; page-view tracking is disabled.");
            return new PageViewStore(null, Clock.systemUTC());
        }
    }

    record ConnectionSettings(String url, Properties properties) { }

    static ConnectionSettings parsePostgres(String value) {
        URI uri = URI.create(value);
        if (!("postgres".equals(uri.getScheme()) || "postgresql".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() == null
                || uri.getRawPath() == null || uri.getRawPath().length() < 2 || uri.getFragment() != null) {
            throw new IllegalArgumentException("Expected a PostgreSQL connection URL");
        }
        String[] credentials = uri.getRawUserInfo().split(":", 2);
        if (credentials.length != 2) throw new IllegalArgumentException("Missing database credentials");
        Properties properties = new Properties();
        properties.setProperty("user", decode(credentials[0]));
        properties.setProperty("password", decode(credentials[1]));
        if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if ("user".equalsIgnoreCase(key) || "password".equalsIgnoreCase(key)) throw new IllegalArgumentException("Duplicate credentials");
            properties.setProperty("channel_binding".equals(key) ? "channelBinding" : key,
                    pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "3");
        // Render internal hosts use the private network. External connections
        // require verified TLS unless the supplied URL explicitly configures it.
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean internal = !uri.getHost().contains(".") || uri.getHost().endsWith(".internal");
        if (!local && !internal) {
            properties.setProperty("sslmode", "verify-full");
            properties.setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
        } else if (!properties.containsKey("sslmode")) properties.setProperty("sslmode", "prefer");
        String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
        String url = "jdbc:postgresql://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + uri.getRawPath();
        return new ConnectionSettings(url, properties);
    }

    private static String decode(String text) {
        return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    boolean configured() { return connections != null; }

    private synchronized void initialize() throws SQLException {
        if (initialized) return;
        if (!configured()) throw new SQLException("Analytics database is not configured");
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            statement.execute("CREATE TABLE IF NOT EXISTS school_page_views (view_date DATE PRIMARY KEY, views BIGINT NOT NULL CHECK (views >= 0))");
            statement.execute("CREATE TABLE IF NOT EXISTS school_view_tracking (id INTEGER PRIMARY KEY CHECK (id = 1), started_at TIMESTAMP WITH TIME ZONE NOT NULL)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO school_view_tracking (id, started_at) VALUES (1, ?)")) {
                insert.setObject(1, OffsetDateTime.now(clock));
                try { insert.executeUpdate(); }
                catch (SQLException existing) { if (!"23505".equals(existing.getSQLState())) throw existing; }
            }
        }
        initialized = true;
    }

    void recordView() {
        if (!configured()) return;
        try {
            initialize();
            LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
            try (Connection connection = connections.open()) {
                if (increment(connection, today) == 0) {
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO school_page_views (view_date, views) VALUES (?, 1)")) {
                        insert.setQueryTimeout(3);
                        insert.setObject(1, today);
                        try { insert.executeUpdate(); }
                        catch (SQLException race) {
                            // An overlapping request may have inserted this day.
                            if (!"23505".equals(race.getSQLState())) throw race;
                            increment(connection, today);
                        }
                    }
                }
            }
        } catch (SQLException failure) {
            Instant stamp = clock.instant();
            if (lastFailure == null || Duration.between(lastFailure, stamp).toSeconds() >= 60) {
                LOG.warning("Page-view recording failed; the school page remains available. Check analytics database connectivity.");
            }
            lastFailure = stamp;
        }
    }

    private int increment(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE school_page_views SET views = views + 1 WHERE view_date = ?")) {
            update.setQueryTimeout(3);
            update.setObject(1, day);
            return update.executeUpdate();
        }
    }

    record Snapshot(long today, long month, long total, String date, String monthLabel,
                    String timezone, String startedAt, String lastRecordingFailure) { }

    Snapshot snapshot() throws SQLException {
        initialize();
        LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
        LocalDate start = today.withDayOfMonth(1);
        String sql = "SELECT COALESCE(SUM(CASE WHEN view_date = ? THEN views ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN view_date >= ? AND view_date < ? THEN views ELSE 0 END),0), "
                + "COALESCE(SUM(views),0) FROM school_page_views";
        try (Connection connection = connections.open(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setQueryTimeout(3);
            query.setObject(1, today); query.setObject(2, start); query.setObject(3, start.plusMonths(1));
            long dayCount, monthCount, total;
            try (ResultSet rows = query.executeQuery()) {
                rows.next(); dayCount = rows.getLong(1); monthCount = rows.getLong(2); total = rows.getLong(3);
            }
            String started;
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT started_at FROM school_view_tracking WHERE id=1")) {
                rows.next(); started = rows.getObject(1, OffsetDateTime.class).toInstant().toString();
            }
            return new Snapshot(dayCount, monthCount, total, today.toString(), YearMonth.from(today).toString(),
                    ZONE.toString(), started, lastFailure == null ? null : lastFailure.toString());
        }
    }
}
