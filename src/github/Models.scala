package github

import upickle.default._

object Models {
  case class Repo(
      name: String = "",
      full_name: String = "",
      owner: User = null,
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
      login: String = ""
  ) derives ReadWriter

  case class Pull(
      url: String = "",
      title: String = "",
      state: String = "",
      assignees: List[User] = Nil,
      requested_reviewers: List[User] = Nil,
      requested_teams: List[Team] = Nil
  ) derives ReadWriter
}
