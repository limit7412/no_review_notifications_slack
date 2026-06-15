import serverless.Lambda
import notify.Usecase
import errors.AppError

@main def main = config.Config.instance.env match {
  case "local" =>
    // Left は例外として送出し非0終了させる(ローカル/CI で失敗を検知できるように)
    handler("1970-01-01T00:00:00Z") match {
      case Right(_)  => ()
      case Left(err) => throw err.toException
    }
  case _ =>
    serverless.Lambda
      .Handler[serverless.Lambda.CloudWatchScheduledEventRequest](
        "handler",
        (event) => {
          // Usecase の Left は例外に変換し、Lambda ランタイムのエラー応答に委ねる
          handler(event.time) match {
            case Right(_)  => serverless.Lambda.Response(200, "ok")
            case Left(err) => throw err.toException
          }
        }
      )
}

def handler(time: String): Either[AppError, Unit] = notify.Usecase.check
