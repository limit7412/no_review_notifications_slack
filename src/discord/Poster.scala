package discord

import errors.{AppError, traverse}
import notify.Models.{Message, Section, PullItem, Link}

// notify.Poster の Discord 実装。中立モデル Message を Discord webhook の
// content + embeds へ変換する。Discord 固有の表記(`<@id>` メンション・
// `[text](url)` リンク・Int カラー)はこのアダプタ内に閉じ込める。
object Poster extends notify.Poster {
  // Discord の制限
  private val MaxEmbeds = 10 // 1メッセージあたり最大 embed 数
  private val MaxDescription = 4096 // embed description の最大文字数

  def post(message: Message): Either[AppError, Unit] = {
    // embed 内ではメンションが機能しないため content に本文と共に設定する
    val mention =
      if (message.mention) s"<@${config.Config.instance.mentionId}> " else ""
    val content = mention + message.text

    val embeds = message.sections.map(sectionToEmbed)

    // 1メッセージ最大 10 embeds のため分割する。embed が無い場合でも
    // content(メンション + 本文)を送るため空チャンクを1つ用意する。
    val chunks = embeds.grouped(MaxEmbeds).toList match {
      case Nil     => List(List.empty[Models.Embed])
      case grouped => grouped
    }

    // メンション + 本文は先頭メッセージにのみ付与し、重複投稿を避ける
    traverse(chunks.zipWithIndex) { case (chunk, idx) =>
      PostRepository.sendPost(
        Models.Post(
          content = if (idx == 0) content else "",
          embeds = chunk
        )
      )
    }.map(_ => ())
  }

  private def sectionToEmbed(section: Section): Models.Embed =
    Models.Embed(
      title = section.title,
      description = truncate(
        section.pulls.map(pullItemText).mkString("\n"),
        MaxDescription
      ),
      color = hexToInt(section.color.hex)
    )

  private def truncate(s: String, max: Int): String =
    if (s.length <= max) s else s.take(max)

  // Discord のリンク記法 `[text](url)`
  private def linkText(link: Link): String = s"[${link.text}](${link.url})"

  private def pullItemText(item: PullItem): String = {
    val fromSuffix =
      item.from.map(f => s" (from ${linkText(f)})").getOrElse("")
    s"[${item.repo}] ${linkText(item.pull)}$fromSuffix"
  }

  // "#dc143c" -> 0xdc143c(Discord embed の color は Int32)
  private def hexToInt(hex: String): Int =
    Integer.parseInt(hex.stripPrefix("#"), 16)
}
