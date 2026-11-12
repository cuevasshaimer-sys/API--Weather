
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

// References:
// https://open-meteo.com/en/docs
// https://open-meteo.com/en/docs/geocoding-api
// https://open-meteo.com/en/docs/historical-weather-api

public class WeatherLookup {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int REQUEST_TIMEOUT = 10;

    record Place(String name, String country, double latitude,
                 double longitude) {
    }

    static class WeatherException extends Exception {

        public WeatherException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("================================");
        System.out.println("       WEATHER LOOKUP APP");
        System.out.println("================================");

        while (true) {

            System.out.print("\nEnter city or country (or quit): ");
            String locationInput = scanner.nextLine().trim();

            if (locationInput.equalsIgnoreCase("quit")) {
                System.out.println("Thank you for using Weather Lookup!");
                break;
            }

            if (locationInput.isEmpty()) {
                System.out.println("Please enter a city or country.");
                continue;
            }

            try {
                Place place = findPlace(locationInput);

                System.out.print("Enter date (YYYY-MM-DD): ");
                String dateInput = scanner.nextLine().trim();

                LocalDate date;

                try {
                    date = LocalDate.parse(dateInput);
                } catch (DateTimeParseException e) {
                    System.out.println(
                            "Invalid date. Please use YYYY-MM-DD format.");
                    continue;
                }

                LocalDate today = LocalDate.now();

                if (date.isAfter(today.plusDays(16))) {
                    System.out.println(
                            "Date is too far ahead. Please choose a date within 16 days.");
                    continue;
                }

                showWeather(place, date);

            } catch (WeatherException e) {
                System.out.println("Error: " + e.getMessage());

            } catch (IOException e) {
                System.out.println(
                        "Network error. Please check your internet connection.");

            } catch (JsonSyntaxException e) {
                System.out.println(
                        "The weather service returned invalid JSON data.");

            } catch (Exception e) {
                System.out.println(
                        "Something went wrong: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static Place findPlace(String input)
            throws WeatherException, IOException {

        String cityName = input;
        String expectedCountry = null;

        if (input.contains(",")) {
            String[] parts = input.split(",", 2);
            cityName = parts[0].trim();
            expectedCountry = parts[1].trim();

            if (cityName.isEmpty() || expectedCountry.isEmpty()) {
                throw new WeatherException(
                        "Please enter the location correctly, such as Davao, Philippines.");
            }
        }

        String encodedName = URLEncoder.encode(
                cityName, StandardCharsets.UTF_8);

        String endpoint = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + encodedName
                + "&count=10"
                + "&language=en"
                + "&format=json";

        JsonObject response = getJson(endpoint);

        if (!response.has("results")
                || response.get("results").isJsonNull()) {
            throw new WeatherException(
                    "Place not found. Please check the city or country name.");
        }

        JsonArray results = response.getAsJsonArray("results");

        if (results.size() == 0) {
            throw new WeatherException(
                    "Place not found. Please try another location.");
        }

        JsonObject selectedPlace = null;

        for (JsonElement element : results) {

            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject result = element.getAsJsonObject();

            if (expectedCountry == null) {
                selectedPlace = result;
                break;
            }

            String resultCountry = result.has("country")
                    && !result.get("country").isJsonNull()
                    ? result.get("country").getAsString()
                    : "";

            if (resultCountry.equalsIgnoreCase(expectedCountry)) {
                selectedPlace = result;
                break;
            }
        }

        if (selectedPlace == null) {
            throw new WeatherException(
                    "Place was found, but it does not belong to the country you entered.");
        }

        String name = getString(selectedPlace, "name", "Unknown place");
        String country = getString(selectedPlace, "country", "Unknown country");

        if (!selectedPlace.has("latitude")
                || !selectedPlace.has("longitude")) {
            throw new WeatherException(
                    "The location result does not contain coordinates.");
        }

        double latitude = selectedPlace.get("latitude").getAsDouble();
        double longitude = selectedPlace.get("longitude").getAsDouble();

        System.out.println("Place found: " + name + ", " + country);

        return new Place(name, country, latitude, longitude);
    }

    private static void showWeather(Place place, LocalDate date)
            throws WeatherException, IOException {

        LocalDate today = LocalDate.now();
        LocalDate forecastStart = today.minusDays(7);

        String service;

        if (date.isBefore(forecastStart)) {
            service = "https://archive-api.open-meteo.com/v1/archive";
        } else {
            service = "https://api.open-meteo.com/v1/forecast";
        }

        String endpoint = service
                + "?latitude=" + place.latitude()
                + "&longitude=" + place.longitude()
                + "&start_date=" + date
                + "&end_date=" + date
                + "&daily=weather_code,temperature_2m_max,"
                + "temperature_2m_min,wind_speed_10m_max"
                + "&timezone=auto";

        JsonObject response = getJson(endpoint);

        if (!response.has("daily")
                || !response.get("daily").isJsonObject()) {
            throw new WeatherException(
                    "Daily weather data is not available for this date.");
        }

        JsonObject daily = response.getAsJsonObject("daily");

        String[] requiredFields = {
                "time",
                "weather_code",
                "temperature_2m_max",
                "temperature_2m_min",
                "wind_speed_10m_max"
        };

        for (String field : requiredFields) {
            if (!daily.has(field)
                    || daily.get(field).isJsonNull()
                    || !daily.get(field).isJsonArray()
                    || daily.getAsJsonArray(field).size() == 0) {

                throw new WeatherException(
                        "Daily weather section is missing or incomplete.");
            }
        }

        JsonArray times = daily.getAsJsonArray("time");
        JsonArray weatherCodes = daily.getAsJsonArray("weather_code");
        JsonArray maxTemps = daily.getAsJsonArray("temperature_2m_max");
        JsonArray minTemps = daily.getAsJsonArray("temperature_2m_min");
        JsonArray maxWinds = daily.getAsJsonArray("wind_speed_10m_max");

        String reportedDate = times.get(0).getAsString();
        int weatherCode = weatherCodes.get(0).getAsInt();
        double maxTemp = maxTemps.get(0).getAsDouble();
        double minTemp = minTemps.get(0).getAsDouble();
        double maxWind = maxWinds.get(0).getAsDouble();

        System.out.println("\n=== Weather Report ===");
        System.out.println("Place : " + place.name() + ", "
                + place.country()
                + " (" + String.format("%.2f", place.latitude())
                + ", " + String.format("%.2f", place.longitude()) + ")");
        System.out.println("Date : " + reportedDate);
        System.out.printf("Temperature : %.1f to %.1f deg C%n",
                minTemp, maxTemp);
        System.out.printf("Wind : up to %.1f km/h%n", maxWind);
        System.out.println("Conditions : "
                + getWeatherDescription(weatherCode));
    }

    private static String getWeatherDescription(int code) {

        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Weather code " + code;
        };
    }

    private static JsonObject getJson(String endpoint)
            throws WeatherException, IOException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();

            if (status != 200) {
                String reason = getApiReason(response.body());

                throw new WeatherException(
                        "HTTP status " + status
                        + (reason.isEmpty() ? "" : ": " + reason));
            }

            return JsonParser.parseString(response.body())
                    .getAsJsonObject();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new WeatherException(
                    "The request was interrupted. Please try again.");

        } catch (JsonSyntaxException e) {
            throw e;

        } catch (IllegalStateException e) {
            throw new WeatherException(
                    "The API returned an unexpected response.");
        }
    }

    private static String getApiReason(String body) {

        if (body == null || body.isBlank()) {
            return "";
        }

        try {
            JsonObject errorJson = JsonParser.parseString(body)
                    .getAsJsonObject();

            if (errorJson.has("reason")
                    && !errorJson.get("reason").isJsonNull()) {
                return errorJson.get("reason").getAsString();
            }

            if (errorJson.has("message")
                    && !errorJson.get("message").isJsonNull()) {
                return errorJson.get("message").getAsString();
            }

        } catch (Exception ignored) {
            // The response body may not contain JSON.
        }

        return "";
    }

    private static String getString(
            JsonObject object, String field, String defaultValue) {

        if (object.has(field) && !object.get(field).isJsonNull()) {
            return object.get(field).getAsString();
        }

        return defaultValue;
    }
}
