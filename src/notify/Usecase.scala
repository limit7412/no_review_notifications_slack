package notify

import errors.AppError

object Usecase {
  // 送信先は Poster として注入する。ユースケース層は中立モデルのみを扱い、
  // Slack/Discord 固有の表記(リンク・メンション・色)には一切依存しない。
  def check(poster: Poster): Either[AppError, Unit] =
    for {
      isHoliday <- holiday.CheckHolidayRepository.get
      pulls <- github.Usecase.getAssignPulls
      (assignPulls, reviewerPulls, teamReviewerPulls) = pulls
      _ <-
        // 通知対象がすべて本人作成のPRなら誰かのレビュー待ちではないため通知しない
        if (
          isOnlySelfAuthored(
            config.Config.instance.githubUsername,
            assignPulls ++ reviewerPulls ++ teamReviewerPulls
          )
        ) Right(())
        else
          poster.post(
            buildMessage(
              isHoliday,
              assignPulls,
              reviewerPulls,
              teamReviewerPulls
            )
          )
    } yield ()

  // 通知対象のPRが1件以上あり、かつ全件が本人(userName)作成かを判定する。
  // 0件の場合は従来どおり通知するため false。作成者不明(user が None)のPRは
  // 判定できないため他人作成とみなし、通知する安全側に倒す。
  private[notify] def isOnlySelfAuthored(
      userName: String,
      pulls: List[github.Models.Pull]
  ): Boolean =
    pulls.nonEmpty && pulls.forall(_.user.exists(_.login == userName))

  // 通知メッセージ(中立モデル)を組み立てる純粋ロジック。
  private def buildMessage(
      isHoliday: Boolean,
      assignPulls: List[github.Models.Pull],
      reviewerPulls: List[github.Models.Pull],
      teamReviewerPulls: List[github.Models.Pull]
  ): Models.Message = {
    // 片方でもレビュアー指名されているPRが存在すれば通知対象
    val isReviewer = reviewerPulls.nonEmpty || teamReviewerPulls.nonEmpty

    // メンションはレビュー依頼があり、かつ休日でないときのみ付与する
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
