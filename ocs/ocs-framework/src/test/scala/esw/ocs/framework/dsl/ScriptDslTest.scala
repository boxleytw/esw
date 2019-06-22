package esw.ocs.framework.dsl

import csw.params.commands.{CommandName, Observe, Setup}
import csw.params.core.models.Prefix
import esw.ocs.framework.BaseTestSuite

import scala.collection.mutable.ArrayBuffer

class ScriptDslTest extends BaseTestSuite {

  "ScriptDsl" must {
    "allow adding and executing setup handler" in {
      var receivedPrefix: Option[Prefix] = None

      val script = new ScriptDsl {
        override def csw: CswServices = ???

        handleSetupCommand("iris") { cmd ⇒
          spawn {
            receivedPrefix = Some(cmd.source)
            ()
          }
        }
      }
      val prefix    = Prefix("iris.move")
      val irisSetup = Setup(prefix, CommandName("iris"), None)
      script.execute(irisSetup).await

      receivedPrefix.value shouldBe prefix
    }

    "allow adding and executing observe handler" in {
      var receivedPrefix: Option[Prefix] = None

      val script = new ScriptDsl {
        override def csw: CswServices = ???

        handleObserveCommand("iris") { cmd ⇒
          spawn {
            receivedPrefix = Some(cmd.source)
            ()
          }
        }
      }
      val prefix      = Prefix("iris.move")
      val irisObserve = Observe(prefix, CommandName("iris"), None)
      script.execute(irisObserve).await

      receivedPrefix.value shouldBe prefix
    }

    "allow adding and executing multiple shutdown handlers in order" in {
      val orderOfShutdownCalled = ArrayBuffer.empty[Int]

      val script = new ScriptDsl {
        override def csw: CswServices = ???

        handleShutdown {
          spawn {
            orderOfShutdownCalled += 1
            ()
          }
        }

        handleShutdown {
          spawn {
            orderOfShutdownCalled += 2
            ()
          }
        }
      }

      script.executeShutdown().await
      orderOfShutdownCalled shouldBe ArrayBuffer(1, 2)
    }
  }
}
