package smartattendance.model;

import java.util.Objects;

public class Student {
    private final String id;
    private final String name;
    private final String password;

    public Student(String id, String name, String password) {
        this.id = requireText(id, "Student id");
        this.name = requireText(name, "Student name");
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

    public String getPassword() {
        return password;
    }

    public boolean passwordMatches(String candidate) {
        return Objects.equals(password, candidate);
    }
}
