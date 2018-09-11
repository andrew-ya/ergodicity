package com.ergodicity.cgate.config

import org.scalatest.WordSpec

import scala.concurrent.duration._
import java.io.File

import scala.language.postfixOps

class PublisherConfigSpec extends WordSpec {

  "Publisher Config" must {
    "provide valid configuration" in {
      val scheme = new File("./cgate/scheme/forts_messages.ini")
      val config = FortsMessages("cmd",5000 millis, scheme, "message")
      assert(config() == "p2mq://FORTS_SRV;category=FORTS_MSG;timeout=5000;scheme=|FILE|"+scheme.getAbsolutePath+"|message;name=cmd")
    }
  }

}