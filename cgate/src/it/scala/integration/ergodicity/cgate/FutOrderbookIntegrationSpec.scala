package integration.ergodicity.cgate

import java.io.File

import integration._
import org.scalatest.{BeforeAndAfterAll, WordSpec, WordSpecLike}
import akka.actor.{Actor, ActorSystem, Props}

import scala.concurrent.duration._
import com.ergodicity.cgate._
import config.ConnectionConfig.Tcp
import akka.actor.FSM.Transition
import akka.actor.FSM.SubscribeTransitionCallBack
import com.ergodicity.cgate.Connection.StartMessageProcessing
import config.{CGateConfig, Replication}
import scheme.OrdBook
import com.ergodicity.cgate.config.Replication._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.event.Logging
import java.util.concurrent.TimeUnit

import ru.micexrts.cgate.{CGate, P2TypeParser, Connection => CGConnection, Listener => CGListener}
import akka.util.Timeout
import com.ergodicity.cgate.DataStream.SubscribeStreamEvents
import com.ergodicity.cgate.StreamEvent.StreamData
import java.util.Date



class FutOrderbookIntegrationSpec extends TestKit(ActorSystem("FutOrderBookIntegrationSpec", ConfigWithDetailedLogging)) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  val log = Logging(system, self)

  val Host = "localhost"
  val Port = 4001

  val RouterConnection = Tcp(Host, Port, system.name)

  implicit val timeout = Timeout(1.second)

  override def beforeAll() {
    val props = CGateConfig(new File("cgate/scheme/cgate_dev.ini"), "11111111")
    CGate.open(props())
    P2TypeParser.setCharset("windows-1251")
  }

  override def afterAll() {
    system.terminate()
    CGate.close()
  }

  "FutInfo DataStream" must {
    "load contents to Reportitory" in {
      val underlyingConnection = new CGConnection(RouterConnection())

      val connection = system.actorOf(Props(new Connection(underlyingConnection)), "Connection")

      val FutOrderBookDataStream = system.actorOf(Props(new DataStream), "FutOrderBookDataStream")

      // Listener
      val listenerConfig = Replication("FORTS_FUTORDERBOOK_REPL", new File("cgate/scheme/orderbook.ini"), "CustReplScheme")
      val underlyingListener = new CGListener(underlyingConnection, listenerConfig(), new DataStreamSubscriber(FutOrderBookDataStream))
      val listener = system.actorOf(Props(new Listener(underlyingListener)), "Listener")


      FutOrderBookDataStream ! SubscribeStreamEvents(TestActorRef(new StreamDataThrottler(10000) {
        override def handleData(data: StreamData) {
          import com.ergodicity.cgate.Protocol._
          data match {
            case StreamData(OrdBook.orders.TABLE_INDEX, bytes) =>
              val rec = implicitly[Reads[OrdBook.orders]] apply bytes
              log.info("Order record; Order id = " + rec.get_id_ord() + ", revision = " + rec.get_replRev() + ", sess id = " + rec.get_sess_id())

            case StreamData(OrdBook.info.TABLE_INDEX, bytes) =>
              val rec = implicitly[Reads[OrdBook.info]] apply bytes
              log.info("Info record; Moment = " + new Date(rec.get_moment()) + ", revision = " + rec.get_logRev())
          }
        }
      }))


      FutOrderBookDataStream ! SubscribeTransitionCallBack(system.actorOf(Props(new Actor {
        def receive = {
          case e => log.info("DATA STREAM STATE = " + e)
        }
      })))

      // On connection Activated open listeners etc
      connection ! SubscribeTransitionCallBack(system.actorOf(Props(new Actor {
        def receive = {
          case Transition(_, _, Active) =>
            // Open Listener in Combined mode
            listener ! Listener.Open(ReplicationParams(ReplicationMode.Snapshot))

            // Process messages
            connection ! StartMessageProcessing(500.millis)
        }
      })))

      // Open connections and track it's status
      connection ! Connection.Open

      Thread.sleep(TimeUnit.DAYS.toMillis(10))
    }
  }


}