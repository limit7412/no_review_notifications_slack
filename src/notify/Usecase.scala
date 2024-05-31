package notify

import slack.PostRepository
import slack.Models

object Usecase {
  def check = {
    val testRes = github.Usecase.getRepos
    println(testRes)

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
