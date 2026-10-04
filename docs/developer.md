# 開発メモ

## Android

Androidアプリは `android/` に独立したGradleプロジェクトとして配置します。CIとローカルの検証は `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain` です。Gradle/ライブラリの更新通知だけはlintのエラー対象から除外し、使用バージョンを固定しています。その他のlint警告はビルドを失敗させます。

- `TimelineStore` は通知追加と非表示の判定を同じSQLiteトランザクションで確定します。投稿IDで重複を排除し、全タブの読み取りで非表示を適用します。
- `HypeDetector` はネットワークや端末機能を持たない文章ルールです。精度を保証する分類器ではありません。
- `PushParser` は表示名や本文のメンションで投稿者を推測しません。公開されたXの通知例にある相対URLにも対応します。
- `AutoPushStream` は単一スレッドでWebSocketの状態を管理します。古いソケットのコールバックを無視し、再接続は5秒から最大5分、close code 4774は30分待機します。設定変更と受信・ブロックの競合はアカウント単位のロックで制御します。
- `XClient` は登録・ブロック専用です。Cookie付きリクエストのリダイレクトは禁止し、ブロック対象の `screen_name` と `blocking` を応答で確認します。失敗の盲目的な再試行は行いません。X上の結果が不明なときもローカルの非表示を維持します。
- `SecretStore` はKeystoreの鍵で暗号化し、Cookie・秘密鍵をログ出力しません。テストでのみダミーCookieを使います。
- Android UIには日本語のみを用意しています。通知本文・時刻を除き、アプリの会話口調はコードや仕様の定型に持ち込みません。

通知形式の根拠: https://blog.x.com/engineering/en_us/topics/infrastructure/2019/user-centric-web-push-aggregation

暗号形式の根拠: RFC 8291 / RFC 8188。登録、VAPID公開鍵、ACKコード、既定のlegacyレコードサイズはリポジトリの既存Rust実装を参照しています。

実際のXから得た通知fixtureは未収録です。現在のペイロードに対応できない場合は、秘密情報を取り除いた実サンプルで `PushParser` の回帰テストを追加してから変更してください。未対応形式をユーザー名の推測やスクレイピングで補完しないでください。
