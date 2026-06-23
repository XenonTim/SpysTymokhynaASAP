package org.example.models;

import java.util.ArrayList;
import java.util.List;

public class Chat {

    public enum Type { PRIVATE, GROUP }

    private String id;
    private Type type;
    private String name;
    private List<String> memberLogins;

    public Chat() {
        this.memberLogins = new ArrayList<>();
    }

    public Chat(Type type, String name, List<String> memberLogins) {
        this.type = type;
        this.name = name;
        this.memberLogins = memberLogins;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getMemberLogins() { return memberLogins; }
    public void setMemberLogins(List<String> memberLogins) { this.memberLogins = memberLogins; }
}
