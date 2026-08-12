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
  // サポートする通知モード。
  // instance の初期化(load -> loadNotifyMode)から参照するため、必ず instance より
  // 前に宣言する。object のフィールドは宣言順に初期化されるため、後ろに置くと
  // instance 初期化時に validModes が null となり NPE になる。
  private val validModes = Set("slack", "discord")

  // 起動時に1度だけ読み込む。必須環境変数が欠落していれば例外で即座に失敗する。
  // lazy val ではなく val とし、コールドスタートの初期化フェーズで欠落を早期検知する。
  val instance: Config = load()

  private def load(): Config =
    Config(
      githubToken = require("GITHUB_TOKEN"),
      githubUsername = require("GITHUB_USERNAME"),
      webhookUrl = require("WEBHOOK_URL"),
      notifyMode = loadNotifyMode(),
      env = require("ENV")
    )

  // NOTIFY_MODE は未設定時 "slack"(後方互換)。不正値は起動時に即エラーとする。
  // 前後の空白や大文字小文字の揺れ(例: "Slack ")は正規化して設定ミスを吸収する。
  private def loadNotifyMode(): String = {
    val mode =
      sys.env.get("NOTIFY_MODE").map(s => asciiLower(s.trim)).getOrElse("slack")
    if (!validModes.contains(mode)) {
      throw new RuntimeException(
        s"invalid NOTIFY_MODE: ${mode} (must be one of ${validModes.mkString(", ")})"
      )
    }
    mode
  }

  // ASCII 範囲のみ小文字化する。引数なしの String.toLowerCase() は
  // Locale.getDefault() に依存し、LANG/LC_* が無い Lambda + Scala Native では
  // getDefault() が null を返して NPE になるため、Locale 非依存で実装する。
  private def asciiLower(s: String): String =
    s.map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)

  private def require(key: String): String =
    sys.env.getOrElse(
      key,
      throw new RuntimeException(
        s"required environment variable not set: ${key}"
      )
    )
}
