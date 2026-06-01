# no-review-notifications-slack

  - githubの未レビューPRを集計しslackで通知
  - scala3
    - Scala Native
    - scala-cli
  - aws lambda
    - provided.al2 カスタムランタイム (zipアップロード形式)
  - serverless framework

## デプロイ

`bootstrap` (Scala Nativeバイナリ) をビルドして zip でアップロードする。
ビルドは Lambda 実行環境と互換性を保つためコンテナ内で行う。

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

```
WEBHOOK_URL: <通知用webhook>
ALERT_WEBHOOK_URL: <アラート用webhook>
GITHUB_TOKEN: <github token(classic)>
GITHUB_USERNAME: <githubユーザーID>
SLACK_ID: <通知先ユーザーid>
ENV: <環境名>
```
