package de.brentspine;

import org.json.JSONObject;

/*
{
    "service": "discord",
    "username": "brentspine",
    "token": "Njg4MTI2MDkzNDI0NzIxOTU0.cxOp5jwEgWgR20BZ9S6tuTvQW0HkZu",
    "expiration": 1751501646656
}
 */
public class Auth {
    private String service;
    private String username;
    private String id;
    public Auth(String service, String username, String id) {
        this.service = service;
        this.username = username;
        this.id = id;
    }

    public String getService() {
        return service;
    }

    public String getUsername() {
        return username;
    }

    public String getId() {
        return id;
    }

    public void toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("service", service);
        jsonObject.put("username", username);
        jsonObject.put("id", id);
    }

    public static Auth fromJson(JSONObject jsonObject) {
        if(jsonObject == null) return null;
        String service = jsonObject.getString("service");
        String username = jsonObject.getString("username");
        String id = jsonObject.getString("id");
        return new Auth(service, username, id);
    }
}
