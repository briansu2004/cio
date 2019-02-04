import cats.effect._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, TimeoutException}
import scala.util.Try

package object cio extends LowPriorityCatsInstances {
  implicit class CIOOps[A](private val self: CIO[A]) extends AnyVal {
    @inline def flatten[B](implicit ev: A <:< CIO[B]): CIO[B]         = self.flatMap(ev)
    @inline def collect[B](pf: PartialFunction[A, B]): CIO[Option[B]] = self.map(pf.lift)

    @inline def filter(f: A => Boolean): CIO[Option[A]] = collect {
      case a if f(a) => a
    }

    @inline def withTimeout(duration: FiniteDuration)(implicit interpreter: Interpreter): CIO[A] =
      new CIOFromIO[A](IO { toIO.unsafeRunTimed(duration).getOrElse(throw new TimeoutException(s"CIO timed out after $duration")) })

    @inline def memoize()(implicit interpreter: Interpreter): CIO[A]                                      = new CIOMemoized[A](Try { interpreter.toIO(self).unsafeRunSync() })
    @inline def toIO(implicit interpreter: Interpreter): IO[A]                                            = interpreter.toIO(self)
    @inline def unsafeToFuture()(implicit interpreter: Interpreter): Future[A]                            = interpreter.toFuture(self)
    @inline def unsafeRunSync()(implicit interpreter: Interpreter): A                                     = toIO.unsafeRunSync()
    @inline def unsafeRunAsync(cb: Either[Throwable, A] => Unit)(implicit interpreter: Interpreter): Unit = toIO.unsafeRunAsync(cb)
    @inline def attempt: CIO[Either[Throwable, A]]                                                        = self.map[Either[Throwable, A]](Right(_)).handleError(Left(_))
    @inline def redeem[B](recover: Throwable => B, map: A => B): CIO[B]                                   = self.map(map).handleError(recover)
    @inline def toExitCode: CIO[ExitCode]                                                                 = redeem(recover = _ => ExitCode.Error, map = _ => ExitCode.Success)
  }

  def putStrLn(msg: Any): CIO[Unit] = CIO.liftIO(IO(println(msg)).flatMap(_ => IO.unit))

  implicit def catsInstances2(implicit interpreter: Interpreter): Effect[CIO] = new Effect[CIO] {
    def runAsync[A](fa: CIO[A])(
        cb: Either[Throwable, A] => IO[Unit]
    ): SyncIO[Unit] = toIO(fa).runAsync(cb)

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): CIO[A] = CIO.async(k)

    def asyncF[A](k: (Either[Throwable, A] => Unit) => CIO[Unit]): CIO[A] =
      new CIOFromIO[A](
        IO.asyncF { cb =>
          k.andThen(toIO).apply(cb)
        }
      )

    def suspend[A](thunk: => CIO[A]): CIO[A] = new CIODelay[CIO[A]](() => thunk).flatten
    def bracketCase[A, B](acquire: CIO[A])(use: A => CIO[B])(release: (A, ExitCase[Throwable]) => CIO[Unit]): CIO[B] = new CIOFromIO[B](
      toIO(acquire).bracketCase(a => toIO(use(a)))((a, ec) => toIO(release(a, ec)))
    )

    def raiseError[A](e: Throwable): CIO[A]                            = catsInstancesForCIO.raiseError(e)
    override def handleError[A](fa: CIO[A])(f: Throwable => A): CIO[A] = catsInstancesForCIO.handleError(fa)(f)
    def handleErrorWith[A](fa: CIO[A])(f: Throwable => CIO[A]): CIO[A] = catsInstancesForCIO.handleErrorWith(fa)(f)
    override def map[A, B](fa: CIO[A])(f: A => B): CIO[B]              = catsInstancesForCIO.map(fa)(f)
    def flatMap[A, B](fa: CIO[A])(f: A => CIO[B]): CIO[B]              = catsInstancesForCIO.flatMap(fa)(f)
    def tailRecM[A, B](a: A)(f: A => CIO[Either[A, B]]): CIO[B]        = catsInstancesForCIO.tailRecM(a)(f)
    def pure[A](x: A): CIO[A]                                          = catsInstancesForCIO.pure(x)
    override def liftIO[A](ioa: IO[A]): CIO[A]                         = new CIOFromIO[A](ioa)
    override def toIO[A](fa: CIO[A]): IO[A]                            = fa.toIO
  }
}
