package notify

// 送信先(Slack / Discord)非依存の中立通知モデル。
// リンクは URL と表示テキストを分離して保持し、各媒体の記法への変換は
// アダプタ(slack.Poster / discord.Poster)側に委ねる。
object Models {
  // 表記非依存のリンク。アダプタが `<url|text>`(Slack)や `[text](url)`(Discord)へ変換する。
  case class Link(url: String, text: String)

  // 1件の PR 項目。リポジトリ名・PR へのリンク・作成者へのリンク(任意)を保持する。
  case class PullItem(repo: String, pull: Link, from: Option[Link])

  // 通知内の1セクション(Slack の1 attachment / Discord の1 embed に相当)。
  case class Section(title: String, color: Color, pulls: List[PullItem])

  // 統一メッセージ。メンション有無・本文(ヘッダ)・セクション群を持つ。
  case class Message(
      mention: Boolean,
      text: String,
      sections: List[Section]
  )
}
