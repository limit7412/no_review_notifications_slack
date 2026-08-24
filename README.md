# no-review-notifications-slack

  - GitHub の未レビュー PR を集計し、Slack または Discord へ通知する
  - scala3
    - Scala Native
    - scala-cli
  - aws lambda
    - provided.al2023 カスタムランタイム (zip アップロード形式)
  - serverless framework

## デプロイ

`bootstrap` (Scala Native バイナリ) をビルドして zip でアップロードする。
ビルドは scala-cli 公式イメージ (`virtuslab/scala-cli`, Debian/glibc) 内で行い、完全静的リンクする ([Dockerfile](Dockerfile))。
完全静的リンクにより、Lambda 実行環境 (provided.al2023) の glibc や libcurl のバージョンに依存しない自己完結のバイナリになるため、ビルド環境と実行環境でライブラリのバージョンを揃える必要がなくなる (#14)。
(以前は glibc/libcurl を一致させるため Amazon Linux 2023 上でビルドしていた)

libcurl は HTTPS 通信のため OpenSSL 込みで静的ビルドし、名前解決は c-ares に任せる (完全静的リンクでは glibc の getaddrinfo が使う NSS モジュールを読み込めないため)。
TLS 検証に使う CA バンドルは、実行環境である AL2023 のパス (`/etc/pki/tls/certs/ca-bundle.crt`) を参照する。

公式イメージが amd64 単一アーキテクチャのため、Lambda のアーキテクチャは x86_64 とする。
Apple Silicon などでローカルビルドする場合は QEMU エミュレーションになる。

`serverless-plugin-scripts` により、`sls deploy` / `sls package` のパッケージング直前 (`before:package:createDeploymentArtifacts`) に `bootstrap` のビルドと取り出しが自動で行われる (Docker が必要)。

```shell
# プラグインをインストール
npm install

# デプロイ (bootstrap の生成 → zip化 → アップロードまで自動)
sls deploy --stage <stage_name>
```

## 環境変数 (env.yml)

```yaml
WEBHOOK_URL: <通知用 webhook (NOTIFY_MODE に対応した Slack/Discord の webhook URL)>
ALERT_WEBHOOK_URL: <アラート用 webhook>
GITHUB_TOKEN: <github token(classic)>
GITHUB_USERNAME: <github ユーザー ID>
NOTIFY_MODE: <slack | discord (未設定時は slack)>
ENV: <環境名>
```

レビュー依頼がある場合の通知はチャンネル全体メンション (Slack: `<!channel>` / Discord: `@everyone`) で行うため、メンション先を指定する環境変数は必要ない。

## メンションの抑制

通知は対象の PR の件数にかかわらず毎回投稿する。
チャンネル全体メンションを付けるのは、reviewer / reviewer(team) に自分 (`GITHUB_USERNAME`) 以外が作成した PR がある場合に限る (#45)。
チーム宛のレビュー依頼には自分の PR も入りうるため、作成者で判定する。

もとは対象の PR がすべて自分の作成した PR である場合に通知そのものを送らない仕様だった (#36)。
レビュー依頼が無い日は assign が自分の PR だけになり、日次の通知が届かなくなっていたため、抑制の対象をメンションだけに狭めた (#45)。

draft の PR は、まだレビューを依頼する段階にないため通知の集計から除外する (#43)。
除外は assign / reviewer / reviewer(team) のすべてに適用され、上記のメンション判定も除外後の PR で行う。

## 通知モード

`NOTIFY_MODE` で通知先を切り替える。

- `slack` (デフォルト): Slack の legacy attachment 形式で通知する
- `discord`: Discord の webhook (`content` + `embeds`) 形式で通知する

Discord モードに切り替える場合の設定:

1. `NOTIFY_MODE: discord` を設定する
2. `WEBHOOK_URL` を Discord チャンネルの webhook URL に変更する

不正な `NOTIFY_MODE` を指定した場合は、起動時にエラーで即座に失敗する。
