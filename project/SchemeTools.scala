import sbt._
//import Process._


object SchemeTools {

  val Package = "com.ergodicity.cgate.scheme"

  case class SchemeProps(ini: String, className: String, scheme: String) {
    val fileName = className + ".java"
  }

  private val Schemes = List(
    SchemeProps("fut_info.ini", "FutInfo", "CustReplScheme"),
    SchemeProps("opt_info.ini", "OptInfo", "CustReplScheme"),

    SchemeProps("fut_trades.ini", "FutTrade", "CustReplScheme"),
    SchemeProps("fut_trades.ini", "FutOrder", "CustReplScheme"),

    SchemeProps("opt_trades.ini", "OptTrade", "CustReplScheme"),
    SchemeProps("opt_trades.ini", "OptOrder", "CustReplScheme"),

    SchemeProps("pos.ini", "Pos", "CustReplScheme"),
    SchemeProps("ordLog_trades.ini", "OrdLog", "CustReplScheme"),
    SchemeProps("forts_messages.ini", "Message", "message"),
    SchemeProps("orderbook.ini", "OrdBook", "CustReplScheme")
//
//      SchemeProps("FutInfo.ini", "FutInfo", "CustReplScheme"),
//    SchemeProps("OptInfo.ini", "OptInfo", "CustReplScheme"),
//
//    SchemeProps("FutTrades.ini", "FutTrade", "CustReplScheme"),
//    SchemeProps("FutOrders.ini", "FutOrder", "CustReplScheme"),
//
//    SchemeProps("OptTrades.ini", "OptTrade", "CustReplScheme"),
//    SchemeProps("OptOrders.ini", "OptOrder", "CustReplScheme"),
//
//    SchemeProps("Pos.ini", "Pos", "CustReplScheme"),
//    SchemeProps("OrdLog.ini", "OrdLog", "CustReplScheme"),
//    SchemeProps("FortsMessages.ini", "Message", "message"),
//    SchemeProps("Orderbook.ini", "OrdBook", "CustReplScheme")
  )

  def generateSchemes(projectDir: File, outDir: File): Seq[File] = {
    val temp = IO.createTemporaryDirectory

    import scala.sys.process._

    Schemes.foreach {
      case props: SchemeProps =>
        val cmd = makeCmd(
          temp / props.fileName,
          props.className,
          projectDir / "scheme" / props.ini,
          props.scheme
        )
        cmd !
    }

    val target = outDir / "com" / "ergodicity" / "cgate" / "scheme"
    IO.copyDirectory(temp, target)
    IO.listFiles(target).toSeq
  }

  private def makeCmd(out: File, className: String, ini: File, scheme: String) = {
    "/opt/cgate/bin/schemetool makesrc -O java -o %s -Djava-time-format=long -Djava-user-package=%s -Djava-class-name=%s %s %s".format(
      out,
      Package,
      className,
      ini,
      scheme
    )
  }
}
