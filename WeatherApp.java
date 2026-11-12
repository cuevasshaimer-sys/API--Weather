
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

// Reference: https://open-meteo.com/en/docs

public class WeatherApp {

    public static String fetch(String endpoint) throws IOException {

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) URI.create(endpoint)
                    .toURL().openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();

            InputStream stream = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (status != 200) {
                String reason = "";

                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        StringBuilder errorBody = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            errorBody.append(line);
                        }

                        reason = errorBody.toString();
                    }
                }

                throw new IOException("HTTP error " + status + ": " + reason);
            }

            StringBuilder response = new StringBuilder();

            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            return response.toString();

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static void main(String[] args) {

        String endpoint = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=7.07"
                + "&longitude=125.61"
                + "&current_weather=true"
                + "&timezone=auto";

        try {
            String jsonResponse = fetch(endpoint);

            JsonObject json = JsonParser.parseString(jsonResponse)
                    .getAsJsonObject();

            JsonObject currentWeather = json.getAsJsonObject("current_weather");

            if (currentWeather == null) {
                System.out.println("Current weather data is not available.");
                return;
            }

            double latitude = json.get("latitude").getAsDouble();
            double longitude = json.get("longitude").getAsDouble();

            String time = currentWeather.get("time").getAsString();
            double temperature = currentWeather.get("temperature").getAsDouble();
            double windspeed = currentWeather.get("windspeed").getAsDouble();
            int winddirection = currentWeather.get("winddirection").getAsInt();
            int weathercode = currentWeather.get("weathercode").getAsInt();

            System.out.println("\n=== Current Weather ===");
            System.out.printf("Location : %.2f, %.2f%n", latitude, longitude);
            System.out.println("Time     : " + time);
            System.out.printf("Temp     : %.1f °C%n", temperature);
            System.out.printf("Wind     : %.1f km/h at %d deg%n",
                    windspeed, winddirection);
            System.out.println("Weather Code : " + weathercode);

        } catch (IOException e) {
            System.out.println("Unable to get weather data: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Unable to read weather data: " + e.getMessage());
        }
    }
}
