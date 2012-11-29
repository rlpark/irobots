import datetime
import random
import serial
import socket
import struct
import sys
import time
import traceback
import threading

class Chrono(object):
    def __init__(self):
        self.__start = datetime.datetime.now()

    def start(self):
        self.__start = datetime.datetime.now()
        
    def __call__(self):
        return (datetime.datetime.now() - self.__start).total_seconds()
    
    def __str__(self):
        return str(datetime.datetime.now() - self.__start)


class Server(object):
    __SensorDropName = "RoombaSensorDrop"
    __ActionDropName = "IRobotCommandByteStringDrop"
    
    __sensorDropFormat = ">i" + "c" * len(__SensorDropName) + "i"
    __CommandSize = 36
    __actionDropFormat = ">i" + "c" * len(__ActionDropName) + "i" + "B" * __CommandSize
    __actionDropSize = struct.calcsize(__actionDropFormat)

    def __init__(self, port = 3000):
        self.port = port
        self.__serverRunning = True
        self.__registeredClient = []
        self.__callback = self.__defaultCallbackFunction
        self.__callbackLock = threading.RLock()

    def __defaultCallbackFunction(self, obs):
        print obs

    def __removeClient(self, clientSocket):
        self.__registeredClient.remove(clientSocket)
        print "client disconnected"
        clientSocket.close()

    def __registerClient(self, clientSocket, addr):
        self.__registeredClient.append(clientSocket)
    
        def recvClientMessage():
            while self.__serverRunning:
                data = clientSocket.recv(self.__actionDropSize)
                if not data:
                    break
                unpacked = struct.unpack(self.__actionDropFormat, data)
                dropName = ''.join(unpacked[1:1 + len(self.__ActionDropName)])
                packetSize = unpacked[1 + len(self.__ActionDropName)]
                if dropName != self.__ActionDropName or packetSize != self.__CommandSize:
                    print str(addr) + " is misaligned"
                    break
                dataSize = unpacked[2 + len(self.__ActionDropName)]
                data = list(unpacked[3 + len(self.__ActionDropName):3 + len(self.__ActionDropName) + dataSize])
                self.__callbackLock.acquire()
                try:
                    self.__callback(data)
                finally:
                    self.__callbackLock.release()
            self.__removeClient(clientSocket)
        clientThread = threading.Thread(None, recvClientMessage, None, [], {})
        clientThread.daemon = True
        clientThread.start()

    def stopserver(self):
        self.__serverRunning = False

    def startserver(self):
        serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # Exit on sig INT and TERM
        serverSocket.bind(("", self.port))
        serverSocket.listen(1)
        def acceptLoop():
            print 'Listening on port ' + str(self.port)
            while self.__serverRunning:
                clientSocket, addr = serverSocket.accept()
                print str(addr) + ' connected' 
                self.__registerClient(clientSocket, addr)
            serverSocket.close()
        serverThread = threading.Thread(None, acceptLoop, None, [], {})
        serverThread.daemon = True
        serverThread.start()

    def dispatch(self, packet):
        dropFormat = self.__sensorDropFormat + "B" * len(packet)
        args = [len(self.__SensorDropName)] + list(self.__SensorDropName) + [len(packet)] + packet
        message = struct.pack(dropFormat, *args)
        for clientSocket in self.__registeredClient:
            try:
                clientSocket.sendall(message)
            except:
                self.__removeClient(clientSocket)

    def setcallback(self, fun):
        self.__callback = fun



class SerialPort(object):
    def __init__(self, filename):
        self.filename = filename
        self.ser = None
        
    def openSerialPort(self):
        chrono = Chrono()
        ser = None
        while ser is None and chrono() < 100:
            try:
                print "Opening " + self.filename + "..."
                ser = serial.Serial(self.filename, 115200, timeout=5.0, parity=serial.PARITY_NONE, rtscts=1)
            except Exception:
                traceback.print_exc()
                time.sleep(7)
        if ser is None:
            print >> sys.stderr, 'Could not open serial port at ' + SerialPort
        self.ser = ser
        return self.ser is not None
    
    def read(self, size):
        return self.ser.read(size)
    
    def sendAndReceive(self, toSend, toExpect):
        self.sendstr(toSend)
        recv = self.ser.read(len(toExpect))
        if recv != toExpect:
            print >> sys.stderr, "error: '" + recv + "' != " + toExpect
        return recv == toExpect
    
    def purgeIFN(self):
        available = self.ser.inWaiting()
        if not available:
            return 0
        return len(self.ser.read(available))
    
    def sendstr(self, data, flush = True):
        self.ser.write(data)
        if flush:
            self.ser.flush()
    
    def send(self, data, flush = True):
        return self.sendstr(struct.pack('B' * len(data), *data), flush)
 
    def close(self):
        self.ser.close()
        self.ser = None
    
    def isOpened(self):
        return self.ser is not None 
      

DataSize = 80
PacketID = 100
Header = [19, 1 + DataSize, PacketID]
PacketSize = len(Header) + DataSize + 1

def alignOnHeader(ser):
    checkedPosition = 0
    errorTime = Chrono()
    timeout = Chrono()
    while checkedPosition < len(Header):
        value = ser.read(1)
        if len(value) == 0:
            continue
        if ord(value) == Header[checkedPosition]:
            checkedPosition += 1
        else:
            checkedPosition = 0;
        if errorTime() > 10:
            ser.purgeIFN()
            print "Packet header cannot be found... just noise on the link"
            errorTime.start()
        if timeout() > 60:
            return False
    ser.read(PacketSize - len(Header))
    return True
        
class Firefly(object):
    def __init__(self, ser):
        self.ser = ser
        
    def initializeFirefly(self):
        if not self.ser.openSerialPort():
            return False
        if self.__dataAvailableOnTheLink():
            if alignOnHeader(self.ser):
                print "Data header found..."
                return True
            print >> sys.stderr, 'No header found: please disconnect the firefly, reset Roomba, reconnect the Firefly and try again'
            return False
        if not self.__configureFirefly():
            return False
        if not self.__configureRobot():
            return False
        print "Start streaming..."
        self.ser.send([148, 1, 100])
        self.ser.close()
        return True
    
    def __configurationRequired(self):
        pass
    
    def __dataAvailableOnTheLink(self):
        self.ser.purgeIFN()
        time.sleep(1)
        if self.ser.purgeIFN() > 0:
            print "Data is available on the link... trying to make sense of it"
            return True
        return False

    def __configureFirefly(self):
        print "Setting up the Firefly..."
        if not self.ser.sendAndReceive("$$$", "CMD\r\n"):
            return False
        if not self.ser.sendAndReceive("U,115k,N\r", "AOK\r\n"):
            return False
        self.ser.sendstr("---\r")
        return True
        
    def __configureRobot(self):
        print "Setting up the robot..."
        dataPurged = True
        timeout = Chrono()
        while dataPurged:
            self.ser.send([128])
            self.ser.send([131])
            self.ser.send([150, 0])
            self.ser.send([139, 0, 255, 255])
            time.sleep(1)
            dataPurged = self.ser.purgeIFN()
            if dataPurged > 0:
                print >> sys.stderr, 'Still receiving data on the link: ' + str(dataPurged)
            if timeout() > 30:
                return False
        timeout.start() 
        print "Testing communication..."
        while not dataPurged:
            self.ser.send([142, PacketID])
            received = self.ser.read(DataSize)
            dataPurged = len(received) == DataSize
            if not dataPurged:
                print "timeout: " + str(len(received)) + " received"
            if timeout() > 30:
                return False  
        print "Communication established"
        return True
    
    
class Robot(object):
    def __init__(self, ser):
        self.server = Server()
        self.server.setcallback(self.__commandRecv)
        self.ser = ser
        self.dockLedOn = True
        self.ledOnTime = Chrono()
        self.pingRobot = True
        self.__headerAligned = False
        
    def __commandRecv(self, data):
        self.pingRobot = False
        self.ser.send(data)

    def __pingRobotIFN(self):
        if self.ledOnTime() > 1 and self.pingRobot:
            if self.dockLedOn:
                self.ser.send([139, 2, 0, 0])
            else:
                self.ser.send([139, 4, 0, 0])
            self.dockLedOn = not self.dockLedOn
            self.ledOnTime.start()
            
    def __readSensorPacket(self):
        checksum = 1
        while checksum != 0:
            data = self.ser.read(PacketSize)
            if len(data) < PacketSize:
                self.__headerAligned = False
                return None                
            packet = struct.unpack("B" * PacketSize, data) 
            if list(packet[0:len(Header)]) != Header:
                self.__headerAligned = False
                return None
            checksum = ((sum(packet[0:-1]) & 0xff) + packet[-1]) & 0xff
        return list(packet[len(Header):])

    def mainLoop(self):
        try:
            if not self.ser.isOpened():
                self.ser.openSerialPort()
            self.server.startserver()
            while True:
                self.__pingRobotIFN()
                if not self.__headerAligned:
                    self.__headerAligned = alignOnHeader(self.ser)
                    if not self.__headerAligned: 
                        break
                packet = self.__readSensorPacket()
                if packet:
                    self.server.dispatch(packet)
        except Exception:
            traceback.print_exc()
        self.server.stopserver()
        self.ser.close()
        
def testServer():        
    server = Server()
    server.startserver()
    while True:
        server.dispatch(list(random.randint(0, 255) for _ in range(80)))
        time.sleep(.2)

def main(serialFilename):
    ser = SerialPort(serialFilename)
    firefly = Firefly(ser)
    if not firefly.initializeFirefly():
        sys.exit(1)
    if not ser.isOpened():
        print "Please wait for reconnection..."
        time.sleep(60)
    Robot(ser).mainLoop()
    
if __name__ == '__main__':
    main("/dev/cu.FireFly-155A-SPP")
