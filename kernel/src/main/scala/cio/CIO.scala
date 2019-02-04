package cio

import cats.effect.IO

import scala.concurrent.Future
import scala.util.Try

class InternalCIOException private (msg: String, cause: Throwable) extends Exception(msg, cause)
object InternalCIOException {
  @inline def apply(msg: String): InternalCIOException                   = new InternalCIOException(msg, null)
  @inline def apply(msg: String, cause: Throwable): InternalCIOException = new InternalCIOException(msg, cause)
}

sealed abstract class CIO[@specialized(Specializable.BestOfBreed) +A] extends Serializable {
  def map[B](f: A => B): CIO[B]
  def flatMap[B](f: A => CIO[B]): CIO[B]
  def handleError[B >: A](f: Throwable => B): CIO[B]
  def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B]
}

class CIOPure[A](protected[cio] val value: A) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]                               = new CIODelay[B](() => f(value))
  @inline def flatMap[B](f: A => CIO[B]): CIO[B]                      = new CIOBind[A, B](this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B]          = this
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = this
}

class CIODelay[A](protected[cio] val thunk: () => A) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]          = new CIODelay[B](() => f(thunk()))
  @inline def flatMap[B](f: A => CIO[B]): CIO[B] = new CIOBind(this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B] =
    new CIODelay[B](
      () =>
        try thunk()
        catch { case e: Throwable => f(e) }
    )

  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = new CIOHandleErrorWith[B](this, f)
}

class CIOMap[A, B](protected[cio] val base: CIO[A], protected[cio] val f0: A => B) extends CIO[B] {
  @inline def map[C](f: B => C): CIO[C]          = new CIOMap[A, C](base, f compose f0)
  @inline def flatMap[C](f: B => CIO[C]): CIO[C] = new CIOBind[A, C](base, f compose f0)
  @inline def handleError[C >: B](f: Throwable => C): CIO[C] =
    new CIOMap[A, C](
      base,
      a =>
        try f0(a)
        catch { case e: Throwable => f(e) }
    )

  @inline def handleErrorWith[C >: B](f: Throwable => CIO[C]): CIO[C] = new CIOBind[A, C](
    base,
    a =>
      try new CIOPure[C](f0(a))
      catch {
        case e: Throwable => f(e)
    }
  )
}

class CIOBind[A, B](protected[cio] val base: CIO[A], protected[cio] val f0: A => CIO[B]) extends CIO[B] {
  @inline def map[C](f: B => C): CIO[C]                               = new CIOBind[A, C](base, f0(_).map(f))
  @inline def flatMap[C](f: B => CIO[C]): CIO[C]                      = new CIOBind[A, C](base, f0(_).flatMap(f))
  @inline def handleError[C >: B](f: Throwable => C): CIO[C]          = new CIOBind[A, C](base, f0(_).handleError(f))
  @inline def handleErrorWith[C >: B](f: Throwable => CIO[C]): CIO[C] = new CIOBind[A, C](base, f0(_).handleErrorWith(f))
}

class CIOHandleError[A](protected[cio] val base: CIO[A], protected[cio] val f0: Throwable => A) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]          = new CIOMap[A, B](this, f)
  @inline def flatMap[B](f: A => CIO[B]): CIO[B] = new CIOBind(this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B] =
    new CIOHandleError[B](
      base,
      e =>
        try f0(e)
        catch {
          case e: Throwable => f(e)
      }
    )
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] =
    new CIOHandleErrorWith[B](
      base,
      e =>
        try new CIOPure[B](f0(e))
        catch {
          case e: Throwable => f(e)
      }
    )
}

class CIOHandleErrorWith[A](protected[cio] val base: CIO[A], protected[cio] val f0: Throwable => CIO[A]) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]                               = new CIOMap[A, B](this, f)
  @inline def flatMap[B](f: A => CIO[B]): CIO[B]                      = new CIOBind(this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B]          = new CIOHandleErrorWith[B](base, f0(_).handleError(f))
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = new CIOHandleErrorWith[B](base, f0(_).handleErrorWith(f))
}

class CIOFromIO[A](protected[cio] val ioa: IO[A]) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]                               = new CIOFromIO[B](ioa.map(f))
  @inline def flatMap[B](f: A => CIO[B]): CIO[B]                      = new CIOBind(this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B]          = new CIOFromIO[B](ioa.handleErrorWith(e => IO.delay(f(e))))
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = new CIOHandleErrorWith[B](this, f)
}

class CIOError[A](protected[cio] val e: Throwable) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]                               = this.asInstanceOf[CIOError[B]]
  @inline def flatMap[B](f: A => CIO[B]): CIO[B]                      = this.asInstanceOf[CIOError[B]]
  @inline def handleError[B >: A](f: Throwable => B): CIO[B]          = new CIODelay[B](() => f(e))
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = f(e)
}

class CIOMemoized[A](protected[cio] val value: Try[A]) extends CIO[A] {
  @inline def map[B](f: A => B): CIO[B]                               = new CIODelay[B](() => value.map(f).get)
  @inline def flatMap[B](f: A => CIO[B]): CIO[B]                      = new CIOBind[A, B](this, f)
  @inline def handleError[B >: A](f: Throwable => B): CIO[B]          = new CIODelay[B](() => value.recover[B] { case e: Throwable => f(e) }.get)
  @inline def handleErrorWith[B >: A](f: Throwable => CIO[B]): CIO[B] = new CIOHandleErrorWith[B](this, f)
}

object CIO {
  val unit: CIO[Unit]  = pure({})
  val never: CIO[Unit] = liftIO(IO.never)

  @inline def pure[A](a: A): CIO[A] = new CIOPure[A](a)

  @inline def raiseError[A](e: Throwable): CIO[A] = new CIOError[A](e)

  @inline def delay[A](thunk: => A): CIO[A] = new CIODelay[A](() => thunk)

  @inline def liftFuture[A](fut: => Future[A]): CIO[A] = liftIO(IO.fromFuture(IO(fut)))

  @inline def liftIO[A](ioa: IO[A]): CIO[A] = new CIOFromIO[A](ioa)

  @inline def async[A](f: (Either[Throwable, A] => Unit) => Unit): CIO[A] = liftIO(IO.async(f))
}
