import cats.StackSafeMonad
import cats.effect.{Effect, ExitCase, IO, SyncIO}

package object cio {
  implicit class CIOOps[A](private val self: CIO[A]) extends AnyVal {
    // todo: implement
    def map[B](f: A => B)(implicit context: Context): CIO[B] = ???
    // todo: implement
    def flatMap[B](f: A => CIO[B])(implicit context: Context): CIO[B] = ???
    // todo: implement
    def handleError(f: Throwable => A)(implicit context: Context): CIO[A] = ???
    // todo: implement
    def handleErrorWith(f: Throwable => CIO[A])(implicit context: Context): CIO[A] = ???
    // todo: implement
    def collect[B](pf: PartialFunction[A, B]): CIO[Option[B]] = ???

    def filter(f: A => Boolean)(implicit context: Context): CIO[Option[A]] = collect {
      case a if f(a) => a
    }
  }

  implicit def cioCatsInstances(implicit context: Context): Effect[CIO] = new Effect[CIO] with StackSafeMonad[CIO] {
    // todo: implement
    def runAsync[A](fa: CIO[A])(
        cb: Either[Throwable, A] => IO[Unit]
    ): SyncIO[Unit]                                                 = ???

    // todo: implement
    def async[A](k: (Either[Throwable, A] => Unit) => Unit): CIO[A] = ???

    // todo: implement
    def asyncF[A](k: (Either[Throwable, A] => Unit) => CIO[Unit]): CIO[A] = ???

    // todo: implement
    def suspend[A](thunk: => CIO[A]): CIO[A] = ???

    // todo: implement
    def bracketCase[A, B](acquire: CIO[A])(use: A => CIO[B])(release: (A, ExitCase[Throwable]) => CIO[Unit]): CIO[B] = ???

    def flatMap[A, B](fa: CIO[A])(f: A => CIO[B]): CIO[B] = fa.flatMap(f)

    override def map[A, B](fa: CIO[A])(f: A => B): CIO[B] = fa.map(f)

    def raiseError[A](e: Throwable): CIO[A] = CIO.raiseError(e)

    override def handleError[A](fa: CIO[A])(f: Throwable => A): CIO[A] = fa.handleError(f)

    def handleErrorWith[A](fa: CIO[A])(f: Throwable => CIO[A]): CIO[A] = fa.handleErrorWith(f)

    def pure[A](x: A): CIO[A] = CIO.pure(x)
  }
}
