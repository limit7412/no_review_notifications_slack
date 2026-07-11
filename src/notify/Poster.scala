package notify

import errors.AppError

// 通知送信の抽象。中立モデル Message を各媒体固有の表記へ変換して送信する。
// slack.Poster / discord.Poster が実装し、NOTIFY_MODE により差し替える。
trait Poster {
  def post(message: Models.Message): Either[AppError, Unit]
}
