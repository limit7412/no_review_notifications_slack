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
ビルドは Lambda 実行環境 (provided.al2023) と glibc/libcurl の互換性を保つため、
Amazon Linux 2023 のコンテナ内で行う ([Dockerfile](Dockerfile))。

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
MENTION_ID: <メンション先ユーザーid（slack: Slack ID / discord: Discord ユーザーID）>
SLACK_ID: <MENTION_ID 未設定時のフォールバック（後方互換）>
NOTIFY_MODE: <slack | discord（未設定時は slack）>
ENV: <環境名>
```

## 通知モード (Discord 対応)

`NOTIFY_MODE` で通知先を切り替える。

- `slack`（デフォルト）: 従来どおり Slack の legacy attachment 形式で通知する
- `discord`: Discord の webhook（`content` + `embeds`）形式で通知する

Discord モードに切り替える場合の設定:

1. `NOTIFY_MODE: discord` を設定する
2. `WEBHOOK_URL` を Discord チャンネルの webhook URL に変更する
3. `MENTION_ID` を Discord のユーザーID（数値ID）に設定する

不正な `NOTIFY_MODE` を指定した場合は起動時にエラーで即座に失敗する。
`MENTION_ID` は未設定時 `SLACK_ID` にフォールバックするため、既存の Slack
運用は環境変数を変更せずそのまま動作する。
