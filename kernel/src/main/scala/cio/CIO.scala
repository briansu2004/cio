package cio

import cats.effect.IO
import scala.concurrent.Future
import scala.reflect.ClassTag

class CIO[+A: ClassTag] protected[cio] (protected[cio] val nodeId: NodeId, protected[cio] val id: ComputationId) {
  @inline protected[cio] def mapImpl[B: ClassTag](f: A => B)(implicit nodeContext: NodeContext): CIO[B] =
    new CIO[B](nodeId, nodeContext.onNode(nodeId)(_.mapComputation(id)(f)))

  @inline protected[cio] def flatMapImpl[B: ClassTag](f: A => CIO[B])(implicit nodeContext: NodeContext): CIO[B] =
    new CIO[B](nodeId, nodeContext.onNode(nodeId)(_.flatMapComputation(id)(f)))

  @inline protected[cio] def handleErrorImpl[B >: A](f: Throwable => B)(implicit nodeContext: NodeContext): CIO[B] =
    withCtg { implicit ctg: ClassTag[B] =>
      new CIO[B](nodeId, nodeContext.onNode(nodeId)(_.handleError(id)(f)))
    }

  @inline protected[cio] def handleErrorWithImpl[B >: A](f: Throwable => CIO[B])(implicit nodeContext: NodeContext): CIO[B] =
    withCtg { implicit ctg: ClassTag[B] =>
      new CIO[B](nodeId, nodeContext.onNode(nodeId)(_.handleErrorWith(id)(f)))
    }

  @inline protected[cio] def unsafeToFutureImpl(implicit nodeContext: NodeContext): Future[A] =
    nodeContext.onNode(nodeId)(_.fetch(id))

  private def withCtg[B >: A, U](f: ClassTag[B] => U): U =
    f(implicitly[ClassTag[A]].asInstanceOf[ClassTag[B]])
}

object CIO {
  def pure[A: ClassTag](a: A)(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode { node =>
    new CIO[A](node.id, node pure a)
  }

  def raiseError[A: ClassTag](e: Throwable)(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode { node =>
    new CIO[A](node.id, node raiseError e)
  }

  def delay[A: ClassTag](thunk: => A)(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode { node =>
    new CIO[A](node.id, node.delay(() => thunk))
  }

  def liftFuture[A: ClassTag](fut: => Future[A])(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode { node =>
    new CIO[A](node.id, node.liftFuture(() => fut))
  }

  def liftIO[A: ClassTag](ioa: IO[A])(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode { node =>
    new CIO[A](node.id, node.liftIO(ioa))
  }

  def async[A: ClassTag](f: (Either[Throwable, A] => Unit) => Unit)(implicit nodeContext: NodeContext): CIO[A] = nodeContext.onRandomNode {
    node =>
      new CIO[A](node.id, node async f)
  }
}
