package it.alian.gun.mesmerize.scalaapi

import org.bukkit.scheduler.BukkitRunnable

private[scalaapi] class ScalaTaskExecutor(task: => Any) extends BukkitRunnable {

  override def run(): Unit = {
    try task catch {
      case _: CancelException => cancel()
      case e: Throwable => throw e
    }
  }

}

object ScalaTaskExecutor {
  def apply(task: => Any): ScalaTaskExecutor = new ScalaTaskExecutor(task)
}

class CancelException extends RuntimeException {
  override def getMessage: String = "Uncaught cancel task signal! Any Task.cancel() should only be used in a Task."
}
