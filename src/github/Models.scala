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
    def toSlackLink() = {
      s"<${html_url}|${login}>"
    }
  }

  case class Pull(
      html_url: String = "",
      title: String = "",
      state: String = "",
      user: Option[User] = None,
      assignees: List[User] = Nil,
      requested_reviewers: List[User] = Nil,
      requested_teams: List[Team] = Nil,
      base: Option[PullBase] = None
  ) derives ReadWriter {
    def toSlackLink() = {
      val repoName = base.flatMap(_.repo).map(_.full_name).getOrElse("")
      val fromSuffix =
        user.map(u => s" (from ${u.toSlackLink()})").getOrElse("")
      s"[${repoName}] <${html_url}|${title}>$fromSuffix"
    }
  }

  case class PullBase(
      repo: Option[Repo] = None
  ) derives ReadWriter
}
