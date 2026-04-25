package smartattendance.model;

public class Admin {
    private final String id;
    private final String name;
    private final String password;

    public Admin(String id, String name, String password) {
        this.id = requireText(id, "Admin id");
        this.name = requireText(name, "Admin name");
        this.password = requireText(password, "Password");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean passwordMatches(String candidate) {
        return password.equals(candidate);
    }
}
