package errors

// アプリ内で発生したエラーを表す共通型。
// Repository 層で握りつぶさず Either の Left として Usecase 層まで伝播させ、
// 境界(main / Lambda ランタイム)でまとめて処理する。
case class AppError(message: String, cause: Option[Throwable] = None) {
  def toException: RuntimeException =
    cause.fold(new RuntimeException(message))(c =>
      new RuntimeException(message, c)
    )
}

// List[A] の各要素に Either を返す f を順に適用し Either[E, List[B]] にまとめる。
// - foldLeft で実装しておりスタックセーフ(大きなリストでもオーバーフローしない)。
// - acc が Left になると flatMap が f を評価しなくなるため、最初のエラー以降は
//   f(API 呼び出し等)が実行されず短絡する(無駄なリクエストを発生させない)。
def traverse[E, A, B](xs: List[A])(f: A => Either[E, B]): Either[E, List[B]] =
  xs
    .foldLeft[Either[E, List[B]]](Right(Nil)) { (acc, x) =>
      acc.flatMap(bs => f(x).map(_ :: bs))
    }
    .map(_.reverse)
