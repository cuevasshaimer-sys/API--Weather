
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

// Reference: https://open-meteo.com/en/docs

public class RawFetch {

    public static void main(String[] args) {

        String endpoint = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=7.07"
                + "&longitude=125.61"
                + "&current_weather=true"
                + "&timezone=auto";

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) URI.create(endpoint)
                    .toURL().openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            System.out.println("HTTP status: " + status);

            InputStream stream = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
