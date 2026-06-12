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
      slug: String = ""
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
      val from = user.map(_.toSlackLink()).getOrElse("")
      s"[${repoName}] <${html_url}|${title}> (from ${from})"
    }
  }

  case class PullBase(
      repo: Option[Repo] = None
  ) derives ReadWriter
}
