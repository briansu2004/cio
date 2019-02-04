package cio
import cats.{MonadError, StackSafeMonad}

trait LowPriorityCatsInstances {
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