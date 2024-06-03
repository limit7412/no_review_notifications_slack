# no-review-notifications-slack

  - githubの未レビューPRを集計しslackで通知
  - scala3
    - GraalVM
    - scala-cli
  - aws lambda
  - serverless framework

```
WEBHOOK_URL: <通知用webhook>
ALERT_WEBHOOK_URL: <アラート用webhook>
GITHUB_TOKEN: <github token(classic)>
GITHUB_USERNAME: <githubユーザーID>
SLACK_ID: <通知先ユーザーid>
ENV: <環境名>
```