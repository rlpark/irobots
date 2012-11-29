package rlpark.plugin.irobot.internal.roomba;

import gnu.io.SerialPort;

import java.io.IOException;
import java.util.Arrays;

import rlpark.plugin.irobot.internal.descriptors.DropDescriptors;
import rlpark.plugin.irobot.internal.descriptors.IRobotObservationReceiver;
import rlpark.plugin.irobot.internal.serial.SerialPortToRobot;
import rlpark.plugin.irobot.internal.serial.SerialPortToRobot.SerialPortInfo;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.robot.internal.disco.datagroup.DropScalarGroup;
import rlpark.plugin.robot.internal.disco.drops.Drop;
import rlpark.plugin.robot.internal.sync.LiteByteBuffer;
import rlpark.plugin.robot.internal.sync.Syncs;
import rlpark.plugin.robot.observations.ObservationVersatile;
import zephyr.plugin.core.api.synchronization.Chrono;

public class RoombaSerialConnection implements IRobotObservationReceiver {
  static public final boolean SetupFireflyMandatory = false;
  private static final byte PacketID = 100;
  private final Drop sensorDrop = newSensorDrop();
  protected final DropScalarGroup sensors = new DropScalarGroup(sensorDrop);
  private final LiteByteBuffer byteBuffer = new LiteByteBuffer(sensorDrop.dataSize());
  private final byte[] Header = { 19, (byte) (1 + sensorDrop.dataSize()), PacketID };
  protected SerialPortToRobot serialPort;
  protected final String fileName;
  private boolean packetValid = false;

  public RoombaSerialConnection(String fileName) {
    this.fileName = fileName;
  }

  public static Drop newSensorDrop() {
    // return new Drop(IRobotLabels.RoombaSensorDropName, new DropData[] { new
    // DropByteArray("Data", 80) });
    // DropData[] descriptors = new DropData[] { new
    // DropBit(IRobotLabels.WheelDropLeft, 3),
    // new DropBit(IRobotLabels.WheelDropRight, 2), new
    // DropBit(IRobotLabels.BumpLeft, 1),
    // new DropBit(IRobotLabels.BumpRight, 0), new DropEndBit("EndPacket7") };
    // return new Drop(IRobotLabels.RoombaSensorDropName, descriptors);
    return DropDescriptors.newRoombaSensorDrop();
  }

  private boolean setupFirefly(SerialPortToRobot serialPort) {
    if (serialPort.purge() > 0)
      return true;
    System.out.println("Setting up Roomba's firefly...");
    try {
      serialPort.sendAndReceive("$$$", "CMD\r\n");
      serialPort.sendAndReceive("U,115k,N\r", "AOK\r\n");
      serialPort.send("---\r");
    } catch (IOException e) {
      System.out.println("Setting up Firefly has failed...");
      if (SetupFireflyMandatory) {
        e.printStackTrace();
        return false;
      }
    }
    return true;
  }

  private boolean setupRoomba(final SerialPortToRobot serialPort) {
    System.out.println("Setting up Roomba...");
    serialPort.wakeupRobot();
    do {
      if (!send(new char[] { 128 }))
        return false;
      if (!send(new char[] { 131 }))
        return false;
      if (!send(new char[] { 150, 0 }))
        return false;
    } while (purgeIFN());
    // send(new char[] { 148, 1, PacketID })
    return true;
  }

  public boolean purgeIFN() {
    int purge = serialPort.purge();
    if (purge > 0) {
      System.out.println("Data on the link: " + purge);
      return true;
    }
    return false;
  }

  @Override
  public void initialize() {
    SerialPortInfo serialPortInfo = new SerialPortInfo(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                                                       SerialPort.PARITY_NONE, SerialPort.FLOWCONTROL_RTSCTS_IN
                                                           | SerialPort.FLOWCONTROL_RTSCTS_OUT);
    serialPort = SerialPortToRobot.tryOpenPort(fileName, serialPortInfo);
    if (serialPort == null)
      return;
    serialPort.wakeupRobot();
    if (!setupFirefly(serialPort)) {
      serialPort.close();
      return;
    }
    if (!setupRoomba(serialPort))
      serialPort.close();
  }

  @Override
  public int packetSize() {
    return sensorDrop.dataSize();
  }

  @Override
  public ObservationVersatile waitForData() {
    pingRobot();
    return waitDataStream();
  }

  private final Chrono pingChrono = new Chrono(Chrono.longTimeAgo());
  private boolean dockOn = false;

  private void pingRobot() {
    if (pingChrono.getCurrentChrono() < 1.0)
      return;
    if (dockOn)
      send(new char[] { 139, 2, 0, 0 });
    else
      send(new char[] { 139, 4, 0, 0 });
    dockOn = !dockOn;
    pingChrono.start();
  }

  protected ObservationVersatile waitForDataRequest() {
    if (!send(new char[] { 142, 100 }))
      return null;
    byte[] data;
    try {
      data = serialPort.read(sensorDrop.dataSize());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    if (data.length < sensorDrop.dataSize())
      System.err.println("data incomplete");
    byteBuffer.clear();
    byteBuffer.put(data);
    return Syncs.createObservation(System.currentTimeMillis(), byteBuffer, sensors);
  }

  private boolean send(char[] cs) {
    try {
      serialPort.send(cs);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private ObservationVersatile waitDataStream() {
    try {
      if (!packetValid)
        alignOnHeader();
      packetValid = readNextPacket(byteBuffer);
    } catch (IOException e) {
      e.printStackTrace();
      packetValid = false;
    }
    if (!packetValid)
      return null;
    return Syncs.createObservation(System.currentTimeMillis(), byteBuffer, sensors);
  }

  private boolean readNextPacket(LiteByteBuffer byteBuffer) throws IOException {
    byte checkSum = 01;
    byte[] data = null;
    while (checkSum != 0) {
      data = serialPort.read(byteBuffer.capacity() + Header.length + 1);
      if (!Arrays.equals(Header, Arrays.copyOf(data, Header.length)))
        return false;
      checkSum = computeChecksum(data);
    }
    byteBuffer.clear();
    byteBuffer.put(Arrays.copyOfRange(data, Header.length, data.length - 1));
    return true;
  }

  private byte computeChecksum(byte[] data) {
    int sum = 0;
    for (byte b : data)
      sum += b;
    return (byte) sum;
  }

  private void alignOnHeader() throws IOException {
    int checkedPosition = 0;
    int nbFailed = 0;
    while (checkedPosition < Header.length) {
      byte[] read = serialPort.read(1);
      if (read.length == 0)
        continue;
      if (read[0] == Header[checkedPosition]) {
        checkedPosition++;
      } else {
        nbFailed++;
        if (nbFailed > 1000) {
          serialPort.purge();
          System.out.println("Packet header cannot be found... just noise on the link");
          nbFailed = 0;
        }
        checkedPosition = 0;
      }
    }
    serialPort.read(sensorDrop.dataSize() + 1);
  }

  @Override
  public boolean isClosed() {
    return serialPort == null || serialPort.isClosed();
  }

  @Override
  public void sendMessage(byte[] bytes) {
    try {
      serialPort.send(bytes);
    } catch (IOException e) {
      e.printStackTrace();
      serialPort.close();
    }
  }

  @Override
  public Legend legend() {
    return sensors.legend();
  }
}
