package integration.ergodicity.cgate

import akka.actor.{Cancellable, ActorLogging, Actor}
import scala.concurrent.duration._
import collection.mutable
import com.ergodicity.cgate.StreamEvent.StreamData
import scala.concurrent.ExecutionContext.Implicits.global
//import akka.util.Duration

class StreamDataThrottler(size: Int, duration: Duration = 1.second) extends Actor with ActorLogging {

  private val counter = mutable.Map[Int, Int]()
  private val cancellable = mutable.Map[Int, Cancellable]()

  def receive = {
    case data@StreamData(idx, _) if (incCounter(idx) < size) => handleData(data)

    case data@StreamData(idx, _) if (incCounter(idx) >= size) => // ignore

    case msg => log.info("Received msg = " + msg)
  }

  def incCounter(id: Int) = {
    val current = counter.getOrElseUpdate(id, 0)
    val next = current + 1
    counter(id) = next
    cancellable.getOrElseUpdate(id, context.system.scheduler.schedule(FiniteDuration(0, MILLISECONDS), FiniteDuration(duration._1, duration._2)) {
      counter(id) = 0
    })
    next
  }

  def handleData(data: StreamData) {
    log.info("Data = " + data)
  }
}