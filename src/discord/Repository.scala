package discord

import scala.util.control.NonFatal
import sttp.client4.quick._
import upickle.default._
import errors.AppError

object PostRepository {
  // Discord webhook は Content-Type: application/json を要求するため明示的に指定する
  // (Slack は text/plain でも受け付けるが Discord は受け付けない)。
  // send() はネットワーク断・DNS 解決失敗・タイムアウト等で例外を送出しうるため
  // NonFatal を捕捉して Left に変換し、境界(main)で一貫して扱えるようにする。
  def sendPost(post: Models.Post): Either[AppError, Unit] =
    try
      basicRequest
        .post(uri"${config.Config.instance.webhookUrl}")
        .header("Content-Type", "application/json")
        .body(write(post))
        .send()
        .body match {
        case Right(_) => Right(())
        case Left(e)  => Left(AppError(s"failed to send discord message: ${e}"))
      }
    catch {
      case NonFatal(e) =>
        Left(AppError(s"failed to send discord message: ${e.getMessage}", Some(e)))
    }
}
