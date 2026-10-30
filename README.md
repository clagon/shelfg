# Game Shelf

> **Steam & Epic Games の所有タイトルを映画ウォッチリスト感覚で横断管理する Android アプリ**

Game Shelf は、Steam と Epic Games に散らばっている所有ゲームライブラリを一元化し、高速に横断検索・ブラウジングできる Android アプリです。  
映画ウォッチリストに着想を得たミニマルで洗練されたポスターUIを採用し、外部サーバーや広告SDKを介さず端末内完結でセキュアに動作します。

アカウント未連携の状態でも、「サンプルでUIを確認」ボタンからデモデータを読み込んで実際の使用感を体験できます。

---

## 主な機能

- **ポスター主役の洗練されたUI**
  - 映画ウォッチリスト風のミニマルなデザイン（中央見出し、アイコン型フローティングナビ、余計な装飾枠を排した設計）。
  - ポスターアートを主役にした2列グリッド表示と、一覧性の高いリスト表示をワンタップで切り替え可能（縦長アートが取得できない場合は横長バナーへ自動フォールバック）。
- **快適な検索とフィルタリング**
  - タイトル名による部分一致検索（大文字・小文字を無視）。
  - プラットフォーム絞り込み（すべて / Steam / Epic Games）。
  - 柔軟なソート機能（タイトル昇順・降順、プレイ時間順、ストア順）。
- **オフライン対応と同期**
  - 手動同期およびアプリ起動時の自動同期。
  - オフライン環境でも前回取得したローカルキャッシュから即座に一覧を表示。
- **プライバシー＆セキュリティ重視**
  - 独自バックエンドサーバー、広告SDK、トラッキング/解析SDKは一切非搭載。
  - 取得した認証情報はすべて端末内の Android Keystore（AES-GCM）で安全に暗号化（Android Auto Backup は無効化）。

---

## 画面構成

- **ライブラリ画面 (`Library`)**: ゲーム一覧の閲覧、検索、ストア絞り込み、表示形式（グリッド/リスト）の切替。
- **設定画面 (`Settings`)**: Steam / Epic Games の接続設定、デモ表示、認証情報・キャッシュの削除。

---

## 初期設定・接続手順

### 1. Steam の連携手順
1. [Steam Web API Key 登録ページ](https://steamcommunity.com/dev/apikey) にアクセスし、APIキーを取得します。
2. アプリの設定画面で、ご自身の **SteamID64（17桁の数字）** と **APIキー** を入力して保存します。
3. ライブラリ画面で同期を実行します。

> **Note**  
> Steam プロフィールのプライバシー設定で「ゲームの詳細」が非公開になっていると、API から一覧が取得できない場合があります。必ず「公開」に設定してください。  
> ※ APIキーは公式エンドポイント（`GetOwnedGames`）への通信時のみ使用されます。

### 2. Epic Games の連携手順（非公式）
1. 設定画面の「Epic公式ログインを開く」をタップし、外部ブラウザで Epic Games 公式サイトにログインします。
2. ログイン完了時にブラウザに表示される JSON から `authorizationCode` の値（または JSON 全文）をコピーします。
3. アプリの設定画面にコードを貼り付け、「Epicに接続」をタップします。
4. 端末側で直接 OAuth トークンを取得・保存し、ライブラリ情報を同期します。

> **Warning**  
> Epic Games の連携は、オープンソースクライアント [Legendary](https://github.com/legendary-gl/legendary) が解析した非公開エンドポイントおよび共有クライアント情報を参考にしています。  
> Epic 公式の開発者向け API ではないため、予告なく仕様変更や利用制限が発生する可能性があります。アプリが直接パスワードを取得・保持することはありません。

---

## セキュリティとデータ管理

| データ種類 | 保存場所 | 暗号化 / 保護 |
| :--- | :--- | :--- |
| **認証情報** (API Key, OAuth Token) | アプリ専用内部ストレージ | **Android Keystore (AES-GCM)** による暗号化 |
| **ゲーム一覧キャッシュ** | アプリ専用 SharedPreferences | 平文（タイトル・メタデータのみ、外部同期なし） |

- **データの削除**: 設定画面の「認証情報を削除」を実行することで、保存された認証情報およびゲームキャッシュを即座に破棄できます。
- **アプリ削除時**: アプリをアンインストールすると、端末内に保存された全データが自動的に消去されます。

---

## ビルド方法

### 必要環境
- **Android Studio**（Target API 37 対応環境）
- **JDK 17** 以上

### 手順
1. リポジトリをクローンします。
   ```bash
   git clone https://github.com/clagon/shelfg.git
   ```
2. Android Studio でプロジェクトルートを開きます。
3. Gradle Sync 完了後、対象のデバイスまたはエミュレータを選択して **Run**（または `./gradlew assembleDebug`）を実行します。

---

## リリース署名と CI/CD

本プロジェクトは **v0.3.0** よりパッケージ ID および署名鍵を固定しています。

- **パッケージ名 / Namespace**: `net.clagon.shelfg`
- **旧バージョン (`dev.gameshelf.mobile`) との互換性**: 以前のパッケージとは別アプリ扱いとなるため、上書きインストールや認証情報の自動引き継ぎは行われません。

### GitHub Actions での自動ビルド
GitHub Actions（`.github/workflows/`）経由で署名済み APK を生成する場合、リポジトリの **Settings → Secrets and variables → Actions** に以下を登録します。

| Secret 名 | 内容 |
| :--- | :--- |
| `SHELFG_KEYSTORE_BASE64` | 配布された `shelfg-signing-secrets.txt` 内のキーストア（Base64文字列・1行） |
| `SHELFG_SIGNING_PASSWORD` | 同ファイル内の署名パスワード |

- 登録完了後、**Actions → Android APK** から `Run workflow` を手動実行すると、署名済み `app-release.apk` がビルドされます。
- Secrets が未設定の場合、署名なしの野良ビルド防止のため main ブランチのリリースビルドは意図的に失敗します（Pull Request では秘密情報を使用せず、Debug コンパイル検証のみを実施します）。

### ローカルでの署名ビルド
環境変数にキーストアのパスとパスワードを指定してビルドします。
```bash
export SHELFG_SIGNING_KEYSTORE="/path/to/keystore.p12"
export SHELFG_SIGNING_PASSWORD="your_keystore_password"
./gradlew assembleRelease
```

---

## 主要構成・アーキテクチャ

```
app/src/main/java/net/clagon/shelfg/
├── GameRepository.kt   # Steam / Epic API との通信、ページング処理、JSON 解析、キャッシュ管理
├── SecureStore.kt      # Android Keystore を用いた認証情報の AES-GCM 暗号化 / 復号
├── MainActivity.kt     # アプリライフサイクル、状態管理、外部ブラウザ連携
└── GameShelfUi.kt      # Jetpack Compose / Material 3 による UI コンポーネント実装
```

- **UI / デザイン**: Jetpack Compose, Material 3
- **非同期処理**: Kotlin Coroutines & Flow
- **暗号化**: Android Keystore System (`AndroidKeyStore`)
- **API 通信**: Ktor / HttpURLConnection (標準的な非同期 HTTP クライアント)

---

## 関連ドキュメント・参照元

- [Steamworks Web API: IPlayerService](https://partner.steamgames.com/doc/webapi/IPlayerService)
- [Legendary: Epic Games API Implementation](https://github.com/legendary-gl/legendary/blob/master/legendary/api/egs.py)
- [Android Developers: Jetpack Compose セットアップ](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android Developers: セキュリティと暗号化](https://developer.android.com/privacy-and-security/cryptography)
