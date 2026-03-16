package id.naturalsmp.naturalvelocity.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.ProxyServer;

import id.naturalsmp.naturalvelocity.NaturalVelocity;

public class SyncConnection {
    // pool for connections to use
    private static Executor connectionPool = Executors.newCachedThreadPool();
    
    // time for keepalive = seconds
    private volatile int keepAliveTimeOutTime = 100;

    // whether this connection has been unregistered
    private volatile boolean hasUnregistered = false;

    // connection name
    private String connectionName = "natural-smp";

    private void startKeepAliveHandler() {
        Runnable keepAliveRunnable = (() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (keepAliveTimeOutTime == 0 && !hasUnregistered) {
                    closeConnection();
                } else if (hasUnregistered) {
                    break;
                }

                keepAliveTimeOutTime--;
            }
        });
        connectionPool.execute(keepAliveRunnable);
    }

    public void closeConnection() {
        SyncServer.connectionList.remove(this);
        hasUnregistered = true;
    }

    /**
     * Starts the Connection
     * 
     * @param clientSocket the client's socket
     */
    public void startConnection(Socket clientSocket) {

        NaturalVelocity.getInstance().getLogger().info("New connection from " + this.connectionName + " established.");

        // Initializes a new connection from a client
        Runnable connectionRunnable = (() -> {
            try {
                // The connected client's input
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // Output for communication with client
                PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);

                // Handle keep alives
                startKeepAliveHandler();

                // Handle client messages
                String clientInput;
                boolean hasAuthorized = false;

                while (true) {

                    clientInput = in.readLine();

                    if (!hasAuthorized) {
                        if (clientInput.contains("password ")) {
                            String password = clientInput.replace("password ", "");
                            if (!password.equals(NaturalVelocity.getInstance().getSyncServer().getPassword())) {
                                closeConnection();
                                break;
                            } else {
                                NaturalVelocity.getInstance().getLogger().info("Successfully authorized a connection");
                                hasAuthorized = true;
                            }
                        }
                        continue;
                    }

                    // Exit connection if keep alive has expired
                    if (hasUnregistered) {
                        break;
                    }

                    // Exit connection if told to do so by the client
                    if (clientInput.equalsIgnoreCase(".")) {
                        closeConnection();
                        break;

                    } else if (clientInput.toLowerCase().contains("name ")) {

                        String finalName = clientInput.toLowerCase().replace("name ", "");
                        SyncConnection.this.connectionName = finalName;

                    } else if (clientInput.equalsIgnoreCase("keep alive packet")) {

                        keepAliveTimeOutTime = 100;

                    } else if (clientInput.toLowerCase().contains("run command ")) {

                        String processCommand = clientInput.toLowerCase().replace("run command ", "");
                        ProxyServer server = NaturalVelocity.getInstance().getServer();

                        NaturalVelocity.getInstance().getLogger().info("received command " + processCommand);

                        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), processCommand);

                        NaturalVelocity.getInstance().getLogger().info("ran command " + processCommand);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Start the connection with the client on its own thread
        connectionPool.execute(connectionRunnable);
    }

}
