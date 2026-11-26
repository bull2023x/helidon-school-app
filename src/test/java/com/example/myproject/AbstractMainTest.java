package com.example.myproject;


import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.CoreMatchers.containsString;

abstract class AbstractMainTest {
    private final Http1Client client;

    protected AbstractMainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        DbInit.init();
        Main.routing(builder);
    }

    
    @Test
    void testMetricsObserver() {
        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    
    @Test
    void testSimpleGreet() {
        ClientResponseTyped<String> response = client.get("/simple-greet").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello World!"));
    }

    @Test
    void currentAdmissionsPageUsesRequestedHeadingAndSourceLabels() {
        ClientResponseTyped<String> response=client.get("/schools-v2-table").request(String.class);
        assertThat(response.status(),is(Status.OK_200));
        assertThat(response.entity(),containsString("2027年度 中学受験 帰国入試＆英語入試情報 &lt;2026年11月25日更新&gt;"));
        assertThat(response.entity(),containsString("公式資料の確認状況"));
        assertThat(response.entity(),containsString("備考（４科目受験の場合の四谷大塚偏差値を参考に併記）"));
        assertThat(response.entity(),containsString("確認した公式資料"));
        assertThat(response.entity(),containsString("2026年度受験（現中１娘）の受験最終レポート２本を公開中（有料）"));
        assertThat(response.entity(),containsString("https://note.com/naiveprince0507/n/n3ff1eed03e00?app_launch=false"));
        assertThat(response.entity(),containsString("https://note.com/naiveprince0507/n/nff78aea25f7f?app_launch=false"));
    }


}
