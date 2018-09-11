package com.ergodicity.cgate

import akka.actor.FSM.Failure
import akka.actor.{Actor, FSM}
import scala.concurrent.duration._
import com.ergodicity.cgate.config.{ListenerConfig, ListenerOpenParams}
import java.util.concurrent.atomic.AtomicReference
import ru.micexrts.cgate.messages.Message
import ru.micexrts.cgate.{Listener => CGListener, Connection => CGConnection, ISubscriber}
import scala.concurrent.ExecutionContext.Implicits.global

object ListenerBinding {
  def apply(f: ISubscriber => CGListener) = new ListenerBinding(f)

  def apply(connection: CGConnection, config: ListenerConfig) = new ListenerBinding(subscriber => new CGListener(connection, config(), subscriber))
}

class ListenerBinding(f: ISubscriber => CGListener) {
  private[this] val subscriber: AtomicReference[Subscriber] = new AtomicReference[Subscriber](null)

  def this(connection: CGConnection, config: ListenerConfig) = this(subscriber => new CGListener(connection, config(), subscriber))

  val listener = f(new ISubscriber {
    def onMessage(connection: CGConnection, listener: CGListener, msg: Message) = subscriber.get().handleMessage(msg)
  })

  def bind(s: Subscriber) {
    if (!subscriber.compareAndSet(null, s))
      throw new IllegalStateException("Can't bind to stream twice")
  }
}

object Listener {

  case class Open(config: ListenerOpenParams)

  case object Close

  case object Dispose

  case object UpdateState

  def apply(underlying: CGListener, updateStateDuration: Option[Duration] = Some(100.millis)) = new Listener(underlying, updateStateDuration)

  case class OpenTimedOut() extends RuntimeException

  case class ListenerError() extends RuntimeException
}

protected[cgate] case class ListenerState(state: State)

class Listener(underlying: CGListener, updateStateDuration: Option[Duration] = Some(1.second)) extends Actor with FSM[State, Option[ListenerOpenParams]] {

  import Listener._

  private val statusTracker = updateStateDuration.map {
    duration =>
      context.system.scheduler.schedule(FiniteDuration(duration._1, duration._2), FiniteDuration(duration._1, duration._2)) {
        self ! UpdateState
      }
  }

  startWith(Closed, None)

  when(Closed) {
    case Event(Open(config), None) =>
      log.info("Open listener with config = " + config())
      underlying.open(config())
      stay() using Some(config)
  }

  when(Opening, stateTimeout = 3.second) {
    case Event(FSM.StateTimeout, _) => throw new OpenTimedOut
  }

  when(Active) {
    case Event(Close, _) =>
      log.info("Close Listener")
      underlying.close()
      stay() using None
  }

  onTransition {
    case Closed -> Opening => log.info("Opening listener")
    case _ -> Active => log.info("Listener opened")
    case _ -> Closed => log.info("Listener closed")
  }

  whenUnhandled {
    case Event(ListenerState(Error), _) => throw new ListenerError

    case Event(ListenerState(state), _) if (state != stateName) => goto(state)

    case Event(ListenerState(state), _) if (state == stateName) => stay()

    case Event(UpdateState, _) =>
      self ! ListenerState(State(underlying.getState))
      stay()

    case Event(Dispose, _) =>
      log.info("Dispose listener")
      underlying.dispose()
      stop(Failure("Disposed"))
  }

  onTermination {
    case StopEvent(reason, s, d) => log.error("Listener failed, reason = " + reason)
  }

  initialize

  override def postStop() {
    statusTracker.foreach(_.cancel())
    super.postStop()
  }
}