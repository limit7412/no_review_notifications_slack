package github

import sttp.client4.quick._
import upickle.default._
import sttp.model.Method
import sttp.model.Uri
import scala.util.{Try, Success, Failure}
import errors.AppError

val GITHUB_API_URL = "https://api.github.com"

private def githubRequest(method: Method, path: Uri) = {
  basicRequest
    .method(method, path)
    .header("Authorization", s"token ${config.Config.instance.githubToken}")
}

// GitHub API から List[T] を取得する共通処理。
// HTTP エラーもパース失敗も AppError の Left として返し、
// 呼び出し側(Usecase 層)で集約・判断できるようにする。
private def getList[T: Reader](
    path: Uri,
    label: => String
): Either[AppError, List[T]] =
  githubRequest(Method.GET, path).send().body match {
    case Right(res) =>
      Try(read[List[T]](res)) match {
        case Success(list) => Right(list)
        case Failure(e) => Left(AppError(s"failed to parse ${label}", Some(e)))
      }
    case Left(e) =>
      Left(AppError(s"${label} not found: ${e}"))
  }

object RepoRepository {
  def findByUsername(username: String): Either[AppError, List[Models.Repo]] =
    getList[Models.Repo](
      uri"${GITHUB_API_URL}/users/${username}/repos",
      s"user(${username}) repos data"
    )

  def findByTeam(
      login: String,
      slug: String
  ): Either[AppError, List[Models.Repo]] =
    getList[Models.Repo](
      uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/repos",
      s"team(${login}, ${slug}) repos data"
    )
}

object OrganizationRepository {
  def findByUsername(
      username: String
  ): Either[AppError, List[Models.Organization]] =
    getList[Models.Organization](
      uri"${GITHUB_API_URL}/users/${username}/orgs",
      s"user(${username}) orgs data"
    )
}

object TeamRepository {
  def findByOrganization(login: String): Either[AppError, List[Models.Team]] =
    getList[Models.Team](
      uri"${GITHUB_API_URL}/orgs/${login}/teams",
      s"org(${login}) teams data"
    )
}

object UserRepository {
  def findByTeam(
      login: String,
      slug: String
  ): Either[AppError, List[Models.User]] =
    getList[Models.User](
      uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/members",
      s"team(${login}, ${slug}) members data"
    )
}

object PullRepository {
  def findByFullName(
      owner: String,
      name: String
  ): Either[AppError, List[Models.Pull]] =
    getList[Models.Pull](
      uri"${GITHUB_API_URL}/repos/${owner}/${name}/pulls",
      s"target(${owner}, ${name}) pulls data"
    )
}
