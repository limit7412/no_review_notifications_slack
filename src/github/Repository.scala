package github

import sttp.client4.quick._
import upickle.default._
import sttp.model.Method
import sttp.model.Uri

val GITHUB_API_URL = "https://api.github.com"

private def githubRequest(method: Method, path: Uri) = {
  basicRequest
    .method(method, path)
    .header("Authorization", s"token ${sys.env("GITHUB_TOKEN")}")
}

object RepoRepository {
  def findByUsername(username: String) = {
    var response =
      githubRequest(Method.GET, uri"${GITHUB_API_URL}/users/${username}/repos")
        .send()

    val body = response.body match {
      case Right(res) => res
      case Left(e) => {
        println(s"user repos data not found: ${e}")
        "[]"
      }
    }

    read[List[Models.Repo]](body)
  }

  def findByTeam(login: String, slug: String) = {
    var response =
      githubRequest(
        Method.GET,
        uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/repos"
      )
        .send()

    val body = response.body match {
      case Right(res) => res
      case Left(e) => {
        println(s"team repos data not found: ${e}")
        "[]"
      }
    }

    read[List[Models.Repo]](body)
  }
}

object OrganizationRepository {
  def findByUsername(username: String) = {
    var response =
      githubRequest(Method.GET, uri"${GITHUB_API_URL}/users/${username}/orgs")
        .send()

    val body = response.body match {
      case Right(res) => res
      case Left(e) => {
        println(s"user orgs data not found: ${e}")
        "[]"
      }
    }

    read[List[Models.Organization]](body)
  }
}

object TeamRepository {
  def findByOrganization(login: String) = {
    var response =
      githubRequest(Method.GET, uri"${GITHUB_API_URL}/orgs/${login}/teams")
        .send()

    val body = response.body match {
      case Right(res) => res
      case Left(e) => {
        println(s"org teams data not found: ${e}")
        "[]"
      }
    }

    read[List[Models.Team]](body)
  }
}

object UserRepository {
  def findByTeam(login: String, slug: String) = {
    var response =
      githubRequest(
        Method.GET,
        uri"${GITHUB_API_URL}/orgs/${login}/teams/${slug}/members"
      )
        .send()

    val body = response.body match {
      case Right(res) => res
      case Left(e) => {
        println(s"team members data not found: ${e}")
        "[]"
      }
    }

    read[List[Models.User]](body)
  }
}

object PullRepository {
  // /repos/{owner}/{repo}/pulls
  // /repos/{full_name}/pulls
}
