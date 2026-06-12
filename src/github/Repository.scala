package github

import sttp.client4.quick._
import upickle.default._
import sttp.model.Method
import sttp.model.Uri

val GITHUB_API_URL = "https://api.github.com"

private def githubRequest(method: Method, path: Uri) = {
  basicRequest
    .method(method, path)
    .header("Authorization", s"token ${config.Config.instance.githubToken}")
}

// GitHub API から List[T] を取得する共通処理。
// 取得失敗時は label を添えて警告ログを出し、空リストを返して処理を継続する。
private def getList[T: Reader](path: Uri, label: => String): List[T] =
  githubRequest(Method.GET, path).send().body match {
    case Right(res) => read[List[T]](res)
    case Left(e) =>
      System.err.println(s"${label} not found: ${e}")
      Nil
  }

object RepoRepository {
  def findByUsername(username: String) =
    getList[Models.Repo](
      uri"${GITHUB_API_URL}/users/${username}/repos",
      s"user(${username}) repos data"
    )

  def findByTeam(login: String, slug: String) =
    getList[Models.Repo](
      uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/repos",
      s"team(${login}, ${slug}) repos data"
    )
}

object OrganizationRepository {
  def findByUsername(username: String) =
    getList[Models.Organization](
      uri"${GITHUB_API_URL}/users/${username}/orgs",
      s"user(${username}) orgs data"
    )
}

object TeamRepository {
  def findByOrganization(login: String) =
    getList[Models.Team](
      uri"${GITHUB_API_URL}/orgs/${login}/teams",
      s"org(${login}) teams data"
    )
}

object UserRepository {
  def findByTeam(login: String, slug: String) =
    getList[Models.User](
      uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/members",
      s"team(${login}, ${slug}) members data"
    )
}

object PullRepository {
  def findByFullName(owner: String, name: String) =
    getList[Models.Pull](
      uri"${GITHUB_API_URL}/repos/${owner}/${name}/pulls",
      s"target(${owner}, ${name}) pulls data"
    )
}
