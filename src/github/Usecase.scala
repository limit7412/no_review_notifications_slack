package github

import upickle.default._

object Usecase {
  def getRepos = {
    val userName = sys.env("GITHUB_USERNAME")

    val userRepos = RepoRepository.findByUsername(userName)

    val orgTeamsMap = OrganizationRepository
      .findByUsername(userName)
      .map { org =>
        val team = TeamRepository
          .findByOrganization(org.login)
          .filter { team =>
            UserRepository
              .findByTeam(org.login, team.slug)
              .exists(_.login == userName)
          }

        (org, team)
      }
      .toMap

    val teamRepos =
      orgTeamsMap.flatMap { (org, teams) =>
        teams.flatMap(team => RepoRepository.findByTeam(org.login, team.slug))
      }.toList

    val teamSlugs = orgTeamsMap
      .flatMap((_, teams) => teams)
      .toList
      .map(_.slug)

    (userRepos ++ teamRepos, teamSlugs)
  }

  def getAssignPulls = {
    val userName = sys.env("GITHUB_USERNAME")

    val (repos, teamSlugs) = getRepos

    val allPulls = repos
      .flatMap(repo =>
        PullRepository.findByFullName(repo.owner.login, repo.name)
      )

    val assignPulls = allPulls
      .filter(_.assignees.exists(_.login == userName))

    val reviewerPulls = allPulls
      .filter(_.requested_reviewers.exists(_.login == userName))

    val teamReviewerPulls = allPulls
      .filter(_.requested_teams.exists(team => teamSlugs.contains(team.slug)))

    (assignPulls, reviewerPulls, teamReviewerPulls)
  }
}
