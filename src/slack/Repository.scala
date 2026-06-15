package slack

import sttp.client4.quick._
import upickle.default._
import errors.AppError

object PostRepository {
  private def sendPost(post: Models.Post): Either[AppError, Unit] =
    basicRequest
      .post(uri"${config.Config.instance.webhookUrl}")
      .body(write(post))
      .send()
      .body match {
      case Right(_) => Right(())
      case Left(e)  => Left(AppError(s"failed to send slack message: ${e}"))
    }

  def sendAttachment(attachment: Models.Attachment): Either[AppError, Unit] =
    sendPost(Models.Post(List(attachment)))

  def sendAttachments(
      attachments: List[Models.Attachment]
  ): Either[AppError, Unit] =
    sendPost(Models.Post(attachments))
}
