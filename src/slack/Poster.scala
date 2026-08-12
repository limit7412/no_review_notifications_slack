package slack

import errors.AppError
import notify.Models.{Message, Section, PullItem, Link}

// notify.Poster の Slack 実装。中立モデル Message を Slack の legacy attachment
// 形式へ変換する。Slack 固有の表記(`<!channel>` メンションや `<url|text>` リンク、
// 16進カラーコード)はこのアダプタ内に閉じ込める。
object Poster extends notify.Poster {
  def post(message: Message): Either[AppError, Unit] =
    PostRepository.sendAttachments(toAttachments(message))

  private def toAttachments(message: Message): List[Models.Attachment] = {
    // チャンネル全体メンション(Slack の `<!channel>`)。
    val mention = if (message.mention) "<!channel> " else ""

    // ヘッダ(メンション + 本文)は、色もタイトルも持たない attachment とする。
    val header = Models.Attachment(
      fallback = message.text,
      pretext = mention + message.text
    )

    header :: message.sections.map { section =>
      Models.Attachment(
        title = section.title,
        color = section.color.hex,
        text = section.pulls.map(pullItemText).mkString("\n")
      )
    }
  }

  // Slack のリンク記法 `<url|text>`
  private def linkText(link: Link): String = s"<${link.url}|${link.text}>"

  private def pullItemText(item: PullItem): String = {
    val fromSuffix =
      item.from.map(f => s" (from ${linkText(f)})").getOrElse("")
    s"[${item.repo}] ${linkText(item.pull)}$fromSuffix"
  }
}
