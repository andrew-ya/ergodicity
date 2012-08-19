package com.ergodicity.engine.service

import akka.actor.{Terminated, ActorRef, Props, ActorSystem}
import org.scalatest.{GivenWhenThen, BeforeAndAfterAll, WordSpec}
import akka.event.Logging
import akka.testkit._
import akka.util.duration._
import org.mockito.Mockito._
import akka.actor.FSM.{Transition, SubscribeTransitionCallBack}
import com.ergodicity.engine.Engine
import ru.micexrts.cgate.{Connection => CGConnection, Listener => CGListener, ISubscriber, Publisher => CGPublisher}
import com.ergodicity.engine.service.Service.Start
import com.ergodicity.engine.Components.CreateListener
import com.ergodicity.cgate.{Opening, Active}

class BrokerManagerSpec extends TestKit(ActorSystem("BrokerManagerSpec", com.ergodicity.engine.EngineSystemConfig)) with ImplicitSender with WordSpec with BeforeAndAfterAll with GivenWhenThen {
  val log = Logging(system, self)

  override def afterAll() {
    system.shutdown()
  }

  private def mockEngine(serviceManager: TestProbe, broker: TestProbe) = TestActorRef(new {
    val ServiceManager = serviceManager.ref
    val StrategyManager = system.deadLetters
    val Broker = broker.ref
  } with Engine with CreateListener with BrokerConnections with Broker {
    val BrokerName = "TestBroker"

    def underlyingPublisherConnection = mock(classOf[CGConnection])

    def underlyingRepliesConnection = mock(classOf[CGConnection])

    def PublisherConnection = system.deadLetters

    def RepliesConnection = system.deadLetters

    implicit def BrokerConfig = null

    def underlyingPublisher = mock(classOf[CGPublisher])

    def listener(connection: CGConnection, config: String, subscriber: ISubscriber) = mock(classOf[CGListener])
  })


  "Broker Manager" must {
    "stash messages before BrokerConnectionsService is activated" in {
      val serviceManager = TestProbe()
      val broker = TestProbe()

      val engine = mockEngine(serviceManager, broker).underlyingActor
      val manager: ActorRef = TestActorRef(Props(new BrokerManager(engine)).withDispatcher("deque-dispatcher"), "BrokerManager")

      when("got Start message before broker connections service started")
      manager ! Start
      then("should stash it")
      broker.expectNoMsg(300.millis)

      when("Broker Connection Service started")
      manager ! ServiceStarted(BrokerConnectionsService)

      then("should track Broker state")
      broker.expectMsg(SubscribeTransitionCallBack(manager))

      when("Broker activated")
      manager ! Transition(broker.ref, Opening, Active)

      then("Service Manager should be notified")
      serviceManager.expectMsg(ServiceStarted(BrokerService))
    }

    "stop actor on Service.Stop message" in {
      val serviceManager = TestProbe()
      val broker = TestProbe()

      val engine = mockEngine(serviceManager, broker).underlyingActor
      val manager: ActorRef = TestActorRef(Props(new BrokerManager(engine)).withDispatcher("deque-dispatcher"), "BrokerManager")

      manager ! ServiceStarted(BrokerConnectionsService)
      watch(manager)

      when("stop Service")
      manager ! Service.Stop

      when("service manager should be notified")
      serviceManager.expectMsg(ServiceStopped(BrokerService))

      and("broker manager actor terminated")
      expectMsg(Terminated(manager))
    }
  }
}