package notify

import slack.PostRepository
import slack.Models

object Usecase {
  def check = {
    val isHoliday = holiday.CheckHolidayRepository.get

    val (assignPulls, reviewerPulls, teamReviewerPulls) =
      github.Usecase.getAssignPulls

    // 自分(個人) または 所属チーム宛のレビュー依頼が1件でも残っているか。
    // これが true のときだけ「至急対応すべきレビューがある」状態とみなす。
    // (単にレビュアー以外でアサインされているだけの状態とは区別する)
    val isReviewer = reviewerPulls.nonEmpty || teamReviewerPulls.nonEmpty

    // 至急対応すべきレビューがあり、かつ平日のときだけ本人にメンションする。
    // 休日は通知だけ行いメンション(呼び出し)はしない。
    val mention = if (isReviewer && !isHoliday) {
      s"<@${sys.env("SLACK_ID")}> "
    } else {
      ""
    }

    // レビュー依頼の有無でメッセージの文面(催促 or 単なるお知らせ)を切り替える。
    val message = if (isReviewer) {
      "レビュー依頼が残っているみたいです！至急確認しましょう！"
    } else {
      "現在アサインされているレビューをお知らせします！"
    }

    val attachment = List(
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

    slack.PostRepository.sendAttachments(attachment)
  }
}
