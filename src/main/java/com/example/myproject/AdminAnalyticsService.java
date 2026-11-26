package com.example.myproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

final class AdminAnalyticsService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AdminAnalyticsService() { }

    static boolean authorized(String configured, String supplied) {
        return configured != null && configured.length() >= 24 && supplied != null
                && MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    static boolean countable(ServerRequest request) {
        // HEAD, browser prefetches, health endpoints and admin views do not count.
        String purpose = request.headers().first(HeaderNames.create("Sec-Purpose")).orElse("")
                + request.headers().first(HeaderNames.create("Purpose")).orElse("");
        return "GET".equals(request.prologue().method().text()) && !purpose.toLowerCase(java.util.Locale.ROOT).contains("prefetch");
    }

    static void register(HttpRouting.Builder routing, PageViewStore store, String adminToken) {
        for (String file : new String[]{"analytics.html", "analytics.js", "analytics.css"}) {
            String route = "analytics.html".equals(file) ? "/admin/analytics" : "/admin/" + file;
            routing.get(route, (req, res) -> {
                res.header("Cache-Control", "no-store");
                res.header("X-Robots-Tag", "noindex, nofollow");
                res.header("X-Content-Type-Options", "nosniff");
                res.header("Referrer-Policy", "no-referrer");
                res.header("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
                res.header("Content-Type", file.endsWith(".js") ? "text/javascript; charset=UTF-8" : file.endsWith(".css") ? "text/css; charset=UTF-8" : "text/html; charset=UTF-8");
                try (var input = AdminAnalyticsService.class.getResourceAsStream("/analytics/" + file)) {
                    if (input == null) { res.status(404).send(); return; }
                    res.send(input.readAllBytes());
                }
            });
        }
        routing.get("/admin/api/analytics", (req, res) -> {
            res.header("Cache-Control", "no-store");
            res.header("Vary", "Authorization");
            res.header("X-Robots-Tag", "noindex, nofollow");
            res.header("Content-Type", "application/json; charset=UTF-8");
            String header = req.headers().first(HeaderNames.AUTHORIZATION).orElse("");
            String token = header.startsWith("Bearer ") ? header.substring(7) : "";
            if (!authorized(adminToken, token)) {
                res.status(401).send(JSON.writeValueAsString(Map.of("error", "管理者キーを確認してください。未設定または24文字未満の場合も利用できません。")));
                return;
            }
            if (!store.configured()) {
                res.status(503).send(JSON.writeValueAsString(Map.of("error", "計測は未設定です。RenderにANALYTICS_DATABASE_URL（永続PostgreSQL）を設定してください。")));
                return;
            }
            try { res.send(JSON.writeValueAsString(store.snapshot())); }
            catch (Exception unavailable) {
                res.status(503).send(JSON.writeValueAsString(Map.of("error", "集計DBに接続できません。0回ではありません。接続設定を確認してください。")));
            }
        });
    }
}
