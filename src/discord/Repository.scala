package discord

import scala.annotation.tailrec
import scala.util.Try
import scala.util.control.NonFatal
import sttp.client4.quick._
import upickle.default._
import errors.AppError

object PostRepository {
  // 429(レート制限)時に再送する回数の上限。Lambda のタイムアウト(300秒)を圧迫しない範囲に抑える。
  private val MaxRetries = 3
  // Retry-After ヘッダが取得できない場合の既定待機時間(ミリ秒)。
  private val DefaultRetryMillis = 1000L

  // Discord webhook へ POST する。
  // - Content-Type: application/json を明示する(Slack と違い Discord では必須)。
  // - send() はネットワーク断や DNS 解決失敗、タイムアウトなどで例外を送出しうるため、
  //   NonFatal を捕捉して Left に変換し、境界(main)で一貫して扱う。
  // - 複数メッセージ送信の途中で 429 が返ると後続チャンクが送られず通知が欠落するため、
  //   429 のときは Retry-After に従って待機してから再送する(回数上限あり)。
  def sendPost(post: Models.Post): Either[AppError, Unit] =
    sendWithRetry(write(post), MaxRetries)

  @tailrec
  private def sendWithRetry(
      body: String,
      retriesLeft: Int
  ): Either[AppError, Unit] = {
    val sent =
      try
        Right(
          basicRequest
            .post(uri"${config.Config.instance.webhookUrl}")
            .header("Content-Type", "application/json")
            .body(body)
            .send()
        )
      catch {
        case NonFatal(e) =>
          Left(
            AppError(s"failed to send discord message: ${e.getMessage}", Some(e))
          )
      }

    sent match {
      case Left(err) => Left(err)
      case Right(res) =>
        if (res.code.code == 429 && retriesLeft > 0) {
          Thread.sleep(retryAfterMillis(res.header("Retry-After")))
          sendWithRetry(body, retriesLeft - 1)
        } else
          res.body match {
            case Right(_) => Right(())
            case Left(e) =>
              Left(AppError(s"failed to send discord message: ${res.code} ${e}"))
          }
    }
  }

  // Retry-After ヘッダ(秒。小数もありうる)をミリ秒へ変換する。取得または解釈できなければ既定値を返す。
  private def retryAfterMillis(header: Option[String]): Long =
    header
      .flatMap(h => Try(h.trim.toDouble).toOption)
      .map(sec => math.max(0L, (sec * 1000).toLong))
      .getOrElse(DefaultRetryMillis)
}
