package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.Logger
import java.io.File

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec extends Specification {
    /*
  "Application" should {
    
    "work from within a browser" in {
      Logger.debug( "IntegrationSpec.Application.work from within a browser 0")
      running(TestServer(3333, new FakeApplication( path = new File("sample"))), HTMLUNIT) { browser =>
        Logger.debug( "IntegrationSpec.Application.work from within a browser 1")
        browser.goTo("http://localhost:3333/")

        Logger.debug( "IntegrationSpec.Application.work from within a browser 2")
        browser.pageSource must contain("Coral Sample")
        Logger.debug( "IntegrationSpec.Application.work from within a browser 3")

      }
    }
    
  }
  */
}