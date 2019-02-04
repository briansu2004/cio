import cats.effect.IO

import scala.concurrent.Future

package object cio {
  implicit class CIOOps[A](private val self: CIO[A]) extends AnyVal {
    @inline def collect[B](pf: PartialFunction[A, B]): CIO[Option[B]] = self.map(pf.lift)

    @inline def filter(f: A => Boolean): CIO[Option[A]] = collect {
      case a if f(a) => a
    }

    @inline def unsafeToFuture()(implicit interpreter: Interpreter): Future[A] = interpreter.toFuture(self)
    @inline def toIO(implicit interpreter: Interpreter): IO[A]                 = interpreter.toIO(self)
    @inline def unsafeRunSync()(implicit interpreter: Interpreter): A          = toIO.unsafeRunSync()
  }
}
