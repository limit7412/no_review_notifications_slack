package notify

// 送信先非依存の通知カラー(16進カラーコードで保持)。
// もとは slack.Color として Slack Attachment 用に定義されていたものを、
// Slack と Discord の双方から使える中立モデルとして notify 側へ移して一般化した。
// 各アダプタが必要な形式(Slack: 16進文字列 / Discord: Int)へ変換する。
// 名称は対応する CSS カラー名に準拠する。
enum Color(val hex: String) {
  case Crimson extends Color("#dc143c")
  case DarkOrange extends Color("#ff8c00")
  case DodgerBlue extends Color("#1e90ff")
  case Gray extends Color("#d8d8d8")
}

object Color {
  // 条件が真なら指定色、偽なら非アクティブ色(Gray)を返す。
  def select(active: Boolean, color: Color): Color =
    if (active) color else Gray
}
