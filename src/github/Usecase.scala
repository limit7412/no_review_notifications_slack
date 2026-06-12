package github

import upickle.default._

object Usecase {
  def getRepos = {
    val userName = sys.env("GITHUB_USERNAME")

    // 本人が所有するリポジトリ
    val userRepos = RepoRepository.findByUsername(userName)

    // 本人が所属する各 org について、「その org 内で本人がメンバーになっているチーム」だけを抽出し
    // org -> 所属チーム一覧 の対応を作る。
    // (org にチームが多数あっても、本人が無関係なチームは以降の集計対象から除外される)
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

    // 本人が所属するチームがアクセスできるリポジトリ
    val teamRepos =
      orgTeamsMap.flatMap { (org, teams) =>
        teams.flatMap(team => RepoRepository.findByTeam(org.login, team.slug))
      }.toList

    // 本人が所属するチームの slug 一覧。後段でチーム宛レビュー依頼の判定に使う。
    val teamSlugs = orgTeamsMap.values.flatten.map(_.slug).toList

    (userRepos ++ teamRepos, teamSlugs)
  }

  def getAssignPulls = {
    val userName = sys.env("GITHUB_USERNAME")

    val (repos, teamSlugs) = getRepos

    // 対象リポジトリすべての open PR を集約する。
    // repo.owner は Option のため .toList で List に変換し、owner 不明のリポジトリはスキップする
    // (Option と List を for 内包表記で混在させるとモナドが揃わずコンパイルできないため)。
    val allPulls = for {
      repo <- repos
      owner <- repo.owner.toList
      pull <- PullRepository.findByFullName(owner.login, repo.name)
    } yield pull

    // 本人が assignee に指定されている PR
    val assignPulls = allPulls
      .filter(_.assignees.exists(_.login == userName))

    // 本人が個人としてレビュアー指名されている PR
    val reviewerPulls = allPulls
      .filter(_.requested_reviewers.exists(_.login == userName))

    // 本人の所属チームがレビュアー指名されている PR
    val teamReviewerPulls = allPulls
      .filter(_.requested_teams.exists(team => teamSlugs.contains(team.slug)))

    (assignPulls, reviewerPulls, teamReviewerPulls)
  }
}
