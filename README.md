# Game Shelf — Android (Kotlin / Compose)

Steam と Epic Games の**所有ゲームの横断検索**に特化した Android アプリのソースコードです。アカウントが未接続の場合は空ライブラリを表示し、「サンプルでUIを確認」から明示的にデモデータを表示できます。`preview.html` はブラウザで見られるUIモックで、認証・同期は実行しません。

## 機能

- 2画面（ライブラリ／設定）
- 2列のグリッド（画面幅に応じて自動調整）とリストの切替
- 所有ゲーム名の部分一致・大文字小文字無視の検索
- すべて／Steam／Epic Games の絞り込み
- タイトル昇順／降順、プレイ時間降順、ストア順でソート
- 手動同期・起動時同期・オフラインでも前回の一覧を表示
- Steam は公式 GetOwnedGames API。Epic は Legendary が参照する非公開 API
- 認証情報を Android Keystore の AES-GCM で暗号化。Android Auto Backup は無効
- 独自サーバー、広告・解析 SDK はなし

## ビルド

1. Android Studio（API 37対応版）で **このフォルダを Open**。
2. JDK 17、Android SDK Platform 37 を入れて Gradle Sync。
3. Gradle 9.3.1 をインストールし、`gradle :app:assembleDebug` でビルド。あるいはAndroid StudioのGradle設定をローカルGradleに切り替えて Build > Build APK(s) を実行してください。
4. GitHub Actions は push / PR / 手動実行でDebug APKをビルドし、成果物 `game-shelf-debug-apk` を公開します。Gradle Wrapper の起動スクリプトとJARは未同梱です。

**注意:** この環境には Android SDK / Gradle がないため、実機でのビルドと API 接続テストは実施していません。コードは実装候補であり、APK完成品ではありません。

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
