package discord

import errors.{AppError, traverse}
import notify.Models.{Message, Section, PullItem, Link}

// notify.Poster の Discord 実装。中立モデル Message を Discord webhook の
// content + embeds へ変換する。Discord 固有の表記(`@everyone` メンションや
// `[text](url)` リンク、Int のカラー値)はこのアダプタ内に閉じ込める。
object Poster extends notify.Poster {
  // Discord 側の制限値
  private val MaxEmbedsPerMessage = 10 // 1メッセージあたり最大 embed 数
  private val MaxDescription = 4096 // embed description の最大文字数
  private val MaxMessageChars = 6000 // 1メッセージ内の全 embed 合計文字数

  def post(message: Message): Either[AppError, Unit] = {
    // チャンネル全体メンション(Discord の `@everyone`)は embed 内では機能しないため、
    // content に本文と共に設定する。
    val mention = if (message.mention) "@everyone " else ""
    val content = mention + message.text

    // 各セクションを、description が 4096 文字を超えないよう行単位で複数の embed に
    // 分割する(PR は切り捨てず全件残す)。
    val embeds = message.sections.flatMap(sectionToEmbeds)

    // embed を「最大10個」かつ「合計文字数 6000 以内」でメッセージにまとめる。
    // embed が無い場合でも content(メンション + 本文)を送るため、空のメッセージを1つ用意する。
    val messages = packEmbeds(embeds) match {
      case Nil    => List(List.empty[Models.Embed])
      case packed => packed
    }

    // content(メンション + 本文)は先頭メッセージにのみ付与し、重複投稿を避ける。
    traverse(messages.zipWithIndex) { case (chunk, idx) =>
      PostRepository.sendPost(
        Models.Post(
          content = if (idx == 0) content else "",
          embeds = chunk
        )
      )
    }.map(_ => ())
  }

  // セクションを embed 群へ変換する。description が 4096 文字を超える場合は
  // 行単位で分割し、複数の embed にまたがって全 PR を残す。
  // タイトルは先頭の embed のみに付け、継続の embed はタイトル無し(同色)にする。
  private def sectionToEmbeds(section: Section): List[Models.Embed] = {
    val color = hexToInt(section.color.hex)
    val descriptions = splitLines(section.pulls.map(pullItemText), MaxDescription)
    descriptions match {
      // PR が無いセクションもタイトルのみの embed として表示する(Slack と表示を揃える)。
      case Nil => List(Models.Embed(title = section.title, color = color))
      case head :: tail =>
        Models.Embed(title = section.title, description = head, color = color) ::
          tail.map(desc => Models.Embed(description = desc, color = color))
    }
  }

  // 行を "\n" で連結したとき maxChars を超えないよう、複数のチャンクにまとめる。
  // 1行だけで maxChars を超える場合はその行のみ切り詰める(通常は発生しない)。
  private def splitLines(lines: List[String], maxChars: Int): List[String] = {
    val (chunks, last) =
      lines.foldLeft((List.empty[String], "")) { case ((acc, current), line0) =>
        val line = if (line0.length > maxChars) line0.take(maxChars) else line0
        if (current.isEmpty) (acc, line)
        else if (current.length + 1 + line.length <= maxChars)
          (acc, s"$current\n$line")
        else (acc :+ current, line)
      }
    if (last.isEmpty) chunks else chunks :+ last
  }

  // embed を「最大 MaxEmbedsPerMessage 個」かつ「title+description の合計文字数が
  // MaxMessageChars 以内」になるようメッセージ単位へグループ化する。
  private def packEmbeds(embeds: List[Models.Embed]): List[List[Models.Embed]] = {
    val (messages, last, _) =
      embeds.foldLeft((List.empty[List[Models.Embed]], List.empty[Models.Embed], 0)) {
        case ((msgs, current, chars), embed) =>
          val size = embed.title.length + embed.description.length
          val overCount = current.length >= MaxEmbedsPerMessage
          val overChars = current.nonEmpty && chars + size > MaxMessageChars
          if (overCount || overChars) (msgs :+ current, List(embed), size)
          else (msgs, current :+ embed, chars + size)
      }
    if (last.isEmpty) messages else messages :+ last
  }

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
