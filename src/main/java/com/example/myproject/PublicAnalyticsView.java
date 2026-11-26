package com.example.myproject;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/** The public page exposes aggregate counts only, never connection or admin details. */
final class PublicAnalyticsView {
    private PublicAnalyticsView() { }

    static String render(PageViewStore store) {
        try {
            var totals = store.snapshot();
            var numbers = NumberFormat.getIntegerInstance(Locale.JAPAN);
            return "<aside class=\"public-views\" aria-label=\"閲覧統計\">"
                    + "<div class=\"views-caption\">閲覧回数（日本時間）</div><dl>"
                    + card("今日", numbers.format(totals.today()))
                    + card("今月", numbers.format(totals.month()))
                    + card("累計", numbers.format(totals.total()))
                    + "</dl><div class=\"views-note\">再読み込み・自動アクセスを含む"
                    + (totals.lastRecordingFailure() == null ? "" : "／一部未集計の可能性あり")
                    + "</div></aside>";
        } catch (SQLException unavailable) {
            return "<aside class=\"public-views\" aria-label=\"閲覧統計\">"
                    + "<div class=\"views-caption\">閲覧回数（日本時間）</div>"
                    + "<p class=\"views-note\">現在、集計を表示できません。</p></aside>";
        }
    }

    private static String card(String label, String value) {
        return "<div><dt>" + label + "</dt><dd>" + value + "<small> 回</small></dd></div>";
    }
}
