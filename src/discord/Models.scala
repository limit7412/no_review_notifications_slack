package discord

import upickle.default._

// Discord webhook のペイロードモデル。
// https://discord.com/developers/docs/resources/webhook#execute-webhook
object Models {
  // Discord の embed。color は Int32(16進を10進化した値)。
  case class Embed(
      title: String = "",
      description: String = "",
      color: Int = 0
  ) derives ReadWriter

  // webhook 本体。embed 内ではメンションが機能しないため、メンションは
  // content フィールドに `<@USER_ID>` として設定する。
  case class Post(
      content: String = "",
      embeds: List[Embed] = Nil
  ) derives ReadWriter
}
