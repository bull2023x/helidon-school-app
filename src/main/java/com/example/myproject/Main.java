package com.example.myproject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import io.helidon.http.HeaderNames;
import io.helidon.common.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {

    private static final String LOGIN_COOKIE_NAME = "school_app_auth";

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();
        Services.set(Config.class, config);

        DbInit.init();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/schools");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    private static String formatDateForDisplay(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.replaceAll("(\\d{4})-(\\d{2})-(\\d{2})", "$1/$2/$3");
    }

    private static String admissionsSummary(List<SchoolV2> schools) {
        int year = AdmissionsPolicy.targetYear();
        long complete = schools.stream().filter(s -> "日程・人数を確認済み".equals(s.verificationStatusLabel)).count();
        long partial = schools.stream().filter(s -> "一部確認済み".equals(s.verificationStatusLabel)).count();
        return "<section class=\"cohort-info\"><strong>" + year + "年度入学向け（" + (year - 1)
                + "年秋〜" + year + "年春の入試）</strong><p>全" + schools.size() + "件：日程・人数確認済み "
                + complete + "件／一部確認済み " + partial + "件／未確認 " + (schools.size() - complete - partial)
                + "件</p><p>対象年度の公式資料で確認できた項目を表示しています。未確認は未公表を意味しません。"
                + "人数が他方式との共通枠の場合は、その範囲を記載しています。試験日には終了済みの回を含みます。</p>"
                + "<p>見出しの日付は最終取得・確認日です。入試内容の確認日は各行の資料確認日時をご覧ください。</p></section>";
    }

    static String admissionsHeading(List<SchoolV2> schools) {
        String base = AdmissionsPolicy.targetYear() + "年度 中学受験 帰国入試＆英語入試情報";
        java.time.OffsetDateTime latest = null;
        for (SchoolV2 school : schools) {
            for (String timestamp : new String[] {school.verifiedAt, school.lastRetrievedAt}) {
                if (timestamp == null || timestamp.isBlank()) continue;
                try {
                    var date = java.time.OffsetDateTime.parse(timestamp);
                    if (latest == null || date.isAfter(latest)) latest = date;
                } catch (java.time.format.DateTimeParseException invalid) {
                    // Ignore invalid metadata without claiming a fresh update.
                }
            }
        }
        if (latest == null) return base + "（更新日未確認）";
        var day = latest.atZoneSameInstant(ZoneId.of("Asia/Tokyo")).toLocalDate();
        return base + " <" + day.format(DateTimeFormatter.ofPattern("uuuu年M月d日")) + "更新>";
    }

    private static boolean websiteUrl(String value) {
        try {
            var uri = java.net.URI.create(value == null ? "" : value);
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static String schoolLinks(SchoolV2 school) {
        StringBuilder links = new StringBuilder();
        if (websiteUrl(school.infoLink)) links.append("<a href=\"").append(escapeHtml(school.infoLink))
                .append("\" target=\"_blank\" rel=\"noopener noreferrer\">公式サイト</a>");
        if (school.admissionsSources != null) for (var source : school.admissionsSources) {
            String url = source.path("url").asText("");
            if (!websiteUrl(url) || source.path("admissionYear").asInt() != AdmissionsPolicy.targetYear()) continue;
            links.append("<br><a href=\"").append(escapeHtml(url)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">確認した公式資料");
            String pages = source.path("pages").asText("");
            if (!pages.isBlank()) links.append("（PDF ").append(escapeHtml(pages)).append("ページ）");
            links.append("</a>");
        }
        return links.toString();
    }

    static String schoolDeviationValues(SchoolV2 school) {
        var reference = school.deviationReference;
        if (reference == null || reference.path("admissionYear").asInt() != AdmissionsPolicy.targetYear()) return "";
        var values = new java.util.StringJoiner(",");
        for (var entry : reference.path("entries")) {
            var value = entry.path("value");
            if (value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 1 && value.asInt() <= 100) {
                values.add(Integer.toString(value.asInt()));
            }
        }
        return values.toString();
    }

    static String schoolNotes(SchoolV2 school) {
        StringBuilder html = new StringBuilder(escapeHtml(school.notes));
        var reference = school.deviationReference;
        if (reference == null || reference.path("admissionYear").asInt() != AdmissionsPolicy.targetYear()) return html.toString();
        html.append("<section class=\"deviation-reference\"><strong>四谷大塚 Aライン80偏差値（合格可能性80％）</strong>")
                .append("<div>").append(escapeHtml(reference.path("publication").asText())).append("</div>");
        if (reference.path("entries").isArray() && !reference.path("entries").isEmpty()) {
            for (var entry : reference.path("entries")) {
                int value = entry.path("value").asInt(-1);
                if (value < 1 || value > 100) continue;
                html.append("<p><b>").append(escapeHtml(entry.path("sex").asText())).append(" ").append(value)
                        .append("</b>（").append(escapeHtml(entry.path("examLabel").asText()))
                        .append("／一覧掲載日程：").append(escapeHtml(entry.path("date").asText()))
                        .append("／").append(escapeHtml(entry.path("subjectLabel").asText())).append("）</p>");
            }
        } else html.append("<p>公式Aライン80一覧に掲載を確認できませんでした。数値の推定はしていません。</p>");
        html.append("<p>").append(escapeHtml(reference.path("note").asText())).append("</p>");
        String source = reference.path("sourceUrl").asText("");
        if (websiteUrl(source)) html.append("<a href=\"").append(escapeHtml(source))
                .append("\" target=\"_blank\" rel=\"noopener noreferrer\">四谷大塚の出典</a>");
        html.append("<div>確認日：").append(escapeHtml(reference.path("checkedDate").asText())).append("</div></section>");
        return html.toString();
    }

private static String extractGeminiText(String json) {
    if (json == null || json.isBlank()) {
        return "Gemini response was empty.";
    }

    String marker = "\"text\": \"";
    int start = json.indexOf(marker);
    if (start < 0) {
        return "Could not extract text from Gemini response: " + json;
    }

    start += marker.length();
    StringBuilder sb = new StringBuilder();

    sb.append("対象は").append(AdmissionsPolicy.targetYear()).append("年度入学です。未確認の項目を推測で補完したり、前年の日付を繰り上げたりしないでください。\n");
    boolean escaped = false;

    for (int i = start; i < json.length(); i++) {
        char c = json.charAt(i);

        if (escaped) {
            switch (c) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append(c);
            }
            escaped = false;
            continue;
        }

        if (c == '\\') {
            escaped = true;
            continue;
        }

        if (c == '"') {
            break;
        }

        sb.append(c);
    }

    return sb.toString();
}

private static String callGemini(String prompt) throws Exception {
    String apiKey = System.getenv("GEMINI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalStateException("GEMINI_API_KEY is not set.");
    }

    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    URL url = new URL(endpoint);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("POST");
    conn.setRequestProperty("x-goog-api-key", apiKey);
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    conn.setDoOutput(true);

    String safePrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");

    String requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "%s"
                    }
                  ]
                }
              ]
            }
            """.formatted(safePrompt);

    try (OutputStream os = conn.getOutputStream()) {
        os.write(requestBody.getBytes(StandardCharsets.UTF_8));
    }

    int status = conn.getResponseCode();
    InputStream is = (status >= 200 && status < 300)
            ? conn.getInputStream()
            : conn.getErrorStream();

    byte[] bytes = is.readAllBytes();
    String responseBody = new String(bytes, StandardCharsets.UTF_8);

    if (status < 200 || status >= 300) {
        throw new RuntimeException("Gemini API error: HTTP " + status + " / " + responseBody);
    }

    return extractGeminiText(responseBody);
}

private static String jsonEscape(String s) {
    if (s == null) {
        return "";
    }
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
}

private static String buildSchoolDataPrompt(List<SchoolV2> schools, String question) {
    StringBuilder sb = new StringBuilder();

    sb.append("""
            You are an assistant helping recommend Japanese junior high schools based only on the provided school data.
            Do not invent schools or facts not contained in the data.
            Be cautious and say "候補" rather than guaranteeing admission.
            Return the answer in Japanese.
            For each recommended school, explain the reason based on the provided fields.
            
            User question:
            """).append(question).append("\n\n");

    sb.append("School data:\n");

    for (SchoolV2 s : schools) {
        sb.append("- 学校名: ").append(nullToEmpty(s.schoolName)).append("\n");
        sb.append("  入試分類: ").append(nullToEmpty(s.category)).append("\n");
        sb.append("  公式資料の確認状況: ").append(nullToEmpty(s.verificationStatusLabel)).append("\n");
        sb.append("  募集人数: ").append(nullToEmpty(s.capacity)).append("\n");
        sb.append("  試験日: ").append(nullToEmpty(s.examDates)).append("\n");
        sb.append("  試験科目: ").append(nullToEmpty(s.subjects)).append("\n");
        sb.append("  試験科目特記: ").append(nullToEmpty(s.alternateSubjects)).append("\n");
        sb.append("  面接有無: ").append(nullToEmpty(s.interview)).append("\n");
        sb.append("  英語資格優遇: ").append(nullToEmpty(s.englishQualificationBenefit)).append("\n");
        sb.append("  備考: ").append(nullToEmpty(s.notes)).append("\n");
        sb.append("  学校リンク: ").append(nullToEmpty(s.infoLink)).append("\n\n");
    }

    sb.append("""
            Please answer in this style:
            1. 最初に全体コメントを2〜4文で書く
            2. その後、「候補校:」として学校名を箇条書き
            3. 各学校ごとに理由を書く
            4. 最後に「注意点」を短く書く
            """);

    return sb.toString();
}

private static String nullToEmpty(String s) {
    return s == null ? "" : s;
}

    private static String getAppPassword() {
        return null;
        }


    private static String buildAuthToken() {
        return URLEncoder.encode(getAppPassword(), StandardCharsets.UTF_8);
    }

    private static boolean isAuthenticated(io.helidon.webserver.http.ServerRequest req) {
            return true;
        }


private static String loginPageHtml(String message) {
    String safeMessage = message == null ? "" : escapeHtml(message);

    return """
            <html>
            <head>
              <meta charset="UTF-8">
              <title>ログイン</title>
              <style>
                body {
                  font-family: Arial, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif;
                  background: #f7f7f7;
                  margin: 0;
                  padding: 0;
                }
                .login-box {
                  width: 420px;
                  max-width: calc(100% - 40px);
                  margin: 80px auto;
                  background: white;
                  border: 1px solid #ddd;
                  border-radius: 8px;
                  padding: 24px;
                  box-sizing: border-box;
                  box-shadow: 0 2px 10px rgba(0,0,0,0.08);
                }
                h2 {
                  margin-top: 0;
                  margin-bottom: 16px;
                }
                .message {
                  color: #c62828;
                  min-height: 1.2em;
                  margin-bottom: 12px;
                }
                input[type="password"] {
                  width: 100%;
                  padding: 10px;
                  font-size: 14px;
                  box-sizing: border-box;
                  margin-bottom: 12px;
                }
                button {
                  padding: 10px 16px;
                  font-size: 14px;
                  cursor: pointer;
                }
              </style>
            </head>
            <body>
              <div class="login-box">
                <h2>学校一覧サイト ログイン</h2>
                <div class="message">"""
            + safeMessage +
            """
                </div>
                <form method="GET" action="/do-login">
                  <input type="password" name="password" placeholder="パスワードを入力">
                  <button type="submit">ログイン</button>
                </form>
              </div>
            </body>
            </html>
            """;
}
    static void routing(HttpRouting.Builder routing) {
        SchoolRepository repository = new SchoolRepository();
        SchoolV2Repository repositoryV2 = new SchoolV2Repository();
        PageViewStore pageViews = PageViewStore.fromEnvironment();
        AdminAnalyticsService.register(routing, pageViews, System.getenv("ANALYTICS_ADMIN_TOKEN"));

        AdminSchoolV2Service.register(routing);
        routing
                .register("/greet", new GreetService())

                .get("/simple-greet", (req, res) -> res.send("Hello World!"))

.get("/ai/test", (req, res) -> {
            try {
                String answer = callGemini("Say hello in Japanese in a friendly way.");
                res.header("Content-Type", "application/json; charset=UTF-8");
                res.send("{\"answer\":\"" +
                        escapeHtml(answer)
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n") +
                        "\"}");
            } catch (Exception e) {
                res.status(500);
                res.send("AI test failed: " + e.getMessage());
            }
        })


                .get("/login", (req, res) -> {
                    String error = req.query().first("error").orElse("");
                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(loginPageHtml(error));
                })

                .get("/do-login", (req, res) -> {
                    String password = req.query().first("password").orElse("");

                    if (getAppPassword().equals(password)) {
                        res.header("Set-Cookie",
                                LOGIN_COOKIE_NAME + "=" + buildAuthToken() + "; Path=/; HttpOnly; SameSite=Lax");
                        res.status(302);
                        res.header("Location", "/schools-v2-table");
                        res.send();
                    } else {
                        String errorMessage = URLEncoder.encode("パスワードが違います。", StandardCharsets.UTF_8);
                        res.status(302);
                        res.header("Location", "/login?error=" + errorMessage);
                        res.send();
                    }
                })

.post("/ai/recommend-schools", (req, res) -> {
    try {
        String body = req.content().as(String.class);

        String question = body;
        String marker = "\"question\":\"";
        int start = body.indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            int end = body.indexOf("\"", start);
            if (end > start) {
                question = body.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
        }

        List<SchoolV2> schools = repositoryV2.findAll();
        String prompt = buildSchoolDataPrompt(schools, question);
        String answer = callGemini(prompt);

        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{\"answer\":\"" + jsonEscape(answer) + "\"}");
    } catch (Exception e) {
        res.status(500);
        res.send("AI recommend failed: " + e.getMessage());
    }
}) 
               .get("/logout", (req, res) -> {
                    res.header("Set-Cookie",
                            LOGIN_COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                    res.status(302);
                    res.header("Location", "/login");
                    res.send();
                })

                .get("/schools", (req, res) -> {
                    String name = req.query().first("name").orElse(null);

                    if (name == null || name.isBlank()) {
                        List<School> schools = repository.findAll();
                        res.send(schools);
                    } else {
                        List<School> schools = repository.findByName(name);
                        res.send(schools);
                    }
                })

                .get("/schools-v2", (req, res) -> {
                    String name = req.query().first("name").orElse(null);

                    if (name == null || name.isBlank()) {
                        List<SchoolV2> schools = repositoryV2.findAll();
                        res.send(schools);
                    } else {
                        List<SchoolV2> schools = repositoryV2.findByName(name);
                        res.send(schools);
                    }
                })

.get("/api/version", (req, res) -> {
    try {
        Path jsonPath = DbInit.getExternalJsonPath();

        String dataVersion = "unknown";
        if (Files.exists(jsonPath)) {
            dataVersion = Files.getLastModifiedTime(jsonPath)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        int recordCount = repositoryV2.findAll().size();

        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{"
                + "\"dataVersion\":\"" + dataVersion + "\","
                + "\"recordCount\":" + recordCount
                + "}");
    } catch (Exception e) {
        e.printStackTrace();
        res.status(500);
        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{\"success\":false,\"message\":\"Failed to get version info\"}");
    }
})

                .get("/schools-table", (req, res) -> {
                    List<School> schools = repository.findAll();

                    StringBuilder html = new StringBuilder();
                    html.append("""
                            <html>
                            <head>
                              <meta charset="UTF-8">
                              <title>Schools</title>
                              <style>
                                body { font-family: Arial, sans-serif; margin: 20px; }
                                table { border-collapse: collapse; width: 100%; }
                                th, td {
                                  border: 1px solid #999;
                                  padding: 8px;
                                  text-align: left;
                                  vertical-align: top;
                                }
                                th { background-color: #f0f0f0; }
                                tr:nth-child(even) { background-color: #fafafa; }
                              </style>
                            </head>
                            <body>
                              <h2>School List</h2>
                              <table>
                                <tr>
                                  <th>ID</th>
                                  <th>Exam Date</th>
                                  <th>School Name</th>
                                  <th>Class Name</th>
                                  <th>Capacity</th>
                                  <th>Subjects</th>
                                  <th>English Only</th>
                                  <th>Application Deadline / Docs</th>
                                  <th>Benefits</th>
                                  <th>Remarks</th>
                                </tr>
                            """);

                    for (School s : schools) {
                        html.append("<tr>")
                                .append("<td>").append(s.id).append("</td>")
                                .append("<td>").append(escapeHtml(formatDateForDisplay(s.examDate))).append("</td>")
                                .append("<td>").append(escapeHtml(s.schoolName)).append("</td>")
                                .append("<td>").append(escapeHtml(s.className)).append("</td>")
                                .append("<td>").append(escapeHtml(s.capacity)).append("</td>")
                                .append("<td>").append(escapeHtml(s.subjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.englishOnly)).append("</td>")
                                .append("<td>").append(escapeHtml(s.applicationDeadlineDocs)).append("</td>")
                                .append("<td>").append(escapeHtml(s.benefits)).append("</td>")
                                .append("<td>").append(escapeHtml(s.remarks)).append("</td>")
                                .append("</tr>");
                    }

                    html.append("""
                              </table>
                            </body>
                            </html>
                            """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(html.toString().getBytes(StandardCharsets.UTF_8));
                })

                .get("/api/schools", (req, res) -> {
                    List<SchoolV2> schools = repositoryV2.findAll();
                    res.send(schools);
                })

                .get("/schools-v2-table", (req, res) -> {
                    if (!isAuthenticated(req)) {
                        res.status(302);
                        res.header("Location", "/login");
                        res.send();
                        return;
                    }

                    List<SchoolV2> schools = repositoryV2.findAll();

                    StringBuilder html = new StringBuilder();
                    html.append("""
                            <html>
                            <head>
                              <meta charset="UTF-8">
                              <meta name="viewport" content="width=device-width, initial-scale=1">
                              <title>__ADMISSION_HEADING__</title>
                              <style>
                                * { box-sizing: border-box; }
                                body {
                                  font-family: Arial, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif;
                                  margin: 10px;
                                  font-size: 12px;
                                  line-height: 1.45;
                                }
                                h2 { font-size: 18px; margin: 0 0 8px; }
                                .cohort-info { background: #eef6fa; border-left: 3px solid #176b68; padding: 6px 9px; margin-bottom: 8px; }
                                .cohort-info p { margin: 4px 0 0; }
                                .review-note { font-size: 10px; color: #526875; margin-top: 4px; }
                                .deviation-reference { border-top: 1px solid #ccd8df; margin-top: 6px; padding-top: 5px; font-size: 10px; line-height: 1.4; }
                                .deviation-reference p { margin: 3px 0; }
                                .toolbar { margin-bottom: 8px; }
                                #searchBox { width: 340px; max-width: 100%; padding: 5px 7px; font-size: 12px; }
                                #schoolTable { border-collapse: collapse; width: 100%; table-layout: fixed; font-size: 11px; }
                                #schoolTable th, #schoolTable td {
                                  border: 1px solid #b6bfc4;
                                  padding: 4px 3px;
                                  text-align: left;
                                  vertical-align: top;
                                  overflow-wrap: anywhere;
                                }
                                #schoolTable th:nth-child(1) { width: 2.5%; }
                                #schoolTable th:nth-child(2) { width: 9%; }
                                #schoolTable th:nth-child(3) { width: 5.5%; }
                                #schoolTable th:nth-child(4) { width: 7%; }
                                #schoolTable th:nth-child(5) { width: 8%; }
                                #schoolTable th:nth-child(6) { width: 10%; }
                                #schoolTable th:nth-child(7) { width: 12%; }
                                #schoolTable th:nth-child(8) { width: 7%; }
                                #schoolTable th:nth-child(9) { width: 8%; }
                                #schoolTable th:nth-child(10) { width: 15%; }
                                #schoolTable th:nth-child(11) { width: 6%; }
                                #schoolTable th:nth-child(12) { width: 6%; }
                                #schoolTable th:nth-child(13) { width: 4%; }
                                #schoolTable td:nth-child(13) { font-size: 10px; }
                                th { background-color: #edf1f3; cursor: pointer; user-select: none; position: sticky; top: 0; }
                                tr:nth-child(even) { background-color: #fafafa; }
                                th.sort-asc::after { content: " ▲"; font-size: 9px; }
                                th.sort-desc::after { content: " ▼"; font-size: 9px; }
                                #schoolTable th.category-header { z-index: 2; cursor: default; }
                                .category-menu summary { cursor: pointer; list-style: none; }
                                .category-menu summary::-webkit-details-marker { display: none; }
                                .category-menu summary::after { content: " ▾"; }
                                .category-menu summary:focus-visible { outline: 2px solid #176b68; }
                                .category-panel { position: absolute; top: 100%; left: 0; width: 200px; padding: 10px; background: white; border: 1px solid #9baeb9; box-shadow: 0 3px 8px #0002; font-size: 12px; font-weight: normal; }
                                .category-panel label { display: block; margin-bottom: 8px; }
                                .category-panel label:last-child { margin-bottom: 0; }
                                .category-panel select { display: block; width: 100%; margin-top: 4px; padding: 4px; font: inherit; }
                                .deviation-panel { left: auto; right: 0; width: min(260px, calc(100vw - 24px)); }
                                .filter-help { margin: 8px 0 0; font-size: 10px; line-height: 1.5; color: #526875; }
                                #visibleCount { margin-left: 8px; font-size: 11px; color: #526875; }
                                a { color: #0645ad; text-decoration: none; }
                                a:hover { text-decoration: underline; }
                              </style>
                            </head>
                            <body>
                              <h2>__ADMISSION_HEADING__</h2>
                              <section class="report-links" aria-label="受験最終レポート" style="background:#fff8e8;border:1px solid #e7d7ad;border-radius:6px;padding:10px;margin:8px 0;">
                                <strong>2026年度受験（現中１娘）の受験最終レポート２本を公開中（有料）</strong>
                                <ul style="margin:6px 0 0;padding-left:20px;">
                                  <li><a href="https://note.com/naiveprince0507/n/n3ff1eed03e00?app_launch=false" target="_blank" rel="noopener noreferrer">「中受英語受験とは何なのか？」</a></li>
                                  <li><a href="https://note.com/naiveprince0507/n/nff78aea25f7f?app_launch=false" target="_blank" rel="noopener noreferrer">「英検＆TOEFLの全成績及び、出願7校の選び方を全部書く」</a></li>
                                </ul>
                              </section>
                              __ADMISSION_SUMMARY__
                              <div style="margin-bottom: 12px;">
                                <a href="/logout">ログアウト</a>
                                <span aria-hidden="true"> ／ </span><a href="/admin/analytics">管理者用：閲覧統計</a>
                              </div>


                              <div class="toolbar">
                                <input type="text" id="searchBox" placeholder="学校名・入試分類・試験日・備考などで検索">
                                <span id="visibleCount" role="status" aria-live="polite"></span>
                              </div>

                              <table id="schoolTable">
                                <thead>
                                  <tr>
                                    <th onclick="sortTable(0, true)">ID</th>
                                    <th onclick="sortTable(1, false)">学校名</th>
                                    <th class="category-header" aria-sort="none">
                                      <details class="category-menu" id="categoryMenu">
                                        <summary>入試分類（帰国入試or 英語入試）</summary>
                                        <div class="category-panel">
                                          <label>並べ替え
                                            <select id="categorySort" aria-label="入試分類の並べ替え">
                                              <option value="">並べ替えを選択</option>
                                              <option value="returnee">帰国を先に表示</option>
                                              <option value="english">英語を先に表示</option>
                                              <option value="original">元の順に戻す</option>
                                            </select>
                                          </label>
                                          <label>表示する分類
                                            <select id="categoryFilter" aria-label="入試分類の絞り込み">
                                              <option value="all">すべて</option>
                                              <option value="帰国">帰国のみ</option>
                                              <option value="英語">英語のみ</option>
                                              <option value="unconfirmed">未確認のみ</option>
                                            </select>
                                          </label>
                                        </div>
                                      </details>
                                    </th>
                                    <th onclick="sortTable(3, false)">募集人数</th>
                                    <th onclick="sortTable(4, false)">試験日</th>
                                    <th onclick="sortTable(5, false)">試験科目</th>
                                    <th onclick="sortTable(6, false)">試験科目特記</th>
                                    <th onclick="sortTable(7, false)">面接有無</th>
                                    <th onclick="sortTable(8, false)">英語資格優遇</th>
                                    <th class="category-header" aria-sort="none">
                                      <details class="category-menu" id="deviationMenu">
                                        <summary>備考（４科目受験の場合の四谷大塚偏差値を参考に併記）</summary>
                                        <div class="category-panel deviation-panel">
                                          <label>四谷偏差値の範囲
                                            <select id="deviationFilter" aria-label="四谷偏差値の範囲">
                                              <option value="all">すべて</option>
                                              <option value="under45">45未満</option>
                                              <option value="45to55">45以上55未満</option>
                                              <option value="55to65">55以上65未満</option>
                                              <option value="65plus">65以上</option>
                                              <option value="missing">未掲載・未確認のみ</option>
                                            </select>
                                          </label>
                                          <label>四谷偏差値の並べ替え
                                            <select id="deviationSort" aria-label="四谷偏差値の並べ替え">
                                              <option value="">並べ替えを選択</option>
                                              <option value="asc">偏差値の低い順</option>
                                              <option value="desc">偏差値の高い順</option>
                                              <option value="original">元の順に戻す</option>
                                            </select>
                                          </label>
                                          <p class="filter-help">複数の掲載値がある場合、範囲はどれか１つが該当すれば表示します。並べ替えは掲載値の最大値を使います。一般入試の参考値です。</p>
                                        </div>
                                      </details>
                                    </th>
                                    <th onclick="sortTable(10, false)">学校リンク・出典</th>
                                    <th onclick="sortTable(11, false)">公式資料の確認状況</th>
                                    <th onclick="sortTable(12, false)">資料確認日時</th>
                                  </tr>
                                </thead>
                                <tbody>
                            """.replace("__ADMISSION_SUMMARY__", admissionsSummary(schools))
                                    .replace("__ADMISSION_HEADING__", escapeHtml(admissionsHeading(schools))));

                    for (SchoolV2 s : schools) {
                        html.append("<tr data-deviation-values=\"").append(schoolDeviationValues(s)).append("\">")
                                .append("<td>").append(s.id).append("</td>")
                                .append("<td>").append(escapeHtml(s.schoolName)).append("</td>")
                                .append("<td>").append(escapeHtml(s.category != null && s.category.contains("帰国")
                                        ? "帰国" : s.category != null && s.category.contains("英語") ? "英語" : s.category)).append("</td>")
                                .append("<td>").append(escapeHtml(s.capacity)).append("</td>")
                                .append("<td>").append(escapeHtml(formatDateForDisplay(s.examDates))).append("</td>")
                                .append("<td>").append(escapeHtml(s.subjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.alternateSubjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.interview)).append("</td>")
                                .append("<td>").append(escapeHtml(s.englishQualificationBenefit)).append("</td>")
                                .append("<td>").append(schoolNotes(s)).append("</td>")
                                .append("<td>")
                                .append(schoolLinks(s))
                                .append("</td>")
                                .append("<td>").append(escapeHtml(s.verificationStatusLabel))
                                .append("<div class=\"review-note\">").append(escapeHtml(s.verificationNote)).append("</div></td>")
                                .append("<td>").append(escapeHtml(s.verifiedAt)).append("</td>")
                                .append("</tr>");
                    }


html.append("""
            </tbody>
          </table>

          <script>
            const searchBox = document.getElementById('searchBox');
            const table = document.getElementById('schoolTable');
            const tbody = table.querySelector('tbody');
            let sortDirections = {};
            const originalRows = Array.from(tbody.querySelectorAll('tr'));
            const categorySort = document.getElementById('categorySort');
            const categoryFilter = document.getElementById('categoryFilter');
            const categoryMenu = document.getElementById('categoryMenu');
            const categoryHeader = categoryMenu.closest('th');
            const deviationMenu = document.getElementById('deviationMenu');
            const deviationHeader = deviationMenu.closest('th');
            const deviationFilter = document.getElementById('deviationFilter');
            const deviationSort = document.getElementById('deviationSort');
            const deviationValues = new Map(originalRows.map(row => [row,
              (row.dataset.deviationValues || '').split(',').filter(Boolean).map(Number)
                .filter(value => Number.isInteger(value) && value >= 1 && value <= 100)]));

            function matchesDeviation(row) {
              const range = deviationFilter.value;
              const values = deviationValues.get(row);
              if (range === 'all') return true;
              if (range === 'missing') return values.length === 0;
              return values.some(value => range === 'under45' ? value < 45
                : range === '45to55' ? value >= 45 && value < 55
                : range === '55to65' ? value >= 55 && value < 65
                : range === '65plus' && value >= 65);
            }


            function applyFilters() {
              const keyword = searchBox.value.trim().toLowerCase();
              const filter = categoryFilter.value;
              let visible = 0;
              originalRows.forEach(row => {
                const category = row.children[2].textContent.trim();
                const matchesCategory = filter === 'all' || (filter === 'unconfirmed'
                  ? !['帰国', '英語'].includes(category) : category === filter);
                const show = matchesCategory && matchesDeviation(row) && row.textContent.toLowerCase().includes(keyword);
                row.style.display = show ? '' : 'none';
                if (show) visible++;
              });
              document.getElementById('visibleCount').textContent = visible + ' / ' + originalRows.length + ' 件';
            }

            searchBox.addEventListener('input', applyFilters);
            categoryFilter.addEventListener('change', applyFilters);
            deviationFilter.addEventListener('change', applyFilters);
            categorySort.addEventListener('change', () => {
              const order = categorySort.value;
              if (!order) return;
              deviationSort.value = '';
              const rows = [...originalRows];
              const first = order === 'english' ? '英語' : '帰国';
              const second = first === '英語' ? '帰国' : '英語';
              const rank = row => {
                const category = row.children[2].textContent.trim();
                return category === first ? 0 : category === second ? 1 : 2;
              };
              if (order !== 'original') rows.sort((a, b) => rank(a) - rank(b));
              table.querySelectorAll('th').forEach(th => {
                th.classList.remove('sort-asc', 'sort-desc');
                th.setAttribute('aria-sort', 'none');
              });
              categoryHeader.setAttribute('aria-sort', order === 'original' ? 'none' : 'other');
              sortDirections = {};
              rows.forEach(row => tbody.appendChild(row));
            });
            deviationSort.addEventListener('change', () => {
              const order = deviationSort.value;
              if (!order) return;
              categorySort.value = '';
              const rows = [...originalRows];
              const key = row => deviationValues.get(row).length ? Math.max(...deviationValues.get(row)) : null;
              if (order !== 'original') rows.sort((a, b) => {
                const av = key(a), bv = key(b);
                if (av === null) return bv === null ? 0 : 1;
                if (bv === null) return -1;
                return order === 'asc' ? av - bv : bv - av;
              });
              table.querySelectorAll('th').forEach(th => {
                th.classList.remove('sort-asc', 'sort-desc');
                th.setAttribute('aria-sort', 'none');
              });
              deviationHeader.setAttribute('aria-sort', order === 'original' ? 'none' : order === 'asc' ? 'ascending' : 'descending');
              sortDirections = {};
              rows.forEach(row => tbody.appendChild(row));
            });
            const filterMenus = [categoryMenu, deviationMenu];
            document.addEventListener('click', event => {
              filterMenus.forEach(menu => { if (!menu.contains(event.target)) menu.open = false; });
            });
            filterMenus.forEach(menu => {
              menu.addEventListener('toggle', () => {
                if (menu.open) filterMenus.forEach(other => { if (other !== menu) other.open = false; });
              });
              menu.addEventListener('keydown', event => {
                if (event.key === 'Escape') {
                  menu.open = false;
                  menu.querySelector('summary').focus();
                }
              });
            });
            applyFilters();

            function sortTable(colIndex, numeric) {
              categorySort.value = '';
              deviationSort.value = '';
              const rows = Array.from(tbody.querySelectorAll('tr'));
              const headers = table.querySelectorAll('th');
              const asc = !sortDirections[colIndex];
              sortDirections = {};
              sortDirections[colIndex] = asc;

              headers.forEach(th => {
                th.classList.remove('sort-asc', 'sort-desc');
                th.setAttribute('aria-sort', 'none');
              });
              headers[colIndex].classList.add(asc ? 'sort-asc' : 'sort-desc');
              headers[colIndex].setAttribute('aria-sort', asc ? 'ascending' : 'descending');

              rows.sort((a, b) => {
                const aText = a.children[colIndex].innerText.trim();
                const bText = b.children[colIndex].innerText.trim();

                if (numeric) {
                  const aNum = parseFloat(aText) || 0;
                  const bNum = parseFloat(bText) || 0;
                  return asc ? aNum - bNum : bNum - aNum;
                }

                return asc
                  ? aText.localeCompare(bText, 'ja')
                  : bText.localeCompare(aText, 'ja');
              });

              
                      rows.forEach(row => tbody.appendChild(row));
            }
          </script>
        </body>
        </html>
        """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    if (AdminAnalyticsService.countable(req)) pageViews.recordView();
                    res.send(html.toString().getBytes(StandardCharsets.UTF_8));
                });
    }
}
