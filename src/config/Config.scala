package config

// アプリ全体で参照する環境変数を1箇所に集約する。
// 各所に散在していた sys.env(...) の文字列キー直書きを排除し、
// 型付きのフィールドアクセスに統一する。
case class Config(
    githubToken: String,
    githubUsername: String,
    slackId: String,
    webhookUrl: String,
    env: String
)

object Config {
  // 起動時に1度だけ読み込む。必須環境変数が欠落していれば例外で即座に失敗する。
  // lazy val ではなく val とし、Cold Start の初期化フェーズで欠落を早期検知する。
  val instance: Config = load()

  private def load(): Config =
    Config(
      githubToken = require("GITHUB_TOKEN"),
      githubUsername = require("GITHUB_USERNAME"),
      slackId = require("SLACK_ID"),
      webhookUrl = require("WEBHOOK_URL"),
      env = require("ENV")
    )

  private def require(key: String): String =
    sys.env.getOrElse(
      key,
      throw new RuntimeException(
        s"required environment variable not set: ${key}"
      )
    )
}
