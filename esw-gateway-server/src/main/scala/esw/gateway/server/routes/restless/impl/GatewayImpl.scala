package esw.gateway.server.routes.restless.impl
import esw.http.core.utils.CswContext

class GatewayImpl(val cswCtx: CswContext) extends CommandGatewayImpl with AlarmGatewayImpl with EventGatewayImpl