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
                                .append("<td>").append(escapeHtml(s.examDate)).append("</td>")
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
                              <title>Schools V2</title>
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
                              <h2>School List V2</h2>
                              <table>
                                <tr>
                                  <th>ID</th>
                                  <th>School Name</th>
                                  <th>Category</th>
                                  <th>Capacity</th>
                                  <th>Exam Dates</th>
                                  <th>Subjects</th>
                                  <th>Alternate Subjects</th>
                                  <th>Interview</th>
                                  <th>English Qualification Benefit</th>
                                  <th>Notes</th>
<th>Info Link</th>
                                </tr>
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
                               // .append("<td>").append(escapeHtml(s.notes)).append("</td>")
                               // .append("</tr>");
.append("<td>").append(escapeHtml(s.notes)).append("</td>")
.append("<td>")
.append(s.infoLink == null || s.infoLink.isBlank()
        ? ""
        : "<a href=\"" + escapeHtml(s.infoLink) + "\" target=\"_blank\">Link</a>")
.append("</td>")
.append("</tr>");
                    }

                    html.append("""
                              </table>
                            </body>
                            </html>
                            """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(html.toString());
                });
    }
}
