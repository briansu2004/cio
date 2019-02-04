package cio.cluster_atomix

import cio._

trait AtomixGC extends GC { self: AtomixInterpreter =>
  def startGC(): Unit = ec.execute(() => gcImpl())

  protected def gcImpl(): Unit = {} // todo: implement
}
