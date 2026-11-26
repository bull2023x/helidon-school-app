
````md
# helidon-school-app

中学受験「英語入試・帰国生入試」情報アプリ。

Helidon + Render + GitHub Actions による、学校情報の半自動更新システムです。

---

# 概要

本システムは、以下を対象にした学校情報管理・公開アプリです。

```text
帰国生入試
英語入試
国際系中学校
````

主な機能は以下です。

```text
学校検索
学校一覧表示
学校情報監視
半自動更新
Render公開
Android App連携
JavaFX Desktop Editorによる手動編集
```

現在は、以下まで対応しています。

```text
約110校
GitHub Actions 自動巡回
archive 自動バックアップ
Render 自動デプロイ
Helidon API
Android App 連携
JavaFX Desktop Editor 連携
```

---

# 2027年度入試情報への更新（2026年11月）

対象は2027年4月入学（2026年秋〜2027年春の入試）です。
公式資料との照合結果は、110件中、日程・人数確認済み96件、一部確認11件、未確認3件です。
未確認は未公表という意味ではありません。学校全体・コース全体・他方式と共通の定員は、その範囲を記載しています。
学校リンク欄に確認した公式資料、表に資料確認日時・確認状況を表示します。

2026年11月25日の追加確認で、科目・試験科目特記は107件に公式出典を付けました。面接は78件、英語資格の出願条件・換算・加点・免除は37件を確認しています。未確認は「制度なし」を意味しません。かえつ有明は国内・海外、Honors/Advanced・Regularの違いを分けて記載しています。國學院久我山はPDF取得制限、金沢大附属は接続失敗により照合未了です。東大附属は帰国・英語専用区分を確定できていません。桐蔭学園のグローバル入試は公式予定版と明記しています。

- 正本：`data/schools-v2.json`
- 初回確認記録：`data/admissions-review-2027.json`
- PDF・画像まで含めた追加確認記録：`data/admissions-deep-review-2027.json`（全110件の確認項目・未確認項目・出典）
- 同梱フォールバック：`src/main/resources/data/schools-v2.json`（正本と同期）
- `lastRetrievedAt`は通信成功日時、`verifiedAt`と`fieldVerification`は公式資料の内容確認です。
- 見出しは「<2026年11月25日更新>」のように日まで表示します。最新の取得日時・内容確認日時を日本時間に換算して使うため、週次の収集成功後は公開時に日付も変わります。取得しただけでは内容確認済みにはならず、各行の資料確認日時は別に保持します。全件取得失敗の場合、取得日付を進めません。
- 各項目の出典年度を確認し、4月には翌年4月入学向けへ切り替えます。対象年度の確認がなければ表示を未確認にします。
- AI推薦にも、画面・APIと同じ年度確認済みデータを渡します。

```sh
python scripts/update_schools.py --input data/schools-v2.json --output data/schools-v2.json --pretty --refresh
python scripts/validate_admissions.py --sync
```

`--refresh`は取得日時を保存するオプションです。要確認の抽出候補は`--apply`を付けても反映しません。
手動確認後は、項目ごとに`fieldVerification`へ`admissionYear`・`sourceUrl`・`verifiedAt`を記録してください。
既存エディターからのアップロードでは、変更されていない項目の出典を保持し、変更された値の古い確認情報は引き継ぎません。

# 半自動更新とは

本アプリの学校データは、GitHub Actions によって毎週自動で巡回・更新されます。

対象の入学年度を明記した公式HTML・PDFから更新候補を抽出します。候補は入試区分・募集枠の照合が必要なため、抽出スコアだけでは自動上書きしません。

取得失敗時は確認済み情報を保持します。ただし、対象年度の公式出典がない項目は、画面とAPIで「未確認」と表示します。前年の日付を一年繰り上げる推計はしません。

更新に失敗した学校については、GitHub Actions の `Run updater` ログや `data/check-report.json` を確認し、必要に応じて手動で修正します。

つまり、本システムは：

```text
完全自動更新
```

ではなく、

```text
半自動監視 + 人手確認
```

を採用しています。

この方式により、学校サイトの SSL 不備、URL 変更、ページ構成変更、PDF 化、Cloudflare などが起きても、更新できる学校は止めずに反映しつつ、問題のある学校だけを個別に確認できます。

---

# システム構成

```text
GitHub Actions
    ↓
Python巡回
requests + BeautifulSoup
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
Web / Android App / JavaFX Desktop Editor
```

---

# リポジトリ構成

```text
.
├── .github/workflows/
│   └── update-schools.yml
├── archive/
│   └── schools-v2-YYYYMMDD-HHMMSS.json
├── data/
│   ├── schools-v2.json
│   └── check-report.json
├── scripts/
│   └── update_schools.py
├── src/
│   └── main/
│       └── java/
├── Dockerfile
├── pom.xml
├── requirements.txt
└── README.md
```

---

# JSON管理

## 正本JSON

このリポジトリにおける学校データの正本は、次のファイルです。

```text
data/schools-v2.json
```

Render 側も、この JSON を元に学校情報 API を提供します。

---

# 自動バックアップ

GitHub Actions 実行時に、更新前後のデータを `archive/` に保存します。

例：

```text
archive/schools-v2-20260526-120049.json
archive/schools-v2-20260629-020400.json
archive/schools-v2-20260706-013306.json
```

---

# GitHub Actions

## Workflow

```text
.github/workflows/update-schools.yml
```

## 実行タイミング

毎週：

```text
月曜 AM3:00 JST
```

に自動実行します。

## GitHub Actions がやること

```text
学校URL巡回
HTML取得
タイトル解析
check-report.json 生成
archive バックアップ
data/schools-v2.json 更新
GitHub commit
Render 自動デプロイ
```

---

# Python 更新スクリプト

## スクリプト

```text
scripts/update_schools.py
```

## requirements.txt

```text
requests
beautifulsoup4
```

## 文字化け対策

学校サイトごとに文字コードが異なるため、以下のような文字化け対策を実装しています。

```python
response.encoding = response.apparent_encoding
```

---

# check-report.json

生成場所：

```text
data/check-report.json
```

このファイルには、各学校ページの巡回結果やエラー情報が出力されます。

---

# エラー分類

## SSL_ERROR

学校サイト側の SSL 設定に問題がある場合です。

多くの場合、アプリ側の問題ではありません。

急ぎでなければ放置しても構いません。

---

## MISSING_URL

`infoLink` が未設定です。

必要に応じて `data/schools-v2.json` の該当学校に URL を追加します。

---

## UNKNOWN_ERROR

要確認です。

学校サイトの構造変更、URL 変更、PDF 化、アクセス制限などの可能性があります。

---

# 毎週の確認手順

## 1. GitHub Actions を確認

```text
GitHub
↓
Actions
↓
Update schools JSON
```

## 2. Success を確認

緑の `Success` なら、workflow は正常終了しています。

## 3. Run updater を確認

以下のようなエラーがないか確認します。

```text
SSL_ERROR
MISSING_URL
UNKNOWN_ERROR
```

## 4. 必要な学校だけ修正

必要に応じて、以下を修正します。

```text
data/schools-v2.json
```

## 5. Commit

GitHub GUI またはローカル Git で commit / push します。

---

# Render

## Auto Deploy

Render は GitHub の main branch への push を検知して、自動デプロイします。

```text
Auto Deploy = On Commit
```

GitHub Actions または手動 push によって `data/schools-v2.json` が更新されると、Render 側も再デプロイされます。

---

# Render 構成

## Dockerfile

Dockerfile では、以下のように `data/` をコンテナへコピーします。

```dockerfile
COPY data ./data
```

## なぜ必要か

Helidon アプリは Render 上で次の external JSON を優先的に読み込みます。

```text
/helidon/data/schools-v2.json
```

そのため、Docker image 内に `data/schools-v2.json` が含まれている必要があります。

---

# DbInit.java

## external JSON 優先

Helidon 側では、classpath resource より external JSON を優先して読み込みます。

```java
private static final Path EXTERNAL_JSON_PATH =
        Paths.get(System.getProperty("user.dir"), "data", "schools-v2.json");
```

## JSON 読み込み順

```text
external file
↓
classpath fallback
```

## Render ログ確認

成功時は Render ログに以下のような内容が出ます。

```text
external exists = true
Loaded schools-v2 from external file
```

---

# 公開API

## 学校データ JSON API

```text
https://helidon-school-app.onrender.com/schools-v2
```

この API は、JavaFX Desktop Editor の `Download Latest Cloud DB` ボタンでも使用します。

## 学校一覧テーブル

```text
https://helidon-school-app.onrender.com/schools-v2-table
```

---

# JavaFX Desktop Editor

## 概要

JavaFX Desktop Editor は、学校データをローカルで確認・編集するためのデスクトップアプリです。

主な用途は以下です。

```text
学校データの一覧表示
JSONの手動編集
Cloud側の最新DB取得
編集内容の保存
GitHub / Render への反映補助
```

## ローカル上の場所

通常は以下のフォルダにあります。

```text
$HOME/projects/school-data-editor
```

## JavaFX アプリ本体

```text
src/main/java/com/example/editor/App.java
```

## ダブルクリック用アプリ

jpackage で作成した macOS 用アプリは以下です。

```text
$HOME/projects/school-data-editor/dist/School Data Editor.app
```

---

# JavaFX Desktop Editor の重要ボタン

## Import JSON

ローカル JSON ファイルを読み込み、現在の一覧を置き換えます。

---

## Import Updates

差分更新用 JSON を読み込み、既存データと突き合わせて反映します。

---

## Download Latest Cloud DB

Render の `/schools-v2` API から最新データを取得します。

取得元：

```text
https://helidon-school-app.onrender.com/schools-v2
```

このボタンを押すと、JavaFX アプリ上の一覧が Cloud 側の最新データで置き換わります。

同時に、ローカルの `schools-v2.json` にも保存します。

---

## Save

現在 JavaFX アプリ上に表示されている内容を、ローカル JSON に保存します。

---

## Publish

ローカル JSON の変更を GitHub に commit / push します。

Render は GitHub push 後に自動デプロイします。

---

## Publish to Server

管理API経由でサーバへ直接反映します。

使用する場合は、環境変数 `ADMIN_API_TOKEN` が必要です。

---

## Open Render

Render 上の公開画面をブラウザで開きます。

---

# JavaFX Editor の同期ルール

JavaFX Editor を起動したら、編集前に必ず以下のボタンを押します。

```text
Download Latest Cloud DB
```

理由は、JavaFX 側がローカル JSON を読んでいるため、起動時点のデータが Cloud 側より古い可能性があるためです。

基本運用は以下です。

```text
1. JavaFX Editor を起動
2. Download Latest Cloud DB を押す
3. Cloud側の最新データを取り込む
4. 必要箇所を編集
5. Save
6. Publish
7. Render の自動デプロイを待つ
8. Open Render で確認
```

---

# JavaFX Desktop Editor のビルド

## Maven で実行

```bash
cd $HOME/projects/school-data-editor

mvn clean javafx:run
```

## jar 作成

```bash
mvn clean package
```

## macOS アプリ作成

```bash
cd $HOME/projects/school-data-editor

rm -rf dist

mvn clean package

jpackage \
  --type app-image \
  --name "School Data Editor" \
  --input target \
  --main-jar school-data-editor-fat.jar \
  --main-class com.example.editor.Launcher \
  --dest dist
```

作成後、以下をダブルクリックして起動します。

```text
dist/School Data Editor.app
```

またはターミナルから：

```bash
open "dist/School Data Editor.app"
```

## 注意

jpackage の main class は `App` ではなく、必ず `Launcher` を指定します。

```bash
--main-class com.example.editor.Launcher
```

`com.example.editor.App` を直接指定すると、JavaFX runtime が見つからず、以下のようなエラーになる場合があります。

```text
JavaFXランタイム・コンポーネントが不足しており、このアプリケーションの実行に必要です
```

---

# ローカル Helidon repo を綺麗に作り直す方法

ローカルの `backup_myproject` が以下のような状態になった場合は、無理に `git pull` で直そうとせず、フォルダを退避して clone し直します。

```text
.DS_Store が大量にある
未追跡ファイルが大量にある
Permission denied が出る
stash が複数ある
pull できない
```

## 手順

```bash
cd $HOME/projects

mv backup_myproject backup_myproject_dirty_YYYYMMDD

git clone https://github.com/bull2023x/helidon-school-app.git backup_myproject

cd backup_myproject

git status
git log --oneline -10
git remote -v
```

期待する状態：

```text
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

remote は以下になっていることを確認します。

```text
origin  https://github.com/bull2023x/helidon-school-app.git
```

## 今後の pull

綺麗に clone し直した後は、以下で最新化できます。

```bash
cd $HOME/projects/backup_myproject

git pull origin main
```

---

# 学校データの変更差分確認

「どの学校の、どのデータが更新されたか」を確認したい場合は、`data/schools-v2.json` の commit 差分を比較します。

## 比較スクリプト例

`backup_myproject` の直下に `compare_schools.py` を作成します。

```python
import json
import subprocess

FILE_PATH = "data/schools-v2.json"

OLD_COMMIT = "origin/main~1"
NEW_COMMIT = "origin/main"

def load_json_from_git(commit):
    raw = subprocess.check_output(
        ["git", "show", f"{commit}:{FILE_PATH}"],
        text=True
    )
    return json.loads(raw)

def key(row):
    return (
        (row.get("schoolName") or "").strip(),
        (row.get("category") or "").strip()
    )

old_rows = load_json_from_git(OLD_COMMIT)
new_rows = load_json_from_git(NEW_COMMIT)

old_map = {key(r): r for r in old_rows}
new_map = {key(r): r for r in new_rows}

all_keys = sorted(set(old_map.keys()) | set(new_map.keys()))

for k in all_keys:
    old = old_map.get(k)
    new = new_map.get(k)

    school, category = k

    if old is None:
        print(f"[ADDED] {school} / {category}")
        continue

    if new is None:
        print(f"[DELETED] {school} / {category}")
        continue

    changed_fields = []

    for field in sorted(set(old.keys()) | set(new.keys())):
        old_value = old.get(field)
        new_value = new.get(field)

        if old_value != new_value:
            changed_fields.append(field)

    if changed_fields:
        print(f"[UPDATED] {school} / {category}")
        for field in changed_fields:
            print(f"  - {field}")
            print(f"    old: {old.get(field)}")
            print(f"    new: {new.get(field)}")
        print()
```

## 実行

```bash
cd $HOME/projects/backup_myproject

git fetch origin main

python3 compare_schools.py
```

## 出力例

```text
[UPDATED] ○○中学校 / 帰国入試
  - examDates
    old: ...
    new: ...

[UPDATED] △△中学校 / 英語入試
  - subjects
    old: ...
    new: ...

[ADDED] □□中学校 / 帰国生入試
```

---

# Android App

## 別プロジェクト

Android アプリは別フォルダで管理します。

```text
SchoolViewer
```

または公開用には：

```text
SchoolViewer-public
```

## Android 構成

```text
Kotlin
Jetpack Compose
Retrofit
Material3
```

## APK debug build

```bash
cd $HOME/projects/SchoolViewer/app

./gradlew assembleDebug
```

生成場所：

```text
app/build/outputs/apk/debug/app-debug.apk
```

実機またはエミュレータにインストール：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

# Google Play

## クローズドテスト対応

現在、以下に対応済みです。

```text
upload key reset
App Bundle
署名鍵
Google Play closed testing
```

## 署名鍵

保存場所例：

```text
SchoolViewer/app/release-keystore.jks
```

秘密鍵類は GitHub に commit しないでください。

---

# App Bundle 生成

```bash
cd $HOME/projects/SchoolViewer

./gradlew bundleRelease
```

AAB 生成場所：

```text
app/build/outputs/bundle/release/app-release.aab
```

---

# .gitignore

Mac と Android / Java の運用上、以下は Git 管理しないようにします。

```gitignore
.DS_Store
**/.DS_Store

*.jks
*.keystore
*.pem

app/build/
target/
dist/
```

特に `.DS_Store` は Mac が自動生成する不要ファイルです。

`git pull` や merge の邪魔になることがあるため、必ず除外します。

---

# Render 再デプロイ

通常は GitHub push 後に Render が自動デプロイします。

必要に応じて、Render ダッシュボードから手動再デプロイできます。

```text
Manual Deploy
↓
Clear build cache & deploy latest commit
```

---

# 現在の完成状態

```text
GitHub Actions
BeautifulSoup 巡回
archive バックアップ
Git 履歴保存
Render 自動デプロイ
external JSON 読込
Helidon API
Android App 連携
Google Play 対応
JavaFX Desktop Editor
Download Latest Cloud DB
半自動更新
```

---

# 今後の候補

```text
差分通知
Slack通知
PR生成
PDF解析強化
AI要約
学校別更新履歴ページ
JavaFX Editor の自動バックアップ強化
```

````

上書き後、ローカルから push する手順はこれです。

```bash
cd $HOME/projects/backup_myproject

git status

git add README.md

git commit -m "Update README with JavaFX editor workflow"

git push origin main
````

### 四谷大塚の参考偏差値

備考欄には、四谷大塚公式の「2027年度用 第4回 合不合判定テスト」のAライン80偏差値（合格可能性80％）を一般入試の代表例として表示します。帰国・英語入試そのものの偏差値ではありません。４科目受験可能な回を優先し、２・４科選択の２科判定、３科目、適性検査等は区別しています。数値・男女区分・参考回・一覧掲載日程・出典・確認日を `deviationReference` に保持します。現在の入試日程は学校公式要項と試験日欄を確認してください。

学校名表記90件（立川国際の重複表記を含む）のうち89件に掲載値を付記し、金沢大学附属中学校は掲載を確認できない旨を表示します。金沢学院大学附属中学校の値は流用していません。照合記録は `data/yotsuya-a80-review-2027.json` です。学校公式の入試条件の確認状態とは別に管理し、備考の年度確認や通常の収集で参考偏差値が消えないようにしています。自動収集で偏差値を推定・更新することはありません。

## 一覧の偏差値メニュー

備考列の▼から四谷大塚Aライン80の掲載値を「45未満」「45以上55未満」「55以上65未満」「65以上」で絞り込めます。入試分類・キーワードとの条件はANDです。男女等で複数値がある場合はいずれかが該当すれば表示し、複数範囲に同じ学校が現れる場合があります。数値順の並べ替えには掲載値の最大値を使用し、未掲載・未確認は末尾に表示します。未掲載・未確認のみの表示も選べます。
