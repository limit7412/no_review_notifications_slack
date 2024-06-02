package notify

import slack.PostRepository
import slack.Models

object Usecase {
  def check = {
    val pulls = github.Usecase.getAssignPulls

    // var attachment = List(
    //   slack.Models.Attachment(
    //     title = "test"
    //   ),
    //   slack.Models.Attachment(
    //     text = testRes
    //   )
    // )

    // slack.PostRepository.sendAttachments(attachment)
  }
}
