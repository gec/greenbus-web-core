package org.totalgrid.coral.mocks

import org.totalgrid.reef.client
import org.totalgrid.reef.client._

/**
 *
 * @author Flint O'Brien
 */
class PromiseMock[T]( value: T) extends client.Promise[T] {
  def await(): T = value

  def listen(p1: PromiseListener[T]) {}

  def isComplete: Boolean = true

  def transform[U](p1: PromiseTransform[T, U]): Promise[U] = null

  def transformError(p1: PromiseErrorTransform): Promise[T] = null
}
