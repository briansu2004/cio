package cio.cluster_atomix

import java.util.UUID
import cats.effect.IO
import io.atomix.cluster.{Node => _}
import io.atomix.core.Atomix
import io.atomix.core.map.AsyncDistributedMap
import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import cio._

case class ComputationId(uuid: UUID) extends AnyVal

class AtomixInterpreter private (protected val atomix: Atomix)(implicit protected val ec: ExecutionContext) extends Interpreter with AtomixGC {
  atomix.start().join()
  startGC()

  protected val values: AsyncDistributedMap[ComputationId, Try[_]] =
    atomix
      .mapBuilder(s"cio-values")
      .withNullValues(false)
      .withCacheEnabled()
      .build()
      .async()

  protected def fetch[A](computationId: ComputationId): Future[A] =
    values.get(computationId).toScala flatMap { v =>
      Future.fromTry(v).asInstanceOf[Future[A]]
    }

  protected def fetchIO[A](computationId: ComputationId): IO[A] = IO.fromFuture(IO(fetch(computationId)))

  protected def addValue[A](v: () => Try[A]): ComputationId = {
    val computationId = ComputationId(UUID.randomUUID())
    values.computeIfAbsent(computationId, ((_: ComputationId) => v()).asJava)
    computationId
  }
  protected def addValueAsync[A](fut: () => Future[A]): ComputationId = {
    val computationId = ComputationId(UUID.randomUUID())
    values.computeIfAbsent(
      computationId,
      ((_: ComputationId) => Await.result(fut(), 10.minutes)).asJava.asInstanceOf[java.util.function.Function[
        _ >: ComputationId,
        _ <: Try[_]
      ]]
    )
    computationId
  }

  @inline def toIO[A](cioa: CIO[A]): IO[A] = cioa match {
    case x: cio.CIOPure[A] =>
      fetchIO[A](addValue(() => Success(x.value)))

    case x: cio.CIODelay[A] =>
      fetchIO[A](addValue(() => Try(x.thunk())))

    case x: cio.CIOMap[_, A] =>
      fetchIO[A](addValueAsync(() => toIO(x.base).map(x.f0(_)).unsafeToFuture()))

    case x: cio.CIOBind[_, A] =>
      fetchIO[A](addValueAsync(() => toIO(x.base).flatMap(a => toIO[A](x.f0(a))).unsafeToFuture()))

    case x: cio.CIOHandleError[A] =>
      fetchIO[A](addValueAsync(() => toIO(x.base).handleErrorWith(e => IO.pure(x.f0(e))).unsafeToFuture()))

    case x: cio.CIOHandleErrorWith[A] =>
      fetchIO[A](addValueAsync(() => toIO(x.base).handleErrorWith(e => toIO(x.f0(e))).unsafeToFuture()))

    case x: cio.CIOFromIO[A] =>
      fetchIO[A](addValueAsync(() => x.ioa.unsafeToFuture()))

    case x: cio.CIOError[A] =>
      fetchIO[A](addValue(() => Failure(x.e)))

    case x: cio.CIOMemoized[A] =>
      IO.fromEither(x.value match {
        case Failure(e) => Left(e)
        case Success(v) => Right(v)
      })
  }
}

object AtomixInterpreter {
  @inline def apply(atomix: Atomix)(implicit ec: ExecutionContext): Interpreter = new AtomixInterpreter(atomix)
}
