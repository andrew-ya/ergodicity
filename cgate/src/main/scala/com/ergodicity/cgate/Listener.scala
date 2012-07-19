package com.ergodicity.cgate

import akka.util.duration._
import config.Replication.ReplicationMode
import ru.micexrts.cgate.{Listener => CGListener}
import akka.actor.FSM.Failure
import akka.actor.{Cancellable, Actor, FSM}
import com.ergodicity.cgate.Listener.OpenSettings

object Listener {
  sealed trait OpenSettings {
    def config: String
  }

  case class ReplicationSettings(mode: ReplicationMode, state: Option[String] = None) {
    private val modeParam = "mode=" + mode.name
    val config = state.map(modeParam + ";replstate=" + _).getOrElse(modeParam)
  }

  case class Open(params: OpenSettings)

  case object Close

}

protected[cgate] case class ListenerState(state: State)

class Listener(underlying: CGListener) extends Actor with FSM[State, Option[OpenSettings]] {

  import Listener._

  private var statusTracker: Option[Cancellable] = None

  startWith(Closed, None)

  when(Closed) {
    case Event(Open(params), None) =>
      log.info("Open listener with params = " + params)
      underlying.open(params.config)
      stay() using Some(params)
  }

  when(Opening, stateTimeout = 3.second) {
    case Event(FSM.StateTimeout, _) => stop(Failure("Connecting timeout"))
  }

  when(Active) {
    case Event(Close, _) =>
      log.info("Close Listener")
      underlying.close()
      goto(Closed) using None
  }

  onTransition {
    case Closed -> Opening => log.info("Opening listener")
    case _ -> Active => log.info("Listener opened")
    case _ -> Closed => log.info("Listener closed")
  }

  whenUnhandled {
    case Event(ListenerState(Error), _) => stop(Failure("Listener in Error state"))

    case Event(ListenerState(state), _) if (state != stateName) => goto(state)

    case Event(ListenerState(state), _) if (state == stateName) => stay()

    case Event(TrackUnderlyingStatus(duration), _) =>
      statusTracker.foreach(_.cancel())
      statusTracker = Some(context.system.scheduler.schedule(0 milliseconds, duration) {
        self ! ListenerState(State(underlying.getState))
      })
      stay()
  }

  onTermination {
    case StopEvent(reason, s, d) => log.error("Listener failed, reason = " + reason)
  }

  initialize
}