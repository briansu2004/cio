package cio

import java.util.UUID
import cats.effect.IO
import cio.internal.LocalNode
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Random

case class NodeId(id: UUID)                      extends AnyVal
case class ComputationId(id: UUID)               extends AnyVal
class InternalCIOException private (msg: String) extends Exception(msg)
object InternalCIOException {
  def apply(msg: String): InternalCIOException = new InternalCIOException(msg)
}

trait Node {
  def id: NodeId
  def pure[A: ClassTag](a: A): ComputationId
  def delay[A: ClassTag](thunk: () => A): ComputationId
  def raiseError[A: ClassTag](e: Throwable): ComputationId
  def mapComputation[A: ClassTag, B: ClassTag](computationId: ComputationId)(f: A => B): ComputationId
  def flatMapComputation[A: ClassTag, B: ClassTag](computationId: ComputationId)(f: A => CIO[B]): ComputationId
  def handleError[A: ClassTag](computationId: ComputationId)(f: Throwable => A): ComputationId
  def handleErrorWith[A: ClassTag](computationId: ComputationId)(f: Throwable => CIO[A]): ComputationId
  def fetch[A: ClassTag](computationId: ComputationId): Future[A]
  def async[A: ClassTag](f: (Either[Throwable, A] => Unit) => Unit): ComputationId = liftIO(IO.async(f))
  def liftIO[A: ClassTag](ioa: IO[A]): ComputationId                               = liftFuture(() => ioa.unsafeToFuture())
  def liftFuture[A: ClassTag](fut: () => Future[A]): ComputationId
}

class NodeContext protected[cio] (nodes: Map[NodeId, Node]) {
  private val nodeIds    = nodes.keys.toArray
  private val nodesCount = nodes.size

  def onNode[U](id: NodeId)(f: Node => U): U = f(nodes(id))
  def onRandomNode[U](f: Node => U): U       = onNode(randomNodeId)(f)
  def randomNodeId: NodeId                   = nodeIds(Random.nextInt(nodesCount))
}

object NodeContext {
  object Implicits {
    implicit lazy val local: NodeContext = NodeContext.local(ExecutionContext.global)
  }

  def local(ec: ExecutionContext): NodeContext = {
    val localNode = new LocalNode()(ec)
    new NodeContext(Map(localNode.id -> localNode))
  }
}
