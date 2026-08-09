# no-review-notifications-slack

  - githubの未レビューPRを集計しslack/discordで通知
  - scala3
    - Scala Native
    - scala-cli
  - aws lambda
    - provided.al2023 カスタムランタイム (zipアップロード形式)
  - serverless framework

## デプロイ

`bootstrap` (Scala Nativeバイナリ) をビルドして zip でアップロードする。
ビルドは scala-cli 公式イメージ (`virtuslab/scala-cli`, Debian/glibc) 内で
**静的リンク (static build)** して行う ([Dockerfile](Dockerfile))。
完全静的リンクにより Lambda 実行環境 (provided.al2023) の glibc/libcurl の
バージョンに依存しない自己完結バイナリになるため、ビルド環境とランタイム環境の
ライブラリ整合を取る必要がなくなる (#14)。
(以前は glibc/libcurl を一致させるため Amazon Linux 2023 上でビルドしていた)。

libcurl は HTTPS 通信のため OpenSSL 込みで、名前解決は c-ares に任せて静的ビルド
する（完全静的リンクでは glibc の getaddrinfo が使う NSS モジュールを読み込めない）。
TLS 検証に使う CA バンドルは実行環境である AL2023 のパス
(`/etc/pki/tls/certs/ca-bundle.crt`) を参照する。

公式イメージが amd64 単一アーキのため、Lambda のアーキテクチャは x86_64。
Apple Silicon 等でローカルビルドする場合は QEMU エミュレーションになる点に注意。

`serverless-plugin-scripts` により、`sls deploy` / `sls package` の
パッケージング直前 (`before:package:createDeploymentArtifacts`) に
`bootstrap` が自動でビルド・取り出しされる (Docker が必要)。

```shell
# プラグインをインストール
npm install

# デプロイ (bootstrap の生成 → zip化 → アップロードまで自動)
sls deploy --stage <stage_name>
```

## 環境変数 (env.yml)

```yaml
WEBHOOK_URL: <通知用webhook（NOTIFY_MODE に対応した Slack/Discord の webhook URL）>
ALERT_WEBHOOK_URL: <アラート用webhook>
GITHUB_TOKEN: <github token(classic)>
GITHUB_USERNAME: <githubユーザーID>
NOTIFY_MODE: <slack | discord（未設定時は slack）>
ENV: <環境名>
```

レビュー依頼がある場合の通知はチャンネル全体メンション（Slack: `<!channel>` /
Discord: `@everyone`）で行うため、メンション先を指定する環境変数は不要。

## 通知の抑制

通知対象のPR（assign / reviewer / reviewer(team)）がすべて自分
（`GITHUB_USERNAME`）の作成したPRのみの場合は、誰かのレビュー待ちではないため
通知を送らない。対象PRが0件の場合は従来どおり通知する。

## 通知モード (Discord 対応)

`NOTIFY_MODE` で通知先を切り替える。

- `slack`（デフォルト）: 従来どおり Slack の legacy attachment 形式で通知する
- `discord`: Discord の webhook（`content` + `embeds`）形式で通知する

Discord モードに切り替える場合の設定:

1. `NOTIFY_MODE: discord` を設定する
2. `WEBHOOK_URL` を Discord チャンネルの webhook URL に変更する

不正な `NOTIFY_MODE` を指定した場合は起動時にエラーで即座に失敗する。
