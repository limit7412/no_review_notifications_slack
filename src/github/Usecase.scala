package github

import upickle.default._

object Usecase {
  def getRepos = {
    val userName = sys.env("GITHUB_USERNAME")

    val userRepos = RepoRepository.findByUsername(userName)
    val teamRepos =
      OrganizationRepository
        .findByUsername(userName)
        .map({ org =>
          TeamRepository
            .findByOrganization(org.login)
            .filter({ team =>
              UserRepository
                .findByTeam(org.login, team.slug)
                .exists({ user =>
                  user.login == userName
                })
            })
            .map({ team =>
              RepoRepository.findByTeam(org.login, team.slug)
            })
            .flatten
        })
        .flatten

    write(teamRepos)
    // userRepos ++ teamRepos
  }
}
