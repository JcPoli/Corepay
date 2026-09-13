package dev.jcpolicarpio.corepay.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "corepay")
public class CorepayProperties {

    private final Security security = new Security();
    private final Statements statements = new Statements();

    public Security getSecurity() {
        return security;
    }

    public Statements getStatements() {
        return statements;
    }

    public static class Security {
        private String jwtSecret = "local-development-secret-change-me-please";
        private int tokenValidityMinutes = 60;
        private List<DemoUser> demoUsers = new ArrayList<>();

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public int getTokenValidityMinutes() {
            return tokenValidityMinutes;
        }

        public void setTokenValidityMinutes(int tokenValidityMinutes) {
            this.tokenValidityMinutes = tokenValidityMinutes;
        }

        public List<DemoUser> getDemoUsers() {
            return demoUsers;
        }

        public void setDemoUsers(List<DemoUser> demoUsers) {
            this.demoUsers = demoUsers;
        }
    }

    public static class DemoUser {
        private String username;
        private String password;
        private String roles = "";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRoles() {
            return roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }
    }

    public static class Statements {
        private String storage = "local";
        private String localDirectory = "./build/statements";
        private String s3Bucket = "";
        private String s3Region = "me-central-1";

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }

        public String getLocalDirectory() {
            return localDirectory;
        }

        public void setLocalDirectory(String localDirectory) {
            this.localDirectory = localDirectory;
        }

        public String getS3Bucket() {
            return s3Bucket;
        }

        public void setS3Bucket(String s3Bucket) {
            this.s3Bucket = s3Bucket;
        }

        public String getS3Region() {
            return s3Region;
        }

        public void setS3Region(String s3Region) {
            this.s3Region = s3Region;
        }
    }
}
