import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SensorHandler {
    private static final int GATEWAY_TCP_PORT = 3333;
    private static final int GATEWAY_UDP_PORT = 5556;
    private static final int SERVER_PORT = 8080;

    private ArrayList<Double> temperatureData = new ArrayList<>();
    private ArrayList<Double> humidityData = new ArrayList<>();
    private Double lastHumidityValue = null;
    private static final Random random = new Random();

    private boolean temperatureSensorActive = true;
    private boolean humiditySensorActive = true;
    private boolean serverHandshakeSuccessful = false;


    private long temperatureLastReceived = System.currentTimeMillis();
    private long humidityLastReceived = System.currentTimeMillis();


    private Logger temperatureLogger;
    private Logger humidityLogger;
    private Logger gatewayLogger;

    public SensorHandler() {
        try {
            temperatureLogger = Logger.getLogger("TemperatureLog");
            FileHandler temperatureFileHandler = new FileHandler("temperature_log.txt");
            temperatureLogger.addHandler(temperatureFileHandler);
            SimpleFormatter temperatureFormatter = new SimpleFormatter();
            temperatureFileHandler.setFormatter(temperatureFormatter);

            humidityLogger = Logger.getLogger("HumidityLog");
            FileHandler humidityFileHandler = new FileHandler("humidity_log.txt");
            humidityLogger.addHandler(humidityFileHandler);
            SimpleFormatter humidityFormatter = new SimpleFormatter();
            humidityFileHandler.setFormatter(humidityFormatter);

            gatewayLogger = Logger.getLogger("GatewayLog");
            FileHandler aliveFileHandler = new FileHandler("gateway_log.txt");
            gatewayLogger.addHandler(aliveFileHandler);
            SimpleFormatter aliveFormatter = new SimpleFormatter();
            aliveFileHandler.setFormatter(aliveFormatter);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SensorHandler sensorHandler = new SensorHandler();
        sensorHandler.start();
    }

    public void start() {
        Thread temperatureSensorThread = new Thread(this::temperatureSensor);
        Thread humiditySensorThread = new Thread(this::humiditySensor);
        Thread gatewayThread = new Thread(this::gateway);
        Thread serverThread = new Thread(this::server);
        Thread aliveThread = new Thread(this::sendAlive);

        temperatureSensorThread.start();
        humiditySensorThread.start();
        gatewayThread.start();
        serverThread.start();
        aliveThread.start();
    }

    private void temperatureSensor() {
        try (Socket tcpSocket = new Socket("localhost", GATEWAY_TCP_PORT);
             PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(), true)) {
            while (temperatureSensorActive) {
                double temperatureValue = random.nextDouble() * 10 + 20;
                String timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm").format(new Date());
                String message = "TEMPERATURE|" + temperatureValue + "|" + timestamp;
                out.println(message);
                temperatureData.add(temperatureValue);
                this.temperatureLastReceived = System.currentTimeMillis();
                temperatureLogger.info("Temperature Sensor - Value: " + String.format("%.2f", temperatureValue) + " - Timestamp: " + timestamp);
                Thread.sleep(1000);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void humiditySensor() {
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            while (humiditySensorActive) {
                double humidityValue = random.nextDouble() * 50 + 40;
                if (humidityValue > 80) {
                    String timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm").format(new Date());
                    String message = "HUMIDITY|" + humidityValue + "|" + timestamp;
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                            InetAddress.getByName("localhost"), GATEWAY_UDP_PORT);
                    udpSocket.send(packet);
                    humidityData.add(humidityValue);
                    lastHumidityValue = humidityValue;
                    this.humidityLastReceived = System.currentTimeMillis();
                    humidityLogger.info("Humidity Sensor - Value: " + String.format("%.2f", humidityValue) + " - Timestamp: " + timestamp);  }
                Thread.sleep(1000);
            }
            this.humiditySensorActive = false;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendAlive() {
        try (DatagramSocket udpSocket = new DatagramSocket()) {

            while (humiditySensorActive) {
                String timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm").format(new Date());
                String message = "ALIVE|" + timestamp;
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                        InetAddress.getByName("localhost"), GATEWAY_UDP_PORT);
                udpSocket.send(packet);
                gatewayLogger.info("Humidity Sensor - ALIVE message sent at: " + timestamp);
                Thread.sleep(3000);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean performHandshakeWithServer() throws IOException, InterruptedException {
        // Placeholder for handshake logic
        // Simulate a successful handshake by sleeping for 1 second
        Thread.sleep(0);
        return true;
    }

    private void gateway() {
        try (ServerSocket tcpServer = new ServerSocket(GATEWAY_TCP_PORT);
             DatagramSocket udpServer = new DatagramSocket(GATEWAY_UDP_PORT)) {

            // Attempt handshake with the server
            if (performHandshakeWithServer()) {
                serverHandshakeSuccessful = true;
                gatewayLogger.info("Handshake with Server successful");
            } else {
                gatewayLogger.warning("Handshake with Server failed");
                // Handle failure (e.g., exit the method, throw an exception, etc.)
                return;
            }

            while (true) {

                if (!temperatureSensorActive) {
                    System.out.println("Gateway - TEMP SENSOR OFF");
                }


                if (!humiditySensorActive) {
                    System.out.println("Gateway - HUMIDITY SENSOR OFF");
                }


                Socket tcpClient = tcpServer.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(tcpClient.getInputStream()));
                String tcpData = in.readLine();
                processTcpData(tcpData);
                temperatureLastReceived = System.currentTimeMillis();

                // Handle UDP communication
                byte[] buffer = new byte[1024];
                DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
                udpServer.receive(udpPacket);
                String udpData = new String(udpPacket.getData(), 0, udpPacket.getLength());
                processUdpData(udpData);
                humidityLastReceived = System.currentTimeMillis();

            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void processTcpData(String data) {
        String[] parts = data.split("\\|");
        String sensorType = parts[0];
        if (sensorType.equals("TEMPERATURE")) {
            double temperatureValue = Double.parseDouble(parts[1]);
            String timestamp = parts[2];
            temperatureData.add(temperatureValue);
            temperatureLogger.info("Gateway - Received Temperature Data: " + temperatureValue + ", Timestamp: " + timestamp);
        }
    }

    private void processUdpData(String data) {
        String[] parts = data.split("\\|");
        String sensorType = parts[0];
        if (sensorType.equals("HUMIDITY")) {
            double humidityValue = Double.parseDouble(parts[1]);
            String timestamp = parts[2];
            humidityData.add(humidityValue);
            lastHumidityValue = humidityValue;
            humidityLogger.info("Gateway - Received Humidity Data: " + humidityValue + ", Timestamp: " + timestamp);
        }
    }

    private void server() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getHtmlHeader(String title) {
        return "<html><head><title>" + title + "</title>" +
                "<style>" +
                "body { background-color: #D9CBE0; color: #000000; font-family: 'Arial', sans-serif; text-align: center; }" +
                "h2 { color: #0066cc; }" +
                "hr { border: 1px solid #000000; width: 400px;}" +
                "</style>" +
                "</head><body>";
    }

    private String getHtmlFooter() {
        return "</body></html>";
    }


    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String request = in.readLine();
            String path = request.split(" ")[1];

            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println("HTTP/1.1 200 OK");
            out.println("Content-type: text/html");
            out.println();

            if ("/temperature".equals(path)) {
                out.println(getHtmlHeader("Temperature Data") +
                        "<h2>Temperature Data</h2><hr>");
                for (int i = 0; i < temperatureData.size(); i++) {
                    Double temp = temperatureData.get(i);
                    long timestamp = System.currentTimeMillis() - (temperatureData.size() - i - 1) * 1000;

                    // Format the timestamp with the desired components
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm / EEE / dd MMM");
                    String formattedTimestamp = sdf.format(new Date(timestamp));

                    out.println("Temperature: " + String.format("%.2f", temp) +
                            " / " + formattedTimestamp + "<br><hr>");
                }
                out.println(getHtmlFooter());

            }  else if ("/humidity".equals(path)) {
                out.println(getHtmlHeader("Humidity Data") +
                        "<h2>Humidity Data</h2><hr>");
                for (int i = 0; i < humidityData.size(); i++) {
                    Double temp = humidityData.get(i);
                    long timestamp = System.currentTimeMillis() - (humidityData.size() - i - 1) * 1000;

                    // Format the timestamp with the desired components
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm / EEE / dd MMM");
                    String formattedTimestamp = sdf.format(new Date(timestamp));

                    out.println("Humidity: " + String.format("%.2f", temp) +
                            " / " + formattedTimestamp + "<br><hr>");
                }
                out.println("</body></html>");

            } else if ("/gethumidity".equals(path)) {
                out.println(getHtmlHeader("Last Humidity Value") +
                        "<h2>Last Humidity Value</h2><hr>");
                if (lastHumidityValue != null) {
                    out.println("Last Humidity Value: " + String.format("%.2f", lastHumidityValue) + "<br><hr>");
                } else {
                    out.println("No humidity data available<br><hr>");
                }
                out.println(getHtmlFooter());

            } else {
                out.println(getHtmlHeader("404 Not Found") +
                        "<h1>404 Not Found</h1></body></html>");
            }

            clientSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
