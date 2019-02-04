import cats.{MonadError, StackSafeMonad}
import cats.effect.{ExitCode, IO}

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
    @inline def attempt: CIO[Either[Throwable, A]]                             = self.map[Either[Throwable, A]](Right(_)).handleError(Left(_))
    @inline def redeem[B](recover: Throwable => B, map: A => B): CIO[B]        = self.map(map).handleError(recover)
    @inline def toExitCode: CIO[ExitCode]                                      = redeem(recover = _ => ExitCode.Error, map = _ => ExitCode.Success)
  }

  def putStrLn(msg: Any): CIO[Unit] = CIO.liftIO(IO(println(msg)).flatMap(_ => IO.unit))

  implicit val catsInstancesForCIO: MonadError[CIO, Throwable] = new MonadError[CIO, Throwable] with StackSafeMonad[CIO] {
    def pure[A](x: A): CIO[A]                                          = CIO.pure(x)
    def flatMap[A, B](fa: CIO[A])(f: A => CIO[B]): CIO[B]              = fa flatMap f
    override def map[A, B](fa: CIO[A])(f: A => B): CIO[B]              = fa map f
    def raiseError[A](e: Throwable): CIO[A]                            = CIO.raiseError(e)
    def handleErrorWith[A](fa: CIO[A])(f: Throwable => CIO[A]): CIO[A] = fa handleErrorWith f
    override def handleError[A](fa: CIO[A])(f: Throwable => A): CIO[A] = fa handleError f
    override def attempt[A](fa: CIO[A]): CIO[Either[Throwable, A]]     = fa.attempt
  }
}
