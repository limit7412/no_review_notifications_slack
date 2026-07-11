import errors.AppError

@main def main = config.Config.instance.env match {
  case "local" =>
    // Left は例外として送出し非0終了させる(ローカル/CI で失敗を検知できるように)
    handler() match {
      case Right(_)  => ()
      case Left(err) => throw err.toException
    }
  case _ =>
    serverless.Lambda
      .Handler[serverless.Lambda.CloudWatchScheduledEventRequest](
        "handler",
        // イベント内容は使用しないため受け取らない
        _ => {
          // Usecase の Left は例外に変換し、Lambda ランタイムのエラー応答に委ねる
          handler() match {
            case Right(_)  => serverless.Lambda.Response(200, "ok")
            case Left(err) => throw err.toException
          }
        }
      )
}

def handler(): Either[AppError, Unit] =
  notify.Usecase.check(selectPoster())

// NOTIFY_MODE に応じた Poster 実装を選択する。値は config.Config の読み込み時に
// 検証済みのため、ここでは slack / discord のみを扱う。
def selectPoster(): notify.Poster =
  config.Config.instance.notifyMode match {
    case "discord" => discord.Poster
    case _         => slack.Poster
  }
