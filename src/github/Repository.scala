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

object UserRepository {
  def getAllRepos(username: String) = {
    var response =
      githubRequest(Method.GET, uri"${GITHUB_API_URL}/users/${username}/repos")
        .send()

    response.body
  }
}

object TeamRepository {
  // /users/{username}/orgs
  // /orgs/{org}/teams
  // /orgs/{org}/teams/{team_slug}/members
  // /orgs/{org}/teams/{team_slug}/repos
}

object PullRepository {
  // /repos/{owner}/{repo}/pulls
}
