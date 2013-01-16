import serial
import threading
import traceback
import struct
import sys

Serial = None
StringPrinting = False

def __readSerial():
    while Serial is not None:
        c = Serial.read(1)
        if not c:
            continue
        v = ord(c)
        if StringPrinting or v >= 33 and v < 128:
            sys.stdout.write(c)
            sys.stdout.flush()
        else: 
            print v

def openser(filename):
    try:
        global Serial
        Serial = serial.Serial(filename, 115200, timeout=5.0, parity=serial.PARITY_NONE, 
                               rtscts = True, dsrdtr = False, xonxoff = False, stopbits = serial.STOPBITS_ONE);
        print filename + " opened. Starting reading thread..."
        readingThread = threading.Thread(None, __readSerial, None, [], {})
        readingThread.daemon = True
        readingThread.start()
    except Exception:
        traceback.print_exc()
 
def send(data, stringPrinting = False):
    global StringPrinting
    StringPrinting = stringPrinting
    Serial.write(data)
    Serial.flush()
    
def sendstr(data):
    send(data + '\r', stringPrinting = True)

def sendcmd(data):
    send(struct.pack('B' * len(data), *data), stringPrinting = False)

def close():
    global Serial
    Serial.close()
    Serial = None