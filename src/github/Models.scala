package github

import upickle.default._

object Models {
  case class Repo(
      name: String = "",
      full_name: String = "",
      owner: Option[User] = None,
      pulls_url: String = ""
  ) derives ReadWriter

  case class Organization(
      login: String = "",
      repos_url: String = "",
      avatar_url: String = ""
  ) derives ReadWriter

  case class Team(
      name: String = "",
      slug: String = "",
      // GET /user/teams のレスポンスに含まれる所属 org 情報。
      // チーム宛のレビュー依頼(Pull.requested_teams)では返らないため Option。
      organization: Option[Organization] = None,
      // ネストされたチームの親チーム(直近1階層)。所属していないチームの
      // レスポンスには含まれないため Option。親チームの判定に使う。
      parent: Option[Team] = None
  ) derives ReadWriter

  case class User(
      login: String = "",
      html_url: String = ""
  ) derives ReadWriter {
    // 送信先非依存の中立リンク表現に変換する。表記(Slack/Discord)への
    // 変換は各アダプタ側に委ねる。
    def toLink(): _root_.notify.Models.Link =
      _root_.notify.Models.Link(html_url, login)
  }

  // PR に付いたラベル。一覧取得(GET /repos/{owner}/{repo}/pulls)の
  // レスポンスに含まれるため、追加の API 呼び出しなしで参照できる。
  case class Label(
      name: String = ""
  ) derives ReadWriter

  case class Pull(
      html_url: String = "",
      title: String = "",
      state: String = "",
      // レビュー準備が整っていない PR かどうか。draft の PR はレビュー待ちでは
      // ないため通知対象から除外する。
      draft: Boolean = false,
      user: Option[User] = None,
      assignees: List[User] = Nil,
      requested_reviewers: List[User] = Nil,
      requested_teams: List[Team] = Nil,
      labels: List[Label] = Nil,
      base: Option[PullBase] = None
  ) derives ReadWriter {
    // 指定したラベルのいずれかが付いているか。names は小文字に正規化済みの
    // 集合を渡す(Config.excludeLabels)。PR 側のラベル名も同じ規則で小文字に
    // 揃えて比較する。
    def hasAnyLabel(names: Set[String]): Boolean =
      labels.exists(label =>
        names.contains(config.Config.asciiLower(label.name.trim))
      )

    // 送信先非依存の中立 PR 項目に変換する。リポジトリ名、PR リンク、
    // 作成者リンク(任意)を分離して保持し、表記への変換はアダプタに委ねる。
    def toPullItem(): _root_.notify.Models.PullItem = {
      val repoName = base.flatMap(_.repo).map(_.full_name).getOrElse("")
      _root_.notify.Models.PullItem(
        repo = repoName,
        pull = _root_.notify.Models.Link(html_url, title),
        from = user.map(_.toLink())
      )
    }
  }

  case class PullBase(
      repo: Option[Repo] = None
  ) derives ReadWriter
}
