package github

import errors.{AppError, traverse}

object Usecase {
  def getRepos: Either[AppError, (List[Models.Repo], List[String])] = {
    val userName = config.Config.instance.githubUsername

    for {
      // 本人が所有するリポジトリ
      userRepos <- RepoRepository.findByUsername(userName)

      // 本人が所属するチーム一覧(org 情報込み)を1リクエストで取得する。
      // GET /user/teams を使うことで org -> teams -> members の N+1 を排除する。
      teams <- TeamRepository.findByAuthenticatedUser

      // 本人が所属するチームがアクセスできるリポジトリ
      // org 情報を持つチームだけを (org, slug) に平坦化してから順に取得し、
      // 失敗時は以降を呼ばず短絡する
      teamReposNested <- traverse(
        teams.flatMap(team => team.organization.toList.map(org => (org, team.slug)))
      ) { case (org, slug) => RepoRepository.findByTeam(org.login, slug) }
    } yield {
      val teamRepos = teamReposNested.flatten
      // 本人が所属するチームの slug 一覧。後段でチーム宛レビュー依頼の判定に使う
      val teamSlugs = teams.map(_.slug)
      // userRepos と teamRepos、または複数チーム間で同一リポジトリが重複しうるため
      // full_name をキーに一意化する。これにより PullRepository.findByFullName の
      // 無駄な呼び出しと Slack 通知での PR 重複表示を防ぐ
      val repos = (userRepos ++ teamRepos).distinctBy(_.full_name)
      (repos, teamSlugs)
    }
  }

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
