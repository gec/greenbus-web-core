package org.totalgrid.coral.util

import play.api.Logger

object TimerLogger extends Enumeration {
  type TimerLogger = Value

  val TRACE = Value
  val DEBUG = Value
}
import org.totalgrid.coral.util.TimerLogger._

/**
 *
 * @author Flint O'Brien
 */
class Timer( var name: String, loggerType: TimerLogger = TimerLogger.TRACE) {

  var timeStart = System.currentTimeMillis
  var timeLast = timeStart
  loggerType match {
    case TRACE => Logger.trace( name + " start")
    case DEBUG => Logger.debug( name + " start")
  }

  def restart( newName: String) = {
    name = newName
    loggerType match {
      case TRACE => Logger.trace( name + " restart")
      case DEBUG => Logger.debug( name + " restart")
    }
    timeStart = System.currentTimeMillis
    timeLast = timeStart
  }
  def delta( message: String) = {
    val now  = System.currentTimeMillis
    val s = f"Timer.$name elapsed:${now-timeStart}%4d  delta:${now-timeLast}%4d $message"
    loggerType match {
      case TRACE => Logger.trace( s)
      case DEBUG => Logger.debug( s)
    }
    timeLast = now
  }
  def end( message: String) = {
    val now  = System.currentTimeMillis
    val s = f"Timer.$name   total:${now-timeStart}%4d  delta:${now-timeLast}%4d $message"
    loggerType match {
      case TRACE => Logger.trace( s)
      case DEBUG => Logger.debug( s)
    }
    timeLast = now
  }
}


