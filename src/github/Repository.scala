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

// GitHub API のページネーションを最後まで辿って List[T] を全件取得する。
// per_page は GitHub の上限である 100 を指定し、取得件数が per_page 未満に
// なったページを最終ページとみなして打ち切る。エラー時はそこで短絡する。
private val PER_PAGE = 100

private def getListAll[T: Reader](
    path: Uri,
    label: => String
): Either[AppError, List[T]] = {
  def loop(page: Int, acc: List[T]): Either[AppError, List[T]] =
    getList[T](
      path.addParam("per_page", PER_PAGE.toString).addParam("page", page.toString),
      label
    ) match {
      case Left(e) => Left(e)
      case Right(list) =>
        val merged = acc ++ list
        if (list.size < PER_PAGE) Right(merged) else loop(page + 1, merged)
    }
  loop(1, Nil)
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

object TeamRepository {
  // 認証ユーザーが所属するチーム一覧を取得する。
  // 各チームには所属 org 情報(organization)が含まれるため、
  // org -> teams -> members の多段呼び出し(N+1)を排除できる。
  // /user/teams はページネーション対象のため全ページを辿って取得する。
  def findByAuthenticatedUser: Either[AppError, List[Models.Team]] =
    getListAll[Models.Team](
      uri"${GITHUB_API_URL}/user/teams",
      "authenticated user teams data"
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
