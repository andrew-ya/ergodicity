package com.ergodicity.cgate

import org.scalatest.{BeforeAndAfterAll, WordSpec, WordSpecLike}
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import akka.actor.ActorSystem
import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.pattern.ask

import scala.concurrent.duration._
import java.nio.ByteBuffer

import com.ergodicity.cgate.StreamEvent.{ClearDeleted, LifeNumChanged, ReplState, StreamData}
import akka.util.Timeout
import com.ergodicity.cgate.DataStream._

class DataStreamSpec extends TestKit(ActorSystem("DataStreamSpec", AkkaConfigurations.ConfigWithDetailedLogging)) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  val log = Logging(system, self)

  implicit val timeout = Timeout(1.second)

  override def afterAll() {
    system.terminate()
  }

  "DataStream" must {
    "initialized in Closed state" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream1")
      assert(dataStream.stateName == DataStreamState.Closed)
    }

    "subscribe events" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream2")
      dataStream ! SubscribeStreamEvents(self)

      assert(dataStream.stateData.set.size == 1)
    }

    "follow Closed -> Opened -> Online -> Closed states" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream3")
      assert(dataStream.stateName == DataStreamState.Closed)

      dataStream ! StreamEvent.Open
      assert(dataStream.stateName == DataStreamState.Opened)

      dataStream ! StreamEvent.StreamOnline
      assert(dataStream.stateName == DataStreamState.Online)

      dataStream ! StreamEvent.Close
      assert(dataStream.stateName == DataStreamState.Closed)
    }

    "forward stream events" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream4")
      dataStream ? SubscribeStreamEvents(self)

      dataStream ! StreamEvent.Open

      dataStream ! StreamEvent.TnBegin
      expectMsg(StreamEvent.TnBegin)

      dataStream ! StreamEvent.TnCommit
      expectMsg(StreamEvent.TnCommit)

      dataStream ! LifeNumChanged(100)
      expectMsg(LifeNumChanged(100))

      dataStream ! SubscribeTransitionCallBack(self)
      expectMsg(CurrentState(dataStream, DataStreamState.Opened))

      dataStream ! StreamEvent.StreamOnline
      expectMsg(Transition(dataStream, DataStreamState.Opened, DataStreamState.Online))

      dataStream ! StreamEvent.Close
      expectMsg(Transition(dataStream, DataStreamState.Online, DataStreamState.Closed))
    }
    
    "forward stream data" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream5")
      dataStream ? SubscribeStreamEvents(self)

      dataStream ! StreamEvent.Open
      
      val buffer1 = ByteBuffer.wrap(Array[Byte]())
      val buffer2 = ByteBuffer.wrap(Array[Byte]())
      dataStream ! StreamData(0, buffer1)
      dataStream ! StreamData(1, buffer2)

      expectMsg(StreamData(0, buffer1))
      expectMsg(StreamData(1, buffer2))

      dataStream ! ClearDeleted(0, 100)
      dataStream ! ClearDeleted(1, 101)

      expectMsg(ClearDeleted(0, 100))
      expectMsg(ClearDeleted(1, 101))
    }

    "subscribe repl states" in {
      val dataStream = TestFSMRef(new DataStream, "DataStream6")
      dataStream ! SubscribeCloseEvent(self)

      dataStream ! StreamEvent.Open

      dataStream ! ReplState("ebaka")

      expectMsg(DataStreamClosed(dataStream, ReplState("ebaka")))
    }
  }
}