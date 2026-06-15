package holiday

import sttp.client4.quick._
import sttp.model.Method
import sttp.model.Uri
import errors.AppError

// https://s-proj.com/utils/holiday.html
object CheckHolidayRepository {
  def get: Either[AppError, Boolean] =
    basicRequest
      .method(Method.GET, uri"https://s-proj.com/utils/checkHoliday.php")
      .send()
      .body match {
      case Right("holiday") => Right(true)
      case Right("else")    => Right(false)
      case Right("error") =>
        Left(AppError("checkHoliday api returned error"))
      case Right(other) =>
        Left(AppError(s"unexpected checkHoliday response: ${other}"))
      case Left(e) =>
        Left(AppError(s"failed to get checkHoliday api: ${e}"))
    }
}
