package rlpark.plugin.irobot.internal.descriptors;

import java.io.IOException;

import rlpark.plugin.irobot.internal.create.SerialLinkStateMachine;
import rlpark.plugin.irobot.internal.serial.SerialPortToRobot;
import rlpark.plugin.irobot.internal.serial.SerialPortToRobot.SerialPortInfo;
import rlpark.plugin.robot.internal.disco.drops.Drop;

public interface IRobotSerialDescriptor {
  SerialLinkStateMachine createStateMachine(SerialPortToRobot serialPort);

  Drop createSensorDrop();

  boolean initializeRobotCommunication(SerialPortToRobot serialPort) throws IOException;

  byte[] messageOnNoClient();

  SerialPortInfo portInfo();
}
