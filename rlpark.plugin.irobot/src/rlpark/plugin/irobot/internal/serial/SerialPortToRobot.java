package rlpark.plugin.irobot.internal.serial;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import rlpark.plugin.robot.internal.disco.drops.DropByteArray;
import zephyr.plugin.core.api.signals.Listener;
import zephyr.plugin.core.api.signals.Signal;

public class SerialPortToRobot implements SerialPortEventListener {
  static public final boolean ExpectedIgnored = false;
  static public boolean DebugSignals = false;

  public static class SerialPortInfo {
    public int rate = 115200;
    public int databits = SerialPort.DATABITS_8;
    public int stopbits = SerialPort.STOPBITS_1;
    public int parity = SerialPort.PARITY_NONE;
    public int flowControl = SerialPort.FLOWCONTROL_NONE;

    public SerialPortInfo() {
      this(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE, SerialPort.FLOWCONTROL_NONE);
    }

    public SerialPortInfo(int rate, int databits, int stopbits, int parity, int flowControl) {
      this.rate = rate;
      this.databits = databits;
      this.stopbits = stopbits;
      this.parity = parity;
      this.flowControl = flowControl;
    }
  }

  protected final SerialStreams serialStreams;
  private final String serialPortFileName;
  private final CommPortIdentifier identifier;
  private final SerialPort serialPort;
  private final Map<Integer, Signal<SerialPortToRobot>> signals = Collections
      .synchronizedMap(new HashMap<Integer, Signal<SerialPortToRobot>>());
  private boolean isClosed;

  public SerialPortToRobot(String fileName, SerialPortInfo portInfo) throws PortInUseException,
      UnsupportedCommOperationException, TooManyListenersException, IOException {
    serialPortFileName = fileName;
    identifier = SerialPorts.getPortIdentifier(serialPortFileName);
    if (identifier == null)
      throw new RuntimeException("Port identifier " + serialPortFileName + " not found");
    serialPort = (SerialPort) identifier.open("RLPark", 150);
    serialPort.addEventListener(this);
    serialPort.setFlowControlMode(portInfo.flowControl);
    serialPort.setSerialPortParams(portInfo.rate, portInfo.databits, portInfo.stopbits, portInfo.parity);
    serialStreams = new SerialStreams(serialPort);
    setNotifiers();
  }

  public void wakeupRobot() {
    serialPort.setRTS(false);
    serialPort.setDTR(false);
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    serialPort.setRTS(true);
    serialPort.setDTR(true);
  }

  private void setNotifiers() {
    serialPort.notifyOnDataAvailable(true);
    serialPort.notifyOnOutputEmpty(true);
    serialPort.notifyOnBreakInterrupt(true);
    serialPort.notifyOnCarrierDetect(true);
    serialPort.notifyOnCTS(true);
    serialPort.notifyOnDSR(true);
    serialPort.notifyOnFramingError(true);
    serialPort.notifyOnOverrunError(true);
    serialPort.notifyOnParityError(true);
    serialPort.notifyOnRingIndicator(true);
  }

  public void register(int event, Listener<SerialPortToRobot> listener) {
    Signal<SerialPortToRobot> signal = signals.get(event);
    if (signal == null) {
      signal = new Signal<SerialPortToRobot>();
      signals.put(event, signal);
    }
    signal.connect(listener);
  }

  public void unregister(int event, Listener<SerialPortToRobot> listener) {
    signals.get(event).disconnect(listener);
  }

  @Override
  public void serialEvent(SerialPortEvent event) {
    Signal<SerialPortToRobot> signal = signals.get(event.getEventType());
    if (signal != null)
      signal.fire(this);
    if (!DebugSignals)
      return;
    switch (event.getEventType()) {
    case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
      System.out.println("Event received: outputBufferEmpty");
      break;

    case SerialPortEvent.DATA_AVAILABLE:
      System.out.println("Event received: dataAvailable");
      break;

    case SerialPortEvent.BI:
      System.out.println("Event received: breakInterrupt");
      break;

    case SerialPortEvent.CD:
      System.out.println("Event received: carrierDetect");
      break;

    case SerialPortEvent.CTS:
      System.out.println("Event received: clearToSend");
      break;

    case SerialPortEvent.DSR:
      System.out.println("Event received: dataSetReady");
      break;

    case SerialPortEvent.FE:
      System.out.println("Event received: framingError");
      break;

    case SerialPortEvent.OE:
      System.out.println("Event received: overrunError");
      break;

    case SerialPortEvent.PE:
      System.out.println("Event received: parityError");
      break;
    case SerialPortEvent.RI:
      System.out.println("Event received: ringIndicator");
      break;
    default:
      System.out.println("Event received: unknown");
    }
  }

  public void send(byte[] bytes) throws IOException {
    serialStreams.write(bytes);
  }

  public void send(char[] chars) throws IOException {
    serialStreams.write(DropByteArray.toBytes(chars));
  }

  public void send(String command) throws IOException {
    serialStreams.write(command.getBytes());
  }

  public void close() {
    // Produce a SEG FAULT
    // serialStreams.close();
    if (isClosed)
      return;
    isClosed = true;
  }

  public void sendAndReceive(String command, final String expectedAnswer) throws IOException {
    send(command.getBytes());
    byte[] received = serialStreams.read(expectedAnswer.length());
    if (!ExpectedIgnored && !Arrays.equals(received, expectedAnswer.getBytes()))
      throw new IOException(String.format("Return incorrect: expected <%s> was <%s>", expectedAnswer,
                                          new String(received)));
  }

  public void sendAndWait(char[] chars) throws IOException {
    sendAndWait(DropByteArray.toBytes(chars));
  }

  public void sendAndWait(byte[] chars) throws IOException {
    final Semaphore semaphore = new Semaphore(0);
    Listener<SerialPortToRobot> listener = new Listener<SerialPortToRobot>() {
      @Override
      public void listen(SerialPortToRobot eventInfo) {
        semaphore.release();
      }
    };
    register(SerialPortEvent.OUTPUT_BUFFER_EMPTY, listener);
    serialStreams.write(chars);
    try {
      semaphore.tryAcquire(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    unregister(SerialPortEvent.OUTPUT_BUFFER_EMPTY, listener);
  }

  public boolean isClosed() {
    return isClosed;
  }

  public byte[] read(int size) throws IOException {
    return serialStreams.read(size);
  }

  static public SerialPortToRobot tryOpenPort(String serialPortFile, SerialPortInfo serialPortInfo) {
    for (int trial = 0; trial < 10; trial++) {
      SerialPortToRobot serialPort = openPort(serialPortFile, serialPortInfo);
      if (serialPort != null)
        return serialPort;
      try {
        Thread.sleep(4000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  static public SerialPortToRobot openPort(String serialPortFile, SerialPortInfo serialPortInfo) {
    SerialPorts.refreshPortIdentifiers();
    SerialPortToRobot serialPort = null;
    try {
      serialPort = new SerialPortToRobot(serialPortFile, serialPortInfo);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return serialPort;
  }

  static public void fatalError(String message) {
    System.err.println(message);
    System.exit(1);
  }

  public int purge() {
    try {
      return serialStreams.read(serialStreams.available()).length;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return 0;
  }
}
