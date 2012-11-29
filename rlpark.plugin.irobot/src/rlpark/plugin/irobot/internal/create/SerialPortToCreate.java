package rlpark.plugin.irobot.internal.create;

import gnu.io.PortInUseException;
import gnu.io.SerialPortEvent;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;

import rlpark.plugin.irobot.internal.create.SerialListeners.AlwaysWakeUpThread;
import rlpark.plugin.irobot.internal.create.SerialListeners.WakeUpThread;
import rlpark.plugin.irobot.internal.serial.SerialPortToRobot;
import rlpark.plugin.robot.internal.disco.drops.DropByteArray;
import zephyr.plugin.core.api.signals.Signal;

public class SerialPortToCreate extends SerialPortToRobot {
  private List<Byte> buffer = new ArrayList<Byte>();
  public final Signal<SerialPortToRobot> onClosed = new Signal<SerialPortToRobot>();

  public SerialPortToCreate(String fileName, SerialPortInfo portInfo) throws PortInUseException,
      UnsupportedCommOperationException, TooManyListenersException, IOException {
    super(fileName, portInfo);
  }

  @Override
  synchronized public void serialEvent(SerialPortEvent event) {
    if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE)
      updateAvailable();
    super.serialEvent(event);
  }

  @Override
  public void send(char[] chars) throws IOException {
    send(DropByteArray.toBytes(chars));
  }

  @Override
  public void send(String command) throws IOException {
    send(command.getBytes());
  }

  synchronized public void sendAndExpect(String command, final String returnExpected) throws IOException {
    SerialListeners.ReadWhenArriveAndWakeUp listener = new SerialListeners.ReadWhenArriveAndWakeUp();
    register(SerialPortEvent.DATA_AVAILABLE, listener);
    send(command);
    waitForSignal();
    unregister(SerialPortEvent.DATA_AVAILABLE, listener);
    if (!ExpectedIgnored && !returnExpected.equals(listener.message()))
      throw new IOException(String.format("Return incorrect: expected <%s> was <%s>", returnExpected,
                                          listener.message()));
  }

  private void updateAvailable() {
    buffer = new ArrayList<Byte>();
    try {
      while (serialStreams.available() > 0)
        buffer.add((byte) serialStreams.read());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void sendAndWait(char[] chars) throws IOException {
    sendAndWait(DropByteArray.toBytes(chars));
  }

  @Override
  synchronized public void sendAndWait(byte[] chars) throws IOException {
    AlwaysWakeUpThread listener = new AlwaysWakeUpThread();
    register(SerialPortEvent.OUTPUT_BUFFER_EMPTY, listener);
    send(chars);
    waitForSignal();
    unregister(SerialPortEvent.OUTPUT_BUFFER_EMPTY, listener);
  }

  private void waitForSignal() {
    try {
      wait(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public int sendAndWaitForData(char[] chars, final int dataSizeToWaitFor) throws IOException {
    return sendAndWaitForData(DropByteArray.toBytes(chars), dataSizeToWaitFor);
  }

  synchronized public int sendAndWaitForData(byte[] bytes, final int dataSizeToWaitFor) throws IOException {
    WakeUpThread listener = new WakeUpThread(new SerialListeners.SerialInputCondition() {
      private int remainingData = dataSizeToWaitFor;

      @Override
      public boolean isSatisfied(SerialPortToRobot serialPort) {
        remainingData -= available();
        return remainingData <= 0;
      }
    });
    register(SerialPortEvent.DATA_AVAILABLE, listener);
    send(bytes);
    waitForSignal();
    unregister(SerialPortEvent.DATA_AVAILABLE, listener);
    return listener.nbDataAvailable();
  }

  public String getAvailableAsString() {
    StringBuilder result = new StringBuilder();
    for (Byte b : buffer)
      result.append((char) (byte) b);
    return result.toString();
  }

  public byte[] getAvailable() {
    byte[] result = new byte[buffer.size()];
    for (int i = 0; i < result.length; i++)
      result[i] = buffer.get(i);
    return result;
  }

  public int available() {
    return buffer.size();
  }

  @Override
  public void close() {
    super.close();
    onClosed.fire(this);
  }
}
