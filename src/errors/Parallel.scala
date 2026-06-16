package errors

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import java.util.concurrent.Executors

// traverse の並列版。List[A] の各要素に f を並列適用し Either[E, List[B]] にまとめる。
//
// 逐次の traverse と異なり「最初の失敗で以降を呼ばない」短絡はできない(全要素を
// 同時に走らせるため)。代わりに全要素を実行したうえで、Left があれば元の順序で
// 最初の Left を返し、無ければ Right(順序保持) を返す。エラー集約の観点では
// 逐次版と同じ「最初のエラーを返す」挙動を保つ。
//
// GitHub API のレートリミットに配慮し、同時実行数は maxConcurrency に制限する
// (固定スレッドプールで上限を設け、超過分はキューで待たせる)。
//
// 実行環境は Scala Native 0.5 のマルチスレッド(project.scala で有効化)。
// HTTP backend(sttp curl)は送信ごとに curl easy handle を生成・破棄するため、
// スレッド間で可変ハンドルを共有しない。
val DEFAULT_MAX_CONCURRENCY = 8

def parTraverse[E, A, B](
    xs: List[A],
    maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY
)(f: A => Either[E, B]): Either[E, List[B]] =
  if (xs.isEmpty) Right(Nil)
  else {
    val pool = Executors.newFixedThreadPool(math.min(maxConcurrency, xs.size))
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
    try {
      val futures = xs.map(x => Future(f(x)))
      val results = Await.result(Future.sequence(futures), Duration.Inf)
      results.collectFirst { case Left(e) => e } match {
        case Some(e) => Left(e)
        case None    => Right(results.collect { case Right(b) => b })
      }
    } finally {
      pool.shutdown()
    }
  }
