package com.example.myproject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PageViewStoreTest {
    @TempDir Path directory;
    private static Clock at(String timestamp) { return Clock.fixed(Instant.parse(timestamp), ZoneOffset.UTC); }
    private PageViewStore fileStore(Clock clock) {
        return new PageViewStore(() -> DriverManager.getConnection("jdbc:h2:file:" + directory.resolve("views"),"sa",""), clock);
    }

    @Test void survivesReopeningAndChangesDayAndMonthInJapan() throws Exception {
        var first = fileStore(at("2026-11-30T14:59:59Z"));
        first.recordView(); first.recordView();
        var before = first.snapshot();
        var after = fileStore(at("2026-11-30T15:00:00Z"));
        after.recordView();
        var totals = after.snapshot();
        assertEquals("2026-12-01", totals.date());
        assertEquals(1, totals.today()); assertEquals(1, totals.month()); assertEquals(3, totals.total());
        assertEquals(before.startedAt(), totals.startedAt());
        assertEquals(3, fileStore(at("2026-11-30T15:00:00Z")).snapshot().total());
    }

    private void concurrentIncrements(PageViewStore store) throws Exception {
        try (var threads = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<?>>();
            for (int i=0;i<8;i++) futures.add(threads.submit(() -> { for (int j=0;j<10;j++) store.recordView(); }));
            for (var task : futures) task.get(30, TimeUnit.SECONDS);
        }
        assertEquals(80, store.snapshot().total());
        assertNull(store.snapshot().lastRecordingFailure());
    }

    @Test void concurrentFirstVisitsHaveNoLostUpdates() throws Exception {
        String name = "jdbc:h2:mem:views_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        concurrentIncrements(new PageViewStore(() -> DriverManager.getConnection(name,"sa",""),Clock.systemUTC()));
    }

    @Test void disabledAndUnavailableNeverLookLikeZeroVisits() {
        var disabled = new PageViewStore(null,Clock.systemUTC());
        assertFalse(disabled.configured()); assertDoesNotThrow(disabled::recordView);
        assertThrows(SQLException.class,disabled::snapshot);
        var unavailable = new PageViewStore(() -> { throw new SQLException("not connected"); },Clock.systemUTC());
        assertDoesNotThrow(unavailable::recordView); assertThrows(SQLException.class,unavailable::snapshot);
    }

    @Test void parsesEncodedCredentialsWithoutPuttingThemInJdbcUrl() {
        var settings = PageViewStore.parsePostgres("postgresql://reader:p%40ss%2Bword@ep-example.neon.tech/views?sslmode=verify-full");
        assertEquals("p@ss+word",settings.properties().getProperty("password"));
        assertEquals("jdbc:postgresql://ep-example.neon.tech/views",settings.url());
        assertEquals("verify-full",settings.properties().getProperty("sslmode"));
        assertThrows(IllegalArgumentException.class,() -> PageViewStore.parsePostgres("jdbc:h2:mem:lost-on-restart"));
        assertThrows(IllegalArgumentException.class,() -> PageViewStore.parsePostgres("https://example.com/views"));
    }

    @Test void postgresConcurrencyAndNewApplicationInstance() throws Exception {
        String value = System.getenv("TEST_ANALYTICS_DATABASE_URL");
        assumeTrue(value != null && !value.isBlank(),"PostgreSQL integration URL not configured");
        var settings = PageViewStore.parsePostgres(value);
        String schema = "test_views_" + java.util.UUID.randomUUID().toString().replace("-","");
        try (var connection = DriverManager.getConnection(settings.url(),settings.properties()); var sql=connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            var props=settings.properties();
            try {
                PageViewStore.Connections connections=() -> {
                    var isolated = DriverManager.getConnection(settings.url(),props);
                    isolated.setSchema(schema);
                    assertEquals(schema, isolated.getSchema());
                    return isolated;
                };
                var first=new PageViewStore(connections,Clock.systemUTC());
                concurrentIncrements(first);
                var reopened=new PageViewStore(connections,Clock.systemUTC());
                assertEquals(80,reopened.snapshot().total());
                assertEquals(first.snapshot().startedAt(),reopened.snapshot().startedAt());
            } finally { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
