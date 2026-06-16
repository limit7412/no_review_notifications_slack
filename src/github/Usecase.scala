package github

import errors.{AppError, traverse, parTraverse}

object Usecase {
  def getRepos: Either[AppError, (List[Models.Repo], List[String])] = {
    val userName = config.Config.instance.githubUsername

    for {
      // /user/teams はトークン所有者(認証ユーザー)基準で返るため、通知対象である
      // GITHUB_USERNAME と認証ユーザーが一致していることを先に検証する。
      // 不一致のまま進むと別ユーザー基準のチーム/リポジトリで通知してしまうため、
      // 黙って誤通知せず AppError で明示的に失敗させる。
      authUser <- AuthenticatedUserRepository.find
      _ <- Either.cond(
        authUser.login == userName,
        (),
        AppError(
          s"GITHUB_USERNAME(${userName}) does not match the authenticated user(${authUser.login}); " +
            "/user/teams is scoped to the token owner"
        )
      )

      // 本人が所有するリポジトリ
      userRepos <- RepoRepository.findByUsername(userName)

      // 本人が直接所属するチーム一覧(org 情報込み)を1リクエストで取得する。
      // GET /user/teams を使うことで org -> teams -> members の N+1 を排除する。
      directTeams <- TeamRepository.findByAuthenticatedUser

      // 直接所属チームに加えて親チームも判定対象に含める。
      // GitHub では親チームの member 一覧に子チームのメンバーも含まれ、
      // 親チーム宛のレビュー依頼/メンションは子チームのメンバーにも届く。
      // /user/teams は直接所属チームのみ返すため、親を辿って補完する。
      // (org 情報を持つチームのみ対象。失敗時は以降を呼ばず短絡する)
      expandedNested <- traverse(
        directTeams.flatMap(team =>
          team.organization.toList.map(org => (org, team))
        )
      ) { case (org, team) =>
        teamWithAncestors(org, team).map(_.map(t => (org, t)))
      }
      // org/slug をキーに一意化する(複数チームが同じ親を持つ場合など)
      expanded = expandedNested.flatten
        .distinctBy { case (org, team) => (org.login, team.slug) }

      // 本人が所属するチームがアクセスできるリポジトリ
      // チーム数に比例して遅くなるため並列取得する(失敗時は元の順序で最初の
      // エラーを返す)
      teamReposNested <- parTraverse(expanded) { case (org, team) =>
        RepoRepository.findByTeam(org.login, team.slug)
      }
    } yield {
      val teamRepos = teamReposNested.flatten
      // 本人が所属する(親も含む)チームの slug 一覧。チーム宛レビュー依頼の判定に使う
      val teamSlugs = expanded.map { case (_, team) => team.slug }.distinct
      // userRepos と teamRepos、または複数チーム間で同一リポジトリが重複しうるため
      // full_name をキーに一意化する。これにより PullRepository.findByFullName の
      // 無駄な呼び出しと Slack 通知での PR 重複表示を防ぐ
      val repos = (userRepos ++ teamRepos).distinctBy(_.full_name)
      (repos, teamSlugs)
    }
  }

  // 指定チームから親チームを根まで辿り、自身と全祖先チームを返す。
  // /user/teams が返す parent は1階層分しか展開されないため、さらに上の親は
  // TeamRepository.findBySlug で取得する。辿る回数はネストの深さに比例するだけで、
  // チーム数 × メンバー数の N+1 には戻らない。
  private def teamWithAncestors(
      org: Models.Organization,
      team: Models.Team
  ): Either[AppError, List[Models.Team]] = {
    def loop(
        current: Models.Team,
        acc: List[Models.Team]
    ): Either[AppError, List[Models.Team]] =
      current.parent match {
        case None => Right(current :: acc)
        case Some(parent) =>
          TeamRepository
            .findBySlug(org.login, parent.slug)
            .flatMap(full => loop(full, current :: acc))
      }
    loop(team, Nil)
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
      // repo.owner は Option のため owner 不明のリポジトリは対象から除外する。
      // リポジトリ数に比例して遅くなるため並列取得する(失敗時は元の順序で
      // 最初のエラーを返す。逐次の短絡とはエラー集約方針を揃えてある)
      pullsNested <- parTraverse(
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
