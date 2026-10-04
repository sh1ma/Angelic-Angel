# Angelic Angel

> 「Angelic Angel/Hello,星を数えて」は、2015年7月1日に Lantis から発売された μ's によるシングルで、楽曲は劇場版『ラブライブ！The School Idol Movie』の挿入歌。
>
> — [Wikipedia](https://ja.wikipedia.org/wiki/Angelic_Angel/Hello,%E6%98%9F%E3%82%92%E6%95%B0%E3%81%88%E3%81%A6)

Twitter/X の通知を Mozilla の Web Push 基盤を通じてリアルタイムに受信する CLI ツールです。自分がフォローしていて、ツイート通知を有効化しているユーザのツイートをストリーミングできます。

[English README](README.md)

## Android版

Android 17向けの通知クライアントを [`android/`](android/README.md) に追加しています。ユーザー別タブ、通知履歴の保存、煽り表現のルールによる自動非表示、任意のX上での自動ブロックに対応します。初回・再登録とX上のブロックにはAPIを使います。ビルド・利用手順と未検証の範囲はAndroid版READMEを参照してください。

## 概要

Angelic Angel はブラウザの Web Push クライアントをエミュレートし、Twitter/X のプッシュ通知を受信します。[Mozilla AutoPush](https://autopush.readthedocs.io/) に WebSocket で接続し、ECE (Encrypted Content-Encoding) で暗号化された通知を復号して、設定された Webhook エンドポイントに転送します。

フォロー中かつ **ツイート通知をオン** にしているユーザのツイートがリアルタイムで届きます。

### 仕組み

```
Twitter/X  ──push──▶  Mozilla AutoPush サーバ  ◀──WebSocket──  Angelic Angel  ──HTTP POST──▶  Webhook
```

1. Angelic Angel が Mozilla AutoPush サーバに Web Push サブスクライバとして登録します。
2. 取得したプッシュサブスクリプションのエンドポイントを Twitter の通知設定 API に登録します。
3. Twitter がプッシュ通知を送信すると、Firefox が使用するものと同じ Mozilla AutoPush サーバを経由して届きます。
4. Angelic Angel が WebSocket 経由で通知を受信・復号し、Webhook にペイロードを転送します。

### 重要事項

- **データの取得元**: すべての通知データは Mozilla の Web Push サーバ (`push.services.mozilla.com`) から受信しています。通知データの取得のために Twitter/X に直接アクセスすることはありません。
- **API の使用は最小限**: Twitter/X の API はプッシュサブスクリプションの初回登録時 (`register` コマンド) にのみ使用されます。通知の受信中に API コールは発生しません。
- **スクレイピング不使用**: このツールは Web スクレイピングを一切行いません。ブラウザがプッシュ通知を配信するのと同じ、標準的な W3C Push API のフローを利用しています。

## 必要環境

- Rust (edition 2024)
- Twitter/X アカウントの認証情報 (`auth_token` と `ct0` Cookie)

### `auth_token` と `ct0` の取得方法

1. Web ブラウザで [x.com](https://x.com) を開いてログインします。
2. 開発者ツール (F12) を開き、**Application** (または **ストレージ**) タブを選択します。
3. **Cookie** → `https://x.com` から `auth_token` と `ct0` の値を確認できます。

## インストール

```sh
cargo install --path .
```

## 使い方

### 1. 設定の初期化

```sh
# 対話モード
angelic-angel init

# 引数を指定する場合
angelic-angel init --auth-token YOUR_AUTH_TOKEN --ct0 YOUR_CT0
```

Twitter の認証情報を含む `angelic-angel.toml` が作成されます。

### 2. プッシュサブスクリプションの登録

```sh
angelic-angel register
```

Mozilla AutoPush に新しいプッシュサブスクリプションを登録し、そのエンドポイントを Twitter のプッシュ通知 API に登録します。

### 3. 通知の受信開始

```sh
WEBHOOK_ENDPOINT=https://your-webhook.example.com/endpoint angelic-angel listen
```

`WEBHOOK_ENDPOINT` 環境変数で、復号された通知ペイロードの HTTP POST 送信先を指定します。

### その他のコマンド

```sh
# 現在の設定と登録状態を確認
angelic-angel status

# プッシュサブスクリプションを解除
angelic-angel unregister
```

### オプション

| フラグ | 説明 |
|--------|------|
| `-c, --config <PATH>` | 設定ファイルのパス (デフォルト: `angelic-angel.toml`) |
| `-v, --verbose` | デバッグログを有効化 |

## 再接続

Angelic Angel は Firefox 互換の再接続戦略を実装しています:

- 指数バックオフ: 5秒 × 2^n (上限 5 分)
- UAID 無効化時の自動再登録
- サーババックオフ (close code 4774): 30 分間の待機
- 接続成功時にリトライカウンタをリセットする無限リトライ

## ライセンス

MIT
