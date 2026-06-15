package notify

import errors.AppError

object Usecase {
  def check: Either[AppError, Unit] =
    holiday.CheckHolidayRepository.get.flatMap { isHoliday =>
      github.Usecase.getAssignPulls.flatMap {
        case (assignPulls, reviewerPulls, teamReviewerPulls) =>
          slack.PostRepository.sendAttachments(
            buildAttachments(
              isHoliday,
              assignPulls,
              reviewerPulls,
              teamReviewerPulls
            )
          )
      }
    }

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
        color = if (reviewerPulls.nonEmpty) "#dc143c" else "#D8D8D8",
        text = reviewerPulls.map(_.toSlackLink()).mkString("\n")
      ),
      slack.Models.Attachment(
        title = "reviewer(team)",
        color = if (teamReviewerPulls.nonEmpty) "#ff8c00" else "#D8D8D8",
        text = teamReviewerPulls.map(_.toSlackLink()).mkString("\n")
      ),
      slack.Models.Attachment(
        title = "assign",
        color = if (assignPulls.nonEmpty) "#1e90ff" else "#D8D8D8",
        text = assignPulls.map(_.toSlackLink()).mkString("\n")
      )
    )
  }
}
