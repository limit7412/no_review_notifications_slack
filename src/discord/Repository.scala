package discord

import sttp.client4.quick._
import upickle.default._
import errors.AppError

object PostRepository {
  // Discord webhook は Content-Type: application/json を要求するため明示的に指定する
  // (Slack は text/plain でも受け付けるが Discord は受け付けない)。
  def sendPost(post: Models.Post): Either[AppError, Unit] =
    basicRequest
      .post(uri"${config.Config.instance.webhookUrl}")
      .header("Content-Type", "application/json")
      .body(write(post))
      .send()
      .body match {
      case Right(_) => Right(())
      case Left(e)  => Left(AppError(s"failed to send discord message: ${e}"))
    }
}
