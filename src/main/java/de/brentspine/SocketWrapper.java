package de.brentspine;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SocketWrapper {

    private final String room;
    private String nickname;
    private SocketController chatController;
    private SocketController gameController;
    public WordLoader wordLoader;
    public boolean willJoin = false;

    public boolean isFarming = false;
    public boolean randomFarming = false;
    public String lastFarmedSyllable = "a";

    public String joinReason;
    public boolean isCreator = false;

    private ArrayList<Peer> peers;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public SocketWrapper(String room, String nickname, String fileName, String serverUrl) throws InterruptedException {
        this(room, nickname, fileName, serverUrl, null);
    }
    public SocketWrapper(String room, String nickname, String fileName, String socketServer, String token) throws InterruptedException {
        this.room = room;
        this.nickname = nickname;
        this.chatController = new SocketController("wss://"+socketServer+"/socket.io/?EIO=4&transport=websocket", token, true);
        this.peers = new ArrayList<>();
        chatController.setRoom(room);
        chatController.setNickname(nickname);
        chatController.setParentWrapper(this);

        HashMap<Character, Integer> alphabetRules = new HashMap<Character, Integer>();
        for (char c = 'a'; c <= 'z'; c++) {
            alphabetRules.put(c, 1); // Default score for each character
        }
        wordLoader = new WordLoader(fileName, alphabetRules);

        Thread.sleep(2000);

        gameController = new SocketController("wss://"+socketServer+"/socket.io/?EIO=4&transport=websocket", chatController.getToken());
        gameController.setRoom(room);
        gameController.setNickname(nickname);
        gameController.setParentWrapper(this);

        sendChatMessage("Hi, I'm BBot, a friendly bot that plays against you! Use -help for help or -commands for a list of commands. -join to join and -joinroom to join your lobby.");
        //if(joinReason != null && !joinReason.isEmpty()) sendChatMessage("Join Reason: " + joinReason);

        // SetInterval for farmNext
        EXECUTOR.scheduleAtFixedRate(() -> {
            if (isFarming) {
                farmNext();
            }
        }, 1, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void close() {
        chatController.close();
        gameController.close();
        EXECUTOR.shutdownNow();
    }

    public void farmNext() {
        if(!isFarming) return;
        if(randomFarming) {
            System.out.println("Trying to farm random syllable");
            StringBuilder syllable = new StringBuilder();
            String characters = "abcdefghijklmnopqrstuvwxyz";
            for(int i = 0; i < 4; i++) {
                int randomIndex = (int) (Math.random() * characters.length());
                syllable.append(characters.charAt(randomIndex));
            }
            sendChatMessage(".c " + syllable + (Math.random() < 0.5 ? " -l" : ""));
        } else {
            System.out.println("Farming next syllable after " + lastFarmedSyllable);
            // Take the last farmed syllable and increment the last character. If not possible, increment the second last character, etc. If all characters are 'z', reset to all to 'a' and increment the character count.
            for(int i = 0; i < lastFarmedSyllable.length(); i++) {
                char c = lastFarmedSyllable.charAt(lastFarmedSyllable.length() - 1 - i);
                if(c == 'z') {
                    if(i == lastFarmedSyllable.length() - 1) {
                        int originalLength = lastFarmedSyllable.length();
                        lastFarmedSyllable = "";
                        for(int j = 0; j < originalLength + 1; j++) {
                            lastFarmedSyllable += 'a';
                        }
                        break;
                    }
                    int saved = this.wordLoader.saveWords();
                    sendChatMessage("Auto-Farmed " + saved + " new words in the last increment.");
                } else {
                    char nextChar = (char) (c + 1);
                    lastFarmedSyllable = lastFarmedSyllable.substring(0, lastFarmedSyllable.length() - 1 - i) + nextChar;
                    for(int j = 0; j < i; j++) {
                        lastFarmedSyllable += 'a';
                    }
                    break;
                }
            }
            sendChatMessage(".c " + lastFarmedSyllable);
        }
    }

    public void sendChatMessage(String message) {
        chatController.sendChatMessage(message);
    }

    public void joinRound() {
        wordLoader.saveWords();
        wordLoader.reset();
        gameController.joinRound();
        chatController.joinRound();
    }
    public void quitRound() {
        gameController.quitRound();
        chatController.quitRound();
    }

    public void allowJoin() {
        willJoin = true;
    }
    public void disallowJoin() {
        willJoin = false;
    }
    public boolean willJoin() {
        return willJoin;
    }

    public void updatePeers() {
        chatController.getChattersProfiles();
    }

    public void updatePeersJson(JSONArray peersJson) {
        peers = new ArrayList<>();
        for (int i = 0; i < peersJson.length(); i++) {
            peers.add(Peer.fromJson(peersJson.getJSONObject(i)));
        }
    }

    public ArrayList<Peer> getPeers() {
        updatePeers();
        return peers;
    }

    public Peer getPeerById(int id) {
        updatePeers();
        for (Peer peer : peers) {
            if (peer.getPeerId() == id) {
                return peer;
            }
        }
        return null;
    }

    public void setJoinReason(String joinReason) {
        this.joinReason = joinReason;
        if (joinReason != null && !joinReason.isEmpty()) {
            sendChatMessage("Join Reason: " + joinReason);
        }
    }

    public String getJoinReason() {
        return joinReason;
    }

    public String getRoom() {
        return room;
    }
}
