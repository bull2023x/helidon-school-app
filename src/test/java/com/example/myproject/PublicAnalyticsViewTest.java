package com.example.myproject;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class PublicAnalyticsViewTest {
    @Test void publicTotalsUseTheSameStoreAndJapaneseDay() {
        var clock = Clock.fixed(Instant.parse("2026-11-26T15:30:00Z"), ZoneOffset.UTC);
        var store = new PageViewStore(() -> DriverManager.getConnection(
                "jdbc:h2:mem:public_views;DB_CLOSE_DELAY=-1", "sa", ""), clock);
        store.recordView(); store.recordView();
        String rendered = PublicAnalyticsView.render(store);
        assertTrue(rendered.contains("閲覧回数（日本時間）"));
        for (String label : new String[]{"今日", "今月", "累計"})
            assertTrue(rendered.contains("<dt>" + label + "</dt><dd>2<small> 回</small>"));
        assertFalse(rendered.contains("startedAt"));
        assertFalse(rendered.contains("jdbc:"));
    }

    @Test void unavailableCountsAreNeverShownAsZeroOrLeakDatabaseErrors() {
        var store = new PageViewStore(() -> { throw new SQLException("private connection details"); }, Clock.systemUTC());
        String rendered = PublicAnalyticsView.render(store);
        assertTrue(rendered.contains("現在、集計を表示できません。"));
        assertFalse(rendered.contains("<dd>0"));
        assertFalse(rendered.contains("private connection details"));
        assertDoesNotThrow(() -> PublicAnalyticsView.render(new PageViewStore(null, Clock.systemUTC())));
    }
}
