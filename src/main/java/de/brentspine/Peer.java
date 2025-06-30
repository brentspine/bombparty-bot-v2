package de.brentspine;

import org.json.JSONObject;

import java.util.ArrayList;

public class Peer {

    // {"peerId":167,"auth":{"service":"discord","username":"brentspine","id":"533779674824966154"},"roles":[],"nickname":"Brentspine"}

    private int peerId;
    private Auth auth;
    private ArrayList<Role> roles;
    private String nickname;
    private String language;

    public Peer(int peerId, Auth auth, ArrayList<Role> roles, String nickname, String language) {
        this.peerId = peerId;
        this.auth = auth;
        this.roles = roles;
        this.nickname = nickname;
        this.language = language;
    }

    public static Peer fromJson(JSONObject json) {
        int peerId = json.getInt("peerId");
        String language = json.optString("language", "Unknown");
        JSONObject authJson = json.getJSONObject("auth");
        Auth auth = Auth.fromJson(authJson);
        ArrayList<Role> roles = new ArrayList<>();
        if (json.has("roles")) {
            for (Object roleObj : json.getJSONArray("roles")) {
                roles.add(Role.fromName(roleObj.toString()));
            }
        }
        String nickname = json.optString("nickname", "");
        return new Peer(peerId, auth, roles, nickname, language);
    }

    public int getPeerId() {
        return peerId;
    }

    public Auth getAuth() {
        return auth;
    }

    public ArrayList<Role> getRoles() {
        return roles;
    }

    public String getNickname() {
        return nickname;
    }

    public String getLanguage() {
        return language;
    }
}
