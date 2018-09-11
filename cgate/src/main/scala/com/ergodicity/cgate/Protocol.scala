package com.ergodicity.cgate

import java.nio.ByteBuffer
import scheme._

trait Reads[T] {
  def apply(in: ByteBuffer): T = read(in)

  def read(in: ByteBuffer): T
}

object Protocol {

  implicit val ReadsFutInfoSessions = new Reads[FutInfo.session] {
    def read(in: ByteBuffer) = new FutInfo.session(in)
  }
  
  implicit val ReadsFutInfoSessionContents = new Reads[FutInfo.fut_sess_contents] {
    def read(in: ByteBuffer) = new FutInfo.fut_sess_contents(in)
  }

  implicit val ReadsFutInfoSysEvents = new Reads[FutInfo.sys_events] {
    def read(in: ByteBuffer) = new FutInfo.sys_events(in)
  }

  implicit val ReadsOptInfoSessionContents = new Reads[OptInfo.opt_sess_contents] {
    def read(in: ByteBuffer) = new OptInfo.opt_sess_contents(in)
  }

  implicit val ReadsOptInfoSysEvents = new Reads[OptInfo.sys_events] {
    def read(in: ByteBuffer) = new OptInfo.sys_events(in)
  }

  implicit val ReadsPosPositions = new Reads[Pos.position] {
    def read(in: ByteBuffer) = new Pos.position(in)
  }
  
  implicit val ReadsOrdLogOrders = new Reads[OrdLog.orders_log] {
    def read(in: ByteBuffer) = new OrdLog.orders_log(in)
  }
  
  implicit val ReadsFutOrders = new Reads[FutOrder.orders_log] {
    def read(in: ByteBuffer) = new FutOrder.orders_log(in)
  }

  implicit val ReadsFutTrades = new Reads[FutTrade.user_deal] {
    def read(in: ByteBuffer) = new FutTrade.user_deal(in)
  }

  implicit val ReadsOptOrders = new Reads[OptOrder.orders_log] {
    def read(in: ByteBuffer) = new OptOrder.orders_log(in)
  }

  implicit val ReadsOptTradeS = new Reads[OptTrade.user_deal] {
    def read(in: ByteBuffer) = new OptTrade.user_deal(in)
  }

  implicit val ReadsOrderBookOrders = new Reads[OrdBook.orders] {
    def read(in: ByteBuffer) = new OrdBook.orders(in)
  }

  implicit val ReadsOrderBookInfo = new Reads[OrdBook.info] {
    def read(in: ByteBuffer) = new OrdBook.info(in)
  }
}