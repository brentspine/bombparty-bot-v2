package de.brentspine;

public enum Role {

    UNKNOWN("unknown"),
    MODERATOR("mod"),
    LEADER("leader");

    private final String roleName;

    Role(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }

    public static Role fromName(String name) {
        for (Role role : Role.values()) {
            if (role.getRoleName().equalsIgnoreCase(name)) {
                return role;
            }
        }
        System.err.println("Role " + name + " not found");
        return Role.UNKNOWN;
    }

}
