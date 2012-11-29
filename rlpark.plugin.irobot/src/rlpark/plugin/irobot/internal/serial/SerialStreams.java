package rlpark.plugin.irobot.internal.serial;

import gnu.io.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class SerialStreams {
  private final InputStream input;
  private final OutputStream output;

  protected SerialStreams(SerialPort serialPort) throws IOException {
    input = serialPort.getInputStream();
    output = serialPort.getOutputStream();
  }

  synchronized public void write(int c) throws IOException {
    output.write(c);
  }

  synchronized public void write(byte[] c) throws IOException {
    output.write(c);
  }

  synchronized public int available() throws IOException {
    return input.available();
  }

  synchronized public int read() throws IOException {
    return input.read();
  }

  synchronized public byte[] read(int size) throws IOException {
    byte[] buffer = new byte[size];
    int alreadyRead = 0;
    while (alreadyRead < size) {
      int justRead = input.read(buffer, alreadyRead, size - alreadyRead);
      if (justRead == 0)
        return Arrays.copyOf(buffer, alreadyRead);
      alreadyRead += justRead;
    }
    return buffer;
  }

  synchronized public void close() {
    try {
      input.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      output.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
