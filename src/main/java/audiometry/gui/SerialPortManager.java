package audiometry.gui;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SerialPortManager {

    private SerialPort activePort;
    private Consumer<String> onMessageReceived;

    public SerialPortManager() {
    }

    /**
     * Gelen veriyi işlemek için geri çağırma fonksiyonu ayarlar.
     */
    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    /**
     * Sistemdeki tüm COM/ttyUSB portlarının adlarını döndürür.
     */
    public static String[] getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] portNames = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portNames[i] = ports[i].getSystemPortName();
        }
        return portNames;
    }

    /**
     * Belirtilen isimdeki porta bağlanmayı dener.
     */
    public boolean connect(String portName) {
        if (activePort != null && activePort.isOpen()) {
            disconnect();
        }

        activePort = SerialPort.getCommPort(portName);
        activePort.setBaudRate(9600); // Standart baud rate
        activePort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

        if (activePort.openPort()) {
            setupDataListener();
            return true;
        }
        return false;
    }

    /**
     * Açık olan portu kapatır.
     */
    public void disconnect() {
        if (activePort != null && activePort.isOpen()) {
            activePort.removeDataListener();
            activePort.closePort();
        }
    }

    /**
     * Bağlantı durumunu döner.
     */
    public boolean isConnected() {
        return activePort != null && activePort.isOpen();
    }

    /**
     * Porta komut gönderir (Örn: "PLAY 1000 30").
     */
    public void sendCommand(String command) {
        if (isConnected()) {
            String fullCommand = command + "\n";
            byte[] bytes = fullCommand.getBytes();
            activePort.writeBytes(bytes, bytes.length);
        }
    }

    /**
     * Seri porttan gelen verileri dinler.
     */
    private void setupDataListener() {
        activePort.addDataListener(new SerialPortDataListener() {
            private StringBuilder buffer = new StringBuilder();

            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;

                byte[] newData = new byte[activePort.bytesAvailable()];
                int numRead = activePort.readBytes(newData, newData.length);
                String receivedText = new String(newData, 0, numRead);
                
                buffer.append(receivedText);
                
                // Gelen veriyi satır satır oku
                int newlineIndex;
                while ((newlineIndex = buffer.indexOf("\n")) != -1) {
                    String line = buffer.substring(0, newlineIndex).trim();
                    buffer.delete(0, newlineIndex + 1);
                    
                    if (!line.isEmpty() && onMessageReceived != null) {
                        onMessageReceived.accept(line);
                    }
                }
            }
        });
    }
}
