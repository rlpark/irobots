package rlpark.plugin.irobot.internal.server;

import java.io.IOException;

import rlpark.plugin.irobot.internal.descriptors.IRobotCreateSerialConnection;
import rlpark.plugin.irobot.internal.descriptors.IRobotSerialDescriptor;

public class IRobotServer {
  final IRobotDiscoServer server;
  private final IRobotCreateSerialConnection serialConnection;
  private Thread serverThread;
  boolean isRunning = false;

  IRobotServer(int port, String serialPortPath, IRobotSerialDescriptor descriptor) {
    serialConnection = new IRobotCreateSerialConnection(serialPortPath, descriptor);
    server = new IRobotDiscoServer(port, serialConnection);
  }

  public void startDetach() {
    serverThread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (!server.initializeSerialLinkCommunication())
          return;
        isRunning = true;
        try {
          server.start();
        } catch (IOException e) {
          e.printStackTrace();
        }
        isRunning = false;
      }
    });
    serverThread.start();
  }

  public void stop() {
    server.close();
    serverThread = null;
  }

  public int port() {
    return server.port();
  }

  public boolean isRunning() {
    return isRunning;
  }
}
