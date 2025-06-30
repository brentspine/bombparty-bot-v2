package de.brentspine;

import de.brentspine.messages.GameMessageType;
import de.brentspine.messages.MessageType;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controls sending commands to the server and receiving responses.
 */
public class SocketController {

    private static final int MAX_TYPE_TIMEOUT = 65; // Average time in milliseconds to wait for a message type response
    private static final int MIN_TYPE_TIMEOUT = 30; // Minimum time in milliseconds to wait for a message type response
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private boolean closed = false;

    private SocketWrapper parentWrapper;

    private boolean isChatController;

    private String serverUri;
    private WebSocketClient client;
    private String latestSid;
    private String token;
    private Auth auth;
    private String room;
    private String nickname;
    private String lastWordEnter;
    private int selfPeerId;
    private JSONObject setupData;

    public SocketController(String serverUri) {
        this.serverUri = serverUri;
        this.auth = null;
        this.isChatController = true;
        connect();
    }

    public SocketController(String serverUri, Auth auth) {
        this.serverUri = serverUri;
        this.auth = auth;
        this.isChatController = false;
        connect();
    }

    public SocketController(String serverUri, String latestToken, boolean isChatController) {
        this.serverUri = serverUri;
        this.token = latestToken;
        this.isChatController = isChatController;
        connect();
    }

    public SocketController(String serverUri, String latestToken) {
        this(serverUri, latestToken, false);
    }

    public void close() {
        if(client == null) return;
        client.close();
        EXECUTOR.shutdown();
    }

    private void connect() {
        if(closed) return;
        this.client = new WebSocketClient(URI.create(serverUri)) {

            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                handleOpen(serverHandshake);
            }

            @Override
            public void onMessage(String s) {
                try {
                    handleMessage(s);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                handleClose(i, s, b);
            }

            @Override
            public void onError(Exception e) {
                handleError(e);
            }
        };
        client.connect();
    }

    public void sendMessage(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
            System.out.println("Sent: " + message);
        } else {
            System.err.println("WebSocket is not open. Cannot send message: " + message);
        }
    }

    public void send(String message) {
        sendMessage(message);
    }

    private void handleOpen(ServerHandshake serverHandshake) {
        System.out.println("Connected to server: " + serverHandshake.getHttpStatusMessage());
    }

    private void handleMessage(String message) throws InterruptedException {
        System.out.println("Received message: " + message);
        MessageType messageType = getMessageType(message);
        if(messageType.getCode() < 0) return;
        message = message.replaceFirst("^\\d+", "");

        switch (messageType) {
            case INIT:
                send("40");
                break;
            case PING_IN:
                send("3");
                break;
            case AUTH:
                JSONObject json = new JSONObject(message);
                handleAuth(json);
                if(room != null) {
                    joinRoom(room, (nickname != null) ? nickname : "BBot" + (int) (Math.random() * 1000));
                }
                break;
            case GAME:
                handleGameMessage(message);
                break;
            case RECEIVE_CHATTER_PROFILES:
                parentWrapper.updatePeersJson(new JSONArray(message).getJSONArray(0));
                break;
        }
    }

    private void handleGameMessage(String message) throws InterruptedException {
        JSONArray jsonArray = new JSONArray(message);
        GameMessageType gameMessageType = GameMessageType.fromString(jsonArray.getString(0));
        if(gameMessageType == GameMessageType.UNKNOWN) return;
        int peerId;
        switch (gameMessageType) {
            case CHATTER_ADDED:
                getChattersProfiles();
                if(parentWrapper.isCreator) sendChatMessage("Hello " + jsonArray.getJSONObject(1).getString("nickname") + ", welcome to my lobby! I'm a friendly bot that plays against you! Type -help for help or -commands for a list of commands 🤖 Beep Boop!");
                break;
            case JOIN_GAME:
                System.out.println("A peer joined the game.");
                break;
            case SETUP:
                System.out.println("Game setup received.");
                this.setupData = jsonArray.getJSONObject(1);
                this.selfPeerId = setupData.optInt("selfPeerId");
                break;
            case SET_PLAYER_WORD:
                this.lastWordEnter = jsonArray.getString(2);
                break;
            case CORRECT_WORD:
                peerId = jsonArray.getJSONObject(1).getInt("playerPeerId");
                if(peerId == selfPeerId) {
                    JSONObject bonusLetters = jsonArray.getJSONObject(1).getJSONObject("bonusLetters");
                    parentWrapper.wordLoader.setAlphabetFromJson(bonusLetters);
                    System.out.println("Updated: " + bonusLetters);
                    break;
                }
                System.out.println("Peer " + jsonArray.getJSONObject(1).getInt("playerPeerId") + " correct with " + this.lastWordEnter);
                parentWrapper.wordLoader.addNewWord(this.lastWordEnter);
                break;
            case CHAT:
                JSONObject peer = jsonArray.getJSONObject(1);
                String chatMessage = jsonArray.getString(2);
                handleChatMessage(peer, chatMessage);
                break;
            case SET_START_TIME:
                /*System.out.println("Reset detected.");
                parentWrapper.wordLoader.reset();
                if(!parentWrapper.willJoin()) {
                    System.out.println("Not joining");
                    break;
                }
                parentWrapper.joinRound();
                // Execute join after 1s
                Runnable joinAfterReset = new Runnable() {
                    @Override
                    public void run() {
                        parentWrapper.joinRound();
                    }
                };
                EXECUTOR.schedule(joinAfterReset, 1, TimeUnit.SECONDS);
                break;*/
                break;
            case CLEAR_USED_WORDS:
                break;
            case SET_MILESTONE:
                JSONObject milestone = jsonArray.getJSONObject(1);
                String name = milestone.getString("name");
                switch(name) {
                    case "round":
                        if(milestone.getInt("currentPlayerPeerId") == selfPeerId) {
                            String syllable = milestone.getString("syllable");
                            String word = parentWrapper.wordLoader.findWord(syllable);
                            typeWordWithDelay(word, syllable);
                        }
                        break;
                    case "seating":
                        if(milestone.has("lastRound") && milestone.getJSONObject("lastRound").has("winner")) {
                            JSONObject winner = milestone.getJSONObject("lastRound").getJSONObject("winner");
                            String winnerName = winner.getString("nickname");
                            int id = winner.getInt("peerId");
                            if(id == selfPeerId)    sendChatMessage("Yay! I won 🤖🏆 Let's play again! (Or use -leave to stop me from joining)");
                            else                    sendChatMessage("Congratulations to " + winnerName + " on winning this round 👑");
                        }
                        if(!parentWrapper.willJoin()) break;
                        parentWrapper.joinRound();
                        break;
                }
                break;
            //case FAIL_WORD:
            case NEXT_TURN:
                peerId = jsonArray.getInt(1);
                if(peerId != selfPeerId) return;
                String syllable = jsonArray.getString(2);
                String word = parentWrapper.wordLoader.findWord(syllable);
                typeWordWithDelay(word, syllable);
                /*StringBuilder currentEntry = new StringBuilder();
                for(int i = 0; i < word.length(); i++) {
                    currentEntry.append(word.charAt(i));
                    Thread.sleep(MIN_TYPE_TIMEOUT, MAX_TYPE_TIMEOUT);
                    setWord(currentEntry.toString(), false);
                }
                setWord(word, true);*/
                break;
            default:
                System.out.println("Recognized as: " + gameMessageType.getType());
                break;
        }
    }

    public void joinRound() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("joinRound");
        send(MessageType.GAME.getCode() + jsonArray.toString());
    }
    public void quitRound() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("leaveRound");
        send(MessageType.GAME.getCode() + jsonArray.toString());
    }

    private void typeWordWithDelay(String word, String syllable) {
        AtomicInteger index = new AtomicInteger(0);
        StringBuilder currentEntry = new StringBuilder();
        Runnable typeNextChar = new Runnable() {
            @Override
            public void run() {
                int i = index.getAndIncrement();
                if (i < word.length()) {
                    currentEntry.append(word.charAt(i));
                    setWord(currentEntry.toString(), false);
                    EXECUTOR.schedule(this, randomNum(MIN_TYPE_TIMEOUT, MAX_TYPE_TIMEOUT), TimeUnit.MILLISECONDS);
                } else {
                    setWord(word, true);
                    if(word.equals("/suicide")) {
                        Peer meowbot = parentWrapper.getPeerById(0);
                        if(meowbot == null) {
                            sendChatMessage("That was awkward... I don't know any words for that syllable");
                            return;
                        }
                        sendChatMessage("Huh, I need to learn more words... Let me ask for help from the all-knowing 😸");
                        Runnable askForHelp = new Runnable() {
                            @Override
                            public void run() {
                                sendChatMessage(".c " + syllable);
                            }
                        };
                        EXECUTOR.schedule(askForHelp, 1000, TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        EXECUTOR.schedule(typeNextChar, 0, TimeUnit.MILLISECONDS);
    }

    private int randomNum(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }

    private void setWord(String word, boolean enter) {
        if(word == null || word.isEmpty()) return;
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("setWord");
        jsonArray.put(word);
        jsonArray.put(enter); // true if we enter it, false if we just set it
        send(MessageType.GAME.getCode() + jsonArray.toString());
    }

    private void handleChatMessage(JSONObject peer, String chatMessage) throws InterruptedException {
        Peer peerWrapper = Peer.fromJson(peer);
        Auth peerAuth = null;
        try {
            peerAuth = Auth.fromJson(peer.optJSONObject("auth", null));
        } catch (Exception ignored) {

        }
        if(!chatMessage.startsWith("-")) {
            // Check if peer["auth"] exists, is not null and an instance of JSON object
            if (!(peer.has("auth") && peer.get("auth") instanceof JSONObject)) return;
            JSONObject auth = peer.getJSONObject("auth");
            // Check whether player is meowbot
            if(!auth.getString("username").equalsIgnoreCase("meowbotjklm") || !auth.getString("service").equalsIgnoreCase("twitch")) return;
            String patternString = "^\\[(\\d+)]\\s.*matching\\s\"(.*?)\":\\s(.+)";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(chatMessage);
            if(!matcher.find()) return;

            int totalWordCount = Integer.parseInt(matcher.group(1));
            String syllable = matcher.group(2);
            String wordGroup = matcher.group(3);
            for(String word : wordGroup.split(",")) {
                parentWrapper.wordLoader.addNewWord(word);
            }
            return;
        }
        chatMessage = chatMessage.substring(1).trim();
        String[] args = chatMessage.split(" ");

        int added;
        switch (args[0].toLowerCase()) {
            case "farm":
                if(!handleOwnerOnly(peerAuth, "farm")) return;
                if(args.length < 2) {
                    sendChatMessage("Usage: -farm <start|stop> [syllable]");
                    return;
                }
                if(args[1].equalsIgnoreCase("stop")) {
                    parentWrapper.isFarming = false;
                    sendChatMessage("Stopped farming words.");
                    break;
                }
                if(!args[1].equalsIgnoreCase("start")) {
                    sendChatMessage("Usage: -farm <start|stop> <random> [syllable]");
                    return;
                }
                parentWrapper.isFarming = true;
                if(args.length < 3) {
                    parentWrapper.randomFarming = true;
                    sendChatMessage("Started farming words with random syllables.");
                    break;
                }
                parentWrapper.randomFarming = false;
                parentWrapper.lastFarmedSyllable = args[2];
                sendChatMessage("Started farming words with syllable: " + parentWrapper.lastFarmedSyllable);
                break;
            case "hello":
                sendChatMessage("Hello " + peer.getString("nickname") + "!");
                break;
            case "save":
                if(!handleOwnerOnly(peerAuth, "save")) return;
                added = parentWrapper.wordLoader.saveWords();
                if(added < 0) sendChatMessage("No words to save");
                else sendChatMessage(added+" words saved successfully!");
                break;
            case "add":
                if(!handleOwnerOnly(peerAuth, "add")) return;
                if(args.length < 2) {
                    sendChatMessage("Usage: -add <word list>");
                    return;
                }
                added = 0;
                for(int i = 1; i < args.length; i++) {
                    String word = args[i];
                    if(parentWrapper.wordLoader.addNewWord(word)) added++;
                }
                sendChatMessage("Added " + added + " words successfully (+"+(args.length-1-added)+" skipped)!");
                break;
            case "willjoin":
                if(!parentWrapper.willJoin()) sendChatMessage("I will not join games automatically, type -join to enable joining.");
                else                          sendChatMessage("I will join games automatically when they start. type -leave to disable joining.");
                break;
            case "join":
                if(!handleModOrHigher(peer, "join")) return;
                parentWrapper.allowJoin();
                parentWrapper.joinRound();
                sendChatMessage("I will now join games when they start. Type -leave to stop joining.");
                break;
            case "joinroom":
                if(!handleAuthRequired(peer, "join room")) return;
                if(args.length < 2) {
                    sendChatMessage("Usage: -joinroom <roomId> [nickname]");
                    return;
                }
                String nickname = "BBot";
                if(args.length > 2) {
                    if(!handleOwnerOnly(peerAuth, "join room with nickname")) return;
                    nickname = args[2];
                    if(nickname.length() < 3 || nickname.length() > 20) {
                        sendChatMessage("Nickname must be between 3 and 20 characters long.");
                        return;
                    }
                }
                String roomId = args[1].toUpperCase().trim();
                if(roomId.length() != 4) {
                    sendChatMessage("Room ID must be exactly 4 characters long.");
                    return;
                }
                String message = BombPartyBot.createSocketWrapper(roomId, nickname, Files.ENGLISH.getFileName(), peerWrapper.getNickname(), generateToken());
                sendChatMessage(message);
                break;
            case "leaveroom":
            case "quitroom":
                if(!handleAuthRequired(peer, "quit room")) return;
                BombPartyBot.removeSocketWrapper(parentWrapper.getRoom());
                sendChatMessage("I have left the room " + parentWrapper.getRoom() + ". You can let the bot rejoin by typing -joinroom in a game I am in. (Maybe check in https://jklm.fun/"+BombPartyBot.getStartingRoom()+")");
                break;
            case "create":
                if(!handleAuthRequired(peer, "create room")) return;
                String roomName = peerWrapper.getNickname() + "'s BBot Room";
                String botName = "BBot";
                boolean isPublic = true;
                if(args.length >= 2 && !args[1].equals("d") && !args[1].equalsIgnoreCase("default")) {
                    roomName = args[1];
                    if(roomName.length() < 3 || roomName.length() > 20) {
                        sendChatMessage("Room name must be between 3 and 20 characters long.");
                        return;
                    }
                }
                if(args.length >= 3 && !args[2].equals("d") && !args[2].equalsIgnoreCase("default")) {
                    botName = args[2];
                    if(botName.length() < 3 || botName.length() > 20) {
                        sendChatMessage("Bot name must be between 3 and 20 characters long.");
                        return;
                    }
                }
                if(args.length >= 4) {
                    String publicOrPrivate = args[3].toLowerCase();
                    if(publicOrPrivate.equals("public")) {
                        isPublic = true;
                    } else if(publicOrPrivate.equals("private")) {
                        isPublic = false;
                    } else {
                        sendChatMessage("Usage: -create <roomname> <botname> <public|private>");
                        return;
                    }
                }
                sendChatMessage(BombPartyBot.startRoom(generateToken(), roomName, isPublic, botName, peerWrapper.getNickname()));
                break;
            case "leave":
            case "quit":
                if(!handleModOrHigher(peer, args[0].toLowerCase())) return;
                parentWrapper.disallowJoin();
                parentWrapper.quitRound();
                sendChatMessage("I will no longer join games automatically. Type -join to re-enable joining.");
                break;
            case "wordcount":
            case "words":
                sendChatMessage("There are " + parentWrapper.wordLoader.getWordCount() + " words I currently know 🤖");
                break;
            case "findid":
                Peer peerById;
                if(args.length < 2) {
                    peerById = peerWrapper;
                } else {
                    int id;
                    try {
                        id = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sendChatMessage("Invalid ID format. Please provide a valid number.");
                        return;
                    }
                    if(id < 0) {
                        sendChatMessage("No player with that ID");
                        break;
                    }
                    if(id == peerWrapper.getPeerId()) {
                        sendChatMessage("You are looking for yourself, silly 🤓 (run -findid without arguments to find your own data)");
                        return;
                    }
                    peerById = parentWrapper.getPeerById(id);
                }
                if(peerById == null) {
                    sendChatMessage("No player with that ID");
                    break;
                }
                StringBuilder output = new StringBuilder();
                output.append("\"").append(peerById.getNickname()).append("\"");
                if(peerById.getAuth() != null) {
                    output  .append(" (")
                            .append(peerById.getAuth().getUsername())
                            .append(" on ")
                            .append(peerById.getAuth().getService())
                            .append(")");
                }
                output.append(" has ID ").append(peerById.getPeerId());
                if(peerById.getRoles() != null && !peerById.getRoles().isEmpty()) {
                    output.append(" and roles: ");
                    for(Role role : peerById.getRoles()) {
                        output.append(role.getRoleName()).append(", ");
                    }
                    output.setLength(output.length() - 2); // Remove last comma and space
                }
                output.append(" User Language is \"").append(peerById.getLanguage()).append("\"");
                sendChatMessage(output.toString());
                break;
            case "owner":
                if(!handleAuthRequired(peer, "become owner")) return;
                setLeader(peerWrapper.getPeerId());
                if(!parentWrapper.isCreator) sendChatMessage("I am not the creator of this room, I probably cannot make you the owner.");
                else                         sendChatMessage("I've made you the room owner 👑");
                break;
            case "help":
                if(args.length < 2) {
                    sendChatMessage("I am a friendly bot, that will play against you! Type -commands for a list of commands or -help <command> for more information about a specific command. (<Required>, [Optional])");
                    return;
                }
                String command = args[1].toLowerCase();
                switch (command) {
                    case "hello":
                        sendChatMessage("Type -hello to greet me! No arguments");
                        break;
                    case "save":
                        sendChatMessage("Type -save to save the words I know to the file. No arguments");
                        break;
                    case "add":
                        sendChatMessage("Type -add <word list> to add new words to my vocabulary.");
                        break;
                    case "wordcount":
                    case "words":
                        sendChatMessage("Type -words to see how many words I know.");
                        break;
                    case "commands":
                        sendChatMessage("Type -commands to see this message again.");
                        break;
                    case "join":
                        sendChatMessage("Allows me to join the game. Will require mod or higher permissions.");
                        break;
                    case "quit":
                    case "leave":
                        sendChatMessage("Will make it so I can no longer join a new game. Will require mod or higher permissions.");
                        break;
                    case "findid":
                        sendChatMessage("-findid Will find a player by ID (or your own without arguments). Usage: -findid [id]");
                        break;
                    case "joinroom":
                        sendChatMessage("-joinroom Allows me to join a specific room. Usage: -joinroom <roomId> [nickname].");
                        break;
                    case "leaveroom":
                        sendChatMessage("-leaveroom Will make me leave this room.");
                        break;
                    case "create":
                        sendChatMessage("-create Will create a new room with the given name and bot name. Usage: -create [roomName] [botName] [public|private]. Use 'd' for default values.");
                        break;
                    case "owner":
                        sendChatMessage("-owner will make you the owner of the game.");
                        break;
                    default:
                        sendChatMessage("Unknown command: " + command);
                }
                break;
            case "commands":
                sendChatMessage("Available commands: -hello, -words, -help [command], -join, -leave, -findid, -joinroom, -leaveroom, -create, -owner");
                break;
        }
    }

    private void setLeader(int peerId) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("setUserLeader");
        jsonArray.put(peerId);
        send(MessageType.GAME.getCode() + jsonArray.toString());
    }

    private boolean handleAuthRequired(JSONObject user, String action) {
        if(user == null) {
            sendChatMessage("User is null, cannot " + action + ".");
            return false;
        }
        Auth peerAuth = Auth.fromJson(user.optJSONObject("auth"));
        if(peerAuth == null) {
            sendChatMessage("You are not authenticated, cannot " + action + ". Please log in with Discord or Twitch.");
            return false;
        }
        return true;
    }

    private boolean handleModOrHigher(JSONObject user, String action) {
        if(user == null) {
            sendChatMessage("User is null, cannot " + action + ".");
            return false;
        }
        if(handleOwnerOnly(Auth.fromJson(user.optJSONObject("auth")))) return true;
        if(!user.has("roles")) {
            sendChatMessage("You have no roles, cannot " + action + ".");
            return false;
        }
        List<Object> roles = user.getJSONArray("roles").toList();
        // Check if the array contains "mod" or "owner"
        if(!roles.contains(Role.MODERATOR.getRoleName()) && !roles.contains(Role.LEADER.getRoleName())) {
            sendChatMessage("You are not allowed to use " + action + ", only mod or owner can.");
            return false;
        }
        return true;
    }

    private boolean handleOwnerOnly(Auth peerAuth, String action) {return handleOwnerOnly(peerAuth, action, false);}
    private boolean handleOwnerOnly(Auth peerAuth) {return handleOwnerOnly(peerAuth, "", true);}
    private boolean handleOwnerOnly(Auth peerAuth, String action, boolean surpressPrint) {
        if(peerAuth == null) {
            if(!surpressPrint) sendChatMessage("Peer auth is null, cannot "+action+".");
            return false;
        }
        if(!peerAuth.getUsername().equals("brentspine")) {
            if(!surpressPrint) sendChatMessage("You are not allowed to "+action+", only owner can.");
            return false;
        }
        if(!peerAuth.getService().equals("discord")) {
            if(!surpressPrint) sendChatMessage("Wrong auth service (" + peerAuth.getService() + "), only discord is allowed.");
            return false;
        }
        return true;
    }

    // Difference between joinRoom and joinGame (Probably if user has already set name)
    // Token is just pulled out of the frontends ass
    /*
    const array = new Uint8Array(16);
      crypto.getRandomValues(array);
      let token = "";
      const digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-";
      for (let i = 0; i < array.length; i++) token += digits[array[i] % digits.length];
     */
    public void joinRoom(String roomId, String username) {
        if(this.token != null && !isChatController) {
            joinRoomWithToken(roomId, token);
            return;
        }
        JSONArray answer = new JSONArray();
        answer.put("joinRoom");
        JSONObject joinRoom = new JSONObject();
        joinRoom.put("roomCode", roomId);
        joinRoom.put("language", "en-GB");
        joinRoom.put("nickname", username);
        joinRoom.put("userToken", (this.token != null) ? this.token : generateToken());
        answer.put(joinRoom);
        send(MessageType.JOIN.getCode() + answer.toString());
    }

    public void joinRoomWithToken(String roomId, String token) {
        JSONArray answer = new JSONArray();
        answer.put("joinGame");
        answer.put("bombparty");
        answer.put(roomId);
        answer.put(token);
        send(MessageType.GAME.getCode() + answer.toString());
    }

    // 420["joinRoom",{"roomCode":"VRKT","userToken":"EY3C0pEcnyjRTAtd","nickname":"Guest8817","language":"en-GB"}]
    // 420["joinRoom",{"userToken":"jf+9F-sZLrAcqfLe","nickname":"BBot","language":"en-GB","roomId":"VRKT"}]
    // 420["joinRoom",{"userToken":"zcF67B0mVtntMYE-","nickname":"BBot","language":"en-GB","roomId":"VRKT"}]

    private String generateToken() {
        StringBuilder t = new StringBuilder();
        String digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-";
        for (int i = 0; i < 16; i++) {
            int randomIndex = (int) (Math.random() * digits.length());
            t.append(digits.charAt(randomIndex));
        }
        System.out.println("Generated token: " + t);
        this.token = t.toString();
        return this.token;
    }

    private void handleAuth(JSONObject jsonObject) {
        System.out.println("Recognized as AUTH");
        this.latestSid = jsonObject.getString("sid");
        System.out.println("Stored SID: " + latestSid);
    }

    private MessageType getMessageType(String message) {
        int messageType;
        // Match and extract the leading number from the message
        Pattern pattern = Pattern.compile("^\\d+");
        Matcher matcher = pattern.matcher(message);
        if(!matcher.find()) {
            System.err.println("Failed to extract message type");
            return MessageType.NOT_FOUND;
        }
        String number = matcher.group();
        try {
            messageType = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse message type: " + e.getMessage());
            return MessageType.NOT_FOUND;
        }
        return MessageType.getMessageType(messageType);
    }

    public void sendChatMessage(String message) {
        if(!isChatController) {
            parentWrapper.sendChatMessage(message);
            return;
        }
        JSONArray x = new JSONArray();
        x.put("chat");
        x.put(message);
        send(MessageType.GAME.getCode() + x.toString());
    }

    public void getChattersProfiles() {
        JSONArray x = new JSONArray();
        x.put("getChatterProfiles");
        send(MessageType.GET_CHATTER_PROFILES.getCode() + x.toString());
    }

    private void handleClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + reason);
        connect();
    }

    private void handleError(Exception ex) {
        System.err.println("Error occurred: " + ex.getMessage());
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public SocketWrapper getParentWrapper() {
        return parentWrapper;
    }

    public void setParentWrapper(SocketWrapper parentWrapper) {
        this.parentWrapper = parentWrapper;
    }
}
