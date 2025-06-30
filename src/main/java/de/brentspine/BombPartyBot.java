package de.brentspine;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Scanner;

public class BombPartyBot
{

    private static final HashMap<String, SocketWrapper> rooms = new HashMap<>();
    private static String startingRoom;

    public static void main( String[] args ) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a starting room ID: ");
        String startingRoom = scanner.nextLine().trim();
        if(startingRoom.length() != 4) {
            System.out.println("Invalid room ID! It must be exactly 4 characters long. Restart the bot and try again.");
            return;
        }

        String serverUrl = getServerUrl(startingRoom);
        SocketWrapper socketWrapper = new SocketWrapper(startingRoom, "BBot", "data/english.txt", serverUrl);
        socketWrapper.setJoinReason("Developer Started Bot with this room as starting point");
        rooms.put(startingRoom, socketWrapper);
    }

    public static String createSocketWrapper(String room, String nickname, String fileName, String spawner, String token) throws InterruptedException {
        if (rooms.containsKey(room)) {
            return "I am already in that lobby!";
        }
        String serverUrl = getServerUrl(room);
        SocketWrapper socketWrapper = new SocketWrapper(room, nickname, fileName, serverUrl, token);
        socketWrapper.setJoinReason("User " + spawner + " has spawned me");
        rooms.put(room, socketWrapper);
        return "I am joining the lobby https://jklm.fun/" + room + " as " + nickname + ". Your name has been given as a join reason. This may take a few seconds.";
    }

    public static void removeSocketWrapper(String room) {
        if(!rooms.containsKey(room)) return;
        SocketWrapper socketWrapper = rooms.get(room);
        socketWrapper.close();
        rooms.remove(room);
    }

    public static String startRoom(String userToken, String name, boolean publicRoom, String botName, String spawner) {
        try {
            URL url = new URL("https://jklm.fun/api/startRoom");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "*/*");
            connection.setDoOutput(true);

            // Create JSON body
            String jsonBody = String.format(
                    "{\"name\":\"%s\",\"isPublic\":%b,\"gameId\":\"bombparty\",\"creatorUserToken\":\"%s\"}",
                    name, publicRoom, userToken
            );

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }

                String responseStr = response.toString();
                if(responseStr.isEmpty()) {
                    return "Room created successfully! I couldn't determine the room ID... Find a room with the name \"" + name + "\" and join it. Then use -joinroom on me";
                }

                JSONObject responseJson;
                try {
                    responseJson = new JSONObject(responseStr);
                    String roomId = responseJson.getString("roomCode");
                    createSocketWrapper(roomId, botName, "data/english.txt", "Room Creation by " + spawner, userToken);
                    SocketWrapper socketWrapper = rooms.get(roomId);
                    socketWrapper.isCreator = true;
                    socketWrapper.willJoin = true;
                    return "Room created successfully! You can join it at https://jklm.fun/" + roomId + ". I will join it as " + botName + ".";
                } catch (Exception e) {
                    return "Room created successfully but couldn't parse room data: " + responseStr;
                }
            } else {
                return "Failed to create room: " + responseCode;
            }
        } catch (Exception e) {
            return "Error creating room: " + e.getMessage();
        }
    }

    public static String getServerUrl(String roomCode) {
        try {
            URL url = new URL("https://jklm.fun/api/joinRoom");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "*/*");
            connection.setDoOutput(true);

            // Create JSON body
            String jsonBody = String.format("{\"roomCode\":\"%s\"}", roomCode);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                JSONObject responseJson = getJsonObjectFromConnection(connection);
                System.out.println("Identified server URL for room " + roomCode + ": " + responseJson.getString("url"));
                return responseJson.getString("url").replace("https://", "");
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static JSONObject getJsonObjectFromConnection(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        JSONObject responseJson = new JSONObject(response.toString());
        return responseJson;
    }

    public static HashMap<String, SocketWrapper> getRooms() {
        return rooms;
    }

    public static String getStartingRoom() {
        return startingRoom;
    }
}
