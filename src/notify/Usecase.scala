package notify

import errors.AppError

object Usecase {
  // 送信先は Poster として注入する。ユースケース層は中立モデルのみを扱い、
  // Slack や Discord に固有の表記(リンクの記法、メンション、色の形式)には依存しない。
  def check(poster: Poster): Either[AppError, Unit] =
    for {
      isHoliday <- holiday.CheckHolidayRepository.get
      pulls <- github.Usecase.getAssignPulls
      (assignPulls, reviewerPulls, teamReviewerPulls) = pulls
      _ <- poster.post(
        buildMessage(
          config.Config.instance.githubUsername,
          isHoliday,
          assignPulls,
          reviewerPulls,
          teamReviewerPulls
        )
      )
    } yield ()

  // 自分がレビューすべき依頼が残っているかを判定する。
  // レビュアー指名のうち自分(userName)が作成した PR は、自分のレビュー待ちではないため除く。
  // 個人宛のレビュー依頼に自分の PR が入ることは GitHub の仕様上ないが、
  // チーム宛のレビュー依頼では自分の PR に自分の所属チームを指名できるため入りうる。
  // 作成者不明(user が None)の PR は他人作成とみなし、メンションする安全側に倒す。
  private[notify] def hasReviewRequest(
      userName: String,
      reviewerPulls: List[github.Models.Pull],
      teamReviewerPulls: List[github.Models.Pull]
  ): Boolean =
    (reviewerPulls ++ teamReviewerPulls)
      .exists(pull => !pull.user.exists(_.login == userName))

  // 通知メッセージ(中立モデル)を組み立てる純粋ロジック。
  private def buildMessage(
      userName: String,
      isHoliday: Boolean,
      assignPulls: List[github.Models.Pull],
      reviewerPulls: List[github.Models.Pull],
      teamReviewerPulls: List[github.Models.Pull]
  ): Models.Message = {
    val isReviewer = hasReviewRequest(userName, reviewerPulls, teamReviewerPulls)

    // メンションは、他人のレビュー依頼があり、かつ休日でないときのみ付与する。
    // 自分の PR しかない場合はチャンネル全体を呼ばずに済ませる (#36, #45)。
    val mention = isReviewer && !isHoliday

    val text = if (isReviewer) {
      "レビュー依頼が残っているみたいです！至急確認しましょう！"
    } else {
      "現在アサインされているレビューをお知らせします！"
    }

    Models.Message(
      mention = mention,
      text = text,
      sections = List(
        Models.Section(
          title = "reviewer",
          color = Color.select(reviewerPulls.nonEmpty, Color.Crimson),
          pulls = reviewerPulls.map(_.toPullItem())
        ),
        Models.Section(
          title = "reviewer(team)",
          color = Color.select(teamReviewerPulls.nonEmpty, Color.DarkOrange),
          pulls = teamReviewerPulls.map(_.toPullItem())
        ),
        Models.Section(
          title = "assign",
          color = Color.select(assignPulls.nonEmpty, Color.DodgerBlue),
          pulls = assignPulls.map(_.toPullItem())
        )
      )
    )
  }
}
