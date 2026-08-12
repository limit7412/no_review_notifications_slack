package discord

import upickle.default._

// Discord webhook のペイロードモデル。
// https://discord.com/developers/docs/resources/webhook#execute-webhook
object Models {
  // Discord の embed。color は Int32(16進カラーコードを整数として解釈した値)。
  case class Embed(
      title: String = "",
      description: String = "",
      color: Int = 0
  ) derives ReadWriter

  // webhook のペイロード本体。embed 内ではメンションが機能しないため、
  // メンション(`@everyone` など)は content フィールドに設定する。
  case class Post(
      content: String = "",
      embeds: List[Embed] = Nil
  ) derives ReadWriter
}
