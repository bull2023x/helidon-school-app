package com.example.myproject;

import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.http.HeaderNames;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;

@RoutingTest
class AdminAnalyticsTest {
    private static final String TOKEN="test-only-admin-token-1234567890";
    private static final PageViewStore STORE=new PageViewStore(() -> DriverManager.getConnection("jdbc:h2:mem:admin_views;DB_CLOSE_DELAY=-1","sa",""),Clock.systemUTC());
    private final DirectClient client;
    AdminAnalyticsTest(DirectClient client) { this.client=client; }

    @SetUpRoute static void routing(HttpRouting.Builder routing) {
        AdminAnalyticsService.register(routing,STORE,TOKEN);
        routing.get("/schools-v2-table",(req,res) -> { if (AdminAnalyticsService.countable(req)) STORE.recordView();res.send("学校一覧"); });
    }

    @Test void authenticationIsRequiredForNumbersButLoginPageIsPublic() {
        assertFalse(AdminAnalyticsService.authorized(null,""));
        assertFalse(AdminAnalyticsService.authorized("short","short"));
        assertFalse(AdminAnalyticsService.authorized(TOKEN,TOKEN+"x"));
        var denied=client.get("/admin/api/analytics").request(String.class);
        assertEquals(401,denied.status().code());assertFalse(denied.entity().contains("today"));
        var login=client.get("/admin/analytics").request(String.class);
        assertEquals(200,login.status().code());assertTrue(login.entity().contains("管理者キー"));
        var valid=client.get("/admin/api/analytics").header(HeaderNames.AUTHORIZATION,"Bearer "+TOKEN).request(String.class);
        assertEquals(200,valid.status().code());assertTrue(valid.entity().contains("Asia/Tokyo"));
        assertFalse(valid.entity().contains(TOKEN));
    }

    @Test void onlyActualPageGetsCountAndStatsDoNotCountThemselves() throws Exception {
        long before=STORE.snapshot().total();
        client.get("/schools-v2-table").request(String.class);
        client.head("/schools-v2-table").request().close();
        client.get("/schools-v2-table").header(HeaderNames.create("Sec-Purpose"),"prefetch").request(String.class);
        client.get("/admin/analytics").request(String.class);
        client.get("/admin/api/analytics").header(HeaderNames.AUTHORIZATION,"Bearer "+TOKEN).request(String.class);
        assertEquals(before+1,STORE.snapshot().total());
    }
}
