package github

import upickle.default._

object Models {
  case class Repo(
      name: String = "",
      full_name: String = "",
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
}
