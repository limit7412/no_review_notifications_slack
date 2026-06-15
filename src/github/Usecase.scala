package github

import errors.{AppError, traverse}

object Usecase {
  def getRepos: Either[AppError, (List[Models.Repo], List[String])] = {
    val userName = config.Config.instance.githubUsername

    for {
      // 本人が所有するリポジトリ
      userRepos <- RepoRepository.findByUsername(userName)

      // 本人が所属する org 一覧
      orgs <- OrganizationRepository.findByUsername(userName)

      // 各 org について、本人がメンバーになっているチームだけを抽出する
      // (org -> 所属チーム一覧 の対応)
      orgTeams <- traverse(orgs)(memberTeamsOf(_, userName))

      // 本人が所属するチームがアクセスできるリポジトリ
      // (org/team を平坦化してから順に取得し、失敗時は以降を呼ばず短絡する)
      teamReposNested <- traverse(
        orgTeams.flatMap((org, teams) => teams.map(team => (org, team)))
      ) { case (org, team) => RepoRepository.findByTeam(org.login, team.slug) }
    } yield {
      val teamRepos = teamReposNested.flatten
      // 本人が所属するチームの slug 一覧。後段でチーム宛レビュー依頼の判定に使う
      val teamSlugs = orgTeams.flatMap((_, teams) => teams).map(_.slug)
      (userRepos ++ teamRepos, teamSlugs)
    }
  }

  // 指定 org のチームのうち、本人がメンバーになっているものだけを返す
  private def memberTeamsOf(
      org: Models.Organization,
      userName: String
  ): Either[AppError, (Models.Organization, List[Models.Team])] =
    for {
      teams <- TeamRepository.findByOrganization(org.login)
      memberships <- traverse(teams) { team =>
        UserRepository
          .findByTeam(org.login, team.slug)
          .map(members => (team, members.exists(_.login == userName)))
      }
    } yield (org, memberships.collect { case (team, true) => team })

  def getAssignPulls: Either[
    AppError,
    (List[Models.Pull], List[Models.Pull], List[Models.Pull])
  ] = {
    val userName = config.Config.instance.githubUsername

    for {
      reposAndSlugs <- getRepos
      (repos, teamSlugs) = reposAndSlugs

      // 対象リポジトリすべての open PR を集約する
      // repo.owner は Option のため owner 不明のリポジトリは対象から除外し、
      // (owner, repo) を順に取得して失敗時は以降を呼ばず短絡する
      pullsNested <- traverse(
        repos.flatMap(repo => repo.owner.toList.map(owner => (owner, repo)))
      ) { case (owner, repo) =>
        PullRepository.findByFullName(owner.login, repo.name)
      }
    } yield {
      val allPulls = pullsNested.flatten

      // 本人が assignee に指定されている PR
      val assignPulls = allPulls.filter(_.assignees.exists(_.login == userName))
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
