import scala.concurrent.Future
import scala.reflect.ClassTag

package object cio {
  implicit class CIOOps[A](private val self: CIO[A]) extends AnyVal {
    @inline def map[B: ClassTag](f: A => B)(implicit nodeContext: NodeContext): CIO[B] = self.mapImpl(f)

    @inline def flatMap[B: ClassTag](f: A => CIO[B])(implicit nodeContext: NodeContext): CIO[B] = self.flatMapImpl(f)

    @inline def handleError(f: Throwable => A)(implicit nodeContext: NodeContext): CIO[A] = self.handleErrorImpl[A](f)

    @inline def handleErrorWith(f: Throwable => CIO[A])(implicit nodeContext: NodeContext): CIO[A] = self.handleErrorWithImpl[A](f)

    @inline def collect[B](pf: PartialFunction[A, B])(implicit nodeContext: NodeContext): CIO[Option[B]] = map(pf.lift)

    @inline def filter(f: A => Boolean)(implicit nodeContext: NodeContext): CIO[Option[A]] = collect {
      case a if f(a) => a
    }

    @inline def unsafeToFuture(implicit nodeContext: NodeContext): Future[A] = self.unsafeToFutureImpl
  }
}
