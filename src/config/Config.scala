package config

// アプリ全体で参照する環境変数を1箇所に集約する。
// 各所に散在していた sys.env(...) の文字列キー直書きを排除し、
// 型付きのフィールドアクセスに統一する。
case class Config(
    githubToken: String,
    githubUsername: String,
    webhookUrl: String,
    // 通知先モード("slack" / "discord")。未設定時は後方互換で "slack"。
    notifyMode: String,
    env: String
)

object Config {
  // 起動時に1度だけ読み込む。必須環境変数が欠落していれば例外で即座に失敗する。
  // lazy val ではなく val とし、Cold Start の初期化フェーズで欠落を早期検知する。
  val instance: Config = load()

  // サポートする通知モード
  private val validModes = Set("slack", "discord")

  private def load(): Config =
    Config(
      githubToken = require("GITHUB_TOKEN"),
      githubUsername = require("GITHUB_USERNAME"),
      webhookUrl = require("WEBHOOK_URL"),
      notifyMode = loadNotifyMode(),
      env = require("ENV")
    )

  // NOTIFY_MODE は未設定時 "slack"(後方互換)。不正値は起動時に即エラーとする。
  // 前後空白・大文字小文字の揺れ(例: "Slack ")は正規化して設定ミスを吸収する。
  private def loadNotifyMode(): String = {
    val mode =
      sys.env.get("NOTIFY_MODE").map(_.trim.toLowerCase).getOrElse("slack")
    if (!validModes.contains(mode)) {
      throw new RuntimeException(
        s"invalid NOTIFY_MODE: ${mode} (must be one of ${validModes.mkString(", ")})"
      )
    }
    mode
  }

  private def require(key: String): String =
    sys.env.getOrElse(
      key,
      throw new RuntimeException(
        s"required environment variable not set: ${key}"
      )
    )
}
