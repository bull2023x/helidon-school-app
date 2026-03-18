package com.example.myproject;

import io.helidon.common.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import java.util.List;

public class Main {

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
    static void routing(HttpRouting.Builder routing) {
        SchoolRepository repository = new SchoolRepository();
        SchoolV2Repository repositoryV2 = new SchoolV2Repository();

        routing
                .register("/greet", new GreetService())
                .get("/simple-greet", (req, res) -> res.send("Hello World!"))

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
                               // .append("<td>").append(escapeHtml(s.examDate)).append("</td>")
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
                    res.send(html.toString());
                })

                .get("/schools-v2-table", (req, res) -> {
                    List<SchoolV2> schools = repositoryV2.findAll();

                    StringBuilder html = new StringBuilder();
                    html.append("""
                            <html>
                            <head>
                              <meta charset="UTF-8">
                              <title>学校一覧 V2</title>
                              <style>
                                body {
                                  font-family: Arial, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif;
                                  margin: 20px;
                                }
                                h2 {
                                  margin-bottom: 12px;
                                }
                                .toolbar {
                                  margin-bottom: 16px;
                                }
                                #searchBox {
                                  width: 380px;
                                  max-width: 100%;
                                  padding: 8px 10px;
                                  font-size: 14px;
                                  box-sizing: border-box;
                                }
                                table {
                                  border-collapse: collapse;
                                  width: 100%;
                                }
                                th, td {
                                  border: 1px solid #999;
                                  padding: 8px;
                                  text-align: left;
                                  vertical-align: top;
                                }
                                th {
                                  background-color: #f0f0f0;
                                  cursor: pointer;
                                  user-select: none;
                                  position: sticky;
                                  top: 0;
                                }
                                tr:nth-child(even) {
                                  background-color: #fafafa;
                                }
                                th.sort-asc::after {
                                  content: " ▲";
                                  font-size: 12px;
                                }
                                th.sort-desc::after {
                                  content: " ▼";
                                  font-size: 12px;
                                }
                                a {
                                  color: #0645ad;
                                  text-decoration: none;
                                }
                                a:hover {
                                  text-decoration: underline;
                                }
                              </style>
                            </head>
                            <body>
                              <h2>英語での中学受験校リスト_2026年3月版_v2</h2>

                              <div class="toolbar">
                                <input type="text" id="searchBox" placeholder="学校名・入試分類・試験日・備考などで検索">
                              </div>

                              <table id="schoolTable">
                                <thead>
                                  <tr>
                                    <th onclick="sortTable(0, false)">ID</th>
                                    <th onclick="sortTable(1, false)">学校名</th>
                                    <th onclick="sortTable(2, false)">入試分類</th>
                                    <th onclick="sortTable(3, false)">募集人数</th>
                                    <th onclick="sortTable(4, false)">試験日</th>
                                    <th onclick="sortTable(5, false)">試験科目</th>
                                    <th onclick="sortTable(6, false)">試験科目特記</th>
                                    <th onclick="sortTable(7, false)">面接有無</th>
                                    <th onclick="sortTable(8, false)">英語資格優遇</th>
                                    <th onclick="sortTable(9, false)">備考</th>
                                    <th onclick="sortTable(10, false)">学校リンク</th>
                                  </tr>
                                </thead>
                                <tbody>
                            """);

                    for (SchoolV2 s : schools) {
                        html.append("<tr>")
                                .append("<td>").append(s.id).append("</td>")
                                .append("<td>").append(escapeHtml(s.schoolName)).append("</td>")
                                .append("<td>").append(escapeHtml(s.category)).append("</td>")
                                .append("<td>").append(escapeHtml(s.capacity)).append("</td>")
                                .append("<td>").append(escapeHtml(s.examDates)).append("</td>")
                                .append("<td>").append(escapeHtml(s.subjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.alternateSubjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.interview)).append("</td>")
                                .append("<td>").append(escapeHtml(s.englishQualificationBenefit)).append("</td>")
                                .append("<td>").append(escapeHtml(s.notes)).append("</td>")
                                .append("<td>")
                                .append(s.infoLink == null || s.infoLink.isBlank()
                                        ? ""
                                        : "<a href=\"" + escapeHtml(s.infoLink) + "\" target=\"_blank\" rel=\"noopener noreferrer\">リンク</a>")
                                .append("</td>")
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

                                searchBox.addEventListener('keyup', function() {
                                  const keyword = this.value.toLowerCase();
                                  const rows = tbody.querySelectorAll('tr');

                                  rows.forEach(row => {
                                    const text = row.innerText.toLowerCase();
                                    row.style.display = text.includes(keyword) ? '' : 'none';
                                  });
                                });

                                function sortTable(colIndex, numeric) {
                                  const rows = Array.from(tbody.querySelectorAll('tr'));
                                  const headers = table.querySelectorAll('th');
                                  const asc = !sortDirections[colIndex];
                                  sortDirections = {};
                                  sortDirections[colIndex] = asc;

                                  headers.forEach(th => {
                                    th.classList.remove('sort-asc', 'sort-desc');
                                  });
                                  headers[colIndex].classList.add(asc ? 'sort-asc' : 'sort-desc');

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

                                // ID列だけ数値ソートにしたいので差し替え
                                document.querySelectorAll('#schoolTable th')[0]
                                  .setAttribute('onclick', 'sortTable(0, true)');
                              </script>
                            </body>
                            </html>
                            """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(html.toString());
                });
    }
}
