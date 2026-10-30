# Game Shelf — Android (Kotlin / Compose)

Steam と Epic Games の**所有ゲームの横断検索**に特化した Android アプリのソースコードです。アカウントが未接続の場合は空ライブラリを表示し、「サンプルでUIを確認」から明示的にデモデータを表示できます。

## 機能

- 2画面（ライブラリ／設定）
- 横長アートを主役にしたコンパクトなグリッド（画面幅に応じて自動調整）とリストの切替
- 所有ゲーム名の部分一致・大文字小文字無視の検索
- すべて／Steam／Epic Games の絞り込み
- タイトル昇順／降順、プレイ時間降順、ストア順でソート
- 手動同期・起動時同期・オフラインでも前回の一覧を表示
- 装飾を抑えた密度の高いUI（カード枠・大見出し・バッジ重畳を廃止し、設定フォームは接続済みなら折りたたみ）
- Steam は公式 GetOwnedGames API。Epic は Legendary が参照する非公開 API
- 認証情報を Android Keystore の AES-GCM で暗号化。Android Auto Backup は無効
- 独自サーバー、広告・解析 SDK はなし

## ビルド

1. Android Studio（API 37対応版）で **このフォルダを Open**。
2. JDK 17、Android SDK Platform 37 を入れて Gradle Sync。
3. Gradle 9.3.1 をインストールし、`gradle :app:assembleDebug` でビルド。あるいはAndroid StudioのGradle設定をローカルGradleに切り替えて Build > Build APK(s) を実行してください。
4. GitHub Actionsではpush・PR・手動実行でDebugビルドを検証します。pushと手動実行ではSecrets設定後に署名済みRelease APKを作り、`shelfg-signed-release-apk` にアップロードします。

**注意:** CIでコンパイルを確認します。実機でのEpic認証・同期は別途確認が必要です。

## Steam 設定

1. https://steamcommunity.com/dev/apikey から自分用の Web API Key を取得。
2. SteamID64（17桁）と API Key を設定画面に入力して保存。
3. Steam の「ゲームの詳細」が非公開だと GetOwnedGames はゲーム一覧を返さないことがあります。
4. API Key は Steam のエンドポイントへの HTTPS 通信で使用されます（URLクエリに渡します）。

## Epic 設定（非公式）

1. 「Epic公式ログインを開く」を押し、Epic公式サイトのブラウザ画面でログイン。
2. Epic が返した JSON 内の `authorizationCode` をコピーして設定画面へ貼り付け（JSON全文も可）。
3. 「Epicに接続」を押すと、OAuthトークンを端末側で取得・更新し、EpicのライブラリAPIを呼び出します。
4. ライブラリAPIにはページング、メタデータ取得には同時接続5本の制限を実装。
5. DLC と Unreal Engine アセットを簡易フィルタリングしています。Epicのカタログ情報を取得できなかったゲームは内部名のまま残ることがあります。

**注意:** Epicのエンドポイントと共有OAuthクライアントID/secretは[Legendary](https://github.com/legendary-gl/legendary)の実装を参考にしています。Epicが一般開発者向けに公開・保証するものではありません。予告なく使用不能になることや、利用条件に影響する可能性があります。パスワードはアプリに入力しません。端末が侵害されている場合まで認証情報の安全を保証するものではありません。

## 保存先とデータ削除

- 認証情報: アプリ専用領域の暗号文 + Android Keystore の鍵
- 一覧キャッシュ: アプリ専用SharedPreferences（平文のゲームタイトルのみ。端末外へ同期しない）
- 設定 > 「認証情報を削除」で対応する認証情報とキャッシュを削除。
- アプリの削除で端末上の保存データも削除されます。

## 開発メモ

- `GameRepository.kt`: Steam / Epic 通信、ページング、JSON解析、キャッシュ
- `SecureStore.kt`: KeystoreベースのAES-GCM暗号化
- `MainActivity.kt`: 同期、ステート管理、外部ブラウザ呼び出し
- `GameShelfUi.kt`: Material 3 Compose画面

## 参照

- https://partner.steamgames.com/doc/webapi/IPlayerService
- https://github.com/legendary-gl/legendary/blob/master/legendary/api/egs.py
- https://github.com/legendary-gl/legendary/blob/master/legendary/core.py
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/privacy-and-security/cryptography


## パッケージ名・固定署名（v0.3.0）

- パッケージID / namespace は `net.clagon.shelfg`。
- **以前の `dev.gameshelf.mobile` は別アプリになります。** 旧APKとは上書き更新できず、認証情報も自動移行されません。旧アプリを残して新しいアプリをインストールできます。
- 署名用PKCS#12鍵とパスワードは別途配布した `shelfg-signing-backup.zip` に含まれます。鍵やパスワードを公開リポジトリにpushしないでください。
- GitHub の [Settings → Secrets and variables → Actions](https://github.com/clagon/shelfg/settings/secrets/actions) で次の Repository secrets を登録してください：
  - `SHELFG_KEYSTORE_BASE64`：`shelfg-signing-secrets.txt` の同名欄のValue全文（1行）。
  - `SHELFG_SIGNING_PASSWORD`：同ファイルの同名欄のValue。
- **この2つのSecretsがない場合、mainの署名済みAPKビルドは意図的に失敗**します。鍵が毎回変わるDebug APKは配布しません。
- 登録後、[Actions → Android APK](https://github.com/clagon/shelfg/actions/workflows/android.yml) の `Run workflow` で署名済み `app-release.apk` を作成できます。
- Pull Requestから秘密情報を使用せず、Debugのコンパイルチェックのみ行います。
- ローカルで署名する場合、環境変数 `SHELFG_SIGNING_KEYSTORE`（p12鍵の絶対パス）と `SHELFG_SIGNING_PASSWORD` を指定します。
- **今後は署名鍵とパッケージIDを固定し、更新時に `versionCode` を増加**させれば上書き更新ができます。鍵を紛失すると同じアプリIDでの更新が困難になるため、署名鍵のZIPを安全な場所へバックアップしてください。
