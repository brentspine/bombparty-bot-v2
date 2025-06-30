package de.brentspine.messages;

public enum GameMessageType {

    UNKNOWN("unknown"), // Unknown message type, should not happen

    JOIN_GAME("joinGame"), // A player enters the lobby,
    SETUP("setup"), // Tells the client the new game rules
    SET_PLAYER_WORD("setPlayerWord"), // Sets a peers current word
    CORRECT_WORD("correctWord"), // Tells the client that the word is correct for peer
    NEXT_TURN("nextTurn"), // New players turn 42["nextTurn",2,"git",0]  2 is peerid git is syllable
    FAIL_WORD("failWord"), // 42["failWord",77,"notInDictionary"] , mustContainSyllable, alreadyUsed
    CHAT("chat"), // Chat message 42["chat",{"peerId":1,"auth":{"service":"twitch","username":"meowbotjklm","id":"1323396088"},"roles":["leader"],"picture":"/9j/4AAQ...base64","nickname":"MeowBot"},"No words found matching \"creepvine\".",null]
    CHATTER_REMOVED("chatterRemoved"), // 42["chatterRemoved",{"nickname":"balls"}]
    CHATTER_ADDED("chatterAdded"), // 42["chatterAdded",{"nickname":"countable pixels","peerId":338,"auth":null}]
    CHAT_CROWDED("chatCrowded"), // 42["chatCrowded",true] (true or false based on whether the chat is crowded or not)
    JOIN_ROUND("joinRound"), // 42["joinRound"]
    ADD_PLAYER("addPlayer"), // 42["addPlayer",{"profile":{"peerId":1,"nickname":"Brentspine","language":"en-GB","auth":{"service":"discord","username":"brentspine","id":"533779674824966154"},"roles":["leader"]},"isOnline":true}]
    SET_START_TIME("setStartTime"), // 42["setStartTime",1750940141278,1750940126278]
    CLEAR_USED_WORDS("clearUsedWords"), // 42["clearUsedWords"]
    LIVES_LOST("livesLost"), // 42["livesLost",2,1]
    SET_WORD("setWord"), // 42["setWord","e",true] (true or false based on we enter it or not)
    START_ROUND_NOW("startRoundNow"), // 42["startRoundNow"]
    SET_RULES_LOCKED("setRulesLocked"), // 42["setRulesLocked",true]
    SET_PLAYER_COUNT("setPlayerCount"), // 42["setPlayerCount",2] (Number of players in the game)
    BONUS_ALPHABET_COMPLETED("bonusAlphabetCompleted"), // 42["bonusAlphabetCompleted",30,3] peer, new lives
    UPDATE_PLAYER("updatePlayer"), // 42["updatePlayer",59,{"peerId":59,"nickname":"StonecutterCatBugMin"}
    SET_MILESTONE("setMilestone"), // idk
    SET_LEADER_PEER("setLeaderPeer"), // 42["setLeaderPeer",59] (peerId of the new leader)

    USER_BANNED("userBanned"), // 42["userBanned",{"nickname":"qwertyonderly","peerId":322}]
    KICKED("kicked"); // Maybe only for joining

    private final String type;
    GameMessageType(String type) {
        this.type = type;
    }
    public String getType() {
        return type;
    }

    public static GameMessageType fromString(String type) {
        for (GameMessageType messageType : GameMessageType.values()) {
            if (messageType.getType().equalsIgnoreCase(type)) {
                return messageType;
            }
        }
        System.err.println("Unknown game message type: " + type);
        return UNKNOWN;
    }

}
