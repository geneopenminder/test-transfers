package com.work.testing;

import com.work.testing.exception.*;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;

import java.io.IOException;

public class Server extends HttpServer {

    private final static MoneyTransferService transferService = new MoneyTransferService();

    public static void main( String[] args ) throws Exception {
        createServer();
    }

    public Server(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    public static void createServer() throws Exception {
        HttpServerConfig config = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = 80;

        config.acceptors = new AcceptorConfig[]{acceptor};
        config.selectors = Runtime.getRuntime().availableProcessors();
        config.affinity = true;

        Server server = new Server(config);
        server.start();
    }

    @Path("/transfer")
    @RequestMethod(Request.METHOD_PUT)
    public Response transfer(@Param("from") String from,
                             @Param("to") String to,
                             @Param("amount") int amount) {
        try {
            transferService.transfer(from, to, amount);
            return new Response(Response.OK);
        } catch (BadArgException | BadAmountException | EqualsAccountsException | NoFundsException e) {
            return new Response(Response.BAD_REQUEST);
        } catch (NotExistsException e) {
            return new Response(Response.NOT_FOUND);
        } catch (SystemBusyException e) {
            return new Response(Response.REQUEST_TIMEOUT);
        } catch (RuntimeException e) {
            return new Response(Response.INTERNAL_ERROR);
        }
    }

}
