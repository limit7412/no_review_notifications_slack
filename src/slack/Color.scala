package slack

// Slack Attachment の色(16進カラーコード)。
// 各所に直書きされていたマジック文字列を一元化する。
// 名称は対応する CSS カラー名に準拠。
enum Color(val hex: String) {
  case Crimson extends Color("#dc143c")
  case DarkOrange extends Color("#ff8c00")
  case DodgerBlue extends Color("#1e90ff")
  case Gray extends Color("#D8D8D8")
}
