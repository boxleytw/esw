package esw.ocs.scripts.examples.testData

import esw.ocs.dsl.core.script
import kotlinx.coroutines.delay

script {

    loadScripts(exceptionHandlerScript)

    handleSetup("successful-command") {
        println("completed successfully")
    }

    handleSetup("long-running-setup") {
        delay(50000)
    }

    handleSetup("fail-setup") {
        throw RuntimeException("handle-setup-failed")
    }

    handleObserve("fail-observe") {
        throw RuntimeException("handle-observe-failed")
    }

    handleGoOffline {
        throw RuntimeException("handle-goOffline-failed")
    }

    handleDiagnosticMode { time, hint ->
        throw RuntimeException("handle-diagnostic-mode-failed")
    }

    handleOperationsMode {
        throw RuntimeException("handle-operations-mode-failed")
    }

    handleStop {
        throw RuntimeException("handle-stop-failed")
    }

    handleAbortSequence {
        throw RuntimeException("handle-abort-failed")
    }
}