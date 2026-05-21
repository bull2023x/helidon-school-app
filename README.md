# helidon-school-app

中学受験「英語入試・帰国生入試」情報アプリ
Helidon + Render + GitHub Actions による半自動更新システム。

---

# 概要

本システムは：

* 帰国生入試
* 英語入試
* 国際系中学校

を対象とした、

```text
学校情報検索
+
学校一覧表示
+
自動巡回監視
+
半自動更新
```

システムです。

現在：

* 約110校
* 毎週自動巡回
* 自動バックアップ
* Render自動デプロイ
* Androidアプリ連携

まで対応しています。

---

# システム構成

```text
GitHub Actions
        ↓
Python巡回
(requests + BeautifulSoup)
        ↓
schools-v2.json 更新
        ↓
archive バックアップ
        ↓
GitHub commit
        ↓
Render Auto Deploy
        ↓
Helidon API
        ↓
Android App / Web
```

---

# リポジトリ構成

```text
.
├── .github/workflows/
├── archive/
├── data/
├── scripts/
├── src/
├── Dockerfile
├── pom.xml
└── README.md
```

---

# JSON管理

## 正本JSON

現在の唯一の正本：

```text
data/schools-v2.json
```

---

# 自動バックアップ

毎週：

```text
archive/schools-v2-YYYY-MM-DD.json
```

へ自動保存。

例：

```text
archive/schools-v2-2026-05-21.json
```

---

# GitHub Actions

## workflow

```text
.github/workflows/update-schools.yml
```

---

# 実行タイミング

毎週：

```text
月曜 AM3:00 JST
```

実行。

---

# 実行内容

GitHub Actions は：

* 学校URL巡回
* HTML取得
* requests
* BeautifulSoup
* タイトル抽出
* check-report.json生成
* archiveバックアップ
* GitHub commit

を実行。

---

# Python

## スクリプト

```text
scripts/update_schools.py
```

---

# requirements.txt

```text
requests
beautifulsoup4
```

---

# 文字化け対策

以下を実装済。

```python
response.encoding = response.apparent_encoding
```

---

# check-report.json

生成場所：

```text
data/check-report.json
```

---

# 主なエラー分類

## SSL_ERROR

学校側SSL証明書問題。

放置OK。

---

## MISSING_URL

infoLink未設定。

必要時のみ手修正。

---

## UNKNOWN_ERROR

要確認。

---

# 半自動運用

## 毎週やること（5分）

### ① GitHub Actions確認

```text
GitHub
↓
Actions
↓
Update schools JSON
```

---

### ② Success確認

緑ならOK。

---

### ③ Run updater確認

以下を見る：

* SSL_ERROR
* MISSING_URL
* UNKNOWN_ERROR

---

### ④ 必要校だけ修正

GitHub GUIで：

```text
data/schools-v2.json
```

を直接編集。

---

### ⑤ Commit

GitHub GUI上で：

```text
Commit changes
```

---

# Render

## 自動デプロイ

Render は：

```text
Auto Deploy = On Commit
```

設定。

GitHub push 後、自動デプロイ。

---

# Renderで重要な構成

## Dockerfile

現在：

```dockerfile
COPY data ./data
```

を実装済。

---

# なぜ必要？

Render で：

```text
/helidon/data/schools-v2.json
```

を external file として読むため。

---

# DbInit.java

## external JSON優先

```java
private static final Path EXTERNAL_JSON_PATH =
        Paths.get(System.getProperty("user.dir"), "data", "schools-v2.json");
```

---

# 読み込み順

```text
external file
↓
classpath fallback
```

---

# Renderログ確認

成功時：

```text
external exists = true
Loaded schools-v2 from external file
```

---

# Android App

別リポジトリ：

```text
SchoolViewer
```

---

# Android構成

* Kotlin
* Jetpack Compose
* Retrofit
* Material3

---

# Google Play

## クローズドテスト対応済

現在：

* upload key reset済
* App Bundle対応済
* 署名鍵対応済

---

# 署名鍵

## 保存場所

```text
SchoolViewer/app/release-keystore.jks
```

---

# .gitignore

必須：

```gitignore
*.jks
*.keystore
*.pem
```

---

# App Bundle生成

```bash
./gradlew bundleRelease
```

---

# AAB生成場所

```text
app/build/outputs/bundle/release/app-release.aab
```

---

# Render再デプロイ

必要時：

```text
Manual Deploy
↓
Clear build cache & deploy latest commit
```

---

# 実運用思想

本システムは：

```text
完全自動更新
```

ではなく、

```text
半自動監視＋人確認
```

を採用。

---

# 理由

学校サイトは：

* HTML変更
* PDF変更
* Cloudflare
* URL変更
* SSL問題

が頻発するため。

---

# 現在の完成状態

✅ GitHub Actions
✅ BeautifulSoup巡回
✅ archiveバックアップ
✅ Git履歴保存
✅ Render自動デプロイ
✅ external JSON読込
✅ Android App連携
✅ Google Play対応
✅ 半自動更新運用

---

# 今後の候補

* 差分通知
* Slack通知
* PR生成
* PDF解析強化
* AI要約

---

