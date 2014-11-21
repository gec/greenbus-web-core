package org.totalgrid.web.util

import play.api.Logger

object Timer {

  sealed trait LogType
  case object INFO extends LogType
  case object DEBUG extends LogType
  case object TRACE extends LogType
}

/**
 *
 * @author Flint O'Brien
 */
class Timer( var name: String, loggerType: Timer.LogType = Timer.TRACE) {
  import Timer._

  val log = loggerType match {
    case INFO => (message: String) => Logger.info( message)
    case DEBUG => (message: String) => Logger.debug( message)
    case TRACE => (message: String) => Logger.trace( message)
  }
  log( "Timer." + name + " start")

  var timeStart = System.currentTimeMillis
  var timeLast = timeStart

  def restart( newName: String) = {
    name = newName
    log( f"Timer.$name restart")
    timeStart = System.currentTimeMillis
    timeLast = timeStart
  }

  def delta( message: String) = {
    val now  = System.currentTimeMillis
    log( f"Timer.$name elapsed:${now-timeStart}%4d  delta:${now-timeLast}%4d $message")
    timeLast = now
  }

  def end( message: String) = {
    val now  = System.currentTimeMillis
    log( f"Timer.$name   total:${now-timeStart}%4d  delta:${now-timeLast}%4d $message")
    timeLast = now
  }

  /**
   * The timer has ended with an error. Log the end time as an error.
   * @param message The message to log.
   */
  def error( message: String) = {
    val now  = System.currentTimeMillis
    Logger.error( f"Timer.$name   total:${now-timeStart}%4d  delta:${now-timeLast}%4d $message")
    timeLast = now
  }
}


