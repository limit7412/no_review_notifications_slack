package slack

import upickle.default._

object Models {
  case class Attachment(
      fallback: String = "",
      authorName: String = "",
      authorIcon: String = "",
      authorLink: String = "",
      pretext: String = "",
      color: String = "",
      title: String = "",
      titleLink: String = "",
      text: String = "",
      footer: String = "",
      footerIcon: String = ""
  ) derives ReadWriter

  case class Post(
      attachments: Array[Attachment]
  ) derives ReadWriter

}
