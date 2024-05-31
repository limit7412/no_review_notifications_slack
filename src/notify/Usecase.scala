package notify

import slack.PostRepository
import slack.Models

object Usecase {
  def check = {
    var attachment = Array(
      slack.Models.Attachment(
        title = "test"
      )
    )

    slack.PostRepository.sendAttachments(attachment)
  }
}
