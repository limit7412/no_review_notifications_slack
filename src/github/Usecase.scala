package github

object Usecase {
  def getRepos = {
    UserRepository.getAllRepos(sys.env("GITHUB_USERNAME")) match {
      case Right(res) => res
      case Left(e) => {
        println(e)
        "failed"
      }
    }
  }
}
