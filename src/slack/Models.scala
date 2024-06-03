package slack

import upickle.default._

object Models {
  case class Attachment(
      fallback: String = "",
      author_Name: String = "",
      author_icon: String = "",
      author_link: String = "",
      pretext: String = "",
      color: String = "",
      title: String = "",
      title_link: String = "",
      text: String = "",
      footer: String = "",
      footerIcon: String = ""
  ) derives ReadWriter

  case class Post(
      attachments: List[Attachment]
  ) derives ReadWriter

}
