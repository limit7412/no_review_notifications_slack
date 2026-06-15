package github

import errors.{AppError, traverse}

object Usecase {
  def getRepos: Either[AppError, (List[Models.Repo], List[String])] = {
    val userName = config.Config.instance.githubUsername

    // 本人が所有するリポジトリ
    RepoRepository.findByUsername(userName).flatMap { userRepos =>
      // 本人が所属する org 一覧
      OrganizationRepository.findByUsername(userName).flatMap { orgs =>
        // 各 org について、本人がメンバーになっているチームだけを抽出する
        // (org -> 所属チーム一覧 の対応)
        traverse(orgs)(memberTeamsOf(_, userName)).flatMap { orgTeams =>
          // 本人が所属するチームがアクセスできるリポジトリ
          // (org/team を平坦化してから順に取得し、失敗時は以降を呼ばず短絡する)
          traverse(
            orgTeams.flatMap((org, teams) => teams.map(team => (org, team)))
          ) { case (org, team) =>
            RepoRepository.findByTeam(org.login, team.slug)
          }.map { teamReposNested =>
            val teamRepos = teamReposNested.flatten
            // 本人が所属するチームの slug 一覧。後段でチーム宛レビュー依頼の判定に使う
            val teamSlugs = orgTeams.flatMap((_, teams) => teams).map(_.slug)
            (userRepos ++ teamRepos, teamSlugs)
          }
        }
      }
    }
  }

  // 指定 org のチームのうち、本人がメンバーになっているものだけを返す
  private def memberTeamsOf(
      org: Models.Organization,
      userName: String
  ): Either[AppError, (Models.Organization, List[Models.Team])] =
    TeamRepository.findByOrganization(org.login).flatMap { teams =>
      traverse(teams) { team =>
        UserRepository
          .findByTeam(org.login, team.slug)
          .map(members => (team, members.exists(_.login == userName)))
      }.map(memberships =>
        (org, memberships.collect { case (team, true) => team })
      )
    }

  def getAssignPulls: Either[
    AppError,
    (List[Models.Pull], List[Models.Pull], List[Models.Pull])
  ] = {
    val userName = config.Config.instance.githubUsername

    getRepos.flatMap { case (repos, teamSlugs) =>
      // 対象リポジトリすべての open PR を集約する
      // repo.owner は Option のため owner 不明のリポジトリは対象から除外し、
      // (owner, repo) を順に取得して失敗時は以降を呼ばず短絡する
      traverse(
        repos.flatMap(repo => repo.owner.toList.map(owner => (owner, repo)))
      ) { case (owner, repo) =>
        PullRepository.findByFullName(owner.login, repo.name)
      }.map { pullsNested =>
        val allPulls = pullsNested.flatten

        // 本人が assignee に指定されている PR
        val assignPulls =
          allPulls.filter(_.assignees.exists(_.login == userName))
        // 本人が個人としてレビュアー指名されている PR
        val reviewerPulls =
          allPulls.filter(_.requested_reviewers.exists(_.login == userName))
        // 本人の所属チームがレビュアー指名されている PR
        val teamReviewerPulls =
          allPulls.filter(
            _.requested_teams.exists(team => teamSlugs.contains(team.slug))
          )

        (assignPulls, reviewerPulls, teamReviewerPulls)
      }
    }
  }
}
