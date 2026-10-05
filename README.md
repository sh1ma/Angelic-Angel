# Angelic Angel

> "Angelic Angel/Hello, Hoshi wo Kazoete" is a single by μ's released on July 1, 2015 from Lantis. The song is an insert song for the film *Love Live! The School Idol Movie*.
>
> — [Wikipedia](https://ja.wikipedia.org/wiki/Angelic_Angel/Hello,%E6%98%9F%E3%82%92%E6%95%B0%E3%81%88%E3%81%A6)

A CLI tool that receives Twitter/X notifications in real time via Mozilla's Web Push infrastructure. Stream tweets from users you follow and have tweet notifications enabled for.

[日本語版 README](README.ja.md)

## Overview

Angelic Angel emulates a browser's Web Push client to receive Twitter/X push notifications. It connects to [Mozilla AutoPush](https://autopush.readthedocs.io/) via WebSocket, decrypts incoming notifications using ECE (Encrypted Content-Encoding), and forwards the decrypted payloads to a configured webhook endpoint.

You will receive notifications for tweets from users that you **follow** and have **tweet notifications turned on** for on Twitter/X.

### How It Works

```
Twitter/X  ──push──▶  Mozilla AutoPush Server  ◀──WebSocket──  Angelic Angel  ──HTTP POST──▶  Webhook
```

1. Angelic Angel registers as a Web Push subscriber with Mozilla's AutoPush server.
2. The push subscription endpoint is registered with Twitter's notification settings API.
3. When Twitter sends a push notification, it goes through Mozilla's AutoPush server — the same infrastructure used by Firefox.
4. Angelic Angel receives and decrypts the notification via WebSocket, then forwards the payload to your webhook.

### Important Notes

- **Data source**: All notification data is received from Mozilla's Web Push server (`push.services.mozilla.com`). Angelic Angel does not access Twitter/X directly for notification data.
- **Minimal API usage**: Angelic Angel does not poll Twitter/X for notifications. It calls Twitter/X only to register the push subscription: once with the `register` command, and again automatically if the AutoPush UAID is invalidated while `listen` is running (see [Reconnection](#reconnection)).
- **No scraping**: This tool does not perform any web scraping. It uses the standard W3C Push API flow, the same mechanism browsers use to deliver push notifications.
- **Unofficial endpoint**: Registration uses an endpoint of the Twitter/X web client (`x.com/i/api/1.1/notifications/settings/login.json`) with your browser session cookies, not the public developer API. This may conflict with the [X Terms of Service](https://x.com/tos), which restrict access to the Services through interfaces other than X's published ones. Use this tool at your own risk, including the risk of restrictions on your account.

## Requirements

- Rust (edition 2024)
- Twitter/X account credentials (`auth_token` and `ct0` cookies)

### Getting `auth_token` and `ct0`

1. Open [x.com](https://x.com) in your web browser and log in.
2. Open Developer Tools (F12) and go to the **Application** (or **Storage**) tab.
3. Under **Cookies** → `https://x.com`, find the values for `auth_token` and `ct0`.

### Handling credentials

The configuration file stores your session cookies (`auth_token`, `ct0`) and, after `register`, the Web Push private key and auth secret in plain text. Exposure of the session credentials could allow unauthorized access to your account.

- Do not share or commit the configuration file. The default `angelic-angel.toml` is listed in `.gitignore`, but files saved under a different name via `-c` may not be.
- Restrict the file's permissions to your user (e.g. `chmod 600 angelic-angel.toml` on Unix-like systems).

## Installation

```sh
cargo install --path .
```

## Usage

### 1. Initialize configuration

```sh
# Interactive mode
angelic-angel init

# Or with arguments
angelic-angel init --auth-token YOUR_AUTH_TOKEN --ct0 YOUR_CT0
```

This creates `angelic-angel.toml` with your Twitter credentials.

### 2. Register push subscription

```sh
angelic-angel register
```

This registers a new push subscription with Mozilla AutoPush and then registers the endpoint with Twitter's push notification API.

### 3. Start listening

```sh
WEBHOOK_ENDPOINT=https://your-webhook.example.com/endpoint angelic-angel listen
```

The `WEBHOOK_ENDPOINT` environment variable specifies where decrypted notification payloads are sent via HTTP POST.

### Other commands

```sh
# Check current configuration and registration status
angelic-angel status

# Remove push subscription
angelic-angel unregister
```

### Options

| Flag | Description |
|------|-------------|
| `-c, --config <PATH>` | Configuration file path (default: `angelic-angel.toml`) |
| `-v, --verbose` | Enable debug logging |

## Reconnection

Angelic Angel implements a Firefox-compatible reconnection strategy:

- Exponential backoff: 5s × 2^n, capped at 5 minutes
- Automatic re-registration on UAID invalidation (a new push subscription is registered with AutoPush, and its endpoint is re-registered with Twitter/X)
- Server backoff (close code 4774): 30-minute delay
- Infinite retries with counter reset on successful connection

## License

MIT
