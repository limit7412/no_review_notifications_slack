package notify

import errors.AppError

object Usecase {
  def check: Either[AppError, Unit] =
    for {
      isHoliday <- holiday.CheckHolidayRepository.get
      pulls <- github.Usecase.getAssignPulls
      (assignPulls, reviewerPulls, teamReviewerPulls) = pulls
      _ <- slack.PostRepository.sendAttachments(
        buildAttachments(
          isHoliday,
          assignPulls,
          reviewerPulls,
          teamReviewerPulls
        )
      )
    } yield ()

  // 通知メッセージ(Slack Attachment)を組み立てる純粋ロジック
  private def buildAttachments(
      isHoliday: Boolean,
      assignPulls: List[github.Models.Pull],
      reviewerPulls: List[github.Models.Pull],
      teamReviewerPulls: List[github.Models.Pull]
  ): List[slack.Models.Attachment] = {
    // 片方でもレビュアー指名されているPRが存在すれば通知対象
    val isReviewer = reviewerPulls.nonEmpty || teamReviewerPulls.nonEmpty

    val mention = if (isReviewer && !isHoliday) {
      s"<@${config.Config.instance.slackId}> "
    } else {
      ""
    }

    val message = if (isReviewer) {
      "レビュー依頼が残っているみたいです！至急確認しましょう！"
    } else {
      "現在アサインされているレビューをお知らせします！"
    }

    List(
      slack.Models.Attachment(
        fallback = message,
        pretext = mention + message
      ),
      slack.Models.Attachment(
        title = "reviewer",
        color = (if (reviewerPulls.nonEmpty) slack.Color.Crimson
                 else slack.Color.Gray).hex,
        text = reviewerPulls.map(_.toSlackLink()).mkString("\n")
      ),
      slack.Models.Attachment(
        title = "reviewer(team)",
        color = (if (teamReviewerPulls.nonEmpty) slack.Color.DarkOrange
                 else slack.Color.Gray).hex,
        text = teamReviewerPulls.map(_.toSlackLink()).mkString("\n")
      ),
      slack.Models.Attachment(
        title = "assign",
        color = (if (assignPulls.nonEmpty) slack.Color.DodgerBlue
                 else slack.Color.Gray).hex,
        text = assignPulls.map(_.toSlackLink()).mkString("\n")
      )
    )
  }
}
