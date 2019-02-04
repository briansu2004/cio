package cio

import cats.effect.IO

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait Interpreter {
  def toIO[A](cioa: CIO[A]): IO[A]
  def toFuture[A](cioa: CIO[A]): Future[A] = toIO(cioa).unsafeToFuture()
}

object Interpreter {
  object LocalInterpreter extends Interpreter with NoopGC {
    @inline def toIO[A](cioa: CIO[A]): IO[A] = cioa match {
      case x: cio.CIOPure[A]                 => IO.pure(x.value)
      case x: cio.CIODelay[A]                => IO.delay(x.thunk())
      case x: cio.CIOMap[Any, A] @unchecked  => toIO[Any](x.base).map(a => x.f0(a))
      case x: cio.CIOBind[Any, A] @unchecked => toIO[Any](x.base).flatMap(a => toIO(x.f0(a)))
      case x: cio.CIOHandleError[A]          => toIO(x.base).handleErrorWith(e => IO.pure(x.f0(e)))
      case x: cio.CIOHandleErrorWith[A]      => toIO(x.base).handleErrorWith(e => toIO(x.f0(e)))
      case x: cio.CIOFromIO[A]               => x.ioa
      case x: cio.CIOError[A]                => IO.raiseError(x.e)
      case x: cio.CIOMemoized[A] =>
        IO.fromEither(x.value match {
          case Failure(e) => Left(e)
          case Success(v) => Right(v)
        })
    }
  }

  object Implicits {
    implicit val local: Interpreter = LocalInterpreter
  }
}
