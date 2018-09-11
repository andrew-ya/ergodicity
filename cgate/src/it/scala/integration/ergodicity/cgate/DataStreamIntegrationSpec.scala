package integration.ergodicity.cgate

import akka.actor.FSM.{SubscribeTransitionCallBack, Transition}
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}

import scala.concurrent.duration._
import com.ergodicity.cgate.Connection.StartMessageProcessing
import com.ergodicity.cgate.DataStream.SubscribeStreamEvents
import com.ergodicity.cgate._
import com.ergodicity.cgate.config.ConnectionConfig.Tcp
import com.ergodicity.cgate.config.Replication._
import config.{CGateConfig, Replication}
import java.io.File
import java.util.concurrent.TimeUnit

import org.scalatest.{BeforeAndAfterAll, WordSpec, WordSpecLike}
import ru.micexrts.cgate.{CGate, P2TypeParser, Connection => CGConnection, Listener => CGListener}

class DataStreamIntegrationSpec extends TestKit(ActorSystem("DataStreamIntegrationSpec", integration.ConfigWithDetailedLogging)) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  val log = Logging(system, self)

  val Host = "localhost"
  val Port = 4001

  val RouterConnection = Tcp(Host, Port, system.name)

  override def beforeAll() {
    val props = CGateConfig(new File("cgate/scheme/cgate_dev.ini"), "11111111")
    CGate.open(props())
    P2TypeParser.setCharset("windows-1251")
  }

  override def afterAll() {
    system.terminate()
    CGate.close()
  }

  "DataStream" must {
    "go online" in {
      val underlyingConnection = new CGConnection(RouterConnection())

      val connection = system.actorOf(Props(new Connection(underlyingConnection)), "Connection")

      val DataStream = system.actorOf(Props(new DataStream), "DataStream")

      // Listener
      val listenerConfig = Replication("FORTS_OPTTRADE_REPL", new File("cgate/scheme/OptTrades.ini"), "CustReplScheme")
      val underlyingListener = new CGListener(underlyingConnection, listenerConfig(), new DataStreamSubscriber(DataStream))
      val listener = system.actorOf(Props(new Listener(underlyingListener)), "Listener")


      DataStream ! SubscribeStreamEvents(TestActorRef(new StreamDataThrottler(10)))

      // On connection Activated open listeners etc
      connection ! SubscribeTransitionCallBack(system.actorOf(Props(new Actor {
        def receive = {
          case Transition(_, _, Active) =>
            // Open Listener in Combined mode
            listener ! Listener.Open(ReplicationParams(ReplicationMode.Combined))

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
