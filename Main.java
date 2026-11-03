import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class Main {

    static ArrayList<Shelter> shelters = new ArrayList<>();

    static final String CSV_FILE =
            "smart_relocator_demo_shelters_313.csv";

    static final String REPORT_FILE =
            "relocation_report.txt";


    // MAIN METHOD
    public static void main(String[] args) throws IOException {

        shelters = loadShelters(CSV_FILE);

        System.out.println("\n================================");
        System.out.println("      SMART RELOCATOR PLAN");
        System.out.println("================================");

        System.out.println("Shelter records loaded: " + shelters.size());

        if (shelters.isEmpty()) {
            System.out.println("ERROR: No shelter records loaded.");
            return;
        }

        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "8080")
        );

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port), 0
        );


        // 1. DASHBOARD STATISTICS
        server.createContext("/api/stats", exchange -> {

            if (handleOptions(exchange)) return;

            if (!exchange.getRequestMethod().equals("GET")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            sendResponse(exchange, 200, getStatistics());
        });


        // 2. SEARCH SHELTERS
        server.createContext("/api/shelters", exchange -> {

            if (handleOptions(exchange)) return;

            if (!exchange.getRequestMethod().equals("GET")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {

                String query = exchange.getRequestURI().getRawQuery();
                String location = "";

                if (query != null && query.startsWith("location=")) {
                    location = URLDecoder.decode(
                            query.substring(9),
                            StandardCharsets.UTF_8
                    );
                }

                if (location.trim().isEmpty()) {
                    sendError(exchange, 400,
                            "Enter a district or state");
                    return;
                }

                ArrayList<Shelter> results =
                        searchShelters(shelters, location);

                System.out.println(
                        "Shelter search: " + location
                        + " | Results: " + results.size()
                );

                sendResponse(exchange, 200,
                        sheltersToJson(results));

            } catch (Exception e) {

                e.printStackTrace();
                sendError(exchange, 500, "Shelter search failed");
            }
        });


        // 3. ALLOCATE PEOPLE AND CREATE REPORT
        server.createContext("/api/relocate", exchange -> {

            if (handleOptions(exchange)) return;

            if (!exchange.getRequestMethod().equals("POST")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {

                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                String location =
                        extractJsonString(body, "location");

                int people =
                        extractJsonInt(body, "people");

                if (location == null || location.trim().isEmpty()
                        || people <= 0) {

                    sendError(exchange, 400,
                            "Enter a valid district and number of people");
                    return;
                }

                ArrayList<Shelter> localShelters =
                        searchShelters(shelters, location);

                if (localShelters.isEmpty()) {
                    sendError(exchange, 404,
                            "No shelters found for this location");
                    return;
                }

                String result = planRelocation(
                        localShelters,
                        location,
                        people
                );

                System.out.println(
                        "Allocation report created for " + location
                );

                sendResponse(exchange, 200, result);

            } catch (IllegalArgumentException e) {

                sendError(exchange, 400,
                        "Invalid request: " + e.getMessage());

            } catch (Exception e) {

                e.printStackTrace();

                sendError(exchange, 500,
                        "Could not create allocation report");
            }
        });


        // 4. VIEW RELOCATION REPORT
        server.createContext("/api/report", exchange -> {

            if (handleOptions(exchange)) return;

            if (!exchange.getRequestMethod().equals("GET")) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {

                String report = Files.readString(
                        Paths.get(REPORT_FILE),
                        StandardCharsets.UTF_8
                );

                String json =
                        "{\"report\":\"" + jsonEscape(report) + "\"}";

                sendResponse(exchange, 200, json);

            } catch (NoSuchFileException e) {

                sendError(exchange, 404,
                        "No allocation report is available yet");

            } catch (IOException e) {

                sendError(exchange, 500,
                        "Could not read allocation report");
            }
        });


        server.setExecutor(null);
        server.start();

        System.out.println("\nBackend running on port: " + port);
        System.out.println("Keep this server running.");
        System.out.println("================================\n");
    }


    // HANDLE OPTIONS REQUESTS
    static boolean handleOptions(HttpExchange exchange)
            throws IOException {

        if (exchange.getRequestMethod().equals("OPTIONS")) {

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "*");

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Methods",
                    "GET, POST, OPTIONS");

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Headers",
                    "Content-Type");

            exchange.sendResponseHeaders(204, -1);
            exchange.close();

            return true;
        }

        return false;
    }


    // LOAD CSV RECORDS
    static ArrayList<Shelter> loadShelters(String fileName) {

        ArrayList<Shelter> list = new ArrayList<>();

        try (BufferedReader br =
                     new BufferedReader(new FileReader(fileName))) {

            br.readLine();

            String line;
            int row = 1;

            while ((line = br.readLine()) != null) {

                row++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] data = line.split(",", -1);

                if (data.length != 11) {
                    System.out.println(
                            "Skipping invalid CSV row: " + row);
                    continue;
                }

                try {

                    Shelter shelter = new Shelter(
                            data[0].trim(),
                            data[1].trim(),
                            data[2].trim(),
                            data[3].trim(),
                            data[4].trim(),
                            Integer.parseInt(data[5].trim()),
                            Integer.parseInt(data[6].trim()),
                            Integer.parseInt(data[7].trim()),
                            data[8].trim(),
                            data[9].trim(),
                            data[10].trim()
                    );

                    list.add(shelter);

                } catch (NumberFormatException e) {

                    System.out.println(
                            "Invalid number in CSV row: " + row);
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Could not read CSV: " + e.getMessage());
        }

        return list;
    }


    // NORMALIZE LOCATION NAMES
    static String normalizeLocation(String location) {

        if (location == null) {
            return "";
        }

        String result = location.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        if (result.endsWith(" district")) {
            result = result.substring(
                    0,
                    result.length() - " district".length()
            ).trim();
        }

        return result;
    }


    // SEARCH BY DISTRICT OR STATE
    static ArrayList<Shelter> searchShelters(
            ArrayList<Shelter> list,
            String location) {

        ArrayList<Shelter> results = new ArrayList<>();

        String search = normalizeLocation(location);

        for (Shelter s : list) {

            String district = normalizeLocation(s.district);
            String state = normalizeLocation(s.state);

            if (district.equals(search) || state.equals(search)) {
                results.add(s);
            }
        }

        return results;
    }


    // DASHBOARD STATISTICS
    static synchronized String getStatistics() {

        int totalSpaces = 0;
        int fullShelters = 0;

        for (Shelter s : shelters) {

            totalSpaces += Math.max(0, s.availableSpaces);

            if (s.availableSpaces <= 0) {
                fullShelters++;
            }
        }

        return "{"
                + "\"totalShelters\":" + shelters.size() + ","
                + "\"availableSpaces\":" + totalSpaces + ","
                + "\"fullShelters\":" + fullShelters
                + "}";
    }


    // CONVERT SHELTER RECORDS TO JSON
    static String sheltersToJson(ArrayList<Shelter> list) {

        StringBuilder json = new StringBuilder("[");

        boolean first = true;

        for (Shelter s : list) {

            if (!first) {
                json.append(",");
            }

            first = false;

            json.append("{")
                    .append("\"id\":\"")
                    .append(jsonEscape(s.shelterId)).append("\",")

                    .append("\"name\":\"")
                    .append(jsonEscape(s.shelterName)).append("\",")

                    .append("\"district\":\"")
                    .append(jsonEscape(s.district)).append("\",")

                    .append("\"state\":\"")
                    .append(jsonEscape(s.state)).append("\",")

                    .append("\"hazard\":\"")
                    .append(jsonEscape(s.hazardType)).append("\",")

                    .append("\"capacity\":")
                    .append(s.totalCapacity).append(",")

                    .append("\"present\":")
                    .append(s.peoplePresent).append(",")

                    .append("\"available\":")
                    .append(s.availableSpaces).append(",")

                    .append("\"accessibility\":\"")
                    .append(jsonEscape(s.accessibility)).append("\",")

                    .append("\"medical\":\"")
                    .append(jsonEscape(s.medicalSupport)).append("\",")

                    .append("\"dataStatus\":\"")
                    .append(jsonEscape(s.dataStatus)).append("\"")

                    .append("}");
        }

        json.append("]");

        return json.toString();
    }


    // PLAN ALLOCATION AND GENERATE REPORT
    static synchronized String planRelocation(
            ArrayList<Shelter> localShelters,
            String location,
            int people) throws IOException {

        int remaining = people;
        int totalAssigned = 0;

        Set<String> districtsUsed = new LinkedHashSet<>();

        ArrayList<String> allocations = new ArrayList<>();

        // Store planned allocations before updating shelter records.
        Map<Shelter, Integer> plannedAllocations =
                new LinkedHashMap<>();


        // CALCULATE ALLOCATION
        for (Shelter s : localShelters) {

            if (remaining <= 0) {
                break;
            }

            int spaces = Math.max(0, s.availableSpaces);
            int assigned = Math.min(remaining, spaces);

            if (assigned > 0) {

                plannedAllocations.put(s, assigned);

                allocations.add("{"
                        + "\"id\":\""
                        + jsonEscape(s.shelterId) + "\","

                        + "\"name\":\""
                        + jsonEscape(s.shelterName) + "\","

                        + "\"district\":\""
                        + jsonEscape(s.district) + "\","

                        + "\"people\":" + assigned
                        + "}");

                districtsUsed.add(s.district);

                remaining -= assigned;
                totalAssigned += assigned;
            }
        }


        // CREATE TEXT REPORT
        try (PrintWriter report =
                     new PrintWriter(
                             new BufferedWriter(
                                     new FileWriter(REPORT_FILE)))) {

            report.println("SMART RELOCATOR PLAN");
            report.println("ALLOCATION REPORT");

            report.println("Generated: " +
                    LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern(
                                    "dd-MM-yyyy HH:mm:ss")));

            report.println("Requested people: " + people);
            report.println("Selected location: " + location);

            report.println("--------------------------------");


            for (Map.Entry<Shelter, Integer> entry
                    : plannedAllocations.entrySet()) {

                Shelter s = entry.getKey();
                int assigned = entry.getValue();

                report.println("\nShelter: " + s.shelterName);
                report.println("Shelter ID: " + s.shelterId);
                report.println("District: " + s.district);
                report.println("People allocated: " + assigned);
                report.println("Available spaces after allocation: "
                        + (s.availableSpaces - assigned));

                report.println("--------------------------------");
            }


            // FINAL REPORT DETAILS
            report.println("\nTotal people allocated: " + totalAssigned);
            report.println("People remaining: " + remaining);

            report.println("Districts used: " +
                    (districtsUsed.isEmpty()
                            ? "None"
                            : String.join(", ", districtsUsed)));

            report.println(remaining > 0
                    ? "Result: Sufficient capacity was not available."
                    : "Result: All requested people were allocated.");

            report.println(
                    "\nNote: Synthetic demo data only. "
                            + "Not for real emergency decisions.");

            report.flush();

            if (report.checkError()) {
                throw new IOException(
                        "Error while writing allocation report.");
            }
        }


        // UPDATE SHELTER RECORDS AFTER REPORT IS SAVED
        for (Map.Entry<Shelter, Integer> entry
                : plannedAllocations.entrySet()) {

            Shelter s = entry.getKey();
            int assigned = entry.getValue();

            s.availableSpaces -= assigned;
            s.peoplePresent += assigned;
        }


        // RETURN ALLOCATION DETAILS TO FRONTEND
        return "{"
                + "\"requested\":" + people + ","
                + "\"assigned\":" + totalAssigned + ","
                + "\"remaining\":" + remaining + ","
                + "\"districts\":\""
                + jsonEscape(String.join(", ", districtsUsed)) + "\","
                + "\"allocations\":["
                + String.join(",", allocations)
                + "]"
                + "}";
    }


    // EXTRACT STRING FROM JSON REQUEST
    static String extractJsonString(String json, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {

            return matcher.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        return null;
    }


    // EXTRACT INTEGER FROM JSON REQUEST
    static int extractJsonInt(String json, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*(-?\\d+)"
        );

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        throw new IllegalArgumentException(
                "Missing or invalid number: " + key);
    }


    // MAKE STRINGS SAFE FOR JSON
    static String jsonEscape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }


    // SEND JSON RESPONSE
    static void sendResponse(
            HttpExchange exchange,
            int status,
            String response) throws IOException {

        byte[] bytes =
                response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    // SEND ERROR RESPONSE
    static void sendError(
            HttpExchange exchange,
            int status,
            String message) throws IOException {

        String response =
                "{\"error\":\"" + jsonEscape(message) + "\"}";

        sendResponse(exchange, status, response);
    }
}