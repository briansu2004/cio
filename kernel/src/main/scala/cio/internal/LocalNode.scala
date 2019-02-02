package cio.internal

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import cio._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class LocalNode(implicit ec: ExecutionContext) extends Node {
  val id: NodeId                                                        = NodeId(UUID.randomUUID())
  private val futures: TrieMap[ComputationId, (ClassTag[_], Future[_])] = TrieMap.empty

  def pure[A: ClassTag](a: A): ComputationId = withFreshId { compId =>
    futures += (compId -> (implicitly[ClassTag[A]] -> Future.successful(a)))
    compId
  }

  def delay[A: ClassTag](thunk: () => A): ComputationId = withFreshId { compId =>
    futures += (compId -> (implicitly[ClassTag[A]] -> Future(thunk())))
    compId
  }

  def raiseError[A: ClassTag](e: Throwable): ComputationId = withFreshId { compId =>
    futures += (compId -> (implicitly[ClassTag[A]] -> Future.failed(e)))
    compId
  }

  def mapComputation[A: ClassTag, B: ClassTag](computationId: ComputationId)(
      f: A => B
  ): ComputationId =
    futures.get(computationId).fold[ComputationId](ifEmpty = throw InternalCIOException(s"Not found computation with id $computationId")) {
      case (_, fut) =>
        val compId = ComputationId(UUID.randomUUID())
        futures += (compId -> (implicitly[ClassTag[B]] -> fut.map(x => f(x.asInstanceOf[A]))))
        compId
    }

  def flatMapComputation[A: ClassTag, B: ClassTag](computationId: ComputationId)(
      f: A => CIO[B]
  ): ComputationId = withFreshId { compId =>
    val future = futures
      .get(computationId)
      .fold[Future[B]](ifEmpty = Future.failed[B](InternalCIOException(s"Not found computation with id $computationId"))) {
        case (_, fut) =>
          fut.flatMap { x =>
            val cio = f(x.asInstanceOf[A])
            fetch[B](cio.id)
          }
      }
    futures += (compId -> (implicitly[ClassTag[B]] -> future))
    compId
  }

  def handleError[A: ClassTag](computationId: ComputationId)(
      f: Throwable => A
  ): ComputationId = withFreshId { compId =>
    futures.get(computationId).fold[ComputationId](ifEmpty = throw InternalCIOException(s"Not found computation with id $computationId")) {
      case (_, fut) =>
        futures += (compId -> (implicitly[ClassTag[A]] -> fut.recover { case e => f(e) }))
        compId
    }
  }

  def handleErrorWith[A: ClassTag](computationId: ComputationId)(
      f: Throwable => CIO[A]
  ): ComputationId =
    withFreshId { compId =>
      val future = futures
        .get(computationId)
        .fold[Future[A]](ifEmpty = Future.failed[A](InternalCIOException(s"Not found computation with id $computationId"))) {
          case (_, fut) =>
            fut.mapTo[A].recoverWith {
              case e =>
                val cio = f(e)
                fetch[A](cio.id)
            }
        }
      futures += (compId -> (implicitly[ClassTag[A]] -> future))
      compId
    }

  def fetch[A: ClassTag](computationId: ComputationId): Future[A] = {
    val (ctg, future) = futures(computationId)
    if (ctg.runtimeClass != implicitly[ClassTag[A]].runtimeClass)
      Future.failed(InternalCIOException(s"Not found computation with id $computationId"))
    else future.mapTo[A]
  }

  def liftFuture[A: ClassTag](fut: () => Future[A]): ComputationId = withFreshId { compId =>
    futures += (compId -> (implicitly[ClassTag[A]] -> fut()))
    compId
  }

  @inline private def withFreshId[U](f: ComputationId => U): U = f(ComputationId(UUID.randomUUID()))
}
